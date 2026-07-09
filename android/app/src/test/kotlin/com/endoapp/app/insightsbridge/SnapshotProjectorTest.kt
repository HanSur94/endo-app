package com.endoapp.app.insightsbridge

import com.endoapp.app.data.entities.BleedingEvent
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.data.entities.FactorReading
import com.endoapp.app.data.entities.ImportedSample
import com.endoapp.app.data.entities.PainPoint
import com.endoapp.app.data.entities.SymptomReading
import com.endoapp.insights.ActivityIntensity
import com.endoapp.insights.ActivityType
import com.endoapp.insights.BodyRegion
import com.endoapp.insights.DaySnapshot
import com.endoapp.insights.FlowIntensity
import com.endoapp.insights.FoodTag
import com.endoapp.insights.SleepQuality
import com.endoapp.insights.SymptomType
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ports the iOS `DaySnapshotMappingTests` case-for-case as a fast pure-JVM suite (no DB, no emulator).
 *
 * [SnapshotProjector] is the ONLY place Room persistence types meet the pure `:insights` domain; this
 * suite proves the fold is honest (absence ≠ 0), privacy-preserving (free text never crosses —
 * `hasNote` only), defensive on corrupt raws, and enum-faithful. Each iOS `@Test` maps to one `@Test`
 * here (24 cases: 17 mapping + 7 enum-parity), plus a handful of imported-merge / ImportedSignals /
 * FactorKind cases that pin the Phase-21 merge inputs.
 */
class SnapshotProjectorTest {

    // A stable epochDay anchor (day identity is epochDay directly; no timestamp→day math in the fold).
    private val day0 = 19_000L

    private var idSeq = 0
    private fun nextId(): String = "row-${idSeq++}"

    private fun sym(day: Long, typeRaw: String?, severity: Int?) =
        SymptomReading(id = nextId(), epochDay = day, typeRaw = typeRaw, severity = severity)

    private fun painRow(day: Long, regionRaw: String?, severity: Int?) =
        PainPoint(id = nextId(), epochDay = day, regionRaw = regionRaw, severity = severity)

    private fun bleed(day: Long, flowRaw: String?, clots: Boolean?) =
        BleedingEvent(id = nextId(), epochDay = day, flowRaw = flowRaw, clots = clots)

    private fun factor(
        day: Long,
        kind: FactorKind,
        value: String? = null,
        amount: Double? = null,
        level: Int? = null,
        note: String? = null,
    ) = FactorReading(
        id = nextId(), epochDay = day, kindRaw = kind.raw,
        valueRaw = value, amount = amount, level = level, note = note,
    )

    private fun entry(day: Long, note: String?) = DayEntry(id = nextId(), epochDay = day, note = note)

    private fun imported(
        day: Long,
        domain: HealthDomain,
        flowRaw: String? = null,
        sleepHours: Double? = null,
        activityTypeRaw: String? = null,
        activityMinutes: Double? = null,
        ateThatDay: Boolean? = null,
    ) = ImportedSample(
        id = nextId(), epochDay = day, importedAtMillis = 0L, domainRaw = domain.raw,
        flowRaw = flowRaw, sleepHours = sleepHours, activityTypeRaw = activityTypeRaw,
        activityMinutes = activityMinutes, ateThatDay = ateThatDay,
    )

    private fun project(
        symptoms: List<SymptomReading> = emptyList(),
        pain: List<PainPoint> = emptyList(),
        bleeding: List<BleedingEvent> = emptyList(),
        factors: List<FactorReading> = emptyList(),
        entries: List<DayEntry> = emptyList(),
        imported: List<ImportedSample> = emptyList(),
        suppressed: Boolean = false,
    ): List<DaySnapshot> = SnapshotProjector.project(
        symptoms, pain, bleeding, factors, entries, imported, suppressed,
    )

    // MARK: - Bucketing

    @Test fun sameDay_collapsesIntoOneSnapshot() {
        val snaps = project(
            symptoms = listOf(sym(day0, SymptomType.FATIGUE.raw, 3)),
            pain = listOf(painRow(day0, BodyRegion.LOWER_BACK.raw, 5)),
            bleeding = listOf(bleed(day0, FlowIntensity.LIGHT.raw, false)),
            factors = listOf(factor(day0, FactorKind.STRESS, level = 4)),
        )
        assertEquals("All signals on one day collapse into a single DaySnapshot.", 1, snaps.size)
        assertEquals(LocalDate.ofEpochDay(day0), snaps.first().day)
    }

