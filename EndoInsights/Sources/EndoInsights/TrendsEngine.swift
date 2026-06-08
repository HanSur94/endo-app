// EndoInsights — the pure, deterministic descriptive-trends engine (Phase 5, INST-01 / SC2).
//
// Foundation ONLY — NO `import SwiftData`, NO `import SwiftUI` (SC1; the BoundaryLint is the proof).
// Mirrors `CycleMath`'s template exactly: an `enum` namespace, an INJECTED `today` + `calendar` (never
// `Date.now`/`Date()` inside), pure helpers, and `Equatable` `Insight` outputs. Same inputs + same
// injected `today` → an equal `[Insight]`.
//
// HONESTY (D-03/D-08/D-16, the endo pitfalls):
//   • absence ≠ 0 — a nil reading is EXCLUDED from a mean's denominator, never summed as 0.
//   • no phantom cycle — the bleeding trend is GATED on `!suppressed && bleedingDayCount >= 1`
//     (mirrors `CycleMath.summarize`'s `if isSuppressed { return .suppressed }` short-circuit — SC3).
//   • no false precision — below the per-trend `minLoggedDays` threshold the engine emits an explicit
//     "not enough data" Insight instead of a misleading trend (D-08).
//   • descriptive, non-causal copy — no "trigger/causes/cause/will" (D-16); the FORMAL
//     confidence-gating/withholding honesty layer is Phase 6, not here.
import Foundation

/// Pure descriptive-trends engine. No store, no UI, no I/O, no logging — value-in/value-out.
public enum TrendsEngine {

    // MARK: - Tunable, unit-asserted constants (no buried magic numbers — D-08).

    /// Minimum logged days for a signal in a window before a confident trend is emitted. Below this the
    /// engine emits an explicit "not enough data" Insight (mirrors `CycleMath`'s "≥ 2 cycles" spirit).
    public static let minLoggedDays = 3

    /// The half-band (in the signal's units) within which a recent-vs-prior delta reads as "steady",
    /// avoiding false precision on tiny movements.
    public static let directionBand = 0.5

    // MARK: - The single public entry point (mirrors CycleMath.summarize).

    /// Descriptive trends over the 7/30/90-day + all-time windows anchored on absolute calendar dates
    /// ending at the injected `today` (NOT cycle phase — that is Phase 6).
    ///
    /// - Parameters:
    ///   - snapshots: the per-day value snapshots (order-independent).
    ///   - today: injected reference date (never read from the clock inside) → deterministic.
    ///   - calendar: injected calendar for day-bucketing (defaults to `.current`).
    public static func trends(
        from snapshots: [DaySnapshot],
        asOf today: Date,
        calendar: Calendar = .current
    ) -> [Insight] {
        let today0 = calendar.startOfDay(for: today)
        var out: [Insight] = []

        for window in TrendWindow.allCases {
            let recent = bucket(snapshots, window: window, endingAt: today0, calendar: calendar)
            // The immediately-prior equal-length window (nil for allTime → no direction).
            let prior = priorBucket(snapshots, window: window, endingAt: today0, calendar: calendar)

            out += symptomTrends(recent: recent, prior: prior, window: window)
            out += factorTrends(recent: recent, prior: prior, window: window)
            out += bleedingTrend(recent: recent, window: window)
        }
        return out
    }

    // MARK: - Window bucketing (DST-safe day math — never epoch arithmetic).

    /// The snapshots whose `day` falls in `[end-(N-1) … end]` inclusive (allTime = everything).
    static func bucket(
        _ snapshots: [DaySnapshot],
        window: TrendWindow,
        endingAt end0: Date,
        calendar: Calendar
    ) -> [DaySnapshot] {
        guard let n = windowLength(window) else { return snapshots }
        let start0 = calendar.date(byAdding: .day, value: -(n - 1), to: end0)!
        return snapshots.filter { snap in
            let d = calendar.startOfDay(for: snap.day)
            return d >= start0 && d <= end0
        }
    }

    /// The immediately-prior equal-length window `[end-(2N-1) … end-N]`; nil for allTime.
    static func priorBucket(
        _ snapshots: [DaySnapshot],
        window: TrendWindow,
        endingAt end0: Date,
        calendar: Calendar
    ) -> [DaySnapshot]? {
        guard let n = windowLength(window) else { return nil }
        let priorEnd = calendar.date(byAdding: .day, value: -n, to: end0)!
        let priorStart = calendar.date(byAdding: .day, value: -(2 * n - 1), to: end0)!
        return snapshots.filter { snap in
            let d = calendar.startOfDay(for: snap.day)
            return d >= priorStart && d <= priorEnd
        }
    }

    /// Day-length of a finite window; nil for `.allTime`.
    static func windowLength(_ window: TrendWindow) -> Int? {
        switch window {
        case .last7:  return 7
        case .last30: return 30
        case .last90: return 90
        case .allTime: return nil
        }
    }

    // MARK: - Per-symptom severity mean + frequency (D-05/D-07).

