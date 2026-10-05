package com.openchat.app.domain.repository

import com.openchat.app.core.Result
import com.openchat.app.data.model.Chat
import com.openchat.app.data.model.ChatType
import com.openchat.app.data.model.Message
import com.openchat.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeChats(): Flow<List<Chat>>
    fun observeChat(chatId: String): Flow<Chat?>
    suspend fun getChat(chatId: String): Result<Chat>
    suspend fun createPrivateChat(userId: String): Result<Chat>
    suspend fun createGroupChat(title: String, members: List<String>): Result<Chat>
    suspend fun createChannel(title: String, description: String?): Result<Chat>
    suspend fun updateChat(chat: Chat): Result<Unit>
    suspend fun deleteChat(chatId: String): Result<Unit>
    suspend fun pinChat(chatId: String, isPinned: Boolean): Result<Unit>
    suspend fun muteChat(chatId: String, isMuted: Boolean): Result<Unit>
    suspend fun markChatAsRead(chatId: String): Result<Unit>
    suspend fun getPrivateChatWithUser(userId: String): Chat?
    suspend fun addMemberToChat(chatId: String, userId: String): Result<Unit>
    suspend fun removeMemberFromChat(chatId: String, userId: String): Result<Unit>
    suspend fun updateMemberRole(chatId: String, userId: String, role: com.openchat.app.domain.model.MemberRole): Result<Unit>
    suspend fun leaveChat(chatId: String): Result<Unit>
    suspend fun clearUserData(): Result<Unit>
}
