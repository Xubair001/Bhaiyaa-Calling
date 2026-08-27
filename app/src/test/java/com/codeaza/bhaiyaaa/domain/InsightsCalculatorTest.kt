package com.codeaza.bhaiyaaa.domain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.domain.usecase.InsightsCalculator
import com.codeaza.bhaiyaaa.util.PhoneNumbers
import com.codeaza.bhaiyaaa.util.TimeRanges
import com.google.common.truth.Truth.assertThat
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
 * Runs against a real Room database rather than a fake DAO, because the part
 * most likely to be wrong is the SQL itself - the day bucketing in particular.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InsightsCalculatorTest {

    private lateinit var db: AppDatabase
    private lateinit var calculator: InsightsCalculator
    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Karachi")

    /** Wednesday 27 August 2025, 14:00 local. */
    private val now: Long = Calendar.getInstance(zone).apply {
        clear()
        set(2025, Calendar.AUGUST, 27, 14, 0, 0)
    }.timeInMillis

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        calculator = InsightsCalculator(db.callRecordDao(), zone)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addCall(id: Long, number: String, type: String, at: Long, duration: Long = 0) {
        db.callRecordDao().insertIfAbsent(
            listOf(
                CallRecordEntity(
                    id = id,
                    phoneNumber = number,
                    matchKey = PhoneNumbers.matchKey(number),
                    contactName = "Person $number",
                    type = type,
                    timestamp = at,
                    durationSeconds = duration
                )
            )
        )
    }

    @Test
    fun `an empty call log reports empty rather than fabricating a chart`() = runTest {
        val insights = calculator.calculate(now)
        assertThat(insights.isEmpty).isTrue()
        assertThat(insights.callsToday).isEqualTo(0)
        assertThat(insights.last7Days.sumOf { it.callCount }).isEqualTo(0)
    }

    @Test
    fun `today's calls are counted against the local midnight boundary`() = runTest {
        val startOfDay = TimeRanges.startOfDay(now, zone)
        addCall(1, "+92300", "INCOMING", startOfDay + 60_000)
        // One minute before midnight is yesterday, not today.
        addCall(2, "+92301", "INCOMING", startOfDay - 60_000)

        val insights = calculator.calculate(now)
        assertThat(insights.callsToday).isEqualTo(1)
    }

    @Test
    fun `the chart always has exactly seven bars including quiet days`() = runTest {
        addCall(1, "+92300", "INCOMING", now - 1000)

        val insights = calculator.calculate(now)
        // A two-bar chart would read as a far busier week than it was.
        assertThat(insights.last7Days).hasSize(7)
        assertThat(insights.last7Days.count { it.callCount == 0 }).isEqualTo(6)
    }

    @Test
    fun `chart days are distinct and in ascending order`() = runTest {
        addCall(1, "+92300", "INCOMING", now - 1000)
        val days = calculator.calculate(now).last7Days.map { it.dayStartMillis }
        assertThat(days).isInOrder()
        assertThat(days.toSet()).hasSize(7)
    }

    @Test
    fun `calls are bucketed into the correct local day`() = runTest {
        val today = TimeRanges.startOfDay(now, zone)
        val yesterday = today - 24L * 60 * 60 * 1000
        addCall(1, "+92300", "INCOMING", today + 3_600_000)
        addCall(2, "+92301", "INCOMING", yesterday + 3_600_000)
        addCall(3, "+92302", "INCOMING", yesterday + 7_200_000)

        val chart = calculator.calculate(now).last7Days
        assertThat(chart.last().callCount).isEqualTo(1)
        assertThat(chart[chart.size - 2].callCount).isEqualTo(2)
    }

    @Test
    fun `incoming and outgoing are split correctly`() = runTest {
        val week = TimeRanges.startOfWeek(now, zone)
        addCall(1, "+92300", "INCOMING", week + 1000)
        addCall(2, "+92301", "INCOMING", week + 2000)
        addCall(3, "+92302", "OUTGOING", week + 3000)
        addCall(4, "+92303", "MISSED", week + 4000)

        val insights = calculator.calculate(now)
        assertThat(insights.incomingThisWeek).isEqualTo(2)
        assertThat(insights.outgoingThisWeek).isEqualTo(1)
        assertThat(insights.missedThisWeek).isEqualTo(1)
    }

    @Test
    fun `most contacted ranks by real call volume`() = runTest {
        val week = TimeRanges.startOfWeek(now, zone)
        repeat(3) { addCall(it + 1L, "+923001111111", "INCOMING", week + 1000L * it) }
        addCall(10, "+923002222222", "INCOMING", week + 5000)

        val top = calculator.calculate(now).mostContacted
        assertThat(top).isNotEmpty()
        assertThat(top.first().callCount).isEqualTo(3)
    }

    @Test
    fun `vip calls this week only counts contacts marked VIP`() = runTest {
        db.contactDao().insertIfAbsent(
            listOf(
                ContactEntity(
                    phoneNumber = "+923001111111",
                    matchKey = PhoneNumbers.matchKey("+923001111111"),
                    name = "Ahmed",
                    vipLevel = VipLevel.VIP.storageValue,
                    createdAt = now,
                    updatedAt = now
                )
            )
        )
        val week = TimeRanges.startOfWeek(now, zone)
        addCall(1, "+923001111111", "INCOMING", week + 1000)
        addCall(2, "+923009999999", "INCOMING", week + 2000)

        assertThat(calculator.calculate(now).vipCallsThisWeek).isEqualTo(1)
    }

    @Test
    fun `longest calls are ordered by duration and exclude unanswered ones`() = runTest {
        addCall(1, "+92300", "INCOMING", now - 5000, duration = 30)
        addCall(2, "+92301", "INCOMING", now - 4000, duration = 600)
        addCall(3, "+92302", "MISSED", now - 3000, duration = 0)

        val longest = calculator.calculate(now).longestCalls
        assertThat(longest.first().durationSeconds).isEqualTo(600)
        assertThat(longest.none { it.durationSeconds == 0L }).isTrue()
    }
}
