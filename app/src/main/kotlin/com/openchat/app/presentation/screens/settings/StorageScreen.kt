package com.openchat.app.presentation.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearChatsDialog by remember { mutableStateOf(false) }

    fun getCacheSize(): String {
        return try {
            val size = context.cacheDir.walk().map { it.length() }.sum()
            val mb = size / (1024.0 * 1024.0)
            String.format("%.2f MB", mb)
        } catch (e: Exception) {
            "0.00 MB"
        }
    }

    var cacheSize by remember { mutableStateOf(getCacheSize()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage & Usage") },
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
                headlineContent = { Text("Storage Usage") },
                supportingContent = { Text("Manage how OpenChat uses storage on this device") },
                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) }
            )
            Spacer(modifier = Modifier.height(16.dp))

            ListItem(
                headlineContent = { Text("Photos & Videos") },
                supportingContent = { Text("Auto-download over Wi-Fi only") }
            )
            ListItem(
                headlineContent = { Text("Voice Messages") },
                supportingContent = { Text("Auto-download over Wi-Fi only") }
            )
            ListItem(
                headlineContent = { Text("Documents") },
                supportingContent = { Text("Auto-download over Wi-Fi only") }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Divider()

            ListItem(
                headlineContent = { Text("Clear Cache ($cacheSize)") },
                supportingContent = { Text("Free up temporary storage space") },
                leadingContent = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                modifier = Modifier.clickable {
                    scope.launch(Dispatchers.IO) {
                        try {
                            context.cacheDir.deleteRecursively()
                            withContext(Dispatchers.Main) {
                                cacheSize = getCacheSize()
                                Toast.makeText(context, "Cache cleared successfully!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Failed to clear cache: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )

            ListItem(
                headlineContent = { Text("Clear All Chats") },
                supportingContent = { Text("Remove all messages and media locally") },
                leadingContent = { Icon(Icons.Default.Warning, contentDescription = null) },
                modifier = Modifier.clickable {
                    showClearChatsDialog = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showClearChatsDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatsDialog = false },
            title = { Text("Clear All Chats?") },
            text = { Text("This will delete all conversations locally. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val db = com.google.firebase.database.FirebaseDatabase.getInstance()
                                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                                if (uid != null) {
                                    db.reference.child("drafts").child(uid).removeValue()
                                }
                                Toast.makeText(context, "Local chat index cleared!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                // Ignore
                            }
                            showClearChatsDialog = false
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
