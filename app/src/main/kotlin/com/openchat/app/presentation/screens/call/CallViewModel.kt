package com.openchat.app.presentation.screens.call

import androidx.lifecycle.viewModelScope
import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.webrtc.WebRTCManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.webrtc.MediaStream
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject

data class CallStateUi(
    val callState: CallState = CallState.IDLE,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val remoteUserId: String = "",
    val remoteUserName: String = "",
    val callStartTime: Long = 0,
    val callDuration: Int = 0,
    val errorMessage: String? = null,
    val isIncoming: Boolean = false,
    val callId: String = "",
    val remoteStream: org.webrtc.MediaStream? = null,
    val eglContext: org.webrtc.EglBase.Context? = null,
    val isSharingScreen: Boolean = false
) : UiState

sealed class CallState {
    object IDLE : CallState()
    object CALLING : CallState()
    object RINGING : CallState()
    object CONNECTED : CallState()
    object RECONNECTING : CallState()
    object ENDED : CallState()
    object ERROR : CallState()
}

sealed class CallEvent : UiEvent {
    data class AcceptCall(val callerId: String, val callId: String) : CallEvent()
    data object DeclineCall : CallEvent()
    data object EndCall : CallEvent()
    data object ToggleMute : CallEvent()
    data object ToggleSpeaker : CallEvent()
    data object SwitchCamera : CallEvent()
    data object ToggleVideo : CallEvent()
    data class InitiateCall(val userId: String, val isVideo: Boolean, val userName: String) : CallEvent()
    data class IncomingCallDetected(val callerId: String, val callId: String, val isVideo: Boolean) : CallEvent()
    data class SetupLocalView(val view: SurfaceViewRenderer) : CallEvent()
    /** Detaches the renderer on dispose without taking ownership of releasing it. */
    data object ClearLocalView : CallEvent()
    data class ToggleScreenShare(val mediaProjection: android.media.projection.MediaProjection? = null) : CallEvent()
}

sealed class CallEffect : UiEffect {
    data object CallAccepted : CallEffect()
    data object CallEnded : CallEffect()
    data object CallDeclined : CallEffect()
    data class CallFailed(val message: String) : CallEffect()
    data class IncomingCall(val callerId: String, val callId: String, val isVideo: Boolean) : CallEffect()
}

