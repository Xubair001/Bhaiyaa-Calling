package com.codeaza.bhaiyaaa.prayer

import android.content.Context
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.domain.model.SilenceWindow
import kotlinx.coroutines.flow.first

/** Answers "is a prayer window running right now" for the incoming-call path. */
object PrayerSilence {

    /**
     * Checks the stored flag first, then recomputes as a fallback.
     *
     * The flag is what the start alarm set, and is the cheap answer. But an
     * alarm delayed by Doze - or dropped entirely by an aggressive OEM - would
     * leave the flag false during a window that has genuinely begun, and
     * Sukoon would then blare through someone's prayer. Recomputing costs one
     * small query and removes that failure.
     */
    suspend fun isActiveNow(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (SilenceController.isSilenceActive(context)) return true
        return currentWindow(context, now) != null
    }

    suspend fun currentWindow(
        context: Context,
        now: Long = System.currentTimeMillis()
    ): SilenceWindow? {
        val settings = SettingsRepository(context).settings.first().prayer
        val db = AppDatabase.getInstance(context)
        // Custom schedules run whether or not the prayer feature is on, so this
        // cannot short-circuit on settings.isUsable the way it used to.
        val windows = SilencePlan.windowsForDay(
            settings = settings,
            prayers = db.prayerDao().allOnce(),
            schedules = db.silenceScheduleDao().allOnce(),
            dayStartMillis = now,
            zone = settings.zone
        )
        return SilencePlan.activeWindow(windows, now)
    }
}
