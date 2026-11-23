package ir.jrweb.smsvoice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnPermissions = findViewById<Button>(R.id.btnPermissions)
        val btnOverlay = findViewById<Button>(R.id.btnOverlay)
        val btnTestPopup = findViewById<Button>(R.id.btnTestPopup)

        btnPermissions.setOnClickListener {
            requestAllPermissions()
        }

        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "مجوز نمایش شناور از قبل فعال است.", Toast.LENGTH_SHORT).show()
            }
        }

        btnTestPopup.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "لطفاً ابتدا مجوز نمایش روی برنامه‌ها را فعال کنید.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val serviceIntent = Intent(this, FloatingVoiceService::class.java).apply {
                putExtra("SENDER", "09120000000")
                putExtra("MESSAGE", "سلام، این یک پیام آزمایشی از دستیار صوتی جی آر وب است.")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    private fun requestAllPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECORD_AUDIO
        )
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            Toast.makeText(this, "تمام مجوزها صادر شده‌اند.", Toast.LENGTH_SHORT).show()
        }
    }
}