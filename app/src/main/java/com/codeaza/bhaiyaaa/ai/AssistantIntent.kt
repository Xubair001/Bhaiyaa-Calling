package com.codeaza.bhaiyaaa.ai

/** A time window an intent can ask about. */
enum class Period(val label: String) {
    TODAY("today"),
    THIS_WEEK("this week"),
    THIS_MONTH("this month"),
    RECENT("recently")
}

/**
 * What the user asked for, once parsed. Keeping intents as data - rather than
 * letting free text reach the query layer - is what stops the assistant from
 * inventing answers: every intent below maps to a concrete database query.
 */
sealed interface AssistantIntent {
    data class MissedCalls(val period: Period) : AssistantIntent
    data object VipList : AssistantIntent
    data class CallCount(val period: Period) : AssistantIntent
    data class MostContacted(val period: Period) : AssistantIntent
    data class LastCallWith(val name: String) : AssistantIntent
    data object RecentCallers : AssistantIntent
    data class CreateReminder(val text: String, val dueAt: Long?) : AssistantIntent
    data class RecallMemory(val query: String) : AssistantIntent
    data class ContactLookup(val name: String) : AssistantIntent
    data object PendingReminders : AssistantIntent
    data object Help : AssistantIntent
    data object Unknown : AssistantIntent
}
