package com.codeaza.bhaiyaaa.ai

import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.entity.ContactEntity
import com.codeaza.bhaiyaaa.data.db.entity.MemoryEntity
import com.codeaza.bhaiyaaa.data.db.entity.ReminderEntity
import com.codeaza.bhaiyaaa.data.db.projection.ContactCallCount
import com.codeaza.bhaiyaaa.domain.model.PersonalityMode
import com.codeaza.bhaiyaaa.domain.model.PrayerSilenceMode
import com.codeaza.bhaiyaaa.domain.model.SilenceSource
import com.codeaza.bhaiyaaa.domain.model.SilenceWindow
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.util.PhoneNumbers
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The assistant's contract: answer only from the data it was given, and admit
 * ignorance otherwise. These tests use a fake data source with known contents,
 * so any invented fact would show up as a failure.
 */
class RuleBasedAssistantEngineTest {

    private val now = 1_756_000_000_000L

    private fun contact(name: String, number: String, vip: VipLevel = VipLevel.NONE) =
        ContactEntity(
            phoneNumber = number,
            matchKey = PhoneNumbers.matchKey(number),
            name = name,
            vipLevel = vip.storageValue,
            createdAt = now,
            updatedAt = now
        )

    private fun call(
        id: Long,
        number: String,
        name: String?,
        type: String,
        at: Long,
        duration: Long = 0
    ) = CallRecordEntity(
        id = id,
        phoneNumber = number,
        matchKey = PhoneNumbers.matchKey(number),
        contactName = name,
        type = type,
        timestamp = at,
        durationSeconds = duration
    )

    private class FakeData(
        val contacts: List<ContactEntity> = emptyList(),
        val calls: List<CallRecordEntity> = emptyList(),
        val memories: List<MemoryEntity> = emptyList(),
        val reminders: List<ReminderEntity> = emptyList()
    ) : AssistantDataSource {
        var createdReminders = mutableListOf<Pair<String, Long?>>()

        override suspend fun allContacts() = contacts
        override suspend fun vipContacts() = contacts.filter { it.vipLevel != "NONE" }
        override suspend fun recentCalls(limit: Int) = calls.sortedByDescending { it.timestamp }.take(limit)
        override suspend fun countCallsSince(since: Long) = calls.count { it.timestamp >= since }
        override suspend fun countMissedSince(since: Long) =
            calls.count { it.timestamp >= since && it.type == "MISSED" }

        override suspend fun mostContactedSince(since: Long, limit: Int) =
            calls.filter { it.timestamp >= since }
                .groupBy { it.matchKey }
                .map { (key, rows) ->
                    ContactCallCount(key, rows.first().contactName, rows.size, rows.sumOf { it.durationSeconds })
                }
                .sortedByDescending { it.callCount }
                .take(limit)

        override suspend fun searchMemories(query: String, limit: Int) =
            memories.filter { m -> query.split(" ").any { m.body.contains(it, ignoreCase = true) } }.take(limit)

        override suspend fun pendingReminders() = reminders
        override suspend fun createReminder(text: String, dueAt: Long?): Long {
            createdReminders += text to dueAt
            return createdReminders.size.toLong()
        }

        override suspend fun callsForMatchKey(matchKey: String, limit: Int) =
            calls.filter { it.matchKey == matchKey }.sortedByDescending { it.timestamp }.take(limit)

        var active: SilenceWindow? = null
        var next: SilenceWindow? = null
        override suspend fun activeQuietWindow() = active
        override suspend fun nextQuietWindow() = next
    }

    private fun engine(data: AssistantDataSource, mode: PersonalityMode = PersonalityMode.FRIENDLY) =
        RuleBasedAssistantEngine(data, DefaultPhrasebook(mode), now = { now })

    @Test
    fun `says there are no missed calls when there are none`() = runTest {
        val response = engine(FakeData()).respond("any missed calls?")
        assertThat(response.text).contains("caught up")
    }

