package ir.jrweb.smsvoice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val sender = messages[0].originatingAddress ?: "ناشناس"
            val fullBody = StringBuilder()
            for (sms in messages) {
                fullBody.append(sms.messageBody)
            }

            if (Settings.canDrawOverlays(context)) {
                val serviceIntent = Intent(context, FloatingVoiceService::class.java).apply {
                    putExtra("SENDER", sender)
                    putExtra("MESSAGE", fullBody.toString())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}