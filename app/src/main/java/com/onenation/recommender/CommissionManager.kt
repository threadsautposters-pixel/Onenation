package com.onenation.recommender

import android.content.Context
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

object CommissionManager {
    private const val PREFS_NAME = DATA_PREFS
    private const val KEY_WEEKLY_COMMISSION = "commission_weekly"
    private const val KEY_WEEKLY_BUCKET = "commission_weekly_bucket"
    private const val KEY_MONTHLY_COMMISSION = "commission_monthly"
    private const val KEY_MONTHLY_BUCKET = "commission_monthly_bucket"
    private const val KEY_LIFETIME_COMMISSION = "commission_lifetime"

    private val commissionRegex = Regex(
        pattern = """ksh\s*([0-9]+(?:\.[0-9]+)?)\s+commission""",
        option = RegexOption.IGNORE_CASE,
    )

    fun maybeRecordFromSms(context: Context, sender: String, body: String): Boolean {
        if (!isCommissionMessage(sender, body)) return false

        val amount = extractAmount(body) ?: return false
        recordCommission(context, amount)
        return true
    }

    fun recordCommission(context: Context, amount: Double) {
        if (amount <= 0.0) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val weeklyBucket = currentWeeklyBucket()
        val monthlyBucket = currentMonthlyBucket()
        val weekly = if (prefs.getString(KEY_WEEKLY_BUCKET, "") == weeklyBucket) {
            prefs.getFloat(KEY_WEEKLY_COMMISSION, 0f).toDouble()
        } else {
            0.0
        }
        val monthly = if (prefs.getString(KEY_MONTHLY_BUCKET, "") == monthlyBucket) {
            prefs.getFloat(KEY_MONTHLY_COMMISSION, 0f).toDouble()
        } else {
            0.0
        }
        val lifetime = prefs.getFloat(KEY_LIFETIME_COMMISSION, 0f).toDouble()

        prefs.edit()
            .putString(KEY_WEEKLY_BUCKET, weeklyBucket)
            .putFloat(KEY_WEEKLY_COMMISSION, (weekly + amount).toFloat())
            .putString(KEY_MONTHLY_BUCKET, monthlyBucket)
            .putFloat(KEY_MONTHLY_COMMISSION, (monthly + amount).toFloat())
            .putFloat(KEY_LIFETIME_COMMISSION, (lifetime + amount).toFloat())
            .apply()
    }

    fun getWeeklyCommission(context: Context): Double {
        ensureCurrentBuckets(context)
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_WEEKLY_COMMISSION, 0f)
            .toDouble()
    }

    fun getMonthlyCommission(context: Context): Double {
        ensureCurrentBuckets(context)
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_MONTHLY_COMMISSION, 0f)
            .toDouble()
    }

    fun getLifetimeCommission(context: Context): Double {
        ensureCurrentBuckets(context)
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_LIFETIME_COMMISSION, 0f)
            .toDouble()
    }

    fun exportState(context: Context): JSONObject {
        ensureCurrentBuckets(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return JSONObject().apply {
            put("weeklyBucket", prefs.getString(KEY_WEEKLY_BUCKET, currentWeeklyBucket()).orEmpty())
            put("weeklyCommission", prefs.getFloat(KEY_WEEKLY_COMMISSION, 0f).toDouble())
            put("monthlyBucket", prefs.getString(KEY_MONTHLY_BUCKET, currentMonthlyBucket()).orEmpty())
            put("monthlyCommission", prefs.getFloat(KEY_MONTHLY_COMMISSION, 0f).toDouble())
            put("lifetimeCommission", prefs.getFloat(KEY_LIFETIME_COMMISSION, 0f).toDouble())
        }
    }

    fun mergeStates(states: List<JSONObject>): JSONObject {
        val currentWeeklyBucket = currentWeeklyBucket()
        val currentMonthlyBucket = currentMonthlyBucket()
        var weekly = 0.0
        var monthly = 0.0
        var lifetime = 0.0

        states.forEach { state ->
            lifetime += state.optDouble("lifetimeCommission", 0.0)
            if (state.optString("weeklyBucket") == currentWeeklyBucket) {
                weekly += state.optDouble("weeklyCommission", 0.0)
            }
            if (state.optString("monthlyBucket") == currentMonthlyBucket) {
                monthly += state.optDouble("monthlyCommission", 0.0)
            }
        }

        return JSONObject().apply {
            put("weeklyBucket", currentWeeklyBucket)
            put("weeklyCommission", weekly)
            put("monthlyBucket", currentMonthlyBucket)
            put("monthlyCommission", monthly)
            put("lifetimeCommission", lifetime)
        }
    }

    fun applyMergedState(context: Context, state: JSONObject) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WEEKLY_BUCKET, state.optString("weeklyBucket", currentWeeklyBucket()))
            .putFloat(KEY_WEEKLY_COMMISSION, state.optDouble("weeklyCommission", 0.0).toFloat())
            .putString(KEY_MONTHLY_BUCKET, state.optString("monthlyBucket", currentMonthlyBucket()))
            .putFloat(KEY_MONTHLY_COMMISSION, state.optDouble("monthlyCommission", 0.0).toFloat())
            .putFloat(KEY_LIFETIME_COMMISSION, state.optDouble("lifetimeCommission", 0.0).toFloat())
            .apply()
    }

    private fun ensureCurrentBuckets(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentWeeklyBucket = currentWeeklyBucket()
        val currentMonthlyBucket = currentMonthlyBucket()
        val storedWeeklyBucket = prefs.getString(KEY_WEEKLY_BUCKET, null)
        val storedMonthlyBucket = prefs.getString(KEY_MONTHLY_BUCKET, null)
        val editor = prefs.edit()
        var changed = false

        if (storedWeeklyBucket != currentWeeklyBucket) {
            editor.putString(KEY_WEEKLY_BUCKET, currentWeeklyBucket)
            editor.putFloat(KEY_WEEKLY_COMMISSION, 0f)
            changed = true
        }
        if (storedMonthlyBucket != currentMonthlyBucket) {
            editor.putString(KEY_MONTHLY_BUCKET, currentMonthlyBucket)
            editor.putFloat(KEY_MONTHLY_COMMISSION, 0f)
            changed = true
        }

        if (changed) {
            editor.apply()
        }
    }

    private fun isCommissionMessage(sender: String, body: String): Boolean {
        val normalizedBody = body.lowercase(Locale.getDefault())
        return normalizedBody.contains("successfully activated my oneapp") &&
            normalizedBody.contains("commission will be credited") &&
            commissionRegex.containsMatchIn(body)
    }

    private fun extractAmount(body: String): Double? {
        val match = commissionRegex.find(body) ?: return null
        return match.groupValues.getOrNull(1)?.toDoubleOrNull()
    }

    private fun currentWeeklyBucket(): String {
        val now = Calendar.getInstance()
        now.firstDayOfWeek = Calendar.SUNDAY
        return "%04d-%02d".format(
            Locale.US,
            now.get(Calendar.YEAR),
            now.get(Calendar.WEEK_OF_YEAR),
        )
    }

    private fun currentMonthlyBucket(): String {
        val now = Calendar.getInstance()
        return "%04d-%02d".format(
            Locale.US,
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
        )
    }
}
