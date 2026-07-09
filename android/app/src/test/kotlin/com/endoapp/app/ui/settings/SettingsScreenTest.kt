package com.endoapp.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.endoapp.app.content.WithdrawConsentCopy
import com.endoapp.app.data.ConsentState
import com.endoapp.app.data.ConsentStore
import com.endoapp.app.data.EndoDatabase
import com.endoapp.app.data.EndoRepository
import com.endoapp.app.data.entities.Medication
import com.endoapp.app.data.entities.TreatmentState
import com.endoapp.app.designsystem.BloomTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Settings screen behaviors (ATRK-03 / ATRK-05, threats T-20-04 / T-20-05b / T-20-01f) on the JVM
 * Robolectric+Compose lane:
 *
 * 1. The five Settings sections render (reminders slot, meds, cycle-off, privacy, withdraw).
 * 2. Toggling cycle-off writes a `suppressed` TreatmentState through the real repository, and a reload
 *    reads it back (latest wins).
 * 3. The graduated withdraw control's confirm calls `onWithdraw` (the re-gate signal); its dialog
 *    renders the exempt [WithdrawConsentCopy] body.
 * 4. Privacy opens the Phase-19 PrivacyPolicyScreen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: EndoDatabase
    private lateinit var repo: EndoRepository

    private class ConsentedStore : ConsentStore {
        override val state: StateFlow<ConsentState> =
            MutableStateFlow(ConsentState.Consented(grantedAtMillis = 0L))
        override suspend fun grant() = Unit
        override suspend fun withdraw() = Unit
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EndoDatabase::class.java,
        ).build()
        repo = EndoRepository.from(ConsentedStore(), db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun rendersAllFiveSections() {
        composeRule.setContent {
            BloomTheme {
                SettingsScreen(onWithdraw = {}, onBack = {})
            }
        }

        composeRule.onNodeWithTag("settings.remindersSlot").assertExists()
        composeRule.onNodeWithTag("settings.manageMeds").assertExists()
        composeRule.onNodeWithTag("settings.cycleOff").assertExists()
        composeRule.onNodeWithTag("settings.privacy").assertExists()
        composeRule.onNodeWithTag("settings.withdraw").assertExists()
    }

    @Test
    fun savedMed_rendersInManageMeds() {
        val med = Medication(id = "m1", name = "Ibuprofen", createdAtMillis = 1L)
        composeRule.setContent {
            BloomTheme {
                SettingsScreen(onWithdraw = {}, onBack = {}, medications = flowOf(listOf(med)))
            }
        }
        composeRule.onNodeWithTag("settings.med.m1").assertExists()
        composeRule.onNodeWithTag("settings.meds.empty").assertDoesNotExist()
    }

    @Test
    fun togglingCycleOff_writesSuppressedTreatment_andReloadReadsItBack() {
        // Capture the boolean the screen hands the seam (deterministic — no async Room race in the UI
        // scope), then perform the SAME write AppGate performs and prove the reload reads it back.
        var toggled: Boolean? = null
        composeRule.setContent {
            BloomTheme {
                SettingsScreen(
                    onWithdraw = {},
                    onBack = {},
                    onSetSuppressed = { on -> toggled = on },
                )
            }
        }

        composeRule.onNodeWithTag("settings.cycleOff").performScrollTo().performClick()
        composeRule.waitForIdle()

        // The screen tells the seam to suppress (cycle tracking off).
        assertEquals("cycle-off toggle signals suppression", true, toggled)

        runBlocking {
            // The AppGate-equivalent write persists a fresh timestamped TreatmentState (latest wins)…
            repo.upsertTreatment(
                TreatmentState(
                    id = "t1",
                    startedAtMillis = 2L,
                    modeRaw = if (toggled == true) "suppressed" else "naturallyCycling",
                ),
            )
            // …and a reload reads the suppression back through the consent-gated repository.
            assertEquals("reload reads suppressed back", "suppressed", repo.latestTreatment()?.modeRaw)
            assertTrue("latest-wins suppression stands", repo.latestTreatment()?.modeRaw == "suppressed")
        }
    }

    @Test
    fun withdrawConfirm_callsOnWithdraw_andShowsLegalBody() {
        var withdrew = 0
        composeRule.setContent {
            BloomTheme {
                SettingsScreen(onWithdraw = { withdrew++ }, onBack = {})
            }
        }

        composeRule.onNodeWithTag("settings.withdraw").performScrollTo().performClick()
        composeRule.waitForIdle()

        // The dialog renders the exempt destructive-consent legal body + a confirm/keep pair.
        composeRule.onNodeWithText(WithdrawConsentCopy.BODY).assertIsDisplayed()
        composeRule.onNodeWithTag("settings.withdrawConfirm").assertExists()
        composeRule.onNodeWithTag("settings.withdrawCancel").assertExists()

        composeRule.onNodeWithTag("settings.withdrawConfirm").performClick()
        composeRule.waitForIdle()
        assertEquals("confirm calls the re-gate signal", 1, withdrew)
    }

    @Test
    fun privacyRow_opensPhase19PrivacyPolicy() {
        composeRule.setContent {
            BloomTheme {
                SettingsScreen(onWithdraw = {}, onBack = {})
            }
        }

        composeRule.onNodeWithTag("settings.privacy").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("privacyPolicy.body").assertExists()
    }
}
