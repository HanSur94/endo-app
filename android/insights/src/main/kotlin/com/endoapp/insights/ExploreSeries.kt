// EndoInsights (:insights) — series catalog + scale taxonomy.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/ExploreSeries.swift`. Pure-JVM value types
// (no android./androidx.; `java.time`/`kotlin.*` only). THE keystone of the chart data-prep layer: the
// single source of truth for "what series exist" that both the Explore chips and the Scatter axis
// pickers (Phase 22 `:app`) read. Every downstream helper (extraction, scatter pairing, normalization)
// composes on top of this file.
//
// HONESTY (encoded in the type system, not runtime checks):
//   • `ScaleKind` makes the quantitative-vs-presence distinction explicit, so a presence series can
//     NEVER be coerced into a 0/1 quantitative value (the structural move).
//   • `quantitativeValue(from)` returns `null` for an unlogged field (gap, absence ≠ 0) and a REAL
//     value for a logged literal 0 — via `snap.x?.toDouble()`, NEVER a `> 0`/truthiness filter.
//     Bleeding-derived series gate on `!snap.suppressed` (no leak).
//   • `ExploreSeries` derives equals/hashCode from `id` ALONE — NO stored extraction closure — so a
//     `Set<ExploreSeries>` chip selection collapses on id.
//
// The catalog is enumerated FROM the existing enum domains (`SymptomType`/`BodyRegion`/`FoodTag`
// `entries`) so it cannot drift from them by construction. Day math is `LocalDate` identity in the
// EXTRACTION helper; the accessors here are value-only — NEVER epoch arithmetic.
package com.endoapp.insights

/** The explicit scale taxonomy for every catalogued series. Modelling the kind explicitly is the
 *  structural honesty move: a `.PRESENCE_EVENT` series has no numeric value, so "coerce a tag to 0/1"
 *  is unrepresentable on the quantitative path.
 *
 *  The `raw: String` mirrors the Swift `enum ScaleKind: String` case names verbatim. */
enum class ScaleKind(val raw: String) {
    /** Symptom/pain severity, stress — a 0…10 ordinal. */
    ORDINAL_0_TO_10("ordinal0to10"),
    /** Day mood — a 1…5 ordinal. */
    ORDINAL_1_TO_5("ordinal1to5"),
    /** Sleep duration — continuous hours. */
    CONTINUOUS_HOURS("continuousHours"),
    /** Activity duration — continuous minutes. */
    CONTINUOUS_MINUTES("continuousMinutes"),
    /** Bleeding intensity — `FlowIntensity` spotting…heavy mapped to a 1…4 ordinal rank. */
    ORDINAL_BLEEDING("ordinalBleeding"),
    /** Food tags, clots, PRN, medication-taken, bleeding-as-events — presence/occurrence only. */
    PRESENCE_EVENT("presenceEvent");

    /** `true` for every kind EXCEPT `.PRESENCE_EVENT`. Presence series route via `isPresent(in)`
     *  (→ `[LocalDate]` occurrence days downstream), never `quantitativeValue(from)`. */
    val isQuantitative: Boolean get() = this != PRESENCE_EVENT
}

/** The stored discriminator the extraction helper switches on.
 *
 *  A stored ENUM (sealed interface), NOT a stored closure — so `ExploreSeries` keeps a Hashable payload
 *  and the type's id-only conformances stay valid. Payload cases carry the sub-series that varies (per
 *  symptom type, per pain region, per food tag); the singleton fields carry none. */
sealed interface ExploreSeriesField {
    data class SymptomSeverity(val type: SymptomType) : ExploreSeriesField
    data class PainSeverity(val region: BodyRegion) : ExploreSeriesField
    data object Stress : ExploreSeriesField
    data object Mood : ExploreSeriesField
    data object SleepHours : ExploreSeriesField
    data object ActivityMinutes : ExploreSeriesField
    data object BleedingIntensity : ExploreSeriesField
    data object BleedingEvent : ExploreSeriesField
    data class Food(val tag: FoodTag) : ExploreSeriesField
    data object Clots : ExploreSeriesField
    data object Prn : ExploreSeriesField
    data object MedicationTaken : ExploreSeriesField
}

/** One catalogued series: a stable value-independent id + display/scale metadata + the extraction
 *  discriminator. equals/hashCode derive from `id` ALONE (no stored closure) so a `Set<ExploreSeries>`
 *  chip selection collapses on id. */
