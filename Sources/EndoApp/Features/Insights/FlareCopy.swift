// Requirement: INST-06 / D-03 / D-04 — the auditable, uncertainty-first flare early-warning copy surface.
//
// Clones the `RecommendationsCopy` posture: an `enum` of fixed static strings + an `auditableStrings`
// array that `CopyAuditTests` scans against `ClaimsPolicy.forbiddenVerbs` / `forbiddenGenderedTokens` /
// `forbiddenImperatives` / a numeric-confidence scan AND the NEW `forbiddenAlarmist` scan (D-04). A flare
// early-warning is a CALM, uncertainty-first DISCUSSION PROMPT — never an alarm, a diagnosis, a
// prediction, or a certainty. The copy reads "your recent pattern looks similar to times before more
// difficult days — something to keep an eye on and discuss with your clinician", never "a flare attack is
// imminent" / "we predict a flare" / "you will flare".
//
// APP↔PACKAGE COPY MIRROR (mirrors the `RecommendationsCopy`↔`RecommendationCopy` precedent): the
// EndoInsights package CANNOT import this app target (SC1), so the SAME curated strings are duplicated in
// a package-internal `FlareCopyText` enum (`EndoInsights/Sources/EndoInsights/FlareEngine.swift`).
// `FlareEngine` (the package producer) emits its copy from that package mirror; THIS enum is the AUDITED
// surface whose `auditableStrings` `CopyAuditTests` scans. The two MUST stay BYTE-IDENTICAL — enforced by
// `FlareDriftGuard` + `FlareCopyDriftTests` (byte-identity of every rendered string + alarmist-list parity).
//
// SUBSTRING TRAP (Pitfall 2): the scans are CASE-INSENSITIVE SUBSTRING scans, so every word here is
// CURATED to avoid the forbidden substrings ENTIRELY. The two LOAD-BEARING traps Phase 12 dodges:
//   • "her" ⊂ "higher" (gendered): the headline says "more difficult days", NEVER "higher-symptom days".
//   • "predict" ⊂ "prediction" (alarmist): the detail OMITS the naive "not a prediction" framing entirely;
//     the not-medical-advice posture is carried by "discuss with your clinician" + the pinned disclaimer.
// Each curated word is verified clean BY HAND against the alarmist ∪ causal ∪ imperative ∪ gendered ∪
// numeric union below and proven by `CopyAuditTests` + `FlareCopyDriftTests`.
import Foundation
import EndoInsights

/// The fixed flare-warning copy the dashboard renders. Centralized as one auditable surface scanned by
/// `CopyAuditTests`; byte-identical to the package-internal `FlareCopyText` (enforced by `FlareDriftGuard`).
enum FlareCopy {

    // MARK: Section header (UI-SPEC / D-07) — the ONLY sanctioned use of "warning".

    /// The "Flare early-warning" section title. `"warning"` is DELIBERATELY allowed ONLY in the HEADER
    /// (D-07) — it is excluded from the alarmist list precisely so this header is legal; the IN-CARD copy
    /// below never uses it.
    static let sectionFlare = "Flare early-warning"

    // MARK: The calm, uncertainty-first warning card (D-03/D-04)

    /// The headline. "more difficult days" deliberately avoids "higher" (the "her" gendered trap); no
    /// "predict"/"attack"/"certain"; uncertainty-first ("looks similar to times before").
    static let headline = "Your recent pattern looks similar to times before more difficult days."

    /// The detail line — the honest "your own data, keep an eye on it, discuss with your clinician"
    /// framing. "keep an eye on" / "discuss" carry no imperative substring (discuss has no "cut"/"use");
    /// "a pattern in your own data" matches `ClaimsPolicy.allowedFraming`. Deliberately OMITS "not a
    /// prediction" (which would trip "predict").
    static let detail = "This is a pattern in your own data — something to keep an eye on and discuss with your clinician."

    /// The optional "what's contributing" line, appended ONLY when a secondary signal fired (derived from
    /// the TYPED engine signals, never parsed from copy). "rising pattern" / "factor" / "recently" are
    /// clean against every scan (no "predict"/causal/imperative/gendered).
    static let contributing = "Based on a recent rising pattern and a factor you have logged recently."

    // MARK: The honest "not enough data yet" insufficiency notice (D-05 / A3 cusp)

    /// The insufficiency-notice title — reuses the `InsightsCopy.sparseTitle` wording for parity.
    static let insufficientTitle = "Not enough data yet."

    /// The insufficiency-notice subtitle — the honest "keep logging" framing, never a fabricated value.
    static let insufficientSubtitle = "Keep logging and this fills in over time."

    /// Every fixed string this surface can show, the surface `CopyAuditTests` scans against the full
    /// alarmist ∪ causal ∪ imperative ∪ gendered ∪ numeric union.
    static var auditableStrings: [String] {
        [
            sectionFlare,
            headline,
            detail,
            contributing,
            insufficientTitle,
            insufficientSubtitle,
        ]
    }
}
