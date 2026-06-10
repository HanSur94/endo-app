// EndoInsights — the flare early-warning producer (Phase 12, Plan 02, INST-06 / D-01..D-08 / SC1 / V7).
//
// THE hardest, riskiest capability of the project — built dead LAST, conservatively, biased toward
// SILENCE. A heuristic, ML-free, non-diagnostic flare early-warning derived from the user's OWN history:
// it COMPOSES three already-gated signals (a recent rising-severity spine, currently-active known
// triggers, proximity to a historically higher-symptom cycle phase) into a BOUNDED (≤ .medium),
// uncertainty-first confidence — and WITHHOLDS entirely when the evidence is insufficient.
//
// Foundation ONLY — NO SwiftData/SwiftUI/UIKit (SC1; BoundaryLint is the proof). Pure: an INJECTED
// `today` + `calendar`, NO `Date.now`/`Date()`/`UUID()`/RNG/`print` (V7/D-16). Mirrors
// `RecommendationsEngine` EXACTLY: an `enum` namespace, one `static func flareWarning(from:asOf:calendar:)`
// entry, `Equatable` `[Insight]` output — same snapshots + same injected `today` → an equal `[Insight]`.
//
// COMPOSE, NEVER RE-DERIVE (D-01/D-02): the engine consumes `TrendsEngine` (rising severity),
// `CorrelationEngine.scoredCandidates` (active triggers), and `CyclePhaseMapper.phaseWindows` (cycle-phase
// proximity) — it runs NO raw statistics of its own. The rising-severity spine is REQUIRED: no recent rise
// → NO warning (silence is honest). Active triggers and cycle-phase proximity can only RAISE the
// confidence one notch each on top of an existing rising spine — they can NEVER start a warning alone.
//
// BIAS TO WITHHOLD (D-03/D-05/Pitfall 2): a suppressed / flat / insufficient user yields ZERO flare
// warning (or, on the A3 cusp — a real recent rise but sub-21-day evidence — ONE honest "not enough data
// yet" notice). The confidence is CAPPED at .medium (NEVER .high), floored at .low; the warning is a
// words-only card (no number, no chart points). A flare warning is never alarmist/diagnostic/certain.
//
// STABLE SIGNALKEY (D-07): the warning's signalKey is the value/window-INDEPENDENT "flare.warning" with
// the `.allTime` window — so the Phase-6 dismissal + Phase-8 cache survive recompute. `Insight.derived`
// yields the stable id from those inputs. The insufficiency notice shares the SAME signalKey (one flare
// signal); the typed `isInsufficiencyNotice` marker (NOT a signalKey suffix — WR-01) carries it past the
// gate floor.
//
// APP↔PACKAGE COPY MIRROR (mirrors the `RecommendationCopy`↔`RecommendationsCopy` precedent): the package
// CANNOT import the app (SC1), so the curated, calm, uncertainty-first copy lives HERE in a
// package-internal `FlareCopyText` enum, BYTE-IDENTICAL to the app's audited `FlareCopy`
// (`Sources/EndoApp/Features/Insights/FlareCopy.swift`). The app surface is audited by `CopyAuditTests`;
// this mirror is proven clean by the gate's zero-forbidden-terms scan (alarmist ∪ causal ∪ imperative).
import Foundation

enum FlareEngine {

    // MARK: - The single public entry point (mirrors RecommendationsEngine.recommendations signature).