class ExploreSeries(
    /** Stable, value-independent key (mirrors `Insight.signalKey` dotted style): e.g.
     *  `"symptom.pelvicPain.severity"`, `"pain.lowerBack.severity"`, `"sleep.hours"`, `"stress"`,
     *  `"mood"`, `"bleeding.intensity"`, `"bleeding.event"`, `"food.dairy"`, `"clots"`, `"prn"`,
     *  `"medication.taken"`. */
    val id: String,
    /** Chrome copy — the descriptive default (Phase 22 routes display copy through its own catalog). */
    val displayName: String,
    /** Honest readout unit: `"of 10"`, `"of 5"`, `"h"`, `"min"`, or `""` (presence / bleeding rank). */
    val unit: String,
    /** The explicit scale taxonomy entry. */
    val scaleKind: ScaleKind,
    /** The closure-free extraction discriminator the accessors switch on. */
    val field: ExploreSeriesField,
) {
    // MARK: id-only conformances (NO stored closure participates)

    /** Two series are equal iff their stable ids match — no other stored field participates. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExploreSeries) return false
        return id == other.id
    }

    /** Hash the stable id ONLY — collapses two series with identical ids and keeps
     *  `Set<ExploreSeries>` count == catalog count. */
    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "ExploreSeries(id=$id)"

    // MARK: - Value accessors

    /** The series' quantitative value for a day, or `null` when that field was NOT logged (gap,
     *  absence ≠ 0). A logged literal 0 returns a REAL `0.0` (`snap.x?.toDouble()`, NEVER a `> 0`
     *  truthiness filter). Presence-kind fields return `null` here — they route via `isPresent(in)`.
     *  Bleeding-derived series gate on `!snap.suppressed` first (no leak).
     *
     *  Symptom/pain severity takes the MAX logged severity among same-type/region entries (never a
     *  positional `.first`), so a duplicate-type day can never silently DROP the higher logged severity. */
    fun quantitativeValue(from: DaySnapshot): Double? {
        return when (val f = field) {
            is ExploreSeriesField.SymptomSeverity ->
                // Repository invariant: one entry per type per day. Take max (never positional-first).
                from.symptoms.filter { it.type == f.type }.mapNotNull { it.severity }.maxOrNull()?.toDouble()
            is ExploreSeriesField.PainSeverity ->
                from.pain.filter { it.region == f.region }.mapNotNull { it.severity }.maxOrNull()?.toDouble()
            ExploreSeriesField.Stress -> from.stress?.toDouble()
            ExploreSeriesField.Mood -> from.mood?.toDouble()
            ExploreSeriesField.SleepHours -> from.sleepHours
            ExploreSeriesField.ActivityMinutes -> from.activityMinutes
            ExploreSeriesField.BleedingIntensity -> {
                // Suppression gate FIRST — a suppressed user's bleeding must never produce a value.
                if (from.suppressed) null
                else from.flow?.let { ordinalRank(it).toDouble() }
            }
            ExploreSeriesField.BleedingEvent, is ExploreSeriesField.Food,
            ExploreSeriesField.Clots, ExploreSeriesField.Prn, ExploreSeriesField.MedicationTaken ->
                // Presence-kind fields have no quantitative value — callers route via `isPresent(in)`.
                null
        }
    }

    /** Whether the presence/event thing was LOGGED on a day. Quantitative fields return `false` here —
     *  they route via `quantitativeValue(from)`. Bleeding-as-event gates on `!snap.suppressed`. */
    fun isPresent(snap: DaySnapshot): Boolean {
        return when (val f = field) {
            is ExploreSeriesField.Food -> snap.foodTags.contains(f.tag)
            ExploreSeriesField.Clots -> snap.clots
            ExploreSeriesField.Prn -> snap.tookPRN
            ExploreSeriesField.MedicationTaken -> snap.medicationsTaken.isNotEmpty()
            ExploreSeriesField.BleedingEvent ->
                // Present iff NOT suppressed AND bleeding was logged that day.
                !snap.suppressed && snap.flow != null
            is ExploreSeriesField.SymptomSeverity, is ExploreSeriesField.PainSeverity,
            ExploreSeriesField.Stress, ExploreSeriesField.Mood, ExploreSeriesField.SleepHours,
            ExploreSeriesField.ActivityMinutes, ExploreSeriesField.BleedingIntensity ->
                // Quantitative fields route via `quantitativeValue(from)`, never presence.
                false
        }
    }

    companion object {
        /** The honest ordinal rank of a `FlowIntensity` for the `bleeding.intensity` quantitative series:
         *  spotting=1, light=2, medium=3, heavy=4 — domain 1…4 (`ScaleKind.ORDINAL_BLEEDING`). */
        fun ordinalRank(flow: FlowIntensity): Int = when (flow) {
            FlowIntensity.SPOTTING -> 1
            FlowIntensity.LIGHT -> 2
            FlowIntensity.MEDIUM -> 3
            FlowIntensity.HEAVY -> 4
        }
    }
}

