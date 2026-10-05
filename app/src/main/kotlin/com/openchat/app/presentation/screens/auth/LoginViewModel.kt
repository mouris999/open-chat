package com.openchat.app.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openchat.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var storedVerificationId: String? = null

    fun signInWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Email and password are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.signInWithEmail(email, password)
            when (result) {
                is com.openchat.app.core.Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, isSignedIn = true)
                }
                is com.openchat.app.core.Result.Error -> {
                    val errorMessage = result.message ?: "Sign in failed"
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMessage)
                }
                is com.openchat.app.core.Result.Loading -> { }
            }
        }
    }

    fun signUpWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Email and password are required")
            return
        }

        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.signUpWithEmail(email, password)
            when (result) {
                is com.openchat.app.core.Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, isSignedIn = true)
                }
                is com.openchat.app.core.Result.Error -> {
                    val errorMessage = result.message ?: "Sign up failed"
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMessage)
                }
                is com.openchat.app.core.Result.Loading -> { }
            }
        }
    }

    fun sendVerificationCode(phoneNumber: String, activity: android.app.Activity) {
        if (phoneNumber.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Phone number is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.sendPhoneVerification(phoneNumber)
            when (result) {
                is com.openchat.app.core.Result.Success -> {
                    val verificationId = result.data
                    storedVerificationId = verificationId
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        verificationId = verificationId,
                        phoneNumber = phoneNumber
                    )
                }
                is com.openchat.app.core.Result.Error -> {
                    val errorMessage = result.message ?: "Failed to send verification code"
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMessage)
                }
                is com.openchat.app.core.Result.Loading -> { }
            }
        }
    }

    fun verifyPhoneCode(verificationCode: String) {
        if (storedVerificationId == null) {
            _uiState.value = _uiState.value.copy(error = "No verification in progress")
            return
        }

        if (verificationCode.length != 6) {
            _uiState.value = _uiState.value.copy(error = "Please enter 6-digit code")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            // Re-checked inside the coroutine: if the field was nulled between the
            // guard above and here (rotation / process death), the bare `return@launch`
            // left isLoading stuck true forever.
            val verificationId = storedVerificationId
            if (verificationId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "No verification in progress")
                return@launch
            }
            val result = authRepository.verifyPhoneCode(verificationId, verificationCode)
            when (result) {
                is com.openchat.app.core.Result.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, isSignedIn = true)
                }
                is com.openchat.app.core.Result.Error -> {
                    val errorMessage = result.message ?: "Verification failed"
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMessage)
                }
                is com.openchat.app.core.Result.Loading -> { }
            }
        }
    }

    fun resendVerificationCode(phoneNumber: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.sendPhoneVerification(phoneNumber)
            when (result) {
                is com.openchat.app.core.Result.Success -> {
                    val verificationId = result.data
                    storedVerificationId = verificationId
                    _uiState.value = _uiState.value.copy(isLoading = false, verificationId = verificationId)
                }
                is com.openchat.app.core.Result.Error -> {
                    val errorMessage = result.message ?: "Failed to resend code"
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMessage)
                }
                is com.openchat.app.core.Result.Loading -> { }
            }
        }
    }

    /**
     * Handle T-Auth OAuth callback result.
     * Call this from onActivityResult after TAuthActivity completes.
     */
    fun handleTAuthResult(accessToken: String?, refreshToken: String?, userBundle: android.os.Bundle?) {
        if (accessToken == null || userBundle == null) {
            _uiState.value = _uiState.value.copy(error = "T-Auth sign in failed")
            return
        }

        // Both of these previously did a bare `return`, which made T-Auth login
        // silently do nothing - no error, no state change.
        val userId = userBundle.getString("id")
        if (userId.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(error = "T-Auth profile is missing an id")
            return
        }
        val email = userBundle.getString("email")
        if (email.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(error = "T-Auth profile is missing an email")
            return
        }
        val name = userBundle.getString("name") ?: email.substringBefore("@")

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // Save user to Firebase/Firestore as well for compatibility
                val result = authRepository.signInWithTAuth(userId, email, name)
                when (result) {
                    is com.openchat.app.core.Result.Success -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, isSignedIn = true)
                    }
                    is com.openchat.app.core.Result.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.message ?: "T-Auth sign in failed"
                        )
                    }
                    is com.openchat.app.core.Result.Loading -> { }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "T-Auth sign in failed"
                )
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = authRepository.signInWithGoogle(idToken)
            when (result) {
                is com.openchat.app.core.Result.Success -> {
                    val needsProfileSetup = result.data
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedIn = !needsProfileSetup,
                        needsProfileSetup = needsProfileSetup
                    )
                }
                is com.openchat.app.core.Result.Error -> {
                    val errorMessage = result.message ?: "Google sign in failed"
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMessage)
                }
                is com.openchat.app.core.Result.Loading -> { }
            }
        }
    }

    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }


    data class LoginUiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val isSignedIn: Boolean = false,
        val needsProfileSetup: Boolean = false,
        val verificationId: String? = null,
        val phoneNumber: String? = null
    )
}
