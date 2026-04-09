package com.gemmacode.assistant

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GemmaEngine(private val context: Context) {

    private var llmInference: LlmInference? = null
    var isReady = false
    private val MODEL_PATH = "/storage/emulated/0/Documents/Gemma4/gemma-2b-it-cpu-int4.bin"
    private val SYSTEM_PROMPT = """
        You are an expert coding assistant.
        Answer programming questions clearly with working code examples.
        Always format code with proper markdown code blocks.
        Be concise and accurate.
    """.trimIndent()

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(MODEL_PATH)
                .setMaxTokens(2048)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            isReady = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generate(userMessage: String, onToken: (String) -> Unit) {
        val inference = llmInference ?: throw IllegalStateException("Model belum siap")
        val prompt = "<start_of_turn>user\n$SYSTEM_PROMPT\n\n$userMessage<end_of_turn>\n<start_of_turn>model\n"
        withContext(Dispatchers.IO) {
            inference.generateResponseAsync(prompt) { partial, _ ->
                if (partial != null) onToken(partial)
            }
        }
    }

    fun close() {
        llmInference?.close()
        llmInference = null
        isReady = false
    }
}
