package com.openchat.app.data.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var llmInference: LlmInference? = null
    
    private val _isModelReady = MutableStateFlow(false)
    val isModelReady = _isModelReady.asStateFlow()

    // Path where the Llama model should be stored
    // Path where the AI model should be stored
    private val modelPath = File(context.filesDir, "gemma2b.task")

    fun initialize() {
        if (modelPath.exists()) {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath.absolutePath)
                .setMaxTokens(1024)
                .setTopK(40)
                .setTemperature(0.7f)
                .build()
            
            llmInference = LlmInference.createFromOptions(context, options)
            _isModelReady.value = true
        }
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val inference = llmInference ?: return@withContext "Model not ready. Please download it first."
        try {
            inference.generateResponse(prompt)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun translate(text: String, targetLanguage: String): String {
        val prompt = "Translate the following text to $targetLanguage: \"$text\"\nOnly provide the translation, no explanation."
        return generateResponse(prompt)
    }
    
    fun getModelFile(): File = modelPath
}
