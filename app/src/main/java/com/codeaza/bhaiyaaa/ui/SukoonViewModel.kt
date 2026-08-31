package com.codeaza.bhaiyaaa.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.db.entity.MemoryEntity
import com.codeaza.bhaiyaaa.data.db.entity.NotificationRuleEntity
import com.codeaza.bhaiyaaa.data.db.entity.ReminderEntity
import com.codeaza.bhaiyaaa.data.db.entity.TagEntity
import com.codeaza.bhaiyaaa.data.db.projection.ContactStats
import com.codeaza.bhaiyaaa.data.export.DataTransfer
import com.codeaza.bhaiyaaa.data.export.TransferResult
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.data.repository.SukoonRepository
import com.codeaza.bhaiyaaa.domain.model.AppSettings
import com.codeaza.bhaiyaaa.domain.model.Lookup
import com.codeaza.bhaiyaaa.domain.model.MemorySource
import com.codeaza.bhaiyaaa.domain.model.PersonalityMode
import com.codeaza.bhaiyaaa.domain.model.ThemeMode
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.domain.usecase.CallInsights
import com.codeaza.bhaiyaaa.domain.usecase.GlobalSearch
import com.codeaza.bhaiyaaa.domain.usecase.InsightsCalculator
import com.codeaza.bhaiyaaa.domain.usecase.SearchResults
import com.codeaza.bhaiyaaa.notifications.NotificationChannels
import com.codeaza.bhaiyaaa.service.ReminderScheduler
import com.codeaza.bhaiyaaa.util.Permissions
import com.codeaza.bhaiyaaa.util.SecurePrefs
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A one-shot message for the UI to show in a snackbar. */
data class UserMessage(val id: Long, val text: String)

/** Whether the privacy lock is currently standing between the user and their data. */
enum class LockState { NOT_SET, LOCKED, UNLOCKED }

/**
 * Shared state for the main screens.
 *
 * Screens observe, they don't compute: every list here is a Room Flow, so the
 * UI updates itself when data changes rather than needing manual refreshes.
 */
class SukoonViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SukoonRepository(application)
    private val settingsRepo = SettingsRepository(application)
    private val db = AppDatabase.getInstance(application)
    private val insightsCalculator = InsightsCalculator(db.callRecordDao())
    private val globalSearch = GlobalSearch(db, repository)
    private val dataTransfer = DataTransfer(application)

    // ------------------------------------------------------------- settings

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /**
     * False until DataStore has answered once.
     *
     * [settings] has to start from some value, and that placeholder says
     * onboardingComplete = false - so anything reading it before the real value
     * arrives concludes this is a first run. That is why onboarding flashed up
     * on every single launch. Nothing that branches on settings should render
     * until this is true.
     */
    private val _settingsLoaded = MutableStateFlow(false)
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()

    // ---------------------------------------------------------------- data

    val contacts: StateFlow<List<ContactEntity>> = repository.contacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val vipContacts: StateFlow<List<ContactEntity>> = repository.vipContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val calls: StateFlow<List<CallRecordEntity>> = repository.calls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val memories: StateFlow<List<MemoryEntity>> = repository.memories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = repository.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<TagEntity>> = repository.tags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val callStats: StateFlow<List<ContactStats>> = repository.callStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notificationRules: StateFlow<List<NotificationRuleEntity>> = repository.notificationRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _insights = MutableStateFlow(CallInsights())
    val insights: StateFlow<CallInsights> = _insights.asStateFlow()

    private val _missedToday = MutableStateFlow(0)
    val missedToday: StateFlow<Int> = _missedToday.asStateFlow()

    // --------------------------------------------------------------- status

    private val _hasCorePermissions = MutableStateFlow(Permissions.allCoreGranted(application))
    val hasCorePermissions: StateFlow<Boolean> = _hasCorePermissions.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _message = MutableStateFlow<UserMessage?>(null)
    val message: StateFlow<UserMessage?> = _message.asStateFlow()

    private val _lockState = MutableStateFlow(
        if (SecurePrefs.isLockEnabled(application)) LockState.LOCKED else LockState.NOT_SET
    )
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    // --------------------------------------------------------------- search

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow(SearchResults())
    val searchResults: StateFlow<SearchResults> = _searchResults.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.settings.first()
            _settingsLoaded.value = true
        }
        // Debounced so typing doesn't fire a query per keystroke.
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _searchQuery
                .debounce(200)
                .distinctUntilChanged()
                .collect { q -> _searchResults.value = globalSearch.search(q) }
        }
        refreshDerived()
        if (_hasCorePermissions.value) sync()
    }

    // -------------------------------------------------------------- actions

    fun onPermissionsChanged() {
        val granted = Permissions.allCoreGranted(getApplication())
        _hasCorePermissions.value = granted
        // Sync as soon as anything is granted - a partial grant still syncs the
        // half it's allowed to read.
        sync()
    }

    fun sync() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = repository.syncFromDevice()
                settingsRepo.setLastSyncAt(System.currentTimeMillis())
                refreshDerived()
                when {
                    !result.contactsPermission && !result.callLogPermission ->
                        showMessage("Grant Contacts and Call log so Sukoon has something to work with.")

                    // A granted permission that still reads nothing means the
                    // provider refused - say so rather than showing an empty
                    // screen that looks like "you have no calls".
                    result.readFailedDespitePermission ->
                        showMessage(
                            "Couldn't read your " +
                                listOfNotNull(
                                    "contacts".takeIf { result.contactsError != null },
                                    "call log".takeIf { result.callLogError != null }
                                ).joinToString(" and ") +
                                ". Check Sukoon's permissions in system settings."
                        )

                    result.storedContacts == 0 && result.callLogPermission && result.storedCalls == 0 ->
                        showMessage("Synced, but your phone returned no contacts or calls.")

                    result.contactsAdded > 0 || result.callsAdded > 0 ->
                        showMessage(
                            "Synced ${result.storedContacts} contacts and ${result.storedCalls} calls."
                        )
                }
            } catch (e: Exception) {
                showMessage("Couldn't sync from your phone just now.")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /** Recomputes anything that isn't a live Flow (aggregates and insights). */
    fun refreshDerived() {
        viewModelScope.launch {
            runCatching {
                _missedToday.value = repository.missedCallsToday()
                _insights.value = insightsCalculator.calculate()
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    // -------------------------------------------------------------- contacts

    fun setVipLevel(phoneNumber: String, level: VipLevel) = viewModelScope.launch {
        repository.setVipLevel(phoneNumber, level)
        showMessage(
            if (level.isVip) "Set to ${level.label}. Their calls get the special treatment now."
            else "VIP removed."
        )
    }

    fun setTag(phoneNumber: String, tag: String?) = viewModelScope.launch {
        repository.setTag(phoneNumber, tag)
    }

    fun setRelationship(phoneNumber: String, value: String?) = viewModelScope.launch {
        repository.setRelationship(phoneNumber, value)
    }

    fun setImportance(phoneNumber: String, importance: Int) = viewModelScope.launch {
        repository.setImportance(phoneNumber, importance)
    }

    fun setNotes(phoneNumber: String, notes: String) = viewModelScope.launch {
        repository.setNotes(phoneNumber, notes)
        showMessage("Note saved.")
    }

    fun setSpam(phoneNumber: String, isSpam: Boolean) = viewModelScope.launch {
        repository.setSpam(phoneNumber, isSpam)
    }

    fun setContactNotifications(phoneNumber: String, enabled: Boolean) = viewModelScope.launch {
        repository.setContactNotifications(phoneNumber, enabled)
    }

    fun observeContact(phoneNumber: String) = repository.observeContact(phoneNumber)

    /** Lookup-shaped so the screen can tell "still loading" from "no such contact". */
    fun observeContactLookup(phoneNumber: String): Flow<Lookup<ContactEntity>> =
        repository.observeContactResolved(phoneNumber)
            .map { if (it == null) Lookup.Missing else Lookup.Found(it) }

    fun observeCallLookup(id: Long): Flow<Lookup<CallRecordEntity>> =
        repository.observeCall(id)
            .map { if (it == null) Lookup.Missing else Lookup.Found(it) }

    fun callsForContact(matchKey: String) = repository.callsForContact(matchKey)

    fun memoriesForContact(phoneNumber: String) = repository.memoriesForContact(phoneNumber)

    fun observeStatsFor(matchKey: String) = repository.observeStatsFor(matchKey)

    fun observeCall(id: Long) = repository.observeCall(id)

    // ----------------------------------------------------------------- calls

    fun setCallImportant(id: Long, important: Boolean) = viewModelScope.launch {
        repository.setCallImportant(id, important)
    }

    fun setCallNote(id: Long, note: String?) = viewModelScope.launch {
        repository.setCallNote(id, note)
    }

    // -------------------------------------------------------------- memories

    fun addMemory(
        body: String,
        title: String? = null,
        contactPhoneNumber: String? = null,
        source: MemorySource = MemorySource.MANUAL,
        callRecordId: Long? = null,
        isPrivate: Boolean = false
    ) = viewModelScope.launch {
        if (body.isBlank()) return@launch
        repository.addMemory(body, title, contactPhoneNumber, source, callRecordId, isPrivate)
        showMessage("Saved to Memory.")
    }

    fun updateMemory(memory: MemoryEntity) = viewModelScope.launch {
        repository.updateMemory(memory)
    }

    /** Direct FTS lookup for the Memory screen's search field. */
    suspend fun searchMemoriesNow(query: String) = repository.searchMemories(query)

    fun deleteMemory(id: Long) = viewModelScope.launch {
        repository.deleteMemory(id)
        showMessage("Memory deleted.")
    }

    // ------------------------------------------------------------- reminders

    fun addReminder(text: String, dueAt: Long? = null, contactPhoneNumber: String? = null) =
        viewModelScope.launch {
            if (text.isBlank()) return@launch
            val id = repository.addReminder(text, dueAt, contactPhoneNumber)
            // Only arm an alarm for a future time - a past due date would fire
            // instantly and read as a bug.
            if (dueAt != null && dueAt > System.currentTimeMillis()) {
                ReminderScheduler.schedule(getApplication(), id, dueAt)
            }
        }

    fun setReminderDone(id: Long, done: Boolean) = viewModelScope.launch {
        repository.setReminderDone(id, done)
        if (done) ReminderScheduler.cancel(getApplication(), id)
    }

    fun deleteReminder(id: Long) = viewModelScope.launch {
        ReminderScheduler.cancel(getApplication(), id)
        repository.deleteReminder(id)
    }

    // -------------------------------------------------------------- settings

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { settingsRepo.setDynamicColor(value) }
    fun setPersonality(mode: PersonalityMode) = viewModelScope.launch { settingsRepo.setPersonality(mode) }
    fun setNotificationsEnabled(v: Boolean) = viewModelScope.launch { settingsRepo.setNotificationsEnabled(v) }
    fun setFlashlightEnabled(v: Boolean) = viewModelScope.launch { settingsRepo.setFlashlightEnabled(v) }
    fun setMissedCallNudge(v: Boolean) = viewModelScope.launch { settingsRepo.setMissedCallNudge(v) }
    fun setAutoSync(v: Boolean) = viewModelScope.launch { settingsRepo.setAutoSync(v) }
    fun setOnboardingComplete() = viewModelScope.launch { settingsRepo.setOnboardingComplete(true) }

    /**
     * Turns Do Not Disturb bypass on or off for a tier.
     *
     * Writes to the channel and to the database. The database copy is what
     * survives: channels get rebuilt on every launch, and without a stored
     * preference to restore from, the setting silently reverts.
     *
     * @param onResult receives what the platform actually did - an OEM build can
     *   accept the call and ignore it, and the UI must not claim success then.
     */
    fun setBypassDnd(level: VipLevel, enabled: Boolean, onResult: (Boolean) -> Unit = {}) =
        viewModelScope.launch {
            val applied = NotificationChannels.setBypassDnd(getApplication(), level, enabled)
            // OrCreate: a tier with no row must not swallow the setting.
            val rule = repository.ruleForOrCreate(level)
            repository.saveRule(rule.copy(bypassDnd = applied))
            if (enabled && !applied) {
                showMessage("This device wouldn't allow Sukoon past Do Not Disturb.")
            }
            onResult(applied)
        }

    fun saveNotificationRule(rule: NotificationRuleEntity) = viewModelScope.launch {
        repository.saveRule(rule)
        showMessage("Alert settings saved.")
    }

    // ---------------------------------------------------------- privacy lock

    fun setPin(pin: String): Boolean {
        val ok = SecurePrefs.setPin(getApplication(), pin)
        if (ok) {
            _lockState.value = LockState.UNLOCKED
            showMessage("Privacy lock is on.")
        } else {
            showMessage("PIN must be ${SecurePrefs.MIN_PIN_LENGTH}–${SecurePrefs.MAX_PIN_LENGTH} digits.")
        }
        return ok
    }

    fun verifyPin(pin: String): Boolean {
        val ok = SecurePrefs.verifyPin(getApplication(), pin)
        if (ok) _lockState.value = LockState.UNLOCKED
        return ok
    }

    fun onBiometricSuccess() {
        _lockState.value = LockState.UNLOCKED
    }

    fun disableLock() {
        SecurePrefs.disableLock(getApplication())
        _lockState.value = LockState.NOT_SET
        showMessage("Privacy lock is off.")
    }

    fun setBiometricEnabled(enabled: Boolean) {
        SecurePrefs.setBiometricEnabled(getApplication(), enabled)
    }

    /** Called when the app goes to the background so it re-locks on return. */
    fun relockIfEnabled() {
        if (SecurePrefs.isLockEnabled(getApplication())) _lockState.value = LockState.LOCKED
    }

    // ------------------------------------------------------- privacy centre

    fun exportData(uri: Uri, includeCallHistory: Boolean) = viewModelScope.launch {
        when (val result = dataTransfer.export(uri, includeCallHistory)) {
            is TransferResult.Success -> showMessage(result.summary)
            is TransferResult.Failure -> showMessage("Export failed: ${result.message}")
        }
    }

    fun importData(uri: Uri) = viewModelScope.launch {
        when (val result = dataTransfer.import(uri)) {
            is TransferResult.Success -> {
                showMessage(result.summary)
                refreshDerived()
            }
            is TransferResult.Failure -> showMessage("Import failed: ${result.message}")
        }
    }

    fun resetVipAndAnnotations() = viewModelScope.launch {
        repository.resetVipAndAnnotations()
        showMessage("VIP tiers, tags and notes cleared.")
    }

    fun clearCallHistory() = viewModelScope.launch {
        repository.clearCallHistory()
        refreshDerived()
        showMessage("Local call history cleared. Your phone's own call log is untouched.")
    }

    fun clearMemories() = viewModelScope.launch {
        repository.clearMemories()
        showMessage("All memories deleted.")
    }

    fun deleteEverything() = viewModelScope.launch {
        repository.deleteEverything()
        settingsRepo.clearAll()
        SecurePrefs.disableLock(getApplication())
        _lockState.value = LockState.NOT_SET
        refreshDerived()
        showMessage("Everything deleted.")
    }

    // -------------------------------------------------------------- messages

    fun showMessage(text: String) {
        _message.value = UserMessage(System.currentTimeMillis(), text)
    }

    fun consumeMessage() {
        _message.value = null
    }
}
