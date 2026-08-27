package com.codeaza.bhaiyaaa.domain

import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.db.entity.NotificationRuleEntity
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.domain.usecase.AlertDecision
import com.codeaza.bhaiyaaa.domain.usecase.AlertOutcome
import com.codeaza.bhaiyaaa.util.PhoneNumbers
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pins the precedence between VIP tiers and prayer silence.
 *
 * The rule that needs stating out loud: "ring through silent" is about the
 * ringer, not about prayer. Allowing a VIP past silent mode must never quietly
 * also allow them to interrupt a prayer - those are two separate permissions
 * and only the prayer one governs a prayer window.
 */
class AlertDecisionTest {

    private fun contact(
        vip: VipLevel = VipLevel.SUPER_VIP,
        notificationsEnabled: Boolean = true
    ) = ContactEntity(
        phoneNumber = "+923001234567",
        matchKey = PhoneNumbers.matchKey("+923001234567"),
        name = "Ahmed",
        vipLevel = vip.storageValue,
        notificationsEnabled = notificationsEnabled,
        createdAt = 0,
        updatedAt = 0
    )

    private fun rule(
        level: VipLevel = VipLevel.SUPER_VIP,
        ringsDuringPrayer: Boolean = false,
        bypassDnd: Boolean = false
    ) = NotificationRuleEntity(
        vipLevel = level.storageValue,
        bypassDnd = bypassDnd,
        ringsDuringPrayer = ringsDuringPrayer
    )

    @Test
    fun `a VIP alerts normally outside a prayer window`() {
        val outcome = AlertDecision.evaluate(
            contact(), rule(), alertsGloballyEnabled = true, prayerSilenceActive = false
        )
        assertThat(outcome).isEqualTo(AlertOutcome.ALERT)
    }

    @Test
    fun `prayer silence overrides a VIP that is not allowed through`() {
        val outcome = AlertDecision.evaluate(
            contact(), rule(ringsDuringPrayer = false),
            alertsGloballyEnabled = true, prayerSilenceActive = true
        )
        assertThat(outcome).isEqualTo(AlertOutcome.PRAYER_SILENCE)
    }

    @Test
    fun `ring-through-silent does NOT grant permission to interrupt a prayer`() {
        // The rule this whole class exists for. Someone allowed past silent mode
        // is still silenced during prayer unless allowed there separately.
        val outcome = AlertDecision.evaluate(
            contact(),
            rule(bypassDnd = true, ringsDuringPrayer = false),
            alertsGloballyEnabled = true,
            prayerSilenceActive = true
        )
        assertThat(outcome).isEqualTo(AlertOutcome.PRAYER_SILENCE)
    }

    @Test
    fun `a tier explicitly allowed through prayer still alerts`() {
        val outcome = AlertDecision.evaluate(
            contact(VipLevel.EMERGENCY),
            rule(VipLevel.EMERGENCY, ringsDuringPrayer = true),
            alertsGloballyEnabled = true,
            prayerSilenceActive = true
        )
        assertThat(outcome).isEqualTo(AlertOutcome.ALERT)
    }

    @Test
    fun `allowing one tier through prayer does not allow the others`() {
        val emergency = AlertDecision.evaluate(
            contact(VipLevel.EMERGENCY),
            rule(VipLevel.EMERGENCY, ringsDuringPrayer = true),
            alertsGloballyEnabled = true, prayerSilenceActive = true
        )
        val superVip = AlertDecision.evaluate(
            contact(VipLevel.SUPER_VIP),
            rule(VipLevel.SUPER_VIP, ringsDuringPrayer = false),
            alertsGloballyEnabled = true, prayerSilenceActive = true
        )
        assertThat(emergency).isEqualTo(AlertOutcome.ALERT)
        assertThat(superVip).isEqualTo(AlertOutcome.PRAYER_SILENCE)
    }

