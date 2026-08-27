package com.codeaza.bhaiyaaa.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Number reconciliation is load-bearing: if these break, VIP alerts silently
 * stop firing because a contact never matches its own call-log rows.
 */
class PhoneNumbersTest {

    @Test
    fun `normalize strips formatting but keeps a leading plus`() {
        assertThat(PhoneNumbers.normalize("+92 300 123-4567")).isEqualTo("+923001234567")
        assertThat(PhoneNumbers.normalize("(0300) 123 4567")).isEqualTo("03001234567")
        assertThat(PhoneNumbers.normalize("  0300-1234567 ")).isEqualTo("03001234567")
    }

    @Test
    fun `normalize returns empty for junk`() {
        assertThat(PhoneNumbers.normalize(null)).isEmpty()
        assertThat(PhoneNumbers.normalize("")).isEmpty()
        assertThat(PhoneNumbers.normalize("   ")).isEmpty()
        assertThat(PhoneNumbers.normalize("Unknown")).isEmpty()
    }

    @Test
    fun `international and local forms of the same number match`() {
        // The exact case that breaks naive string comparison.
        assertThat(PhoneNumbers.sameNumber("+92 300 1234567", "03001234567")).isTrue()
        assertThat(PhoneNumbers.sameNumber("0092-300-1234567", "0300 1234567")).isTrue()
    }

    @Test
    fun `different numbers do not match`() {
        assertThat(PhoneNumbers.sameNumber("+923001234567", "+923009999999")).isFalse()
    }

    @Test
    fun `short codes are kept whole so they cannot collide with real numbers`() {
        assertThat(PhoneNumbers.matchKey("8558")).isEqualTo("8558")
        assertThat(PhoneNumbers.sameNumber("8558", "923001238558")).isFalse()
    }

    @Test
    fun `blank numbers never match each other`() {
        // Two unknown callers must not be treated as the same person.
        assertThat(PhoneNumbers.sameNumber("", "")).isFalse()
        assertThat(PhoneNumbers.sameNumber(null, null)).isFalse()
    }

    @Test
    fun `matchKey takes the last nine significant digits`() {
        assertThat(PhoneNumbers.matchKey("+923001234567")).isEqualTo("001234567")
        assertThat(PhoneNumbers.matchKey("03001234567")).isEqualTo("001234567")
    }
}
