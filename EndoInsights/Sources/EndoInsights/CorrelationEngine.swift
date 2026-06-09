// EndoInsights — the .correlation producer (Phase 7, Plan 02, INST-03 / D-04..D-18 / SC1 / V7).
//
// THE MVP vertical slice that turns the proven Plan-01 math (`CorrelationStats`) into HONEST findings.
// Foundation ONLY — NO SwiftData/SwiftUI/UIKit (SC1; BoundaryLint is the proof). Pure: an INJECTED
// `today` + `calendar`, NO `Date.now`/`Date()`/`UUID()`/RNG/`print` (V7/D-16). Mirrors `CyclePhaseMapper`
// exactly: an `enum` namespace, one `static func insights(from:asOf:calendar:)` entry, `Equatable`
// `[Insight]` output — same snapshots + same injected `today` → an equal `[Insight]`.
//
// THE DELIVERABLE IS THE SUPPRESSION (D-18). The engine emits a `.correlation` Insight ONLY for the
// top-5 survivors that clear the three-AND hard gate (n ≥ minSample & each group ≥ 3, |δ|/|RD| ≥ the
// medium floor, AND passes BH-FDR q=0.10 over ALL tested pairs). An under-sampled / spurious /
// multiple-comparison-flood pair never clears the gate → it is NOT emitted (zero false positives). The
// cycle-phase factor derives ONLY from `CyclePhaseMapper.phaseWindows`, so a suppressed/irregular stream
// yields no phase windows → no `factor.cyclePhase.*` pair is ever tested (D-04 by construction).
//
// SINGLE SOURCE OF TRUTH: the per-pair sample floor reads `HonestyGate.minSample(for: .correlation)` —
// no second magic number. The gate (Plan 03) re-enforces it as a superset backstop. This engine does
// NOT touch `HonestyGate`/`InsightsEngine`.
import Foundation

enum CorrelationEngine {

    // MARK: - Tuning constants (named — no buried literals; 07-RESEARCH A4/A6/§6).

    /// Benjamini–Hochberg false-discovery-rate target, over the FULL candidate set (D-06, A4).
    static let fdrQ = 0.10
    /// The top-N strongest gate-eligible survivors emitted (D-10, A6) — caps the flood at the source.
    static let topN = 5

    // Cliff's-δ effect-magnitude floors (severity arm) — the locked Romano bands (07-RESEARCH §2A).
    private static let severityMediumFloor = 0.33
    private static let severityLargeFloor = 0.474
    // Risk-difference effect-magnitude floors (presence arm) — the locked RD bands (07-RESEARCH §2B).
    private static let presenceMediumFloor = 0.20
    private static let presenceLargeFloor = 0.35

    // MARK: - The single public entry point (mirrors CyclePhaseMapper.insights signature EXACTLY).

    /// - Parameters:
    ///   - snapshots: the per-day value snapshots (order-independent).
    ///   - today: injected reference date (never read from the clock inside) → deterministic.
    ///   - calendar: injected calendar for day-bucketing (defaults to `.current`).
    static func insights(
        from rawSnapshots: [DaySnapshot],
        asOf today: Date,
        calendar: Calendar = .current
    ) -> [Insight] {
        // Phase 11 (D-01 "compose, never re-derive"): `insights(...)` and `scoredCandidates(...)` share
        // ONE private survivor pipeline (`scoredSurvivors`). This entry maps the survivors to the public
        // `.correlation` `[Insight]` via `makeInsight()`; `scoredCandidates(...)` maps the SAME survivors
        // to typed `[CorrelationFinding]`. A finding exists iff the correlation insight exists, with
        // identical signalKey/confidence/pairedDays — the math runs once.
        scoredSurvivors(from: rawSnapshots, asOf: today, calendar: calendar).map { $0.makeInsight() }
    }

