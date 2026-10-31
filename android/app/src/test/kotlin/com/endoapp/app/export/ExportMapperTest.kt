package com.endoapp.app.export

import com.endoapp.app.data.entities.BleedingEvent
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.data.entities.DismissedInsight
import com.endoapp.app.data.entities.FactorReading
import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.data.entities.Medication
import com.endoapp.app.data.entities.PainPoint
import com.endoapp.app.data.entities.SymptomReading
import com.endoapp.app.data.entities.TreatmentState
import com.endoapp.app.export.ExportMapper.toDto
import com.endoapp.app.export.ExportMapper.toEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Pure-JVM proof that [ExportMapper] is a LOSSLESS, single conversion place for all 9 exported types
 * (APRT-01/02). For each type it round-trips BOTH a fully-populated entity AND a null-health-value
 * entity `entity.toDto().toEntity()` and asserts identity — so absence (null) survives, never coerced
 * to 0/false. It also pins the exact unit conversions: a known epochDay ↔ yyyy-MM-dd and a known
 * millis ↔ ISO-8601 instant, forward and back.
 */
class ExportMapperTest {

    // A concrete day + instant with known string forms.
    private val epochDay = 20_278L // 2025-07-09
    private val isoDay = "2025-07-09"
    private val millis = 1_752_064_496_000L // 2025-07-09T12:34:56Z
    private val iso = "2025-07-09T12:34:56Z"

    @Test
    fun epochDay_and_millis_convert_exactly_and_reversibly() {
        assertEquals(isoDay, LocalDate.ofEpochDay(epochDay).toString())
        assertEquals(epochDay, LocalDate.parse(isoDay).toEpochDay())
        assertEquals(iso, Instant.ofEpochMilli(millis).toString())
        assertEquals(millis, Instant.parse(iso).toEpochMilli())
    }

    @Test
    fun dayEntry_roundTrips_bothFullAndNull() {
        val full = DayEntry(id = "de-1", epochDay = epochDay, note = "a note")
        val nulls = DayEntry(id = "de-2", epochDay = epochDay, note = null)
        assertEquals(isoDay, full.toDto().day)
        assertEquals(full, full.toDto().toEntity())
        assertEquals(nulls, nulls.toDto().toEntity())
    }

    @Test
    fun symptomReading_roundTrips_bothFullAndNull() {
        val full = SymptomReading(id = "s-1", epochDay = epochDay, typeRaw = "pelvicPain", severity = 3, note = "n")
        val nulls = SymptomReading(id = "s-2", epochDay = epochDay, typeRaw = null, severity = null, note = null)
        assertEquals(isoDay, full.toDto().day)
        assertEquals(full, full.toDto().toEntity())
        assertEquals("null severity survives (absence ≠ 0)", nulls, nulls.toDto().toEntity())
    }

    @Test
    fun painPoint_roundTrips_bothFullAndNull() {
        val full = PainPoint(id = "p-1", epochDay = epochDay, regionRaw = "lowerAbdomen", severity = 2)
        val nulls = PainPoint(id = "p-2", epochDay = epochDay, regionRaw = null, severity = null)
        assertEquals(full, full.toDto().toEntity())
        assertEquals(nulls, nulls.toDto().toEntity())
    }

    @Test
    fun bleedingEvent_roundTrips_bothFullAndNull() {
        val full = BleedingEvent(id = "b-1", epochDay = epochDay, flowRaw = "heavy", note = "n", clots = true)
        val nulls = BleedingEvent(id = "b-2", epochDay = epochDay, flowRaw = null, note = null, clots = null)
        assertEquals(full, full.toDto().toEntity())
        assertEquals("null clots survives (null ≠ false)", nulls, nulls.toDto().toEntity())
    }

    @Test
    fun factorReading_roundTrips_bothFullAndNull() {
        val full = FactorReading(
            id = "f-1", epochDay = epochDay, kindRaw = "activity", valueRaw = "walk",
            amount = 30.0, level = 2, note = "n",
        )
        val nulls = FactorReading(
            id = "f-2", epochDay = epochDay, kindRaw = null, valueRaw = null,
            amount = null, level = null, note = null,
        )
        assertEquals(full, full.toDto().toEntity())
        assertEquals("all payload nulls survive", nulls, nulls.toDto().toEntity())
    }

