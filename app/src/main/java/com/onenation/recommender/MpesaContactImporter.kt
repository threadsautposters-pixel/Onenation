package com.onenation.recommender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MpesaContactImporter : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pdus = intent.extras?.get("pdus") as? Array<*> ?: return

        for (pdu in pdus) {
            val sms = SmsMessage.createFromPdu(pdu as ByteArray)
            val body = sms.messageBody ?: continue
            val sender = sms.originatingAddress.orEmpty()
            val normalizedBody = body.lowercase(Locale.getDefault())
            val isMpesaMessage =
                sender.equals("MPESA", true) ||
                    body.contains("M-Pesa", true) ||
                    (normalizedBody.contains("confirmed") && normalizedBody.contains("received from"))

            if (!isMpesaMessage) continue

            AutomationPauseManager.pauseForMpesa(context)
            val remaining = AutomationPauseManager.describeRemaining(
                AutomationPauseManager.getRemainingPauseMs(context),
            )
            RecommendationService.lastLog = "Paused for $remaining after M-Pesa SMS"
            saveLog(
                context,
                "[INFO] M-Pesa SMS received. Automation paused for $remaining",
            )
            RecommendationService.onUpdate?.invoke()

            val phone = Regex("(?:254|0)(?:7\\d{8}|1\\d{8})").find(body)?.value.orEmpty()
            if (phone.isNotEmpty() && !ContactManager.isInstalled(context, phone)) {
                val now = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                ContactManager.saveNumber(
                    context,
                    SavedNumber(
                        phone = phone,
                        dateAdded = now,
                        timeAdded = time,
                        status = "IMPORTED_PENDING",
                        source = "IMPORTED_MPESA",
                    ),
                )
            }
        }
    }
}
