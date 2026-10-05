package com.tauth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * TAuthClient — Android SDK for T-Auth OAuth 2.0
 *
 * Usage:
 *   val tAuth = TAuthClient.getInstance(context)
 *   tAuth.init("https://t-auth.com", "your_client_id", "tauth://callback")
 */
class TAuthClient private constructor(private val context: Context) {

    private lateinit var serverUrl: String
    lateinit var clientId: String
    lateinit var redirectUri: String
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME      = "tauth_secure_prefs"
        private const val KEY_ACCESS_TOKEN  = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_JSON     = "user_json"
        private const val KEY_CODE_VERIFIER = "code_verifier"

        @Volatile
        private var instance: TAuthClient? = null

        fun getInstance(context: Context): TAuthClient {
            return instance ?: synchronized(this) {
                instance ?: TAuthClient(context.applicationContext).also { instance = it }
            }
        }
    }

    // ─── Init ─────────────────────────────────────────────────────────────────
    fun init(serverUrl: String, clientId: String, redirectUri: String) {
        this.serverUrl   = serverUrl.trimEnd('/')
        this.clientId    = clientId
        this.redirectUri = redirectUri

        // Use EncryptedSharedPreferences for secure token storage
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ─── Build Authorization URL ──────────────────────────────────────────────
    fun buildAuthUrl(scopes: String = "openid profile email"): String {
        val codeVerifier  = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        val state         = generateState()

        // Save verifier so we can use it when exchanging the code
        prefs.edit().putString(KEY_CODE_VERIFIER, codeVerifier).apply()

        return buildString {
            append("$serverUrl/oauth/authorize")
            append("?client_id=$clientId")
            append("&redirect_uri=${java.net.URLEncoder.encode(redirectUri, "UTF-8")}")
            append("&response_type=code")
            append("&scope=${java.net.URLEncoder.encode(scopes, "UTF-8")}")
            append("&state=$state")
            append("&code_challenge=$codeChallenge")
            append("&code_challenge_method=S256")
        }
    }

    // ─── Exchange Code for Tokens ─────────────────────────────────────────────
    suspend fun exchangeCode(code: String): TAuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val codeVerifier = prefs.getString(KEY_CODE_VERIFIER, null)
                    ?: return@withContext TAuthResult.Error("code_verifier missing")

                val body = buildString {
                    append("grant_type=authorization_code")
                    append("&code=${java.net.URLEncoder.encode(code, "UTF-8")}")
                    append("&client_id=$clientId")
                    append("&redirect_uri=${java.net.URLEncoder.encode(redirectUri, "UTF-8")}")
                    append("&code_verifier=$codeVerifier")
                }

                val response = post("$serverUrl/oauth/token", body, isForm = true)

                if (response.isSuccess) {
                    val json = JSONObject(response.body)
                    saveTokens(json.getString("access_token"), json.getString("refresh_token"))
                    prefs.edit().remove(KEY_CODE_VERIFIER).apply()

                    // Fetch user info
                    val user = getUserInfo()
                    TAuthResult.Success(user)
                } else {
                    TAuthResult.Error(response.body)
                }
            } catch (e: Exception) {
                TAuthResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ─── Get User Info ────────────────────────────────────────────────────────
    suspend fun getUserInfo(): TAuthUser? {
        return withContext(Dispatchers.IO) {
            val token = getAccessToken() ?: return@withContext null
            try {
                val response = get("$serverUrl/oauth/userinfo", token)
                if (response.isSuccess) {
                    val json    = JSONObject(response.body)
                    val user = TAuthUser(
                        id       = json.optString("sub"),
                        email    = json.optString("email"),
                        name     = json.optString("name"),
                        picture  = json.optString("picture")
                    )
                    prefs.edit().putString(KEY_USER_JSON, response.body).apply()
                    user
                } else null
            } catch (e: Exception) { null }
        }
    }

    // ─── Token Refresh ────────────────────────────────────────────────────────
    suspend fun refreshToken(): Boolean {
        return withContext(Dispatchers.IO) {
            val refresh = getRefreshToken() ?: return@withContext false
            try {
                val body = "grant_type=refresh_token&refresh_token=${java.net.URLEncoder.encode(refresh, "UTF-8")}&client_id=$clientId"
                val response = post("$serverUrl/oauth/token", body, isForm = true)
                if (response.isSuccess) {
                    val json = JSONObject(response.body)
                    saveTokens(json.getString("access_token"), json.getString("refresh_token"))
                    true
                } else false
            } catch (e: Exception) { false }
        }
    }

    // ─── Logout ───────────────────────────────────────────────────────────────
    fun logout() {
        prefs.edit().clear().apply()
    }

    // ─── State helpers ────────────────────────────────────────────────────────
    fun isLoggedIn(): Boolean = getAccessToken() != null

    fun getCachedUser(): TAuthUser? {
        val json = prefs.getString(KEY_USER_JSON, null) ?: return null
        return try {
            val j = JSONObject(json)
            TAuthUser(j.optString("sub"), j.optString("email"), j.optString("name"), j.optString("picture"))
        } catch (e: Exception) { null }
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    private fun saveTokens(access: String, refresh: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, access)
            .putString(KEY_REFRESH_TOKEN, refresh)
            .apply()
    }

    // ─── PKCE helpers ─────────────────────────────────────────────────────────
    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun generateState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    // ─── HTTP helpers ─────────────────────────────────────────────────────────
    private data class HttpResponse(val code: Int, val body: String) {
        val isSuccess get() = code in 200..299
    }

    private fun get(url: String, token: String): HttpResponse {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        return readResponse(conn)
    }

    private fun post(url: String, body: String, isForm: Boolean = false): HttpResponse {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", if (isForm) "application/x-www-form-urlencoded" else "application/json")
            setRequestProperty("Accept", "application/json")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        return readResponse(conn)
    }

    private fun readResponse(conn: HttpURLConnection): HttpResponse {
        return try {
            val code   = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body   = stream?.bufferedReader()?.readText() ?: ""
            HttpResponse(code, body)
        } finally {
            conn.disconnect()
        }
    }
}

// ─── Data classes ─────────────────────────────────────────────────────────────
data class TAuthUser(
    val id: String,
    val email: String,
    val name: String,
    val picture: String
)

sealed class TAuthResult {
    data class Success(val user: TAuthUser?) : TAuthResult()
    data class Error(val message: String) : TAuthResult()
}