    /// PACKAGE-INTERNAL (NOT public) typed accessor for `RecommendationsEngine` (Phase 11, INST-05, D-01;
    /// RESEARCH Pattern 2 Option A). Returns EXACTLY the same survivors `insights(...)` emits, in the SAME
    /// deterministic order, but as typed `CorrelationFinding`s carrying a `direction` recovered from the
    /// SIGNED effect (`meanDiff`/`riskRatio`) — NEVER by parsing the audited public `.correlation` copy
    /// (RESEARCH Anti-Patterns; the one real engineering decision, Pitfall 1). No public symbol is added,
    /// so the package boundary (BoundaryLintTests) stays Foundation-only with zero new egress.
    static func scoredCandidates(
        from rawSnapshots: [DaySnapshot],
        asOf today: Date,
        calendar: Calendar = .current
    ) -> [CorrelationFinding] {
        scoredSurvivors(from: rawSnapshots, asOf: today, calendar: calendar).map { $0.makeFinding() }
    }

    /// The SINGLE shared survivor pipeline (steps 0–6) feeding BOTH `insights(...)` and
    /// `scoredCandidates(...)`. Returns the sorted, gate-filtered (`confidence >= .low`), top-N
    /// `ScoredCandidate` list. Extracted from the former `insights(...)` body VERBATIM so the public
    /// `.correlation` behavior stays byte-identical (CorrelationEngineTests/InsightsEngineTests stay GREEN).
    private static func scoredSurvivors(
        from rawSnapshots: [DaySnapshot],
        asOf today: Date,
        calendar: Calendar
    ) -> [ScoredCandidate] {
        // (0) DEFEND THE PACKAGE BOUNDARY (CR-01/WR-04): the `DaySnapshot` header documents "one per
        //     calendar day," but nothing upstream enforces it. A caller that supplies two snapshots with
        //     the same `day` (un-normalized dates, a repository mapping bug, a fixture) must NOT crash the
        //     public insights pipeline — that contradicts the package's honesty/robustness posture.
        //     Collapse to ONE snapshot per `day` deterministically: keep the LAST by input order
        //     (matches `cyclePhaseLabels`' last-write-wins idiom so both maps agree on duplicates), then
        //     sort by day so the whole engine — including the severity/presence loops that iterate
        //     `snapshots` directly — sees a clean, de-duplicated, ordered stream.
        let snapshots = Dictionary(rawSnapshots.map { ($0.day, $0) }, uniquingKeysWith: { _, last in last })
            .values
            .sorted { $0.day < $1.day }

        // (1) Label every day with its estimated CyclePhase via CyclePhaseMapper (reused VERBATIM, D-04/D-17).
        //     A suppressed/irregular/under-sampled stream → [] windows → every label is nil → no phase
        //     factor is ever present → no cyclePhase pair is tested (D-04 by construction, no extra code).
        let phaseByDay = cyclePhaseLabels(snapshots, asOf: today, calendar: calendar)

        // (2) Enumerate the bounded (factor × symptomTarget) pairs (D-09), build a candidate per pair that
        //     clears the sample floor + per-group minimum. Defer FDR + confidence (need ALL p-values first).
        let factors = enumerateFactors(snapshots, phaseByDay: phaseByDay, calendar: calendar)
        var candidates: [Candidate] = []

        for factor in factors {
            // The per-day observed state for this factor (nil = unobserved → excluded from both groups).
            // `snapshots` is already de-duplicated to one entry per `day` (step 0), so no collision can
            // occur here; use `uniquingKeysWith:` (last-wins) rather than `uniqueKeysWithValues:` so the
            // construction is duplicate-TOLERANT by idiom — both engine maps now agree (WR-04), and the
            // duplicate-day trap can never reappear if a future edit touches only one path.
            let stateByDay: [Date: FactorState] = Dictionary(
                snapshots.map { snap in
                    (snap.day, Factor.state(of: factor, on: snap, phase: phaseByDay[snap.day] ?? nil))
                },
                uniquingKeysWith: { _, last in last }
            )

            // Severity arm + presence arm, per symptom; bleeding presence arm (OQ3). Sorted for determinism.
            for symptom in SymptomType.allCases {
                if let c = severityCandidate(factor: factor, symptom: symptom, snapshots: snapshots, stateByDay: stateByDay) {
                    candidates.append(c)
                }
                if let c = presenceCandidate(factor: factor, target: .symptom(symptom), snapshots: snapshots, stateByDay: stateByDay) {
                    candidates.append(c)
                }
            }
            if let c = presenceCandidate(factor: factor, target: .bleeding, snapshots: snapshots, stateByDay: stateByDay) {
                candidates.append(c)
            }
        }

        guard !candidates.isEmpty else { return [] }

        // (3) Collect ALL K p-values and run BH-FDR ONCE over the full candidate set (NEVER per-pair).
        let pValues = candidates.map(\.pValue)
        let passingIndices = CorrelationStats.benjaminiHochbergPasses(pValues, q: fdrQ)

        // (4) Map each candidate to its InsightConfidence via the three-AND gate (effect floor, n, FDR).
        var scored: [ScoredCandidate] = []
        for (idx, candidate) in candidates.enumerated() {
            let confidence = CorrelationStats.confidence(
                effectMag: candidate.effectMagnitude,
                n: candidate.pairedDays,
                minN: HonestyGate.minSample(for: .correlation),   // SSOT: the ONE correlation floor (WR-01).
                mediumFloor: candidate.mediumFloor,
                largeFloor: candidate.largeFloor,
                passedFDR: passingIndices.contains(idx)
            )
            scored.append(ScoredCandidate(candidate: candidate, confidence: confidence))
        }

        // (5) Deterministic order: confidence desc, effectMag desc, pairedDays desc, signalKey asc.
        scored.sort { a, b in
            if a.confidence != b.confidence { return a.confidence > b.confidence }
            if a.candidate.effectMagnitude != b.candidate.effectMagnitude {
                return a.candidate.effectMagnitude > b.candidate.effectMagnitude
            }
            if a.candidate.pairedDays != b.candidate.pairedDays {
                return a.candidate.pairedDays > b.candidate.pairedDays
            }
            return a.candidate.signalKey < b.candidate.signalKey
        }

        // (6) Emit ONLY the top-5 surviving candidates whose confidence >= .low. Below-gate (`.none`) pairs
        //     are NOT emitted at all — so "the engine surfaces zero false positives" is direct and the
        //     top-N cap is over gate-ELIGIBLE survivors only (a withheld `.none` never occupies a slot).
        return scored
            .filter { $0.confidence >= .low }
            .prefix(topN)
            .map { $0 }
    }

