package com.codeaza.bhaiyaaa.data

import android.content.ContentValues
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import com.codeaza.bhaiyaaa.data.db.migrationsForTest

/**
 * The v3 -> v4 migration carries real user data across: VIP tiers, tags and
 * notes people typed in by hand. Falling back to a destructive migration would
 * silently delete all of it, so this test builds a genuine v3 database, opens
 * it with the v4 schema, and checks what survived.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationTest {

    private val dbName = "migration-test.db"
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()
    }

    @After
    fun tearDown() {
        if (dbFile.exists()) dbFile.delete()
    }

    /** Recreates the exact schema version 3 shipped with. */
    private fun createV3Database() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val callback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE contacts (
                        phoneNumber TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        vipLevel TEXT NOT NULL DEFAULT 'NONE',
                        tag TEXT,
                        notes TEXT,
                        callCount INTEGER NOT NULL DEFAULT 0,
                        lastCallTimestamp INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE call_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        phoneNumber TEXT NOT NULL,
                        contactName TEXT,
                        type TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        durationSeconds INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        text TEXT NOT NULL,
                        contactPhoneNumber TEXT,
                        createdAtMillis INTEGER NOT NULL,
                        isDone INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )

        helper.writableDatabase.use { db ->
            db.insert(
                "contacts",
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("phoneNumber", "+923001234567")
                    put("name", "Ahmed Khan")
                    put("vipLevel", "SUPER_VIP")
                    put("tag", "Work")
                    put("notes", "Working on the tender project")
                }
            )
            db.insert(
                "reminders",
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                ContentValues().apply {
                    put("text", "Call Ali back")
                    put("createdAtMillis", 1_700_000_000_000L)
                    put("isDone", 0)
                }
            )
        }
    }

    @Test
    fun `migrating from v3 preserves hand-entered contact data`() = runTest {
        createV3Database()

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            dbName
        ).addMigrations(*migrationsForTest()).allowMainThreadQueries().build()

        val contact = db.contactDao().findByPhoneNumber("+923001234567")
        assertThat(contact).isNotNull()
        assertThat(contact?.name).isEqualTo("Ahmed Khan")
        assertThat(contact?.vipLevel).isEqualTo("SUPER_VIP")
        assertThat(contact?.tag).isEqualTo("Work")
        assertThat(contact?.notes).isEqualTo("Working on the tender project")
        db.close()
    }

    @Test
    fun `migrating from v3 computes a usable match key`() = runTest {
        createV3Database()

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            dbName
        ).addMigrations(*migrationsForTest()).allowMainThreadQueries().build()

        // The SQL fallback must produce a key that still matches the local form
        // of the number, or VIP alerts break for everyone who upgrades.
        val found = db.contactDao().findByMatchKey("001234567")
        assertThat(found?.name).isEqualTo("Ahmed Khan")
        db.close()
    }

    @Test
    fun `migrating from v3 carries reminders across the column rename`() = runTest {
        createV3Database()

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            dbName
        ).addMigrations(*migrationsForTest()).allowMainThreadQueries().build()

        val reminders = db.reminderDao().allOnce()
        assertThat(reminders).hasSize(1)
        assertThat(reminders.first().text).isEqualTo("Call Ali back")
        // createdAtMillis -> createdAt
        assertThat(reminders.first().createdAt).isEqualTo(1_700_000_000_000L)
        db.close()
    }

    @Test
    fun `the new tables exist and work after migrating`() = runTest {
        createV3Database()

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            dbName
        ).addMigrations(*migrationsForTest()).allowMainThreadQueries().build()

        val id = db.memoryDao().insert(
            com.codeaza.bhaiyaaa.data.db.entity.MemoryEntity(
                body = "Deployment is due Friday",
                source = "MANUAL",
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        assertThat(id).isGreaterThan(0L)
        // The FTS triggers created by the migration must work too.
        assertThat(db.memoryDao().searchFts("\"deployment\"*")).hasSize(1)
        db.close()
    }
}
