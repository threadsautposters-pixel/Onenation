package com.onenation.recommender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING,
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                CallStateManager.setCallInProgress(context, true)
                RecommendationService.lastLog = "Paused while call is active"
                RecommendationService.onUpdate?.invoke()
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (CallStateManager.isCallInProgress(context)) {
                    CallStateManager.setCallInProgress(context, false)
                    RecommendationService.lastLog = "Call ended, resuming automation"
                    RecommendationService.onUpdate?.invoke()
                }
            }
        }
    }
}
