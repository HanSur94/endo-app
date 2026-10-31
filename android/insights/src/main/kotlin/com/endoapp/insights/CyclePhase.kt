// EndoInsights (:insights) — the estimated menstrual-cycle phase enum.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/CyclePhase.swift`. Pure-JVM; `raw` is the
// parity-critical fragment folded into `cyclePhase.<phase.raw>` factor/signalKeys.
//
// HONESTY: these are ESTIMATED windows derived from recorded bleeding only — no ovulation test or
// hormone signal. The `CyclePhaseMapper` (plan 17-04) emits NOTHING for suppressed/irregular/
// under-sampled streams rather than fabricating a phase, and caps confidence at `.medium`.
package com.endoapp.insights

import java.time.LocalDate

/** The four named phases of an estimated menstrual cycle. Derived from recorded bleeding runs + the
 *  median cycle length — never an assumed 28-day default and never an external hormone signal. */
enum class CyclePhase(val raw: String) {
    /** The recorded bleeding days of the current cycle (the actual logged run — never a fixed count). */
    MENSTRUAL("menstrual"),
    /** End-of-bleeding up to the pre-ovulation uncertainty band. */
    FOLLICULAR("follicular"),
    /** The ovulation uncertainty band, centered ~14 days BEFORE the next predicted period start. */
    OVULATORY("ovulatory"),
    /** The post-ovulation window up to the next predicted period start (the biologically stable anchor). */
    LUTEAL("luteal");

    companion object {
        /** Defensive decode by raw value (CyclePhase has no failable init in Swift; returns `null` on unknown). */
        fun fromRawOrNull(raw: String?): CyclePhase? =
            raw?.let { r -> entries.firstOrNull { it.raw == r } }
    }
}

/** One labelled calendar day with its estimated `CyclePhase` (the `InsightPoint`-shape result type).
 *  Swift `day: Date` → Kotlin `day: LocalDate` (zone-free day identity, the locked decision). */
data class PhaseDay(
    val day: LocalDate,
    val phase: CyclePhase,
)
