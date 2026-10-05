package com.openchat.app.presentation.screens.stories

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.lifecycle.viewModelScope
import com.openchat.app.core.BaseViewModel
import com.openchat.app.core.UiEffect
import com.openchat.app.core.UiEvent
import com.openchat.app.core.UiState
import com.openchat.app.data.model.BuiltInFilters
import com.openchat.app.data.model.CameraFilter
import com.openchat.app.data.repository.FilterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class StoryCameraState(
    val isFrontCamera: Boolean = true,
    val selectedFilter: CameraFilter? = null,
    val availableFilters: List<CameraFilter> = BuiltInFilters.AllFilters,
    val isRecording: Boolean = false,
    val recordingDuration: Int = 0,
    val isFlashOn: Boolean = false,
    val zoomLevel: Float = 1f
) : UiState

sealed class StoryCameraEvent : UiEvent {
    data object ToggleCamera : StoryCameraEvent()
    data class SelectFilter(val filter: CameraFilter?) : StoryCameraEvent()
    data object ToggleFlash : StoryCameraEvent()
    data class SetZoom(val zoom: Float) : StoryCameraEvent()
    data object StartRecording : StoryCameraEvent()
    data object StopRecording : StoryCameraEvent()
}

sealed class StoryCameraEffect : UiEffect {
    data class StoryCaptured(val uri: String, val filterId: String?) : StoryCameraEffect()
    data class ShowError(val message: String) : StoryCameraEffect()
}

@HiltViewModel
class StoryCameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val filterRepository: FilterRepository,
    private val cloudinaryRepository: com.openchat.app.data.repository.CloudinaryRepository
) : BaseViewModel<StoryCameraState, StoryCameraEvent, StoryCameraEffect>() {

    override val _uiState = MutableStateFlow(StoryCameraState())

    private var surfaceTexture: SurfaceTexture? = null
    private var recordingStartTime: Long = 0
    private var _imageCapture: ImageCapture? = null

    fun setImageCapture(capture: ImageCapture) {
        _imageCapture = capture
    }

    init {
        loadFilters()
    }

    private fun loadFilters() {
        viewModelScope.launch {
            val builtIn = BuiltInFilters.AllFilters
            setState { copy(availableFilters = builtIn) }
        }
    }

    override fun onEvent(event: StoryCameraEvent) {
        when (event) {
            is StoryCameraEvent.ToggleCamera -> toggleCamera()
            is StoryCameraEvent.SelectFilter -> selectFilter(event.filter)
            is StoryCameraEvent.ToggleFlash -> toggleFlash()
            is StoryCameraEvent.SetZoom -> setZoom(event.zoom)
            is StoryCameraEvent.StartRecording -> startRecording()
            is StoryCameraEvent.StopRecording -> stopRecording()
        }
    }

    internal fun toggleCamera() {
        setState { copy(isFrontCamera = !isFrontCamera) }
    }

    internal fun selectFilter(filter: CameraFilter?) {
        setState { copy(selectedFilter = filter) }
    }

    private fun toggleFlash() {
        setState { copy(isFlashOn = !isFlashOn) }
    }

    private fun setZoom(zoom: Float) {
        setState { copy(zoomLevel = zoom.coerceIn(1f, 5f)) }
    }

    private fun startRecording() {
        setState { copy(isRecording = true) }
        recordingStartTime = System.currentTimeMillis()

        viewModelScope.launch {
            while (_uiState.value.isRecording) {
                kotlinx.coroutines.delay(1000)
                val duration = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
                setState { copy(recordingDuration = duration) }

                if (duration >= 60) {
                    stopRecording()
                }
            }
        }
    }

    private fun stopRecording() {
        setState { copy(isRecording = false, recordingDuration = 0) }
    }

    fun setSurfaceTexture(texture: SurfaceTexture) {
        surfaceTexture = texture
    }

    suspend fun captureStory(onComplete: (String) -> Unit) {
        val imageCapture = _imageCapture
        if (imageCapture == null) {
            sendEffect(StoryCameraEffect.ShowError("Camera not ready"))
            return
        }

        val tempFile = File(context.cacheDir, "story_${UUID.randomUUID()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        try {
            imageCapture.takePicture(
                outputOptions,
                context.mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        viewModelScope.launch {
                            try {
                                val result = cloudinaryRepository.uploadImage(Uri.fromFile(tempFile), "stories")
                                if (result != null) {
                                    onComplete(result)
                                    android.util.Log.d("StoryCameraVM", "Story uploaded: $result")
                                } else {
                                    sendEffect(StoryCameraEffect.ShowError("Failed to upload story"))
                                }
                                tempFile.delete()
                            } catch (e: Exception) {
                                sendEffect(StoryCameraEffect.ShowError("Upload failed: ${e.message}"))
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        android.util.Log.e("StoryCameraVM", "Capture failed", exception)
                        sendEffect(StoryCameraEffect.ShowError("Capture failed: ${exception.message}"))
                    }
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("StoryCameraVM", "Failed to capture story", e)
            sendEffect(StoryCameraEffect.ShowError("Failed to capture story: ${e.message}"))
        }
    }

    fun shareFilterPack(packId: String, targetUserId: String) {
        viewModelScope.launch {
            try {
                val result = filterRepository.sendFilterPackToUser(packId, targetUserId)
                if (result.isSuccess) {
                    sendEffect(StoryCameraEffect.ShowError("Filter pack shared!"))
                } else {
                    sendEffect(StoryCameraEffect.ShowError("Failed to share filter pack"))
                }
            } catch (e: Exception) {
                sendEffect(StoryCameraEffect.ShowError("Error sharing filter: ${e.message}"))
            }
        }
    }
}
