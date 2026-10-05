package com.openchat.app.presentation.screens.lock

import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.domain.repository.AppLockRepository
import com.openchat.app.domain.repository.AuthMode
import com.openchat.app.domain.repository.AuthResult
import android.util.Log
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LockState(
    val isPrimaryPasswordSet: Boolean = false,
    val isSecondaryPasswordSet: Boolean = false,
    val password: String = "",
    val error: String? = null,
    val isLoading: Boolean = false,
    val unlockTarget: String? = null
) : UiState

sealed class LockEvent : UiEvent {
    data class EnterPassword(val password: String) : LockEvent()
    data object UnlockPrimary : LockEvent()
    data object UnlockSecondary : LockEvent()
    data object ClearError : LockEvent()
}

sealed class LockEffect : UiEffect {
    data object AuthenticatedPrimary : LockEffect()
    data object AuthenticatedSecondary : LockEffect()
    data object Locked : LockEffect()
    data class ShowError(val message: String) : LockEffect()
}

@HiltViewModel
class LockViewModel @Inject constructor(
    private val appLockRepository: AppLockRepository
) : BaseViewModel<LockState, LockEvent, LockEffect>() {

    private companion object { const val TAG = "LockViewModel" }

    override val _uiState = MutableStateFlow(LockState(
        isPrimaryPasswordSet = appLockRepository.isPrimaryPasswordSet(),
        isSecondaryPasswordSet = appLockRepository.isSecondaryPasswordSet()
    ))

    override fun onEvent(event: LockEvent) {
        when (event) {
            is LockEvent.EnterPassword -> setState { copy(password = event.password, error = null) }
            LockEvent.UnlockPrimary -> unlockPrimary()
            LockEvent.UnlockSecondary -> unlockSecondary()
            LockEvent.ClearError -> setState { copy(error = null) }
        }
    }

    /**
     * PBKDF2 with 600_000 iterations costs hundreds of ms to seconds. Calling it
     * straight from onEvent froze the UI on every Unlock tap (ANR risk) and left
     * isLoading flipped true->false inside the same synchronous block, so the
     * screen's "busy" state was never actually observable. Runs off the main thread.
     */
    private fun unlockPrimary() {
        val password = _uiState.value.password
        if (password.isBlank()) {
            setState { copy(error = "Please enter a password") }
            return
        }
        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.Default) {
                runCatching { appLockRepository.verifyPrimaryPassword(password) }
                    .getOrElse { e ->
                        Log.e(TAG, "verifyPrimaryPassword failed", e)
                        AuthResult.NotConfigured
                    }
            }
            when (r) {
                AuthResult.Authorized -> {
                    appLockRepository.setCurrentAuthMode(AuthMode.STANDARD)
                    setState { copy(isLoading = false) }
                    sendEffect(LockEffect.AuthenticatedPrimary)
                }
                AuthResult.WrongPassword -> {
                    setState { copy(isLoading = false, error = "Wrong password", password = "") }
                }
                AuthResult.NotConfigured -> {
                    setState { copy(isLoading = false, error = "App lock not configured") }
                }
                else -> { setState { copy(isLoading = false, error = "Auth failed") } }
            }
        }
    }

    private fun unlockSecondary() {
        val password = _uiState.value.password
        if (password.isBlank()) {
            setState { copy(error = "Please enter a password") }
            return
        }
        setState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.Default) {
                runCatching { appLockRepository.verifySecondaryPassword(password) }
                    .getOrElse { e ->
                        Log.e(TAG, "verifySecondaryPassword failed", e)
                        AuthResult.NotConfigured
                    }
            }
            when (r) {
                AuthResult.Authorized -> {
                    appLockRepository.setCurrentAuthMode(AuthMode.PRIVATE)
                    setState { copy(isLoading = false) }
                    sendEffect(LockEffect.AuthenticatedSecondary)
                }
                AuthResult.WrongPassword -> {
                    setState { copy(isLoading = false, error = "Wrong password", password = "") }
                }
                AuthResult.NotConfigured -> {
                    setState { copy(isLoading = false, error = "Private password not configured") }
                }
                else -> { setState { copy(isLoading = false, error = "Auth failed") } }
            }
        }
    }
}
