// EndoInsights — the TrendsEngine behavior spec (Phase 5, D-05..D-08/D-13/D-16, INST-01/SC2).
//
// TDD RED-first: these reference `TrendsEngine.trends(from:asOf:calendar:)` which does NOT exist yet,
// so the suite fails to compile (RED). They encode: absolute-date window anchoring (SC2), per-symptom
// severity mean + frequency, absence ≠ 0 in means (D-03), coarse direction (recent vs prior window),
// the min-data threshold (D-08), descriptive non-causal copy (D-16), and determinism (D-13). The
// suppressed/no-bleeding keystone (SC3) lives in `SuppressedFixtureTests.swift`.
import Testing
import Foundation
@testable import EndoInsights

@Suite("INST-01 TrendsEngine — descriptive trends")
struct TrendsEngineTests {

    private func trends(_ snapshots: [DaySnapshot], todayOffset: Int = 0) -> [Insight] {
        TrendsEngine.trends(from: snapshots, asOf: Fixtures.day(todayOffset), calendar: Fixtures.calendar)
    }

    // MARK: SC2 — absolute-date window anchoring (a day exactly N-1 back is IN; N back is OUT).

    @Test func window_last7_includesDayN1Back_excludesDayNBack() async throws {
        // A symptom logged on offset -6 (in last7) and offset -7 (out of last7), plus filler to clear
        // the threshold inside last7.
        let inWindow = (0...6).map {
            Fixtures.snapshot(offset: -$0, symptoms: [SymptomPoint(type: .pelvicPain, severity: 5)])
        }
        let outWindow = [Fixtures.snapshot(offset: -7, symptoms: [SymptomPoint(type: .pelvicPain, severity: 5)])]
        let insights = trends(inWindow + outWindow)

        // The last7 frequency trend counts the 7 in-window days, NOT the day at offset -7.
        let last7Freq = insights.first {
            $0.window == .last7 && $0.detail.localizedCaseInsensitiveContains("7")
                && $0.headline.localizedCaseInsensitiveContains("pelvic")
        }
        #expect(last7Freq != nil, "Expected a last7 pelvic-pain trend over the 7 in-window days.")
    }

    @Test func allTime_coversEverySnapshotRegardlessOfDate() async throws {
        // A symptom logged far in the past (offset -200) still counts in allTime.
        let old = (0...3).map {
            Fixtures.snapshot(offset: -200 - $0, symptoms: [SymptomPoint(type: .fatigue, severity: 4)])
        }
        let insights = trends(old)
        #expect(insights.contains { $0.window == .allTime && $0.headline.localizedCaseInsensitiveContains("fatigue") })
        // …but NOT in last30 (all logs are 200 days old).
        #expect(!insights.contains { $0.window == .last30 && $0.headline.localizedCaseInsensitiveContains("fatigue") })
    }

    // MARK: D-05/D-07 — per-symptom severity mean + frequency.

