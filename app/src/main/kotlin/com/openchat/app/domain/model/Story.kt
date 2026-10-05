package com.openchat.app.domain.model

data class Story(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String? = null,
    val mediaUrl: String = "",
    val mediaType: StoryMediaType = StoryMediaType.IMAGE,
    val caption: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + (24 * 60 * 60 * 1000),
    val isViewed: Boolean = false
)

enum class StoryMediaType {
    IMAGE, VIDEO
}
