// EndoInsights — the FlareEngine honesty/absence spec (Phase 12, INST-06 / D-01..D-08 / SC1).
//
// RED until Plan 12-02 implements FlareEngine + FlareCopy.
//
// TDD: this asserts the FLARE producer's two highest-stakes HONESTY invariants (Wave-0 VALIDATION
// requirement) BEFORE the producer exists:
//   1. ABSENCE-BY-TEST (load-bearing, D-05/D-08/SC3): a suppressed / flat-no-rise / insufficient-evidence
//      user gets ZERO non-notice flare warning. The engine must NEVER fabricate a speculative warning under
//      sparse/suppressed/insufficient data — absence is the honest answer.
//   2. A RISING-WITH-EVIDENCE user (≥21 days of personal history + a recent rising-severity spine) surfaces
//      EXACTLY ONE bounded (≤ .medium), uncertainty-first, NON-alarmist flare warning.
//
// A flare composes already-gated signals (D-01: compose, NEVER re-derive). Plan 02 ships the real
// `FlareEngine.flareWarning(...)` in `EndoInsights/Sources/EndoInsights/FlareEngine.swift`, so the
// file-local RED placeholder shim that kept the shared scheme compiling in Plan 01 is now DELETED — these
// assertions exercise the REAL producer.
//
// Posture mirrors `RecommendationsEngineTests` EXACTLY: inject `Fixtures.day(0)` + `Fixtures.calendar`, no
// clock, deterministic; a private `flareWarnings(_:)` filter on `.flare`; an `assertNoForbiddenTerms` over
// causal ∪ imperative ∪ alarmist.
import Testing
import Foundation
@testable import EndoInsights

@Suite("INST-06 FlareEngine — suppressed/flat/insufficient → ZERO; rising-with-evidence → one bounded warning")
struct FlareEngineTests {

    /// The future producer entry point (Plan 02). Mirrors `RecommendationsEngine.recommendations` signature.
    private func flareWarning(_ snapshots: [DaySnapshot], todayOffset: Int = 0) -> [Insight] {
        FlareEngine.flareWarning(from: snapshots, asOf: Fixtures.day(todayOffset), calendar: Fixtures.calendar)
    }

    /// Only the `.flare` Insights (the engine routes through the same gate as every kind).
    private func flareWarnings(_ snapshots: [DaySnapshot]) -> [Insight] {
        flareWarning(snapshots).filter { $0.kind == .flare }
    }

    // The forbidden CAUSAL terms a flare warning must NEVER contain (mirrors the package ForbiddenTerms
    // non-causal posture, D-04).
    private let forbiddenCausalTerms = [
        "cause", "causes", "trigger", "triggers", "because", "will ", "leads to", "linked to",
        "worsen", "improve", "reduce", "relieve", "aggravate",
    ]

    // The forbidden IMPERATIVE substrings (D-04 directive verbs) a flare warning must NEVER contain.
    private let forbiddenImperatives = [
        "take", "stop", "avoid", "start", "should", "must", "use", "try", "cut",
    ]

    // The forbidden ALARMIST / DIAGNOSTIC / CERTAINTY substrings (Phase 12 D-04) a flare warning must NEVER
    // contain. The SAME list as `ForbiddenTerms.alarmist` / `ClaimsPolicy.forbiddenAlarmist` — note that
    // "warning" is DELIBERATELY ABSENT (the section header is "Flare early-warning", D-07).
    private let forbiddenAlarmist = [
        "attack", "alarm", "alert", "emergency", "urgent", "danger",
        "diagnos", "predict", "imminent", "certain", "definitely",
        "episode", "will flare", "guarantee",
    ]

