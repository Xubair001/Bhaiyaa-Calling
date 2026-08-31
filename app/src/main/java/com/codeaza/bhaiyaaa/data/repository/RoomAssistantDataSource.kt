package com.codeaza.bhaiyaaa.data.repository

import com.codeaza.bhaiyaaa.ai.AssistantDataSource
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.db.entity.MemoryEntity
import com.codeaza.bhaiyaaa.data.db.entity.ReminderEntity
import com.codeaza.bhaiyaaa.data.db.projection.ContactCallCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Binds the assistant to Room. Every method is a direct query - the assistant
 * only ever sees rows that actually exist.
 */
class RoomAssistantDataSource(
    private val db: AppDatabase,
    private val repository: SukoonRepository
) : AssistantDataSource {

    override suspend fun allContacts(): List<ContactEntity> = withContext(Dispatchers.IO) {
        db.contactDao().allOnce()
    }

    override suspend fun vipContacts(): List<ContactEntity> = withContext(Dispatchers.IO) {
        db.contactDao().observeVip().first()
    }

    override suspend fun recentCalls(limit: Int): List<CallRecordEntity> = withContext(Dispatchers.IO) {
        db.callRecordDao().observeRecent(limit).first()
    }

    override suspend fun countCallsSince(since: Long): Int = withContext(Dispatchers.IO) {
        db.callRecordDao().countSince(since)
    }

    override suspend fun countMissedSince(since: Long): Int = withContext(Dispatchers.IO) {
        db.callRecordDao().missedSince(since)
    }

    override suspend fun mostContactedSince(since: Long, limit: Int): List<ContactCallCount> =
        withContext(Dispatchers.IO) { db.callRecordDao().mostContactedSince(since, limit) }

    override suspend fun searchMemories(query: String, limit: Int): List<MemoryEntity> =
        repository.searchMemories(query, limit)

    override suspend fun pendingReminders(): List<ReminderEntity> = withContext(Dispatchers.IO) {
        db.reminderDao().observeActive().first()
    }

    override suspend fun createReminder(text: String, dueAt: Long?): Long =
        repository.addReminder(text, dueAt)

    override suspend fun callsForMatchKey(matchKey: String, limit: Int): List<CallRecordEntity> =
        withContext(Dispatchers.IO) {
            db.callRecordDao().observeForContact(matchKey).first().take(limit)
        }
}
