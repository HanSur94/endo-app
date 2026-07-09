// EndoApp (:app) — the PURE draft ↔ Room-rows seam (ATRK-05, threat T-20-01). The single place a
// [DayDraft] becomes persistable rows and back, and the ONLY place the absence ≠ 0 distinction is
// resolved on the write/read path.
//
// PURITY: no Android, no Room engine, no coroutine — plain entity value lists in and out, so it runs
// as fast JVM unit tests (AbsenceNotZeroTest) with no emulator. Identity is injected via `idFor` so
// the mapper fabricates no UUIDs of its own (the app supplies stable per-day ids for idempotent
// upserts; tests pass identity).
//
// HONESTY (T-20-01): `?: 0` (or any nil→0 coercion) is FORBIDDEN on every health value here. An
// untouched optional field produces NO row (or a null column) so it reads back nil; a logged 0
// produces a real 0. The DayEntry anchor is ALWAYS emitted (even for an empty confirm) so the
// adherence marker is recorded without inventing any factor/symptom/bleeding data.
package com.endoapp.app.ui.tracking

import com.endoapp.app.data.entities.BleedingEvent
import com.endoapp.app.data.entities.DayEntry
import com.endoapp.app.data.entities.FactorReading
import com.endoapp.app.data.entities.PainPoint
import com.endoapp.app.data.entities.SymptomReading
import com.endoapp.app.insightsbridge.FactorKind
import com.endoapp.insights.ActivityIntensity
import com.endoapp.insights.ActivityType
import com.endoapp.insights.BodyRegion
import com.endoapp.insights.FlowIntensity
import com.endoapp.insights.FoodTag
import com.endoapp.insights.SleepQuality
import com.endoapp.insights.SymptomType

/**
 * The stable row bundle a [DayDraft] maps to — the anchor [DayEntry] plus the day-scoped event rows.
 * The lists are the exact rows [EndoRepository][com.endoapp.app.data.EndoRepository] upserts for the
 * day; an untouched field contributes NO row (never a phantom zero-valued one).
 */
data class DayLogRows(
    val dayEntry: DayEntry,
    val symptoms: List<SymptomReading>,
    val pain: List<PainPoint>,
    val bleeding: List<BleedingEvent>,
    val factors: List<FactorReading>,
)

/**
 * The pure [DayDraft] ↔ [DayLogRows] mapper. [toRows] is the write direction (draft → persistable
 * rows); [fromRows] the read direction (reloaded rows → draft), decoding with the SAME defensive
 * rules as [com.endoapp.app.insightsbridge.SnapshotProjector] while preserving nil-vs-0 exactly.
 */
object DayDraftMapper {

    /**
     * Map [draft] to the day's persistable rows for [epochDay]. [idFor] supplies a stable id for a
     * given row key so re-writing the same day upserts (idempotent) rather than duplicating. The
     * anchor [DayEntry] is ALWAYS emitted; every other row is emitted ONLY for a touched field.
     */
    fun toRows(epochDay: Long, draft: DayDraft, idFor: (String) -> String): DayLogRows {
        val symptoms = draft.symptoms.map { (type, severity) ->
            SymptomReading(
                id = idFor("symptom:${type.raw}"),
                epochDay = epochDay,
                typeRaw = type.raw,
                severity = severity, // nil stays nil; 0 stays a real 0. NEVER `?: 0`.
            )
        }

        val pain = draft.pain.map { (region, severity) ->
            PainPoint(
                id = idFor("pain:${region.raw}"),
                epochDay = epochDay,
                regionRaw = region.raw,
                severity = severity,
            )
        }

        // Bleeding: a nil flow clears the day's bleeding (no row). clots rides the flow-bearing row.
        val bleeding = draft.flow?.let { flow ->
            listOf(
                BleedingEvent(
                    id = idFor("bleeding"),
                    epochDay = epochDay,
                    flowRaw = flow.raw,
                    clots = draft.clots,
                ),
            )
        }.orEmpty()

        val factors = buildList {
            // Food tags — one row per tag (valueRaw = tag.raw, no note). Untouched → nothing.
            draft.foodTags.forEach { tag ->
                add(
                    FactorReading(
                        id = idFor("food:tag:${tag.raw}"),
                        epochDay = epochDay,
                        kindRaw = FactorKind.FOOD.raw,
                        valueRaw = tag.raw,
                    ),
                )
            }
            // Food note — a separate note-bearing .food row (valueRaw null). Absent note → nothing.
            draft.foodNote?.let { note ->
                add(
                    FactorReading(
                        id = idFor("food:note"),
                        epochDay = epochDay,
                        kindRaw = FactorKind.FOOD.raw,
                        note = note,
                    ),
                )
            }
            // Activity — a nil type = not logged (minutes + intensity are then also nil ⇒ no row).
            draft.activityType?.let { type ->
                add(
                    FactorReading(
                        id = idFor("activity"),
                        epochDay = epochDay,
                        kindRaw = FactorKind.ACTIVITY.raw,
                        valueRaw = type.raw,
                        amount = draft.activityMinutes, // 0.0 with a type = real 0; nil stays nil.
                        level = levelFor(draft.activityIntensity),
                    ),
                )
            }
            // Sleep — emitted when hours are set OR a quality is chosen. amount is null unless
            // `sleepHoursSet` (so 0.0h-with-set is a real 0, distinct from hours-not-logged).
            if (draft.sleepHoursSet || draft.sleepQuality != null) {
                add(
                    FactorReading(
                        id = idFor("sleep"),
                        epochDay = epochDay,
                        kindRaw = FactorKind.SLEEP.raw,
                        valueRaw = draft.sleepQuality?.raw,
                        amount = if (draft.sleepHoursSet) draft.sleepHours else null,
                    ),
                )
            }
            // Medication — one row per taken saved-med id; the as-needed flag is the "prn" sentinel.
            draft.medsTaken.forEach { medId ->
                add(
                    FactorReading(
                        id = idFor("med:$medId"),
                        epochDay = epochDay,
                        kindRaw = FactorKind.MEDICATION.raw,
                        valueRaw = medId,
                    ),
                )
            }
            if (draft.tookPRN) {
                add(
                    FactorReading(
                        id = idFor("med:prn"),
                        epochDay = epochDay,
                        kindRaw = FactorKind.MEDICATION.raw,
                        valueRaw = PRN_SENTINEL,
                    ),
                )
            }
            // Stress — not opted in ⇒ no row (nil); opted in with 0 ⇒ a real 0 (level = 0).
            draft.stress?.let { stress ->
                add(
                    FactorReading(
                        id = idFor("stress"),
                        epochDay = epochDay,
                        kindRaw = FactorKind.STRESS.raw,
                        level = stress,
                    ),
                )
            }
            // Mood — nil ⇒ no row; 1..5 a real value.
            draft.mood?.let { mood ->
                add(
                    FactorReading(
                        id = idFor("mood"),
                        epochDay = epochDay,
                        kindRaw = FactorKind.MOOD.raw,
                        level = mood,
                    ),
                )
            }
        }

        return DayLogRows(
            dayEntry = DayEntry(id = idFor("dayEntry"), epochDay = epochDay, note = draft.note),
            symptoms = symptoms,
            pain = pain,
            bleeding = bleeding,
            factors = factors,
        )
    }

