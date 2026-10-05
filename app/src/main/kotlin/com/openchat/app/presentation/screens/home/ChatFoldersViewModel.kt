package com.openchat.app.presentation.screens.home

import androidx.lifecycle.viewModelScope
import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.data.model.ChatFolder
import com.openchat.app.data.repository.MessagingRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatFoldersState(
    val customFolders: List<ChatFolder> = emptyList(),
    val totalChatCount: Int = 0,
    val unreadCount: Int = 0,
    val personalCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed class ChatFoldersEvent : UiEvent {
    data object LoadFolders : ChatFoldersEvent()
    data class CreateFolder(val name: String, val icon: String) : ChatFoldersEvent()
    data class DeleteFolder(val folderId: String) : ChatFoldersEvent()
}

sealed class ChatFoldersEffect : UiEffect {
    data class ShowError(val message: String) : ChatFoldersEffect()
}

@HiltViewModel
class ChatFoldersViewModel @Inject constructor(
    private val messagingRepository: MessagingRepository,
    private val observeChatsUseCase: com.openchat.app.domain.usecase.chat.ObserveChatsUseCase,
    private val firebaseAuth: FirebaseAuth
) : BaseViewModel<ChatFoldersState, ChatFoldersEvent, ChatFoldersEffect>() {

    override val _uiState = MutableStateFlow(ChatFoldersState())

    fun loadFolders() {
        onEvent(ChatFoldersEvent.LoadFolders)
    }

    fun createFolder(name: String, icon: String) {
        onEvent(ChatFoldersEvent.CreateFolder(name, icon))
    }

    fun deleteFolder(folderId: String) {
        onEvent(ChatFoldersEvent.DeleteFolder(folderId))
    }

    override fun onEvent(event: ChatFoldersEvent) {
        when (event) {
            ChatFoldersEvent.LoadFolders -> loadFoldersData()
            is ChatFoldersEvent.CreateFolder -> createNewFolder(event.name, event.icon)
            is ChatFoldersEvent.DeleteFolder -> deleteExistingFolder(event.folderId)
        }
    }

    /** Held so reloading cancels the previous collector instead of stacking one. */
    private var loadFoldersJob: kotlinx.coroutines.Job? = null

    private fun loadFoldersData() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        loadFoldersJob?.cancel()
        setState { copy(isLoading = true) }

        // The three counts were declared in the state and rendered by the screen but
        // never assigned, so "All Chats", "Unread" and "Personal" always showed 0.
        data class Counts(val total: Int, val unread: Int, val personal: Int)

        loadFoldersJob = combine(
            messagingRepository.getUserChatFolders(currentUserId),
            observeChatsUseCase()
        ) { folders, chats ->
            val unread = chats.sumOf { chat -> chat.unreadCount.values.sum() }
            val personal = chats.count { it.type == com.openchat.app.data.model.ChatType.PRIVATE }
            Pair(folders.filter { it.isCustom }, Counts(chats.size, unread, personal))
        }
            .onEach { (folders, counts) ->
                setState {
                    copy(
                        customFolders = folders,
                        totalChatCount = counts.total,
                        unreadCount = counts.unread,
                        personalCount = counts.personal,
                        isLoading = false,
                        error = null
                    )
                }
            }
            .catch { e ->
                setState { copy(isLoading = false, error = e.message) }
                sendEffect(ChatFoldersEffect.ShowError(e.message ?: "Failed to load folders"))
            }
            .launchIn(viewModelScope)
    }

    private fun createNewFolder(name: String, icon: String) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        // Previously no validation: blank names were accepted, and ids built from
        // System.currentTimeMillis() collided when two folders were created inside
        // the same millisecond.
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            sendEffect(ChatFoldersEffect.ShowError("Folder name cannot be empty"))
            return
        }
        if (_uiState.value.customFolders.any { it.name.equals(trimmed, ignoreCase = true) }) {
            sendEffect(ChatFoldersEffect.ShowError("A folder named \"$trimmed\" already exists"))
            return
        }

        viewModelScope.launch {
            try {
                val folder = ChatFolder(
                    id = java.util.UUID.randomUUID().toString(),
                    name = trimmed,
                    icon = icon,
                    chatIds = emptyList(),
                    isCustom = true
                )
                
                messagingRepository.createChatFolder(currentUserId, folder)
                
                // Refresh folders
                loadFoldersData()
            } catch (e: Exception) {
                sendEffect(ChatFoldersEffect.ShowError("Failed to create folder: ${e.message}"))
            }
        }
    }

    private fun deleteExistingFolder(folderId: String) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                messagingRepository.deleteChatFolder(currentUserId, folderId)
                
                // Refresh folders
                loadFoldersData()
            } catch (e: Exception) {
                sendEffect(ChatFoldersEffect.ShowError("Failed to delete folder: ${e.message}"))
            }
        }
    }
}
