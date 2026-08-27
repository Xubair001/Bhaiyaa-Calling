package com.codeaza.bhaiyaaa.ai.speech

import android.content.Context
import com.codeaza.bhaiyaaa.ai.model.ModelCatalog
import com.codeaza.bhaiyaaa.ai.model.ModelStorage
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.ai.model.ModelPurpose
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Offline speech via Vosk (Apache-2.0), using a model the user chose to install.
 *
 * Nothing is bundled: if no speech model is installed and enabled this reports
 * unavailable, and the caller falls back to the platform recognizer. Model
 * loading is heavy, so it happens when listening starts and is released the
 * moment the flow is cancelled - never held open in the background (brief §29).
 */
class VoskSpeechRecognizer(private val context: Context) : SpeechRecognizerEngine {

    override val kind: SpeechEngineKind = SpeechEngineKind.VOSK_OFFLINE

    override suspend fun isAvailable(): Boolean = nativeLibraryLoads() && activeModelPath() != null

    /**
     * Probes that libvosk actually loaded for this device's ABI.
     *
     * A missing native library surfaces as UnsatisfiedLinkError - an Error, not
     * an Exception - so it slips straight past an ordinary catch and would take
     * the process down. Checked once here, and every call site below catches
     * Throwable for the same reason.
     */
    private fun nativeLibraryLoads(): Boolean = nativeLoadResult

    private val nativeLoadResult: Boolean by lazy {
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            true
        } catch (t: Throwable) {
            false
        }
    }

    private suspend fun activeModelPath(): String? {
        val row = AppDatabase.getInstance(context).aiModelDao()
            .activeForPurpose(ModelPurpose.SPEECH_RECOGNITION.storageValue) ?: return null
        val path = row.installedPath ?: return null
        // Trust the filesystem, not the row: a model directory can be cleared
        // by the OS reclaiming space without the database hearing about it.
        return if (ModelStorage(context).isInstalled(row.id)) path else null
    }

    override fun listen(): Flow<SpeechEvent> = callbackFlow {
        val path = activeModelPath()
        if (path == null) {
            trySend(SpeechEvent.Error("No offline speech model installed", recoverable = false))
            close()
            return@callbackFlow
        }

        var model: Model? = null
        var service: SpeechService? = null

        try {
            model = Model(path)
            val recognizer = Recognizer(model, SAMPLE_RATE)
            service = SpeechService(recognizer, SAMPLE_RATE)

            val listener = object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    textOf(hypothesis, "partial")?.let { trySend(SpeechEvent.Partial(it)) }
                }

                override fun onResult(hypothesis: String?) {
                    textOf(hypothesis, "text")?.let { trySend(SpeechEvent.Final(it)) }
                }

                override fun onFinalResult(hypothesis: String?) {
                    textOf(hypothesis, "text")?.let { trySend(SpeechEvent.Final(it)) }
                    trySend(SpeechEvent.Stopped)
                    close()
                }

                override fun onError(exception: Exception?) {
                    trySend(SpeechEvent.Error(exception?.message ?: "Recognition failed"))
                    close()
                }

                override fun onTimeout() {
                    trySend(SpeechEvent.Stopped)
                    close()
                }
            }

            trySend(SpeechEvent.Ready)
            service.startListening(listener)
        } catch (t: Throwable) {
            // Throwable, not Exception: a missing libvosk for this ABI raises
            // UnsatisfiedLinkError, which an Exception catch would let through
            // and crash the app. Missing RECORD_AUDIO and a corrupt model
            // directory land here too.
            trySend(SpeechEvent.Error(t.message ?: "Could not start offline recognition"))
            close()
        }

        awaitClose {
            // runCatching swallows Throwable, which is what we want while
            // tearing down native resources.
            runCatching { service?.stop() }
            runCatching { service?.shutdown() }
            runCatching { model?.close() }
        }
    }

    private fun textOf(hypothesisJson: String?, key: String): String? {
        if (hypothesisJson.isNullOrBlank()) return null
        return runCatching {
            JSONObject(hypothesisJson).optString(key).takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    companion object {
        private const val SAMPLE_RATE = 16000.0f

        /** Ids this recognizer knows how to load. */
        val SUPPORTED_MODEL_IDS = setOf(ModelCatalog.VOSK_EN_SMALL, ModelCatalog.VOSK_HI_SMALL)
    }
}
