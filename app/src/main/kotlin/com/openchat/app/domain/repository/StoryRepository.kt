package com.openchat.app.domain.repository

import android.net.Uri
import com.openchat.app.core.Result
import com.openchat.app.domain.model.Story
import kotlinx.coroutines.flow.Flow

interface StoryRepository {
    fun observeStories(): Flow<List<Story>>
    suspend fun uploadStory(imageUri: Uri): Result<Unit>
    suspend fun deleteStory(storyId: String): Result<Unit>
    suspend fun reactToStory(storyId: String, emoji: String): Result<Unit>
    suspend fun replyToStory(storyId: String, reply: String): Result<Unit>
    suspend fun markStoryViewed(storyId: String): Result<Unit>
}
