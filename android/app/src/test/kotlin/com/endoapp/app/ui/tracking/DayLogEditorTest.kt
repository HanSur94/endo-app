package com.endoapp.app.ui.tracking

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.endoapp.app.content.TrackingCopy
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.insights.SymptomType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Day Log editor behaviors (ATRK-02, threats T-20-01 / T-20-04b) on the JVM Robolectric+Compose
 * lane:
 *
 * 1. The editor renders the §1 sections + the Save capsule and loads/saves through the honesty mapper.
 * 2. A fresh (never-logged) day offers NO delete; a day WITH data offers a guarded "Delete this day"
 *    behind a confirmation dialog whose confirm delegates to the [deleteDay] cascade.
 * 3. An untouched section reads "Not logged", never a phantom 0.
 * 4. Save maps the draft via [DayDraftMapper.toRows] and persists it through the injected seam.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DayLogEditorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val day = 20_200L // 2025-04-22

    @Test
    fun freshDay_rendersSectionsAndSave_noDelete() {
        composeRule.setContent {
            BloomTheme {
                DayLogEditorScreen(
                    epochDay = day,
                    loadDay = { null }, // a fresh day
                    persistDay = {},
                    deleteDay = {},
                    onDismiss = {},
                )
            }
        }

        // The Save capsule + a representative set of the §1 sections render.
        composeRule.onNodeWithTag("daylog.save").assertExists()
        composeRule.onNodeWithTag("daylog.symptom.${SymptomType.PELVIC_PAIN.raw}").assertExists()
        composeRule.onNodeWithTag("bodymap.sideToggle").assertExists() // the Pain areas BodyMap is mounted
        composeRule.onNodeWithTag("daylog.note").assertExists()

        // A fresh day (no existing data) offers NO destructive delete.
        composeRule.onNodeWithTag("daylog.delete").assertDoesNotExist()
    }

    @Test
    fun untouchedSection_readsNotLogged_neverZero() {
        composeRule.setContent {
            BloomTheme {
                DayLogEditorScreen(
                    epochDay = day,
                    loadDay = { null },
                    persistDay = {},
                    deleteDay = {},
                    onDismiss = {},
                )
            }
        }

        // The collapsed stress reveal row is a representative untouched section — it reads "Not logged".
        composeRule.onNodeWithTag("daylog.stress").assertExists()
        assertTrue(
            "an untouched section must read \"Not logged\", never 0",
            composeRule.onAllNodesWithText(TrackingCopy.NOT_LOGGED).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun dayWithData_showsGuardedDelete_confirmDelegatesToCascade() {
        var deletedDay: Long? = null
        var dismissed = 0

        composeRule.setContent {
            BloomTheme {
                DayLogEditorScreen(
                    epochDay = day,
                    // A day that already has data (a logged symptom) → the delete is offered.
                    loadDay = { DayDraft(symptoms = mapOf(SymptomType.FATIGUE to 4)) },
                    persistDay = {},
                    deleteDay = { deletedDay = it },
                    onDismiss = { dismissed++ },
                )
            }
        }
        composeRule.waitForIdle()

        // The guarded delete is offered; tapping it opens the confirmation dialog (its confirm +
        // cancel actions surface). Scroll it into view first (it sits below the full form).
        composeRule.onNodeWithTag("daylog.delete").assertExists().performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("daylog.deleteConfirm").assertExists()
        composeRule.onNodeWithTag("daylog.deleteCancel").assertExists()

        // Confirm delegates to the delete cascade for THIS day and dismisses.
        composeRule.onNodeWithTag("daylog.deleteConfirm").performClick()
        composeRule.waitForIdle()

        assertEquals("delete cascade runs for the edited day", day, deletedDay)
        assertEquals("editor dismisses after delete", 1, dismissed)
    }

    @Test
    fun save_persistsThroughTheMapper_andDismisses() {
        var captured: DayLogRows? = null
        var dismissed = 0

        composeRule.setContent {
            BloomTheme {
                DayLogEditorScreen(
                    epochDay = day,
                    loadDay = { null },
                    persistDay = { captured = it },
                    deleteDay = {},
                    onDismiss = { dismissed++ },
                    idFor = { "$day:$it" },
                )
            }
        }

        composeRule.onNodeWithTag("daylog.save").performClick()
        composeRule.waitForIdle()

        val rows = requireNotNull(captured) { "Save must persist the mapped rows through the seam" }
        assertEquals("the anchor records THIS day", day, rows.dayEntry.epochDay)
        assertEquals("Save dismisses the editor", 1, dismissed)
    }
}
