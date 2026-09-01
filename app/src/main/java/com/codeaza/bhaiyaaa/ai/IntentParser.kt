package com.codeaza.bhaiyaaa.ai

import com.codeaza.bhaiyaaa.domain.model.Prayer
import java.util.Locale

/**
 * Turns a typed or spoken phrase into an [AssistantIntent].
 *
 * Rule-based by design. Section 13 of the product brief is explicit that
 * structured questions must hit the database rather than have a model guess at
 * them, so this parser's only job is to decide *which* query to run and with
 * what arguments. It never produces an answer itself, and when it can't tell
 * what was meant it returns [AssistantIntent.Unknown] rather than picking the
 * closest-looking option.
 */
object IntentParser {

    private val REMINDER_PREFIXES = listOf(
        "remind me to ", "remind me ", "reminder to ", "reminder: ",
        "remember to ", "note to self ", "set a reminder to ", "set reminder to "
    )

    private val RECALL_PREFIXES = listOf(
        "what did ", "what has ", "when did ", "did i note ", "what do you know about ",
        "what was ", "remind me what "
    )

    /**
     * "silence for 30 minutes", "mute my phone for 2 hours", "quiet for 45 min".
     *
     * The gap allowance has to cover the words people actually put between the
     * verb and the number - "my phone for " alone is fourteen characters.
     */
    private val SILENCE_FOR = Regex(
        """\b(?:silence|silent|mute|quiet)\b[^0-9]{0,25}(\d{1,3})\s*(min|mins|minute|minutes|hour|hours|hr|hrs)\b"""
    )

    /** A bare "silence my phone", with no duration - defaults to half an hour. */
    private val SILENCE_SHORTHAND = Regex(
        """\b(?:silence|mute)\s+(?:my\s+)?phone\b|\bgo\s+quiet\b|\bsilence\s+me\b"""
    )

    /**
     * What people actually call each prayer, including the spellings that
     * differ by region. Matched as whole words so "fajr" in the middle of a
     * sentence counts and "asrar" does not.
     */
    private val PRAYER_WORDS: List<Pair<Prayer, Regex>> = listOf(
        Prayer.FAJR to listOf("fajr", "fajar", "subh"),
        Prayer.DHUHR to listOf("dhuhr", "duhr", "zuhr", "zohar", "zohr", "luhar"),
        Prayer.ASR to listOf("asr", "asar"),
        Prayer.MAGHRIB to listOf("maghrib", "magrib", "maghreb"),
        Prayer.ISHA to listOf("isha", "esha", "ishaa")
        // Compiled once, at class-load, rather than per word per question:
        // parsing runs on every message and building five regexes each time
        // would be work with an identical answer.
    ).map { (prayer, words) ->
        prayer to Regex(words.joinToString("|", prefix = "\\b(?:", postfix = ")\\b"))
    }

    /** Words that make a sentence a question about a time rather than a statement. */
    private val ASKS_TIME = Regex(
        """\b(?:when|what\s+time|time\s+(?:of|for)|kab)\b"""
    )

    private val ADHAN_WORD = Regex("""\b(?:adhan|azan|azaan|athan)\b""")
    private val TURN_ON = Regex("""\b(?:turn\s+on|enable|start|play|switch\s+on)\b""")
    private val TURN_OFF = Regex(
        """\b(?:turn\s+off|disable|stop|mute|silence|switch\s+off|don't\s+play|do\s+not\s+play)\b"""
    )

