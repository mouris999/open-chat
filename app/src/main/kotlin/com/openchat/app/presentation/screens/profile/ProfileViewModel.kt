package com.openchat.app.presentation.screens.profile

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.domain.usecase.user.ObserveCurrentUserUseCase
import com.openchat.app.domain.usecase.user.UpdateProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileState(
    val displayName: String = "",
    val bio: String = "",
    val photoUri: Uri? = null,
    val initialPhotoUrl: String? = null,
    val phoneNumber: String = "",
    val email: String = "",
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
) : UiState

sealed class ProfileEvent : UiEvent {
    data class DisplayNameChanged(val name: String) : ProfileEvent()
    data class BioChanged(val bio: String) : ProfileEvent()
    data class PhotoSelected(val uri: Uri) : ProfileEvent()
    data object SaveProfile : ProfileEvent()
}

sealed class ProfileEffect : UiEffect {
    data class ShowError(val message: String) : ProfileEffect()
    data object NavigateBack : ProfileEffect()
    data class ShowSuccess(val message: String) : ProfileEffect()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase
) : BaseViewModel<ProfileState, ProfileEvent, ProfileEffect>() {

    override val _uiState = MutableStateFlow(ProfileState())

    /**
     * The observer must seed the form exactly once. observeCurrentUserUseCase is
     * backed by Room, and the same row is rewritten by presence updates on every
     * app foreground/background - so re-seeding on every emission overwrote the
     * display name and bio the user was in the middle of typing.
     */
    private var formSeeded = false

    init {
        observeCurrentUser()
    }

    private fun observeCurrentUser() {
        observeCurrentUserUseCase()
            .onEach { user ->
                if (user != null && !formSeeded) {
                    formSeeded = true
                    setState {
                        copy(
                            displayName = user.displayName,
                            bio = user.bio ?: "",
                            initialPhotoUrl = user.photoUrl,
                            phoneNumber = user.phoneNumber ?: "",
                            email = user.email ?: ""
                        )
                    }
                }
            }
            .catch { e ->
                setState { copy(error = e.message) }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.DisplayNameChanged -> {
                setState { copy(displayName = event.name, error = null) }
            }
            is ProfileEvent.BioChanged -> {
                setState { copy(bio = event.bio) }
            }
            is ProfileEvent.PhotoSelected -> {
                setState { copy(photoUri = event.uri) }
            }
            ProfileEvent.SaveProfile -> saveProfile()
        }
    }

    private fun saveProfile() {
        if (_uiState.value.displayName.isBlank()) {
            setState { copy(error = "Display name cannot be empty") }
            return
        }

        setState { copy(isLoading = true, error = null) }

        viewModelScope.launch {
            // runCatching so a thrown exception (upload/storage failure) cannot escape
            // viewModelScope, which has no CoroutineExceptionHandler and would crash.
            val result = runCatching {
                updateProfileUseCase(
                    displayName = _uiState.value.displayName,
                    bio = _uiState.value.bio.takeIf { it.isNotBlank() },
                    photoUri = _uiState.value.photoUri
                )
            }.getOrElse { e ->
                setState { copy(isLoading = false, error = e.message) }
                sendEffect(ProfileEffect.ShowError(e.message ?: "Failed to save profile"))
                return@launch
            }

            result.onSuccess {
                setState { copy(isLoading = false, isComplete = true) }
                sendEffect(ProfileEffect.ShowSuccess("Profile updated successfully"))
                sendEffect(ProfileEffect.NavigateBack)
            }.onError { error, _ ->
                setState { copy(isLoading = false, error = error.message) }
                sendEffect(ProfileEffect.ShowError(error.message ?: "Failed to save profile"))
            }
        }
    }
}
