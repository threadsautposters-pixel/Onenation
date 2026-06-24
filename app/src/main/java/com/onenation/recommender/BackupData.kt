package com.onenation.recommender

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale

object BackupData {
    private const val VERSION = 1
    private const val KEY_PENDING = "pending"
    private const val KEY_INSTALLED = "installed"
    private const val KEY_GENERATED_COUNT = "gen"
    private const val KEY_LIFETIME_TOTAL = "total"
    private const val KEY_LIFETIME_SUCCESS = "success"
    private const val KEY_GENERATION_CREDITS = "generation_credits"
    private const val KEY_AUTO_DELETE = "autodel"
    private const val KEY_LOGS = "logs"

    private data class BackupPayload(
        val data: JSONObject,
        val logs: JSONObject,
        val settings: JSONObject,
        val score: Int,
    )

    fun exportToJson(ctx: Context): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()))
        root.put("data", exportPrefs(ctx.getSharedPreferences(DATA_PREFS, Context.MODE_PRIVATE)))
        root.put("logs", exportPrefs(ctx.getSharedPreferences(LOGS_PREFS, Context.MODE_PRIVATE)))
        root.put("settings", exportPrefs(ctx.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)))
        return root.toString()
    }

    fun writeBackup(ctx: Context, uri: Uri) {
        val content = exportToJson(ctx)
        ctx.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: error("Unable to open output stream")
    }

    fun restoreFromBackup(ctx: Context, uri: Uri) {
        restoreFromBackups(ctx, listOf(uri))
    }

    fun restoreFromBackups(ctx: Context, uris: List<Uri>) {
        require(uris.isNotEmpty()) { "No backup files selected" }

        val current = currentPayload(ctx)
        val backups = uris.map { uri -> readBackup(ctx, uri) }
        val allPayloads = listOf(current) + backups
        val preferredPayload = allPayloads.maxByOrNull { it.score } ?: current

        replacePrefs(ctx, DATA_PREFS, mergeDataPrefs(allPayloads, preferredPayload))
        replacePrefs(ctx, LOGS_PREFS, mergeLogsPrefs(allPayloads))
        replacePrefs(ctx, SETTINGS_PREFS, mergeSettingsPrefs(current.settings, preferredPayload.settings))
        CommissionManager.applyMergedState(
            ctx,
            CommissionManager.mergeStates(allPayloads.map { payload -> extractCommissionState(payload.data) }),
        )
    }

    private fun exportPrefs(prefs: android.content.SharedPreferences): JSONObject {
        val obj = JSONObject()
        prefs.all.forEach { (key, value) ->
            val item = JSONObject()
            when (value) {
                is String -> {
                    item.put("t", "s")
                    item.put("v", value)
                }
                is Int -> {
                    item.put("t", "i")
                    item.put("v", value)
                }
                is Long -> {
                    item.put("t", "l")
                    item.put("v", value)
                }
                is Float -> {
                    item.put("t", "f")
                    item.put("v", value.toDouble())
                }
                is Boolean -> {
                    item.put("t", "b")
                    item.put("v", value)
                }
                else -> {
                    item.put("t", "s")
                    item.put("v", value?.toString().orEmpty())
                }
            }
            obj.put(key, item)
        }
        return obj
    }

    private fun replacePrefs(ctx: Context, prefsName: String, obj: JSONObject) {
        val prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val editor = prefs.edit().clear()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = obj.optJSONObject(key) ?: continue
            when (item.optString("t")) {
                "s" -> editor.putString(key, item.optString("v"))
                "i" -> editor.putInt(key, item.optInt("v"))
                "l" -> editor.putLong(key, item.optLong("v"))
                "f" -> editor.putFloat(key, item.optDouble("v").toFloat())
                "b" -> editor.putBoolean(key, item.optBoolean("v"))
            }
        }
        editor.apply()
    }

    private fun currentPayload(ctx: Context): BackupPayload {
        val data = exportPrefs(ctx.getSharedPreferences(DATA_PREFS, Context.MODE_PRIVATE))
        val logs = exportPrefs(ctx.getSharedPreferences(LOGS_PREFS, Context.MODE_PRIVATE))
        val settings = exportPrefs(ctx.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE))
        return BackupPayload(data = data, logs = logs, settings = settings, score = calculateScore(data, logs))
    }

    private fun readBackup(ctx: Context, uri: Uri): BackupPayload {
        val text = ctx.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: error("Unable to open input stream")

        val root = JSONObject(text)
        val data = root.optJSONObject("data") ?: JSONObject()
        val logs = root.optJSONObject("logs") ?: JSONObject()
        val settings = root.optJSONObject("settings") ?: JSONObject()
        return BackupPayload(data = data, logs = logs, settings = settings, score = calculateScore(data, logs))
    }

    private fun mergeDataPrefs(payloads: List<BackupPayload>, preferredPayload: BackupPayload): JSONObject {
        val installedByPhone = linkedMapOf<String, SavedNumber>()
        val pendingByPhone = linkedMapOf<String, SavedNumber>()

        payloads.forEach { payload ->
            parseSavedNumbers(payload.data, KEY_INSTALLED).forEach { saved ->
                val merged = mergeSavedNumber(installedByPhone[saved.phone], saved)
                installedByPhone[saved.phone] = merged
                pendingByPhone.remove(saved.phone)
            }
        }

        payloads.forEach { payload ->
            parseSavedNumbers(payload.data, KEY_PENDING).forEach { saved ->
                if (installedByPhone.containsKey(saved.phone)) return@forEach
                val merged = mergeSavedNumber(pendingByPhone[saved.phone], saved)
                pendingByPhone[saved.phone] = merged
            }
        }

        val merged = JSONObject()
        merged.put(KEY_PENDING, stringItem(serializeSavedNumbers(pendingByPhone.values.toList())))
        merged.put(KEY_INSTALLED, stringItem(serializeSavedNumbers(installedByPhone.values.toList())))
        merged.put(KEY_GENERATED_COUNT, longItem(payloads.sumOf { itemLong(it.data, KEY_GENERATED_COUNT) }))
        merged.put(KEY_LIFETIME_TOTAL, longItem(payloads.sumOf { itemLong(it.data, KEY_LIFETIME_TOTAL) }))
        merged.put(KEY_LIFETIME_SUCCESS, longItem(payloads.sumOf { itemLong(it.data, KEY_LIFETIME_SUCCESS) }))
        merged.put(KEY_GENERATION_CREDITS, intItem(payloads.sumOf { itemInt(it.data, KEY_GENERATION_CREDITS) }))
        merged.put(
            KEY_AUTO_DELETE,
            stringItem(
                preferredPayload.data.optJSONObject(KEY_AUTO_DELETE)?.optString("v").orEmpty().ifBlank { "never" },
            ),
        )

        val handledKeys = setOf(
            KEY_PENDING,
            KEY_INSTALLED,
            KEY_GENERATED_COUNT,
            KEY_LIFETIME_TOTAL,
            KEY_LIFETIME_SUCCESS,
            KEY_GENERATION_CREDITS,
            KEY_AUTO_DELETE,
        )
        val preferredKeys = preferredPayload.data.keys()
        while (preferredKeys.hasNext()) {
            val key = preferredKeys.next()
            if (key in handledKeys) continue
            merged.put(key, preferredPayload.data.optJSONObject(key))
        }

        return merged
    }

    private fun mergeLogsPrefs(payloads: List<BackupPayload>): JSONObject {
        val mergedLogs = LinkedHashSet<String>()
        payloads.forEach { payload ->
            val rawLogs = payload.logs.optJSONObject(KEY_LOGS)?.optString("v").orEmpty()
            if (rawLogs.isBlank()) return@forEach
            runCatching {
                val logArray = JSONArray(rawLogs)
                for (index in 0 until logArray.length()) {
                    mergedLogs.add(logArray.optString(index))
                }
            }
        }

        val limitedLogs = JSONArray()
        mergedLogs.toList().takeLast(500).forEach { limitedLogs.put(it) }
        return JSONObject().apply {
            put(KEY_LOGS, stringItem(limitedLogs.toString()))
        }
    }

    private fun mergeSettingsPrefs(currentSettings: JSONObject, preferredSettings: JSONObject): JSONObject {
        val merged = JSONObject(currentSettings.toString())
        val keys = preferredSettings.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            merged.put(key, preferredSettings.optJSONObject(key))
        }
        return merged
    }

    private fun calculateScore(data: JSONObject, logs: JSONObject): Int {
        return parseSavedNumbers(data, KEY_PENDING).size +
            parseSavedNumbers(data, KEY_INSTALLED).size +
            parseLogs(logs).size
    }

    private fun extractCommissionState(data: JSONObject): JSONObject {
        return JSONObject().apply {
            put("weeklyBucket", data.optJSONObject("commission_weekly_bucket")?.optString("v").orEmpty())
            put("weeklyCommission", itemDouble(data, "commission_weekly"))
            put("monthlyBucket", data.optJSONObject("commission_monthly_bucket")?.optString("v").orEmpty())
            put("monthlyCommission", itemDouble(data, "commission_monthly"))
            put("lifetimeCommission", itemDouble(data, "commission_lifetime"))
        }
    }

    private fun parseSavedNumbers(data: JSONObject, key: String): List<SavedNumber> {
        val item = data.optJSONObject(key) ?: return emptyList()
        val raw = item.optString("v")
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                SavedNumber(
                    id = obj.optLong("id", System.currentTimeMillis() + index),
                    phone = obj.optString("phone"),
                    dateAdded = obj.optString("dateAdded"),
                    timeAdded = obj.optString("timeAdded"),
                    status = obj.optString("status"),
                    source = obj.optString("source"),
                    lastAttempted = obj.optString("last"),
                    retryCount = obj.optInt("retry"),
                    nextRetryTime = obj.optString("next"),
                )
            }.filter { it.phone.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun serializeSavedNumbers(numbers: List<SavedNumber>): String {
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
        return array.toString()
    }

    private fun mergeSavedNumber(existing: SavedNumber?, incoming: SavedNumber): SavedNumber {
        if (existing == null) return incoming
        return existing.copy(
            id = minOf(existing.id, incoming.id),
            dateAdded = existing.dateAdded.ifBlank { incoming.dateAdded },
            timeAdded = existing.timeAdded.ifBlank { incoming.timeAdded },
            status = if (incoming.status.length > existing.status.length) incoming.status else existing.status,
            source = if (incoming.source.length > existing.source.length) incoming.source else existing.source,
            lastAttempted = if (incoming.lastAttempted.isNotBlank()) incoming.lastAttempted else existing.lastAttempted,
            retryCount = maxOf(existing.retryCount, incoming.retryCount),
            nextRetryTime = if (incoming.nextRetryTime.isNotBlank()) incoming.nextRetryTime else existing.nextRetryTime,
        )
    }

    private fun parseLogs(logs: JSONObject): List<String> {
        val raw = logs.optJSONObject(KEY_LOGS)?.optString("v").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index -> array.optString(index) }
        }.getOrDefault(emptyList())
    }

    private fun itemLong(obj: JSONObject, key: String): Long {
        val item = obj.optJSONObject(key) ?: return 0L
        return when (item.optString("t")) {
            "i", "l" -> item.optLong("v")
            "s" -> item.optString("v").toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun itemInt(obj: JSONObject, key: String): Int {
        val item = obj.optJSONObject(key) ?: return 0
        return when (item.optString("t")) {
            "i", "l" -> item.optInt("v")
            "s" -> item.optString("v").toIntOrNull() ?: 0
            else -> 0
        }
    }

    private fun itemDouble(obj: JSONObject, key: String): Double {
        val item = obj.optJSONObject(key) ?: return 0.0
        return when (item.optString("t")) {
            "f", "i", "l" -> item.optDouble("v")
            "s" -> item.optString("v").toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun stringItem(value: String): JSONObject = JSONObject().apply {
        put("t", "s")
        put("v", value)
    }

    private fun intItem(value: Int): JSONObject = JSONObject().apply {
        put("t", "i")
        put("v", value)
    }

    private fun longItem(value: Long): JSONObject = JSONObject().apply {
        put("t", "l")
        put("v", value)
    }
}
