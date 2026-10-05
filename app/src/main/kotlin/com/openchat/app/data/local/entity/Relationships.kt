package com.openchat.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class ChatWithMembers(
    @Embedded
    val chat: ChatEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            ChatMemberEntity::class,
            parentColumn = "chatId",
            entityColumn = "userId"
        )
    )
    val members: List<UserEntity>
)

data class MessageWithReactions(
    @Embedded
    val message: MessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "messageId"
    )
    val reactions: List<MessageReactionEntity>
)

data class StoryWithViews(
    @Embedded
    val story: StoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "storyId"
    )
    val views: List<StoryViewEntity>
) {
    fun toDomainModel(): com.openchat.app.domain.model.Story {
        return com.openchat.app.domain.model.Story(
            id = story.id,
            userId = story.userId,
            mediaUrl = story.mediaUrl,
            mediaType = com.openchat.app.domain.model.StoryMediaType.valueOf(story.mediaType.name),
            caption = story.caption,
            createdAt = story.createdAt,
            expiresAt = story.expiresAt,
            isViewed = story.isViewed
        )
    }
}
