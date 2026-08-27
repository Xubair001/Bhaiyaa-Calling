package com.codeaza.bhaiyaaa.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_records")
data class CallRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val contactName: String?,
    val type: String,
    val timestamp: Long,
    val durationSeconds: Long
)
