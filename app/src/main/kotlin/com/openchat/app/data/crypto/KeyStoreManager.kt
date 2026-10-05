package com.openchat.app.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.openchat.app.core.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val androidKeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context, Constants.KEYSTORE_ALIAS)
            .setKeyGenParameterSpec(
                KeyGenParameterSpec.Builder(
                    Constants.KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            .build()
    }
    
    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    fun generateKeyPair(alias: String) {
        if (!androidKeyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGenerator.generateKey()
        }
    }
    
    fun getSecretKey(alias: String): SecretKey? {
        return (androidKeyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }
    
    fun storeEncryptedData(key: String, data: ByteArray) {
        encryptedPrefs.edit().putString(key, android.util.Base64.encodeToString(data, android.util.Base64.DEFAULT)).apply()
    }
    
    fun retrieveEncryptedData(key: String): ByteArray? {
        val encoded = encryptedPrefs.getString(key, null) ?: return null
        return android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
    }
    
    fun removeKey(alias: String) {
        androidKeyStore.deleteEntry(alias)
    }
    
    fun clearAllKeys() {
        androidKeyStore.aliases().toList().forEach { alias ->
            androidKeyStore.deleteEntry(alias)
        }
        encryptedPrefs.edit().clear().apply()
    }
    
    fun hasKey(alias: String): Boolean {
        return androidKeyStore.containsAlias(alias)
    }
}
