package com.onenation.recommender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        RetryAlarmReceiver.schedule(context)

        val shouldResumeService = context
            .getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_SERVICE_RUNNING, false)

        if (shouldResumeService) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RecommendationService::class.java),
            )
        }
    }
}