    @Test
    fun `counts real missed calls and names the caller`() = runTest {
        val data = FakeData(
            calls = listOf(
                call(1, "+923001234567", "Ahmed", "MISSED", now - 60_000),
                call(2, "+923001234567", "Ahmed", "MISSED", now - 120_000)
            )
        )
        val response = engine(data).respond("any missed calls?")
        assertThat(response.text).contains("2 missed calls")
        assertThat(response.text).contains("Ahmed")
    }

    @Test
    fun `lists real VIPs grouped by tier`() = runTest {
        val data = FakeData(
            contacts = listOf(
                contact("Ahmed", "+923001234567", VipLevel.SUPER_VIP),
                contact("Ali", "+923007654321", VipLevel.VIP),
                contact("Random", "+923009999999")
            )
        )
        val response = engine(data).respond("show my vip contacts")
        assertThat(response.text).contains("Ahmed")
        assertThat(response.text).contains("Ali")
        // A non-VIP must never be listed as one.
        assertThat(response.text).doesNotContain("Random")
    }

    @Test
    fun `admits when there are no VIPs rather than inventing one`() = runTest {
        val response = engine(FakeData(contacts = listOf(contact("Ali", "+923001111111"))))
            .respond("show my vips")
        assertThat(response.text).contains("No VIP contacts set")
    }

    @Test
    fun `reports the real last call with a named contact`() = runTest {
        val data = FakeData(
            contacts = listOf(contact("Ahmed", "+923001234567")),
            calls = listOf(call(1, "+923001234567", "Ahmed", "INCOMING", now - 3_600_000, 120))
        )
        val response = engine(data).respond("when did I last talk to Ahmed?")
        assertThat(response.text).contains("Ahmed")
        assertThat(response.sources).isNotEmpty()
    }

    @Test
    fun `does not invent a contact that does not exist`() = runTest {
        val data = FakeData(contacts = listOf(contact("Ahmed", "+923001234567")))
        val response = engine(data).respond("when did I last talk to Nobody?")
        assertThat(response.text).contains("don't have a contact")
    }

    @Test
    fun `asks which person when a name is ambiguous`() = runTest {
        val data = FakeData(
            contacts = listOf(
                contact("Ali Raza", "+923001111111"),
                contact("Ali Hassan", "+923002222222")
            )
        )
        val response = engine(data).respond("when did I last talk to Ali?")
        // Picking one silently would risk reporting the wrong person's calls.
        assertThat(response.text).contains("Which one")
    }

    @Test
    fun `creates a real reminder and reports it`() = runTest {
        val data = FakeData()
        val response = engine(data).respond("remind me to call Ali tomorrow")
        assertThat(data.createdReminders).hasSize(1)
        assertThat(data.createdReminders.first().first).isEqualTo("call Ali")
        assertThat(data.createdReminders.first().second).isNotNull()
        assertThat(response.action).isInstanceOf(AssistantAction.ReminderCreated::class.java)
    }

    @Test
    fun `recalls only memories the user actually saved`() = runTest {
        val data = FakeData(
            memories = listOf(
                MemoryEntity(
                    id = 1,
                    body = "Ahmed wants the deployment done by Friday",
                    source = "MANUAL",
                    createdAt = now,
                    updatedAt = now
                )
            )
        )
        val response = engine(data).respond("what did Ahmed say about deployment")
        assertThat(response.text).contains("deployment done by Friday")
    }

    @Test
    fun `says nothing is saved rather than guessing at a conversation`() = runTest {
        val response = engine(FakeData()).respond("what did Ahmed say about the invoice")
        assertThat(response.text).contains("nothing saved")
        // The honesty statement matters as much as the answer.
        assertThat(response.text).contains("can't hear or record your calls")
    }

