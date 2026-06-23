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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecommendationService : Service() {
    companion object {
        const val ACT_STOP = "com.onenation.recommender.STOP"
        private const val CHANNEL_ID = "on"
        private const val NOTIFICATION_ID = 4001

        var isRunning = false
        var successCount = 0
        var totalAttempts = 0
        var failedCount = 0
        var installedCount = 0
        var lastLog = ""
        var onUpdate: (() -> Unit)? = null
    }

    private val h = Handler(Looper.getMainLooper())
    private var target = 1000
    private var today = 0
    private val gen = mutableSetOf<String>()

    private val r = object : Runnable {
        override fun run() {
            if (!isRunning) return
            try {
                target = getSharedPreferences("onenation_settings", Context.MODE_PRIVATE)
                    .getInt("daily_target", 1000)
                if (today >= target) {
                    lastLog = "Target reached"
                    onUpdate?.invoke()
                    stopS()
                    return
                }

                val nextPhone = nextPhoneToProcess()
                proc(nextPhone)
            } catch (t: Throwable) {
                handleFailure("RUN", t)
            } finally {
                if (isRunning) h.postDelayed(this, 8000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "One Nation",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Runs recommendation automation in the background"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        return try {
            if (i?.action == ACT_STOP) {
                stopS()
                return START_NOT_STICKY
            }

            if (!isRunning) {
                isRunning = true
                today = 0
                successCount = 0
                failedCount = 0
                installedCount = 0
                gen.clear()
                lastLog = "Started in background on ${SimSelection.getSelectedSimLabel(this)}"
                onUpdate?.invoke()
                startForeground(NOTIFICATION_ID, buildN())
                h.post(r)
            }
            START_STICKY
        } catch (t: Throwable) {
            handleFailure("START", t)
            START_NOT_STICKY
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    private fun nextPhoneToProcess(): String {
        val due = ContactManager.getDueForRetry(this)
        if (due.isNotEmpty()) return due.first().phone

        val pending = ContactManager.getPending(this).filter { it.nextRetryTime.isBlank() }
        if (pending.isNotEmpty()) return pending.first().phone

        var generated: String
        do {
            generated = NumberGenerator.generate()
        } while (
            gen.contains(generated) ||
            ContactManager.isPending(this, generated) ||
            ContactManager.isInstalled(this, generated)
        )
        gen.add(generated)
        ContactManager.incrementGeneratedCount(this)
        return generated
    }

    private fun proc(phone: String) {
        try {
            val code = "*180*5*4*$phone*1#"
            totalAttempts++
            today++
            val tm = SimSelection.getTelephonyManager(this)
            val simLabel = SimSelection.getSelectedSimLabel(this)
            val now = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            val started = SilentUssd.execute(
                tm,
                code,
                { response -> handleUssdResponse(phone, simLabel, now, time, response) },
                { response -> handleUssdError(phone, simLabel, now, time, response) },
            )

            if (!started) {
                failedCount++
                lastLog = "USSD not supported on this device"
                saveLog(
                    this,
                    "[$now $time] ERROR [$simLabel]: $phone | FINAL RESPONSE: USSD request could not start",
                )
                updateN()
                onUpdate?.invoke()
            }
        } catch (t: Throwable) {
            handleFailure("PROCESS", t)
        }
    }

    private fun handleUssdResponse(
        phone: String,
        simLabel: String,
        date: String,
        time: String,
        rawResponse: String,
    ) {
        val finalResponse = compactResponse(rawResponse)
        when (ResponseParser.parse(rawResponse)) {
            RecommendationResult.SUBMITTED -> {
                successCount++
                lastLog = "SUCCESS [$simLabel]: $phone"
                if (!ContactManager.isPending(this, phone)) {
                    ContactManager.saveNumber(
                        this,
                        SavedNumber(
                            phone = phone,
                            dateAdded = date,
                            timeAdded = time,
                            status = "PENDING",
                            source = "GENERATED",
                        ),
                    )
                }
                ContactManager.updateLastAttempted(this, phone)
                saveLog(
                    this,
                    "[$date $time] SUCCESS [$simLabel]: $phone | FINAL RESPONSE: $finalResponse",
                )
            }

            RecommendationResult.ALREADY_INSTALLED -> {
                installedCount++
                lastLog = "INSTALLED [$simLabel]: $phone"
                ContactManager.moveToInstalled(this, phone)
                saveLog(
                    this,
                    "[$date $time] INSTALLED [$simLabel]: $phone | FINAL RESPONSE: $finalResponse",
                )
            }

            RecommendationResult.FAILED -> {
                failedCount++
                lastLog = "FAILED [$simLabel]: $phone"
                saveLog(
                    this,
                    "[$date $time] FAILED [$simLabel]: $phone | FINAL RESPONSE: $finalResponse",
                )
            }
        }
        updateN()
        onUpdate?.invoke()
    }

    private fun handleUssdError(
        phone: String,
        simLabel: String,
        date: String,
        time: String,
        rawResponse: String,
    ) {
        failedCount++
        val finalResponse = compactResponse(rawResponse)
        lastLog = "ERROR [$simLabel]: $phone"
        saveLog(
            this,
            "[$date $time] ERROR [$simLabel]: $phone | FINAL RESPONSE: $finalResponse",
        )
        updateN()
        onUpdate?.invoke()
    }

    private fun stopS() {
        isRunning = false
        h.removeCallbacks(r)
        lastLog = "Stopped"
        onUpdate?.invoke()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        h.removeCallbacks(r)
        super.onDestroy()
    }

    private fun updateN() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildN())
    }

    private fun buildN(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, RecommendationService::class.java).apply { action = ACT_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val mainIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val status = "Success: $successCount | ${SimSelection.getSelectedSimLabel(this)}"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("One Nation Automation")
                .setContentText(status)
                .setSubText("Running in background")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setContentIntent(mainIntent)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("One Nation Automation")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setContentIntent(mainIntent)
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
                .build()
        }
    }

    private fun handleFailure(stage: String, t: Throwable) {
        failedCount++
        lastLog = "$stage ERROR: ${t.message ?: t.javaClass.simpleName}"
        saveLog(this, "[$stage ERROR] ${t.javaClass.simpleName}: ${t.message.orEmpty()}")
        onUpdate?.invoke()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
        isRunning = false
        h.removeCallbacks(r)
    }

    private fun compactResponse(response: String): String {
        val compact = response.replace("\\s+".toRegex(), " ").trim()
        return compact.ifBlank { "No response returned" }
    }
}
