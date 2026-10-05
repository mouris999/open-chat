package com.openchat.app.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import coil.compose.AsyncImage

/**
 * Data class representing a user that can be mentioned
 */
data class MentionableUser(
    val id: String,
    val userName: String,
    val displayName: String,
    val photoUrl: String? = null
)

/**
 * Component for displaying text with clickable mentions and hashtags
 * Similar to Instagram and Telegram
 */
@Composable
fun MentionsText(
    text: String,
    onMentionClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit,
    onUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        val mentionPattern = "@([a-zA-Z0-9_]+)".toRegex()
        val hashtagPattern = "#([a-zA-Z0-9_]+)".toRegex()
        val urlPattern = "(https?://[^\\s]+)".toRegex()
        
        val allMatches = mutableListOf<Triple<Int, Int, MatchType>>()
        
        mentionPattern.findAll(text).forEach { match ->
            allMatches.add(Triple(match.range.first, match.range.last + 1, MatchType.MENTION))
        }
        
        hashtagPattern.findAll(text).forEach { match ->
            allMatches.add(Triple(match.range.first, match.range.last + 1, MatchType.HASHTAG))
        }
        
        urlPattern.findAll(text).forEach { match ->
            allMatches.add(Triple(match.range.first, match.range.last + 1, MatchType.URL))
        }
        
        allMatches.sortBy { it.first }
        
        var lastEnd = 0
        allMatches.forEach { (start, end, type) ->
            // Add text before match
            if (start > lastEnd) {
                append(text.substring(lastEnd, start))
            }
            
            // Add styled match
            val matchText = text.substring(start, end)
            val color = when (type) {
                MatchType.MENTION -> MaterialTheme.colorScheme.primary
                MatchType.HASHTAG -> MaterialTheme.colorScheme.tertiary
                MatchType.URL -> MaterialTheme.colorScheme.secondary
            }
            
            pushStringAnnotation(tag = type.name, annotation = matchText)
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium)) {
                append(matchText)
            }
            pop()
            
            lastEnd = end
        }
        
        // Add remaining text
        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }
    
    Text(
        text = annotatedString,
        style = style,
        modifier = modifier
    )
}

enum class MatchType {
    MENTION, HASHTAG, URL
}

/**
 * Text input field with mention suggestions (Instagram/Telegram style)
 */
@Composable
fun MentionsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    availableUsers: List<MentionableUser>,
    onUserSelected: (MentionableUser) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Type a message...",
    maxLines: Int = 5
) {
    var showSuggestions by remember { mutableStateOf(false) }
    var currentQuery by remember { mutableStateOf("") }
    var cursorPosition by remember { mutableIntStateOf(0) }
    
    // Detect when user types @ to show suggestions
    LaunchedEffect(value) {
        val lastAtIndex = value.lastIndexOf("@")
        if (lastAtIndex != -1 && lastAtIndex == value.length - 1) {
            showSuggestions = true
            currentQuery = ""
        } else if (lastAtIndex != -1) {
            val afterAt = value.substring(lastAtIndex + 1)
            val spaceIndex = afterAt.indexOf(" ")
            if (spaceIndex == -1) {
                currentQuery = afterAt
                showSuggestions = true
            } else {
                showSuggestions = false
            }
        } else {
            showSuggestions = false
        }
    }
    
    Box(modifier = modifier) {
        // Text field
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                cursorPosition = it.length
            },
            placeholder = { Text(placeholder) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Send
            ),
            maxLines = maxLines,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Mention suggestions popup
        if (showSuggestions && availableUsers.isNotEmpty()) {
            val filteredUsers = if (currentQuery.isBlank()) {
                availableUsers
            } else {
                availableUsers.filter { 
                    it.userName.contains(currentQuery, ignoreCase = true) ||
                    it.displayName.contains(currentQuery, ignoreCase = true)
                }
            }.take(5)
            
            if (filteredUsers.isNotEmpty()) {
                Popup(
                    alignment = androidx.compose.ui.Alignment.TopStart,
                    onDismissRequest = { showSuggestions = false }
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 56.dp)
                    ) {
                        LazyColumn {
                            items(filteredUsers) { user ->
                                UserMentionItem(
                                    user = user,
                                    onClick = {
                                        // Replace @query with @username
                                        val lastAtIndex = value.lastIndexOf("@")
                                        val newValue = value.substring(0, lastAtIndex + 1) + 
                                                      user.userName + " " +
                                                      value.substring(lastAtIndex + 1 + currentQuery.length)
                                        onValueChange(newValue)
                                        onUserSelected(user)
                                        showSuggestions = false
                                    }
                                )
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMentionItem(
    user: MentionableUser,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.photoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = user.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "@${user.userName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
