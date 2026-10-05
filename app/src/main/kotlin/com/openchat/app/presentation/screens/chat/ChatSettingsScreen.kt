package com.openchat.app.presentation.screens.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openchat.app.data.model.DisappearingTimer
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsScreen(
    chatId: String,
    onNavigateBack: () -> Unit,
    onNavigateToSharedMedia: () -> Unit = {},
    viewModel: ChatSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDisappearingDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        viewModel.loadChatSettings(chatId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Settings") },
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
            // Chat Header Info
            ChatHeaderInfo(uiState.chatTitle, uiState.chatPhotoUrl, uiState.participantCount)

            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // Disappearing Messages
            SettingsCategory("Privacy")

            ListItem(
                headlineContent = { Text("Disappearing Messages") },
                supportingContent = {
                    Text(
                        DisappearingTimer.entries.find { it.durationMs == uiState.disappearingTimer }?.displayName
                            ?: "Off"
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.Timer, contentDescription = null)
                },
                modifier = Modifier.clickable { showDisappearingDialog = true }
            )

            // Chat Wallpaper
            ListItem(
                headlineContent = { Text("Chat Wallpaper") },
                leadingContent = {
                    Icon(Icons.Default.Wallpaper, contentDescription = null)
                },
                trailingContent = {
                    if (uiState.wallpaperUrl != null) {
                        AsyncImage(
                            model = uiState.wallpaperUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                },
                modifier = Modifier.clickable { showWallpaperDialog = true }
            )

            // Theme Color
            ListItem(
                headlineContent = { Text("Theme Color") },
                leadingContent = {
                    Icon(Icons.Default.Palette, contentDescription = null)
                },
                trailingContent = {
                    ColorSelectorRow(
                        selectedColor = uiState.themeColor,
                        onColorSelected = { viewModel.setThemeColor(it) }
                    )
                }
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // Media & Files
            SettingsCategory("Media & Files")

            val ctx = androidx.compose.ui.platform.LocalContext.current
            ListItem(
                headlineContent = { Text("View Media, Links & Docs") },
                leadingContent = {
                    Icon(Icons.Default.Collections, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    onNavigateToSharedMedia()
                }
            )

            ListItem(
                headlineContent = { Text("Export Chat") },
                leadingContent = {
                    Icon(Icons.Default.Download, contentDescription = null)
                },
                modifier = Modifier.clickable { viewModel.exportChat() }
            )

            Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // Actions
            SettingsCategory("Actions")

            ListItem(
                headlineContent = { Text("Clear Chat", color = MaterialTheme.colorScheme.error) },
                leadingContent = {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.clickable { showClearChatDialog = true }
            )

            // Disappearing Messages Dialog
            if (showDisappearingDialog) {
                DisappearingMessagesDialog(
                    currentTimer = uiState.disappearingTimer,
                    onTimerSelected = { duration ->
                        viewModel.setDisappearingTimer(duration)
                        showDisappearingDialog = false
                    },
                    onDismiss = { showDisappearingDialog = false }
                )
            }

            // Clear Chat Dialog
            if (showClearChatDialog) {
                AlertDialog(
                    onDismissRequest = { showClearChatDialog = false },
                    title = { Text("Clear Chat") },
                    text = { Text("This will delete all messages in this chat. Messages will still be visible to other participants.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.clearChat()
                                showClearChatDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearChatDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatHeaderInfo(title: String, photoUrl: String?, participantCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "$participantCount participants",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsCategory(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ColorSelectorRow(
    selectedColor: String?,
    onColorSelected: (String) -> Unit
) {
    val colors = listOf(
        "default" to MaterialTheme.colorScheme.primary,
        "red" to Color(0xFFE53935),
        "pink" to Color(0xFFD81B60),
        "purple" to Color(0xFF8E24AA),
        "blue" to Color(0xFF1E88E5),
        "green" to Color(0xFF43A047),
        "orange" to Color(0xFFFB8C00)
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(colors) { (colorName, color) ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onColorSelected(colorName) }
                    .then(
                        if (selectedColor == colorName) {
                            Modifier.padding(2.dp)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selectedColor == colorName) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DisappearingMessagesDialog(
    currentTimer: Long,
    onTimerSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disappearing Messages") },
        text = {
            Column {
                Text(
                    "When enabled, new messages will disappear from this chat after the selected time.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                DisappearingTimer.entries.forEach { timer ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTimerSelected(timer.durationMs) }
                            .padding(vertical = 12.dp)
                    ) {
                        RadioButton(
                            selected = timer.durationMs == currentTimer,
                            onClick = { onTimerSelected(timer.durationMs) }
                        )
                        Text(
                            text = timer.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
