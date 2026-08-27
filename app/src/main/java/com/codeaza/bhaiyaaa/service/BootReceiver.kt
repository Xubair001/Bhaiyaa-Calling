package com.codeaza.bhaiyaaa.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.service.work.CallSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Alarms do not survive a reboot, so every still-pending reminder is re-armed
 * here. Without this, a reminder set before a restart would simply never fire.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000L) {
                    val now = System.currentTimeMillis()
                    val dao = AppDatabase.getInstance(appContext).reminderDao()
                    dao.pendingScheduled().forEach { reminder ->
                        val dueAt = reminder.dueAt ?: return@forEach
                        if (dueAt > now) {
                            ReminderScheduler.schedule(appContext, reminder.id, dueAt)
                        } else {
                            // Its moment passed while the phone was off - deliver
                            // it now rather than dropping it silently.
                            com.codeaza.bhaiyaaa.notifications.Notifier
                                .notifyReminder(appContext, reminder.id, reminder.text)
                            dao.markNotified(reminder.id)
                        }
                    }
                    // Alarms do not survive a reboot; without this a prayer
                    // window set before the restart would never fire.
                    com.codeaza.bhaiyaaa.prayer.PrayerScheduler.reschedule(appContext)
                    CallSyncWorker.enqueuePeriodic(appContext)
                }
            } catch (e: Exception) {
                // Boot broadcasts must never crash.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
