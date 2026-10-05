package com.openchat.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.paging.PagingData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.openchat.app.core.Result
import com.openchat.app.core.safeApiCall
import com.openchat.app.data.model.Message
import com.openchat.app.data.model.MessageType
import com.openchat.app.domain.model.MediaType
import com.openchat.app.domain.model.MessageReaction
import com.openchat.app.domain.model.Message as DomainMessage
import com.openchat.app.domain.repository.MessageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val database: FirebaseDatabase,
    private val firebaseAuth: FirebaseAuth,
    private val messagingRepository: MessagingRepository,
    private val cloudinaryRepository: CloudinaryRepository
) : MessageRepository {

    override fun observeMessages(chatId: String): Flow<List<DomainMessage>> {
        return messagingRepository.getMessages(chatId).map { messages ->
            messages.map { it.toDomain(chatId) }
        }
    }

    override fun getMessagesPaging(chatId: String): Flow<PagingData<DomainMessage>> {
        return messagingRepository.getMessages(chatId).map { messages ->
            PagingData.from(messages.map { it.toDomain() })
        }
    }

    override suspend fun sendMessage(
        chatId: String,
        content: String?,
        mediaUri: Uri?,
        mediaType: MediaType?,
        replyToMessageId: String?
    ): Result<DomainMessage> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        val currentUserName = firebaseAuth.currentUser?.displayName ?: "User"

        val message = Message(
            id = System.currentTimeMillis().toString(),
            senderId = currentUserId,
            senderName = currentUserName,
            content = content ?: "",
            messageType = when (mediaType) {
                MediaType.IMAGE -> MessageType.IMAGE
                MediaType.VIDEO -> MessageType.VIDEO
                MediaType.VOICE -> MessageType.VOICE
                MediaType.AUDIO -> MessageType.VOICE
                MediaType.DOCUMENT -> MessageType.FILE
                MediaType.STICKER -> MessageType.STICKER
                MediaType.GIF -> MessageType.IMAGE
                else -> MessageType.TEXT
            },
            replyToMessageId = replyToMessageId,
            timestamp = System.currentTimeMillis()
        )

        messagingRepository.sendMessage(chatId, message)
        message.toDomain(chatId)
    }

    override suspend fun sendImageMessage(chatId: String, imageUri: Uri, caption: String?): Result<DomainMessage> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        val currentUserName = firebaseAuth.currentUser?.displayName ?: "User"

        val imageUrl = cloudinaryRepository.uploadImage(imageUri, chatId)
            ?: throw Exception("Failed to upload image")

        val message = Message(
            id = "img_${System.currentTimeMillis()}",
            senderId = currentUserId,
            senderName = currentUserName,
            content = imageUrl,
            messageType = MessageType.IMAGE,
            fileName = caption,
            timestamp = System.currentTimeMillis()
        )

        messagingRepository.sendMessage(chatId, message)
        message.toDomain(chatId)
    }

    override suspend fun sendVideoMessage(chatId: String, videoUri: Uri, caption: String?): Result<DomainMessage> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        val currentUserName = firebaseAuth.currentUser?.displayName ?: "User"

        val videoUrl = cloudinaryRepository.uploadVideo(videoUri, chatId)
            ?: throw Exception("Failed to upload video")

        val message = Message(
            id = "vid_${System.currentTimeMillis()}",
            senderId = currentUserId,
            senderName = currentUserName,
            content = videoUrl,
            messageType = MessageType.VIDEO,
            fileName = caption,
            timestamp = System.currentTimeMillis()
        )

        messagingRepository.sendMessage(chatId, message)
        message.toDomain(chatId)
    }

    override suspend fun sendVoiceMessage(chatId: String, audioUri: Uri, duration: Int): Result<DomainMessage> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        val currentUserName = firebaseAuth.currentUser?.displayName ?: "User"

        val voiceUrl = cloudinaryRepository.uploadVoice(audioUri, chatId)
            ?: throw Exception("Failed to upload voice message")

        val message = Message(
            id = "voice_${System.currentTimeMillis()}",
            senderId = currentUserId,
            senderName = currentUserName,
            content = voiceUrl,
            messageType = MessageType.VOICE,
            voiceDuration = duration,
            timestamp = System.currentTimeMillis()
        )

        messagingRepository.sendMessage(chatId, message)
        message.toDomain(chatId)
    }

    override suspend fun sendLocationMessage(chatId: String, latitude: Double, longitude: Double): Result<DomainMessage> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        val currentUserName = firebaseAuth.currentUser?.displayName ?: "User"

        val locationContent = "loc:$latitude,$longitude"

        val message = Message(
            id = "loc_${System.currentTimeMillis()}",
            senderId = currentUserId,
            senderName = currentUserName,
            content = locationContent,
            messageType = MessageType.LOCATION,
            timestamp = System.currentTimeMillis()
        )

        messagingRepository.sendMessage(chatId, message)
        message.toDomain(chatId)
    }

    override suspend fun editMessage(messageId: String, newContent: String): Result<Unit> = safeApiCall {
        val chatId = findChatForMessage(messageId) ?: throw Exception("Message not found")
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        messagingRepository.editMessage(chatId, messageId, userId, newContent)
    }

    override suspend fun deleteMessage(messageId: String, deleteForEveryone: Boolean): Result<Unit> = safeApiCall {
        val chatId = findChatForMessage(messageId) ?: throw Exception("Message not found")
        if (deleteForEveryone) {
            firestore.collection("messages").document(messageId)
                .update("isDeleted", true, "content", "This message was deleted")
                .await()
        }
        // Soft delete via RTDB
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        messagingRepository.deleteMessage(chatId, messageId, userId)
    }

    override suspend fun pinMessage(messageId: String, isPinned: Boolean): Result<Unit> = safeApiCall {
        val chatId = findChatForMessage(messageId) ?: throw Exception("Message not found")
        database.reference.child("messages").child(chatId).child(messageId)
            .child("isPinned").setValue(isPinned).await()
    }

    override suspend fun forwardMessage(messageId: String, toChatId: String): Result<DomainMessage> = safeApiCall {
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        // The original chat id was hard-coded to "", so the read against
        // messages//{id} always missed and this threw "Original message not found".
        val sourceChatId = findChatForMessage(messageId) ?: throw Exception("Original message not found")
        val newId = messagingRepository.forwardMessage(messageId, sourceChatId, toChatId, userId)
        // Read the message back instead of fabricating an empty TEXT message with no
        // content or timestamp.
        val forwarded = database.reference.child("messages").child(toChatId).child(newId)
            .get().await().getValue(Message::class.java)
            ?: throw Exception("Forwarded message could not be read back")
        forwarded.toDomain(toChatId)
    }

    override suspend fun addReaction(messageId: String, emoji: String): Result<Unit> = safeApiCall {
        val chatId = findChatForMessage(messageId) ?: throw Exception("Message not found")
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        messagingRepository.addReaction(chatId, messageId, userId, emoji)
    }

    override suspend fun removeReaction(messageId: String, emoji: String): Result<Unit> = safeApiCall {
        val chatId = findChatForMessage(messageId) ?: throw Exception("Message not found")
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        database.reference.child("messages").child(chatId).child(messageId)
            .child("reactions").child(userId).removeValue().await()
    }

    override suspend fun searchMessages(chatId: String, query: String): List<DomainMessage> {
        if (query.length < 2) return emptyList()
        // Truncate rather than return nothing: a long query used to silently yield
        // zero results.
        val term = query.take(100)
        val snapshot = database.reference.child("messages").child(chatId).get().await()
        return snapshot.children.mapNotNull { it.getValue(Message::class.java) }
            .filter { !it.isDeleted }
            .filter { it.content.orEmpty().contains(term, ignoreCase = true) }
            .sortedByDescending { it.timestamp }
            .take(50)
            .map { it.toDomain(chatId) }
    }

    override suspend fun searchMessagesGlobal(query: String): List<DomainMessage> {
        val userId = firebaseAuth.currentUser?.uid ?: return emptyList()
        val chatsSnapshot = database.reference.child("chats").get().await()
        val userChatIds = chatsSnapshot.children
            .filter { chatSnap ->
                val pmap = chatSnap.child("participants_map").value as? Map<String, Boolean>
                val plist = chatSnap.child("participants").value as? List<String>
                pmap?.containsKey(userId) == true || plist?.contains(userId) == true
            }
            .mapNotNull { it.key }

        val results = mutableListOf<DomainMessage>()
        for (chatId in userChatIds) {
            results.addAll(searchMessages(chatId, query))
        }
        return results.sortedByDescending { it.timestamp }.take(100)
    }

    override suspend fun markMessageAsRead(messageId: String): Result<Unit> = safeApiCall {
        val chatId = findChatForMessage(messageId) ?: throw Exception("Message not found")
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        messagingRepository.markMessageAsRead(chatId, messageId, userId)
    }

    private suspend fun findChatForMessage(messageId: String): String? {
        val snapshot = database.reference.child("messages").get().await()
        for (chatSnapshot in snapshot.children) {
            if (chatSnapshot.hasChild(messageId)) {
                return chatSnapshot.key
            }
        }
        return null
    }

    private fun com.openchat.app.data.model.Message.toDomain(chatId: String = ""): DomainMessage {
        return DomainMessage(
            id = id,
            // Hard-coded to "" previously, so anything downstream filtering or
            // routing by chatId silently failed.
            chatId = chatId,
            senderId = senderId,
            type = when (messageType) {
                MessageType.TEXT -> com.openchat.app.domain.model.MessageType.TEXT
                MessageType.IMAGE -> com.openchat.app.domain.model.MessageType.IMAGE
                MessageType.VOICE -> com.openchat.app.domain.model.MessageType.VOICE
                MessageType.VIDEO -> com.openchat.app.domain.model.MessageType.VIDEO
                MessageType.FILE -> com.openchat.app.domain.model.MessageType.DOCUMENT
                MessageType.CALL -> com.openchat.app.domain.model.MessageType.SYSTEM
                MessageType.POLL -> com.openchat.app.domain.model.MessageType.POLL
                MessageType.CODE -> com.openchat.app.domain.model.MessageType.SYSTEM
                MessageType.STICKER -> com.openchat.app.domain.model.MessageType.STICKER
                MessageType.LOCATION -> com.openchat.app.domain.model.MessageType.LOCATION
                MessageType.CONTACT -> com.openchat.app.domain.model.MessageType.CONTACT
            },
            content = content,
            timestamp = timestamp,
            reactions = reactions.map { (userId, emoji) ->
                MessageReaction(userId = userId, emoji = emoji)
            },
            isDeleted = isDeleted
        )
    }
}
