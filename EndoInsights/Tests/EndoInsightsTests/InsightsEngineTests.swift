// EndoInsights — the InsightsEngine composition + no-regression spec (Phase 6, INST-04 / D-12/D-13/D-16 / SC1).
//
// TDD: `InsightsEngine` is the ONE public producer (D-13). These assert it (a) COMPOSES trends +
// cycle-phase, (b) does NOT regress the Phase-5 trends — the gate is a SUPERSET filter, so
// `InsightsEngine.insights ⊇ TrendsEngine.trends` over the Phase-5 fixture (Pitfall 1) — and (c) honors
// dismissal IDEMPOTENTLY (a dismissed signal STAYS dismissed across recomputation, D-12).
// `today` is injected (no `Date.now`); the fixed-UTC `Fixtures.calendar` keeps day-bucketing stable.
import Testing
import Foundation
@testable import EndoInsights

@Suite("INST-04 InsightsEngine — composition + Phase-5 no-regression + dismissal")
struct InsightsEngineTests {

    private func engine(_ snapshots: [DaySnapshot], dismissed: Set<String> = []) -> [Insight] {
        InsightsEngine.insights(
            from: snapshots, asOf: Fixtures.day(0), dismissed: dismissed, calendar: Fixtures.calendar
        )
    }

    private func rawTrends(_ snapshots: [DaySnapshot]) -> [Insight] {
        TrendsEngine.trends(from: snapshots, asOf: Fixtures.day(0), calendar: Fixtures.calendar)
    }

    // MARK: (a) Composition — both `.trend` and `.cyclePhase` kinds surface over a regular cycle.

    @Test func composition_emitsBothTrendAndCyclePhaseKinds() async throws {
        let insights = engine(Fixtures.regularCycleFixture())
        let kinds = Set(insights.map(\.kind))
        #expect(kinds.contains(.trend), "InsightsEngine must compose TrendsEngine trends.")
        #expect(kinds.contains(.cyclePhase), "InsightsEngine must compose CyclePhaseMapper associations.")
    }

    // MARK: (b) NO-REGRESSION — the gate is a SUPERSET filter; every raw trend survives (Pitfall 1).

    @Test func noRegression_insightsAreSupersetOfRawTrends() async throws {
        let fixture = Fixtures.risingPelvicPainFixture()
        let gatedIDs = Set(engine(fixture).map(\.id))
        let rawIDs = Set(rawTrends(fixture).map(\.id))

        #expect(!rawIDs.isEmpty, "The Phase-5 fixture must produce raw trends (sanity).")
        // The CRITICAL assertion: NO raw TrendsEngine output may be absent from the gated output.
        let dropped = rawIDs.subtracting(gatedIDs)
        #expect(dropped.isEmpty, "Phase-5 regression: \(dropped.count) raw trend(s) dropped by the gate.")
        #expect(gatedIDs.isSuperset(of: rawIDs), "InsightsEngine.insights must be a superset of TrendsEngine.trends.")
    }

    // MARK: (a') Phase-7 composition — a strong association surfaces a `.correlation` through the gate.

