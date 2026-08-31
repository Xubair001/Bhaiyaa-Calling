package com.codeaza.bhaiyaaa.ui.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeaza.bhaiyaaa.ai.AssistantAction
import com.codeaza.bhaiyaaa.ai.AssistantSource
import com.codeaza.bhaiyaaa.ai.ResourcePhrasebook
import com.codeaza.bhaiyaaa.ai.RuleBasedAssistantEngine
import com.codeaza.bhaiyaaa.ai.model.ModelPurpose
import com.codeaza.bhaiyaaa.ai.speech.AndroidSpeechRecognizer
import com.codeaza.bhaiyaaa.ai.speech.SpeechEngineKind
import com.codeaza.bhaiyaaa.ai.speech.SpeechEvent
import com.codeaza.bhaiyaaa.ai.speech.SpeechRecognizerEngine
import com.codeaza.bhaiyaaa.ai.speech.VoskSpeechRecognizer
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.data.repository.SukoonRepository
import com.codeaza.bhaiyaaa.data.repository.RoomAssistantDataSource
import com.codeaza.bhaiyaaa.service.ReminderScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One turn of the conversation. */
data class ChatMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
    val sources: List<AssistantSource> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class SpeechUiState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val engine: SpeechEngineKind? = null,
    val error: String? = null
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = SukoonRepository(application)
    private val settingsRepo = SettingsRepository(application)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _speech = MutableStateFlow(SpeechUiState())
    val speech: StateFlow<SpeechUiState> = _speech.asStateFlow()

    /** Which recognizer would be used right now, shown in the UI before listening. */
    private val _availableSpeechEngine = MutableStateFlow<SpeechEngineKind?>(null)
    val availableSpeechEngine: StateFlow<SpeechEngineKind?> = _availableSpeechEngine.asStateFlow()

    private var listenJob: Job? = null
    private var nextId = 1L

    init {
        viewModelScope.launch { refreshSpeechAvailability() }
    }

    /**
     * Prefers Vosk when the user has installed a model, since that is genuinely
     * offline. Falls back to the platform recognizer otherwise, and the UI
     * labels which one is in play rather than claiming offline either way.
     */
    private suspend fun pickSpeechEngine(): SpeechRecognizerEngine? {
        val vosk = VoskSpeechRecognizer(getApplication())
        if (vosk.isAvailable()) return vosk
        val android = AndroidSpeechRecognizer(getApplication())
        if (android.isAvailable()) return android
        return null
    }

    suspend fun refreshSpeechAvailability() {
        _availableSpeechEngine.value = pickSpeechEngine()?.kind
    }

    suspend fun offlineModelInstalled(): Boolean =
        db.aiModelDao().activeForPurpose(ModelPurpose.SPEECH_RECOGNITION.storageValue) != null

    fun send(input: String) {
        val text = input.trim()
        if (text.isBlank()) return

        _messages.value = _messages.value + ChatMessage(nextId++, text, fromUser = true)
        _isThinking.value = true

        viewModelScope.launch {
            try {
                val response = engine().respond(text)
                _messages.value = _messages.value + ChatMessage(
                    id = nextId++,
                    text = response.text,
                    fromUser = false,
                    sources = response.sources
                )
                // The engine creates the reminder row; arming its alarm is a
                // platform concern, so it happens here rather than inside the
                // engine (which stays pure and unit-testable).
                (response.action as? AssistantAction.ReminderCreated)?.let { created ->
                    val dueAt = created.dueAt
                    if (dueAt != null && dueAt > System.currentTimeMillis()) {
                        ReminderScheduler.schedule(getApplication(), created.id, dueAt)
                    }
                }
            } catch (e: Exception) {
                _messages.value = _messages.value + ChatMessage(
                    nextId++,
                    "Something went wrong reading your data just now.",
                    fromUser = false
                )
            } finally {
                _isThinking.value = false
            }
        }
    }

    private suspend fun engine(): RuleBasedAssistantEngine {
        val personality = settingsRepo.settings.first().personality
        return RuleBasedAssistantEngine(
            data = RoomAssistantDataSource(db, repository),
            phrasebook = ResourcePhrasebook(getApplication(), personality)
        )
    }

    fun clearConversation() {
        _messages.value = emptyList()
    }

    // ---------------------------------------------------------------- speech

    fun startListening() {
        if (_speech.value.isListening) return
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            val recognizer = pickSpeechEngine()
            if (recognizer == null) {
                _speech.value = SpeechUiState(error = "No speech recognizer available on this device.")
                return@launch
            }
            _speech.value = SpeechUiState(isListening = true, engine = recognizer.kind)
            try {
                recognizer.listen().collect { event ->
                    when (event) {
                        is SpeechEvent.Ready ->
                            _speech.value = _speech.value.copy(isListening = true, error = null)
                        is SpeechEvent.Partial ->
                            _speech.value = _speech.value.copy(partialText = event.text)
                        is SpeechEvent.Final -> {
                            _speech.value = SpeechUiState(engine = recognizer.kind)
                            if (event.text.isNotBlank()) send(event.text)
                        }
                        is SpeechEvent.Error ->
                            _speech.value = SpeechUiState(engine = recognizer.kind, error = event.message)
                        is SpeechEvent.Stopped ->
                            _speech.value = _speech.value.copy(isListening = false, partialText = "")
                    }
                }
            } catch (e: Exception) {
                _speech.value = SpeechUiState(error = e.message ?: "Speech recognition failed")
            }
        }
    }

    fun stopListening() {
        // Cancelling the flow releases the microphone and unloads the model -
        // nothing heavy is left running in the background.
        listenJob?.cancel()
        listenJob = null
        _speech.value = _speech.value.copy(isListening = false, partialText = "")
    }

    fun dismissSpeechError() {
        _speech.value = _speech.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        listenJob?.cancel()
    }
}
