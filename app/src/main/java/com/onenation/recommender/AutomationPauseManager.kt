package com.onenation.recommender

import android.content.Context

object AutomationPauseManager {
    fun pauseForMpesa(context: Context, durationMs: Long = MPESA_PAUSE_MS): Long {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val currentPauseUntil = prefs.getLong(KEY_AUTOMATION_PAUSED_UNTIL, 0L)
        val pauseUntil = maxOf(currentPauseUntil, now + durationMs)

        prefs.edit()
            .putLong(KEY_AUTOMATION_PAUSED_UNTIL, pauseUntil)
            .apply()

        return pauseUntil
    }

    fun getRemainingPauseMs(context: Context): Long {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val pauseUntil = prefs.getLong(KEY_AUTOMATION_PAUSED_UNTIL, 0L)
        val remaining = pauseUntil - System.currentTimeMillis()

        if (remaining <= 0L && pauseUntil != 0L) {
            prefs.edit().remove(KEY_AUTOMATION_PAUSED_UNTIL).apply()
            return 0L
        }

        return remaining.coerceAtLeast(0L)
    }

    fun describeRemaining(remainingMs: Long): String {
        val totalSeconds = (remainingMs + 999L) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }
    }
}