    /// Compose the three already-gated signals into AT MOST one bounded (≤ .medium), uncertainty-first
    /// `.flare` Insight — or, on the A3 cusp, one honest "not enough data yet" notice — biased toward
    /// WITHHOLDING. NO re-derived statistics (D-01); the rising-severity spine is REQUIRED.
    ///
    /// - Parameters:
    ///   - snapshots: the per-day value snapshots (order-independent).
    ///   - today: injected reference date (never read from the clock inside) → deterministic.
    ///   - calendar: injected calendar for day-bucketing (defaults to `.current`).
    static func flareWarning(
        from snapshots: [DaySnapshot],
        asOf today: Date,
        calendar: Calendar = .current
    ) -> [Insight] {
        // 1. SUPPRESSED SHORT-CIRCUIT FIRST (D-08/SC3, Pitfall 2): a user who has turned insight derivation
        //    OFF gets ZERO flare warning — the most caution-sensitive surface is the MOST conservative.
        //    Mirrors RecommendationsEngine line 59 / CyclePhaseMapper line 41. Never a phantom warning.
        guard !snapshots.contains(where: { $0.suppressed }) else { return [] }

        let today0 = calendar.startOfDay(for: today)

        // 2. SIGNAL A — the rising-severity spine (REQUIRED). Compose TrendsEngine helpers (never
        //    re-derive). No rising symptom → return [] (silence is honest, NOT an insufficiency notice).
        guard let rising = risingSeveritySignal(snapshots, today0: today0, calendar: calendar) else {
            return []
        }

        // 5. EVIDENCE FLOOR (D-05 / A3). The evidence is the rising signal's logged-severity day count over
        //    the recent window, gated against the SAME `minSample(.flare) == 21` the gate enforces (Pitfall
        //    4 — so the gate's backstop never re-drops a valid warning). Below the floor: a real recent rise
        //    but insufficient evidence → ONE honest "not enough data yet" notice (A3 cusp), never a warning.
        let evidence = rising.loggedDays
        guard evidence >= HonestyGate.minSample(for: .flare) else {
            return [insufficiencyNotice(evidence: evidence)]
        }

        // 3. SIGNAL B — currently-active aggravating triggers (count only). Reuse
        //    CorrelationEngine.scoredCandidates; never re-derive correlation stats.
        let activeTriggerCount = activeAggravatingTriggers(snapshots, today0: today0, calendar: calendar)

        // 4. SIGNAL C — cycle-phase proximity (boolean). Reuse CyclePhaseMapper.phaseWindows.
        let nearHigherPhase = cyclePhaseProximity(rising: rising, snapshots: snapshots, today0: today0, calendar: calendar)

        // 6. BOUNDED COMBINE (D-03): start at .low (the rising spine exists), +1 notch per secondary signal,
        //    CAP at .medium (NEVER .high), floor .low (bias to withhold). Emit EXACTLY one .flare Insight.
        let confidence = boundedConfidence(activeTriggerCount: activeTriggerCount, nearHigherPhase: nearHigherPhase)
        guard confidence >= .low else { return [] }   // bias to withhold (defensive; .low is the floor).

        let note = contributingNote(activeTriggerCount: activeTriggerCount, nearHigherPhase: nearHigherPhase)
        return [makeFlareInsight(confidence: confidence, evidence: evidence, contributing: note)]
    }

    // MARK: - Signal A: the REQUIRED rising-severity spine (compose TrendsEngine, never re-derive).

    /// The dominant logged symptom whose RECENT severity is rising, with the evidence day-count it is based
    /// on — or `nil` if NO symptom is rising (→ no warning, honest silence). Composes `TrendsEngine`:
    ///   • dominant symptom = the one with the MOST logged-severity days in the `.last7` recent bucket
    ///     (tie → lowest `rawValue`, deterministic).
    ///   • rising iff `TrendsEngine.direction(recent .last7 mean vs prior .last7 mean) == .up` (the same
    ///     coarse, banded, non-causal direction the trends engine uses — composed, not re-derived).
    ///   • loggedDays = the days-with-a-logged-severity for that symptom in the `.last30` recent bucket —
    ///     the substantial-personal-history evidence the 21-day floor is measured against.
    private struct RisingSignal {
        let symptom: SymptomType
        let loggedDays: Int
    }

