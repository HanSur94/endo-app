// EndoInsights (:insights) — pure scatter pairing.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/ScatterPairing.swift`. Pure-JVM
// (no android./androidx.; `java.time`/`kotlin.*` only). THE both-logged-day honesty move of the scatter
// view. A scatter point is a day where BOTH chosen factors were LOGGED — nothing else:
//
//   • both-logged-only: a day missing EITHER factor produces NO pair. The day is NEVER imputed, NEVER
//     0-substituted (`?? 0`/lastKnown is a lie about the user's own health data). Bleeding suppression
//     already lives inside `quantitativeValue(from)`, so a suppressed bleeding axis contributes nothing.
//
//   • count-encoded, never jittered: identical `(x, y)` day-pairs aggregate into ONE `ScatterPoint`
//     whose `count` is a FACTUAL tally of how many days landed on that exact cell. We NEVER add jitter
//     to spread overlapping points — that would fabricate fractional values the user never logged.
//
//   • the wellness-vs-medical-device line: `ScatterPoint` carries ONLY x / y / count — NO fitted line,
//     NO r-value, NO coefficient, NO regression. The layer emits a factual tally, never a statistic.
//     This is enforced by the type's shape, not a runtime check.
//
// Scatter axes are QUANTITATIVE-ONLY: a presence factor has no numeric value, so it cannot be a scatter
// axis. Enforced at the API boundary by a kind guard that GRACEFULLY DEGRADES to an empty result on a
// wrong-kind axis (a library returns empty, never traps a release build).
package com.endoapp.insights

/** The pure scatter-pairing namespace. The single legal path from `List<DaySnapshot>` to the
 *  both-logged scatter cells. */
object ScatterPairing {

    /** One scatter cell: an exact `(x, y)` value pair plus how many days landed on it.
     *
     *  `count` is a FACTUAL tally — the number of distinct logged days at this exact `(x, y)` — NOT a
     *  statistic. There is deliberately NO fitted line, NO r-value, NO coefficient, NO slope on this
     *  type: the scatter layer reports what the user logged, never a derived correlation claim. */
    data class ScatterPoint(
        /** The x-axis value (a real logged value on a quantitative axis — never imputed). */
        val x: Double,
        /** The y-axis value (a real logged value on a quantitative axis — never imputed). */
        val y: Double,
        /** How many days were logged at EXACTLY this `(x, y)` — a factual tally, never jitter. */
        val count: Int,
    )

    /** Pair two quantitative series into both-logged-day scatter cells with count-aggregation.
     *
     *  A day where EITHER factor is unlogged (`quantitativeValue(from) == null`) contributes NO pair —
     *  never imputed, never 0-substituted. A logged literal 0 on both axes IS a real pair (the accessor
     *  returns `0.0`, never dropped). Identical `(x, y)` days aggregate into ONE `ScatterPoint` with
     *  `count` incremented — NEVER jittered. Bleeding suppression carries through from the accessor.
     *  Output is sorted by `(x, y)` for deterministic fixtures.
     *
     *  BOTH axes MUST be quantitative — a presence factor has no numeric value and cannot be a scatter
     *  axis. A misrouted (wrong-kind) axis DEGRADES GRACEFULLY to an empty result rather than trapping. */
    fun pairs(snapshots: List<DaySnapshot>, x: ExploreSeries, y: ExploreSeries): List<ScatterPoint> {
        // Wrong-kind (presence) axis → no numeric value to pair. Degrade to [] rather than trap.
        if (!x.scaleKind.isQuantitative || !y.scaleKind.isQuantitative) return emptyList()

        // Bit-pattern-exact key: integer-valued ordinals (0…10, 1…5, 1…4, continuous hours/minutes)
        // aggregate exactly. NO jitter, NO rounding, NO fractional fabrication.
        val counts = HashMap<PairKey, Int>()
        for (snap in snapshots) {
            // EITHER factor null → the day is excluded, NEVER imputed/0-substituted. The bleeding
            // suppression gate already lives inside quantitativeValue and carries through here.
            val xv = x.quantitativeValue(from = snap) ?: continue
            val yv = y.quantitativeValue(from = snap) ?: continue
            val key = PairKey(xv, yv)
            counts[key] = (counts[key] ?: 0) + 1
        }

        return counts
            .map { (key, count) -> ScatterPoint(x = key.x, y = key.y, count = count) }
            .sortedWith(compareBy({ it.x }, { it.y }))
    }

    /** Aggregation key for identical `(x, y)` day-pairs — hashed by the exact value pair so identical
     *  logged ordinals collapse to ONE cell (count-encoded). No jitter, no rounding.
     *
     *  Exact-`Double` keying is correct ONLY while axis values are VERBATIM logged integers/doubles —
     *  `Double(Int)` ordinals or user-logged hours/minutes carried with NO intervening arithmetic. A
     *  future axis that COMPUTES/scales/rounds a value before keying MUST quantize to a canonical key. */
    private data class PairKey(val x: Double, val y: Double)
}