    fun parse(rawInput: String, now: Long): AssistantIntent {
        val raw = rawInput.trim()
        if (raw.isBlank()) return AssistantIntent.Unknown
        val q = raw.lowercase(Locale.ROOT)

        if (q == "help" || q.startsWith("what can you") || q.startsWith("how do you")) {
            return AssistantIntent.Help
        }

        // --- reminders (checked first: "remind me to call Ali tomorrow" also
        //     contains "call", which would otherwise look like a call query) ---
        for (prefix in REMINDER_PREFIXES) {
            if (!q.startsWith(prefix)) continue
            val body = raw.substring(prefix.length).trim()
            val parsed = TimeExpressions.parse(body, now)
            val cleaned = TimeExpressions.stripTimePhrase(body, parsed.matchedText)
            val text = cleaned.ifBlank { body }
            return AssistantIntent.CreateReminder(text, parsed.dueAt)
        }

        // Silence requests are checked early: "quiet for an hour" contains
        // "hour", which the time parser would otherwise pull into a reminder.
        SILENCE_FOR.find(q)?.let { m ->
            val amount = m.groupValues[1].toIntOrNull()
            val unit = m.groupValues[2]
            if (amount != null && amount > 0) {
                val minutes = if (unit.startsWith("hour") || unit.startsWith("hr")) amount * 60 else amount
                return AssistantIntent.SilenceFor(minutes.coerceIn(1, 720))
            }
        }
        if (SILENCE_SHORTHAND.containsMatchIn(q)) {
            return AssistantIntent.SilenceFor(30)
        }

        // Checked before the prayer-time question: "turn on the adhan" also
        // contains a prayer word in most phrasings, and acting on a preference
        // is the more specific reading.
        if (ADHAN_WORD.containsMatchIn(q)) {
            when {
                TURN_OFF.containsMatchIn(q) -> return AssistantIntent.SetAdhan(false)
                TURN_ON.containsMatchIn(q) && !ASKS_TIME.containsMatchIn(q) ->
                    return AssistantIntent.SetAdhan(true)
            }
        }

        // "when is Asr" - a named prayer, which is a different question from
        // "what is next" and must not be answered with the next one.
        if (ASKS_TIME.containsMatchIn(q)) {
            PRAYER_WORDS.firstOrNull { (_, pattern) -> pattern.containsMatchIn(q) }
                ?.let { (prayer, _) -> return AssistantIntent.PrayerTimeToday(prayer) }
        }

        if (q.contains("next prayer") || q.contains("next quiet") ||
            q.contains("when is prayer") || q.contains("when does my phone go quiet") ||
            q.contains("next namaz") || q.contains("next azan") || q.contains("next adhan")
        ) {
            return AssistantIntent.NextQuietTime
        }

        if (q.contains("my reminders") || q.contains("pending reminders") ||
            q == "reminders" || q.contains("what do i have to do")
        ) {
            return AssistantIntent.PendingReminders
        }

        // --- memory recall: "what did Ahmed say about the deployment" ---------
        for (prefix in RECALL_PREFIXES) {
            if (!q.startsWith(prefix)) continue
            // "when did i last talk to X" is a call question, not a memory one.
            if (q.contains("last call") || q.contains("last talk") || q.contains("last speak") ||
                q.contains("last spoke")
            ) break
            val body = raw.substring(prefix.length).trim().removeSuffix("?").trim()
            if (body.isNotBlank()) return AssistantIntent.RecallMemory(body)
        }

        // --- VIP -------------------------------------------------------------
        if (q.contains("vip")) return AssistantIntent.VipList

        // --- last call with someone -----------------------------------------
        if (q.contains("last call") || q.contains("last talk") || q.contains("last spoke") ||
            q.contains("last speak") || q.contains("when did i")
        ) {
            val name = extractName(raw)
            if (name.isNotBlank()) return AssistantIntent.LastCallWith(name)
        }

        // --- missed ----------------------------------------------------------
        if (q.contains("missed")) return AssistantIntent.MissedCalls(periodOf(q))

        // --- most contacted --------------------------------------------------
        if (q.contains("most") && (q.contains("call") || q.contains("contact") || q.contains("talk"))) {
            return AssistantIntent.MostContacted(periodOf(q))
        }

        // --- counts ----------------------------------------------------------
        if ((q.contains("how many") || q.contains("count")) && q.contains("call")) {
            return AssistantIntent.CallCount(periodOf(q))
        }

        // --- recent callers --------------------------------------------------
        if (q.contains("who called") || q.contains("recent call") || q.contains("recent caller") ||
            q.startsWith("who has called") || q == "recent"
        ) {
            return AssistantIntent.RecentCallers
        }

        // --- a bare name, or "tell me about X" -------------------------------
        if (q.startsWith("who is ") || q.startsWith("tell me about ") || q.startsWith("show me ")) {
            val name = raw.substringAfter(' ').substringAfter(' ').trim().removeSuffix("?")
            if (name.isNotBlank()) return AssistantIntent.ContactLookup(name)
        }

        if (q.contains("call") && q.contains("today")) return AssistantIntent.CallCount(Period.TODAY)

        return AssistantIntent.Unknown
    }

    private fun periodOf(q: String): Period = when {
        q.contains("today") -> Period.TODAY
        q.contains("this week") || q.contains("week") -> Period.THIS_WEEK
        q.contains("this month") || q.contains("month") -> Period.THIS_MONTH
        else -> Period.RECENT
    }

    /** Pulls a person's name out of "…with Ahmed", "…to Ali", "…from Sara". */
    internal fun extractName(raw: String): String {
        val lower = raw.lowercase(Locale.ROOT)
        for (marker in listOf(" with ", " to ", " from ", " speak to ", " talk to ")) {
            val idx = lower.lastIndexOf(marker)
            if (idx == -1) continue
            return raw.substring(idx + marker.length)
                .trim()
                .removeSuffix("?")
                .trim()
        }
        return ""
    }
}
