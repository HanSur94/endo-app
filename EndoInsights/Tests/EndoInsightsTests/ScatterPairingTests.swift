// EndoInsights — the unit-pinned honesty invariants for scatter pairing (Phase 14, Plan 03).
//
// These fixtures lock the scatter honesty invariants BEFORE any scatter pixel exists (the
// `ChartScrubSelectionTests` / Plan-02 playbook — invariants live in unit tests, not E2Es):
//   • both-logged-only: a scatter point is a day where BOTH factors were logged; a day missing
//     EITHER factor produces NO pair (never imputed/0-substituted — T-14-05 / Pitfall 1/5).
//   • count-encoded, never jittered: identical (x,y) day-pairs aggregate into ONE ScatterPoint
//     whose `count` is a FACTUAL tally — never `Double.random`/jitter fabricating non-integer
//     values the user never logged (T-14-06 / SCAT-02 / Pitfall 5).
//   • logged-0 is real: a day logging a literal 0 on both axes is a REAL pair, never a gap
//     (Pitfall 2 carried into scatter).
//   • suppression carries through: a suppressed bleeding axis contributes no pair (the
//     `quantitativeValue` gate from Plan 01 carries through — T-14-02).
//   • determinism: distinct pairs produce distinct points, sorted by (x, y) for stable fixtures.
//
// Uses the Plan-01 `SeriesTestSupport` harness (UTC-fixed calendar + day()/daySnapshot() builders) —
// NEVER a real `Date()` (determinism). Series under test are resolved from the real catalog.
import Testing
import Foundation
@testable import EndoInsights

@Suite("Scatter pairing (both-logged-only; count-encoded, never jittered or imputed)")
struct ScatterPairingTests {

    // MARK: catalog lookups (resolve the real series under test by id)

    private var stressSeries: ExploreSeries {
        ExploreSeriesCatalog.all.first { $0.id == "stress" }!
    }
    private var moodSeries: ExploreSeries {
        ExploreSeriesCatalog.all.first { $0.id == "mood" }!
    }
    private var pelvicPainSeries: ExploreSeries {
        ExploreSeriesCatalog.all.first { $0.id == "symptom.pelvicPain.severity" }!
    }
    private var bleedingIntensitySeries: ExploreSeries {
        ExploreSeriesCatalog.all.first { $0.id == "bleeding.intensity" }!
    }

    // MARK: count-aggregation, no jitter (SCAT-02 / T-14-06)

    /// 10 distinct days all at (stress 7, mood 2) aggregate into EXACTLY one ScatterPoint with
    /// count == 10 — never 10 jittered points fabricating values the user never logged.
    @Test func identicalPairs_aggregateIntoOnePointWithCount() {
        let cal = utcCalendar()
        let snaps = (1...10).map { d in
            daySnapshot(day: day(2026, 6, d), stress: 7, mood: 2)
        }
        let points = ScatterPairing.pairs(snaps, x: stressSeries, y: moodSeries, calendar: cal)
        #expect(points == [ScatterPairing.ScatterPoint(x: 7, y: 2, count: 10)])
    }

    // MARK: both-logged-only, never imputed (T-14-05 / Pitfall 1/5)

    /// A day with stress logged but mood nil produces NO pair — either factor nil excludes the day,
    /// never imputed/0-substituted.
    @Test func dayWithOneFactorMissing_producesNoPair() {
        let cal = utcCalendar()
        let snap = daySnapshot(day: day(2026, 6, 1), stress: 7, mood: nil)
        #expect(ScatterPairing.pairs([snap], x: stressSeries, y: moodSeries, calendar: cal).isEmpty)
    }

    /// 3 both-logged days at (5,3), 2 days with mood nil, 1 day with stress nil → the single
    /// ScatterPoint has count == 3 (the missing-factor days are excluded — T-14-05).
    @Test func mixedDays_onlyBothLoggedDaysCounted() {
        let cal = utcCalendar()
        var snaps: [DaySnapshot] = []
        for d in 1...3 { snaps.append(daySnapshot(day: day(2026, 6, d), stress: 5, mood: 3)) }
        for d in 4...5 { snaps.append(daySnapshot(day: day(2026, 6, d), stress: 5, mood: nil)) }
        snaps.append(daySnapshot(day: day(2026, 6, 6), stress: nil, mood: 3))
        let points = ScatterPairing.pairs(snaps, x: stressSeries, y: moodSeries, calendar: cal)
        #expect(points == [ScatterPairing.ScatterPoint(x: 5, y: 3, count: 3)])
    }

    // MARK: determinism — distinct pairs → distinct sorted points

    /// Days at (1,1), (3,2), (2,5) → exactly those three points sorted by (x,y): (1,1),(2,5),(3,2).
    @Test func distinctPairs_produceDistinctSortedPoints() {
        let cal = utcCalendar()
        let snaps = [
            daySnapshot(day: day(2026, 6, 1), stress: 1, mood: 1),
            daySnapshot(day: day(2026, 6, 2), stress: 3, mood: 2),
            daySnapshot(day: day(2026, 6, 3), stress: 2, mood: 5),
        ]
        let points = ScatterPairing.pairs(snaps, x: stressSeries, y: moodSeries, calendar: cal)
        #expect(points == [
            ScatterPairing.ScatterPoint(x: 1, y: 1, count: 1),
            ScatterPairing.ScatterPoint(x: 2, y: 5, count: 1),
            ScatterPairing.ScatterPoint(x: 3, y: 2, count: 1),
        ])
    }

    // MARK: logged-0 is a real pair, not excluded (Pitfall 2 carried into scatter)

    /// A day with stress 0 and pelvic-pain severity 0 — both literal logged 0s on 0…10 axes — is a
    /// REAL pair at (0, 0) with count 1, never excluded as a gap (logged 0 ≠ absence).
    @Test func loggedZeroBothFactors_isARealPair_notExcluded() {
        let cal = utcCalendar()
        let snap = daySnapshot(day: day(2026, 6, 1),
                               symptoms: [SymptomPoint(type: .pelvicPain, severity: 0)],
                               stress: 0)
        let points = ScatterPairing.pairs([snap], x: stressSeries, y: pelvicPainSeries, calendar: cal)
        #expect(points == [ScatterPairing.ScatterPoint(x: 0, y: 0, count: 1)])
    }

    // MARK: suppression carries through (T-14-02)

    /// A suppressed bleeding-intensity axis contributes no pair — the suppression gate inside
    /// `quantitativeValue` carries through, so even with stress logged the day yields nothing.
    @Test func suppressedBleedingAxis_contributesNoPair() {
        let cal = utcCalendar()
        let snap = daySnapshot(day: day(2026, 6, 1), flow: .medium, stress: 5, suppressed: true)
        let points = ScatterPairing.pairs([snap], x: bleedingIntensitySeries, y: stressSeries, calendar: cal)
        #expect(points.isEmpty)
    }
}
