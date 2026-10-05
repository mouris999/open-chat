package com.openchat.app.presentation

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.openchat.app.R
import com.openchat.app.service.call.CallService

class IncomingCallActivity : ComponentActivity() {

    private var ringtone: android.media.MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        setContentView(R.layout.activity_incoming_call)

        val callId = intent.getStringExtra("call_id") ?: ""
        val callerId = intent.getStringExtra("caller_id") ?: ""
        val callerName = intent.getStringExtra("caller_name") ?: "Unknown"
        val isVideo = intent.getBooleanExtra("is_video", false)

        val tvCallerName = findViewById<TextView>(R.id.tvCallerName) ?: return
        val tvCallType = findViewById<TextView>(R.id.tvCallType) ?: return
        val btnAccept = findViewById<LinearLayout>(R.id.btnAccept) ?: return
        val btnDecline = findViewById<LinearLayout>(R.id.btnDecline) ?: return

        tvCallerName.text = callerName
        tvCallType.text = if (isVideo) "Incoming Video Call" else "Incoming Voice Call"

        startRingAndVibrate()

        btnAccept.setOnClickListener {
            stopRingAndVibrate()
            acceptCall(callId, callerId, callerName, isVideo)
        }

        btnDecline.setOnClickListener {
            stopRingAndVibrate()
            declineCall(callId, callerId)
        }
    }

    private fun startRingAndVibrate() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = android.media.MediaPlayer().apply {
                setDataSource(this@IncomingCallActivity, ringtoneUri)
                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
        } catch (e: Exception) { android.util.Log.w("IncomingCallActivity", "Ringtone start failed", e) }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(Vibrator::class.java)
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 1000, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopRingAndVibrate() {
        try {
            ringtone?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) { android.util.Log.w("IncomingCallActivity", "Ringtone stop failed", e) }
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun acceptCall(callId: String, callerId: String, callerName: String, isVideo: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id", callId)
            putExtra("caller_id", callerId)
            putExtra("caller_name", callerName)
            putExtra("is_video", isVideo)
            putExtra("is_incoming", true)
        }
        startActivity(intent)

        cancelNotification(callId.hashCode())
        finish()
    }

    private fun declineCall(callId: String, callerId: String) {
        if (callId.isNotEmpty()) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId != null) {
                FirebaseDatabase.getInstance().reference
                    .child("webrtc_signaling")
                    .child(callerId)
                    .child(callId)
                    .child("status")
                    .setValue("declined")
            }

            CallService.stop(this)
        }

        cancelNotification(callId.hashCode())
        finish()
    }

    private fun cancelNotification(notificationId: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    override fun onDestroy() {
        stopRingAndVibrate()
        super.onDestroy()
    }
}
