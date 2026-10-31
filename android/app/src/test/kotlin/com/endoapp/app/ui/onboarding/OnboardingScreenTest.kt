package com.endoapp.app.ui.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.endoapp.app.designsystem.BloomTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Bloom onboarding + consent flow (ASHL-01/02) — proven on the JVM Robolectric+Compose lane.
 * Renders the real [OnboardingScreen] over test-local grant/decline counters and asserts the
 * informed-consent contract:
 *
 *  1. The disclaimer + privacy link are present BEFORE the consent action (informed-consent order).
 *  2. Tapping "I consent" (`consent.grantButton`) fires `onConsent` exactly once.
 *  3. Tapping "Not now" (`consent.declineButton`) fires `onDecline` and grants NOTHING (fail-closed).
 *  4. Tapping the privacy link surfaces the offline [PrivacyPolicyScreen] content.
 *  5. All four required consent/onboarding testTags exist.
 *
 * Pixel fidelity + the TalkBack focus-order sweep are the Manual-Only device checks per 19-VALIDATION.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var grantCount = 0
    private var declineCount = 0

    private fun setOnboarding() {
        composeRule.setContent {
            BloomTheme {
                OnboardingScreen(
                    onConsent = { grantCount++ },
                    onDecline = { declineCount++ },
                )
            }
        }
    }

    // --- 1. Disclaimer + privacy reachable BEFORE consent (informed-consent order) ---

    @Test
    fun disclaimerAndPrivacy_areReachableBeforeConsent() {
        setOnboarding()

        // All three coexist in the pre-consent state — the disclaimer + privacy link are reachable
        // before (alongside) the explicit consent action, and nothing has been granted yet.
        composeRule.onNodeWithTag("onboarding.disclaimer").assertExists()
        composeRule.onNodeWithTag("onboarding.privacyLink").assertExists()
        composeRule.onNodeWithTag("consent.grantButton").assertExists()
        composeRule.onNodeWithTag("consent.declineButton").assertExists()
        assertEquals("Nothing is granted before an explicit action.", 0, grantCount)
    }

    // --- 2. Grant fires onConsent exactly once ---

    @Test
    fun grant_firesOnConsentExactlyOnce() {
        setOnboarding()

        composeRule.onNodeWithTag("consent.grantButton").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("Granting consent invokes onConsent exactly once.", 1, grantCount)
        assertEquals("Granting must not trigger the decline path.", 0, declineCount)
    }

    // --- 3. Decline fires onDecline and grants nothing (fail-closed) ---

    @Test
    fun decline_firesOnDecline_andGrantsNothing() {
        setOnboarding()

        composeRule.onNodeWithTag("consent.declineButton").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("Declining invokes onDecline.", 1, declineCount)
        assertEquals("Declining stores/grants nothing (fail-closed).", 0, grantCount)
    }

    // --- 4. Privacy link opens the offline policy ---

    @Test
    fun privacyLink_opensTheOfflinePolicy() {
        setOnboarding()

        // The policy is not shown until the link is tapped.
        composeRule.onNodeWithTag("privacyPolicy.body").assertDoesNotExist()

        composeRule.onNodeWithTag("onboarding.privacyLink").performClick()
        composeRule.waitForIdle()

        // Tapping the link surfaces the offline PrivacyPolicyScreen content — before any consent.
        composeRule.onNodeWithTag("privacyPolicy.body").assertExists()
        assertEquals("Opening the policy grants nothing.", 0, grantCount)
    }

    // --- 5. Required testTags exist ---

    @Test
    fun requiredTestTags_exist() {
        setOnboarding()

        listOf(
            "onboarding.disclaimer",
            "onboarding.privacyLink",
            "consent.grantButton",
            "consent.declineButton",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }
    }
}
