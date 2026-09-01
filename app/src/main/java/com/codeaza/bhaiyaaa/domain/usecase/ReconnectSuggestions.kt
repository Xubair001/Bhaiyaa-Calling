package com.codeaza.bhaiyaaa.domain.usecase

import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.db.projection.ContactStats
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import java.util.concurrent.TimeUnit

/** Someone worth calling, and how long it has been. */
data class ReconnectSuggestion(
    val contact: ContactEntity,
    /** Null when there is no call on record with them at all. */
    val lastCallAt: Long?,
    /** Null alongside a null [lastCallAt] - "never" is not a number of days. */
    val daysSince: Long?
) {
    val level: VipLevel get() = VipLevel.from(contact.vipLevel)
}

/**
 * "You haven't spoken to Ammi in three months."
 *
 * The other half of the app's promise. Sukoon is good at the *quiet* - it
 * silences the phone, it guards the prayer window - and this is the part that
 * looks after the people, which is the harder and more valuable half.
 *
 * Built entirely from data already flowing through the app: the VIP list and
 * the per-contact call stats are both live Flows on the view model, and
 * [ContactStats.lastCallAt] is already aggregated straight out of the call log.
 * No new query, no new index, no new permission - which is also why it costs
 * nothing to compute.
 *
 * Pure, so the thresholds are a table a test can read rather than behaviour
 * that has to be waited out.
 */
object ReconnectSuggestions {

    /**
     * How long is too long, per tier.
     *
     * Graded because the tiers mean different things. Someone marked Emergency
     * is family you would notice not hearing from within a month; an ordinary
     * VIP can go a season without it meaning anything. Set too low, this
     * becomes a list of everyone, every day, which is a list nobody reads.
     */
    private val QUIET_DAYS: Map<VipLevel, Long> = mapOf(
        VipLevel.EMERGENCY to 21L,
        VipLevel.SUPER_VIP to 30L,
        VipLevel.VIP to 60L
    )

    /**
     * @param vips the contacts marked VIP, at any tier.
     * @param stats per-contact call aggregates, keyed by match key.
     * @param limit how many to show; the dashboard has room for a few, and a
     *   longer list would be a chore rather than a nudge.
     * @return longest silence first, so the most overdue is at the top.
     */
    fun forVips(
        vips: List<ContactEntity>,
        stats: List<ContactStats>,
        now: Long,
        limit: Int = DEFAULT_LIMIT
    ): List<ReconnectSuggestion> {
        val lastCallByKey = stats.associate { it.matchKey to it.lastCallAt }

        return vips
            .mapNotNull { contact ->
                val threshold = QUIET_DAYS[VipLevel.from(contact.vipLevel)] ?: return@mapNotNull null
                val lastCallAt = lastCallByKey[contact.matchKey]

                if (lastCallAt == null) {
                    // Never spoken. Only worth raising once the contact has
                    // been on the list a while - otherwise marking someone VIP
                    // nags about them the same afternoon.
                    val age = daysBetween(contact.createdAt, now)
                    if (contact.createdAt <= 0L || age < threshold) return@mapNotNull null
                    return@mapNotNull ReconnectSuggestion(contact, null, null)
                }

                val days = daysBetween(lastCallAt, now)
                if (days < threshold) null
                else ReconnectSuggestion(contact, lastCallAt, days)
            }
            // Never-spoken first, then longest silence. Sorting nulls last by
            // day count would bury the strongest signal in the list.
            .sortedWith(
                compareByDescending<ReconnectSuggestion> { it.daysSince == null }
                    .thenByDescending { it.daysSince ?: 0L }
            )
            .take(limit)
    }

    private fun daysBetween(from: Long, to: Long): Long =
        TimeUnit.MILLISECONDS.toDays((to - from).coerceAtLeast(0L))

    const val DEFAULT_LIMIT = 3
}
