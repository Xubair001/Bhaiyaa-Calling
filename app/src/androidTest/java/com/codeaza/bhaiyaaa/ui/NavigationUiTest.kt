package com.codeaza.bhaiyaaa.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codeaza.bhaiyaaa.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end navigation over the real activity.
 *
 * Deliberately asserts only on chrome that exists regardless of what data or
 * permissions the test device happens to have - a test that needed real
 * contacts would be flaky on a fresh emulator.
 */
@RunWith(AndroidJUnit4::class)
class NavigationUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** Steps past onboarding if this is a fresh install. */
    private fun completeOnboardingIfShown() {
        repeat(6) {
            val next = composeRule.onAllNodesWithTextSafely("Next")
            val start = composeRule.onAllNodesWithTextSafely("Get started")
            when {
                start > 0 -> {
                    composeRule.onNodeWithText("Get started").performClick()
                    composeRule.waitForIdle()
                    return
                }
                next > 0 -> {
                    composeRule.onNodeWithText("Next").performClick()
                    composeRule.waitForIdle()
                }
                else -> return
            }
        }
    }

    @Test
    fun bottomBar_showsEveryTopLevelDestination() {
        completeOnboardingIfShown()
        listOf("Home", "Calls", "Contacts", "Assistant", "More").forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
    }

    @Test
    fun tappingMore_revealsTheSecondaryDestinations() {
        completeOnboardingIfShown()
        composeRule.onNodeWithText("More").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("VIP contacts").assertIsDisplayed()
        composeRule.onNodeWithText("Memory").assertIsDisplayed()
        composeRule.onNodeWithText("Insights").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun settings_openseverySection() {
        completeOnboardingIfShown()
        composeRule.onNodeWithText("More").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()

        listOf("Notifications", "VIP alerts", "Appearance", "Assistant personality", "AI models",
            "Security", "Data", "About BHAIYAAA").forEach { row ->
            composeRule.onNodeWithText(row).assertExists()
        }
    }

    @Test
    fun appearanceSettings_offersEveryThemeMode() {
        completeOnboardingIfShown()
        composeRule.onNodeWithText("More").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Appearance").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Follow system").assertIsDisplayed()
        composeRule.onNodeWithText("Light").assertIsDisplayed()
        composeRule.onNodeWithText("Dark").assertIsDisplayed()
    }

    @Test
    fun personalitySettings_previewChangesWithTheSelectedTone() {
        completeOnboardingIfShown()
        composeRule.onNodeWithText("More").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Assistant personality").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Bhai Mode").performClick()

        // The tone is persisted to DataStore and read back as a Flow, so the
        // recomposition happens after an async round-trip. waitForIdle() can
        // return before that lands, so poll for the node instead of asserting
        // immediately - otherwise this passes or fails on timing.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodes(hasText("Assalam o Alaikum, boss 👋"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        // The preview is generated from the same phrasebook the app uses.
        composeRule.onNodeWithText("Assalam o Alaikum, boss 👋").assertExists()
    }

    @Test
    fun assistant_offersSuggestionsAndAnInputField() {
        completeOnboardingIfShown()
        composeRule.onNodeWithText("Assistant").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Ask BHAIYAAA").assertIsDisplayed()
        composeRule.onNodeWithText("Who called me?").assertExists()
    }
}

/** Counts matching nodes without throwing when there are none. */
private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextSafely(text: String): Int =
    runCatching {
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
    }.getOrDefault(0)
