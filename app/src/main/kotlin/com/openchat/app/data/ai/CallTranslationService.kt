package com.openchat.app.data.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallTranslationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiEngine: AiEngine
) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private val _lastTranscribedText = MutableStateFlow("")
    val lastTranscribedText = _lastTranscribedText.asStateFlow()

    private val _lastTranslatedText = MutableStateFlow("")
    val lastTranslatedText = _lastTranslatedText.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _speechError = MutableStateFlow<String?>(null)
    val speechError = _speechError.asStateFlow()

    private val _speechLevel = MutableStateFlow(0f)
    val speechLevel = _speechLevel.asStateFlow()

    private var targetLanguage: String = "Spanish"
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var restartScope: CoroutineScope? = null

    init {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(this)
        tts = TextToSpeech(context, this)
    }

    fun startListening(language: String = "Spanish") {
        _isListening.value = true
        _speechError.value = null
        targetLanguage = language
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        _isListening.value = false
        speechRecognizer?.stopListening()
    }

    fun restartListening() {
        stopListening()
        restartScope?.cancel()
        restartScope = CoroutineScope(Dispatchers.Main).apply {
            launch {
                kotlinx.coroutines.delay(500)
                startListening(targetLanguage)
            }
        }
    }

    override fun onResults(results: Bundle?) {
        _isListening.value = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.getOrNull(0) ?: return

        _lastTranscribedText.value = text

        scope.launch {
            val translation = aiEngine.translate(text, targetLanguage)
            _lastTranslatedText.value = translation
            speak(translation)
            restartListening()
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.getOrNull(0)
        if (!text.isNullOrBlank()) {
            _lastTranscribedText.value = text
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        _isListening.value = true
        _speechError.value = null
    }

    override fun onBeginningOfSpeech() {
    }

    override fun onRmsChanged(rmsdB: Float) {
        _speechLevel.value = rmsdB
    }

    override fun onBufferReceived(buffer: ByteArray?) {
    }

    override fun onEndOfSpeech() {
        _isListening.value = false
    }

    override fun onError(error: Int) {
        _isListening.value = false
        val errorMsg = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
            else -> "Unknown error ($error)"
        }
        _speechError.value = errorMsg
        restartListening()
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        }
    }

    fun release() {
        scope.cancel()
        restartScope?.cancel()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }
}
