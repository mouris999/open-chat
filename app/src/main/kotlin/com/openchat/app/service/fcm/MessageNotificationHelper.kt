package com.openchat.app.service.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.openchat.app.OpenChatApplication
import com.openchat.app.R
import com.openchat.app.data.repository.ChatLockRepositoryImpl
import com.openchat.app.di.ChatLockPrefs
import com.openchat.app.presentation.MainActivity

object MessageNotificationHelper {

    fun showMessageNotification(context: Context, chatId: String, senderName: String, content: String) {
        val isChatLocked = isChatLocked(context, chatId)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", chatId)
            if (isChatLocked) {
                putExtra("requires_private_unlock", true)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, OpenChatApplication.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(chatId.hashCode(), notification)
    }

    fun showIncomingCallNotification(context: Context, callId: String, callerName: String, isVideo: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id", callId)
            putExtra("is_video", isVideo)
            putExtra("is_incoming", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            callId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, OpenChatApplication.CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("Incoming ${if (isVideo) "Video" else "Voice"} Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(callId.hashCode(), notification)
    }

    private fun isChatLocked(context: Context, chatId: String): Boolean {
        return try {
            val prefs = context.getSharedPreferences("chat_lock_prefs", Context.MODE_PRIVATE)
            val lockedChats = prefs.getStringSet("locked_chats", emptySet()) ?: emptySet()
            lockedChats.contains(chatId)
        } catch (e: Exception) {
            false
        }
    }
}
