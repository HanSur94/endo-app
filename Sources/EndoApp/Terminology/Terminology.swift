// Requirement: ONBD-03 — the neutral-default lookup ALL user-facing copy resolves through (D-08).
//
// `Terminology` is the copy choke-point (RESEARCH Pattern 4, Pitfall 5): every bleeding/cycle word
// the UI shows resolves through it, so the inclusive-language posture is enforceable from one place.
// Defaults are neutral ("bleeding"/"cycle") and contain NO gendered tokens (asserted by
// `TerminologyTests` against `ClaimsPolicy.forbiddenGenderedTokens`). The cycle word is always
// "cycle" (D-08) — never period/gendered framing by default.
import Foundation

/// The neutral-default terminology lookup (ONBD-03, D-08). Initialized from a `TerminologyPreference`;
/// all bleeding/cycle copy resolves through `bleedingNoun` / `cycleNoun`.
struct Terminology {
    private let preference: TerminologyPreference

    init(preference: TerminologyPreference = TerminologyPreference()) {
        self.preference = preference
    }

    /// The user-facing noun for bleeding. Default "bleeding" (D-08); reflects the user's choice
    /// ("period" / "menstruation") when customized (D-07).
    var bleedingNoun: String { preference.bleedingTerm.noun }

    /// The user-facing noun for the menstrual cycle. Always neutral "cycle" (D-08) — never gendered.
    var cycleNoun: String { "cycle" }

    // Plan-body aliases (`bleedingWord`/`cycleWord`) for call sites that read it that way.
    var bleedingWord: String { bleedingNoun }
    var cycleWord: String { cycleNoun }

    /// Every default user-facing string this lookup can produce — the surface the copy-audit test
    /// scans for gendered tokens.
    var auditableDefaultStrings: [String] { [bleedingNoun, cycleNoun] }
}
