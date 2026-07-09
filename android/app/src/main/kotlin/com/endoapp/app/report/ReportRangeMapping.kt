// Requirement: AREL-01 (Phase 24, A1) — the range-preset → interval + custom-range → engine-window
// mapping, plus the invalid-range guard. Kotlin port of the iOS
// `Sources/EndoApp/Features/Report/ReportRangeMapping.swift`.
//
// THE A1 decision made code: the `:insights` engine is window-based (7/30/90/all-time, anchored at
// `asOf`) and has NO range parameter. So the report:
//   • resolves a [RangePreset] to a concrete [ReportRange] (descriptive sections honor it EXACTLY), and
//   • for the Insights section, surfaces ONLY the engine windows whose span is a SUBSET of the chosen
//     range ([enginewindowsInRange]) so a custom range like 46 days shows e.g. the "last 30 days" window
//     labeled honestly — never overstating coverage (a window WIDER than the range is excluded).
//
// PURE / DST-safe: no store, no UI, no clock. `java.time.LocalDate` is day-granular, so the intervals are
// computed with `LocalDate.minusDays` / `ChronoUnit.DAYS.between` — never epoch-millis arithmetic (mirrors
// `CycleMath` and the Repository's day-bucketing).
package com.endoapp.app.report

import com.endoapp.insights.TrendWindow
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A concrete date range the report covers. The Kotlin stand-in for the iOS `DateInterval`, but INCLUSIVE
 * of [end]'s calendar day — a report "through today" must include today's logs. `LocalDate` is already
 * day-granular, so no start-of-day normalization is needed (unlike the iOS wall-clock `Date`).
 */
data class ReportRange(val start: LocalDate, val end: LocalDate)

/** The date-range presets the builder offers. [CUSTOM] carries two user-picked dates. */
enum class RangePreset {
    LAST30,
    LAST90,
    CUSTOM,
}

/**
 * Pure range mapping (A1). Value-in / value-out so it is unit-testable and reusable by both the builder
 * (Plan 05 UI) and the report model.
 */
object ReportRangeMapping {

    /**
     * The day-span of each engine [TrendWindow] (used to decide which windows fit inside a range).
     * [TrendWindow.ALL_TIME] has no fixed span — it is `null` and handled explicitly in
     * [enginewindowsInRange].
     */
    fun windowDays(window: TrendWindow): Int? = when (window) {
        TrendWindow.LAST7 -> 7
        TrendWindow.LAST30 -> 30
        TrendWindow.LAST90 -> 90
        TrendWindow.ALL_TIME -> null
    }

    /**
     * Resolve a preset to a concrete [ReportRange] (A1). [RangePreset.LAST30]/[RangePreset.LAST90] measure
     * back from [asOf] (so `asOf == range.end` makes them map cleanly onto the engine's same-named
     * windows). [RangePreset.CUSTOM] spans `min(from, to) … max(from, to)` — the EXACT range the
     * descriptive sections honor. `from`/`to` are ignored for the non-custom presets.
     */
    fun interval(preset: RangePreset, from: LocalDate, to: LocalDate, asOf: LocalDate): ReportRange =
        when (preset) {
            RangePreset.LAST30 -> ReportRange(start = asOf.minusDays(30), end = asOf)
            RangePreset.LAST90 -> ReportRange(start = asOf.minusDays(90), end = asOf)
            RangePreset.CUSTOM -> {
                // Defensive: never construct an inverted range (the guard below disables Generate first).
                val lo = if (from <= to) from else to
                val hi = if (from <= to) to else from
                ReportRange(start = lo, end = hi)
            }
        }

    /**
     * The A1 helper: the engine windows whose span is a SUBSET of [range] (so the Insights section labels
     * only windows the range can honestly support). A window WIDER than the range is EXCLUDED (e.g. a
     * 46-day range yields [TrendWindow.LAST7] + [TrendWindow.LAST30], never [TrendWindow.LAST90]).
     * [TrendWindow.ALL_TIME] is included because, once the snapshots are filtered to the range, "all time"
     * IS the range — it can never overstate it. Returned widest-first (descending span) so the renderer
     * can prefer the broadest fitting window.
     */
    fun enginewindowsInRange(range: ReportRange): List<TrendWindow> {
        // The range's day-span, INCLUSIVE of the end day (mirrors ReportModel's inclusive-end posture).
        val spanDays = ChronoUnit.DAYS.between(range.start, range.end).toInt() + 1

        return TrendWindow.entries
            .filter { window ->
                val days = windowDays(window) ?: return@filter true // ALL_TIME ⊆ the range by definition
                days <= spanDays
            }
            // Widest-first: larger fixed span first; ALL_TIME (no fixed span → -1) sorts last.
            .sortedByDescending { windowDays(it) ?: -1 }
    }

    /**
     * The invalid-range guard (drives the Generate-disabled state in the builder). Only the custom path can
     * be invalid — a custom `to` that precedes `from` is rejected; the presets are always valid.
     */
    fun isValid(preset: RangePreset, from: LocalDate, to: LocalDate): Boolean =
        if (preset != RangePreset.CUSTOM) true else from <= to
}
