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
    fun `a missing rule is treated as off rather than as permission`() {
        val outcome = AlertDecision.evaluate(
            contact(), rule = null,
            alertsGloballyEnabled = true, prayerSilenceActive = false
        )
        assertThat(outcome).isEqualTo(AlertOutcome.TIER_ALERTS_OFF)
    }
}
