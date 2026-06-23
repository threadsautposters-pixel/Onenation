package com.onenation.recommender

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager

object SilentUssd {
    sealed interface StartResult {
        object Started : StartResult

        data class NotStarted(val reason: String) : StartResult
    }

    fun execute(
        tm: TelephonyManager,
        code: String,
        ok: (String) -> Unit,
        err: (String) -> Unit,
    ): StartResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return StartResult.NotStarted("USSD requires Android 8.0+")
        }

        val c = if (code.endsWith("#")) code else "$code#"
        val handler = Handler(Looper.getMainLooper())

        return try {
            tm.sendUssdRequest(
                c,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        request: String,
                        response: CharSequence,
                    ) {
                        ok(response.toString())
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        request: String,
                        failureCode: Int,
                    ) {
                        err("USSD failed: $failureCode")
                    }
                },
                handler,
            )
            StartResult.Started
        } catch (e: SecurityException) {
            StartResult.NotStarted(e.message ?: "USSD blocked (missing permission or system restriction)")
        } catch (t: Throwable) {
            StartResult.NotStarted(t.message ?: t.javaClass.simpleName)
        }
    }
}
