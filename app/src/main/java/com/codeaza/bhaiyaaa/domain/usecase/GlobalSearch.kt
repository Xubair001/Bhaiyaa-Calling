package com.codeaza.bhaiyaaa.domain.usecase

import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.db.entity.MemoryEntity
import com.codeaza.bhaiyaaa.data.db.entity.ReminderEntity
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.repository.BhaiyaaaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** One search across everything BHAIYAAA stores locally. */
data class SearchResults(
    val query: String = "",
    val contacts: List<ContactEntity> = emptyList(),
    val calls: List<CallRecordEntity> = emptyList(),
    val memories: List<MemoryEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList()
) {
    val total: Int get() = contacts.size + calls.size + memories.size + reminders.size
    val isEmpty: Boolean get() = total == 0
}

/**
 * Offline search across contacts, calls, memories and reminders (brief §30).
 *
 * Memories go through the FTS4 index; the other three are indexed substring
 * queries, which is the right trade for tables this size - a personal call log
 * is thousands of rows, not millions, and an FTS table per entity would cost
 * more in write amplification than it saves on read.
 *
 * The four queries run concurrently, so the slowest one sets the latency rather
 * than the sum of all four.
 */
class GlobalSearch(
    private val db: AppDatabase,
    private val repository: BhaiyaaaRepository
) {

    suspend fun search(rawQuery: String, limitPerSection: Int = 20): SearchResults {
        val query = rawQuery.trim()
        // One character matches almost everything and just churns the database.
        if (query.length < MIN_QUERY_LENGTH) return SearchResults(query = query)

        return withContext(Dispatchers.IO) {
            coroutineScope {
                val contacts = async { db.contactDao().search(query, limitPerSection) }
                val calls = async { db.callRecordDao().search(query, limitPerSection) }
                val memories = async { repository.searchMemories(query, limitPerSection) }
                val reminders = async { db.reminderDao().search(query, limitPerSection) }

                SearchResults(
                    query = query,
                    contacts = contacts.await(),
                    calls = calls.await(),
                    memories = memories.await(),
                    reminders = reminders.await()
                )
            }
        }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
