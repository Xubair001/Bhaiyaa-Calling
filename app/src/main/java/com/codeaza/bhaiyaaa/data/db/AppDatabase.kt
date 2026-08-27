package com.codeaza.bhaiyaaa.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.codeaza.bhaiyaaa.data.db.dao.AiModelDao
import com.codeaza.bhaiyaaa.data.db.dao.CallRecordDao
import com.codeaza.bhaiyaaa.data.db.dao.ContactDao
import com.codeaza.bhaiyaaa.data.db.dao.MemoryDao
import com.codeaza.bhaiyaaa.data.db.dao.NotificationRuleDao
import com.codeaza.bhaiyaaa.data.db.dao.PrayerDao
import com.codeaza.bhaiyaaa.data.db.dao.ReminderDao
import com.codeaza.bhaiyaaa.data.db.dao.TagDao
import com.codeaza.bhaiyaaa.data.db.entity.AiModelEntity
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.db.entity.MemoryEntity
import com.codeaza.bhaiyaaa.data.db.entity.MemoryFtsEntity
import com.codeaza.bhaiyaaa.data.db.entity.NotificationRuleEntity
import com.codeaza.bhaiyaaa.data.db.entity.PrayerEntity
import com.codeaza.bhaiyaaa.data.db.entity.ReminderEntity
import com.codeaza.bhaiyaaa.data.db.entity.TagEntity

@Database(
    entities = [
        ContactEntity::class,
        CallRecordEntity::class,
        MemoryEntity::class,
        MemoryFtsEntity::class,
        ReminderEntity::class,
        TagEntity::class,
        AiModelEntity::class,
        NotificationRuleEntity::class,
        PrayerEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun callRecordDao(): CallRecordDao
    abstract fun memoryDao(): MemoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun tagDao(): TagDao
    abstract fun aiModelDao(): AiModelDao
    abstract fun notificationRuleDao(): NotificationRuleDao
    abstract fun prayerDao(): PrayerDao

    companion object {
        private const val DB_NAME = "bhaiyaaa.db"

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                // Real migrations, not destructive fallback: v3 shipped with
                // hand-entered VIP tiers, tags and notes that must survive.
                .addMigrations(*ALL_MIGRATIONS)
                // WAL: readers (the many Flow queries backing the UI) never
                // block on the call-log sync writing in the background.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()

        /** Test seam: lets instrumentation and Robolectric tests inject an in-memory DB. */
        internal fun setInstanceForTest(db: AppDatabase?) {
            INSTANCE = db
        }
    }
}
