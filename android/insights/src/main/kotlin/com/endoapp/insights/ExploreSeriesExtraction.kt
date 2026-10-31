// EndoInsights (:insights) — pure series extraction.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/ExploreSeriesExtraction.swift`. Pure-JVM
// (no android./androidx.; `java.time`/`kotlin.*` only). THE structural honesty move: TWO kind-gated
// entry points returning TWO DIFFERENT types.
//
//   • `quantitativePoints(...) -> List<InsightPoint>` — the QUANTITATIVE path. A `mapNotNull` over the
//     snapshots: an unlogged field (`quantitativeValue(from) == null`) emits NO point for that day (a
//     gap — absence ≠ 0, NEVER a fabricated 0); a logged literal 0 emits a REAL `InsightPoint` at value
//     0 (the accessor returns `0.0`, never dropped).
//
//   • `occurrenceDays(...) -> List<LocalDate>` — the PRESENCE/EVENT path. A DISTINCT return type with
//     NO `value` field, so "coerce a tag to a 0/1 value" is UNREPRESENTABLE in the type system — this
//     success criterion is enforced by CONSTRUCTION, not by a runtime check.
//
// Both bleeding-derived series gate on `!snap.suppressed` inside the accessors, so a suppressed user's
// bleeding cannot leak: zero points AND zero occurrences.
//
// Day identity is `LocalDate` (the injected Swift `Calendar` + `startOfDay` are DROPPED — LocalDate is
// already a day). Pure value-in / value-out, provable in unit tests without rendering.
package com.endoapp.insights

/** The pure extraction namespace. Two kind-gated entry points; the quantitative path returns
 *  `List<InsightPoint>`, the presence path returns `List<LocalDate>` (no value field, so 0/1 coercion
 *  is structurally unrepresentable). */
object ExploreSeriesExtraction {

    /** Extract the quantitative series as logged-day `InsightPoint`s. `mapNotNull` skips an unlogged day
     *  (`quantitativeValue(from) == null`) → a gap (absence ≠ 0, never a fabricated 0); a logged literal
     *  0 is kept as a REAL point at value 0. Suppressed bleeding yields no points.
     *
     *  `series.scaleKind.isQuantitative` MUST hold — presence series route via `occurrenceDays`. A
     *  misrouted presence series DEGRADES GRACEFULLY to `[]` rather than trapping: a routing bug in a
     *  privacy-first, must-not-crash health app must yield "no data", never a release crash. */
    fun quantitativePoints(snapshots: List<DaySnapshot>, series: ExploreSeries): List<InsightPoint> {
        // Wrong-kind (presence) series → no quantitative value. Degrade to [] rather than trap.
        if (!series.scaleKind.isQuantitative) return emptyList()
        return snapshots.mapNotNull { snap ->
            val value = series.quantitativeValue(from = snap) ?: return@mapNotNull null
            InsightPoint(date = snap.day, value = value)
        }
    }

    /** Extract the presence/event series as occurrence `LocalDate`s (one per logged-present day). This
     *  path has NO value field — so "coerce a tag to a 0 or 1" is unrepresentable (the success
     *  criterion). `mapNotNull` maps a present day → its day, an absent day → null (so a logged day
     *  WITHOUT the thing emits NO occurrence — presence-absent ≠ logged-absent). The `bleeding.event`
     *  accessor already gates on `!snap.suppressed`, so a suppressed user emits nothing.
     *
     *  `series.scaleKind == PRESENCE_EVENT` MUST hold — quantitative series route via
     *  `quantitativePoints`. A misrouted quantitative series DEGRADES GRACEFULLY to `[]` rather than
     *  trapping: a routing bug must yield "no data", never a release crash in a health app. */
    fun occurrenceDays(snapshots: List<DaySnapshot>, series: ExploreSeries): List<java.time.LocalDate> {
        // Wrong-kind (quantitative) series → no presence/occurrence. Degrade to [] rather than trap.
        if (series.scaleKind != ScaleKind.PRESENCE_EVENT) return emptyList()
        return snapshots.mapNotNull { snap ->
            if (series.isPresent(snap)) snap.day else null
        }
    }
}
