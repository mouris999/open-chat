package com.openchat.app.domain.repository

import com.openchat.app.core.Result
import com.openchat.app.domain.model.Call
import kotlinx.coroutines.flow.Flow

interface CallRepository {
    fun observeCalls(): Flow<List<Call>>
    fun observeIncomingCalls(): Flow<List<Call>>
    suspend fun startCall(userId: String, isVideo: Boolean): Result<Call>
    suspend fun acceptCall(callId: String): Result<Unit>
    suspend fun rejectCall(callId: String): Result<Unit>
    suspend fun endCall(callId: String): Result<Unit>
    suspend fun muteCall(callId: String, isMuted: Boolean): Result<Unit>
    suspend fun enableSpeaker(callId: String, isEnabled: Boolean): Result<Unit>
    suspend fun switchCamera(callId: String): Result<Unit>
    suspend fun enableVideo(callId: String, isEnabled: Boolean): Result<Unit>
    suspend fun deleteCall(callId: String): Result<Unit>
}