    private static func risingSeveritySignal(
        _ snapshots: [DaySnapshot],
        today0: Date,
        calendar: Calendar
    ) -> RisingSignal? {
        // The recent vs immediately-prior short windows (TrendsEngine's bucket math, composed verbatim).
        let recent7 = TrendsEngine.bucket(snapshots, window: .last7, endingAt: today0, calendar: calendar)
        guard let prior7 = TrendsEngine.priorBucket(snapshots, window: .last7, endingAt: today0, calendar: calendar) else {
            return nil
        }
        // The wider recent window the evidence count is drawn from (substantial-history floor, D-02).
        let recent30 = TrendsEngine.bucket(snapshots, window: .last30, endingAt: today0, calendar: calendar)

        // Dominant symptom: most logged-severity days in the recent short bucket; tie → lowest rawValue.
        let typesLogged = Set(recent7.flatMap { snap in
            snap.symptoms.filter { $0.severity != nil }.map(\.type)
        })
        guard !typesLogged.isEmpty else { return nil }

        func loggedSeverityDays(_ bucket: [DaySnapshot], _ type: SymptomType) -> Int {
            bucket.filter { snap in snap.symptoms.contains { $0.type == type && $0.severity != nil } }.count
        }

        let dominant = typesLogged.sorted { a, b in
            let da = loggedSeverityDays(recent7, a)
            let db = loggedSeverityDays(recent7, b)
            if da != db { return da > db }
            return a.rawValue < b.rawValue
        }.first!

        // Rising iff the recent mean is meaningfully HIGHER than the prior mean (TrendsEngine.direction).
        guard let recentMean = TrendsEngine.meanSeverity(recent7, type: dominant) else { return nil }
        let priorMean = TrendsEngine.meanSeverity(prior7, type: dominant)
        let dir = TrendsEngine.direction(recentMean: recentMean, priorMean: priorMean, window: .last7)
        guard dir == .up else { return nil }

        return RisingSignal(symptom: dominant, loggedDays: loggedSeverityDays(recent30, dominant))
    }

    // MARK: - Signal B: currently-active aggravating triggers (reuse CorrelationEngine, never re-derive).

    /// The number of `.aggravating` correlation survivors whose factor the user logged on at least one
    /// recent (`.last30`) day. Reuses `CorrelationEngine.scoredCandidates` — runs NO correlation stats of
    /// its own (D-01). "Currently active" means the factor still shows up in the recent window, so a stale
    /// trigger the user no longer logs does not inflate the warning.
    private static func activeAggravatingTriggers(
        _ snapshots: [DaySnapshot],
        today0: Date,
        calendar: Calendar
    ) -> Int {
        let findings = CorrelationEngine.scoredCandidates(from: snapshots, asOf: today0, calendar: calendar)
        guard !findings.isEmpty else { return 0 }

        let recent30 = TrendsEngine.bucket(snapshots, window: .last30, endingAt: today0, calendar: calendar)
        // The set of factor keys the user logged as PRESENT on at least one recent day.
        let recentlyActiveFactorKeys = activeFactorKeys(in: recent30)

        // A finding's signalKey is `correlation.<factor.key>.<target…>`; the factor key is the first two
        // dot-components after the `correlation.` prefix (`food.dairy`, `sleep.poor`, …). Count the
        // aggravating findings whose factor is still recently active.
        var count = 0
        for finding in findings where finding.direction == .aggravating {
            guard let factorKey = factorKey(fromCorrelationSignalKey: finding.signalKey) else { continue }
            if recentlyActiveFactorKeys.contains(factorKey) { count += 1 }
        }
        return count
    }

    /// The set of `Factor.key`s that are `.present` on at least one day in `bucket` (the recently-active
    /// factors). Mirrors `CorrelationEngine`'s factor enumeration but only needs presence, not a stat.
    private static func activeFactorKeys(in bucket: [DaySnapshot]) -> Set<String> {
        var keys: Set<String> = []
        for snap in bucket {
            for tag in snap.foodTags { keys.insert(Factor.food(tag).key) }
            if let type = snap.activityType {
                keys.insert(Factor.activityPresent.key)
                keys.insert(Factor.activity(type).key)
            }
            if snap.sleepQuality == .poor { keys.insert(Factor.sleepPoor.key) }
            if let stress = snap.stress, stress >= Factor.highStressThreshold { keys.insert(Factor.stressHigh.key) }
            if let mood = snap.mood, mood <= Factor.lowMoodThreshold { keys.insert(Factor.moodLow.key) }
            if !snap.medicationsTaken.isEmpty { keys.insert(Factor.medicationTaken.key) }
            if snap.tookPRN { keys.insert(Factor.prnTaken.key) }
        }
        return keys
    }

