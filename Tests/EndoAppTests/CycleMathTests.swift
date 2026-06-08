// Requirement: CYCL-02 / CYCL-03 / CYCL-04 — derived cycle math (never an assumed 28-day cycle).
//
// The keystone fixtures (Plan 02-03, TDD): regular / irregular / suppressed / sparse + edges.
// These lock the no-phantom-cycle contract (D-08/D-09/D-10): cycle is DERIVED from bleeding runs,
// suppressed emits NOTHING, < 2 cycles says "not enough data yet", and the next-period estimate is
// ALWAYS a RANGE with a confidence — never a single hard date, never 28 by default.
//
// Determinism: `today` is injected (no `Date.now`/`Date()` inside CycleMath); same inputs → equal
// `CycleSummary` (Equatable). Fixtures are hand-built from a fixed UTC anchor via a fixed Calendar.
import Testing
import Foundation
@testable import EndoApp

@Suite("CYCL-02/03/04 cycle math")
struct CycleMathTests {

    // A fixed, deterministic calendar (UTC) so day-bucketing is timezone-stable in CI.
    private static let calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    // A fixed anchor: 2025-01-01 00:00:00 UTC.
    private static let anchor = Date(timeIntervalSince1970: 1_735_689_600)

    /// `anchor + dayOffset` days, at start-of-day in the fixed calendar.
    private static func day(_ offset: Int) -> Date {
        calendar.date(byAdding: .day, value: offset, to: anchor)!
    }

    /// Build a list of flow days from period-start offsets, each period lasting `length` consecutive days.
    private static func bleedingDays(starts: [Int], length: Int = 4) -> [Date] {
        starts.flatMap { start in (0..<length).map { day(start + $0) } }
    }

    private func summarize(_ days: [Date], suppressed: Bool = false, todayOffset: Int) -> CycleSummary {
        CycleMath.summarize(
            bleedingDays: days,
            isSuppressed: suppressed,
            today: Self.day(todayOffset),
            calendar: Self.calendar
        )
    }

    // MARK: CYCL-02 — regular cycler: median length + a sensible RANGE (never a single hard date).

    @Test func regularCycler_medianLength_sensibleRange() async throws {
        // Period starts at days 0, 28, 56, 84 → four runs → three ~28-day gaps (regular, low spread).
        let days = Self.bleedingDays(starts: [0, 28, 56, 84])
        let summary = summarize(days, todayOffset: 90)

        #expect(summary.isSuppressed == false)
        #expect(summary.hasEnoughData == true)
        // cycleDay = days since last period start (day 84) to today (day 90), +1 → 7.
        #expect(summary.cycleDay == 7)
        #expect(summary.cycleDay! > 0)
        // nextPeriod is a non-empty RANGE centered near lastStart(84) + median(28) = day 112.
        let next = try #require(summary.nextPeriod)
        #expect(next.lowerBound <= next.upperBound)
        #expect(next.lowerBound <= Self.day(112) && Self.day(112) <= next.upperBound)
        // A regular cycler is high confidence.
        #expect(summary.confidence == .high)
        // Phase stays nil this phase (Phase 6 populates named phases — Open Question 2).
        #expect(summary.phase == nil)
    }

    // MARK: CYCL-02 — irregular cycler: wider range / lower confidence.

    @Test func irregularCycler_widerRange_lowerConfidence() async throws {
        // Gaps of 24, 35, 27, 40 days → high spread, irregular.
        let starts = [0, 24, 59, 86, 126]
        let days = Self.bleedingDays(starts: starts)
        let summary = summarize(days, todayOffset: 130)

        #expect(summary.hasEnoughData == true)
        let irregularNext = try #require(summary.nextPeriod)
        // Confidence is strictly lower than the regular fixture's .high.
        #expect(summary.confidence == .low || summary.confidence == .medium)
        #expect(summary.confidence.rawValue < Confidence.high.rawValue)

        // Compare against the regular fixture's range width — irregular must be WIDER.
        let regularDays = Self.bleedingDays(starts: [0, 28, 56, 84])
        let regular = summarize(regularDays, todayOffset: 90)
        let regularNext = try #require(regular.nextPeriod)
        let irregularWidth = irregularNext.upperBound.timeIntervalSince(irregularNext.lowerBound)
        let regularWidth = regularNext.upperBound.timeIntervalSince(regularNext.lowerBound)
        #expect(irregularWidth > regularWidth)
    }

    // MARK: CYCL-03 — suppressed: emits nothing (the no-phantom-cycle proof).

    @Test func suppressed_emitsNothing() async throws {
        // Even with a full regular bleeding history, suppressed must emit NOTHING cycle-related.
        let days = Self.bleedingDays(starts: [0, 28, 56, 84])
        let summary = summarize(days, suppressed: true, todayOffset: 90)

        #expect(summary.isSuppressed == true)
        #expect(summary.cycleDay == nil)
        #expect(summary.phase == nil)
        #expect(summary.nextPeriod == nil)
        #expect(summary.confidence == .none)
        #expect(summary.hasEnoughData == false)
        #expect(summary == .suppressed)
    }

    // MARK: CYCL-04 — sparse (<2 cycles): not enough data yet.

    @Test func sparse_underTwoCycles_notEnoughData() async throws {
        // Two period runs → only ONE start-to-start gap → < 2 gaps → not enough data.
        let days = Self.bleedingDays(starts: [0, 28])
        let summary = summarize(days, todayOffset: 30)

        #expect(summary.isSuppressed == false)
        #expect(summary.hasEnoughData == false)
        #expect(summary.cycleDay == nil)
        #expect(summary.nextPeriod == nil)
        #expect(summary.confidence == .none)
        #expect(summary == .notEnoughData)
    }

    // MARK: EDGE — empty input → not enough data, no crash.

    @Test func emptyInput_notEnoughData() async throws {
        let summary = summarize([], todayOffset: 0)
        #expect(summary == .notEnoughData)
    }

    // MARK: EDGE — a single bleeding day → one run, zero gaps → not enough data.

    @Test func singleBleedingDay_notEnoughData() async throws {
        let summary = summarize([Self.day(0)], todayOffset: 1)
        #expect(summary == .notEnoughData)
        #expect(summary.cycleDay == nil)
    }

    // MARK: EDGE — consecutive days collapse into ONE run (not N starts), so one period → not enough.

    @Test func consecutiveDays_collapseIntoOneRun() async throws {
        // Seven consecutive flow days is ONE period, not seven starts.
        let oneLongRun = (0..<7).map { Self.day($0) }
        let summary = summarize(oneLongRun, todayOffset: 10)
        #expect(summary == .notEnoughData)
    }

    // MARK: EDGE — a never-cycling-then-suppressed user emits nothing (suppressed dominates sparse).

    @Test func neverCycledThenSuppressed_emitsNothing() async throws {
        let summary = summarize([], suppressed: true, todayOffset: 0)
        #expect(summary == .suppressed)
        #expect(summary.isSuppressed == true)
        #expect(summary.hasEnoughData == false)
    }

    // MARK: Determinism — same inputs + same injected `today` → equal CycleSummary.

    @Test func determinism_sameInputs_equalSummary() async throws {
        let days = Self.bleedingDays(starts: [0, 28, 56, 84])
        let a = summarize(days, todayOffset: 90)
        let b = summarize(days, todayOffset: 90)
        #expect(a == b)
    }
}
