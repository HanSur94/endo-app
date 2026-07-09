package com.endoapp.app.health

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.endoapp.app.data.ConsentState
import com.endoapp.app.data.ConsentStore
import com.endoapp.app.data.EndoDatabase
import com.endoapp.app.data.EndoRepository
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runnable JVM proof (Robolectric + real in-memory Room + a real Preferences DataStore, the
 * MedicationTest lane) for the Plan 03 dedupe/deletion primitives:
 *
 * 1. `deleteImportedByHcRecordId` removes EXACTLY the matching row (Room round-trip); an absent
 *    hcRecordId is a no-op (HC-deletion propagation, T-21-02).
 * 2. `ChangesTokenStore` persists a per-domain changes cursor OUTSIDE the health DB (its own
 *    Preferences DataStore file), reads null when unseeded, isolates domains, and clears on demand
 *    (differential-sync cursor; T-21-02e / -02f).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportedSampleDaoTest {

    private lateinit var db: EndoDatabase
    private lateinit var repo: EndoRepository
    private val dsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
    fun tearDown() {
        db.close()
        dsScope.cancel()
    }

    private fun tokenStore(): ChangesTokenStore {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(scope = dsScope) {
            context.preferencesDataStoreFile("health_tokens_test_${System.nanoTime()}")
        }
        return ChangesTokenStore(dataStore)
    }

    @Test
    fun deleteImportedByHcRecordId_removesOnlyTheMatchingRow() = runBlocking {
        val keep = ImportedSample(
            id = "id-keep", hcRecordId = "hc-keep", epochDay = 100,
            domainRaw = HealthDomain.SLEEP.raw, sleepHours = 7.0, importedAtMillis = 1L,
        )
        val drop = ImportedSample(
            id = "id-drop", hcRecordId = "hc-drop", epochDay = 100,
            domainRaw = HealthDomain.SLEEP.raw, sleepHours = 6.0, importedAtMillis = 2L,
        )
        repo.upsertImported(keep)
        repo.upsertImported(drop)
        assertEquals("both rows present before delete", 2, repo.importedForDay(100).size)

        repo.deleteImportedByHcRecordId("hc-drop")

        val remaining = repo.importedForDay(100)
        assertEquals("exactly one row survives", 1, remaining.size)
        assertEquals("the non-matching row survives", "hc-keep", remaining.single().hcRecordId)
    }

    @Test
    fun deleteImportedByHcRecordId_absentId_isNoOp() = runBlocking {
        val row = ImportedSample(
            id = "id-1", hcRecordId = "hc-1", epochDay = 100,
            domainRaw = HealthDomain.CYCLE.raw, flowRaw = "light", importedAtMillis = 1L,
        )
        repo.upsertImported(row)

        repo.deleteImportedByHcRecordId("hc-does-not-exist")

        assertEquals("no row removed for an absent hcRecordId", 1, repo.importedForDay(100).size)
    }

    @Test
    fun changesTokenStore_roundTripsPerDomain_outsideTheHealthDb() = runBlocking {
        val store = tokenStore()

        // Unseeded → null (a seed read must mint a fresh cursor).
        assertNull("unseeded token is null", store.token(HealthDomain.SLEEP))

        store.setToken(HealthDomain.SLEEP, "sleep-cursor-1")
        store.setToken(HealthDomain.WORKOUTS, "workout-cursor-1")

        assertEquals("sleep-cursor-1", store.token(HealthDomain.SLEEP))
        assertEquals("workout-cursor-1", store.token(HealthDomain.WORKOUTS))
        // Domains are isolated — advancing sleep never touched cycle.
        assertNull("cycle stays unseeded", store.token(HealthDomain.CYCLE))
    }

    @Test
    fun changesTokenStore_clearAndNull_removeTheCursor() = runBlocking {
        val store = tokenStore()
        store.setToken(HealthDomain.SLEEP, "cursor")
        assertEquals("cursor", store.token(HealthDomain.SLEEP))

        store.clear(HealthDomain.SLEEP)
        assertNull("clear removes the cursor (re-seed next sync)", store.token(HealthDomain.SLEEP))

        store.setToken(HealthDomain.SLEEP, "cursor2")
        store.setToken(HealthDomain.SLEEP, null)
        assertNull("setToken(null) also removes the cursor", store.token(HealthDomain.SLEEP))
    }
}
