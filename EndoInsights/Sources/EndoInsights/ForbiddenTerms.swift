// EndoInsights — the package-local non-causal language policy (Phase 6, INST-04 / D-08 / D-16 / SC1).
//
// Foundation ONLY — NO SwiftData/SwiftUI/UIKit (SC1). This is a PACKAGE-LOCAL MIRROR of the app's
// `ClaimsPolicy.forbiddenVerbs` (`Sources/EndoApp/Content/ClaimsPolicy.swift`, lines 27-39): the package
// CANNOT import the app target (SC1 boundary), so the causal-verb posture is duplicated here as data and
// EXTENDED with the D-08 causal connectives. The `HonestyGate` scans every emitted insight's copy against
// this list — descriptive, non-causal language is ENFORCED, not trusted.
//
// CHOSEN APPROACH — LOCKED for Phase 7/11/12 producers (do NOT redesign):
//   • A CURATED hand-written copy list + a case-insensitive SUBSTRING scan.
//   • The scan is the ENFORCEMENT; it is NOT a copy generator. Producers write descriptive copy by hand
//     and this gate proves it stays non-causal.
//
// SUBSTRING SHARP EDGE (RESEARCH Pitfall 2): "cause" ⊂ "because". A naive substring scan flags the
// connective "because" via the verb "cause" — that is FINE here (both are forbidden), but the inverse
// risk is real: a producer must NEVER hand-write copy whose ONLY forbidden substring is an innocent
// host word. The mitigation is curation: insight copy is written to avoid the forbidden substrings
// entirely (e.g. "tended to co-occur with", not "may cause"). The gate catches violations; the curated
// copy avoids them. Every future insight kind (Ph7/11/12) MUST add its emitted copy to the
// zero-forbidden-terms test in `HonestyGateTests` — because the runtime enforcement is a DEBUG assert
// (see `HonestyGate.apply`), the TEST, not the release runtime, is the durable guarantee.
import Foundation

/// The package-local non-causal language policy (mirrors `ClaimsPolicy.forbiddenVerbs`, extended with
/// the D-08 causal connectives). Package-internal — only `HonestyGate` consumes it.
enum ForbiddenTerms {

    /// Causal / medical-purpose terms that must NEVER appear in an emitted insight's headline or detail.
    ///
    /// The first block mirrors `ClaimsPolicy.forbiddenVerbs` (the MDR/DiGA causal-verb audit list); the
    /// second block adds the D-08 causal CONNECTIVES (a sentence implying causation reads as a medical
    /// claim even without a forbidden verb). All matched case-insensitively as substrings.
    static let causal: [String] = [
        // --- Mirror of ClaimsPolicy.forbiddenVerbs (causal / medical-purpose verbs) ---
        "trigger",      // substring also catches "triggers"/"triggered"/"triggering".
        "cause",        // catches "causes"/"caused"/"causing"; NOTE: "cause" ⊂ "because" (Pitfall 2) — both forbidden, fine.
        "will",
        "diagnose",     // also "diagnoses"/"diagnosed".
        "detect",       // also "detects"/"detected".
        "treat",        // also "treats"/"treated"/"treatment".
        "cure",         // also "cures"/"cured".
        "prevent",      // also "prevents"/"prevented".
        // --- D-08 causal connectives (a causal sentence reads as a medical claim) ---
        "because",
        "due to",
        "leads to",
        "leading to",       // WR-03: the progressive connective the scan previously missed.
        "results in",
        "responsible for",
        "contributes to",   // WR-03: causal connective.
        "linked to",        // WR-03: a causal reading.
        // --- WR-03: causal inflections the policy intends but the list previously omitted ---
        "worsen",       // catches "worsen"/"worsens"/"worsened"/"worsening".
        "improve",      // catches "improve"/"improves"/"improved"/"improving" (a causal benefit claim).
        "reduce",       // catches "reduce"/"reduces"/"reduced"/"reducing" (a causal-effect verb).
        "relieve",      // catches "relieve"/"relieves"/"relieved" (a treatment-effect claim).
        "aggravate",    // catches "aggravate"/"aggravates"/"aggravated".
    ]

    /// Phase-11 (D-04): the directive/IMPERATIVE verbs a RECOMMENDATION must never use. A recommendation
    /// is a DISCUSSION PROMPT ("something you might explore…"), never an instruction ("you should avoid
    /// dairy"). PACKAGE-LOCAL MIRROR of the app's `ClaimsPolicy.forbiddenImperatives` (`Sources/EndoApp/
    /// Content/ClaimsPolicy.swift`): the package CANNOT import the app target (SC1), so the imperative list
    /// is duplicated here as data and MUST stay in sync with the app list. The `HonestyGate` now scans
    /// every emitted insight's copy against this list too (see `violation(in:)`), so an imperative in any
    /// engine-emitted recommendation is caught — the gate enforces, the curated copy avoids.
    ///
    /// SUBSTRING TRAP (Pitfall 2): this is a CASE-INSENSITIVE SUBSTRING scan, so an innocent host word can
    /// collide — e.g. "use" ⊂ "because"/"caused", "start" ⊂ "started", "cut" ⊂ "cute"/"acute",
    /// "try" ⊂ "country", "her" (gendered) ⊂ "higher". Emitted copy MUST therefore be CURATED to avoid
    /// the forbidden substrings ENTIRELY (the scan enforces; curation avoids). The curated recommendation
    /// words ("explore", "discuss", "tend to feel better", "a pattern in your own data") are verified
    /// clean against this list.
    static let imperative: [String] = [
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

    /// The first forbidden term `text` contains (case-insensitive substring), or `nil` if the copy is
    /// clean. Scans BOTH the causal terms AND the imperative directives (D-04) so a causal claim OR an
    /// instruction in any emitted insight is withheld. Pure — value-in/value-out, no logging (V7).
    static func violation(in text: String) -> String? {
        let lowered = text.lowercased()
        return causal.first { lowered.contains($0) }
            ?? imperative.first { lowered.contains($0) }
    }
}
