package com.codeaza.bhaiyaaa.domain.usecase

import com.codeaza.bhaiyaaa.data.db.dao.CallRecordDao
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.projection.ContactCallCount
import com.codeaza.bhaiyaaa.data.db.projection.DailyCallCount
import com.codeaza.bhaiyaaa.data.db.projection.HourlyCallCount
import com.codeaza.bhaiyaaa.util.TimeRanges
import java.util.TimeZone

/**
 * Every number on the Insights screen, computed from the user's real call log.
 *
 * Nothing here is sampled, estimated or seeded with demo data - if the call log
 * is empty every figure is genuinely zero, and the screen says so rather than
 * showing a plausible-looking chart.
 */
data class CallInsights(
    val callsToday: Int = 0,
    val callsThisWeek: Int = 0,
    val missedThisWeek: Int = 0,
    val missedToday: Int = 0,
    val vipCallsThisWeek: Int = 0,
    val incomingThisWeek: Int = 0,
    val outgoingThisWeek: Int = 0,
    val mostContacted: List<ContactCallCount> = emptyList(),
    val longestCalls: List<CallRecordEntity> = emptyList(),
    val busiestHours: List<HourlyCallCount> = emptyList(),
    val last7Days: List<DailyCallCount> = emptyList()
) {
    /** True when there is genuinely nothing to show, so the UI can show an empty state. */
    val isEmpty: Boolean
        get() = callsThisWeek == 0 && callsToday == 0 && last7Days.all { it.callCount == 0 }
}

class InsightsCalculator(
    private val callDao: CallRecordDao,
    private val zone: TimeZone = TimeZone.getDefault()
) {

    suspend fun calculate(now: Long = System.currentTimeMillis()): CallInsights {
        val startOfDay = TimeRanges.startOfDay(now, zone)
        val startOfWeek = TimeRanges.startOfWeek(now, zone)
        val offset = TimeRanges.utcOffsetMillis(now, zone)
        // 6 days back plus today = a 7-bar chart.
        val chartStart = TimeRanges.startOfDaysAgo(now, 6, zone)

        return CallInsights(
            callsToday = callDao.countSince(startOfDay),
            callsThisWeek = callDao.countSince(startOfWeek),
            missedToday = callDao.missedSince(startOfDay),
            missedThisWeek = callDao.missedSince(startOfWeek),
            vipCallsThisWeek = callDao.vipCallCountSince(startOfWeek),
            incomingThisWeek = callDao.countByTypeSince("INCOMING", startOfWeek),
            outgoingThisWeek = callDao.countByTypeSince("OUTGOING", startOfWeek),
            mostContacted = callDao.mostContactedSince(startOfWeek, 5),
            longestCalls = callDao.longestCalls(3),
            busiestHours = callDao.hourlyCountsSince(startOfWeek, offset).take(3),
            last7Days = fillMissingDays(
                callDao.dailyCountsSince(chartStart, offset),
                chartStart,
                zone
            )
        )
    }

    /**
     * SQL only returns days that had calls. The chart needs all seven bars, so
     * quiet days are filled in as explicit zeroes - otherwise a week with two
     * busy days would render as a two-bar chart and read as a much fuller week
     * than it was.
     */
    internal fun fillMissingDays(
        rows: List<DailyCallCount>,
        chartStart: Long,
        zone: TimeZone
    ): List<DailyCallCount> {
        val byDay = rows.associateBy { it.dayStartMillis }
        return (0 until 7).map { offsetDays ->
            val dayStart = TimeRanges.startOfDay(
                chartStart + offsetDays * MILLIS_PER_DAY + MILLIS_PER_DAY / 2,
                zone
            )
            byDay[dayStart] ?: DailyCallCount(dayStart, 0)
        }
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