    @Test
    fun `a non-VIP never alerts, prayer or not`() {
        listOf(true, false).forEach { praying ->
            val outcome = AlertDecision.evaluate(
                contact(VipLevel.NONE), rule(),
                alertsGloballyEnabled = true, prayerSilenceActive = praying
            )
            assertThat(outcome).isEqualTo(AlertOutcome.NOT_VIP)
        }
    }

    @Test
    fun `muting one contact silences them even at Emergency tier`() {
        val outcome = AlertDecision.evaluate(
            contact(VipLevel.EMERGENCY, notificationsEnabled = false),
            rule(VipLevel.EMERGENCY, ringsDuringPrayer = true),
            alertsGloballyEnabled = true, prayerSilenceActive = false
        )
        assertThat(outcome).isEqualTo(AlertOutcome.CONTACT_MUTED)
    }

    @Test
    fun `the global switch silences every tier`() {
        val outcome = AlertDecision.evaluate(
            contact(VipLevel.EMERGENCY),
            rule(VipLevel.EMERGENCY, ringsDuringPrayer = true),
            alertsGloballyEnabled = false, prayerSilenceActive = false
        )
        assertThat(outcome).isEqualTo(AlertOutcome.ALERTS_OFF)
    }

    @Test
    fun `a tier with every alert switched off does not fire`() {
        val silentRule = NotificationRuleEntity(
            vipLevel = VipLevel.VIP.storageValue,
            notificationsEnabled = false,
            vibrationEnabled = false,
            flashEnabled = false
        )
        val outcome = AlertDecision.evaluate(
            contact(VipLevel.VIP), silentRule,
            alertsGloballyEnabled = true, prayerSilenceActive = false
        )
        assertThat(outcome).isEqualTo(AlertOutcome.TIER_ALERTS_OFF)
    }

    @Test
    fun `a missing rule falls back to the tier defaults instead of going silent`() {
        // A tier with no stored row is a seeding failure, not the user turning
        // alerts off. Treating them the same silently killed Super VIP and
        // Emergency while VIP kept working.
        VipLevel.assignable.forEach { level ->
            val outcome = AlertDecision.evaluate(
                contact(level), rule = null,
                alertsGloballyEnabled = true, prayerSilenceActive = false
            )
            assertThat(outcome).isEqualTo(AlertOutcome.ALERT)
        }
    }

    @Test
    fun `every tier alerts, not just VIP`() {
        VipLevel.assignable.forEach { level ->
            val outcome = AlertDecision.evaluate(
                contact(level), rule(level),
                alertsGloballyEnabled = true, prayerSilenceActive = false
            )
            assertThat(outcome).isEqualTo(AlertOutcome.ALERT)
        }
    }

    @Test
    fun `tier defaults carry distinct alert patterns`() {
        val vip = NotificationRuleEntity.defaultFor(VipLevel.VIP.storageValue)
        val superVip = NotificationRuleEntity.defaultFor(VipLevel.SUPER_VIP.storageValue)
        val emergency = NotificationRuleEntity.defaultFor(VipLevel.EMERGENCY.storageValue)

        // Escalating flash counts, so the tiers are distinguishable in the dark.
        assertThat(vip.flashCount).isLessThan(superVip.flashCount)
        assertThat(superVip.flashCount).isLessThan(emergency.flashCount)
        assertThat(setOf(vip.vibrationPatternCsv, superVip.vibrationPatternCsv, emergency.vibrationPatternCsv))
            .hasSize(3)
        // Only Emergency gets through a prayer by default.
        assertThat(vip.ringsDuringPrayer).isFalse()
        assertThat(superVip.ringsDuringPrayer).isFalse()
        assertThat(emergency.ringsDuringPrayer).isTrue()
    }

    @Test
    fun `defaults exist for all three assignable tiers`() {
        val all = NotificationRuleEntity.allDefaults()
        assertThat(all.map { it.vipLevel })
            .containsExactly("VIP", "SUPER_VIP", "EMERGENCY")
        assertThat(all.all { it.vibrationEnabled && it.flashEnabled && it.notificationsEnabled })
            .isTrue()
    }
}
