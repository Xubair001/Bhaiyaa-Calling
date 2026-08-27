package com.codeaza.bhaiyaaa.prayer

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

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

    /** @return false when DND access is missing, so the caller can say so. */
    fun enterSilence(context: Context, prayerName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (!hasDndAccess(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false

        return runCatching {
            // Remember what to go back to *before* changing it. If this is a
            // repeat call, keep the originally saved value rather than saving
            // our own filter over it.
            if (!isSilenceActive(context)) {
                prefs(context).edit()
                    .putInt(KEY_PREVIOUS_FILTER, manager.currentInterruptionFilter)
                    .apply()
            }
            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
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
            val previous = store.getInt(
                KEY_PREVIOUS_FILTER,
                NotificationManager.INTERRUPTION_FILTER_ALL
            )
            if (hasDndAccess(context)) {
                manager.setInterruptionFilter(previous)
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
