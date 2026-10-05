package com.openchat.app.presentation.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.tween
import com.openchat.app.presentation.theme.Motion
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ForwardToInbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.openchat.app.core.extensions.formatAsRelativeTime
import com.openchat.app.core.extensions.formatAsTime
import com.openchat.app.data.model.Message

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToChatSettings: (String) -> Unit,
    onNavigateToCall: (String, Boolean) -> Unit = { _, _ -> },
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replyToMessage by remember { mutableStateOf<Message?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }
    var voiceRecordingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var audioRecorder by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var hasRecordPermission by remember { mutableStateOf(false) }
    var forwardMessageId by remember { mutableStateOf<String?>(null) }

    // Voice recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                recordingDuration++
            }
        }
    }

    // Permission launcher for recording audio
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (!isGranted) {
            android.widget.Toast.makeText(context, "Microphone permission required for voice messages", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    var pendingCallIsVideo by remember { mutableStateOf<Boolean?>(null) }

    // Call permission launcher
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hasRecordPermission = true
            pendingCallIsVideo?.let { viewModel.onEvent(ChatEvent.StartCall(it)) }
        } else {
            android.widget.Toast.makeText(context, "Microphone permission required for calls", android.widget.Toast.LENGTH_LONG).show()
        }
        pendingCallIsVideo = null
    }

    var hasCameraPermission by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            android.widget.Toast.makeText(context, "Camera permission required to capture photos", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uri ->
                viewModel.onEvent(ChatEvent.SendImage(uri))
            }
        }
    }

    // Check initial permission state
    LaunchedEffect(Unit) {
        hasRecordPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // Cleanup recorder when leaving
    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) {
                try {
                    audioRecorder?.stop()
                    audioRecorder?.release()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onEvent(ChatEvent.SendImage(it)) }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onEvent(ChatEvent.SendVideo(it)) }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onEvent(ChatEvent.SendFile(it)) }
    }

    LaunchedEffect(chatId) {
        viewModel.loadChat(chatId)
    }

    // Handle effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ChatEffect.ShowError -> {
                    android.widget.Toast.makeText(context, effect.message, android.widget.Toast.LENGTH_LONG).show()
                }
                ChatEffect.ShowAttachmentPicker -> {
                    imagePicker.launch("image/*")
                }
                ChatEffect.ShowVideoPicker -> {
                    videoPicker.launch("video/*")
                }
                ChatEffect.ShowFilePicker -> {
                    filePicker.launch("*/*")
                }
                ChatEffect.ShowVoiceRecorder -> {
                    // Show voice recorder
                }
                is ChatEffect.NeedForwardTargetPicker -> {
                    forwardMessageId = effect.messageId
                }
                is ChatEffect.NavigateToCall -> {
                    onNavigateToCall(effect.userId, effect.isVideo)
                }
                // Successes get short, neutral toasts - they previously arrived
                // through ShowError and rendered with error styling.
                ChatEffect.ChatCleared -> {
                    android.widget.Toast.makeText(context, "Chat cleared", android.widget.Toast.LENGTH_SHORT).show()
                }
                ChatEffect.MessageForwarded -> {
                    android.widget.Toast.makeText(context, "Message forwarded", android.widget.Toast.LENGTH_SHORT).show()
                }
                ChatEffect.MessageScheduled -> {
                    android.widget.Toast.makeText(context, "Message scheduled", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            // Only auto-scroll when the user is already near the bottom. Yanking them
            // to the newest message while they are reading history is jarring, and it
            // fought with the list's own item-placement animation.
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val distanceFromBottom = listState.layoutInfo.totalItemsCount - 1 - lastVisible
            if (distanceFromBottom <= 4) {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }
    
    Scaffold(
        topBar = {
            if (isSearchMode) {
                TopAppBar(
                    title = {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search messages...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearchMode = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { 
                                uiState.chat?.participants?.firstOrNull { it != uiState.currentUserId }?.let { 
                                    onNavigateToProfile(it) 
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = uiState.chat?.title ?: "Chat",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (uiState.isTyping) "typing..." else if (uiState.isOnline) "online" else uiState.lastSeen?.let { "last seen ${it.formatAsRelativeTime()}" } ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.takeIf { uiState.isTyping } ?: MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        var showMenu by remember { mutableStateOf(false) }
                        
                        IconButton(onClick = { isSearchMode = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = {
                            if (hasRecordPermission) viewModel.onEvent(ChatEvent.StartCall(false))
                            else {
                                pendingCallIsVideo = false
                                callPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        }) {
                            Icon(Icons.Default.Call, contentDescription = "Voice Call")
                        }
                        IconButton(onClick = {
                            if (hasRecordPermission) viewModel.onEvent(ChatEvent.StartCall(true))
                            else {
                                pendingCallIsVideo = true
                                callPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                        }) {
                            Icon(Icons.Default.VideoCall, contentDescription = "Video Call")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Contact Info") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    uiState.chat?.participants?.firstOrNull { it != uiState.currentUserId }?.let { 
                                        onNavigateToProfile(it) 
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Chat Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val chatId = uiState.chat?.id
                                    if (!chatId.isNullOrBlank()) {
                                        onNavigateToChatSettings(chatId)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Chat") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.onEvent(ChatEvent.ClearChat)
                                }
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val filteredMessages = if (searchQuery.isNotBlank()) {
                uiState.messages.filter { it.content.contains(searchQuery, ignoreCase = true) }
            } else {
                uiState.messages
            }
            
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                reverseLayout = false
            ) {
                items(filteredMessages, key = { it.id }) { message ->
                    var showMenu by remember { mutableStateOf(false) }
                    var showEmojiPicker by remember { mutableStateOf(false) }

                    Box(Modifier.animateItemPlacement(tween(Motion.MEDIUM, easing = Motion.Decelerate))) {
                        SwipeableMessageBubble(
                            message = message,
                            isCurrentUser = message.senderId == uiState.currentUserId,
                            currentUserId = uiState.currentUserId,
                            onLongClick = { showMenu = true },
                            onReplyClick = { replyToMessage = message },
                            onSwipeToReply = { replyToMessage = message },
                            onReact = { messageId, emoji -> viewModel.onEvent(ChatEvent.ReactToMessage(messageId, emoji)) }
                        )
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Reply") },
                                leadingIcon = { Icon(Icons.Default.Reply, contentDescription = null) },
                                onClick = {
                                    replyToMessage = message
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("message", message.content)
                                    clipboard.setPrimaryClip(clip)
                                    showMenu = false
                                }
                            )
                            if (message.messageType == com.openchat.app.data.model.MessageType.TEXT) {
                                DropdownMenuItem(
                                    text = { Text("Forward") },
                                    leadingIcon = { Icon(Icons.Default.ForwardToInbox, contentDescription = null) },
                                    onClick = {
                                        viewModel.onEvent(ChatEvent.ForwardMessage(message.id))
                                        showMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("React") },
                                leadingIcon = { Icon(Icons.Default.EmojiEmotions, contentDescription = null) },
                                onClick = {
                                    showEmojiPicker = true
                                    showMenu = false
                                }
                            )
                            if (message.senderId == uiState.currentUserId && !message.isDeleted) {
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        viewModel.onEvent(ChatEvent.DeleteMessage(message.id))
                                        showMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Translate to English") },
                                onClick = {
                                    viewModel.onEvent(ChatEvent.TranslateMessage(message.id, "English"))
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Translate to Spanish") },
                                onClick = {
                                    viewModel.onEvent(ChatEvent.TranslateMessage(message.id, "Spanish"))
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Translate to Hindi") },
                                onClick = {
                                    viewModel.onEvent(ChatEvent.TranslateMessage(message.id, "Hindi"))
                                    showMenu = false
                                }
                            )
                        }
                        
                        // Emoji Picker Dialog
                        if (showEmojiPicker) {
                            EmojiPickerDialog(
                                onEmojiSelected = { emoji ->
                                    viewModel.onEvent(ChatEvent.AddReaction(message.id, emoji))
                                    showEmojiPicker = false
                                },
                                onDismiss = { showEmojiPicker = false }
                            )
                        }
                    }
                }
            }

            var showScheduleDialog by remember { mutableStateOf(false) }
            var pendingMessage by remember { mutableStateOf("") }

            MessageInputBar(
                replyToMessage = replyToMessage,
                onClearReply = { replyToMessage = null },
                isRecording = isRecording,
                recordingDuration = recordingDuration,
                onStartRecording = {
                    // Check/request permission first
                    if (!hasRecordPermission) {
                        recordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        return@MessageInputBar
                    }
                    // Start actual recording
                    val outputFile = java.io.File(context.cacheDir, "voice_${System.currentTimeMillis()}.3gp")
                    voiceRecordingUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        outputFile
                    )
                    // Create and configure recorder lazily
                    audioRecorder = android.media.MediaRecorder().apply {
                        setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                        setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP)
                        setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB)
                        setOutputFile(outputFile.absolutePath)
                        prepare()
                        start()
                    }
                    isRecording = true
                    recordingDuration = 0
                },
                onStopRecording = {
                    isRecording = false
                    val duration = recordingDuration
                    if (duration > 0) {
                        try {
                            audioRecorder?.stop()
                            audioRecorder?.release()
                            voiceRecordingUri?.let { uri ->
                                viewModel.onEvent(ChatEvent.SendVoiceMessage(uri, duration))
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Failed to send voice: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    audioRecorder = null
                    recordingDuration = 0
                    voiceRecordingUri = null
                },
                onSendMessage = { message ->
                    val reply = replyToMessage
                    if (reply != null) {
                        viewModel.onEvent(ChatEvent.SendReplyMessage(message, reply.id, reply.content))
                        replyToMessage = null
                    } else {
                        viewModel.onEvent(ChatEvent.SendMessage(message))
                    }
                },
                onScheduleMessage = { message ->
                    pendingMessage = message
                    showScheduleDialog = true
                },
                onAttachFile = {
                    viewModel.onEvent(ChatEvent.AttachFile)
                },
                onAttachVideo = {
                    viewModel.onEvent(ChatEvent.AttachVideo)
                },
                onAttachFileDoc = {
                    viewModel.onEvent(ChatEvent.AttachFileDoc)
                },
                onCapturePhoto = {
                    if (hasCameraPermission) {
                        val photoFile = java.io.File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                        photoUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile
                        )
                        photoUri?.let { takePictureLauncher.launch(it) }
                    } else {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    }
                },
                isLoading = uiState.isSending,
                modifier = Modifier.imePadding()
            )

            // Schedule Message Dialog
            if (showScheduleDialog) {
                ScheduleMessageDialog(
                    onSchedule = { scheduledTime ->
                        if (pendingMessage.isNotBlank()) {
                            viewModel.scheduleMessage(pendingMessage, scheduledTime, replyToMessage?.id)
                            pendingMessage = ""
                            replyToMessage = null
                        }
                        showScheduleDialog = false
                    },
                    onDismiss = { 
                        showScheduleDialog = false 
                        pendingMessage = ""
                    }
                )
            }

            // Forward chat picker dialog
            forwardMessageId?.let { msgId ->
                ForwardChatPickerDialog(
                    chatId = chatId,
                    currentUserId = uiState.currentUserId,
                    messagingRepository = viewModel.messagingRepository,
                    onForward = { targetChatId ->
                        viewModel.forwardToChat(msgId, targetChatId)
                        forwardMessageId = null
                    },
                    onDismiss = { forwardMessageId = null }
                )
            }
        }
    }
}

@Composable
private fun ForwardChatPickerDialog(
    chatId: String,
    currentUserId: String,
    messagingRepository: com.openchat.app.data.repository.MessagingRepository,
    onForward: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val chats = remember { mutableStateOf<List<com.openchat.app.data.model.Chat>>(emptyList()) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            val result = messagingRepository.getUserChats(currentUserId)
            chats.value = result.filter { it.id != chatId }
        } catch (e: Exception) {
            android.util.Log.w("ChatScreen", "Failed to load chats for forwarding", e)
        }
    }

    val options = chats.value
    var expanded by remember { mutableStateOf(true) }

    if (options.isEmpty()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Forward Message") },
            text = { Text("No other chats available.") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        )
    } else {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Forward to...") },
            text = {
                Column {
                    options.forEach { chat ->
                        ListItem(
                            headlineContent = { Text(chat.title ?: "Chat") },
                            modifier = Modifier.clickable {
                                onForward(chat.id)
                                expanded = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SwipeableMessageBubble(
    message: Message,
    isCurrentUser: Boolean,
    currentUserId: String,
    onLongClick: () -> Unit = {},
    onReplyClick: () -> Unit = {},
    onSwipeToReply: () -> Unit = {},
    onReact: (String, String) -> Unit = { _, _ -> }
) {
    val swipeThreshold = 80.dp
    var offsetX by remember { mutableStateOf(0f) }
    var isSwiping by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { isSwiping = true },
                    onDragEnd = {
                        if (kotlin.math.abs(offsetX) > swipeThreshold.toPx()) {
                            onSwipeToReply()
                        }
                        offsetX = 0f
                        isSwiping = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // Only allow swipe towards left for current user, right for others
                        val maxSwipe = swipeThreshold.toPx()
                        offsetX = (offsetX + dragAmount).coerceIn(-maxSwipe, maxSwipe)
                    }
                )
            }
    ) {
        // Background reply icon (shows when swiping)
        if (kotlin.math.abs(offsetX) > 20f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(if (offsetX > 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Reply,
                    contentDescription = "Reply",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Message bubble with offset
        Box(
            modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), 0) }
        ) {
            MessageBubble(
                message = message,
                isCurrentUser = isCurrentUser,
                currentUserId = currentUserId,
                onLongClick = onLongClick,
                onReplyClick = onReplyClick,
                onReact = onReact
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isCurrentUser: Boolean,
    currentUserId: String,
    onLongClick: () -> Unit = {},
    onReplyClick: () -> Unit = {},
    onReact: (String, String) -> Unit = { _, _ -> }
) {
    val bubbleColor = if (isCurrentUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    
    // Get unique reactions with counts
    val reactionCounts = message.reactions.values.groupingBy { it }.eachCount()

    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .combinedClickable(
                                onClick = {
                                    onReact(message.id, "👍")
                                },
                                onLongClick = onLongClick
                            ),
                        contentAlignment = alignment
                    ) {
        Column(horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isCurrentUser) 16.dp else 4.dp,
                    bottomEnd = if (isCurrentUser) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(containerColor = bubbleColor)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Show reply-to info if exists
                    if (message.replyToMessageId != null && message.replyToContent != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(bottom = 4.dp)
                                .clickable { onReplyClick() }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Replying to",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = message.replyToContent ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Show deleted message
                    if (message.isDeleted) {
                        Text(
                            text = "This message was deleted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    } else {
                        when (message.messageType) {
                            com.openchat.app.data.model.MessageType.TEXT -> {
                                Text(
                                    text = message.content,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            com.openchat.app.data.model.MessageType.IMAGE -> {
                                AsyncImage(
                                    model = message.content,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            com.openchat.app.data.model.MessageType.VOICE -> {
                                VoiceMessagePlayer(
                                    audioData = message.content,
                                    duration = message.voiceDuration ?: 0,
                                    isFromMe = isCurrentUser,
                                    modifier = Modifier.fillMaxWidth(0.8f)
                                )
                            }
                            com.openchat.app.data.model.MessageType.VIDEO -> {
                                Text(
                                    text = "[Video message]",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            com.openchat.app.data.model.MessageType.FILE -> {
                                Text(
                                    text = "[File: ${message.content.substringAfterLast("/")}]",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            com.openchat.app.data.model.MessageType.CALL -> {
                                Text(
                                    text = "[Call]",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            com.openchat.app.data.model.MessageType.POLL -> {
                                Text(
                                    text = "[Poll]",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            com.openchat.app.data.model.MessageType.CODE -> {
                                Text(
                                    text = "[Code snippet]",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            com.openchat.app.data.model.MessageType.STICKER -> {
                                Text(
                                    text = "[Sticker]",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            com.openchat.app.data.model.MessageType.LOCATION -> {
                                Text(
                                    text = "[Location]",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            com.openchat.app.data.model.MessageType.CONTACT -> {
                                Text(
                                    text = "[Contact]",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message.timestamp.formatAsTime(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isCurrentUser) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (message.isRead) Color.Blue else Color.Red)
                            )
                        }
                    }
                }
            }
            
            // Reactions row
            if (reactionCounts.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
                ) {
                    reactionCounts.forEach { (emoji, count) ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 2.dp,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(text = emoji, style = MaterialTheme.typography.bodySmall)
                                if (count > 1) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun MessageInputBar(
    replyToMessage: Message? = null,
    onClearReply: () -> Unit = {},
    isRecording: Boolean = false,
    recordingDuration: Int = 0,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onSendMessage: (String) -> Unit,
    onScheduleMessage: (String) -> Unit = {},
    onAttachFile: () -> Unit,
    onAttachVideo: () -> Unit,
    onAttachFileDoc: () -> Unit,
    onCapturePhoto: () -> Unit = {},
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var showAttachments by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        // Recording indicator
        if (isRecording) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recording ${recordingDuration}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        // Reply preview
        if (replyToMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replying to",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = replyToMessage.content.take(50),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onClearReply) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel reply")
                    }
                }
            }
        }
        
        if (showAttachments) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                AttachmentOption(
                    icon = Icons.Default.Image,
                    label = "Photo",
                    onClick = { 
                        onAttachFile()
                        showAttachments = false
                    }
                )
                AttachmentOption(
                    icon = Icons.Default.VideoLibrary,
                    label = "Video",
                    onClick = { 
                        onAttachVideo()
                        showAttachments = false
                    }
                )
                AttachmentOption(
                    icon = Icons.Default.InsertDriveFile,
                    label = "File",
                    onClick = { 
                        onAttachFileDoc()
                        showAttachments = false
                    }
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showAttachments = !showAttachments }) {
                    Icon(
                        imageVector = if (showAttachments) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Attachments"
                    )
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                text = if (replyToMessage != null) "Reply..." else "Message",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                )
            }
            
            // Camera capture button
            IconButton(
                onClick = onCapturePhoto,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Take Photo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Send or Voice Record button
            if (text.isBlank() && replyToMessage == null) {
                // Voice recording button when no text
                IconButton(
                    onClick = {
                        if (isRecording) {
                            onStopRecording()
                        } else {
                            onStartRecording()
                        }
                    },
                    modifier = Modifier.background(
                        color = if (isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    )
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "Stop Recording" else "Record Voice",
                        tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                // Send button when has text - with long press for scheduling
                var showScheduleHint by remember { mutableStateOf(false) }
                
                Box {
                    val combinedClickModifier = @OptIn(ExperimentalFoundationApi::class) Modifier.combinedClickable(
                        onClick = {
                            if (text.isNotBlank()) {
                                onSendMessage(text)
                                text = ""
                            }
                        },
                        onLongClick = {
                            if (text.isNotBlank()) {
                                onScheduleMessage(text)
                            }
                        },
                        onLongClickLabel = "Schedule Message"
                    )
                    IconButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                onSendMessage(text)
                                text = ""
                            }
                        },
                        modifier = combinedClickModifier,
                        enabled = !isLoading && text.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send (Long-press to schedule)",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
}
@Composable
private fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmojiPickerDialog(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val commonEmojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🔥", "👏", "🎉", "😍", "🤔", "👎")
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Add Reaction",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    commonEmojis.take(6).forEach { emoji ->
                        TextButton(
                            onClick = { onEmojiSelected(emoji) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Text(text = emoji, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    commonEmojis.drop(6).forEach { emoji ->
                        TextButton(
                            onClick = { onEmojiSelected(emoji) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Text(text = emoji, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ScheduleMessageDialog(
    onSchedule: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis() + 3600000) } // Default to 1 hour from now
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Message") },
        text = {
            Column {
                Text(
                    text = "Select when to send this message:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Quick options
                val quickOptions = listOf(
                    "1 Hour" to 3600000L,
                    "2 Hours" to 7200000L,
                    "Tomorrow" to 86400000L,
                    "Next Week" to 604800000L
                )
                
                quickOptions.forEach { (label, delay) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        modifier = Modifier.clickable {
                            onSchedule(System.currentTimeMillis() + delay)
                        }
                    )
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Custom date/time
                ListItem(
                    headlineContent = { Text("Custom Time") },
                    supportingContent = { Text(dateFormat.format(java.util.Date(selectedDate))) },
                    leadingContent = {
                        Icon(Icons.Default.EditCalendar, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        showDatePicker = true
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSchedule(selectedDate) }
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    
    // Simple date picker simulation (using a text field for now)
    if (showDatePicker) {
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("Select Time") },
            text = {
                Column {
                    val timeOptions = listOf(
                        "In 5 minutes" to 300000L,
                        "In 15 minutes" to 900000L,
                        "In 30 minutes" to 1800000L,
                        "In 1 hour" to 3600000L,
                        "In 3 hours" to 10800000L,
                        "In 6 hours" to 21600000L,
                        "Tomorrow morning" to 86400000L + 28800000L // 8am next day
                    )
                    
                    timeOptions.forEach { (label, delay) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            modifier = Modifier.clickable {
                                selectedDate = System.currentTimeMillis() + delay
                                showDatePicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun VoiceMessagePlayer(
    audioData: String,
    duration: Int,
    isFromMe: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var currentPosition by remember { mutableStateOf(0) }

    // Format duration
    val durationText = remember(duration) {
        val minutes = duration / 60
        val seconds = duration % 60
        "${minutes}:${seconds.toString().padStart(2, '0')}"
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    // Update progress while playing
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        currentPosition = player.currentPosition / 1000
                        progress = if (duration > 0) {
                            player.currentPosition.toFloat() / (duration * 1000)
                        } else 0f
                    } else {
                        isPlaying = false
                        progress = 0f
                        currentPosition = 0
                    }
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    fun playAudio() {
        try {
            // Release any existing player
            mediaPlayer?.release()

            mediaPlayer = android.media.MediaPlayer().apply {
                if (audioData.startsWith("data:")) {
                    // Base64 data URI
                    val base64Data = audioData.substringAfter("base64,")
                    val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    val tempFile = java.io.File.createTempFile("voice", ".3gp", context.cacheDir)
                    tempFile.writeBytes(bytes)
                    setDataSource(tempFile.absolutePath)
                } else {
                    // Regular URL (from Firebase Storage)
                    setDataSource(audioData)
                }
                prepare()
                start()
                isPlaying = true

                setOnCompletionListener {
                    isPlaying = false
                    progress = 0f
                    currentPosition = 0
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VoicePlayer", "Failed to play audio", e)
            isPlaying = false
        }
    }

    fun pauseAudio() {
        mediaPlayer?.pause()
        isPlaying = false
    }

    fun togglePlay() {
        if (isPlaying) {
            pauseAudio()
        } else {
            playAudio()
        }
    }

    val currentTimeText = remember(currentPosition) {
        val minutes = currentPosition / 60
        val seconds = currentPosition % 60
        "${minutes}:${seconds.toString().padStart(2, '0')}"
    }

    val contentColor = if (isFromMe) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isFromMe) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Play/Pause button
        IconButton(
            onClick = { togglePlay() },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
        }

        // Progress bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(contentColor.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(contentColor)
            )
        }

        // Duration text
        Text(
            text = if (isPlaying) currentTimeText else durationText,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor
        )

        // Mic icon
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
    }
}