    /// The two-component `<family>.<value>` factor key from a `correlation.<factor.key>.<target…>`
    /// signalKey (`food.dairy`, `sleep.poor`, `cyclePhase.luteal`, …), or `nil` if the shape is unexpected.
    private static func factorKey(fromCorrelationSignalKey key: String) -> String? {
        let parts = key.split(separator: ".")
        guard parts.count >= 3, parts[0] == "correlation" else { return nil }
        return parts[1...2].joined(separator: ".")
    }

    // MARK: - Signal C: cycle-phase proximity (reuse CyclePhaseMapper, never re-derive).

    /// `true` iff `today` falls in an estimated phase window AND that phase is the one with the MOST logged
    /// dominant-symptom days historically (a "historically higher-symptom phase"). Reuses
    /// `CyclePhaseMapper.phaseWindows` — inherits []-on-suppressed/irregular/<2-cycle (no windows → false).
    private static func cyclePhaseProximity(
        rising: RisingSignal,
        snapshots: [DaySnapshot],
        today0: Date,
        calendar: Calendar
    ) -> Bool {
        let windows = estimatedPhaseWindows(snapshots, today0: today0, calendar: calendar)
        guard !windows.isEmpty else { return false }

        // Which phase is today in? (nil → not near any phase window.)
        guard let todayPhase = CyclePhaseMapper.phase(for: today0, in: windows, calendar: calendar) else {
            return false
        }

        // The historically-highest dominant-symptom phase: count logged dominant-symptom days per phase
        // across all history. today's phase must BE that phase to count as "near a higher-symptom phase".
        var perPhaseSymptomDays: [CyclePhase: Int] = [:]
        for snap in snapshots {
            guard let phase = CyclePhaseMapper.phase(for: snap.day, in: windows, calendar: calendar) else { continue }
            if snap.symptoms.contains(where: { $0.type == rising.symptom }) {
                perPhaseSymptomDays[phase, default: 0] += 1
            }
        }
        // Deterministic argmax: most days wins; tie → lowest rawValue.
        let highest = perPhaseSymptomDays
            .sorted { a, b in a.value != b.value ? a.value > b.value : a.key.rawValue < b.key.rawValue }
            .first?.key
        guard let highest, perPhaseSymptomDays[highest, default: 0] > 0 else { return false }
        return todayPhase == highest
    }

    /// Re-derive the current cycle's estimated phase windows the SAME way `CyclePhaseMapper.insights` does
    /// (lines 36–61), reused verbatim (D-04/D-17). Suppressed/irregular/<2-cycle → `[]` (no proximity).
    private static func estimatedPhaseWindows(
        _ snapshots: [DaySnapshot],
        today0: Date,
        calendar: Calendar
    ) -> [CyclePhaseMapper.PhaseWindow] {
        let bleedingDays = snapshots.filter { $0.flow != nil }.map(\.day)
        let runs = CyclePhaseMapper.periodRuns(bleedingDays, calendar: calendar)
        let starts = runs.compactMap(\.first)
        let gaps = CyclePhaseMapper.consecutiveGaps(starts, calendar: calendar)
        guard gaps.count >= 2, let lastStart = starts.last, let lastRun = runs.last else { return [] }
        let median = CyclePhaseMapper.medianInt(gaps)
        let spread = CyclePhaseMapper.spread(gaps)
        guard CyclePhaseMapper.phaseConfidence(cycleCount: gaps.count, spread: spread) != nil else { return [] }
        return CyclePhaseMapper.phaseWindows(
            lastStart: lastStart, lastRun: lastRun, median: median, spread: spread, calendar: calendar
        )
    }

    // MARK: - Bounded combine (D-03 — start .low, +1 notch each, CAP .medium, NEVER .high).

