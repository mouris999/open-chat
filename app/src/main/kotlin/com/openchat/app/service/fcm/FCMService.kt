package com.openchat.app.service.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.openchat.app.OpenChatApplication
import com.openchat.app.R
import com.openchat.app.presentation.IncomingCallActivity
import com.openchat.app.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference
            .child("fcm_tokens")
            .child(userId)
            .setValue(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data["type"]

        when (type) {
            "message" -> handleMessageNotification(data)
            "call" -> handleCallNotification(data)
            "missed_call" -> handleMissedCallNotification(data)
            "reaction" -> handleReactionNotification(data)
        }
    }

    private fun isChatLocked(chatId: String): Boolean {
        return try {
            val prefs = getSharedPreferences("chat_lock_prefs", MODE_PRIVATE)
            val lockedChats = prefs.getStringSet("locked_chats", emptySet()) ?: emptySet()
            lockedChats.contains(chatId)
        } catch (e: Exception) {
            false
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun handleMessageNotification(data: Map<String, String>) {
        val chatId = data["chat_id"] ?: return
        val senderName = data["sender_name"] ?: "Unknown"
        val messageText = data["message"] ?: "New message"

        if (!hasNotificationPermission()) return

        val isLocked = isChatLocked(chatId)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", chatId)
            if (isLocked) {
                putExtra("requires_private_unlock", true)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, OpenChatApplication.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(chatId.hashCode(), notification)
    }

    private fun handleCallNotification(data: Map<String, String>) {
        val callId = data["call_id"] ?: return
        if (!hasNotificationPermission()) return
        val callerName = data["caller_name"] ?: "Unknown"
        val isVideo = data["is_video"]?.toBoolean() ?: false

        val callerId = data["caller_id"] ?: ""
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id", callId)
            putExtra("caller_id", callerId)
            putExtra("caller_name", callerName)
            putExtra("is_video", isVideo)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, OpenChatApplication.CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Incoming ${if (isVideo) "Video" else "Voice"} Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(callId.hashCode(), notification)
    }

    private fun handleMissedCallNotification(data: Map<String, String>) {
        if (!hasNotificationPermission()) return
        val callerId = data["caller_id"] ?: return
        val isVideo = data["is_video"]?.toBoolean() ?: false
        val callerName = data["caller_name"] ?: "Unknown"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("missed_call_from", callerId)
            putExtra("is_video", isVideo)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            callerId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, OpenChatApplication.CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Missed ${if (isVideo) "Video" else "Voice"} Call")
            .setContentText("Missed call from $callerName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(callerId.hashCode(), notification)
    }

    private fun handleReactionNotification(data: Map<String, String>) {
        val chatId = data["chat_id"] ?: return
        val senderName = data["sender_name"] ?: "Someone"
        val emoji = data["emoji"] ?: "\uD83D\uDC4D"
        val messageText = data["message"] ?: "a message"

        if (!hasNotificationPermission()) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", chatId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, chatId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, OpenChatApplication.CHANNEL_MENTIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText("Reacted $emoji to $messageText")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify("reaction_$chatId".hashCode(), notification)
    }
}
