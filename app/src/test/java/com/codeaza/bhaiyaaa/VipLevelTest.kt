package com.codeaza.bhaiyaaa

import com.codeaza.bhaiyaaa.util.VipLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class VipLevelTest {
    @Test
    fun `label returns human readable text for each level`() {
        assertEquals("None", VipLevel.label("NONE"))
        assertEquals("VIP", VipLevel.label("VIP"))
        assertEquals("Super VIP", VipLevel.label("SUPER_VIP"))
        assertEquals("Emergency", VipLevel.label("EMERGENCY"))
    }

    @Test
    fun `unknown level falls back to None instead of crashing`() {
        assertEquals("None", VipLevel.label("garbage"))
    }

    @Test
    fun `ALL contains exactly four levels`() {
        assertEquals(4, VipLevel.ALL.size)
    }
}
