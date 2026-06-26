package com.onenation.recommender

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.BitSet
import java.lang.Math.floorMod

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

data class ContactImportBatchResult(
    val added: Int,
    val skippedDuplicate: Int,
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
    private const val KEY_GENERATED_BLOOM_LAYER_COUNT = "gen_bloom_layers"
    private const val KEY_GENERATED_BLOOM_LAYER_PREFIX = "gen_bloom_layer_"
    private const val KEY_GENERATED_BLOOM_COUNT_PREFIX = "gen_bloom_count_"
    private val retryFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    private const val MAX_DAILY_RETRY_COUNT = 7

    private const val GENERATED_BLOOM_BITS = 8_388_608
    private const val GENERATED_BLOOM_HASHES = 4
    private const val GENERATED_BLOOM_MAX_ITEMS_PER_LAYER = 350_000L

    private data class BloomLayer(
        val bits: BitSet,
        var count: Long,
    )

    private var bloomCache: MutableList<BloomLayer>? = null

    fun saveNumber(context: Context, number: SavedNumber) {
        if (isInstalled(context, number.phone)) return

        val pending = getPending(context).toMutableList()
        if (pending.any { it.phone == number.phone }) return

        pending.add(number)
        savePending(context, pending)
        incrementLifetimeTotal(context)
    }

    fun addImportedContactsBatch(
        context: Context,
        phones: Set<String>,
        date: String,
        time: String,
    ): ContactImportBatchResult {
        if (phones.isEmpty()) return ContactImportBatchResult(added = 0, skippedDuplicate = 0)

        val pending = getPending(context).toMutableList()
        val pendingSet = pending.map { it.phone }.toHashSet()
        val installedSet = getInstalled(context).map { it.phone }.toHashSet()

        var added = 0
        var skipped = 0

        phones.forEach { phone ->
            if (pendingSet.contains(phone) || installedSet.contains(phone)) {
                skipped++
                return@forEach
            }

            pending.add(
                SavedNumber(
                    phone = phone,
                    dateAdded = date,
                    timeAdded = time,
                    status = "IMPORTED_PENDING",
                    source = "IMPORTED_CONTACTS",
                ),
            )
            pendingSet.add(phone)
            added++
        }

        if (added > 0) {
            savePending(context, pending)
            incrementLifetimeTotalBy(context, added.toLong())
        }

        return ContactImportBatchResult(added = added, skippedDuplicate = skipped)
    }

    fun wasGeneratedBefore(context: Context, phone: String): Boolean {
        val layers = getBloomLayers(context)
        if (layers.isEmpty()) return false
        val (h1, h2) = hashPair(phone)
        layers.forEach { layer ->
            if (layer.bits.isEmpty) return@forEach
            var all = true
            for (i in 0 until GENERATED_BLOOM_HASHES) {
                val index = bloomIndex(h1, h2, i)
                if (!layer.bits.get(index)) {
                    all = false
                    break
                }
            }
            if (all) return true
        }
        return false
    }

    fun markGenerated(context: Context, phone: String) {
        val prefs = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        val layers = getBloomLayers(context)
        if (layers.isEmpty()) {
            layers.add(BloomLayer(BitSet(GENERATED_BLOOM_BITS), 0L))
        }

        val targetLayer = layers.last().let { layer ->
            if (layer.count >= GENERATED_BLOOM_MAX_ITEMS_PER_LAYER) {
                val newLayer = BloomLayer(BitSet(GENERATED_BLOOM_BITS), 0L)
                layers.add(newLayer)
                newLayer
            } else {
                layer
            }
        }

        val (h1, h2) = hashPair(phone)
        for (i in 0 until GENERATED_BLOOM_HASHES) {
            val index = bloomIndex(h1, h2, i)
            targetLayer.bits.set(index)
        }
        targetLayer.count += 1
        saveBloomLayers(prefs, layers)
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

    fun updateLastAttempted(context: Context, phone: String): Boolean {
        val pending = getPending(context).toMutableList()
        val index = pending.indexOfFirst { it.phone == phone }
        if (index < 0) return false
        val updatedRetryCount = pending[index].retryCount + 1
        if (updatedRetryCount >= MAX_DAILY_RETRY_COUNT) {
            pending.removeAt(index)
            savePending(context, pending)
            return true
        }
        val nextRetryAt = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
        pending[index] = pending[index].copy(
            lastAttempted = formatNow(),
            retryCount = updatedRetryCount,
            nextRetryTime = retryFormatter.format(Date(nextRetryAt)),
        )
        savePending(context, pending)
        return false
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

    fun deleteExceededRetryLimit(context: Context): Int {
        val pending = getPending(context)
        val filtered = pending.filter { it.retryCount < MAX_DAILY_RETRY_COUNT }
        val removed = pending.size - filtered.size
        if (removed > 0) {
            savePending(context, filtered)
        }
        return removed
    }

    fun getInstalled(context: Context): List<SavedNumber> = parse(
        context.getSharedPreferences(P, Context.MODE_PRIVATE),
        KEY_INSTALLED,
    )

    fun replaceAll(context: Context, pending: List<SavedNumber>, installed: List<SavedNumber>) {
        savePending(context, pending)
        saveInstalled(context, installed)
    }

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

    fun terminateNumber(context: Context, phone: String) {
        deleteNumber(context, phone)
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

    private fun incrementLifetimeTotalBy(context: Context, delta: Long) {
        if (delta <= 0L) return
        val prefs = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LIFETIME_TOTAL, getLifetimeTotal(context) + delta).apply()
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

    private fun getBloomLayers(context: Context): MutableList<BloomLayer> {
        bloomCache?.let { return it }
        val prefs = context.getSharedPreferences(P, Context.MODE_PRIVATE)
        val layerCount = prefs.getInt(KEY_GENERATED_BLOOM_LAYER_COUNT, 0).coerceAtLeast(0)
        val result = mutableListOf<BloomLayer>()
        for (i in 0 until layerCount) {
            val encoded = prefs.getString("$KEY_GENERATED_BLOOM_LAYER_PREFIX$i", "").orEmpty()
            val count = prefs.getLong("$KEY_GENERATED_BLOOM_COUNT_PREFIX$i", 0L).coerceAtLeast(0L)
            val bits = if (encoded.isBlank()) {
                BitSet(GENERATED_BLOOM_BITS)
            } else {
                val data = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
                if (data == null || data.isEmpty()) BitSet(GENERATED_BLOOM_BITS) else BitSet.valueOf(data)
            }
            result.add(BloomLayer(bits, count))
        }
        bloomCache = result
        return result
    }

    private fun saveBloomLayers(prefs: SharedPreferences, layers: List<BloomLayer>) {
        val edit = prefs.edit()
        edit.putInt(KEY_GENERATED_BLOOM_LAYER_COUNT, layers.size)
        layers.forEachIndexed { index, layer ->
            val encoded = Base64.encodeToString(layer.bits.toByteArray(), Base64.NO_WRAP)
            edit.putString("$KEY_GENERATED_BLOOM_LAYER_PREFIX$index", encoded)
            edit.putLong("$KEY_GENERATED_BLOOM_COUNT_PREFIX$index", layer.count)
        }
        edit.apply()
        bloomCache = layers.toMutableList()
    }

    private fun bloomIndex(h1: Long, h2: Long, round: Int): Int {
        val mixed = h1 + (h2 * (round.toLong() + 1L))
        // NOTE: We must ALWAYS return an index in [0, GENERATED_BLOOM_BITS),
        // otherwise BitSet.get/set can crash with "bitIndex < 0".
        // Using floorMod avoids negatives even for overflow / Long.MIN_VALUE edge cases.
        return floorMod(mixed, GENERATED_BLOOM_BITS.toLong()).toInt()
    }

    private fun hashPair(input: String): Pair<Long, Long> {
        return fnv1a64(input, 0xcbf29ce484222325uL.toLong()) to fnv1a64(input, 0xaf63dc4c8601ec8cuL.toLong())
    }

    private fun fnv1a64(input: String, seed: Long): Long {
        var hash = seed
        val prime = 0x100000001b3L
        input.forEach { ch ->
            hash = hash xor ch.code.toLong()
            hash *= prime
        }
        return hash
    }
}
