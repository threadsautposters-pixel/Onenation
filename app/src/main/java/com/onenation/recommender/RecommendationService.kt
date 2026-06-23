package com.onenation.recommender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.TelephonyManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecommendationService : Service() {

    companion object {
        const val ACT_STOP = "com.onenation.recommender.STOP"

        private const val CHANNEL_ID = "on"
        private const val NOTIFICATION_ID = 4001
        private const val RUN_INTERVAL_MS = 8_000L

        var isRunning = false
        var successCount = 0
        var totalAttempts = 0
        var failedCount = 0
        var installedCount = 0
        var lastLog = ""
        var onUpdate: (() -> Unit)? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var target = DEFAULT_DAILY_TARGET
    private var today = 0
    private val generatedNumbers = mutableSetOf<String>()

    private val worker = object : Runnable {
        override fun run() {
            if (!isRunning) return

            target = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_DAILY_TARGET, DEFAULT_DAILY_TARGET)

            if (today >= target) {
                lastLog = "Daily target reached"
                saveLog(this@RecommendationService, "[INFO] Daily target reached: $target")
                onUpdate?.invoke()
                stopServiceSafely()
                return
            }

            val nextPhone = pickNextPhone()
            processPhone(nextPhone)
            handler.postDelayed(this, RUN_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "One Nation",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACT_STOP) {
            persistBackgroundState(false)
            stopServiceSafely()
            return START_NOT_STICKY
        }

        if (isRunning) return START_STICKY

        return try {
            persistBackgroundState(true)
            isRunning = true
            today = 0
            successCount = 0
            totalAttempts = 0
            failedCount = 0
            installedCount = 0
            generatedNumbers.clear()
            lastLog = "Started on ${SimSelection.getSelectedSimLabel(this)}"
            onUpdate?.invoke()
            startForeground(NOTIFICATION_ID, buildNotification())
            handler.post(worker)
            START_STICKY
        } catch (e: Exception) {
            isRunning = false
            persistBackgroundState(false)
            lastLog = "Start failed: ${e.message.orEmpty()}"
            saveLog(this, "[START ERROR] ${e.javaClass.simpleName}: ${e.message.orEmpty()}")
            onUpdate?.invoke()
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun pickNextPhone(): String {
        val due = ContactManager.getDueForRetry(this)
        if (due.isNotEmpty()) return due.first().phone

        val pending = ContactManager.getPending(this).firstOrNull { it.nextRetryTime.isBlank() }
        if (pending != null) return pending.phone

        var generated: String
        do {
            generated = NumberGenerator.generate()
        } while (
            generatedNumbers.contains(generated) ||
            ContactManager.isPending(this, generated) ||
            ContactManager.isInstalled(this, generated)
        )

        generatedNumbers.add(generated)
        ContactManager.incrementGeneratedCount(this)
        return generated
    }

    private fun processPhone(phone: String) {
        val code = "*180*5*4*$phone*1#"
        totalAttempts++
        today++

        val simLabel = SimSelection.getSelectedSimLabel(this)
        val now = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        try {
            val telephonyManager = SimSelection.getTelephonyManager(this)
            val startResult = SilentUssd.execute(
                tm = telephonyManager,
                code = code,
                ok = { response ->
                    val finalResponse = response.ifBlank { "No response text returned" }
                    saveLog(
                        this,
                        "[$now $time] USSD RESPONSE [$simLabel]: ${sanitizeLog(finalResponse)}",
                    )

                    when (ResponseParser.parse(response)) {
                        RecommendationResult.SUBMITTED -> {
                            successCount++
                            lastLog = "Success on $phone"
                            if (!ContactManager.isPending(this, phone)) {
                                ContactManager.saveNumber(
                                    this,
                                    SavedNumber(
                                        phone = phone,
                                        dateAdded = now,
                                        timeAdded = time,
                                        status = "PENDING",
                                        source = "GENERATED",
                                    ),
                                )
                            }
                            ContactManager.updateLastAttempted(this, phone)
                            saveLog(this, "[$now $time] SUCCESS [$simLabel]: $phone")
                        }

                        RecommendationResult.ALREADY_INSTALLED -> {
                            installedCount++
                            lastLog = "Already installed: $phone"
                            ContactManager.moveToInstalled(this, phone)
                            saveLog(this, "[$now $time] INSTALLED [$simLabel]: $phone")
                        }

                        RecommendationResult.FAILED -> {
                            failedCount++
                            lastLog = "Failed on $phone"
                            saveLog(this, "[$now $time] FAILED [$simLabel]: $phone")
                        }
                    }

                    updateNotification()
                    onUpdate?.invoke()
                },
                err = { error ->
                    failedCount++
                    val message = error.ifBlank { "Unknown USSD error" }
                    lastLog = "USSD error on $phone"
                    saveLog(this, "[$now $time] USSD ERROR [$simLabel]: ${sanitizeLog(message)}")
                    saveLog(this, "[$now $time] ERROR [$simLabel]: $phone")
                    updateNotification()
                    onUpdate?.invoke()
                },
            )

            if (startResult is SilentUssd.StartResult.NotStarted) {
                failedCount++
                lastLog = "USSD request could not start"
                saveLog(
                    this,
                    "[$now $time] ERROR [$simLabel]: Unable to start USSD for $phone (${sanitizeLog(startResult.reason)})",
                )
                updateNotification()
                onUpdate?.invoke()
            }
        } catch (e: Exception) {
            failedCount++
            lastLog = "Crash prevented while processing $phone"
            saveLog(this, "[$now $time] ERROR [$simLabel]: ${e.javaClass.simpleName}: ${e.message.orEmpty()}")
            updateNotification()
            onUpdate?.invoke()
        }
    }

    private fun stopServiceSafely() {
        isRunning = false
        handler.removeCallbacks(worker)
        lastLog = "Stopped"
        onUpdate?.invoke()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(worker)
        super.onDestroy()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, RecommendationService::class.java).apply { action = ACT_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val status = "Running in background | Success: $successCount | ${SimSelection.getSelectedSimLabel(this)}"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("One Nation automation")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setContentIntent(openAppIntent)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("One Nation automation")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setContentIntent(openAppIntent)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
                .build()
        }
    }

    private fun persistBackgroundState(shouldKeepRunning: Boolean) {
        getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_KEEP_SERVICE_RUNNING, shouldKeepRunning)
            .apply()
    }

    private fun sanitizeLog(value: String): String = value.replace("\n", " ").trim()
}