    @Test func composition_surfacesCorrelation_overStrongFixture() async throws {
        let kinds = Set(engine(Fixtures.strongAssociationFixture()).map(\.kind))
        #expect(kinds.contains(.correlation),
                "InsightsEngine must compose CorrelationEngine — a strong association surfaces a .correlation.")
    }

    // MARK: (b') Withholding — the gate drops under-sampled / spurious / flood correlations (the value, D-18).

    @Test func gate_withholdsUnderSampledCorrelation() async throws {
        let insights = engine(Fixtures.underSampledAssociationFixture())   // n=9 < minSample(.correlation)==10
        #expect(!insights.contains { $0.kind == .correlation },
                "An under-sampled (n=9) correlation must be WITHHELD through the public engine.")
    }

    @Test func gate_withholdsSpuriousCorrelation() async throws {
        let insights = engine(Fixtures.spuriousAssociationFixture())
        #expect(!insights.contains { $0.kind == .correlation },
                "A spurious (independent) correlation must be WITHHELD through the public engine.")
    }

    @Test func gate_withholdsMultipleComparisonFlood() async throws {
        // The load-bearing acceptance (SC3): K≈100+ pure-noise pairs surface ZERO correlations.
        let insights = engine(Fixtures.multipleComparisonFloodFixture())
        #expect(!insights.contains { $0.kind == .correlation },
                "A multiple-comparison flood of pure noise must surface ZERO correlations (SC3).")
    }

    // MARK: (c') Suppressed user — no cycle-phase correlation ever reaches the public output (D-04).

    @Test func suppressedUser_noCyclePhaseCorrelation_throughGate() async throws {
        let insights = engine(Fixtures.suppressedUserCorrelationFixture())
        #expect(!insights.contains { $0.signalKey.contains("cyclePhase") },
                "A suppressed user must yield NO cyclePhase correlation through the gate (D-04 by construction).")
    }

    // MARK: (a'') Phase-11 composition — a strong association surfaces a `.recommendation` through the gate.

    @Test func composition_surfacesRecommendation_overStrongFixture() async throws {
        // The aggravating fixture (dairy PRESENT → HIGHER pelvic-pain severity) is a strong, well-sampled
        // correlation; the composed RecommendationsEngine re-frames it into a `.recommendation` behind the gate.
        let kinds = Set(engine(Fixtures.strongAssociationFixture()).map(\.kind))
        #expect(kinds.contains(.recommendation),
                "InsightsEngine must compose RecommendationsEngine — a strong association surfaces a .recommendation.")
    }

    // MARK: (b'') Withholding — suppressed + flood users yield ZERO recommendations through the public engine (SC3).

    @Test func gate_withholdsRecommendation_forSuppressedAndFlood() async throws {
        let suppressed = engine(Fixtures.suppressedUserCorrelationFixture())
        #expect(!suppressed.contains { $0.kind == .recommendation },
                "A suppressed user must yield ZERO recommendations through the public engine (SC3).")
        let flood = engine(Fixtures.multipleComparisonFloodFixture())
        #expect(!flood.contains { $0.kind == .recommendation },
                "A multiple-comparison flood of pure noise must surface ZERO recommendations (SC3).")
    }

    // MARK: (c'') Dismissal-exclusion of a recommendation + idempotence (D-07).

    @Test func dismissal_excludesRecommendation_andStaysExcluded() async throws {
        let fixture = Fixtures.strongAssociationFixture()
        let baseline = engine(fixture)
        let rec = baseline.first { $0.kind == .recommendation }
        let target = try #require(rec?.signalKey, "The strong fixture must surface a recommendation to dismiss.")

        let afterDismiss = engine(fixture, dismissed: [target])
        #expect(!afterDismiss.contains { $0.signalKey == target }, "The dismissed recommendation must be excluded.")

        // Idempotence (D-07): a second recompute with the same dismissed set keeps it excluded.
        let afterDismissAgain = engine(fixture, dismissed: [target])
        #expect(afterDismissAgain.map(\.id) == afterDismiss.map(\.id),
                "A dismissed recommendation STAYS dismissed across recompute (D-07).")
    }

    // MARK: (a''') Phase-12 composition — a rising-with-≥21-evidence history surfaces ONE `.flare` through the gate.

    @Test func composition_surfacesFlare_overRisingFixture() async throws {
        // The rising flare fixture (30 days, a recent rising-severity spine over ≥21 days of evidence) is a
        // real, well-sampled rise; the composed FlareEngine surfaces exactly one bounded ≤.medium `.flare`
        // warning behind the SAME single gate. The producer is now reachable through the PUBLIC engine.
        let flares = engine(Fixtures.risingFlareFixture()).filter { $0.kind == .flare && !$0.isInsufficiencyNotice }
        #expect(flares.count == 1,
                "InsightsEngine must compose FlareEngine — a rising-with-≥21-evidence history surfaces exactly one .flare warning.")
        // Uncertainty-first (D-03): the composed flare warning is bounded at ≤ .medium (never .high).
        #expect(flares.allSatisfy { $0.confidence != .high },
                "A composed flare warning must be bounded at ≤ .medium (uncertainty-first, D-03).")
    }

    // MARK: (b''') Withholding — suppressed + flat users yield ZERO flare warnings through the public engine (SC3).

    @Test func gate_withholdsFlare_forSuppressedAndFlat() async throws {
        let suppressed = engine(Fixtures.suppressedNoBleedingFixture())
        #expect(!suppressed.contains { $0.kind == .flare },
                "A suppressed user must yield ZERO flare warnings through the public engine (SC3).")
        let flat = engine(Fixtures.flatNoRiseFixture())
        #expect(!flat.contains { $0.kind == .flare && !$0.isInsufficiencyNotice },
                "A flat / no-rise history must surface NO non-notice flare warning through the public engine (SC3).")
    }

    // MARK: (c) Dismissal-exclusion + idempotence (D-12: stays dismissed across recomputation).

    @Test func dismissal_excludesExactlyThatSignal_andStaysExcluded() async throws {
        let fixture = Fixtures.regularCycleFixture()
        let baseline = engine(fixture)
        #expect(!baseline.isEmpty, "Need at least one emitted insight to dismiss.")

        let target = baseline.first!.signalKey
        let afterDismiss = engine(fixture, dismissed: [target])

        // The dismissed signal is absent; everything else is unchanged.
        #expect(!afterDismiss.contains { $0.signalKey == target }, "The dismissed signal must be excluded.")
        let expectedRemainder = baseline.filter { $0.signalKey != target }.map(\.id)
        #expect(afterDismiss.map(\.id) == expectedRemainder, "Only the dismissed signal is removed; order preserved.")

        // Idempotence (D-12): a second recomputation with the same dismissed set yields the same exclusion.
        let afterDismissAgain = engine(fixture, dismissed: [target])
        #expect(afterDismissAgain.map(\.id) == afterDismiss.map(\.id), "A dismissed signal STAYS dismissed (D-12).")
    }

    // MARK: Determinism — same inputs + same injected today → exactly equal [Insight].

    @Test func determinism_sameInputs_equalInsights() async throws {
        #expect(engine(Fixtures.regularCycleFixture()) == engine(Fixtures.regularCycleFixture()))
    }
}
