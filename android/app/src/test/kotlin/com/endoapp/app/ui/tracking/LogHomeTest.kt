package com.endoapp.app.ui.tracking

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.endoapp.app.CheckInRouter
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.designsystem.BloomTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Log-home host behavior (ATRK-01) on the JVM Robolectric+Compose lane: the real Log-home shows
 * the CTA + settings gear + honest empty state; the CTA pushes CheckIn onto the in-Log stack and
 * system back pops it (no NavHost); a recent day renders its editable row. Uses
 * `createAndroidComposeRule<ComponentActivity>()` to drive a committed system back through the real
 * `OnBackPressedDispatcher` (mirrors `ConsentedShellTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogHomeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setHost(
        days: List<DayEntry> = emptyList(),
        router: CheckInRouter = CheckInRouter(),
    ) {
        composeRule.setContent {
            BloomTheme {
                LogTabHost(
                    recentDays = MutableStateFlow(days),
                    router = router,
                    onWithdraw = {},
                )
            }
        }
    }

    @Test
    fun default_showsCta_settingsGear_andEmptyState() {
        setHost()

        composeRule.onNodeWithTag("log.checkInCta").assertExists()
        composeRule.onNodeWithTag("log.settingsButton").assertExists()
        composeRule.onNodeWithTag("log.emptyState").assertExists()
    }

    @Test
    fun tappingCta_pushesCheckIn_andBackPopsToHome() {
        setHost()

        composeRule.onNodeWithTag("log.checkInCta").performClick()
        composeRule.waitForIdle()

        // The CheckIn route is shown; the home CTA is gone.
        composeRule.onNodeWithTag("log.route.checkIn").assertExists()
        composeRule.onNodeWithTag("log.checkInCta").assertDoesNotExist()

        // System back pops the in-Log stack back to home.
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("log.checkInCta").assertExists()
        composeRule.onNodeWithTag("log.route.checkIn").assertDoesNotExist()
    }

    @Test
    fun recentDay_rendersEditableRow_andNoEmptyState() {
        // epochDay 20000 = 2024-10-04.
        setHost(days = listOf(DayEntry(id = "d", epochDay = 20_000L)))

        composeRule.onNodeWithTag("log.editDay.2024-10-04").assertExists()
        composeRule.onNodeWithTag("log.emptyState").assertDoesNotExist()
    }

    @Test
    fun settingsGear_pushesSettings() {
        setHost()

        composeRule.onNodeWithTag("log.settingsButton").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("log.route.settings").assertExists()
    }
}
