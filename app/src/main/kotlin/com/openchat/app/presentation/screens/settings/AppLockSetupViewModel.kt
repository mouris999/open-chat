package com.openchat.app.presentation.screens.settings

import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.domain.repository.AppLockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

data class AppLockSetupState(
    val isEnabled: Boolean = false,
    val primaryPassword: String = "",
    val secondaryPassword: String = "",
    val confirmSecondaryPassword: String = "",
    val setupStep: SetupStep = SetupStep.INITIAL,
    val error: String? = null,
    val isSaving: Boolean = false
) : UiState

enum class SetupStep {
    INITIAL, SET_PRIMARY, SET_SECONDARY, CONFIRM_SECONDARY, COMPLETE
}

sealed class AppLockSetupEvent : UiEvent {
    data object ToggleEnabled : AppLockSetupEvent()
    data class SetPrimaryPassword(val password: String) : AppLockSetupEvent()
    data class SetSecondaryPassword(val password: String) : AppLockSetupEvent()
    data class SetConfirmSecondaryPassword(val password: String) : AppLockSetupEvent()
    data object SavePrimary : AppLockSetupEvent()
    data object SaveSecondary : AppLockSetupEvent()
    data object SkipSecondary : AppLockSetupEvent()
    data object ClearError : AppLockSetupEvent()
    data object Disable : AppLockSetupEvent()
}

sealed class AppLockSetupEffect : UiEffect {
    data object SetupComplete : AppLockSetupEffect()
    data object SetupSkipped : AppLockSetupEffect()
    data class ShowError(val message: String) : AppLockSetupEffect()
}

@HiltViewModel
class AppLockSetupViewModel @Inject constructor(
    private val appLockRepository: AppLockRepository
) : BaseViewModel<AppLockSetupState, AppLockSetupEvent, AppLockSetupEffect>() {

    override val _uiState = MutableStateFlow(AppLockSetupState(
        isEnabled = appLockRepository.isAppLockEnabled(),
        setupStep = if (appLockRepository.isAppLockEnabled()) SetupStep.COMPLETE else SetupStep.INITIAL
    ))

    override fun onEvent(event: AppLockSetupEvent) {
        when (event) {
            AppLockSetupEvent.ToggleEnabled -> {
                if (_uiState.value.isEnabled) {
                    onEvent(AppLockSetupEvent.Disable)
                } else {
                    setState { copy(setupStep = SetupStep.SET_PRIMARY) }
                }
            }
            is AppLockSetupEvent.SetPrimaryPassword -> setState { copy(primaryPassword = event.password) }
            is AppLockSetupEvent.SetSecondaryPassword -> setState { copy(secondaryPassword = event.password) }
            is AppLockSetupEvent.SetConfirmSecondaryPassword -> setState { copy(confirmSecondaryPassword = event.password) }
            AppLockSetupEvent.SavePrimary -> savePrimary()
            AppLockSetupEvent.SaveSecondary -> saveSecondary()
            AppLockSetupEvent.SkipSecondary -> skipSecondary()
            AppLockSetupEvent.ClearError -> setState { copy(error = null) }
            AppLockSetupEvent.Disable -> disable()
        }
    }

    private fun savePrimary() {
        val password = _uiState.value.primaryPassword
        if (password.length < 4) {
            setState { copy(error = "Password must be at least 4 characters") }
            return
        }
        setState { copy(isSaving = true) }
        appLockRepository.setPrimaryPassword(password)
        setState { copy(isSaving = false, setupStep = SetupStep.SET_SECONDARY) }
    }

    private fun saveSecondary() {
        val secondary = _uiState.value.secondaryPassword
        val confirm = _uiState.value.confirmSecondaryPassword
        if (secondary.length < 4) {
            setState { copy(error = "Password must be at least 4 characters") }
            return
        }
        if (secondary != confirm) {
            setState { copy(error = "Passwords do not match") }
            return
        }
        if (secondary == _uiState.value.primaryPassword) {
            setState { copy(error = "Private password must differ from primary password") }
            return
        }
        setState { copy(isSaving = true) }
        appLockRepository.setSecondaryPassword(secondary)
        appLockRepository.setAppLockEnabled(true)
        setState { copy(isSaving = false, isEnabled = true, setupStep = SetupStep.COMPLETE) }
        sendEffect(AppLockSetupEffect.SetupComplete)
    }

    private fun skipSecondary() {
        appLockRepository.setAppLockEnabled(true)
        setState { copy(isEnabled = true, setupStep = SetupStep.COMPLETE) }
        sendEffect(AppLockSetupEffect.SetupSkipped)
    }

    private fun disable() {
        appLockRepository.clearPasswords()
        appLockRepository.setAppLockEnabled(false)
        setState { copy(isEnabled = false, setupStep = SetupStep.INITIAL, primaryPassword = "", secondaryPassword = "", confirmSecondaryPassword = "") }
    }
}
