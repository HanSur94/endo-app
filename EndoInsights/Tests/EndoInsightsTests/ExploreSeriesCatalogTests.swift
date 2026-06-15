// EndoInsights — Phase-14 catalog coverage, stable-id, and scale-taxonomy invariants (Plan 14-01).
//
// These unit tests carry the keystone's contract (the v1.0 `ChartScrubSelection` playbook — invariants
// live in unit tests, not E2Es):
//   • The catalog covers EVERY loggable DaySnapshot field (per-symptom-type, per-pain-region,
//     per-food-tag enumerated FROM .allCases, plus every singleton).
//   • Ids are unique (Hashable-on-id has no collisions — Pitfall 3).
//   • ScaleKind is correct per series, and bleeding appears as BOTH quantitative AND presence (OQ2).
import Testing
import Foundation
@testable import EndoInsights

@Suite("Explore series catalog (coverage, stable ids, scale taxonomy)")
struct ExploreSeriesCatalogTests {

    private var ids: Set<String> { Set(ExploreSeriesCatalog.all.map(\.id)) }

    private func series(_ id: String) -> ExploreSeries? {
        ExploreSeriesCatalog.all.first { $0.id == id }
    }

    // MARK: Coverage — enumerated FROM .allCases (cannot drift)

    @Test func catalogCoversEverySymptomType() {
        for type in SymptomType.allCases {
            let id = "symptom.\(type.rawValue).severity"
            #expect(ids.contains(id), "Missing catalog series for symptom \(type.rawValue)")
            // exactly one entry per symptom type
            #expect(ExploreSeriesCatalog.all.filter { $0.id == id }.count == 1)
        }
    }

    @Test func catalogCoversEveryBodyRegion() {
        for region in BodyRegion.allCases {
            let id = "pain.\(region.rawValue).severity"
            #expect(ids.contains(id), "Missing catalog series for pain region \(region.rawValue)")
            #expect(ExploreSeriesCatalog.all.filter { $0.id == id }.count == 1)
        }
    }

    @Test func catalogCoversEveryFoodTag() {
        for tag in FoodTag.allCases {
            let id = "food.\(tag.rawValue)"
            #expect(ids.contains(id), "Missing catalog series for food tag \(tag.rawValue)")
            #expect(ExploreSeriesCatalog.all.filter { $0.id == id }.count == 1)
        }
    }

    @Test func catalogHasSingletonFields() {
        let singletons = [
            "sleep.hours", "activity.minutes", "stress", "mood",
            "bleeding.intensity", "bleeding.event", "clots", "prn", "medication.taken",
        ]
        for id in singletons {
            #expect(ids.contains(id), "Missing singleton catalog series \(id)")
        }
    }

    // MARK: Stable ids (Pitfall 3 — Hashable-on-id proves no collisions)

    @Test func catalogIdsAreUnique() {
        #expect(Set(ExploreSeriesCatalog.all.map(\.id)).count == ExploreSeriesCatalog.all.count,
                "Catalog ids must be unique — a collision would silently merge two series under Set<ExploreSeries>.")
    }

    @Test func seriesHashableCollapsesOnSharedId() {
        // Two series with the same id are equal and hash-collide (id-only conformance).
        let a = ExploreSeries(id: "x", displayName: "A", unit: "", scaleKind: .ordinal0to10, field: .stress)
        let b = ExploreSeries(id: "x", displayName: "B different", unit: "of 10", scaleKind: .ordinal1to5, field: .mood)
        #expect(a == b, "Identical ids must compare equal regardless of other metadata.")
        #expect(Set([a, b]).count == 1, "Identical ids must collapse in a Set.")

        let c = ExploreSeries(id: "y", displayName: "A", unit: "", scaleKind: .ordinal0to10, field: .stress)
        #expect(a != c, "Distinct ids must compare unequal.")
        #expect(Set([a, c]).count == 2)
    }

    // MARK: Scale taxonomy correctness

    @Test func scaleKindsAreCorrect() {
        // per-symptom / per-pain / stress → ordinal0to10
        for type in SymptomType.allCases {
            #expect(series("symptom.\(type.rawValue).severity")?.scaleKind == .ordinal0to10)
        }
        for region in BodyRegion.allCases {
            #expect(series("pain.\(region.rawValue).severity")?.scaleKind == .ordinal0to10)
        }
        #expect(series("stress")?.scaleKind == .ordinal0to10)

        // mood → ordinal1to5
        #expect(series("mood")?.scaleKind == .ordinal1to5)

        // sleep / activity → continuous
        #expect(series("sleep.hours")?.scaleKind == .continuousHours)
        #expect(series("activity.minutes")?.scaleKind == .continuousMinutes)

        // bleeding.intensity → ordinalBleeding
        #expect(series("bleeding.intensity")?.scaleKind == .ordinalBleeding)

        // presence-kind series → presenceEvent
        for id in ["bleeding.event", "clots", "prn", "medication.taken"] {
            #expect(series(id)?.scaleKind == .presenceEvent)
        }
        for tag in FoodTag.allCases {
            #expect(series("food.\(tag.rawValue)")?.scaleKind == .presenceEvent)
        }
    }

    @Test func isQuantitativeMatchesScaleKind() {
        for s in ExploreSeriesCatalog.all {
            #expect(s.scaleKind.isQuantitative == (s.scaleKind != .presenceEvent))
            // presence series are NOT quantitative; everything else IS
            if s.scaleKind == .presenceEvent {
                #expect(!s.scaleKind.isQuantitative)
            } else {
                #expect(s.scaleKind.isQuantitative)
            }
        }
    }

    @Test func bleedingAppearsAsBothQuantitativeAndPresence() {
        // RESEARCH OQ2 RESOLVED — both framings catalogued, each suppression-gated downstream.
        let intensity = series("bleeding.intensity")
        let event = series("bleeding.event")
        #expect(intensity?.scaleKind == .ordinalBleeding)
        #expect(intensity?.scaleKind.isQuantitative == true)
        #expect(event?.scaleKind == .presenceEvent)
        #expect(event?.scaleKind.isQuantitative == false)
    }
}
