package com.openchat.app.presentation.screens.call

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.openchat.app.presentation.theme.Motion
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openchat.app.service.call.MediaProjectionService
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import org.webrtc.SurfaceViewRenderer
import java.util.concurrent.TimeUnit

enum class CallType {
    VOICE, VIDEO, GROUP_VOICE, GROUP_VIDEO
}

private val instaBg = Color(0xFF1A1A2E)
private val instaBgGradient = listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
private val overlayColor = Color(0x33000000)
private val btnMute = Color(0xFF4A4A5A)
private val btnEnd = Color(0xFFFF3B30)
private val btnAccept = Color(0xFF34C759)
private val btnNormal = Color(0x99000000)

@Composable
fun CallScreen(
    contactName: String,
    contactPhoto: String?,
    isIncoming: Boolean,
    callType: CallType,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleScreenShare: (android.media.projection.MediaProjection?) -> Unit,
    modifier: Modifier = Modifier,
    // Must be passed down: ActiveCallView owns the local SurfaceViewRenderer, and
    // the ViewModel is what supplies the renderer to WebRTCManager.
    viewModel: CallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val remoteStreamState = remember { mutableStateOf<org.webrtc.MediaStream?>(null) }
    LaunchedEffect(uiState.remoteStream) { remoteStreamState.value = uiState.remoteStream }

    val context = LocalContext.current
    var controlsVisible by remember { mutableStateOf(true) }
    val controlsAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = tween(Motion.MEDIUM, easing = Motion.Decelerate)
    )

    LaunchedEffect(uiState.callState) {
        if (uiState.callState == CallState.CONNECTED) {
            controlsVisible = true
            delay(3000)
            controlsVisible = false
        }
    }

    DisposableEffect(uiState.callState) {
        val activity = context as? ComponentActivity
        val isActive = uiState.callState == CallState.CONNECTED
                || uiState.callState == CallState.CALLING
                || uiState.callState == CallState.RINGING
        if (isActive) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            MediaProjectionService.stop(context)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) android.util.Log.w("CallScreen", "Audio permission denied")
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) android.util.Log.w("CallScreen", "Camera permission denied")
    }

    val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val screenCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data ?: return@rememberLauncherForActivityResult
            val projection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, data)
            onToggleScreenShare(projection)
        } else {
            MediaProjectionService.stop(context)
        }
    }

    LaunchedEffect(uiState.callState) {
        if (uiState.callState == CallState.CALLING || uiState.callState == CallState.RINGING) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            if (uiState.isVideoEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val ringtonePlayer = remember {
        if (isIncoming) try {
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            android.media.MediaPlayer().apply { setDataSource(context, uri); isLooping = true; prepare() }
        } catch (e: Exception) { null } else null
    }
    LaunchedEffect(isIncoming, uiState.callState) {
        if (isIncoming && uiState.callState == CallState.RINGING) try { ringtonePlayer?.start() } catch (e: Exception) { android.util.Log.w("CallScreen", "Ringtone start failed", e) }
        else try { if (ringtonePlayer?.isPlaying == true) ringtonePlayer?.stop() } catch (e: Exception) { android.util.Log.w("CallScreen", "Ringtone stop failed", e) }
    }
    DisposableEffect(Unit) { onDispose { try { ringtonePlayer?.apply { if (isPlaying) stop(); release() } } catch (e: Exception) { android.util.Log.w("CallScreen", "Ringtone release failed", e) } } }

    val handleScreenShare: () -> Unit = {
        if (uiState.isSharingScreen) {
            MediaProjectionService.stop(context)
            onToggleScreenShare(null)
        } else if (uiState.callState == CallState.CONNECTED) {
            MediaProjectionService.start(context)
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(instaBgGradient))
            .clickable(enabled = uiState.callState == CallState.CONNECTED) { controlsVisible = !controlsVisible }
    ) {
        when {
            isIncoming && uiState.callState == CallState.RINGING -> IncomingCallView(contactName, contactPhoto, callType, onAccept, onDecline)
            else -> ActiveCallView(contactName, contactPhoto, callType, uiState, remoteStreamState, controlsAlpha, viewModel, onEnd, onToggleMute, onToggleSpeaker, onSwitchCamera, handleScreenShare)
        }
    }
}

@Composable
private fun IncomingCallView(
    contactName: String, contactPhoto: String?, callType: CallType,
    onAccept: () -> Unit, onDecline: () -> Unit
) {
    val pulse = rememberInfiniteTransition()
    val scale by pulse.animateFloat(1f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "")

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.15f))

        Text(
            text = "OpenChat",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(40.dp))

        Box(Modifier.scale(scale), contentAlignment = Alignment.Center) {
            Box(Modifier.size(140.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)))
            Box(Modifier.size(120.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                if (contactPhoto != null) AsyncImage(model = contactPhoto, contentDescription = null, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Default.Person, contentDescription = null, Modifier.size(60.dp), tint = instaBg)
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(contactName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (callType == CallType.VIDEO) "OpenChat Video Call" else "OpenChat Voice Call",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp
        )

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            InstaButton(btnEnd, Icons.Default.CallEnd, "Decline", 72.dp, onDecline)
            InstaButton(btnAccept, Icons.Default.Call, "Accept", 72.dp, onAccept)
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun ActiveCallView(
    contactName: String, contactPhoto: String?, callType: CallType,
    uiState: CallStateUi,
    remoteStreamState: androidx.compose.runtime.MutableState<org.webrtc.MediaStream?>,
    controlsAlpha: Float,
    viewModel: CallViewModel,
    onEnd: () -> Unit, onToggleMute: () -> Unit, onToggleSpeaker: () -> Unit, onSwitchCamera: () -> Unit,
    onToggleScreenShare: () -> Unit
) {
    val stream = remoteStreamState.value
    val isVideo = callType == CallType.VIDEO || callType == CallType.GROUP_VIDEO
    val isConnected = uiState.callState == CallState.CONNECTED
    val minutes = TimeUnit.SECONDS.toMinutes(uiState.callDuration.toLong())
    val seconds = uiState.callDuration % 60
    val isCalling = uiState.callState == CallState.CALLING || uiState.callState == CallState.RINGING

    Box(Modifier.fillMaxSize()) {
        if (isVideo) {
            if (stream != null) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            try { init(uiState.eglContext, null) } catch (e: Exception) { init(null, null) }
                            setMirror(false)
                            setEnableHardwareScaler(true)
                            stream.videoTracks.firstOrNull()?.addSink(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black))
                InstaAvatar(contactPhoto, 80.dp, Modifier.align(Alignment.Center))
                if (isCalling) {
                    Text("Connecting...", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center).padding(top = 100.dp))
                }
            }

            if (uiState.eglContext != null) {
                // Hand the renderer to the ViewModel: CallEvent.SetupLocalView was
                // declared and handled but never dispatched from anywhere, so
                // localView was always null - startCall/acceptIncomingCall received no
                // surface, and the self-view stayed black for every video call.
                var renderer: SurfaceViewRenderer? = null
                DisposableEffect(uiState.eglContext) {
                    onDispose {
                        viewModel.onEvent(CallEvent.ClearLocalView)
                        renderer?.release()
                        renderer = null
                    }
                }
                Box(Modifier.align(Alignment.TopEnd).padding(16.dp).size(100.dp, 140.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color.DarkGray)) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                try { init(uiState.eglContext, null) } catch (e: Exception) { init(null, null) }
                                setMirror(true)
                                setEnableHardwareScaler(true)
                                renderer = this
                                viewModel.onEvent(CallEvent.SetupLocalView(this))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            InstaAvatar(contactPhoto, 120.dp, Modifier.align(Alignment.Center).padding(bottom = 60.dp))
        }

        Column(Modifier.fillMaxSize()) {
            // Top bar. Driven by controlsAlpha rather than a boolean so the auto-hide
            // after 3s crossfades smoothly instead of blinking out.
            AnimatedVisibility(
                visible = controlsAlpha > 0.01f,
                enter = fadeIn(tween(Motion.MEDIUM, easing = Motion.Decelerate)) +
                    slideInVertically(tween(Motion.SLOW, easing = Motion.Decelerate)) { -it / 3 },
                exit = fadeOut(tween(Motion.FAST, easing = Motion.Accelerate)) +
                    slideOutVertically(tween(Motion.MEDIUM, easing = Motion.Accelerate)) { -it / 3 }
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(overlayColor).padding(16.dp).padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(contactName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        if (isConnected) {
                            Text(
                                text = String.format("%02d:%02d", minutes, seconds),
                                color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp
                            )
                        } else {
                            Text(
                                text = if (isCalling) "Connecting..." else "Connected",
                                color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp
                            )
                        }
                        if (isConnected && isVideo) {
                            Spacer(Modifier.height(2.dp))
                            Text("OpenChat", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Bottom controls - rise from the bottom edge, mirroring the top bar.
            AnimatedVisibility(
                visible = controlsAlpha > 0.01f,
                enter = fadeIn(tween(Motion.MEDIUM, easing = Motion.Decelerate)) +
                    slideInVertically(tween(Motion.SLOW, easing = Motion.Decelerate)) { it / 2 },
                exit = fadeOut(tween(Motion.FAST, easing = Motion.Accelerate)) +
                    slideOutVertically(tween(Motion.MEDIUM, easing = Motion.Accelerate)) { it / 2 }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(overlayColor).padding(bottom = 32.dp).padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (isVideo) {
                            InstaControl(if (uiState.isMuted) Icons.Default.MicOff else Icons.Default.Mic, btnMute, onToggleMute, uiState.isMuted)
                            InstaControl(Icons.Default.Videocam, btnMute, { /* toggle video */ })
                            InstaCallEnd(btnEnd, onEnd)
                            InstaControl(Icons.Default.Cameraswitch, btnMute, onSwitchCamera)
                            InstaControl(Icons.Default.Cast, btnMute, onToggleScreenShare, uiState.isSharingScreen)
                            InstaControl(if (uiState.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown, btnMute, onToggleSpeaker, uiState.isSpeakerOn)
                        } else {
                            InstaControl(if (uiState.isMuted) Icons.Default.MicOff else Icons.Default.Mic, btnMute, onToggleMute, uiState.isMuted)
                            InstaCallEnd(btnEnd, onEnd)
                            InstaControl(if (uiState.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown, btnMute, onToggleSpeaker, uiState.isSpeakerOn)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstaAvatar(photo: String?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(modifier.then(Modifier.size(size)).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        if (photo != null) {
            AsyncImage(model = photo, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Default.Person, contentDescription = null, Modifier.size(size / 2), tint = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun InstaControl(icon: ImageVector, bg: Color, onClick: () -> Unit, isActive: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(52.dp).clip(CircleShape).background(if (isActive) Color.White else btnNormal).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, Modifier.size(24.dp), tint = if (isActive) Color.Black else Color.White)
        }
    }
}

@Composable
private fun InstaCallEnd(bg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(64.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.CallEnd, contentDescription = "End call", Modifier.size(28.dp), tint = Color.White)
    }
}

@Composable
private fun InstaButton(bg: Color, icon: ImageVector, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, Modifier.size(size / 2.2f), tint = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}
