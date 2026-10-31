// EndoInsights (:insights) — contract guarantees the downstream plans and phases build against.
//
// The 1:1 Kotlin port of `EndoInsights/Tests/EndoInsightsTests/ContractTests.swift` (7 cases). Covers:
// the deterministic-id contract (Pitfall 4), DaySnapshot/Insight structural equality + nil-honesty,
// the confidence ordering, and the package-local enum defensive decode (Option D). The D-12
// import-boundary lint lives in the dedicated `BoundaryLintTests` (single source of truth).
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ContractTests {

    // MARK: Deterministic Insight.id (Pitfall 4)

    @Test
    fun derivedId_isStableForSameInputs() {
        val a = Insight.derived(
            kind = InsightKind.TREND, window = TrendWindow.LAST30, signalKey = "symptom.pelvicPain.severity",
            headline = "h", detail = "d", confidence = InsightConfidence.MEDIUM, points = emptyList(), sampleSize = 5,
        )
        val b = Insight.derived(
            kind = InsightKind.TREND, window = TrendWindow.LAST30, signalKey = "symptom.pelvicPain.severity",
            headline = "different copy", detail = "different", confidence = InsightConfidence.LOW, points = emptyList(), sampleSize = 9,
        )
        // Same kind+window+signalKey ⇒ same id, regardless of copy/confidence/points/sampleSize.
        assertEquals(a.id, b.id)
    }

    @Test
    fun derivedId_differsForDifferentSignals() {
        val a = Insight.derived(
            kind = InsightKind.TREND, window = TrendWindow.LAST30, signalKey = "symptom.pelvicPain.severity",
            headline = "h", detail = "d", confidence = InsightConfidence.MEDIUM, points = emptyList(), sampleSize = 5,
        )
        val bySignal = Insight.derived(
            kind = InsightKind.TREND, window = TrendWindow.LAST30, signalKey = "symptom.fatigue.severity",
            headline = "h", detail = "d", confidence = InsightConfidence.MEDIUM, points = emptyList(), sampleSize = 5,
        )
        val byWindow = Insight.derived(
            kind = InsightKind.TREND, window = TrendWindow.LAST7, signalKey = "symptom.pelvicPain.severity",
            headline = "h", detail = "d", confidence = InsightConfidence.MEDIUM, points = emptyList(), sampleSize = 5,
        )
        val byKind = Insight.derived(
            kind = InsightKind.CORRELATION, window = TrendWindow.LAST30, signalKey = "symptom.pelvicPain.severity",
            headline = "h", detail = "d", confidence = InsightConfidence.MEDIUM, points = emptyList(), sampleSize = 14,
        )
        assertNotEquals(a.id, bySignal.id)
        assertNotEquals(a.id, byWindow.id)
        assertNotEquals(a.id, byKind.id)
    }

    @Test
    fun derivedId_isWellFormedV5UUID() {
        val i = Insight.derived(
            kind = InsightKind.TREND, window = TrendWindow.ALL_TIME, signalKey = "factor.sleep.poor.frequency",
            headline = "h", detail = "d", confidence = InsightConfidence.HIGH, points = emptyList(), sampleSize = 30,
        )
        // RFC-4122 version 5 (name-based) + IETF variant (10xx → UUID.variant() == 2).
        assertEquals(5, i.id.version())
        assertEquals(2, i.id.variant())
    }

    // MARK: Structural equality + honesty (absence ≠ 0)

    @Test
    fun insight_isEquatable_includingDeterministicId() {
        val point = InsightPoint(date = LocalDate.of(1970, 1, 1), value = 1.0)
        val a = Insight.derived(
            kind = InsightKind.TREND, window = TrendWindow.LAST90, signalKey = "k",
            headline = "h", detail = "d", confidence = InsightConfidence.LOW, points = listOf(point), sampleSize = 7,
        )
        val b = Insight.derived(
            kind = InsightKind.TREND, window = TrendWindow.LAST90, signalKey = "k",
            headline = "h", detail = "d", confidence = InsightConfidence.LOW, points = listOf(point), sampleSize = 7,
        )
        assertEquals(a, b)
    }

    @Test
    fun daySnapshot_carriesNilHonestly_andIsEquatable() {
        val day = LocalDate.of(2025, 1, 1)
        fun snap(stress: Int?): DaySnapshot = DaySnapshot.of(day = day, stress = stress)
        // null (unlogged) and 0 (logged zero) are DISTINCT — absence ≠ 0.
        assertNotEquals(snap(null), snap(0))
        assertEquals(0, snap(0).stress)
        assertNull(snap(null).stress)
        assertEquals(snap(3), snap(3))
    }

    @Test
    fun confidence_isComparable() {
        assertTrue(InsightConfidence.NONE < InsightConfidence.LOW)
        assertTrue(InsightConfidence.LOW < InsightConfidence.MEDIUM)
        assertTrue(InsightConfidence.MEDIUM < InsightConfidence.HIGH)
    }

    // MARK: Package-local enum defensive decode (Option D)

    @Test
    fun enums_decodeDefensively() {
        assertEquals(SymptomType.PELVIC_PAIN, SymptomType.fromRaw("pelvicPain"))
        assertEquals(SymptomType.OTHER, SymptomType.fromRaw("garbage")) // unknown → .other sink
        assertEquals(SymptomType.OTHER, SymptomType.fromRaw(null))
        assertEquals(FoodTag.OTHER, FoodTag.fromRaw("garbage"))
        assertEquals(ActivityType.OTHER, ActivityType.fromRaw(null))

        assertEquals(BodyRegion.LOWER_BACK, BodyRegion.fromRawOrNull("lowerBack"))
        assertNull(BodyRegion.fromRawOrNull("garbage")) // unknown → null (not marked)
        assertNull(FlowIntensity.fromRawOrNull(null))
        assertNull(ActivityIntensity.fromRawOrNull("garbage"))
        assertEquals(SleepQuality.GOOD, SleepQuality.fromRawOrNull("good"))
        assertNull(SleepQuality.fromRawOrNull(null))
    }
}
