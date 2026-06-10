// Requirement: CHART-01 / CHART-02 — the unit-pinned honesty invariants for chart scrubbing.
//
// These tests carry the phase's honesty contract (the v1.0 `ChartSeriesGrouping` playbook: the
// invariants live in unit tests, not E2Es):
//   • A raw scrub Date on a logged day resolves to EXACTLY that day's point — never a neighbor,
//     never an interpolation (CHART-01).
//   • A raw scrub Date on an unlogged day resolves to (day, nil) — the explicit gap, never a
//     fabricated zero (CHART-02, half 1).
//   • A logged severity-0 day resolves to a REAL point with value 0 — provably distinct from nil
//     (absence ≠ 0; CHART-02, half 2).
//   • Day math is calendar-API-only and DST-safe (23:59 boundary + Europe/Berlin spring-forward).
import Testing
import Foundation
@testable import EndoInsights

@Suite("Chart scrub selection (exact-day honesty, absence ≠ 0)")
struct ChartScrubSelectionTests {

    /// A deterministic, DST-agnostic calendar fixed to UTC so day math is reproducible.
    private var calendar: Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        return cal
    }

    private func day(_ year: Int, _ month: Int, _ d: Int, hour: Int = 12, minute: Int = 0,
                     in cal: Calendar? = nil) -> Date {
        var comps = DateComponents()
        comps.year = year; comps.month = month; comps.day = d
        comps.hour = hour; comps.minute = minute
        return (cal ?? calendar).date(from: comps)!
    }

    private func point(_ year: Int, _ month: Int, _ d: Int, value: Double,
                       in cal: Calendar? = nil) -> InsightPoint {
        InsightPoint(date: day(year, month, d, in: cal), value: value)
    }

    // Test 1 (nil input): no raw date → no selection, no chrome.
    @Test func nilRawDate_resolvesToNil() {
        let points = [point(2026, 6, 1, value: 3), point(2026, 6, 2, value: 4)]
        let resolution = ChartScrubSelection.resolve(nil, in: points, calendar: calendar)
        #expect(resolution == nil)
    }

    // Test 2 (exact logged-day match): a midday raw date on a logged day returns THAT day's point,
    // value preserved exactly — never a neighbor (CHART-01).
    @Test func loggedDay_resolvesToExactPoint() {
        let target = point(2026, 6, 5, value: 6)
        let points = [point(2026, 6, 4, value: 3), target, point(2026, 6, 6, value: 8)]
        let raw = day(2026, 6, 5, hour: 12)
        let resolution = ChartScrubSelection.resolve(raw, in: points, calendar: calendar)
        #expect(resolution?.day == calendar.startOfDay(for: raw))
        #expect(resolution?.point == target)
        #expect(resolution?.point?.value == 6)
    }

    // Test 3 (gap day, CHART-02): a raw date inside a gap between logged days returns the correct
    // day with point == nil — NEVER the nearest logged point, NEVER a fabricated value.
    @Test func gapDay_resolvesToNilPoint_neverNearestNeighbor() {
        // Logged Jun 1 and Jun 4; Jun 2–3 are an unlogged gap.
        let points = [point(2026, 6, 1, value: 3), point(2026, 6, 4, value: 7)]
        let raw = day(2026, 6, 2, hour: 12)
        let resolution = ChartScrubSelection.resolve(raw, in: points, calendar: calendar)
        #expect(resolution != nil, "The day under the finger is real — the gap must still resolve.")
        #expect(resolution?.day == calendar.startOfDay(for: raw))
        #expect(resolution?.point == nil,
                "A gap day must resolve to an explicit nil point — never snap to a neighbor.")
    }

    // Test 4 (logged severity-0, CHART-02): a day logged with value 0 is a REAL point reading 0 —
    // NOT nil (absence ≠ 0).
    @Test func loggedSeverityZeroDay_resolvesToRealZeroPoint_notNil() {
        let zeroDay = point(2026, 6, 3, value: 0)
        let points = [point(2026, 6, 2, value: 5), zeroDay]
        let raw = day(2026, 6, 3, hour: 9)
        let resolution = ChartScrubSelection.resolve(raw, in: points, calendar: calendar)
        #expect(resolution?.point != nil,
                "A logged severity-0 day is a REAL point — it must never read as a gap.")
        #expect(resolution?.point?.value == 0)
    }

    // Test 5 (23:59 boundary): a raw date at 23:59 of a logged day still matches that SAME day
    // (startOfDay normalization, never epoch arithmetic).
    @Test func lateEveningRawDate_matchesSameDay() {
        let target = point(2026, 6, 5, value: 4)
        let points = [target, point(2026, 6, 6, value: 9)]
        let raw = day(2026, 6, 5, hour: 23, minute: 59)
        let resolution = ChartScrubSelection.resolve(raw, in: points, calendar: calendar)
        #expect(resolution?.day == calendar.startOfDay(for: raw))
        #expect(resolution?.point == target,
                "23:59 belongs to the SAME calendar day — never roll into the neighbor.")
    }

    // Test 6 (DST boundary): around the Europe/Berlin 2026-03-29 spring-forward (a 23-hour day),
    // a raw date on the transition day resolves to that day — calendar APIs, not 86400-multiples.
    @Test func dstSpringForwardDay_resolvesCorrectly() {
        var berlin = Calendar(identifier: .gregorian)
        berlin.timeZone = TimeZone(identifier: "Europe/Berlin")!
        let points = [
            point(2026, 3, 28, value: 5, in: berlin),
            point(2026, 3, 29, value: 6, in: berlin), // the spring-forward day (02:00 → 03:00)
            point(2026, 3, 30, value: 7, in: berlin),
        ]
        // Late on the transition day — epoch math (day = 86400s) would mis-bucket this.
        let raw = day(2026, 3, 29, hour: 23, minute: 30, in: berlin)
        let resolution = ChartScrubSelection.resolve(raw, in: points, calendar: berlin)
        #expect(resolution?.day == berlin.startOfDay(for: raw))
        #expect(resolution?.point?.value == 6,
                "The DST transition day must resolve to ITS OWN point, never a neighbor.")
    }

    // Test 7 (unsorted input): points supplied out of order still resolve correctly — the resolver
    // must not assume producer sort order.
    @Test func unsortedInput_resolvesCorrectly() {
        let target = point(2026, 6, 10, value: 2)
        let points = [point(2026, 6, 12, value: 8), target, point(2026, 6, 8, value: 5)]
        let raw = day(2026, 6, 10, hour: 15)
        let resolution = ChartScrubSelection.resolve(raw, in: points, calendar: calendar)
        #expect(resolution?.point == target)
    }
}
