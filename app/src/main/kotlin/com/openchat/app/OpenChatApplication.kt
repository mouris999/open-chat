package com.openchat.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import com.openchat.app.R
import com.openchat.app.tauth.TAuthClient
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OpenChatApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initializeTAuth()
    }

    private fun initializeTAuth() {
        // Initialize T-Auth with your server configuration
        TAuthClient.getInstance(this).init(
            serverUrl = "http://10.0.2.2:3000", // Android emulator localhost
            clientId = "openchat_client_id", // Will be updated after client registration
            redirectUri = "tauth://callback"
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_CHANNELS_CREATED, false)) return

            val channels = listOf(
                NotificationChannel(
                    CHANNEL_MESSAGES,
                    getString(R.string.channel_messages_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = getString(R.string.channel_messages_description)
                    enableVibration(true)
                    setShowBadge(true)
                },
                NotificationChannel(
                    CHANNEL_CALLS,
                    getString(R.string.channel_calls_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = getString(R.string.channel_calls_description)
                    enableVibration(true)
                    setShowBadge(true)
                    val ringtoneUri = android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_RINGTONE
                    )
                    setSound(ringtoneUri, android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                },
                NotificationChannel(
                    CHANNEL_MENTIONS,
                    getString(R.string.channel_mentions_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = getString(R.string.channel_mentions_description)
                },
                NotificationChannel(
                    CHANNEL_SILENT,
                    getString(R.string.channel_silent_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.channel_silent_description)
                }
            )

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannels(channels)
            prefs.edit().putBoolean(KEY_CHANNELS_CREATED, true).apply()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    companion object {
        private const val PREFS_NAME = "notification_prefs"
        private const val KEY_CHANNELS_CREATED = "channels_created"

        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_CALLS = "calls"
        const val CHANNEL_MENTIONS = "mentions"
        const val CHANNEL_SILENT = "silent"
    }
}
