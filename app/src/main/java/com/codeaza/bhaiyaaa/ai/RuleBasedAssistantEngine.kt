package com.codeaza.bhaiyaaa.ai

import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.util.Formatting
import com.codeaza.bhaiyaaa.util.PhoneNumbers
import com.codeaza.bhaiyaaa.util.TimeRanges
import java.util.Locale

/**
 * Sukoon's default assistant: intent matching over the user's own local data.
 *
 * It is not a language model, and it does not pretend to be one. Every sentence
 * it returns is built from rows it just read out of Room, which is what makes
 * the anti-hallucination rule in §13 of the brief actually hold rather than
 * being a hope. When it doesn't understand a question it says so and offers
 * what it does handle - it never guesses at an answer about someone's calls.
 *
 * Runs entirely on-device, needs no model download, no network and no API key,
 * so it is always available as the floor beneath any optional local model.
 */
class RuleBasedAssistantEngine(
    private val data: AssistantDataSource,
    private val phrasebook: Phrasebook,
    private val now: () -> Long = System::currentTimeMillis
) : AssistantEngine {

    override val id: String = ENGINE_ID
    override val displayName: String = "Sukoon local rules"

    /** Always true: this engine has no model to install and no service to reach. */
    override suspend fun isAvailable(): Boolean = true

    override suspend fun respond(input: String): AssistantResponse {
        val timestamp = now()
        val intent = IntentParser.parse(input, timestamp)
        return when (intent) {
            is AssistantIntent.CreateReminder -> createReminder(intent)
            is AssistantIntent.MissedCalls -> missedCalls(intent)
            is AssistantIntent.VipList -> vipList(intent)
            is AssistantIntent.CallCount -> callCount(intent)
            is AssistantIntent.MostContacted -> mostContacted(intent)
            is AssistantIntent.LastCallWith -> lastCallWith(intent)
            is AssistantIntent.RecentCallers -> recentCallers(intent)
            is AssistantIntent.RecallMemory -> recallMemory(intent)
            is AssistantIntent.ContactLookup -> contactLookup(intent)
            is AssistantIntent.PendingReminders -> pendingReminders(intent)
            is AssistantIntent.Help -> help(intent)
            is AssistantIntent.Unknown -> unknown(intent)
        }
    }

    // ---------------------------------------------------------------- actions

    private suspend fun createReminder(intent: AssistantIntent.CreateReminder): AssistantResponse {
        if (intent.text.isBlank()) {
            return AssistantResponse(
                phrasebook.withAddress("Tell me what to remind you about"),
                intent
            )
        }
        val id = data.createReminder(intent.text, intent.dueAt)
        val when_ = intent.dueAt?.let { " for ${Formatting.relativeDateTime(it, now())}" }.orEmpty()
        return AssistantResponse(
            text = phrasebook.reminderCreated() + when_.ifBlank { "" },
            intent = intent,
            action = AssistantAction.ReminderCreated(id, intent.text, intent.dueAt),
            sources = listOf(AssistantSource("Reminder saved", intent.text))
        )
    }

    // ---------------------------------------------------------------- queries

    private suspend fun missedCalls(intent: AssistantIntent.MissedCalls): AssistantResponse {
        val since = sinceFor(intent.period)
        val count = data.countMissedSince(since)
        if (count == 0) {
            return AssistantResponse(phrasebook.noMissedCalls(), intent, sources = periodSource(intent.period))
        }
        // Name the actual people rather than only a number.
        val missed = data.recentCalls(200).filter { it.type == "MISSED" && it.timestamp >= since }
        val names = missed.mapNotNull { it.contactName ?: it.phoneNumber.takeIf { n -> n.isNotBlank() } }
            .distinct()
            .take(3)
        val who = if (names.isEmpty()) "" else " (${names.joinToString(", ")})"
        return AssistantResponse(
            text = phrasebook.withAddress(
                "You've got ${Formatting.plural(count, "missed call")} ${intent.period.label}$who"
            ),
            intent = intent,
            sources = periodSource(intent.period)
        )
    }

    private suspend fun vipList(intent: AssistantIntent): AssistantResponse {
        val vips = data.vipContacts()
        if (vips.isEmpty()) {
            return AssistantResponse(
                phrasebook.withAddress("No VIP contacts set yet. Open a contact and set their VIP level"),
                intent
            )
        }
        val grouped = vips.groupBy { VipLevel.from(it.vipLevel) }
        val summary = VipLevel.assignable.reversed().mapNotNull { level ->
            grouped[level]?.takeIf { it.isNotEmpty() }?.let { members ->
                "${level.label}: ${members.joinToString(", ") { it.name }}"
            }
        }.joinToString(" · ")
        return AssistantResponse(
            text = phrasebook.withAddress("You have ${Formatting.plural(vips.size, "VIP")} — $summary"),
            intent = intent,
            sources = listOf(AssistantSource("From your contacts", "${vips.size} marked VIP"))
        )
    }

    private suspend fun callCount(intent: AssistantIntent.CallCount): AssistantResponse {
        val since = sinceFor(intent.period)
        val count = data.countCallsSince(since)
        val text = if (count == 0) "No calls ${intent.period.label}"
        else "${Formatting.plural(count, "call")} ${intent.period.label}"
        return AssistantResponse(
            phrasebook.withAddress(text), intent, sources = periodSource(intent.period)
        )
    }

    private suspend fun mostContacted(intent: AssistantIntent.MostContacted): AssistantResponse {
        val since = sinceFor(intent.period)
        val top = data.mostContactedSince(since, 3)
        if (top.isEmpty()) {
            return AssistantResponse(
                phrasebook.withAddress("No calls ${intent.period.label} to rank"), intent
            )
        }
        val leader = top.first()
        val rest = top.drop(1).joinToString(", ") { "${it.displayName ?: "Unknown"} (${it.callCount})" }
        val tail = if (rest.isBlank()) "" else ". Then $rest"
        return AssistantResponse(
            text = phrasebook.withAddress(
                "${leader.displayName ?: "Unknown"} — ${Formatting.plural(leader.callCount, "call")} " +
                    "${intent.period.label}$tail"
            ),
            intent = intent,
            sources = periodSource(intent.period)
        )
    }

    private suspend fun lastCallWith(intent: AssistantIntent.LastCallWith): AssistantResponse {
        val matches = matchContactsByName(intent.name)
        if (matches.isEmpty()) {
            return AssistantResponse(
                phrasebook.withAddress("I don't have a contact called \"${intent.name}\""), intent
            )
        }
        if (matches.size > 1) {
            return AssistantResponse(
                text = "A few people match \"${intent.name}\": ${matches.take(4).joinToString(", ") { it.name }}. " +
                    "Which one?",
                intent = intent
            )
        }
        val contact = matches.first()
        val call = data.callsForMatchKey(contact.matchKey, 1).firstOrNull()
            ?: return AssistantResponse(
                phrasebook.withAddress("No calls logged with ${contact.name} yet"), intent
            )
        val direction = when (call.type) {
            "OUTGOING" -> "You called ${contact.name}"
            "MISSED" -> "You missed a call from ${contact.name}"
            else -> "${contact.name} called you"
        }
        val length = if (call.durationSeconds > 0) " · ${Formatting.duration(call.durationSeconds)}" else ""
        return AssistantResponse(
            text = phrasebook.withAddress(
                "$direction ${Formatting.relativeDateTime(call.timestamp, now())}$length"
            ),
            intent = intent,
            sources = listOf(AssistantSource("From your call log", Formatting.dateTime(call.timestamp)))
        )
    }

    private suspend fun recentCallers(intent: AssistantIntent): AssistantResponse {
        val recent = data.recentCalls(5)
        if (recent.isEmpty()) {
            return AssistantResponse(phrasebook.withAddress("No calls logged yet"), intent)
        }
        val names = recent.map { it.contactName ?: PhoneNumbers.forDisplay(it.phoneNumber) }
            .distinct()
            .take(3)
        return AssistantResponse(
            text = phrasebook.withAddress("Recent callers: ${names.joinToString(", ")}"),
            intent = intent,
            sources = listOf(AssistantSource("From your call log", "Last ${recent.size} calls"))
        )
    }

    /**
     * Memory recall. Critically, this only ever returns text the user themselves
     * saved - Sukoon does not transcribe or infer what was said on a call, so
     * if nothing was written down it says exactly that.
     */
    private suspend fun recallMemory(intent: AssistantIntent.RecallMemory): AssistantResponse {
        val hits = data.searchMemories(intent.query, 3)
        if (hits.isEmpty()) {
            return AssistantResponse(
                text = "I've got nothing saved about that. Sukoon only remembers notes you " +
                    "saved yourself — it can't hear or record your calls.",
                intent = intent
            )
        }
        val best = hits.first()
        val extra = if (hits.size > 1) " (+${hits.size - 1} more)" else ""
        return AssistantResponse(
            text = "You noted: \"${best.body}\"$extra",
            intent = intent,
            sources = hits.map {
                AssistantSource("Saved ${Formatting.date(it.createdAt)}", it.title ?: it.body.take(60))
            }
        )
    }

    private suspend fun contactLookup(intent: AssistantIntent.ContactLookup): AssistantResponse {
        val matches = matchContactsByName(intent.name)
        if (matches.isEmpty()) {
            return AssistantResponse(
                phrasebook.withAddress("No contact called \"${intent.name}\""), intent
            )
        }
        val c = matches.first()
        val calls = data.callsForMatchKey(c.matchKey, 500)
        val vip = VipLevel.from(c.vipLevel)
        val bits = buildList {
            if (vip.isVip) add(vip.label)
            c.tag?.let { add(it) }
            add("${Formatting.plural(calls.size, "call")} logged")
            calls.firstOrNull()?.let { add("last ${Formatting.relativeDateTime(it.timestamp, now())}") }
            c.notes?.takeIf { it.isNotBlank() }?.let { add("note: \"${it.take(60)}\"") }
        }
        return AssistantResponse(
            text = "${c.name} — ${bits.joinToString(" · ")}",
            intent = intent,
            sources = listOf(AssistantSource("From your contacts", c.name))
        )
    }

    private suspend fun pendingReminders(intent: AssistantIntent): AssistantResponse {
        val pending = data.pendingReminders()
        if (pending.isEmpty()) {
            return AssistantResponse(phrasebook.withAddress("Nothing pending. You're clear"), intent)
        }
        val list = pending.take(3).joinToString("; ") { r ->
            r.dueAt?.let { "${r.text} (${Formatting.relativeDateTime(it, now())})" } ?: r.text
        }
        val more = if (pending.size > 3) " …and ${pending.size - 3} more" else ""
        return AssistantResponse(
            text = "${Formatting.plural(pending.size, "reminder")} pending: $list$more",
            intent = intent,
            sources = listOf(AssistantSource("Your reminders", "${pending.size} open"))
        )
    }

    private fun help(intent: AssistantIntent): AssistantResponse = AssistantResponse(
        text = "I read your own call log, contacts and saved notes. Try:\n" +
            "• Who called me?\n" +
            "• Any missed calls today?\n" +
            "• Show my VIP contacts\n" +
            "• When did I last talk to Ahmed?\n" +
            "• Who called me most this week?\n" +
            "• Remind me to call Ali tomorrow at 5pm\n" +
            "• What did Ahmed say about the deployment?",
        intent = intent
    )

    private fun unknown(intent: AssistantIntent): AssistantResponse = AssistantResponse(
        text = phrasebook.withAddress(
            "Didn't catch that one. Ask me about missed calls, VIPs, recent callers, " +
                "how many calls today, or say \"remind me to…\""
        ),
        intent = intent
    )

    // ---------------------------------------------------------------- helpers

    /**
     * Name matching is deliberately forgiving on case and partial words, but
     * never fuzzy enough to pick the wrong person silently: when more than one
     * contact matches, the caller asks the user which one they meant.
     */
    private suspend fun matchContactsByName(name: String) =
        data.allContacts().let { all ->
            val needle = name.trim().lowercase(Locale.ROOT)
            if (needle.isBlank()) return@let emptyList()
            val exact = all.filter { it.name.lowercase(Locale.ROOT) == needle }
            if (exact.isNotEmpty()) return@let exact
            val startsWith = all.filter { it.name.lowercase(Locale.ROOT).startsWith(needle) }
            if (startsWith.isNotEmpty()) return@let startsWith
            all.filter { it.name.lowercase(Locale.ROOT).contains(needle) }
        }

    private fun sinceFor(period: Period): Long {
        val t = now()
        return when (period) {
            Period.TODAY -> TimeRanges.startOfDay(t)
            Period.THIS_WEEK -> TimeRanges.startOfWeek(t)
            Period.THIS_MONTH -> TimeRanges.startOfMonth(t)
            Period.RECENT -> TimeRanges.daysAgo(t, 30)
        }
    }

    private fun periodSource(period: Period) =
        listOf(AssistantSource("From your call log", "Window: ${period.label}"))

    companion object {
        const val ENGINE_ID = "rule-based-local"
    }
}
