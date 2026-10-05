package com.openchat.app.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.openchat.app.domain.model.Story
import kotlinx.coroutines.delay

@Composable
fun StoryViewer(
    stories: List<Story>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onReact: (String, String) -> Unit,
    onReply: (String, String) -> Unit,
    onViewersClick: (String) -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    var isPaused by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    val currentStory = stories.getOrNull(currentIndex)
    
    val progressAnimatable = remember { Animatable(0f) }
    
    LaunchedEffect(currentIndex, isPaused) {
        if (!isPaused && currentIndex < stories.size) {
            progressAnimatable.snapTo(0f)
            progressAnimatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(5000, easing = LinearEasing)
            )
            // Auto advance when complete
            if (currentIndex < stories.size - 1) {
                currentIndex++
            } else {
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Story content
            currentStory?.let { story ->
                AsyncImage(
                    model = story.mediaUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                
                // Top bar with progress and user info
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Progress bars
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        stories.forEachIndexed { index, _ ->
                            LinearProgressIndicator(
                                progress = {
                                    when {
                                        index < currentIndex -> 1f
                                        index == currentIndex -> progressAnimatable.value
                                        else -> 0f
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(2.dp),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                    
                    // User info row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = story.userPhotoUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = story.userName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = formatTimeAgo(story.createdAt),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }
                
                // Caption
                if (!story.caption.isNullOrBlank()) {
                    Text(
                        text = story.caption,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .padding(bottom = 80.dp)
                    )
                }
                
                // Bottom actions
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // React bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("❤️", "🔥", "😂", "😮", "👏").forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 32.sp,
                                modifier = Modifier.clickable {
                                    onReact(story.id, emoji)
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Reply input
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { 
                            Text("Reply to ${story.userName}...", color = Color.White.copy(alpha = 0.7f)) 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                            focusedBorderColor = Color.White
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (replyText.isNotBlank()) {
                                        onReply(story.id, replyText)
                                    }
                                },
                                enabled = replyText.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = if (replyText.isNotBlank()) Color.White else Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    )
                }
                
                // Tap areas for navigation
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Left tap - previous
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        if (currentIndex > 0) {
                                            currentIndex--
                                        }
                                    },
                                    onPress = {
                                        isPaused = true
                                        tryAwaitRelease()
                                        isPaused = false
                                    }
                                )
                            }
                    )
                    
                    // Right tap - next
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        if (currentIndex < stories.size - 1) {
                                            currentIndex++
                                        } else {
                                            onDismiss()
                                        }
                                    },
                                    onPress = {
                                        isPaused = true
                                        tryAwaitRelease()
                                        isPaused = false
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> "${diff / 86400000}d ago"
    }
}
