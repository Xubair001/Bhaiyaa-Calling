package com.codeaza.bhaiyaaa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.db.entity.MemoryEntity
import com.codeaza.bhaiyaaa.data.db.entity.ReminderEntity
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.util.PhoneNumbers
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppDatabaseTest {

    private lateinit var db: AppDatabase
    private val now = 1_756_000_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun contact(name: String, number: String, vip: VipLevel = VipLevel.NONE) =
        ContactEntity(
            phoneNumber = number,
            matchKey = PhoneNumbers.matchKey(number),
            name = name,
            vipLevel = vip.storageValue,
            createdAt = now,
            updatedAt = now
        )

    private fun call(id: Long, number: String, type: String, at: Long, duration: Long = 0) =
        CallRecordEntity(
            id = id,
            phoneNumber = number,
            matchKey = PhoneNumbers.matchKey(number),
            contactName = null,
            type = type,
            timestamp = at,
            durationSeconds = duration
        )

    // ------------------------------------------------------------- contacts

    @Test
    fun `re-syncing contacts never clobbers a VIP tier the user set`() = runTest {
        val dao = db.contactDao()
        dao.insertIfAbsent(listOf(contact("Ahmed", "+923001234567")))
        dao.setVipLevel("+923001234567", VipLevel.SUPER_VIP.storageValue, now)

        // A later device sync re-offers the same contact with default fields.
        dao.insertIfAbsent(listOf(contact("Ahmed", "+923001234567")))

        val stored = dao.findByPhoneNumber("+923001234567")
        assertThat(stored?.vipLevel).isEqualTo(VipLevel.SUPER_VIP.storageValue)
    }

    @Test
    fun `sync refreshes the display name without touching notes`() = runTest {
        val dao = db.contactDao()
        dao.insertIfAbsent(listOf(contact("Ahmed", "+923001234567")))
        dao.setNotes("+923001234567", "Works on the tender project", now)

        dao.refreshDeviceFields("+923001234567", "Ahmed Khan", PhoneNumbers.matchKey("+923001234567"), now + 1)

        val stored = dao.findByPhoneNumber("+923001234567")
        assertThat(stored?.name).isEqualTo("Ahmed Khan")
        assertThat(stored?.notes).isEqualTo("Works on the tender project")
    }

    @Test
    fun `a contact is found by a differently formatted number`() = runTest {
        val dao = db.contactDao()
        dao.insertIfAbsent(listOf(contact("Ahmed", "+923001234567", VipLevel.VIP)))

        // The call log reports the local form of the same number.
        val found = dao.findByMatchKey(PhoneNumbers.matchKey("03001234567"))
        assertThat(found?.name).isEqualTo("Ahmed")
    }

    @Test
    fun `the highest VIP tier wins when a number appears twice`() = runTest {
        val dao = db.contactDao()
        dao.insertIfAbsent(
            listOf(
                contact("Ahmed work", "+923001234567", VipLevel.VIP),
                contact("Ahmed personal", "03001234567", VipLevel.EMERGENCY)
            )
        )
        val found = dao.findByMatchKey(PhoneNumbers.matchKey("+923001234567"))
        assertThat(found?.vipLevel).isEqualTo(VipLevel.EMERGENCY.storageValue)
    }

    @Test
    fun `resetting user fields keeps the contacts themselves`() = runTest {
        val dao = db.contactDao()
        dao.insertIfAbsent(listOf(contact("Ahmed", "+923001234567", VipLevel.VIP)))
        dao.setNotes("+923001234567", "secret", now)

        dao.resetAllUserFields(now)

        assertThat(dao.count()).isEqualTo(1)
        val stored = dao.findByPhoneNumber("+923001234567")
        assertThat(stored?.vipLevel).isEqualTo("NONE")
        assertThat(stored?.notes).isNull()
    }

    // ---------------------------------------------------------------- calls

    @Test
    fun `syncing the same calls twice does not duplicate history`() = runTest {
        val dao = db.callRecordDao()
        val batch = listOf(
            call(1, "+923001234567", "INCOMING", now - 1000),
            call(2, "+923001234567", "MISSED", now - 2000)
        )
        dao.insertIfAbsent(batch)
        dao.insertIfAbsent(batch)

        // Keyed by the device call-log id, so a re-read is a no-op.
        assertThat(dao.allOnce()).hasSize(2)
    }

    @Test
    fun `a re-sync preserves annotations the user added to a call`() = runTest {
        val dao = db.callRecordDao()
        dao.insertIfAbsent(listOf(call(1, "+923001234567", "INCOMING", now - 1000)))
        dao.setImportant(1, true)
        dao.setNote(1, "Discussed the deployment")

        dao.insertIfAbsent(listOf(call(1, "+923001234567", "INCOMING", now - 1000)))

        val stored = dao.findById(1)
        assertThat(stored?.isImportant).isTrue()
        assertThat(stored?.note).isEqualTo("Discussed the deployment")
    }

    @Test
    fun `missed calls are counted only within the window`() = runTest {
        val dao = db.callRecordDao()
        dao.insertIfAbsent(
            listOf(
                call(1, "+92300", "MISSED", now - 1_000),
                call(2, "+92300", "MISSED", now - 10_000),
                call(3, "+92300", "MISSED", now - 100_000_000),
                call(4, "+92300", "INCOMING", now - 1_000)
            )
        )
        assertThat(dao.missedSince(now - 60_000)).isEqualTo(2)
    }

    @Test
    fun `per-contact stats average only answered calls`() = runTest {
        val dao = db.callRecordDao()
        val key = PhoneNumbers.matchKey("+923001234567")
        dao.insertIfAbsent(
            listOf(
                call(1, "+923001234567", "INCOMING", now - 1000, duration = 100),
                call(2, "+923001234567", "INCOMING", now - 2000, duration = 200),
                // A missed call has no duration and must not drag the average down.
                call(3, "+923001234567", "MISSED", now - 3000, duration = 0)
            )
        )
        val stats = dao.statsForContact(key)
        assertThat(stats?.totalCalls).isEqualTo(3)
        assertThat(stats?.missedCalls).isEqualTo(1)
        assertThat(stats?.answeredCalls).isEqualTo(2)
        assertThat(stats?.averageDurationSeconds).isEqualTo(150)
    }

    @Test
    fun `vip call counting joins calls to contacts`() = runTest {
        db.contactDao().insertIfAbsent(listOf(contact("Ahmed", "+923001234567", VipLevel.VIP)))
        db.callRecordDao().insertIfAbsent(
            listOf(
                call(1, "03001234567", "INCOMING", now - 1000),
                call(2, "+923009999999", "INCOMING", now - 1000)
            )
        )
        // Only the VIP's call counts, even though it was logged in local format.
        assertThat(db.callRecordDao().vipCallCountSince(now - 60_000)).isEqualTo(1)
    }

    // ------------------------------------------------------------- memories

    @Test
    fun `full text search finds a memory by a word in its body`() = runTest {
        val dao = db.memoryDao()
        dao.insert(
            MemoryEntity(
                body = "Ahmed wants the deployment finished by Friday",
                source = "MANUAL",
                createdAt = now,
                updatedAt = now
            )
        )
        dao.insert(
            MemoryEntity(
                body = "Order new office chairs",
                source = "MANUAL",
                createdAt = now,
                updatedAt = now
            )
        )

        val hits = dao.searchFts("\"deployment\"*")
        assertThat(hits).hasSize(1)
        assertThat(hits.first().body).contains("deployment")
    }

    @Test
    fun `the fts index follows deletes`() = runTest {
        val dao = db.memoryDao()
        val id = dao.insert(
            MemoryEntity(body = "Invoice due Tuesday", source = "MANUAL", createdAt = now, updatedAt = now)
        )
        assertThat(dao.searchFts("\"invoice\"*")).hasSize(1)

        dao.deleteById(id)

        // A stale index would keep returning a memory that no longer exists.
        assertThat(dao.searchFts("\"invoice\"*")).isEmpty()
    }

    @Test
    fun `the fts index follows updates`() = runTest {
        val dao = db.memoryDao()
        val id = dao.insert(
            MemoryEntity(body = "Call the plumber", source = "MANUAL", createdAt = now, updatedAt = now)
        )
        val stored = requireNotNull(dao.findById(id))
        dao.update(stored.copy(body = "Call the electrician"))

        assertThat(dao.searchFts("\"plumber\"*")).isEmpty()
        assertThat(dao.searchFts("\"electrician\"*")).hasSize(1)
    }

    @Test
    fun `private memories are excluded from the locked view`() = runTest {
        val dao = db.memoryDao()
        dao.insert(MemoryEntity(body = "Public note", source = "MANUAL", createdAt = now, updatedAt = now))
        dao.insert(
            MemoryEntity(body = "Private note", source = "MANUAL", isPrivate = true, createdAt = now, updatedAt = now)
        )

        assertThat(dao.observeAll().first()).hasSize(2)
        assertThat(dao.observeNonPrivate().first()).hasSize(1)
    }

    @Test
    fun `deleting a contact nulls the link but keeps the memory`() = runTest {
        db.contactDao().insertIfAbsent(listOf(contact("Ahmed", "+923001234567")))
        val id = db.memoryDao().insert(
            MemoryEntity(
                contactPhoneNumber = "+923001234567",
                body = "Wants the report",
                source = "MANUAL",
                createdAt = now,
                updatedAt = now
            )
        )

        db.contactDao().deleteAll()

        // ON DELETE SET NULL: the note survives losing its contact.
        val stored = db.memoryDao().findById(id)
        assertThat(stored).isNotNull()
        assertThat(stored?.contactPhoneNumber).isNull()
    }

    // ------------------------------------------------------------ reminders

    @Test
    fun `active reminders exclude completed ones and sort by due date`() = runTest {
        val dao = db.reminderDao()
        dao.insert(ReminderEntity(text = "later", createdAt = now, dueAt = now + 100_000))
        dao.insert(ReminderEntity(text = "sooner", createdAt = now, dueAt = now + 1_000))
        dao.insert(ReminderEntity(text = "someday", createdAt = now, dueAt = null))
        val doneId = dao.insert(ReminderEntity(text = "done", createdAt = now, dueAt = now + 50))
        dao.setDone(doneId, true)

        val active = dao.observeActive().first()
        assertThat(active.map { it.text }).containsExactly("sooner", "later", "someday").inOrder()
    }

    @Test
    fun `pendingScheduled returns only un-notified future reminders`() = runTest {
        val dao = db.reminderDao()
        dao.insert(ReminderEntity(text = "a", createdAt = now, dueAt = now + 1000))
        val notified = dao.insert(ReminderEntity(text = "b", createdAt = now, dueAt = now + 1000))
        dao.markNotified(notified)
        dao.insert(ReminderEntity(text = "c", createdAt = now, dueAt = null))

        val pending = dao.pendingScheduled()
        assertThat(pending.map { it.text }).containsExactly("a")
    }
}