    @Test fun differentDays_distinctSnapshots_sortedAscending() {
        val dayLater = day0 + 3
        val snaps = project(
            factors = listOf(
                factor(dayLater, FactorKind.STRESS, level = 2),
                factor(day0, FactorKind.STRESS, level = 7),
            ),
        )
        assertEquals("Two distinct days produce two snapshots.", 2, snaps.size)
        assertEquals(snaps.map { it.day }.sorted(), snaps.map { it.day })
        assertEquals("Earliest day comes first.", 7, snaps.first().stress)
        assertEquals("Latest day comes last.", 2, snaps.last().stress)
    }

    // MARK: - Symptoms

    @Test fun symptom_mapsTypeAndSeverity() {
        val snap = project(symptoms = listOf(sym(day0, SymptomType.BRAIN_FOG.raw, 6))).first()
        assertEquals(1, snap.symptoms.size)
        val point = snap.symptoms.first()
        assertEquals(SymptomType.BRAIN_FOG, point.type)
        assertEquals(6, point.severity)
    }

    @Test fun symptom_nilSeverity_staysNil() {
        val snap = project(symptoms = listOf(sym(day0, SymptomType.FATIGUE.raw, null))).first()
        assertNull("A logged-present symptom with no severity reads back null, never 0.", snap.symptoms.first().severity)
    }

    // MARK: - Pain

    @Test fun pain_mapsRegionAndSeverity() {
        val snap = project(pain = listOf(painRow(day0, BodyRegion.BLADDER.raw, 8))).first()
        val point = snap.pain.first()
        assertEquals(BodyRegion.BLADDER, point.region)
        assertEquals(8, point.severity)
    }

    @Test fun pain_unknownRegion_isDropped_noCrash() {
        val snap = project(
            pain = listOf(
                painRow(day0, "not-a-real-region", 4),
                painRow(day0, BodyRegion.HIPS.raw, 2),
            ),
        ).first()
        assertEquals("The unknown-region point is dropped; the valid one survives.", 1, snap.pain.size)
        assertEquals(BodyRegion.HIPS, snap.pain.first().region)
    }

    // MARK: - Bleeding

    @Test fun bleeding_flowAndClots_mapped() {
        val snap = project(bleeding = listOf(bleed(day0, FlowIntensity.HEAVY.raw, true))).first()
        assertEquals(FlowIntensity.HEAVY, snap.flow)
        assertTrue(snap.clots)
    }

    @Test fun bleeding_nilClots_coalescesToFalse() {
        val snap = project(bleeding = listOf(bleed(day0, FlowIntensity.LIGHT.raw, null))).first()
        assertEquals(FlowIntensity.LIGHT, snap.flow)
        assertFalse("A null stored clots flag coalesces to false.", snap.clots)
    }

    @Test fun bleeding_noEvent_flowIsNil() {
        val snap = project(factors = listOf(factor(day0, FactorKind.STRESS, level = 3))).first()
        assertNull("No bleeding event ⇒ flow null (frequency uses flow != null).", snap.flow)
        assertFalse(snap.clots)
    }

    // MARK: - Factors honesty (absence ≠ 0)

    @Test fun unloggedStressAndMood_areNil_notZero() {
        val snap = project(
            factors = listOf(factor(day0, FactorKind.SLEEP, value = SleepQuality.GOOD.raw, amount = 8.0)),
        ).first()
        assertNull("Unlogged stress reads back null — never coerced to 0.", snap.stress)
        assertNull("Unlogged mood reads back null — never coerced.", snap.mood)
        assertEquals(8.0, snap.sleepHours!!, 0.0)
        assertEquals(SleepQuality.GOOD, snap.sleepQuality)
    }

    @Test fun loggedStressZero_isZero_notNil() {
        val snap = project(factors = listOf(factor(day0, FactorKind.STRESS, level = 0))).first()
        assertEquals("A logged stress of 0 reads back 0, never null.", 0, snap.stress)
    }

    @Test fun factorFields_mapToPackageEnums() {
        val snap = project(
            factors = listOf(
                factor(day0, FactorKind.FOOD, value = FoodTag.DAIRY.raw),
                factor(day0, FactorKind.FOOD, value = FoodTag.CAFFEINE.raw),
                factor(day0, FactorKind.ACTIVITY, value = ActivityType.CARDIO.raw, amount = 30.0, level = 2),
            ),
        ).first()
        assertEquals(setOf(FoodTag.DAIRY, FoodTag.CAFFEINE), snap.foodTags.toSet())
        assertEquals(ActivityType.CARDIO, snap.activityType)
        assertEquals(30.0, snap.activityMinutes!!, 0.0)
        assertEquals(ActivityIntensity.HIGH, snap.activityIntensity)
    }

    // MARK: - hasNote (presence only — never the prose)