    @Test func symptomSeverityMean_excludesNilSeverity() async throws {
        // pelvicPain logged on 4 days: severities 6, 6, nil (present-without-severity), 6.
        // Mean must be 6 (the nil day is excluded from the mean denominator), not (6+6+0+6)/4.
        let days = [
            Fixtures.snapshot(offset: 0, symptoms: [SymptomPoint(type: .pelvicPain, severity: 6)]),
            Fixtures.snapshot(offset: -1, symptoms: [SymptomPoint(type: .pelvicPain, severity: 6)]),
            Fixtures.snapshot(offset: -2, symptoms: [SymptomPoint(type: .pelvicPain, severity: nil)]),
            Fixtures.snapshot(offset: -3, symptoms: [SymptomPoint(type: .pelvicPain, severity: 6)]),
        ]
        let insights = trends(days)
        let severity = try #require(insights.first {
            $0.window == .last7 && $0.headline.localizedCaseInsensitiveContains("pelvic")
                && $0.detail.localizedCaseInsensitiveContains("severity")
        })
        // Mean encoded as an InsightPoint value (last point) — 6.0 exactly.
        let mean = try #require(severity.points.last?.value)
        #expect(mean == 6.0)
    }

    @Test func symptomFrequency_isDaysPresentOverDaysWithAnyLog() async throws {
        // 4 days exist in last7; pelvicPain logged on 2 of them → frequency 0.5.
        let days = [
            Fixtures.snapshot(offset: 0, symptoms: [SymptomPoint(type: .pelvicPain, severity: 3)]),
            Fixtures.snapshot(offset: -1, symptoms: [SymptomPoint(type: .pelvicPain, severity: 3)]),
            Fixtures.snapshot(offset: -2, stress: 5),
            Fixtures.snapshot(offset: -3, stress: 5),
        ]
        let insights = trends(days)
        let freq = try #require(insights.first {
            $0.window == .last7 && $0.headline.localizedCaseInsensitiveContains("pelvic")
                && $0.detail.localizedCaseInsensitiveContains("2 of")
        })
        #expect(freq.detail.localizedCaseInsensitiveContains("2 of the"))
    }

    // MARK: D-03 — absence ≠ 0 in means: unlogged stress NEVER pulls the mean toward 0.

    @Test func stressMean_unloggedDaysExcluded_differsFromLoggedZero() async throws {
        // logged-zero: 3 days of stress 0 → mean 0.
        let zeroInsights = trends(Fixtures.loggedZeroStressFixture())
        let zeroMean = try #require(zeroInsights.first {
            $0.window == .last7 && $0.headline.localizedCaseInsensitiveContains("stress")
        }?.points.last?.value)

        // logged-4 on 3 days + unlogged(nil) on 3 days → mean 4 (nil excluded), NOT 2.
        let mixedInsights = trends(Fixtures.unloggedStressFixture())
        let mixedMean = try #require(mixedInsights.first {
            $0.window == .last7 && $0.headline.localizedCaseInsensitiveContains("stress")
        }?.points.last?.value)

        #expect(zeroMean == 0.0)
        #expect(mixedMean == 4.0)            // nil days excluded — NOT (4*3 + 0*3)/6 == 2
        #expect(zeroMean != mixedMean)
    }

    // MARK: D-07 — coarse direction (recent window vs immediately-prior equal-length window).

    @Test func direction_risingSeverity_reportsUp_andHasNoCausalCopy() async throws {
        let insights = trends(Fixtures.risingPelvicPainFixture())
        let last7 = try #require(insights.first {
            $0.window == .last7 && $0.headline.localizedCaseInsensitiveContains("pelvic")
                && $0.detail.localizedCaseInsensitiveContains("severity")
        })
        // Recent (sev 6) vs prior (sev 2) → the detail describes an upward direction.
        #expect(last7.detail.localizedCaseInsensitiveContains("up")
            || last7.detail.localizedCaseInsensitiveContains("higher")
            || last7.detail.localizedCaseInsensitiveContains("increase"))
    }

    @Test func allTime_omitsDirection() async throws {
        let insights = trends(Fixtures.risingPelvicPainFixture())
        let allTime = insights.filter { $0.window == .allTime }
        // allTime has no prior window → its copy must not claim a direction.
        for i in allTime {
            #expect(!i.detail.localizedCaseInsensitiveContains("trending up"))
            #expect(!i.detail.localizedCaseInsensitiveContains("trending down"))
        }
    }

    // MARK: D-08 — min-data threshold: sub-threshold emits "not enough data" (never a misleading trend).

    @Test func subThreshold_emitsNotEnoughData_notAMisleadingTrend() async throws {
        let insights = trends(Fixtures.subThresholdFixture())
        // With only 2 logged fatigue days (< 3), there is NO confident fatigue severity/frequency trend;
        // instead an explicit "not enough data" Insight exists for that signal.
        let notEnough = insights.first {
            $0.headline.localizedCaseInsensitiveContains("fatigue")
                && $0.detail.localizedCaseInsensitiveContains("not enough data")
        }
        #expect(notEnough != nil, "Sub-threshold data must surface 'not enough data', not a trend.")
        // The not-enough-data Insight is honestly low/none confidence.
        if let ne = notEnough {
            #expect(ne.confidence <= .low)
        }
    }

    // MARK: D-16 — descriptive, non-causal copy across ALL emitted insights.

    @Test func copy_isDescriptive_neverCausal() async throws {
        let allInsights =
            trends(Fixtures.risingPelvicPainFixture())
            + trends(Fixtures.bleedingFixture())
            + trends(Fixtures.unloggedStressFixture())
        let banned = ["trigger", "causes", "cause", "will "]
        for i in allInsights {
            let text = (i.headline + " " + i.detail).lowercased()
            for word in banned {
                #expect(!text.contains(word), "Causal/forward-looking word '\(word)' in: \(i.headline) / \(i.detail)")
            }
        }
    }

    // MARK: D-13 — determinism: same fixtures + same injected today → exactly equal [Insight].

    @Test func determinism_sameInputs_equalInsights() async throws {
        let a = trends(Fixtures.risingPelvicPainFixture())
        let b = trends(Fixtures.risingPelvicPainFixture())
        #expect(a == b)
    }

    // MARK: A non-suppressed bleeding day DOES emit a bleeding trend (the positive of SC3).

    @Test func bleeding_notSuppressed_emitsBleedingTrend() async throws {
        let insights = trends(Fixtures.bleedingFixture())
        #expect(insights.contains { $0.headline.localizedCaseInsensitiveContains("bleed") })
    }
}
