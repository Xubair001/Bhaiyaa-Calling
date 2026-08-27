package com.codeaza.bhaiyaaa.service.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.data.repository.BhaiyaaaRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Keeps the local mirror of contacts and call history fresh.
 *
 * Periodic work rather than a foreground service or a polling loop: the sync is
 * cheap, incremental (only calls newer than the newest already stored), and the
 * system batches it with other work, which is the battery-friendly shape the
 * brief asks for in §29.
 */
class CallSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val settings = SettingsRepository(applicationContext).settings.first()
        if (!settings.autoSyncEnabled) {
            Result.success()
        } else {
            val repo = BhaiyaaaRepository(applicationContext)
            repo.syncFromDevice()
            SettingsRepository(applicationContext).setLastSyncAt(System.currentTimeMillis())
            Result.success()
        }
    } catch (e: Exception) {
        // Transient provider failures are common on OEM builds; retry rather
        // than giving up on syncing forever.
        if (runAttemptCount < 3) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "bhaiyaaa-call-sync"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<CallSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
