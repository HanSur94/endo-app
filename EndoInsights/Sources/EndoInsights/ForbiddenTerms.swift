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
        "trigger",
        "causes",
        "cause",        // NOTE: "cause" ⊂ "because"; curated copy avoids the host word (Pitfall 2).
        "will",
        "diagnose",
        "detect",
        "treat",
        "cure",
        "prevent",
        // --- D-08 causal connectives (a causal sentence reads as a medical claim) ---
        "because",
        "due to",
        "leads to",
        "results in",
        "responsible for",
    ]

    /// The first forbidden term `text` contains (case-insensitive substring), or `nil` if the copy is
    /// clean. Pure — value-in/value-out, no logging (V7).
    static func violation(in text: String) -> String? {
        let lowered = text.lowercased()
        return causal.first { lowered.contains($0) }
    }
}
