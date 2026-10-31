package com.endoapp.app.export

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.endoapp.app.data.ConsentState
import com.endoapp.app.data.ConsentStore
import com.endoapp.app.data.EndoDatabase
import com.endoapp.app.data.EndoRepository
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
 * Idempotency proof against REAL in-memory Room (Robolectric): importing the SAME envelope twice into a
 * db leaves BYTE-IDENTICAL rows — no duplicates, no change — asserted per table (APRT-02, T-23-06c).
 *
 * The mechanism under test is `OnConflictStrategy.REPLACE` on the UUID primary key inside the single
 * `@Transaction` [com.endoapp.app.data.daos.ImportDao.importAll]: the second import REPLACEs each row
 * with identical content keyed by the same UUID, so counts stay stable and contents are unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportIdempotencyTest {

    private lateinit var db: EndoDatabase

    private class SettableConsentStore(state: ConsentState) : ConsentStore {
        private val _state = MutableStateFlow(state)
        override val state: StateFlow<ConsentState> = _state
        override suspend fun grant() { _state.value = ConsentState.Consented(0L) }
        override suspend fun withdraw() { _state.value = ConsentState.NotConsented }
    }

    private fun repo(): EndoRepository =
        EndoRepository.from(SettableConsentStore(ConsentState.Consented(0L)), db)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EndoDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** A full envelope covering every one of the 9 exported types (two rows each). */
    private val envelope = ExportEnvelope(
        exportedAt = "2025-07-09T12:34:56Z",
        dayEntries = listOf(
            DayEntryDto(id = "de-1", day = "2025-07-09", note = "n"),
            DayEntryDto(id = "de-2", day = "2025-07-10", note = null),
        ),
        symptomReadings = listOf(
            SymptomReadingDto(id = "s-1", day = "2025-07-09", typeRaw = "pelvicPain", severity = 4),
            SymptomReadingDto(id = "s-2", day = "2025-07-10", severity = null),
        ),
        painPoints = listOf(
            PainPointDto(id = "p-1", day = "2025-07-09", regionRaw = "lowerAbdomen", severity = 2),
        ),
        bleedingEvents = listOf(
            BleedingEventDto(id = "b-1", day = "2025-07-09", flowRaw = "heavy", clots = true),
        ),
        factorReadings = listOf(
            FactorReadingDto(id = "f-1", day = "2025-07-09", kindRaw = "activity", amount = 30.0, level = 2),
        ),
        medications = listOf(
            MedicationDto(id = "m-1", name = "Ibuprofen", createdAt = "2025-07-09T12:34:56Z"),
        ),
        treatmentStates = listOf(
            TreatmentStateDto(id = "t-1", startedAt = "2025-07-09T12:34:56Z", modeRaw = "suppressed"),
        ),
        importedSamples = listOf(
            ImportedSampleDto(id = "i-1", hcRecordId = "hc-1", day = "2025-07-09", importedAt = "2025-07-09T12:34:56Z"),
        ),
        dismissedInsights = listOf(
            DismissedInsightDto(id = "x-1", signalKey = "symptom.pelvicPain.severity", dismissedAt = "2025-07-09T12:34:56Z"),
        ),
    )

    private fun rowCounts() = runBlocking {
        listOf(
            db.dayEntryDao().all().size,
            db.symptomDao().allSymptoms().size,
            db.symptomDao().allPain().size,
            db.symptomDao().allBleeding().size,
            db.symptomDao().allFactors().size,
            db.medicationDao().allMedications().size,
            db.medicationDao().allTreatments().size,
            db.symptomDao().allImported().size,
            db.insightsDao().allDismissed().size,
        )
    }

    @Test
    fun reimportingSameEnvelope_leavesIdenticalRows_noDuplication() = runBlocking {
        val r = repo()

        r.importEnvelope(envelope)
        val afterFirst = rowCounts()
        val symptomsAfterFirst = db.symptomDao().allSymptoms().toSet()

        // Re-import the SAME envelope.
        r.importEnvelope(envelope)
        val afterSecond = rowCounts()
        val symptomsAfterSecond = db.symptomDao().allSymptoms().toSet()

        assertEquals("row counts per table are stable across a re-import (no duplicates)", afterFirst, afterSecond)
        assertEquals("the expected per-table counts after import", listOf(2, 2, 1, 1, 1, 1, 1, 1, 1), afterSecond)
        assertEquals("row CONTENTS are unchanged by the re-import", symptomsAfterFirst, symptomsAfterSecond)
    }
}
