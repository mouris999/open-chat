package com.openchat.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["senderId"]),
        Index(value = ["timestamp"]),
        Index(value = ["status"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val chatId: String,
    val senderId: String,
    val type: MessageType,
    val content: String? = null,
    val mediaUrl: String? = null,
    val mediaType: MediaType? = null,
    val mediaSize: Long? = null,
    val mediaDuration: Int? = null,
    val thumbnailUrl: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val replyToMessageId: String? = null,
    val forwardedFromChatId: String? = null,
    val forwardedFromMessageId: String? = null,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val isPinned: Boolean = false,
    val isEncrypted: Boolean = false,
    val status: MessageStatus = MessageStatus.SENDING,
    val timestamp: Long = System.currentTimeMillis(),
    val editedAt: Long? = null,
    val scheduledAt: Long? = null,
    val selfDestructTimer: Int? = null
)

enum class MessageType {
    TEXT, IMAGE, VIDEO, AUDIO, VOICE, DOCUMENT, STICKER, GIF, LOCATION, CONTACT, POLL, SYSTEM
}

enum class MediaType {
    IMAGE, VIDEO, AUDIO, VOICE, DOCUMENT, STICKER, GIF
}

enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ, FAILED
}

@Entity(
    tableName = "message_reactions",
    primaryKeys = ["messageId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["messageId"])]
)
data class MessageReactionEntity(
    val messageId: String,
    val userId: String,
    val emoji: String,
    val timestamp: Long = System.currentTimeMillis()
)
