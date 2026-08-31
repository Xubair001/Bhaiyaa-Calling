package com.codeaza.bhaiyaaa.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.domain.model.SilenceWindow
import com.codeaza.bhaiyaaa.service.PrayerAlarmReceiver
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * Arms the alarms that open and close every quiet window - prayers and custom
 * schedules alike, since [SilencePlan] has already merged them.
 *
 * Timing is the whole point of this class, so it asks for exact alarms and uses
 * them whenever the platform allows. A window that opens two minutes late has
 * missed what it was for. Where exact alarms are refused it still works,
 * approximately, and the UI says so rather than appearing unreliable for a
 * reason the user cannot see.
 *
 * Today and tomorrow are both planned. Alarms do not survive a reboot, and the
 * last window of the day re-arms the next, so covering two days means an
 * overnight gap can never leave the phone unscheduled.
 */
object PrayerScheduler {

    private const val PREFS = "bhaiyaaa_alarm_codes"
    private const val KEY_ARMED = "armed_codes"
    private const val REQUEST_TEST_END = 8999

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True when the platform will honour an exact alarm from this app. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        return runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /** The system screen where exact alarms are granted. Android 12+ only. */
    fun exactAlarmSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(android.net.Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            null
        }

    /** Recomputes today's and tomorrow's windows and re-arms every alarm. */
    suspend fun reschedule(context: Context, now: Long = System.currentTimeMillis()) {
        cancelAll(context)

        val settings = SettingsRepository(context).settings.first().prayer
        val db = AppDatabase.getInstance(context)
        val prayers = db.prayerDao().allOnce()
        val schedules = db.silenceScheduleDao().allOnce()
        val zone = settings.zone

        val windows = listOf(0, 1).flatMap { dayOffset ->
            val day = Calendar.getInstance(zone).apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }.timeInMillis
            SilencePlan.windowsForDay(settings, prayers, schedules, day, zone)
        }

        val active = windows.firstOrNull { it.containsNow(now) }

        // If the process died mid-window, hand the phone back before re-arming.
        SilenceController.recoverIfStale(context, stillInsideWindow = active != null)

        val armed = mutableSetOf<String>()
        windows.filter { it.enabled && it.endMillis > now }.forEach { window ->
            // A window already under way has a start in the past, so only its
            // end is armed - the start is applied directly below instead.
            if (window.startMillis > now) {
                schedule(context, window, start = true)?.let { armed += it.toString() }
            }
            schedule(context, window, start = false)?.let { armed += it.toString() }
        }
        prefs(context).edit().putStringSet(KEY_ARMED, armed).apply()

        // A window that has already begun gets no start alarm, so it is applied
        // here. Setting a time to the current minute lands inside one
        // immediately, and so does any head start on a window starting soon.
        // Without this the app displayed an active window while the phone rang.
        if (active != null && !SilenceController.isSilenceActive(context)) {
            SilenceController.enterSilence(context, active.label, active.mode)
        }
    }

    /**
     * Arms a one-off exit, for the "test silence" button.
     *
     * Through AlarmManager rather than a coroutine delay so the phone is handed
     * back even if the app is closed or killed meanwhile - being left silent by
     * a test would be worse than the bug it is checking for.
     */
    fun scheduleSilenceEnd(context: Context, at: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = PrayerAlarmReceiver.ACTION_END
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER, "TEST")
        }
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_TEST_END, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fire(context, alarmManager, at, pending)
    }

    private fun schedule(context: Context, window: SilenceWindow, start: Boolean): Int? {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return null
        val code = requestCode(window.key, start)
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = if (start) PrayerAlarmReceiver.ACTION_START else PrayerAlarmReceiver.ACTION_END
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER, window.label)
            putExtra(PrayerAlarmReceiver.EXTRA_MODE, window.mode.storageValue)
        }
        val pending = PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fire(context, alarmManager, if (start) window.startMillis else window.endMillis, pending)
        return code
    }

    private fun fire(context: Context, alarmManager: AlarmManager, at: Long, pending: PendingIntent) {
        try {
            if (canScheduleExact(context)) {
                // setAlarmClock rather than setExactAndAllowWhileIdle: it is the
                // only tier the platform never defers, and it survives Doze and
                // battery saver. The cost is an alarm icon in the status bar,
                // which is a fair trade for a window that must open on the
                // second rather than "within a few minutes".
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at, null), pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call, or an OEM
            // refusing outright. Inexact is better than nothing.
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }
    }

    /**
     * Cancels everything previously armed.
     *
     * The codes are persisted because the set is no longer a fixed five: a
     * custom schedule the user has since deleted still has an alarm out there,
     * and iterating the current schedules would never find it.
     */
    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val armed = prefs(context).getStringSet(KEY_ARMED, emptySet()).orEmpty()
        armed.mapNotNull { it.toIntOrNull() }.forEach { code ->
            val intent = Intent(context, PrayerAlarmReceiver::class.java)
            PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { alarmManager.cancel(it) }
        }
        prefs(context).edit().remove(KEY_ARMED).apply()
    }

    /** Stable per window and edge, so re-arming replaces rather than duplicates. */
    private fun requestCode(key: String, start: Boolean): Int =
        (key.hashCode() and 0x0FFFFFFF) or (if (start) 0x40000000 else 0)
}
