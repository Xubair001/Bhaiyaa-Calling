package com.codeaza.bhaiyaaa.ai

import android.content.Context
import com.codeaza.bhaiyaaa.R
import com.codeaza.bhaiyaaa.domain.model.PersonalityMode

/**
 * Resource-backed phrasing. Same contract as [DefaultPhrasebook], but every
 * line comes from strings.xml - so translating BHAIYAAA into Urdu or Roman
 * Urdu is a values-ur/ folder and nothing else.
 */
class ResourcePhrasebook(
    private val context: Context,
    override val mode: PersonalityMode
) : Phrasebook {

    private fun pick(pro: Int, friendly: Int, bhai: Int): String = context.getString(
        when (mode) {
            PersonalityMode.PROFESSIONAL -> pro
            PersonalityMode.FRIENDLY -> friendly
            PersonalityMode.BHAI -> bhai
        }
    )

    override fun greetingMorning(): String = pick(
        R.string.persona_pro_greeting_morning,
        R.string.persona_friendly_greeting_morning,
        R.string.persona_bhai_greeting_morning
    )

    override fun greetingAfternoon(): String = pick(
        R.string.persona_pro_greeting_afternoon,
        R.string.persona_friendly_greeting_afternoon,
        R.string.persona_bhai_greeting_afternoon
    )

    override fun greetingEvening(): String = pick(
        R.string.persona_pro_greeting_evening,
        R.string.persona_friendly_greeting_evening,
        R.string.persona_bhai_greeting_evening
    )

    override fun vipCalling(name: String): String = context.getString(
        when (mode) {
            PersonalityMode.PROFESSIONAL -> R.string.persona_pro_vip_calling
            PersonalityMode.FRIENDLY -> R.string.persona_friendly_vip_calling
            PersonalityMode.BHAI -> R.string.persona_bhai_vip_calling
        },
        name
    )

    override fun reminderCreated(): String = pick(
        R.string.persona_pro_reminder_created,
        R.string.persona_friendly_reminder_created,
        R.string.persona_bhai_reminder_created
    )

    override fun noMissedCalls(): String = pick(
        R.string.persona_pro_no_missed,
        R.string.persona_friendly_no_missed,
        R.string.persona_bhai_no_missed
    )

    override fun address(): String = if (mode == PersonalityMode.BHAI) "bhai" else ""
}
