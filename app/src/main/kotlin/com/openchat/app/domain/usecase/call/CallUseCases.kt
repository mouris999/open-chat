package com.openchat.app.domain.usecase.call

import com.openchat.app.core.Result
import com.openchat.app.domain.model.Call
import com.openchat.app.domain.repository.CallRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCallsUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    operator fun invoke(): Flow<List<Call>> {
        return callRepository.observeCalls()
    }
}

class ObserveIncomingCallsUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    operator fun invoke(): Flow<List<Call>> {
        return callRepository.observeIncomingCalls()
    }
}

class StartCallUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(userId: String, isVideo: Boolean): Result<Call> {
        return callRepository.startCall(userId, isVideo)
    }
}

class AcceptCallUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(callId: String): Result<Unit> {
        return callRepository.acceptCall(callId)
    }
}

class RejectCallUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(callId: String): Result<Unit> {
        return callRepository.rejectCall(callId)
    }
}

class EndCallUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(callId: String): Result<Unit> {
        return callRepository.endCall(callId)
    }
}

class MuteCallUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(callId: String, isMuted: Boolean): Result<Unit> {
        return callRepository.muteCall(callId, isMuted)
    }
}

class EnableSpeakerUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(callId: String, isEnabled: Boolean): Result<Unit> {
        return callRepository.enableSpeaker(callId, isEnabled)
    }
}

class SwitchCameraUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(callId: String): Result<Unit> {
        return callRepository.switchCamera(callId)
    }
}

class EnableVideoUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(callId: String, isEnabled: Boolean): Result<Unit> {
        return callRepository.enableVideo(callId, isEnabled)
    }
}
