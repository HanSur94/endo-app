// Requirement: CYCL-02 / CYCL-03 / CYCL-04 — the pure, value-type derived-cycle engine (D-08/D-09/D-10).
//
// Pure value type: Foundation ONLY — NO `import SwiftData`, NO `import SwiftUI`. `today` is INJECTED
// (never `Date.now`/`Date()` inside the logic) so the engine is deterministic and unit-testable, and
// portable to the future insights engine. This is the no-phantom-cycle keystone (the #1 endo pitfall):
//
//   1. suppressed short-circuit            → emit NOTHING (`.suppressed`).
//   2. periodRuns                          → group flow days into runs of consecutive calendar days.
//   3. starts = first day of each run.
//   4. consecutiveGaps                     → start-to-start day gaps.
//   5. guard gaps.count >= 2 else          → `.notEnoughData` ("not enough data yet").
//   6. medianInt(gaps)                     → cycle length as the MEDIAN gap (never an assumed default).
//   7. spread(gaps)                        → a regularity measure (max − min).
//   8. confidenceLevel(cycleCount:spread:) → more cycles + lower spread → higher confidence.
//   9. predictRange                        → ALWAYS a ClosedRange<Date> centered on lastStart+median,
//                                            widened by spread and inversely with confidence.
//  10. cycleDay = days from lastStart to today, +1.
//
// NOTE: there is deliberately NO `28`/`cycleLength` default literal anywhere — the length is derived.
import Foundation

/// Pure derived-cycle engine. No store, no UI, no I/O, no logging (V7) — a value-in/value-out function.
enum CycleMath {

    /// Summarize derived cycle state from the calendar days that had any flow.
    ///
    /// - Parameters:
    ///   - bleedingDays: calendar `Date`s of days with any recorded flow (order-independent).
    ///   - isSuppressed: D-09 suppressed / no-cycle mode — short-circuits to emit nothing.
    ///   - today: injected reference date (never read from the clock inside) → deterministic.
    ///   - calendar: injected calendar for day-bucketing (defaults to `.current`).
    static func summarize(
        bleedingDays: [Date],
        isSuppressed: Bool,
        today: Date,
        calendar: Calendar = .current
    ) -> CycleSummary {
        // 1. D-09 hard short-circuit: suppressed → emit nothing derivable (the no-phantom-cycle proof).
        if isSuppressed { return .suppressed }

        // 2–4. runs → period starts → start-to-start gaps.
        let runs = periodRuns(bleedingDays, calendar: calendar)
        let starts = runs.compactMap(\.first)
        let gaps = consecutiveGaps(starts, calendar: calendar)

        // 5. < 2 cycles (i.e. < 2 start-to-start gaps) → not meaningful yet (CYCL-04).
        guard gaps.count >= 2, let lastStart = starts.last else {
            return .notEnoughData
        }

        // 6–8. median length, regularity spread, confidence.
        let median = medianInt(gaps)
        let spread = self.spread(gaps)
        let confidence = confidenceLevel(cycleCount: gaps.count, spread: spread)

        // 9. next-period RANGE (never a single hard date), centered on lastStart+median.
        let nextRange = predictRange(
            lastStart: lastStart, median: median,
            spread: spread, confidence: confidence, calendar: calendar
        )

        // 10. derived day-of-cycle: days since the last period start, +1.
        let today0 = calendar.startOfDay(for: today)
        let lastStart0 = calendar.startOfDay(for: lastStart)
        let cycleDay = calendar.dateComponents([.day], from: lastStart0, to: today0).day.map { $0 + 1 }

        return CycleSummary(
            isSuppressed: false,
            cycleDay: cycleDay,
            phase: nil,                 // held nil this phase — Phase 6 populates named phases (OQ 2).
            nextPeriod: nextRange,
            confidence: confidence,
            hasEnoughData: true
        )
    }

    // MARK: - Pure helpers (each independently unit-testable)

    /// Group flow days into runs of consecutive calendar days. Consecutive days collapse into ONE run
    /// (a period), not N starts. De-duplicates same-day entries. Returns runs ascending by start.
    static func periodRuns(_ days: [Date], calendar: Calendar) -> [[Date]] {
        // Normalize to start-of-day, de-dupe, sort ascending.
        let sorted = Array(Set(days.map { calendar.startOfDay(for: $0) })).sorted()
        guard !sorted.isEmpty else { return [] }

        var runs: [[Date]] = []
        var current: [Date] = [sorted[0]]
        for d in sorted.dropFirst() {
            let prev = current.last!
            let dayGap = calendar.dateComponents([.day], from: prev, to: d).day ?? 0
            if dayGap == 1 {
                current.append(d)           // consecutive → same run
            } else {
                runs.append(current)        // a gap > 1 day → a new period run
                current = [d]
            }
        }
        runs.append(current)
        return runs
    }

    /// Day gaps between consecutive period-start dates. `[]` for fewer than two starts.
    static func consecutiveGaps(_ starts: [Date], calendar: Calendar) -> [Int] {
        guard starts.count >= 2 else { return [] }
        return zip(starts, starts.dropFirst()).map { a, b in
            calendar.dateComponents([.day], from: a, to: b).day ?? 0
        }
    }

    /// Median of integer gaps (sort + middle; mean of the two middles for an even count).
    static func medianInt(_ values: [Int]) -> Int {
        guard !values.isEmpty else { return 0 }
        let s = values.sorted()
        let mid = s.count / 2
        if s.count % 2 == 1 { return s[mid] }
        return (s[mid - 1] + s[mid]) / 2
    }

    /// Regularity measure: the spread (max − min) of the gaps. 0 → perfectly regular.
    static func spread(_ values: [Int]) -> Int {
        guard let lo = values.min(), let hi = values.max() else { return 0 }
        return hi - lo
    }

    /// More recorded cycles + lower spread → higher confidence. Tunable heuristic (A3) — the contract
    /// it locks is monotonic: a tight, longer history is `.high`; a wide spread is never `.high`.
    static func confidenceLevel(cycleCount: Int, spread: Int) -> Confidence {
        guard cycleCount >= 2 else { return .none }
        switch (cycleCount, spread) {
        case let (count, s) where count >= 3 && s <= 3:  return .high
        case let (_, s) where s <= 3:                    return .medium   // tight but few cycles
        case let (count, s) where count >= 3 && s <= 9:  return .medium   // moderate spread
        default:                                         return .low      // wide spread / sparse
        }
    }

    /// Next-period estimate as a RANGE (never a single date). Centered on `lastStart + median`, with a
    /// half-width that grows with `spread` and shrinks with `confidence` — irregular ⇒ wider window.
    static func predictRange(
        lastStart: Date,
        median: Int,
        spread: Int,
        confidence: Confidence,
        calendar: Calendar
    ) -> ClosedRange<Date> {
        let lastStart0 = calendar.startOfDay(for: lastStart)
        let center = calendar.date(byAdding: .day, value: median, to: lastStart0)!

        // Confidence padding: lower confidence widens the window.
        let confidencePad: Int
        switch confidence {
        case .high:   confidencePad = 1
        case .medium: confidencePad = 3
        case .low:    confidencePad = 5
        case .none:   confidencePad = 5
        }
        // Half-width grows with spread (regularity) + the confidence pad; always ≥ 1 day so the range
        // is non-degenerate (lower < upper).
        let halfWidth = max(1, (spread / 2) + confidencePad)

        let lower = calendar.date(byAdding: .day, value: -halfWidth, to: center)!
        let upper = calendar.date(byAdding: .day, value: halfWidth, to: center)!
        return lower...upper
    }
}
