package com.openchat.app.service.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.AndroidEntryPoint
import com.openchat.app.R
import com.openchat.app.presentation.IncomingCallActivity
import com.openchat.app.presentation.MainActivity

@AndroidEntryPoint
class CallService : LifecycleService() {

    private var callId: String? = null
    private var isVideoCall = false
    private var remoteUserName: String? = null
    private var callerId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        callId = intent?.getStringExtra(EXTRA_CALL_ID)
        isVideoCall = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) ?: false
        remoteUserName = intent?.getStringExtra(EXTRA_REMOTE_USER_NAME)
        callerId = intent?.getStringExtra(EXTRA_CALLER_ID)

        val action = intent?.action

        when (action) {
            ACTION_END_CALL -> handleEndCall()
            ACTION_ANSWER -> handleAnswer()
            ACTION_DECLINE -> handleDecline()
            else -> startForegroundService()
        }

        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = createCallNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createCallNotification())
    }

    private fun handleEndCall() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createCallNotification(): android.app.Notification {
        createNotificationChannel()
        val callerName = remoteUserName ?: "Ongoing Call"
        val titleText = if (isVideoCall) "OpenChat Video Call" else "OpenChat Voice Call"

        val endCallIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_END_CALL
        }
        val endPendingIntent = PendingIntent.getService(
            this, 0, endCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_active", true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (isVideoCall) R.drawable.ic_videocam else R.drawable.ic_call)
            .setContentTitle(titleText)
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.ic_call_end, "End Call", endPendingIntent)
            .setAutoCancel(false)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Call notifications"
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun handleAnswer() {
        val cId = callId ?: return
        val cName = remoteUserName ?: return
        val cId2 = callerId ?: return
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id", cId)
            putExtra("caller_id", cId2)
            putExtra("caller_name", cName)
            putExtra("is_video", isVideoCall)
        }
        startActivity(intent)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleDecline() {
        val cId = callId
        val cId2 = callerId
        if (cId != null && cId2 != null) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId != null) {
                FirebaseDatabase.getInstance().reference
                    .child("webrtc_signaling")
                    .child(cId2)
                    .child(cId)
                    .child("status")
                    .setValue("declined")
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_IS_VIDEO = "is_video"
        const val EXTRA_REMOTE_USER_NAME = "remote_user_name"
        const val EXTRA_CALLER_ID = "caller_id"
        const val NOTIFICATION_ID = 1001
        const val ACTION_END_CALL = "com.openchat.app.ACTION_END_CALL"
        const val ACTION_ANSWER = "com.openchat.app.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.openchat.app.ACTION_DECLINE"
        private const val CHANNEL_ID = "call_channel"

        fun start(context: Context, callId: String, isVideo: Boolean, remoteUserName: String? = null, callerId: String? = null) {
            val intent = Intent(context, CallService::class.java).apply {
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_IS_VIDEO, isVideo)
                putExtra(EXTRA_REMOTE_USER_NAME, remoteUserName)
                putExtra(EXTRA_CALLER_ID, callerId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CallService::class.java).apply {
                action = ACTION_END_CALL
            }
            context.startService(intent)
        }
    }
}
