package com.codeaza.bhaiyaaa.prayer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.domain.model.PrayerWindow
import com.codeaza.bhaiyaaa.service.PrayerAlarmReceiver
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.TimeZone

/**
 * Arms the alarms that start and end each prayer silence window.
 *
 * Prayer timing is one of the few places in this app where being a few minutes
 * late genuinely matters, so it asks for exact alarms and uses them when the
 * user has allowed it. When not allowed it still works, just approximately, and
 * the settings screen says so rather than pretending otherwise.
 *
 * Today and tomorrow are both scheduled. Alarms do not survive a reboot, and
 * the last window of the day re-arms the next one, so scheduling two days out
 * means an overnight gap can never leave the phone unscheduled.
 */
object PrayerScheduler {

    private const val REQUEST_BASE_START = 8000
    private const val REQUEST_BASE_END = 8500
    private const val REQUEST_TEST_END = 8999

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
        if (!settings.isUsable) {
            // Feature off, or automatic mode with no location yet. Make sure we
            // are not holding the phone silent on the way out.
            SilenceController.recoverIfStale(context, stillInsideWindow = false)
            return
        }

        val prayers = AppDatabase.getInstance(context).prayerDao().allOnce()
        if (prayers.isEmpty()) return

        val zone = TimeZone.getDefault()
        val windows = listOf(0L, 1L).flatMap { dayOffset ->
            val day = Calendar.getInstance(zone).apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, dayOffset.toInt())
            }.timeInMillis
            PrayerTimeCalculator.windowsForDay(settings, prayers, day, zone)
        }

        // If the process died mid-window, hand the phone back before re-arming.
        val inside = windows.any { it.containsNow(now) }
        SilenceController.recoverIfStale(context, stillInsideWindow = inside)

        windows.filter { it.enabled && it.endMillis > now }.forEach { window ->
            if (window.startMillis > now) schedule(context, window, start = true)
            schedule(context, window, start = false)
        }
    }

    private fun schedule(context: Context, window: PrayerWindow, start: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = if (start) window.startMillis else window.endMillis
        val pending = pendingIntent(context, window, start) ?: return

        try {
            if (canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                // Still fires in Doze, just not to the minute. Better late than
                // never, and the settings screen explains the difference.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (e: SecurityException) {
            // Exact-alarm permission revoked between the check and the call.
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }
    }

    /**
     * Arms a one-off exit for the "test silence" button.
     *
     * Goes through AlarmManager rather than a coroutine delay so the phone is
     * handed back even if the app is closed or killed in the meantime - being
     * left silent by a test would be worse than the bug it is checking for.
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
        runCatching {
            if (canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        com.codeaza.bhaiyaaa.domain.model.Prayer.entries.forEach { prayer ->
            listOf(true, false).forEach { start ->
                val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                    action = if (start) PrayerAlarmReceiver.ACTION_START else PrayerAlarmReceiver.ACTION_END
                    putExtra(PrayerAlarmReceiver.EXTRA_PRAYER, prayer.storageValue)
                }
                val code = requestCode(prayer.order, start)
                PendingIntent.getBroadcast(
                    context, code, intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )?.let { alarmManager.cancel(it) }
            }
        }
    }

    private fun pendingIntent(context: Context, window: PrayerWindow, start: Boolean): PendingIntent? {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = if (start) PrayerAlarmReceiver.ACTION_START else PrayerAlarmReceiver.ACTION_END
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER, window.prayer.storageValue)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(window.prayer.order, start),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(order: Int, start: Boolean): Int =
        (if (start) REQUEST_BASE_START else REQUEST_BASE_END) + order
}
