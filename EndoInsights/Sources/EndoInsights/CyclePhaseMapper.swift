// EndoInsights — the estimated cycle-phase mapper (Phase 6, INST-02 / D-01/D-02/D-03/D-14/D-17 / SC1).
//
// Foundation ONLY — NO `import SwiftData`, NO `import SwiftUI` (SC1; BoundaryLint is the proof). Ports
// `CycleMath`'s derived-cycle math VERBATIM (D-17 mandates identical math — do NOT "improve" it) into
// the package idiom: an `enum` namespace, an INJECTED `today` + `calendar` (never `Date.now`/`Date()`),
// pure helpers, `Equatable` `Insight` outputs, NO logging/print (V7). Same inputs + same injected
// `today` → an equal `[Insight]`.
//
// HONESTY — the deliverable IS the omission (the #1 endo pitfall, SC1):
//   • suppressed   → return [] (mirrors `CycleMath`'s `if isSuppressed { return .suppressed }`).
//   • < 2 cycles   → return [] (no phantom phase from a single bleed).
//   • spread > 9   → return [] (irregular: the cycle is too unstable to estimate phases, D-03).
//   • confidence   → caps at `.medium`, NEVER `.high` — a phase window is an estimate even at best (D-02).
//   • copy         → descriptive, non-causal ("tended to co-occur with") — never trigger/causes/will/because.
import Foundation

/// Pure estimated-cycle-phase mapper. No store, no UI, no I/O, no logging (V7) — value-in/value-out.
///
/// Maps recorded symptoms onto ESTIMATED cycle phases ONLY when a reliable, regular cycle exists; for
/// suppressed / irregular / under-sampled streams it returns `[]` (no phantom phase). `InsightsEngine`
/// (Plan 02) composes this with `TrendsEngine` behind the `HonestyGate`.
enum CyclePhaseMapper {

    /// The single public entry point (mirrors `CycleMath.summarize` / `TrendsEngine.trends`).
    ///
    /// - Parameters:
    ///   - snapshots: the per-day value snapshots (order-independent). Bleeding + suppression are
    ///     re-derived self-contained from these (D-14).
    ///   - today: injected reference date (never read from the clock inside) → deterministic.
    ///   - calendar: injected calendar for day-bucketing (defaults to `.current`).
    static func insights(
        from snapshots: [DaySnapshot],
        asOf today: Date,
        calendar: Calendar = .current
    ) -> [Insight] {
        // Re-derive bleeding + suppression self-contained (D-14) — mirrors TrendsEngine.bleedingTrend.
        let bleedingDays = snapshots.filter { $0.flow != nil }.map(\.day)
        let anySuppressed = snapshots.contains { $0.suppressed }

        // 1. Suppressed short-circuit (SC1) — emit NOTHING (CycleMath: `if isSuppressed { return .suppressed }`).
        guard !anySuppressed else { return [] }

        // 2–4. runs → period starts → start-to-start gaps.
        let runs = periodRuns(bleedingDays, calendar: calendar)
        let starts = runs.compactMap(\.first)
        let gaps = consecutiveGaps(starts, calendar: calendar)

        // 3. < 2 cycles (i.e. < 2 start-to-start gaps) → no phantom phase.
        guard gaps.count >= 2, let lastStart = starts.last, let lastRun = runs.last else { return [] }

        // 4. median cycle length, regularity spread.
        let median = medianInt(gaps)
        let spread = self.spread(gaps)

        // 5. confidence gate — `nil` for irregular (spread > 9) → no phases (D-03).
        guard let confidence = phaseConfidence(cycleCount: gaps.count, spread: spread) else { return [] }

        // Build the four estimated phase windows for the CURRENT cycle (the one starting at lastStart).
        let windows = phaseWindows(
            lastStart: lastStart, lastRun: lastRun, median: median, spread: spread, calendar: calendar
        )

        // Label every in-range snapshot day with its phase, then emit one descriptive insight per
        // (phase, symptom) with a logged co-occurrence.
        return associations(snapshots, windows: windows, confidence: confidence, calendar: calendar)
    }

    // MARK: - Ported VERBATIM from CycleMath (D-17 — identical math; do NOT alter).

