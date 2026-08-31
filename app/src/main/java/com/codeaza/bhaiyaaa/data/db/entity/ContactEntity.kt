package com.codeaza.bhaiyaaa.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A contact as Sukoon knows it. Keyed by the normalised phone number so a
 * device re-sync always lands on the same row.
 *
 * Deliberately holds only *user-owned* data (VIP tier, tag, notes, per-contact
 * alert prefs). Call counts, durations and "last interaction" are NOT stored
 * here - they are aggregated from `call_records` on demand, so they can never
 * drift out of sync with the real call log.
 */
@Entity(
    tableName = "contacts",
    indices = [
        Index("name"),
        Index("vipLevel"),
        Index("tag"),
        Index("isSpam"),
        Index("matchKey")
    ]
)
data class ContactEntity(
    @PrimaryKey val phoneNumber: String,
    /** Suffix key used to reconcile this contact with call-log rows. */
    val matchKey: String,
    val name: String,
    val vipLevel: String = "NONE",
    val tag: String? = null,
    val relationship: String? = null,
    val importance: Int = 1,
    val notes: String? = null,
    val customRingtoneUri: String? = null,
    val notificationsEnabled: Boolean = true,
    val isSpam: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
