package com.codeaza.bhaiyaaa.ai.speech

import kotlinx.coroutines.flow.Flow

/** Streaming state of a dictation session. */
sealed interface SpeechEvent {
    data object Ready : SpeechEvent
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data class Error(val message: String, val recoverable: Boolean = true) : SpeechEvent
    data object Stopped : SpeechEvent
}

/** Which engine actually handled a dictation, so the UI can be honest about it. */
enum class SpeechEngineKind(val label: String) {
    /**
     * Vosk, running from a model on this device. Genuinely offline: no audio
     * and no text leaves the phone, with or without a network connection.
     */
    VOSK_OFFLINE("On-device (Vosk)"),

    /**
     * Android's own recognizer. Asked to prefer offline, but whether it really
     * stays on-device depends on the user having an offline language pack
     * installed - so this is never described as guaranteed-offline.
     */
    ANDROID_SYSTEM("System recognizer")
}

/**
 * A dictation source. Two implementations exist so BHAIYAAA degrades honestly:
 * Vosk when the user has installed a model, the platform recognizer otherwise,
 * and the Assistant screen always shows which one is in use.
 */
interface SpeechRecognizerEngine {
    val kind: SpeechEngineKind

    /** True if this engine can start right now (model present, service available). */
    suspend fun isAvailable(): Boolean

    /**
     * Starts listening. The flow completes after [SpeechEvent.Final] or
     * [SpeechEvent.Stopped]; cancelling the collector stops the microphone and
     * releases the engine.
     */
    fun listen(): Flow<SpeechEvent>
}
