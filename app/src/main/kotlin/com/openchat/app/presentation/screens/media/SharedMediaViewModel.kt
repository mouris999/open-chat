package com.openchat.app.presentation.screens.media

import androidx.lifecycle.viewModelScope
import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.data.model.Message
import com.openchat.app.data.model.MessageType
import com.openchat.app.data.repository.MessagingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class SharedMediaState(
    val allMessages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState {
    val mediaMessages: List<Message>
        get() = allMessages.filter { 
            it.messageType == MessageType.IMAGE || it.messageType == MessageType.VIDEO 
        }
    
    val fileMessages: List<Message>
        get() = allMessages.filter { it.messageType == MessageType.FILE }
    
    val linkMessages: List<Message>
        get() = allMessages.filter { 
            it.messageType == MessageType.TEXT && 
            it.content.contains("http")
        }
    
    val voiceMessages: List<Message>
        get() = allMessages.filter { it.messageType == MessageType.VOICE }
}

sealed class SharedMediaEvent : UiEvent {
    data class LoadMedia(val chatId: String) : SharedMediaEvent()
}

sealed class SharedMediaEffect : UiEffect {
    data class ShowError(val message: String) : SharedMediaEffect()
}

@HiltViewModel
class SharedMediaViewModel @Inject constructor(
    private val messagingRepository: MessagingRepository
) : BaseViewModel<SharedMediaState, SharedMediaEvent, SharedMediaEffect>() {

    override val _uiState = MutableStateFlow(SharedMediaState())

    fun loadMedia(chatId: String) {
        onEvent(SharedMediaEvent.LoadMedia(chatId))
    }

    override fun onEvent(event: SharedMediaEvent) {
        when (event) {
            is SharedMediaEvent.LoadMedia -> loadMediaForChat(event.chatId)
        }
    }

    private fun loadMediaForChat(chatId: String) {
        setState { copy(isLoading = true) }

        messagingRepository.getMessages(chatId)
            .onEach { messages ->
                setState { 
                    copy(
                        allMessages = messages.filter { 
                            it.messageType in listOf(
                                MessageType.IMAGE, 
                                MessageType.VIDEO, 
                                MessageType.FILE, 
                                MessageType.VOICE
                            ) || (it.messageType == MessageType.TEXT && it.content.contains("http"))
                        },
                        isLoading = false
                    ) 
                }
            }
            .catch { e ->
                setState { copy(isLoading = false, error = e.message) }
                sendEffect(SharedMediaEffect.ShowError(e.message ?: "Failed to load media"))
            }
            .launchIn(viewModelScope)
    }
}
