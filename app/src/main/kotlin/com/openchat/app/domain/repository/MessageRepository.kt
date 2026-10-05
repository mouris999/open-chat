package com.openchat.app.domain.repository

import androidx.paging.PagingData
import com.openchat.app.core.Result
import com.openchat.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun observeMessages(chatId: String): Flow<List<Message>>
    fun getMessagesPaging(chatId: String): Flow<PagingData<Message>>
    suspend fun sendMessage(
        chatId: String,
        content: String? = null,
        mediaUri: android.net.Uri? = null,
        mediaType: com.openchat.app.domain.model.MediaType? = null,
        replyToMessageId: String? = null
    ): Result<Message>
    suspend fun sendImageMessage(chatId: String, imageUri: android.net.Uri, caption: String? = null): Result<Message>
    suspend fun sendVideoMessage(chatId: String, videoUri: android.net.Uri, caption: String? = null): Result<Message>
    suspend fun sendVoiceMessage(chatId: String, audioUri: android.net.Uri, duration: Int): Result<Message>
    suspend fun sendLocationMessage(chatId: String, latitude: Double, longitude: Double): Result<Message>
    suspend fun editMessage(messageId: String, newContent: String): Result<Unit>
    suspend fun deleteMessage(messageId: String, deleteForEveryone: Boolean = false): Result<Unit>
    suspend fun pinMessage(messageId: String, isPinned: Boolean): Result<Unit>
    suspend fun forwardMessage(messageId: String, toChatId: String): Result<Message>
    suspend fun addReaction(messageId: String, emoji: String): Result<Unit>
    suspend fun removeReaction(messageId: String, emoji: String): Result<Unit>
    suspend fun searchMessages(chatId: String, query: String): List<Message>
    suspend fun searchMessagesGlobal(query: String): List<Message>
    suspend fun markMessageAsRead(messageId: String): Result<Unit>
}
