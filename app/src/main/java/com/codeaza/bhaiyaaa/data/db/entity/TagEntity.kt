package com.codeaza.bhaiyaaa.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** User-definable contact categories, seeded with the built-in set on first run. */
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val name: String,
    val colorArgb: Int,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0
)
