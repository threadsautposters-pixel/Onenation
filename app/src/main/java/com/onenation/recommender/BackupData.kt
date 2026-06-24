package com.onenation.recommender

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupData {
    private const val VERSION = 1

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
        val text = ctx.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: error("Unable to open input stream")

        val root = JSONObject(text)
        val data = root.optJSONObject("data") ?: JSONObject()
        val logs = root.optJSONObject("logs") ?: JSONObject()
        val settings = root.optJSONObject("settings") ?: JSONObject()

        importPrefs(ctx, DATA_PREFS, data)
        importPrefs(ctx, LOGS_PREFS, logs)
        importPrefs(ctx, SETTINGS_PREFS, settings)
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

    private fun importPrefs(ctx: Context, prefsName: String, obj: JSONObject) {
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
}

