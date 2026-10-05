package com.openchat.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.openchat.app.data.local.entity.StoryEntity
import com.openchat.app.data.local.entity.StoryViewEntity
import com.openchat.app.data.local.entity.StoryWithViews
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Transaction
    @Query("SELECT * FROM stories WHERE expiresAt > :currentTime ORDER BY createdAt DESC")
    fun observeActiveStories(currentTime: Long = System.currentTimeMillis()): Flow<List<StoryWithViews>>

    @Transaction
    @Query("SELECT * FROM stories WHERE userId = :userId AND expiresAt > :currentTime ORDER BY createdAt DESC")
    fun observeUserStories(userId: String, currentTime: Long = System.currentTimeMillis()): Flow<List<StoryWithViews>>

    @Query("SELECT * FROM stories WHERE id = :storyId")
    suspend fun getStoryById(storyId: String): StoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: StoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<StoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStoryView(view: StoryViewEntity)

    @Query("UPDATE stories SET isViewed = 1, viewCount = viewCount + 1 WHERE id = :storyId")
    suspend fun markStoryAsViewed(storyId: String)

    @Delete
    suspend fun deleteStory(story: StoryEntity)

    @Query("DELETE FROM stories WHERE expiresAt < :currentTime")
    suspend fun deleteExpiredStories(currentTime: Long = System.currentTimeMillis())
}
