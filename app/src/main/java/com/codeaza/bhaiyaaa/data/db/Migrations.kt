package com.codeaza.bhaiyaaa.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema history.
 *
 * v3 was the original three-table build (contacts / call_records / reminders).
 * v4 is the full schema: match keys for reliable number reconciliation, call
 * annotations, memories with an FTS index, tags, AI models and notification
 * rules.
 *
 * Note on DDL style: none of these CREATE TABLE statements carry SQL DEFAULT
 * clauses. The entities express their defaults in Kotlin, not via
 * @ColumnInfo(defaultValue=...), so Room's expected schema has no defaults -
 * and Room validates the migrated database against that expectation on first
 * open. A stray `DEFAULT 0` here fails the whole migration at runtime, which is
 * exactly what MigrationTest pins down.
 *
 * The migration is written out rather than falling back to a destructive one
 * because v3 already held real user data - VIP tiers, tags and notes that a
 * person typed in by hand. Those are carried across; call history is not,
 * because it is re-derivable from the device call log on the next sync.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // --- contacts: add the new columns, preserving user-owned data ---------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS contacts_new (
                phoneNumber TEXT NOT NULL PRIMARY KEY,
                matchKey TEXT NOT NULL,
                name TEXT NOT NULL,
                vipLevel TEXT NOT NULL,
                tag TEXT,
                relationship TEXT,
                importance INTEGER NOT NULL,
                notes TEXT,
                customRingtoneUri TEXT,
                notificationsEnabled INTEGER NOT NULL,
                isSpam INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        // Best-effort match key in SQL (strip the punctuation real numbers use,
        // keep the last 9 digits). The next device sync recomputes it properly
        // in Kotlin via PhoneNumbers.matchKey, so this only has to be close.
        db.execSQL(
            """
            INSERT OR IGNORE INTO contacts_new
                (phoneNumber, matchKey, name, vipLevel, tag, relationship, importance,
                 notes, customRingtoneUri, notificationsEnabled, isSpam, createdAt, updatedAt)
            SELECT phoneNumber,
                   substr(replace(replace(replace(replace(replace(
                       phoneNumber, '+', ''), '-', ''), ' ', ''), '(', ''), ')', ''), -9),
                   name, vipLevel, tag, NULL, 1, notes, NULL, 1, 0, 0, 0
            FROM contacts
            """.trimIndent()
        )
        db.execSQL("DROP TABLE contacts")
        db.execSQL("ALTER TABLE contacts_new RENAME TO contacts")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_name ON contacts(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_vipLevel ON contacts(vipLevel)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_tag ON contacts(tag)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_isSpam ON contacts(isSpam)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_matchKey ON contacts(matchKey)")

        // --- call_records: repopulated from the device log, so just rebuild ----
        db.execSQL("DROP TABLE IF EXISTS call_records")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS call_records (
                id INTEGER NOT NULL PRIMARY KEY,
                phoneNumber TEXT NOT NULL,
                matchKey TEXT NOT NULL,
                contactName TEXT,
                type TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                durationSeconds INTEGER NOT NULL,
                isImportant INTEGER NOT NULL,
                note TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_call_records_phoneNumber ON call_records(phoneNumber)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_call_records_timestamp ON call_records(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_call_records_type ON call_records(type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_call_records_matchKey ON call_records(matchKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_call_records_matchKey_timestamp ON call_records(matchKey, timestamp)")

        // --- reminders: gains dueAt / notified, renames createdAtMillis --------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reminders_new (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                text TEXT NOT NULL,
                contactPhoneNumber TEXT,
                createdAt INTEGER NOT NULL,
                dueAt INTEGER,
                isDone INTEGER NOT NULL,
                notified INTEGER NOT NULL,
                FOREIGN KEY(contactPhoneNumber) REFERENCES contacts(phoneNumber) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO reminders_new (id, text, contactPhoneNumber, createdAt, dueAt, isDone, notified)
            SELECT id, text, contactPhoneNumber, createdAtMillis, NULL, isDone, 0 FROM reminders
            """.trimIndent()
        )
        db.execSQL("DROP TABLE reminders")
        db.execSQL("ALTER TABLE reminders_new RENAME TO reminders")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_dueAt ON reminders(dueAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_isDone ON reminders(isDone)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_contactPhoneNumber ON reminders(contactPhoneNumber)")

        // --- new tables -------------------------------------------------------
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memories (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                contactPhoneNumber TEXT,
                title TEXT,
                body TEXT NOT NULL,
                source TEXT NOT NULL,
                callRecordId INTEGER,
                isPrivate INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(contactPhoneNumber) REFERENCES contacts(phoneNumber) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memories_contactPhoneNumber ON memories(contactPhoneNumber)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memories_createdAt ON memories(createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memories_isPrivate ON memories(isPrivate)")

        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS memories_fts USING fts4(" +
                "title TEXT, body TEXT, content=`memories`)"
        )
        // External-content FTS needs the sync triggers Room would otherwise create.
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memories_fts_BEFORE_UPDATE " +
                "BEFORE UPDATE ON memories BEGIN DELETE FROM memories_fts WHERE docid=OLD.rowid; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memories_fts_BEFORE_DELETE " +
                "BEFORE DELETE ON memories BEGIN DELETE FROM memories_fts WHERE docid=OLD.rowid; END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memories_fts_AFTER_UPDATE " +
                "AFTER UPDATE ON memories BEGIN INSERT INTO memories_fts(docid, title, body) " +
                "VALUES (NEW.rowid, NEW.title, NEW.body); END"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memories_fts_AFTER_INSERT " +
                "AFTER INSERT ON memories BEGIN INSERT INTO memories_fts(docid, title, body) " +
                "VALUES (NEW.rowid, NEW.title, NEW.body); END"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tags (
                name TEXT NOT NULL PRIMARY KEY,
                colorArgb INTEGER NOT NULL,
                isBuiltIn INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ai_models (
                id TEXT NOT NULL PRIMARY KEY,
                displayName TEXT NOT NULL,
                purpose TEXT NOT NULL,
                sizeBytes INTEGER NOT NULL,
                license TEXT NOT NULL,
                sourceUrl TEXT NOT NULL,
                status TEXT NOT NULL,
                installedPath TEXT,
                enabled INTEGER NOT NULL,
                downloadedBytes INTEGER NOT NULL,
                lastError TEXT,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notification_rules (
                vipLevel TEXT NOT NULL PRIMARY KEY,
                notificationsEnabled INTEGER NOT NULL,
                vibrationEnabled INTEGER NOT NULL,
                vibrationPatternCsv TEXT NOT NULL,
                flashEnabled INTEGER NOT NULL,
                flashCount INTEGER NOT NULL,
                flashOnMillis INTEGER NOT NULL,
                flashOffMillis INTEGER NOT NULL,
                customSoundUri TEXT,
                bypassDnd INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * v5 adds the prayer-silence feature: a row per prayer, and a per-tier flag for
 * whether a VIP still reaches the user during a silence window.
 *
 * The ALTER TABLE carries a DEFAULT because SQLite requires one when adding a
 * NOT NULL column to a table that already has rows. The entity therefore
 * declares the same default via @ColumnInfo, so Room's expected schema and the
 * migrated database agree - they are compared on the next open, and a mismatch
 * is a hard failure.
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS prayers (
                name TEXT NOT NULL PRIMARY KEY,
                enabled INTEGER NOT NULL,
                silenceMinutes INTEGER NOT NULL,
                manualMinutesFromMidnight INTEGER,
                sortOrder INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "ALTER TABLE notification_rules ADD COLUMN ringsDuringPrayer INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/**
 * v6 gives each prayer a start offset, so the quiet window can open before the
 * prayer rather than at it - you want the phone already silent as you arrive.
 *
 * Rows still holding the old 20-minute default are moved to the new 15-minute
 * one. That default was seeded by the app and never chosen by anyone, so
 * carrying it forward would just preserve a value nobody picked.
 */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE prayers ADD COLUMN startOffsetMinutes INTEGER NOT NULL DEFAULT -3")
        db.execSQL("UPDATE prayers SET silenceMinutes = 15 WHERE silenceMinutes = 20")
    }
}

/**
 * v7 adds user-defined quiet periods, which are not tied to a prayer.
 *
 * Times are stored as minutes past local midnight with a weekday mask, not as
 * instants: "quiet from 9pm on weeknights" describes the clock, and has to keep
 * meaning that tomorrow, and after the user changes time zone.
 */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS silence_schedules (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                label TEXT NOT NULL,
                startMinutesFromMidnight INTEGER NOT NULL,
                durationMinutes INTEGER NOT NULL,
                daysMask INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                silenceMode TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/**
 * v8 does two things a prayer app has to get right.
 *
 * **It fixes prayer times stored in the wrong half of the clock.** Until now
 * the editor offered a bare AM/PM picker with nothing stopping Fajr being
 * saved at 5 PM or Asr at 4 AM. A row like that is not a slightly wrong
 * setting - the phone silences twelve hours from when it was meant, which
 * looks exactly like the feature not working. The correction flips the
 * meridiem rather than clamping to noon or midnight, because 5 PM entered for
 * Fajr was 5 AM meant, and clamping would replace it with a time nobody typed.
 * Rows with no manual time are untouched: NULL means "use the calculation" and
 * must stay NULL.
 *
 * **It adds voice recordings.** Metadata only - the audio lives in the app's
 * private files directory. See [com.codeaza.bhaiyaaa.data.db.entity.VoiceRecordingEntity].
 */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Fajr is before dawn: anything at or after noon is twelve hours out.
        db.execSQL(
            """
            UPDATE prayers
            SET manualMinutesFromMidnight = manualMinutesFromMidnight - 720
            WHERE name = 'FAJR'
              AND manualMinutesFromMidnight IS NOT NULL
              AND manualMinutesFromMidnight >= 720
            """.trimIndent()
        )
        // Every prayer after Fajr is after noon.
        db.execSQL(
            """
            UPDATE prayers
            SET manualMinutesFromMidnight = manualMinutesFromMidnight + 720
            WHERE name IN ('DHUHR', 'ASR', 'MAGHRIB', 'ISHA')
              AND manualMinutesFromMidnight IS NOT NULL
              AND manualMinutesFromMidnight < 720
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS voice_recordings (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                label TEXT NOT NULL,
                fileName TEXT NOT NULL,
                durationMillis INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                source TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_voice_recordings_fileName " +
                "ON voice_recordings (fileName)"
        )
    }
}

/**
 * v9 lets a recording belong to a call.
 *
 * Sukoon cannot capture call audio - Android reserves that for privileged,
 * pre-installed apps - so this is the achievable half of the same need: a
 * voice note made just after a call, or a recording the phone's own dialer
 * produced and the user imported, filed against the call it belongs to.
 *
 * `callId` is the device call-log row id, which `call_records` also uses as its
 * primary key. Deliberately not a foreign key: clearing the phone's call log
 * removes the call row, and a note the user made by hand should survive that
 * rather than being deleted along with it.
 */
internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE voice_recordings ADD COLUMN callId INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_voice_recordings_callId " +
                "ON voice_recordings (callId)"
        )
    }
}

internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
)

/**
 * Test seam. The migration is the one piece of this app that can destroy data
 * a user typed in by hand, so it is exercised directly rather than trusted.
 */
fun migrationsForTest(): Array<Migration> = ALL_MIGRATIONS
