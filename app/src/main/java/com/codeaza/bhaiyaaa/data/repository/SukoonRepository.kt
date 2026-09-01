package com.codeaza.bhaiyaaa.data.repository

import androidx.room.withTransaction
import android.content.Context
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.db.entity.MemoryEntity
import com.codeaza.bhaiyaaa.data.db.entity.NotificationRuleEntity
import com.codeaza.bhaiyaaa.data.db.entity.ReminderEntity
import com.codeaza.bhaiyaaa.data.db.entity.TagEntity
import com.codeaza.bhaiyaaa.data.db.projection.ContactCallCount
import com.codeaza.bhaiyaaa.data.db.projection.ContactStats
import com.codeaza.bhaiyaaa.domain.model.ContactTag
import com.codeaza.bhaiyaaa.domain.model.MemorySource
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.prayer.PrayerTimeCalculator
import com.codeaza.bhaiyaaa.util.PhoneNumbers
import com.codeaza.bhaiyaaa.util.TimeRanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * The app's single door onto local data. Everything is on-device: there is no
 * network call anywhere in this class, and no data leaves the phone.
 */
class SukoonRepository(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.getInstance(context),
    private val deviceContacts: DeviceContactsRepository = DeviceContactsRepository(context),
    private val deviceCallLog: DeviceCallLogRepository = DeviceCallLogRepository(context),
    private val now: () -> Long = System::currentTimeMillis
) {
    private val contactDao get() = db.contactDao()
    private val callDao get() = db.callRecordDao()
    private val memoryDao get() = db.memoryDao()
    private val reminderDao get() = db.reminderDao()
    private val tagDao get() = db.tagDao()
    private val ruleDao get() = db.notificationRuleDao()

    // ------------------------------------------------------------- observers

    val contacts: Flow<List<ContactEntity>> = contactDao.observeAll()
    val vipContacts: Flow<List<ContactEntity>> = contactDao.observeVip()
    val vipCount: Flow<Int> = contactDao.observeVipCount()
    val calls: Flow<List<CallRecordEntity>> = callDao.observeAll()
    val reminders: Flow<List<ReminderEntity>> = reminderDao.observeActive()
    val pendingReminderCount: Flow<Int> = reminderDao.observePendingCount()
    val doneReminders: Flow<List<ReminderEntity>> = reminderDao.observeDone()
    val memories: Flow<List<MemoryEntity>> = memoryDao.observeAll()
    val nonPrivateMemories: Flow<List<MemoryEntity>> = memoryDao.observeNonPrivate()
    val memoryCount: Flow<Int> = memoryDao.observeCount()
    val tags: Flow<List<TagEntity>> = tagDao.observeAll()
    val callStats: Flow<List<ContactStats>> = callDao.observeAllStats()
    val notificationRules: Flow<List<NotificationRuleEntity>> = ruleDao.observeAll()

    fun callsForContact(matchKey: String): Flow<List<CallRecordEntity>> =
        callDao.observeForContact(matchKey)

    fun memoriesForContact(phoneNumber: String): Flow<List<MemoryEntity>> =
        memoryDao.observeForContact(phoneNumber)

    /**
     * Resolves a contact for a detail screen.
     *
     * Tries the exact primary key first, then falls back to the suffix match
     * key. The fallback matters because the number reaching this function has
     * been through navigation-route encoding and may not be byte-identical to
     * the stored key - without it, opening a contact shows "not found" for a
     * number the database actually holds.
     */
    fun observeContactResolved(rawNumber: String): Flow<ContactEntity?> {
        val exact = contactDao.observeByPhoneNumber(rawNumber)
        val normalized = PhoneNumbers.normalize(rawNumber)
        val byKey = contactDao.observeByMatchKey(PhoneNumbers.matchKey(rawNumber))
        val byNormalized = contactDao.observeByPhoneNumber(normalized)
        return combine(exact, byNormalized, byKey) { a, b, c -> a ?: b ?: c }
    }

    fun observeContact(phoneNumber: String): Flow<ContactEntity?> =
        contactDao.observeByPhoneNumber(phoneNumber)

    fun observeCall(id: Long): Flow<CallRecordEntity?> = callDao.observeById(id)

    fun observeStatsFor(matchKey: String): Flow<ContactStats?> =
        callDao.observeStatsForContact(matchKey)

    // ------------------------------------------------------------------ sync

    /**
     * Pulls contacts and new calls from the device.
     *
     * Both reads are permission-gated and return empty when denied, so a partial
     * grant (contacts yes, call log no) still syncs the half it is allowed to.
     * Call reads are incremental from the newest timestamp already stored.
     */
    suspend fun syncFromDevice(): SyncResult = withContext(Dispatchers.IO) {
        val timestamp = now()
        var contactsAdded = 0
        var callsAdded = 0

        if (deviceContacts.hasPermission()) {
            val fromDevice = deviceContacts.readDeviceContacts(timestamp)
            if (fromDevice.isNotEmpty()) {
                val before = contactDao.count()
                contactDao.insertIfAbsent(fromDevice)
                contactsAdded = contactDao.count() - before
                // Refresh only device-owned columns; VIP tier, tags and notes
                // the user set are never touched by a sync.
                //
                // In one transaction. Without it this is a separate SQLite
                // transaction - and an fsync - per contact, so a phone with
                // two thousand contacts did two thousand of them on every
                // periodic sync. Batched, it is one.
                db.withTransaction {
                    fromDevice.forEach {
                        contactDao.refreshDeviceFields(
                            it.phoneNumber,
                            it.name,
                            it.matchKey,
                            timestamp
                        )
                    }
                }
            }
        }

        if (deviceCallLog.hasPermission()) {
            val since = callDao.latestTimestamp() ?: 0L
            val fresh = deviceCallLog.readCalls(sinceMillis = since)
            if (fresh.isNotEmpty()) {
                val inserted = callDao.insertIfAbsent(fresh)
                callsAdded = inserted.count { it != -1L }
            }
        }

        SyncResult(
            contactsAdded = contactsAdded,
            callsAdded = callsAdded,
            contactsPermission = deviceContacts.hasPermission(),
            callLogPermission = deviceCallLog.hasPermission(),
            contactsError = deviceContacts.lastError,
            callLogError = deviceCallLog.lastError,
            storedContacts = contactDao.count(),
            storedCalls = callDao.allOnce().size
        )
    }

    data class SyncResult(
        val contactsAdded: Int,
        val callsAdded: Int,
        val contactsPermission: Boolean,
        val callLogPermission: Boolean,
        /** Non-null when the provider itself refused or failed the read. */
        val contactsError: String? = null,
        val callLogError: String? = null,
        val storedContacts: Int = 0,
        val storedCalls: Int = 0
    ) {
        /**
         * True when Sukoon holds the permission but still ended up with
         * nothing - the case worth telling the user about, because it means
         * something other than a missing permission went wrong.
         */
        val readFailedDespitePermission: Boolean
            get() = (contactsPermission && contactsError != null) ||
                (callLogPermission && callLogError != null)
    }

    /** Seeds built-in tags and default alert rules. Idempotent - safe every launch. */
    suspend fun seedDefaults() = withContext(Dispatchers.IO) {
        tagDao.insertIfAbsent(
            ContactTag.BUILT_IN.mapIndexed { index, name ->
                TagEntity(name = name, colorArgb = TAG_COLORS[index % TAG_COLORS.size], isBuiltIn = true, sortOrder = index)
            }
        )
        ruleDao.insertIfAbsent(NotificationRuleEntity.allDefaults())
        db.prayerDao().insertIfAbsent(PrayerTimeCalculator.defaultPrayerRows())
    }

    // -------------------------------------------------------------- contacts

    suspend fun findContact(phoneNumber: String) = withContext(Dispatchers.IO) {
        contactDao.findByPhoneNumber(phoneNumber)
    }

    suspend fun findContactByNumber(rawNumber: String) = withContext(Dispatchers.IO) {
        contactDao.findByMatchKey(PhoneNumbers.matchKey(rawNumber))
    }

    suspend fun statsFor(matchKey: String): ContactStats? = withContext(Dispatchers.IO) {
        callDao.statsForContact(matchKey)
    }

    suspend fun setVipLevel(phoneNumber: String, level: VipLevel) = withContext(Dispatchers.IO) {
        contactDao.setVipLevel(phoneNumber, level.storageValue, now())
    }

    suspend fun setTag(phoneNumber: String, tag: String?) = withContext(Dispatchers.IO) {
        contactDao.setTag(phoneNumber, tag, now())
    }

    suspend fun setRelationship(phoneNumber: String, relationship: String?) = withContext(Dispatchers.IO) {
        contactDao.setRelationship(phoneNumber, relationship, now())
    }

    suspend fun setImportance(phoneNumber: String, importance: Int) = withContext(Dispatchers.IO) {
        contactDao.setImportance(phoneNumber, importance, now())
    }

    suspend fun setNotes(phoneNumber: String, notes: String?) = withContext(Dispatchers.IO) {
        contactDao.setNotes(phoneNumber, notes?.takeIf { it.isNotBlank() }, now())
    }

    suspend fun setSpam(phoneNumber: String, isSpam: Boolean) = withContext(Dispatchers.IO) {
        contactDao.setSpam(phoneNumber, isSpam, now())
    }

    suspend fun setContactNotifications(phoneNumber: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        contactDao.setNotificationsEnabled(phoneNumber, enabled, now())
    }

    suspend fun setCustomRingtone(phoneNumber: String, uri: String?) = withContext(Dispatchers.IO) {
        contactDao.setCustomRingtone(phoneNumber, uri, now())
    }

    suspend fun upsertTag(tag: TagEntity) = withContext(Dispatchers.IO) { tagDao.upsert(tag) }

    suspend fun deleteCustomTag(name: String) = withContext(Dispatchers.IO) { tagDao.deleteCustom(name) }

    // ------------------------------------------------------------------ calls

    suspend fun setCallImportant(id: Long, important: Boolean) = withContext(Dispatchers.IO) {
        callDao.setImportant(id, important)
    }

    suspend fun setCallNote(id: Long, note: String?) = withContext(Dispatchers.IO) {
        callDao.setNote(id, note?.takeIf { it.isNotBlank() })
    }

    suspend fun findCall(id: Long) = withContext(Dispatchers.IO) { callDao.findById(id) }

    suspend fun missedCallsToday(): Int = withContext(Dispatchers.IO) {
        callDao.missedSince(TimeRanges.startOfDay(now()))
    }

    suspend fun mostContactedSince(since: Long, limit: Int): List<ContactCallCount> =
        withContext(Dispatchers.IO) { callDao.mostContactedSince(since, limit) }

    // --------------------------------------------------------------- memories

    suspend fun addMemory(
        body: String,
        title: String? = null,
        contactPhoneNumber: String? = null,
        source: MemorySource = MemorySource.MANUAL,
        callRecordId: Long? = null,
        isPrivate: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val timestamp = now()
        memoryDao.insert(
            MemoryEntity(
                contactPhoneNumber = contactPhoneNumber,
                title = title?.takeIf { it.isNotBlank() },
                body = body.trim(),
                source = source.storageValue,
                callRecordId = callRecordId,
                isPrivate = isPrivate,
                createdAt = timestamp,
                updatedAt = timestamp
            )
        )
    }

    suspend fun updateMemory(memory: MemoryEntity) = withContext(Dispatchers.IO) {
        memoryDao.update(memory.copy(updatedAt = now()))
    }

    suspend fun deleteMemory(id: Long) = withContext(Dispatchers.IO) { memoryDao.deleteById(id) }

    /**
     * Full-text search with a substring fallback. FTS handles ordinary words;
     * the LIKE path catches short or punctuation-heavy queries FTS can't express
     * so a search never comes back empty just because of tokenisation.
     */
    suspend fun searchMemories(query: String, limit: Int = 50): List<MemoryEntity> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext emptyList()
            val fts = MemorySearch.toFtsQuery(trimmed)
            val hits = if (fts != null) {
                runCatching { memoryDao.searchFts(fts, limit) }.getOrElse {
                    memoryDao.searchLike(trimmed, limit)
                }
            } else {
                memoryDao.searchLike(trimmed, limit)
            }
            val result = hits.ifEmpty { memoryDao.searchLike(trimmed, limit) }
            MemorySearch.rank(result, trimmed) { "${it.title.orEmpty()} ${it.body}" }
        }

    // -------------------------------------------------------------- reminders

    suspend fun addReminder(text: String, dueAt: Long? = null, contactPhoneNumber: String? = null): Long =
        withContext(Dispatchers.IO) {
            reminderDao.insert(
                ReminderEntity(
                    text = text.trim(),
                    contactPhoneNumber = contactPhoneNumber,
                    createdAt = now(),
                    dueAt = dueAt
                )
            )
        }

    suspend fun setReminderDone(id: Long, done: Boolean) = withContext(Dispatchers.IO) {
        reminderDao.setDone(id, done)
    }

    suspend fun editReminder(id: Long, text: String, dueAt: Long?) = withContext(Dispatchers.IO) {
        reminderDao.edit(id, text.trim(), dueAt)
    }

    suspend fun snoozeReminder(id: Long, until: Long) = withContext(Dispatchers.IO) {
        reminderDao.rescheduleTo(id, until)
    }

    suspend fun deleteReminder(id: Long) = withContext(Dispatchers.IO) { reminderDao.deleteById(id) }

    suspend fun findReminder(id: Long) = withContext(Dispatchers.IO) { reminderDao.findById(id) }

    suspend fun markReminderNotified(id: Long) = withContext(Dispatchers.IO) {
        reminderDao.markNotified(id)
    }

    suspend fun pendingScheduledReminders() = withContext(Dispatchers.IO) {
        reminderDao.pendingScheduled()
    }

    // ---------------------------------------------------------------- privacy

    suspend fun resetVipAndAnnotations() = withContext(Dispatchers.IO) {
        contactDao.resetAllUserFields(now())
    }

    suspend fun clearCallHistory() = withContext(Dispatchers.IO) { callDao.deleteAll() }

    suspend fun clearMemories() = withContext(Dispatchers.IO) { memoryDao.deleteAll() }

    suspend fun clearReminders() = withContext(Dispatchers.IO) { reminderDao.deleteAll() }

    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        memoryDao.deleteAll()
        reminderDao.deleteAll()
        callDao.deleteAll()
        contactDao.deleteAll()
    }

    // ------------------------------------------------------------ alert rules

    suspend fun ruleFor(level: VipLevel): NotificationRuleEntity? = withContext(Dispatchers.IO) {
        ruleDao.findForLevel(level.storageValue)
    }

    /**
     * The rule for a tier, creating it from the defaults if it is missing.
     *
     * Callers that write a setting must never silently no-op because a row was
     * absent - that turns a seeding failure into a toggle that appears to work
     * and then forgets.
     */
    suspend fun ruleForOrCreate(level: VipLevel): NotificationRuleEntity =
        withContext(Dispatchers.IO) {
            ruleDao.findForLevel(level.storageValue) ?: run {
                val created = NotificationRuleEntity.defaultFor(level.storageValue)
                ruleDao.upsert(created)
                created
            }
        }

    suspend fun saveRule(rule: NotificationRuleEntity) = withContext(Dispatchers.IO) {
        ruleDao.upsert(rule)
    }

    companion object {
        private val TAG_COLORS = intArrayOf(
            0xFF2E7D57.toInt(), // Family
            0xFF2F6FB0.toInt(), // Friends
            0xFF8A5B22.toInt(), // Work
            0xFF6A4C93.toInt(), // Client
            0xFFB3261E.toInt(), // Important
            0xFF5F6368.toInt(), // Unknown
            0xFF7A2E2E.toInt()  // Spam
        )
    }
}
