package com.openchat.app.domain.model

data class User(
    val id: String,
    val phoneNumber: String? = null,
    val displayName: String,
    val username: String? = null,
    val email: String? = null,
    val bio: String? = null,
    val photoUrl: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: Long? = null,
    val isContact: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class Chat(
    val id: String,
    val type: ChatType,
    val title: String? = null,
    val photoUrl: String? = null,
    val description: String? = null,
    val members: List<ChatMember> = emptyList(),
    val creatorId: String? = null,
    val isSecret: Boolean = false,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessage: MessagePreview? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ChatMember(
    val userId: String,
    val user: User? = null,
    val role: MemberRole = MemberRole.MEMBER,
    val joinedAt: Long = System.currentTimeMillis()
)

data class MessagePreview(
    val id: String,
    val text: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ChatType {
    PRIVATE, GROUP, CHANNEL
}

enum class MemberRole {
    OWNER, ADMIN, MEMBER
}
