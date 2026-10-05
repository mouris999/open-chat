package com.openchat.app.presentation.screens.group

import androidx.lifecycle.viewModelScope
import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.domain.model.User
import com.openchat.app.domain.usecase.contact.ObserveRegisteredContactsUseCase
import com.openchat.app.data.repository.MessagingRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateGroupState(
    val contacts: List<User> = emptyList(),
    val selectedContacts: List<User> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState {
    val filteredContacts: List<User>
        get() = if (searchQuery.isBlank()) {
            contacts
        } else {
            contacts.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.username?.contains(searchQuery, ignoreCase = true) == true
            }
        }
}

sealed class CreateGroupEvent : UiEvent {
    data class ToggleContact(val contact: User) : CreateGroupEvent()
    data class SearchQueryChanged(val query: String) : CreateGroupEvent()
    data class CreateGroup(val name: String) : CreateGroupEvent()
}

sealed class CreateGroupEffect : UiEffect {
    data class NavigateToChat(val chatId: String) : CreateGroupEffect()
    data class ShowError(val message: String) : CreateGroupEffect()
}

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val observeRegisteredContactsUseCase: ObserveRegisteredContactsUseCase,
    private val messagingRepository: MessagingRepository,
    private val firebaseAuth: FirebaseAuth
) : BaseViewModel<CreateGroupState, CreateGroupEvent, CreateGroupEffect>() {

    override val _uiState = MutableStateFlow(CreateGroupState())

    init {
        observeContacts()
    }

    private fun observeContacts() {
        observeRegisteredContactsUseCase()
            .onEach { contacts ->
                setState { copy(contacts = contacts, isLoading = false) }
            }
            .catch { e ->
                setState { copy(isLoading = false, error = e.message) }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: CreateGroupEvent) {
        when (event) {
            is CreateGroupEvent.ToggleContact -> toggleContact(event.contact)
            is CreateGroupEvent.SearchQueryChanged -> {
                setState { copy(searchQuery = event.query) }
            }
            is CreateGroupEvent.CreateGroup -> createGroup(event.name)
        }
    }

    private fun toggleContact(contact: User) {
        val currentSelected = _uiState.value.selectedContacts
        setState {
            copy(
                selectedContacts = if (currentSelected.contains(contact)) {
                    currentSelected - contact
                } else {
                    currentSelected + contact
                }
            )
        }
    }

    private fun createGroup(name: String) {
        // Validate before writing: previously a group with a blank name and no
        // selected members was created silently.
        if (name.isBlank()) {
            sendEffect(CreateGroupEffect.ShowError("Give the group a name"))
            return
        }
        if (_uiState.value.selectedContacts.isEmpty()) {
            sendEffect(CreateGroupEffect.ShowError("Select at least one member"))
            return
        }
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            sendEffect(CreateGroupEffect.ShowError("You need to sign in to create a group"))
            return
        }
        val members = _uiState.value.selectedContacts.map { it.id } + currentUserId

        viewModelScope.launch {
            setState { copy(isLoading = true) }
            try {
                val chatId = messagingRepository.createGroupChat(name, members, currentUserId)
                setState { copy(isLoading = false) }
                sendEffect(CreateGroupEffect.NavigateToChat(chatId))
            } catch (e: Exception) {
                setState { copy(isLoading = false) }
                sendEffect(CreateGroupEffect.ShowError("Failed to create group: ${e.message}"))
            }
        }
    }
}
