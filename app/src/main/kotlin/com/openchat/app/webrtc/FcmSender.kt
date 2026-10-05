package com.openchat.app.webrtc

import android.util.Base64
import android.util.Log
import com.openchat.app.BuildConfig
import com.google.firebase.database.FirebaseDatabase
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

object FcmSender {
    private const val TAG = "FcmSender"
    private const val FCM_V1_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send"
    private const val SCOPE = "https://www.googleapis.com/auth/firebase.messaging"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var cachedAccessToken: String? = null
    private var tokenExpiry: Date = Date(0)
    private var serviceAccountJson: JSONObject? = null
    private var cachedPrivateKey: PrivateKey? = null
    private var projectId: String? = null
    private var clientEmail: String? = null
    private var tokenUri: String? = null

    private fun loadServiceAccount() {
        if (serviceAccountJson != null) return
        val base64 = BuildConfig.FCM_SERVICE_ACCOUNT_JSON
        if (base64.isBlank()) {
            Log.w(TAG, "FCM_SERVICE_ACCOUNT_JSON not configured. Add app/service-account.json")
            return
        }
        try {
            val jsonBytes = Base64.decode(base64, Base64.DEFAULT)
            val json = JSONObject(String(jsonBytes, Charsets.UTF_8))
            serviceAccountJson = json
            projectId = json.optString("project_id")
            clientEmail = json.optString("client_email")
            tokenUri = json.optString("token_uri")
            val pem = json.optString("private_key")
            if (pem.isNotBlank()) {
                val pemData = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("\\s".toRegex(), "")
                val keyBytes = Base64.decode(pemData, Base64.DEFAULT)
                val keySpec = PKCS8EncodedKeySpec(keyBytes)
                val keyFactory = KeyFactory.getInstance("RSA")
                cachedPrivateKey = keyFactory.generatePrivate(keySpec)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse service account", e)
        }
    }

    private fun getAccessToken(callback: (String?) -> Unit) {
        if (cachedAccessToken != null && Date().before(tokenExpiry)) {
            callback(cachedAccessToken)
            return
        }
        loadServiceAccount()
        val key = cachedPrivateKey ?: run {
            Log.w(TAG, "Service account private key not available")
            callback(null)
            return
        }
        val email = clientEmail ?: run {
            Log.w(TAG, "Service account client_email not available")
            callback(null)
            return
        }
        val uri = tokenUri ?: run {
            Log.w(TAG, "Service account token_uri not available")
            callback(null)
            return
        }

        try {
            val now = System.currentTimeMillis() / 1000
            val header = JSONObject().apply {
                put("alg", "RS256")
                put("typ", "JWT")
            }
            val claims = JSONObject().apply {
                put("iss", email)
                put("scope", SCOPE)
                put("aud", uri)
                put("exp", now + 3600)
                put("iat", now)
            }

            val headerB64 = base64UrlEncode(header.toString().toByteArray())
            val claimsB64 = base64UrlEncode(claims.toString().toByteArray())
            val signingInput = "$headerB64.$claimsB64"

            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(key)
            signature.update(signingInput.toByteArray())
            val sigB64 = base64UrlEncode(signature.sign())

            val jwt = "$signingInput.$sigB64"
            val formBody = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt"
            val request = Request.Builder()
                .url(uri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(formBody.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.e(TAG, "Error getting access token", e)
                    callback(null)
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val body = resp.body?.string() ?: ""
                        if (!resp.isSuccessful) {
                            Log.e(TAG, "Failed to get access token: ${resp.code} $body")
                            callback(null)
                            return
                        }
                        val tokenJson = JSONObject(body)
                        val accessToken = tokenJson.optString("access_token")
                        val expiresIn = tokenJson.optInt("expires_in", 3600)
                        cachedAccessToken = accessToken
                        tokenExpiry = Date(System.currentTimeMillis() + (expiresIn - 60) * 1000L)
                        callback(accessToken)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access token", e)
            callback(null)
        }
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun getFcmToken(receiverUid: String, callback: (String) -> Unit) {
        FirebaseDatabase.getInstance().reference
            .child("fcm_tokens")
            .child(receiverUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val token = snapshot.getValue(String::class.java)
                if (token.isNullOrBlank()) {
                    Log.w(TAG, "No FCM token found for receiver $receiverUid")
                } else {
                    callback(token)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get FCM token for receiver", e)
            }
    }

    private fun sendV1Message(accessToken: String, payload: JSONObject) {
        try {
            val pid = projectId
            if (pid == null) {
                Log.e(TAG, "Project ID not available from service account")
                return
            }
            val url = String.format(FCM_V1_URL, pid)
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json; charset=UTF-8")
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.e(TAG, "FCM v1 send failed", e)
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val responseBody = resp.body?.string() ?: ""
                        Log.d(TAG, "FCM v1 send response: $responseBody")
                        if (!resp.isSuccessful) {
                            Log.e(TAG, "FCM v1 send failed: ${resp.code} $responseBody")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error sending FCM v1 message", e)
        }
    }

    fun sendCallNotification(
        receiverUid: String,
        callId: String,
        callerName: String,
        callerId: String,
        isVideo: Boolean
    ) {
        getAccessToken { accessToken ->
            if (accessToken == null) return@getAccessToken
            getFcmToken(receiverUid) { deviceToken ->
                val data = JSONObject().apply {
                    put("type", "call")
                    put("call_id", callId)
                    put("caller_name", callerName)
                    put("caller_id", callerId)
                    put("is_video", isVideo.toString())
                }
                val message = JSONObject().apply {
                    put("token", deviceToken)
                    put("data", data)
                    put("android", JSONObject().apply {
                        put("priority", "high")
                    })
                }
                sendV1Message(accessToken, JSONObject().apply {
                    put("message", message)
                })
            }
        }
    }

    fun sendMissedCallNotification(
        receiverUid: String,
        callerName: String,
        callerId: String,
        isVideo: Boolean
    ) {
        getAccessToken { accessToken ->
            if (accessToken == null) return@getAccessToken
            getFcmToken(receiverUid) { deviceToken ->
                val data = JSONObject().apply {
                    put("type", "missed_call")
                    put("caller_name", callerName)
                    put("caller_id", callerId)
                    put("is_video", isVideo.toString())
                }
                val notification = JSONObject().apply {
                    put("title", "Missed ${if (isVideo) "Video" else "Voice"} Call")
                    put("body", "Missed call from $callerName")
                }
                val message = JSONObject().apply {
                    put("token", deviceToken)
                    put("data", data)
                    put("notification", notification)
                    put("android", JSONObject().apply {
                        put("priority", "high")
                        put("notification", JSONObject().apply {
                            put("sound", "default")
                        })
                    })
                }
                sendV1Message(accessToken, JSONObject().apply {
                    put("message", message)
                })
            }
        }
    }

    fun sendMessageNotification(
        receiverUid: String,
        chatId: String,
        senderName: String,
        messageContent: String,
        senderId: String
    ) {
        getAccessToken { accessToken ->
            if (accessToken == null) return@getAccessToken
            getFcmToken(receiverUid) { deviceToken ->
                val data = JSONObject().apply {
                    put("type", "message")
                    put("chat_id", chatId)
                    put("sender_name", senderName)
                    put("message", messageContent)
                    put("sender_id", senderId)
                }
                val notification = JSONObject().apply {
                    put("title", senderName)
                    put("body", messageContent)
                }
                val message = JSONObject().apply {
                    put("token", deviceToken)
                    put("data", data)
                    put("notification", notification)
                    put("android", JSONObject().apply {
                        put("priority", "high")
                        put("notification", JSONObject().apply {
                            put("sound", "default")
                        })
                    })
                }
                sendV1Message(accessToken, JSONObject().apply {
                    put("message", message)
                })
            }
        }
    }
}
