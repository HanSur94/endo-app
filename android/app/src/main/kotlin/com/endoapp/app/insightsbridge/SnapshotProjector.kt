// EndoApp (:app) — the ONLY persistence↔domain meeting point (INST-01 / D-10 / D-14).
//
// Kotlin port of the iOS `Repository.daySnapshots()` fold (Repository.swift 533–591, makeDaySnapshot
// 864–939, projectFactors 945–1000). A PURE function: Room row lists in, `:insights` [DaySnapshot]
// values out — no DB, no coroutines, no Android type in the fold logic beyond the entity params. It
// runs as fast JVM unit tests (no emulator) and is pinned by the 24-case parity suite that mirrors
// the iOS `DaySnapshotMappingTests` case-for-case.
//
// PRIVACY (T-18-09): free text NEVER crosses the bridge. `DayEntry.note` becomes `hasNote: Boolean`
// (presence only); the note-bearing `.food` FactorReading row is DROPPED, not carried.
//
// HONESTY (T-18-10): absence ≠ 0. Unlogged stress/mood → `null`; a logged 0 → 0. No `?: 0` on health
// values. Day bucketing goes through `java.time.LocalDate` (epochDay), never epoch-millis math.
//
// ROBUSTNESS (T-18-06): every raw decodes defensively (fromRaw / fromRawOrNull / FactorKind.fromRaw);
// an unknown/corrupt raw drops or sinks, never throws. UUID parse is guarded.
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
import com.endoapp.insights.SymptomPoint
import com.endoapp.insights.SymptomType
import java.time.LocalDate
import java.util.UUID
import com.endoapp.insights.PainPoint as PainSnapshot

/**
 * The pure Room-rows → `[DaySnapshot]` projection. The single seam where persistence entity types
 * meet the pure `:insights` domain; entity types must never leak past it.
 */
object SnapshotProjector {

    /**
     * Fold the fetched day-scoped row lists into one [DaySnapshot] per union-day, ascending.
     *
     * @param suppressed the current suppression flag (latest TreatmentState), stamped on EVERY snapshot.
     */
    fun project(
        symptoms: List<SymptomReading>,
        pain: List<PainPoint>,
        bleeding: List<BleedingEvent>,
        factors: List<FactorReading>,
        entries: List<DayEntry>,
        imported: List<ImportedSample>,
        suppressed: Boolean,
    ): List<DaySnapshot> {
        // Bucket every day-scoped type by epochDay. The union of all day keys drives one DaySnapshot
        // per day that carries ANY logged signal.
        val symptomsByDay = symptoms.groupBy { it.epochDay }
        val painByDay = pain.groupBy { it.epochDay }
        val bleedingByDay = bleeding.groupBy { it.epochDay }
        val factorsByDay = factors.groupBy { it.epochDay }
        val importedByDay = imported.groupBy { it.epochDay }
        // noteDays = the set of days whose DayEntry.note != null (presence only — prose never crosses).
        val noteDays: Set<Long> = entries.filter { it.note != null }.map { it.epochDay }.toSet()

        val dayKeys: Set<Long> = buildSet {
            addAll(symptomsByDay.keys)
            addAll(painByDay.keys)
            addAll(bleedingByDay.keys)
            addAll(factorsByDay.keys)
            addAll(noteDays)
            addAll(importedByDay.keys)
        }

        return dayKeys.sorted().map { key ->
            makeDaySnapshot(
                day = LocalDate.ofEpochDay(key),
                symptoms = symptomsByDay[key].orEmpty(),
                pain = painByDay[key].orEmpty(),
                bleeding = bleedingByDay[key].orEmpty(),
                factors = factorsByDay[key].orEmpty(),
                imported = ImportedSignals.from(importedByDay[key].orEmpty()),
                hasNote = noteDays.contains(key),
                suppressed = suppressed,
            )
        }
    }

