package com.openchat.app.presentation.screens.chat

import androidx.lifecycle.viewModelScope
import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import android.net.Uri
import com.openchat.app.data.model.Chat
import com.openchat.app.data.model.Message
import com.openchat.app.data.model.MessageType
import com.openchat.app.data.repository.MessagingRepository
import com.openchat.app.domain.repository.UserRepository
import com.openchat.app.domain.repository.CallRepository
import com.openchat.app.webrtc.FcmSender
import com.openchat.app.data.ai.AiEngine
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ChatState(
    val chatId: String? = null,
    val chat: com.openchat.app.data.model.Chat? = null,
    val messages: List<com.openchat.app.data.model.Message> = emptyList(),
    val currentUserId: String = "",
    val isOnline: Boolean = false,
    val isTyping: Boolean = false,
    val lastSeen: Long? = null,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null
) : UiState

sealed class ChatEvent : UiEvent {
    data class SendMessage(val content: String) : ChatEvent()
    data class SendReplyMessage(val content: String, val replyToId: String, val replyToContent: String) : ChatEvent()
    data class SendImage(val uri: Uri) : ChatEvent()
    data class SendVideo(val uri: Uri) : ChatEvent()
    data class SendFile(val uri: Uri) : ChatEvent()
    data object AttachFile : ChatEvent()
    data object AttachVideo : ChatEvent()
    data object AttachFileDoc : ChatEvent()
    data object RecordVoice : ChatEvent()
    data class SendVoiceMessage(val uri: Uri, val duration: Int) : ChatEvent()
    data class TranslateMessage(val messageId: String, val targetLanguage: String) : ChatEvent()
    data class DeleteMessage(val messageId: String) : ChatEvent()
    data class AddReaction(val messageId: String, val emoji: String) : ChatEvent()
    data class ReactToMessage(val messageId: String, val emoji: String) : ChatEvent()
    data class ForwardMessage(val messageId: String) : ChatEvent()
    data class StartCall(val isVideo: Boolean) : ChatEvent()
    data object ClearChat : ChatEvent()
}

sealed class ChatEffect : UiEffect {
    data object ShowAttachmentPicker : ChatEffect()
    data object ShowVideoPicker : ChatEffect()
    data object ShowFilePicker : ChatEffect()
    data object ShowVoiceRecorder : ChatEffect()
    data class NeedForwardTargetPicker(val messageId: String) : ChatEffect()
    data class NavigateToCall(val userId: String, val isVideo: Boolean) : ChatEffect()
    data class ShowError(val message: String) : ChatEffect()

