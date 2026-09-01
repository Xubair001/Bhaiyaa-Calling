package com.codeaza.bhaiyaaa.data.repository

import com.codeaza.bhaiyaaa.ai.AssistantDataSource
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.db.entity.MemoryEntity
import com.codeaza.bhaiyaaa.data.db.entity.PrayerEntity
import com.codeaza.bhaiyaaa.data.db.entity.ReminderEntity
import com.codeaza.bhaiyaaa.data.db.projection.ContactCallCount
import com.codeaza.bhaiyaaa.data.prefs.SettingsRepository
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.domain.model.PrayerSettings
import com.codeaza.bhaiyaaa.domain.model.SilenceWindow
import com.codeaza.bhaiyaaa.prayer.PrayerTimeCalculator
import com.codeaza.bhaiyaaa.prayer.SilencePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Binds the assistant to Room. Every method is a direct query - the assistant
 * only ever sees rows that actually exist.
 */
class RoomAssistantDataSource(
    private val context: android.content.Context,
    private val db: AppDatabase,
    private val repository: SukoonRepository
) : AssistantDataSource {

    /**
     * Held rather than constructed per call.
     *
     * Two days of quiet windows used to mean two DataStore reads and four DAO
     * reads for one "when is the next prayer" - the same three answers fetched
     * twice because each day planned itself from scratch. [quietPlan] reads
     * once and plans both.
     */
    private val settingsRepo = SettingsRepository(context)

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

    override suspend fun activeQuietWindow(): SilenceWindow? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        SilencePlan.activeWindow(quietPlan(now, includeTomorrow = false), now)
    }

    override suspend fun nextQuietWindow(): SilenceWindow? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Today and tomorrow, so "when is the next prayer" asked late at night
        // answers with tomorrow's Fajr rather than with nothing.
        SilencePlan.nextWindow(quietPlan(now, includeTomorrow = true), now)
    }

    override suspend fun prayerTimesToday(): Map<Prayer, Long> = withContext(Dispatchers.IO) {
        val (settings, prayers) = prayerInputs()
        PrayerTimeCalculator.anchorsForDay(
            settings = settings,
            prayers = prayers,
            dayStartMillis = System.currentTimeMillis(),
            zone = settings.zone
        )
    }

    override suspend fun adhanEnabled(): Boolean = withContext(Dispatchers.IO) {
        settingsRepo.settings.first().prayer.adhan.enabled
    }

    /** Settings and prayer rows, read once for whatever needs both. */
    private suspend fun prayerInputs(): Pair<PrayerSettings, List<PrayerEntity>> =
        settingsRepo.settings.first().prayer to db.prayerDao().allOnce()

    /**
     * Every quiet window for today, optionally with tomorrow's as well.
     *
     * @param includeTomorrow only for "what is next", which can legitimately
     *   be tomorrow's Fajr. Asking what is running *now* never needs tomorrow,
     *   and planning it would be a second solar calculation for an answer that
     *   is discarded.
     */
    private suspend fun quietPlan(now: Long, includeTomorrow: Boolean): List<SilenceWindow> {
        val (settings, prayers) = prayerInputs()
        val schedules = db.silenceScheduleDao().allOnce()
        val days = if (includeTomorrow) listOf(now, now + DAY_MILLIS) else listOf(now)
        return days.flatMap { day ->
            SilencePlan.windowsForDay(
                settings = settings,
                prayers = prayers,
                schedules = schedules,
                dayStartMillis = day,
                zone = settings.zone
            )
        }
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