    private func assertNoForbiddenTerms(_ insight: Insight) {
        let surface = (insight.headline + " " + insight.detail).lowercased()
        for term in forbiddenCausalTerms {
            #expect(!surface.contains(term),
                    "Flare copy must contain NO causal term \"\(term)\": \"\(surface)\".")
        }
        for verb in forbiddenImperatives {
            #expect(!surface.contains(verb),
                    "Flare copy must contain NO imperative substring \"\(verb)\": \"\(surface)\".")
        }
        for term in forbiddenAlarmist {
            #expect(!surface.contains(term),
                    "Flare copy must contain NO alarmist/diagnostic term \"\(term)\": \"\(surface)\".")
        }
    }

    // MARK: - Absence: suppressed / flat-no-rise → ZERO flare warning (the load-bearing honesty contract).

    @Test func suppressedUser_yieldsZeroFlareWarning() async throws {
        // A suppressed user (insight derivation turned OFF) must NEVER get a fabricated flare warning — the
        // most caution-sensitive surface is the MOST conservative (SC3 / D-08).
        #expect(flareWarnings(Fixtures.suppressedNoBleedingFixture()).isEmpty,
                "A suppressed user yields ZERO flare warning (never fabricated).")
    }

    @Test func flatNoRiseUser_yieldsZeroFlareWarning() async throws {
        // Substantial history (≥21 days) but a STEADY severity with NO recent rise → no rising spine → the
        // engine must withhold entirely (a flat history is not a warning).
        #expect(flareWarnings(Fixtures.flatNoRiseFixture()).isEmpty,
                "A flat / no-rise user yields ZERO flare warning (no rising spine to honestly warn about).")
    }

    // MARK: - Insufficiency cusp (A3): a recent rise but < 21 days of evidence → [] OR one notice, NO warning.

    @Test func underEvidenceRise_yieldsNoNonNoticeWarning() async throws {
        // A recent rise IS present, but the history is below the `minSample(.flare) == 21` floor. The honest
        // answer is EITHER nothing OR a single "not enough data yet" insufficiency notice — NEVER a
        // non-notice flare warning (insufficient evidence cannot be a warning, D-05).
        let insights = flareWarnings(Fixtures.risingButUnderEvidenceFixture())
        let nonNoticeWarnings = insights.filter { !$0.isInsufficiencyNotice }
        #expect(nonNoticeWarnings.isEmpty,
                "An under-21-evidence rise must surface NO non-notice flare warning (only [] or one insufficiency notice).")
        // If anything surfaces at all, it is at most ONE honest insufficiency notice (never a warning pile).
        #expect(insights.count <= 1,
                "An under-evidence rise surfaces at most ONE honest insufficiency notice, never multiple cards.")
        if let notice = insights.first {
            #expect(notice.isInsufficiencyNotice,
                    "The only card an under-evidence rise may surface is an honest insufficiency notice.")
        }
    }

    // MARK: - Surfaced: a rising-with-≥21-evidence user → exactly one bounded ≤ .medium flare warning.

    @Test func risingWithEvidence_surfacesOneBoundedWarning() async throws {
        let warnings = flareWarnings(Fixtures.risingFlareFixture())
        #expect(warnings.count == 1,
                "A rising pattern over ≥21 days of evidence surfaces EXACTLY ONE flare warning.")
        let warning = try #require(warnings.first)
        #expect(warning.kind == .flare)
        #expect(warning.window == .allTime,
                "A flare warning is window-independent (mirror recommendations/correlations).")
        #expect(warning.signalKey == "flare.warning",
                "The flare warning uses the stable, value-independent signalKey \"flare.warning\".")
        // Bounded, uncertainty-first (D-03): never high-confidence, never below the gate floor.
        #expect(warning.confidence <= .medium,
                "A flare warning is bounded at ≤ .medium — uncertainty-first, never a high-confidence alarm (D-03).")
        #expect(warning.confidence >= .low,
                "A surfaced flare warning is at least .low (gate-eligible); below that it is withheld.")
        #expect(warning.points.isEmpty, "A flare warning is a words-only card (no chart points).")
        #expect(warning.sampleSize >= 21,
                "A flare warning inherits the ≥21-day evidence floor (minSample(.flare) == 21).")
        #expect(!warning.isInsufficiencyNotice,
                "A real rising-with-evidence warning is NOT an insufficiency notice.")
        assertNoForbiddenTerms(warning)
    }

    // MARK: - Determinism: the same fixture twice yields exactly equal `[Insight]`.

    @Test func output_isDeterministic() async throws {
        let first = flareWarnings(Fixtures.risingFlareFixture())
        let second = flareWarnings(Fixtures.risingFlareFixture())
        #expect(first == second,
                "Same inputs → exactly equal flare [Insight] (Equatable, no RNG/clock).")
    }
}
