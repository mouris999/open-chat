package com.openchat.app.data.model

import com.openchat.app.domain.model.Story
import com.openchat.app.domain.model.StoryMediaType

data class StoryRemote(
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String? = null,
    val mediaUrl: String = "",
    val mediaType: String = "IMAGE",
    val caption: String? = null,
    val timestamp: Long = 0L,
    val expiresAt: Long = 0L
) {
    fun toDomain(id: String) = Story(
        id = id,
        userId = userId,
        userName = userName,
        userPhotoUrl = userPhotoUrl,
        mediaUrl = mediaUrl,
        mediaType = try { StoryMediaType.valueOf(mediaType) } catch (e: Exception) { StoryMediaType.IMAGE },
        caption = caption,
        createdAt = timestamp,
        expiresAt = expiresAt
    )
}
