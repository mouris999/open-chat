package com.openchat.app.domain.repository

import com.openchat.app.core.Result
import com.openchat.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeCurrentUser(): Flow<User?>
    suspend fun getCurrentUser(): User?
    suspend fun getUser(userId: String): Result<User>
    suspend fun getUsers(userIds: List<String>): Result<List<User>>
    suspend fun updateProfile(displayName: String? = null, bio: String? = null, photoUri: android.net.Uri? = null): Result<Unit>
    suspend fun updateUsername(username: String): Result<Unit>
    suspend fun searchUsers(query: String): Result<List<User>>
    suspend fun updateOnlineStatus(isOnline: Boolean): Result<Unit>
    fun observeUserOnlineStatus(userId: String): Flow<Boolean>
    fun observeUserLastSeen(userId: String): Flow<Long?>
}
