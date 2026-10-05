package com.openchat.app.presentation.screens.settings

import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import androidx.lifecycle.viewModelScope
import com.openchat.app.data.model.Chat
import com.openchat.app.domain.repository.ChatLockRepository
import com.openchat.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class ChatLockSetupState(
    val isChatLockEnabled: Boolean = false,
    val allChats: List<Chat> = emptyList(),
    val lockedChatIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = false
) : UiState {
    val filteredChats: List<Chat>
        get() = if (searchQuery.isBlank()) allChats
        else allChats.filter { it.title?.contains(searchQuery, ignoreCase = true) == true }
}

sealed class ChatLockSetupEvent : UiEvent {
    data object ToggleEnabled : ChatLockSetupEvent()
    data class ToggleChatLock(val chatId: String) : ChatLockSetupEvent()
    data class SetSearchQuery(val query: String) : ChatLockSetupEvent()
    data object LoadChats : ChatLockSetupEvent()
}

sealed class ChatLockSetupEffect : UiEffect {
    data class ShowError(val message: String) : ChatLockSetupEffect()
}

@HiltViewModel
class ChatLockSetupViewModel @Inject constructor(
    private val chatLockRepository: ChatLockRepository,
    private val chatRepository: ChatRepository
) : BaseViewModel<ChatLockSetupState, ChatLockSetupEvent, ChatLockSetupEffect>() {

    override val _uiState = MutableStateFlow(ChatLockSetupState(
        isChatLockEnabled = chatLockRepository.isChatLockEnabled(),
        lockedChatIds = chatLockRepository.getLockedChatIds()
    ))

    /** Held so re-issuing LoadChats cancels the previous collector. */
    private var loadChatsJob: kotlinx.coroutines.Job? = null

    init {
        onEvent(ChatLockSetupEvent.LoadChats)
    }

    override fun onEvent(event: ChatLockSetupEvent) {
        when (event) {
            ChatLockSetupEvent.ToggleEnabled -> {
                val enabled = !_uiState.value.isChatLockEnabled
                chatLockRepository.setChatLockEnabled(enabled)
                if (!enabled) {
                    // Disabling the feature must also unlock everything. Leaving the
                    // locked_chats set in place was a one-way trap: Home filtered
                    // those chats out unconditionally, and this screen hides the
                    // per-chat list while the lock is off - so the user could no
                    // longer reach the chats they had just hidden.
                    _uiState.value.lockedChatIds.forEach { chatLockRepository.unlockChat(it) }
                }
                setState {
                    copy(
                        isChatLockEnabled = enabled,
                        lockedChatIds = chatLockRepository.getLockedChatIds()
                    )
                }
            }
            is ChatLockSetupEvent.ToggleChatLock -> {
                val chatId = event.chatId
                if (chatLockRepository.isChatLocked(chatId)) {
                    chatLockRepository.unlockChat(chatId)
                } else {
                    chatLockRepository.lockChat(chatId)
                }
                setState { copy(lockedChatIds = chatLockRepository.getLockedChatIds()) }
            }
            is ChatLockSetupEvent.SetSearchQuery -> setState { copy(searchQuery = event.query) }
            ChatLockSetupEvent.LoadChats -> loadChats()
        }
    }

    private fun loadChats() {
        loadChatsJob?.cancel()
        setState { copy(isLoading = true) }
        loadChatsJob = chatRepository.observeChats()
            .onEach { chats ->
                setState { copy(allChats = chats, isLoading = false, lockedChatIds = chatLockRepository.getLockedChatIds()) }
            }
            .catch { e ->
                setState { copy(isLoading = false) }
                sendEffect(ChatLockSetupEffect.ShowError(e.message ?: "Failed to load chats"))
            }
            .launchIn(viewModelScope)
    }
}
