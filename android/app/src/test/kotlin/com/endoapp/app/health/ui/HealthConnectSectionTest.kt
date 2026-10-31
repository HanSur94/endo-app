package com.endoapp.app.health.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.endoapp.app.content.HealthConnectCopy
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.app.health.FakeHealthConnectController
import com.endoapp.app.health.HealthAvailability
import com.endoapp.app.ui.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Health Connect connect-entry behaviors (AHC-01, threats T-21-04b) on the JVM Robolectric+Compose
 * lane, driven off [FakeHealthConnectController]:
 *
 * 1. AVAILABLE → tapping the entry routes onward (fires `onConnect`), no inline error surface.
 * 2. PROVIDER_UPDATE_REQUIRED → the honest install line + the Play deep-link button (only here).
 * 3. UNAVAILABLE → the softened "not available … manual logging works the same" line, no action.
 * 4. The provenance badge renders its registered copy.
 * 5. The section is omitted when no controller is threaded (inert) and never gates manual logging.
 *
 * Every asserted string is a [HealthConnectCopy] constant (CopyAudit-clean, registered at birth).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun available_tapConnect_routesOnward_noErrorSurface() {
        val fake = FakeHealthConnectController(availability = HealthAvailability.AVAILABLE)
        var connected = 0
        composeRule.setContent {
            BloomTheme { HealthConnectSection(controller = fake, onConnect = { connected++ }) }
        }

        composeRule.onNodeWithText(HealthConnectCopy.ENTRY_LABEL).assertExists()
        composeRule.onNodeWithText(HealthConnectCopy.ENTRY_CAPTION).assertExists()

        composeRule.onNodeWithTag("hc.section.connect").performClick()
        composeRule.waitForIdle()

        assertEquals("AVAILABLE routes to the import flow", 1, connected)
        composeRule.onNodeWithTag("hc.section.updateRequired").assertDoesNotExist()
        composeRule.onNodeWithTag("hc.section.unavailable").assertDoesNotExist()
    }

    @Test
    fun updateRequired_showsHonestInstallLine_andPlayButton() {
        val fake = FakeHealthConnectController(availability = HealthAvailability.PROVIDER_UPDATE_REQUIRED)
        var connected = 0
        var play = 0
        composeRule.setContent {
            BloomTheme {
                HealthConnectSection(controller = fake, onConnect = { connected++ }, onGetOnPlay = { play++ })
            }
        }

        composeRule.onNodeWithTag("hc.section.connect").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hc.section.updateRequired").assertExists()
        composeRule.onNodeWithText(HealthConnectCopy.UNAVAILABLE_NOT_INSTALLED).assertIsDisplayed()

        composeRule.onNodeWithTag("hc.section.getOnPlay").performClick()
        composeRule.waitForIdle()
        assertEquals("Play deep link fires", 1, play)
        assertEquals("update-required does NOT route to the import flow", 0, connected)
    }

    @Test
    fun unavailable_readsAsNormal_noActionAffordance() {
        val fake = FakeHealthConnectController(availability = HealthAvailability.UNAVAILABLE)
        var connected = 0
        composeRule.setContent {
            BloomTheme { HealthConnectSection(controller = fake, onConnect = { connected++ }) }
        }

        composeRule.onNodeWithTag("hc.section.connect").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hc.section.unavailable").assertExists()
        composeRule.onNodeWithText(HealthConnectCopy.UNAVAILABLE_IMPOSSIBLE).assertIsDisplayed()
        // No Play button in UNAVAILABLE; no onward routing (softened, not an error).
        composeRule.onNodeWithTag("hc.section.getOnPlay").assertDoesNotExist()
        assertEquals("UNAVAILABLE takes no action", 0, connected)
    }

    @Test
    fun provenanceBadge_rendersRegisteredCopy() {
        composeRule.setContent { BloomTheme { HealthSourceBadge() } }

        composeRule.onNodeWithTag("hc.sourceBadge").assertExists()
        composeRule.onNodeWithText(HealthConnectCopy.PROVENANCE_BADGE).assertExists()
    }

    @Test
    fun settings_omitsSection_whenNoControllerThreaded() {
        composeRule.setContent { BloomTheme { SettingsScreen(onWithdraw = {}, onBack = {}) } }

        // Inert by default — the connect section is absent, existing Settings sections untouched.
        composeRule.onNodeWithTag("settings.healthConnectSlot").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.remindersSlot").assertExists()
        composeRule.onNodeWithTag("settings.privacy").assertExists()
    }

    @Test
    fun settings_rendersSection_whenControllerThreaded() {
        composeRule.setContent {
            BloomTheme {
                SettingsScreen(onWithdraw = {}, onBack = {}, healthConnect = FakeHealthConnectController())
            }
        }

        composeRule.onNodeWithTag("settings.healthConnectSlot").performScrollTo().assertExists()
        // Manual-logging sections remain present alongside it — never gated.
        composeRule.onNodeWithTag("settings.cycleOff").assertExists()
    }
}
