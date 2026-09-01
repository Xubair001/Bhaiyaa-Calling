package com.codeaza.bhaiyaaa.ui.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.codeaza.bhaiyaaa.ai.AssistantAction
import com.codeaza.bhaiyaaa.ai.AssistantSource
import com.codeaza.bhaiyaaa.ai.ResourcePhrasebook
import com.codeaza.bhaiyaaa.ai.RuleBasedAssistantEngine
import com.codeaza.bhaiyaaa.ai.speech.AndroidSpeechRecognizer
import com.codeaza.bhaiyaaa.ai.speech.SpeechEngineKind
import com.codeaza.bhaiyaaa.ai.speech.SpeechEvent
import com.codeaza.bhaiyaaa.ai.speech.SpeechRecognizerEngine
import com.codeaza.bhaiyaaa.ai.speech.VoskSpeechRecognizer
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.data.repository.RoomAssistantDataSource
import com.codeaza.bhaiyaaa.data.repository.SukoonRepository
import com.codeaza.bhaiyaaa.domain.model.PersonalityMode
import com.codeaza.bhaiyaaa.prayer.PrayerScheduler
import com.codeaza.bhaiyaaa.prayer.SilenceController
import com.codeaza.bhaiyaaa.service.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One turn of the conversation. */
data class ChatMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
    val sources: List<AssistantSource> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    /**
     * True for the message that reports a failure.
     *
     * Modelled rather than left as text so the UI can offer a retry on it.
     * A failure the user can only read is a dead end; a failure they can act
     * on is a hiccup.
     */
    val isError: Boolean = false
)

data class SpeechUiState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val engine: SpeechEngineKind? = null,
    val error: String? = null
)

/**
 * The Assistant.
 *
 * ## What changed and why
 *
 * The engine used to be rebuilt for every single message - a new engine, a new
 * Room data source and a new resource-backed phrasebook, which reads strings -
 * for an object whose only variable is the personality setting. It is now
 * built once and rebuilt only when that setting actually changes.
 *
 * Answering also ran on the caller's thread. Parsing and formatting are not
 * heavy, but they are not free either, and they had no business between two
 * frames; [respond] now hands the work to a background dispatcher, so the
 * input field stays live while an answer is being worked out.
 *
 * The conversation survives the process being killed, capped at
 * [MAX_SAVED_MESSAGES]. Losing the thread because Android reclaimed memory
 * while the user was reading a text message is a bad enough experience to be
 * worth the small state; saving *everything* would eventually be a
 * TransactionTooLargeException, which would be a worse one.
 */
