// EndoInsights — SC3 keystone: no bleeding/cycle trend under suppression (Phase 5, D-08/D-16).
//
// The single most important honesty proof of the engine, mirroring `CycleMath.summarize`'s
// `if isSuppressed { return .suppressed }` short-circuit. Over a `[DaySnapshot]` where every day is
// `suppressed == true` and `flow == nil` for MONTHS, `TrendsEngine.trends` MUST emit:
//   • NO Insight whose headline mentions bleeding/cycle/period, AND
//   • NO Insight with `kind == .cyclePhase`.
// TDD RED-first: references `TrendsEngine.trends` (not yet written) → the suite fails to compile.
import Testing
import Foundation
@testable import EndoInsights

@Suite("SC3 suppressed — no phantom bleeding/cycle trend")
struct SuppressedFixtureTests {

    private func trends(_ snapshots: [DaySnapshot]) -> [Insight] {
        TrendsEngine.trends(from: snapshots, asOf: Fixtures.day(0), calendar: Fixtures.calendar)
    }

    @Test func suppressedNoBleeding_emitsNoBleedingOrCycleTrend() async throws {
        let insights = trends(Fixtures.suppressedNoBleedingFixture(monthsBack: 4))

        // No headline mentions bleeding/period/cycle.
        #expect(!insights.contains { $0.headline.localizedCaseInsensitiveContains("bleed") })
        #expect(!insights.contains { $0.headline.localizedCaseInsensitiveContains("period") })
        #expect(!insights.contains { $0.headline.localizedCaseInsensitiveContains("cycle") })

        // No .cyclePhase kind (Phase 5 emits only .trend anyway; this guards the gate explicitly).
        #expect(!insights.contains { $0.kind == .cyclePhase })
    }

    @Test func suppressedNoBleeding_stillEmitsNonBleedingTrends() async throws {
        // The fixture logs pelvicPain + stress every day, so NON-bleeding descriptive trends are fine —
        // suppression only gates the bleeding/cycle trend, not symptom/factor trends.
        let insights = trends(Fixtures.suppressedNoBleedingFixture(monthsBack: 4))
        #expect(insights.contains { $0.headline.localizedCaseInsensitiveContains("pelvic") })
    }
}
