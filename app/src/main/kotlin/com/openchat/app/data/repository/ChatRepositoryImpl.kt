package com.openchat.app.data.repository

import com.openchat.app.core.Result
import com.openchat.app.core.safeApiCall
import com.openchat.app.data.model.Chat
import com.openchat.app.data.model.ChatType
import com.openchat.app.domain.repository.ChatRepository
import com.openchat.app.domain.repository.UserRepository
import com.openchat.app.data.local.dao.ChatDao
import com.openchat.app.data.local.dao.MessageDao
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Global cache for current session chat IDs to ensure they appear immediately
private val sessionChatIds = mutableSetOf<String>()

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseDatabase: com.google.firebase.database.FirebaseDatabase,
    private val firebaseAuth: com.google.firebase.auth.FirebaseAuth,
    private val userRepository: UserRepository,
    private val messagingRepository: MessagingRepository,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val userDao: com.openchat.app.data.local.dao.UserDao,
    private val contactDao: com.openchat.app.data.local.dao.ContactDao
) : ChatRepository {
    
    private val databaseRef = firebaseDatabase.reference

    override fun observeChats(): Flow<List<Chat>> = callbackFlow {
        val currentUserId = firebaseAuth.currentUser?.uid
        if (currentUserId == null) {
            trySend(emptyList())
            return@callbackFlow
        }

        android.util.Log.d("ChatRepository", "Current user ID: $currentUserId")

        // Query Realtime Database using participants_map for user's chats
        val chatsRef = databaseRef.child("chats")
        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                android.util.Log.d("ChatRepository", "RTDB Snapshot received: ${snapshot.childrenCount} chats")

                val remoteChats = snapshot.children.mapNotNull { chatSnapshot ->
                    val chatId = chatSnapshot.key ?: return@mapNotNull null
                    
                    // Log all child keys for debugging
                    val allKeys = chatSnapshot.children.map { it.key }.joinToString(", ")
                    android.util.Log.d("ChatRepository", "Processing chat: $chatId, keys: [$allKeys]")
                    
                    val participantsMapKeys = chatSnapshot.child("participants_map").children.mapNotNull { it.key }
                    val participantsMapValues = chatSnapshot.child("participants_map").children.associate { it.key!! to (it.getValue(Boolean::class.java) ?: false) }
                    val participantsList = chatSnapshot.child("participants").children.mapNotNull { it.getValue(String::class.java) }
                    val participantsMapForLog = if (participantsMapValues.isNotEmpty()) participantsMapValues else null
                    val participantsListForLog = if (participantsList.isNotEmpty()) participantsList else null
                    android.util.Log.d("ChatRepository", "Chat $chatId - participants_map: $participantsMapForLog, participants: $participantsListForLog")
                    android.util.Log.d("ChatRepository", "Chat $chatId - currentUser: $currentUserId")
                    val isUserInMap = participantsMapValues[currentUserId] == true
                    val isUserInList = participantsList.contains(currentUserId)
                    val hasNoParticipants = participantsMapValues.isEmpty() && participantsList.isEmpty()
                    android.util.Log.d("ChatRepository", "Chat $chatId - isUserInMap: $isUserInMap, isUserInList: $isUserInList, hasNoParticipants: $hasNoParticipants")
                    val isUserInChat = isUserInMap || isUserInList
                    if (!isUserInChat) {
                        android.util.Log.d("ChatRepository", "Chat $chatId filtered out - user $currentUserId not in participants")
                        return@mapNotNull null
                    }
                    sessionChatIds.add(chatId)
                    val participants = when {
                        participantsList.isNotEmpty() -> participantsList
                        participantsMapKeys.isNotEmpty() -> participantsMapKeys
                        else -> emptyList()
                    }
                    
                    // Get title or generate from other user
                    val title = chatSnapshot.child("title").getValue(String::class.java) 
                        ?: if (participants.size == 2) {
                            // For private chat without title, use "Chat" as placeholder
                            "Chat"
                        } else "Group Chat"
                    
                    val type = when (chatSnapshot.child("type").getValue(String::class.java)) {
                        "GROUP" -> ChatType.GROUP
                        "CHANNEL" -> ChatType.CHANNEL
                        else -> ChatType.PRIVATE
                    }
                    val lastMessageTimestamp = chatSnapshot.child("lastMessageTimestamp").getValue(Long::class.java) ?: 0L
                    
                    android.util.Log.d("ChatRepository", "Chat: $chatId, title: $title, participants: ${participants.size}")

                    // Parse last message
                    val lastMessageSnapshot = chatSnapshot.child("lastMessage")
                    val lastMessage = if (lastMessageSnapshot.exists()) {
                        com.openchat.app.data.model.Message(
                            id = lastMessageSnapshot.child("id").getValue(String::class.java) ?: "",
                            senderId = lastMessageSnapshot.child("senderId").getValue(String::class.java) ?: "",
                            content = lastMessageSnapshot.child("content").getValue(String::class.java) 
                                ?: lastMessageSnapshot.child("text").getValue(String::class.java) ?: "",
                            timestamp = lastMessageSnapshot.child("timestamp").getValue(Long::class.java) ?: lastMessageTimestamp
                        )
                    } else null

                    val unreadCount = chatSnapshot.child("unreadCount").children.mapNotNull {
                        val key = it.key ?: return@mapNotNull null
                        val value = (it.getValue(Long::class.java) ?: it.getValue(Int::class.java)?.toLong() ?: return@mapNotNull null).toInt()
                        key to value
                    }.toMap()

                    // Parse chat settings fields
                    val disappearingTimer = chatSnapshot.child("disappearingTimer").getValue(Long::class.java) ?: 0L
                    val wallpaperUrl = chatSnapshot.child("wallpaperUrl").getValue(String::class.java)
                    val themeColor = chatSnapshot.child("themeColor").getValue(String::class.java)

                    Chat(
                        id = chatId,
                        title = title,
                        type = type,
                        participants = participants,
                        lastMessage = lastMessage,
                        unreadCount = unreadCount,
                        lastMessageTimestamp = lastMessageTimestamp,
                        disappearingTimer = disappearingTimer,
                        wallpaperUrl = wallpaperUrl,
                        themeColor = themeColor
                    )
                }

                android.util.Log.d("ChatRepository", "Sending ${remoteChats.size} chats to UI")
                trySend(remoteChats)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                android.util.Log.e("ChatRepository", "RTDB query error: ${error.message}", error.toException())
                trySend(emptyList())
            }
        }

        chatsRef.addValueEventListener(listener)
        awaitClose { chatsRef.removeEventListener(listener) }
    }
    
    override fun observeChat(chatId: String): Flow<Chat?> = callbackFlow {
        val chatRef = databaseRef.child("chats").child(chatId)
        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                val chat = parseChat(chatId, snapshot)
                trySend(chat)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                close(error.toException())
            }
        }
        chatRef.addValueEventListener(listener)
        awaitClose { chatRef.removeEventListener(listener) }
    }

    override suspend fun getChat(chatId: String): Result<Chat> = safeApiCall {
        val snapshot = databaseRef.child("chats").child(chatId).get().await()
        if (!snapshot.exists()) throw Exception("Chat not found")
        
        val participantsMap = snapshot.child("participants_map").value as? Map<String, Boolean>
        val participantsList = snapshot.child("participants").value as? List<String>
        val participants = participantsList ?: participantsMap?.keys?.toList() ?: emptyList()

        val title = snapshot.child("title").getValue(String::class.java)
            ?: if (participants.size == 2) "Chat" else "Group Chat"

        Chat(
            id = chatId,
            title = title,
            type = when (snapshot.child("type").getValue(String::class.java)) {
                "GROUP" -> ChatType.GROUP
                "CHANNEL" -> ChatType.CHANNEL
                else -> ChatType.PRIVATE
            },
            participants = participants,
            lastMessageTimestamp = snapshot.child("lastMessageTimestamp").getValue(Long::class.java) ?: 0L,
            unreadCount = emptyMap(),
            isPinned = false
        )
    }

    private fun parseChat(chatId: String, snapshot: com.google.firebase.database.DataSnapshot): Chat {
        val participantsMap = snapshot.child("participants_map").value as? Map<String, Boolean>
        val participantsList = snapshot.child("participants").value as? List<String>
        val participants = participantsList ?: participantsMap?.keys?.toList() ?: emptyList()

        val title = snapshot.child("title").getValue(String::class.java)
            ?: if (participants.size == 2) "Chat" else "Group Chat"

        val lastMessageTimestamp = snapshot.child("lastMessageTimestamp").getValue(Long::class.java) ?: 0L

        return Chat(
            id = chatId,
            title = title,
            type = when (snapshot.child("type").getValue(String::class.java)) {
                "GROUP" -> ChatType.GROUP
                "CHANNEL" -> ChatType.CHANNEL
                else -> ChatType.PRIVATE
            },
            participants = participants,
            lastMessageTimestamp = lastMessageTimestamp,
            unreadCount = emptyMap(),
            isPinned = false
        )
    }
    
    override suspend fun createPrivateChat(userId: String): Result<Chat> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        val chatId = messagingRepository.getOrCreateChat(currentUserId, userId)
        val snapshot = databaseRef.child("chats").child(chatId).get().await()
        val participantsList = snapshot.child("participants").value as? List<String>
        Chat(
            id = chatId,
            title = snapshot.child("title").getValue(String::class.java),
            type = ChatType.PRIVATE,
            participants = participantsList ?: emptyList(),
            lastMessageTimestamp = System.currentTimeMillis()
        )
    }

    override suspend fun createGroupChat(title: String, members: List<String>): Result<Chat> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        val chatId = messagingRepository.createGroupChat(title, members, currentUserId)
        Chat(id = chatId, title = title, type = ChatType.GROUP, participants = members)
    }

    override suspend fun createChannel(title: String, description: String?): Result<Chat> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        val chatRef = databaseRef.child("chats").push()
        val chatId = chatRef.key ?: throw Exception("Failed to generate chat ID")
        // Every reader (observeChats, getChat, getPrivateChatWithUser) expects
        // `participants` to be a List<String> and `participants_map` to be the map.
        // Writing a Map into `participants` left both reads empty, so
        // isUserInChat was false and the channel was filtered out of the creator's
        // own chat list immediately after creation.
        val channel = mapOf(
            "id" to chatId, "title" to title, "type" to "CHANNEL",
            "creatorId" to currentUserId, "description" to description,
            "participants" to listOf(currentUserId),
            "participants_map" to mapOf(currentUserId to true),
            "admins" to listOf(currentUserId),
            "createdAt" to System.currentTimeMillis(), "lastMessageTimestamp" to System.currentTimeMillis()
        )
        chatRef.setValue(channel).await()
        Chat(id = chatId, title = title, type = ChatType.CHANNEL, creatorId = currentUserId)
    }

    override suspend fun updateChat(chat: Chat): Result<Unit> = safeApiCall {
        databaseRef.child("chats").child(chat.id).child("title").setValue(chat.title).await()
    }

    override suspend fun deleteChat(chatId: String): Result<Unit> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        val chatRef = databaseRef.child("chats").child(chatId)
        val snapshot = chatRef.get().await()
        val creatorId = snapshot.child("creatorId").getValue(String::class.java)
        val admins = (snapshot.child("admins").value as? List<String>) ?: emptyList()
        if (creatorId != null && creatorId != currentUserId && currentUserId !in admins) {
            throw Exception("Only the creator or admins can delete this chat")
        }
        chatRef.removeValue().await()
        firestore.collection("chats").document(chatId).delete().await()
    }

    override suspend fun pinChat(chatId: String, isPinned: Boolean): Result<Unit> = safeApiCall {
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        val pinRef = databaseRef.child("userSettings").child(userId).child("pinnedChats").child(chatId)
        if (isPinned) pinRef.setValue(System.currentTimeMillis()).await()
        else pinRef.removeValue().await()
    }

    override suspend fun muteChat(chatId: String, isMuted: Boolean): Result<Unit> = safeApiCall {
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        val muteRef = databaseRef.child("userSettings").child(userId).child("mutedChats").child(chatId)
        if (isMuted) muteRef.setValue(Long.MAX_VALUE).await()
        else muteRef.removeValue().await()
    }

    override suspend fun markChatAsRead(chatId: String): Result<Unit> = safeApiCall {
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        messagingRepository.markMessagesAsRead(chatId, userId)
    }

    override suspend fun getPrivateChatWithUser(userId: String): Chat? {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return null
        val chatsSnapshot = databaseRef.child("chats").get().await()
        for (chatSnapshot in chatsSnapshot.children) {
            val pmap = chatSnapshot.child("participants_map").value as? Map<String, Boolean>
            val plist = chatSnapshot.child("participants").value as? List<String>
            val participants = plist ?: pmap?.keys?.toList() ?: continue
            if (participants.contains(currentUserId) && participants.contains(userId)) {
                return Chat(
                    id = chatSnapshot.key ?: continue,
                    title = chatSnapshot.child("title").getValue(String::class.java),
                    type = ChatType.PRIVATE,
                    participants = participants,
                    lastMessageTimestamp = chatSnapshot.child("lastMessageTimestamp").getValue(Long::class.java) ?: 0L
                )
            }
        }
        return null
    }

    override suspend fun addMemberToChat(chatId: String, userId: String): Result<Unit> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        val chatRef = databaseRef.child("chats").child(chatId)
        val snapshot = chatRef.get().await()
        val admins = (snapshot.child("admins").value as? List<String>) ?: emptyList()
        if (admins.isNotEmpty() && currentUserId !in admins) {
            throw Exception("Only admins can add members")
        }
        val participantsList = (snapshot.child("participants").value as? List<String>)?.toMutableList() ?: mutableListOf()
        if (!participantsList.contains(userId)) {
            participantsList.add(userId)
            chatRef.child("participants").setValue(participantsList).await()
            chatRef.child("participants_map").child(userId).setValue(true).await()
        }
    }

    override suspend fun removeMemberFromChat(chatId: String, userId: String): Result<Unit> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        val chatRef = databaseRef.child("chats").child(chatId)
        val snapshot = chatRef.get().await()
        val admins = (snapshot.child("admins").value as? List<String>) ?: emptyList()
        if (admins.isNotEmpty() && currentUserId !in admins) {
            throw Exception("Only admins can remove members")
        }
        val participantsList = (snapshot.child("participants").value as? List<String>)?.toMutableList() ?: mutableListOf()
        participantsList.remove(userId)
        chatRef.child("participants").setValue(participantsList).await()
        chatRef.child("participants_map").child(userId).removeValue().await()
    }

    override suspend fun updateMemberRole(chatId: String, userId: String, role: com.openchat.app.domain.model.MemberRole): Result<Unit> = safeApiCall {
        databaseRef.child("chats").child(chatId).child("roles").child(userId).setValue(role.name).await()
    }

    override suspend fun leaveChat(chatId: String): Result<Unit> = safeApiCall {
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        // Cannot delegate to removeMemberFromChat: that refuses when the caller is
        // an admin, and the creator is always an admin - so an admin (or the
        // creator) could never leave their own chat. Leaving is always permitted;
        // remove yourself from the admin list first.
        val chatRef = databaseRef.child("chats").child(chatId)
        val snapshot = chatRef.get().await()

        val admins = ((snapshot.child("admins").value as? List<String>) ?: emptyList()) - userId
        chatRef.child("admins").setValue(admins).await()

        val participants =
            ((snapshot.child("participants").value as? List<String>) ?: emptyList()) - userId
        chatRef.child("participants").setValue(participants).await()

        chatRef.child("participants_map").child(userId).removeValue().await()

        // Hand the chat to another admin if any remain, so it is not orphaned.
        if (snapshot.child("creatorId").getValue(String::class.java) == userId && admins.isNotEmpty()) {
            chatRef.child("creatorId").setValue(admins.first()).await()
        }
    }

    override suspend fun clearUserData(): Result<Unit> = safeApiCall {
        // Clear the in-memory session cache
        sessionChatIds.clear()
        android.util.Log.d("ChatRepository", "Cleared session chat IDs cache")

        // Clear local database. userDao/contactDao are cleared too: previously the
        // previous user's cached profile and address book survived sign-out, which
        // matters on a shared device.
        chatDao.clearAllMembers()
        chatDao.clearAllChats()
        messageDao.clearAllReactions()
        messageDao.clearAllMessages()
        userDao.clearAllUsers()
        contactDao.deleteAllContacts()
        android.util.Log.d("ChatRepository", "Cleared local database")
    }
}
