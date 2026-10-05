package com.openchat.app.webrtc

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.openchat.app.service.call.CallService
import com.openchat.app.webrtc.FcmSender
import com.openchat.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth
) {
    companion object {
        private const val TAG = "WebRTCManager"
        private const val STUN_SERVER = "stun:stun.l.google.com:19302"
        private const val TURN_SERVER = "turn:openchat-turn.gl.meetcat.net:3478"
        private const val SIGNALING_PATH = "/webrtc_signaling"
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var localVideoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var eglBase: EglBase? = null

    private var currentCallId: String? = null
    val ongoingCallId: String? get() = currentCallId

    private var isInitiator = false
    private var remoteUserId: String? = null  // Store the other party's ID
    val ongoingRemoteUserId: String? get() = remoteUserId

    var callStartedAtMillis: Long = 0L
        private set

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState

    private val _remoteStream = MutableStateFlow<MediaStream?>(null)
    val remoteStream: StateFlow<MediaStream?> = _remoteStream

    private var signalingListener: ValueEventListener? = null
    private val database = FirebaseDatabase.getInstance()
    private var isReleased = false
    private var ringbackPlayer: MediaPlayer? = null

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val powerManager: PowerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private var callWakeLock: PowerManager.WakeLock? = null

    val eglBaseContext: EglBase.Context?
        get() = eglBase?.eglBaseContext

    enum class CallState {
        IDLE, CONNECTING, RINGING, CONNECTED, DISCONNECTED, ERROR
    }

    init {
        initializePeerConnectionFactory()
    }

    fun cleanupStaleSignalingData(onComplete: () -> Unit = {}) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        database.reference
            .child(SIGNALING_PATH)
            .child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val now = System.currentTimeMillis()
                    for (child in snapshot.children) {
                        val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        if (now - timestamp > 60_000L) {
                            Log.d(TAG, "Cleaning up stale signaling data: ${child.key}")
                            child.ref.removeValue()
                        }
                    }
                    onComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to cleanup stale signaling: ${error.message}")
                    onComplete()
                }
            })
    }

    private fun initializePeerConnectionFactory() {
        try {
            eglBase = try {
                EglBase.create()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create EGL base with default config, trying alternative", e)
                null
            }

            if (eglBase == null) {
                // Create PeerConnectionFactory without hardware acceleration
                val options = PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
                PeerConnectionFactory.initialize(options)

                peerConnectionFactory = PeerConnectionFactory.builder()
                    .setVideoEncoderFactory(DefaultVideoEncoderFactory(null, true, true))
                    .setVideoDecoderFactory(DefaultVideoDecoderFactory(null))
                    .createPeerConnectionFactory()

                Log.d(TAG, "PeerConnectionFactory initialized (software rendering)")
                return
            }

            val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)

            val eglCtx = eglBase?.eglBaseContext
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglCtx, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglCtx))
                .createPeerConnectionFactory()

            Log.d(TAG, "PeerConnectionFactory initialized (hardware accelerated)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC", e)
        }
    }

    private fun resetMediaState() {
        localVideoTrack = null
        localAudioTrack = null
        localVideoCapturer = null
        surfaceTextureHelper = null
        peerConnection = null
        _remoteStream.value = null
        ringbackPlayer?.release()
        ringbackPlayer = null
        signalingListener = null
        iceCandidateListener = null
    }

    fun startCall(targetUserId: String, isVideo: Boolean, localView: SurfaceViewRenderer? = null): String? {
        resetMediaState()
        isEnded = false
        isReleased = false
        val callId = UUID.randomUUID().toString()
        currentCallId = callId
        remoteUserId = targetUserId
        isInitiator = true
        currentIsVideo = isVideo
        _callState.value = CallState.CONNECTING

        val currentUserId = firebaseAuth.currentUser?.uid ?: return null

        if (!hasRequiredPermissions(isVideo)) {
            Log.e(TAG, "Cannot start call: missing required permissions")
            _callState.value = CallState.ERROR
            return null
        }

        try {
            setupAudioForCall()
            createPeerConnection()
            setupLocalMedia(isVideo, localView)

            // Create offer
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideo) "true" else "false"))
            }

                        peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) {
                    description?.let { sdp ->
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {
                                Log.d(TAG, "Local description created successfully")
                            }
                            override fun onSetSuccess() {
                                Log.d(TAG, "Local description set, sending offer")
                                sendSignalingMessage(targetUserId, callId, "offer", sdp.description) { success ->
                                    if (success) {
                                        answerListenerHasSeenData = false
                                        listenForAnswer(targetUserId, callId)
                                        startListeningForIceCandidates(callId)
                                        startRingbackTone()
                                        // Send FCM push to wake the receiver
                                        val callerName = firebaseAuth.currentUser?.displayName ?: "Unknown"
                                        FcmSender.sendCallNotification(targetUserId, callId, callerName, currentUserId, isVideo)
                                    } else {
                                        Log.e(TAG, "Failed to send offer, ending call")
                                        _callState.value = CallState.ERROR
                                    }
                                }
                            }
                            override fun onCreateFailure(p0: String?) {
                                Log.w(TAG, "Local description creation failed: $p0")
                            }
                            override fun onSetFailure(p0: String?) {
                                Log.e(TAG, "Failed to set local description: $p0")
                                _callState.value = CallState.ERROR
                            }
                        }, sdp)
                    }
                }
                override fun onSetSuccess() {
                    Log.d(TAG, "Offer set successfully")
                }
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Failed to create offer: $error")
                    _callState.value = CallState.ERROR
                }
                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Set offer failure: $error")
                    _callState.value = CallState.ERROR
                }
            }, constraints)

            return callId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call", e)
            _callState.value = CallState.ERROR
            return null
        }
    }

    fun acceptIncomingCall(callId: String, callerId: String, isVideo: Boolean, localView: SurfaceViewRenderer? = null) {
        resetMediaState()
        isEnded = false
        isReleased = false
        currentCallId = callId
        isInitiator = false
        currentIsVideo = isVideo
        _callState.value = CallState.CONNECTING

        if (!hasRequiredPermissions(isVideo)) {
            Log.e(TAG, "Cannot accept call: missing required permissions")
            _callState.value = CallState.ERROR
            return
        }

        try {
            setupAudioForCall()
            createPeerConnection()
            setupLocalMedia(isVideo, localView)
            listenForOffer(callerId, callId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to accept call", e)
            _callState.value = CallState.ERROR
        }
    }

    private fun hasRequiredPermissions(isVideo: Boolean): Boolean {
        val audioGranted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!audioGranted) {
            Log.w(TAG, "Missing RECORD_AUDIO permission")
            return false
        }
        if (isVideo) {
            val cameraGranted = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (!cameraGranted) {
                Log.w(TAG, "Missing CAMERA permission")
                return false
            }
        }
        return true
    }

    private fun createPeerConnection() {
        if (peerConnectionFactory == null) {
            isReleased = false
            initializePeerConnectionFactory()
        }
        val iceServers = listOf(
            PeerConnection.IceServer.builder(STUN_SERVER).createIceServer(),
            PeerConnection.IceServer.builder(TURN_SERVER)
                .setUsername(BuildConfig.TURN_USERNAME)
                .setPassword(BuildConfig.TURN_CREDENTIAL)
                .createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
                    Log.d(TAG, "Signaling state: $newState")
                }

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "ICE connection state: $newState")
                    when (newState) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            stopRingbackTone()
                            setupAudioForCall()
                            if (callStartedAtMillis == 0L) callStartedAtMillis = System.currentTimeMillis()
                            _callState.value = CallState.CONNECTED
                            // Start foreground service to keep call alive in background
                            try {
                                val userName = firebaseAuth.currentUser?.displayName ?: remoteUserId ?: "Call"
                                CallService.start(context, currentCallId ?: "", currentIsVideo, userName)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to start foreground service", e)
                            }
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            _callState.value = CallState.DISCONNECTED
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            _callState.value = CallState.ERROR
                        }
                        else -> Log.d(TAG, "Unhandled ICE connection state: $newState")
                    }
                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {
                    Log.d(TAG, "ICE connection receiving: $p0")
                }
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                    Log.d(TAG, "ICE gathering state: $p0")
                }
                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let { ice ->
                        sendIceCandidate(ice)
                    }
                }

                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
                    Log.d(TAG, "ICE candidates removed")
                }

                override fun onAddStream(stream: MediaStream?) {
                    Log.d(TAG, "Remote stream added")
                    _remoteStream.value = stream
                }

                override fun onRemoveStream(p0: MediaStream?) {
                    Log.d(TAG, "Remote stream removed")
                    _remoteStream.value = null
                }

                override fun onDataChannel(p0: DataChannel?) {
                    Log.d(TAG, "Data channel created")
                }
                override fun onRenegotiationNeeded() {
                    Log.d(TAG, "Renegotiation needed")
                }
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    Log.d(TAG, "Track added: ${receiver?.track()?.id()}")
                }
            }
        )
    }

    private fun setupLocalMedia(isVideo: Boolean, localView: SurfaceViewRenderer?) {
        // Audio
        val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio_0", audioSource)
        localAudioTrack?.setEnabled(true)

        val audioSender = peerConnection?.addTrack(localAudioTrack, listOf("stream_0"))

        // Video (if video call)
        if (isVideo) {
            localView?.let { view ->
                eglBase?.let { egl ->
                    try {
                        view.init(egl.eglBaseContext, null)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to init local view with shared context, falling back", e)
                        view.init(null, null)
                    }
                    view.setMirror(true)
                    view.setEnableHardwareScaler(true)
                }
            }

            val eglCtx2 = eglBase?.eglBaseContext
            if (eglCtx2 != null) {
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglCtx2)
            }
            localVideoCapturer = createCameraCapturer()
            val capturer = localVideoCapturer
            if (capturer != null && surfaceTextureHelper != null) {
                val videoSource = peerConnectionFactory?.createVideoSource(capturer.isScreencast) ?: return
                localVideoCapturer?.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
                localVideoCapturer?.startCapture(1280, 720, 30)

                localVideoTrack = peerConnectionFactory?.createVideoTrack("video_0", videoSource)
                localVideoTrack?.setEnabled(true)
                localView?.let { localVideoTrack?.addSink(it) }

                peerConnection?.addTrack(localVideoTrack, listOf("stream_0"))
            }
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }

        return if (deviceNames.isNotEmpty()) {
            enumerator.createCapturer(deviceNames[0], null)
        } else null
    }

    private var currentIsVideo = false
    val ongoingIsVideo: Boolean get() = currentIsVideo

    private fun sendSignalingMessage(userId: String, callId: String, type: String, sdp: String, onComplete: ((Boolean) -> Unit)? = null) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return

        val updateMap = HashMap<String, Any>()
        updateMap["status"] = if (type == "offer") "calling" else "connected"
        updateMap["timestamp"] = ServerValue.TIMESTAMP

        if (type == "offer") {
            updateMap["callerId"] = currentUserId
            updateMap["receiverId"] = userId
            updateMap["isVideo"] = currentIsVideo
            updateMap["offer"] = hashMapOf(
                "type" to type,
                "sdp" to sdp
            )
        } else if (type == "answer") {
            updateMap["isVideo"] = currentIsVideo
            updateMap["answer"] = hashMapOf(
                "type" to type,
                "sdp" to sdp
            )
        }

        database.reference
            .child(SIGNALING_PATH)
            .child(userId)
            .child(callId)
            .updateChildren(updateMap) { error, _ ->
                if (error != null) {
                    Log.e(TAG, "Failed to send signaling ($type): ${error.message}")
                } else {
                    Log.d(TAG, "Signaling ($type) written successfully")
                }
                onComplete?.invoke(error == null)
            }
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        val callId = currentCallId ?: return

        val iceData = hashMapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "candidate" to candidate.sdp,
            "senderId" to currentUserId
        )

        database.reference
            .child("ice_candidates")
            .child(callId)
            .push()
            .setValue(iceData)
    }

    private fun listenForAnswer(targetUserId: String, callId: String) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        removeSignalingListener()
        signalingCallId = callId
        listeningUserId = targetUserId
        answerListenerHasSeenData = false

        signalingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    if (answerListenerHasSeenData) {
                        Log.d(TAG, "Signaling data removed (call ended by remote)")
                        handleRemoteEndCall()
                    } else {
                        Log.d(TAG, "Signaling data not yet available, waiting...")
                    }
                    return
                }
                answerListenerHasSeenData = true

                val status = snapshot.child("status").getValue(String::class.java)
                if (status == "ended") {
                    Log.d(TAG, "Remote ended call")
                    handleRemoteEndCall()
                    return
                }
                if (status != "connected") return

                if (answerProcessed) return

                val answerMap = snapshot.child("answer").value as? Map<*, *>
                val type = answerMap?.get("type") as? String
                val sdp = answerMap?.get("sdp") as? String
                if (type != null && sdp != null) {
                    answerProcessed = true
                    val sessionDescription = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {
                            Log.d(TAG, "Remote description created for answer")
                        }
                        override fun onSetSuccess() {
                            Log.d(TAG, "Remote description set (answer)")
                        }
                        override fun onCreateFailure(p0: String?) {
                            Log.w(TAG, "Remote description creation failed: $p0")
                        }
                        override fun onSetFailure(p0: String?) {
                            Log.e(TAG, "Failed to set remote description: $p0")
                            _callState.value = CallState.ERROR
                        }
                    }, sessionDescription)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Answer listener cancelled: ${error.message}")
            }
        }

        signalingListener?.let { listener ->
            database.reference
                .child(SIGNALING_PATH)
                .child(targetUserId)
                .child(callId)
                .addValueEventListener(listener)
        }
    }

    private fun listenForOffer(callerId: String, callId: String) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        removeSignalingListener()
        remoteUserId = callerId
        signalingCallId = callId
        listeningUserId = currentUserId
        offerListenerHasSeenData = false

        signalingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    if (offerListenerHasSeenData) {
                        Log.d(TAG, "Offer signaling data removed (call ended by remote)")
                        handleRemoteEndCall()
                    } else {
                        Log.d(TAG, "Offer signaling data not yet available, waiting...")
                    }
                    return
                }
                offerListenerHasSeenData = true

                val status = snapshot.child("status").getValue(String::class.java)
                if (status == "ended") {
                    Log.d(TAG, "Remote ended call while waiting for offer")
                    handleRemoteEndCall()
                    return
                }

                val receiverId = snapshot.child("receiverId").getValue(String::class.java)
                if (receiverId != currentUserId) return

                if (offerProcessed) return

                val offerMap = snapshot.child("offer").value as? Map<*, *>
                val offerType = offerMap?.get("type") as? String
                val sdp = offerMap?.get("sdp") as? String
                if (offerType != null && sdp != null) {
                    offerProcessed = true
                    val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
                    peerConnection?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {
                            Log.d(TAG, "Remote description created for offer")
                        }
                        override fun onSetSuccess() {
                            Log.d(TAG, "Remote description set (offer)")
                            createAnswer()
                            startListeningForIceCandidates(callId)
                        }
                        override fun onCreateFailure(p0: String?) {
                            Log.w(TAG, "Remote description creation failed: $p0")
                        }
                        override fun onSetFailure(p0: String?) {
                            Log.e(TAG, "Failed to set remote description: $p0")
                            _callState.value = CallState.ERROR
                        }
                    }, sessionDescription)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Offer listener cancelled: ${error.message}")
            }
        }

        signalingListener?.let { listener ->
            database.reference
                .child(SIGNALING_PATH)
                .child(currentUserId)
                .child(callId)
                .addValueEventListener(listener)
        }
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

                            peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription?) {
                description?.let { sdp ->
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {
                            Log.d(TAG, "Local answer description created")
                        }
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local answer set, sending answer")
                            val currentUserId = firebaseAuth.currentUser?.uid ?: return
                            val callId = currentCallId ?: return
                            sendSignalingMessage(currentUserId, callId, "answer", sdp.description)
                        }
                        override fun onCreateFailure(p0: String?) {
                            Log.w(TAG, "Local answer creation failed: $p0")
                        }
                        override fun onSetFailure(p0: String?) {
                            Log.e(TAG, "Failed to set local description for answer: $p0")
                            _callState.value = CallState.ERROR
                        }
                    }, sdp)
                }
            }
            override fun onSetSuccess() {
                Log.d(TAG, "Answer set successfully")
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create answer: $error")
                _callState.value = CallState.ERROR
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Set answer failure: $error")
                _callState.value = CallState.ERROR
            }
        }, constraints)
    }

    @Volatile private var isEnded = false
    private var signalingCallId: String? = null
    private var listeningUserId: String? = null
    private var iceCandidateListener: ValueEventListener? = null
    private var answerListenerHasSeenData = false
    private var offerListenerHasSeenData = false
    private var answerProcessed = false
    private var offerProcessed = false
    private val processedIceCandidates = java.util.Collections.synchronizedSet(
        java.util.HashSet<String>()
    )

    // Screen sharing
    private var screenCapturer: ScreenCapturer? = null
    private var screenVideoSource: VideoSource? = null
    private var screenVideoTrack: VideoTrack? = null
    private var isSharingScreen = false
    private var screenSender: RtpSender? = null
    private var originalVideoSender: RtpSender? = null

    private fun startListeningForIceCandidates(callId: String) {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        removeIceCandidateListener()

        iceCandidateListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                
                for (child in snapshot.children) {
                    val senderId = child.child("senderId").getValue(String::class.java)
                    if (senderId == null || senderId == currentUserId) continue
                    
                    val sdpMid = child.child("sdpMid").getValue(String::class.java)
                    val sdpMLineIndex = child.child("sdpMLineIndex").getValue(Int::class.java) ?: 0
                    val candidate = child.child("candidate").getValue(String::class.java)
                    
                    if (sdpMid != null && candidate != null) {
                        val key = "${child.key}:$sdpMid:$sdpMLineIndex"
                        if (!processedIceCandidates.add(key)) continue
                        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
                        peerConnection?.addIceCandidate(iceCandidate)
                        Log.d(TAG, "Added ICE candidate from $senderId")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "ICE candidate listener cancelled: ${error.message}")
            }
        }

        iceCandidateListener?.let { listener ->
            database.reference
                .child("ice_candidates")
                .child(callId)
                .addValueEventListener(listener)
        }
    }

    private fun removeIceCandidateListener() {
        val listener = iceCandidateListener
        val callId = signalingCallId
        if (listener != null && callId != null) {
            database.reference
                .child("ice_candidates")
                .child(callId)
                .removeEventListener(listener)
        }
        iceCandidateListener = null
    }

    private fun removeSignalingListener() {
        val listener = signalingListener
        val callId = signalingCallId
        val userId = listeningUserId
        if (listener != null && callId != null && userId != null) {
            database.reference
                .child(SIGNALING_PATH)
                .child(userId)
                .child(callId)
                .removeEventListener(listener)
        }
        signalingListener = null
    }
    
    fun endCall() {
        if (isEnded) {
            Log.d(TAG, "Call already ended, skipping")
            return
        }
        isEnded = true
        processedIceCandidates.clear()

        Log.d(TAG, "Ending call")

        removeSignalingListener()
        removeIceCandidateListener()
        stopRingbackTone()

        // Send "ended" status to remote peer before cleanup
        sendEndCallSignal()

        // Restore audio mode
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build()
                )
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting audio", e)
        }

        // Release wake lock to allow screen to turn back on after call
        try {
            callWakeLock?.apply {
                if (isHeld) {
                    release()
                }
            }
            callWakeLock = null
            Log.d(TAG, "Call wake lock released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }

        // Send missed call notification if call was never answered (initiator only)
        val wasConnected = _callState.value == CallState.CONNECTED
        val rid = remoteUserId
        if (isInitiator && !wasConnected && rid != null) {
            try {
                val callerName = firebaseAuth.currentUser?.displayName ?: "Unknown"
                FcmSender.sendMissedCallNotification(
                    receiverUid = rid,
                    callerName = callerName,
                    callerId = firebaseAuth.currentUser?.uid ?: "",
                    isVideo = currentIsVideo
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error sending missed call notification", e)
            }
        }

        // Stop local media safely
        try {
            localVideoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture", e)
        }
        
        try {
            localVideoCapturer?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing capturer", e)
        }
        localVideoCapturer = null
        
        try {
            localVideoTrack?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing video track", e)
        }
        localVideoTrack = null
        
        try {
            localAudioTrack?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing audio track", e)
        }
        localAudioTrack = null
        
        try {
            surfaceTextureHelper?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing surface helper", e)
        }
        surfaceTextureHelper = null

        // Close peer connection
        try {
            peerConnection?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing peer connection", e)
        }
        peerConnection = null

        // Remove signaling listener from the correct path
        signalingListener?.let { listener ->
            try {
                val uid = listeningUserId
                val cId = currentCallId
                if (uid != null && cId != null) {
                    database.reference
                        .child(SIGNALING_PATH)
                        .child(uid)
                        .child(cId)
                        .removeEventListener(listener)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing listener", e)
            }
        }

        // Remove ICE candidate listener
        iceCandidateListener?.let { listener ->
            try {
                val callId = currentCallId
                if (callId != null) {
                    database.reference
                        .child("ice_candidates")
                        .child(callId)
                        .removeEventListener(listener)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing ICE listener", e)
            }
        }

        // Clean up signaling data from Firebase
        signalingCallId?.let { sigCallId ->
            val uid = listeningUserId
            if (uid != null) {
                try {
                    database.reference
                        .child(SIGNALING_PATH)
                        .child(uid)
                        .child(sigCallId)
                        .removeValue()
                    database.reference
                        .child("ice_candidates")
                        .child(sigCallId)
                        .removeValue()
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning up signaling", e)
                }
            }
        }

        // Clear state
        currentCallId = null
        remoteUserId = null
        listeningUserId = null
        answerListenerHasSeenData = false
        offerListenerHasSeenData = false
        answerProcessed = false
        offerProcessed = false
        signalingListener = null
        iceCandidateListener = null
        _remoteStream.value = null
        // Stop foreground service when call ends
        try {
            CallService.stop(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping call service", e)
        }
        callStartedAtMillis = 0L
        _callState.value = CallState.IDLE
    }

    private fun sendEndCallSignal() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: return
        val callId = currentCallId ?: return
        val signalingUid = listeningUserId ?: remoteUserId ?: return
        try {
            val updateMap = HashMap<String, Any>()
            updateMap["callerId"] = currentUserId
            updateMap["status"] = "ended"
            updateMap["timestamp"] = ServerValue.TIMESTAMP
            database.reference
                .child(SIGNALING_PATH)
                .child(signalingUid)
                .child(callId)
                .updateChildren(updateMap)
            Log.d(TAG, "Sent end call signal to signaling node of $signalingUid")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send end call signal", e)
        }
    }

    private fun handleRemoteEndCall() {
        Log.d(TAG, "Handling remote end call")
        handler.post {
            endCall()
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun toggleMute(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun setSpeakerphoneOn(on: Boolean) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = on
            Log.d(TAG, "Speakerphone ${if (on) "on" else "off"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set speakerphone", e)
        }
    }

    private fun startRingbackTone() {
        try {
            val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringbackPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(context, ringtoneUri)
                isLooping = true
                prepare()
                start()
            }
            Log.d(TAG, "Ringback tone started")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play ringback tone", e)
        }
    }

    private fun stopRingbackTone() {
        try {
            ringbackPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            ringbackPlayer = null
            Log.d(TAG, "Ringback tone stopped")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping ringback tone", e)
        }
    }

    private fun setupAudioForCall() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN)
            }

            audioManager.isSpeakerphoneOn = false

            // Acquire proximity wake lock so the screen turns back on when call ends
            @Suppress("DEPRECATION")
            callWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "OpenChat:CallWakeLock"
            )
            callWakeLock?.acquire()

            Log.d(TAG, "Audio configured for voice call")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup audio", e)
        }
    }

    fun toggleVideo(isVideoEnabled: Boolean) {
        localVideoTrack?.setEnabled(isVideoEnabled)
    }

    fun switchCamera() {
        localVideoCapturer?.switchCamera(null)
    }

    fun release() {
        if (isReleased) {
            Log.d(TAG, "Already released, skipping")
            return
        }
        isReleased = true
        
        endCall()
        try {
            eglBase?.release()
        } catch (e: Exception) {
            Log.w(TAG, "EglBase already released: ${e.message}")
        }
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
    }

    fun startScreenSharing(mediaProjection: android.media.projection.MediaProjection) {
        if (isSharingScreen) return
        isSharingScreen = true

        try {
            // Create a video source for screen capture
            screenVideoSource = peerConnectionFactory?.createVideoSource(false)
            if (screenVideoSource == null) {
                Log.e(TAG, "Failed to create screen video source")
                isSharingScreen = false
                return
            }

            val source = screenVideoSource
            if (source == null) {
                Log.e(TAG, "Screen video source null after creation")
                isSharingScreen = false
                return
            }
            // Create screen capturer
            screenCapturer = ScreenCapturer(context, source)
            screenCapturer?.start(mediaProjection)

            // Create video track from screen source
            screenVideoTrack = peerConnectionFactory?.createVideoTrack("screen_0", source)
            screenVideoTrack?.setEnabled(true)

            // Find the original video sender and replace its track with screen track
            val pc = peerConnection ?: return
            for (sender in pc.senders) {
                val track = sender.track()
                if (track != null && track.kind() == "video") {
                    originalVideoSender = sender
                    sender.setTrack(screenVideoTrack, false)
                    Log.d(TAG, "Screen sharing started - replaced camera with screen track")
                    break
                }
            }

            // Disable local camera to save resources
            localVideoTrack?.setEnabled(false)
            localVideoCapturer?.stopCapture()

            Log.d(TAG, "Screen sharing enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start screen sharing", e)
            isSharingScreen = false
        }
    }

    fun stopScreenSharing() {
        if (!isSharingScreen) return
        isSharingScreen = false

        try {
            // Stop screen capturer
            screenCapturer?.stop()
            screenCapturer = null

            // Restore original camera track
            if (originalVideoSender != null) {
                originalVideoSender?.setTrack(localVideoTrack, false)
                originalVideoSender = null
                Log.d(TAG, "Screen sharing stopped - restored camera track")
            }

            // Re-enable local camera
            localVideoTrack?.setEnabled(true)
            try {
                localVideoCapturer?.startCapture(1280, 720, 30)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restart camera after screen share", e)
            }

            // Dispose screen track and source
            screenVideoTrack?.dispose()
            screenVideoTrack = null
            screenVideoSource?.dispose()
            screenVideoSource = null

            Log.d(TAG, "Screen sharing disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping screen sharing", e)
        }
    }

    fun isScreenSharing(): Boolean = isSharingScreen
}
