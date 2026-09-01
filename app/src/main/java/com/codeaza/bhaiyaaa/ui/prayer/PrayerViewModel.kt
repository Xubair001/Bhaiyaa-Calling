package com.codeaza.bhaiyaaa.ui.prayer

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.dao.PrayerDao
import com.codeaza.bhaiyaaa.data.db.entity.NotificationRuleEntity
import com.codeaza.bhaiyaaa.data.db.entity.PrayerEntity
import com.codeaza.bhaiyaaa.data.db.entity.SilenceScheduleEntity
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.domain.model.PrayerMadhab
import com.codeaza.bhaiyaaa.domain.model.PrayerMethod
import com.codeaza.bhaiyaaa.domain.model.PrayerMode
import com.codeaza.bhaiyaaa.domain.model.PrayerSettings
import com.codeaza.bhaiyaaa.domain.model.PrayerSilenceMode
import com.codeaza.bhaiyaaa.domain.model.SilenceWindow
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.domain.model.Weekdays
import com.codeaza.bhaiyaaa.prayer.PrayerScheduler
import com.codeaza.bhaiyaaa.prayer.PrayerTimeCalculator
import com.codeaza.bhaiyaaa.prayer.SilenceController
import com.codeaza.bhaiyaaa.prayer.SilencePlan
import com.codeaza.bhaiyaaa.service.AdhanService
import com.codeaza.bhaiyaaa.util.Permissions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * State and actions for the Quiet times screen.
 *
 * ## Why this is shaped the way it is
 *
 * Every prayer setting used to be written like this: run the query, re-read
 * everything from DataStore and Room, recompute the day, then re-arm every
 * alarm - all on whichever thread called, which for a tap was the main one.
 * The editor changed three things, so one Save ran that pipeline three times,
 * and two `collect` blocks in `init` re-ran the recompute on every emission it
 * produced. Rearming alone is dozens of binder transactions. That, not the
 * database, is what made changing a prayer time feel slow.
 *
 * Three things replace it:
 *
 * 1. **Derived state, not recomputed state.** [todayWindows] is a `combine` of
 *    the three inputs, mapped on a background dispatcher and shared. It cannot
 *    fall out of date and it cannot be computed twice for one change.
 * 2. **Optimistic edits.** A write shows in [prayers] on the next frame via
 *    [pendingEdits], before the database has answered. The overlay clears when
 *    the stored row matches, and is rolled back with a message if the write
 *    fails - so the UI is never a lie for longer than the write takes.
 * 3. **Debounced rescheduling.** Alarms are re-armed once the user has stopped
 *    changing things, on the IO dispatcher, rather than after every keystroke
 *    of a slider.
 */
