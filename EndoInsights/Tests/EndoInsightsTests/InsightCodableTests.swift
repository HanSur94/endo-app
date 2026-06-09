// Requirement: D-02/D-18 (Phase 8) — `Insight` (and its member value types) must be `Codable` so the
// app can serialize an `Insight` into a `CachedInsight` payload blob and decode it back EQUAL.
//
// The make-or-break contract (PATTERNS entry 1 / RESEARCH Pitfall 2): the `Codable` conformance is
// ADDITIVE (the value stays `Equatable`/`Sendable`/`Identifiable`) and there is NO custom `init(from:)`.
// The synthesized decode reads the STORED derived `id` from the blob — it MUST NOT re-derive via
// `DeterministicUUID`. So a round-trip preserves the exact stored `id` (`decoded.id == encoded.id`) and
// the whole value (`decoded == encoded`).
import Testing
import Foundation
@testable import EndoInsights

@Suite("Insight Codable round-trip (additive, id-preserving)")
struct InsightCodableTests {

    /// A full-field fixture: every member type exercised — `points`, `confidence`, `kind`, `window`,
    /// `sampleSize`, AND `isInsufficiencyNotice == true`.
    private func makeFullFieldInsight() -> Insight {
        Insight.derived(
            kind: .trend,
            window: .last30,
            signalKey: "symptom.pelvicPain.severity",
            headline: "Pelvic pain has been higher recently",
            detail: "Across your last 30 logged days, pelvic-pain severity trended upward.",
            confidence: .medium,
            points: [
                InsightPoint(date: Date(timeIntervalSince1970: 1_700_000_000), value: 3.5),
                InsightPoint(date: Date(timeIntervalSince1970: 1_700_086_400), value: 6.0),
                InsightPoint(date: Date(timeIntervalSince1970: 1_700_172_800), value: 4.25),
            ],
            sampleSize: 17,
            isInsufficiencyNotice: true)
    }

    @Test func insight_roundTrips_equalAcrossEncodeDecode() throws {
        let original = makeFullFieldInsight()
        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(Insight.self, from: data)
        // Equatable round-trip (A3: equality, not byte-identity).
        #expect(decoded == original)
        // Spot-check the non-id fields survive (incl. the insufficiency notice marker + points).
        #expect(decoded.points == original.points)
        #expect(decoded.confidence == original.confidence)
        #expect(decoded.sampleSize == original.sampleSize)
        #expect(decoded.isInsufficiencyNotice == true)
    }

    @Test func insight_decode_preservesStoredDerivedId_doesNotRegenerate() throws {
        let original = makeFullFieldInsight()
        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(Insight.self, from: data)
        // The DERIVED id must be the STORED one — never re-derived on decode (Pitfall / anti-pattern).
        #expect(decoded.id == original.id)
    }

    @Test func insightKind_roundTripsViaRawValue() throws {
        for kind in InsightKind.allCases {
            let decoded = try JSONDecoder().decode(InsightKind.self, from: JSONEncoder().encode(kind))
            #expect(decoded == kind)
        }
    }

    @Test func trendWindow_roundTripsViaRawValue() throws {
        for window in TrendWindow.allCases {
            let decoded = try JSONDecoder().decode(TrendWindow.self, from: JSONEncoder().encode(window))
            #expect(decoded == window)
        }
    }

    @Test func insightConfidence_roundTripsViaIntRawValue() throws {
        for confidence in InsightConfidence.allCases {
            let decoded = try JSONDecoder().decode(InsightConfidence.self, from: JSONEncoder().encode(confidence))
            #expect(decoded == confidence)
        }
    }

    @Test func insightPoint_roundTripsDateAndValue() throws {
        let point = InsightPoint(date: Date(timeIntervalSince1970: 1_695_000_000), value: 7.5)
        let decoded = try JSONDecoder().decode(InsightPoint.self, from: JSONEncoder().encode(point))
        #expect(decoded == point)
    }
}
