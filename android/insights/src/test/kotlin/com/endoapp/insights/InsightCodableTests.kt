// The Kotlin mirror of `EndoInsights/Tests/EndoInsightsTests/InsightCodableTests.swift` (6 @Test).
//
// The make-or-break contract: the `@Serializable` conformance is ADDITIVE (the value stays a structural
// data class) and there is NO custom decode. The synthesized decode reads the STORED derived `id` from
// the blob — it MUST NOT re-derive via `DeterministicUUID`. So a round-trip preserves the exact stored
// `id` (`decoded.id == encoded.id`) and the whole value (`decoded == encoded`).
//
// A3 / Open Question 3: this is a Kotlin-INDEPENDENT round-trip (encode→decode→equal), NOT a
// cross-platform byte compare — Swift `JSONEncoder` encodes `Date` as a 2001-epoch double, which is not
// portable. Only round-trip equality + id-preservation is asserted.
package com.endoapp.insights

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class InsightCodableTests {

    private val json = Json

    /** A full-field fixture: every member type exercised — `points`, `confidence`, `kind`, `window`,
     *  `sampleSize`, AND `isInsufficiencyNotice == true`. */
    private fun makeFullFieldInsight(): Insight = Insight.derived(
        kind = InsightKind.TREND,
        window = TrendWindow.LAST30,
        signalKey = "symptom.pelvicPain.severity",
        headline = "Pelvic pain has been higher recently",
        detail = "Across your last 30 logged days, pelvic-pain severity trended upward.",
        confidence = InsightConfidence.MEDIUM,
        points = listOf(
            InsightPoint(LocalDate.of(2023, 11, 14), 3.5),
            InsightPoint(LocalDate.of(2023, 11, 15), 6.0),
            InsightPoint(LocalDate.of(2023, 11, 16), 4.25),
        ),
        sampleSize = 17,
        isInsufficiencyNotice = true,
    )

    @Test
    fun insight_roundTrips_equalAcrossEncodeDecode() {
        val original = makeFullFieldInsight()
        val data = json.encodeToString(original)
        val decoded = json.decodeFromString<Insight>(data)
        // Equatable round-trip (equality, not byte-identity).
        assertEquals(original, decoded)
        // Spot-check the non-id fields survive (incl. the insufficiency notice marker + points).
        assertEquals(original.points, decoded.points)
        assertEquals(original.confidence, decoded.confidence)
        assertEquals(original.sampleSize, decoded.sampleSize)
        assertTrue(decoded.isInsufficiencyNotice)
    }

    @Test
    fun insight_decode_preservesStoredDerivedId_doesNotRegenerate() {
        val original = makeFullFieldInsight()
        val data = json.encodeToString(original)
        val decoded = json.decodeFromString<Insight>(data)
        // The DERIVED id must be the STORED one — never re-derived on decode (the anti-pattern).
        assertEquals(original.id, decoded.id)
    }

    @Test
    fun insightKind_roundTripsViaRawValue() {
        for (kind in InsightKind.entries) {
            val decoded = json.decodeFromString<InsightKind>(json.encodeToString(kind))
            assertEquals(kind, decoded)
        }
    }

    @Test
    fun trendWindow_roundTripsViaRawValue() {
        for (window in TrendWindow.entries) {
            val decoded = json.decodeFromString<TrendWindow>(json.encodeToString(window))
            assertEquals(window, decoded)
        }
    }

    @Test
    fun insightConfidence_roundTripsViaIntRawValue() {
        for (confidence in InsightConfidence.entries) {
            val decoded = json.decodeFromString<InsightConfidence>(json.encodeToString(confidence))
            assertEquals(confidence, decoded)
        }
    }

    @Test
    fun insightPoint_roundTripsDateAndValue() {
        val point = InsightPoint(LocalDate.of(2023, 9, 18), 7.5)
        val decoded = json.decodeFromString<InsightPoint>(json.encodeToString(point))
        assertEquals(point, decoded)
    }
}