    @Test
    fun medication_roundTrips_bothFullAndNull() {
        val full = Medication(id = "m-1", name = "Ibuprofen", note = "400mg", createdAtMillis = millis, isArchived = true)
        val nulls = Medication(id = "m-2", name = null, note = null, createdAtMillis = millis, isArchived = null)
        assertEquals(iso, full.toDto().createdAt)
        assertEquals(full, full.toDto().toEntity())
        assertEquals(nulls, nulls.toDto().toEntity())
    }

    @Test
    fun treatmentState_roundTrips_bothFullAndNull() {
        val full = TreatmentState(id = "t-1", startedAtMillis = millis, modeRaw = "suppressed")
        val nulls = TreatmentState(id = "t-2", startedAtMillis = millis, modeRaw = null)
        assertEquals(iso, full.toDto().startedAt)
        assertEquals(full, full.toDto().toEntity())
        assertEquals(nulls, nulls.toDto().toEntity())
    }

    @Test
    fun importedSample_roundTrips_bothFullAndNull() {
        val full = ImportedSample(
            id = "i-1", hcRecordId = "hc-1", epochDay = epochDay, domainRaw = "sleep", flowRaw = null,
            sleepHours = 7.5, sleepQualityRaw = "good", activityTypeRaw = "run", activityMinutes = 45.0,
            ateThatDay = true, importedAtMillis = millis,
        )
        val nulls = ImportedSample(
            id = "i-2", hcRecordId = null, epochDay = epochDay, domainRaw = null, flowRaw = null,
            sleepHours = null, sleepQualityRaw = null, activityTypeRaw = null, activityMinutes = null,
            ateThatDay = null, importedAtMillis = millis,
        )
        assertEquals(isoDay, full.toDto().day)
        assertEquals(iso, full.toDto().importedAt)
        assertEquals(full, full.toDto().toEntity())
        assertEquals("all imported health nulls survive", nulls, nulls.toDto().toEntity())
    }

    @Test
    fun dismissedInsight_roundTrips_bothFullAndNull() {
        val full = DismissedInsight(id = "x-1", signalKey = "symptom.pelvicPain.severity", dismissedAtMillis = millis)
        val nulls = DismissedInsight(id = "x-2", signalKey = null, dismissedAtMillis = millis)
        assertEquals(iso, full.toDto().dismissedAt)
        assertEquals(full, full.toDto().toEntity())
        assertEquals(nulls, nulls.toDto().toEntity())
    }

    @Test
    fun toEnvelope_then_toEntities_roundTrips_allNineLists() {
        val dayEntries = listOf(DayEntry(id = "de", epochDay = epochDay, note = "n"))
        val symptoms = listOf(SymptomReading(id = "s", epochDay = epochDay, severity = null))
        val pain = listOf(PainPoint(id = "p", epochDay = epochDay))
        val bleeding = listOf(BleedingEvent(id = "b", epochDay = epochDay, clots = null))
        val factors = listOf(FactorReading(id = "f", epochDay = epochDay))
        val meds = listOf(Medication(id = "m", createdAtMillis = millis))
        val treatments = listOf(TreatmentState(id = "t", startedAtMillis = millis))
        val imported = listOf(ImportedSample(id = "i", epochDay = epochDay, importedAtMillis = millis))
        val dismissed = listOf(DismissedInsight(id = "x", dismissedAtMillis = millis))

        val envelope = ExportMapper.toEnvelope(
            dayEntries, symptoms, pain, bleeding, factors, meds, treatments, imported, dismissed,
            exportedAt = iso,
        )
        assertEquals(1, envelope.schemaVersion)
        assertEquals(iso, envelope.exportedAt)

        val bundle = ExportMapper.toEntities(envelope)
        assertEquals(dayEntries, bundle.dayEntries)
        assertEquals(symptoms, bundle.symptomReadings)
        assertEquals(pain, bundle.painPoints)
        assertEquals(bleeding, bundle.bleedingEvents)
        assertEquals(factors, bundle.factorReadings)
        assertEquals(meds, bundle.medications)
        assertEquals(treatments, bundle.treatmentStates)
        assertEquals(imported, bundle.importedSamples)
        assertEquals(dismissed, bundle.dismissedInsights)
    }
}
