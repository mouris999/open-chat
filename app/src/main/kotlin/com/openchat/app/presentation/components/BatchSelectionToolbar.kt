package com.openchat.app.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchSelectionToolbar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onForwardSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onPinSelected: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = selectedCount > 0,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClearSelection) {
                        Icon(Icons.Default.Close, contentDescription = "Clear selection")
                    }
                    
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Row {
                    // Pin action (if applicable)
                    IconButton(onClick = onPinSelected) {
                        Icon(Icons.Default.PushPin, contentDescription = "Pin")
                    }
                    
                    // Copy action
                    IconButton(onClick = onCopySelected) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                    
                    // Forward action
                    IconButton(onClick = onForwardSelected) {
                        Icon(Icons.Default.ForwardToInbox, contentDescription = "Forward")
                    }
                    
                    // Delete action
                    IconButton(onClick = onDeleteSelected) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SelectableMessageContainer(
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        // Selection indicator
        androidx.compose.animation.AnimatedVisibility(
            visible = isSelected,
            modifier = Modifier.align(androidx.compose.ui.Alignment.TopStart)
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(2.dp)
                )
            }
        }
        
        // Message content with selection background
        Surface(
            color = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (isSelected) 32.dp else 0.dp)
        ) {
            content()
        }
    }
}
