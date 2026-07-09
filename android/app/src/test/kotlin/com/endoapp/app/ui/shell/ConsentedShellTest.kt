package com.endoapp.app.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.endoapp.app.CheckInRouter
import com.endoapp.app.designsystem.BloomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The consented 3-tab shell behavior (ASHL-03) — proven on the JVM Robolectric+Compose lane. Renders
 * the real [ConsentedShell] inside [BloomTheme] over a real [CheckInRouter] and asserts:
 *
 *  1. Default tab is Log; all three tab nodes exist; the Log placeholder body is shown.
 *  2. Tapping Insights switches the rendered body (Insights placeholder shown, Log placeholder gone).
 *  3. System back from Insights returns to Log — driven through the activity's real
 *     `OnBackPressedDispatcher` (the same signal a device back gesture commits to).
 *  4. The pure [tabBackTarget] decision: Insights/Explore → Log, Log → `null` (back not intercepted,
 *     so it exits) — the logic-level pin for the predictive-back rule.
 *
 * Uses `createAndroidComposeRule<ComponentActivity>()` (vs. the `createComposeRule()` of
 * `AppGateConsentTest`) precisely so the test can reach `activity.onBackPressedDispatcher` and drive
 * a committed back. The predictive-back ANIMATION and the true edge-to-edge inset RENDERING are the
 * Manual-Only device checks per 19-VALIDATION — this suite pins the state transitions, not the pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConsentedShellTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setShell(router: CheckInRouter = CheckInRouter()) {
        composeRule.setContent {
            BloomTheme {
                ConsentedShell(router = router, onWithdraw = {})
            }
        }
    }

    // --- 1. Default Log tab + Log placeholder ---

    @Test
    fun defaultTab_isLog_showsThreeTabs_andLogPlaceholder() {
        setShell()

        composeRule.onNodeWithTag("tab.log").assertExists()
        composeRule.onNodeWithTag("tab.insights").assertExists()
        composeRule.onNodeWithTag("tab.explore").assertExists()
        composeRule.onNodeWithTag("tab.log").assertIsSelected()

        composeRule.onNodeWithTag("tab.log.body").assertExists()
        composeRule.onNodeWithTag("tab.insights.body").assertDoesNotExist()
    }

    // --- 2. Tab switch swaps the rendered body ---

    @Test
    fun tappingInsights_rendersInsightsPlaceholder_andHidesLog() {
        setShell()

        composeRule.onNodeWithTag("tab.insights").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tab.insights").assertIsSelected()
        composeRule.onNodeWithTag("tab.insights.body").assertExists()
        composeRule.onNodeWithTag("tab.log.body").assertDoesNotExist()
    }

    // --- 3. Back from Insights returns to Log (driven through the real back dispatcher) ---

    @Test
    fun backFromInsights_returnsToLog() {
        setShell()

        composeRule.onNodeWithTag("tab.insights").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tab.insights.body").assertExists()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        // Back off a secondary tab returns to the start tab (Log), not app exit.
        composeRule.onNodeWithTag("tab.log").assertIsSelected()
        composeRule.onNodeWithTag("tab.log.body").assertExists()
        composeRule.onNodeWithTag("tab.insights.body").assertDoesNotExist()
    }

    // --- 4. The pure predictive-back decision (Log is not intercepted → exits) ---

    @Test
    fun tabBackTarget_returnsLogFromSecondaryTabs_andNullFromLog() {
        assertNull(
            "Back from Log must NOT be intercepted (returns null → falls through to exit).",
            tabBackTarget(ConsentedTab.Log),
        )
        assertEquals(ConsentedTab.Log, tabBackTarget(ConsentedTab.Insights))
        assertEquals(ConsentedTab.Log, tabBackTarget(ConsentedTab.Explore))
    }
}
