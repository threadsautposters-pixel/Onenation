package com.onenation.recommender

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SavedNumber(
    val id: Long = System.currentTimeMillis(),
    val phone: String,
    val dateAdded: String,
    val timeAdded: String,
    var status: String,
    val source: String,
    var lastAttempted: String = "",
    var retryCount: Int = 0,
    var nextRetryTime: String = "",
)

object ContactManager {
    private const val P = "onenation_data"
    private const val KEY_GENERATED_COUNT = "gen"
    private const val KEY_LIFETIME_TOTAL = "total"
    private const val KEY_LIFETIME_SUCCESS = "success"
    private const val KEY_AUTO_DELETE = "autodel"
    private const val KEY_GENERATION_CREDITS = "generation_credits"
    private const val KEY_PENDING = "pending"
    private const val KEY_INSTALLED = "installed"
    private val retryFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun saveNumber(context: Context, number: SavedNumber) {
        if (isInstalled(context, number.phone)) return

        val pending = getPending(context).toMutableList()
        if (pending.any { it.phone == number.phone }) return

        pending.add(number)
        savePending(context, pending)
        incrementLifetimeTotal(context)
    }

    fun moveToInstalled(context: Context, phone: String) {
        val pending = getPending(context).toMutableList()
        val found = pending.find { it.phone == phone }
        pending.removeAll { it.phone == phone }
        savePending(context, pending)

        if (found != null) {
            val installed = getInstalled(context).toMutableList()
            if (installed.none { it.phone == phone }) {
                installed.add(
                    found.copy(
                        status = if (found.source.startsWith("IMPORTED")) "IMPORTED_INSTALLED" else "INSTALLED",
                        lastAttempted = formatNow(),
                    ),
                )
                saveInstalled(context, installed)
                incrementLifetimeSuccess(context)
                incrementGenerationCredits(context)
            }
        }
    }

    fun updateLastAttempted(context: Context, phone: String) {
        val pending = getPending(context).toMutableList()
        val index = pending.indexOfFirst { it.phone == phone }
        if (index < 0) return

        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        pending[index] = pending[index].copy(
            lastAttempted = formatNow(),
            retryCount = pending[index].retryCount + 1,
            nextRetryTime = retryFormatter.format(calendar.time),
        )
        savePending(context, pending)
    }

    fun getDueForRetry(context: Context): List<SavedNumber> {
        val now = System.currentTimeMillis()
        return getPending(context)
            .filter { parseRetryTime(it.nextRetryTime)?.let { time -> time <= now } ?: false }
            .sortedBy { parseRetryTime(it.nextRetryTime) ?: Long.MAX_VALUE }
    }

    fun getNextRetryDelayMs(context: Context): Long? {
        val now = System.currentTimeMillis()
        val nextRetryAt = getPending(context)
            .mapNotNull { parseRetryTime(it.nextRetryTime) }
            .minOrNull()
            ?: return null

        return (nextRetryAt - now).coerceAtLeast(0L)
    }

    fun getPending(context: Context): List<SavedNumber> = parse(
        context.getSharedPreferences(P, Context.MODE_PRIVATE),
        KEY_PENDING,
    )

    fun getInstalled(context: Context): List<SavedNumber> = parse(
        context.getSharedPreferences(P, Context.MODE_PRIVATE),
        KEY_INSTALLED,
    )

    fun getPendingCount(context: Context): Int = getPending(context).size

    fun getInstalledCount(context: Context): Int = getInstalled(context).size

    fun getGeneratedCount(context: Context): Long = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        .getLong(KEY_GENERATED_COUNT, 0L)

    fun incrementGeneratedCount(context: Context) {
        val prefs = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_GENERATED_COUNT, getGeneratedCount(context) + 1).apply()
    }

    fun getLifetimeTotal(context: Context): Long = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        .getLong(KEY_LIFETIME_TOTAL, 0L)

    fun getLifetimeSuccess(context: Context): Long = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        .getLong(KEY_LIFETIME_SUCCESS, 0L)

    fun getAutoDeleteSetting(context: Context): String = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        .getString(KEY_AUTO_DELETE, "never") ?: "never"

    fun setAutoDeleteSetting(context: Context, setting: String) {
        context.getSharedPreferences(P, Context.MODE_PRIVATE).edit().putString(KEY_AUTO_DELETE, setting).apply()
    }

    fun deleteInstalledNumbers(context: Context) {
        saveInstalled(context, emptyList())
    }

    fun deleteNumber(context: Context, phone: String) {
        savePending(context, getPending(context).filter { it.phone != phone })
        saveInstalled(context, getInstalled(context).filter { it.phone != phone })
    }

    fun isInstalled(context: Context, phone: String): Boolean = getInstalled(context).any { it.phone == phone }

    fun isPending(context: Context, phone: String): Boolean = getPending(context).any { it.phone == phone }

    fun getGenerationCredits(context: Context): Int = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        .getInt(KEY_GENERATION_CREDITS, 0)

    fun consumeGenerationCredit(context: Context): Boolean {
        val current = getGenerationCredits(context)
        if (current <= 0) return false

        context.getSharedPreferences(P, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_GENERATION_CREDITS, current - 1)
            .apply()
        return true
    }

    private fun incrementGenerationCredits(context: Context) {
        val prefs = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_GENERATION_CREDITS, getGenerationCredits(context) + 1).apply()
    }

    private fun incrementLifetimeTotal(context: Context) {
        val prefs = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LIFETIME_TOTAL, getLifetimeTotal(context) + 1).apply()
    }

    private fun incrementLifetimeSuccess(context: Context) {
        val prefs = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LIFETIME_SUCCESS, getLifetimeSuccess(context) + 1).apply()
    }

    private fun formatNow(): String = retryFormatter.format(Date())

    private fun parseRetryTime(value: String): Long? {
        if (value.isBlank()) return null
        return runCatching { retryFormatter.parse(value)?.time }.getOrNull()
    }

    private fun parse(prefs: SharedPreferences, key: String): List<SavedNumber> {
        return try {
            val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                SavedNumber(
                    id = obj.optLong("id"),
                    phone = obj.getString("phone"),
                    dateAdded = obj.getString("dateAdded"),
                    timeAdded = obj.getString("timeAdded"),
                    status = obj.getString("status"),
                    source = obj.getString("source"),
                    lastAttempted = obj.optString("last"),
                    retryCount = obj.optInt("retry"),
                    nextRetryTime = obj.optString("next"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun savePending(context: Context, numbers: List<SavedNumber>) {
        save(context, KEY_PENDING, numbers)
    }

    private fun saveInstalled(context: Context, numbers: List<SavedNumber>) {
        save(context, KEY_INSTALLED, numbers)
    }

    private fun save(context: Context, key: String, numbers: List<SavedNumber>) {
        val array = JSONArray()
        numbers.forEach { number ->
            array.put(
                JSONObject().apply {
                    put("id", number.id)
                    put("phone", number.phone)
                    put("dateAdded", number.dateAdded)
                    put("timeAdded", number.timeAdded)
                    put("status", number.status)
                    put("source", number.source)
                    put("last", number.lastAttempted)
                    put("retry", number.retryCount)
                    put("next", number.nextRetryTime)
                },
            )
        }

        context.getSharedPreferences(P, Context.MODE_PRIVATE)
            .edit()
            .putString(key, array.toString())
            .apply()
    }
}
