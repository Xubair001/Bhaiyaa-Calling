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
                vipLevel TEXT NOT NULL DEFAULT 'NONE',
                tag TEXT,
                relationship TEXT,
                importance INTEGER NOT NULL DEFAULT 1,
                notes TEXT,
                customRingtoneUri TEXT,
                notificationsEnabled INTEGER NOT NULL DEFAULT 1,
                isSpam INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        // Best-effort match key in SQL (strip the punctuation real numbers use,
        // keep the last 9 digits). The next device sync recomputes it properly
        // in Kotlin via PhoneNumbers.matchKey, so this only has to be close.
        db.execSQL(
            """
            INSERT OR IGNORE INTO contacts_new
                (phoneNumber, matchKey, name, vipLevel, tag, notes, createdAt, updatedAt)
            SELECT phoneNumber,
                   substr(replace(replace(replace(replace(replace(
                       phoneNumber, '+', ''), '-', ''), ' ', ''), '(', ''), ')', ''), -9),
                   name, vipLevel, tag, notes, 0, 0
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
                isImportant INTEGER NOT NULL DEFAULT 0,
                note TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_call_records_phoneNumber ON call_records(phoneNumber)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_call_records_timestamp ON call_records(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_call_records_type ON call_records(type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_call_records_phoneNumber_timestamp ON call_records(phoneNumber, timestamp)")
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
                isDone INTEGER NOT NULL DEFAULT 0,
                notified INTEGER NOT NULL DEFAULT 0,
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
                isPrivate INTEGER NOT NULL DEFAULT 0,
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
                isBuiltIn INTEGER NOT NULL DEFAULT 0,
                sortOrder INTEGER NOT NULL DEFAULT 0
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
                enabled INTEGER NOT NULL DEFAULT 0,
                downloadedBytes INTEGER NOT NULL DEFAULT 0,
                lastError TEXT,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notification_rules (
                vipLevel TEXT NOT NULL PRIMARY KEY,
                notificationsEnabled INTEGER NOT NULL DEFAULT 1,
                vibrationEnabled INTEGER NOT NULL DEFAULT 1,
                vibrationPatternCsv TEXT NOT NULL DEFAULT '0,400,200,400',
                flashEnabled INTEGER NOT NULL DEFAULT 1,
                flashCount INTEGER NOT NULL DEFAULT 3,
                flashOnMillis INTEGER NOT NULL DEFAULT 180,
                flashOffMillis INTEGER NOT NULL DEFAULT 180,
                customSoundUri TEXT,
                bypassDnd INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}

internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_3_4)
