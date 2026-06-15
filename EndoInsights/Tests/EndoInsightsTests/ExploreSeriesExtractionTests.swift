// EndoInsights — the unit-pinned honesty invariants for series extraction (Phase 14, Plan 02).
//
// These fixtures lock the phase's two structural honesty moves BEFORE any Explore pixel exists (the
// `ChartScrubSelectionTests` playbook — the invariants live in unit tests, not E2Es):
//   • absence ≠ 0: an unlogged quantitative field emits NO point (a gap), while a logged literal 0
//     emits a REAL point at value 0 (SC2 / T-14-01).
//   • presence is unrepresentable as 0/1: presence/event series extract as occurrence `[Date]` — a
//     return type with NO `value` field, so coercing a tag to 0 or 1 is impossible to even write
//     (SC3 / T-14-03).
//   • no suppression leak: a suppressed snapshot's bleeding produces zero intensity points AND zero
//     event occurrences (SC3 no-leak / T-14-02 / Pitfall 4); the gate only suppresses, never universally
//     drops (the un-suppressed mirror fixture proves the un-gated path still emits).
//
// Uses the Plan-01 `SeriesTestSupport` harness (UTC-fixed calendar + day()/daySnapshot() builders) —
// NEVER a real `Date()` (determinism). Series under test are resolved from the real catalog.
import Testing
import Foundation
@testable import EndoInsights

@Suite("Explore series extraction (absence ≠ 0; presence unrepresentable as 0/1; no suppression leak)")
struct ExploreSeriesExtractionTests {

    // MARK: catalog lookups (resolve the real series under test by id)

    private var stressSeries: ExploreSeries {
        ExploreSeriesCatalog.all.first { $0.id == "stress" }!
    }
    private var pelvicPainSymptomSeries: ExploreSeries {
        ExploreSeriesCatalog.all.first { $0.id == "symptom.pelvicPain.severity" }!
    }
    private var dairySeries: ExploreSeries {
        ExploreSeriesCatalog.all.first { $0.id == "food.dairy" }!
    }
    private var moodSeries: ExploreSeries {
        ExploreSeriesCatalog.all.first { $0.id == "mood" }!
    }
    private var bleedingIntensitySeries: ExploreSeries {
        ExploreSeriesCatalog.all.first { $0.id == "bleeding.intensity" }!
    }
    private var bleedingEventSeries: ExploreSeries {
        ExploreSeriesCatalog.all.first { $0.id == "bleeding.event" }!
    }

    // MARK: absence ≠ 0 — quantitative path (T-14-01 / SC2)

    /// An unlogged quantitative field (stress nil, even though the day logged something else) emits NO
    /// point — nil → gap, NEVER a fabricated 0.
    @Test func unloggedQuantitativeDay_emitsNoPoint() {
        let cal = utcCalendar()
        // Day logged SOMETHING (a symptom) but stress is nil → stress is a gap, not a zero.
        let snap = daySnapshot(day: day(2026, 6, 1),
                               symptoms: [SymptomPoint(type: .pelvicPain, severity: 5)],
                               stress: nil)
        #expect(ExploreSeriesExtraction.quantitativePoints([snap], series: stressSeries, calendar: cal).isEmpty)
    }

    /// A logged literal 0 (stress 0) emits a REAL point at value 0 — provably distinct from a gap
    /// (mirrors loggedSeverityZeroDay_..., Pitfall 2: a logged 0 is NOT a gap).
    @Test func loggedZero_emitsRealPointAtZero() {
        let cal = utcCalendar()
        let snap = daySnapshot(day: day(2026, 6, 2), stress: 0)
        let points = ExploreSeriesExtraction.quantitativePoints([snap], series: stressSeries, calendar: cal)
        #expect(points.count == 1)
        #expect(points.first?.value == 0)
        #expect(points.first?.date == cal.startOfDay(for: snap.day))
    }

    /// A logged symptom severity 0 emits a REAL point at value 0 for that symptom's series.
    @Test func loggedZeroSymptomSeverity_emitsRealPointAtZero() {
        let cal = utcCalendar()
        let snap = daySnapshot(day: day(2026, 6, 3),
                               symptoms: [SymptomPoint(type: .pelvicPain, severity: 0)])
        let points = ExploreSeriesExtraction.quantitativePoints([snap], series: pelvicPainSymptomSeries, calendar: cal)
        #expect(points.count == 1)
        #expect(points.first?.value == 0)
        #expect(points.first?.date == cal.startOfDay(for: snap.day))
    }

    /// Defensive against a duplicate-type day (a fixture or repository bug that violates the one-entry-
    /// per-type-per-day invariant): the accessor takes the MAX logged severity, never positional-first, so
    /// it can never silently drop the higher logged value (WR-03). Two pelvicPain entries (severity 2 and
    /// 8) on one day → the extracted point is the max (8), not whichever happened to be first.
    @Test func duplicateTypeDay_keepsMaxSeverity_neverDropsHigher() {
        let cal = utcCalendar()
        let snap = daySnapshot(day: day(2026, 6, 9),
                               symptoms: [SymptomPoint(type: .pelvicPain, severity: 2),
                                          SymptomPoint(type: .pelvicPain, severity: 8)])
        let points = ExploreSeriesExtraction.quantitativePoints([snap], series: pelvicPainSymptomSeries, calendar: cal)
        #expect(points.count == 1)
        #expect(points.first?.value == 8)
    }

