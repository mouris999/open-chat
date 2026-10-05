package com.openchat.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.openchat.app.data.local.entity.ChatEntity
import com.openchat.app.data.local.entity.ChatMemberEntity
import com.openchat.app.data.local.entity.ChatWithMembers
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Transaction
    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun observeAllChats(): Flow<List<ChatWithMembers>>

    @Transaction
    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: String): ChatWithMembers?

    @Transaction
    @Query("SELECT * FROM chats WHERE id = :chatId")
    fun observeChat(chatId: String): Flow<ChatWithMembers?>

    @Query("SELECT * FROM chats WHERE type = 'PRIVATE' AND id IN (SELECT chatId FROM chat_members WHERE userId = :userId)")
    suspend fun getPrivateChatWithUser(userId: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE isPinned = 1 ORDER BY updatedAt DESC")
    fun observePinnedChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE isMuted = 1")
    fun observeMutedChats(): Flow<List<ChatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: ChatMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<ChatMemberEntity>)

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("UPDATE chats SET unreadCount = :count WHERE id = :chatId")
    suspend fun updateUnreadCount(chatId: String, count: Int)

    @Query("UPDATE chats SET isPinned = :isPinned WHERE id = :chatId")
    suspend fun updatePinStatus(chatId: String, isPinned: Boolean)

    @Query("UPDATE chats SET isMuted = :isMuted WHERE id = :chatId")
    suspend fun updateMuteStatus(chatId: String, isMuted: Boolean)

    @Query("UPDATE chats SET lastMessageId = :messageId, lastMessageText = :text, lastMessageTime = :timestamp WHERE id = :chatId")
    suspend fun updateLastMessage(chatId: String, messageId: String, text: String?, timestamp: Long)

    @Delete
    suspend fun deleteChat(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: String)

    @Query("DELETE FROM chat_members WHERE chatId = :chatId AND userId = :userId")
    suspend fun removeMember(chatId: String, userId: String)

    @Query("DELETE FROM chats")
    suspend fun clearAllChats()

    @Query("DELETE FROM chat_members")
    suspend fun clearAllMembers()
}
