package com.codeaza.bhaiyaaa.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per call, mirrored from the device call log.
 *
 * The primary key is the device call-log row id, NOT an auto-generated one.
 * That makes syncing idempotent: re-reading the call log inserts the same ids
 * and conflicts are ignored, so repeated syncs can't duplicate history and
 * can't clobber the `isImportant` / `note` annotations the user added.
 */
@Entity(
    tableName = "call_records",
    indices = [
        Index("phoneNumber"),
        Index("timestamp"),
        Index("type"),
        Index("matchKey"),
        Index(value = ["matchKey", "timestamp"])
    ]
)
data class CallRecordEntity(
    @PrimaryKey val id: Long,
    val phoneNumber: String,
    /** Suffix key used to reconcile this call with a contact. */
    val matchKey: String,
    val contactName: String?,
    val type: String,
    val timestamp: Long,
    val durationSeconds: Long,
    val isImportant: Boolean = false,
    val note: String? = null
)