@HiltViewModel
class CallViewModel @Inject constructor(
    private val webRTCManager: WebRTCManager,
    private val firebaseAuth: FirebaseAuth
) : BaseViewModel<CallStateUi, CallEvent, CallEffect>() {

    override val _uiState = MutableStateFlow(CallStateUi())
    
    private var localView: SurfaceViewRenderer? = null
    private var callTimerJob: kotlinx.coroutines.Job? = null
    private var incomingCallListener: ChildEventListener? = null
    private var isRestoringCall = false

    init {
        setState { copy(eglContext = webRTCManager.eglBaseContext) }

        // Detect if ViewModel is being recreated during an active call
        isRestoringCall = webRTCManager.callState.value != WebRTCManager.CallState.IDLE

        if (isRestoringCall) {
            setState {
                copy(
                    // Each WebRTC state maps 1:1. Previously DISCONNECTED and ERROR
                    // both fell into an `else` that reported CONNECTED, so a
                    // ViewModel recreated mid-failure restored as a live call and
                    // the user saw live controls and a running timer for a call
                    // that was already over.
                    callState = when (webRTCManager.callState.value) {
                        WebRTCManager.CallState.CONNECTED -> CallState.CONNECTED
                        WebRTCManager.CallState.CONNECTING -> CallState.CALLING
                        WebRTCManager.CallState.RINGING -> CallState.RINGING
                        WebRTCManager.CallState.ERROR -> CallState.ERROR
                        else -> CallState.ENDED
                    },
                    callStartTime = if (webRTCManager.callStartedAtMillis > 0) webRTCManager.callStartedAtMillis else System.currentTimeMillis(),
                    remoteUserId = webRTCManager.ongoingRemoteUserId ?: "",
                    callId = webRTCManager.ongoingCallId ?: "",
                    isVideoEnabled = webRTCManager.ongoingIsVideo
                )
            }
        }

        // Listen to WebRTC state changes
        webRTCManager.callState
            .onEach { state ->
                when (state) {
                    WebRTCManager.CallState.CONNECTED -> {
                        setState {
                            copy(
                                callState = CallState.CONNECTED,
                                // callStartedAtMillis is 0 until it is stamped and is
                                // reset to 0 on teardown. Falling back to now() avoids
                                // a timer computed as (0 - now), i.e. ~1.7 billion seconds.
                                callStartTime = webRTCManager.callStartedAtMillis
                                    .takeIf { it > 0L } ?: System.currentTimeMillis()
                            )
                        }
                        startCallTimer()
                        if (!isRestoringCall) {
                            sendEffect(CallEffect.CallAccepted)
                        }
                        isRestoringCall = false
                    }
                    WebRTCManager.CallState.CONNECTING -> {
                        setState { copy(callState = CallState.CALLING) }
                    }
                    WebRTCManager.CallState.RINGING -> {
                        setState { copy(callState = CallState.RINGING) }
                    }
                    WebRTCManager.CallState.DISCONNECTED -> {
                        endCall()
                    }
                    WebRTCManager.CallState.ERROR -> {
                        setState { copy(callState = CallState.ERROR, errorMessage = "Call error occurred") }
                        sendEffect(CallEffect.CallFailed("Call error occurred"))
                    }
                    WebRTCManager.CallState.IDLE -> {
                        // Only react to a call that we still believe is live. endCall()
                        // already marks the state ENDED before tearing down the manager,
                        // so its own IDLE emission is filtered out here rather than
                        // re-entering endCall() and emitting a duplicate effect.
                        if (_uiState.value.callState == CallState.CONNECTED) {
                            endCall()
                        }
                    }
                }
            }
            .launchIn(viewModelScope)

        // Listen to remote stream from WebRTCManager
        webRTCManager.remoteStream
            .onEach { stream ->
                setState { copy(remoteStream = stream) }
            }
            .launchIn(viewModelScope)

        // Listen for incoming calls
        listenForIncomingCalls()
    }

    override fun onEvent(event: CallEvent) {
        when (event) {
            is CallEvent.InitiateCall -> initiateCall(event.userId, event.isVideo, event.userName)
            is CallEvent.IncomingCallDetected -> setState {
                copy(
                    callState = CallState.RINGING,
                    callId = event.callId,
                    remoteUserId = event.callerId,
                    isVideoEnabled = event.isVideo,
                    isIncoming = true
                )
            }
            is CallEvent.AcceptCall -> acceptCall(event.callerId, event.callId)
            CallEvent.DeclineCall -> declineCall()
            CallEvent.EndCall -> endCall()
            CallEvent.ToggleMute -> toggleMute()
            CallEvent.ToggleSpeaker -> toggleSpeaker()
            CallEvent.SwitchCamera -> switchCamera()
            CallEvent.ToggleVideo -> toggleVideo()
            is CallEvent.ToggleScreenShare -> toggleScreenShare(event.mediaProjection)
            // null is passed on dispose so the field does not outlive the view.
            // null is passed on dispose so the field does not outlive the view.
            is CallEvent.SetupLocalView -> localView = event.view
            CallEvent.ClearLocalView -> localView = null
        }
    }

    private fun initiateCall(userId: String, isVideo: Boolean, userName: String) {
        setState {
            copy(
                callState = CallState.CALLING,
                isVideoEnabled = isVideo,
                remoteUserId = userId,
                remoteUserName = userName,
                isIncoming = false
            )
        }
        
        val callId = webRTCManager.startCall(userId, isVideo, localView)
        if (callId != null) {
            setState { copy(callId = callId) }
        } else {
            setState { 
                copy(
                    callState = CallState.ERROR,
                    errorMessage = "Failed to start call"
                )
            }
            sendEffect(CallEffect.CallFailed("Failed to start call"))
        }
    }

    private fun acceptCall(callerId: String, callId: String) {
        setState { 
            copy(
                callState = CallState.CALLING,
                isIncoming = true,
                remoteUserId = callerId,
                callId = callId
            )
        }
        
        // Accept the incoming call via WebRTC
        webRTCManager.acceptIncomingCall(callId, callerId, _uiState.value.isVideoEnabled, localView)
    }

    private fun declineCall() {
        // Same reasoning as endCall(): mark the call terminal first so WebRTCManager's
        // synchronous IDLE emission cannot re-enter endCall() and emit a second
        // (conflicting) terminal effect on top of CallDeclined.
        callTimerJob?.cancel()
        setState { copy(callState = CallState.ENDED) }
        webRTCManager.endCall()
        sendEffect(CallEffect.CallDeclined)
    }

    /**
     * Tears the call down exactly once.
     *
     * Order matters: [webRTCManager] emits IDLE synchronously from [WebRTCManager.endCall],
     * and the IDLE collector re-enters this method. Setting the terminal state FIRST
     * makes that guard fail, so only one CallEnded effect is emitted. Previously two
     * were sent and the NavHost popped the back stack twice - throwing the user two
     * screens out of the call.
     */
    private fun endCall() {
        if (_uiState.value.callState == CallState.ENDED) return
        callTimerJob?.cancel()
        setState { copy(callState = CallState.ENDED) }
        webRTCManager.endCall()
        sendEffect(CallEffect.CallEnded)
    }

    private fun toggleMute() {
        val newMutedState = !_uiState.value.isMuted
        webRTCManager.toggleMute(newMutedState)
        setState { copy(isMuted = newMutedState) }
    }

    private fun toggleSpeaker() {
        val newState = !_uiState.value.isSpeakerOn
        webRTCManager.setSpeakerphoneOn(newState)
        setState { copy(isSpeakerOn = newState) }
    }

    private fun switchCamera() {
        webRTCManager.switchCamera()
    }

    private fun toggleVideo() {
        val newVideoState = !_uiState.value.isVideoEnabled
        webRTCManager.toggleVideo(newVideoState)
        setState { copy(isVideoEnabled = newVideoState) }
    }

    private fun toggleScreenShare(mediaProjection: android.media.projection.MediaProjection?) {
        if (webRTCManager.isScreenSharing()) {
            webRTCManager.stopScreenSharing()
            setState { copy(isSharingScreen = false) }
        } else if (mediaProjection != null) {
            webRTCManager.startScreenSharing(mediaProjection)
            setState { copy(isSharingScreen = true) }
        }
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val duration = ((System.currentTimeMillis() - _uiState.value.callStartTime) / 1000).toInt()
                setState { copy(callDuration = duration) }
            }
        }
    }

    private fun listenForIncomingCalls() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        
        incomingCallListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val status = snapshot.child("status").getValue(String::class.java)
                if (status != "calling") return
                
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                val now = System.currentTimeMillis()
                if (now - timestamp > 60_000L) {
                    android.util.Log.d("CallViewModel", "Ignoring stale incoming call (age: ${(now - timestamp) / 1000}s)")
                    return
                }
                
                val callerId = snapshot.child("callerId").getValue(String::class.java) ?: return
                val callId = snapshot.key ?: return
                val isVideo = snapshot.child("isVideo").getValue(Boolean::class.java) ?: false
                
                if (_uiState.value.callState == CallState.IDLE) {
                    setState {
                        copy(
                            callState = CallState.RINGING,
                            isIncoming = true,
                            isVideoEnabled = isVideo,
                            remoteUserId = callerId,
                            callId = callId
                        )
                    }
                    sendEffect(CallEffect.IncomingCall(callerId, callId, isVideo))
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                android.util.Log.d("CallViewModel", "onChildChanged: ${snapshot.key}")
            }
            override fun onChildRemoved(snapshot: DataSnapshot) {
                android.util.Log.d("CallViewModel", "onChildRemoved: ${snapshot.key}")
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                android.util.Log.d("CallViewModel", "onChildMoved: ${snapshot.key}, previousChildName: $previousChildName")
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("CallViewModel", "Incoming call listener cancelled: ${error.message}")
            }
        }
        
        incomingCallListener?.let { listener ->
            FirebaseDatabase.getInstance()
                .reference
                .child("/webrtc_signaling")
                .child(currentUserId)
                .addChildEventListener(listener)
        }
    }

    override fun onCleared() {
        super.onCleared()
        callTimerJob?.cancel()
        // The ViewModel outlives the composable, so a renderer retained here would
        // leak its native surface. The screen also disposes it via
        // DisposableEffect, so this is the backstop for the case where the ViewModel
        // is cleared without the view ever being composed.
        localView?.release()
        localView = null
        incomingCallListener?.let { listener ->
            val currentUserId = firebaseAuth.currentUser?.uid
            if (currentUserId != null) {
                FirebaseDatabase.getInstance()
                    .reference
                    .child("/webrtc_signaling")
                    .child(currentUserId)
                    .removeEventListener(listener)
            }
        }
    }
}

