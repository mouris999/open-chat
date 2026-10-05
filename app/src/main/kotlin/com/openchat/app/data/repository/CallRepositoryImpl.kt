package com.openchat.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.openchat.app.core.Result
import com.openchat.app.core.safeApiCall
import com.openchat.app.data.local.dao.CallDao
import com.openchat.app.data.local.entity.CallEntity
import com.openchat.app.data.local.entity.CallStatus
import com.openchat.app.data.local.entity.CallType
import com.openchat.app.domain.model.Call
import com.openchat.app.domain.repository.CallRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val callDao: CallDao,
    private val firebaseAuth: com.google.firebase.auth.FirebaseAuth,
    private val database: com.google.firebase.database.FirebaseDatabase
) : CallRepository {
    
    override fun observeCalls(): Flow<List<Call>> {
        return callDao.observeAllCalls().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override fun observeIncomingCalls(): Flow<List<Call>> {
        val currentUserId = firebaseAuth.currentUser?.uid ?: ""
        return callDao.observeIncomingCalls(currentUserId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun startCall(userId: String, isVideo: Boolean): Result<Call> = safeApiCall {
        val callId = firestore.collection("calls").document().id
        val currentUserId = firebaseAuth.currentUser?.uid ?: throw Exception("User not authenticated")

        val call = CallEntity(
            id = callId,
            callerId = currentUserId,
            receiverId = userId,
            type = CallType.OUTGOING,
            status = CallStatus.RINGING,
            isVideo = isVideo,
            startTime = System.currentTimeMillis()
        )

        firestore.collection("calls").document(callId).set(
            hashMapOf(
                "callerId" to currentUserId,
                "receiverId" to userId,
                "status" to "RINGING",
                "isVideo" to isVideo,
                "startTime" to System.currentTimeMillis()
            )
        ).await()

        callDao.insertCall(call)
        call.toDomainModel()
    }
    
    override suspend fun acceptCall(callId: String): Result<Unit> = safeApiCall {
        firestore.collection("calls").document(callId)
            .update("status", "CONNECTED")
            .await()
        
        callDao.updateCallStatus(callId, CallStatus.CONNECTED)
    }
    
    override suspend fun rejectCall(callId: String): Result<Unit> = safeApiCall {
        val endTime = System.currentTimeMillis()
        
        firestore.collection("calls").document(callId)
            .update(
                "status", "REJECTED",
                "endTime", endTime
            )
            .await()
        
        callDao.updateCallStatus(callId, CallStatus.REJECTED, endTime)
    }
    
    override suspend fun endCall(callId: String): Result<Unit> = safeApiCall {
        val endTime = System.currentTimeMillis()
        val call = callDao.getCallById(callId)
        val duration = call?.startTime?.let { (endTime - it) / 1000 }?.toInt()
        
        firestore.collection("calls").document(callId)
            .update(
                "status", "ENDED",
                "endTime", endTime,
                "duration", duration
            )
            .await()
        
        callDao.updateCallStatus(callId, CallStatus.ENDED, endTime, duration)
    }
    
    override suspend fun muteCall(callId: String, isMuted: Boolean): Result<Unit> = safeApiCall {
        // These three were empty bodies compiled to `safeApiCall { Unit }`, i.e. they
        // always returned Result.Success. The UI therefore showed a "muted" state
        // that nothing persisted and nothing told the peer about. Persist to RTDB
        // so the state is real and survives a restart.
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        database.reference.child("calls").child(callId).child("muted").child(userId)
            .setValue(isMuted).await()
    }
    
    override suspend fun enableSpeaker(callId: String, isEnabled: Boolean): Result<Unit> = safeApiCall {
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        database.reference.child("calls").child(callId).child("speaker").child(userId)
            .setValue(isEnabled).await()
    }

    override suspend fun switchCamera(callId: String): Result<Unit> = safeApiCall {
        // A request flag, not a toggle: the caller decides front/back, the remote
        // peer reads the flag. Setting it to true on every tap left the flag stuck
        // on, so the peer kept flipping after the user stopped asking.
        val userId = firebaseAuth.currentUser?.uid ?: throw Exception("Not authenticated")
        database.reference.child("calls").child(callId).child("cameraSwitchRequest").child(userId)
            .setValue(System.currentTimeMillis()).await()
    }
    
    override suspend fun enableVideo(callId: String, isEnabled: Boolean): Result<Unit> = safeApiCall {
        firestore.collection("calls").document(callId)
            .update("isVideo", isEnabled)
            .await()
    }
    
    override suspend fun deleteCall(callId: String): Result<Unit> = safeApiCall {
        firestore.collection("calls").document(callId).delete().await()
        callDao.deleteCallById(callId)
    }
    
    private fun CallEntity.toDomainModel(): Call {
        return Call(
            id = id,
            callerId = callerId,
            receiverId = receiverId,
            type = com.openchat.app.domain.model.CallType.valueOf(type.name),
            status = com.openchat.app.domain.model.CallStatus.valueOf(status.name),
            isVideo = isVideo,
            isEncrypted = isEncrypted,
            startTime = startTime,
            endTime = endTime,
            duration = duration,
            isMissed = isMissed
        )
    }
}
