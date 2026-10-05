package com.openchat.app.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.openchat.app.core.Result
import com.openchat.app.core.safeApiCall
import com.openchat.app.domain.model.Story
import com.openchat.app.domain.repository.StoryRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val cloudinaryRepository: CloudinaryRepository,
    private val database: FirebaseDatabase
) : StoryRepository {

    override fun observeStories(): Flow<List<Story>> = callbackFlow {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return@callbackFlow

        val listener = database.reference.child("stories")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    // Evaluated INSIDE the listener. Hoisting it outside captured the
                    // clock once at subscription time, so stories expiring mid-session
                    // stayed visible until the app was restarted.
                    val now = System.currentTimeMillis()
                    val stories = snapshot.children.flatMap { userStories ->
                        userStories.children.mapNotNull { storySnapshot ->
                            val story = storySnapshot.getValue(com.openchat.app.data.model.Story::class.java)
                            if (story != null && story.expiresAt > now) story else null
                        }
                    }
                    trySend(stories.map { it.toDomain() })
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.w("StoryRepo", "Stories observation cancelled: ${error.message}")
                    close()
                }
            })

        awaitClose { database.reference.child("stories").removeEventListener(listener) }
    }

    override suspend fun uploadStory(imageUri: Uri): Result<Unit> = safeApiCall {
        val userId = firebaseAuth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        val userName = firebaseAuth.currentUser?.displayName ?: "User"
        val userPhotoUrl = firebaseAuth.currentUser?.photoUrl?.toString()

        val mediaUrl = cloudinaryRepository.uploadImage(imageUri, "stories")
            ?: throw Exception("Failed to upload story image")

        val storyRef = database.reference.child("stories").child(userId).push()
        val storyId = storyRef.key ?: throw Exception("Failed to generate story ID")

        val storyData = mapOf(
            "id" to storyId,
            "userId" to userId,
            "userName" to userName,
            "userPhotoUrl" to userPhotoUrl,
            "mediaUrl" to mediaUrl,
            "mediaType" to "IMAGE",
            "caption" to "",
            "createdAt" to System.currentTimeMillis(),
            "expiresAt" to System.currentTimeMillis() + (24 * 60 * 60 * 1000),
            "viewers" to emptyList<String>(),
            "reactions" to emptyMap<String, String>()
        )

        // await() was missing: the Task was fire-and-forget, so a failed write was
        // still reported as Result.Success and the story silently never appeared.
        storyRef.setValue(storyData).await()
    }

    override suspend fun deleteStory(storyId: String): Result<Unit> = safeApiCall {
        val userId = firebaseAuth.currentUser?.uid ?: return@safeApiCall
        database.reference.child("stories").child(userId).child(storyId).removeValue().await()
    }

    override suspend fun reactToStory(storyId: String, emoji: String): Result<Unit> = safeApiCall {
        val userId = firebaseAuth.currentUser?.uid ?: return@safeApiCall
        database.reference.child("story_reactions").child(storyId).child(userId).setValue(
            mapOf("emoji" to emoji, "timestamp" to System.currentTimeMillis())
        ).await()
    }

    override suspend fun replyToStory(storyId: String, reply: String): Result<Unit> = safeApiCall {
        val userId = firebaseAuth.currentUser?.uid ?: return@safeApiCall
        val userName = firebaseAuth.currentUser?.displayName ?: "User"
        database.reference.child("story_replies").child(storyId).push().setValue(
            mapOf(
                "userId" to userId,
                "userName" to userName,
                "reply" to reply,
                "timestamp" to System.currentTimeMillis()
            )
        ).await()
    }

    override suspend fun markStoryViewed(storyId: String): Result<Unit> = safeApiCall {
        val userId = firebaseAuth.currentUser?.uid ?: return@safeApiCall

        // The view has to be recorded under the story OWNER's node. Writing it to
        // stories/{currentUserId}/... marked nothing and instead wrote the viewer into
        // their own story subtree, corrupting it.
        val storiesSnapshot = database.reference.child("stories").get().await()
        val ownerId = storiesSnapshot.children
            .firstOrNull { it.hasChild(storyId) }
            ?.key
            ?: throw Exception("Story not found")

        database.reference.child("stories").child(ownerId).child(storyId)
            .child("viewers").child(userId)
            .setValue(System.currentTimeMillis()).await()
    }

    private fun com.openchat.app.data.model.Story.toDomain(): Story {
        return Story(
            id = id,
            userId = userId,
            userName = userName,
            userPhotoUrl = userPhotoUrl,
            mediaUrl = mediaUrl,
            mediaType = when (mediaType) {
                com.openchat.app.data.model.StoryMediaType.IMAGE -> com.openchat.app.domain.model.StoryMediaType.IMAGE
                com.openchat.app.data.model.StoryMediaType.VIDEO -> com.openchat.app.domain.model.StoryMediaType.VIDEO
                com.openchat.app.data.model.StoryMediaType.TEXT -> com.openchat.app.domain.model.StoryMediaType.IMAGE
            },
            caption = caption,
            createdAt = createdAt,
            expiresAt = expiresAt,
            isViewed = viewers.isNotEmpty()
        )
    }
}
