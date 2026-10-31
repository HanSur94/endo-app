package com.endoapp.app.export.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.endoapp.app.content.ExportImportCopy
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.app.export.ExportImportUiState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The export/import screen behaviors (APRT-01/02, threats T-23-05 / T-23-10b) on the JVM
 * Robolectric+Compose lane. The screen is driven ENTIRELY off a hand-set [MutableStateFlow] +
 * captured pick seams, so the real SAF picker never appears:
 *
 * 1. The honest cannot-recover warning is ALWAYS visible.
 * 2. The Export action is disabled on a passphrase mismatch and enabled on a match.
 * 3. A match hands the entered passphrase to the export pick seam (String -> CharArray boundary).
 * 4. A [Working] state shows the KDF spinner + the in-progress message.
 * 5. Each gentle error state renders its verbatim [ExportImportCopy] line (never a stack trace).
 * 6. A [Success] state renders the success line.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportImportScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // --- 1. The cannot-recover warning is always visible ---

    @Test
    fun cannotRecoverWarning_isAlwaysVisible() {
        composeRule.setContent {
            BloomTheme {
                ExportImportScreen(state = MutableStateFlow(ExportImportUiState.Idle))
            }
        }
        composeRule.onNodeWithTag("export.warning").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(ExportImportCopy.CANNOT_RECOVER).assertIsDisplayed()
    }

    // --- 2 + 3. Mismatch disables Export; a match enables it AND forwards the passphrase ---

    @Test
    fun exportAction_disabledOnMismatch() {
        composeRule.setContent {
            BloomTheme {
                ExportImportScreen(state = MutableStateFlow(ExportImportUiState.Idle))
            }
        }

        // Blank → disabled.
        composeRule.onNodeWithTag("export.action").assertIsNotEnabled()

        // Mismatch → still disabled.
        composeRule.onNodeWithTag("export.passphrase").performScrollTo().performTextInput("hunter2")
        composeRule.onNodeWithTag("export.passphraseConfirm").performScrollTo().performTextInput("hunterX")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("export.action").assertIsNotEnabled()
        composeRule.onNodeWithText(ExportImportCopy.PASSPHRASE_MISMATCH).assertIsDisplayed()
    }

    @Test
    fun exportAction_onMatch_isEnabled_andForwardsEnteredPassphrase() {
        var forwarded: String? = null
        composeRule.setContent {
            BloomTheme {
                ExportImportScreen(
                    state = MutableStateFlow(ExportImportUiState.Idle),
                    onPickExportDestination = { forwarded = String(it) },
                )
            }
        }

        composeRule.onNodeWithTag("export.passphrase").performScrollTo().performTextInput("correct horse")
        composeRule.onNodeWithTag("export.passphraseConfirm").performScrollTo().performTextInput("correct horse")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("export.action").assertIsEnabled()
        composeRule.onNodeWithTag("export.action").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("the entered passphrase reaches the export seam", "correct horse", forwarded)
    }

    // --- 3b. Import forwards its passphrase to the open seam ---

    @Test
    fun importAction_forwardsEnteredPassphrase() {
        var forwarded: String? = null
        composeRule.setContent {
            BloomTheme {
                ExportImportScreen(
                    state = MutableStateFlow(ExportImportUiState.Idle),
                    onPickImportSource = { forwarded = String(it) },
                )
            }
        }

        composeRule.onNodeWithTag("import.action").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("import.passphrase").performScrollTo().performTextInput("open sesame")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("import.action").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("the entered passphrase reaches the import seam", "open sesame", forwarded)
    }

    // --- 4. Working → spinner + in-progress message ---

    @Test
    fun workingState_showsSpinnerAndMessage() {
        composeRule.setContent {
            BloomTheme {
                ExportImportScreen(
                    state = MutableStateFlow(
                        ExportImportUiState.Working(ExportImportCopy.EXPORT_IN_PROGRESS),
                    ),
                )
            }
        }
        composeRule.onNodeWithTag("exportImport.spinner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(ExportImportCopy.EXPORT_IN_PROGRESS).assertIsDisplayed()
    }

    // --- 5. The gentle error messages render verbatim ---

    @Test
    fun wrongPassphraseError_showsGentleMessage() {
        composeRule.setContent {
            BloomTheme {
                ExportImportScreen(
                    state = MutableStateFlow(
                        ExportImportUiState.Error(ExportImportCopy.WRONG_PASSPHRASE),
                    ),
                )
            }
        }
        composeRule.onNodeWithTag("exportImport.status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(ExportImportCopy.WRONG_PASSPHRASE).assertIsDisplayed()
    }

    @Test
    fun notAnExportError_showsGentleMessage() {
        composeRule.setContent {
            BloomTheme {
                ExportImportScreen(
                    state = MutableStateFlow(
                        ExportImportUiState.Error(ExportImportCopy.NOT_AN_EXPORT),
                    ),
                )
            }
        }
        composeRule.onNodeWithTag("exportImport.status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(ExportImportCopy.NOT_AN_EXPORT).assertIsDisplayed()
    }

    // --- 6. Success renders the success line ---

    @Test
    fun successState_showsSuccessMessage() {
        composeRule.setContent {
            BloomTheme {
                ExportImportScreen(
                    state = MutableStateFlow(
                        ExportImportUiState.Success(ExportImportCopy.EXPORT_SUCCESS),
                    ),
                )
            }
        }
        composeRule.onNodeWithTag("exportImport.status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(ExportImportCopy.EXPORT_SUCCESS).assertIsDisplayed()
    }
}
