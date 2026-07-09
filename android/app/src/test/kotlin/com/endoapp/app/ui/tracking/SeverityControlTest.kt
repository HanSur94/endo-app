package com.endoapp.app.ui.tracking

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.endoapp.app.designsystem.BloomTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared [SeverityControl] behavior (SYMP-01) on the JVM Robolectric+Compose lane: the "N/10"
 * readout tracks the value, the slider node carries its testTag, and a preset chip drives
 * [onValueChange] to the preset value (Severe = 9).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeverityControlTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readout_showsNOver10_forCurrentValue() {
        composeRule.setContent {
            BloomTheme { SeverityControl(value = 5, onValueChange = {}) }
        }
        composeRule.onNodeWithText("5/10").assertIsDisplayed()
        composeRule.onNodeWithTag("daylog.severitySlider").assertExists()
    }

    @Test
    fun tappingSeverePreset_setsValueTo9() {
        var value = 0
        composeRule.setContent {
            var v by mutableIntStateOf(0)
            BloomTheme {
                SeverityControl(
                    value = v,
                    onValueChange = { v = it; value = it },
                )
            }
        }

        composeRule.onNodeWithTag("daylog.severityPreset.severe").performClick()
        composeRule.waitForIdle()

        assertEquals("the Severe preset selects severity 9", 9, value)
        composeRule.onNodeWithText("9/10").assertIsDisplayed()
    }

    @Test
    fun tappingMildPreset_setsValueTo3() {
        var value = 0
        composeRule.setContent {
            var v by mutableIntStateOf(10)
            BloomTheme { SeverityControl(value = v, onValueChange = { v = it; value = it }) }
        }

        composeRule.onNodeWithTag("daylog.severityPreset.mild").performClick()
        composeRule.waitForIdle()

        assertEquals(3, value)
    }
}
