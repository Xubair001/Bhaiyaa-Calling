package com.codeaza.bhaiyaaa.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.projection.ContactCallCount
import com.codeaza.bhaiyaaa.data.db.projection.ContactStats
import com.codeaza.bhaiyaaa.data.db.projection.DailyCallCount
import com.codeaza.bhaiyaaa.data.db.projection.HourlyCallCount
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordDao {

    @Query("SELECT * FROM call_records ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records WHERE matchKey = :matchKey ORDER BY timestamp DESC")
    fun observeForContact(matchKey: String): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CallRecordEntity?

    /** Observable form, so a detail screen reflects its own edits immediately. */
    @Query("SELECT * FROM call_records WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<CallRecordEntity?>

    /**
     * IGNORE, not REPLACE: the primary key is the device call-log id, so a
     * re-sync re-offers rows we already have. Ignoring them keeps sync
     * idempotent and preserves the user's own `isImportant` / `note` edits.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(calls: List<CallRecordEntity>): List<Long>

    @Query("UPDATE call_records SET isImportant = :important WHERE id = :id")
    suspend fun setImportant(id: Long, important: Boolean)

    @Query("UPDATE call_records SET note = :note WHERE id = :id")
    suspend fun setNote(id: Long, note: String?)

    @Query("SELECT COUNT(*) FROM call_records WHERE type = 'MISSED' AND timestamp >= :sinceMillis")
    suspend fun missedSince(sinceMillis: Long): Int

    @Query("SELECT COUNT(*) FROM call_records WHERE timestamp >= :sinceMillis")
    suspend fun countSince(sinceMillis: Long): Int

    @Query("SELECT COUNT(*) FROM call_records WHERE type = :type AND timestamp >= :sinceMillis")
    suspend fun countByTypeSince(type: String, sinceMillis: Long): Int

    @Query("SELECT MAX(timestamp) FROM call_records")
    suspend fun latestTimestamp(): Long?

    /** Per-contact aggregates. Duration is summed over answered calls only. */
    @Query(
        """
        SELECT matchKey AS matchKey,
               COUNT(*) AS totalCalls,
               SUM(CASE WHEN type = 'INCOMING' THEN 1 ELSE 0 END) AS incomingCalls,
               SUM(CASE WHEN type = 'OUTGOING' THEN 1 ELSE 0 END) AS outgoingCalls,
               SUM(CASE WHEN type = 'MISSED' THEN 1 ELSE 0 END) AS missedCalls,
               SUM(CASE WHEN durationSeconds > 0 THEN durationSeconds ELSE 0 END) AS answeredDurationSeconds,
               SUM(CASE WHEN durationSeconds > 0 THEN 1 ELSE 0 END) AS answeredCalls,
               MAX(timestamp) AS lastCallAt
        FROM call_records
        WHERE matchKey = :matchKey
        """
    )
    suspend fun statsForContact(matchKey: String): ContactStats?

    /** Observable form of [statsForContact], for the contact detail screen. */
    @Query(
        """
        SELECT matchKey AS matchKey,
               COUNT(*) AS totalCalls,
               SUM(CASE WHEN type = 'INCOMING' THEN 1 ELSE 0 END) AS incomingCalls,
               SUM(CASE WHEN type = 'OUTGOING' THEN 1 ELSE 0 END) AS outgoingCalls,
               SUM(CASE WHEN type = 'MISSED' THEN 1 ELSE 0 END) AS missedCalls,
               SUM(CASE WHEN durationSeconds > 0 THEN durationSeconds ELSE 0 END) AS answeredDurationSeconds,
               SUM(CASE WHEN durationSeconds > 0 THEN 1 ELSE 0 END) AS answeredCalls,
               MAX(timestamp) AS lastCallAt
        FROM call_records
        WHERE matchKey = :matchKey
        """
    )
    fun observeStatsForContact(matchKey: String): Flow<ContactStats?>

    @Query(
        """
        SELECT matchKey AS matchKey,
               COUNT(*) AS totalCalls,
               SUM(CASE WHEN type = 'INCOMING' THEN 1 ELSE 0 END) AS incomingCalls,
               SUM(CASE WHEN type = 'OUTGOING' THEN 1 ELSE 0 END) AS outgoingCalls,
               SUM(CASE WHEN type = 'MISSED' THEN 1 ELSE 0 END) AS missedCalls,
               SUM(CASE WHEN durationSeconds > 0 THEN durationSeconds ELSE 0 END) AS answeredDurationSeconds,
               SUM(CASE WHEN durationSeconds > 0 THEN 1 ELSE 0 END) AS answeredCalls,
               MAX(timestamp) AS lastCallAt
        FROM call_records
        WHERE matchKey != ''
        GROUP BY matchKey
        """
    )
    fun observeAllStats(): Flow<List<ContactStats>>

    /** Most-contacted people in a window. Falls back to the number when no name is cached. */
    @Query(
        """
        SELECT matchKey AS matchKey,
               MAX(IFNULL(contactName, phoneNumber)) AS displayName,
               COUNT(*) AS callCount,
               SUM(durationSeconds) AS totalDurationSeconds
        FROM call_records
        WHERE timestamp >= :sinceMillis AND matchKey != ''
        GROUP BY matchKey
        ORDER BY callCount DESC, totalDurationSeconds DESC
        LIMIT :limit
        """
    )
    suspend fun mostContactedSince(sinceMillis: Long, limit: Int): List<ContactCallCount>

    /** Calls from anyone currently marked VIP, at any tier. */
    @Query(
        """
        SELECT COUNT(*) FROM call_records AS c
        JOIN contacts AS ct ON ct.matchKey = c.matchKey
        WHERE c.timestamp >= :sinceMillis AND ct.vipLevel != 'NONE'
        """
    )
    suspend fun vipCallCountSince(sinceMillis: Long): Int

    /** Missed calls from contacts the user marked HIGH or CRITICAL importance. */
    @Query(
        """
        SELECT COUNT(*) FROM call_records AS c
        JOIN contacts AS ct ON ct.matchKey = c.matchKey
        WHERE c.timestamp >= :sinceMillis AND c.type = 'MISSED'
          AND (ct.vipLevel != 'NONE' OR ct.importance >= 2)
        """
    )
    suspend fun missedImportantSince(sinceMillis: Long): Int

    @Query(
        """
        SELECT * FROM call_records
        WHERE durationSeconds > 0
        ORDER BY durationSeconds DESC
        LIMIT :limit
        """
    )
    suspend fun longestCalls(limit: Int): List<CallRecordEntity>

    /**
     * Buckets calls into local-midnight-aligned days. The offset shifts UTC
     * epoch millis into the device's local day before integer-dividing, so bars
     * line up with the user's calendar rather than with UTC.
     */
    @Query(
        """
        SELECT (((timestamp + :utcOffsetMillis) / 86400000) * 86400000) - :utcOffsetMillis AS dayStartMillis,
               COUNT(*) AS callCount
        FROM call_records
        WHERE timestamp >= :sinceMillis
        GROUP BY dayStartMillis
        ORDER BY dayStartMillis ASC
        """
    )
    suspend fun dailyCountsSince(sinceMillis: Long, utcOffsetMillis: Long): List<DailyCallCount>

    @Query(
        """
        SELECT CAST(strftime('%H', datetime((timestamp + :utcOffsetMillis) / 1000, 'unixepoch')) AS INTEGER) AS hourOfDay,
               COUNT(*) AS callCount
        FROM call_records
        WHERE timestamp >= :sinceMillis
        GROUP BY hourOfDay
        ORDER BY callCount DESC
        """
    )
    suspend fun hourlyCountsSince(sinceMillis: Long, utcOffsetMillis: Long): List<HourlyCallCount>

    @Query(
        """
        SELECT * FROM call_records
        WHERE IFNULL(contactName,'') LIKE '%' || :q || '%'
           OR phoneNumber LIKE '%' || :q || '%'
           OR IFNULL(note,'') LIKE '%' || :q || '%'
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun search(q: String, limit: Int = 50): List<CallRecordEntity>

    @Query("SELECT * FROM call_records ORDER BY timestamp DESC")
    suspend fun allOnce(): List<CallRecordEntity>

    @Query("DELETE FROM call_records")
    suspend fun deleteAll()
}
