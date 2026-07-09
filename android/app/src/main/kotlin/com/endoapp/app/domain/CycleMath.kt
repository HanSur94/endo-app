// EndoApp (:app) — the pure, value-type derived-cycle engine (D-08/D-09/D-10).
//
// Kotlin port of `Sources/EndoApp/Domain/CycleMath.swift` (CYCL-02 / CYCL-03 / CYCL-04). Pure —
// Foundation-free equivalent: `java.time.LocalDate` only, `today` INJECTED (never `LocalDate.now()`
// inside) so the engine is deterministic and unit-testable. This is the no-phantom-cycle keystone:
//
//   1. suppressed short-circuit  → emit NOTHING (`.suppressed`).
//   2. periodRuns                → group flow days into runs of consecutive calendar days.
//   3. starts = first day of each run.
//   4. consecutiveGaps           → start-to-start day gaps.
//   5. < 2 gaps                  → `.notEnoughData` ("not enough data yet").
//   6. median gap                → cycle length as the MEDIAN (never an assumed 28-day default).
//   7. spread                    → a regularity measure (max − min).
//   8. confidence                → more cycles + lower spread → higher, CAPPED at MEDIUM (D-02).
//   9. predictRange              → ALWAYS a ClosedRange<LocalDate> centered on lastStart + median.
//  10. cycleDay                  → days from lastStart to today, +1.
//
// REUSE (D-17, no second algorithm): the run-segmentation / median / spread arithmetic is the SAME
// math already proven in `:insights` `CyclePhaseMapper` — this engine calls those public helpers
// verbatim rather than re-deriving them. All date math is `LocalDate.plusDays` /
// `ChronoUnit.DAYS.between` (DST-safe, never epoch-millis arithmetic).
package com.endoapp.app.domain

import com.endoapp.insights.CyclePhaseMapper
import com.endoapp.insights.InsightConfidence
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Pure derived-cycle engine. No store, no UI, no I/O, no logging — a value-in/value-out function. */
object CycleMath {

    /**
     * Summarize derived cycle state from the calendar days that had any flow.
     *
     * @param bleedingDays calendar days with any recorded flow (order-independent).
     * @param isSuppressed D-09 suppressed / no-cycle mode — short-circuits to emit nothing.
     * @param today injected reference date (never read from the clock inside) → deterministic.
     */
    fun summarize(
        bleedingDays: List<LocalDate>,
        isSuppressed: Boolean,
        today: LocalDate,
    ): CycleSummary {
        // 1. D-09 hard short-circuit: suppressed → emit nothing derivable (the no-phantom-cycle proof).
        if (isSuppressed) return CycleSummary.suppressed

        // 2–4. runs → period starts → start-to-start gaps (reuse the CyclePhaseMapper arithmetic).
        val runs = CyclePhaseMapper.periodRuns(bleedingDays)
        val starts = runs.mapNotNull { it.firstOrNull() }
        val gaps = CyclePhaseMapper.consecutiveGaps(starts)

        // 5. < 2 cycles (i.e. < 2 start-to-start gaps) → not meaningful yet (CYCL-04).
        val lastStart = starts.lastOrNull()
        if (gaps.size < 2 || lastStart == null) return CycleSummary.notEnoughData

        // 6–8. median length, regularity spread, confidence (capped at MEDIUM).
        val median = CyclePhaseMapper.medianInt(gaps)
        val spread = CyclePhaseMapper.spread(gaps)
        val confidence = confidenceLevel(cycleCount = gaps.size, spread = spread)

        // 9. next-period RANGE (never a single hard date), centered on lastStart + median.
        val nextPeriod = predictRange(lastStart = lastStart, median = median, spread = spread, confidence = confidence)

        // 10. derived day-of-cycle: days since the last period start, +1 (DST-safe LocalDate math).
        val cycleDay = ChronoUnit.DAYS.between(lastStart, today).toInt() + 1

        return CycleSummary(
            isSuppressed = false,
            cycleDay = cycleDay,
            nextPeriod = nextPeriod,
            confidence = confidence,
            hasEnoughData = true,
        )
    }

    /**
     * More recorded cycles + lower spread → higher confidence, but CAPPED at MEDIUM — a cycle estimate
     * is NEVER HIGH (D-02, matching `CyclePhaseMapper.phaseConfidence`). Ports the iOS
     * `confidenceLevel(cycleCount:spread:)` monotonic contract, then coerces HIGH → MEDIUM:
     *   - tight (spread ≤ 3)                 → MEDIUM
     *   - moderate (≥ 3 cycles, spread ≤ 9)  → MEDIUM
     *   - wide spread / sparse               → LOW
     *   - < 2 cycles                         → NONE (never reached from [summarize]'s gate).
     */
    fun confidenceLevel(cycleCount: Int, spread: Int): InsightConfidence {
        if (cycleCount < 2) return InsightConfidence.NONE
        val raw = when {
            cycleCount >= 3 && spread <= 3 -> InsightConfidence.HIGH
            spread <= 3 -> InsightConfidence.MEDIUM
            cycleCount >= 3 && spread <= 9 -> InsightConfidence.MEDIUM
            else -> InsightConfidence.LOW
        }
        // A cycle estimate is never HIGH — cap at MEDIUM.
        return if (raw > InsightConfidence.MEDIUM) InsightConfidence.MEDIUM else raw
    }

    /**
     * Next-period estimate as a RANGE (never a single date). Centered on `lastStart + median`, with a
     * half-width that grows with `spread` and shrinks with `confidence` — irregular ⇒ wider window.
     * Always ≥ 1 day so the range is non-degenerate (lower < upper).
     */
    fun predictRange(
        lastStart: LocalDate,
        median: Int,
        spread: Int,
        confidence: InsightConfidence,
    ): ClosedRange<LocalDate> {
        val center = lastStart.plusDays(median.toLong())
        val confidencePad = when (confidence) {
            InsightConfidence.HIGH -> 1
            InsightConfidence.MEDIUM -> 3
            InsightConfidence.LOW -> 5
            InsightConfidence.NONE -> 5
        }
        val halfWidth = maxOf(1, (spread / 2) + confidencePad)
        val lower = center.minusDays(halfWidth.toLong())
        val upper = center.plusDays(halfWidth.toLong())
        return lower..upper
    }
}
