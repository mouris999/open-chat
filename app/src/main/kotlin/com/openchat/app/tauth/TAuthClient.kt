package com.openchat.app.tauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * T-Auth Client SDK for OAuth 2.0 authentication
 * 
 * Usage:
 * ```
 * // Initialize in Application.onCreate() or first Activity
 * TAuthClient.getInstance(context).init(
 *     serverUrl = "https://your-t-auth-server.com",
 *     clientId = "your_client_id",
 *     redirectUri = "tauth://callback"
 * )
 * 
 * // Launch login flow
 * TAuthActivity.launch(activity, REQUEST_CODE)
 * 
 * // Check login status
 * if (TAuthClient.getInstance(context).isLoggedIn()) { ... }
 * 
 * // Get current user
 * val user = TAuthClient.getInstance(context).getCurrentUser()
 * 
 * // Logout
 * TAuthClient.getInstance(context).logout()
 * ```
 */
class TAuthClient private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            appContext,
            "tauth_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        @Volatile
        private var instance: TAuthClient? = null
        
        fun getInstance(context: Context): TAuthClient {
            return instance ?: synchronized(this) {
                instance ?: TAuthClient(context).also { instance = it }
            }
        }
        
        const val EXTRA_USER = "tauth_user"
        const val EXTRA_ACCESS_TOKEN = "tauth_access_token"
        const val EXTRA_REFRESH_TOKEN = "tauth_refresh_token"
        const val RESULT_SUCCESS = 1
        const val RESULT_CANCELLED = 0
        const val RESULT_ERROR = -1
    }

    // Configuration
    private var serverUrl: String = ""
    private var clientId: String = ""
    private var redirectUri: String = ""
    private var pkceCodeVerifier: String = ""

    /**
     * Initialize the T-Auth client with your server configuration.
     * Call this once before using any other methods.
     */
    fun init(serverUrl: String, clientId: String, redirectUri: String = "tauth://callback") {
        this.serverUrl = serverUrl.trimEnd('/')
        this.clientId = clientId
        this.redirectUri = redirectUri
        
        // Validate initialization
        require(serverUrl.isNotBlank()) { "Server URL cannot be empty" }
        require(clientId.isNotBlank()) { "Client ID cannot be empty" }
        require(redirectUri.isNotBlank()) { "Redirect URI cannot be empty" }
    }

    /**
     * Build the OAuth authorization URL with PKCE.
     * This URL should be opened in TAuthActivity's WebView.
     */
    internal fun buildAuthUrl(): String {
        validateInitialized()
        
        // Generate PKCE code verifier
        pkceCodeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(pkceCodeVerifier)
        
        // Store code verifier for later use
        prefs.edit().putString("pkce_verifier", pkceCodeVerifier).apply()
        
        return buildString {
            append(serverUrl)
            append("/oauth/authorize")
            append("?client_id=").append(Uri.encode(clientId))
            append("&redirect_uri=").append(Uri.encode(redirectUri))
            append("&response_type=code")
            append("&code_challenge=").append(Uri.encode(codeChallenge))
            append("&code_challenge_method=S256")
            append("&scope=openid%20profile%20email")
        }
    }

    /**
     * Exchange authorization code for access and refresh tokens.
     * This is called internally by TAuthActivity after successful login.
     */
    internal suspend fun exchangeCode(code: String): Result<TokenResponse> = withContext(Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        try {
            val storedVerifier = prefs.getString("pkce_verifier", "") 
                ?: return@withContext Result.failure(Exception("PKCE verifier not found"))
            
            val url = java.net.URL("$serverUrl/oauth/token")
            connection = url.openConnection() as java.net.HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            
            val params = buildString {
                append("grant_type=authorization_code")
                append("&code=").append(Uri.encode(code))
                append("&client_id=").append(Uri.encode(clientId))
                append("&redirect_uri=").append(Uri.encode(redirectUri))
                append("&code_verifier=").append(Uri.encode(storedVerifier))
            }
            
            connection.outputStream.use { it.write(params.toByteArray()) }
            
            val responseCode = connection.responseCode
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            
            if (responseCode == 200) {
                val tokenResponse = json.decodeFromString<TokenResponse>(response)
                saveTokens(tokenResponse)
                Result.success(tokenResponse)
            } else {
                Result.failure(Exception("Token exchange failed: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Get current user info using the access token.
     */
    suspend fun getUserInfo(): Result<UserInfo> = withContext(Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        try {
            val accessToken = getAccessToken()
                ?: return@withContext Result.failure(Exception("Not logged in"))
            
            val url = java.net.URL("$serverUrl/oauth/userinfo")
            connection = url.openConnection() as java.net.HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            
            val responseCode = connection.responseCode
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            
            if (responseCode == 200) {
                val userInfo = json.decodeFromString<UserInfo>(response)
                prefs.edit().putString("user_info", json.encodeToString(UserInfo.serializer(), userInfo)).apply()
                Result.success(userInfo)
            } else if (responseCode == 401) {
                refreshToken().onSuccess {
                    return@withContext getUserInfo()
                }.onFailure {
                    return@withContext Result.failure(it)
                }
                Result.failure(Exception("Session expired"))
            } else {
                Result.failure(Exception("Failed to get user info: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Refresh the access token using the refresh token.
     */
    suspend fun refreshToken(): Result<TokenResponse> = withContext(Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        try {
            val refreshToken = getRefreshToken()
                ?: return@withContext Result.failure(Exception("No refresh token available"))
            
            val url = java.net.URL("$serverUrl/auth/refresh")
            connection = url.openConnection() as java.net.HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val jsonBody = """{"refresh_token":"$refreshToken"}"""
            connection.outputStream.use { it.write(jsonBody.toByteArray()) }
            
            val responseCode = connection.responseCode
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            
            if (responseCode == 200) {
                val tokenResponse = json.decodeFromString<TokenResponse>(response)
                saveTokens(tokenResponse)
                Result.success(tokenResponse)
            } else {
                clearTokens()
                Result.failure(Exception("Token refresh failed: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Logout the current user and revoke tokens.
     */
    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        var connection: java.net.HttpURLConnection? = null
        try {
            val refreshToken = getRefreshToken()
            
            if (refreshToken != null) {
                val url = java.net.URL("$serverUrl/auth/logout")
                connection = url.openConnection() as java.net.HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                
                val jsonBody = """{"refresh_token":"$refreshToken"}"""
                connection.outputStream.use { it.write(jsonBody.toByteArray()) }
                
                connection.responseCode
            }
            
            clearTokens()
            Result.success(Unit)
        } catch (e: Exception) {
            clearTokens()
            Result.success(Unit)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Check if user is currently logged in.
     */
    fun isLoggedIn(): Boolean {
        return getAccessToken() != null
    }

    /**
     * Get the currently logged in user info from cache.
     */
    fun getCurrentUser(): UserInfo? {
        val userJson = prefs.getString("user_info", null) ?: return null
        return try {
            json.decodeFromString(UserInfo.serializer(), userJson)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the access token for API calls.
     */
    fun getAccessToken(): String? {
        return prefs.getString("access_token", null)
    }

    /**
     * Get the refresh token.
     */
    fun getRefreshToken(): String? {
        return prefs.getString("refresh_token", null)
    }

    // Private helper methods
    
    private fun saveTokens(tokenResponse: TokenResponse) {
        prefs.edit()
            .putString("access_token", tokenResponse.accessToken)
            .putString("refresh_token", tokenResponse.refreshToken)
            .apply()
    }

    private fun clearTokens() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("user_info")
            .remove("pkce_verifier")
            .apply()
    }

    private fun validateInitialized() {
        if (serverUrl.isBlank() || clientId.isBlank()) {
            throw IllegalStateException("TAuthClient not initialized. Call init() first.")
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)

@Serializable
data class UserInfo(
    val id: String,
    val email: String,
    val name: String? = null,
    val picture: String? = null
)
