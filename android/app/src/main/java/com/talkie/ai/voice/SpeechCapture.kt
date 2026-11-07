package com.talkie.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Press-and-hold dictation on top of the platform [SpeechRecognizer]. Shows partial text while
 * listening; [stop] delivers the final transcript to the callback passed to [start].
 */
class SpeechCapture(context: Context) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var onFinal: ((String?) -> Unit)? = null
    private var stopped = false

    var listening by mutableStateOf(false)
        private set
    var partial by mutableStateOf("")
        private set

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start(languageTag: String, onResult: (String?) -> Unit) {
        cancel()
        onFinal = onResult
        stopped = false
        partial = ""
        val instance = SpeechRecognizer.createSpeechRecognizer(appContext).also { recognizer = it }
        instance.setRecognitionListener(listener)
        instance.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Holding the glow is the end-of-speech signal, so keep listening through pauses.
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
            }
        )
        listening = true
    }

    /** Ends capture and delivers the transcript (or the latest partial text). */
    fun stop() {
        if (!listening) return
        stopped = true
        if (ended) finish(heldResult) else recognizer?.stopListening()
    }

    fun cancel() {
        onFinal = null
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        listening = false
        ended = false
        heldResult = null
        partial = ""
    }

    private fun finish(text: String?) {
        val callback = onFinal
        val result = text?.takeIf { it.isNotBlank() } ?: partial.takeIf { it.isNotBlank() }
        cancel()
        callback?.invoke(result)
    }

    /** The recognizer stopped on its own while the user is still holding: keep the text until release. */
    private fun hold(text: String?) {
        heldResult = text?.takeIf { it.isNotBlank() } ?: partial.takeIf { it.isNotBlank() }
        heldResult?.let { partial = it }
        ended = true
        recognizer?.destroy()
        recognizer = null
    }

    private var ended = false
    private var heldResult: String? = null

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }?.let { partial = it }
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (stopped) finish(text) else hold(text)
        }

        override fun onError(error: Int) {
            if (stopped) finish(null) else hold(null)
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}

@Composable
fun rememberSpeechCapture(): SpeechCapture {
    val context = LocalContext.current
    val capture = remember { SpeechCapture(context) }
    DisposableEffect(capture) { onDispose { capture.cancel() } }
    return capture
}
