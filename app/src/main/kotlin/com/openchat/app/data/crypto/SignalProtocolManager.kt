package com.openchat.app.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.openchat.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

/**
 * WARNING: This is a STUB implementation of end-to-end encryption.
 * Messages are encrypted with a locally-generated AES-256-GCM key stored in
 * Android Keystore, but there is NO actual key exchange (X3DH), NO forward
 * secrecy (no ratchet), and both participants share the same symmetric key.
 *
 * In production, integrate the Signal Protocol library (libsignal-client) for
 * proper X3DH key exchange, Double Ratchet, and sealed sender support.
 */
@Singleton
class SignalProtocolManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineContext
) {
    private var isInitialized = false
    private val userIdKeyMap = mutableMapOf<String, String>() // userId -> keyAlias

    suspend fun initializeProtocolStore(userId: String) = withContext(ioDispatcher) {
        val keyAlias = "signal_key_${userId}"
        userIdKeyMap[userId] = keyAlias
        if (!keyExists(keyAlias)) {
            generateKey(keyAlias)
        }
        isInitialized = true
    }

    suspend fun encryptMessage(recipientId: String, deviceId: Int, plaintext: String): ByteArray = withContext(ioDispatcher) {
        if (!isInitialized) throw IllegalStateException("Protocol store not initialized")
        val keyAlias = userIdKeyMap[recipientId] ?: "signal_key_${recipientId}"
        val secretKey = loadKey(keyAlias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // Prepend IV to ciphertext
        iv + encrypted
    }

    suspend fun decryptMessage(senderId: String, deviceId: Int, ciphertext: ByteArray): String = withContext(ioDispatcher) {
        if (!isInitialized) throw IllegalStateException("Protocol store not initialized")
        val keyAlias = userIdKeyMap[senderId] ?: "signal_key_${senderId}"
        val secretKey = loadKey(keyAlias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ciphertext.copyOfRange(0, 12)
        val encrypted = ciphertext.copyOfRange(12, ciphertext.size)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    suspend fun establishSession(recipientId: String, deviceId: Int, preKeyBundle: PreKeyBundle) = withContext(ioDispatcher) {
        if (!isInitialized) throw IllegalStateException("Protocol store not initialized")
        val keyAlias = "signal_key_${recipientId}"
        if (!keyExists(keyAlias)) {
            generateKey(keyAlias)
        }
    }

    fun getIdentityKeyPair(): IdentityKeyPair? = null
    fun getRegistrationId(): Int? = null
    fun getPreKeys(): List<PreKeyRecord> = emptyList()
    fun getSignedPreKey(): SignedPreKeyRecord? = null
    fun generateNewPreKeys(startId: Int, count: Int): List<PreKeyRecord> = emptyList()
    fun clearProtocolStore() { isInitialized = false }

    private fun keyExists(alias: String): Boolean {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return keyStore.containsAlias(alias)
    }

    private fun generateKey(alias: String) {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        keyGenerator.generateKey()
    }

    private fun loadKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            ?: throw IllegalStateException("Key entry for '$alias' is not a SecretKeyEntry or does not exist")
        return entry.secretKey
    }
}

// Stub data classes (kept for API compatibility)
data class PreKeyBundle(
    val registrationId: Int,
    val deviceId: Int,
    val preKeyId: Int,
    val preKeyPublic: ByteArray,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: ByteArray,
    val signedPreKeySignature: ByteArray,
    val identityKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreKeyBundle) return false
        if (registrationId != other.registrationId) return false
        if (deviceId != other.deviceId) return false
        if (preKeyId != other.preKeyId) return false
        if (signedPreKeyId != other.signedPreKeyId) return false
        if (!preKeyPublic.contentEquals(other.preKeyPublic)) return false
        if (!signedPreKeyPublic.contentEquals(other.signedPreKeyPublic)) return false
        if (!signedPreKeySignature.contentEquals(other.signedPreKeySignature)) return false
        return identityKey.contentEquals(other.identityKey)
    }

    override fun hashCode(): Int {
        var result = registrationId
        result = 31 * result + deviceId
        result = 31 * result + preKeyId
        result = 31 * result + preKeyPublic.contentHashCode()
        result = 31 * result + signedPreKeyId
        result = 31 * result + signedPreKeyPublic.contentHashCode()
        result = 31 * result + signedPreKeySignature.contentHashCode()
        result = 31 * result + identityKey.contentHashCode()
        return result
    }
}

data class IdentityKeyPair(val publicKey: ByteArray)
data class PreKeyRecord(val id: Int, val publicKey: ByteArray)
data class SignedPreKeyRecord(val id: Int, val publicKey: ByteArray, val signature: ByteArray)