    // MARK: - (1) Cycle-phase labelling — reuse CyclePhaseMapper's windows VERBATIM (D-04/D-17).

    /// Per-day estimated `CyclePhase?` label. Re-derives runs/starts/gaps/median/spread the SAME way
    /// `CyclePhaseMapper.insights` does (lines 36–61), then labels each day via `phase(for:in:calendar:)`.
    /// Suppressed/irregular/under-sampled → `[]` windows → every label is `nil` → no phase factor (D-04).
    private static func cyclePhaseLabels(
        _ snapshots: [DaySnapshot],
        asOf today: Date,
        calendar: Calendar
    ) -> [Date: CyclePhase?] {
        var labels: [Date: CyclePhase?] = [:]
        for snap in snapshots { labels[snap.day] = .some(nil) }   // default: outside any window

        let bleedingDays = snapshots.filter { $0.flow != nil }.map(\.day)
        let anySuppressed = snapshots.contains { $0.suppressed }
        guard !anySuppressed else { return labels }               // suppressed → no windows (D-04)

        let runs = CyclePhaseMapper.periodRuns(bleedingDays, calendar: calendar)
        let starts = runs.compactMap(\.first)
        let gaps = CyclePhaseMapper.consecutiveGaps(starts, calendar: calendar)
        guard gaps.count >= 2, let lastStart = starts.last, let lastRun = runs.last else { return labels }

        let median = CyclePhaseMapper.medianInt(gaps)
        let spread = CyclePhaseMapper.spread(gaps)
        guard CyclePhaseMapper.phaseConfidence(cycleCount: gaps.count, spread: spread) != nil else { return labels }

        let windows = CyclePhaseMapper.phaseWindows(
            lastStart: lastStart, lastRun: lastRun, median: median, spread: spread, calendar: calendar
        )
        guard !windows.isEmpty else { return labels }

        for snap in snapshots {
            labels[snap.day] = .some(CyclePhaseMapper.phase(for: snap.day, in: windows, calendar: calendar))
        }
        return labels
    }

    // MARK: - (2) Factor enumeration — the bounded factor set (D-09), in a deterministic order.

