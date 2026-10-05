package com.openchat.app.presentation.screens.settings

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCallSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE) }

    var messageNotifications by remember { mutableStateOf(prefs.getBoolean("message_notifications", true)) }
    var groupNotifications by remember { mutableStateOf(prefs.getBoolean("group_notifications", true)) }
    var callNotifications by remember { mutableStateOf(prefs.getBoolean("call_notifications", true)) }
    var showPreview by remember { mutableStateOf(prefs.getBoolean("show_preview", true)) }
    var isVibrate by remember { mutableStateOf(prefs.getBoolean("is_vibrate", true)) }
    var soundEnabled by remember { mutableStateOf(prefs.getBoolean("sound_enabled", true)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text("Message Notifications") },
                supportingContent = { Text("Show notifications for new messages") },
                leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = messageNotifications,
                        onCheckedChange = {
                            messageNotifications = it
                            prefs.edit().putBoolean("message_notifications", it).apply()
                        }
                    )
                }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Group Notifications") },
                supportingContent = { Text("Show notifications for group messages") },
                leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = groupNotifications,
                        onCheckedChange = {
                            groupNotifications = it
                            prefs.edit().putBoolean("group_notifications", it).apply()
                        }
                    )
                }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Call Notifications") },
                supportingContent = { Text("Show notifications for incoming calls") },
                leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = callNotifications,
                        onCheckedChange = {
                            callNotifications = it
                            prefs.edit().putBoolean("call_notifications", it).apply()
                        }
                    )
                }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Call Ringtone") },
                supportingContent = { Text("Choose ringtone for incoming calls") },
                leadingContent = { Icon(Icons.Default.RingVolume, contentDescription = null) },
                modifier = Modifier.clickable { onNavigateToCallSettings() }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Message Preview") },
                supportingContent = { Text("Show message content in notifications") },
                leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = showPreview,
                        onCheckedChange = {
                            showPreview = it
                            prefs.edit().putBoolean("show_preview", it).apply()
                        }
                    )
                }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Sound") },
                supportingContent = { Text(if (soundEnabled) "Enabled" else "Disabled") },
                leadingContent = { Icon(Icons.Default.VolumeUp, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = {
                            soundEnabled = it
                            prefs.edit().putBoolean("sound_enabled", it).apply()
                        }
                    )
                }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Vibrate") },
                supportingContent = { Text(if (isVibrate) "Enabled" else "Disabled") },
                leadingContent = { Icon(Icons.Default.Vibration, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = isVibrate,
                        onCheckedChange = {
                            isVibrate = it
                            prefs.edit().putBoolean("is_vibrate", it).apply()
                        }
                    )
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