    /// Group flow days into runs of consecutive calendar days. Consecutive days collapse into ONE run
    /// (a period), not N starts. De-duplicates same-day entries. Returns runs ascending by start.
    static func periodRuns(_ days: [Date], calendar: Calendar) -> [[Date]] {
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

    // MARK: - Phase confidence (A2 — caps at .medium, NEVER .high; nil = too irregular → no phases).

    /// More cycles + lower spread → higher confidence, but CAPPED at `.medium` because a phase window is
    /// an estimate even at best (D-01/D-02). Returning `nil` is the explicit "too irregular, omit
    /// mapping" decision (the irregular gate, D-03):
    ///   - `(c ≥ 3 && spread ≤ 3)` → `.medium`  (a tight, multi-cycle history)
    ///   - `(spread ≤ 9)`          → `.low`     (a usable but looser estimate)
    ///   - else                    → `nil`      (spread > 9 → irregular → NO phases)
    static func phaseConfidence(cycleCount: Int, spread: Int) -> InsightConfidence? {
        switch (cycleCount, spread) {
        case let (c, s) where c >= 3 && s <= 3: return .medium
        case let (_, s) where s <= 9:           return .low
        default:                                return nil
        }
    }

    // MARK: - Estimated phase windows (DST-safe day math — never epoch arithmetic).

    /// A half-open-ish day range a phase covers (inclusive lower, inclusive upper, start-of-day).
    struct PhaseWindow {
        let phase: CyclePhase
        let lower: Date
        let upper: Date
    }

    /// The four estimated phase windows for the current cycle, relative to the last period start `S`,
    /// median cycle length `M`, and an ovulation `band = max(2, spread / 2)` that WIDENS with
    /// irregularity (D-02 "wide uncertainty band"). Ovulation is anchored 14 days BEFORE the next
    /// predicted start `S + M` (the luteal phase is the biologically stable ~14-day anchor, A7):
    ///   • menstrual  = the ACTUAL recorded bleeding-run days (never a fixed count — honest).
    ///   • follicular = end-of-bleeding … `(S + M - 14 - band)`.
    ///   • ovulatory  = `[S + M - 14 - band … S + M - 14 + band]`.
    ///   • luteal     = `(S + M - 14 + band) … (S + M)`.
    /// All day math via `calendar.date(byAdding:.day)` + `startOfDay` (CycleMath.predictRange idiom).
    static func phaseWindows(
        lastStart: Date,
        lastRun: [Date],
        median: Int,
        spread: Int,
        calendar: Calendar
    ) -> [PhaseWindow] {
        let s0 = calendar.startOfDay(for: lastStart)
        let band = max(2, spread / 2)

        func add(_ days: Int, to base: Date) -> Date {
            calendar.startOfDay(for: calendar.date(byAdding: .day, value: days, to: base)!)
        }

        // The menstrual window is the actual recorded run (honest — not a fixed count).
        let bleedEnd = lastRun.map { calendar.startOfDay(for: $0) }.max() ?? s0

        let nextStart = add(median, to: s0)                  // S + M
        let ovCenter = add(-14, to: nextStart)               // S + M - 14
        let ovLower = add(-band, to: ovCenter)               // S + M - 14 - band
        let ovUpper = add(band, to: ovCenter)                // S + M - 14 + band

        // follicular starts the day AFTER bleeding ends; ends the day BEFORE the ovulation band.
        let folLower = add(1, to: bleedEnd)
        let folUpper = add(-1, to: ovLower)
        // luteal starts the day AFTER the ovulation band; ends the day BEFORE the next predicted start.
        let lutLower = add(1, to: ovUpper)
        let lutUpper = add(-1, to: nextStart)

        var windows: [PhaseWindow] = [
            PhaseWindow(phase: .menstrual, lower: s0, upper: bleedEnd),
            PhaseWindow(phase: .ovulatory, lower: ovLower, upper: ovUpper),
        ]
        // Guard against degenerate (inverted) follicular/luteal ranges for very short cycles.
        if folLower <= folUpper { windows.append(PhaseWindow(phase: .follicular, lower: folLower, upper: folUpper)) }
        if lutLower <= lutUpper { windows.append(PhaseWindow(phase: .luteal, lower: lutLower, upper: lutUpper)) }

        // WR-05: overlap resolution must be EXPLICIT and order-INDEPENDENT, never "whichever window appears
        // first in the array wins". For a short median cycle or a wide band the windows can overlap (e.g.
        // bleeding runs into the ovulation band: `bleedEnd >= ovLower`). Attributing an overlap day by array
        // position is not an honest, defensible mapping — so if ANY constructed window pair overlaps, the
        // cycle is too short to phase-map honestly: return [] (consistent with the irregular short-circuit,
        // D-03), rather than silently mis-attributing days.
        guard !windowsOverlap(windows) else { return [] }
        return windows
    }

    /// True if ANY two of the constructed phase windows share a day (inclusive ranges). Order-independent —
    /// it compares every unordered pair, so it does not depend on array insertion order (WR-05).
    static func windowsOverlap(_ windows: [PhaseWindow]) -> Bool {
        for i in windows.indices {
            for j in windows.indices where j > i {
                // Inclusive ranges overlap iff each starts on or before the other's end.
                if windows[i].lower <= windows[j].upper && windows[j].lower <= windows[i].upper {
                    return true
                }
            }
        }
        return false
    }

    /// The estimated phase a calendar day falls in, or `nil` if it is outside the current cycle's windows.
    /// Windows are guaranteed non-overlapping by `phaseWindows` (WR-05), so `first` is unambiguous here.
    static func phase(for day: Date, in windows: [PhaseWindow], calendar: Calendar) -> CyclePhase? {
        let d = calendar.startOfDay(for: day)
        return windows.first { $0.lower <= d && d <= $0.upper }?.phase
    }

    // MARK: - Per-(phase, symptom) descriptive association emission (non-causal copy, D-08/D-16).

    /// Label each in-range snapshot day with its phase, then emit ONE descriptive `.cyclePhase` Insight
    /// per (phase, symptom) with a logged co-occurrence. Copy is NON-CAUSAL — "tended to co-occur with"
    /// — never trigger/causes/will/because. Output is deterministic (sorted by phase then symptom).
    static func associations(
        _ snapshots: [DaySnapshot],
        windows: [PhaseWindow],
        confidence: InsightConfidence,
        calendar: Calendar
    ) -> [Insight] {
        // Per-phase: total in-phase days, and per-symptom co-occurrence counts.
        var dayCount: [CyclePhase: Int] = [:]
        var symptomCount: [CyclePhase: [SymptomType: Int]] = [:]

        for snap in snapshots {
            guard let phase = phase(for: snap.day, in: windows, calendar: calendar) else { continue }
            dayCount[phase, default: 0] += 1
            for symptom in Set(snap.symptoms.map(\.type)) {
                symptomCount[phase, default: [:]][symptom, default: 0] += 1
            }
        }

        var out: [Insight] = []
        for phase in CyclePhase.allCases {                       // deterministic phase order
            let totalDays = dayCount[phase] ?? 0
            guard totalDays > 0, let counts = symptomCount[phase] else { continue }
            // Deterministic symptom order.
            for type in counts.keys.sorted(by: { $0.rawValue < $1.rawValue }) {
                let logged = counts[type] ?? 0
                // Emit the candidate at its REAL evidence count; the HonestyGate enforces
                // `sampleSize >= minSample(.cyclePhase) == 2` and WITHHOLDS a single-day (n=1) coincidental
                // association (CR-01/WR-02). This producer no longer relies on a non-existent gate self-gate.
                guard logged >= 1 else { continue }     // skip zero-count entries; the n>=2 floor is the GATE's job (now LIVE).
                let label = displayName(type)
                let phaseLabel = phaseName(phase)
                out.append(Insight.derived(
                    kind: .cyclePhase,
                    window: .allTime,
                    signalKey: "cyclePhase.\(phase.rawValue).symptom.\(type.rawValue)",
                    // Descriptive + non-causal: a logged co-occurrence count only. NOTE the copy itself must
                    // avoid the forbidden substrings (RESEARCH Pitfall 2: "cause" ⊂ "because") — phrase as a
                    // plain co-occurrence; the honesty-gate term scan (Plan 02) scans headline + detail.
                    headline: "\(label) tended to co-occur with your \(phaseLabel) phase",
                    detail: "\(label) was logged on \(logged) of \(totalDays) of your estimated \(phaseLabel)-phase days — a descriptive co-occurrence only.",
                    confidence: confidence,
                    points: [],
                    // sampleSize = the per-association logged-occurrence count — the REAL evidence count for
                    // this (phase, symptom) association. The gate drops it when < minSample(.cyclePhase).
                    sampleSize: logged
                ))
            }
        }
        return out
    }

    // MARK: - Labels (package-local; mirrors TrendsEngine.displayName).

    static func displayName(_ type: SymptomType) -> String {
        switch type {
        case .pelvicPain:   return "Pelvic pain"
        case .dysmenorrhea: return "Period pain"
        case .dyspareunia:  return "Painful sex"
        case .giEndoBelly:  return "Endo belly / GI"
        case .urinary:      return "Urinary symptoms"
        case .fatigue:      return "Fatigue"
        case .brainFog:     return "Brain fog"
        case .lowMood:      return "Low mood"
        case .headache:     return "Headache"
        case .backLegPain:  return "Back / leg pain"
        case .other:        return "Other symptom"
        }
    }

    static func phaseName(_ phase: CyclePhase) -> String {
        switch phase {
        case .menstrual:  return "menstrual"
        case .follicular: return "follicular"
        case .ovulatory:  return "ovulatory"
        case .luteal:     return "luteal"
        }
    }
}