    // MARK: presence path returns [Date], unrepresentable as 0/1 (T-14-03 / SC3)

    /// A present food tag emits that day's startOfDay occurrence Date. Type-level note: occurrenceDays
    /// returns `[Date]` — there is NO `value` field to assert as 0 or 1, so the presence-to-0/1 coercion
    /// is unrepresentable in the type system (the success criterion, T-14-03), not merely unchecked here.
    @Test func presentFoodTag_emitsOccurrenceDate_andReturnsDateArray() {
        let cal = utcCalendar()
        let snap = daySnapshot(day: day(2026, 6, 4), foodTags: [.dairy])
        let occurrences: [Date] = ExploreSeriesExtraction.occurrenceDays([snap], series: dairySeries, calendar: cal)
        #expect(occurrences == [cal.startOfDay(for: snap.day)])
    }

    /// A logged day WITHOUT the presence thing (food tags empty, though something else is logged) emits
    /// NO occurrence — presence-absent ≠ logged-absent (T-14-01).
    @Test func loggedDayWithoutTag_emitsNoOccurrence() {
        let cal = utcCalendar()
        let snap = daySnapshot(day: day(2026, 6, 5),
                               foodTags: [],
                               stress: 3) // logged something else, but NOT dairy
        #expect(ExploreSeriesExtraction.occurrenceDays([snap], series: dairySeries, calendar: cal).isEmpty)
    }

    // MARK: wrong-door routing degrades gracefully, never crashes (WR-01 / IN-03 cross-routing)

    /// A presence series handed to `quantitativePoints` (the wrong door) DEGRADES to an empty result —
    /// never a release crash — EVEN when the underlying presence thing is logged (WR-01). Locks the
    /// graceful-degrade contract so a Phase-15/16 picker bug returns "no data", not a trap.
    @Test func presenceSeriesIntoQuantitativePoints_degradesToEmpty_notCrash() {
        let cal = utcCalendar()
        let snap = daySnapshot(day: day(2026, 6, 7), foodTags: [.dairy]) // dairy IS logged
        #expect(ExploreSeriesExtraction.quantitativePoints([snap], series: dairySeries, calendar: cal).isEmpty)
    }

    /// A quantitative series handed to `occurrenceDays` (the wrong door) DEGRADES to an empty result —
    /// never a release crash — EVEN when the underlying value is logged (WR-01). The quantitative-into-
    /// presence cross case (IN-03), locked directly rather than left to code inspection.
    @Test func quantitativeSeriesIntoOccurrenceDays_degradesToEmpty_notCrash() {
        let cal = utcCalendar()
        let snap = daySnapshot(day: day(2026, 6, 8), mood: 3) // mood IS logged
        #expect(ExploreSeriesExtraction.occurrenceDays([snap], series: moodSeries, calendar: cal).isEmpty)
    }

    // MARK: suppression — no leak (T-14-02 / SC3), and the gate only suppresses (mirror)

    /// A suppressed snapshot's bleeding produces ZERO intensity points AND ZERO event occurrences — the
    /// suppression gate inside the accessors closes BOTH bleeding-derived paths (no leak, Pitfall 4).
    @Test func suppressedBleeding_emitsNoPointsAndNoOccurrences() {
        let cal = utcCalendar()
        let snap = daySnapshot(day: day(2026, 6, 6), flow: .heavy, suppressed: true)
        #expect(ExploreSeriesExtraction.quantitativePoints([snap], series: bleedingIntensitySeries, calendar: cal).isEmpty)
        #expect(ExploreSeriesExtraction.occurrenceDays([snap], series: bleedingEventSeries, calendar: cal).isEmpty)
    }

    /// The same fixture un-suppressed: one intensity point at the honest ordinal rank of `.heavy` (4) AND
    /// one event occurrence. Proves the gate only SUPPRESSES — it never universally drops the series.
    @Test func unsuppressedBleeding_emitsIntensityPointAndEventOccurrence() {
        let cal = utcCalendar()
        let snap = daySnapshot(day: day(2026, 6, 6), flow: .heavy, suppressed: false)
        let points = ExploreSeriesExtraction.quantitativePoints([snap], series: bleedingIntensitySeries, calendar: cal)
        #expect(points.count == 1)
        #expect(points.first?.value == 4) // spotting=1, light=2, medium=3, heavy=4
        let occurrences = ExploreSeriesExtraction.occurrenceDays([snap], series: bleedingEventSeries, calendar: cal)
        #expect(occurrences == [cal.startOfDay(for: snap.day)])
    }
}
