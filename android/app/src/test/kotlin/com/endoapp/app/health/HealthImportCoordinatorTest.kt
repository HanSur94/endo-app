package com.endoapp.app.health

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.endoapp.app.data.ConsentState
import com.endoapp.app.data.ConsentStore
import com.endoapp.app.data.EndoDatabase
import com.endoapp.app.data.EndoRepository
import com.endoapp.app.data.entities.FactorReading
import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.insightsbridge.HealthDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The Plan 03 data-integrity core, proven RUNNABLY on the JVM lane (Robolectric + a REAL in-memory
 * Room [EndoRepository] + a REAL Preferences [ChangesTokenStore], driven by a scripted
 * [FakeHealthReader] — no emulator, no Health Connect provider). Pins threat T-21-02 / -02d / -02e:
 *
 * 1. **idempotency** — the same seed read scripted twice leaves exactly one row per hcRecordId and the
 *    ImportedSignals sleep sum does NOT double (deterministic id + REPLACE).
 * 2. **deletion** — a `DeletionChange` on re-sync removes its ImportedSample by hcRecordId.
 * 3. **nil-honest** — a denied/empty read persists nothing and leaves a pre-existing manual row
 *    untouched (absence ≠ 0); a manual entry beats an imported one through the Phase-18 merge.
 * 4. **provenance** — a persisted row keeps its Health-Connect identity (hcRecordId + domainRaw).
 * 5. **token-expiry** — a scripted expiry clears the domain cursor and completes without crashing; a
 *    subsequent seed re-imports.
 *
 * The window label (AHC-04) and the pre-consent refusal (T-21-02d, belt + suspenders) are also pinned.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthImportCoordinatorTest {

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

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EndoDatabase::class.java,
        ).build()
        repo = EndoRepository.from(FixedConsent(ConsentState.Consented(0L)), db)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dataStore = PreferenceDataStoreFactory.create(scope = dsScope) {
            context.preferencesDataStoreFile("health_tokens_test_${System.nanoTime()}")
        }
        tokenStore = ChangesTokenStore(dataStore)
    }

    @After
    fun tearDown() {
        db.close()
        dsScope.cancel()
    }

    private fun coordinator(
        reader: HealthReader,
        consent: ConsentState = ConsentState.Consented(0L),
    ) = HealthImportCoordinator(
        reader = reader,
        repository = EndoRepository.from(FixedConsent(consent), db),
        changesTokenStore = tokenStore,
        consent = FixedConsent(consent),
    )

    private fun sleepRow(hcId: String, hours: Double) = ImportedSample(
        id = HealthRecordMapping.stableId(hcId), hcRecordId = hcId, epochDay = DAY,
        domainRaw = HealthDomain.SLEEP.raw, sleepHours = hours, importedAtMillis = 1L,
    )

    private fun cycleRow(hcId: String) = ImportedSample(
        id = HealthRecordMapping.stableId(hcId), hcRecordId = hcId, epochDay = DAY,
        domainRaw = HealthDomain.CYCLE.raw, flowRaw = "light", importedAtMillis = 1L,
    )

    private suspend fun sleepHoursFor(day: Long): Double? =
        repo.daySnapshots().firstOrNull { it.day == LocalDate.ofEpochDay(day) }?.sleepHours

    @Test
    fun idempotentReimport_oneRowPerHcRecordId_sleepSumDoesNotDouble() = runBlocking {
        val rows = listOf(sleepRow("hc-s1", 7.0), sleepRow("hc-s2", 1.0)) // nightly Σ = 8.0
        val reader = FakeHealthReader(
            scripts = mapOf(
                HealthDomain.SLEEP to listOf(
                    ReadResult(rows, emptyList(), "t1"),
                    ReadResult(rows, emptyList(), "t2"),
                ),
            ),
        )
        val coordinator = coordinator(reader)

        coordinator.importAll()
        coordinator.importAll() // re-import the SAME records

        assertEquals("exactly one row per hcRecordId", 2, repo.importedForDay(DAY).size)
        assertEquals("sleep Σ does not double on re-import", 8.0, sleepHoursFor(DAY)!!, 0.0001)
    }

    @Test
    fun deletionChange_propagates_removesTheImportedRow() = runBlocking {
        val reader = FakeHealthReader(
            scripts = mapOf(
                HealthDomain.SLEEP to listOf(
                    ReadResult(listOf(sleepRow("hc-a", 6.0)), emptyList(), "t1"),
                    ReadResult(emptyList(), listOf("hc-a"), "t2"), // HC deletion
                ),
            ),
        )
        val coordinator = coordinator(reader)

        coordinator.importAll()
        assertEquals("row present after first import", 1, repo.importedForDay(DAY).size)

        coordinator.importAll() // carries the DeletionChange
        assertTrue("HC deletion propagated to row removal", repo.importedForDay(DAY).isEmpty())
    }

    @Test
    fun deniedOrEmptyRead_persistsNothing_leavesManualUntouched() = runBlocking {
        // Pre-existing MANUAL sleep entry.
        repo.upsertFactor(FactorReading(id = "manual-sleep", epochDay = DAY, kindRaw = "sleep", amount = 6.5))

        val reader = FakeHealthReader() // no script → every read EMPTY (denied/granted-but-empty)
        coordinator(reader).importAll()

        assertTrue("nothing imported on an empty/denied read", repo.importedForDay(DAY).isEmpty())
        assertEquals("manual sleep untouched (no fabricated 0)", 6.5, sleepHoursFor(DAY)!!, 0.0001)
    }

    @Test
    fun manualWins_manualBeatsImported_throughPhase18Merge() = runBlocking {
        repo.upsertFactor(FactorReading(id = "manual-sleep", epochDay = DAY, kindRaw = "sleep", amount = 6.5))

        val reader = FakeHealthReader(
            scripts = mapOf(
                HealthDomain.SLEEP to listOf(ReadResult(listOf(sleepRow("hc-x", 9.0)), emptyList(), "t1")),
            ),
        )
        coordinator(reader).importAll()

        // The imported row DOES persist (provenance retained) …
        assertEquals(1, repo.importedForDay(DAY).size)
        // … but the merged snapshot shows the MANUAL value (manual-wins, per-field, nil-honest).
        assertEquals("manual entry wins over imported", 6.5, sleepHoursFor(DAY)!!, 0.0001)
    }

    @Test
    fun provenance_importedRowKeepsHealthConnectIdentity() = runBlocking {
        val reader = FakeHealthReader(
            scripts = mapOf(
                HealthDomain.CYCLE to listOf(ReadResult(listOf(cycleRow("hc-cyc")), emptyList(), "t1")),
            ),
        )
        coordinator(reader).importAll()

        val row = repo.importedForDay(DAY).single()
        assertEquals("provenance: the HC record id is retained", "hc-cyc", row.hcRecordId)
        assertEquals("provenance: the import domain is retained", HealthDomain.CYCLE.raw, row.domainRaw)
    }

    @Test
    fun tokenExpiry_clearsCursor_completesWithoutCrash_thenReseeds() = runBlocking {
        // Seed a stale cursor so the coordinator reads the differential path first.
        tokenStore.setToken(HealthDomain.SLEEP, "stale-cursor")

        val reader = FakeHealthReader(
            scripts = mapOf(
                HealthDomain.SLEEP to listOf(
                    ReadResult(emptyList(), emptyList(), null, tokenExpired = true), // expiry
                    ReadResult(listOf(sleepRow("hc-fresh", 7.5)), emptyList(), "fresh"), // re-seed
                ),
            ),
        )
        val coordinator = coordinator(reader)

        coordinator.importAll() // must NOT crash on expiry
        assertNull("expired cursor cleared for re-seed", tokenStore.token(HealthDomain.SLEEP))
        assertTrue("nothing persisted on the expiry pass", repo.importedForDay(DAY).isEmpty())

        coordinator.importAll() // seed read (token now null) re-imports
        assertEquals("re-seed imported the fresh row", 1, repo.importedForDay(DAY).size)
        assertEquals("fresh cursor persisted after a successful persist", "fresh", tokenStore.token(HealthDomain.SLEEP))
    }

    @Test
    fun tokenAdvancesOnlyAfterSuccessfulPersist() = runBlocking {
        val reader = FakeHealthReader(
            scripts = mapOf(
                HealthDomain.SLEEP to listOf(ReadResult(listOf(sleepRow("hc-1", 8.0)), emptyList(), "next-token")),
            ),
        )
        assertNull("unseeded before import", tokenStore.token(HealthDomain.SLEEP))

        coordinator(reader).importAll()

        assertEquals("cursor advanced to the read's nextToken after persist", "next-token", tokenStore.token(HealthDomain.SLEEP))
        // The coordinator read using the persisted (null) cursor on the seed.
        assertTrue("seed read used a null cursor", reader.calls.any { it == HealthDomain.SLEEP to null })
    }

    @Test
    fun windowLabel_reflectsGrantedHistory_ahc04() = runBlocking {
        val withHistory = FakeHealthReader(granted = HealthConnectPermissions.REQUESTED)
        val outcomeFull = coordinator(withHistory).importAll()
        assertTrue("history granted → full-history label", outcomeFull.fullHistoryGranted)
        assertTrue(outcomeFull.grantedPermissions.contains(HealthConnectPermissions.READ_SLEEP))

        val noHistory = FakeHealthReader(granted = HealthConnectPermissions.READ)
        val outcomeCapped = coordinator(noHistory).importAll()
        assertTrue("history absent → 30-day label", !outcomeCapped.fullHistoryGranted)
    }

    @Test
    fun refusesToRunPreConsent_beltAndSuspenders() {
        val reader = FakeHealthReader(
            scripts = mapOf(
                HealthDomain.SLEEP to listOf(ReadResult(listOf(sleepRow("hc-1", 8.0)), emptyList(), "t1")),
            ),
        )
        val coordinator = coordinator(reader, consent = ConsentState.NotConsented)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.importAll() }
        }
        assertTrue("no read happens before consent (fail closed)", reader.calls.isEmpty())
    }
}
