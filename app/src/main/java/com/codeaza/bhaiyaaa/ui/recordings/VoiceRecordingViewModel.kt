package com.codeaza.bhaiyaaa.ui.recordings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeaza.bhaiyaaa.data.db.entity.VoiceRecordingEntity
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.data.repository.VoiceRecordingRepository
import com.codeaza.bhaiyaaa.domain.model.AdhanSettings
import com.codeaza.bhaiyaaa.service.AdhanService
import com.codeaza.bhaiyaaa.util.AudioRecorder
import com.codeaza.bhaiyaaa.util.SoundPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The voice recordings screen.
 *
 * A self-contained feature: nothing in the prayer, silence or alarm path calls
 * into it, and the adhan falls back to the phone's own alarm tone if there are
 * no recordings at all. Removing this package would cost the app this screen
 * and nothing else, which is what "modular so it can be disabled" has to mean
 * in practice.
 */
class VoiceRecordingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRecordingRepository(application)
    private val settingsRepo = SettingsRepository(application)
    private val recorder = AudioRecorder(application)
    private val preview = SoundPreview()

    val recordings: StateFlow<List<VoiceRecordingEntity>> = repository.recordings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The URI currently set as the adhan, so the list can mark which one it is. */
    val selectedSoundUri: StateFlow<String?> = settingsRepo.settings
        .map { it.prayer.adhan.soundUri }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /** Which recording is previewing, so only one stop button is ever shown. */
    private val _previewingId = MutableStateFlow<Long?>(null)
    val previewingId: StateFlow<Long?> = _previewingId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var pendingFile: File? = null

    /**
     * The call the in-flight recording belongs to.
     *
     * Captured when recording starts rather than passed to stop: the caller
     * that began knows the context, and threading it through the stop button
     * would let a screen change between the two and misfile the note.
     */
    private var pendingCallId: Long? = null

    init {
        // Clear anything a killed recording left behind, once, quietly.
        viewModelScope.launch { runCatching { repository.removeOrphanedFiles() } }
    }

    /** @param callId files the result against a call; null keeps it standalone. */
    fun startRecording(callId: Long? = null) {
        if (_isRecording.value) return
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { repository.newRecordingFile() }
            val started = withContext(Dispatchers.IO) { recorder.start(file) }
            if (!started) {
                _message.value = "Couldn't open the microphone. Check the permission and try again."
                return@launch
            }
            pendingFile = file
            pendingCallId = callId
            _isRecording.value = true
        }
    }

    fun stopRecording(label: String) {
        if (!_isRecording.value) return
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { recorder.stop() }
            val callId = pendingCallId
            _isRecording.value = false
            pendingFile = null
            pendingCallId = null
            if (file == null) {
                _message.value = "That was too short to save."
                return@launch
            }
            repository.addRecorded(file, label, callId)
            _message.value = "Saved."
        }
    }

    fun cancelRecording() {
        if (!_isRecording.value) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { recorder.cancel() }
            _isRecording.value = false
            pendingFile = null
            pendingCallId = null
        }
    }

    /**
     * Brings an audio file the user already has into Sukoon.
     *
     * This is the route for a recording the phone's own dialer made: Sukoon
     * cannot capture the call itself, but it can be where that file lives and
     * is filed against the right call.
     */
    fun import(uri: Uri, label: String, callId: Long? = null) = viewModelScope.launch {
        val id = repository.importFrom(uri, label, callId)
        _message.value = if (id == null) "Couldn't read that file." else "Imported."
    }

    /** One call's voice notes. Remember the result - it builds a new Flow. */
    fun recordingsForCall(callId: Long) = repository.recordingsForCall(callId)

    fun rename(id: Long, label: String) = viewModelScope.launch {
        repository.rename(id, label)
    }

    fun delete(recording: VoiceRecordingEntity) = viewModelScope.launch {
        // If the one being deleted is the chosen adhan, fall back to the
        // phone's alarm tone rather than leaving the preference pointing at a
        // file that no longer exists - which would be silence at prayer time.
        if (selectedSoundUri.value == repository.uriFor(recording).toString()) {
            settingsRepo.setAdhanSound(null, "")
        }
        if (_previewingId.value == recording.id) stopPreview()
        repository.delete(recording.id)
        _message.value = "Deleted."
    }

    fun togglePreview(recording: VoiceRecordingEntity) {
        if (_previewingId.value == recording.id) {
            stopPreview()
            return
        }
        val started = preview.play(getApplication(), repository.uriFor(recording)) {
            _previewingId.value = null
        }
        _previewingId.value = if (started) recording.id else null
        if (!started) _message.value = "Couldn't play that recording."
    }

    /**
     * Whether this recording is the one the adhan plays.
     *
     * Takes the current selection as a parameter rather than reading the flow
     * itself, so a screen that passes observed state gets a real recomposition
     * dependency instead of a value silently read once. Compares whole URIs:
     * two files can share a name, and "which one is selected" is not a
     * question to answer by string suffix.
     */
    fun isSelectedAsAdhan(recording: VoiceRecordingEntity, selectedUri: String?): Boolean =
        selectedUri == repository.uriFor(recording).toString()

    fun stopPreview() {
        preview.stop()
        _previewingId.value = null
    }

    fun useAsAdhan(recording: VoiceRecordingEntity) = viewModelScope.launch {
        settingsRepo.setAdhanSound(repository.uriFor(recording).toString(), recording.label)
        // So the choice can be heard at the next prayer even if one has already
        // sounded today.
        AdhanService.clearPlaybackHistory(getApplication())
        _message.value = "\"${recording.label}\" will play at each prayer."
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** The cap the adhan will apply to whatever is selected, for the UI to state. */
    val maxAdhanSeconds: Int = AdhanSettings.DEFAULT_MAX_DURATION_SECONDS

    override fun onCleared() {
        super.onCleared()
        // Leaving the screen must not leave the microphone open or a preview
        // playing - both outlive the composition otherwise.
        recorder.cancel()
        preview.stop()
    }
}
