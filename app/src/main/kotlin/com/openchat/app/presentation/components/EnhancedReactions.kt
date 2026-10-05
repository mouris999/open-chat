package com.openchat.app.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Popup
import androidx.compose.ui.layout.Layout

/**
 * Enhanced reactions with custom emoji support (Discord/Slack style)
 */
@Composable
fun EnhancedReactionsBar(
    reactions: Map<String, List<String>>, // emoji -> list of userIds
    currentUserId: String,
    onReactionClick: (String) -> Unit,
    onReactionLongPress: (String) -> Unit,
    onAddReaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedReactions = reactions.toList().sortedByDescending { it.second.size }
    
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(sortedReactions) { (emoji, users) ->
            val isSelected = users.contains(currentUserId)
            val count = users.size
            
            ReactionChip(
                emoji = emoji,
                count = count,
                isSelected = isSelected,
                onClick = { onReactionClick(emoji) },
                onLongPress = { onReactionLongPress(emoji) }
            )
        }
        
        item {
            AddReactionButton(onClick = onAddReaction)
        }
    }
}

@Composable
private fun ReactionChip(
    emoji: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    Surface(
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        shadowElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 16.sp)
            if (count > 1) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddReactionButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AddReaction,
                contentDescription = "Add reaction",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Emoji reaction picker popup
 */
@Composable
fun ReactionPicker(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val frequentlyUsed = listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "👏", "🎉")
    val allEmojis = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
        "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
        "👍", "👎", "👏", "🙌", "👐", "🤝", "🙏", "💪", "🤞", "✌️",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
        "🔥", "💯", "⭐", "✨", "⚡", "💫", "🎉", "🎊", "🎁", "🎈"
    )
    
    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "Frequently used",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Quick access emojis
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    frequentlyUsed.forEach { emoji ->
                        EmojiButton(
                            emoji = emoji,
                            onClick = { onEmojiSelected(emoji); onDismiss() }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // All emojis grid
                FlowRow(
                    modifier = Modifier.width(280.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    allEmojis.forEach { emoji ->
                        EmojiButton(
                            emoji = emoji,
                            onClick = { onEmojiSelected(emoji); onDismiss() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiButton(
    emoji: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.3f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "scale"
    )
    
    Box(
        modifier = modifier
            .size(36.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                isPressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 20.sp)
    }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(200)
            isPressed = false
        }
    }
}

/**
 * Custom reaction dialog for creating custom emoji reactions
 */
@Composable
fun CustomReactionDialog(
    onCustomReaction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Reaction") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search emoji") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Emoji grid would go here
                Text(
                    text = "Tap an emoji to react",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Animated reaction burst effect (when adding reaction)
 */
@Composable
fun ReactionBurstEffect(
    emoji: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val particles = remember { List(8) { it } }
    
    Box(modifier = modifier) {
        particles.forEach { index ->
            val angle = (index * 45).toFloat()
            val infiniteTransition = rememberInfiniteTransition(label = "burst")
            
            val offset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 50f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "offset"
            )
            
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "alpha"
            )
            
            val radians = Math.toRadians(angle.toDouble())
            val x = kotlin.math.cos(radians).toFloat() * offset
            val y = kotlin.math.sin(radians).toFloat() * offset
            
            Text(
                text = emoji,
                modifier = Modifier
                    .offset(x.dp, y.dp)
                    .alpha(alpha),
                fontSize = 20.sp
            )
        }
    }
    
    LaunchedEffect(Unit) {
        delay(800)
        onComplete()
    }
}

/**
 * FlowRow implementation for emoji grid
 */
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val hGapPx = 4.dp.roundToPx()
        val vGapPx = 4.dp.roundToPx()
        
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        val rowWidths = mutableListOf<Int>()
        val rowHeights = mutableListOf<Int>()
        
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0
        var currentRowHeight = 0
        
        measurables.forEach { measurable ->
            val placeable = measurable.measure(constraints)
            val gap = if (currentRow.isEmpty()) 0 else hGapPx
            
            if (currentRowWidth + placeable.width + gap > constraints.maxWidth) {
                rows.add(currentRow)
                rowWidths.add(currentRowWidth)
                rowHeights.add(currentRowHeight)
                currentRow = mutableListOf()
                currentRowWidth = 0
                currentRowHeight = 0
            }
            
            currentRow.add(placeable)
            currentRowWidth += placeable.width + if (currentRow.size == 1) 0 else hGapPx
            currentRowHeight = maxOf(currentRowHeight, placeable.height)
        }
        
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
            rowWidths.add(currentRowWidth)
            rowHeights.add(currentRowHeight)
        }
        
        val totalHeight = rowHeights.sum() + (rowHeights.size - 1) * vGapPx
        
        layout(
            width = constraints.maxWidth,
            height = totalHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        ) {
            var y = 0
            
            rows.forEachIndexed { rowIndex, row ->
                var x = 0
                
                row.forEachIndexed { index, placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + hGapPx
                }
                
                y += rowHeights[rowIndex] + vGapPx
            }
        }
    }
}
