package com.endoapp.app.health.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.endoapp.app.content.HealthConnectCopy
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.app.health.FakeHealthConnectController
import com.endoapp.app.health.HealthImportState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Health Connect import-screen behaviors (AHC-03 / AHC-04, threats T-21-04a / T-21-04b) on the JVM
 * Robolectric+Compose lane, driven off [FakeHealthConnectController]:
 *
 * 1. The HONEST window label reflects the granted-history flag (full history vs last 30 days, AHC-04).
 * 2. The progress → done / empty states render their registered [HealthConnectCopy].
 * 3. Done-with-data carries the provenance badge.
 * 4. The denial state renders the gentle banner + re-open hint and fires NO re-request (no nag).
 * 5. The refresh action triggers a re-import on the controller.
 * 6. The rationale "Continue" fires the permission request, then the import.
 *
 * Every asserted string is a [HealthConnectCopy] constant (CopyAudit-clean, registered at birth).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthImportScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun windowLabel_historyGranted_readsFullHistory() {
        val fake = FakeHealthConnectController(history = true, initial = HealthImportState.InProgress)
        composeRule.setContent { BloomTheme { HealthImportScreen(controller = fake) } }

        composeRule.onNodeWithTag("hc.import.progress").assertExists()
        composeRule.onNodeWithText(HealthConnectCopy.IMPORT_IN_PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithText(HealthConnectCopy.WINDOW_LABEL_HISTORY).assertIsDisplayed()
    }

    @Test
    fun windowLabel_historyAbsent_readsLast30Days() {
        val fake = FakeHealthConnectController(history = false, initial = HealthImportState.InProgress)
        composeRule.setContent { BloomTheme { HealthImportScreen(controller = fake) } }

        composeRule.onNodeWithText(HealthConnectCopy.WINDOW_LABEL_30_DAY).assertIsDisplayed()
        composeRule.onNodeWithText(HealthConnectCopy.WINDOW_LABEL_HISTORY).assertDoesNotExist()
    }

    @Test
    fun doneWithData_showsConnectedCopy_andProvenanceBadge() {
        val fake = FakeHealthConnectController(
            initial = HealthImportState.Done(hasData = true, grantedPermissions = emptySet()),
        )
        composeRule.setContent { BloomTheme { HealthImportScreen(controller = fake) } }

        composeRule.onNodeWithText(HealthConnectCopy.IMPORT_DONE).assertIsDisplayed()
        composeRule.onNodeWithTag("hc.sourceBadge").assertExists()
        composeRule.onNodeWithText(HealthConnectCopy.PROVENANCE_BADGE).assertExists()
    }

    @Test
    fun emptyResult_showsHonestEmptyCopy() {
        val fake = FakeHealthConnectController(
            initial = HealthImportState.Done(hasData = false),
        )
        composeRule.setContent { BloomTheme { HealthImportScreen(controller = fake) } }

        composeRule.onNodeWithTag("hc.import.empty").assertExists()
        composeRule.onNodeWithText(HealthConnectCopy.IMPORT_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText(HealthConnectCopy.IMPORT_DONE).assertDoesNotExist()
    }

    @Test
    fun denied_showsGentleBanner_andHint_firesNoRequest() {
        val fake = FakeHealthConnectController(initial = HealthImportState.Denied)
        var requests = 0
        composeRule.setContent {
            BloomTheme {
                HealthImportScreen(controller = fake, onRequestPermissions = { requests++ })
            }
        }

        composeRule.onNodeWithTag("hc.import.denied").assertExists()
        composeRule.onNodeWithText(HealthConnectCopy.DENIED_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText(HealthConnectCopy.DENIED_REOPEN_HINT).assertIsDisplayed()
        // Gentle: NO re-prompt loop and NO refresh affordance.
        assertEquals("denial fires no re-request", 0, requests)
        composeRule.onNodeWithTag("hc.import.refresh").assertDoesNotExist()
    }

    @Test
    fun refresh_triggersReimportOnController() {
        val fake = FakeHealthConnectController(
            initial = HealthImportState.Done(hasData = true),
        )
        composeRule.setContent { BloomTheme { HealthImportScreen(controller = fake) } }

        composeRule.onNodeWithTag("hc.import.refresh").performClick()
        composeRule.waitForIdle()
        assertEquals("refresh re-imports", 1, fake.refreshCalls)
    }

    @Test
    fun rationaleContinue_firesPermissionRequest_thenImports() {
        val fake = FakeHealthConnectController(initial = HealthImportState.Idle)
        var requests = 0
        composeRule.setContent {
            BloomTheme {
                HealthImportScreen(
                    controller = fake,
                    // Simulate the OS returning a granted set immediately.
                    onRequestPermissions = { cb -> requests++; cb(emptySet()) },
                )
            }
        }

        // Idle → the Plan-01 rationale screen; "Continue" is the only path to the system sheet.
        composeRule.onNodeWithTag("hc.rationale.continue").performClick()
        composeRule.waitForIdle()

        assertEquals("Continue fires the permission request", 1, requests)
        assertEquals("the granted result launches the import", 1, fake.importCalls)
    }
}
