package com.openchat.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openchat.app.data.local.entity.CallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {
    @Query("SELECT * FROM calls ORDER BY startTime DESC")
    fun observeAllCalls(): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE callerId = :userId OR receiverId = :userId ORDER BY startTime DESC")
    fun observeCallsWithUser(userId: String): Flow<List<CallEntity>>

    @Query("SELECT * FROM calls WHERE id = :callId")
    suspend fun getCallById(callId: String): CallEntity?

    @Query("SELECT * FROM calls WHERE status = 'RINGING' AND callerId != :currentUserId")
    fun observeIncomingCalls(currentUserId: String): Flow<List<CallEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallEntity)

    @Query("UPDATE calls SET status = :status, endTime = :endTime, duration = :duration WHERE id = :callId")
    suspend fun updateCallStatus(callId: String, status: com.openchat.app.data.local.entity.CallStatus, endTime: Long? = null, duration: Int? = null)

    @Delete
    suspend fun deleteCall(call: CallEntity)

    @Query("DELETE FROM calls WHERE id = :callId")
    suspend fun deleteCallById(callId: String)

    @Query("DELETE FROM calls WHERE startTime < :beforeTime")
    suspend fun deleteOldCalls(beforeTime: Long)
}
