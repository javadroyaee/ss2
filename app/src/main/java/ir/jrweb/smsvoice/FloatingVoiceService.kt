package ir.jrweb.smsvoice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.SmsManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.Locale

class FloatingVoiceService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private var currentSender: String = ""
    private var currentMessage: String = ""
    private var isTtsReady = false
    private var isReadyToSend = false

    private lateinit var tvSender: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etReplyText: EditText
    private lateinit var btnActionPrimary: Button
    private lateinit var btnReplyVoice: Button
    private lateinit var btnClose: Button

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        tts = TextToSpeech(this, this)
        setupFloatingWindow()
    }

    private fun startForegroundNotification() {
        val channelId = "JRwebSmsChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "سرویس دستیار پیامک JRweb",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("دستیار پیامک JRweb")
            .setContentText("پنجره شناور پیامک فعال است")
            .setSmallIcon(R.drawable.ic_jrweb_logo)
            .build()
        startForeground(1, notification)
    }

    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_sms, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 120

        tvSender = floatingView!!.findViewById(R.id.tvSender)
        tvStatus = floatingView!!.findViewById(R.id.tvStatus)
        etReplyText = floatingView!!.findViewById(R.id.etReplyText)
        btnActionPrimary = floatingView!!.findViewById(R.id.btnActionPrimary)
        btnReplyVoice = floatingView!!.findViewById(R.id.btnReplyVoice)
        btnClose = floatingView!!.findViewById(R.id.btnClose)

        btnClose.setOnClickListener {
            stopSelf()
        }

        btnActionPrimary.setOnClickListener {
            if (isReadyToSend) {
                sendSmsReply()
            } else {
                readSmsAloud()
            }
        }

        btnReplyVoice.setOnClickListener {
            startVoiceDictation()
        }

        windowManager.addView(floatingView, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentSender = intent?.getStringExtra("SENDER") ?: "ناشناس"
        currentMessage = intent?.getStringExtra("MESSAGE") ?: ""

        tvSender.text = "از: $currentSender"
        tvStatus.text = "پیامک جدید دریافت شد. برای شنیدن دکمه زیر را بزنید."
        etReplyText.visibility = View.GONE
        etReplyText.setText("")
        btnReplyVoice.visibility = View.GONE
        btnActionPrimary.text = "🔊 خواندن پیام"
        isReadyToSend = false

        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val faResult = tts?.setLanguage(Locale("fa", "IR"))
            if (faResult == TextToSpeech.LANG_MISSING_DATA || faResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            isTtsReady = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    floatingView?.post {
                        tvStatus.text = "خواندن پیام تمام شد. می‌توانید پاسخ دهید:"
                        btnActionPrimary.text = "🔄 تکرار خواندن"
                        btnReplyVoice.visibility = View.VISIBLE
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {}
            })
        }
    }

    private fun readSmsAloud() {
        if (!isTtsReady) {
            Toast.makeText(this, "موتور صوتی در حال آماده‌سازی است...", Toast.LENGTH_SHORT).show()
            return
        }
        tvStatus.text = "در حال خواندن پیام..."
        btnReplyVoice.visibility = View.VISIBLE
        val params = Bundle()
        tts?.speak(currentMessage, TextToSpeech.QUEUE_FLUSH, params, "SMS_UTTERANCE")
    }

    private fun startVoiceDictation() {
        tts?.stop()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "تشخیص گفتار گوگل روی گوشی فعال نیست.", Toast.LENGTH_LONG).show()
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                tvStatus.text = "🎙️ در حال گوش دادن... صحبت کنید"
                etReplyText.visibility = View.VISIBLE
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                tvStatus.text = "در حال پردازش صدا..."
            }
            override fun onError(error: Int) {
                tvStatus.text = "صدایی دریافت نشد. دوباره دکمه پاسخ را بزنید."
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    etReplyText.setText(matches[0])
                    tvStatus.text = "متن آماده است. برای ارسال دکمه سبز را بزنید:"
                    btnActionPrimary.text = "📤 ارسال پیامک"
                    btnActionPrimary.setBackgroundColor(0xFF4CAF50.toInt())
                    isReadyToSend = true
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partial.isNullOrEmpty()) {
                    etReplyText.setText(partial[0])
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(recognizerIntent)
    }

    private fun sendSmsReply() {
        val replyMessage = etReplyText.text.toString().trim()
        if (replyMessage.isEmpty()) {
            Toast.makeText(this, "متن پاسخ خالی است!", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(currentSender, null, replyMessage, null, null)
            Toast.makeText(this, "پاسخ با موفقیت ارسال شد!", Toast.LENGTH_LONG).show()
            stopSelf()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در ارسال پیامک: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        if (floatingView != null) {
            windowManager.removeView(floatingView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}