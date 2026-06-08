// Requirement: SYMP-02 — record pain location on a body map with severity (D-04).
//
// Logic-level binding test (NO rendering): tapping a `BodyRegion` with a severity persists a
// `PainPoint(region, severity)` through the Repository and round-trips. Re-tapping the SAME region
// with a new severity UPDATES (not duplicates). An unmarked region has no PainPoint. A `PainPoint`
// with an unknown `regionRaw` decodes nil-safely (T-02-13, no crash).
import Testing
import Foundation
import SwiftData
@testable import EndoApp

@Suite("SYMP-02 body-map binding")
struct BodyMapBindingTests {

    // Tapping a region → severity → setPainPoint persists a PainPoint that round-trips at the
    // logic level (no SwiftUI rendering involved).
    @Test func tappingRegion_producesPainPoint_atLogicLevel_noRender() async throws {
        let container = try makeModelContainer(inMemory: true)
        let repo = SwiftDataRepository(context: ModelContext(container))
        let day = Date(timeIntervalSince1970: 1_700_000_000)

        try repo.setPainPoint(region: .lowerAbdomenPelvis, severity: 7, on: day)

        let points = try repo.painPoints(on: day)
        #expect(points.count == 1)
        #expect(BodyRegion(raw: points.first?.regionRaw) == .lowerAbdomenPelvis)
        #expect(points.first?.severity == 7)
    }

    // Re-tapping the SAME region with a NEW severity updates the existing PainPoint — no duplicate.
    @Test func reTappingSameRegion_updatesSeverity_noDuplicate() async throws {
        let container = try makeModelContainer(inMemory: true)
        let repo = SwiftDataRepository(context: ModelContext(container))
        let day = Date(timeIntervalSince1970: 1_700_000_000)

        try repo.setPainPoint(region: .lowerBack, severity: 3, on: day)
        try repo.setPainPoint(region: .lowerBack, severity: 9, on: day)

        let points = try repo.painPoints(on: day)
        #expect(points.count == 1, "Re-tapping the same region must update, not duplicate.")
        #expect(points.first?.severity == 9)
    }

    // Distinct regions on the same day produce distinct PainPoints.
    @Test func distinctRegions_produceDistinctPainPoints() async throws {
        let container = try makeModelContainer(inMemory: true)
        let repo = SwiftDataRepository(context: ModelContext(container))
        let day = Date(timeIntervalSince1970: 1_700_000_000)

        try repo.setPainPoint(region: .bladder, severity: 4, on: day)
        try repo.setPainPoint(region: .shoulder, severity: 2, on: day)

        let points = try repo.painPoints(on: day)
        #expect(points.count == 2)
        let regions = Set(points.compactMap { BodyRegion(raw: $0.regionRaw) })
        #expect(regions == [.bladder, .shoulder])
    }

    // An unmarked region has no PainPoint (absence ≠ severity 0).
    @Test func unmarkedRegion_hasNoPainPoint() async throws {
        let container = try makeModelContainer(inMemory: true)
        let repo = SwiftDataRepository(context: ModelContext(container))
        let day = Date(timeIntervalSince1970: 1_700_000_000)

        try repo.setPainPoint(region: .hips, severity: 5, on: day)

        let points = try repo.painPoints(on: day)
        #expect(points.contains { BodyRegion(raw: $0.regionRaw) == .hips })
        #expect(!points.contains { BodyRegion(raw: $0.regionRaw) == .upperThighs },
                "An unmarked region must have no PainPoint.")
    }

    // T-02-13: a PainPoint with an unknown regionRaw decodes nil-safely — never crashes.
    @Test func unknownRegionRaw_decodesNilSafely_noCrash() async throws {
        let container = try makeModelContainer(inMemory: true)
        let ctx = ModelContext(container)
        ctx.insert(SchemaV1.PainPoint(regionRaw: "not_a_real_region_😈", severity: 3))
        try ctx.save()

        let fetched = try ctx.fetch(FetchDescriptor<SchemaV1.PainPoint>())
        #expect(fetched.count == 1)
        // Defensive decode returns nil for an unknown raw — the binding treats nil as "not marked".
        #expect(BodyRegion(raw: fetched.first?.regionRaw) == nil)
    }
}
