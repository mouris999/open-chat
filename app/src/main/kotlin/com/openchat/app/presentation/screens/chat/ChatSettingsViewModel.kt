package com.openchat.app.presentation.screens.chat

import androidx.lifecycle.viewModelScope
import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.data.model.Chat
import com.openchat.app.data.repository.MessagingRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChatSettingsState(
    val chatId: String = "",
    val chatTitle: String = "",
    val chatPhotoUrl: String? = null,
    val participantCount: Int = 0,
    val disappearingTimer: Long = 0L,
    val wallpaperUrl: String? = null,
    val themeColor: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed class ChatSettingsEvent : UiEvent {
    data class LoadSettings(val chatId: String) : ChatSettingsEvent()
    data class SetDisappearingTimer(val durationMs: Long) : ChatSettingsEvent()
    data class SetWallpaper(val url: String?) : ChatSettingsEvent()
    data class SetThemeColor(val color: String) : ChatSettingsEvent()
    data object ClearChat : ChatSettingsEvent()
    data object ExportChat : ChatSettingsEvent()
}

sealed class ChatSettingsEffect : UiEffect {
    data object ChatCleared : ChatSettingsEffect()
    data class ChatExported(val filePath: String) : ChatSettingsEffect()
    data class ShowError(val message: String) : ChatSettingsEffect()
}

@HiltViewModel
class ChatSettingsViewModel @Inject constructor(
    private val messagingRepository: MessagingRepository,
    private val firebaseAuth: FirebaseAuth,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : BaseViewModel<ChatSettingsState, ChatSettingsEvent, ChatSettingsEffect>() {

    override val _uiState = MutableStateFlow(ChatSettingsState())

    /**
     * Held so a re-entry cancels the previous collector. ChatSettingsScreen
     * dispatches LoadSettings from a LaunchedEffect(chatId), and the ViewModel
     * survives across chats on the back stack - without this the old chat's
     * collector keeps writing into the same state alongside the new one.
     */
    private var loadJob: kotlinx.coroutines.Job? = null

    fun loadChatSettings(chatId: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            setState { copy(isLoading = true) }
            try {
                messagingRepository.getChat(chatId)
                    .collect { chat ->
                        if (chat != null) {
                            setState {
                                copy(
                                    chatId = chat.id,
                                    chatTitle = chat.title ?: "Chat",
                                    // Was missing from this copy(): the state field
                                    // exists and the screen renders it, so the group
                                    // photo always fell back to the generic icon.
                                    chatPhotoUrl = chat.photoUrl,
                                    participantCount = chat.participants.size,
                                    disappearingTimer = chat.disappearingTimer,
                                    wallpaperUrl = chat.wallpaperUrl,
                                    themeColor = chat.themeColor,
                                    isLoading = false
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                android.util.Log.e("ChatSettingsVM", "Failed to load chat settings", e)
                setState { copy(isLoading = false, error = e.message) }
            }
        }
    }

    override fun onEvent(event: ChatSettingsEvent) {
        when (event) {
            is ChatSettingsEvent.LoadSettings -> loadChatSettings(event.chatId)
            is ChatSettingsEvent.SetDisappearingTimer -> setDisappearingTimer(event.durationMs)
            is ChatSettingsEvent.SetWallpaper -> setWallpaper(event.url)
            is ChatSettingsEvent.SetThemeColor -> setThemeColor(event.color)
            ChatSettingsEvent.ClearChat -> clearChat()
            ChatSettingsEvent.ExportChat -> exportChat()
        }
    }

    fun setDisappearingTimer(durationMs: Long) {
        val chatId = _uiState.value.chatId
        if (chatId.isBlank()) return

        viewModelScope.launch {
            try {
                messagingRepository.updateChatSettings(chatId, mapOf("disappearingTimer" to durationMs))
                setState { copy(disappearingTimer = durationMs) }
            } catch (e: Exception) {
                sendEffect(ChatSettingsEffect.ShowError("Failed to update settings: ${e.message}"))
            }
        }
    }

    private fun setWallpaper(url: String?) {
        val chatId = _uiState.value.chatId
        if (chatId.isBlank()) return

        viewModelScope.launch {
            try {
                messagingRepository.updateChatSettings(chatId, mapOf("wallpaperUrl" to url))
                setState { copy(wallpaperUrl = url) }
            } catch (e: Exception) {
                sendEffect(ChatSettingsEffect.ShowError("Failed to update wallpaper: ${e.message}"))
            }
        }
    }

    fun setThemeColor(color: String) {
        val chatId = _uiState.value.chatId
        if (chatId.isBlank()) return

        viewModelScope.launch {
            try {
                messagingRepository.updateChatSettings(chatId, mapOf("themeColor" to color))
                setState { copy(themeColor = color) }
            } catch (e: Exception) {
                sendEffect(ChatSettingsEffect.ShowError("Failed to update theme: ${e.message}"))
            }
        }
    }

    fun clearChat() {
        val chatId = _uiState.value.chatId
        // Every sibling mutator guards on a blank chatId; without this a premature
        // "Clear chat" silently does nothing.
        if (chatId.isBlank()) {
            sendEffect(ChatSettingsEffect.ShowError("No chat selected"))
            return
        }
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                messagingRepository.clearChatForUser(chatId, currentUserId)
                sendEffect(ChatSettingsEffect.ChatCleared)
            } catch (e: Exception) {
                sendEffect(ChatSettingsEffect.ShowError("Failed to clear chat: ${e.message}"))
            }
        }
    }

    fun exportChat() {
        val chatId = _uiState.value.chatId
        if (chatId.isBlank()) {
            sendEffect(ChatSettingsEffect.ShowError("No chat selected"))
            return
        }

        viewModelScope.launch {
            setState { copy(isLoading = true) }
            try {
                val messages = messagingRepository.getChatMessages(chatId)
                val json = com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(messages)
                val fileName = "chat_export_${chatId.take(8)}_${System.currentTimeMillis()}.json"

                // Two problems with the old approach: it ran the fetch + JSON
                // serialisation + disk write on Dispatchers.Main inside viewModelScope,
                // and getExternalStoragePublicDirectory(DOCUMENTS) is read-only under
                // scoped storage on API 29+, so export always failed. Write to the
                // app-specific external dir, which needs no permission on any API level.
                val exportDir: String = appContext
                    .getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                    ?.absolutePath
                    ?: appContext.filesDir.absolutePath

                val filePath: String = withContext(Dispatchers.IO) {
                    val dir = java.io.File(exportDir)
                    if (!dir.exists()) dir.mkdirs()
                    java.io.File(dir, fileName).also { it.writeText(json) }.absolutePath
                }

                setState { copy(isLoading = false) }
                sendEffect(ChatSettingsEffect.ChatExported(filePath))
            } catch (e: Exception) {
                setState { copy(isLoading = false) }
                sendEffect(ChatSettingsEffect.ShowError("Export failed: ${e.message}"))
            }
        }
    }
}
