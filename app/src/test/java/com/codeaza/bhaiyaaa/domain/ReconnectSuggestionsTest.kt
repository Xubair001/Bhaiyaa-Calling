package com.codeaza.bhaiyaaa.domain

import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.db.projection.ContactStats
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.domain.usecase.ReconnectSuggestions
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * "You haven't spoken to them in a while."
 *
 * The thresholds are graded by tier on purpose, and the failure mode this
 * guards against is the list becoming everyone, every day - a nudge nobody
 * reads is worse than no nudge.
 */
class ReconnectSuggestionsTest {

    private val now = 1_756_000_000_000L

    private fun daysAgo(days: Long) = now - TimeUnit.DAYS.toMillis(days)

    private fun contact(
        name: String,
        level: VipLevel,
        createdAt: Long = daysAgo(365)
    ) = ContactEntity(
        phoneNumber = "+92300$name",
        matchKey = name,
        name = name,
        vipLevel = level.storageValue,
        createdAt = createdAt
    )

    private fun stats(matchKey: String, lastCallAt: Long?) = ContactStats(
        matchKey = matchKey,
        totalCalls = if (lastCallAt == null) 0 else 1,
        incomingCalls = 0,
        outgoingCalls = 0,
        missedCalls = 0,
        answeredDurationSeconds = 0,
        answeredCalls = 0,
        lastCallAt = lastCallAt
    )

    @Test
    fun `someone spoken to recently is not suggested`() {
        val result = ReconnectSuggestions.forVips(
            vips = listOf(contact("Ali", VipLevel.VIP)),
            stats = listOf(stats("Ali", daysAgo(3))),
            now = now
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `a long silence is surfaced`() {
        val result = ReconnectSuggestions.forVips(
            vips = listOf(contact("Ammi", VipLevel.EMERGENCY)),
            stats = listOf(stats("Ammi", daysAgo(90))),
            now = now
        )

        assertThat(result).hasSize(1)
        assertThat(result.single().daysSince).isEqualTo(90)
    }

    @Test
    fun `the threshold is stricter for a closer tier`() {
        // Twenty-five days is overdue for family and unremarkable for a VIP.
        val vips = listOf(
            contact("Ammi", VipLevel.EMERGENCY),
            contact("Colleague", VipLevel.VIP)
        )
        val callStats = listOf(
            stats("Ammi", daysAgo(25)),
            stats("Colleague", daysAgo(25))
        )

        val result = ReconnectSuggestions.forVips(vips, callStats, now)

        assertThat(result.map { it.contact.name }).containsExactly("Ammi")
    }

    @Test
    fun `the longest silence comes first`() {
        val vips = listOf(
            contact("Recent", VipLevel.VIP),
            contact("Ancient", VipLevel.VIP),
            contact("Middling", VipLevel.VIP)
        )
        val callStats = listOf(
            stats("Recent", daysAgo(61)),
            stats("Ancient", daysAgo(400)),
            stats("Middling", daysAgo(120))
        )

        val result = ReconnectSuggestions.forVips(vips, callStats, now)

        assertThat(result.map { it.contact.name })
            .containsExactly("Ancient", "Middling", "Recent").inOrder()
    }

    @Test
    fun `someone never spoken to leads the list`() {
        val vips = listOf(
            contact("NeverCalled", VipLevel.VIP),
            contact("LongAgo", VipLevel.VIP)
        )
        val callStats = listOf(stats("LongAgo", daysAgo(300)))

        val result = ReconnectSuggestions.forVips(vips, callStats, now)

        assertThat(result.first().contact.name).isEqualTo("NeverCalled")
        assertThat(result.first().daysSince).isNull()
        assertThat(result.first().lastCallAt).isNull()
    }

    @Test
    fun `marking someone VIP does not nag about them the same afternoon`() {
        // Newly added and never called is not a lapsed relationship, it is a
        // contact added a minute ago.
        val result = ReconnectSuggestions.forVips(
            vips = listOf(contact("JustAdded", VipLevel.VIP, createdAt = daysAgo(1))),
            stats = emptyList(),
            now = now
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `a contact with no creation date on record is left alone`() {
        // Rows imported from an old export can have createdAt = 0. Treating
        // that as "added in 1970" would suggest every one of them at once.
        val result = ReconnectSuggestions.forVips(
            vips = listOf(contact("Imported", VipLevel.VIP, createdAt = 0L)),
            stats = emptyList(),
            now = now
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `someone not marked VIP is never suggested`() {
        val result = ReconnectSuggestions.forVips(
            vips = listOf(contact("Stranger", VipLevel.NONE)),
            stats = listOf(stats("Stranger", daysAgo(999))),
            now = now
        )

        assertThat(result).isEmpty()
    }

    @Test
    fun `the list stays short enough to read`() {
        val vips = (1..20).map { contact("P$it", VipLevel.VIP) }
        val callStats = vips.map { stats(it.matchKey, daysAgo(200)) }

        val result = ReconnectSuggestions.forVips(vips, callStats, now)

        assertThat(result).hasSize(ReconnectSuggestions.DEFAULT_LIMIT)
    }
}