    @Test fun hasNote_reflectsPresenceOnly() {
        val snaps = project(
            factors = listOf(factor(day0 + 1, FactorKind.STRESS, level = 1)),
            entries = listOf(entry(day0, "a private jot")),
        )
        assertTrue("A day with a note → hasNote true.", snaps.first().hasNote)
        assertFalse("A day without a note → hasNote false.", snaps.last().hasNote)
    }

    // MARK: - Suppressed propagation

    @Test fun suppressed_propagatesOntoEverySnapshot() {
        val factors = listOf(
            factor(day0, FactorKind.STRESS, level = 2),
            factor(day0 + 1, FactorKind.STRESS, level = 5),
        )
        project(factors = factors, suppressed = true).forEach {
            assertTrue("Every snapshot is suppressed under suppression.", it.suppressed)
        }
        project(factors = factors, suppressed = false).forEach {
            assertFalse("No snapshot is suppressed when not suppressed.", it.suppressed)
        }
    }

    // MARK: - Defensive decode

    @Test fun unknownRaws_decodeDefensively_noCrash() {
        val snap = project(
            symptoms = listOf(sym(day0, SymptomType.FATIGUE.raw, 3)),
            factors = listOf(factor(day0, FactorKind.FOOD, value = "not-a-real-tag")),
        ).first()
        assertEquals("Unknown food tag decodes to OTHER.", listOf(FoodTag.OTHER), snap.foodTags)
    }

    @Test fun unknownSymptomType_isDropped_notFabricatedAsOther() {
        val snap = project(
            symptoms = listOf(
                sym(day0, "not-a-real-symptom", 2),
                sym(day0, SymptomType.FATIGUE.raw, 4),
            ),
        ).first()
        assertEquals("The undecodable-type symptom row is dropped; the valid one survives.", 1, snap.symptoms.size)
        assertEquals(SymptomType.FATIGUE, snap.symptoms.first().type)
        assertFalse(
            "An unknown typeRaw must NEVER be coerced to a fabricated OTHER symptom (WR-01).",
            snap.symptoms.any { it.type == SymptomType.OTHER },
        )
    }

    @Test fun nilSymptomType_isDropped_notFabricatedAsOther() {
        val snap = project(
            symptoms = listOf(sym(day0, null, 2)),
            factors = listOf(factor(day0, FactorKind.STRESS, level = 3)),
        ).first()
        assertTrue("A null-typeRaw symptom row is dropped, not fabricated as OTHER (WR-01).", snap.symptoms.isEmpty())
    }

    // MARK: - Enum parity (no drift): every catalog raw round-trips through the projector's decoder.

    @Test fun enumParity_symptomType() {
        SymptomType.entries.forEach { e -> assertEquals(e, SymptomType.fromRaw(e.raw)) }
    }

    @Test fun enumParity_bodyRegion() {
        BodyRegion.entries.forEach { e -> assertEquals(e, BodyRegion.fromRawOrNull(e.raw)) }
    }

    @Test fun enumParity_flowIntensity() {
        FlowIntensity.entries.forEach { e -> assertEquals(e, FlowIntensity.fromRawOrNull(e.raw)) }
    }

    @Test fun enumParity_foodTag() {
        FoodTag.entries.forEach { e -> assertEquals(e, FoodTag.fromRaw(e.raw)) }
    }

    @Test fun enumParity_activityType() {
        ActivityType.entries.forEach { e -> assertEquals(e, ActivityType.fromRaw(e.raw)) }
    }

    @Test fun enumParity_activityIntensity() {
        ActivityIntensity.entries.forEach { e -> assertEquals(e, ActivityIntensity.fromRawOrNull(e.raw)) }
    }

    @Test fun enumParity_sleepQuality() {
        SleepQuality.entries.forEach { e -> assertEquals(e, SleepQuality.fromRawOrNull(e.raw)) }
    }

    // MARK: - Imported merge + ImportedSignals + FactorKind (Phase-21 merge inputs pinned now)

    // An imported-only day joins the union and its signals surface (manual absent).
    @Test fun importedOnlyDay_joinsUnion_andSurfaces() {
        val snap = project(
            imported = listOf(imported(day0, HealthDomain.CYCLE, flowRaw = FlowIntensity.MEDIUM.raw)),
        ).single()
        assertEquals(LocalDate.ofEpochDay(day0), snap.day)
        assertEquals("Imported flow surfaces when there is no manual flow.", FlowIntensity.MEDIUM, snap.flow)
    }

