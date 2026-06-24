package com.onenation.recommender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MpesaContactImporter : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val groupedMessages = linkedMapOf<String, StringBuilder>()
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        messages.forEach { sms ->
            val sender = sms.originatingAddress.orEmpty()
            val body = sms.messageBody.orEmpty()
            val key = "${sender.lowercase(Locale.getDefault())}:${sms.timestampMillis}"
            groupedMessages.getOrPut(key) { StringBuilder() }.append(body)
        }

        groupedMessages.forEach { (key, value) ->
            val sender = key.substringBefore(':')
            val body = value.toString()
            val normalizedBody = body.lowercase(Locale.getDefault())

            if (CommissionManager.maybeRecordFromSms(context, sender, body)) {
                val amount = Regex("""ksh\s*([0-9]+(?:\.[0-9]+)?)\s+commission""", RegexOption.IGNORE_CASE)
                    .find(body)
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()
                saveLog(context, "[INFO] Commission SMS recorded: Ksh $amount")
                RecommendationService.lastLog = "Commission recorded: Ksh $amount"
                RecommendationService.onUpdate?.invoke()
            }

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
