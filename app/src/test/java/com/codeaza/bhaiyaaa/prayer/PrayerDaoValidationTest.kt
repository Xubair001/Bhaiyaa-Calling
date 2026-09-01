package com.codeaza.bhaiyaaa.prayer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The AM/PM rule at the persistence boundary.
 *
 * The picker cannot express an invalid time - but a picker can be bypassed, by
 * an import, by the assistant, by whatever gets added next. The brief was
 * explicit that this must not rely on the front end alone, so the rule lives
 * in the DAO and this is the test that it holds there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrayerDaoValidationTest {

    private lateinit var db: AppDatabase

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

    @Test
    fun `an afternoon time offered for Fajr is stored as the morning time meant`() = runTest {
        db.prayerDao().setManualTime(Prayer.FAJR, 17 * 60)

        assertThat(db.prayerDao().find(Prayer.FAJR.storageValue)?.manualMinutesFromMidnight)
            .isEqualTo(5 * 60)
    }

    @Test
    fun `a morning time offered for an afternoon prayer is corrected`() = runTest {
        db.prayerDao().setManualTime(Prayer.ASR, 4 * 60 + 30)

        assertThat(db.prayerDao().find(Prayer.ASR.storageValue)?.manualMinutesFromMidnight)
            .isEqualTo(16 * 60 + 30)
    }

    @Test
    fun `no prayer can ever hold a time outside its own half of the clock`() = runTest {
        // Every minute of the day, offered to every prayer.
        Prayer.entries.forEach { prayer ->
            (0 until 24 * 60 step 13).forEach { minutes ->
                db.prayerDao().setManualTime(prayer, minutes)
                val stored = requireNotNull(
                    db.prayerDao().find(prayer.storageValue)?.manualMinutesFromMidnight
                )
                assertThat(prayer.isValidTime(stored)).isTrue()
            }
        }
    }

    @Test
    fun `clearing a time stays cleared rather than being normalised to something`() = runTest {
        db.prayerDao().setManualTime(Prayer.MAGHRIB, 19 * 60)
        db.prayerDao().setManualTime(Prayer.MAGHRIB, null)

        assertThat(db.prayerDao().find(Prayer.MAGHRIB.storageValue)?.manualMinutesFromMidnight)
            .isNull()
    }

    @Test
    fun `saveEdit writes the time, the length and the offset together`() = runTest {
        db.prayerDao().saveEdit(
            prayer = Prayer.ISHA,
            minutesFromMidnight = 20 * 60 + 15,
            silenceMinutes = 25,
            startOffsetMinutes = -7
        )

        val row = requireNotNull(db.prayerDao().find(Prayer.ISHA.storageValue))
        assertThat(row.manualMinutesFromMidnight).isEqualTo(20 * 60 + 15)
        assertThat(row.silenceMinutes).isEqualTo(25)
        assertThat(row.startOffsetMinutes).isEqualTo(-7)
    }

    @Test
    fun `saveEdit normalises the time and clamps the rest`() = runTest {
        db.prayerDao().saveEdit(
            prayer = Prayer.DHUHR,
            // All three out of range: an AM time for a PM prayer, a silence
            // longer than any window should be, an absurd head start.
            minutesFromMidnight = 30,
            silenceMinutes = 10_000,
            startOffsetMinutes = -5_000
        )

        val row = requireNotNull(db.prayerDao().find(Prayer.DHUHR.storageValue))
        assertThat(row.manualMinutesFromMidnight).isEqualTo(12 * 60 + 30)
        assertThat(row.silenceMinutes).isEqualTo(180)
        assertThat(row.startOffsetMinutes).isEqualTo(-60)
    }

    @Test
    fun `saveEdit leaves everything else on the row alone`() = runTest {
        db.prayerDao().setEnabled(Prayer.ASR.storageValue, false)

        db.prayerDao().saveEdit(Prayer.ASR, 16 * 60, 20, -5)

        val row = requireNotNull(db.prayerDao().find(Prayer.ASR.storageValue))
        assertThat(row.enabled).isFalse()
        assertThat(row.sortOrder).isEqualTo(Prayer.ASR.order)
    }
}
