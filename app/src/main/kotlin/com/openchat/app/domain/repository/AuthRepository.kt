package com.openchat.app.domain.repository

import com.openchat.app.core.Result
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun isAuthenticated(): Boolean
    fun observeAuthState(): Flow<Boolean>
    suspend fun signInWithEmail(email: String, password: String): Result<Unit>
    suspend fun signUpWithEmail(email: String, password: String): Result<Unit>
    suspend fun sendPhoneVerification(phoneNumber: String): Result<String>
    suspend fun verifyPhoneCode(verificationId: String, code: String): Result<Unit>
    suspend fun signOut(): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun updateProfile(name: String, photoUrl: String? = null): Result<Unit>
    fun getCurrentUserId(): String?
    suspend fun signInWithTAuth(userId: String, email: String, name: String): Result<Unit>
    suspend fun signInWithGoogle(idToken: String): Result<Boolean>
}
