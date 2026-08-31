package com.codeaza.bhaiyaaa.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Something worth remembering: a note from a call, an action item, or a fact
 * the user asked Sukoon to keep.
 *
 * `source` records provenance so the UI can always say where a memory came
 * from. Sukoon never writes a memory the user did not actually give it.
 */
@Entity(
    tableName = "memories",
    indices = [Index("contactPhoneNumber"), Index("createdAt"), Index("isPrivate")],
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["phoneNumber"],
            childColumns = ["contactPhoneNumber"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactPhoneNumber: String? = null,
    val title: String? = null,
    val body: String,
    val source: String,
    val callRecordId: Long? = null,
    val isPrivate: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * SQLite FTS4 index over memories, so search stays fast as the table grows.
 * Declared as an external-content table (`contentEntity`), which means Room
 * generates the triggers that keep it in step with `memories` - there is no
 * second copy of the data to keep in sync by hand.
 */
@Fts4(contentEntity = MemoryEntity::class)
@Entity(tableName = "memories_fts")
data class MemoryFtsEntity(
    val title: String?,
    val body: String
)
