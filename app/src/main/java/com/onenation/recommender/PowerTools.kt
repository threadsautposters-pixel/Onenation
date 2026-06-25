package com.onenation.recommender

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DataCleanupResult(
    val pendingBefore: Int,
    val installedBefore: Int,
    val pendingAfter: Int,
    val installedAfter: Int,
    val removedInvalid: Int,
    val removedDuplicates: Int,
)

object PowerTools {
    fun generateDiagnosticsReport(ctx: Context): String {
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val permissions = listOf(
            "CALL_PHONE" to android.Manifest.permission.CALL_PHONE,
            "READ_CONTACTS" to android.Manifest.permission.READ_CONTACTS,
            "RECEIVE_SMS" to android.Manifest.permission.RECEIVE_SMS,
            "READ_SMS" to android.Manifest.permission.READ_SMS,
            "READ_PHONE_STATE" to android.Manifest.permission.READ_PHONE_STATE,
        ).joinToString("\n") { (label, perm) ->
            val granted = ContextCompat.checkSelfPermission(ctx, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
            "$label: ${if (granted) "GRANTED" else "DENIED"}"
        }

        val pending = ContactManager.getPendingCount(ctx)
        val installed = ContactManager.getInstalledCount(ctx)
        val gen = ContactManager.getGeneratedCount(ctx)
        val total = ContactManager.getLifetimeTotal(ctx)
        val success = ContactManager.getLifetimeSuccess(ctx)
        val sim = SimSelection.getSelectedSimLabel(ctx)
        val interval = describeExecutionInterval(ctx)

        val prefs = ctx.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val dailyTarget = prefs.getInt(KEY_DAILY_TARGET, DEFAULT_DAILY_TARGET)
        val keepRunning = prefs.getBoolean(KEY_KEEP_SERVICE_RUNNING, false)
        val pausedMs = AutomationPauseManager.getRemainingPauseMs(ctx)

        return buildString {
            appendLine("ONE NATION DIAGNOSTICS")
            appendLine("Generated: $now")
            appendLine()
            appendLine("APP")
            appendLine("Version: ${appVersionName(ctx)}")
            appendLine("Running: ${RecommendationService.isRunning}")
            appendLine("LastLog: ${RecommendationService.lastLog}")
            appendLine()
            appendLine("DEVICE")
            appendLine("SDK: ${Build.VERSION.SDK_INT}")
            appendLine("Model: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine("SETTINGS")
            appendLine("SIM: $sim")
            appendLine("DailyTarget: $dailyTarget")
            appendLine("Interval: $interval")
            appendLine("KeepRunning: $keepRunning")
            appendLine("PausedRemainingMs: $pausedMs")
            appendLine()
            appendLine("STATS")
            appendLine("Pending: $pending")
            appendLine("Installed: $installed")
            appendLine("GeneratedCount: $gen")
            appendLine("LifetimeTotalSaved: $total")
            appendLine("LifetimeSuccess: $success")
            appendLine()
            appendLine("PERMISSIONS")
            appendLine(permissions)
        }
    }

    fun writeTextFile(ctx: Context, uri: Uri, content: String) {
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: error("Unable to open output stream")
    }

    fun exportPendingCsv(ctx: Context): String {
        return exportCsv(ContactManager.getPending(ctx))
    }

    fun exportInstalledCsv(ctx: Context): String {
        return exportCsv(ContactManager.getInstalled(ctx))
    }

    private fun exportCsv(items: List<SavedNumber>): String {
        fun esc(value: String): String {
            val safe = value.replace("\"", "\"\"")
            return "\"$safe\""
        }

        val header = listOf(
            "phone",
            "dateAdded",
            "timeAdded",
            "status",
            "source",
            "lastAttempted",
            "retryCount",
            "nextRetryTime",
        ).joinToString(",")

        val rows = items.joinToString("\n") { n ->
            listOf(
                esc(n.phone),
                esc(n.dateAdded),
                esc(n.timeAdded),
                esc(n.status),
                esc(n.source),
                esc(n.lastAttempted),
                n.retryCount.toString(),
                esc(n.nextRetryTime),
            ).joinToString(",")
        }

        return if (rows.isBlank()) header else "$header\n$rows"
    }

    fun cleanAndDedup(ctx: Context): DataCleanupResult {
        val pendingBefore = ContactManager.getPending(ctx)
        val installedBefore = ContactManager.getInstalled(ctx)

        val validRegex = Regex("^0[17]\\d{8}$")

        val cleanedInstalled = linkedMapOf<String, SavedNumber>()
        var removedInvalid = 0
        var removedDuplicates = 0

        installedBefore.forEach { item ->
            if (!item.phone.matches(validRegex)) {
                removedInvalid++
                return@forEach
            }
            if (cleanedInstalled.containsKey(item.phone)) {
                removedDuplicates++
                return@forEach
            }
            cleanedInstalled[item.phone] = item
        }

        val cleanedPending = linkedMapOf<String, SavedNumber>()
        pendingBefore.forEach { item ->
            if (!item.phone.matches(validRegex)) {
                removedInvalid++
                return@forEach
            }
            if (cleanedInstalled.containsKey(item.phone)) {
                removedDuplicates++
                return@forEach
            }
            if (cleanedPending.containsKey(item.phone)) {
                removedDuplicates++
                return@forEach
            }
            cleanedPending[item.phone] = item
        }

        ContactManager.replaceAll(ctx, cleanedPending.values.toList(), cleanedInstalled.values.toList())

        return DataCleanupResult(
            pendingBefore = pendingBefore.size,
            installedBefore = installedBefore.size,
            pendingAfter = cleanedPending.size,
            installedAfter = cleanedInstalled.size,
            removedInvalid = removedInvalid,
            removedDuplicates = removedDuplicates,
        )
    }
}