    @Test
    fun `unknown questions offer help instead of an answer`() = runTest {
        val response = engine(FakeData()).respond("what is the capital of France")
        assertThat(response.intent).isEqualTo(AssistantIntent.Unknown)
        assertThat(response.text).contains("Didn't catch that")
        assertThat(response.text).doesNotContain("Paris")
    }

    @Test
    fun `personality changes wording but never the facts`() = runTest {
        val data = FakeData(calls = listOf(call(1, "+92300", "Ahmed", "MISSED", now - 1000)))
        val bhai = engine(data, PersonalityMode.BHAI).respond("missed calls?")
        val pro = engine(data, PersonalityMode.PROFESSIONAL).respond("missed calls?")
        assertThat(bhai.text).contains("1 missed call")
        assertThat(pro.text).contains("1 missed call")
        assertThat(bhai.text).contains("bhai")
        assertThat(pro.text).doesNotContain("bhai")
    }

    @Test
    fun `silence for a duration is understood and asked for, not performed`() = runTest {
        val response = engine(FakeData()).respond("silence my phone for 20 minutes")

        assertThat(response.intent).isEqualTo(AssistantIntent.SilenceFor(20))
        val action = response.action
        assertThat(action).isInstanceOf(AssistantAction.SilenceRequested::class.java)
        // The engine stays pure: it decides, the caller touches the ringer.
        assertThat((action as AssistantAction.SilenceRequested).minutes).isEqualTo(20)
    }

    @Test
    fun `hours are converted to minutes`() = runTest {
        val response = engine(FakeData()).respond("mute for 2 hours")
        assertThat(response.intent).isEqualTo(AssistantIntent.SilenceFor(120))
    }

    @Test
    fun `a bare silence request gets a sensible default`() = runTest {
        val response = engine(FakeData()).respond("silence my phone")
        assertThat(response.intent).isEqualTo(AssistantIntent.SilenceFor(30))
    }

    @Test
    fun `it reports a quiet window already running`() = runTest {
        val data = FakeData()
        data.active = SilenceWindow(
            key = "prayer:ASR", label = "Asr", source = SilenceSource.PRAYER,
            anchorMillis = now, startMillis = now - 60_000, durationMinutes = 15,
            enabled = true, mode = PrayerSilenceMode.SILENT
        )
        val response = engine(data).respond("when is the next prayer")
        assertThat(response.text).contains("Asr")
        assertThat(response.text).contains("quiet until")
    }

    @Test
    fun `it reports the next quiet window when none is running`() = runTest {
        val data = FakeData()
        data.next = SilenceWindow(
            key = "prayer:MAGHRIB", label = "Maghrib", source = SilenceSource.PRAYER,
            anchorMillis = now + 3_600_000, startMillis = now + 3_420_000,
            durationMinutes = 15, enabled = true, mode = PrayerSilenceMode.SILENT
        )
        val response = engine(data).respond("when is the next prayer")
        assertThat(response.text).contains("Maghrib")
    }

    @Test
    fun `with nothing scheduled it says so rather than inventing a time`() = runTest {
        val response = engine(FakeData()).respond("when is the next prayer")
        assertThat(response.text).contains("Nothing scheduled")
    }

    @Test
    fun `a reminder mentioning an hour is not mistaken for a silence request`() = runTest {
        val data = FakeData()
        val response = engine(data).respond("remind me to call Ali in 2 hours")
        // "hours" appears in both phrasings; the reminder prefix has to win.
        assertThat(response.intent).isInstanceOf(AssistantIntent.CreateReminder::class.java)
        assertThat(data.createdReminders).hasSize(1)
    }

    @Test
    fun `counts calls today from real data`() = runTest {
        val data = FakeData(
            calls = listOf(
                call(1, "+92300", "A", "INCOMING", now - 1000),
                call(2, "+92301", "B", "OUTGOING", now - 2000)
            )
        )
        val response = engine(data).respond("how many calls today?")
        assertThat(response.text).contains("2 calls")
    }
}
