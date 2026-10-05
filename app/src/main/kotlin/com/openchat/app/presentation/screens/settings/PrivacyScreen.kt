package com.openchat.app.presentation.screens.settings

import android.content.Context
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAppLock: () -> Unit,
    onNavigateToChatLock: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("privacy_settings", Context.MODE_PRIVATE) }
    val uid = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }
    val firestore = remember { com.google.firebase.firestore.FirebaseFirestore.getInstance() }

    var lastSeen by remember { mutableStateOf(prefs.getString("last_seen", "Everyone") ?: "Everyone") }
    var profilePhoto by remember { mutableStateOf(prefs.getString("profile_photo", "Everyone") ?: "Everyone") }
    var readReceipts by remember { mutableStateOf(prefs.getBoolean("read_receipts", true)) }
    var typingIndicator by remember { mutableStateOf(prefs.getBoolean("typing_indicator", true)) }

    var showLastSeenDialog by remember { mutableStateOf(false) }
    var showProfilePhotoDialog by remember { mutableStateOf(false) }

    fun updatePrivacySetting(key: String, value: Any) {
        if (uid != null) {
            scope.launch {
                try {
                    firestore.collection("users").document(uid)
                        .set(mapOf("privacy_$key" to value), com.google.firebase.firestore.SetOptions.merge())
                } catch (e: Exception) {
                    // Ignore remote failures
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
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
                headlineContent = { Text("Last Seen & Online") },
                supportingContent = { Text(lastSeen) },
                leadingContent = { Icon(Icons.Default.Visibility, contentDescription = null) },
                modifier = Modifier.clickable { showLastSeenDialog = true }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Profile Photo") },
                supportingContent = { Text(profilePhoto) },
                leadingContent = { Icon(Icons.Default.Visibility, contentDescription = null) },
                modifier = Modifier.clickable { showProfilePhotoDialog = true }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Read Receipts") },
                supportingContent = { Text("Show when you've read a message") },
                leadingContent = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = readReceipts,
                        onCheckedChange = {
                            readReceipts = it
                            prefs.edit().putBoolean("read_receipts", it).apply()
                            updatePrivacySetting("read_receipts", it)
                        }
                    )
                }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Typing Indicator") },
                supportingContent = { Text("Show when you're typing") },
                leadingContent = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = typingIndicator,
                        onCheckedChange = {
                            typingIndicator = it
                            prefs.edit().putBoolean("typing_indicator", it).apply()
                            updatePrivacySetting("typing_indicator", it)
                        }
                    )
                }
            )
            Divider()
            ListItem(
                headlineContent = { Text("App Lock") },
                supportingContent = { Text("Secure the app with a password") },
                leadingContent = { Icon(Icons.Default.Security, contentDescription = null) },
                modifier = Modifier.clickable { onNavigateToAppLock() }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Chat Lock") },
                supportingContent = { Text("Lock individual chats with a password") },
                leadingContent = { Icon(Icons.Default.Block, contentDescription = null) },
                modifier = Modifier.clickable { onNavigateToChatLock() }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Last Seen Dialog
    if (showLastSeenDialog) {
        PrivacyOptionDialog(
            title = "Last Seen & Online",
            currentOption = lastSeen,
            options = listOf("Everyone", "My Contacts", "Nobody"),
            onOptionSelected = { option ->
                lastSeen = option
                prefs.edit().putString("last_seen", option).apply()
                updatePrivacySetting("last_seen", option)
                showLastSeenDialog = false
            },
            onDismiss = { showLastSeenDialog = false }
        )
    }

    // Profile Photo Dialog
    if (showProfilePhotoDialog) {
        PrivacyOptionDialog(
            title = "Profile Photo",
            currentOption = profilePhoto,
            options = listOf("Everyone", "My Contacts", "Nobody"),
            onOptionSelected = { option ->
                profilePhoto = option
                prefs.edit().putString("profile_photo", option).apply()
                updatePrivacySetting("profile_photo", option)
                showProfilePhotoDialog = false
            },
            onDismiss = { showProfilePhotoDialog = false }
        )
    }
}

@Composable
private fun PrivacyOptionDialog(
    title: String,
    currentOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionSelected(option) }
                            .padding(vertical = 12.dp)
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = option == currentOption,
                            onClick = { onOptionSelected(option) }
                        )
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
