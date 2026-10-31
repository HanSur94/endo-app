package com.endoapp.app.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proves the Bloom component contracts compose under [BloomTheme] on the JVM Robolectric+Compose
 * lane: the primary button renders and fires its callback, all five components compose without
 * error, and the disclaimer banner exposes a SINGLE merged semantics node (its text is merged into
 * the tagged banner node — TalkBack reads the whole banner at once).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BloomComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primaryButtonRendersAndInvokesCallback() {
        var clicks = 0
        composeRule.setContent {
            BloomTheme {
                BloomPrimaryButton(
                    text = "I consent",
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("primary.button"),
                )
            }
        }

        composeRule.onNodeWithTag("primary.button").assertExists()
        composeRule.onNodeWithTag("primary.button").performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun allComponentsComposeUnderTheme() {
        composeRule.setContent {
            BloomTheme {
                Column {
                    Box(Modifier.bloomCard().testTag("card"))
                    BloomChip(text = "Chip")
                    EyebrowLabel(text = "log")
                    DisclaimerBanner(
                        text = "This app offers wellness tracking, not medical advice.",
                        modifier = Modifier.testTag("banner"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("card").assertExists()
        composeRule.onNodeWithTag("banner").assertExists()
        composeRule.onNodeWithText("This app offers wellness tracking, not medical advice.")
            .assertExists()
    }

    @Test
    fun disclaimerBannerMergesSemanticsIntoOneNode() {
        val copy = "Wellness tracking only — not a medical device."
        composeRule.setContent {
            BloomTheme {
                DisclaimerBanner(text = copy, modifier = Modifier.testTag("banner"))
            }
        }

        // If the banner merged its descendants, the tagged Row node itself carries the text.
        // Were the semantics NOT merged, the text would live on a separate child node and this
        // combined matcher would find nothing — so this asserts the single-merged-node contract.
        composeRule.onNode(hasTestTag("banner") and hasText(copy)).assertExists()
    }
}
