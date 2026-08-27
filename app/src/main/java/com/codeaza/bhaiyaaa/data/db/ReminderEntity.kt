package com.codeaza.bhaiyaaa.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val contactPhoneNumber: String? = null,
    val createdAtMillis: Long,
    val isDone: Boolean = false
)