/** The single source of truth for "what series exist". Built by enumerating the existing enum domains
 *  (`SymptomType`/`BodyRegion`/`FoodTag` `entries`) so it stays in sync with them BY CONSTRUCTION — no
 *  hand-maintained case list to drift. Bleeding appears as BOTH a quantitative ordinal series
 *  (`bleeding.intensity`) AND a presence event series (`bleeding.event`), each suppression-gated
 *  downstream. */
object ExploreSeriesCatalog {

    /** Every loggable series, in a stable order: per-symptom severity (11) → per-pain-region severity
     *  (10) → per-food-tag presence (9) → the singleton fields. Ids are unique, dotted, and
     *  value-independent. */
    val all: List<ExploreSeries> = buildList {
        // Per-symptom-type severity — a DISTINCT selectable series each (NOT a lumped "symptoms").
        for (type in SymptomType.entries) {
            add(
                ExploreSeries(
                    id = "symptom.${type.raw}.severity",
                    displayName = "${type.raw} severity",
                    unit = "of 10",
                    scaleKind = ScaleKind.ORDINAL_0_TO_10,
                    field = ExploreSeriesField.SymptomSeverity(type),
                ),
            )
        }

        // Per-pain-region severity — a DISTINCT selectable series each.
        for (region in BodyRegion.entries) {
            add(
                ExploreSeries(
                    id = "pain.${region.raw}.severity",
                    displayName = "${region.raw} pain severity",
                    unit = "of 10",
                    scaleKind = ScaleKind.ORDINAL_0_TO_10,
                    field = ExploreSeriesField.PainSeverity(region),
                ),
            )
        }

        // Per-food-tag presence/occurrence.
        for (tag in FoodTag.entries) {
            add(
                ExploreSeries(
                    id = "food.${tag.raw}",
                    displayName = tag.raw,
                    unit = "",
                    scaleKind = ScaleKind.PRESENCE_EVENT,
                    field = ExploreSeriesField.Food(tag),
                ),
            )
        }

        // Singleton fields.
        add(
            ExploreSeries(
                id = "sleep.hours", displayName = "Sleep", unit = "h",
                scaleKind = ScaleKind.CONTINUOUS_HOURS, field = ExploreSeriesField.SleepHours,
            ),
        )
        add(
            ExploreSeries(
                id = "activity.minutes", displayName = "Activity", unit = "min",
                scaleKind = ScaleKind.CONTINUOUS_MINUTES, field = ExploreSeriesField.ActivityMinutes,
            ),
        )
        add(
            ExploreSeries(
                id = "stress", displayName = "Stress", unit = "of 10",
                scaleKind = ScaleKind.ORDINAL_0_TO_10, field = ExploreSeriesField.Stress,
            ),
        )
        add(
            ExploreSeries(
                id = "mood", displayName = "Mood", unit = "of 5",
                scaleKind = ScaleKind.ORDINAL_1_TO_5, field = ExploreSeriesField.Mood,
            ),
        )
        add(
            ExploreSeries(
                id = "bleeding.intensity", displayName = "Bleeding intensity", unit = "",
                scaleKind = ScaleKind.ORDINAL_BLEEDING, field = ExploreSeriesField.BleedingIntensity,
            ),
        )
        add(
            ExploreSeries(
                id = "bleeding.event", displayName = "Bleeding", unit = "",
                scaleKind = ScaleKind.PRESENCE_EVENT, field = ExploreSeriesField.BleedingEvent,
            ),
        )
        add(
            ExploreSeries(
                id = "clots", displayName = "Clots", unit = "",
                scaleKind = ScaleKind.PRESENCE_EVENT, field = ExploreSeriesField.Clots,
            ),
        )
        add(
            ExploreSeries(
                id = "prn", displayName = "PRN pain relief", unit = "",
                scaleKind = ScaleKind.PRESENCE_EVENT, field = ExploreSeriesField.Prn,
            ),
        )
        add(
            ExploreSeries(
                id = "medication.taken", displayName = "Medication taken", unit = "",
                scaleKind = ScaleKind.PRESENCE_EVENT, field = ExploreSeriesField.MedicationTaken,
            ),
        )
    }
}
