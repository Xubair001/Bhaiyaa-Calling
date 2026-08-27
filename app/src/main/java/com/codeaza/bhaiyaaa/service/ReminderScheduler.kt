package com.codeaza.bhaiyaaa.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.codeaza.bhaiyaaa.prayer.PrayerScheduler

/**
 * Schedules reminder alarms.
 *
 * Uses an exact alarm when the platform allows one. A reminder that arrives
 * several minutes after the time you asked for is not a reminder - it is a
 * notification about something you have already missed, which is exactly what
 * the inexact scheduling this used to rely on produced.
 *
 * When exact alarms are not permitted it still fires, just approximately, and
 * Settings says so rather than letting the app appear unreliable for a reason
 * the user cannot see.
 */
object ReminderScheduler {

    fun schedule(context: Context, reminderId: Long, dueAt: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(context, reminderId) ?: return
        try {
            if (PrayerScheduler.canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pending)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pending)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, dueAt, pending)
            }
        } catch (e: SecurityException) {
            // Exact-alarm permission revoked between the check and the call, or
            // an OEM restricting alarms for background apps. Fall back rather
            // than losing the reminder entirely.
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pending)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, dueAt, pending)
                }
            }
        }
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        pendingIntent(context, reminderId)?.let { alarmManager.cancel(it) }
    }

    private fun pendingIntent(context: Context, reminderId: Long): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_FIRE
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
