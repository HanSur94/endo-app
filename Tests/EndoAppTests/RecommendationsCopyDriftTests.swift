// Phase 11 (WR-02 / WR-03) — the app↔package "drift guard" for the recommendation copy mirror and the
// imperative forbidden-list mirror, mirroring the existing enum-parity precedent in
// `DaySnapshotMappingTests` ("…drifted between app and package").
//
// Two pairs of surfaces are duplicated across the SC1 app/package boundary with only MANUAL sync:
//   • WR-02: the curated recommendation TEMPLATES — `RecommendationsCopy` (app, audited by CopyAuditTests)
//            vs the package-internal `RecommendationCopy` (proven clean by the HonestyGate scan). Both
//            headers state they MUST stay BYTE-IDENTICAL, but nothing enforced it: a change to one file
//            would leave the OTHER unaudited and shipping unchanged.
//   • WR-03: the imperative forbidden list — `ClaimsPolicy.forbiddenImperatives` (app) vs
//            `ForbiddenTerms.imperative` (package). They are the two halves of the Phase-11 imperative
//            scan and are documented to "stay in sync", but no test asserted parity.
//
// The app test target sees only the package's PUBLIC API (it imports `EndoInsights` as a release product,
// not `@testable`), so the package-internal mirrors are reached through the public, test-support-only
// `RecommendationsDriftGuard` shim — the only surface that can see BOTH halves. A future edit to one side
// without the other now fails HERE.
import Testing
import Foundation
@testable import EndoApp
import EndoInsights

@Suite("Phase-11 app↔package recommendation-mirror drift guards")
struct RecommendationsCopyDriftTests {

    // MARK: - WR-02: the recommendation copy templates must stay BYTE-IDENTICAL across the boundary.

    // Representative interpolation values exercising both templates' interpolation points. Byte identity
    // of the RENDERED strings for the SAME inputs pins the two template literals together character-for-
    // character (any divergence in the surrounding fixed text fails the equality).
    private let sampleFactors = ["gentle movement", "dairy", "poor sleep", "as-needed pain relief"]
    private let sampleSymptoms = ["pelvic pain", "fatigue", "bleeding"]

    @Test func beneficialHeadline_isByteIdenticalAcrossBoundary() {
        for factor in sampleFactors {
            let app = RecommendationsCopy.beneficialHeadline(factor: factor)
            let pkg = RecommendationsDriftGuard.packageBeneficialHeadline(factor: factor)
            #expect(app == pkg,
                    "Beneficial recommendation copy drifted between app and package for factor \"\(factor)\": app=\"\(app)\" package=\"\(pkg)\".")
        }
    }

    @Test func aggravatingHeadline_isByteIdenticalAcrossBoundary() {
        for symptom in sampleSymptoms {
            for factor in sampleFactors {
                let app = RecommendationsCopy.aggravatingHeadline(symptom: symptom, factor: factor)
                let pkg = RecommendationsDriftGuard.packageAggravatingHeadline(symptom: symptom, factor: factor)
                #expect(app == pkg,
                        "Aggravating recommendation copy drifted between app and package for symptom \"\(symptom)\" / factor \"\(factor)\": app=\"\(app)\" package=\"\(pkg)\".")
            }
        }
    }

    @Test func detailLine_isByteIdenticalAcrossBoundary() {
        #expect(RecommendationsCopy.detail == RecommendationsDriftGuard.packageDetail,
                "Recommendation detail line drifted between app and package: app=\"\(RecommendationsCopy.detail)\" package=\"\(RecommendationsDriftGuard.packageDetail)\".")
    }

    // MARK: - WR-03: the imperative forbidden list must stay in sync across the boundary.

    @Test func imperativeList_isIdenticalAcrossBoundary() {
        let app = ClaimsPolicy.forbiddenImperatives
        let pkg = RecommendationsDriftGuard.packageForbiddenImperatives
        // ORDER-independent equality (both are flat substring lists; order is irrelevant to the scan).
        #expect(Set(app) == Set(pkg),
                "Imperative forbidden list drifted between app (ClaimsPolicy.forbiddenImperatives) and package (ForbiddenTerms.imperative): app=\(app) package=\(pkg).")
    }

    // WR-03 note: the CAUSAL lists are intentionally NOT equal and NOT a plain set-subset. Two reasons:
    //   1. The relationship is SUBSTRING COVERAGE, not literal membership — the package uses the stem
    //      "cause" to catch the app's "causes" (and "because"); a literal `Set.isSubset` would wrongly fail
    //      on "causes" even though the package gate fully covers it.
    //   2. A few app entries are MARKETING / medical-device claims ("medical-grade", "clinically
    //      validated"/"clinically proven") that the app forbids in its OWN user-facing surface but that the
    //      package gate has no reason to scan for — the package only scans engine-EMITTED insight copy,
    //      which is curated from a fixed template set and never contains marketing claims. These are
    //      app-surface-only by design and are excluded from the cross-boundary coverage guard.
    //
    // The honest invariant the guard asserts: every SHARED causal/medical-purpose verb the app forbids is
    // COVERED (as a substring) by some package causal term, so any engine copy the package gate passes
    // could not contain a causal verb the app would have rejected. A new SHARED causal verb added app-side
    // without a package counterpart fails here.
    @Test func causalList_sharedVerbsAreCoveredByPackage() {
        // App entries that are intentionally app-user-surface-only (marketing / medical-device claims the
        // engine never emits) — excluded from the cross-boundary coverage guard with rationale above.
        let appOnlyMarketingClaims: Set<String> = [
            "medical-grade", "clinically validated", "clinically proven",
        ]
        let pkg = RecommendationsDriftGuard.packageForbiddenCausal.map { $0.lowercased() }
        for verb in ClaimsPolicy.forbiddenVerbs {
            let lower = verb.lowercased()
            if appOnlyMarketingClaims.contains(lower) { continue }
            // Covered iff some package term is a substring of the app verb (the package stem catches the
            // app inflection, e.g. "cause" ⊂ "causes") OR vice-versa (the app verb is itself a package term).
            let covered = pkg.contains { lower.contains($0) || $0.contains(lower) }
            #expect(covered,
                    "Shared causal verb \"\(verb)\" (app ClaimsPolicy.forbiddenVerbs) is NOT covered by any package ForbiddenTerms.causal term — the lists drifted.")
        }
    }

    // Sanity: the mirrored surfaces are non-empty (catches an accidentally-emptied list/shim).
    @Test func mirroredSurfaces_areNonEmpty() {
        #expect(!ClaimsPolicy.forbiddenImperatives.isEmpty)
        #expect(!RecommendationsDriftGuard.packageForbiddenImperatives.isEmpty)
        #expect(!RecommendationsDriftGuard.packageDetail.isEmpty)
    }
}