    static func symptomTrends(recent: [DaySnapshot], prior: [DaySnapshot]?, window: TrendWindow) -> [Insight] {
        let typesLogged = Set(recent.flatMap { $0.symptoms.map(\.type) })
        var out: [Insight] = []
        let daysWithAnyLog = recent.count

        for type in typesLogged.sorted(by: { $0.rawValue < $1.rawValue }) {
            let label = displayName(type)
            let daysPresent = recent.filter { $0.symptoms.contains { $0.type == type } }.count

            // --- Frequency trend (days-present / days-with-any-log) ---
            // Gated on enough TRACKED days in the window (days-with-any-log), so a legitimate "2 of 4"
            // frequency emits, while a window with too little data overall surfaces "not enough data".
            if daysWithAnyLog >= minLoggedDays {
                out.append(Insight.derived(
                    kind: .trend, window: window, signalKey: "symptom.\(type.rawValue).frequency",
                    headline: "\(label) logged on \(daysPresent) of \(daysWithAnyLog) logged days",
                    detail: "You logged \(label.lowercased()) on \(daysPresent) of the \(daysWithAnyLog) days you tracked \(windowPhrase(window)).",
                    confidence: confidence(forLoggedDays: daysPresent),
                    points: frequencyPoints(recent, predicate: { $0.symptoms.contains { $0.type == type } })
                ))
            } else {
                out.append(notEnoughData(signal: "symptom.\(type.rawValue).frequency",
                                         label: label, window: window))
            }

            // --- Severity-mean trend (nil severities excluded from the denominator) ---
            let severities = recent.compactMap { snap in
                snap.symptoms.first { $0.type == type }?.severity
            }.map(Double.init)
            if severities.count >= minLoggedDays {
                let mean = average(severities)!
                let dir = direction(
                    recentMean: mean,
                    priorMean: prior.flatMap { meanSeverity($0, type: type) },
                    window: window
                )
                out.append(Insight.derived(
                    kind: .trend, window: window, signalKey: "symptom.\(type.rawValue).severity",
                    headline: "\(label) severity averaged \(format(mean)) \(windowPhrase(window))",
                    detail: "Average logged \(label.lowercased()) severity was \(format(mean))\(directionPhrase(dir, window: window)).",
                    confidence: confidence(forLoggedDays: severities.count),
                    points: severityPoints(recent, type: type) + [InsightPoint(date: lastDay(recent), value: mean)]
                ))
            }
        }
        return out
    }

    // MARK: - Per-factor frequency + stress/mood means (D-05/D-07).

    static func factorTrends(recent: [DaySnapshot], prior: [DaySnapshot]?, window: TrendWindow) -> [Insight] {
        var out: [Insight] = []

        // Stress mean (nil excluded).
        if let stressInsight = scaleMeanTrend(
            recent: recent, prior: prior, window: window,
            value: { $0.stress.map(Double.init) }, label: "stress", signal: "factor.stress.mean") {
            out.append(stressInsight)
        }
        // Mood mean (nil excluded).
        if let moodInsight = scaleMeanTrend(
            recent: recent, prior: prior, window: window,
            value: { $0.mood.map(Double.init) }, label: "mood", signal: "factor.mood.mean") {
            out.append(moodInsight)
        }
        // Poor-sleep-day frequency (denominator = days sleep quality was logged).
        let sleepDays = recent.filter { $0.sleepQuality != nil }.count
        let poorDays = recent.filter { $0.sleepQuality == .poor }.count
        if sleepDays >= minLoggedDays && poorDays >= 1 {
            out.append(Insight.derived(
                kind: .trend, window: window, signalKey: "factor.sleep.poor.frequency",
                headline: "Poor sleep on \(poorDays) of \(sleepDays) logged sleep days",
                detail: "You rated sleep poor on \(poorDays) of the \(sleepDays) days you logged sleep \(windowPhrase(window)).",
                confidence: confidence(forLoggedDays: sleepDays),
                points: frequencyPoints(recent.filter { $0.sleepQuality != nil }, predicate: { $0.sleepQuality == .poor })))
        }
        return out
    }

    static func scaleMeanTrend(
        recent: [DaySnapshot], prior: [DaySnapshot]?, window: TrendWindow,
        value: (DaySnapshot) -> Double?, label: String, signal: String
    ) -> Insight? {
        let readings = recent.compactMap(value)
        guard readings.count >= minLoggedDays else { return nil }
        let mean = average(readings)!
        let priorMean = prior.map { $0.compactMap(value) }.flatMap { $0.isEmpty ? nil : average($0) }
        let dir = direction(recentMean: mean, priorMean: priorMean, window: window)
        return Insight.derived(
            kind: .trend, window: window, signalKey: signal,
            headline: "\(label.capitalized) averaged \(format(mean)) \(windowPhrase(window))",
            detail: "Average logged \(label) was \(format(mean))\(directionPhrase(dir, window: window)).",
            confidence: confidence(forLoggedDays: readings.count),
            points: [InsightPoint(date: lastDay(recent), value: mean)])
    }

    // MARK: - Bleeding-day frequency, GATED on !suppressed && bleedingDayCount >= 1 (SC3).

