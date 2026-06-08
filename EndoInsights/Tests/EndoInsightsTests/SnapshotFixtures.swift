// EndoInsights — synthetic `[DaySnapshot]` fixture DSL for the TrendsEngine suite (Phase 5, D-11).
//
// Lifts the fixed-UTC-calendar + fixed-anchor + day-offset builder from `CycleMathTests.swift` so
// day-bucketing is timezone-stable in CI and `today` is injected (no `Date.now` anywhere). Every
// builder returns in-memory `[DaySnapshot]` value types — NO `ModelContainer`, NO SwiftData (the SC1
// separation the package tests are the proof of). The keystone is `suppressedNoBleedingFixture` (SC3):
// months of suppressed, no-bleeding days that must yield NO bleeding/cycle Insight.
import Testing
import Foundation
@testable import EndoInsights

/// Shared fixture helpers for the TrendsEngine suites.
enum Fixtures {

    // A fixed, deterministic calendar (UTC) so day-bucketing is timezone-stable in CI.
    static let calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    /// A fixed anchor: 2025-01-01 00:00:00 UTC (matches `CycleMathTests`).
    static let anchor = Date(timeIntervalSince1970: 1_735_689_600)

    /// `anchor + offset` days, at start-of-day in the fixed calendar.
    static func day(_ offset: Int) -> Date {
        calendar.startOfDay(for: calendar.date(byAdding: .day, value: offset, to: anchor)!)
    }

    /// A single-day `DaySnapshot` builder defaulting EVERY field to absent/nil/empty.
    ///
    /// Pass `offset` (days from `anchor`) and only the signals under test; everything else stays
    /// honestly unlogged (`nil`/`[]`/`false`).
    static func snapshot(
        offset: Int,
        symptoms: [SymptomPoint] = [],
        pain: [PainPoint] = [],
        flow: FlowIntensity? = nil,
        clots: Bool = false,
        foodTags: [FoodTag] = [],
        activityType: ActivityType? = nil,
        activityMinutes: Double? = nil,
        activityIntensity: ActivityIntensity? = nil,
        sleepHours: Double? = nil,
        sleepQuality: SleepQuality? = nil,
        medicationsTaken: [UUID] = [],
        tookPRN: Bool = false,
        stress: Int? = nil,
        mood: Int? = nil,
        hasNote: Bool = false,
        suppressed: Bool = false
    ) -> DaySnapshot {
        DaySnapshot(
            day: day(offset),
            symptoms: symptoms,
            pain: pain,
            flow: flow,
            clots: clots,
            foodTags: foodTags,
            activityType: activityType,
            activityMinutes: activityMinutes,
            activityIntensity: activityIntensity,
            sleepHours: sleepHours,
            sleepQuality: sleepQuality,
            medicationsTaken: medicationsTaken,
            tookPRN: tookPRN,
            stress: stress,
            mood: mood,
            hasNote: hasNote,
            suppressed: suppressed
        )
    }

    // MARK: SC3 — the suppressed / no-bleeding-for-months keystone fixture.

    /// Months of `suppressed == true`, `flow == nil` days ending at `today` (offset 0).
    ///
    /// Every day carries SOME log (a logged symptom + stress) so the days exist in the stream, but
    /// NO bleeding is ever logged and `suppressed` is always true. `TrendsEngine.trends` over this MUST
    /// emit no bleeding-frequency Insight and no `.cyclePhase` Insight (gate: `!suppressed && bleedingDayCount >= 1`).
    ///
    /// - Parameter monthsBack: how many ~30-day months of history to synthesize (default 4).
    static func suppressedNoBleedingFixture(monthsBack: Int = 4) -> [DaySnapshot] {
        let totalDays = monthsBack * 30
        return (0..<totalDays).map { i in
            snapshot(
                offset: -i,
                symptoms: [SymptomPoint(type: .pelvicPain, severity: 4)],
                stress: 5,
                suppressed: true
            )
        }
    }

    // MARK: A regular symptom fixture clearing the threshold, with a recent-vs-prior delta.

    /// A symptom logged densely enough to clear the threshold in 7/30/90 windows, with a HIGHER recent
    /// severity than the immediately-prior window so `direction == .up` is testable.
    ///
    /// Recent 7-day window (offsets 0…-6): severity 6. Prior 7-day window (offsets -7…-13): severity 2.
    /// Earlier days (-14…-89): severity 3 to populate last30/last90/allTime.
    static func risingPelvicPainFixture() -> [DaySnapshot] {
        var days: [DaySnapshot] = []
        for i in 0...6 {   // recent 7-day window — high severity
            days.append(snapshot(offset: -i, symptoms: [SymptomPoint(type: .pelvicPain, severity: 6)]))
        }
        for i in 7...13 {  // immediately-prior 7-day window — low severity
            days.append(snapshot(offset: -i, symptoms: [SymptomPoint(type: .pelvicPain, severity: 2)]))
        }
        for i in 14...89 { // earlier history — mid severity (populates last30/last90/allTime)
            days.append(snapshot(offset: -i, symptoms: [SymptomPoint(type: .pelvicPain, severity: 3)]))
        }
        return days
    }

    // MARK: A sub-threshold fixture (< minLoggedDays) for the not-enough-data case.

    /// Exactly TWO logged days of a symptom — below the `minLoggedDays` (3) threshold in every window.
    static func subThresholdFixture() -> [DaySnapshot] {
        [
            snapshot(offset: 0, symptoms: [SymptomPoint(type: .fatigue, severity: 5)]),
            snapshot(offset: -2, symptoms: [SymptomPoint(type: .fatigue, severity: 5)]),
        ]
    }

    // MARK: A logged-0-vs-unlogged-nil pair for the honesty mean test (absence ≠ 0).

    /// Three days each logging stress 0 → mean stress 0.
    static func loggedZeroStressFixture() -> [DaySnapshot] {
        (0...2).map { snapshot(offset: -$0, symptoms: [SymptomPoint(type: .pelvicPain, severity: 3)], stress: 0) }
    }

    /// Three days logging stress 4, plus three days that NEVER log stress (nil) but DO log a symptom so
    /// the days exist. The nil days must NOT pull the stress mean toward 0 (excluded from the denominator).
    static func unloggedStressFixture() -> [DaySnapshot] {
        var days = (0...2).map { snapshot(offset: -$0, symptoms: [SymptomPoint(type: .pelvicPain, severity: 3)], stress: 4) }
        days += (3...5).map { snapshot(offset: -$0, symptoms: [SymptomPoint(type: .pelvicPain, severity: 3)], stress: nil) }
        return days
    }

    /// A fixture with one in-window bleeding day and NO suppression — the bleeding trend SHOULD emit.
    static func bleedingFixture() -> [DaySnapshot] {
        [
            snapshot(offset: 0, flow: .medium),
            snapshot(offset: -1, symptoms: [SymptomPoint(type: .pelvicPain, severity: 3)]),
            snapshot(offset: -2, symptoms: [SymptomPoint(type: .pelvicPain, severity: 3)]),
        ]
    }
}
