package com.codeaza.bhaiyaaa.ai.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Fallback dictation using Android's own recognizer.
 *
 * EXTRA_PREFER_OFFLINE asks the platform to stay on-device, but whether it
 * actually does depends on the user having an offline language pack installed
 * (Settings → System → Languages → On-device speech recognition). Because that
 * cannot be verified from here, the Assistant screen labels this engine as the
 * system recognizer rather than claiming it is offline.
 */
class AndroidSpeechRecognizer(private val context: Context) : SpeechRecognizerEngine {

    override val kind: SpeechEngineKind = SpeechEngineKind.ANDROID_SYSTEM

    override suspend fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun listen(): Flow<SpeechEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechEvent.Error("No speech recognizer on this device", recoverable = false))
            close()
            return@callbackFlow
        }

        // SpeechRecognizer is main-thread-only, so it is created and driven there.
        var recognizer: SpeechRecognizer? = null

        val created = kotlinx.coroutines.CompletableDeferred<SpeechRecognizer?>()
        launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
            if (r == null) {
                trySend(SpeechEvent.Error("Could not create speech recognizer", recoverable = false))
                created.complete(null)
                close()
                return@launch
            }
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { trySend(SpeechEvent.Ready) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    trySend(SpeechEvent.Error(describeError(error), recoverable = error != SpeechRecognizer.ERROR_CLIENT))
                    close()
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotBlank()) trySend(SpeechEvent.Final(text))
                    trySend(SpeechEvent.Stopped)
                    close()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { trySend(SpeechEvent.Partial(it)) }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            runCatching { r.startListening(intent) }
                .onFailure {
                    trySend(SpeechEvent.Error(it.message ?: "Could not start listening"))
                    close()
                }
            created.complete(r)
        }

        awaitClose {
            val r = recognizer
            if (r != null) {
                // Release on the main thread too - destroying off-thread throws.
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    runCatching { r.cancel() }
                    runCatching { r.destroy() }
                }
            }
        }
    }

    private fun describeError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
        SpeechRecognizer.ERROR_CLIENT -> "Recognition cancelled"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission not granted"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "This device's recognizer wanted the network. Install an offline model in Settings → AI Models."
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
        else -> "Speech recognition failed"
    }
}
