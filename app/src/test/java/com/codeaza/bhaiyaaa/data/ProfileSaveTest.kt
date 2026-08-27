package com.codeaza.bhaiyaaa.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.repository.BhaiyaaaRepository
import com.codeaza.bhaiyaaa.data.repository.DeviceCallLogRepository
import com.codeaza.bhaiyaaa.data.repository.DeviceContactsRepository
import com.codeaza.bhaiyaaa.domain.model.ContactTag
import com.codeaza.bhaiyaaa.domain.model.Importance
import com.codeaza.bhaiyaaa.domain.model.MemorySource
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.util.PhoneNumbers
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Everything a user can save on a contact's profile, written through the
 * repository exactly as the UI does, then read back.
 *
 * The last test is the one that matters most: a device re-sync must never
 * overwrite what the user typed. That is the failure mode people actually
 * notice - you tag someone, the app syncs, and your work is gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProfileSaveTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: BhaiyaaaRepository
    private val now = 1_756_000_000_000L
    private val number = "+923001234567"

    // runBlocking, not runTest. runTest builds a TestScope with its own
    // scheduler and uncaught-exception handling, and doing that outside a test
    // body leaves state behind that surfaces as UncaughtExceptionsBeforeTest in
    // whichever test happens to run next in the same JVM - which made the
    // failure look flaky and land on an unrelated class.
    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BhaiyaaaRepository(
            context = context,
            db = db,
            deviceContacts = DeviceContactsRepository(context),
            deviceCallLog = DeviceCallLogRepository(context),
            now = { now }
        )
        db.contactDao().insertIfAbsent(
            listOf(
                ContactEntity(
                    phoneNumber = number,
                    matchKey = PhoneNumbers.matchKey(number),
                    name = "Ahmed Khan",
                    createdAt = now,
                    updatedAt = now
                )
            )
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `saving a VIP tier persists`() = runTest {
        repo.setVipLevel(number, VipLevel.SUPER_VIP)
        assertThat(repo.findContact(number)?.vipLevel).isEqualTo(VipLevel.SUPER_VIP.storageValue)

        // And it can be taken back off again.
        repo.setVipLevel(number, VipLevel.NONE)
        assertThat(repo.findContact(number)?.vipLevel).isEqualTo(VipLevel.NONE.storageValue)
    }

    @Test
    fun `saving a category tag persists`() = runTest {
        repo.setTag(number, ContactTag.WORK)
        assertThat(repo.findContact(number)?.tag).isEqualTo(ContactTag.WORK)

        repo.setTag(number, null)
        assertThat(repo.findContact(number)?.tag).isNull()
    }

    @Test
    fun `saving importance persists`() = runTest {
        repo.setImportance(number, Importance.CRITICAL.storageValue)
        assertThat(repo.findContact(number)?.importance).isEqualTo(Importance.CRITICAL.storageValue)
    }

    @Test
    fun `saving a note persists and can be edited`() = runTest {
        repo.setNotes(number, "Working on the tender project")
        assertThat(repo.findContact(number)?.notes).isEqualTo("Working on the tender project")

        repo.setNotes(number, "Prefers calls after 7pm")
        assertThat(repo.findContact(number)?.notes).isEqualTo("Prefers calls after 7pm")
    }

    @Test
    fun `clearing a note stores null rather than an empty string`() = runTest {
        repo.setNotes(number, "something")
        repo.setNotes(number, "   ")
        // Blank and absent must not be two different states, or the UI's
        // "has this changed?" check flickers.
        assertThat(repo.findContact(number)?.notes).isNull()
    }

    @Test
    fun `saving per-contact alert and spam flags persists`() = runTest {
        repo.setContactNotifications(number, false)
        repo.setSpam(number, true)
        val stored = repo.findContact(number)
        assertThat(stored?.notificationsEnabled).isFalse()
        assertThat(stored?.isSpam).isTrue()
    }

    @Test
    fun `saving a relationship persists`() = runTest {
        repo.setRelationship(number, "Client")
        assertThat(repo.findContact(number)?.relationship).isEqualTo("Client")
    }

    @Test
    fun `each save updates the timestamp without disturbing other fields`() = runTest {
        repo.setVipLevel(number, VipLevel.VIP)
        repo.setTag(number, ContactTag.FAMILY)
        repo.setNotes(number, "keep")
        repo.setImportance(number, Importance.HIGH.storageValue)

        val stored = repo.findContact(number)
        // Writing one field must not blank the others.
        assertThat(stored?.vipLevel).isEqualTo(VipLevel.VIP.storageValue)
        assertThat(stored?.tag).isEqualTo(ContactTag.FAMILY)
        assertThat(stored?.notes).isEqualTo("keep")
        assertThat(stored?.importance).isEqualTo(Importance.HIGH.storageValue)
        assertThat(stored?.name).isEqualTo("Ahmed Khan")
        assertThat(stored?.updatedAt).isEqualTo(now)
    }

    @Test
    fun `a memory saved against a contact is findable by search`() = runTest {
        repo.addMemory(
            body = "Wants the deployment finished by Friday",
            contactPhoneNumber = number,
            source = MemorySource.MANUAL
        )
        val forContact = repo.memoriesForContact(number).first()
        assertThat(forContact).hasSize(1)

        val hits = repo.searchMemories("deployment")
        assertThat(hits).hasSize(1)
        assertThat(hits.first().contactPhoneNumber).isEqualTo(number)
    }

    @Test
    fun `a reminder saved against a contact persists`() = runTest {
        repo.addReminder("Call back about the invoice", dueAt = now + 3_600_000, contactPhoneNumber = number)
        val active = repo.reminders.first()
        assertThat(active).hasSize(1)
        assertThat(active.first().contactPhoneNumber).isEqualTo(number)
        assertThat(active.first().dueAt).isEqualTo(now + 3_600_000)
    }

    @Test
    fun `resetting annotations clears user fields but keeps the contact and its name`() = runTest {
        repo.setVipLevel(number, VipLevel.EMERGENCY)
        repo.setTag(number, ContactTag.WORK)
        repo.setNotes(number, "secret")

        repo.resetVipAndAnnotations()

        val stored = repo.findContact(number)
        assertThat(stored).isNotNull()
        assertThat(stored?.name).isEqualTo("Ahmed Khan")
        assertThat(stored?.vipLevel).isEqualTo(VipLevel.NONE.storageValue)
        assertThat(stored?.tag).isNull()
        assertThat(stored?.notes).isNull()
    }

    @Test
    fun `a device re-sync never overwrites a saved profile`() = runTest {
        repo.setVipLevel(number, VipLevel.EMERGENCY)
        repo.setTag(number, ContactTag.CLIENT)
        repo.setNotes(number, "Tender project lead")
        repo.setImportance(number, Importance.CRITICAL.storageValue)
        repo.setContactNotifications(number, false)

        // Simulate the device offering the same contact again with default
        // fields, which is exactly what a sync does.
        db.contactDao().insertIfAbsent(
            listOf(
                ContactEntity(
                    phoneNumber = number,
                    matchKey = PhoneNumbers.matchKey(number),
                    name = "Ahmed Khan (mobile)",
                    createdAt = now,
                    updatedAt = now
                )
            )
        )
        db.contactDao().refreshDeviceFields(
            number, "Ahmed Khan (mobile)", PhoneNumbers.matchKey(number), now + 1
        )

        val stored = repo.findContact(number)
        // The device owns the name; the user owns everything else.
        assertThat(stored?.name).isEqualTo("Ahmed Khan (mobile)")
        assertThat(stored?.vipLevel).isEqualTo(VipLevel.EMERGENCY.storageValue)
        assertThat(stored?.tag).isEqualTo(ContactTag.CLIENT)
        assertThat(stored?.notes).isEqualTo("Tender project lead")
        assertThat(stored?.importance).isEqualTo(Importance.CRITICAL.storageValue)
        assertThat(stored?.notificationsEnabled).isFalse()
    }

    @Test
    fun `profile survives being read back through the observable flow`() = runTest {
        repo.setVipLevel(number, VipLevel.VIP)
        repo.setNotes(number, "note via flow")

        // The detail screen reads through this path, not findContact.
        val observed = repo.observeContactResolved(number).first()
        assertThat(observed?.vipLevel).isEqualTo(VipLevel.VIP.storageValue)
        assertThat(observed?.notes).isEqualTo("note via flow")
    }

    @Test
    fun `a contact resolves through the flow even by a differently formatted number`() = runTest {
        repo.setVipLevel(number, VipLevel.VIP)
        // What the detail screen may receive after route encoding / local format.
        val observed = repo.observeContactResolved("03001234567").first()
        assertThat(observed?.name).isEqualTo("Ahmed Khan")
    }
}
