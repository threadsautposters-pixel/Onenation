package com.onenation.recommender

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

data class SimOption(val subscriptionId: Int, val slotIndex: Int, val label: String)

object SimSelection {
    private const val PREFS = "onenation_settings"
    private const val KEY_SUBSCRIPTION_ID = "sim_subscription_id"
    const val AUTO_SUBSCRIPTION_ID = -1

    fun getStoredSubscriptionId(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SUBSCRIPTION_ID, AUTO_SUBSCRIPTION_ID)

    fun saveSelectedSubscriptionId(ctx: Context, subscriptionId: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_SUBSCRIPTION_ID, subscriptionId).apply()
    }

    @SuppressLint("MissingPermission")
    fun getAvailableSimOptions(ctx: Context): List<SimOption> {
        return try {
            val manager = SubscriptionManager.from(ctx)
            val active = manager.activeSubscriptionInfoList.orEmpty()
            active.sortedBy { it.simSlotIndex }.map { info ->
                val simName = "SIM ${info.simSlotIndex + 1}"
                val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                val lastDigits = info.number?.takeLast(4)?.takeIf { it.isNotBlank() }
                val label = buildString {
                    append(simName)
                    if (carrier != null) append(" - $carrier")
                    if (lastDigits != null) append(" ($lastDigits)")
                }
                SimOption(subscriptionId = info.subscriptionId, slotIndex = info.simSlotIndex, label = label)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getSelectedSubscriptionId(ctx: Context): Int? {
        val stored = getStoredSubscriptionId(ctx)
        if (stored == AUTO_SUBSCRIPTION_ID) return null
        return getAvailableSimOptions(ctx).firstOrNull { it.subscriptionId == stored }?.subscriptionId
    }

    fun getSelectedSimLabel(ctx: Context): String {
        val selectedId = getSelectedSubscriptionId(ctx) ?: return "Auto"
        return getAvailableSimOptions(ctx).firstOrNull { it.subscriptionId == selectedId }?.label ?: "Auto"
    }

    fun getTelephonyManager(ctx: Context): TelephonyManager {
        val telephonyManager = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val selectedId = getSelectedSubscriptionId(ctx) ?: return telephonyManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            telephonyManager.createForSubscriptionId(selectedId)
        } else {
            telephonyManager
        }
    }
}
