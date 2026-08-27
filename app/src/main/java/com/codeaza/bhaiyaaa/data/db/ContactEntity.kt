package com.codeaza.bhaiyaaa.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val phoneNumber: String,
    val name: String,
    val vipLevel: String = "NONE",
    val tag: String? = null,
    val notes: String? = null,
    val callCount: Int = 0,
    val lastCallTimestamp: Long? = null
)