    /**
     * Decode reloaded [rows] back into a [DayDraft], preserving nil-vs-0 exactly. Uses the same
     * defensive decode as the SnapshotProjector: a symptom/region whose raw does not decode is
     * DROPPED (never coerced to a fabricated value); a nil severity stays nil.
     */
    fun fromRows(rows: DayLogRows): DayDraft {
        val symptoms: Map<SymptomType, Int?> = rows.symptoms.mapNotNull { r ->
            val type = SymptomType.entries.firstOrNull { it.raw == r.typeRaw } ?: return@mapNotNull null
            type to r.severity
        }.toMap()

        val pain: Map<BodyRegion, Int?> = rows.pain.mapNotNull { p ->
            val region = BodyRegion.fromRawOrNull(p.regionRaw) ?: return@mapNotNull null
            region to p.severity
        }.toMap()

        val flowEvent = rows.bleeding.firstOrNull { FlowIntensity.fromRawOrNull(it.flowRaw) != null }
        val flow = FlowIntensity.fromRawOrNull(flowEvent?.flowRaw)
        val clots = flowEvent?.clots

        val foodTags = mutableSetOf<FoodTag>()
        var foodNote: String? = null
        var activityType: ActivityType? = null
        var activityMinutes: Double? = null
        var activityIntensity: ActivityIntensity? = null
        var sleepHours: Double? = null
        var sleepHoursSet = false
        var sleepQuality: SleepQuality? = null
        val medsTaken = mutableSetOf<String>()
        var tookPRN = false
        var stress: Int? = null
        var mood: Int? = null

        for (reading in rows.factors) {
            when (FactorKind.fromRaw(reading.kindRaw)) {
                FactorKind.FOOD -> when {
                    reading.note != null -> foodNote = reading.note
                    reading.valueRaw != null ->
                        FoodTag.entries.firstOrNull { it.raw == reading.valueRaw }?.let(foodTags::add)
                }
                FactorKind.ACTIVITY -> {
                    activityType = ActivityType.entries.firstOrNull { it.raw == reading.valueRaw }
                    activityMinutes = reading.amount
                    activityIntensity = intensityFor(reading.level)
                }
                FactorKind.SLEEP -> {
                    sleepQuality = SleepQuality.fromRawOrNull(reading.valueRaw)
                    sleepHours = reading.amount
                    sleepHoursSet = reading.amount != null
                }
                FactorKind.MEDICATION -> {
                    if (reading.valueRaw == PRN_SENTINEL) tookPRN = true
                    else reading.valueRaw?.let(medsTaken::add)
                }
                FactorKind.STRESS -> stress = reading.level
                FactorKind.MOOD -> mood = reading.level
                FactorKind.OTHER -> Unit
            }
        }

        return DayDraft(
            symptoms = symptoms,
            pain = pain,
            flow = flow,
            clots = clots,
            foodTags = foodTags,
            foodNote = foodNote,
            activityType = activityType,
            activityMinutes = activityMinutes,
            activityIntensity = activityIntensity,
            sleepHours = sleepHours,
            sleepHoursSet = sleepHoursSet,
            sleepQuality = sleepQuality,
            medsTaken = medsTaken,
            tookPRN = tookPRN,
            stress = stress,
            mood = mood,
            note = rows.dayEntry.note,
        )
    }

    /** The as-needed medication sentinel (mirrors the SnapshotProjector `"prn"` marker). */
    const val PRN_SENTINEL: String = "prn"

    /** [ActivityIntensity] → stored `level` (0 = low, 1 = medium, 2 = high). Mirrors SnapshotProjector. */
    private fun levelFor(intensity: ActivityIntensity?): Int? = when (intensity) {
        ActivityIntensity.LOW -> 0
        ActivityIntensity.MEDIUM -> 1
        ActivityIntensity.HIGH -> 2
        null -> null
    }

    /** Stored `level` → [ActivityIntensity] (0 = low, 1 = medium, 2 = high; else null). */
    private fun intensityFor(level: Int?): ActivityIntensity? = when (level) {
        0 -> ActivityIntensity.LOW
        1 -> ActivityIntensity.MEDIUM
        2 -> ActivityIntensity.HIGH
        else -> null
    }
}