    /// Start at `.low` (the rising spine exists); `+1 notch` if any aggravating trigger is currently
    /// active; `+1 notch` if today is near a historically higher-symptom phase; CAP at `.medium`. NEVER
    /// `.high` — a flare warning is uncertainty-first even at its strongest (mirrors CyclePhaseMapper's cap).
    private static func boundedConfidence(activeTriggerCount: Int, nearHigherPhase: Bool) -> InsightConfidence {
        var notches = 1   // .low — the rising spine is present.
        if activeTriggerCount >= 1 { notches += 1 }
        if nearHigherPhase { notches += 1 }
        let capped = min(notches, InsightConfidence.medium.rawValue)   // cap at .medium (raw 2), never .high.
        return InsightConfidence(rawValue: capped) ?? .low
    }

    // MARK: - The "what's contributing" note (from the TYPED signals, NEVER parsed copy — RESEARCH).

    /// The curated `FlareCopyText.contributing` line — appended ONLY when at least one secondary signal
    /// fired. Derived from the TYPED signals (`activeTriggerCount` / `nearHigherPhase`), never parsed from
    /// the audited copy (the fragile-substring anti-pattern). `nil` when only the bare rising spine exists.
    private static func contributingNote(activeTriggerCount: Int, nearHigherPhase: Bool) -> String? {
        (activeTriggerCount >= 1 || nearHigherPhase) ? FlareCopyText.contributing : nil
    }

    // MARK: - Insight construction (stable signalKey "flare.warning", window .allTime, words-only).

    /// The one bounded `.flare` warning. Stable window-independent signalKey "flare.warning" (D-07) so
    /// dismissal survives recompute; `.allTime` window; words-only (`points: []`); `sampleSize == evidence`
    /// (the rising signal's logged-day count, self-gated on the SAME `minSample(.flare)` the gate enforces).
    private static func makeFlareInsight(
        confidence: InsightConfidence,
        evidence: Int,
        contributing: String?
    ) -> Insight {
        let detail = contributing.map { "\(FlareCopyText.detail) \($0)" } ?? FlareCopyText.detail
        return Insight.derived(
            kind: .flare,
            window: .allTime,
            signalKey: "flare.warning",
            headline: FlareCopyText.headline,
            detail: detail,
            confidence: confidence,
            points: [],
            sampleSize: evidence
        )
    }

    /// The single honest "not enough data yet" notice (A3 cusp: a real recent rise but sub-21 evidence).
    /// SHARES the SAME signalKey "flare.warning" (one flare signal); the typed `isInsufficiencyNotice`
    /// marker (NOT a signalKey suffix — WR-01) carries it past the gate's min-sample + confidence floor.
    private static func insufficiencyNotice(evidence: Int) -> Insight {
        Insight.derived(
            kind: .flare,
            window: .allTime,
            signalKey: "flare.warning",
            headline: FlareCopyText.insufficientTitle,
            detail: FlareCopyText.insufficientSubtitle,
            confidence: .none,
            points: [],
            sampleSize: evidence,
            isInsufficiencyNotice: true
        )
    }
}

// MARK: - Package-internal curated copy mirror (BYTE-IDENTICAL to the app's audited FlareCopy).

/// PACKAGE-LOCAL MIRROR of the app's `FlareCopy` (`Sources/EndoApp/Features/Insights/FlareCopy.swift`):
/// the package CANNOT import the app target (SC1), so the SAME curated, calm, uncertainty-first,
/// NON-alarmist strings are duplicated here as data and MUST stay byte-identical with the app surface.
/// The app surface is audited by `CopyAuditTests`; THIS mirror is proven clean by the HonestyGate
/// zero-forbidden-terms scan (alarmist + causal + imperative, D-04) and the `FlareCopyDriftTests` byte
/// identity guard.
///
/// SUBSTRING TRAP (Pitfall 2): every word is CURATED to avoid the forbidden substrings ENTIRELY. The two
/// LOAD-BEARING traps Phase 12 dodges (verified BY HAND against the alarmist ∪ causal ∪ imperative ∪
/// gendered ∪ numeric union):
///   • "more difficult days" — no "her" (avoids the "higher" gendered trap), no "predict", no alarmist
///     word, no imperative ("difficult" has no "cut"/"use"; "more" is clean).
///   • "keep an eye on" / "discuss with your clinician" — no imperative substring (discuss has no
///     "cut"/"use"), no causal, no alarmist.
///   • "a pattern in your own data" — matches `ClaimsPolicy.allowedFraming`; no forbidden substring.
///   • "Based on a recent rising pattern and a factor you have logged recently." — NO "predict", NO causal
///     ("rising"/"pattern"/"factor" are clean), NO imperative; "recently" has no "certain", "factor" has
///     no alarmist substring.
///   • The detail deliberately OMITS "not a prediction" (that would trip "predict") — the
///     not-medical-advice framing is carried by the pinned DisclaimerBanner + "discuss with your clinician".
///   • "warning" appears ONLY in `sectionFlare` ("Flare early-warning", D-07) — the one sanctioned use;
///     it is deliberately EXCLUDED from the alarmist list so the header is legal.
enum FlareCopyText {

