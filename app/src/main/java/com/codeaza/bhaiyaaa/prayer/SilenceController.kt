package com.codeaza.bhaiyaaa.prayer

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.codeaza.bhaiyaaa.domain.model.PrayerSilenceMode

/**
 * Turns the phone quiet for a prayer window and puts it back afterwards.
 *
 * Uses Do Not Disturb rather than forcing the ringer to silent. Two reasons:
 * DND is a mode the platform already understands and shows in the status bar,
 * so the user is never confused about why their phone is quiet; and it is
 * cleanly reversible, where stamping on the ringer mode fights whatever the
 * user had set.
 *
 * INTERRUPTION_FILTER_ALARMS specifically, not NONE. Alarms still need to fire
 * during a prayer window - silencing someone's alarm clock because they were
 * praying would be a genuinely harmful bug. It also leaves the alarm stream
 * usable, which is how a tier marked "rings during prayer" still gets through.
 *
 * The previous filter is written to disk before changing anything, because the
 * process can be killed between the start alarm and the end alarm, and an app
 * that leaves someone's phone silent is worse than one that never silenced it.
 */
object SilenceController {

    private const val PREFS = "bhaiyaaa_silence_state"
    private const val KEY_PREVIOUS_FILTER = "previous_filter"
    private const val KEY_PREVIOUS_RINGER = "previous_ringer"
    private const val KEY_ACTIVE = "silence_active"
    private const val KEY_ACTIVE_PRAYER = "active_prayer"
    private const val TAG = "BhaiyaaaSilence"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasDndAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { manager.isNotificationPolicyAccessGranted }.getOrDefault(false)
    }

    fun isSilenceActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ACTIVE, false)

    fun activePrayerName(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE_PRAYER, null)

    /**
     * @param mode SILENT uses Do Not Disturb; VIBRATE uses the ringer.
     *
     * They need different mechanisms. DND's alarms-only filter suppresses
     * vibration along with sound, so asking for "vibrate only" through DND
     * produces a phone that does nothing at all - which is exactly what
     * happened. Vibrate is therefore done with the ringer mode and the DND
     * filter is left alone.
     *
     * @return false when DND access is missing, so the caller can say so.
     */
    fun enterSilence(
        context: Context,
        prayerName: String,
        mode: PrayerSilenceMode = PrayerSilenceMode.SILENT
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false

        // Only SILENT needs policy access: setting RINGER_MODE_SILENT and
        // changing the DND filter are both gated on it, but switching the
        // ringer to VIBRATE is not. Gating both meant vibrate-only failed for
        // anyone who had not granted DND access, despite not needing it.
        if (mode == PrayerSilenceMode.SILENT && !hasDndAccess(context)) return false

        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        return runCatching {
            // Remember what to go back to *before* changing anything. On a
            // repeat call keep the originally saved values rather than saving
            // our own state over them.
            if (!isSilenceActive(context)) {
                prefs(context).edit()
                    .putInt(KEY_PREVIOUS_FILTER, manager.currentInterruptionFilter)
                    .putInt(KEY_PREVIOUS_RINGER, audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL)
                    .apply()
            }

            when (mode) {
                PrayerSilenceMode.SILENT -> {
                    manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                    runCatching { audio?.ringerMode = AudioManager.RINGER_MODE_SILENT }
                }
                PrayerSilenceMode.VIBRATE -> {
                    // Deliberately not touching the DND filter: turning it on
                    // would suppress the very vibration being asked for.
                    runCatching { audio?.ringerMode = AudioManager.RINGER_MODE_VIBRATE }
                }
            }

            prefs(context).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString(KEY_ACTIVE_PRAYER, prayerName)
                .apply()
            true
        }.getOrElse {
            Log.w(TAG, "Could not enter silence: ${it.javaClass.simpleName}")
            false
        }
    }

    /** Restores the filter that was in force before the window began. */
    fun exitSilence(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val store = prefs(context)
        if (!store.getBoolean(KEY_ACTIVE, false)) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false

        return runCatching {
            val previousFilter = store.getInt(
                KEY_PREVIOUS_FILTER,
                NotificationManager.INTERRUPTION_FILTER_ALL
            )
            val previousRinger = store.getInt(
                KEY_PREVIOUS_RINGER,
                AudioManager.RINGER_MODE_NORMAL
            )
            // The ringer is restored regardless: leaving the phone on vibrate
            // after a prayer is the failure that actually costs the user a call,
            // and restoring it needs no policy access.
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            runCatching { audio?.ringerMode = previousRinger }
            if (hasDndAccess(context)) {
                runCatching { manager.setInterruptionFilter(previousFilter) }
            }
            store.edit()
                .putBoolean(KEY_ACTIVE, false)
                .remove(KEY_ACTIVE_PRAYER)
                .apply()
            true
        }.getOrElse {
            Log.w(TAG, "Could not exit silence: ${it.javaClass.simpleName}")
            false
        }
    }

    /**
     * Why [enterSilence] could not run, for the UI to explain. Null when fine.
     */
    fun blockedReason(context: Context, mode: PrayerSilenceMode): String? = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ->
            "This Android version is too old for automatic silencing."
        mode == PrayerSilenceMode.SILENT && !hasDndAccess(context) ->
            "Silent mode needs Do Not Disturb access. Grant it, or switch to Vibrate only."
        else -> null
    }

    /**
     * Called on launch and after a reboot.
     *
     * If the process died mid-window the end alarm may never have run, so a
     * stale "silence active" flag is cleared and the phone handed back. Being
     * left permanently silent is the worst failure this feature has.
     */
    fun recoverIfStale(context: Context, stillInsideWindow: Boolean) {
        if (isSilenceActive(context) && !stillInsideWindow) {
            exitSilence(context)
        }
    }
}
