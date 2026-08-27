package com.codeaza.bhaiyaaa.ui.prayer

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.entity.NotificationRuleEntity
import com.codeaza.bhaiyaaa.data.db.entity.PrayerEntity
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.domain.model.PrayerMadhab
import com.codeaza.bhaiyaaa.domain.model.PrayerMethod
import com.codeaza.bhaiyaaa.domain.model.PrayerMode
import com.codeaza.bhaiyaaa.domain.model.PrayerSettings
import com.codeaza.bhaiyaaa.domain.model.PrayerSilenceMode
import com.codeaza.bhaiyaaa.domain.model.PrayerWindow
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.prayer.PrayerScheduler
import com.codeaza.bhaiyaaa.prayer.PrayerTimeCalculator
import com.codeaza.bhaiyaaa.util.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone

class PrayerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val settingsRepo = SettingsRepository(application)

    val settings: StateFlow<PrayerSettings> = settingsRepo.settings
        .map { it.prayer }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PrayerSettings())

    val prayers: StateFlow<List<PrayerEntity>> = db.prayerDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules = db.notificationRuleDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Today's resolved windows, recomputed whenever anything they depend on changes. */
    private val _todayWindows = MutableStateFlow<List<PrayerWindow>>(emptyList())
    val todayWindows: StateFlow<List<PrayerWindow>> = _todayWindows.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.settings.collect { recompute() }
        }
        viewModelScope.launch {
            db.prayerDao().observeAll().collect { recompute() }
        }
    }

    private suspend fun recompute() {
        val prayerSettings = settingsRepo.settings.first().prayer
        val rows = db.prayerDao().allOnce()
        _todayWindows.value = PrayerTimeCalculator.windowsForDay(
            prayerSettings, rows, System.currentTimeMillis(), TimeZone.getDefault()
        )
    }

    // ------------------------------------------------------------- settings

    fun setEnabled(value: Boolean) = update { settingsRepo.setPrayerEnabled(value) }
    fun setMode(mode: PrayerMode) = update { settingsRepo.setPrayerMode(mode) }
    fun setMethod(method: PrayerMethod) = update { settingsRepo.setPrayerMethod(method) }
    fun setMadhab(madhab: PrayerMadhab) = update { settingsRepo.setPrayerMadhab(madhab) }
    fun setSilenceMode(mode: PrayerSilenceMode) = update { settingsRepo.setPrayerSilenceMode(mode) }

    fun setSilenceMinutes(prayer: Prayer, minutes: Int) = update {
        db.prayerDao().setSilenceMinutes(prayer.storageValue, minutes.coerceIn(1, 180))
    }

    /** @param minutes negative means the window opens before the prayer. */
    fun setStartOffset(prayer: Prayer, minutes: Int) = update {
        db.prayerDao().find(prayer.storageValue)?.let { row ->
            db.prayerDao().upsert(row.copy(startOffsetMinutes = minutes.coerceIn(-60, 60)))
        }
    }

    fun setPrayerEnabled(prayer: Prayer, enabled: Boolean) = update {
        db.prayerDao().setEnabled(prayer.storageValue, enabled)
    }

    /** @param minutesFromMidnight null clears the override and returns to the calculation. */
    fun setManualTime(prayer: Prayer, minutesFromMidnight: Int?) = update {
        db.prayerDao().setManualTime(prayer.storageValue, minutesFromMidnight?.coerceIn(0, 1439))
    }

    fun setRingsDuringPrayer(level: VipLevel, rings: Boolean) = update {
        // Create the row if it is missing, rather than dropping the setting.
        val rule = db.notificationRuleDao().findForLevel(level.storageValue)
            ?: NotificationRuleEntity.defaultFor(level.storageValue)
        db.notificationRuleDao().upsert(rule.copy(ringsDuringPrayer = rings))
    }

    /**
     * Every change re-arms the alarms. Cheap, and it means a setting can never
     * be saved but not scheduled - which would look exactly like the feature
     * silently not working.
     */
    private fun update(block: suspend () -> Unit) = viewModelScope.launch {
        block()
        recompute()
        PrayerScheduler.reschedule(getApplication())
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
     */
    fun useCurrentLocation() = viewModelScope.launch {
        if (!hasLocationPermission()) {
            _message.value = "Location permission is needed, or type coordinates instead."
            return@launch
        }
        val found = withContext(Dispatchers.IO) { lastKnownLocation(getApplication()) }
        if (found == null) {
            _message.value = "No location available yet. Open a maps app once, or type coordinates."
            return@launch
        }
        val (lat, lng) = found
        val label = withContext(Dispatchers.IO) { describe(getApplication(), lat, lng) }
        settingsRepo.setPrayerLocation(lat, lng, label)
        recompute()
        PrayerScheduler.reschedule(getApplication())
        _message.value = "Location set to $label."
    }

    fun setManualLocation(latitude: Double, longitude: Double) = viewModelScope.launch {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            _message.value = "Latitude must be −90 to 90, longitude −180 to 180."
            return@launch
        }
        val label = withContext(Dispatchers.IO) { describe(getApplication(), latitude, longitude) }
        settingsRepo.setPrayerLocation(latitude, longitude, label)
        recompute()
        PrayerScheduler.reschedule(getApplication())
        _message.value = "Location set to $label."
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

    fun consumeMessage() {
        _message.value = null
    }
}