    /// The bounded factor families (07-RESEARCH §1). The cycle-phase family is included ONLY if at least
    /// one day carries a non-nil phase label (suppressed/irregular → none → no phase pairs tested, D-04).
    private static func enumerateFactors(
        _ snapshots: [DaySnapshot],
        phaseByDay: [Date: CyclePhase?],
        calendar: Calendar
    ) -> [Factor] {
        var factors: [Factor] = []
        factors += FoodTag.allCases.map { Factor.food($0) }
        factors.append(.activityPresent)
        factors += ActivityType.allCases.map { Factor.activity($0) }
        factors.append(.sleepPoor)
        factors.append(.stressHigh)
        factors.append(.moodLow)
        factors.append(.medicationTaken)
        factors.append(.prnTaken)

        // Cycle-phase factors only when SOME day has a phase label (D-04 by construction).
        let anyPhaseLabel = phaseByDay.values.contains { $0 != nil }
        if anyPhaseLabel {
            factors += CyclePhase.allCases.map { Factor.cyclePhase($0) }
        }

        // Deterministic key order (Pitfall 4) — sort the whole set by stable key before testing.
        return factors.sorted { $0.key < $1.key }
    }

    // MARK: - (3) Per-pair candidate construction (severity arm + presence arm).

    /// What a presence-arm factor is correlated against (D-09: each symptom, plus bleeding per OQ3).
    private enum PresenceTarget {
        case symptom(SymptomType)
        case bleeding

        var keyFragment: String {
            switch self {
            case .symptom(let t): return "symptom.\(t.rawValue)"
            case .bleeding:       return "bleeding"
            }
        }
    }

    /// Severity arm: Cliff's δ + mean-diff + Mann–Whitney p over the two groups of non-nil severities for
    /// `symptom`, split by the factor's observed state. nil severities are EXCLUDED (absence ≠ 0).
    private static func severityCandidate(
        factor: Factor,
        symptom: SymptomType,
        snapshots: [DaySnapshot],
        stateByDay: [Date: FactorState]
    ) -> Candidate? {
        var present: [Double] = []
        var absent: [Double] = []
        for snap in snapshots {
            guard let state = stateByDay[snap.day], state != .unobserved else { continue }
            // The severities for THIS symptom on this day (nil severities excluded — absence ≠ 0).
            // ONE OBSERVATION PER DAY (WR-02): a single day may carry more than one SymptomPoint of the
            // same type ([SymptomPoint] is not contract-unique-per-type), and appending each one would
            // make a single day contribute 2+ data points — inflating `n`/`sampleSize` past the observed
            // day count, weakening the n ≥ minSample floor, and violating Mann–Whitney's independence
            // assumption. Reduce the day to ONE value (the max recorded severity) so `n` counts DAYS.
            let daySeverities = snap.symptoms.filter { $0.type == symptom }.compactMap { $0.severity }
            guard let s = daySeverities.max() else { continue }
            if state == .present { present.append(Double(s)) } else { absent.append(Double(s)) }
        }
        let pairedDays = present.count + absent.count
        guard passesSampleFloor(pairedDays: pairedDays, present: present.count, absent: absent.count) else { return nil }
        guard let delta = CorrelationStats.cliffsDelta(present: present, absent: absent),
              let meanDiff = CorrelationStats.meanDifference(present: present, absent: absent) else { return nil }
        let p = CorrelationStats.mannWhitneyP(present: present, absent: absent)

        return Candidate(
            signalKey: "correlation.\(factor.key).symptom.\(symptom.rawValue)",
            factorLabel: factor.displayLabel,
            targetLabel: CyclePhaseMapper.displayName(symptom),
            arm: .severity(meanDiff: meanDiff),
            effectMagnitude: abs(delta),
            pairedDays: pairedDays,
            pValue: p,
            mediumFloor: severityMediumFloor,
            largeFloor: severityLargeFloor
        )
    }

