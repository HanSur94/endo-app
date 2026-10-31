package com.endoapp.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator proof of the exact delete-day survival set (GDPR Art. 17, born complete).
 *
 * Inserts one row of ALL 10 entity types — the 6 day-scoped ones on the SAME `epochDay` D, the
 * definitions/dismissal on their own keys — then calls [DeleteDayDao.deleteDay] and asserts:
 *   - all 6 day-scoped tables are empty for D (incl. `ImportedSample` — the iOS omission is closed),
 *   - `Medication`, `TreatmentState`, `DismissedInsight` survive (not day-scoped),
 *   - `CachedInsight` is empty (cache invalidation).
 */
@RunWith(AndroidJUnit4::class)
class DeleteDayCascadeTest {

    private lateinit var db: EndoDatabase

    private val day = 20_000L
    private val otherDay = 20_001L

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

    @Test
    fun deleteDay_purgesAll6DayScopedTypes_survivesDefinitionsAndDismissals() = runBlocking {
        val dayEntryDao = db.dayEntryDao()
        val symptomDao = db.symptomDao()
        val medicationDao = db.medicationDao()
        val insightsDao = db.insightsDao()
        val deleteDayDao = db.deleteDayDao()

        // --- Insert one row of every day-scoped type on day D ---
        dayEntryDao.upsert(DayEntry(id = "de", epochDay = day, note = "note"))
        symptomDao.upsertSymptom(SymptomReading(id = "sr", epochDay = day, typeRaw = "cramps", severity = 3))
        symptomDao.upsertPain(PainPoint(id = "pp", epochDay = day, regionRaw = "pelvicPain", severity = 2))
        symptomDao.upsertBleeding(BleedingEvent(id = "be", epochDay = day, flowRaw = "medium"))
        symptomDao.upsertFactor(FactorReading(id = "fr", epochDay = day, kindRaw = "stress", level = 4))
        symptomDao.upsertImported(
            ImportedSample(id = "is", hcRecordId = "hc1", epochDay = day, importedAtMillis = 111L),
        )

        // --- Insert the definitions / dismissal that MUST survive ---
        medicationDao.upsertMedication(Medication(id = "med", name = "Ibuprofen", createdAtMillis = 1L))
        medicationDao.upsertTreatment(TreatmentState(id = "ts", startedAtMillis = 2L, modeRaw = "suppressed"))
        insightsDao.insertDismissed(DismissedInsight(id = "di", signalKey = "sig", dismissedAtMillis = 3L))
        insightsDao.insertCached(
            listOf(CachedInsight(id = "ci", signalKey = "sig", computedAtMillis = 4L)),
        )

        // A row on a DIFFERENT day must be untouched by deleteDay(D).
        symptomDao.upsertSymptom(SymptomReading(id = "sr2", epochDay = otherDay, typeRaw = "cramps"))

        // --- Act ---
        deleteDayDao.deleteDay(day)

        // --- Assert: all 6 day-scoped tables empty for D ---
        assertTrue("DayEntry not purged", dayEntryDao.forDay(day).isEmpty())
        assertTrue("SymptomReading not purged", symptomDao.symptomsForDay(day).isEmpty())
        assertTrue("PainPoint not purged", symptomDao.painForDay(day).isEmpty())
        assertTrue("BleedingEvent not purged", symptomDao.bleedingForDay(day).isEmpty())
        assertTrue("FactorReading not purged", symptomDao.factorsForDay(day).isEmpty())
        assertTrue("ImportedSample not purged (iOS omission not closed!)", symptomDao.importedForDay(day).isEmpty())

        // --- Assert: definitions + dismissal survive ---
        assertEquals("Medication must survive", 1, medicationDao.allMedications().size)
        assertEquals("TreatmentState must survive", 1, medicationDao.allTreatments().size)
        assertEquals("DismissedInsight must survive", 1, insightsDao.allDismissed().size)

        // --- Assert: CachedInsight invalidated (delete-all) ---
        assertTrue("CachedInsight must be invalidated", insightsDao.allCached().isEmpty())

        // --- Assert: a different day is untouched ---
        assertEquals("Other day must survive", 1, symptomDao.symptomsForDay(otherDay).size)
    }
}
