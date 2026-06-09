// Requirement: RPRT-01 / RPRT-02 (Phase 10) — the pure honesty + source-label + range-scoping suite.
//
// Proves the PURE `ReportModel` is correct + HONEST WITHOUT any rendering (Pattern 1) over an in-memory
// `SwiftDataRepository` (mirrors `DaySnapshotMappingTests`' container setup; no CloudKit / network / UI).
// The contract under test (the endo pitfall made checkable):
//   • scopesToRange — a day outside the DateInterval is excluded from every section; an inside day in.
//   • sparseRangeIsHonest — one sub-threshold day → the honest "not enough data" state, never fabricated.
//   • suppressedNoPhantomCycle — isSuppressed() → cycle is .suppressed (NO cycle-day/window/phase).
//   • absenceIsNotZero — an unlogged lifestyle field is nil ("Not logged"), distinct from a logged 0.
//   • sourceLabelsMatchProvenance — labels aggregate from daySnapshotSources (single merge gate, Pitfall 1).
//
// Task 1 (Nyquist Wave-0): the SectionSource.aggregate case is ENABLED now (pure, no assemble). Every
// assemble-dependent case is `.disabled("Task 2")` until Task 2 implements `ReportModel.assemble`.
import Testing
import Foundation
import SwiftData
@testable import EndoApp
import EndoInsights

@Suite("Phase-10 ReportModel honesty + scoping + source labels")
struct ReportModelTests {