    // Success outcomes get their own effects. These three previously travelled
    // through ShowError, so callers rendering error-styled feedback treated a
    // successful clear/forward/schedule as a failure.
    data object ChatCleared : ChatEffect()
    data object MessageForwarded : ChatEffect()
    data object MessageScheduled : ChatEffect()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    val messagingRepository: MessagingRepository,
    private val userRepository: UserRepository,
    private val callRepository: CallRepository,
    private val storage: FirebaseStorage,
    private val aiEngine: AiEngine,
    private val application: android.app.Application,
    private val cloudinaryRepository: com.openchat.app.data.repository.CloudinaryRepository
) : BaseViewModel<ChatState, ChatEvent, ChatEffect>() {
    
    override val _uiState = MutableStateFlow(ChatState())

    /**
     * Collectors started by [loadChat]. ChatScreen dispatches LoadChat from a
     * LaunchedEffect(chatId) and this ViewModel survives across chats on the back
     * stack, so without cancelling the previous jobs the old chat's message and
     * typing observers kept writing into the same state alongside the new chat's.
     */
    private val chatJobs = mutableListOf<kotlinx.coroutines.Job>()

    fun loadChat(chatId: String) {
        chatJobs.forEach { it.cancel() }
        chatJobs.clear()
        val currentUserId = firebaseAuth.currentUser?.uid ?: ""
        setState { copy(chatId = chatId, currentUserId = currentUserId, isLoading = true) }
        android.util.Log.d("ChatViewModel", "loadChat: $chatId, currentUser: $currentUserId")

        // Resolved display names, cached across emissions. Previously every emission
        // of the chat list (i.e. every incoming message in any chat) kicked off N
        // sequential getUser() calls - each potentially a Firestore round trip -
        // before the list could be drawn.
        val titleCache = mutableMapOf<String, String>()

        chatJobs += viewModelScope.launch {
            try {
                // Observe chat metadata
                messagingRepository.getChat(chatId)
                    .onEach { chat ->
                        var updatedChat = chat
                        if (chat != null && chat.title == null) {
                            val otherUserId = chat.participants.firstOrNull { it != currentUserId }
                            if (otherUserId != null) {
                                val cached = titleCache[otherUserId]
                                if (cached != null) {
                                    updatedChat = chat.copy(title = cached)
                                } else {
                                    updatedChat = chat.copy(
                                        title = "User ${otherUserId.take(4)}"
                                    )
                                    // Resolve in the background so the header shows a
                                    // placeholder immediately instead of blocking.
                                    viewModelScope.launch {
                                        // One call, not two: the previous code invoked
                                        // getUser() again for the username fallback.
                                        val user = userRepository.getUser(otherUserId).getOrNull()
                                        val name = user?.displayName ?: user?.username
                                        if (name != null) {
                                            titleCache[otherUserId] = name
                                            // Only apply if still looking at this chat
                                            // and the title has not been set meanwhile.
                                            val current = _uiState.value
                                            val c = current.chat
                                            if (c != null && c.id == chatId && c.title == null) {
                                                setState { copy(chat = c.copy(title = name)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        setState { copy(chat = updatedChat, isLoading = false) }
                    }
                    .catch { e ->
                        setState { copy(isLoading = false, error = e.message) }
                        sendEffect(ChatEffect.ShowError(e.message ?: "Failed to load chat"))
                    }
                    .collect()

                // Presence for the other participant. The header rendered
                // isOnline/lastSeen but nothing ever assigned them, so the indicator
                // was permanently blank even though the repository exposes both.
                observePresence(currentUserId)

                // Mark messages as read. Also re-marked on every new message in
                // observeMessages: marking only here meant messages arriving while
                // the chat was open kept the unread badge on the conversation.
                runCatching { messagingRepository.markMessagesAsRead(chatId, currentUserId) }
                    .onFailure { android.util.Log.w("ChatViewModel", "markAsRead failed", it) }
            } catch (e: Exception) {
                setState { copy(isLoading = false, error = e.message) }
            }
        }

        // Observe messages
        chatJobs += viewModelScope.launch { observeMessages(chatId) }

        // Observe typing status
        chatJobs += viewModelScope.launch { observeTypingStatus(chatId, currentUserId) }
    }
    
    /**
     * Clears the unread badge for messages that arrived while the chat is open.
     * Called on every emission, not just once at load time.
     */
    private fun markIncomingAsRead(
        chatId: String,
        messages: List<com.openchat.app.data.model.Message>
    ) {
        val currentUserId = _uiState.value.currentUserId
        if (currentUserId.isBlank()) return
        val hasUnreadIncoming = messages.any { it.senderId != currentUserId && !it.isRead }
        if (!hasUnreadIncoming) return
        viewModelScope.launch {
            runCatching { messagingRepository.markMessagesAsRead(chatId, currentUserId) }
                .onFailure { android.util.Log.w("ChatViewModel", "markAsRead failed", it) }
        }
    }

    /**
     * Mirrors the other participant's online / last-seen status into state so the
     * chat header can render a presence indicator.
     */
    private suspend fun observePresence(currentUserId: String) {
        val otherUserId = _uiState.value.chat?.participants
            ?.firstOrNull { it != currentUserId }
            ?: return
        if (otherUserId.isBlank()) return

        combine(
            userRepository.observeUserOnlineStatus(otherUserId),
            userRepository.observeUserLastSeen(otherUserId)
        ) { online, lastSeen -> online to lastSeen }
            .catch { e -> android.util.Log.w("ChatViewModel", "presence observe failed", e) }
            .collect { (online, lastSeen) ->
                setState { copy(isOnline = online, lastSeen = lastSeen) }
            }
    }

    private suspend fun observeMessages(chatId: String) {
        android.util.Log.d("ChatViewModel", "Observing messages for chat: $chatId")
        messagingRepository.getMessages(chatId)
            .onEach { messages ->
                // If chat has no participants, try to reconstruct from message senders
                val currentChat = _uiState.value.chat
                val currentUserId = _uiState.value.currentUserId
                if (currentChat != null && currentChat.participants.isEmpty() && messages.isNotEmpty()) {
                    val senderIds = messages.map { it.senderId }.distinct()
                    val reconstructedParticipants = (senderIds + currentUserId).distinct()

                    android.util.Log.d("ChatViewModel", "Reconstructing participants from messages: $reconstructedParticipants")

                    val updatedChat = currentChat.copy(participants = reconstructedParticipants)
                    setState { copy(chat = updatedChat, messages = messages) }

                    // Persist the fix to Firebase
                    runCatching { messagingRepository.fixChatParticipants(chatId, reconstructedParticipants) }
                        .onFailure { android.util.Log.e("ChatViewModel", "Failed to persist chat fix", it) }
                } else {
                    setState { copy(messages = messages) }
                }
                markIncomingAsRead(chatId, messages)
            }
            .catch { e ->
                android.util.Log.e("ChatViewModel", "Error observing messages for chat $chatId: ${e.message}")
                sendEffect(ChatEffect.ShowError(e.message ?: "Failed to load messages"))
            }
            .collect()
    }

    private suspend fun observeTypingStatus(chatId: String, currentUserId: String) {
        messagingRepository.observeTypingStatus(chatId, currentUserId)
            .onEach { typingUsers ->
                setState { copy(isTyping = typingUsers.isNotEmpty()) }
            }
            .catch { e -> android.util.Log.e("ChatVM", "Typing observation error", e) }
            .collect()
    }
    
    override fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.SendMessage -> sendMessage(event.content)
            is ChatEvent.SendReplyMessage -> sendReplyMessage(event.content, event.replyToId, event.replyToContent)
            is ChatEvent.SendImage -> sendImage(event.uri)
            is ChatEvent.SendVideo -> sendVideo(event.uri)
            is ChatEvent.SendFile -> sendFile(event.uri)
            ChatEvent.AttachFile -> sendEffect(ChatEffect.ShowAttachmentPicker)
            ChatEvent.AttachVideo -> sendEffect(ChatEffect.ShowVideoPicker)
            ChatEvent.AttachFileDoc -> sendEffect(ChatEffect.ShowFilePicker)
            ChatEvent.RecordVoice -> sendEffect(ChatEffect.ShowVoiceRecorder)
            is ChatEvent.TranslateMessage -> translateMessage(event.messageId, event.targetLanguage)
            is ChatEvent.DeleteMessage -> deleteMessage(event.messageId)
            // AddReaction and ReactToMessage were two events for one action, with
            // different screens using different ones. Collapsed to a single path so
            // the event layer is unambiguous.
            is ChatEvent.AddReaction -> addReaction(event.messageId, event.emoji)
            is ChatEvent.ReactToMessage -> addReaction(event.messageId, event.emoji)
            is ChatEvent.StartCall -> startCall(event.isVideo)
            is ChatEvent.SendVoiceMessage -> sendVoiceMessage(event.uri, event.duration)
            is ChatEvent.ForwardMessage -> forwardMessage(event.messageId)
            ChatEvent.ClearChat -> clearChat()
        }
    }

    private fun clearChat() {
        val chatId = _uiState.value.chatId ?: return
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                messagingRepository.clearChatForUser(chatId, currentUserId)
                sendEffect(ChatEffect.ChatCleared)
            } catch (e: Exception) {
                sendEffect(ChatEffect.ShowError("Failed to clear chat: ${e.message}"))
            }
        }
    }
    
    private fun forwardMessage(messageId: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        sendEffect(ChatEffect.NeedForwardTargetPicker(messageId))
    }

    fun forwardToChat(messageId: String, targetChatId: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        val currentUserName = firebaseAuth.currentUser?.displayName ?: "User"

        viewModelScope.launch {
            try {
                val forwardMsg = Message(
                    id = "fwd_${System.currentTimeMillis()}",
                    senderId = currentUserId,
                    senderName = currentUserName,
                    content = message.content,
                    messageType = message.messageType,
                    timestamp = System.currentTimeMillis(),
                    replyToMessageId = null,
                    replyToContent = "Forwarded: ${message.content.take(50)}..."
                )
                messagingRepository.sendMessage(targetChatId, forwardMsg)
                sendEffect(ChatEffect.MessageForwarded)
            } catch (e: Exception) {
                sendEffect(ChatEffect.ShowError("Failed to forward: ${e.message}"))
            }
        }
    }
    
    private fun sendMessage(content: String) {
        if (content.isBlank()) return
        val chatId = _uiState.value.chatId ?: return

        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        val currentUserName = firebaseAuth.currentUser?.displayName ?: "User"

        val message = Message(
            id = System.currentTimeMillis().toString(),
            senderId = currentUserId,
            senderName = currentUserName,
            content = content,
            timestamp = System.currentTimeMillis()
        )

        // Optimistic update
        val updatedMessages = _uiState.value.messages + message
        setState { copy(messages = updatedMessages, isSending = true) }

        viewModelScope.launch {
            try {
                messagingRepository.sendMessage(chatId, message)
                setState { copy(isSending = false) }
            } catch (e: Exception) {
                val rolledBackMessages = _uiState.value.messages.filter { it.id != message.id }
                setState { copy(messages = rolledBackMessages, isSending = false) }
                sendEffect(ChatEffect.ShowError("Message failed: ${e.localizedMessage}"))
            }
        }
    }
    
    private fun sendImage(uri: Uri) {
        val chatId = _uiState.value.chatId ?: return
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        val currentUserName = firebaseAuth.currentUser?.displayName ?: "User"

        setState { copy(isSending = true) }

        viewModelScope.launch {
            try {
                // 1. Upload to Cloudinary (FREE)
                val imageId = System.currentTimeMillis().toString()
                val imageUrl = cloudinaryRepository.uploadImage(uri, chatId)
                
                if (imageUrl == null) {
                    setState { copy(isSending = false) }
                    sendEffect(ChatEffect.ShowError("Failed to upload photo to Cloudinary"))
                    return@launch
                }

                // 2. Send Message
                val message = Message(
                    id = imageId,
                    senderId = currentUserId,
                    senderName = currentUserName,
                    content = imageUrl,
                    messageType = MessageType.IMAGE,
                    timestamp = System.currentTimeMillis()
                )
                messagingRepository.sendMessage(chatId, message)
                setState { copy(isSending = false) }
                android.util.Log.d("ChatViewModel", "Image sent via Cloudinary: $imageUrl")
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to send image", e)
                setState { copy(isSending = false) }
                sendEffect(ChatEffect.ShowError(e.message ?: "Failed to upload photo"))
            }
        }
    }

    private fun sendVideo(uri: Uri) {
        val chatId = _uiState.value.chatId ?: return
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        val currentUserName = firebaseAuth.currentUser?.displayName ?: "User"

        setState { copy(isSending = true) }

        viewModelScope.launch {
            try {
                // 1. Upload to Cloudinary (FREE)
                val videoId = System.currentTimeMillis().toString()
                val videoUrl = cloudinaryRepository.uploadVideo(uri, chatId)
                
                if (videoUrl == null) {
                    setState { copy(isSending = false) }
                    sendEffect(ChatEffect.ShowError("Failed to upload video to Cloudinary"))
                    return@launch
                }

                // 2. Send Message
                val message = Message(
                    id = videoId,
                    senderId = currentUserId,
                    senderName = currentUserName,
                    content = videoUrl,
                    messageType = MessageType.VIDEO,
                    timestamp = System.currentTimeMillis()
                )
                messagingRepository.sendMessage(chatId, message)
                setState { copy(isSending = false) }
                android.util.Log.d("ChatViewModel", "Video sent via Cloudinary: $videoUrl")
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to send video", e)
                setState { copy(isSending = false) }
                sendEffect(ChatEffect.ShowError(e.message ?: "Failed to upload video"))
            }
        }
    }

    private fun sendFile(uri: Uri) {
        val chatId = _uiState.value.chatId ?: return
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        val currentUserName = firebaseAuth.currentUser?.displayName ?: "User"

        setState { copy(isSending = true) }

        viewModelScope.launch {
            try {
                // Get filename from URI
                val fileName = getFileNameFromUri(uri)
                
                // Upload to Cloudinary (FREE)
                val fileUrl = cloudinaryRepository.uploadFile(uri, chatId, fileName)
                
                if (fileUrl == null) {
                    setState { copy(isSending = false) }
                    sendEffect(ChatEffect.ShowError("Failed to upload file to Cloudinary"))
                    return@launch
                }

                // 2. Send Message
                val fileId = "file_${System.currentTimeMillis()}"
                val message = Message(
                    id = fileId,
                    senderId = currentUserId,
                    senderName = currentUserName,
                    content = fileUrl,
                    messageType = MessageType.FILE,
                    timestamp = System.currentTimeMillis(),
                    fileName = fileName
                )
                messagingRepository.sendMessage(chatId, message)
                setState { copy(isSending = false) }
                android.util.Log.d("ChatViewModel", "File sent via Cloudinary: $fileUrl")
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to send file", e)
                setState { copy(isSending = false) }
                sendEffect(ChatEffect.ShowError(e.message ?: "Failed to upload file"))
            }
        }
    }
    
    private fun getFileNameFromUri(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = application.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.lastPathSegment
        }
        return result
    }

    private fun uploadFile(uri: Uri, folder: String, type: MessageType) {
        val chatId = _uiState.value.chatId ?: return
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        val currentUserName = firebaseAuth.currentUser?.displayName ?: "User"

        android.util.Log.d("ChatViewModel", "uploadFile: chatId=$chatId, folder=$folder, type=$type, uri=$uri")

        setState { copy(isSending = true) }

        viewModelScope.launch {
            try {
                val fileId = "${folder}_${System.currentTimeMillis()}"
                val ext = when (type) {
                    MessageType.IMAGE -> "jpg"
                    MessageType.VIDEO -> "mp4"
                    MessageType.VOICE -> "3gp"
                    else -> "bin"
                }
                val safeChatId = chatId.replace(":", "_").replace("/", "_")
                val path = "${folder}/$safeChatId/$fileId.$ext"
                android.util.Log.d("ChatViewModel", "Uploading $folder to: $path")

                val storageRef = storage.reference.child(path)

                // Set content type
                val contentType = when (type) {
                    MessageType.IMAGE -> "image/jpeg"
                    MessageType.VIDEO -> "video/mp4"
                    else -> "application/octet-stream"
                }
                val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                    .setContentType(contentType)
                    .build()

                storageRef.putFile(uri, metadata).await()
                val fileUrl = storageRef.downloadUrl.await().toString()
                android.util.Log.d("ChatViewModel", "Upload complete: $fileUrl")

                val message = Message(
                    id = fileId,
                    senderId = currentUserId,
                    senderName = currentUserName,
                    content = fileUrl,
                    messageType = type,
                    timestamp = System.currentTimeMillis()
                )
                messagingRepository.sendMessage(chatId, message)
                setState { copy(isSending = false) }
                android.util.Log.d("ChatViewModel", "$folder message sent successfully")
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to upload $folder", e)
                setState { copy(isSending = false) }
                sendEffect(ChatEffect.ShowError("Failed to upload: ${e.message}"))
            }
        }
    }

    private fun sendVoiceMessage(uri: Uri, duration: Int) {
        val chatId = _uiState.value.chatId ?: return
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        val currentUserName = firebaseAuth.currentUser?.displayName ?: "User"

        android.util.Log.d("ChatViewModel", "sendVoiceMessage: chatId=$chatId, uri=$uri, duration=$duration")

        setState { copy(isSending = true) }

        viewModelScope.launch {
            try {
                val voiceId = "voice_${System.currentTimeMillis()}"

                // Read file and convert to Base64 (FREE - no Storage needed).
                // `.use {}` closes the FD on the success AND failure paths; the old
                // code called close() only after a successful read, leaking the
                // descriptor whenever readBytes() threw.
                val bytes = application.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() }
                    ?: throw IllegalStateException("Cannot read voice file")

                // RTDB caps a single value at ~256 KB of payload; 500 KB of audio is
                // the practical ceiling once Base64 expansion is accounted for.
                if (bytes.size > 500 * 1024) {
                    sendEffect(ChatEffect.ShowError("Voice message is too large (max 500 KB)."))
                    setState { copy(isSending = false) }
                    return@launch
                }

                // Convert to Base64
                val base64Audio = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                android.util.Log.d("ChatViewModel", "Voice Base64 size: ${base64Audio.length} chars")

                // Create data URI
                val dataUri = "data:audio/3gpp;base64,$base64Audio"

                val message = Message(
                    id = voiceId,
                    senderId = currentUserId,
                    senderName = currentUserName,
                    content = dataUri,
                    messageType = MessageType.VOICE,
                    voiceDuration = duration,
                    timestamp = System.currentTimeMillis()
                )
                messagingRepository.sendMessage(chatId, message)
                setState { copy(isSending = false) }
                android.util.Log.d("ChatViewModel", "Voice message sent successfully (Base64)")
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to send voice message", e)
                setState { copy(isSending = false) }
                sendEffect(ChatEffect.ShowError("Failed to send voice: ${e.message}"))
            }
        }
    }

    private fun translateMessage(messageId: String, targetLanguage: String) {
        val message = _uiState.value.messages.find { it.id == messageId } ?: return

        viewModelScope.launch {
            val translation = aiEngine.translate(message.content, targetLanguage)

            // AiEngine.translate reports failures as return strings ("Error: ...",
            // "Model not ready. Please download it first.") rather than throwing.
            // Those were previously appended to the message body as if they were
            // translations, permanently polluting the content.
            if (translation.startsWith("Error") || translation.startsWith("Model not ready")) {
                sendEffect(ChatEffect.ShowError(translation))
                return@launch
            }

            // Idempotent: replace any existing block instead of appending another.
            // The old append meant repeated taps stacked duplicate sections on an
            // already-mutated string, and the mutation was lost on the next DB
            // emission anyway.
            val base = message.content.substringBefore("\n\n--- Translation (")
            setState {
                copy(messages = messages.map {
                    if (it.id == messageId) {
                        it.copy(content = "$base\n\n--- Translation ($targetLanguage) ---\n$translation")
                    } else it
                })
            }
        }
    }

    private fun startCall(isVideo: Boolean) {
        val chat = _uiState.value.chat
        val currentUserId = firebaseAuth.currentUser?.uid

        android.util.Log.d("ChatViewModel", "startCall: chat=$chat, currentUserId=$currentUserId")

        if (chat == null) {
            android.util.Log.e("ChatViewModel", "Cannot start call - chat not loaded")
            sendEffect(ChatEffect.ShowError("Chat not loaded yet. Please wait..."))
            return
        }

        if (currentUserId == null) {
            android.util.Log.e("ChatViewModel", "Cannot start call - user not logged in")
            sendEffect(ChatEffect.ShowError("You must be logged in to make calls"))
            return
        }

        // Get the other user's ID from participants
        val otherUserId = chat.participants.firstOrNull { it != currentUserId }
        
        android.util.Log.d("ChatViewModel", "startCall: participants=${chat.participants}, otherUserId=$otherUserId")

        if (otherUserId == null) {
            android.util.Log.e("ChatViewModel", "Cannot start call - no other participant found. Participants: ${chat.participants}")
            sendEffect(ChatEffect.ShowError("Cannot identify call recipient. Try again later."))
            return
        }

        viewModelScope.launch {
            try {
                android.util.Log.d("ChatViewModel", "Starting call with $otherUserId, isVideo=$isVideo")
                callRepository.startCall(otherUserId, isVideo)
                // Navigate to call screen via effect
                sendEffect(ChatEffect.NavigateToCall(otherUserId, isVideo))
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to start call", e)
                sendEffect(ChatEffect.ShowError("Failed to start call: ${e.message}"))
            }
        }
    }

    private fun sendReplyMessage(content: String, replyToId: String, replyToContent: String) {
        if (content.isBlank()) return
        val chatId = _uiState.value.chatId ?: return

        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        val currentUserName = firebaseAuth.currentUser?.displayName ?: "User"

        val message = Message(
            id = System.currentTimeMillis().toString(),
            senderId = currentUserId,
            senderName = currentUserName,
            content = content,
            timestamp = System.currentTimeMillis(),
            replyToMessageId = replyToId,
            replyToContent = replyToContent.take(100)
        )

        viewModelScope.launch {
            try {
                messagingRepository.sendMessage(chatId, message)
            } catch (e: Exception) {
                sendEffect(ChatEffect.ShowError("Failed to send reply: ${e.message}"))
            }
        }
    }

    private fun deleteMessage(messageId: String) {
        val chatId = _uiState.value.chatId ?: return
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                messagingRepository.deleteMessage(chatId, messageId, currentUserId)
            } catch (e: Exception) {
                sendEffect(ChatEffect.ShowError("Failed to delete message: ${e.message}"))
            }
        }
    }

    private fun addReaction(messageId: String, emoji: String) {
        val chatId = _uiState.value.chatId ?: return
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                messagingRepository.addReaction(chatId, messageId, currentUserId, emoji)
            } catch (e: Exception) {
                sendEffect(ChatEffect.ShowError("Failed to add reaction: ${e.message}"))
            }
        }
    }

    fun scheduleMessage(content: String, scheduledTime: Long, replyToMessageId: String?) {
        if (content.isBlank()) return
        val chatId = _uiState.value.chatId ?: return
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                messagingRepository.scheduleMessage(
                    chatId = chatId,
                    senderId = currentUserId,
                    content = content,
                    scheduledTime = scheduledTime,
                    replyToMessageId = replyToMessageId
                )
                sendEffect(ChatEffect.MessageScheduled)
            } catch (e: Exception) {
                sendEffect(ChatEffect.ShowError("Failed to schedule message: ${e.message}"))
            }
        }
    }
}
