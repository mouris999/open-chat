package com.openchat.app.data.model

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@IgnoreExtraProperties
data class Message(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: MessageType = MessageType.TEXT,
    val isRead: Boolean = false,
    val readBy: Map<String, Long> = emptyMap(), // userId -> timestamp
    val replyToMessageId: String? = null,
    val replyToContent: String? = null,
    val reactions: Map<String, String> = emptyMap(), // userId -> emoji
    @PropertyName("deleted") val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val expiresAt: Long? = null, // For disappearing messages
    @PropertyName("pinned") val isPinned: Boolean = false,
    // Voice message fields
    val voiceDuration: Int = 0, // Duration in seconds
    val voiceWaveform: List<Float> = emptyList(), // Amplitude data for waveform
    @PropertyName("listened") val isListened: Boolean = false, // For voice messages
    // File upload fields
    val fileName: String? = null, // Original filename for file uploads
    // Silent message (no notification sound)
    @PropertyName("silent") val isSilent: Boolean = false,
    // Edited message
    @PropertyName("edited") val isEdited: Boolean = false,
    val editedAt: Long? = null,
    val originalContent: String? = null,
    // Thread/Conversation threading
    val parentMessageId: String? = null, // For thread replies
    val threadReplyCount: Int = 0,
    val threadLastReplyAt: Long? = null,
    // Forwarding
    @PropertyName("forwarded") val isForwarded: Boolean = false,
    val forwardInfo: ForwardedMessage? = null,
    // Mentions
    val mentions: List<String> = emptyList(), // List of mentioned userIds
    // Translation
    val translatedContent: String? = null,
    val translationLanguage: String? = null,
    // Smart reply suggestions
    val smartReplies: List<String> = emptyList()
)

// Draft message for each chat
data class DraftMessage(
    val chatId: String = "",
    val content: String = "",
    val replyToMessageId: String? = null,
    val savedAt: Long = System.currentTimeMillis()
)

enum class DisappearingTimer(val displayName: String, val durationMs: Long) {
    OFF("Off", 0),
    SECONDS_30("30 Seconds", 30_000),
    MINUTES_1("1 Minute", 60_000),
    MINUTES_5("5 Minutes", 300_000),
    HOURS_1("1 Hour", 3_600_000),
    HOURS_24("24 Hours", 86_400_000),
    DAYS_7("7 Days", 604_800_000)
}

enum class MessageType {
    TEXT,
    IMAGE,
    VOICE,
    VIDEO,
    FILE,
    CALL,
    POLL,
    CODE, // GitHub-style code snippets
    STICKER,
    LOCATION,
    CONTACT
}

// Poll data for poll messages
data class Poll(
    val id: String = "",
    val question: String = "",
    val options: List<PollOption> = emptyList(),
    val isMultipleChoice: Boolean = false,
    val isAnonymous: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val endsAt: Long? = null // Optional end time
)

data class PollOption(
    val id: String = "",
    val text: String = "",
    val votes: Map<String, String> = emptyMap() // userId -> optionId they voted for
)

// Scheduled message data
data class ScheduledMessage(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val content: String = "",
    val messageType: MessageType = MessageType.TEXT,
    val scheduledTime: Long = 0,
    val replyToMessageId: String? = null,
    val isSent: Boolean = false
)

data class Chat(
    val id: String = "",
    val title: String? = null,
    val participants: List<String> = emptyList(),
    val lastMessage: Message? = null,
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Map<String, Int> = emptyMap(),
    val isPinned: Boolean = false,
    val pinOrder: Long = 0L,
    val disappearingTimer: Long = 0L, // Duration in ms, 0 = off
    val wallpaperUrl: String? = null, // Chat background wallpaper
    val themeColor: String? = null, // Custom theme color
    // Group chat fields
    val type: ChatType = ChatType.PRIVATE,
    val creatorId: String? = null,
    val admins: List<String> = emptyList(), // List of admin user IDs
    val slowModeSeconds: Int = 0, // 0 = off, otherwise seconds between messages
    // Notification settings per chat
    val customNotificationSound: String? = null, // URI or resource name
    val isMuted: Boolean = false,
    val muteUntil: Long? = null,
    // Group avatar. Rendered by ChatSettingsScreen and ForwardedMessageView, but was
    // never declared here, so those call sites had nothing to bind to.
    val photoUrl: String? = null
)

enum class ChatType {
    PRIVATE,
    GROUP,
    CHANNEL
}

// Chat folder/collection for organizing chats
data class ChatFolder(
    val id: String = "",
    val name: String = "",
    val icon: String = "", // Icon name or emoji
    val chatIds: List<String> = emptyList(),
    val isCustom: Boolean = true
)

// Forwarded message attribution
data class ForwardedMessage(
    val originalMessageId: String = "",
    val originalSenderId: String = "",
    val originalSenderName: String = "",
    val originalChatId: String = "",
    val originalTimestamp: Long = 0L,
    val forwardedAt: Long = System.currentTimeMillis(),
    val forwardedBy: String = ""
)

// Story/Status model (Instagram/Snapchat style)
data class Story(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String? = null,
    val mediaUrl: String = "",
    val mediaType: StoryMediaType = StoryMediaType.IMAGE,
    val caption: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 86400000, // 24 hours
    val viewers: List<String> = emptyList(),
    val reactions: Map<String, String> = emptyMap(), // userId -> emoji
    val isCloseFriendsOnly: Boolean = false,
    val mentions: List<String> = emptyList(), // List of mentioned userIds
    val location: String? = null,
    val musicTrack: String? = null
)

enum class StoryMediaType {
    IMAGE,
    VIDEO,
    TEXT
}

// Mention in message
data class Mention(
    val userId: String = "",
    val userName: String = "",
    val startIndex: Int = 0,
    val endIndex: Int = 0
)

// Code snippet for GitHub-style sharing
data class CodeSnippet(
    val language: String = "", // kotlin, java, python, etc.
    val code: String = "",
    val fileName: String? = null,
    val lineNumbers: Boolean = true
)

// Location sharing
data class SharedLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val name: String? = null,
    val address: String? = null
)

