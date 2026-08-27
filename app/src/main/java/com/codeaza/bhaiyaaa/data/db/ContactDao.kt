package com.codeaza.bhaiyaaa.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE vipLevel != 'NONE' ORDER BY name ASC")
    fun observeVipContacts(): Flow<List<ContactEntity>>

    // IGNORE (not REPLACE) on conflict - so re-syncing from the device never
    // clobbers a VIP level, tag, or notes you already set on an existing contact.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(contacts: List<ContactEntity>)

    @Query("UPDATE contacts SET name = :name WHERE phoneNumber = :phoneNumber")
    suspend fun updateName(phoneNumber: String, name: String)

    @Query("UPDATE contacts SET vipLevel = :level WHERE phoneNumber = :phoneNumber")
    suspend fun setVipLevel(phoneNumber: String, level: String)

    @Query("UPDATE contacts SET tag = :tag WHERE phoneNumber = :phoneNumber")
    suspend fun setTag(phoneNumber: String, tag: String?)

    @Query("UPDATE contacts SET notes = :notes WHERE phoneNumber = :phoneNumber")
    suspend fun setNotes(phoneNumber: String, notes: String)

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun findByPhoneNumber(phoneNumber: String): ContactEntity?

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun count(): Int

    @Query("UPDATE contacts SET vipLevel = 'NONE', notes = NULL, tag = NULL")
    suspend fun clearAllCustomFields()
}
