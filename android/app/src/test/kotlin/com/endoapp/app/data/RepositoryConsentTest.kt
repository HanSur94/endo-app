package com.endoapp.app.data

import com.endoapp.app.data.daos.DayEntryDao
import com.endoapp.app.data.daos.DeleteDayDao
import com.endoapp.app.data.daos.InsightsDao
import com.endoapp.app.data.daos.MedicationDao
import com.endoapp.app.data.daos.SymptomDao
import com.endoapp.app.data.entities.BleedingEvent
import com.endoapp.app.data.entities.CachedInsight
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.data.entities.DismissedInsight
import com.endoapp.app.data.entities.FactorReading
import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.data.entities.Medication
import com.endoapp.app.data.entities.PainPoint
import com.endoapp.app.data.entities.SymptomReading
import com.endoapp.app.data.entities.TreatmentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Ports the iOS `ConsentGateTests` as a fast JVM test: the consent check is state-only, so no SQLite
 * / Room / emulator is needed — a settable [StateFlow] fake consent store and relaxed fake DAOs
 * suffice.
 *
 * Pins threat T-18-02: EVERY health method on [EndoRepository] throws [IllegalStateException] when
 * consent is [ConsentState.Unknown] (async-DataStore gap) AND when [ConsentState.NotConsented] (both
 * fail closed), and none throws once [ConsentState.Consented].
 */
class RepositoryConsentTest {

    /** Every gated health operation on the repository, as an invocable lambda. */
    private val healthOps: List<Pair<String, suspend EndoRepository.() -> Unit>> = listOf(
        "upsertDayEntry" to { upsertDayEntry(DayEntry(id = "d", epochDay = 0)) },
        "upsertSymptom" to { upsertSymptom(SymptomReading(id = "s", epochDay = 0)) },
        "upsertPain" to { upsertPain(PainPoint(id = "p", epochDay = 0)) },
        "upsertBleeding" to { upsertBleeding(BleedingEvent(id = "b", epochDay = 0)) },
        "upsertFactor" to { upsertFactor(FactorReading(id = "f", epochDay = 0)) },
        "upsertImported" to { upsertImported(ImportedSample(id = "i", epochDay = 0, importedAtMillis = 0)) },
        "upsertMedication" to { upsertMedication(Medication(id = "m", createdAtMillis = 0)) },
        "upsertTreatment" to { upsertTreatment(TreatmentState(id = "t", startedAtMillis = 0)) },
        "dismissInsight" to { dismissInsight(DismissedInsight(id = "x", dismissedAtMillis = 0)) },
        "dayEntriesForDay" to { dayEntriesForDay(0) },
        "symptomsForDay" to { symptomsForDay(0) },
        "painForDay" to { painForDay(0) },
        "bleedingForDay" to { bleedingForDay(0) },
        "factorsForDay" to { factorsForDay(0) },
        "importedForDay" to { importedForDay(0) },
        "daySnapshots" to { daySnapshots() },
        "deleteDay" to { deleteDay(0) },
    )

    @Test
    fun everyHealthMethodThrows_whenConsentUnknown() {
        val repo = repository(ConsentState.Unknown)
        healthOps.forEach { (name, op) ->
            assertThrows(
                "$name must throw when consent is Unknown (fail closed)",
                IllegalStateException::class.java,
            ) { runBlocking { repo.op() } }
        }
    }

    @Test
    fun everyHealthMethodThrows_whenNotConsented() {
        val repo = repository(ConsentState.NotConsented)
        healthOps.forEach { (name, op) ->
            assertThrows(
                "$name must throw when NotConsented",
                IllegalStateException::class.java,
            ) { runBlocking { repo.op() } }
        }
    }

    @Test
    fun noHealthMethodThrows_whenConsented() {
        val repo = repository(ConsentState.Consented(grantedAtMillis = 123L))
        // If any op threw here the test would fail with the raised exception.
        healthOps.forEach { (_, op) -> runBlocking { repo.op() } }
    }

    private fun repository(state: ConsentState): EndoRepository = EndoRepository(
        consent = FakeConsentStore(state),
        dayEntryDao = FakeDayEntryDao(),
        symptomDao = FakeSymptomDao(),
        medicationDao = FakeMedicationDao(),
        insightsDao = FakeInsightsDao(),
        deleteDayDao = FakeDeleteDayDao(),
    )

