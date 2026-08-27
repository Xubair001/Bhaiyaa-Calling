package com.codeaza.bhaiyaaa.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.codeaza.bhaiyaaa.domain.model.AppSettings
import com.codeaza.bhaiyaaa.domain.model.PersonalityMode
import com.codeaza.bhaiyaaa.domain.model.PrayerMadhab
import com.codeaza.bhaiyaaa.domain.model.PrayerMethod
import com.codeaza.bhaiyaaa.domain.model.PrayerMode
import com.codeaza.bhaiyaaa.domain.model.PrayerSettings
import com.codeaza.bhaiyaaa.domain.model.PrayerSilenceMode
import com.codeaza.bhaiyaaa.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bhaiyaaa_settings")

/**
 * Non-sensitive preferences. Anything secret (the privacy-lock PIN hash) lives
 * in [com.codeaza.bhaiyaaa.util.SecurePrefs] instead, backed by the Keystore.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PERSONALITY = stringPreferencesKey("personality")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val FLASHLIGHT_ENABLED = booleanPreferencesKey("flashlight_enabled")
        val MISSED_NUDGE = booleanPreferencesKey("missed_call_nudge")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync_enabled")
        val PRIVATE_HIDDEN = booleanPreferencesKey("private_memories_hidden")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")

        val PRAYER_ENABLED = booleanPreferencesKey("prayer_enabled")
        val PRAYER_MODE = stringPreferencesKey("prayer_mode")
        val PRAYER_METHOD = stringPreferencesKey("prayer_method")
        val PRAYER_MADHAB = stringPreferencesKey("prayer_madhab")
        val PRAYER_LAT = stringPreferencesKey("prayer_latitude")
        val PRAYER_LNG = stringPreferencesKey("prayer_longitude")
        val PRAYER_LOCATION_LABEL = stringPreferencesKey("prayer_location_label")
        val PRAYER_SILENCE_MODE = stringPreferencesKey("prayer_silence_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        // A corrupt preferences file must not take the whole app down; fall
        // back to defaults and carry on.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                onboardingComplete = p[Keys.ONBOARDING_COMPLETE] ?: false,
                themeMode = ThemeMode.from(p[Keys.THEME_MODE]),
                dynamicColor = p[Keys.DYNAMIC_COLOR] ?: false,
                personality = PersonalityMode.from(p[Keys.PERSONALITY]),
                notificationsEnabled = p[Keys.NOTIFICATIONS_ENABLED] ?: true,
                flashlightEnabled = p[Keys.FLASHLIGHT_ENABLED] ?: true,
                missedCallNudgeEnabled = p[Keys.MISSED_NUDGE] ?: true,
                autoSyncEnabled = p[Keys.AUTO_SYNC] ?: true,
                privateMemoriesHidden = p[Keys.PRIVATE_HIDDEN] ?: true,
                lastSyncAt = p[Keys.LAST_SYNC_AT] ?: 0L,
                prayer = PrayerSettings(
                    enabled = p[Keys.PRAYER_ENABLED] ?: false,
                    mode = PrayerMode.from(p[Keys.PRAYER_MODE]),
                    method = PrayerMethod.from(p[Keys.PRAYER_METHOD]),
                    madhab = PrayerMadhab.from(p[Keys.PRAYER_MADHAB]),
                    // Stored as strings: DataStore has no double key, and a
                    // coordinate is exact data that must not be rounded.
                    latitude = p[Keys.PRAYER_LAT]?.toDoubleOrNull(),
                    longitude = p[Keys.PRAYER_LNG]?.toDoubleOrNull(),
                    locationLabel = p[Keys.PRAYER_LOCATION_LABEL].orEmpty(),
                    silenceMode = PrayerSilenceMode.from(p[Keys.PRAYER_SILENCE_MODE])
                )
            )
        }

    suspend fun setOnboardingComplete(value: Boolean) = put(Keys.ONBOARDING_COMPLETE, value)
    suspend fun setThemeMode(mode: ThemeMode) = put(Keys.THEME_MODE, mode.storageValue)
    suspend fun setDynamicColor(value: Boolean) = put(Keys.DYNAMIC_COLOR, value)
    suspend fun setPersonality(mode: PersonalityMode) = put(Keys.PERSONALITY, mode.storageValue)
    suspend fun setNotificationsEnabled(value: Boolean) = put(Keys.NOTIFICATIONS_ENABLED, value)
    suspend fun setFlashlightEnabled(value: Boolean) = put(Keys.FLASHLIGHT_ENABLED, value)
    suspend fun setMissedCallNudge(value: Boolean) = put(Keys.MISSED_NUDGE, value)
    suspend fun setAutoSync(value: Boolean) = put(Keys.AUTO_SYNC, value)
    suspend fun setPrivateMemoriesHidden(value: Boolean) = put(Keys.PRIVATE_HIDDEN, value)
    suspend fun setLastSyncAt(value: Long) = put(Keys.LAST_SYNC_AT, value)

    suspend fun setPrayerEnabled(value: Boolean) = put(Keys.PRAYER_ENABLED, value)
    suspend fun setPrayerMode(mode: PrayerMode) = put(Keys.PRAYER_MODE, mode.storageValue)
    suspend fun setPrayerMethod(method: PrayerMethod) = put(Keys.PRAYER_METHOD, method.storageValue)
    suspend fun setPrayerMadhab(madhab: PrayerMadhab) = put(Keys.PRAYER_MADHAB, madhab.storageValue)
    suspend fun setPrayerSilenceMode(mode: PrayerSilenceMode) =
        put(Keys.PRAYER_SILENCE_MODE, mode.storageValue)

    suspend fun setPrayerLocation(latitude: Double, longitude: Double, label: String) {
        context.dataStore.edit {
            it[Keys.PRAYER_LAT] = latitude.toString()
            it[Keys.PRAYER_LNG] = longitude.toString()
            it[Keys.PRAYER_LOCATION_LABEL] = label
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
