package com.openchat.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stories",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["createdAt"])
    ]
)
data class StoryEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val mediaUrl: String,
    val mediaType: StoryMediaType,
    val caption: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000),
    val viewCount: Int = 0,
    val isViewed: Boolean = false
)

enum class StoryMediaType {
    IMAGE, VIDEO
}

@Entity(
    tableName = "story_views",
    primaryKeys = ["storyId", "viewerId"],
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["storyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["storyId"])]
)
data class StoryViewEntity(
    val storyId: String,
    val viewerId: String,
    val viewedAt: Long = System.currentTimeMillis()
)
