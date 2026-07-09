// Requirement: ONBD-04 — in-app privacy policy (offline source of truth, D-14).
// The Android-adapted Kotlin port of the iOS `Sources/EndoApp/Content/PrivacyPolicyText.swift`.
//
// `PrivacyPolicyText.body` is the offline, plain-language privacy policy — the source of truth (D-14).
// It works with no network and no hosting dependency. It truthfully describes the Android
// architecture: data lives ON THIS DEVICE ONLY (there is NO iCloud/sync — Android is local-only, the
// no-INTERNET, no-backend posture proven by `ManifestPermissionsTest`), there are no servers, no
// accounts, and zero third-party sharing or analytics. The independent developer is the data
// controller (D-13) with a contact email.
//
// ANDROID ADAPTATION: all iOS "private iCloud / if you turn on sync" wording is REMOVED in favor of
// "stored on this device only — no cloud" (the Android reality). `PrivacyPolicyTest` guards that the
// word "iCloud" never reappears.
//
// EXEMPT from the curated `CopyAudit` substring scan (deliberately NOT in
// [com.endoapp.app.content.CopyAuditRegistry.sources]): long-form legal prose legitimately contains
// host words the forbidden-token gate would false-flag. It is pinned instead by its own must-contain
// test (`PrivacyPolicyTest`), mirroring the iOS `PrivacyPolicyTests`.
package com.endoapp.app.content

/**
 * The offline, in-app privacy policy (ONBD-04, D-14) — the single source of truth. MUST state:
 * on-device storage, no servers, no accounts, no third-party sharing/analytics, and name the
 * developer + [contactEmail] (enforced by `PrivacyPolicyTest`). MUST NOT contain iCloud/sync wording
 * (the Android local-only adaptation).
 */
object PrivacyPolicyText {
    /** Contact email for the data controller (D-13 — the developer's email until branding). */
    const val contactEmail = "sannahrush@googlemail.com"

    /**
     * Plain-language policy body — Android-adapted (local-only, no iCloud/sync). Built from paragraph
     * blocks joined by blank lines so each paragraph renders as continuous flowing text (source-line
     * wraps must never leak into the rendered copy as hard breaks).
     */
    val body: String = listOf(
        "Your privacy is the whole point.",
        "Where your data lives\n" +
            "Everything you log in Endo-App is stored on this device only. There is no cloud copy " +
            "and no backup to any server we run. Your health data does not leave your phone unless " +
            "you export it yourself.",
        "No servers, no accounts\n" +
            "Endo-App has no servers of its own. There is no account to create and no login. Your " +
            "health data never travels to any backend we operate, because we do not operate one.",
        "No third-party sharing, no analytics\n" +
            "There is no third-party sharing of your data and no third-party analytics or " +
            "advertising. We do not embed tracking SDKs. We do not sell, rent, or share your " +
            "information with anyone.",
        "Who is responsible\n" +
            "This app is built by an independent developer, who is the data controller for the " +
            "purposes of data-protection law. You can get in touch at $contactEmail.",
        "Your control\n" +
            "You give explicit consent before any health data is processed, and you can withdraw " +
            "that consent at any time in Settings. Because your data lives only on this device, you " +
            "stay in control of it.",
    ).joinToString("\n\n")
}
