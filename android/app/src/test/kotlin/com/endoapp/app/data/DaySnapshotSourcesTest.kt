package com.endoapp.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.endoapp.app.data.entities.BleedingEvent
import com.endoapp.app.data.entities.FactorReading
import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.insightsbridge.HealthDomain
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
 * Runnable JVM proof (Robolectric + real in-memory Room) that per-day provenance is STORE-DERIVED
 * (from row presence) and MANUAL-WINS — pinning T-24-05 (tampering of meaning). Mirrors the iOS
 * `daySnapshotSources(on:)` semantics: manual → MANUAL; else imported present → HEALTH_CONNECT; else
 * `null` (no fabricated badge). Consent-gating is pinned separately in [RepositoryConsentTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DaySnapshotSourcesTest {

    private lateinit var db: EndoDatabase
    private lateinit var repo: EndoRepository
    private val day = 100L

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

    private fun factor(kind: String) =
        FactorReading(id = "f-$kind-$day", epochDay = day, kindRaw = kind)

    private fun importedSleep() = ImportedSample(
        id = "imp-sleep", epochDay = day, domainRaw = HealthDomain.SLEEP.raw,
        sleepHours = 7.0, importedAtMillis = 1L,
    )

    private fun importedWorkout(type: String?, minutes: Double?) = ImportedSample(
        id = "imp-workout", epochDay = day, domainRaw = HealthDomain.WORKOUTS.raw,
        activityTypeRaw = type, activityMinutes = minutes, importedAtMillis = 1L,
    )

    @Test
    fun manualSleepFactorOnly_yieldsManualSleep_othersNull() = runBlocking {
        repo.upsertFactor(factor("sleep"))

        val src = repo.daySnapshotSources(day)

        assertEquals(ValueSource.MANUAL, src.sleep)
        assertNull(src.flow)
        assertNull(src.activity)
        assertNull(src.food)
    }

    @Test
    fun importedSleepOnly_yieldsHealthConnectSleep() = runBlocking {
        repo.upsertImported(importedSleep())

        assertEquals(ValueSource.HEALTH_CONNECT, repo.daySnapshotSources(day).sleep)
    }

    @Test
    fun manualAndImportedSleep_manualWins() = runBlocking {
        repo.upsertFactor(factor("sleep"))
        repo.upsertImported(importedSleep())

        assertEquals(ValueSource.MANUAL, repo.daySnapshotSources(day).sleep)
    }

    @Test
    fun importedActivityMinutesOnly_noType_yieldsNullActivity() = runBlocking {
        // Minutes present but no imported TYPE and no manual activity → the badge must NOT be claimed
        // (minutes alone never win — WR-01).
        repo.upsertImported(importedWorkout(type = null, minutes = 30.0))

        assertNull(repo.daySnapshotSources(day).activity)
    }

    @Test
    fun importedActivityWithType_yieldsHealthConnectActivity() = runBlocking {
        repo.upsertImported(importedWorkout(type = "walking", minutes = 30.0))

        assertEquals(ValueSource.HEALTH_CONNECT, repo.daySnapshotSources(day).activity)
    }

    @Test
    fun manualActivityWins_overImportedTypedWorkout() = runBlocking {
        repo.upsertFactor(factor("activity"))
        repo.upsertImported(importedWorkout(type = "walking", minutes = 30.0))

        assertEquals(ValueSource.MANUAL, repo.daySnapshotSources(day).activity)
    }

    @Test
    fun manualBleedingFlow_yieldsManualFlow() = runBlocking {
        repo.upsertBleeding(BleedingEvent(id = "b1", epochDay = day, flowRaw = "light"))

        assertEquals(ValueSource.MANUAL, repo.daySnapshotSources(day).flow)
    }

    @Test
    fun importedNutrition_yieldsHealthConnectFood() = runBlocking {
        repo.upsertImported(
            ImportedSample(
                id = "imp-food", epochDay = day, domainRaw = HealthDomain.NUTRITION.raw,
                ateThatDay = true, importedAtMillis = 1L,
            ),
        )

        assertEquals(ValueSource.HEALTH_CONNECT, repo.daySnapshotSources(day).food)
    }

    @Test
    fun emptyDay_yieldsAllNull() = runBlocking {
        assertEquals(DaySnapshotSource(), repo.daySnapshotSources(day))
    }

    @Test
    fun batchRead_keysByEpochDay() = runBlocking {
        repo.upsertFactor(factor("sleep"))

        val map = repo.daySnapshotSources(listOf(day, 200L))

        assertEquals(ValueSource.MANUAL, map[day]?.sleep)
        assertEquals(DaySnapshotSource(), map[200L])
    }
}
