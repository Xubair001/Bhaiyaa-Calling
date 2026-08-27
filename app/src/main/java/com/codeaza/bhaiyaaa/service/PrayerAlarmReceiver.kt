package com.codeaza.bhaiyaaa.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.prayer.PrayerScheduler
import com.codeaza.bhaiyaaa.prayer.SilenceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Starts and ends a prayer silence window. */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_START && action != ACTION_END) return

        val prayer = Prayer.from(intent.getStringExtra(EXTRA_PRAYER))
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000L) {
                    if (action == ACTION_START) {
                        val mode = SettingsRepository(appContext).settings.first().prayer.silenceMode
                        SilenceController.enterSilence(appContext, prayer.storageValue, mode)
                    } else {
                        SilenceController.exitSilence(appContext)
                        // The last window of the day re-arms tomorrow's, so the
                        // schedule never runs dry without the app being opened.
                        PrayerScheduler.reschedule(appContext)
                    }
                }
            } catch (e: Exception) {
                // An alarm broadcast must never crash. Worst case the phone
                // stays as it was and the next window recovers.
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_START = "com.codeaza.bhaiyaaa.action.PRAYER_SILENCE_START"
        const val ACTION_END = "com.codeaza.bhaiyaaa.action.PRAYER_SILENCE_END"
        const val EXTRA_PRAYER = "prayer"
    }
}
