package com.codeaza.bhaiyaaa.domain.usecase

import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.db.entity.NotificationRuleEntity
import com.codeaza.bhaiyaaa.domain.model.VipLevel

/** Why a call did or did not raise an alert. Kept so the UI can explain itself. */
enum class AlertOutcome(val explanation: String) {
    ALERT("Alert fires"),
    NOT_VIP("Caller is not a VIP"),
    CONTACT_MUTED("Alerts are muted for this contact"),
    ALERTS_OFF("VIP alerts are switched off"),
    TIER_ALERTS_OFF("Alerts are switched off for this tier"),
    PRAYER_SILENCE("A prayer silence window is running and this tier isn't allowed through")
}

/**
 * Decides whether an incoming call raises a VIP alert.
 *
 * Pulled out of the broadcast receiver so the precedence is stated in one place
 * and can be tested, rather than being an implicit consequence of the order of
 * some early returns.
 *
 * The order matters, and one rule in particular. "Ring through silent" is a
 * setting about the phone being on silent - it is NOT permission to interrupt a
 * prayer. A VIP marked to ring through silent is still silenced during a prayer
 * window unless that tier is separately allowed through, because those are two
 * different questions and answering the first should never quietly answer the
 * second.
 */
object AlertDecision {

    fun evaluate(
        contact: ContactEntity,
        rule: NotificationRuleEntity?,
        alertsGloballyEnabled: Boolean,
        prayerSilenceActive: Boolean
    ): AlertOutcome {
        val level = VipLevel.from(contact.vipLevel)
        if (!level.isVip) return AlertOutcome.NOT_VIP
        if (!contact.notificationsEnabled) return AlertOutcome.CONTACT_MUTED
        if (!alertsGloballyEnabled) return AlertOutcome.ALERTS_OFF

        // A missing row means seeding failed, not that the user switched this
        // tier off - so fall back to the tier's shipped defaults. Treating the
        // two as the same silently killed every tier whose row was absent.
        val effective = rule ?: NotificationRuleEntity.defaultFor(level.storageValue)

        // Prayer outranks the tier, and outranks bypassDnd. Checked before the
        // per-tier enable so the reason reported is the true one.
        if (prayerSilenceActive && !effective.ringsDuringPrayer) {
            return AlertOutcome.PRAYER_SILENCE
        }

        if (!effective.notificationsEnabled &&
            !effective.vibrationEnabled &&
            !effective.flashEnabled
        ) {
            return AlertOutcome.TIER_ALERTS_OFF
        }

        return AlertOutcome.ALERT
    }
}
