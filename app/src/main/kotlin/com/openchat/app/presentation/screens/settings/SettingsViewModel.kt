package com.openchat.app.presentation.screens.settings

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.domain.model.User
import com.openchat.app.domain.repository.AppLockRepository
import com.openchat.app.domain.repository.AuthRepository
import com.openchat.app.domain.repository.ChatLockRepository
import com.openchat.app.domain.usecase.user.ObserveCurrentUserUseCase
import com.openchat.app.presentation.theme.ThemeManager
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class SettingsState(
    val currentUser: User? = null,
    val isSigningOut: Boolean = false,
    val isDarkMode: Boolean = false,
    val themeMode: com.openchat.app.presentation.theme.ThemeMode = com.openchat.app.presentation.theme.ThemeMode.SYSTEM,
    val fontSize: FontSize = FontSize.MEDIUM,
    val showFontSizeDialog: Boolean = false,
    val showThemeDialog: Boolean = false,
    val isAppLockEnabled: Boolean = false,
    val isChatLockEnabled: Boolean = false,
    val lockedChatCount: Int = 0,
    val error: String? = null
) : UiState

sealed class SettingsEvent : UiEvent {
    data object SignOut : SettingsEvent()
    data object DeleteAccount : SettingsEvent()
    data class ToggleDarkMode(val enabled: Boolean) : SettingsEvent()
    data class SetThemeMode(val mode: com.openchat.app.presentation.theme.ThemeMode) : SettingsEvent()
    data object ShowFontSizeDialog : SettingsEvent()
    data object DismissFontSizeDialog : SettingsEvent()
    data object ShowThemeDialog : SettingsEvent()
    data object DismissThemeDialog : SettingsEvent()
    data class SetFontSize(val size: FontSize) : SettingsEvent()
    data class LinkWebDevice(val qrCodeContent: String) : SettingsEvent()
    data class LinkWebDeviceFromDeepLink(val token: String) : SettingsEvent()
    /**
     * Re-reads the lock settings from their repositories. Must be dispatched on
     * every entry to Settings, because ChatLockSetup lives on the same back stack
     * and this ViewModel is reused across the two screens - without a refresh the
     * "Chat lock" row keeps showing stale "Off" / count values.
     */
    data object RefreshLockState : SettingsEvent()
}

sealed class SettingsEffect : UiEffect {
    data object NavigateToAuth : SettingsEffect()
    data object DeviceLinked : SettingsEffect()
    data class ShowError(val message: String) : SettingsEffect()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth,
    private val appLockRepository: AppLockRepository,
    private val chatLockRepository: ChatLockRepository,
    private val themeManager: ThemeManager
) : BaseViewModel<SettingsState, SettingsEvent, SettingsEffect>() {

    private companion object { const val TAG = "SettingsViewModel" }

    override val _uiState = MutableStateFlow(SettingsState(
        isDarkMode = themeManager.isDarkMode.value,
        themeMode = themeManager.themeMode.value,
        fontSize = themeManager.fontSize.value,
        isAppLockEnabled = appLockRepository.isAppLockEnabled(),
        isChatLockEnabled = chatLockRepository.isChatLockEnabled(),
        lockedChatCount = chatLockRepository.getLockedChatIds().size
    ))

    init {
        // .catch is required: viewModelScope has no CoroutineExceptionHandler, so an
        // upstream Firestore/RTDB failure would otherwise crash the process.
        observeCurrentUserUseCase()
            .onEach { user ->
                setState { copy(currentUser = user) }
            }
            .catch { e ->
                Log.e(TAG, "Failed to observe current user", e)
                setState { copy(error = e.message) }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.SignOut -> signOut()
            SettingsEvent.DeleteAccount -> deleteAccount()
            is SettingsEvent.ToggleDarkMode -> {
                themeManager.setDarkMode(event.enabled)
                setState { copy(isDarkMode = event.enabled, themeMode = themeManager.themeMode.value) }
            }
            is SettingsEvent.SetThemeMode -> {
                themeManager.setThemeMode(event.mode)
                setState { copy(themeMode = event.mode, isDarkMode = themeManager.isDarkMode.value, showThemeDialog = false) }
            }
            SettingsEvent.ShowThemeDialog -> setState { copy(showThemeDialog = true) }
            SettingsEvent.DismissThemeDialog -> setState { copy(showThemeDialog = false) }
            SettingsEvent.ShowFontSizeDialog -> setState { copy(showFontSizeDialog = true) }
            SettingsEvent.DismissFontSizeDialog -> setState { copy(showFontSizeDialog = false) }
            is SettingsEvent.SetFontSize -> {
                themeManager.setFontSize(event.size)
                setState { copy(fontSize = event.size, showFontSizeDialog = false) }
            }
            is SettingsEvent.LinkWebDevice -> {
                val qrContent = event.qrCodeContent
                val token = if (qrContent.startsWith("openchat://link?token=")) {
                    qrContent.substring("openchat://link?token=".length)
                } else {
                    try {
                        val uri = android.net.Uri.parse(qrContent)
                        uri.getQueryParameter("token") ?: qrContent
                    } catch (e: Exception) {
                        qrContent
                    }
                }
                linkWebDevice(token)
            }
            is SettingsEvent.LinkWebDeviceFromDeepLink -> {
                linkWebDevice(event.token)
            }
            SettingsEvent.RefreshLockState -> refreshLockState()
        }
    }

    private fun refreshLockState() {
        setState {
            copy(
                isAppLockEnabled = appLockRepository.isAppLockEnabled(),
                isChatLockEnabled = chatLockRepository.isChatLockEnabled(),
                lockedChatCount = chatLockRepository.getLockedChatIds().size,
                error = null
            )
        }
    }

    private fun signOut() {
        viewModelScope.launch {
            setState { copy(isSigningOut = true) }
            try {
                authRepository.signOut()
                firebaseAuth.signOut()
                setState { copy(isSigningOut = false) }
                sendEffect(SettingsEffect.NavigateToAuth)
            } catch (e: Exception) {
                setState { copy(isSigningOut = false, error = e.message) }
                sendEffect(SettingsEffect.ShowError("Sign out failed: ${e.message}"))
            }
        }
    }

    private fun deleteAccount() {
        viewModelScope.launch {
            try {
                authRepository.deleteAccount()
                firebaseAuth.signOut()
                sendEffect(SettingsEffect.NavigateToAuth)
            } catch (e: Exception) {
                sendEffect(SettingsEffect.ShowError("Delete account failed: ${e.message}"))
            }
        }
    }

    private fun linkWebDevice(token: String) {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (token.isNotEmpty() && currentUserId != null) {
            viewModelScope.launch {
                try {
                    val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                    val sessionRef = database.getReference("sessions").child(token)
                    val updates = mapOf(
                        "status" to "authorized",
                        "authenticatedUid" to currentUserId,
                        "authorizedAt" to System.currentTimeMillis()
                    )
                    sessionRef.updateChildren(updates).await()
                    sendEffect(SettingsEffect.DeviceLinked)
                } catch (e: Exception) {
                    Log.e("SettingsViewModel", "Failed to link web device", e)
                    sendEffect(SettingsEffect.ShowError("Linking failed: ${e.message}"))
                }
            }
        } else {
            sendEffect(SettingsEffect.ShowError("Invalid token or user not authenticated"))
        }
    }
}
