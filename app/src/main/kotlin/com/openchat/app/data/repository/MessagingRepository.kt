package com.openchat.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.openchat.app.data.model.Chat
import com.openchat.app.data.model.ChatType
import com.openchat.app.data.model.Message
import com.openchat.app.data.model.MessageType
import com.openchat.app.webrtc.FcmSender
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingRepository @Inject constructor(
    private val firebaseDatabase: FirebaseDatabase
) {

    private val databaseRef = firebaseDatabase.reference

    // Send a message
    suspend fun sendMessage(chatId: String, message: Message) {
        android.util.Log.d("MessagingRepository", "Sending message to chat: $chatId")
        android.util.Log.d("MessagingRepository", "Message: id=${message.id}, sender=${message.senderId}, content=${message.content}")
        
        // Ensure chat exists with participants before sending metadata
        val chatRef = databaseRef.child("chats").child(chatId)
        val chatSnapshot = chatRef.get().await()
        
        // Get disappearing timer setting
        val disappearingTimer = chatSnapshot.child("disappearingTimer").getValue(Long::class.java) ?: 0L
        
        val messageRef = databaseRef.child("messages").child(chatId).push()
        val messageId = messageRef.key ?: ""
        android.util.Log.d("MessagingRepository", "Generated message ID: $messageId")
        
        // Set expiration if disappearing timer is enabled
        val messageWithIdAndExpiration = if (disappearingTimer > 0) {
            message.copy(
                id = messageId,
                expiresAt = System.currentTimeMillis() + disappearingTimer
            )
        } else {
            message.copy(id = messageId)
        }
        
        try {
            messageRef.setValue(messageWithIdAndExpiration).await()
            android.util.Log.d("MessagingRepository", "Message saved successfully to /messages/$chatId/$messageId")
        } catch (e: Exception) {
            android.util.Log.e("MessagingRepository", "Failed to save message: ${e.message}", e)
            throw e
        }

        // Update chat metadata (last message, timestamp)
        updateChatMetadata(chatId, messageWithIdAndExpiration)

        // Send FCM push notification to receiver if app is backgrounded/killed
        try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val chatSnapshot = chatRef.get().await()
            val participants = chatSnapshot.child("participants").children.mapNotNull { it.getValue(String::class.java) }
            val receiverId = participants.firstOrNull { it != currentUserId }
            if (receiverId != null && message.senderId != messageWithIdAndExpiration.id.hashCode().toString()) {
                // Don't send FCM for own messages in other sessions
                FcmSender.sendMessageNotification(
                    receiverUid = receiverId,
                    chatId = chatId,
                    senderName = message.senderName.ifEmpty { FirebaseAuth.getInstance().currentUser?.displayName ?: "Unknown" },
                    messageContent = message.content,
                    senderId = currentUserId
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("MessagingRepository", "Failed to send FCM notification", e)
        }
    }

    // Listen for messages in a chat
    fun getMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        android.util.Log.d("MessagingRepository", "Starting message listener for chat: $chatId")
        val messagesRef = databaseRef.child("messages").child(chatId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                android.util.Log.d("MessagingRepository", "Message snapshot received for chat $chatId: ${snapshot.childrenCount} messages")
                val messages = snapshot.children.mapNotNull { childSnapshot ->
                    try {
                        val msg = childSnapshot.getValue(Message::class.java)
                        if (msg == null) {
                            android.util.Log.w("MessagingRepository", "Failed to parse message: ${childSnapshot.key}")
                        }
                        msg
                    } catch (e: Exception) {
                        android.util.Log.e("MessagingRepository", "Error parsing message ${childSnapshot.key}: ${e.message}")
                        null
                    }
                }.sortedBy { it.timestamp }
                android.util.Log.d("MessagingRepository", "Sending ${messages.size} messages to UI for chat $chatId")
                trySend(messages)
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("MessagingRepository", "Message listener cancelled for chat $chatId: ${error.message}")
                close(error.toException())
            }
        }

        messagesRef.addValueEventListener(listener)

        awaitClose {
            android.util.Log.d("MessagingRepository", "Removing message listener for chat: $chatId")
            messagesRef.removeEventListener(listener)
        }
    }

    // Get chat metadata
    fun getChat(chatId: String): Flow<Chat?> = callbackFlow {
        val chatRef = databaseRef.child("chats").child(chatId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                
                // Manual parsing to handle participants from both sources
                val id = snapshot.child("id").getValue(String::class.java) ?: chatId
                val title = snapshot.child("title").getValue(String::class.java)
                
                // Get participants from list or map
                val participantsList = snapshot.child("participants").children.mapNotNull { 
                    it.getValue(String::class.java) 
                }
                val participantsMap = snapshot.child("participants_map").value as? Map<String, Boolean>
                val participants = participantsList.takeIf { it.isNotEmpty() } 
                    ?: participantsMap?.keys?.toList() 
                    ?: emptyList()
                
                android.util.Log.d("MessagingRepository", "Chat $chatId - participants: $participants (from list: $participantsList, from map: ${participantsMap?.keys})")
                
                val chat = Chat(
                    id = id,
                    title = title,
                    participants = participants,
                    lastMessageTimestamp = snapshot.child("lastMessageTimestamp").getValue(Long::class.java) ?: 0L,
                    unreadCount = (snapshot.child("unreadCount").value as? Map<String, Int>) ?: emptyMap(),
                    isPinned = snapshot.child("isPinned").getValue(Boolean::class.java) ?: false,
                    pinOrder = snapshot.child("pinOrder").getValue(Long::class.java) ?: 0L,
                    disappearingTimer = snapshot.child("disappearingTimer").getValue(Long::class.java) ?: 0L,
                    wallpaperUrl = snapshot.child("wallpaperUrl").getValue(String::class.java),
                    themeColor = snapshot.child("themeColor").getValue(String::class.java),
                    type = try {
                        ChatType.valueOf(snapshot.child("type").getValue(String::class.java) ?: "PRIVATE")
                    } catch (e: Exception) { ChatType.PRIVATE },
                    creatorId = snapshot.child("creatorId").getValue(String::class.java),
                    admins = snapshot.child("admins").children.mapNotNull { it.getValue(String::class.java) },
                    isMuted = snapshot.child("isMuted").getValue(Boolean::class.java) ?: false,
                    muteUntil = snapshot.child("muteUntil").getValue(Long::class.java)
                )
                trySend(chat)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        chatRef.addValueEventListener(listener)

        awaitClose {
            chatRef.removeEventListener(listener)
        }
    }

    // Create or get existing chat between two users
    suspend fun getOrCreateChat(currentUserId: String, otherUserId: String): String {
        if (currentUserId == otherUserId) throw Exception("Cannot chat with yourself")

        // First, check if a chat with these participants already exists
        val chatsSnapshot = databaseRef.child("chats").get().await()
        for (chatSnapshot in chatsSnapshot.children) {
            val chat = chatSnapshot.getValue(Chat::class.java)
            if (chat != null && chat.participants.contains(currentUserId) && chat.participants.contains(otherUserId)) {
                android.util.Log.d("MessagingRepository", "Found existing chat: ${chat.id}")
                return chat.id
            }
            // Also check participants_map
            val participantsMap = chatSnapshot.child("participants_map").value as? Map<String, Boolean>
            if (participantsMap?.containsKey(currentUserId) == true && participantsMap?.containsKey(otherUserId) == true) {
                val existingChatId = chatSnapshot.key ?: continue
                android.util.Log.d("MessagingRepository", "Found existing chat via map: $existingChatId")
                return existingChatId
            }
        }

        // If not found, generate a new one
        val chatId = generateChatId(currentUserId, otherUserId)
        val chatRef = databaseRef.child("chats").child(chatId)
        
        // Fetch other user's name for the title
        val otherUser = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(otherUserId).get().await()
        val otherUserName = otherUser.getString("displayName")
            ?: otherUser.getString("username")
            ?: otherUser.getString("phoneNumber")
            ?: otherUser.getString("email")
            ?: "User"

        // Create new chat with explicit title and participants map for easier searching
        val chatData = mapOf(
            "id" to chatId,
            "participants" to listOf(currentUserId, otherUserId),
            "participants_map" to mapOf(currentUserId to true, otherUserId to true),
            "title" to otherUserName,
            "unreadCount" to mapOf(currentUserId to 0, otherUserId to 0),
            "createdAt" to System.currentTimeMillis(),
            "lastMessageTimestamp" to System.currentTimeMillis()
        )
        chatRef.setValue(chatData).await()

        // ALSO create in Firestore for Home Screen visibility
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("chats").document(chatId)
            .set(chatData).await()

        return chatId
    }

    // Mark messages as read
    suspend fun markMessagesAsRead(chatId: String, userId: String) {
        val messagesRef = databaseRef.child("messages").child(chatId)
        val snapshot = messagesRef.get().await()

        val updates = mutableMapOf<String, Any>()
        snapshot.children.forEach { messageSnapshot ->
            val message = messageSnapshot.getValue(Message::class.java)
            if (message != null && message.senderId != userId && !message.isRead) {
                updates["${messageSnapshot.key}/isRead"] = true
            }
        }

        if (updates.isNotEmpty()) {
            messagesRef.updateChildren(updates).await()
        }

        // Update unread count
        updateUnreadCount(chatId, userId, 0)
    }

    private suspend fun updateChatMetadata(chatId: String, lastMessage: Message) {
        val chatRef = databaseRef.child("chats").child(chatId)
        val updates = mapOf(
            "lastMessage" to lastMessage,
            "lastMessageTimestamp" to lastMessage.timestamp
        )
        chatRef.updateChildren(updates).await()

        // ALSO update Firestore for Home Screen preview
        // Fetch full chat data from Realtime Database to ensure all fields are included
        val chatSnapshot = chatRef.get().await()
        if (chatSnapshot.exists()) {
            val chatData = chatSnapshot.value as? Map<*, *>
            if (chatData != null) {
                // Merge the updates with existing chat data
                val fullData = chatData.toMutableMap()
                fullData["lastMessage"] = lastMessage
                fullData["lastMessageTimestamp"] = lastMessage.timestamp
                
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("chats").document(chatId)
                    .set(fullData, com.google.firebase.firestore.SetOptions.merge()).await()
            }
        }
    }

    private suspend fun updateUnreadCount(chatId: String, userId: String, count: Int) {
        val chatRef = databaseRef.child("chats").child(chatId).child("unreadCount").child(userId)
        chatRef.setValue(count).await()
    }

    private fun generateChatId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }

    // Delete a message (soft delete)
    suspend fun deleteMessage(chatId: String, messageId: String, userId: String) {
        val messageRef = databaseRef.child("messages").child(chatId).child(messageId)
        val snapshot = messageRef.get().await()
        val message = snapshot.getValue(Message::class.java)
        
        // Only allow deletion if user is the sender
        if (message?.senderId == userId) {
            val updates = mapOf(
                "isDeleted" to true,
                "deletedAt" to System.currentTimeMillis(),
                "content" to "This message was deleted"
            )
            messageRef.updateChildren(updates).await()
        }
    }

    // Add or remove a reaction to a message
    suspend fun addReaction(chatId: String, messageId: String, userId: String, emoji: String) {
        val messageRef = databaseRef.child("messages").child(chatId).child(messageId)
        val reactionsRef = messageRef.child("reactions")
        
        val currentReactions = reactionsRef.get().await().getValue<Map<String, String>>() ?: emptyMap()
        
        // Toggle reaction - remove if same emoji exists, add if different
        val updatedReactions = currentReactions.toMutableMap()
        if (currentReactions[userId] == emoji) {
            updatedReactions.remove(userId) // Remove if same emoji
        } else {
            updatedReactions[userId] = emoji // Add or update emoji
        }
        
        reactionsRef.setValue(updatedReactions).await()
    }

    // Set typing indicator
    suspend fun setTypingStatus(chatId: String, userId: String, isTyping: Boolean) {
        val typingRef = databaseRef.child("typing").child(chatId).child(userId)
        if (isTyping) {
            typingRef.setValue(System.currentTimeMillis()).await()
        } else {
            typingRef.removeValue().await()
        }
    }

    // Observe typing status
    fun observeTypingStatus(chatId: String, currentUserId: String): Flow<List<String>> = callbackFlow {
        val typingRef = databaseRef.child("typing").child(chatId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val typingUsers = snapshot.children
                    .mapNotNull { it.key }
                    .filter { it != currentUserId }
                trySend(typingUsers)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        typingRef.addValueEventListener(listener)
        awaitClose { typingRef.removeEventListener(listener) }
    }

    // Create a group chat
    suspend fun createGroupChat(name: String, members: List<String>, creatorId: String): String {
        val chatId = databaseRef.child("chats").push().key ?: throw Exception("Failed to generate chat ID")
        
        val participantsMap = members.associateWith { true }
        
        val chatData = mapOf(
            "id" to chatId,
            "type" to "GROUP",
            "title" to name,
            "participants" to members,
            "participants_map" to participantsMap,
            "creatorId" to creatorId,
            "createdAt" to System.currentTimeMillis(),
            "lastMessageTimestamp" to System.currentTimeMillis(),
            "unreadCount" to members.associateWith { 0 }
        )
        
        // Save to Realtime Database
        databaseRef.child("chats").child(chatId).setValue(chatData).await()
        
        // Save to Firestore
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("chats")
            .document(chatId)
            .set(chatData)
            .await()
        
        return chatId
    }

    // Update chat settings
    suspend fun updateChatSettings(chatId: String, settings: Map<String, Any?>) {
        val chatRef = databaseRef.child("chats").child(chatId)
        chatRef.updateChildren(settings).await()
        
        // Also update Firestore
        val firestoreUpdates = settings.filterValues { it != null }
        if (firestoreUpdates.isNotEmpty()) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .update(firestoreUpdates)
                .await()
        }
    }

    // Clear chat for a specific user (soft delete messages)
    suspend fun clearChatForUser(chatId: String, userId: String) {
        // Mark all messages as deleted for this user
        val messagesRef = databaseRef.child("messages").child(chatId)
        val snapshot = messagesRef.get().await()
        
        val updates = mutableMapOf<String, Any>()
        snapshot.children.forEach { messageSnapshot ->
            val messageId = messageSnapshot.key ?: return@forEach
            updates["$messageId/clearedFor/$userId"] = true
        }
        
        if (updates.isNotEmpty()) {
            messagesRef.updateChildren(updates).await()
        }
    }

    // Mark message as read with timestamp
    suspend fun markMessageAsRead(chatId: String, messageId: String, userId: String) {
        val messageRef = databaseRef.child("messages").child(chatId).child(messageId)
        val readByRef = messageRef.child("readBy").child(userId)
        readByRef.setValue(System.currentTimeMillis()).await()
    }

    // Pin/Unpin chat
    suspend fun pinChat(chatId: String, userId: String, isPinned: Boolean) {
        val pinRef = databaseRef.child("userSettings").child(userId).child("pinnedChats").child(chatId)
        if (isPinned) {
            pinRef.setValue(System.currentTimeMillis()).await()
        } else {
            pinRef.removeValue().await()
        }
    }

    // Get pinned chats for user
    fun getPinnedChats(userId: String): Flow<List<String>> = callbackFlow {
        val pinnedRef = databaseRef.child("userSettings").child(userId).child("pinnedChats")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val pinnedChats = snapshot.children.mapNotNull { it.key }
                trySend(pinnedChats)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        pinnedRef.addValueEventListener(listener)
        awaitClose { pinnedRef.removeEventListener(listener) }
    }

    // Schedule a message for later
    suspend fun scheduleMessage(chatId: String, senderId: String, content: String, scheduledTime: Long, replyToMessageId: String? = null): String {
        val scheduledRef = databaseRef.child("scheduledMessages").child(senderId).push()
        val scheduledId = scheduledRef.key ?: throw Exception("Failed to generate scheduled message ID")
        
        val scheduledMessage = mapOf(
            "id" to scheduledId,
            "chatId" to chatId,
            "senderId" to senderId,
            "content" to content,
            "messageType" to "TEXT",
            "scheduledTime" to scheduledTime,
            "replyToMessageId" to replyToMessageId,
            "isSent" to false,
            "createdAt" to System.currentTimeMillis()
        )
        
        scheduledRef.setValue(scheduledMessage).await()
        return scheduledId
    }

    // Get scheduled messages for user
    fun getScheduledMessages(userId: String): Flow<List<com.openchat.app.data.model.ScheduledMessage>> = callbackFlow {
        val scheduledRef = databaseRef.child("scheduledMessages").child(userId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = snapshot.children.mapNotNull { 
                    it.getValue(com.openchat.app.data.model.ScheduledMessage::class.java) 
                }.filter { !it.isSent }
                trySend(messages.sortedBy { it.scheduledTime })
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        scheduledRef.addValueEventListener(listener)
        awaitClose { scheduledRef.removeEventListener(listener) }
    }

    // Cancel scheduled message
    suspend fun cancelScheduledMessage(userId: String, messageId: String) {
        databaseRef.child("scheduledMessages").child(userId).child(messageId).removeValue().await()
    }

    // Create a poll
    suspend fun createPoll(chatId: String, senderId: String, question: String, options: List<String>): String {
        val pollId = databaseRef.child("polls").push().key ?: throw Exception("Failed to generate poll ID")
        
        val pollOptions = options.mapIndexed { index, optionText ->
            com.openchat.app.data.model.PollOption(
                id = "option_$index",
                text = optionText,
                votes = emptyMap()
            )
        }
        
        val poll = com.openchat.app.data.model.Poll(
            id = pollId,
            question = question,
            options = pollOptions
        )
        
        // Save poll
        databaseRef.child("polls").child(pollId).setValue(poll).await()
        
        // Send poll as message
        val message = Message(
            senderId = senderId,
            content = "📊 Poll: $question",
            messageType = MessageType.POLL,
            timestamp = System.currentTimeMillis()
        )
        
        sendMessage(chatId, message)
        
        return pollId
    }

    // Vote on poll
    suspend fun voteOnPoll(pollId: String, userId: String, optionId: String) {
        val pollRef = databaseRef.child("polls").child(pollId)
        val votesRef = pollRef.child("options").child(optionId).child("votes").child(userId)
        
        // Toggle vote - remove if exists, add if not
        val currentVote = votesRef.get().await().getValue(String::class.java)
        if (currentVote != null) {
            votesRef.removeValue().await()
        } else {
            votesRef.setValue(optionId).await()
        }
    }

    // Get poll results
    fun getPoll(pollId: String): Flow<com.openchat.app.data.model.Poll?> = callbackFlow {
        val pollRef = databaseRef.child("polls").child(pollId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val poll = snapshot.getValue(com.openchat.app.data.model.Poll::class.java)
                trySend(poll)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        pollRef.addValueEventListener(listener)
        awaitClose { pollRef.removeEventListener(listener) }
    }

    // Search messages by date range
    suspend fun searchMessagesByDate(chatId: String, startDate: Long, endDate: Long): List<Message> {
        val messagesRef = databaseRef.child("messages").child(chatId)
        val snapshot = messagesRef.get().await()
        
        return snapshot.children.mapNotNull { it.getValue(Message::class.java) }
            .filter { it.timestamp in startDate..endDate }
            .sortedBy { it.timestamp }
    }

    // Backup chat data
    suspend fun backupChat(chatId: String, userId: String): String {
        val chatRef = databaseRef.child("chats").child(chatId)
        val messagesRef = databaseRef.child("messages").child(chatId)
        
        val chatData = chatRef.get().await().getValue(Chat::class.java)
        val messagesSnapshot = messagesRef.get().await()
        val messages = messagesSnapshot.children.mapNotNull { it.getValue(Message::class.java) }
        
        val backupData = mapOf(
            "chatId" to chatId,
            "backedUpAt" to System.currentTimeMillis(),
            "chat" to chatData,
            "messages" to messages,
            "messageCount" to messages.size
        )
        
        val backupRef = databaseRef.child("backups").child(userId).child(chatId)
        backupRef.setValue(backupData).await()
        
        return "Backup completed: ${messages.size} messages saved"
    }

    // Restore chat from backup
    suspend fun restoreChatFromBackup(userId: String, chatId: String): Boolean {
        val backupRef = databaseRef.child("backups").child(userId).child(chatId)
        val backupSnapshot = backupRef.get().await()
        
        if (!backupSnapshot.exists()) return false
        
        val backupData = backupSnapshot.getValue(Map::class.java) ?: return false
        val messages = (backupData["messages"] as? List<Map<String, Any>>) ?: emptyList()
        
        for (messageData in messages) {
            val messageRef = databaseRef.child("messages").child(chatId).push()
            val message = Message(
                id = messageRef.key ?: continue,
                senderId = messageData["senderId"] as? String ?: "",
                senderName = messageData["senderName"] as? String ?: "",
                content = messageData["content"] as? String ?: "",
                timestamp = (messageData["timestamp"] as? Number)?.toLong() ?: 0L,
                messageType = try { MessageType.valueOf(messageData["messageType"] as? String ?: "TEXT") } catch (e: Exception) { MessageType.TEXT }
            )
            messageRef.setValue(message).await()
        }
        
        val chatData = backupData["chat"] as? Map<String, Any>
        if (chatData != null) {
            val chatRef = databaseRef.child("chats").child(chatId)
            chatRef.updateChildren(chatData.filter { it.key != "messages" }.toMap()).await()
        }
        
        return true
    }

    // Get user's chat folders
    fun getUserChatFolders(userId: String): Flow<List<com.openchat.app.data.model.ChatFolder>> = callbackFlow {
        val foldersRef = databaseRef.child("userSettings").child(userId).child("folders")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val folders = snapshot.children.mapNotNull { 
                    it.getValue(com.openchat.app.data.model.ChatFolder::class.java) 
                }
                trySend(folders)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        foldersRef.addValueEventListener(listener)
        awaitClose { foldersRef.removeEventListener(listener) }
    }

    // Create chat folder
    suspend fun createChatFolder(userId: String, folder: com.openchat.app.data.model.ChatFolder) {
        val folderRef = databaseRef.child("userSettings").child(userId).child("folders").child(folder.id)
        folderRef.setValue(folder).await()
    }

    // Delete chat folder
    suspend fun deleteChatFolder(userId: String, folderId: String) {
        databaseRef.child("userSettings").child(userId).child("folders").child(folderId).removeValue().await()
    }

    // Add chat to folder
    suspend fun addChatToFolder(userId: String, folderId: String, chatId: String) {
        val chatRef = databaseRef.child("userSettings").child(userId).child("folders").child(folderId).child("chatIds")
        chatRef.child(chatId).setValue(true).await()
    }

    // Remove chat from folder
    suspend fun removeChatFromFolder(userId: String, folderId: String, chatId: String) {
        databaseRef.child("userSettings").child(userId).child("folders").child(folderId).child("chatIds").child(chatId).removeValue().await()
    }

    // Save draft message
    suspend fun saveDraft(userId: String, draft: com.openchat.app.data.model.DraftMessage) {
        val draftRef = databaseRef.child("drafts").child(userId).child(draft.chatId)
        draftRef.setValue(draft).await()
    }

    // Get draft for chat
    suspend fun getDraft(userId: String, chatId: String): com.openchat.app.data.model.DraftMessage? {
        val draftRef = databaseRef.child("drafts").child(userId).child(chatId)
        val snapshot = draftRef.get().await()
        return snapshot.getValue(com.openchat.app.data.model.DraftMessage::class.java)
    }

    // Clear draft for chat
    suspend fun clearDraft(userId: String, chatId: String) {
        databaseRef.child("drafts").child(userId).child(chatId).removeValue().await()
    }

    // Mark voice message as listened
    suspend fun markVoiceAsListened(chatId: String, messageId: String, userId: String) {
        val messageRef = databaseRef.child("messages").child(chatId).child(messageId)
        messageRef.child("listenedBy").child(userId).setValue(System.currentTimeMillis()).await()
    }

    // Edit message
    suspend fun editMessage(chatId: String, messageId: String, userId: String, newContent: String) {
        val messageRef = databaseRef.child("messages").child(chatId).child(messageId)
        val snapshot = messageRef.get().await()
        val message = snapshot.getValue(Message::class.java)
        
        // Only sender can edit
        if (message?.senderId == userId) {
            val updates = mapOf(
                "content" to newContent,
                "isEdited" to true,
                "editedAt" to System.currentTimeMillis(),
                "originalContent" to (message.originalContent ?: message.content)
            )
            messageRef.updateChildren(updates).await()
        }
    }

    // Mute/Unmute chat
    suspend fun muteChat(userId: String, chatId: String, muteUntil: Long?) {
        val muteRef = databaseRef.child("userSettings").child(userId).child("mutedChats").child(chatId)
        if (muteUntil != null) {
            muteRef.setValue(muteUntil).await()
        } else {
            muteRef.removeValue().await()
        }
    }

    // Get muted chats
    fun getMutedChats(userId: String): Flow<Map<String, Long>> = callbackFlow {
        val mutedRef = databaseRef.child("userSettings").child(userId).child("mutedChats")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val muted = snapshot.children.mapNotNull { 
                    it.key?.let { key ->
                        it.getValue(Long::class.java)?.let { value ->
                            key to value
                        }
                    }
                }.toMap()
                trySend(muted)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        mutedRef.addValueEventListener(listener)
        awaitClose { mutedRef.removeEventListener(listener) }
    }

    // ============== STORIES / STATUS (Instagram/Snapchat style) ==============
    
    // Create a new story
    suspend fun createStory(story: com.openchat.app.data.model.Story): String {
        val storyRef = databaseRef.child("stories").child(story.userId).push()
        val storyId = storyRef.key ?: throw Exception("Failed to generate story ID")
        
        val storyWithId = story.copy(id = storyId)
        storyRef.setValue(storyWithId).await()
        
        return storyId
    }
    
    // Get stories from all contacts
    fun getStories(userIds: List<String>): Flow<List<com.openchat.app.data.model.Story>> = callbackFlow {
        val storiesList = mutableListOf<com.openchat.app.data.model.Story>()
        val listeners = mutableListOf<ValueEventListener>()
        var completedCount = 0
        
        userIds.forEach { userId ->
            val storiesRef = databaseRef.child("stories").child(userId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userStories = snapshot.children.mapNotNull { 
                        it.getValue(com.openchat.app.data.model.Story::class.java)?.takeIf { story ->
                            story.expiresAt > System.currentTimeMillis()
                        }
                    }
                    storiesList.removeAll { it.userId == userId }
                    storiesList.addAll(userStories)
                    trySend(storiesList.toList())
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }
            }
            storiesRef.addValueEventListener(listener)
            listeners.add(listener)
        }
        
        awaitClose { 
            userIds.forEachIndexed { index, userId ->
                listeners.getOrNull(index)?.let { listener ->
                    databaseRef.child("stories").child(userId).removeEventListener(listener)
                }
            }
        }
    }
    
    // Mark story as viewed
    suspend fun viewStory(storyId: String, viewerId: String) {
        // Find the story first
        val storiesQuery = databaseRef.child("stories").orderByKey()
        val snapshot = storiesQuery.get().await()
        
        snapshot.children.forEach { userStories ->
            val story = userStories.child(storyId).getValue(com.openchat.app.data.model.Story::class.java)
            if (story != null) {
                val viewerRef = databaseRef.child("stories").child(story.userId).child(storyId)
                    .child("viewers").child(viewerId)
                viewerRef.setValue(System.currentTimeMillis()).await()
            }
        }
    }
    
    // React to story
    suspend fun reactToStory(storyId: String, userId: String, emoji: String) {
        val storiesQuery = databaseRef.child("stories").orderByKey()
        val snapshot = storiesQuery.get().await()
        
        snapshot.children.forEach { userStories ->
            val story = userStories.child(storyId).getValue(com.openchat.app.data.model.Story::class.java)
            if (story != null) {
                val reactionRef = databaseRef.child("stories").child(story.userId).child(storyId)
                    .child("reactions").child(userId)
                reactionRef.setValue(emoji).await()
            }
        }
    }
    
    // Delete story
    suspend fun deleteStory(userId: String, storyId: String) {
        databaseRef.child("stories").child(userId).child(storyId).removeValue().await()
    }

    // ============== FORWARD MESSAGES (Telegram style) ==============
    
    // Forward a message with attribution
    suspend fun forwardMessage(
        originalMessageId: String,
        originalChatId: String,
        targetChatId: String,
        forwardedBy: String
    ): String {
        // Get original message
        val originalRef = databaseRef.child("messages").child(originalChatId).child(originalMessageId)
        val originalSnapshot = originalRef.get().await()
        val originalMessage = originalSnapshot.getValue(Message::class.java)
            ?: throw Exception("Original message not found")
        
        // Create forwarded message
        val targetRef = databaseRef.child("messages").child(targetChatId).push()
        val newMessageId = targetRef.key ?: throw Exception("Failed to generate message ID")
        
        val forwardInfo = com.openchat.app.data.model.ForwardedMessage(
            originalMessageId = originalMessageId,
            originalSenderId = originalMessage.senderId,
            originalSenderName = originalMessage.senderName,
            originalChatId = originalChatId,
            originalTimestamp = originalMessage.timestamp,
            forwardedAt = System.currentTimeMillis(),
            forwardedBy = forwardedBy
        )
        
        val forwarderName = FirebaseAuth.getInstance().currentUser?.displayName
            ?: FirebaseAuth.getInstance().currentUser?.email
            ?: forwardedBy

        val messageMap = mapOf(
            "id" to newMessageId,
            "senderId" to forwardedBy,
            "senderName" to forwarderName,
            "content" to originalMessage.content,
            "timestamp" to System.currentTimeMillis(),
            "messageType" to originalMessage.messageType.name,
            "forwardedFrom" to mapOf(
                "originalSenderName" to forwardInfo.originalSenderName,
                "originalTimestamp" to forwardInfo.originalTimestamp,
                "forwardedAt" to forwardInfo.forwardedAt
            )
        )
        
        targetRef.setValue(messageMap).await()
        
        // Update chat's last message
        val chatRef = databaseRef.child("chats").child(targetChatId)
        chatRef.child("lastMessage").setValue(messageMap).await()
        chatRef.child("lastMessageTimestamp").setValue(System.currentTimeMillis()).await()
        
        return newMessageId
    }

    // ============== CHANNELS (Telegram style) ==============
    
    // Create a channel (broadcast group)
    suspend fun createChannel(
        creatorId: String,
        channelName: String,
        description: String? = null
    ): String {
        val chatRef = databaseRef.child("chats").push()
        val chatId = chatRef.key ?: throw Exception("Failed to generate chat ID")
        
        val channel = mapOf(
            "id" to chatId,
            "title" to channelName,
            "type" to "CHANNEL",
            "creatorId" to creatorId,
            "participants" to mapOf(creatorId to true),
            "admins" to listOf(creatorId),
            "description" to description,
            "createdAt" to System.currentTimeMillis(),
            "lastMessageTimestamp" to System.currentTimeMillis()
        )
        
        chatRef.setValue(channel).await()
        return chatId
    }
    
    // Subscribe to channel
    suspend fun subscribeToChannel(channelId: String, userId: String) {
        val subscriberRef = databaseRef.child("chats").child(channelId).child("participants").child(userId)
        subscriberRef.setValue(true).await()
        
        val userSubRef = databaseRef.child("userSettings").child(userId).child("subscriptions").child(channelId)
        userSubRef.setValue(System.currentTimeMillis()).await()
    }
    
    // Unsubscribe from channel
    suspend fun unsubscribeFromChannel(channelId: String, userId: String) {
        databaseRef.child("chats").child(channelId).child("participants").child(userId).removeValue().await()
        databaseRef.child("userSettings").child(userId).child("subscriptions").child(channelId).removeValue().await()
    }
    
    // Post to channel (admin only)
    suspend fun postToChannel(
        channelId: String,
        senderId: String,
        content: String,
        messageType: MessageType = MessageType.TEXT
    ): String {
        // Check if sender is admin
        val channelRef = databaseRef.child("chats").child(channelId)
        val channelSnapshot = channelRef.get().await()
        val admins = channelSnapshot.child("admins").getValue<List<String>>() ?: emptyList()
        
        if (!admins.contains(senderId)) {
            throw Exception("Only admins can post to channels")
        }
        
        val messageRef = databaseRef.child("messages").child(channelId).push()
        val messageId = messageRef.key ?: throw Exception("Failed to generate message ID")
        
        val message = mapOf(
            "id" to messageId,
            "senderId" to senderId,
            "content" to content,
            "timestamp" to System.currentTimeMillis(),
            "messageType" to messageType.name,
            "isChannelPost" to true,
            "views" to 0
        )
        
        messageRef.setValue(message).await()
        
        // Update channel last message
        channelRef.child("lastMessage").setValue(message).await()
        channelRef.child("lastMessageTimestamp").setValue(System.currentTimeMillis()).await()
        
        return messageId
    }
    
    // View channel post
    suspend fun viewChannelPost(channelId: String, messageId: String, userId: String) {
        val viewRef = databaseRef.child("messages").child(channelId).child(messageId)
            .child("viewers").child(userId)
        viewRef.setValue(System.currentTimeMillis()).await()
    }

    // ============== CODE SNIPPETS (GitHub style) ==============
    
    // Send code snippet
    suspend fun sendCodeSnippet(
        chatId: String,
        senderId: String,
        code: String,
        language: String,
        fileName: String? = null
    ): String {
        val messageRef = databaseRef.child("messages").child(chatId).push()
        val messageId = messageRef.key ?: throw Exception("Failed to generate message ID")
        
        val message = mapOf(
            "id" to messageId,
            "senderId" to senderId,
            "content" to code,
            "timestamp" to System.currentTimeMillis(),
            "messageType" to "CODE",
            "codeLanguage" to language,
            "fileName" to fileName
        )
        
        messageRef.setValue(message).await()
        return messageId
    }

    // Fix chat with missing participants data
    suspend fun fixChatParticipants(chatId: String, participants: List<String>) {
        android.util.Log.d("MessagingRepository", "Fixing chat $chatId with participants: $participants")
        val chatRef = databaseRef.child("chats").child(chatId)
        
        val updates = mapOf(
            "participants" to participants,
            "participants_map" to participants.associateWith { true }
        )
        
        chatRef.updateChildren(updates).await()
        android.util.Log.d("MessagingRepository", "Chat $chatId participants fixed successfully")
    }

    // Get all chats for a user
    suspend fun getUserChats(userId: String): List<Chat> {
        val snapshot = databaseRef.child("chats").get().await()
        return snapshot.children.mapNotNull { chatSnapshot ->
            val chat = chatSnapshot.getValue(Chat::class.java)
            if (chat != null && (chat.participants.contains(userId) || (chatSnapshot.child("participants_map").value as? Map<String, *>)?.containsKey(userId) == true)) {
                chat
            } else null
        }
    }

    // Get chat messages (for export)
    suspend fun getChatMessages(chatId: String): List<Message> {
        val snapshot = databaseRef.child("messages").child(chatId).get().await()
        return snapshot.children.mapNotNull { it.getValue(Message::class.java) }.sortedBy { it.timestamp }
    }
}