    private func makeRepo() throws -> (SwiftDataRepository, ModelContext) {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)
        return (SwiftDataRepository(context: ctx), ctx)
    }

    /// A stable, calendar-safe anchor (mirrors DaySnapshotMappingTests).
    private let day0 = Date(timeIntervalSince1970: 1_700_000_000)

    /// A 30-day range ending at `day0` (so an in-range day and an out-of-range day are easy to seed).
    private var range: DateInterval {
        let cal = Calendar.current
        return DateInterval(
            start: cal.date(byAdding: .day, value: -30, to: day0)!,
            end: day0)
    }

    /// The injected engine closure for the pure assembly. Tests that don't care about insight content
    /// pass the real LOCKED gate so the window-based engine is exercised exactly as production does.
    private func engine(_ snapshots: [DaySnapshot], _ asOf: Date) -> [Insight] {
        InsightsEngine.insights(from: snapshots, asOf: asOf, dismissed: [])
    }

    // MARK: - ENABLED now (Task 1): the pure SectionSource.aggregate contract (Pattern 4)

    @Test func aggregateSource_emptyPresentSet_isNone() {
        #expect(SectionSource.aggregate([]) == .none)
        #expect(SectionSource.aggregate([nil, nil]) == .none, "All-absent provenance → no badge.")
    }

    @Test func aggregateSource_allManual_isSelfReported() {
        #expect(SectionSource.aggregate([.manual, .manual, nil]) == .selfReported)
    }

    @Test func aggregateSource_allAppleHealth_isAppleHealth() {
        #expect(SectionSource.aggregate([.appleHealth, nil, .appleHealth]) == .appleHealth)
    }

    @Test func aggregateSource_bothPresent_isMixed() {
        #expect(SectionSource.aggregate([.manual, .appleHealth]) == .mixed)
    }

    // MARK: - Task 2 (GREEN): assemble-dependent honesty + scoping cases

    @Test
    func scopesToRange() throws {
        let (repo, _) = try makeRepo()
        let cal = Calendar.current
        // An INSIDE day: 10 days before day0 (within the 30-day range).
        let inside = cal.date(byAdding: .day, value: -10, to: day0)!
        // An OUTSIDE day: 60 days before day0 (well outside the 30-day range).
        let outside = cal.date(byAdding: .day, value: -60, to: day0)!
        try repo.upsertSymptom(type: .pelvicPain, severity: 5, on: inside)
        try repo.upsertSymptom(type: .headache, severity: 8, on: outside)

        let model = try ReportModel.assemble(
            range: range, repository: repo, engine: engine, asOf: day0)

        // The inside symptom is present; the outside symptom is excluded from every section.
        #expect(model.symptoms.rows.contains { $0.type == .pelvicPain },
                "An in-range symptom is included.")
        #expect(!model.symptoms.rows.contains { $0.type == .headache },
                "An out-of-range symptom is excluded from the symptoms section.")
        #expect(model.summary.daysLogged == 1, "Only the in-range logged day counts toward coverage.")
        #expect(model.range == range, "The model honors the exact requested range.")
    }

    @Test
    func sparseRangeIsHonest() throws {
        let (repo, _) = try makeRepo()
        let cal = Calendar.current
        // ONE logged day in range — sub-threshold for a cycle estimate (< 2 cycles) and trends.
        try repo.upsertSymptom(type: .pelvicPain, severity: 4,
                               on: cal.date(byAdding: .day, value: -5, to: day0)!)

        let model = try ReportModel.assemble(
            range: range, repository: repo, engine: engine, asOf: day0)

        // The symptoms section reads as honest sparse — never a fabricated trend.
        #expect(model.symptoms.hasEnoughData == false,
                "A single sub-threshold day yields the honest 'not enough data' state.")
        // The cycle section is the honest not-enough-data state (one bleeding day can't form 2 cycles).
        #expect(model.cycle == .notEnoughData || model.cycle == .suppressed,
                "A sparse range never fabricates a derived cycle.")
        // The gated engine surfaces NO fabricated insight for a 1-day range — only the honest
        // "not enough data yet" insufficiency notice may appear (that marker IS the honest sparse signal,
        // not a fabrication). An empty array trivially satisfies this.
        #expect(model.insights.allSatisfy { $0.isInsufficiencyNotice },
                "A sub-threshold range fabricates no real insight — only the honest insufficiency notice.")
    }

    @Test
    func suppressedNoPhantomCycle() throws {
        let (repo, _) = try makeRepo()
        let cal = Calendar.current
        // Even WITH plenty of bleeding history, suppression must short-circuit to the off-state.
        for offset in [-2, -3, -28, -29, -56, -57] {
            try repo.setBleeding(intensity: .medium, clots: false,
                                 on: cal.date(byAdding: .day, value: offset, to: day0)!)
        }
        try repo.setSuppressed(true)

        let model = try ReportModel.assemble(
            range: range, repository: repo, engine: engine, asOf: day0)

        #expect(model.cycle == .suppressed,
                "A suppressed user shows the off-state — NO phantom cycle/day/window/phase.")
    }

    @Test
    func absenceIsNotZero() throws {
        let (repo, _) = try makeRepo()
        let cal = Calendar.current
        let logDay = cal.date(byAdding: .day, value: -5, to: day0)!
        // Log a REAL stress of 0 (distinct from absence) and a symptom — but NEVER sleep/activity/mood.
        try repo.setStress(0, on: logDay)
        try repo.upsertSymptom(type: .fatigue, severity: 2, on: logDay)

        let model = try ReportModel.assemble(
            range: range, repository: repo, engine: engine, asOf: day0)

        // A logged level-0 stress is a real 0 — NOT coerced to nil.
        #expect(model.lifestyle.stress == 0, "A logged stress of 0 is a real value, never absence.")
        // Never-logged factors stay nil ("Not logged"), never 0.
        #expect(model.lifestyle.sleepHours == nil, "Unlogged sleep is nil — never 0 (absence ≠ 0).")
        #expect(model.lifestyle.activityMinutes == nil, "Unlogged activity is nil — never 0.")
        #expect(model.lifestyle.mood == nil, "Unlogged mood is nil — never a neutral fabricated value.")
    }

    @Test
    func sourceLabelsMatchProvenance() throws {
        let (repo, _) = try makeRepo()
        let cal = Calendar.current
        let logDay = cal.date(byAdding: .day, value: -5, to: day0)!
        // All-manual logging in range → the symptoms/lifestyle sections aggregate to .selfReported.
        try repo.setStress(6, on: logDay)
        try repo.setSleep(hours: 7, quality: .good, on: logDay)
        try repo.upsertSymptom(type: .pelvicPain, severity: 5, on: logDay)

        let model = try ReportModel.assemble(
            range: range, repository: repo, engine: engine, asOf: day0)

        #expect(model.lifestyle.source == .selfReported,
                "All-manual lifestyle logging aggregates to Self-reported (from daySnapshotSources).")
        #expect(model.summary.source == .selfReported,
                "The summary's aggregate provenance reflects the manual logging.")
        #expect(model.legend.hasSelfReported, "The legend surfaces the Self-reported label in use.")
        #expect(!model.legend.hasAppleHealth, "No Apple Health source present → no Apple Health legend line.")
    }
}
