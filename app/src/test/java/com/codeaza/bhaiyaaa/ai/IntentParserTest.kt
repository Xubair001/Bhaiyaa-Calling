package com.codeaza.bhaiyaaa.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IntentParserTest {

    private val now = 1_756_000_000_000L

    @Test
    fun `reminder phrasing is recognised and the body extracted`() {
        val intent = IntentParser.parse("remind me to call Ali tomorrow", now)
        assertThat(intent).isInstanceOf(AssistantIntent.CreateReminder::class.java)
        val reminder = intent as AssistantIntent.CreateReminder
        assertThat(reminder.text).isEqualTo("call Ali")
        assertThat(reminder.dueAt).isNotNull()
    }

    @Test
    fun `reminders win over call keywords in the same sentence`() {
        // "call" also appears in call-history questions; the reminder prefix
        // has to be checked first or this becomes a CallCount query.
        val intent = IntentParser.parse("remind me to call the bank today", now)
        assertThat(intent).isInstanceOf(AssistantIntent.CreateReminder::class.java)
    }

    @Test
    fun `missed call questions carry their period`() {
        assertThat(IntentParser.parse("any missed calls today?", now))
            .isEqualTo(AssistantIntent.MissedCalls(Period.TODAY))
        assertThat(IntentParser.parse("missed calls this week", now))
            .isEqualTo(AssistantIntent.MissedCalls(Period.THIS_WEEK))
    }

    @Test
    fun `vip questions map to the vip list`() {
        assertThat(IntentParser.parse("show my VIP contacts", now)).isEqualTo(AssistantIntent.VipList)
    }

    @Test
    fun `last call questions extract the name`() {
        val intent = IntentParser.parse("when did I last talk to Ahmed?", now)
        assertThat(intent).isEqualTo(AssistantIntent.LastCallWith("Ahmed"))
    }

    @Test
    fun `who called me maps to recent callers`() {
        assertThat(IntentParser.parse("who called me?", now)).isEqualTo(AssistantIntent.RecentCallers)
    }

    @Test
    fun `most contacted is recognised`() {
        assertThat(IntentParser.parse("who called me most this week?", now))
            .isEqualTo(AssistantIntent.MostContacted(Period.THIS_WEEK))
    }

    @Test
    fun `how many calls maps to a count`() {
        assertThat(IntentParser.parse("how many calls did I get today?", now))
            .isEqualTo(AssistantIntent.CallCount(Period.TODAY))
    }

    @Test
    fun `memory recall is distinguished from a call question`() {
        val intent = IntentParser.parse("what did Ahmed say about the deployment", now)
        assertThat(intent).isInstanceOf(AssistantIntent.RecallMemory::class.java)
    }

    @Test
    fun `when did I last speak is a call question not a memory one`() {
        // Both start with "when did", so the parser must disambiguate.
        val intent = IntentParser.parse("when did I last speak to Sara", now)
        assertThat(intent).isEqualTo(AssistantIntent.LastCallWith("Sara"))
    }

    @Test
    fun `unrecognised input returns Unknown rather than a guess`() {
        // The whole anti-hallucination stance rests on this.
        assertThat(IntentParser.parse("what is the capital of France", now))
            .isEqualTo(AssistantIntent.Unknown)
        assertThat(IntentParser.parse("asdkjfhasdf", now)).isEqualTo(AssistantIntent.Unknown)
    }

    @Test
    fun `blank input is Unknown`() {
        assertThat(IntentParser.parse("", now)).isEqualTo(AssistantIntent.Unknown)
        assertThat(IntentParser.parse("   ", now)).isEqualTo(AssistantIntent.Unknown)
    }

    @Test
    fun `help is recognised`() {
        assertThat(IntentParser.parse("help", now)).isEqualTo(AssistantIntent.Help)
        assertThat(IntentParser.parse("what can you do?", now)).isEqualTo(AssistantIntent.Help)
    }

    @Test
    fun `name extraction handles the common markers`() {
        assertThat(IntentParser.extractName("last call with Ahmed Khan")).isEqualTo("Ahmed Khan")
        assertThat(IntentParser.extractName("when did I last talk to Ali?")).isEqualTo("Ali")
        assertThat(IntentParser.extractName("no marker here")).isEmpty()
    }
}
