package com.codeaza.bhaiyaaa.assistant

import com.codeaza.bhaiyaaa.data.db.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.ContactEntity
import com.codeaza.bhaiyaaa.util.VipLevel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * This is deliberately NOT a language model. Per the original spec's own
 * anti-hallucination rule, this matches a few keyword intents and answers
 * every question by querying your real local data directly - so it can
 * never invent a call, a contact, or a note that doesn't exist.
 */
sealed class AssistantResult {
    data class Reply(val text: String) : AssistantResult()
    data class ReminderCreated(val reminderText: String, val reply: String) : AssistantResult()
}

object AssistantEngine {

    fun process(
        rawQuery: String,
        contacts: List<ContactEntity>,
        calls: List<CallRecordEntity>
    ): AssistantResult {
        val q = rawQuery.trim().lowercase(Locale.getDefault())
        if (q.isBlank()) {
            return AssistantResult.Reply("Say something, bhai - ask about calls, VIPs, or say 'remind me to...'.")
        }

        return when {
            q.startsWith("remind me") -> {
                AssistantResult.ReminderCreated(
                    rawQuery.trim(),
                    "Added to your reminders list, bhai."
                )
            }

            q.contains("missed") -> {
                val count = calls.count { it.type == "MISSED" }
                AssistantResult.Reply(
                    if (count == 0) "No missed calls in your recent history, boss."
                    else "You've got $count missed call(s) in your recent history, bhai."
                )
            }

            q.contains("vip") -> {
                val vips = contacts.filter { it.vipLevel != VipLevel.NONE }
                if (vips.isEmpty()) {
                    AssistantResult.Reply("No VIP contacts set yet, boss. Go tag someone in Contacts.")
                } else {
                    val list = vips.joinToString(", ") { "${it.name} (${VipLevel.label(it.vipLevel)})" }
                    AssistantResult.Reply("Your VIPs: $list")
                }
            }

            q.contains("last call") || q.contains("last talk") || q.contains("when did i") -> {
                val name = extractName(q)
                val match = calls.firstOrNull { it.contactName?.contains(name, ignoreCase = true) == true }
                if (name.isBlank() || match == null) {
                    AssistantResult.Reply("Couldn't find a recent call matching that name, bhai.")
                } else {
                    AssistantResult.Reply(
                        "Last call with ${match.contactName} was on ${formatDate(match.timestamp)}."
                    )
                }
            }

            q.contains("today") -> {
                val startOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val count = calls.count { it.timestamp >= startOfDay }
                AssistantResult.Reply("You've had $count call(s) today.")
            }

            q.contains("recent") || q.contains("who called") -> {
                val recent = calls.take(3).joinToString(", ") { it.contactName ?: it.phoneNumber }
                if (recent.isBlank()) {
                    AssistantResult.Reply("No calls logged yet, bhai.")
                } else {
                    AssistantResult.Reply("Recent callers: $recent")
                }
            }

            else -> AssistantResult.Reply(
                "Try asking: missed calls, VIP contacts, calls today, recent calls, " +
                    "'last call with [name]', or 'remind me to...'."
            )
        }
    }

    private fun extractName(query: String): String {
        val markers = listOf(" with ", " to ", " from ")
        for (marker in markers) {
            val idx = query.indexOf(marker)
            if (idx != -1) {
                return query.substring(idx + marker.length).trim().removeSuffix("?")
            }
        }
        return ""
    }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(timestamp)
}
