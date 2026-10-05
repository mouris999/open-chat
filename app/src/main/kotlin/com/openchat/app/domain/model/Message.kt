package com.openchat.app.domain.model

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val sender: User? = null,
    val type: MessageType,
    val content: String? = null,
    val media: MediaAttachment? = null,
    val location: LocationData? = null,
    val replyToMessage: Message? = null,
    val forwardedFrom: ForwardedFrom? = null,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val isPinned: Boolean = false,
    val isEncrypted: Boolean = false,
    val status: MessageStatus = MessageStatus.SENDING,
    val reactions: List<MessageReaction> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val editedAt: Long? = null,
    val scheduledAt: Long? = null,
    val selfDestructTimer: Int? = null
)

data class MediaAttachment(
    val url: String,
    val type: MediaType,
    val size: Long? = null,
    val duration: Int? = null,
    val thumbnailUrl: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null
)

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)

data class ForwardedFrom(
    val chatId: String,
    val messageId: String,
    val chatTitle: String? = null
)

data class MessageReaction(
    val userId: String,
    val emoji: String,
    val timestamp: Long = System.currentTimeMillis()
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