    /// Presence arm: a 2×2 over paired/observed days → RD + RR + Fisher p. The target is "logged that day".
    private static func presenceCandidate(
        factor: Factor,
        target: PresenceTarget,
        snapshots: [DaySnapshot],
        stateByDay: [Date: FactorState]
    ) -> Candidate? {
        var a = 0, b = 0, c = 0, d = 0   // a: factor+ & target+, b: factor+ & target-, c: factor- & target+, d: factor- & target-
        for snap in snapshots {
            guard let state = stateByDay[snap.day], state != .unobserved else { continue }
            let targetPresent: Bool
            switch target {
            case .symptom(let t): targetPresent = snap.symptoms.contains { $0.type == t }
            case .bleeding:       targetPresent = snap.flow != nil
            }
            switch (state, targetPresent) {
            case (.present, true):  a += 1
            case (.present, false): b += 1
            case (.absent, true):   c += 1
            case (.absent, false):  d += 1
            case (.unobserved, _):  break
            }
        }
        let pairedDays = a + b + c + d
        guard passesSampleFloor(pairedDays: pairedDays, present: a + b, absent: c + d) else { return nil }
        guard let rd = CorrelationStats.riskDifference(a: a, b: b, c: c, d: d) else { return nil }
        let rr = CorrelationStats.riskRatio(a: a, b: b, c: c, d: d)
        let p = CorrelationStats.fisherExactTwoTailed(a: a, b: b, c: c, d: d)

        let targetLabel: String
        switch target {
        case .symptom(let t): targetLabel = CyclePhaseMapper.displayName(t)
        case .bleeding:       targetLabel = "bleeding"
        }

        return Candidate(
            signalKey: "correlation.\(factor.key).\(target.keyFragment)",
            factorLabel: factor.displayLabel,
            targetLabel: targetLabel,
            arm: .presence(riskRatio: rr),
            effectMagnitude: abs(rd),
            pairedDays: pairedDays,
            pValue: p,
            mediumFloor: presenceMediumFloor,
            largeFloor: presenceLargeFloor
        )
    }

    /// Pair-level floor (single source of truth) + per-group minimum. A pair that clears BOTH is tested;
    /// otherwise it is dropped BEFORE emitting (self-gating — it never reaches the FDR/confidence step).
    private static func passesSampleFloor(pairedDays: Int, present: Int, absent: Int) -> Bool {
        pairedDays >= HonestyGate.minSample(for: .correlation)
            && present >= Factor.perGroupMinimum
            && absent >= Factor.perGroupMinimum
    }

    // MARK: - Phase 11 direction recovery (INST-05, D-03; RESEARCH Pattern 2 "Direction semantics").

    /// PACKAGE-INTERNAL typed association direction for a surviving correlation candidate, recovered from
    /// the SIGNED effect (RESEARCH Pattern 2 Option A). `RecommendationsEngine` (Plan 02) re-frames this
    /// typed value into direction-aware discussion-prompt copy — it is NEVER inferred from the audited
    /// public correlation copy (the fragile-substring anti-pattern).
    enum RecommendationDirection: Equatable {
        /// The factor's present-group fares BETTER (lower symptom severity, or the symptom logged LESS often).
        case beneficial
        /// The factor's present-group fares WORSE (higher symptom severity, or the symptom logged MORE often).
        case aggravating
    }

    /// PACKAGE-INTERNAL typed survivor the recommendation engine consumes (RESEARCH Pattern 2 Option A).
    /// Carries the same identity/evidence fields the emitted `.correlation` Insight does, PLUS the typed
    /// `direction`. A `CorrelationFinding` exists iff the matching `.correlation` Insight exists (same
    /// shared `scoredSurvivors` pipeline), with identical `signalKey`/`confidence`/`pairedDays`.
    struct CorrelationFinding: Equatable {
        let signalKey: String
        let factorLabel: String
        let targetLabel: String
        let confidence: InsightConfidence
        let pairedDays: Int
        let effectMagnitude: Double
        let direction: RecommendationDirection
    }

    // MARK: - Candidate value types.

    /// Which arm produced a candidate (carries the human-readable effect for the copy).
    private enum Arm {
        case severity(meanDiff: Double)
        case presence(riskRatio: Double?)

