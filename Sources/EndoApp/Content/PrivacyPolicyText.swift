// Requirement: ONBD-04 — in-app privacy policy (offline source of truth, D-14).
//
// `PrivacyPolicyText.body` is the offline, plain-language privacy policy — the source of truth
// (D-14). It works with no network and no hosting dependency; the eventual hosted URL (D-15,
// deferred to App Store submission) will mirror this exact text. It truthfully describes the
// architecture verified across Plans 01–02: data lives on-device and in the user's PRIVATE iCloud,
// there are no servers, and there is zero third-party sharing or analytics. The independent
// developer is the data controller (D-13) with a contact email.
import Foundation

/// The offline, in-app privacy policy (ONBD-04, D-14). The single source of truth; the hosted URL
/// (D-15) will mirror this verbatim.
enum PrivacyPolicyText {
    /// Contact email for the data controller (D-13 — placeholder, the user's email until branding).
    static let contactEmail = "sannahrush@googlemail.com"

    /// Plain-language policy body. MUST state: on-device storage, private iCloud, no servers, no
    /// third-party sharing/analytics, and name the developer + contact email (enforced by
    /// `PrivacyPolicyTests`).
    static let body: String = """
    Your privacy is the whole point.

    Where your data lives
    Everything you log in Endo-App is stored on-device. If you turn on sync, your data is \
    kept only in your own private iCloud account — the same private storage your iPhone backups \
    use. Apple cannot read it, and neither can we.

    No servers, no accounts
    Endo-App has no servers of its own. There is no account to create and no login. Your health \
    data never travels to any backend we operate, because we do not operate one.

    No third-party sharing, no analytics
    There is no third-party sharing of your data and no third-party analytics or advertising. \
    We do not embed tracking SDKs. We do not sell, rent, or share your information with anyone.

    Who is responsible
    This app is built by an independent developer, who is the data controller for the purposes \
    of data-protection law. You can get in touch at \(contactEmail).

    Your control
    You give explicit consent before any health data is processed, and you can withdraw that \
    consent at any time in Settings. Because your data lives on your device and in your private \
    iCloud, you stay in control of it.
    """
}
