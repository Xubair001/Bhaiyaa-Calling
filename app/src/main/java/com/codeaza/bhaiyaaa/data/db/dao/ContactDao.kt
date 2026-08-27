package com.codeaza.bhaiyaaa.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE vipLevel != 'NONE' ORDER BY name COLLATE NOCASE ASC")
    fun observeVip(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun findByPhoneNumber(phoneNumber: String): ContactEntity?

    /**
     * VIP lookup for an incoming call. Matches on the suffix key rather than the
     * raw string, so "+92 300 1234567" in contacts still matches "03001234567"
     * as reported by the telephony broadcast. Highest VIP tier wins if a number
     * somehow appears twice.
     */
    @Query(
        """
        SELECT * FROM contacts
        WHERE matchKey = :matchKey AND matchKey != ''
        ORDER BY CASE vipLevel
                    WHEN 'EMERGENCY' THEN 3
                    WHEN 'SUPER_VIP' THEN 2
                    WHEN 'VIP' THEN 1
                    ELSE 0 END DESC
        LIMIT 1
        """
    )
    suspend fun findByMatchKey(matchKey: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE matchKey = :matchKey AND matchKey != '' LIMIT 1")
    fun observeByMatchKey(matchKey: String): Flow<ContactEntity?>

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phoneNumber LIMIT 1")
    fun observeByPhoneNumber(phoneNumber: String): Flow<ContactEntity?>

    /**
     * Device sync uses IGNORE so re-importing contacts never overwrites a VIP
     * tier, tag or note the user set. Names are refreshed separately by
     * [refreshDeviceFields], which only touches device-owned columns.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(contacts: List<ContactEntity>)

    @Query("UPDATE contacts SET name = :name, matchKey = :matchKey, updatedAt = :now WHERE phoneNumber = :phoneNumber")
    suspend fun refreshDeviceFields(phoneNumber: String, name: String, matchKey: String, now: Long)

    @Upsert
    suspend fun upsert(contact: ContactEntity)

    @Query("UPDATE contacts SET vipLevel = :level, updatedAt = :now WHERE phoneNumber = :phoneNumber")
    suspend fun setVipLevel(phoneNumber: String, level: String, now: Long)

    @Query("UPDATE contacts SET tag = :tag, updatedAt = :now WHERE phoneNumber = :phoneNumber")
    suspend fun setTag(phoneNumber: String, tag: String?, now: Long)

    @Query("UPDATE contacts SET relationship = :relationship, updatedAt = :now WHERE phoneNumber = :phoneNumber")
    suspend fun setRelationship(phoneNumber: String, relationship: String?, now: Long)

    @Query("UPDATE contacts SET importance = :importance, updatedAt = :now WHERE phoneNumber = :phoneNumber")
    suspend fun setImportance(phoneNumber: String, importance: Int, now: Long)

    @Query("UPDATE contacts SET notes = :notes, updatedAt = :now WHERE phoneNumber = :phoneNumber")
    suspend fun setNotes(phoneNumber: String, notes: String?, now: Long)

    @Query("UPDATE contacts SET isSpam = :isSpam, updatedAt = :now WHERE phoneNumber = :phoneNumber")
    suspend fun setSpam(phoneNumber: String, isSpam: Boolean, now: Long)

    @Query("UPDATE contacts SET notificationsEnabled = :enabled, updatedAt = :now WHERE phoneNumber = :phoneNumber")
    suspend fun setNotificationsEnabled(phoneNumber: String, enabled: Boolean, now: Long)

    @Query("UPDATE contacts SET customRingtoneUri = :uri, updatedAt = :now WHERE phoneNumber = :phoneNumber")
    suspend fun setCustomRingtone(phoneNumber: String, uri: String?, now: Long)

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM contacts WHERE vipLevel != 'NONE'")
    fun observeVipCount(): Flow<Int>

    @Query("SELECT * FROM contacts WHERE name LIKE '%' || :q || '%' OR phoneNumber LIKE '%' || :q || '%' OR IFNULL(notes,'') LIKE '%' || :q || '%' ORDER BY name COLLATE NOCASE ASC LIMIT :limit")
    suspend fun search(q: String, limit: Int = 50): List<ContactEntity>

    @Query("SELECT * FROM contacts")
    suspend fun allOnce(): List<ContactEntity>

    /** Privacy Center: reset VIP tiers, tags and notes without touching the contact list itself. */
    @Query("UPDATE contacts SET vipLevel = 'NONE', tag = NULL, notes = NULL, relationship = NULL, importance = 1, isSpam = 0, updatedAt = :now")
    suspend fun resetAllUserFields(now: Long)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()
}
