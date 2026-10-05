package com.openchat.app.presentation.screens.scheduled

import androidx.lifecycle.viewModelScope
import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.data.model.ScheduledMessage
import com.openchat.app.data.repository.MessagingRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduledMessagesState(
    val messages: List<ScheduledMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed class ScheduledMessagesEvent : UiEvent {
    data object LoadMessages : ScheduledMessagesEvent()
    data class CancelMessage(val messageId: String) : ScheduledMessagesEvent()
}

sealed class ScheduledMessagesEffect : UiEffect {
    data class ShowError(val message: String) : ScheduledMessagesEffect()
    data object MessageCancelled : ScheduledMessagesEffect()
}

@HiltViewModel
class ScheduledMessagesViewModel @Inject constructor(
    private val messagingRepository: MessagingRepository,
    private val firebaseAuth: FirebaseAuth
) : BaseViewModel<ScheduledMessagesState, ScheduledMessagesEvent, ScheduledMessagesEffect>() {

    override val _uiState = MutableStateFlow(ScheduledMessagesState())

    fun loadScheduledMessages() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        onEvent(ScheduledMessagesEvent.LoadMessages)
    }

    override fun onEvent(event: ScheduledMessagesEvent) {
        when (event) {
            ScheduledMessagesEvent.LoadMessages -> loadMessages()
            is ScheduledMessagesEvent.CancelMessage -> cancelScheduledMessage(event.messageId)
        }
    }

    fun cancelScheduledMessage(messageId: String) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                messagingRepository.cancelScheduledMessage(currentUserId, messageId)
                sendEffect(ScheduledMessagesEffect.MessageCancelled)
            } catch (e: Exception) {
                sendEffect(ScheduledMessagesEffect.ShowError("Failed to cancel message: ${e.message}"))
            }
        }
    }

    private fun loadMessages() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        setState { copy(isLoading = true) }

        messagingRepository.getScheduledMessages(currentUserId)
            .onEach { messages ->
                setState { copy(messages = messages, isLoading = false) }
            }
            .catch { e ->
                setState { copy(isLoading = false, error = e.message) }
                sendEffect(ScheduledMessagesEffect.ShowError(e.message ?: "Failed to load messages"))
            }
            .launchIn(viewModelScope)
    }

}
