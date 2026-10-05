package com.openchat.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.openchat.app.data.model.BuiltInFilters
import com.openchat.app.data.model.CameraFilter
import com.openchat.app.data.model.FilterPack
import com.openchat.app.data.model.FilterType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FilterRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val storage: FirebaseStorage
) {
    companion object {
        private const val FILTERS_PATH = "/filters"
        private const val PACKS_PATH = "/filter_packs"
        private const val USER_FILTERS_PATH = "/user_filters"
    }
    
    /**
     * Get all built-in filters
     */
    fun getBuiltInFilters(): List<CameraFilter> {
        return BuiltInFilters.AllFilters
    }
    
    /**
     * Get the official filter pack
     */
    fun getOfficialPack(): FilterPack {
        return BuiltInFilters.OfficialPack
    }
    
    /**
     * Upload a custom filter pack to share with others
     */
    suspend fun shareFilterPack(pack: FilterPack): Result<String> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid 
                ?: return Result.failure(Exception("User not authenticated"))
            
            val packId = database.reference.child(PACKS_PATH).push().key 
                ?: return Result.failure(Exception("Failed to generate pack ID"))
            
            val packData = hashMapOf(
                "id" to packId,
                "name" to pack.name,
                "description" to pack.description,
                "coverImageUrl" to pack.coverImageUrl,
                "creatorId" to currentUserId,
                "creatorName" to pack.creatorName,
                "downloadCount" to 0,
                "rating" to 5.0f,
                "createdAt" to System.currentTimeMillis(),
                "tags" to pack.tags,
                "filters" to pack.filters.map { filterToMap(it) }
            )
            
            database.reference
                .child(PACKS_PATH)
                .child(packId)
                .setValue(packData)
                .await()
            
            // Also add to user's shared packs
            database.reference
                .child(USER_FILTERS_PATH)
                .child(currentUserId)
                .child("shared_packs")
                .child(packId)
                .setValue(true)
                .await()
            
            Result.success(packId)
        } catch (e: Exception) {
            android.util.Log.e("FilterRepository", "Failed to share filter pack", e)
            Result.failure(e)
        }
    }
    
    /**
     * Download a shared filter pack
     */
    suspend fun downloadFilterPack(packId: String): Result<FilterPack> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid 
                ?: return Result.failure(Exception("User not authenticated"))
            
            // Get pack data
            val snapshot = database.reference
                .child(PACKS_PATH)
                .child(packId)
                .get()
                .await()
            
            if (!snapshot.exists()) {
                return Result.failure(Exception("Pack not found"))
            }
            
            val pack = snapshotToFilterPack(snapshot)
            
            // Add to user's downloaded packs
            database.reference
                .child(USER_FILTERS_PATH)
                .child(currentUserId)
                .child("downloaded_packs")
                .child(packId)
                .setValue(System.currentTimeMillis())
                .await()
            
            // Increment atomically on the server. Read-then-write (pack.downloadCount + 1)
            // lost increments whenever two users downloaded at the same time.
            database.reference
                .child(PACKS_PATH)
                .child(packId)
                .child("downloadCount")
                .setValue(com.google.firebase.database.ServerValue.increment(1))
                .await()
            
            Result.success(pack)
        } catch (e: Exception) {
            android.util.Log.e("FilterRepository", "Failed to download filter pack", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all publicly shared filter packs
     */
    fun observePublicFilterPacks(): Flow<List<FilterPack>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val packs = mutableListOf<FilterPack>()
                
                for (child in snapshot.children) {
                    try {
                        val pack = snapshotToFilterPack(child)
                        packs.add(pack)
                    } catch (e: Exception) {
                        android.util.Log.e("FilterRepository", "Error parsing pack", e)
                    }
                }
                
                // Sort by download count (most popular first)
                trySend(packs.sortedByDescending { it.downloadCount })
            }
            
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("FilterRepository", "Observer cancelled", error.toException())
                // Must close the flow. Without this the collector hangs forever with
                // zero emissions on a permission error, so the UI spinner never
                // resolves and the failure is invisible.
                close(error.toException())
            }
        }
        
        database.reference
            .child(PACKS_PATH)
            .orderByChild("downloadCount")
            .addValueEventListener(listener)
        
        awaitClose {
            database.reference.child(PACKS_PATH).removeEventListener(listener)
        }
    }
    
    /**
     * Get filter packs created by a specific user
     */
    suspend fun getUserFilterPacks(userId: String): List<FilterPack> {
        return try {
            val snapshot = database.reference
                .child(PACKS_PATH)
                .orderByChild("creatorId")
                .equalTo(userId)
                .get()
                .await()
            
            snapshot.children.mapNotNull { child ->
                try {
                    snapshotToFilterPack(child)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FilterRepository", "Failed to get user packs", e)
            emptyList()
        }
    }
    
    /**
     * Send a filter pack to another user via chat
     */
    suspend fun sendFilterPackToUser(packId: String, targetUserId: String): Result<Unit> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid 
                ?: return Result.failure(Exception("User not authenticated"))
            
            val shareData = hashMapOf(
                "packId" to packId,
                "fromUserId" to currentUserId,
                "timestamp" to System.currentTimeMillis(),
                "status" to "pending"
            )
            
            // Add to target user's pending filter shares
            database.reference
                .child(USER_FILTERS_PATH)
                .child(targetUserId)
                .child("pending_shares")
                .push()
                .setValue(shareData)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FilterRepository", "Failed to send filter pack", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get pending filter shares for current user
     */
    fun observePendingFilterShares(): Flow<List<FilterShare>> = callbackFlow {
        val currentUserId = firebaseAuth.currentUser?.uid ?: run {
            trySend(emptyList())
            return@callbackFlow
        }
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val shares = mutableListOf<FilterShare>()
                
                for (child in snapshot.children) {
                    val packId = child.child("packId").getValue(String::class.java) ?: continue
                    val fromUserId = child.child("fromUserId").getValue(String::class.java) ?: continue
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    
                    shares.add(FilterShare(
                        shareId = child.key ?: "",
                        packId = packId,
                        fromUserId = fromUserId,
                        timestamp = timestamp
                    ))
                }
                
                trySend(shares.sortedByDescending { it.timestamp })
            }
            
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("FilterRepository", "Share observer cancelled", error.toException())
                // Must close the flow - see the note in the public-packs observer.
                close(error.toException())
            }
        }
        
        database.reference
            .child(USER_FILTERS_PATH)
            .child(currentUserId)
            .child("pending_shares")
            .addValueEventListener(listener)
        
        awaitClose {
            database.reference
                .child(USER_FILTERS_PATH)
                .child(currentUserId)
                .child("pending_shares")
                .removeEventListener(listener)
        }
    }
    
    /**
     * Accept a pending filter share
     */
    suspend fun acceptFilterShare(shareId: String, packId: String): Result<Unit> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid 
                ?: return Result.failure(Exception("User not authenticated"))
            
            // Download the pack
            val result = downloadFilterPack(packId)
            if (result.isFailure) {
                return Result.failure(result.exceptionOrNull() ?: Exception("Failed to download pack"))
            }
            
            // Remove from pending shares
            database.reference
                .child(USER_FILTERS_PATH)
                .child(currentUserId)
                .child("pending_shares")
                .child(shareId)
                .removeValue()
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FilterRepository", "Failed to accept filter share", e)
            Result.failure(e)
        }
    }
    
    /**
     * Rate a filter pack
     */
    suspend fun rateFilterPack(packId: String, rating: Float): Result<Unit> {
        return try {
            val currentUserId = firebaseAuth.currentUser?.uid 
                ?: return Result.failure(Exception("User not authenticated"))
            
            database.reference
                .child(PACKS_PATH)
                .child(packId)
                .child("ratings")
                .child(currentUserId)
                .setValue(rating)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FilterRepository", "Failed to rate filter pack", e)
            Result.failure(e)
        }
    }
    
    /**
     * Search filter packs by tags or name
     */
    suspend fun searchFilterPacks(query: String): List<FilterPack> {
        return try {
            val snapshot = database.reference
                .child(PACKS_PATH)
                .get()
                .await()
            
            val packs = snapshot.children.mapNotNull { child ->
                try {
                    snapshotToFilterPack(child)
                } catch (e: Exception) {
                    null
                }
            }
            
            // Filter locally (Firebase RTDB doesn't support text search)
            packs.filter { pack ->
                pack.name.contains(query, ignoreCase = true) ||
                pack.description.contains(query, ignoreCase = true) ||
                pack.tags.any { it.contains(query, ignoreCase = true) }
            }
        } catch (e: Exception) {
            android.util.Log.e("FilterRepository", "Failed to search filter packs", e)
            emptyList()
        }
    }
    
    // Helper functions
    private fun filterToMap(filter: CameraFilter): Map<String, Any?> {
        return hashMapOf(
            "id" to filter.id,
            "name" to filter.name,
            "description" to filter.description,
            "thumbnailUrl" to filter.thumbnailUrl,
            "filterType" to filter.filterType.name,
            "shaderCode" to filter.shaderCode,
            "parameters" to filter.parameters.mapValues { (_, param) ->
                hashMapOf(
                    "name" to param.name,
                    "type" to param.type.name,
                    "defaultValue" to param.defaultValue,
                    "minValue" to param.minValue,
                    "maxValue" to param.maxValue,
                    "step" to param.step
                )
            },
            "isOfficial" to filter.isOfficial,
            "createdAt" to filter.createdAt,
            "accentColor" to filter.accentColor.value.toLong()
        )
    }
    
    private fun snapshotToFilterPack(snapshot: DataSnapshot): FilterPack {
        val filtersSnapshot = snapshot.child("filters")
        val filters = filtersSnapshot.children.mapNotNull { filterChild ->
            try {
                CameraFilter(
                    id = filterChild.child("id").getValue(String::class.java) ?: "",
                    name = filterChild.child("name").getValue(String::class.java) ?: "",
                    description = filterChild.child("description").getValue(String::class.java) ?: "",
                    thumbnailUrl = filterChild.child("thumbnailUrl").getValue(String::class.java),
                    filterType = try {
                        FilterType.valueOf(
                            filterChild.child("filterType").getValue(String::class.java) ?: "NORMAL"
                        )
                    } catch (e: Exception) {
                        FilterType.NORMAL
                    },
                    shaderCode = filterChild.child("shaderCode").getValue(String::class.java) ?: "",
                    isOfficial = filterChild.child("isOfficial").getValue(Boolean::class.java) ?: false,
                    createdAt = filterChild.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()
                )
            } catch (e: Exception) {
                null
            }
        }
        
        @Suppress("UNCHECKED_CAST")
        return FilterPack(
            id = snapshot.child("id").getValue(String::class.java) 
                ?: snapshot.key ?: "",
            name = snapshot.child("name").getValue(String::class.java) ?: "",
            description = snapshot.child("description").getValue(String::class.java) ?: "",
            coverImageUrl = snapshot.child("coverImageUrl").getValue(String::class.java),
            filters = filters,
            creatorId = snapshot.child("creatorId").getValue(String::class.java) ?: "",
            creatorName = snapshot.child("creatorName").getValue(String::class.java) ?: "",
            downloadCount = snapshot.child("downloadCount").getValue(Int::class.java) ?: 0,
            rating = snapshot.child("rating").getValue(Float::class.java) ?: 5.0f,
            createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis(),
            isOfficial = snapshot.child("isOfficial").getValue(Boolean::class.java) ?: false,
            tags = (snapshot.child("tags").value as? List<String>) ?: emptyList()
        )
    }
}

data class FilterShare(
    val shareId: String,
    val packId: String,
    val fromUserId: String,
    val timestamp: Long
)
