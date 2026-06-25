package com.onenation.recommender

import android.content.Context

const val SETTINGS_PREFS = "onenation_settings"
const val LOGS_PREFS = "onenation_logs"
const val DATA_PREFS = "onenation_data"

const val KEY_DAILY_TARGET = "daily_target"
const val KEY_SIM_SUBSCRIPTION_ID = "sim_subscription_id"
const val KEY_KEEP_SERVICE_RUNNING = "keep_service_running"
const val KEY_AUTOMATION_PAUSED_UNTIL = "automation_paused_until"
const val KEY_CALL_IN_PROGRESS = "call_in_progress"
const val KEY_EXECUTION_INTERVAL_VALUE = "execution_interval_value"
const val KEY_EXECUTION_INTERVAL_UNIT = "execution_interval_unit"

const val DEFAULT_DAILY_TARGET = 7000
const val DEFAULT_EXECUTION_INTERVAL_VALUE = 8
const val EXECUTION_INTERVAL_UNIT_SECONDS = "seconds"
const val EXECUTION_INTERVAL_UNIT_MINUTES = "minutes"
const val MPESA_PAUSE_MS = 2 * 60 * 1000L

fun getExecutionIntervalValue(ctx: Context): Int {
    return ctx.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getInt(KEY_EXECUTION_INTERVAL_VALUE, DEFAULT_EXECUTION_INTERVAL_VALUE)
        .coerceAtLeast(1)
}

fun getExecutionIntervalUnit(ctx: Context): String {
    val unit = ctx.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_EXECUTION_INTERVAL_UNIT, EXECUTION_INTERVAL_UNIT_SECONDS)
        .orEmpty()
    return when (unit) {
        EXECUTION_INTERVAL_UNIT_MINUTES -> EXECUTION_INTERVAL_UNIT_MINUTES
        else -> EXECUTION_INTERVAL_UNIT_SECONDS
    }
}

fun getExecutionIntervalMs(ctx: Context): Long {
    val value = getExecutionIntervalValue(ctx).toLong()
    return when (getExecutionIntervalUnit(ctx)) {
        EXECUTION_INTERVAL_UNIT_MINUTES -> value * 60_000L
        else -> value * 1_000L
    }
}

fun formatExecutionInterval(value: Int, unit: String): String {
    val safeValue = value.coerceAtLeast(1)
    return when (unit) {
        EXECUTION_INTERVAL_UNIT_MINUTES ->
            if (safeValue == 1) "1 minute" else "$safeValue minutes"
        else ->
            if (safeValue == 1) "1 second" else "$safeValue seconds"
    }
}

fun describeExecutionInterval(ctx: Context): String {
    return formatExecutionInterval(getExecutionIntervalValue(ctx), getExecutionIntervalUnit(ctx))
}
