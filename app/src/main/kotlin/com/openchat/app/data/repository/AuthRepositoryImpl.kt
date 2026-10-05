package com.openchat.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.openchat.app.core.Result
import com.openchat.app.core.safeApiCall
import com.openchat.app.data.api.AuthApiService
import com.openchat.app.data.model.*
import com.openchat.app.di.AuthPrefs
import com.openchat.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @AuthPrefs private val encryptedPreferences: android.content.SharedPreferences,
    private val authApiService: AuthApiService,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val chatRepository: com.openchat.app.domain.repository.ChatRepository
) : AuthRepository {

    private val _authState = MutableStateFlow(isAuthenticated())
    private val authState: Flow<Boolean> = _authState.asStateFlow()

    init {
        // Without this, observeAuthState() only ever reflected our own signIn/signOut
        // calls: a session revoked or expired by Firebase, or one established
        // elsewhere, still reported "authenticated" to every consumer.
        firebaseAuth.addAuthStateListener { auth ->
            _authState.value = auth.currentUser != null
        }
    }

    companion object {
        private const val PREF_USER_ID = "user_id"
        private const val PREF_AUTH_TOKEN = "auth_token"
        private const val PREF_USER_NAME = "user_name"
        private const val PREF_USER_EMAIL = "user_email"
    }

    override fun isAuthenticated(): Boolean {
        return encryptedPreferences.getString(PREF_AUTH_TOKEN, null) != null
    }

    override fun observeAuthState(): Flow<Boolean> = authState

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> = safeApiCall {
        val response = authApiService.login(LoginRequest(email, password))

        encryptedPreferences.edit()
            .putString(PREF_USER_ID, response.user.id)
            .putString(PREF_AUTH_TOKEN, response.token)
            .putString(PREF_USER_EMAIL, response.user.email)
            .putString(PREF_USER_NAME, response.user.name)
            .apply()

        _authState.value = true
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<Unit> = safeApiCall {
        val response = authApiService.register(RegisterRequest(email, password))

        encryptedPreferences.edit()
            .putString(PREF_USER_ID, response.user.id)
            .putString(PREF_AUTH_TOKEN, response.token)
            .putString(PREF_USER_EMAIL, response.user.email)
            .putString(PREF_USER_NAME, response.user.name)
            .apply()

        _authState.value = true
    }

    override suspend fun sendPhoneVerification(phoneNumber: String): Result<String> = safeApiCall {
        val response = authApiService.sendPhoneVerification(PhoneVerificationRequest(phoneNumber))
        response.verificationId
    }

    override suspend fun verifyPhoneCode(verificationId: String, code: String): Result<Unit> = safeApiCall {
        val response = authApiService.verifyPhone(VerifyPhoneRequest(verificationId, code))

        encryptedPreferences.edit()
            .putString(PREF_USER_ID, response.user.id)
            .putString(PREF_AUTH_TOKEN, response.token)
            .putString(PREF_USER_EMAIL, response.user.email)
            .putString(PREF_USER_NAME, response.user.name)
            .apply()

        _authState.value = true
    }

    override suspend fun signOut(): Result<Unit> = safeApiCall {
        // Clear chat data and caches
        chatRepository.clearUserData()

        // Sign out from Firebase Auth
        firebaseAuth.signOut()

        // Clear local preferences
        encryptedPreferences.edit()
            .remove(PREF_USER_ID)
            .remove(PREF_AUTH_TOKEN)
            .remove(PREF_USER_NAME)
            .remove(PREF_USER_EMAIL)
            .apply()

        _authState.value = false
        android.util.Log.d("AuthRepository", "User signed out successfully")
    }

    override suspend fun deleteAccount(): Result<Unit> = safeApiCall {
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        try {
            // Delete user data from Firestore
            firestore.collection("users").document(userId).delete().await()
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to delete Firestore user data", e)
        }
        try {
            // Delete user from Firebase Auth
            firebaseAuth.currentUser?.delete()?.await()
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to delete Firebase Auth user", e)
        }
        // signOut() returns a Result. Discarding it reported a failed sign-out as a
        // successful account deletion.
        val signOutResult = signOut()
        if (signOutResult is Result.Error) throw signOutResult.exception
    }

    override suspend fun updateProfile(name: String, photoUrl: String?): Result<Unit> = safeApiCall {
        val userId = firebaseAuth.currentUser?.uid
        if (userId != null) {
            try {
                val updates = hashMapOf<String, Any>("displayName" to name, "updatedAt" to System.currentTimeMillis())
                photoUrl?.let { updates["photoUrl"] = it }
                firestore.collection("users").document(userId)
                    .set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.e("AuthRepository", "Failed to update Firestore profile", e)
            }
        }
        encryptedPreferences.edit()
            .putString(PREF_USER_NAME, name)
            .apply()
    }

    override fun getCurrentUserId(): String? {
        return encryptedPreferences.getString(PREF_USER_ID, null)
    }

    override suspend fun signInWithTAuth(userId: String, email: String, name: String): Result<Unit> = safeApiCall {
        // Store T-Auth user data in shared preferences
        encryptedPreferences.edit()
            .putString(PREF_USER_ID, userId)
            .putString(PREF_USER_EMAIL, email)
            .putString(PREF_USER_NAME, name)
            .putString(PREF_AUTH_TOKEN, "tauth_token") // T-Auth manages its own tokens
            .apply()

        _authState.value = true
    }

    override suspend fun signInWithGoogle(idToken: String): Result<Boolean> = safeApiCall {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = firebaseAuth.signInWithCredential(credential).await()
        val user = authResult.user ?: throw Exception("Google sign in failed")

        var displayName = user.displayName ?: user.email?.substringBefore("@") ?: "User"
        var needsProfileSetup = true

        try {
            // Check if user exists in Firestore
            val userDoc = firestore.collection("users").document(user.uid).get().await()
            val exists = userDoc.exists()

            if (exists) {
                val firestoreName = userDoc.getString("displayName")
                if (!firestoreName.isNullOrBlank()) {
                    displayName = firestoreName
                    needsProfileSetup = false
                }
            }
        } catch (e: Exception) {
            // If Firestore fails (e.g. offline, API disabled, permissions), 
            // we catch the exception so the user can still log in.
            // They will just be directed to the profile setup screen.
            e.printStackTrace()
        }

        // Store user data in shared preferences
        encryptedPreferences.edit()
            .putString(PREF_USER_ID, user.uid)
            .putString(PREF_AUTH_TOKEN, "firebase_token")
            .putString(PREF_USER_EMAIL, user.email ?: "")
            .putString(PREF_USER_NAME, displayName)
            .apply()

        _authState.value = true
        needsProfileSetup
    }
}
