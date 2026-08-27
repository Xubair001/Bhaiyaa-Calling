package com.codeaza.bhaiyaaa

import android.app.Application
import androidx.work.Configuration
import com.codeaza.bhaiyaaa.ai.model.ModelCatalog
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.repository.BhaiyaaaRepository
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.notifications.NotificationChannels
import com.codeaza.bhaiyaaa.prayer.PrayerScheduler
import com.codeaza.bhaiyaaa.service.work.CallSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BhaiyaaaApplication : Application(), Configuration.Provider {

    /** Application-scoped: outlives any screen, cancelled only with the process. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.INFO else android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Created immediately so a call arriving seconds after launch has a
        // channel to post on. Passing no map means existing channels keep the
        // Do Not Disturb setting they already have rather than being reset.
        NotificationChannels.createAll(this)

        // Seeding touches the database, so it stays off the main thread. None of
        // it blocks first paint: the UI renders from empty Flows until it lands.
        appScope.launch {
            runCatching {
                val repo = BhaiyaaaRepository(applicationContext)
                repo.seedDefaults()
                // Register the model catalogue as NOT_INSTALLED rows. Listing a
                // model is not downloading it - nothing is fetched until the
                // user taps download in Settings -> AI Models.
                AppDatabase.getInstance(applicationContext).aiModelDao()
                    .insertIfAbsent(ModelCatalog.seedRows(System.currentTimeMillis()))
                // Reconcile the channels against the stored preference. The
                // database is the source of truth: channels can be reset by a
                // reinstall or cleared data, and this puts the user's choice
                // back without them having to notice it went missing.
                val rules = AppDatabase.getInstance(applicationContext)
                    .notificationRuleDao()
                    .allOnce()
                rules.forEach { rule ->
                    if (rule.bypassDnd) {
                        NotificationChannels.setBypassDnd(
                            applicationContext,
                            VipLevel.from(rule.vipLevel),
                            true
                        )
                    }
                }

                // Re-arms today's and tomorrow's prayer windows, and hands the
                // phone back if the process died mid-window.
                PrayerScheduler.reschedule(applicationContext)

                CallSyncWorker.enqueuePeriodic(applicationContext)
            }
        }
    }
}
