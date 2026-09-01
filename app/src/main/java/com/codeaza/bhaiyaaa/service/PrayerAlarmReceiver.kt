package com.codeaza.bhaiyaaa.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.domain.model.PrayerSilenceMode
import com.codeaza.bhaiyaaa.notifications.Notifier
import com.codeaza.bhaiyaaa.prayer.PrayerScheduler
import com.codeaza.bhaiyaaa.prayer.SilenceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Starts and ends a prayer silence window, and sounds the adhan. */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_START && action != ACTION_END && action != ACTION_ADHAN) return

        val label = intent.getStringExtra(EXTRA_PRAYER).orEmpty().ifBlank { "Quiet time" }
        // The mode travels with the alarm: a custom schedule can ask for vibrate
        // while prayers stay silent, so reading a single global setting here
        // would apply the wrong one.
        val mode = intent.getStringExtra(EXTRA_MODE)
        val prayerKey = intent.getStringExtra(EXTRA_PRAYER_KEY)
        val appContext = context.applicationContext

        if (action == ACTION_ADHAN) {
            // Handed straight to a foreground service rather than played here.
            // goAsync() holds a broadcast up for about ten seconds; an adhan
            // outlasts that, and a killed process mid-adhan is exactly the
            // failure the service exists to prevent. Every check on whether it
            // should sound at all happens inside the service, at the moment of
            // playing - see AdhanPlayback.
            if (prayerKey != null) {
                val prayer = Prayer.from(prayerKey)
                val started = AdhanService.play(appContext, prayer, System.currentTimeMillis())
                if (!started) {
                    // The platform refused to start a foreground service, which
                    // happens on Android 12+ when exact alarms are not granted
                    // and the alarm was therefore inexact. Silence would leave
                    // the user with nothing; a quiet card at least says the
                    // prayer has come in, and Settings already explains what
                    // granting exact alarms fixes.
                    Notifier.notifyPrayerTime(appContext, prayer, soundLabel = "")
                }
            }
            return
        }

        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000L) {
                    if (action == ACTION_START) {
                        val resolved = mode?.let { PrayerSilenceMode.from(it) }
                            ?: SettingsRepository(appContext).settings.first().prayer.silenceMode
                        SilenceController.enterSilence(appContext, label, resolved)
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
        const val ACTION_ADHAN = "com.codeaza.bhaiyaaa.action.PRAYER_ADHAN"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_MODE = "mode"
        const val EXTRA_PRAYER_KEY = "prayer_key"
    }
}
