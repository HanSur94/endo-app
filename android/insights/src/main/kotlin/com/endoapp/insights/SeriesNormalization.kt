// EndoInsights (:insights) — the presentation-agnostic normalization primitive.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/SeriesNormalization.swift`. Pure-JVM
// (no android./androidx.; `kotlin.*` only). The single source of the FIXED, HONEST per-scale-kind
// domain that the chart layer composes on WITHOUT silently misleading the user:
//   • `domain(for)` returns a FIXED literal range per ScaleKind — NEVER a per-visible-window min/max.
//     A per-window domain would distort a series' shape across window switches. Presence has NO numeric
//     domain (null) — an event lane is not a normalized value.
//   • `normalize` maps a real value onto the unit interval for shape-only overlay; `denormalize` is its
//     exact inverse, so a normalize→denormalize round-trip recovers the real value (within tolerance).
//   • `readout` ALWAYS reports the REAL value + unit — NEVER the normalized [0,1] number (honesty).
//     There is no silent normalization: every value the user READS stays honest.
//
// Pure namespace: value-in / value-out, provable in unit tests without rendering. The domain literals
// are FIXED constants, never computed from data (no min/max/reduce).
package com.endoapp.insights

import kotlin.math.floor

/** The FIXED honest domain + unit-interval normalization + real-unit readout for catalogued series.
 *  Presentation-agnostic: this supplies the primitive only; the chart layer decides lanes-vs-normalize. */
object SeriesNormalization {

    /** The FIXED honest domain for a scale kind, or `null` for `.PRESENCE_EVENT` (an event lane has no
     *  numeric domain). These ranges are FIXED literals per kind — NEVER computed from a visible
     *  window's min/max:
     *    • `ORDINAL_0_TO_10`     → `0…10`  — symptom/pain severity, stress.
     *    • `ORDINAL_1_TO_5`      → `1…5`   — day mood.
     *    • `ORDINAL_BLEEDING`    → `1…4`   — FlowIntensity spotting…heavy ordinal rank.
     *    • `CONTINUOUS_HOURS`    → `0…24`  — honest hour ceiling for sleep duration.
     *    • `CONTINUOUS_MINUTES`  → `0…240` — honest minute ceiling for activity duration.
     *    • `PRESENCE_EVENT`      → `null`  — no numeric domain (presence/occurrence only). */
    fun domain(for_: ScaleKind): ClosedFloatingPointRange<Double>? = when (for_) {
        ScaleKind.ORDINAL_0_TO_10 -> 0.0..10.0
        ScaleKind.ORDINAL_1_TO_5 -> 1.0..5.0
        ScaleKind.ORDINAL_BLEEDING -> 1.0..4.0
        ScaleKind.CONTINUOUS_HOURS -> 0.0..24.0
        ScaleKind.CONTINUOUS_MINUTES -> 0.0..240.0
        ScaleKind.PRESENCE_EVENT -> null
    }

    /** Map a real `value` onto the unit interval `[0,1]` for shape-only overlay, or `null` for a kind
     *  with no numeric domain (`.PRESENCE_EVENT`). Linear against the FIXED domain — so a series' shape
     *  stays stable across window switches. The output is CLAMPED to `[0,1]`: a free user-entered value
     *  above the honest domain ceiling cannot escape the unit interval. The honest real value still
     *  routes through `readout`, so clamping the SHAPE coordinate never lies about the data.
     *
     *  Round-trip nuance: because the output is clamped, `denormalize(normalize(x)) == x` holds ONLY for
     *  `x` WITHIN the domain; for an out-of-domain `x` the clamp is intentionally lossy. */
    fun normalize(value: Double, in_: ScaleKind): Double? {
        val d = domain(for_ = in_) ?: return null
        val t = (value - d.start) / (d.endInclusive - d.start)
        return minOf(maxOf(t, 0.0), 1.0)
    }

    /** The exact inverse of `normalize`: map a `unit`-interval position back to its real value, or `null`
     *  for a kind with no numeric domain. `denormalize(normalize(v)) == v` within float tolerance. */
    fun denormalize(unit: Double, in_: ScaleKind): Double? {
        val d = domain(for_ = in_) ?: return null
        return d.start + unit * (d.endInclusive - d.start)
    }

    /** The honest readout: ALWAYS the REAL `value` + `unit`, NEVER the normalized [0,1] number. An empty
     *  `unit` (presence) yields the bare real value.
     *
     *  A whole-number value renders WITHOUT the trailing `.0` ("7 of 10", not "7.0 of 10"); a genuine
     *  fractional (e.g. 6.5 h) keeps its decimals. This is formatting only — the real logged value is
     *  preserved exactly, never the normalized number. The whole-number check is `value == floor(value)`
     *  (NOT `kotlin.math.round`, which is banker's / ties-to-even and would mis-classify `.5` ties). */
    fun readout(value: Double, kind: ScaleKind, unit: String): String {
        val valueString = if (value == floor(value)) {
            value.toInt().toString() // whole number → no trailing ".0"
        } else {
            value.toString() // genuine fractional → keep decimals (e.g. "6.5")
        }
        return if (unit.isEmpty()) valueString else "$valueString $unit"
    }
}
