package com.codeaza.bhaiyaaa.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** How a recording got here. Shown in the UI so provenance is never a guess. */
enum class VoiceRecordingSource(val storageValue: String, val label: String) {
    RECORDED("RECORDED", "Recorded here"),
    IMPORTED("IMPORTED", "Imported file");

    companion object {
        fun from(value: String?): VoiceRecordingSource =
            entries.firstOrNull { it.storageValue == value } ?: RECORDED
    }
}

/**
 * A sound the user recorded or imported, for use as the adhan.
 *
 * Only the metadata is in the database. The audio itself sits in the app's
 * private files directory, which means it is covered by the same
 * `allowBackup="false"` as everything else, is removed with the app, and is
 * unreadable by other apps without root - none of which would be true of the
 * shared media store.
 *
 * [fileName] is a name inside that directory, never a full path: an absolute
 * path stored in a row goes stale the moment Android moves the app's data
 * directory, which it does on some upgrade and restore paths.
 */
@Entity(
    tableName = "voice_recordings",
    indices = [
        // Unique so two rows can never claim the same file - one of them would
        // then delete audio the other still points at.
        Index(value = ["fileName"], unique = true),
        // The call detail screen asks for one call's recordings every time it
        // opens, which is the only query here that filters rather than listing
        // everything - so it is the only one that earns an index.
        Index(value = ["callId"])
    ]
)
data class VoiceRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val fileName: String,
    val durationMillis: Long,
    val createdAt: Long,
    val source: String = VoiceRecordingSource.RECORDED.storageValue,
    /**
     * The call this belongs to, or null for a standalone sound such as an adhan.
     *
     * The device call-log row id, which [CallRecordEntity] also uses as its
     * primary key - stable across syncs, so a note stays attached to its call.
     * Deliberately not a foreign key: clearing the phone's call log removes the
     * call row, and a recording the user made should outlive that rather than
     * being cascaded away with it. A recording whose call has gone simply shows
     * in the main Recordings list.
     */
    val callId: Long? = null
)
