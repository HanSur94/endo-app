package com.endoapp.app.ui.insights

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.endoapp.app.content.ClaimsPolicy
import com.endoapp.app.content.InsightsCopy
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.insights.InsightConfidence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the plain-language confidence pill (22-UI-SPEC §Confidence Pill / invariant H6) on the JVM
 * Robolectric+Compose lane. The load-bearing honesty rule: confidence is TEXT + a SHAPE icon — NEVER a
 * number, NEVER color alone. Proves:
 *   • each level renders the audited plain-language WORD (from [InsightsCopy.confidenceWord]),
 *   • each level renders a SHAPE icon (`…confidence.icon`) — so color is never the sole signal,
 *   • no digit / "%" / "p =" / "r =" appears in ANY confidence surface (text + shape, never a number).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfidencePillTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Each level gets its own root so all four pills coexist in ONE composition (one setContent). */
    private fun rootFor(level: InsightConfidence) = "insights.card.${level.name.lowercase()}"

    private val levels = listOf(
        InsightConfidence.HIGH,
        InsightConfidence.MEDIUM,
        InsightConfidence.LOW,
        InsightConfidence.NONE,
    )

    private fun setAllPills() {
        composeRule.setContent {
            BloomTheme {
                Column {
                    levels.forEach { level ->
                        ConfidencePill(confidence = level, testTagRoot = rootFor(level))
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun eachLevel_rendersItsPlainLanguageWord_andAShapeIcon() {
        setAllPills()

        for (level in levels) {
            val root = rootFor(level)
            // The pill exists, carries a SHAPE icon (never color alone), and shows the audited word.
            composeRule.onNodeWithTag("$root.confidence").assertExists()
            composeRule.onNodeWithTag("$root.confidence.icon").assertExists()
            // LOW and NONE share the "Low" word, so at least one node must carry it.
            composeRule.onAllNodesWithText(InsightsCopy.confidenceWord(level)).fetchSemanticsNodes().isNotEmpty()
                .let { assertTrue("the audited confidence word must render for $level", it) }
        }
    }

    @Test
    fun confidenceWords_carryNoNumberOrPrecisionToken() {
        // The rendered confidence surface is plain-language: no digit, no "%", no "p ="/"r =". These are
        // the ONLY confidence words the pill ever shows (the icon carries the level by SHAPE, not a value).
        for (level in InsightConfidence.entries) {
            val word = InsightsCopy.confidenceWord(level)
            assertFalse("Confidence word \"$word\" must contain no digit.", word.any { it.isDigit() })
            for (token in ClaimsPolicy.forbiddenPrecisionTokens) {
                assertFalse(
                    "Confidence word \"$word\" must contain no false-precision token \"$token\".",
                    word.lowercase().contains(token.lowercase()),
                )
            }
        }
    }
}
