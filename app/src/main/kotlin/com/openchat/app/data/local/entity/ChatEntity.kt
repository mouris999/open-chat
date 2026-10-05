package com.openchat.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chats",
    indices = [
        Index(value = ["type"]),
        Index(value = ["updatedAt"])
    ]
)
data class ChatEntity(
    @PrimaryKey
    val id: String,
    val type: ChatType,
    val title: String? = null,
    val photoUrl: String? = null,
    val description: String? = null,
    val creatorId: String? = null,
    val isSecret: Boolean = false,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessageId: String? = null,
    val lastMessageText: String? = null,
    val lastMessageTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ChatType {
    PRIVATE, GROUP, CHANNEL
}

@Entity(
    tableName = "chat_members",
    primaryKeys = ["chatId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["userId"])
    ]
)
data class ChatMemberEntity(
    val chatId: String,
    val userId: String,
    val role: MemberRole = MemberRole.MEMBER,
    val joinedAt: Long = System.currentTimeMillis()
)

enum class MemberRole {
    OWNER, ADMIN, MEMBER
}
