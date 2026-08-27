package com.codeaza.bhaiyaaa.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A local, open-source AI model the user may choose to install.
 *
 * Nothing here is downloaded automatically: a row exists in the catalogue with
 * status NOT_INSTALLED until the user explicitly taps download, having seen the
 * size and licence first.
 */
@Entity(tableName = "ai_models")
data class AiModelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val purpose: String,
    val sizeBytes: Long,
    val license: String,
    val sourceUrl: String,
    val status: String,
    val installedPath: String? = null,
    val enabled: Boolean = false,
    val downloadedBytes: Long = 0,
    val lastError: String? = null,
    val updatedAt: Long = 0L
)
