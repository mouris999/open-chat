package com.openchat.app.presentation

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.openchat.app.presentation.navigation.OpenChatNavHost
import com.openchat.app.presentation.theme.OpenChatTheme
import com.openchat.app.presentation.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeManager: ThemeManager

    private val pendingCallId = mutableStateOf<String?>(null)
    private val pendingCallerId = mutableStateOf<String?>(null)
    private val pendingIsVideo = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        updatePendingCallFromIntent(intent)

        setContent {
            val isDarkMode by themeManager.isDarkMode.collectAsState()
            val fontSize by themeManager.fontSize.collectAsState()
            androidx.compose.foundation.isSystemInDarkTheme().let { sysDark -> LaunchedEffect(sysDark){ themeManager.refreshSystemTheme(sysDark) } }

            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density * fontSize.scale, 1f)) {
            OpenChatTheme(darkTheme = isDarkMode) {
                var showNotificationDialog by remember { mutableStateOf(false) }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (!isGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        android.util.Log.d("MainActivity", "Notification permission denied")
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (!hasPermission) {
                            showNotificationDialog = true
                        }
                    }
                }

                if (showNotificationDialog) {
                    AlertDialog(
                        onDismissRequest = { showNotificationDialog = false },
                        title = { Text("Enable Notifications") },
                        text = { Text("OpenChat needs notification permissions to alert you when you receive new messages and calls.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showNotificationDialog = false
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }) {
                                Text("Allow")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showNotificationDialog = false
                            }) {
                                Text("Not Now")
                            }
                        }
                    )
                }

                OpenChatNavHost(
                    pendingCallId = pendingCallId.value,
                    pendingCallerId = pendingCallerId.value,
                    pendingIsVideo = pendingIsVideo.value
                )
            } }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        updatePendingCallFromIntent(intent)
    }

    private fun updatePendingCallFromIntent(intent: android.content.Intent?) {
        val callId = intent?.getStringExtra("call_id")
        val callerId = intent?.getStringExtra("caller_id")
        if (callId != null && callerId != null) {
            pendingCallId.value = callId
            pendingCallerId.value = callerId
            pendingIsVideo.value = intent.getBooleanExtra("is_video", false)
        }
    }

    fun getDeviceLinkToken(intent: android.content.Intent?): String? {
        val data = intent?.data
        return if (data != null && data.scheme == "openchat" && data.host == "link") data.getQueryParameter("token") else null
    }
}
