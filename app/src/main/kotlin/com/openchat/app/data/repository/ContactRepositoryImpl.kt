package com.openchat.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.openchat.app.core.Result
import com.openchat.app.core.safeApiCall
import com.openchat.app.data.local.dao.ContactDao
import com.openchat.app.data.local.entity.ContactEntity
import com.openchat.app.domain.model.User
import com.openchat.app.domain.repository.ContactRepository
import com.openchat.app.domain.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val database: FirebaseDatabase,
    private val firebaseAuth: FirebaseAuth,
    private val contactDao: ContactDao,
    private val authRepository: AuthRepository
) : ContactRepository {
    
    override fun observeContacts(): Flow<List<User>> {
        return contactDao.observeAllContacts().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override fun observeRegisteredContacts(): Flow<List<User>> {
        return contactDao.observeAllContacts().map { contactEntities ->
            val phoneNumbers = contactEntities.mapNotNull { it.phoneNumber }.distinct()

            if (phoneNumbers.isNotEmpty()) {
                val registeredUsers = mutableListOf<User>()

                phoneNumbers.chunked(10).forEach { chunk ->
                    try {
                        val querySnapshot = firestore.collection("users")
                            .whereIn("phoneNumber", chunk)
                            .get()
                            .await()

                        val users: List<User> = querySnapshot.documents.map { doc: DocumentSnapshot ->
                            User(
                                id = doc.id,
                                phoneNumber = doc.getString("phoneNumber") ?: "",
                                displayName = doc.getString("displayName") ?: "",
                                username = doc.getString("username"),
                                photoUrl = doc.getString("photoUrl"),
                                isOnline = doc.getBoolean("isOnline") ?: false
                            )
                        }
                        registeredUsers.addAll(users)

                        // Persist the registration flag so the next read is a pure
                        // Room lookup. Guarded so it only fires on an actual change:
                        // this write invalidates observeAllContacts, which re-triggers
                        // this whole batch - an unguarded write here caused a feedback
                        // loop of O(N) Firestore queries per emission.
                        users.forEach { user ->
                            val contactEntity = contactEntities.find { it.phoneNumber == user.phoneNumber }
                            if (contactEntity != null &&
                                (!contactEntity.isRegistered || contactEntity.userId != user.id)
                            ) {
                                contactDao.updateRegistrationStatus(contactEntity.id, true, user.id)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("ContactRepositoryImpl", "Error processing contact chunk", e)
                    }
                }

                // ONLY registered contacts belong in this list. The old code had an
                // `else` branch that emitted a synthetic User for every address-book
                // entry, so "contacts on OpenChat" and the group-member picker both
                // listed the user's entire phone book instead of OpenChat users.
                contactEntities.mapNotNull { localContact ->
                    val remoteUser = registeredUsers.find { it.phoneNumber == localContact.phoneNumber }
                        ?: return@mapNotNull null
                    remoteUser.copy(
                        displayName = localContact.displayName.ifBlank { remoteUser.displayName },
                        isContact = true
                    )
                }
            } else {
                emptyList()
            }
        }
    }
    
    override suspend fun syncContacts(): Result<Unit> = safeApiCall {
        // ContentResolver.query walks the whole address book and blocks on binder IO;
        // without this it ran on whichever dispatcher the caller happened to be on.
        withContext(Dispatchers.IO) {
        val contacts = mutableListOf<ContactEntity>()

        // A null cursor means READ_CONTACTS was denied. Returning Success here made
        // the sync look like it had succeeded while storing nothing.
        val contentResolver: ContentResolver = context.contentResolver
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        ) ?: throw Exception("Cannot read contacts - permission not granted")

        cursor.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoUriIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            while (it.moveToNext()) {
                val contactId = it.getString(idIndex)
                val displayName = it.getString(nameIndex)
                val phoneNumber = it.getString(numberIndex)?.replace(Regex("[^\\d+]"), "") // Clean phone number
                val photoUri = it.getString(photoUriIndex)

                if (!contactId.isNullOrBlank() && !phoneNumber.isNullOrBlank()) {
                    contacts.add(
                        ContactEntity(
                            // The Phone provider returns one ROW PER PHONE NUMBER but
                            // every row of a contact shares CONTACT_ID. Using that as
                            // the primary key made each insert REPLACE the previous
                            // one, so after a sync only the last number of each contact
                            // survived. Key on contactId + number instead.
                            id = "$contactId|${android.net.Uri.encode(phoneNumber)}",
                            displayName = displayName ?: "Unknown",
                            phoneNumber = phoneNumber,
                            photoUri = photoUri,
                            isRegistered = false,
                            userId = null
                        )
                    )
                }
            }
        }

        // Clear existing contacts and insert new ones
        contactDao.deleteAllContacts()
        if (contacts.isNotEmpty()) {
            contactDao.insertContacts(contacts)
        }
        }
    }
    
    override suspend fun findUserByPhoneNumber(phoneNumber: String): Result<User?> = safeApiCall {
        val cleanNumber = phoneNumber.replace(Regex("[^\\d+]"), "")
        val snapshot = firestore.collection("users")
            .whereEqualTo("phoneNumber", cleanNumber)
            .limit(1)
            .get()
            .await()
        val doc = snapshot.documents.firstOrNull() ?: return@safeApiCall null
        User(
            id = doc.id,
            phoneNumber = doc.getString("phoneNumber") ?: cleanNumber,
            displayName = doc.getString("displayName") ?: "User",
            username = doc.getString("username"),
            photoUrl = doc.getString("photoUrl"),
            isOnline = doc.getBoolean("isOnline") ?: false
        )
    }
    
    override suspend fun inviteUser(phoneNumber: String): Result<Unit> = safeApiCall {
        // ACTION_SEND opens a generic share sheet and IGNORES EXTRA_PHONE_NUMBER;
        // ACTION_SENDTO with an smsto: URI is the only form that actually targets the
        // recipient. FLAG_ACTIVITY_NEW_TASK is mandatory because `context` is the
        // Application context - startActivity() without it always throws.
        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("smsto:${android.net.Uri.encode(phoneNumber)}")
            putExtra("sms_body", "Join me on OpenChat! Download the app to start chatting.")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }
    
    override suspend fun blockUser(userId: String): Result<Unit> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        database.reference.child("blocks").child(currentUserId).child(userId).setValue(true).await()
    }
    
    override suspend fun unblockUser(userId: String): Result<Unit> = safeApiCall {
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        database.reference.child("blocks").child(currentUserId).child(userId).removeValue().await()
    }
    
    override suspend fun isUserBlocked(userId: String): Boolean {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return false
        val snapshot = database.reference.child("blocks").child(currentUserId).child(userId)
            .get().await()
        return snapshot.exists()
    }
    
    private fun ContactEntity.toDomainModel(): User {
        return User(
            id = userId ?: id,
            phoneNumber = phoneNumber,
            displayName = displayName,
            photoUrl = photoUri,
            isContact = true
        )
    }
}
