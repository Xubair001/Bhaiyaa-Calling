package com.codeaza.bhaiyaaa.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.data.db.migrationsForTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The v7 -> v8 correction of prayer times stored in the wrong half of the clock.
 *
 * Until v8 the editor offered a bare AM/PM picker with nothing stopping Fajr
 * being saved in the afternoon. Anyone who did that has a row on their phone
 * right now that silences twelve hours from when they meant, so the upgrade
 * has to fix it rather than only preventing new ones - and it has to fix it by
 * flipping the meridiem, not by clamping to a time nobody chose.
 *
 * Exercised against a real v7 `prayers` table rather than through Room, so the
 * SQL itself is what is under test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrayerTimeMigrationTest {

    private val dbName = "prayer-migration-test.db"
    private lateinit var dbFile: File
    private lateinit var helper: SupportSQLiteOpenHelper

    private val migration
        get() = migrationsForTest().single { it.startVersion == 7 && it.endVersion == 8 }

    private val migrationV9
        get() = migrationsForTest().single { it.startVersion == 8 && it.endVersion == 9 }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        // The prayers table exactly as version 7 shipped it.
        val callback = object : SupportSQLiteOpenHelper.Callback(7) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE prayers (
                        name TEXT NOT NULL PRIMARY KEY,
                        enabled INTEGER NOT NULL,
                        silenceMinutes INTEGER NOT NULL,
                        manualMinutesFromMidnight INTEGER,
                        startOffsetMinutes INTEGER NOT NULL DEFAULT -3,
                        sortOrder INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
        }
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
    }

    @After
    fun tearDown() {
        helper.close()
        if (dbFile.exists()) dbFile.delete()
    }

    private fun insert(name: String, minutes: Int?) {
        helper.writableDatabase.execSQL(
            "INSERT INTO prayers (name, enabled, silenceMinutes, manualMinutesFromMidnight, " +
                "startOffsetMinutes, sortOrder) VALUES (?, 1, 15, ?, -3, 0)",
            arrayOf(name, minutes)
        )
    }

    private fun storedTime(name: String): Int? =
        helper.writableDatabase.query(
            "SELECT manualMinutesFromMidnight FROM prayers WHERE name = ?",
            arrayOf(name)
        ).use { cursor ->
            if (!cursor.moveToFirst()) null
            else if (cursor.isNull(0)) null
            else cursor.getInt(0)
        }

    @Test
    fun `a Fajr saved in the afternoon becomes the morning time that was meant`() {
        insert("FAJR", 17 * 60)

        migration.migrate(helper.writableDatabase)

        assertThat(storedTime("FAJR")).isEqualTo(5 * 60)
    }

    @Test
    fun `an afternoon prayer saved in the morning is moved to the afternoon`() {
        insert("ASR", 4 * 60 + 15)
        insert("DHUHR", 30)
        insert("MAGHRIB", 6 * 60 + 30)
        insert("ISHA", 8 * 60)

        migration.migrate(helper.writableDatabase)

        assertThat(storedTime("ASR")).isEqualTo(16 * 60 + 15)
        assertThat(storedTime("DHUHR")).isEqualTo(12 * 60 + 30)
        assertThat(storedTime("MAGHRIB")).isEqualTo(18 * 60 + 30)
        assertThat(storedTime("ISHA")).isEqualTo(20 * 60)
    }

    @Test
    fun `times that were already right are left exactly as they were`() {
        insert("FAJR", 5 * 60 + 12)
        insert("ISHA", 20 * 60 + 45)

        migration.migrate(helper.writableDatabase)

        assertThat(storedTime("FAJR")).isEqualTo(5 * 60 + 12)
        assertThat(storedTime("ISHA")).isEqualTo(20 * 60 + 45)
    }

    @Test
    fun `a prayer with no time keeps having no time`() {
        // NULL means "use the calculation". Turning it into a number would
        // silently give every upgrading user five manual overrides.
        insert("FAJR", null)
        insert("ASR", null)

        migration.migrate(helper.writableDatabase)

        assertThat(storedTime("FAJR")).isNull()
        assertThat(storedTime("ASR")).isNull()
    }

    @Test
    fun `midnight Fajr survives, since minute zero is a time and not an absence`() {
        insert("FAJR", 0)

        migration.migrate(helper.writableDatabase)

        assertThat(storedTime("FAJR")).isEqualTo(0)
    }

    @Test
    fun `the recordings table is created and is usable`() {
        migration.migrate(helper.writableDatabase)

        helper.writableDatabase.execSQL(
            "INSERT INTO voice_recordings (label, fileName, durationMillis, createdAt, source) " +
                "VALUES ('Adhan', 'a.m4a', 1000, 1, 'RECORDED')"
        )
        val count = helper.writableDatabase
            .query("SELECT COUNT(*) FROM voice_recordings")
            .use { it.moveToFirst(); it.getInt(0) }
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `v8 to v9 files a recording against a call without disturbing existing rows`() {
        migration.migrate(helper.writableDatabase)
        // A recording made before the upgrade, with no call.
        helper.writableDatabase.execSQL(
            "INSERT INTO voice_recordings (label, fileName, durationMillis, createdAt, source) " +
                "VALUES ('Adhan', 'adhan.m4a', 120000, 1, 'RECORDED')"
        )

        migrationV9.migrate(helper.writableDatabase)

        // The existing row survives and is simply unattached.
        val existing = helper.writableDatabase
            .query("SELECT callId FROM voice_recordings WHERE fileName = 'adhan.m4a'")
            .use { it.moveToFirst(); if (it.isNull(0)) null else it.getLong(0) }
        assertThat(existing).isNull()

        // And a new one can name the call it belongs to.
        helper.writableDatabase.execSQL(
            "INSERT INTO voice_recordings (label, fileName, durationMillis, createdAt, source, callId) " +
                "VALUES ('After the call', 'note.m4a', 5000, 2, 'RECORDED', 4242)"
        )
        val attached = helper.writableDatabase
            .query("SELECT callId FROM voice_recordings WHERE fileName = 'note.m4a'")
            .use { it.moveToFirst(); it.getLong(0) }
        assertThat(attached).isEqualTo(4242L)
    }

    @Test
    fun `two recordings cannot claim the same file`() {
        migration.migrate(helper.writableDatabase)
        helper.writableDatabase.execSQL(
            "INSERT INTO voice_recordings (label, fileName, durationMillis, createdAt, source) " +
                "VALUES ('One', 'a.m4a', 1000, 1, 'RECORDED')"
        )

        // Deleting one would otherwise remove audio the other still points at.
        val duplicate = runCatching {
            helper.writableDatabase.execSQL(
                "INSERT INTO voice_recordings (label, fileName, durationMillis, createdAt, source) " +
                    "VALUES ('Two', 'a.m4a', 1000, 2, 'IMPORTED')"
            )
        }
        assertThat(duplicate.isFailure).isTrue()
    }
}
