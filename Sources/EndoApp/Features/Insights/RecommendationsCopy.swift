// Requirement: INST-05 / D-03 / D-04 — the auditable, direction-aware recommendation-copy surface.
//
// Clones the `InsightsCopy` posture: an `enum` of fixed static strings + direction-aware template funcs
// + an `auditableStrings` array that `CopyAuditTests` scans against `ClaimsPolicy.forbiddenVerbs` /
// `forbiddenGenderedTokens` / `forbiddenImperatives` AND a numeric-confidence scan (D-11). A
// recommendation is a DISCUSSION PROMPT, never an instruction — the copy reads "you tend to feel
// better…" / "…something you might explore", never "you should avoid…".
//
// APP↔PACKAGE COPY MIRROR (mirrors the `ClaimsPolicy`↔`ForbiddenTerms` precedent): the EndoInsights
// package CANNOT import this app target (SC1), so the SAME curated template strings are duplicated in a
// package-internal `RecommendationCopy` enum (`EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift`).
// `RecommendationsEngine` (the package producer) emits its copy from that package mirror; THIS enum is
// the AUDITED surface whose `auditableStrings` `CopyAuditTests` scans. The two MUST stay BYTE-IDENTICAL —
// the package copy is additionally proven clean by the HonestyGate zero-forbidden-terms scan.
//
// SUBSTRING TRAP (Pitfall 2): the scans are CASE-INSENSITIVE SUBSTRING scans, so every word here is
// CURATED to avoid the forbidden substrings ENTIRELY — never "higher"/"lower" ("her" ⊂ both, gendered),
// never "use"/"start"/"cut"/"avoid"/"should" (imperatives), never "worsen"/"improve"/"reduce" (causal).
// The chosen wording — "feel better", "more intense", "something you might explore", "discuss",
// "a pattern in your own data" — is verified clean BY HAND and proven by `CopyAuditTests`.
import Foundation
import EndoInsights

/// The fixed + direction-aware recommendation copy the dashboard renders. Centralized as one auditable
/// surface scanned by `CopyAuditTests`; byte-identical to the package-internal `RecommendationCopy`.
enum RecommendationsCopy {

    // MARK: Section header (UI-SPEC) — the "Recommendations" section title.

    static let sectionRecommendations = "Recommendations"

    // MARK: Direction-aware headlines (NON-prescriptive discussion prompts, D-03/D-04)

    /// BENEFICIAL framing: the factor's present-group fares BETTER → the "feel better" discussion prompt.
    /// `factor` is an already-audited domain label (e.g. "activity", "gentle movement"). Verified clean:
    /// "feel better" / "tend" / "on days with" / "a pattern in your own data" contain NO causal verb, NO
    /// imperative substring (no take/stop/avoid/start/should/must/use/try/cut), NO gendered "her", NO digit.
    static func beneficialHeadline(factor: String) -> String {
        "You tend to feel better on days with \(factor) — a pattern in your own data."
    }

    /// AGGRAVATING framing: the factor's present-group fares WORSE → a DESCRIPTIVE prompt. Deliberately
    /// uses "more intense" (NOT "higher"/"lower" — the "her" gendered-substring trap) and "something you
    /// might explore" (NOT "avoid"/"cut" — imperatives). `symptom` is lowercased so it reads naturally.
    /// Verified clean against the causal / imperative / gendered / numeric scans.
    static func aggravatingHeadline(symptom: String, factor: String) -> String {
        "Your \(symptom.lowercased()) tends to be more intense on days with \(factor) — something you might explore."
    }

    /// The shared detail line — the honest "your own data, not advice" framing. "discuss" carries no
    /// "cut"/"use" substring; "a pattern in your own data" matches `ClaimsPolicy.allowedFraming`.
    static let detail = "This is a pattern in your own data, not advice — something to discuss with your clinician."

    /// Every fixed string this surface can show PLUS representative templated outputs (so the audit covers
    /// the interpolated forms), exactly as `InsightsCopy.auditableStrings` appends a representative line.
    /// The example interpolation values ("gentle movement", "pelvic pain", "poor sleep") are themselves
    /// curated clean against every scan.
    static var auditableStrings: [String] {
        [
            sectionRecommendations,
            detail,
            beneficialHeadline(factor: "gentle movement"),
            aggravatingHeadline(symptom: "pelvic pain", factor: "poor sleep"),
        ]
    }
}