    /// The "Flare early-warning" section title (the ONLY sanctioned use of "warning", D-07).
    static let sectionFlare = "Flare early-warning"

    /// The calm, uncertainty-first headline ("more difficult days", NOT "higher-symptom days").
    static let headline = "Your recent pattern looks similar to times before more difficult days."

    /// The honest "your own data, keep an eye on it, discuss with your clinician" detail line.
    static let detail = "This is a pattern in your own data — something to keep an eye on and discuss with your clinician."

    /// The optional "what's contributing" line, appended ONLY when a secondary signal fired (from the TYPED
    /// engine signals, never parsed copy).
    static let contributing = "Based on a recent rising pattern and a factor you have logged recently."

    /// The insufficiency-notice title (reuses the sparse-title wording for parity).
    static let insufficientTitle = "Not enough data yet."

    /// The insufficiency-notice subtitle — the honest "keep logging" framing.
    static let insufficientSubtitle = "Keep logging and this fills in over time."
}

// MARK: - Cross-boundary drift-guard surface (WR-02 / WR-03) — the ONLY public window onto the mirrors.

/// PUBLIC, TEST-SUPPORT-ONLY window onto the package-internal mirrors the APP duplicates and must keep in
/// sync (the app↔package "drift guard" precedent established by `RecommendationsDriftGuard`). The app test
/// target sees ONLY the package's PUBLIC API (it imports `EndoInsights` as a release product, NOT
/// `@testable`), so the package-internal `FlareCopyText` strings and `ForbiddenTerms.alarmist` list are
/// invisible to it. This shim forwards those mirrored surfaces — and NOTHING else — so an app-side test
/// (`FlareCopyDriftTests`) can assert the two halves are byte-identical / set-identical and a future edit
/// to ONE side without the other fails a test. The forwarded values are pure data with no behavior;
/// exposing them does NOT widen the engine's real API surface or the Foundation-only boundary
/// (BoundaryLintTests stays green — this imports nothing).
public enum FlareDriftGuard {

    /// The package-internal `FlareCopyText.headline` mirror, for byte-identity comparison against the app's
    /// audited `FlareCopy.headline` (WR-02).
    public static var packageHeadline: String { FlareCopyText.headline }

    /// The package-internal `FlareCopyText.detail` mirror (WR-02).
    public static var packageDetail: String { FlareCopyText.detail }

    /// The package-internal `FlareCopyText.contributing` mirror (WR-02).
    public static var packageContributing: String { FlareCopyText.contributing }

    /// The package-internal `FlareCopyText.sectionFlare` mirror (WR-02).
    public static var packageSectionFlare: String { FlareCopyText.sectionFlare }

    /// The package-internal `FlareCopyText.insufficientTitle` mirror (WR-02).
    public static var packageInsufficientTitle: String { FlareCopyText.insufficientTitle }

    /// The package-internal `FlareCopyText.insufficientSubtitle` mirror (WR-02).
    public static var packageInsufficientSubtitle: String { FlareCopyText.insufficientSubtitle }

    /// The package-internal alarmist forbidden list (`ForbiddenTerms.alarmist`), for set-equality parity
    /// against the app's `ClaimsPolicy.forbiddenAlarmist` (WR-03). The two lists are documented to stay
    /// BYTE-SET-IDENTICAL; `FlareCopyDriftTests` asserts `Set(app) == Set(package)`.
    public static var packageForbiddenAlarmist: [String] { ForbiddenTerms.alarmist }
}