class PrayerViewModel(
    application: Application,
    /** Injectable so tests can run the derivation on a deterministic dispatcher. */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    private companion object {
        const val TEST_SILENCE_MILLIS = 60_000L

        /**
         * How long to wait after the last change before re-arming alarms.
         *
         * Long enough that dragging a slider arms once rather than forty
         * times, short enough that nobody can leave the screen before it
         * lands - and leaving does not cancel it, because the work runs in
         * viewModelScope, which outlives the composition.
         */
        const val RESCHEDULE_DEBOUNCE_MILLIS = 350L
    }

    private val db = AppDatabase.getInstance(application)
    private val settingsRepo = SettingsRepository(application)

    val settings: StateFlow<PrayerSettings> = settingsRepo.settings
        .map { it.prayer }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, PrayerSettings())

    /**
     * Edits shown before the database has confirmed them, keyed by prayer name.
     *
     * The alternative - waiting for Room's Flow to come back - is a write, a
     * transaction, an invalidation and a re-query between the tap and the
     * screen changing. That round trip is exactly what "the UI must update
     * immediately" rules out.
     */
    private val pendingEdits = MutableStateFlow<Map<String, PrayerEntity>>(emptyMap())

    private val storedPrayers: StateFlow<List<PrayerEntity>> = db.prayerDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val prayers: StateFlow<List<PrayerEntity>> =
        combine(storedPrayers, pendingEdits) { stored, pending ->
            if (pending.isEmpty()) return@combine stored
            val merged = stored.map { pending[it.name] ?: it }
            // A pending edit for a row the stored list does not have yet is
            // still the user's edit. Dropping it would make an edit made
            // before the first query returned simply not appear.
            val unseen = pending.values.filter { edit -> stored.none { it.name == edit.name } }
            (merged + unseen).sortedBy { it.sortOrder }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules = db.notificationRuleDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** User-defined quiet periods. Independent of the prayer feature. */
    val schedules: StateFlow<List<SilenceScheduleEntity>> = db.silenceScheduleDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Today's resolved windows.
     *
     * Derived rather than recomputed on demand: it is a pure function of the
     * three inputs, so expressing it as one is what guarantees the card, the
     * list and the alarms are all looking at the same day. The mapping runs on
     * [computeDispatcher] because it does the solar calculation for five
     * prayers, which has no business on the frame thread.
     */
    val todayWindows: StateFlow<List<SilenceWindow>> =
        combine(settings, prayers, schedules) { prayerSettings, rows, scheduleRows ->
            SilencePlan.windowsForDay(
                settings = prayerSettings,
                prayers = rows,
                schedules = scheduleRows,
                dayStartMillis = System.currentTimeMillis(),
                zone = prayerSettings.zone
            )
        }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Today's prayer instants, whether or not silencing is switched on.
     *
     * Separate from [todayWindows] because they answer different questions.
     * A window exists only when the phone is going to be silenced; an anchor
     * is simply when the prayer is. The Hadith card and the adhan both need
     * the second without the first - someone can want to know it is Asr
     * without wanting their phone silenced for it.
     */
    val prayerAnchors: StateFlow<Map<Prayer, Long>> =
        combine(settings, prayers) { prayerSettings, rows ->
            PrayerTimeCalculator.anchorsForDay(
                settings = prayerSettings,
                prayers = rows,
                dayStartMillis = System.currentTimeMillis(),
                zone = prayerSettings.zone
            )
        }
            .flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * Requests to re-arm the alarms.
     *
     * A conflating buffer: while one reschedule is in flight, a hundred more
     * requests collapse into one that runs after it. Combined with the
     * debounce, dragging a slider arms the alarms once.
     */
    private val rescheduleRequests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @OptIn(FlowPreview::class)
    private fun startRescheduleWorker() {
        viewModelScope.launch {
            rescheduleRequests
                .debounce(RESCHEDULE_DEBOUNCE_MILLIS)
                .collect {
                    // PrayerScheduler moves itself to IO, but being explicit
                    // here documents that this is deliberately not on the
                    // caller's thread.
                    withContext(ioDispatcher) {
                        runCatching { PrayerScheduler.reschedule(getApplication()) }
                    }
                }
        }
    }

    init {
        startRescheduleWorker()

        // Drop an optimistic edit once the stored row has caught up with it.
        // Holding it beyond that point would mean a later change made
        // elsewhere - an import, the assistant - could not show through.
        viewModelScope.launch {
            storedPrayers.collect { rows ->
                if (pendingEdits.value.isEmpty()) return@collect
                pendingEdits.update { edits ->
                    edits.filterValues { edit -> rows.none { it == edit } }
                }
            }
        }
    }

    // ------------------------------------------------------------- settings

    fun setEnabled(value: Boolean) = write { settingsRepo.setPrayerEnabled(value) }
    fun setMode(mode: PrayerMode) = write { settingsRepo.setPrayerMode(mode) }
    fun setMethod(method: PrayerMethod) = write { settingsRepo.setPrayerMethod(method) }
    fun setMadhab(madhab: PrayerMadhab) = write { settingsRepo.setPrayerMadhab(madhab) }
    fun setSilenceMode(mode: PrayerSilenceMode) = write { settingsRepo.setPrayerSilenceMode(mode) }

    fun setPrayerEnabled(prayer: Prayer, enabled: Boolean) =
        optimistic(prayer, { it.copy(enabled = enabled) }) {
            db.prayerDao().setEnabled(prayer.storageValue, enabled)
        }

    /**
     * Everything one prayer's editor can change, in one write.
     *
     * @param minutesFromMidnight null clears the override and returns that
     *   prayer to the calculation. Anything else is normalised into the
     *   prayer's own half of the clock by the DAO, so a value that somehow
     *   arrived invalid is corrected rather than stored.
     */
    fun savePrayerEdit(
        prayer: Prayer,
        minutesFromMidnight: Int?,
        silenceMinutes: Int,
        startOffsetMinutes: Int
    ) = optimistic(
        prayer,
        {
            // Applies exactly what the DAO will apply, coercion included.
            // If the optimistic row and the stored row could differ, the
            // overlay would never be recognised as settled and would mask the
            // real value for the life of the screen.
            it.copy(
                manualMinutesFromMidnight = minutesFromMidnight?.let(prayer::normaliseTime),
                silenceMinutes = silenceMinutes.coerceIn(
                    PrayerDao.SILENCE_MINUTES_MIN,
                    PrayerDao.SILENCE_MINUTES_MAX
                ),
                startOffsetMinutes = startOffsetMinutes.coerceIn(
                    PrayerDao.OFFSET_MIN,
                    PrayerDao.OFFSET_MAX
                )
            )
        }
    ) {
        db.prayerDao().saveEdit(prayer, minutesFromMidnight, silenceMinutes, startOffsetMinutes)
    }

    fun setRingsDuringPrayer(level: VipLevel, rings: Boolean) = write {
        // Create the row if it is missing, rather than dropping the setting.
        val rule = db.notificationRuleDao().findForLevel(level.storageValue)
            ?: NotificationRuleEntity.defaultFor(level.storageValue)
        db.notificationRuleDao().upsert(rule.copy(ringsDuringPrayer = rings))
    }

    // ---------------------------------------------------------------- adhan

    fun setAdhanEnabled(value: Boolean) = write {
        settingsRepo.setAdhanEnabled(value)
        _message.value = if (value) {
            "The adhan will sound at each prayer you have switched on."
        } else {
            "The adhan is off. Nothing will play."
        }
    }

    fun setAdhanSound(uri: String?, label: String) = write {
        settingsRepo.setAdhanSound(uri, label)
        // A newly chosen sound should be hearable at the next prayer even if
        // one has already sounded today, otherwise picking a sound in the
        // evening means waiting until tomorrow to hear it.
        AdhanService.clearPlaybackHistory(getApplication())
        _message.value = if (uri == null) "Using this phone's alarm tone." else "Adhan set to $label."
    }

    // ------------------------------------------------------------- location

    fun hasLocationPermission(): Boolean =
        Permissions.isGranted(getApplication(), Manifest.permission.ACCESS_COARSE_LOCATION)

    /**
     * Reads the last known coarse location.
     *
     * Deliberately does not request a fresh fix: prayer times shift by seconds
     * over tens of kilometres, so the last known position is plenty, and asking
     * for a live fix would spin up the radio for no benefit.
     *
     * Setting a location never disturbs a time the user typed in - an override
     * always wins over the calculation, and this only changes what the prayers
     * *without* an override resolve to.
     */
    fun useCurrentLocation() = viewModelScope.launch {
        if (!hasLocationPermission()) {
            _message.value = "Location permission is needed, or type coordinates instead."
            return@launch
        }
        val found = withContext(ioDispatcher) { lastKnownLocation(getApplication()) }
        if (found == null) {
            _message.value = "No location available yet. Open a maps app once, or type coordinates."
            return@launch
        }
        val (lat, lng) = found
        val label = withContext(ioDispatcher) { describe(getApplication(), lat, lng) }
        withContext(ioDispatcher) { settingsRepo.setPrayerLocation(lat, lng, label) }
        requestReschedule()
        _message.value = "Location set to $label. Times you entered yourself are kept."
    }

    fun setManualLocation(latitude: Double, longitude: Double) = viewModelScope.launch {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            _message.value = "Latitude must be −90 to 90, longitude −180 to 180."
            return@launch
        }
        val label = withContext(ioDispatcher) { describe(getApplication(), latitude, longitude) }
        withContext(ioDispatcher) { settingsRepo.setPrayerLocation(latitude, longitude, label) }
        requestReschedule()
        _message.value = "Location set to $label. Times you entered yourself are kept."
    }

    private fun lastKnownLocation(context: Context): Pair<Double, Double>? = try {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        manager?.getProviders(true).orEmpty()
            .asSequence()
            .mapNotNull { provider ->
                @Suppress("MissingPermission")
                runCatching { manager?.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    } catch (e: SecurityException) {
        null
    }

    /** Best-effort place name. Falls back to the coordinates, never to a guess. */
    private fun describe(context: Context, lat: Double, lng: Double): String {
        val fallback = String.format(Locale.US, "%.3f, %.3f", lat, lng)
        if (!Geocoder.isPresent()) return fallback
        return try {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(lat, lng, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
                ?: fallback
        } catch (e: Exception) {
            // Geocoding is a network call on most devices; offline it throws.
            fallback
        }
    }

    private val _blockedReason = MutableStateFlow<String?>(null)

    /**
     * Why silence cannot run right now, or null.
     *
     * State rather than a function the screen calls: it queries
     * NotificationManager, and it used to be invoked straight from
     * composition - a system call on every single recomposition. It is
     * refreshed when the mode changes and when the user returns from a system
     * settings screen, which are the only two moments the answer can change.
     */
    val blockedReason: StateFlow<String?> = _blockedReason.asStateFlow()

    fun refreshBlockedReason() = viewModelScope.launch {
        _blockedReason.value = withContext(ioDispatcher) {
            SilenceController.blockedReason(getApplication(), settings.value.silenceMode)
        }
    }

    /**
     * Applies the silence for one minute, right now.
     *
     * Waiting until the next prayer to find out whether this works is a poor
     * way to test it, and it is why the feature could look broken with no way
     * to tell what part had failed.
     */
    fun testSilenceNow() = viewModelScope.launch {
        val mode = settings.value.silenceMode
        val blocked = withContext(ioDispatcher) {
            SilenceController.blockedReason(getApplication(), mode)
        }
        if (blocked != null) {
            _blockedReason.value = blocked
            _message.value = blocked
            return@launch
        }
        val applied = withContext(ioDispatcher) {
            SilenceController.enterSilence(getApplication(), "TEST", mode)
        }
        if (!applied) {
            _message.value = "This phone wouldn't let Sukoon change the ringer."
            return@launch
        }
        // The exit is an alarm, not a delay, so the phone comes back even if
        // this screen is closed.
        withContext(ioDispatcher) {
            PrayerScheduler.scheduleSilenceEnd(
                getApplication(),
                System.currentTimeMillis() + TEST_SILENCE_MILLIS
            )
        }
        _message.value = when (mode) {
            PrayerSilenceMode.VIBRATE -> "Vibrate only for one minute — try calling yourself."
            PrayerSilenceMode.SILENT -> "Silenced for one minute — try calling yourself."
        }
    }

    // ----------------------------------------------------- custom schedules

    fun saveSchedule(
        id: Long?,
        label: String,
        startMinutes: Int,
        durationMinutes: Int,
        daysMask: Int,
        mode: PrayerSilenceMode,
        enabled: Boolean = true
    ) = write {
        val cleanLabel = label.trim().ifBlank { "Quiet time" }
        val entity = SilenceScheduleEntity(
            id = id ?: 0,
            label = cleanLabel,
            startMinutesFromMidnight = startMinutes.coerceIn(0, 1439),
            durationMinutes = durationMinutes.coerceIn(1, 720),
            // A schedule with no days would never run and would look broken;
            // fall back to every day rather than silently saving a dead row.
            daysMask = daysMask.takeIf { it and Weekdays.EVERY_DAY != 0 } ?: Weekdays.EVERY_DAY,
            enabled = enabled,
            silenceMode = mode.storageValue,
            createdAt = System.currentTimeMillis()
        )
        if (id == null) db.silenceScheduleDao().insert(entity)
        else db.silenceScheduleDao().upsert(entity)
        _message.value = if (id == null) "\"$cleanLabel\" added." else "\"$cleanLabel\" updated."
    }

    fun setScheduleEnabled(id: Long, enabled: Boolean) = write {
        db.silenceScheduleDao().setEnabled(id, enabled)
    }

    fun deleteSchedule(id: Long) = write {
        db.silenceScheduleDao().deleteById(id)
        _message.value = "Schedule deleted."
    }

    // ------------------------------------------------------------ time zone

    fun setTimeZone(id: String?) = write { settingsRepo.setPrayerTimeZone(id) }

    /** A short, curated list plus whatever the device is on, rather than all 600. */
    fun timeZoneOptions(): List<String> {
        val device = java.util.TimeZone.getDefault().id
        val common = listOf(
            "Asia/Karachi", "Asia/Dubai", "Asia/Riyadh", "Asia/Kolkata", "Asia/Dhaka",
            "Europe/London", "Europe/Istanbul", "America/New_York", "America/Chicago",
            "America/Los_Angeles", "Australia/Sydney"
        )
        return (listOf(device) + common).distinct()
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ------------------------------------------------------------- plumbing

    /**
     * A persisted change: written off the main thread, then the alarms are
     * asked to catch up.
     *
     * The rearm is a request rather than a call, so ten changes in a second
     * cost one rearm rather than ten.
     */
    private fun write(block: suspend () -> Unit) = viewModelScope.launch {
        val ok = runCatching { withContext(ioDispatcher) { block() } }.isSuccess
        if (!ok) {
            _message.value = "That didn't save. Try again."
            return@launch
        }
        requestReschedule()
    }

    /**
     * A change to one prayer, shown immediately and persisted behind it.
     *
     * @param optimistic applied to the current row so the UI has the new value
     *   before the write completes.
     * @param persist the real write. If it throws, the overlay is dropped and
     *   the screen falls back to whatever the database actually holds - so a
     *   failure is a visible revert rather than a UI permanently out of step
     *   with storage.
     */
    private fun optimistic(
        prayer: Prayer,
        optimistic: (PrayerEntity) -> PrayerEntity,
        persist: suspend () -> Unit
    ) = viewModelScope.launch {
        val current = prayers.value.firstOrNull { it.name == prayer.storageValue }
            ?: PrayerEntity(name = prayer.storageValue, sortOrder = prayer.order)
        val edited = optimistic(current)
        pendingEdits.update { it + (prayer.storageValue to edited) }

        val ok = runCatching { withContext(ioDispatcher) { persist() } }.isSuccess
        if (!ok) {
            pendingEdits.update { it - prayer.storageValue }
            _message.value = "${prayer.label} didn't save. Your previous time is still in place."
            return@launch
        }
        requestReschedule()
    }

    private fun requestReschedule() {
        rescheduleRequests.tryEmit(Unit)
    }
}
