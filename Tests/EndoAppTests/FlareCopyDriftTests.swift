// Phase 12 (WR-02 / WR-03) — the app↔package "drift guard" for the flare copy mirror and the alarmist
// forbidden-list mirror, mirroring the Phase-11 `RecommendationsCopyDriftTests` precedent.
//
// Two pairs of surfaces are duplicated across the SC1 app/package boundary with only MANUAL sync:
//   • WR-02: the curated flare CARD copy — `FlareCopy` (app, audited by CopyAuditTests) vs the
//            package-internal `FlareCopyText` (proven clean by the HonestyGate scan). Both headers state
//            they MUST stay BYTE-IDENTICAL, but nothing else enforced it: a change to one file would leave
//            the OTHER unaudited and shipping unchanged. This guard pins every rendered string together.
//   • WR-03: the alarmist forbidden list — `ClaimsPolicy.forbiddenAlarmist` (app) vs
//            `ForbiddenTerms.alarmist` (package). They are the two halves of the Phase-12 alarmist scan
//            and are documented to stay BYTE-SET-IDENTICAL; this asserts set-equality.
//
// The app test target sees only the package's PUBLIC API (it imports `EndoInsights` as a release product,
// not `@testable`), so the package-internal mirrors are reached through the public, test-support-only
// `FlareDriftGuard` shim — the only surface that can see BOTH halves. A future edit to one side without
// the other now fails HERE.
import Testing
import Foundation
@testable import EndoApp
import EndoInsights

@Suite("Phase-12 app↔package flare-mirror drift guards")
struct FlareCopyDriftTests {

    // MARK: - WR-02: the flare copy strings must stay BYTE-IDENTICAL across the boundary.

    @Test func headline_isByteIdenticalAcrossBoundary() {
        #expect(FlareCopy.headline == FlareDriftGuard.packageHeadline,
                "Flare headline drifted between app and package: app=\"\(FlareCopy.headline)\" package=\"\(FlareDriftGuard.packageHeadline)\".")
    }

    @Test func detail_isByteIdenticalAcrossBoundary() {
        #expect(FlareCopy.detail == FlareDriftGuard.packageDetail,
                "Flare detail drifted between app and package: app=\"\(FlareCopy.detail)\" package=\"\(FlareDriftGuard.packageDetail)\".")
    }

    @Test func contributing_isByteIdenticalAcrossBoundary() {
        #expect(FlareCopy.contributing == FlareDriftGuard.packageContributing,
                "Flare contributing note drifted between app and package: app=\"\(FlareCopy.contributing)\" package=\"\(FlareDriftGuard.packageContributing)\".")
    }

    @Test func sectionFlare_isByteIdenticalAcrossBoundary() {
        #expect(FlareCopy.sectionFlare == FlareDriftGuard.packageSectionFlare,
                "Flare section header drifted between app and package: app=\"\(FlareCopy.sectionFlare)\" package=\"\(FlareDriftGuard.packageSectionFlare)\".")
    }

    @Test func insufficientTitle_isByteIdenticalAcrossBoundary() {
        #expect(FlareCopy.insufficientTitle == FlareDriftGuard.packageInsufficientTitle,
                "Flare insufficiency title drifted between app and package: app=\"\(FlareCopy.insufficientTitle)\" package=\"\(FlareDriftGuard.packageInsufficientTitle)\".")
    }

    @Test func insufficientSubtitle_isByteIdenticalAcrossBoundary() {
        #expect(FlareCopy.insufficientSubtitle == FlareDriftGuard.packageInsufficientSubtitle,
                "Flare insufficiency subtitle drifted between app and package: app=\"\(FlareCopy.insufficientSubtitle)\" package=\"\(FlareDriftGuard.packageInsufficientSubtitle)\".")
    }

    // MARK: - WR-03: the alarmist forbidden list must stay set-identical across the boundary.

    @Test func alarmistList_isSetIdenticalAcrossBoundary() {
        let app = ClaimsPolicy.forbiddenAlarmist
        let pkg = FlareDriftGuard.packageForbiddenAlarmist
        // ORDER-independent set-equality (both are flat substring lists; order is irrelevant to the scan).
        #expect(Set(app) == Set(pkg),
                "Alarmist forbidden list drifted between app (ClaimsPolicy.forbiddenAlarmist) and package (ForbiddenTerms.alarmist): app=\(app) package=\(pkg).")
    }

    // Sanity: the mirrored surfaces are non-empty (catches an accidentally-emptied list/shim).
    @Test func mirroredSurfaces_areNonEmpty() {
        #expect(!FlareDriftGuard.packageHeadline.isEmpty)
        #expect(!FlareDriftGuard.packageDetail.isEmpty)
        #expect(!FlareDriftGuard.packageContributing.isEmpty)
        #expect(!FlareDriftGuard.packageSectionFlare.isEmpty)
        #expect(!FlareDriftGuard.packageInsufficientTitle.isEmpty)
        #expect(!FlareDriftGuard.packageInsufficientSubtitle.isEmpty)
        #expect(!FlareDriftGuard.packageForbiddenAlarmist.isEmpty)
        #expect(!ClaimsPolicy.forbiddenAlarmist.isEmpty)
    }
}