    /** Project one day's raw rows into a [DaySnapshot], with the manual-wins / nil-honest merge. */
    private fun makeDaySnapshot(
        day: LocalDate,
        symptoms: List<SymptomReading>,
        pain: List<PainPoint>,
        bleeding: List<BleedingEvent>,
        factors: List<FactorReading>,
        imported: ImportedSignals,
        hasNote: Boolean,
        suppressed: Boolean,
    ): DaySnapshot {
        // Symptoms → SymptomPoint. A row whose typeRaw is null/undecodable is DROPPED (never coerced
        // to a real OTHER), mirroring the pain-point drop below (WR-01). nil severity stays nil (≠ 0).
        val symptomPoints: List<SymptomPoint> = symptoms.mapNotNull { r ->
            val type = SymptomType.entries.firstOrNull { it.raw == r.typeRaw } ?: return@mapNotNull null
            SymptomPoint(type = type, severity = r.severity)
        }

        // Pain → PainSnapshot. A point whose region decodes to null (unknown) is DROPPED, never crashed.
        val painPoints: List<PainSnapshot> = pain.mapNotNull { p ->
            val region = BodyRegion.fromRawOrNull(p.regionRaw) ?: return@mapNotNull null
            PainSnapshot(region = region, severity = p.severity)
        }

        // Bleeding → the day's flow-bearing event (no event ⇒ manual flow nil; nil clots ⇒ false).
        val flowEvent = bleeding.firstOrNull { FlowIntensity.fromRawOrNull(it.flowRaw) != null }
        val manualFlow = FlowIntensity.fromRawOrNull(flowEvent?.flowRaw)
        val clots = flowEvent?.clots ?: false

        val f = projectFactors(factors)

        // MANUAL-WINS, PER-FIELD, NIL-HONEST MERGE (D-02/D-04): for each imported-capable field,
        // manual present ⇒ manual; else imported present ⇒ imported; else nil (NEVER 0, NEVER fabricated).
        val flow = manualFlow ?: imported.flow
        val sleepHours = f.sleepHours ?: imported.sleepHours
        val sleepQuality = f.sleepQuality ?: imported.sleepQuality
        // Activity type+minutes are one logical unit: imported fills both ONLY when there is no manual
        // activity type (so an imported workout never grafts minutes onto a manual-typed activity).
        val useImportedActivity = f.activityType == null && imported.activityType != null
        val activityType = if (useImportedActivity) imported.activityType else f.activityType
        val activityMinutes = if (useImportedActivity) imported.activityMinutes else f.activityMinutes

        // Food: nutrition is presence-only (D-16). Imported "ate that day" contributes a single OTHER
        // food-presence tag ONLY when the day has NO manual food tags (manual wins).
        val foodTags = if (f.foodTags.isEmpty() && imported.ateThatDay == true) {
            listOf(FoodTag.OTHER)
        } else {
            f.foodTags
        }

        return DaySnapshot.of(
            day = day,
            symptoms = symptomPoints,
            pain = painPoints,
            flow = flow,
            clots = clots,
            foodTags = foodTags,
            activityType = activityType,
            activityMinutes = activityMinutes,
            activityIntensity = f.activityIntensity,
            sleepHours = sleepHours,
            sleepQuality = sleepQuality,
            medicationsTaken = f.medicationsTaken,
            tookPRN = f.tookPRN,
            stress = f.stress,
            mood = f.mood,
            hasNote = hasNote,
            suppressed = suppressed,
        )
    }

    /** Per-day factor projection, discriminated by [FactorKind]. Defensive decode; honest nil ≠ 0. */
    private fun projectFactors(readings: List<FactorReading>): FactorProjection {
        val foodTags = mutableListOf<FoodTag>()
        var activityType: ActivityType? = null
        var activityMinutes: Double? = null
        var activityIntensity: ActivityIntensity? = null
        var sleepHours: Double? = null
        var sleepQuality: SleepQuality? = null
        val medicationsTaken = mutableListOf<UUID>()
        var tookPRN = false
        var stress: Int? = null
        var mood: Int? = null

        for (reading in readings) {
            when (FactorKind.fromRaw(reading.kindRaw)) {
                FactorKind.FOOD -> {
                    when {
                        // The note-bearing `.food` row carries free text — NOT carried into the
                        // snapshot (privacy, T-18-09).
                        reading.note != null -> Unit
                        // Unknown raw → OTHER (never dropped silently).
                        reading.valueRaw != null -> foodTags.add(FoodTag.fromRaw(reading.valueRaw))
                    }
                }
                FactorKind.ACTIVITY -> {
                    activityType = ActivityType.fromRaw(reading.valueRaw)
                    activityMinutes = reading.amount
                    activityIntensity = intensityFromLevel(reading.level)
                }
                FactorKind.SLEEP -> {
                    sleepHours = reading.amount
                    sleepQuality = SleepQuality.fromRawOrNull(reading.valueRaw)
                }
                FactorKind.MEDICATION -> {
                    if (reading.valueRaw == "prn") {
                        tookPRN = true
                    } else {
                        reading.valueRaw?.let { raw ->
                            parseUuidOrNull(raw)?.let { medicationsTaken.add(it) }
                        }
                    }
                }
                FactorKind.STRESS -> stress = reading.level // nil stays nil (no reading ⇒ never set).
                FactorKind.MOOD -> mood = reading.level
                FactorKind.OTHER -> Unit
            }
        }

        return FactorProjection(
            foodTags = foodTags,
            activityType = activityType,
            activityMinutes = activityMinutes,
            activityIntensity = activityIntensity,
            sleepHours = sleepHours,
            sleepQuality = sleepQuality,
            medicationsTaken = medicationsTaken,
            tookPRN = tookPRN,
            stress = stress,
            mood = mood,
        )
    }

    /** Stored `level` → [ActivityIntensity] (0 = low, 1 = medium, 2 = high; else null). */
    private fun intensityFromLevel(level: Int?): ActivityIntensity? = when (level) {
        0 -> ActivityIntensity.LOW
        1 -> ActivityIntensity.MEDIUM
        2 -> ActivityIntensity.HIGH
        else -> null
    }

    /** Guarded UUID parse — a non-UUID valueRaw (e.g. the "prn" marker slipping through) → null. */
    private fun parseUuidOrNull(raw: String): UUID? =
        try { UUID.fromString(raw) } catch (_: IllegalArgumentException) { null }

    private data class FactorProjection(
        val foodTags: List<FoodTag>,
        val activityType: ActivityType?,
        val activityMinutes: Double?,
        val activityIntensity: ActivityIntensity?,
        val sleepHours: Double?,
        val sleepQuality: SleepQuality?,
        val medicationsTaken: List<UUID>,
        val tookPRN: Boolean,
        val stress: Int?,
        val mood: Int?,
    )
}
