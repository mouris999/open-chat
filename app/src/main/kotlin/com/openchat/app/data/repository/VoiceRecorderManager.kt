package com.openchat.app.data.repository

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRecorderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _amplitude = MutableStateFlow(0.0)
    val amplitude: StateFlow<Double> = _amplitude

    suspend fun startRecording(): File = withContext(Dispatchers.IO) {
        stopRecording()

        val recordingsDir = File(context.cacheDir, "voice_recordings")
        recordingsDir.mkdirs()

        val fileName = "voice_${System.currentTimeMillis()}.3gp"
        outputFile = File(recordingsDir, fileName)

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outputFile?.absolutePath ?: return@apply)

            try {
                prepare()
                start()
                _isRecording.value = true
            } catch (e: Exception) {
                android.util.Log.e("VoiceRecorderManager", "Failed to start recording", e)
                release()
            }
        }

        return@withContext outputFile ?: File(recordingsDir, "voice_fallback_${System.currentTimeMillis()}.3gp")
    }

    suspend fun stopRecording(): Uri? = withContext(Dispatchers.IO) {
        try {
            mediaRecorder?.apply {
                if (_isRecording.value) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceRecorderManager", "Error stopping recording", e)
        }

        mediaRecorder = null
        _isRecording.value = false
        _amplitude.value = 0.0

        return@withContext outputFile?.let { Uri.fromFile(it) }
    }

    fun getMaxAmplitude(): Int {
        return if (_isRecording.value) {
            (mediaRecorder?.maxAmplitude ?: 0) / 100
        } else 0
    }

    private fun release() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            android.util.Log.e("VoiceRecorderManager", "Error releasing recorder", e)
        }
        mediaRecorder = null
        _isRecording.value = false
    }
}