        /// The typed association direction (RESEARCH Pattern 2 "Direction semantics"):
        /// - SEVERITY arm: `beneficial` when `meanDiff < 0` (the factor's present-group has LOWER symptom
        ///   severity), `aggravating` when `meanDiff > 0` (higher). A `0`/non-finite diff defaults to
        ///   `aggravating` (a surviving severity finding with a sign-less effect is the conservative read).
        /// - PRESENCE arm: `aggravating` when the risk-ratio implies the target is logged MORE often on
        ///   factor-present days (`riskRatio > 1`); `beneficial` when logged LESS often (`riskRatio < 1`).
        ///   A `nil`/non-finite risk ratio defaults to `aggravating` — the conservative reading, since a
        ///   presence finding that surfaced AT ALL is an elevated co-occurrence.
        var direction: RecommendationDirection {
            switch self {
            case .severity(let meanDiff):
                return (meanDiff.isFinite && meanDiff < 0) ? .beneficial : .aggravating
            case .presence(let riskRatio):
                guard let rr = riskRatio, rr.isFinite else { return .aggravating }
                return rr < 1 ? .beneficial : .aggravating
            }
        }
    }

    /// A tested pair that cleared the sample floor, awaiting FDR + confidence. `effectMagnitude` is |δ|
    /// (severity) or |RD| (presence); `pValue` feeds the single BH-FDR pass.
    private struct Candidate {
        let signalKey: String
        let factorLabel: String
        let targetLabel: String
        let arm: Arm
        let effectMagnitude: Double
        let pairedDays: Int
        let pValue: Double
        let mediumFloor: Double
        let largeFloor: Double
    }

    /// A candidate with its computed `InsightConfidence` (`.none` ⇒ withheld, never emitted).
    private struct ScoredCandidate {
        let candidate: Candidate
        let confidence: InsightConfidence

        /// Build the emitted `.correlation` Insight with hand-written NON-CAUSAL copy (D-12/Pitfall 3):
        /// ONLY "possibly associated with" / "tended to co-occur with" / "logged more often"; NEVER
        /// trigger/cause/will/because/linked to/worsen/improve/reduce (mirrors CyclePhaseMapper line 261).
        func makeInsight() -> Insight {
            let factorLabel = candidate.factorLabel
            let targetLabel = candidate.targetLabel
            let headline: String
            let detail: String

            switch candidate.arm {
            case .severity(let meanDiff):
                let points = abs(meanDiff).rounded()
                let pointWord = points == 1 ? "point" : "points"
                headline = "Higher \(targetLabel.lowercased()) severity possibly associated with logging \(factorLabel)"
                detail = "On the days you logged \(factorLabel), \(targetLabel.lowercased()) severity averaged "
                    + "~\(Int(points)) \(pointWord) higher than on other observed days — a descriptive co-occurrence only."

            case .presence(let riskRatio):
                let liftPhrase: String
                if let rr = riskRatio, rr.isFinite, rr > 0 {
                    let times = (rr * 10).rounded() / 10
                    liftPhrase = "about \(formatLift(times))× as often"
                } else {
                    liftPhrase = "more often"
                }
                headline = "\(targetLabel.capitalized) possibly associated with logging \(factorLabel)"
                detail = "You logged \(targetLabel.lowercased()) \(liftPhrase) on the days you logged "
                    + "\(factorLabel), across \(candidate.pairedDays) observed days — a descriptive co-occurrence only."
            }

            return Insight.derived(
                kind: .correlation,
                window: .allTime,
                signalKey: candidate.signalKey,
                headline: headline,
                detail: detail,
                confidence: confidence,
                points: [],
                sampleSize: candidate.pairedDays
            )
        }

        /// Phase 11 (INST-05, D-01): map this survivor to the typed `CorrelationFinding` the recommendation
        /// engine consumes. The `direction` is recovered from the candidate's `arm` (the SIGNED effect),
        /// NOT from the emitted copy. Identity/evidence fields are the SAME values `makeInsight()` emits.
        func makeFinding() -> CorrelationEngine.CorrelationFinding {
            CorrelationEngine.CorrelationFinding(
                signalKey: candidate.signalKey,
                factorLabel: candidate.factorLabel,
                targetLabel: candidate.targetLabel,
                confidence: confidence,
                pairedDays: candidate.pairedDays,
                effectMagnitude: candidate.effectMagnitude,
                direction: candidate.arm.direction
            )
        }

        private func formatLift(_ value: Double) -> String {
            value == value.rounded() ? String(Int(value)) : String(format: "%.1f", value)
        }
    }
}
