package com.endoapp.app.health

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.endoapp.app.AppGate
import com.endoapp.app.CheckInRouter
import com.endoapp.app.data.ConsentState
import com.endoapp.app.data.ConsentStore
import com.endoapp.app.data.EndoDatabase
import com.endoapp.app.data.EndoRepository
import com.endoapp.app.designsystem.BloomTheme
import com.endoapp.app.health.ui.HealthImportScreen
import com.endoapp.app.insightsbridge.HealthDomain
import com.endoapp.app.ui.settings.SettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import java.time.LocalDate

/**
 * The Plan 05 composition-root wiring, proven RUNNABLY on the JVM lane (Robolectric + Compose + a REAL
 * in-memory Room [EndoRepository] + a REAL Preferences [ChangesTokenStore]) — no emulator, no Health
 * Connect provider. Drives the concrete [RealHealthConnectController] over a scripted [FakeHealthReader]
 * so the end-to-end path is provable without the device-gated permission dialog.
 *
 * Pins the plan's must-have truths + threat register:
 *  - **wired import persists rows (AHC-03):** the controller → the coordinator → the consent-gated repo;
 *    an import through the controller lands [com.endoapp.app.data.entities.ImportedSample] rows.
 *  - **availability-gated nullable reader (T-21-05b):** when the provider is not AVAILABLE the reader is
 *    null, the controller does NO client work, imports nothing, and never crashes.
 *  - **per-session grant / denial (T-21-03):** an empty granted set ⇒ the honest [HealthImportState.Denied].
 *  - **end-to-end flow:** rationale → request callback → import states render against the wired controller.
 *  - **consent-by-construction (T-21-05a):** the connect surface is UNREACHABLE outside the Consented arm.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: EndoDatabase
    private lateinit var repo: EndoRepository
    private lateinit var tokenStore: ChangesTokenStore
    private lateinit var dataStore: DataStore<Preferences>
    private val dsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val DAY = LocalDate.of(2026, 3, 1).toEpochDay()

    private class FixedConsent(state: ConsentState) : ConsentStore {
        override val state: StateFlow<ConsentState> = MutableStateFlow(state)
        override suspend fun grant() = Unit
        override suspend fun withdraw() = Unit
    }

    /** A settable-state fake consent store for the AppGate pre-consent guard. */
    private class SettableConsent(initial: ConsentState) : ConsentStore {
        private val _state = MutableStateFlow(initial)
        override val state: StateFlow<ConsentState> = _state
        override suspend fun grant() { _state.value = ConsentState.Consented(0L) }
        override suspend fun withdraw() { _state.value = ConsentState.NotConsented }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EndoDatabase::class.java,
        ).build()
        repo = EndoRepository.from(FixedConsent(ConsentState.Consented(0L)), db)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dataStore = PreferenceDataStoreFactory.create(scope = dsScope) {
            context.preferencesDataStoreFile("hc_flow_test_${System.nanoTime()}")
        }
        tokenStore = ChangesTokenStore(dataStore)
    }

    @After
    fun tearDown() {
        db.close()
        dsScope.cancel()
    }

    private fun sleepRow(hcId: String, hours: Double) = com.endoapp.app.data.entities.ImportedSample(
        id = HealthRecordMapping.stableId(hcId), hcRecordId = hcId, epochDay = DAY,
        domainRaw = HealthDomain.SLEEP.raw, sleepHours = hours, importedAtMillis = 1L,
    )

    /**
     * Build the REAL controller over a real coordinator (in-memory repo + token store), gated on
     * [available], reading through [reader]. Uses [Dispatchers.Unconfined] so state transitions are
     * deterministic under the test clock.
     */
    private fun controller(
        reader: HealthReader,
        available: HealthAvailability = HealthAvailability.AVAILABLE,
    ) = RealHealthConnectController(
        availabilityProvider = { available },
        readerProvider = { if (available == HealthAvailability.AVAILABLE) reader else null },
        coordinatorFor = { r ->
            HealthImportCoordinator(r, repo, tokenStore, FixedConsent(ConsentState.Consented(0L)))
        },
        hasImportedData = { repo.hasImportedData() },
        dispatcher = Dispatchers.Unconfined,
    )

    private fun seededReader(granted: Set<String> = HealthConnectPermissions.REQUESTED) = FakeHealthReader(
        scripts = mapOf(HealthDomain.SLEEP to listOf(ReadResult(listOf(sleepRow("hc-1", 7.5)), emptyList(), "t1"))),
        granted = granted,
    )

    // --- 1. The wired controller persists rows through the coordinator (AHC-03) ---

    @Test
    fun controllerImport_whenAvailable_persistsImportedRowsThroughTheCoordinator() = runBlocking {
        val controller = controller(seededReader())

        controller.importNow()

        assertEquals("the import through the controller persisted the row", 1, repo.importedForDay(DAY).size)
        val state = controller.importState.value
        assertTrue("terminal Done state", state is HealthImportState.Done)
        assertTrue("Done reports data present", (state as HealthImportState.Done).hasData)
        assertTrue("granted permissions surfaced for the window label", state.grantedPermissions.contains(HealthConnectPermissions.READ_SLEEP))
    }

    // --- 2. Availability-gated nullable reader: unavailable ⇒ no client work (T-21-05b) ---

    @Test
    fun controllerImport_whenUnavailable_doesNoClientWork_persistsNothing_noCrash() = runBlocking {
        val reader = seededReader()
        val controller = controller(reader, available = HealthAvailability.UNAVAILABLE)

        controller.importNow() // must not crash, must touch no client

        assertTrue("nothing imported on an unavailable device", repo.importedForDay(DAY).isEmpty())
        assertTrue("the reader was never touched (no getOrCreate, no read)", reader.calls.isEmpty())
        val state = controller.importState.value
        assertTrue("honest empty terminal", state is HealthImportState.Done && !state.hasData)
        assertTrue("no history claimed when unavailable", !controller.grantedHistory())
    }

    // --- 3. Per-session grant read: an empty grant ⇒ the honest denial (T-21-03) ---

    @Test
    fun controllerImport_withEmptyGrant_rendersDenied_persistsNothing() = runBlocking {
        val reader = seededReader(granted = emptySet()) // the user declined at the system sheet
        val controller = controller(reader)

        controller.importNow()

        assertEquals("nothing imported on a full denial", 0, repo.importedForDay(DAY).size)
        assertTrue("denied surface", controller.importState.value is HealthImportState.Denied)
    }

    // --- 4. End-to-end UI flow against the wired controller: rationale → request → import states ---

    @Test
    fun endToEnd_rationaleThenRequestCallback_rendersImportStates_againstWiredController() {
        val controller = controller(seededReader())

        composeRule.setContent {
            BloomTheme {
                HealthImportScreen(
                    controller = controller,
                    // Stand in for the OS permission sheet: immediately return a granted set so the
                    // screen runs the foreground import through the wired controller (the real launcher
                    // is device-gated). This exercises rationale → request-callback → import states.
                    onRequestPermissions = { onResult -> onResult(HealthConnectPermissions.REQUESTED) },
                )
            }
        }

        // Idle → the rationale-before-permission screen.
        composeRule.onNodeWithTag("hc.rationale.continue").assertIsDisplayed()

        // Continue → request callback fires → foreground import runs → the Done state renders.
        composeRule.onNodeWithTag("hc.rationale.continue").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("hc.import.done").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("hc.import.done").assertIsDisplayed()
        composeRule.onNodeWithTag("hc.import.windowLabel").assertIsDisplayed()

        // The import genuinely persisted through the wired controller → coordinator → repo.
        assertEquals(1, runBlocking { repo.importedForDay(DAY).size })
    }

    // --- 5. The Settings connect entry routes onward when AVAILABLE ---

    @Test
    fun settingsConnectEntry_available_routesToImport() {
        val controller = controller(seededReader())
        var connectedOpened = false

        composeRule.setContent {
            BloomTheme {
                SettingsScreen(
                    onWithdraw = {},
                    onBack = {},
                    healthConnect = controller,
                    onConnectHealth = { connectedOpened = true },
                )
            }
        }

        composeRule.onNodeWithTag("hc.section.connect").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { connectedOpened }
        assertTrue("AVAILABLE tap routes to the import screen", connectedOpened)
    }

    // --- 6. Consent-by-construction: the connect surface is unreachable pre-consent (T-21-05a) ---

    @Test
    fun connectSurface_isUnreachable_preConsent_andReachable_whenConsented() {
        val controller = controller(seededReader())
        val consent = SettableConsent(ConsentState.NotConsented)

        composeRule.setContent {
            BloomTheme {
                AppGate(
                    consentStore = consent,
                    router = CheckInRouter(),
                    healthConnect = controller,
                )
            }
        }

        // Pre-consent: no shell, so the connect entry cannot be constructed at all.
        composeRule.onNodeWithTag("settings.healthConnectSlot").assertDoesNotExist()
        composeRule.onNodeWithTag("hc.section.connect").assertDoesNotExist()

        // Grant consent → navigate Log-home gear → Settings → the connect entry now exists.
        composeRule.onNodeWithTag("consent.grantButton").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("log.settingsButton").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("hc.section.connect").performScrollTo().assertIsDisplayed()
    }
}
