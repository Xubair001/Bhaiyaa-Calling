package com.codeaza.bhaiyaaa

import com.codeaza.bhaiyaaa.assistant.AssistantEngine
import com.codeaza.bhaiyaaa.assistant.AssistantResult
import com.codeaza.bhaiyaaa.data.db.CallRecordEntity
import com.codeaza.bhaiyaaa.data.db.ContactEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantEngineTest {

    private val contacts = listOf(
        ContactEntity(phoneNumber = "+1111", name = "Ahmed", vipLevel = "VIP"),
        ContactEntity(phoneNumber = "+2222", name = "Ali", vipLevel = "NONE")
    )

    private val calls = listOf(
        CallRecordEntity(phoneNumber = "+1111", contactName = "Ahmed", type = "MISSED", timestamp = 1000L, durationSeconds = 0),
        CallRecordEntity(phoneNumber = "+2222", contactName = "Ali", type = "INCOMING", timestamp = 2000L, durationSeconds = 30)
    )

    @Test
    fun `never invents a VIP that is not in the real contact list`() {
        val result = AssistantEngine.process("who are my vips", contacts, calls) as AssistantResult.Reply
        assertTrue(result.text.contains("Ahmed"))
        assertTrue(!result.text.contains("Ali") || result.text.contains("Ahmed (VIP)"))
    }

    @Test
    fun `missed call count matches actual data, not a guess`() {
        val result = AssistantEngine.process("any missed calls?", contacts, calls) as AssistantResult.Reply
        assertTrue(result.text.contains("1"))
    }

    @Test
    fun `remind me creates a reminder result instead of a plain reply`() {
        val result = AssistantEngine.process("remind me to call Ahmed tomorrow", contacts, calls)
        assertTrue(result is AssistantResult.ReminderCreated)
    }

    @Test
    fun `unrecognized query returns a helpful fallback, never a fabricated answer`() {
        val result = AssistantEngine.process("what is the meaning of life", contacts, calls) as AssistantResult.Reply
        assertTrue(result.text.contains("Try asking"))
    }
}
