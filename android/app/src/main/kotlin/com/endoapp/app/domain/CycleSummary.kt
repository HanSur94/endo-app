// EndoApp (:app) — the value type the report/UI renders for a person's cycle state.
//
// Kotlin port of `Sources/EndoApp/Domain/CycleSummary.swift` (CYCL-03 / CYCL-04). Pure — no Room, no
// Compose, no Android type: `java.time.LocalDate` + the `:insights` `InsightConfidence`. It is the
// OUTPUT of [CycleMath.summarize] and the input to the Plan-02 report cycle section.
//
// The no-phantom-cycle contract (D-08/D-09/D-10) — the three honest states:
//   • [suppressed]     — user in no-cycle mode: emit NOTHING (no cycleDay, no window).
//   • [notEnoughData]  — < 2 recorded cycles: "not enough data yet".
//   • derived          — a cycleDay + a next-period date RANGE + confidence (capped at MEDIUM).
// Every cycle field is OPTIONAL and derived — never a stored column, never a fabricated default.
package com.endoapp.app.domain

import com.endoapp.insights.InsightConfidence
import java.time.LocalDate

/**
 * A snapshot of derived cycle state. A `data class` (Equatable) for deterministic tests.
 *
 * The Swift `phase: String?` field is deliberately OMITTED here — the report cycle section consumes
 * only the five fields below (per the Plan key-links). Named phases live in the `:insights`
 * `CyclePhaseMapper`, not this summary.
 */
data class CycleSummary(
    /** When true, the user is in suppressed / no-cycle mode (D-09): render NOTHING cycle-related. */
    val isSuppressed: Boolean,
    /** Derived day-of-cycle (days since the last period start, +1). `null` unless meaningful (CYCL-04). */
    val cycleDay: Int?,
    /** Next-period estimate as a date RANGE (D-08) — never a single hard date. `null` unless meaningful. */
    val nextPeriod: ClosedRange<LocalDate>?,
    /** Confidence in the estimate, or [InsightConfidence.NONE] when nothing is emitted. Capped at MEDIUM. */
    val confidence: InsightConfidence,
    /** `false` → "not enough data yet" (CYCL-04): hide the cycle summary entirely. */
    val hasEnoughData: Boolean,
) {
    companion object {
        /** Suppressed / no-cycle mode — emits NOTHING derivable (the no-phantom-cycle proof, D-09). */
        val suppressed = CycleSummary(
            isSuppressed = true,
            cycleDay = null,
            nextPeriod = null,
            confidence = InsightConfidence.NONE,
            hasEnoughData = false,
        )

        /** Fewer than 2 recorded cycles — "not enough data yet" (CYCL-04). */
        val notEnoughData = CycleSummary(
            isSuppressed = false,
            cycleDay = null,
            nextPeriod = null,
            confidence = InsightConfidence.NONE,
            hasEnoughData = false,
        )
    }
}
