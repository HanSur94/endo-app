// Requirement: Pitfall 5 — claims/language policy as an enforceable checklist (ONBD-02, ONBD-03).
//
// `ClaimsPolicy` encodes RESEARCH §Claims & Language Policy as DATA so it is enforceable, not just
// documentation. Two forces are at play, both effectively irreversible (reputational/regulatory):
//   1. Medical-device line (MDR/DiGA): copy must stay informational and must avoid medical-purpose
//      verbs ("diagnose", "treat", "clinically proven", …). `allowedFraming` is the safe vocabulary;
//      `forbiddenVerbs` is the audit list.
//   2. Inclusive language (D-06/08): default copy must never assume the user is a woman.
//      `forbiddenGenderedTokens` is the audit set the `Terminology` copy-audit test reads from.
import Foundation

/// The enforceable claims/language policy (Pitfall 5). Tests and (later) copy reviews read from
/// these lists so the posture is checkable, not aspirational.
enum ClaimsPolicy {
    /// Safe, informational framing the app is allowed to use (RESEARCH §Claims & Language Policy).
    static let allowedFraming: [String] = [
        "informational",
        "patterns in your own data",
        "possible association",
        "worth discussing with your clinician",
        "not medical advice",
        "not a medical device",
        "self-tracking",
    ]

    /// Medical-purpose / causal verbs the app must NOT use in user-facing copy (MDR/DiGA line).
    static let forbiddenVerbs: [String] = [
        "diagnose",
        "detect",
        "treat",
        "cure",
        "prevent",
        "clinically validated",
        "clinically proven",
        "medical-grade",
        "trigger",
        "causes",
        "will",
    ]

    /// Gendered tokens that must never appear in DEFAULT copy (D-06/08). The `Terminology` copy-audit
    /// test asserts no default resolved string contains any of these.
    static let forbiddenGenderedTokens: [String] = [
        "she",
        "her",
        "women",
        "woman",
        "lady",
        "ladies",
    ]

    /// Phase-11 (D-04): the directive/IMPERATIVE verbs a RECOMMENDATION must never use — recommendations
    /// are DISCUSSION PROMPTS ("you might explore…", "something to discuss with your clinician…"), never
    /// instructions ("you should avoid dairy"). The `CopyAuditTests` imperative scan asserts no
    /// `RecommendationsCopy` string contains any of these as a substring, ON TOP OF the existing
    /// forbidden-verb / gendered-token / numeric-confidence scans.
    ///
    /// SUBSTRING TRAP (Pitfall 2): this is a CASE-INSENSITIVE SUBSTRING scan, so an innocent host word can
    /// collide — e.g. "use" ⊂ "because"/"caused", "start" ⊂ "started", "cut" ⊂ "cute", "her" (gendered)
    /// ⊂ "higher". Recommendation copy MUST therefore be CURATED to avoid the forbidden substrings ENTIRELY
    /// (the scan enforces; curation avoids). The curated discussion words ("explore", "discuss", "tend to
    /// feel better", "a pattern in your own data") are verified clean against this list in `CopyAuditTests`.
    static let forbiddenImperatives: [String] = [
        "take",
        "stop",
        "avoid",
        "start",
        "should",
        "must",
        "use",
        "try",
        "cut",
    ]
}
