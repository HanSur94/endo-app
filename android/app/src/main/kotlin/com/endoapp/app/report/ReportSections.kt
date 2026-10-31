// Requirement: AREL-01 / AREL-02 (Phase 24, D-04) — the PURE, Equatable section value types the report
// model assembles, plus the per-section provenance label. Kotlin port of the section structs in the iOS
// `Sources/EndoApp/Features/Report/ReportModel.swift`.
//
// These types carry NO rendering (Plan 05 renders them) and NO copy (the fixed strings live in
// `ReportCopy`). They are flat, `data class` / sealed-class value types the unit tests assert against —
// the honesty + source-label boundary of the doctor-ready PDF, decoupled from "does it draw?".
//
// [SectionSource] is defined HERE (not in `ReportModel.kt`) so `ReportCopy.sourceTag` can import it
// without pulling in the assembly function.
package com.endoapp.app.report

import com.endoapp.app.data.ValueSource
import com.endoapp.insights.ActivityType
import com.endoapp.insights.InsightConfidence
import com.endoapp.insights.InsightPoint
import com.endoapp.insights.SleepQuality
import com.endoapp.insights.SymptomType
import java.time.LocalDate

// MARK: - Source provenance (AREL-02)

/**
 * The aggregated, per-section provenance label (AREL-02). Derived ONLY from the Plan-01
 * `DaySnapshotSource` merge gate — NEVER recomputed from values. [NONE] = no provenance present for the
 * section across the range → no badge (honesty extends to provenance).
 *
 * Rendered as glyph + word (never color) by Plan 05: "Self-reported" / "from Health Connect" / "Mixed".
 * A clinician printing grayscale MUST distinguish them by shape + word, never hue.
 */
enum class SectionSource {
    SELF_REPORTED,
    HEALTH_CONNECT,
    MIXED,
    NONE,
    ;

    companion object {
        /**
         * Aggregate a section's per-day [ValueSource]? provenance into a single label. The PURE honesty
         * rule: an empty present-set → [NONE]; both manual and Health Connect present → [MIXED]; else
         * all-manual → [SELF_REPORTED], all-imported → [HEALTH_CONNECT]. NEVER recomputed from values.
         */
        fun aggregate(sources: List<ValueSource?>): SectionSource {
            val present = sources.filterNotNull()
            if (present.isEmpty()) return NONE // no badge — honesty
            val manual = present.contains(ValueSource.MANUAL)
            val health = present.contains(ValueSource.HEALTH_CONNECT)
            if (manual && health) return MIXED
            return if (manual) SELF_REPORTED else HEALTH_CONNECT
        }
    }
}

// MARK: - Sections (D-04 order)

/**
 * Section 1 — Header: the exact covered range + the assembly stamp. The disclaimer PROSE is NOT carried
 * here (Plan 05 reuses `Disclaimer.text` verbatim, never retyped); the model carries only the structured
 * facts the header renders around it.
 */
data class HeaderSection(
    val range: ReportRange,
    val generatedOn: LocalDate,
)

/**
 * Section 2 — Summary / coverage: an honest count of logged days in range + the sources present.
 * `daysLogged == 0` is the honest "nothing in this range" state, never a fabricated coverage line.
 */
data class SummarySection(
    val daysLogged: Int,
    val source: SectionSource,
)

/**
 * One symptom's descriptive trend row over the range (absolute, never cycle-relative). [minSeverity] /
 * [maxSeverity] are `null` when the symptom was logged WITHOUT a severity (absence ≠ 0). [points] is the
 * REAL per-day series (one point per day-with-a-logged-severity, at that day's max), sorted ascending —
 * gaps stay gaps, never interpolated.
 */
data class SymptomRow(
    val type: SymptomType,
    val minSeverity: Int?,
    val maxSeverity: Int?,
    val daysLogged: Int,
    val points: List<InsightPoint>,
)

/**
 * Section 3 — Symptoms & pain: the descriptive trend table + chart points. When the range is
 * sub-threshold the section is honestly EMPTY (`rows.isEmpty && hasEnoughData == false`) → the renderer
 * maps it to "Not enough data in this range." — never a fabricated value.
 */
data class SymptomsSection(
    val rows: List<SymptomRow>,
    val hasEnoughData: Boolean,
    val source: SectionSource,
)

/**
 * Section 4 — Cycle & bleeding. A THREE-STATE type mirroring `CycleSummary` EXACTLY (the no-phantom-cycle
 * contract): a suppressed user is [Suppressed] (off-state, NOTHING cycle-derivable); a sub-threshold user
 * is [NotEnoughData]; only a meaningful history is [Derived] with the optional cycle-day, the next-window
 * date RANGE (never a single date), and the plain confidence word.
 */
sealed interface CycleSection {
    /** Suppressed / no-cycle mode — the off-state. NO cycle-day, window, or phase. */
    data object Suppressed : CycleSection

    /** Fewer than 2 recorded cycles — the honest "not enough data" state. */
    data object NotEnoughData : CycleSection

    /** A meaningful derived estimate (mirrors `CycleSummary`'s derived fields). */
    data class Derived(
        val cycleDay: Int?,
        val nextWindow: ClosedRange<LocalDate>?,
        val confidence: InsightConfidence,
    ) : CycleSection
}

/**
 * Section 5 — Lifestyle factors over the range. EVERY field is NULLABLE so absence ≠ 0: a `null` field
 * renders "Not logged"; a logged 0/level reads as a real value. Each field is the LATEST logged value in
 * range.
 */
data class LifestyleSection(
    val sleepHours: Double?,
    val sleepQuality: SleepQuality?,
    val activityType: ActivityType?,
    val activityMinutes: Double?,
    val loggedFood: Boolean,
    val stress: Int?,
    val mood: Int?,
    val source: SectionSource,
)

/**
 * Section 7 — Source legend. A structured marker of which sources actually appear in the report, so the
 * renderer explains only the labels in use (glyph + text, never color).
 */
data class SourceLegend(
    val hasSelfReported: Boolean,
    val hasHealthConnect: Boolean,
)
