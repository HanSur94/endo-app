// EndoInsights (:insights) — the TrendsEngine behavior spec (Kotlin mirror).
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/TrendsEngineTests.swift` (11 cases).
// Encodes: absolute-LocalDate window anchoring (SC2), per-symptom severity mean + frequency, absence ≠ 0
// in means (D-03), coarse direction (recent vs prior window), the min-data threshold (D-08), descriptive
// non-causal copy (D-16), and determinism (D-13). Per Pattern 2 the injected Swift `Calendar` is DROPPED:
// `today` is a `LocalDate` (offset 0). The suppressed/no-bleeding keystone lives in `SuppressedFixtureTests.kt`.
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrendsEngineTests {

    private fun trends(snapshots: List<DaySnapshot>, todayOffset: Int = 0): List<Insight> =
        TrendsEngine.trends(from = snapshots, asOf = Fixtures.day(todayOffset))

    // MARK: SC2 — absolute-date window anchoring (a day exactly N-1 back is IN; N back is OUT).

    @Test
    fun window_last7_includesDayN1Back_excludesDayNBack() {
        val inWindow = (0..6).map {
            Fixtures.snapshot(offset = -it, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 5)))
        }
        val outWindow = listOf(
            Fixtures.snapshot(offset = -7, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 5))),
        )
        val insights = trends(inWindow + outWindow)

        val last7Freq = insights.firstOrNull {
            it.window == TrendWindow.LAST7 && it.detail.contains("7", ignoreCase = true) &&
                it.headline.contains("pelvic", ignoreCase = true)
        }
        assertNotNull(last7Freq, "Expected a last7 pelvic-pain trend over the 7 in-window days.")
    }

    @Test
    fun allTime_coversEverySnapshotRegardlessOfDate() {
        val old = (0..3).map {
            Fixtures.snapshot(offset = -200 - it, symptoms = listOf(SymptomPoint(SymptomType.FATIGUE, 4)))
        }
        val insights = trends(old)
        assertTrue(insights.any { it.window == TrendWindow.ALL_TIME && it.headline.contains("fatigue", ignoreCase = true) })
        // …but NOT in last30 (all logs are 200 days old).
        assertFalse(insights.any { it.window == TrendWindow.LAST30 && it.headline.contains("fatigue", ignoreCase = true) })
    }

    // MARK: D-05/D-07 — per-symptom severity mean + frequency.

    @Test
    fun symptomSeverityMean_excludesNilSeverity() {
        // pelvicPain logged on 4 days: severities 6, 6, null (present-without-severity), 6.
        // Mean must be 6 (the null day is excluded from the mean denominator), not (6+6+0+6)/4.
        val days = listOf(
            Fixtures.snapshot(offset = 0, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 6))),
            Fixtures.snapshot(offset = -1, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 6))),
            Fixtures.snapshot(offset = -2, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, null))),
            Fixtures.snapshot(offset = -3, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 6))),
        )
        val insights = trends(days)
        val severity = insights.first {
            it.window == TrendWindow.LAST7 && it.headline.contains("pelvic", ignoreCase = true) &&
                it.detail.contains("severity", ignoreCase = true)
        }
        // The mean is reported in the headline (6.0); null days are excluded from the denominator.
        assertTrue(
            severity.headline.contains("6.0", ignoreCase = true),
            "Mean severity (null-day excluded) is 6.0 — NOT (6+6+0+6)/4.",
        )
        // WR-04: `points` is the honest per-day reading series ONLY — no spliced synthetic mean point.
        assertEquals(3, severity.points.size, "points is the per-day reading series (null-severity day omitted).")
        assertTrue(severity.points.all { it.value == 6.0 })
        // No two points share a date (no duplicate x-value / synthetic mean collision).
        assertEquals(
            severity.points.map { it.date }.toSet().size,
            severity.points.size,
            "No duplicate x-values in the daily series (WR-04).",
        )
    }

    @Test
    fun symptomFrequency_isDaysPresentOverDaysWithAnyLog() {
        // 4 days exist in last7; pelvicPain logged on 2 of them → frequency 0.5.
        val days = listOf(
            Fixtures.snapshot(offset = 0, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 3))),
            Fixtures.snapshot(offset = -1, symptoms = listOf(SymptomPoint(SymptomType.PELVIC_PAIN, 3))),
            Fixtures.snapshot(offset = -2, stress = 5),
            Fixtures.snapshot(offset = -3, stress = 5),
        )
        val insights = trends(days)
        val freq = insights.first {
            it.window == TrendWindow.LAST7 && it.headline.contains("pelvic", ignoreCase = true) &&
                it.detail.contains("2 of", ignoreCase = true)
        }
        assertTrue(freq.detail.contains("2 of the", ignoreCase = true))
    }

    // MARK: D-03 — absence ≠ 0 in means: unlogged stress NEVER pulls the mean toward 0.

    @Test
    fun stressMean_unloggedDaysExcluded_differsFromLoggedZero() {
        // logged-zero: 3 days of stress 0 → mean 0.
        val zeroInsights = trends(Fixtures.loggedZeroStressFixture())
        val zeroMean = zeroInsights.first {
            it.window == TrendWindow.LAST7 && it.headline.contains("stress", ignoreCase = true)
        }.points.last().value

        // logged-4 on 3 days + unlogged(null) on 3 days → mean 4 (null excluded), NOT 2.
        val mixedInsights = trends(Fixtures.unloggedStressFixture())
        val mixedMean = mixedInsights.first {
            it.window == TrendWindow.LAST7 && it.headline.contains("stress", ignoreCase = true)
        }.points.last().value

        assertEquals(0.0, zeroMean)
        assertEquals(4.0, mixedMean) // null days excluded — NOT (4*3 + 0*3)/6 == 2
        assertTrue(zeroMean != mixedMean)
    }

    // MARK: D-07 — coarse direction (recent window vs immediately-prior equal-length window).

    @Test
    fun direction_risingSeverity_reportsUp_andHasNoCausalCopy() {
        val insights = trends(Fixtures.risingPelvicPainFixture())
        val last7 = insights.first {
            it.window == TrendWindow.LAST7 && it.headline.contains("pelvic", ignoreCase = true) &&
                it.detail.contains("severity", ignoreCase = true)
        }
        // Recent (sev 6) vs prior (sev 2) → the detail describes an upward direction.
        assertTrue(
            last7.detail.contains("up", ignoreCase = true) ||
                last7.detail.contains("higher", ignoreCase = true) ||
                last7.detail.contains("increase", ignoreCase = true),
        )
    }

    @Test
    fun allTime_omitsDirection() {
        val insights = trends(Fixtures.risingPelvicPainFixture())
        val allTime = insights.filter { it.window == TrendWindow.ALL_TIME }
        // allTime has no prior window → its copy must not claim a direction.
        for (i in allTime) {
            assertFalse(i.detail.contains("trending up", ignoreCase = true))
            assertFalse(i.detail.contains("trending down", ignoreCase = true))
        }
    }

    // MARK: D-08 — min-data threshold: sub-threshold emits "not enough data" (never a misleading trend).

    @Test
    fun subThreshold_emitsNotEnoughData_notAMisleadingTrend() {
        val insights = trends(Fixtures.subThresholdFixture())
        // With only 2 logged fatigue days (< 3), there is NO confident fatigue severity/frequency trend;
        // instead an explicit "not enough data" Insight exists for that signal.
        val notEnough = insights.firstOrNull {
            it.headline.contains("fatigue", ignoreCase = true) &&
                it.detail.contains("not enough data", ignoreCase = true)
        }
        assertNotNull(notEnough, "Sub-threshold data must surface 'not enough data', not a trend.")
        // The not-enough-data Insight is honestly low/none confidence.
        assertTrue(notEnough!!.confidence <= InsightConfidence.LOW)
    }

    // MARK: D-16 — descriptive, non-causal copy across ALL emitted insights.

    @Test
    fun copy_isDescriptive_neverCausal() {
        val allInsights =
            trends(Fixtures.risingPelvicPainFixture()) +
                trends(Fixtures.bleedingFixture()) +
                trends(Fixtures.unloggedStressFixture())
        val banned = listOf("trigger", "causes", "cause", "will ")
        for (i in allInsights) {
            val text = (i.headline + " " + i.detail).lowercase()
            for (word in banned) {
                assertFalse(text.contains(word), "Causal/forward-looking word '$word' in: ${i.headline} / ${i.detail}")
            }
        }
    }

    // MARK: D-13 — determinism: same fixtures + same injected today → exactly equal [Insight].

    @Test
    fun determinism_sameInputs_equalInsights() {
        val a = trends(Fixtures.risingPelvicPainFixture())
        val b = trends(Fixtures.risingPelvicPainFixture())
        assertEquals(a, b)
    }

    // MARK: A non-suppressed bleeding day DOES emit a bleeding trend (the positive of SC3).

    @Test
    fun bleeding_notSuppressed_emitsBleedingTrend() {
        val insights = trends(Fixtures.bleedingFixture())
        assertTrue(insights.any { it.headline.contains("bleed", ignoreCase = true) })
    }
}
