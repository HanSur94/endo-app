// EndoInsights (:insights) — the honesty choke-point for chart scrubbing.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/ChartScrubSelection.swift`. Pure-JVM
// (no android./androidx.; `java.time` only). Position-based chart-selection APIs interpolate by default:
// a finger between two logged days would otherwise read a fabricated in-between value, lying to the user
// about their own health data. This resolver is the ONLY legal path from a raw gesture day to a
// displayed value:
//   • It NEVER interpolates and NEVER snaps to the nearest logged day — the match is the EXACT calendar
//     day under the finger, or nothing.
//   • An unlogged day resolves to `Resolution(day, point = null)` — the explicit "Not logged" gap,
//     never a zero.
//   • A literal logged severity-0 day resolves to a REAL point with value 0 (absence ≠ 0).
//
// Day identity is `LocalDate` (the injected Swift `Calendar` is DROPPED — `startOfDay` is a no-op and
// `Calendar.isDate(_:inSameDayAs:)` becomes `LocalDate ==`). Value-in / value-out, provable in unit
// tests without rendering.
package com.endoapp.insights

import java.time.LocalDate

/** Resolves a raw chart-selection day to the EXACT logged calendar day — or an explicit gap. NEVER
 *  interpolates, NEVER snaps to the nearest logged day (the honesty choke-point). */
object ChartScrubSelection {

    /** The outcome of resolving a raw scrub day against a chart's logged points. */
    data class Resolution(
        /** The calendar day under the finger — always real (the date axis is real). */
        val day: LocalDate,
        /** The logged point on EXACTLY that day, or null = "Not logged" (absence ≠ 0;
         *  a literal severity-0 point is returned as a real point, value 0). */
        val point: InsightPoint?,
    )

    /** Resolve `rawDate` to the exact logged day under the finger, or the explicit gap state.
     *
     *  @param rawDate the raw selection day from the chart gesture; `null` = no active selection.
     *  @param points the chart's logged-day points (one per day; order-independent).
     *  @return `null` when there is no selection; otherwise the day plus that day's exact point — or
     *          `point == null` for an unlogged day (the honest gap, never a neighbor). */
    fun resolve(rawDate: LocalDate?, points: List<InsightPoint>): Resolution? {
        val day = rawDate ?: return null
        // `startOfDay` is a no-op under LocalDate; exact-day match is `==`, never nearest-neighbour.
        val match = points.firstOrNull { it.date == day }
        return Resolution(day = day, point = match)
    }
}
