package com.openchat.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.hardware.biometrics.BiometricManager
import android.util.Base64
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.openchat.app.di.AppLockPrefs
import com.openchat.app.domain.repository.AppLockRepository
import com.openchat.app.domain.repository.AuthMode
import com.openchat.app.domain.repository.AuthResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockRepositoryImpl @Inject constructor(
    @AppLockPrefs private val prefs: SharedPreferences,
    @ApplicationContext private val context: Context
) : AppLockRepository {

    private val appLockEnabledKey = "app_lock_enabled"
    private val primaryHashKey = "primary_password_hash"
    private val primarySaltKey = "primary_password_salt"
    private val secondaryHashKey = "secondary_password_hash"
    private val secondarySaltKey = "secondary_password_salt"
    private val authModeKey = "current_auth_mode"
    private val hiddenUserModeKey = "hidden_user_mode_enabled"

    override fun isAppLockEnabled(): Boolean {
        return prefs.getBoolean(appLockEnabledKey, false)
    }

    override fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(appLockEnabledKey, enabled).apply()
        if (!enabled) {
            setCurrentAuthMode(AuthMode.NONE)
        }
    }

    override fun isPrimaryPasswordSet(): Boolean {
        return prefs.contains(primaryHashKey)
    }

    override fun isSecondaryPasswordSet(): Boolean {
        return prefs.contains(secondaryHashKey)
    }

    override fun setPrimaryPassword(password: String) {
        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        prefs.edit()
            .putString(primaryHashKey, hash)
            .putString(primarySaltKey, salt)
            .apply()
    }

    override fun setSecondaryPassword(password: String) {
        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        prefs.edit()
            .putString(secondaryHashKey, hash)
            .putString(secondarySaltKey, salt)
            .apply()
    }

    override fun verifyPrimaryPassword(password: String): AuthResult {
        val storedHash = prefs.getString(primaryHashKey, null) ?: return AuthResult.NotConfigured
        val salt = prefs.getString(primarySaltKey, null) ?: return AuthResult.NotConfigured
        val computedHash = hashPassword(password, salt)
        return if (computedHash == storedHash) AuthResult.Authorized else AuthResult.WrongPassword
    }

    override fun verifySecondaryPassword(password: String): AuthResult {
        val storedHash = prefs.getString(secondaryHashKey, null) ?: return AuthResult.NotConfigured
        val salt = prefs.getString(secondarySaltKey, null) ?: return AuthResult.NotConfigured
        val computedHash = hashPassword(password, salt)
        return if (computedHash == storedHash) AuthResult.Authorized else AuthResult.WrongPassword
    }

    override fun clearPasswords() {
        prefs.edit()
            .remove(primaryHashKey)
            .remove(primarySaltKey)
            .remove(secondaryHashKey)
            .remove(secondarySaltKey)
            .remove(appLockEnabledKey)
            .apply()
        setCurrentAuthMode(AuthMode.NONE)
    }

    override fun getCurrentAuthMode(): AuthMode {
        val ordinal = prefs.getInt(authModeKey, AuthMode.NONE.ordinal)
        return AuthMode.entries.getOrElse(ordinal) { AuthMode.NONE }
    }

    override fun setCurrentAuthMode(mode: AuthMode) {
        prefs.edit().putInt(authModeKey, mode.ordinal).apply()
    }

    override fun isHiddenUserModeEnabled(): Boolean {
        return prefs.getBoolean(hiddenUserModeKey, false)
    }

    override fun setHiddenUserModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(hiddenUserModeKey, enabled).apply()
    }

    /**
     * These two were byte-for-byte identical, so callers could not tell "no sensor"
     * from "sensor present but nothing enrolled" - both returned false. Split them:
     * available = hardware exists, enrolled = something is registered with it.
     */
    override fun isBiometricAvailable(): Boolean {
        val canAuthenticate = androidx.biometric.BiometricManager.from(context)
            .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return canAuthenticate != androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE &&
            canAuthenticate != androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
    }

    override fun isBiometricEnrolled(): Boolean {
        val canAuthenticate = androidx.biometric.BiometricManager.from(context)
            .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return canAuthenticate == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun generateSalt(): String {
        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    private fun hashPassword(password: String, salt: String): String {
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, 600000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}
