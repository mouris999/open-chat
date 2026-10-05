package com.openchat.app.presentation.screens.auth

import android.net.Uri
import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.domain.usecase.user.UpdateProfileUseCase
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileSetupState(
    val displayName: String = "",
    val bio: String = "",
    val photoUri: Uri? = null,
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
) : UiState

sealed class ProfileSetupEvent : UiEvent {
    data class DisplayNameChanged(val name: String) : ProfileSetupEvent()
    data class BioChanged(val bio: String) : ProfileSetupEvent()
    data class PhotoSelected(val uri: Uri) : ProfileSetupEvent()
    data object SaveProfile : ProfileSetupEvent()
}

sealed class ProfileSetupEffect : UiEffect {
    data class ShowError(val message: String) : ProfileSetupEffect()
    data object NavigateToHome : ProfileSetupEffect()
}

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val updateProfileUseCase: UpdateProfileUseCase
) : BaseViewModel<ProfileSetupState, ProfileSetupEvent, ProfileSetupEffect>() {
    
    override val _uiState = MutableStateFlow(ProfileSetupState())
    
    override fun onEvent(event: ProfileSetupEvent) {
        when (event) {
            is ProfileSetupEvent.DisplayNameChanged -> {
                setState { copy(displayName = event.name, error = null) }
            }
            is ProfileSetupEvent.BioChanged -> {
                setState { copy(bio = event.bio) }
            }
            is ProfileSetupEvent.PhotoSelected -> {
                setState { copy(photoUri = event.uri) }
            }
            ProfileSetupEvent.SaveProfile -> saveProfile()
        }
    }
    
    private fun saveProfile() {
        // Previously this returned silently, leaving the user tapping Save with no
        // feedback and no explanation.
        if (_uiState.value.displayName.isBlank()) {
            setState { copy(error = "Display name is required") }
            sendEffect(ProfileSetupEffect.ShowError("Display name is required"))
            return
        }

        setState { copy(isLoading = true, error = null) }

        viewModelScope.launch {
            // runCatching: an uncaught throw here escapes viewModelScope (no
            // CoroutineExceptionHandler) and crashes the process.
            val result = runCatching {
                updateProfileUseCase(
                    displayName = _uiState.value.displayName,
                    bio = _uiState.value.bio.takeIf { it.isNotBlank() },
                    photoUri = _uiState.value.photoUri
                )
            }.getOrElse { e ->
                setState { copy(isLoading = false, error = e.message) }
                sendEffect(ProfileSetupEffect.ShowError(e.message ?: "Failed to save profile"))
                return@launch
            }

            result.onSuccess {
                setState { copy(isLoading = false, isComplete = true) }
                sendEffect(ProfileSetupEffect.NavigateToHome)
            }.onError { error, _ ->
                setState { copy(isLoading = false, error = error.message) }
                sendEffect(ProfileSetupEffect.ShowError(error.message ?: "Failed to save profile"))
            }
        }
    }
}
