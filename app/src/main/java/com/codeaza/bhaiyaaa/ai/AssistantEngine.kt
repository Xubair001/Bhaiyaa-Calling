package com.codeaza.bhaiyaaa.ai

import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.db.entity.MemoryEntity
import com.codeaza.bhaiyaaa.data.db.entity.ReminderEntity
import com.codeaza.bhaiyaaa.data.db.projection.ContactCallCount
import com.codeaza.bhaiyaaa.domain.model.SilenceWindow

/** An action the assistant actually carried out, so the UI can reflect it. */
sealed interface AssistantAction {
    data class ReminderCreated(val id: Long, val text: String, val dueAt: Long?) : AssistantAction

    /**
     * The engine decided the phone should go quiet, but did not do it.
     *
     * Silencing is a platform action, not a database one. Keeping it out of the
     * engine is what lets the engine stay pure and unit-testable - the caller
     * applies it, exactly as it does for a reminder's alarm.
     */
    data class SilenceRequested(val minutes: Int) : AssistantAction
}

/**
 * Where an answer's facts came from. Surfaced in the UI so the user can always
 * check Sukoon's working rather than taking a sentence on trust.
 */
data class AssistantSource(val label: String, val detail: String)

data class AssistantResponse(
    val text: String,
    val intent: AssistantIntent,
    val action: AssistantAction? = null,
    val sources: List<AssistantSource> = emptyList()
)

/**
 * The swappable AI boundary (brief §38). [RuleBasedAssistantEngine] is the
 * always-available default that needs no model download; a future engine
 * backed by a local llama.cpp / ONNX model can implement the same interface
 * and be selected in Settings → AI Models without the UI changing.
 */
interface AssistantEngine {
    val id: String
    val displayName: String

    /** True when this engine can run right now (e.g. its model is installed). */
    suspend fun isAvailable(): Boolean

    suspend fun respond(input: String): AssistantResponse
}

/**
 * Every read the assistant is allowed to make. Narrow on purpose: the engine
 * can only answer with what these methods return, so it has nothing to
 * hallucinate from.
 */
interface AssistantDataSource {
    suspend fun allContacts(): List<ContactEntity>
    suspend fun vipContacts(): List<ContactEntity>
    suspend fun recentCalls(limit: Int): List<CallRecordEntity>
    suspend fun countCallsSince(since: Long): Int
    suspend fun countMissedSince(since: Long): Int
    suspend fun mostContactedSince(since: Long, limit: Int): List<ContactCallCount>
    suspend fun searchMemories(query: String, limit: Int): List<MemoryEntity>
    suspend fun pendingReminders(): List<ReminderEntity>
    suspend fun createReminder(text: String, dueAt: Long?): Long
    suspend fun callsForMatchKey(matchKey: String, limit: Int): List<CallRecordEntity>

    /** The window currently silencing the phone, if any. */
    suspend fun activeQuietWindow(): SilenceWindow?

    /** The next window due to start, prayer or custom. */
    suspend fun nextQuietWindow(): SilenceWindow?
}
