package com.codeaza.bhaiyaaa.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.codeaza.bhaiyaaa.data.db.entity.ReminderEntity
import com.codeaza.bhaiyaaa.ui.screens.ReminderEditorDialog
import com.codeaza.bhaiyaaa.ui.screens.ReminderRow
import com.codeaza.bhaiyaaa.ui.theme.SukoonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/**
 * The reminder editor and list row, with controlled inputs.
 *
 * Robolectric rather than instrumentation: CI has no emulator and MIUI blocks
 * sideloading the instrumentation APK, so a test left in androidTest would
 * never run anywhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReminderUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun reminder(
        text: String = "Call the bank",
        dueAt: Long? = System.currentTimeMillis() + 3_600_000L,
        isDone: Boolean = false
    ) = ReminderEntity(
        id = 1L,
        text = text,
        createdAt = System.currentTimeMillis(),
        dueAt = dueAt,
        isDone = isDone
    )

    @Test
    fun editor_savesEditedText() {
        var savedText: String? = null
        composeRule.setContent {
            SukoonTheme {
                ReminderEditorDialog(
                    initialText = "Call the bank",
                    initialDueAt = System.currentTimeMillis() + 3_600_000L,
                    onSave = { text, _ -> savedText = text },
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithText("Call the bank").performTextReplacement("Call the bank back")
        composeRule.onNodeWithText("Save").performClick()
        assertEquals("Call the bank back", savedText)
    }

    @Test
    fun editor_somedayClearsTheDueDate() {
        // A reminder with no time is valid - it stays in the list and never
        // alerts - so clearing must produce null rather than a fallback time.
        var saved: Long? = -1L
        var called = false
        composeRule.setContent {
            SukoonTheme {
                ReminderEditorDialog(
                    initialText = "Call the bank",
                    initialDueAt = System.currentTimeMillis() + 3_600_000L,
                    onSave = { _, dueAt -> saved = dueAt; called = true },
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithText("Someday").performClick()
        composeRule.onNodeWithText("No time set - it stays in the list but never alerts.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Save").performClick()
        assertTrue(called)
        assertNull(saved)
    }

    @Test
    fun editor_givingASomedayReminderADayProducesATime() {
        var saved: Long? = null
        composeRule.setContent {
            SukoonTheme {
                ReminderEditorDialog(
                    initialText = "Buy milk",
                    initialDueAt = null,
                    onSave = { _, dueAt -> saved = dueAt },
                    onDismiss = {}
                )
            }
        }
        composeRule.onNodeWithText("Tomorrow").performClick()
        composeRule.onNodeWithText("Save").performClick()

        assertNotNull(saved)
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = saved!! }
        val tomorrow = Calendar.getInstance(TimeZone.getDefault())
            .apply { add(Calendar.DAY_OF_YEAR, 1) }
        assertEquals(tomorrow.get(Calendar.DAY_OF_YEAR), cal.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun row_completedReminderCanBeBroughtBack() {
        // The active list hides completed reminders, so the checkbox on a done
        // row is the only route back. It has to report false, not true.
        var reported: Boolean? = null
        composeRule.setContent {
            SukoonTheme {
                ReminderRow(
                    reminder = reminder(isDone = true),
                    overdue = false,
                    onToggleDone = { reported = it },
                    onEdit = {},
                    onSnooze = {},
                    onDelete = {}
                )
            }
        }
        composeRule.onNode(
            androidx.compose.ui.test.hasClickAction() and
                androidx.compose.ui.test.isToggleable()
        ).assertIsOn().performClick()
        assertEquals(false, reported)
    }

    @Test
    fun row_offersSnoozeOnlyWhenThereIsATimeToMove() {
        composeRule.setContent {
            SukoonTheme {
                ReminderRow(
                    reminder = reminder(dueAt = null),
                    overdue = false,
                    onToggleDone = {},
                    onEdit = {},
                    onSnooze = {},
                    onDelete = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("More actions for Call the bank").performClick()
        composeRule.onNodeWithText("Edit…").assertIsDisplayed()
        // Nothing to postpone when the reminder has no time in the first place.
        composeRule.onNodeWithText("In 10 minutes").assertDoesNotExist()
    }

    @Test
    fun row_snoozeMovesTheReminderForward() {
        var until: Long? = null
        composeRule.setContent {
            SukoonTheme {
                ReminderRow(
                    reminder = reminder(),
                    overdue = true,
                    onToggleDone = {},
                    onEdit = {},
                    onSnooze = { until = it },
                    onDelete = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("More actions for Call the bank").performClick()
        composeRule.onNodeWithText("In 10 minutes").performClick()
        assertNotNull(until)
        assertTrue(until!! > System.currentTimeMillis())
    }
}
