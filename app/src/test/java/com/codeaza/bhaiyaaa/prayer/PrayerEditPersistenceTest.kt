package com.codeaza.bhaiyaaa.prayer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.domain.model.PrayerMadhab
import com.codeaza.bhaiyaaa.domain.model.PrayerMethod
import com.codeaza.bhaiyaaa.domain.model.PrayerMode
import com.codeaza.bhaiyaaa.domain.model.PrayerSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/**
 * The full "set my own time" path: write through the DAO exactly as the editor
 * does, read it back, and recompute the window the scheduler would use.
 *
 * Separate from PrayerTimeCalculatorTest, which feeds the calculator rows
 * directly. This one goes through storage, so it catches the case where a time
 * appears to save but never reaches the calculation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrayerEditPersistenceTest {

    private lateinit var db: AppDatabase
    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Karachi")

    private val day: Long = Calendar.getInstance(zone).apply {
        clear()
        set(2025, Calendar.AUGUST, 27, 8, 0, 0)
    }.timeInMillis

    private val automatic = PrayerSettings(
        enabled = true,
        mode = PrayerMode.AUTOMATIC,
        method = PrayerMethod.KARACHI,
        madhab = PrayerMadhab.HANAFI,
        latitude = 31.5204,
        longitude = 74.3587,
        locationLabel = "Lahore"
    )

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        db.prayerDao().insertIfAbsent(PrayerTimeCalculator.defaultPrayerRows())
    }

    @After
    fun tearDown() = db.close()

    private fun hourMinute(millis: Long): Pair<Int, Int> =
        Calendar.getInstance(zone).apply { timeInMillis = millis }
            .let { it.get(Calendar.HOUR_OF_DAY) to it.get(Calendar.MINUTE) }

    @Test
    fun `setting a manual time persists to the database`() = runTest {
        db.prayerDao().setManualTime(Prayer.DHUHR.storageValue, 12 * 60 + 30)

        val row = db.prayerDao().find(Prayer.DHUHR.storageValue)
        assertThat(row?.manualMinutesFromMidnight).isEqualTo(750)
    }

    @Test
    fun `a saved manual time reaches the computed window`() = runTest {
        db.prayerDao().setManualTime(Prayer.DHUHR.storageValue, 12 * 60 + 30)

        val windows = PrayerTimeCalculator.windowsForDay(
            automatic, db.prayerDao().allOnce(), day, zone
        )
        val dhuhr = windows.first { it.prayer == Prayer.DHUHR }

        assertThat(hourMinute(dhuhr.prayerTimeMillis)).isEqualTo(12 to 30)
        assertThat(dhuhr.isOverridden).isTrue()
    }

    @Test
    fun `each prayer can hold its own separate time`() = runTest {
        val wanted = mapOf(
            Prayer.FAJR to (4 * 60 + 45),
            Prayer.DHUHR to (12 * 60 + 30),
            Prayer.ASR to (16 * 60 + 15),
            Prayer.MAGHRIB to (18 * 60 + 55),
            Prayer.ISHA to (20 * 60 + 20)
        )
        wanted.forEach { (p, m) -> db.prayerDao().setManualTime(p.storageValue, m) }

        val windows = PrayerTimeCalculator.windowsForDay(
            automatic.copy(mode = PrayerMode.MANUAL), db.prayerDao().allOnce(), day, zone
        ).associateBy { it.prayer }

        wanted.forEach { (p, m) ->
            assertThat(hourMinute(windows.getValue(p).prayerTimeMillis))
                .isEqualTo((m / 60) to (m % 60))
        }
    }

    @Test
    fun `editing a time a second time replaces the first`() = runTest {
        db.prayerDao().setManualTime(Prayer.ASR.storageValue, 16 * 60)
        db.prayerDao().setManualTime(Prayer.ASR.storageValue, 17 * 60 + 5)

        val windows = PrayerTimeCalculator.windowsForDay(
            automatic, db.prayerDao().allOnce(), day, zone
        )
        assertThat(hourMinute(windows.first { it.prayer == Prayer.ASR }.prayerTimeMillis))
            .isEqualTo(17 to 5)
    }

    @Test
    fun `clearing an override returns that prayer to the calculation`() = runTest {
        val calculated = PrayerTimeCalculator.windowsForDay(
            automatic, db.prayerDao().allOnce(), day, zone
        ).first { it.prayer == Prayer.MAGHRIB }.prayerTimeMillis

        db.prayerDao().setManualTime(Prayer.MAGHRIB.storageValue, 19 * 60)
        db.prayerDao().setManualTime(Prayer.MAGHRIB.storageValue, null)

        val after = PrayerTimeCalculator.windowsForDay(
            automatic, db.prayerDao().allOnce(), day, zone
        ).first { it.prayer == Prayer.MAGHRIB }
        assertThat(after.prayerTimeMillis).isEqualTo(calculated)
        assertThat(after.isOverridden).isFalse()
    }

    @Test
    fun `editing the silence length and offset persists and shifts the window`() = runTest {
        db.prayerDao().setManualTime(Prayer.ISHA.storageValue, 20 * 60)
        db.prayerDao().setSilenceMinutes(Prayer.ISHA.storageValue, 30)
        val row = requireNotNull(db.prayerDao().find(Prayer.ISHA.storageValue))
        db.prayerDao().upsert(row.copy(startOffsetMinutes = -10))

        val isha = PrayerTimeCalculator.windowsForDay(
            automatic, db.prayerDao().allOnce(), day, zone
        ).first { it.prayer == Prayer.ISHA }

        assertThat(hourMinute(isha.prayerTimeMillis)).isEqualTo(20 to 0)
        assertThat(hourMinute(isha.startMillis)).isEqualTo(19 to 50)
        assertThat(isha.endMillis - isha.startMillis).isEqualTo(30 * 60_000L)
    }

    @Test
    fun `disabling one prayer does not disturb the others`() = runTest {
        db.prayerDao().setEnabled(Prayer.FAJR.storageValue, false)

        val windows = PrayerTimeCalculator.windowsForDay(
            automatic, db.prayerDao().allOnce(), day, zone
        ).associateBy { it.prayer }

        assertThat(windows.getValue(Prayer.FAJR).enabled).isFalse()
        assertThat(windows.getValue(Prayer.DHUHR).enabled).isTrue()
    }

    @Test
    fun `only Fajr defaults to a morning time`() {
        // Where the editor opens when nothing is set. Everything after Fajr is
        // an afternoon or evening prayer, and opening those at 12 AM invites a
        // time set twelve hours out.
        assertThat(Prayer.FAJR.defaultsToMorning).isTrue()
        listOf(Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA).forEach {
            assertThat(it.defaultsToMorning).isFalse()
        }
    }

    @Test
    fun `picker defaults are real times in the right order`() {
        val defaults = Prayer.entries.map { it.defaultClockMinutes }
        // Each within a day, and ascending like the prayers themselves.
        assertThat(defaults.all { it in 0..1439 }).isTrue()
        assertThat(defaults).isInOrder()
    }

    @Test
    fun `defaults are only a starting position and are never stored`() = runTest {
        // A freshly seeded prayer must still read as "no time set", so the
        // calculated time keeps winning until the user actually picks one.
        val row = db.prayerDao().find(Prayer.MAGHRIB.storageValue)
        assertThat(row?.manualMinutesFromMidnight).isNull()

        val maghrib = PrayerTimeCalculator.windowsForDay(
            automatic, db.prayerDao().allOnce(), day, zone
        ).first { it.prayer == Prayer.MAGHRIB }
        assertThat(maghrib.isOverridden).isFalse()
    }

    @Test
    fun `a time typed at midnight is stored, not treated as unset`() = runTest {
        // 00:00 is minute zero, which must not be confused with "no override".
        db.prayerDao().setManualTime(Prayer.FAJR.storageValue, 0)

        val row = db.prayerDao().find(Prayer.FAJR.storageValue)
        assertThat(row?.manualMinutesFromMidnight).isEqualTo(0)

        val fajr = PrayerTimeCalculator.windowsForDay(
            automatic, db.prayerDao().allOnce(), day, zone
        ).first { it.prayer == Prayer.FAJR }
        assertThat(fajr.isOverridden).isTrue()
        assertThat(hourMinute(fajr.prayerTimeMillis)).isEqualTo(0 to 0)
    }
}