    // Manual-wins, per-field: a manual flow beats an imported flow on the same day.
    @Test fun manualWins_overImported_perField() {
        val snap = project(
            bleeding = listOf(bleed(day0, FlowIntensity.LIGHT.raw, null)),
            imported = listOf(imported(day0, HealthDomain.CYCLE, flowRaw = FlowIntensity.HEAVY.raw)),
        ).single()
        assertEquals("Manual flow wins over imported flow.", FlowIntensity.LIGHT, snap.flow)
    }

    // Imported "ate that day" contributes a single OTHER food tag ONLY when no manual food exists.
    @Test fun importedAteThatDay_addsOtherTag_onlyWhenNoManualFood() {
        val withImportOnly = project(
            imported = listOf(imported(day0, HealthDomain.NUTRITION, ateThatDay = true)),
        ).single()
        assertEquals(listOf(FoodTag.OTHER), withImportOnly.foodTags)

        val withManualFood = project(
            factors = listOf(factor(day0, FactorKind.FOOD, value = FoodTag.DAIRY.raw)),
            imported = listOf(imported(day0, HealthDomain.NUTRITION, ateThatDay = true)),
        ).single()
        assertEquals("Manual food wins — no fabricated OTHER tag.", listOf(FoodTag.DAIRY), withManualFood.foodTags)
    }

    // ImportedSignals CR-01: sleep hours = SUM of the night's segment rows; coarse quality from the total.
    @Test fun importedSignals_sleepSumsSegments_andBandsFromTotal() {
        val signals = ImportedSignals.from(
            listOf(
                imported(day0, HealthDomain.SLEEP, sleepHours = 3.0),
                imported(day0, HealthDomain.SLEEP, sleepHours = 4.5),
            ),
        )
        assertEquals(7.5, signals.sleepHours!!, 0.0)
        assertEquals("7.5h total lands in the GOOD band (≥ 7).", SleepQuality.GOOD, signals.sleepQuality)
    }

    // ImportedSignals WR-04: workout minutes SUM; the type comes from the LONGEST session, deterministically.
    @Test fun importedSignals_workoutSumsMinutes_keepsLongestType() {
        val signals = ImportedSignals.from(
            listOf(
                imported(day0, HealthDomain.WORKOUTS, activityTypeRaw = ActivityType.WALKING.raw, activityMinutes = 20.0),
                imported(day0, HealthDomain.WORKOUTS, activityTypeRaw = ActivityType.CARDIO.raw, activityMinutes = 45.0),
            ),
        )
        assertEquals(65.0, signals.activityMinutes!!, 0.0)
        assertEquals("Longest session (45m cardio) sets the type.", ActivityType.CARDIO, signals.activityType)
    }

    // ImportedSignals T-18-06: an unknown domain raw contributes nothing, never crashes.
    @Test fun importedSignals_unknownDomain_contributesNothing() {
        val sample = ImportedSample(id = nextId(), epochDay = day0, importedAtMillis = 0L, domainRaw = "not-a-domain")
        val signals = ImportedSignals.from(listOf(sample))
        assertEquals(ImportedSignals(), signals)
    }

    // FactorKind T-18-06: an unknown/null raw sinks to OTHER; each of the 7 raws round-trips.
    @Test fun factorKind_defensiveDecode_andRoundTrip() {
        assertEquals(FactorKind.OTHER, FactorKind.fromRaw(null))
        assertEquals(FactorKind.OTHER, FactorKind.fromRaw("not-a-kind"))
        FactorKind.entries.forEach { k -> assertEquals(k, FactorKind.fromRaw(k.raw)) }
    }

    // Medication factor: a saved-med UUID is carried; the "prn" marker sets tookPRN, never a fake UUID.
    @Test fun medicationFactor_carriesUuid_andPrnMarker() {
        val medId = UUID.randomUUID()
        val snap = project(
            factors = listOf(
                factor(day0, FactorKind.MEDICATION, value = medId.toString()),
                factor(day0, FactorKind.MEDICATION, value = "prn"),
            ),
        ).single()
        assertEquals(listOf(medId), snap.medicationsTaken)
        assertTrue("The prn marker sets tookPRN.", snap.tookPRN)
    }

    // Privacy T-18-09: the note-bearing `.food` row is DROPPED — its free text never crosses.
    @Test fun noteBearingFoodRow_isDropped_prosNeverCrosses() {
        val snap = project(
            factors = listOf(
                factor(day0, FactorKind.FOOD, value = FoodTag.GLUTEN_WHEAT.raw),
                factor(day0, FactorKind.FOOD, note = "ate a whole cake, felt awful"),
            ),
        ).single()
        assertEquals("Only the tag row crosses; the note-bearing row is dropped.", listOf(FoodTag.GLUTEN_WHEAT), snap.foodTags)
    }
}