    // --- Fakes (no DB) ---

    private class FakeConsentStore(state: ConsentState) : ConsentStore {
        private val _state = MutableStateFlow(state)
        override val state: StateFlow<ConsentState> = _state
        override suspend fun grant() { _state.value = ConsentState.Consented(0L) }
        override suspend fun withdraw() { _state.value = ConsentState.NotConsented }
    }

    private class FakeDayEntryDao : DayEntryDao {
        override suspend fun upsert(entry: DayEntry) = Unit
        override suspend fun forDay(epochDay: Long): List<DayEntry> = emptyList()
        override suspend fun all(): List<DayEntry> = emptyList()
        override fun observeAll(): Flow<List<DayEntry>> = flowOf(emptyList())
        override suspend fun deleteForDay(epochDay: Long) = Unit
    }

    private class FakeSymptomDao : SymptomDao {
        override suspend fun upsertSymptom(reading: SymptomReading) = Unit
        override suspend fun symptomsForDay(epochDay: Long): List<SymptomReading> = emptyList()
        override suspend fun allSymptoms(): List<SymptomReading> = emptyList()
        override suspend fun deleteSymptomsForDay(epochDay: Long) = Unit
        override suspend fun upsertPain(point: PainPoint) = Unit
        override suspend fun painForDay(epochDay: Long): List<PainPoint> = emptyList()
        override suspend fun allPain(): List<PainPoint> = emptyList()
        override suspend fun deletePainForDay(epochDay: Long) = Unit
        override suspend fun upsertBleeding(event: BleedingEvent) = Unit
        override suspend fun bleedingForDay(epochDay: Long): List<BleedingEvent> = emptyList()
        override suspend fun allBleeding(): List<BleedingEvent> = emptyList()
        override suspend fun deleteBleedingForDay(epochDay: Long) = Unit
        override suspend fun upsertFactor(reading: FactorReading) = Unit
        override suspend fun factorsForDay(epochDay: Long): List<FactorReading> = emptyList()
        override suspend fun allFactors(): List<FactorReading> = emptyList()
        override suspend fun deleteFactorsForDay(epochDay: Long) = Unit
        override suspend fun upsertImported(sample: ImportedSample) = Unit
        override suspend fun importedForDay(epochDay: Long): List<ImportedSample> = emptyList()
        override suspend fun allImported(): List<ImportedSample> = emptyList()
        override suspend fun deleteImportedForDay(epochDay: Long) = Unit
    }

    private class FakeMedicationDao : MedicationDao {
        override suspend fun upsertMedication(medication: Medication) = Unit
        override fun observeActive(): Flow<List<Medication>> = flowOf(emptyList())
        override suspend fun allMedications(): List<Medication> = emptyList()
        override suspend fun upsertTreatment(state: TreatmentState) = Unit
        override suspend fun latestTreatment(): TreatmentState? = null
        override suspend fun allTreatments(): List<TreatmentState> = emptyList()
    }

    private class FakeInsightsDao : InsightsDao {
        override suspend fun insertCached(insights: List<CachedInsight>) = Unit
        override suspend fun clearCached() = Unit
        override suspend fun allCached(): List<CachedInsight> = emptyList()
        override fun observeCached(): Flow<List<CachedInsight>> = flowOf(emptyList())
        override suspend fun insertDismissed(dismissed: DismissedInsight) = Unit
        override suspend fun allDismissed(): List<DismissedInsight> = emptyList()
        override suspend fun dismissedSignalKeys(): List<String> = emptyList()
    }

    private class FakeDeleteDayDao : DeleteDayDao {
        override suspend fun deleteDayEntries(epochDay: Long) = Unit
        override suspend fun deleteSymptoms(epochDay: Long) = Unit
        override suspend fun deletePainPoints(epochDay: Long) = Unit
        override suspend fun deleteBleeding(epochDay: Long) = Unit
        override suspend fun deleteFactors(epochDay: Long) = Unit
        override suspend fun deleteImported(epochDay: Long) = Unit
        override suspend fun clearCachedInsights() = Unit
        // deleteDay() default method is inherited — it calls the no-op deletes above.
    }
}
