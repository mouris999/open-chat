package com.openchat.app.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Reminder message component (Telegram/WhatsApp style)
 * Allows setting reminders on messages
 */
@Composable
fun ReminderMessage(
    reminderTime: Long,
    reminderText: String,
    isCompleted: Boolean,
    onComplete: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val timeString = dateFormat.format(Date(reminderTime))
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted)
                MaterialTheme.colorScheme.surfaceVariant
            else
                Color(0xFFFF9800).copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reminder icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCompleted) "Reminder completed" else "Reminder set",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Text(
                    text = reminderText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCompleted)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        Color(0xFFFF9800)
                )
            }
            
            if (!isCompleted) {
                IconButton(onClick = onComplete) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Complete",
                        tint = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

/**
 * Set reminder dialog
 */
@Composable
fun SetReminderDialog(
    messageContent: String,
    onReminderSet: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOption by remember { mutableIntStateOf(0) }
    var customTime by remember { mutableStateOf("") }
    var customNote by remember { mutableStateOf("") }
    
    val quickOptions = listOf(
        Triple("In 30 minutes", 30, Icons.Default.Timer),
        Triple("In 1 hour", 60, Icons.Default.Schedule),
        Triple("Tomorrow", 24 * 60, Icons.Default.Today),
        Triple("Next week", 7 * 24 * 60, Icons.Default.CalendarMonth)
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Reminder") },
        text = {
            Column {
                // Message preview
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "\"${messageContent.take(100)}${if (messageContent.length > 100) "..." else ""}\"",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                // Quick options
                quickOptions.forEachIndexed { index, (label, minutes, icon) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = index }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == index,
                            onClick = { selectedOption = index }
                        )
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(label)
                    }
                }
                
                // Custom note
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customNote,
                    onValueChange = { customNote = it },
                    label = { Text("Add note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val (_, minutes, _) = quickOptions[selectedOption]
                    val reminderTime = System.currentTimeMillis() + (minutes * 60 * 1000)
                    onReminderSet(reminderTime, customNote.ifBlank { "Reminder for message" })
                }
            ) {
                Text("Set Reminder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Reminder list screen
 */
@Composable
fun RemindersList(
    reminders: List<ReminderItem>,
    onComplete: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(reminders.sortedBy { it.time }) { reminder ->
            val isOverdue = reminder.time < System.currentTimeMillis() && !reminder.isCompleted
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        reminder.isCompleted -> MaterialTheme.colorScheme.surfaceVariant
                        isOverdue -> Color(0xFFF44336).copy(alpha = 0.1f)
                        else -> Color(0xFFFF9800).copy(alpha = 0.15f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    reminder.isCompleted -> Color(0xFF4CAF50)
                                    isOverdue -> Color(0xFFF44336)
                                    else -> Color(0xFFFF9800)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                reminder.isCompleted -> Icons.Default.Check
                                isOverdue -> Icons.Default.Warning
                                else -> Icons.Default.Notifications
                            },
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reminder.note,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                        Text(
                            text = dateFormat.format(Date(reminder.time)),
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isOverdue -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        if (isOverdue) {
                            Text(
                                text = "Overdue",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF44336)
                            )
                        }
                    }
                    
                    if (!reminder.isCompleted) {
                        IconButton(onClick = { onComplete(reminder.id) }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Complete",
                                tint = Color(0xFF4CAF50)
                            )
                        }
                    }
                    
                    IconButton(onClick = { onDelete(reminder.id) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

data class ReminderItem(
    val id: String,
    val messageId: String,
    val time: Long,
    val note: String,
    val isCompleted: Boolean = false
)
