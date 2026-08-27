package com.codeaza.bhaiyaaa.domain

import com.codeaza.bhaiyaaa.domain.model.CallType
import com.codeaza.bhaiyaaa.domain.model.Importance
import com.codeaza.bhaiyaaa.domain.model.MemorySource
import com.codeaza.bhaiyaaa.domain.model.PersonalityMode
import com.codeaza.bhaiyaaa.domain.model.ThemeMode
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.service.CallAlertManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DomainModelTest {

    @Test
    fun `vip levels round-trip through their storage value`() {
        VipLevel.entries.forEach { level ->
            assertThat(VipLevel.from(level.storageValue)).isEqualTo(level)
        }
    }

    @Test
    fun `an unknown or missing vip value degrades to NONE`() {
        // A corrupt row must not be treated as a VIP by accident.
        assertThat(VipLevel.from(null)).isEqualTo(VipLevel.NONE)
        assertThat(VipLevel.from("GARBAGE")).isEqualTo(VipLevel.NONE)
        assertThat(VipLevel.from("")).isEqualTo(VipLevel.NONE)
    }

    @Test
    fun `vip tiers rank in escalating order`() {
        assertThat(VipLevel.EMERGENCY.rank).isGreaterThan(VipLevel.SUPER_VIP.rank)
        assertThat(VipLevel.SUPER_VIP.rank).isGreaterThan(VipLevel.VIP.rank)
        assertThat(VipLevel.VIP.rank).isGreaterThan(VipLevel.NONE.rank)
    }

    @Test
    fun `only real tiers count as vip`() {
        assertThat(VipLevel.NONE.isVip).isFalse()
        VipLevel.assignable.forEach { assertThat(it.isVip).isTrue() }
    }

    @Test
    fun `call types classify answered versus unanswered correctly`() {
        assertThat(CallType.MISSED.isUnanswered).isTrue()
        assertThat(CallType.REJECTED.isUnanswered).isTrue()
        assertThat(CallType.BLOCKED.isUnanswered).isTrue()
        assertThat(CallType.INCOMING.isUnanswered).isFalse()
        assertThat(CallType.OUTGOING.isUnanswered).isFalse()
    }

    @Test
    fun `unknown enum values fall back to safe defaults`() {
        assertThat(CallType.from("NOPE")).isEqualTo(CallType.OTHER)
        assertThat(MemorySource.from(null)).isEqualTo(MemorySource.MANUAL)
        assertThat(Importance.from(99)).isEqualTo(Importance.NORMAL)
        assertThat(PersonalityMode.from("X")).isEqualTo(PersonalityMode.FRIENDLY)
        assertThat(ThemeMode.from("X")).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `a valid vibration pattern parses to a waveform`() {
        val pattern = CallAlertManager.parsePattern("0,400,200,400")
        assertThat(pattern).isNotNull()
        assertThat(pattern!!.toList()).containsExactly(0L, 400L, 200L, 400L).inOrder()
    }

    @Test
    fun `a malformed vibration pattern is rejected rather than buzzing randomly`() {
        assertThat(CallAlertManager.parsePattern("")).isNull()
        assertThat(CallAlertManager.parsePattern("abc")).isNull()
        assertThat(CallAlertManager.parsePattern("-1,400")).isNull()
        // Absurd durations would leave the motor running.
        assertThat(CallAlertManager.parsePattern("0,999999")).isNull()
        assertThat(CallAlertManager.parsePattern((1..50).joinToString(",") { "100" })).isNull()
    }
}
