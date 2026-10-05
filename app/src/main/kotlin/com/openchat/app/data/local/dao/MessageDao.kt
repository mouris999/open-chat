package com.openchat.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.openchat.app.data.local.entity.MessageEntity
import com.openchat.app.data.local.entity.MessageReactionEntity
import com.openchat.app.data.local.entity.MessageWithReactions
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Transaction
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun observeMessages(chatId: String): Flow<List<MessageWithReactions>>

    @Transaction
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun getMessagesPagingSource(chatId: String): PagingSource<Int, MessageWithReactions>

    @Transaction
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageWithReactions?

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND status = 'SENDING'")
    suspend fun getPendingMessages(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isPinned = 1 ORDER BY timestamp DESC")
    fun observePinnedMessages(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND type = 'TEXT' AND content LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    suspend fun searchMessages(chatId: String, query: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 50")
    suspend fun searchMessagesGlobal(query: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: com.openchat.app.data.local.entity.MessageStatus)

    @Query("UPDATE messages SET isDeleted = 1 WHERE id = :messageId")
    suspend fun markMessageAsDeleted(messageId: String)

    @Query("UPDATE messages SET isEdited = 1, content = :newContent, editedAt = :editedAt WHERE id = :messageId")
    suspend fun editMessage(messageId: String, newContent: String, editedAt: Long)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChat(chatId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReaction(reaction: MessageReactionEntity)

    @Delete
    suspend fun deleteReaction(reaction: MessageReactionEntity)

    @Query("SELECT * FROM message_reactions WHERE messageId = :messageId")
    fun observeReactions(messageId: String): Flow<List<MessageReactionEntity>>

    @Query("DELETE FROM message_reactions WHERE messageId = :messageId AND userId = :userId AND emoji = :emoji")
    suspend fun removeReaction(messageId: String, userId: String, emoji: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM message_reactions")
    suspend fun clearAllReactions()
}
