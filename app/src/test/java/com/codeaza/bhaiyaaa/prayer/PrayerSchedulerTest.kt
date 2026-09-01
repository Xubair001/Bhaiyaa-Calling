package com.codeaza.bhaiyaaa.prayer

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.domain.model.PrayerMode
import com.codeaza.bhaiyaaa.domain.model.PrayerSilenceMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/**
 * Whether a prayer window actually takes effect, as opposed to merely being
 * displayed.
 *
 * The bug these exist for: a window whose start is already in the past gets no
 * start alarm, because there is no point scheduling one for a moment that has
 * been and gone. Nothing then applied it, so the app showed "phone is quiet"
 * while the phone rang - the card computes the window, but only the alarm
 * silences anything. Setting a prayer to the current time hits this every
 * single time, and so does the default three-minute head start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrayerSchedulerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase

    private val zone: TimeZone = TimeZone.getDefault()

    /**
     * A fixed "now", at one o'clock this afternoon.
     *
     * These tests used to run against the wall clock, which made them depend on
     * the hour CI happened to start. That was already fragile across midnight,
     * and it became untenable once a prayer could only hold a time in its own
     * half of the clock: a suite running at 09:00 would have been asking for
     * Dhuhr in the morning. Pinning the clock fixes both, and PrayerScheduler
     * already takes `now` as a parameter precisely so it can be pinned.
     */
    private val now: Long = Calendar.getInstance(zone).apply {
        set(Calendar.HOUR_OF_DAY, 13)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Before
    fun setUp(): Unit = runBlocking {
        db = AppDatabase.getInstance(context)
        db.prayerDao().insertIfAbsent(PrayerTimeCalculator.defaultPrayerRows())
        // Every prayer off by default, so each test enables only what it needs.
        Prayer.entries.forEach { db.prayerDao().setEnabled(it.storageValue, false) }

        shadowOf(context.getSystemService(NotificationManager::class.java))
            .setNotificationPolicyAccessGranted(true)
        SilenceController.exitSilence(context)

        SettingsRepository(context).apply {
            setPrayerEnabled(true)
            setPrayerMode(PrayerMode.MANUAL)
            setPrayerSilenceMode(PrayerSilenceMode.SILENT)
        }
    }

    @After
    fun tearDown(): Unit = runBlocking {
        SilenceController.exitSilence(context)
        PrayerScheduler.cancelAll(context)
        SettingsRepository(context).setPrayerEnabled(false)
    }

    /** Minutes past local midnight for an instant [offsetMinutes] from [now]. */
    private fun minutesFromMidnight(offsetMinutes: Int): Int {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = now + offsetMinutes * 60_000L
        }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private suspend fun configure(prayer: Prayer, atOffsetMinutes: Int, silence: Int, early: Int) {
        db.prayerDao().setEnabled(prayer.storageValue, true)
        db.prayerDao().setManualTime(prayer, minutesFromMidnight(atOffsetMinutes))
        db.prayerDao().setSilenceMinutes(prayer.storageValue, silence)
        val row = requireNotNull(db.prayerDao().find(prayer.storageValue))
        db.prayerDao().upsert(row.copy(startOffsetMinutes = -early))
    }

    @Test
    fun `a window already under way is applied immediately`() = runTest {
        // Prayer five minutes ago, quiet for thirty: we are inside it right now.
        configure(Prayer.DHUHR, atOffsetMinutes = -5, silence = 30, early = 3)

        PrayerScheduler.reschedule(context, now)

        // The start alarm is in the past and was never armed, so this only
        // passes if reschedule applies a running window itself.
        assertThat(SilenceController.isSilenceActive(context)).isTrue()
        assertThat(SilenceController.activeWindowLabel(context)).isEqualTo(Prayer.DHUHR.label)
    }

    @Test
    fun `setting a prayer to right now silences right now`() = runTest {
        // The exact case reported: set the time to the current minute.
        configure(Prayer.ASR, atOffsetMinutes = 0, silence = 15, early = 3)

        PrayerScheduler.reschedule(context, now)

        assertThat(SilenceController.isSilenceActive(context)).isTrue()
    }

    @Test
    fun `the three minute head start alone puts us inside the window`() = runTest {
        // Prayer one minute ahead, but quiet from three minutes before it - so
        // the window opened two minutes ago even though the prayer has not
        // arrived. Nothing would have applied it before.
        configure(Prayer.MAGHRIB, atOffsetMinutes = 1, silence = 15, early = 3)

        PrayerScheduler.reschedule(context, now)

        assertThat(SilenceController.isSilenceActive(context)).isTrue()
    }

    @Test
    fun `a window still ahead arms an alarm and does not silence yet`() = runTest {
        configure(Prayer.ISHA, atOffsetMinutes = 30, silence = 15, early = 3)

        PrayerScheduler.reschedule(context, now)

        // Not silenced now...
        assertThat(SilenceController.isSilenceActive(context)).isFalse()
        // ...but armed for later.
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms
        assertThat(alarms).isNotEmpty()
    }

    @Test
    fun `a window that has finished neither silences nor stays silent`() = runTest {
        // Prayer an hour ago, quiet for fifteen minutes: long over.
        configure(Prayer.MAGHRIB, atOffsetMinutes = -60, silence = 15, early = 3)

        PrayerScheduler.reschedule(context, now)

        assertThat(SilenceController.isSilenceActive(context)).isFalse()
    }

    @Test
    fun `a disabled prayer is never applied even when its time is now`() = runTest {
        configure(Prayer.DHUHR, atOffsetMinutes = -2, silence = 15, early = 3)
        db.prayerDao().setEnabled(Prayer.DHUHR.storageValue, false)

        PrayerScheduler.reschedule(context, now)

        assertThat(SilenceController.isSilenceActive(context)).isFalse()
    }

    @Test
    fun `switching the feature off releases a running window`() = runTest {
        configure(Prayer.DHUHR, atOffsetMinutes = -2, silence = 20, early = 3)
        PrayerScheduler.reschedule(context, now)
        assertThat(SilenceController.isSilenceActive(context)).isTrue()

        SettingsRepository(context).setPrayerEnabled(false)
        PrayerScheduler.reschedule(context, now)

        // Turning it off must hand the phone back, not leave it silent.
        assertThat(SilenceController.isSilenceActive(context)).isFalse()
    }

    @Test
    fun `rescheduling twice inside a window does not double-apply`() = runTest {
        configure(Prayer.ASR, atOffsetMinutes = -1, silence = 20, early = 3)

        PrayerScheduler.reschedule(context, now)
        val firstPrayer = SilenceController.activeWindowLabel(context)
        PrayerScheduler.reschedule(context, now)

        assertThat(SilenceController.isSilenceActive(context)).isTrue()
        assertThat(SilenceController.activeWindowLabel(context)).isEqualTo(firstPrayer)
    }
}
