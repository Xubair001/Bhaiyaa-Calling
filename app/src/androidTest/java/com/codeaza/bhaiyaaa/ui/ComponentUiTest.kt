package com.codeaza.bhaiyaaa.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.codeaza.bhaiyaaa.data.db.entity.CallRecordEntity
import com.codeaza.bhaiyaaa.domain.model.VipLevel
import com.codeaza.bhaiyaaa.ui.components.CallRow
import com.codeaza.bhaiyaaa.ui.components.EmptyState
import com.codeaza.bhaiyaaa.ui.components.StatTile
import com.codeaza.bhaiyaaa.ui.components.VipBadge
import com.codeaza.bhaiyaaa.ui.theme.BhaiyaaaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Component-level UI tests. Isolated composables with controlled inputs, so
 * these assert rendering and accessibility rather than depending on the
 * device's real contacts or call log.
 */
class ComponentUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyState_showsTitleBodyAndAction() {
        var clicked = false
        composeRule.setContent {
            BhaiyaaaTheme {
                EmptyState(
                    icon = Icons.Filled.Star,
                    title = "No VIPs yet",
                    body = "Set someone as VIP to get special alerts.",
                    actionLabel = "Sync now",
                    onAction = { clicked = true }
                )
            }
        }

        composeRule.onNodeWithText("No VIPs yet").assertIsDisplayed()
        composeRule.onNodeWithText("Set someone as VIP to get special alerts.").assertIsDisplayed()
        composeRule.onNodeWithText("Sync now").performClick()
        assertTrue(clicked)
    }

    @Test
    fun vipBadge_announcesTierToScreenReaders() {
        composeRule.setContent {
            BhaiyaaaTheme { VipBadge(VipLevel.SUPER_VIP) }
        }
        // Colour must never be the only signal of tier.
        composeRule.onNodeWithContentDescription("Super VIP contact").assertExists()
        composeRule.onNodeWithText("SUPER VIP").assertIsDisplayed()
    }

    @Test
    fun vipBadge_rendersNothingForNonVip() {
        composeRule.setContent {
            BhaiyaaaTheme { VipBadge(VipLevel.NONE) }
        }
        composeRule.onNodeWithText("NONE").assertDoesNotExist()
    }

    @Test
    fun callRow_showsNameDirectionAndDuration() {
        val call = CallRecordEntity(
            id = 1,
            phoneNumber = "+923001234567",
            matchKey = "001234567",
            contactName = "Ahmed Khan",
            type = "MISSED",
            timestamp = System.currentTimeMillis() - 60_000,
            durationSeconds = 0
        )
        composeRule.setContent {
            BhaiyaaaTheme { CallRow(call = call, vipLevel = VipLevel.VIP) }
        }

        composeRule.onNodeWithText("Ahmed Khan").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Missed call").assertExists()
        composeRule.onNodeWithContentDescription("VIP contact").assertExists()
    }

    @Test
    fun callRow_fallsBackToTheNumberWhenThereIsNoName() {
        val call = CallRecordEntity(
            id = 2,
            phoneNumber = "+923009999999",
            matchKey = "009999999",
            contactName = null,
            type = "INCOMING",
            timestamp = System.currentTimeMillis(),
            durationSeconds = 45
        )
        composeRule.setContent {
            BhaiyaaaTheme { CallRow(call = call) }
        }
        composeRule.onNodeWithText("+923009999999").assertIsDisplayed()
        composeRule.onNodeWithText("45s").assertIsDisplayed()
    }

    @Test
    fun statTile_showsValueAndLabel() {
        composeRule.setContent {
            BhaiyaaaTheme { StatTile(value = "7", label = "Calls today") }
        }
        composeRule.onNodeWithText("7").assertIsDisplayed()
        composeRule.onNodeWithText("Calls today").assertIsDisplayed()
    }
}