class AssistantViewModel(
    application: Application,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : AndroidViewModel(application) {

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
    private var answerJob: Job? = null
    private var nextId = 1L

    /**
     * The engine, and the personality it was built for.
     *
     * Rebuilt only when the personality changes, which is a settings action
     * taken rarely - rather than on every message, which is what it used to be.
     */
    private var engine: RuleBasedAssistantEngine? = null
    private var enginePersonality: PersonalityMode? = null

    /** The last thing the user asked, so a failed turn can be tried again. */
    private var lastUserInput: String? = null

    init {
        restoreConversation()
        viewModelScope.launch { refreshSpeechAvailability() }

        // Drop the cached engine when the tone changes, rather than checking
        // the setting on every message.
        viewModelScope.launch {
            settingsRepo.settings
                .map { it.personality }
                .distinctUntilChanged()
                .collect { personality ->
                    if (personality != enginePersonality) engine = null
                }
        }
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

    // ------------------------------------------------------------ conversation

    fun send(input: String) {
        val text = input.trim()
        if (text.isBlank()) return
        lastUserInput = text
        append(ChatMessage(nextId++, text, fromUser = true))
        answer(text)
    }

    /**
     * Re-runs the last question after a failure.
     *
     * Drops the error message first, so a retry that works leaves no trace of
     * the one that did not - a conversation littered with failures the user
     * already recovered from is noise.
     */
    fun retry() {
        val text = lastUserInput ?: return
        _messages.value = _messages.value.filterNot { it.isError }
        answer(text)
    }

    private fun answer(text: String) {
        answerJob?.cancel()
        _isThinking.value = true
        answerJob = viewModelScope.launch {
            try {
                // Off the main thread. Parsing and formatting are small, but
                // "small" is not "free", and nothing here needs a frame.
                val response = withContext(Dispatchers.Default) { engine().respond(text) }

                append(
                    ChatMessage(
                        id = nextId++,
                        text = response.text,
                        fromUser = false,
                        sources = response.sources
                    )
                )
                applyAction(response.action)
            } catch (e: Exception) {
                append(
                    ChatMessage(
                        id = nextId++,
                        text = "Something went wrong reading your data just now.",
                        fromUser = false,
                        isError = true
                    )
                )
            } finally {
                _isThinking.value = false
            }
        }
    }

    /**
     * Carries out what the engine decided, but deliberately did not do.
     *
     * The engine stays pure and unit-testable; anything that touches the
     * platform - a ringer, an alarm, a stored preference - is applied here.
     */
    private suspend fun applyAction(action: AssistantAction?) {
        when (action) {
            is AssistantAction.SilenceRequested -> {
                val mode = settingsRepo.settings.first().prayer.silenceMode
                val applied = withContext(Dispatchers.IO) {
                    SilenceController.enterSilence(getApplication(), "Quiet time", mode)
                }
                if (applied) {
                    withContext(Dispatchers.IO) {
                        PrayerScheduler.scheduleSilenceEnd(
                            getApplication(),
                            System.currentTimeMillis() + action.minutes * 60_000L
                        )
                    }
                } else {
                    append(
                        ChatMessage(
                            nextId++,
                            "I couldn't change the ringer. Grant Do Not Disturb access in " +
                                "Settings → Quiet times, or choose Vibrate only.",
                            fromUser = false,
                            isError = true
                        )
                    )
                }
            }

            is AssistantAction.ReminderCreated -> {
                val dueAt = action.dueAt
                if (dueAt != null && dueAt > System.currentTimeMillis()) {
                    withContext(Dispatchers.IO) {
                        ReminderScheduler.schedule(getApplication(), action.id, dueAt)
                    }
                }
            }

            is AssistantAction.AdhanPreference -> {
                withContext(Dispatchers.IO) {
                    settingsRepo.setAdhanEnabled(action.enabled)
                    // Turning it on has to arm the alarms, and turning it off
                    // has to drop them, or the setting would not take effect
                    // until something else happened to reschedule.
                    PrayerScheduler.reschedule(getApplication())
                }
            }

            null -> Unit
        }
    }

    private suspend fun engine(): RuleBasedAssistantEngine {
        val personality = settingsRepo.settings.first().personality
        engine?.takeIf { enginePersonality == personality }?.let { return it }
        return RuleBasedAssistantEngine(
            data = RoomAssistantDataSource(getApplication(), db, repository),
            phrasebook = ResourcePhrasebook(getApplication(), personality)
        ).also {
            engine = it
            enginePersonality = personality
        }
    }

    fun clearConversation() {
        answerJob?.cancel()
        _isThinking.value = false
        _messages.value = emptyList()
        lastUserInput = null
        saveConversation()
    }

    private fun append(message: ChatMessage) {
        _messages.value = _messages.value + message
        saveConversation()
    }

    // ------------------------------------------------------------ persistence

    /**
     * Saves the tail of the conversation into the saved-state bundle.
     *
     * Only the text and who said it: sources are provenance for an answer
     * already on screen, and re-deriving them after a process death would mean
     * re-running the query anyway. Parallel arrays rather than a serialised
     * object so nothing here depends on a class shape that a future version
     * might change under a restored bundle.
     */
    private fun saveConversation() {
        val tail = _messages.value.takeLast(MAX_SAVED_MESSAGES)
        savedState[KEY_TEXTS] = ArrayList(tail.map { it.text })
        savedState[KEY_FROM_USER] = tail.map { it.fromUser }.toBooleanArray()
    }

    private fun restoreConversation() {
        val texts: ArrayList<String> = savedState[KEY_TEXTS] ?: return
        val fromUser: BooleanArray = savedState[KEY_FROM_USER] ?: return
        if (texts.size != fromUser.size) return
        _messages.value = texts.mapIndexed { index, text ->
            ChatMessage(id = nextId++, text = text, fromUser = fromUser[index])
        }
        // The last thing the *user* said, so a retry after a restore asks the
        // right question rather than replaying an answer.
        lastUserInput = texts.indices.lastOrNull { fromUser[it] }?.let { texts[it] }
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

    override fun onCleared() {
        super.onCleared()
        listenJob?.cancel()
        answerJob?.cancel()
    }

    private companion object {
        /**
         * Saved-state bundles are capped by the binder transaction size, and
         * exceeding it crashes the app on a configuration change. Twenty turns
         * is far more than anyone scrolls back through and nowhere near it.
         */
        const val MAX_SAVED_MESSAGES = 20
        const val KEY_TEXTS = "assistant_texts"
        const val KEY_FROM_USER = "assistant_from_user"
    }
}
