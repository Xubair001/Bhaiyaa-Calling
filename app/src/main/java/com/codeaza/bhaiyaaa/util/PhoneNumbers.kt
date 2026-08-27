package com.codeaza.bhaiyaaa.util

/**
 * Phone numbers arrive in wildly different shapes: a contact might be saved as
 * "+92 300 1234567" while the call log reports "03001234567" for the very same
 * person. If those don't reconcile, VIP alerts silently never fire - so every
 * number is reduced to two forms:
 *
 *  - [normalize]  a display/storage form: digits plus a leading '+' if present.
 *  - [matchKey]   a comparison form: the last [MATCH_DIGITS] significant digits,
 *                 which survives country codes, trunk zeros and spacing.
 *
 * Matching on a suffix is what mainstream dialers do. It is not perfect - two
 * numbers from different countries could in principle share a suffix - so it is
 * used only to link a call to a contact, never to make a security decision.
 */
object PhoneNumbers {

    /** Long enough to be specific, short enough to survive +92 / 0 prefixes. */
    const val MATCH_DIGITS = 9

    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        return if (hasPlus) "+$digits" else digits
    }

    /**
     * Comparison key. Short numbers (shortcodes, service numbers) are kept whole
     * rather than padded, so "8558" never collides with a real subscriber line.
     */
    fun matchKey(raw: String?): String {
        val digits = normalize(raw).filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        return if (digits.length <= MATCH_DIGITS) digits
        else digits.takeLast(MATCH_DIGITS)
    }

    /** True when two raw numbers most likely belong to the same person. */
    fun sameNumber(a: String?, b: String?): Boolean {
        val ka = matchKey(a)
        val kb = matchKey(b)
        return ka.isNotEmpty() && ka == kb
    }

    /** Light formatting for display when we have nothing but a raw number. */
    fun forDisplay(raw: String?): String {
        val n = normalize(raw)
        return if (n.isBlank()) "Unknown" else n
    }
}
