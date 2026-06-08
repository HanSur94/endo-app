// Requirement: ONBD-04 — in-app privacy policy (offline source of truth, D-14).
// State: GREEN (Plan 03). The `PrivacyPolicyText.body` constant now exists.
//
// These assertions prove the offline policy truthfully states the privacy architecture (on-device +
// private iCloud, no servers, no third-party sharing/analytics) and names the developer + contact.
import Testing
import Foundation
@testable import EndoApp

@Suite("ONBD-04 privacy policy text")
struct PrivacyPolicyTests {

    // (a) PrivacyPolicyText is non-empty and mentions on-device, private iCloud, no servers, and no third-party sharing/analytics.
    @Test func privacyPolicy_mentionsOnDevicePrivateICloudAndNoSharing() async throws {
        let body = PrivacyPolicyText.body
        #expect(!body.isEmpty)
        #expect(body.localizedCaseInsensitiveContains("on-device"))
        #expect(body.localizedCaseInsensitiveContains("private iCloud"))
        #expect(body.localizedCaseInsensitiveContains("no servers"))
        #expect(
            body.localizedCaseInsensitiveContains("no third-party") ||
            body.localizedCaseInsensitiveContains("no analytics")
        )
    }

    // (b) The policy names the independent developer as controller and provides the contact email (D-13).
    @Test func privacyPolicy_namesControllerAndContact() async throws {
        let body = PrivacyPolicyText.body
        #expect(body.localizedCaseInsensitiveContains("data controller"))
        #expect(body.contains(PrivacyPolicyText.contactEmail))
        #expect(PrivacyPolicyText.contactEmail == "sannahrush@googlemail.com")
    }
}
