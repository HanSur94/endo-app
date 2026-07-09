package com.endoapp.app.export

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.endoapp.app.data.ConsentState
import com.endoapp.app.data.ConsentStore
import com.endoapp.app.data.EndoDatabase
import com.endoapp.app.data.EndoRepository
import com.endoapp.app.data.entities.BleedingEvent
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.data.entities.DismissedInsight
import com.endoapp.app.data.entities.FactorReading
import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.data.entities.Medication
import com.endoapp.app.data.entities.PainPoint
import com.endoapp.app.data.entities.SymptomReading
import com.endoapp.app.data.entities.TreatmentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Full-fidelity round-trip proof against REAL in-memory Room (Robolectric, the ImportedSampleDaoTest
 * lane): seed rows across ALL 9 exported entity types into a SOURCE db, `exportSnapshot()`, encode +
 * decode through the 23-01 [ExportCodec] (the real byte pipeline), then `importEnvelope()` into a
 * FRESH db and assert the reloaded rows equal the originals — per table (APRT-01/02).
 *
 * Because the export SOURCE and import TARGET are two independent databases and the envelope crosses
 * the encrypted byte boundary between them, this exercises the entire data path end to end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoundTripTest {

    private lateinit var source: EndoDatabase
    private lateinit var target: EndoDatabase

    private class SettableConsentStore(state: ConsentState) : ConsentStore {
        private val _state = MutableStateFlow(state)
        override val state: StateFlow<ConsentState> = _state
        override suspend fun grant() { _state.value = ConsentState.Consented(0L) }
        override suspend fun withdraw() { _state.value = ConsentState.NotConsented }
    }

    private fun repo(db: EndoDatabase): EndoRepository =
        EndoRepository.from(SettableConsentStore(ConsentState.Consented(0L)), db)

    @Before
    fun setUp() {
        source = fresh()
        target = fresh()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private fun fresh(): EndoDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        EndoDatabase::class.java,
    ).build()

    // --- The seed set: one populated + one null-health row per type ---

    private val dayEntries = listOf(
        DayEntry(id = "de-1", epochDay = 20_278, note = "cramps in the morning"),
        DayEntry(id = "de-2", epochDay = 20_279, note = null),
    )
    private val symptoms = listOf(
        SymptomReading(id = "s-1", epochDay = 20_278, typeRaw = "pelvicPain", severity = 4, note = "n"),
        SymptomReading(id = "s-2", epochDay = 20_279, typeRaw = null, severity = null, note = null),
    )
    private val pain = listOf(
        PainPoint(id = "p-1", epochDay = 20_278, regionRaw = "lowerAbdomen", severity = 3),
        PainPoint(id = "p-2", epochDay = 20_279, regionRaw = null, severity = null),
    )
    private val bleeding = listOf(
        BleedingEvent(id = "b-1", epochDay = 20_278, flowRaw = "heavy", note = "n", clots = true),
        BleedingEvent(id = "b-2", epochDay = 20_279, flowRaw = null, note = null, clots = null),
    )
    private val factors = listOf(
        FactorReading(id = "f-1", epochDay = 20_278, kindRaw = "activity", valueRaw = "walk", amount = 30.0, level = 2, note = "n"),
        FactorReading(id = "f-2", epochDay = 20_279, kindRaw = null, valueRaw = null, amount = null, level = null, note = null),
    )
    private val meds = listOf(
        Medication(id = "m-1", name = "Ibuprofen", note = "400mg", createdAtMillis = 1_752_064_496_000, isArchived = false),
        Medication(id = "m-2", name = null, note = null, createdAtMillis = 1_752_064_497_000, isArchived = null),
    )
    private val treatments = listOf(
        TreatmentState(id = "t-1", startedAtMillis = 1_752_064_496_000, modeRaw = "suppressed"),
        TreatmentState(id = "t-2", startedAtMillis = 1_752_064_497_000, modeRaw = null),
    )
    private val imported = listOf(
        ImportedSample(
            id = "i-1", hcRecordId = "hc-1", epochDay = 20_278, domainRaw = "sleep", flowRaw = null,
            sleepHours = 7.5, sleepQualityRaw = "good", activityTypeRaw = null, activityMinutes = null,
            ateThatDay = null, importedAtMillis = 1_752_064_496_000,
        ),
        ImportedSample(
            id = "i-2", hcRecordId = null, epochDay = 20_279, domainRaw = null, flowRaw = null,
            sleepHours = null, sleepQualityRaw = null, activityTypeRaw = null, activityMinutes = null,
            ateThatDay = null, importedAtMillis = 1_752_064_497_000,
        ),
    )
    private val dismissed = listOf(
        DismissedInsight(id = "x-1", signalKey = "symptom.pelvicPain.severity", dismissedAtMillis = 1_752_064_496_000),
        DismissedInsight(id = "x-2", signalKey = null, dismissedAtMillis = 1_752_064_497_000),
    )

    private suspend fun seed(db: EndoDatabase) {
        val r = repo(db)
        dayEntries.forEach { r.upsertDayEntry(it) }
        symptoms.forEach { r.upsertSymptom(it) }
        pain.forEach { r.upsertPain(it) }
        bleeding.forEach { r.upsertBleeding(it) }
        factors.forEach { r.upsertFactor(it) }
        // upsertMedication length-bounds text but our values are short — stored verbatim.
        meds.forEach { r.upsertMedication(it) }
        treatments.forEach { r.upsertTreatment(it) }
        imported.forEach { r.upsertImported(it) }
        dismissed.forEach { r.dismissInsight(it) }
    }

    private fun assertReloaded(db: EndoDatabase) = runBlocking {
        assertEquals(dayEntries.toSet(), db.dayEntryDao().all().toSet())
        assertEquals(symptoms.toSet(), db.symptomDao().allSymptoms().toSet())
        assertEquals(pain.toSet(), db.symptomDao().allPain().toSet())
        assertEquals(bleeding.toSet(), db.symptomDao().allBleeding().toSet())
        assertEquals(factors.toSet(), db.symptomDao().allFactors().toSet())
        assertEquals(meds.toSet(), db.medicationDao().allMedications().toSet())
        assertEquals(treatments.toSet(), db.medicationDao().allTreatments().toSet())
        assertEquals(imported.toSet(), db.symptomDao().allImported().toSet())
        assertEquals(dismissed.toSet(), db.insightsDao().allDismissed().toSet())
    }

    @Test
    fun seed_export_encode_decode_import_reproducesEveryRow() = runBlocking {
        seed(source)

        // Export the SOURCE, then push the envelope through the REAL encrypted byte pipeline (23-01).
        val envelope = repo(source).exportSnapshot()
        val passphrase = "correct horse battery staple".toCharArray()
        val bytes = ExportCodec.encode(envelope, passphrase.copyOf())
        val decoded = ExportCodec.decode(bytes, passphrase.copyOf())
        val recovered = (decoded as DecodeResult.Success).envelope

        // Import into a FRESH db and assert every table matches the originals.
        repo(target).importEnvelope(recovered)
        assertReloaded(target)
    }
}
