// Requirement: DATA-05 / D-03/D-06/D-07/D-10 (Phase 9, Plan 09-03) — the auditable copy surface for the
// HealthKit connect/rationale/provenance/refresh UI. Clones the `InsightsCopy`/`CycleSummaryCopy` posture:
// an enum of fixed static strings + an `auditableStrings` array that `HealthConnectCopyAuditTests` scans
// against `ClaimsPolicy.forbiddenVerbs` / `forbiddenGenderedTokens` (the choke-point, threat T-09-15).
//
// HONESTY / SCOPE NOTES baked into the wording:
//   • Read-only + private + optional are stated plainly in the rationale (D-06).
//   • The badge reads "from Apple Health" (D-03) — pure provenance, never a claim about the value.
//   • NO forbidden verb (diagnose/detect/treat/cure/prevent/clinically …/trigger/causes/will) and NO
//     gendered token (she/her/women/woman/lady/ladies — incl. the "her" SUBSTRING trap: avoid words like
//     "here"/"there"/"gather"/"other"/"higher" that embed "her").
//   • `healthShareUsageDescription` is the SOURCE OF TRUTH for the Info.plist NSHealthShareUsageDescription
//     literal (Task 1 places the same string), so the audit covers the system-sheet copy too.
import Foundation

/// The fixed (non-engine) copy the HealthKit connect/rationale/provenance/refresh UI renders. One
/// auditable surface scanned by `HealthConnectCopyAuditTests` (mirrors `InsightsCopy`).
enum HealthConnectCopy {

    // MARK: - Rationale screen (shown BEFORE Apple's system sheet — D-06)

    /// The rationale headline. Avoids "her"/forbidden-verb substrings ("Connect Apple Health" is safe).
    static let rationaleHeadline = "Connect Apple Health"

    /// The read-only line (D-06). "read" is fine; avoids any forbidden verb.
    static let rationaleReadOnly = "Endo-App only reads from Apple Health. It never changes or adds anything."

    /// The private line (D-06). "stays on your device and in your private iCloud" — no gendered token.
    static let rationalePrivate = "Your data stays on your device and in your private iCloud. No one else can see it."

    /// The optional line (D-06). Plain "this is optional" framing.
    static let rationaleOptional = "This is optional. You can skip it, and you can turn it off anytime."

    /// What gets read, plain list (D-14/D-15/D-16). No micronutrient/medical framing.
    static let rationaleWhatItReads = "It can bring in your cycle, sleep, workouts, and meal days so you do not log them twice."

    // MARK: - Buttons

    /// Primary "Continue" — the ONLY path that proceeds to the system sheet (D-06).
    static let continueButton = "Continue"
    /// "Not now" dismiss — closes the rationale without requesting access (no nag, D-08).
    static let notNowButton = "Not now"
    /// The passive connect affordance label (D-05/D-07). Reused in Settings + on logging surfaces.
    static let connectButton = "Connect Apple Health"
    /// The deep-link-to-Settings label for a previously-decided user (D-08; reads cannot be re-requested).
    static let openSettingsButton = "Open Settings"
    /// The "Refresh from Health" action label (D-10).
    static let refreshButton = "Refresh from Health"

    // MARK: - Provenance badge (D-03)

    /// The "from Apple Health" provenance label rendered next to an imported value (D-03). Pure source
    /// tag — never a claim about the value. Avoids any gendered token.
    static let sourceBadge = "from Apple Health"

    // MARK: - Section + footers (Settings — D-07)

    /// The Settings section title for the connect/refresh rows (D-07).
    static let sectionTitle = "Apple Health"

    /// The Settings section footer: passive, non-nagging, states optional + private (D-07/D-08).
    static let sectionFooter = "Connecting Apple Health is optional. When you connect, Endo-App only reads your data, and it always stays in your private iCloud."

    /// The previously-decided footer line guiding the user to the Settings app (D-08). Apple does not let
    /// an app re-request a denied read, so the path is the system Settings — stated plainly, no nag.
    static let deniedFooter = "If you want to change what Apple Health shares, you can update it anytime in the Settings app."

    // MARK: - System-sheet usage string (source of truth for Info.plist — Task 1 mirrors this)

    /// The `NSHealthShareUsageDescription` literal (D-06). Task 1 places the SAME string in Info.plist;
    /// keeping it here lets the audit cover the system-sheet copy. Read-only + private + optional.
    static let healthShareUsageDescription = "Endo-App can read your cycle, sleep, workout, and meal-day records from Apple Health so you do not have to log them twice. It stays on your device and in your private iCloud, and it is entirely optional."

    // MARK: - Audit surface

    /// Every fixed string this surface can show — the array `HealthConnectCopyAuditTests` scans against
    /// `ClaimsPolicy.forbiddenVerbs` + `forbiddenGenderedTokens` (T-09-15).
    static var auditableStrings: [String] {
        [
            rationaleHeadline, rationaleReadOnly, rationalePrivate, rationaleOptional, rationaleWhatItReads,
            continueButton, notNowButton, connectButton, openSettingsButton, refreshButton,
            sourceBadge,
            sectionTitle, sectionFooter, deniedFooter,
            healthShareUsageDescription,
        ]
    }
}
