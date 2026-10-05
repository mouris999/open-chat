package com.openchat.app.domain.repository

import com.openchat.app.core.Result
import com.openchat.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun observeContacts(): Flow<List<User>>
    fun observeRegisteredContacts(): Flow<List<User>>
    suspend fun syncContacts(): Result<Unit>
    suspend fun findUserByPhoneNumber(phoneNumber: String): Result<User?>
    suspend fun inviteUser(phoneNumber: String): Result<Unit>
    suspend fun blockUser(userId: String): Result<Unit>
    suspend fun unblockUser(userId: String): Result<Unit>
    suspend fun isUserBlocked(userId: String): Boolean
}