    static func bleedingTrend(recent: [DaySnapshot], window: TrendWindow) -> [Insight] {
        // The suppressed short-circuit, mirroring CycleMath's `if isSuppressed`. If ANY in-window day is
        // suppressed, OR there is no bleeding logged, emit NO bleeding/cycle trend (the no-phantom proof).
        let anySuppressed = recent.contains { $0.suppressed }
        let bleedingDayCount = recent.filter { $0.flow != nil }.count
        guard !anySuppressed && bleedingDayCount >= 1 else { return [] }

        let total = recent.count
        return [Insight.derived(
            kind: .trend, window: window, signalKey: "bleeding.frequency",
            headline: "Bleeding logged on \(bleedingDayCount) of \(total) days",
            detail: "You logged bleeding on \(bleedingDayCount) of the \(total) days you tracked \(windowPhrase(window)).",
            confidence: confidence(forLoggedDays: bleedingDayCount),
            points: frequencyPoints(recent, predicate: { $0.flow != nil }))]
    }

    // MARK: - "Not enough data" honesty Insight (D-08).

    static func notEnoughData(signal: String, label: String, window: TrendWindow) -> Insight {
        Insight.derived(
            kind: .trend, window: window, signalKey: "\(signal).insufficient",
            headline: "\(label): not enough data yet",
            detail: "Not enough data yet to describe \(label.lowercased()) \(windowPhrase(window)) — keep logging.",
            confidence: .none,
            points: [])
    }

    // MARK: - Pure helpers (each independently testable).

    /// Mean of a non-empty set of values; nil for an empty set (nil is NEVER coerced to 0).
    static func average(_ values: [Double]) -> Double? {
        guard !values.isEmpty else { return nil }
        return values.reduce(0, +) / Double(values.count)
    }

    static func meanSeverity(_ snapshots: [DaySnapshot], type: SymptomType) -> Double? {
        average(snapshots.compactMap { $0.symptoms.first { $0.type == type }?.severity }.map(Double.init))
    }

    /// A coarse, non-causal direction: `.up`/`.down`/`.steady` with a band to avoid false precision.
    /// `nil` prior (allTime) → `.none`.
    enum Direction { case up, down, steady, none }

    static func direction(recentMean: Double, priorMean: Double?, window: TrendWindow) -> Direction {
        guard windowLength(window) != nil, let prior = priorMean else { return .none }
        let delta = recentMean - prior
        if delta > directionBand { return .up }
        if delta < -directionBand { return .down }
        return .steady
    }

    /// Descriptive, NON-CAUSAL, present/past-tense phrasing of a direction (no "trigger/cause/will").
    static func directionPhrase(_ dir: Direction, window: TrendWindow) -> String {
        switch dir {
        case .up:     return ", higher than the prior period"
        case .down:   return ", lower than the prior period"
        case .steady: return ", about the same as the prior period"
        case .none:   return ""   // allTime / no prior window → omit direction entirely
        }
    }

    static func confidence(forLoggedDays days: Int) -> InsightConfidence {
        switch days {
        case 0..<minLoggedDays: return .none
        case minLoggedDays..<7: return .low
        case 7..<14:            return .medium
        default:                return .high
        }
    }

    static func format(_ v: Double) -> String {
        String(format: "%.1f", v)
    }

    static func windowPhrase(_ window: TrendWindow) -> String {
        switch window {
        case .last7:  return "in the last 7 days"
        case .last30: return "in the last 30 days"
        case .last90: return "in the last 90 days"
        case .allTime: return "across all your logs"
        }
    }

    static func displayName(_ type: SymptomType) -> String {
        switch type {
        case .pelvicPain:  return "Pelvic pain"
        case .dysmenorrhea: return "Period pain"
        case .dyspareunia: return "Painful sex"
        case .giEndoBelly: return "Endo belly / GI"
        case .urinary:     return "Urinary symptoms"
        case .fatigue:     return "Fatigue"
        case .brainFog:    return "Brain fog"
        case .lowMood:     return "Low mood"
        case .headache:    return "Headache"
        case .backLegPain: return "Back / leg pain"
        case .other:       return "Other symptom"
        }
    }

    static func lastDay(_ snapshots: [DaySnapshot]) -> Date {
        snapshots.map(\.day).max() ?? Date(timeIntervalSince1970: 0)
    }

    static func severityPoints(_ snapshots: [DaySnapshot], type: SymptomType) -> [InsightPoint] {
        snapshots
            .compactMap { snap -> InsightPoint? in
                guard let sev = snap.symptoms.first(where: { $0.type == type })?.severity else { return nil }
                return InsightPoint(date: snap.day, value: Double(sev))
            }
            .sorted { $0.date < $1.date }
    }

    static func frequencyPoints(_ snapshots: [DaySnapshot], predicate: (DaySnapshot) -> Bool) -> [InsightPoint] {
        snapshots
            .map { InsightPoint(date: $0.day, value: predicate($0) ? 1 : 0) }
            .sorted { $0.date < $1.date }
    }
}
