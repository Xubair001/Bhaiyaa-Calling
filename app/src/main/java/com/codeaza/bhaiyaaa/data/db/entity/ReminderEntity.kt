package com.codeaza.bhaiyaaa.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    indices = [Index("dueAt"), Index("isDone"), Index("contactPhoneNumber")],
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["phoneNumber"],
            childColumns = ["contactPhoneNumber"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val contactPhoneNumber: String? = null,
    val createdAt: Long,
    /** Null means "someday" - it shows in the list but never fires a notification. */
    val dueAt: Long? = null,
    val isDone: Boolean = false,
    val notified: Boolean = false
)
