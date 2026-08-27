package com.codeaza.bhaiyaaa.data.db.projection

import androidx.room.Embedded
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity

/**
 * Call statistics aggregated straight out of `call_records`. Nothing here is
 * stored on the contact row, so these numbers are always the truth about the
 * call log rather than a counter that drifted.
 */
data class ContactStats(
    val matchKey: String,
    val totalCalls: Int,
    val incomingCalls: Int,
    val outgoingCalls: Int,
    val missedCalls: Int,
    val answeredDurationSeconds: Long,
    val answeredCalls: Int,
    val lastCallAt: Long?
) {
    /** Average over *answered* calls only - missed calls have no duration to average. */
    val averageDurationSeconds: Long
        get() = if (answeredCalls == 0) 0L else answeredDurationSeconds / answeredCalls
}

/** A contact plus its live call stats, for list and detail screens. */
data class ContactWithStats(
    @Embedded val contact: ContactEntity,
    @Embedded val stats: ContactStats?
)

/** "Who did I speak to most" - used by Insights and the assistant. */
data class ContactCallCount(
    val matchKey: String,
    val displayName: String?,
    val callCount: Int,
    val totalDurationSeconds: Long
)

/** One bar of the 7-day activity chart. */
data class DailyCallCount(
    val dayStartMillis: Long,
    val callCount: Int
)

/** Calls bucketed by hour of day, for the "most active hours" insight. */
data class HourlyCallCount(
    val hourOfDay: Int,
    val callCount: Int
)
