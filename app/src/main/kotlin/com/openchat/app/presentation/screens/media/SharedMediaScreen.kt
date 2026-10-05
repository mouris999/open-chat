package com.openchat.app.presentation.screens.media

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.openchat.app.data.model.Message
import com.openchat.app.data.model.MessageType
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedMediaScreen(
    chatId: String,
    onNavigateBack: () -> Unit,
    onNavigateToMessage: (String) -> Unit,
    viewModel: SharedMediaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(chatId) {
        viewModel.loadMedia(chatId)
    }

    val tabs = listOf("Media", "Files", "Links", "Voice")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shared Content") },
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
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = pagerState.currentPage
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { Text(title) },
                        icon = {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.Image
                                    1 -> Icons.Default.InsertDriveFile
                                    2 -> Icons.Default.Link
                                    3 -> Icons.Default.Mic
                                    else -> Icons.Default.Image
                                },
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> MediaGrid(
                        media = uiState.mediaMessages,
                        onItemClick = { message ->
                            onNavigateToMessage(message.id)
                        }
                    )
                    1 -> FilesList(
                        files = uiState.fileMessages,
                        onItemClick = { message ->
                            onNavigateToMessage(message.id)
                        }
                    )
                    2 -> LinksList(
                        links = uiState.linkMessages,
                        onItemClick = { message ->
                            onNavigateToMessage(message.id)
                        }
                    )
                    3 -> VoiceList(
                        voiceMessages = uiState.voiceMessages,
                        onItemClick = { message ->
                            onNavigateToMessage(message.id)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
    media: List<Message>,
    onItemClick: (Message) -> Unit
) {
    if (media.isEmpty()) {
        EmptyContentView("No media shared yet")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp)
    ) {
        items(media, key = { it.id }) { message ->
            AsyncImage(
                model = message.content,
                contentDescription = null,
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(2.dp)
                    .clickable { onItemClick(message) },
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun FilesList(
    files: List<Message>,
    onItemClick: (Message) -> Unit
) {
    if (files.isEmpty()) {
        EmptyContentView("No files shared yet")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize()
    ) {
        items(files, key = { it.id }) { message ->
            ListItem(
                headlineContent = { 
                    Text(message.content.substringAfterLast("/").take(30)) 
                },
                supportingContent = { 
                    Text(message.timestamp.toFormattedDate()) 
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                },
                modifier = Modifier.clickable { onItemClick(message) }
            )
        }
    }
}

@Composable
private fun LinksList(
    links: List<Message>,
    onItemClick: (Message) -> Unit
) {
    if (links.isEmpty()) {
        EmptyContentView("No links shared yet")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize()
    ) {
        items(links, key = { it.id }) { message ->
            val url = extractUrl(message.content) ?: message.content
            ListItem(
                headlineContent = { 
                    Text(url.take(50), maxLines = 1) 
                },
                supportingContent = { 
                    Text("Shared by ${message.senderName}") 
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                },
                modifier = Modifier.clickable { onItemClick(message) }
            )
        }
    }
}

@Composable
private fun VoiceList(
    voiceMessages: List<Message>,
    onItemClick: (Message) -> Unit
) {
    if (voiceMessages.isEmpty()) {
        EmptyContentView("No voice messages yet")
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize()
    ) {
        items(voiceMessages, key = { it.id }) { message ->
            ListItem(
                headlineContent = { 
                    Text("Voice Message (${formatDuration(message.voiceDuration)})") 
                },
                supportingContent = { 
                    Text(message.timestamp.toFormattedDate()) 
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = if (message.isListened) 
                            MaterialTheme.colorScheme.onSurfaceVariant 
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.clickable { onItemClick(message) }
            )
        }
    }
}

@Composable
private fun EmptyContentView(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun extractUrl(text: String): String? {
    val urlRegex = "(https?://[^\\s]+)".toRegex()
    return urlRegex.find(text)?.value
}

private fun formatDuration(seconds: Int): String {
    return String.format("%d:%02d", seconds / 60, seconds % 60)
}

private fun Long.toFormattedDate(): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(this))
}
