package com.openchat.app.domain.repository

sealed class AuthResult {
    data object Authorized : AuthResult()
    data object WrongPassword : AuthResult()
    data object NotConfigured : AuthResult()
    data object BiometricFailed : AuthResult()
    data object BiometricNotAvailable : AuthResult()
}

enum class AuthMode {
    NONE, STANDARD, PRIVATE
}

interface AppLockRepository {
    fun isAppLockEnabled(): Boolean
    fun setAppLockEnabled(enabled: Boolean)
    fun isPrimaryPasswordSet(): Boolean
    fun isSecondaryPasswordSet(): Boolean
    fun setPrimaryPassword(password: String)
    fun setSecondaryPassword(password: String)
    fun verifyPrimaryPassword(password: String): AuthResult
    fun verifySecondaryPassword(password: String): AuthResult
    fun clearPasswords()
    fun getCurrentAuthMode(): AuthMode
    fun setCurrentAuthMode(mode: AuthMode)
    fun isHiddenUserModeEnabled(): Boolean
    fun setHiddenUserModeEnabled(enabled: Boolean)
    fun isBiometricAvailable(): Boolean
    fun isBiometricEnrolled(): Boolean
}
