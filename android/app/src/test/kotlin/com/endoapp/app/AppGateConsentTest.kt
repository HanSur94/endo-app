package com.endoapp.app

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.endoapp.app.data.ConsentState
import com.endoapp.app.data.ConsentStore
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.app.export.ExportImportViewModel
import com.endoapp.app.export.FakeDocumentIo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The consent-by-construction invariant (ASHL-01 / threat T-19-01) — proven on the JVM
 * Robolectric+Compose lane established by Plan 01. Renders the real [AppGate] over a fake
 * [ConsentStore] (a settable [StateFlow], mirroring `RepositoryConsentTest`'s fake — no DataStore
 * file) and a real [CheckInRouter], and asserts the four invariants:
 *
 *  1. Pre-consent (both `NotConsented` AND `Unknown`) the tab surface is UNCONSTRUCTIBLE — no
 *     `tab.log`/`tab.insights`/`tab.explore` node exists.
 *  2. Granting consent reveals the shell (the three tabs exist; Log is the default selection).
 *  3. Withdrawing consent re-gates (the tab nodes leave composition; the consent CTA returns).
 *  4. A deep-link/notification flag set while `NotConsented` lands on the gate (no health UI).
 *
 * RED-CAPABLE: if [AppGate] ever composed the shell outside its `Consented` arm, the
 * `assertDoesNotExist()` cases would fail. Verified red-capable during authoring by temporarily
 * routing `NotConsented` to the shell (all four pre-consent assertions turned RED), then reverted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppGateConsentTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** The three consented-shell tab tags — none may exist pre-consent. */
    private val tabTags = listOf("tab.log", "tab.insights", "tab.explore")

    /** A fake consent store whose state is a settable [MutableStateFlow] — no DataStore, no DB. */
    private class FakeConsentStore(initial: ConsentState) : ConsentStore {
        private val _state = MutableStateFlow(initial)
        override val state: StateFlow<ConsentState> = _state
        override suspend fun grant() { _state.value = ConsentState.Consented(grantedAtMillis = 0L) }
        override suspend fun withdraw() { _state.value = ConsentState.NotConsented }
    }

    private fun setGate(store: ConsentStore, router: CheckInRouter = CheckInRouter()) {
        composeRule.setContent {
            BloomTheme {
                AppGate(consentStore = store, router = router)
            }
        }
    }

    /** A trivially-constructible export/import ViewModel over fakes (its seams are never invoked here —
     *  the test only asserts the Settings surface's presence/absence, not a real export). */
    private fun fakeExportImport(): ExportImportViewModel = ExportImportViewModel(
        exportSnapshot = { error("unused in this test") },
        importEnvelope = { },
        documentIo = FakeDocumentIo(),
    )

    private fun setGateWithExportImport(store: ConsentStore) {
        composeRule.setContent {
            BloomTheme {
                AppGate(consentStore = store, router = CheckInRouter(), exportImport = fakeExportImport())
            }
        }
    }

    // --- 1. Unreachable pre-consent (NotConsented) ---

    @Test
    fun notConsented_hasNoTabNodes_andShowsConsentButton() {
        setGate(FakeConsentStore(ConsentState.NotConsented))

        tabTags.forEach { composeRule.onNodeWithTag(it).assertDoesNotExist() }
        composeRule.onNodeWithTag("consent.grantButton").assertExists()
    }

    // --- 1b. Unreachable pre-consent (Unknown) — fail closed ---

    @Test
    fun unknown_failsClosed_showsSplash_noTabNodes_noConsentButton() {
        setGate(FakeConsentStore(ConsentState.Unknown))

        tabTags.forEach { composeRule.onNodeWithTag(it).assertDoesNotExist() }
        // Unknown shows the splash — NEVER the gated shell and never (yet) the onboarding CTA.
        composeRule.onNodeWithTag("gate.splash").assertExists()
        composeRule.onNodeWithTag("consent.grantButton").assertDoesNotExist()
    }

    // --- 2. Granting consent reveals the shell (Log default) ---

    @Test
    fun grantingConsent_revealsShell_withLogSelectedByDefault() {
        setGate(FakeConsentStore(ConsentState.NotConsented))

        composeRule.onNodeWithTag("consent.grantButton").performClick()
        composeRule.waitForIdle()

        tabTags.forEach { composeRule.onNodeWithTag(it).assertExists() }
        composeRule.onNodeWithTag("tab.log").assertIsSelected()
    }

    // --- 3. Withdrawing consent re-gates (via the graduated Settings control) ---

    @Test
    fun withdrawingConsent_viaSettings_reGatesToOnboarding() {
        setGate(FakeConsentStore(ConsentState.Consented(grantedAtMillis = 0L)))

        // Precondition: consented → the shell is present.
        tabTags.forEach { composeRule.onNodeWithTag(it).assertExists() }

        // The Phase-19 temp shell.withdraw affordance is GONE — the withdraw control now lives in
        // Settings (graduated). Drive it: Log-home gear → Settings → Withdraw consent → confirm dialog.
        composeRule.onNodeWithTag("shell.withdraw").assertDoesNotExist()
        composeRule.onNodeWithTag("log.settingsButton").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings.withdraw").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings.withdrawConfirm").performClick()
        composeRule.waitForIdle()

        tabTags.forEach { composeRule.onNodeWithTag(it).assertDoesNotExist() }
        composeRule.onNodeWithTag("consent.grantButton").assertExists()
    }

    // --- 3b. The export/import surface is consent-gated (T-23-10, consent-by-construction) ---

    @Test
    fun exportImportSeam_threaded_isUnreachablePreConsent() {
        // Even with the seam THREADED, NotConsented never passes it to the shell (bound only in the
        // Consented arm), so the "Your data" export/import entries are unconstructible pre-consent.
        setGateWithExportImport(FakeConsentStore(ConsentState.NotConsented))

        composeRule.onNodeWithTag("settings.export").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.import").assertDoesNotExist()
        composeRule.onNodeWithTag("consent.grantButton").assertExists()
    }

    @Test
    fun exportImportSeam_threaded_rendersYourDataEntriesWhenConsented() {
        setGateWithExportImport(FakeConsentStore(ConsentState.Consented(grantedAtMillis = 0L)))

        // Log-home gear → Settings; the "Your data" export + import entries render (seam threaded).
        composeRule.onNodeWithTag("log.settingsButton").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings.export").performScrollTo().assertExists()
        composeRule.onNodeWithTag("settings.import").performScrollTo().assertExists()
    }

    // --- 4. A deep-link flag set while NotConsented lands on the gate ---

    @Test
    fun deepLinkFlag_whileNotConsented_landsOnGate_noHealthUi() {
        val router = CheckInRouter().apply { pendingCheckIn = true }
        setGate(FakeConsentStore(ConsentState.NotConsented), router)

        // The flag is observed only inside the consented branch — so it surfaces no health/tab UI.
        tabTags.forEach { composeRule.onNodeWithTag(it).assertDoesNotExist() }
        composeRule.onNodeWithTag("consent.grantButton").assertExists()
    }
}
