// Requirement: D-02/D-14/D-18 (Phase 8) — the Repository CachedInsight CRUD round-trip proof.
//
// Proves SwiftDataRepository.replaceCachedInsights(_:computedAt:fingerprint:) durably persists a gated
// `[Insight]` as CachedInsight rows and loadCachedInsights() decodes them back EQUAL (set/order-
// insensitive), that replace-all clears prior rows (no append), and that a corrupt/non-decodable
// payload blob is SKIPPED (compactMap) rather than crashing (RESEARCH Security V5 / T-08-04). The store
// is an in-memory SchemaV5 container (fast, deterministic, no Simulator/iCloud) — the round-trip seam
// the migration test does not exercise.
//
// CONSENT NOTE (T-08-02): like every sibling Repository method, the two CRUD methods call
// `assertConsented()` first. That is a debug-only `assert()` (a death-test, not a thrown error) — the
// same posture RepositoryDismissalTests relies on — so it is not exercised as a catchable throw here;
// the gate is proven structurally (the methods open with `assertConsented()`).
import Testing
import Foundation
import SwiftData
import EndoInsights
@testable import EndoApp

@Suite("D-02/D-18 CachedInsight CRUD round-trip")
struct CachedInsightRoundTripTests {

    /// Build an in-memory SchemaV5 container + a consent-defaulted repository (consentGuard defaults true).
    private func makeRepo() throws -> SwiftDataRepository {
        let container = try makeModelContainer(inMemory: true)
        return SwiftDataRepository(context: ModelContext(container))
    }

    /// A full-field insight fixture (points + isInsufficiencyNotice) — every field must survive the blob.
    private func insight(signalKey: String, notice: Bool = false) -> Insight {
        Insight.derived(
            kind: .trend,
            window: .last30,
            signalKey: signalKey,
            headline: "Headline for \(signalKey)",
            detail: "Detail for \(signalKey)",
            confidence: .medium,
            points: [
                InsightPoint(date: Date(timeIntervalSince1970: 1_700_000_000), value: 3.0),
                InsightPoint(date: Date(timeIntervalSince1970: 1_700_086_400), value: 5.5),
            ],
            sampleSize: 17,
            isInsufficiencyNotice: notice)
    }

    @Test func replaceThenLoad_roundTripsInsightsEqual() throws {
        let repo = try makeRepo()
        let a = insight(signalKey: "symptom.pelvicPain.severity", notice: true)
        let b = insight(signalKey: "symptom.fatigue.severity")

        try repo.replaceCachedInsights([a, b], computedAt: Date(timeIntervalSince1970: 1_701_000_000),
                                       fingerprint: "fp-1")

        let loaded = try repo.loadCachedInsights()
        // Set/order-insensitive compare (rows have no guaranteed fetch order).
        #expect(Set(loaded) == Set([a, b]))
        // The notice marker + points survived the blob.
        let pelvic = loaded.first { $0.signalKey == "symptom.pelvicPain.severity" }
        #expect(pelvic?.isInsufficiencyNotice == true)
        #expect(pelvic?.points.count == 2)
    }

    @Test func secondReplace_clearsPriorRows_replaceAll() throws {
        let repo = try makeRepo()
        let a = insight(signalKey: "symptom.pelvicPain.severity")
        let b = insight(signalKey: "symptom.fatigue.severity")
        let c = insight(signalKey: "symptom.nausea.severity")

        try repo.replaceCachedInsights([a, b], computedAt: .now, fingerprint: "fp-1")
        // Replace-all: the second write must DELETE the prior rows, not append.
        try repo.replaceCachedInsights([c], computedAt: .now, fingerprint: "fp-2")

        let loaded = try repo.loadCachedInsights()
        #expect(loaded == [c])
        #expect(loaded.count == 1)
    }

    @Test func emptyReplace_clearsCache() throws {
        let repo = try makeRepo()
        try repo.replaceCachedInsights([insight(signalKey: "symptom.pelvicPain.severity")],
                                       computedAt: .now, fingerprint: "fp-1")
        try repo.replaceCachedInsights([], computedAt: .now, fingerprint: "fp-2")
        #expect(try repo.loadCachedInsights().isEmpty)
    }

    @Test func corruptPayload_isSkipped_notFatal() throws {
        let container = try makeModelContainer(inMemory: true)
        let context = ModelContext(container)
        let repo = SwiftDataRepository(context: context)

        // One good insight (via the CRUD path) + one row with a deliberately non-decodable blob.
        try repo.replaceCachedInsights([insight(signalKey: "symptom.pelvicPain.severity")],
                                       computedAt: .now, fingerprint: "fp-1")
        context.insert(SchemaV1.CachedInsight(
            signalKey: "corrupt.row",
            kindRaw: "trend",
            windowRaw: "last30",
            payload: Data([0x00, 0x01, 0x02, 0xFF]),   // not valid JSON for an Insight
            computedAt: .now,
            fingerprint: "fp-1"))
        try context.save()

        // The corrupt row is compactMap-skipped (T-08-04) — load returns only the good insight, no crash.
        let loaded = try repo.loadCachedInsights()
        #expect(loaded.count == 1)
        #expect(loaded.first?.signalKey == "symptom.pelvicPain.severity")
    }
}
