package com.codeaza.bhaiyaaa.ai

import com.codeaza.bhaiyaaa.domain.model.PersonalityMode

/**
 * Sukoon's tone, isolated behind an interface.
 *
 * Two reasons it is not just hard-coded strings:
 *  - localisation: [ResourcePhrasebook] reads the same phrases out of
 *    strings.xml, so a values-ur/ folder translates Sukoon without touching
 *    a line of logic.
 *  - testability: [DefaultPhrasebook] is pure Kotlin, so the assistant's
 *    behaviour can be unit-tested on the JVM with no Android context.
 *
 * Tone never changes the facts - only the wording around them.
 */
interface Phrasebook {
    val mode: PersonalityMode

    fun greetingMorning(): String
    fun greetingAfternoon(): String
    fun greetingEvening(): String
    fun vipCalling(name: String): String
    fun reminderCreated(): String
    fun noMissedCalls(): String

    /** Term of address appended to answers, e.g. "bhai". Empty for professional. */
    fun address(): String

    /** Appends the term of address naturally, if this tone uses one. */
    fun withAddress(sentence: String): String {
        val term = address()
        if (term.isBlank()) return sentence
        val trimmed = sentence.trimEnd()
        return when {
            trimmed.endsWith("?") || trimmed.endsWith("!") ->
                trimmed.dropLast(1) + ", $term" + trimmed.last()
            trimmed.endsWith(".") -> trimmed.dropLast(1) + ", $term."
            else -> "$trimmed, $term."
        }
    }
}

/** Built-in English phrasing. Also the fallback if a resource lookup fails. */
class DefaultPhrasebook(override val mode: PersonalityMode) : Phrasebook {

    override fun greetingMorning(): String = when (mode) {
        PersonalityMode.PROFESSIONAL -> "Good morning"
        PersonalityMode.FRIENDLY -> "Good morning 👋"
        PersonalityMode.BHAI -> "Assalam o Alaikum, boss 👋"
    }

    override fun greetingAfternoon(): String = when (mode) {
        PersonalityMode.PROFESSIONAL -> "Good afternoon"
        PersonalityMode.FRIENDLY -> "Good afternoon 👋"
        PersonalityMode.BHAI -> "What's up, bhai?"
    }

    override fun greetingEvening(): String = when (mode) {
        PersonalityMode.PROFESSIONAL -> "Good evening"
        PersonalityMode.FRIENDLY -> "Good evening 🌙"
        PersonalityMode.BHAI -> "Good evening, bhai 🌙"
    }

    override fun vipCalling(name: String): String = when (mode) {
        PersonalityMode.PROFESSIONAL -> "VIP contact calling: $name"
        PersonalityMode.FRIENDLY -> "$name is calling - that's a VIP."
        PersonalityMode.BHAI -> "Boss, $name is calling 👀"
    }

    override fun reminderCreated(): String = when (mode) {
        PersonalityMode.PROFESSIONAL -> "Reminder created."
        PersonalityMode.FRIENDLY -> "Got it, reminder saved."
        PersonalityMode.BHAI -> "Done scene. Reminder bana diya."
    }

    override fun noMissedCalls(): String = when (mode) {
        PersonalityMode.PROFESSIONAL -> "No missed calls."
        PersonalityMode.FRIENDLY -> "Nothing missed. You're all caught up."
        PersonalityMode.BHAI -> "Chill karo - kuch miss nahi hua."
    }

    override fun address(): String = when (mode) {
        PersonalityMode.PROFESSIONAL -> ""
        PersonalityMode.FRIENDLY -> ""
        PersonalityMode.BHAI -> "bhai"
    }
}
