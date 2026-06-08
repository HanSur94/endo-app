// Requirement: ONBD-04 — in-app privacy policy (offline source of truth, D-14).
// State: RED (Wave 0). Turned GREEN by Plan 02 when the PrivacyPolicyText constant exists.
//
// These tests intentionally FAIL now: the PrivacyPolicyText content constant does not exist yet.
// The real assertion is kept compiling behind a `#expect(Bool(false), ...)` RED placeholder;
// Plan 02 introduces the offline privacy-policy text (on-device + private iCloud storage, no
// third-party sharing/analytics) reachable from onboarding and Settings.
import Testing
import Foundation
@testable import EndoApp

@Suite("ONBD-04 privacy policy text")
struct PrivacyPolicyTests {

    // (a) PrivacyPolicyText is non-empty and mentions on-device, private iCloud, and no third-party sharing/analytics.
    @Test func privacyPolicy_mentionsOnDevicePrivateICloudAndNoSharing() async throws {
        // RED until Plan 02:
        //   #expect(!PrivacyPolicyText.body.isEmpty)
        //   #expect(PrivacyPolicyText.body.localizedCaseInsensitiveContains("on-device"))
        //   #expect(PrivacyPolicyText.body.localizedCaseInsensitiveContains("iCloud"))
        //   #expect(PrivacyPolicyText.body.localizedCaseInsensitiveContains("no third-party") ||
        //           PrivacyPolicyText.body.localizedCaseInsensitiveContains("no analytics"))
        #expect(Bool(false), "RED — implement PrivacyPolicyText constant in Plan 02")
    }
}
