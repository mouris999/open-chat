package com.openchat.app.presentation.screens.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.domain.model.User
import com.openchat.app.domain.usecase.contact.ObserveRegisteredContactsUseCase
import com.openchat.app.domain.usecase.contact.SyncContactsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.firebase.auth.FirebaseAuth
import com.openchat.app.data.repository.MessagingRepository

data class ContactsState(
    val contacts: List<User> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) : UiState

sealed class ContactsEvent : UiEvent {
    data class SearchQueryChanged(val query: String) : ContactsEvent()
    data class ContactClicked(val userId: String) : ContactsEvent()
    data object RefreshContacts : ContactsEvent()
    data object SyncContacts : ContactsEvent()
    data class PermissionResult(val granted: Boolean) : ContactsEvent()
}

sealed class ContactsEffect : UiEffect {
    data class NavigateToChat(val userId: String) : ContactsEffect()
    data class ShowError(val message: String) : ContactsEffect()
    data object RequestContactsPermission : ContactsEffect()
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observeRegisteredContactsUseCase: ObserveRegisteredContactsUseCase,
    private val syncContactsUseCase: SyncContactsUseCase,
    private val messagingRepository: MessagingRepository,
    private val firebaseAuth: FirebaseAuth
) : BaseViewModel<ContactsState, ContactsEvent, ContactsEffect>() {
    
    override val _uiState = MutableStateFlow(ContactsState())
    
    init {
        observeContacts()
        checkAndSyncContacts()
    }
    
    private var allContacts: List<User> = emptyList()
    private fun filterContacts(list: List<User>, query: String): List<User> {
        if (query.isBlank()) return list
        return list.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.username?.contains(query, ignoreCase = true) == true ||
                it.email?.contains(query, ignoreCase = true) == true ||
                it.phoneNumber?.contains(query, ignoreCase = true) == true
        }
    }
    private fun observeContacts() {
        observeRegisteredContactsUseCase()
            .onEach { contacts ->
                allContacts = contacts
                setState {
                    copy(
                        contacts = filterContacts(contacts, searchQuery),
                        isLoading = false
                    )
                }
            }
            .catch { e ->
                setState { copy(isLoading = false, error = e.message) }
            }
            .launchIn(viewModelScope)
    }

    private fun checkAndSyncContacts() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            syncContacts()
        } else {
            sendEffect(ContactsEffect.RequestContactsPermission)
        }
    }
    
    private fun syncContacts() {
        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }

            val result = syncContactsUseCase()

            // isLoading must be cleared on BOTH paths. Clearing it only in the error
            // branch left the screen stuck behind a full-screen spinner whenever the
            // sync succeeded but produced no database change (so no new emission
            // arrived from observeRegisteredContactsUseCase to reset it).
            setState { copy(isLoading = false) }

            result.onError { error, _ ->
                setState { copy(error = error.message) }
                sendEffect(ContactsEffect.ShowError(error.message ?: "Failed to sync contacts"))
            }
        }
    }
    
    override fun onEvent(event: ContactsEvent) {
        when (event) {
            is ContactsEvent.SearchQueryChanged -> {
                setState { copy(searchQuery = event.query, contacts = filterContacts(allContacts, event.query)) }
            }
            is ContactsEvent.ContactClicked -> openChatWith(event.userId)
            ContactsEvent.RefreshContacts -> checkAndSyncContacts()
            ContactsEvent.SyncContacts -> checkAndSyncContacts()
            is ContactsEvent.PermissionResult -> {
                if (event.granted) {
                    syncContacts()
                } else {
                    sendEffect(ContactsEffect.ShowError("Contacts permission is required to sync contacts"))
                }
            }
        }
    }

    /**
     * Creates (or resolves) the 1:1 chat with [otherUserId] and navigates to it.
     *
     * This must run through the ViewModel rather than the screen: the screen only
     * knows the user's *uid*, but the CHAT route needs a *chatId*. Navigating with
     * the uid opens a chat against a non-existent node - permanently empty.
     */
    private fun openChatWith(otherUserId: String) {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            sendEffect(ContactsEffect.ShowError("You need to sign in to start a chat"))
            return
        }
        if (currentUserId == otherUserId) {
            sendEffect(ContactsEffect.ShowError("You cannot start a chat with yourself"))
            return
        }

        viewModelScope.launch {
            setState { copy(isLoading = true, error = null) }
            try {
                val chatId = messagingRepository.getOrCreateChat(currentUserId, otherUserId)
                setState { copy(isLoading = false) }
                sendEffect(ContactsEffect.NavigateToChat(chatId))
            } catch (e: Exception) {
                // Without this the exception escapes viewModelScope (which has no
                // CoroutineExceptionHandler) and crashes the process, leaving the
                // screen stuck behind its full-screen spinner.
                android.util.Log.e("ContactsViewModel", "Failed to open chat", e)
                setState { copy(isLoading = false, error = e.message) }
                sendEffect(ContactsEffect.ShowError(e.message ?: "Could not open chat"))
            }
        }
    }
}
