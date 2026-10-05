package com.openchat.app.data.repository

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.openchat.app.core.Result
import com.openchat.app.core.safeApiCall
import com.openchat.app.data.local.dao.UserDao
import com.openchat.app.data.local.entity.UserEntity
import com.openchat.app.domain.model.User
import com.openchat.app.domain.repository.UserRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val database: FirebaseDatabase,
    private val userDao: UserDao
) : UserRepository {
    
    override fun observeCurrentUser(): Flow<User?> {
        val currentUserId = firebaseAuth.currentUser?.uid
        return if (currentUserId != null) {
            userDao.observeUser(currentUserId).map { it?.toDomainModel() }
        } else {
            kotlinx.coroutines.flow.flow { emit(null) }
        }
    }
    
    override suspend fun getCurrentUser(): User? {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return null
        val localUser = userDao.getUserById(currentUserId)
        
        if (localUser == null) {
            try {
                val remoteUser = firestore.collection("users").document(currentUserId).get().await()
                val user = UserEntity(
                    id = currentUserId,
                    phoneNumber = remoteUser.getString("phoneNumber") ?: "",
                    displayName = remoteUser.getString("displayName") ?: "",
                    username = remoteUser.getString("username"),
                    bio = remoteUser.getString("bio"),
                    photoUrl = remoteUser.getString("photoUrl"),
                    isOnline = true,
                    lastSeen = System.currentTimeMillis()
                )
                userDao.insertUser(user)
                return user.toDomainModel()
            } catch (e: Exception) {
                return null
            }
        }
        
        return localUser.toDomainModel()
    }
    
    override suspend fun getUser(userId: String): Result<User> = safeApiCall {
        val localUser = userDao.getUserById(userId)
        if (localUser != null) {
            return@safeApiCall localUser.toDomainModel()
        }
        
        val remoteUser = firestore.collection("users").document(userId).get().await()
        val user = UserEntity(
            id = userId,
            phoneNumber = remoteUser.getString("phoneNumber") ?: "",
            displayName = remoteUser.getString("displayName") ?: "",
            username = remoteUser.getString("username"),
            bio = remoteUser.getString("bio"),
            photoUrl = remoteUser.getString("photoUrl"),
            isOnline = remoteUser.getBoolean("isOnline") ?: false,
            lastSeen = remoteUser.getLong("lastSeen")
        )
        userDao.insertUser(user)
        user.toDomainModel()
    }
    
    override suspend fun getUsers(userIds: List<String>): Result<List<User>> = safeApiCall {
        userIds.mapNotNull { userDao.getUserById(it)?.toDomainModel() }
    }
    
    override suspend fun updateProfile(displayName: String?, bio: String?, photoUri: Uri?): Result<Unit> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        
        var photoUrl: String? = null
        try {
            if (photoUri != null) {
                val storageRef = storage.reference.child("profile_photos/$currentUserId.jpg")
                storageRef.putFile(photoUri).await()
                photoUrl = storageRef.downloadUrl.await().toString()
            }
            
            val updates = hashMapOf<String, Any>()
            displayName?.let { updates["displayName"] = it }
            bio?.let { updates["bio"] = it }
            photoUrl?.let { updates["photoUrl"] = it }
            updates["updatedAt"] = System.currentTimeMillis()
            
            // Use set with merge so it creates the document if it doesn't exist
            firestore.collection("users").document(currentUserId)
                .set(updates, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
            // Ignore remote failure to prevent blocking the user
        }
        
        val localUser = userDao.getUserById(currentUserId)
        if (localUser != null) {
            userDao.updateUser(
                localUser.copy(
                    displayName = displayName ?: localUser.displayName,
                    bio = bio ?: localUser.bio,
                    photoUrl = photoUrl ?: localUser.photoUrl,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            // Create initial local user if not exists
            userDao.insertUser(
                UserEntity(
                    id = currentUserId,
                    displayName = displayName ?: "",
                    bio = bio ?: "",
                    photoUrl = photoUrl,
                    phoneNumber = firebaseAuth.currentUser?.phoneNumber ?: "",
                    username = null,
                    isOnline = true,
                    lastSeen = System.currentTimeMillis()
                )
            )
        }
    }
    
    override suspend fun updateUsername(username: String): Result<Unit> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        
        val existingUser = firestore.collection("users")
            .whereEqualTo("username", username)
            .get()
            .await()
        
        if (!existingUser.isEmpty) {
            throw IllegalArgumentException("Username already taken")
        }
        
        firestore.collection("users").document(currentUserId)
            .update("username", username, "updatedAt", System.currentTimeMillis())
            .await()
    }
    
    override suspend fun searchUsers(query: String): Result<List<User>> = safeApiCall {
        if (query.length < 3) return@safeApiCall emptyList()
        
        val usersByName = firestore.collection("users")
            .whereGreaterThanOrEqualTo("displayName", query)
            .whereLessThanOrEqualTo("displayName", query + "\uf8ff")
            .get()
            .await()
        
        val usersByUsername = firestore.collection("users")
            .whereGreaterThanOrEqualTo("username", query)
            .whereLessThanOrEqualTo("username", query + "\uf8ff")
            .get()
            .await()
        
        (usersByName.documents + usersByUsername.documents)
            .distinctBy { it.id }
            .map { doc ->
                User(
                    id = doc.id,
                    phoneNumber = doc.getString("phoneNumber") ?: "",
                    displayName = doc.getString("displayName") ?: "",
                    username = doc.getString("username"),
                    photoUrl = doc.getString("photoUrl"),
                    isOnline = doc.getBoolean("isOnline") ?: false
                )
            }
    }
    
    override suspend fun updateOnlineStatus(isOnline: Boolean): Result<Unit> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return@safeApiCall
        val statusRef = database.reference.child("status").child(currentUserId)
        
        if (isOnline) {
            statusRef.child("online").setValue(true).await()
            statusRef.child("lastSeen").setValue(System.currentTimeMillis()).await()
            database.reference.child("status").child(currentUserId).child("online").onDisconnect().setValue(false)
            database.reference.child("status").child(currentUserId).child("lastSeen").onDisconnect().setValue(System.currentTimeMillis())
        } else {
            statusRef.child("online").setValue(false).await()
            statusRef.child("lastSeen").setValue(System.currentTimeMillis()).await()
        }
        
        // Going online must NOT null out lastSeen: the "last seen" UI depends on that
        // timestamp, and wiping it every time the app came to the foreground meant the
        // value was destroyed before anyone could read it.
        val lastSeen = if (isOnline) {
            userDao.getUserById(currentUserId)?.lastSeen
        } else {
            System.currentTimeMillis()
        }
        userDao.updateOnlineStatus(currentUserId, isOnline, lastSeen)
    }
    
    override fun observeUserOnlineStatus(userId: String): Flow<Boolean> = callbackFlow {
        val statusRef = database.reference.child("status").child(userId).child("online")
        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val online = snapshot.getValue(Boolean::class.java) ?: false
                trySend(online)
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                close(error.toException())
            }
        }
        statusRef.addValueEventListener(listener)
        awaitClose { statusRef.removeEventListener(listener) }
    }
    
    override fun observeUserLastSeen(userId: String): Flow<Long?> = callbackFlow {
        val lastSeenRef = database.reference.child("status").child(userId).child("lastSeen")
        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val lastSeen = snapshot.getValue(Long::class.java)
                trySend(lastSeen)
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                close(error.toException())
            }
        }
        lastSeenRef.addValueEventListener(listener)
        awaitClose { lastSeenRef.removeEventListener(listener) }
    }
    
    private fun UserEntity.toDomainModel(): User {
        return User(
            id = id,
            phoneNumber = phoneNumber,
            displayName = displayName,
            username = username,
            bio = bio,
            photoUrl = photoUrl,
            isOnline = isOnline,
            lastSeen = lastSeen,
            createdAt = createdAt
        )
    }
}
