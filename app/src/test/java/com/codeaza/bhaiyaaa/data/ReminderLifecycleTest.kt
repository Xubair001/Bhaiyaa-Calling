package com.codeaza.bhaiyaaa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.entity.ReminderEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reminder lifecycle around [ReminderEntity.notified].
 *
 * The alarm receiver refuses to post for anything already notified, so any
 * write that should earn a fresh alert has to clear that flag. Getting it
 * wrong fails silently - the reminder sits in the list looking armed and
 * simply never speaks - which is why it is pinned down here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReminderLifecycleTest {

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

    private val dao get() = db.reminderDao()

    private suspend fun insertFired(): Long = dao.insert(
        ReminderEntity(
            text = "Call the bank",
            createdAt = now,
            dueAt = now + 60_000L,
            isDone = false,
            notified = true
        )
    )

    @Test
    fun `un-completing clears notified so it can fire again`() = runTest {
        val id = insertFired()
        dao.setDone(id, true)
        dao.setDone(id, false)

        val reminder = dao.findById(id)!!
        assertThat(reminder.isDone).isFalse()
        assertThat(reminder.notified).isFalse()
    }

    @Test
    fun `completing leaves notified alone`() = runTest {
        // Nothing is owed an alert on the way out, and clearing it here would
        // let a completed reminder re-post if its alarm were still armed.
        val id = insertFired()
        dao.setDone(id, true)

        val reminder = dao.findById(id)!!
        assertThat(reminder.isDone).isTrue()
        assertThat(reminder.notified).isTrue()
    }

    @Test
    fun `editing the time clears notified`() = runTest {
        val id = insertFired()
        dao.edit(id, "Call the bank back", now + 3_600_000L)

        val reminder = dao.findById(id)!!
        assertThat(reminder.text).isEqualTo("Call the bank back")
        assertThat(reminder.dueAt).isEqualTo(now + 3_600_000L)
        assertThat(reminder.notified).isFalse()
    }

    @Test
    fun `editing to someday drops the due date`() = runTest {
        val id = insertFired()
        dao.edit(id, "Call the bank", null)
        assertThat(dao.findById(id)!!.dueAt).isNull()
    }

    @Test
    fun `snoozing clears notified and un-completes`() = runTest {
        val id = insertFired()
        dao.setDone(id, true)
        dao.rescheduleTo(id, now + 600_000L)

        val reminder = dao.findById(id)!!
        assertThat(reminder.isDone).isFalse()
        assertThat(reminder.notified).isFalse()
        assertThat(reminder.dueAt).isEqualTo(now + 600_000L)
    }

    @Test
    fun `a snoozed reminder is owed an alarm again`() = runTest {
        // pendingScheduled is what re-arms alarms after a reboot; a snoozed
        // reminder has to appear in it or the snooze is lost on restart.
        val id = insertFired()
        assertThat(dao.pendingScheduled().map { it.id }).doesNotContain(id)

        dao.rescheduleTo(id, now + 600_000L)
        assertThat(dao.pendingScheduled().map { it.id }).contains(id)
    }

    @Test
    fun `completed reminders leave the active list and show under done`() = runTest {
        val id = insertFired()
        assertThat(dao.observeActive().first().map { it.id }).contains(id)

        dao.setDone(id, true)
        assertThat(dao.observeActive().first().map { it.id }).doesNotContain(id)
        assertThat(dao.observeDone(30).first().map { it.id }).contains(id)
    }
}
