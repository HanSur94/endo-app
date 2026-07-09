// The `:app` enforceable claims / language policy — the Kotlin port of the iOS
// `Sources/EndoApp/Content/ClaimsPolicy.swift` (Pitfall 5; ONBD-02/03, D-06/08, D-11).
//
// TWO IRREVERSIBLE FORCES encoded as DATA so the posture is checkable, not aspirational:
//   1. Medical-device line (MDR/DiGA): copy stays informational, avoids medical-purpose / causal
//      verbs and alarmist / diagnostic / certainty language.
//   2. Inclusive language (D-06/08): default copy never assumes the user is a woman.
//
// SINGLE SOURCE OF TRUTH (RESEARCH Pitfall 2 / T-18-11): the shared causal / imperative / alarmist
// substring lists are NOT re-declared here. They are REUSED from `:insights`
// `com.endoapp.insights.ForbiddenTerms` — the drift-guarded single source (Phase 17's
// `ClaimsPolicyDriftTests` already asserts it stays faithful to the Swift `ClaimsPolicy.swift`).
// A second, divergent copy would let the two platforms silently enforce different policies while
// both suites stayed green; `ClaimsPolicyReuseTest` asserts no such fork exists.
//
// The `:app` layer ADDS ONLY the app-only lists the pure `:insights` engine never needs:
// `allowedFraming`, `forbiddenGenderedTokens`, `forbiddenPrecisionTokens`. These lists are policy
// DATA (they legitimately CONTAIN the forbidden tokens); the `CopyAudit` harness scans the
// registered `AuditableCopy` catalog, never these definitions.
package com.endoapp.app.content

import com.endoapp.insights.ForbiddenTerms

/**
 * The enforceable claims / language policy for `:app` user-facing copy. Tests (and later copy
 * reviews) read from these lists so the posture is checkable, not aspirational.
 *
 * The shared causal / imperative / alarmist lists are REUSED from the drift-guarded `:insights`
 * [ForbiddenTerms] single source (getters delegate — never re-declared). Only the app-only lists
 * are defined here.
 */
object ClaimsPolicy {

    // --- REUSED shared three (single source: :insights ForbiddenTerms, drift-guarded in Phase 17) ---

    /** Medical-purpose / causal verbs + D-08 causal connectives — REUSED, never forked. */
    val causal: List<String> get() = ForbiddenTerms.causal

    /** Directive / IMPERATIVE verbs a recommendation must never use (D-04) — REUSED, never forked. */
    val imperative: List<String> get() = ForbiddenTerms.imperative

    /** Alarmist / diagnostic / certainty terms a flare card must never use (D-04) — REUSED, never forked. */
    val alarmist: List<String> get() = ForbiddenTerms.alarmist

    // --- APP-ONLY lists (not needed by the pure :insights engine copy) ---

    /** Safe, informational framing the app is allowed to use (RESEARCH §Claims & Language Policy).
     *  Verbatim from the iOS `ClaimsPolicy.allowedFraming` (7 items). */
    val allowedFraming: List<String> = listOf(
        "informational",
        "patterns in your own data",
        "possible association",
        "worth discussing with your clinician",
        "not medical advice",
        "not a medical device",
        "self-tracking",
    )

    /** Gendered tokens that must never appear in DEFAULT copy (D-06/08). The copy-audit scans assert
     *  no registered string contains any of these. Verbatim from iOS `forbiddenGenderedTokens` (6 items).
     *  SUBSTRING TRAP (Pitfall 2): `"her"` ⊂ "higher"/"there"/"other" — curated copy avoids it entirely. */
    val forbiddenGenderedTokens: List<String> = listOf(
        "she",
        "her",
        "women",
        "woman",
        "lady",
        "ladies",
    )

    /** False-precision / numeric-confidence tokens that must never appear in user-facing copy (D-11):
     *  confidence is one of three plain-language words, NEVER a "%", "p =", "r =", or coefficient form.
     *  Mirrors the iOS `CopyAuditTests` `forbiddenPrecisionTokens` scan set. */
    val forbiddenPrecisionTokens: List<String> = listOf(
        "%",
        "p =",
        "p=",
        "r =",
        "r=",
        "p-value",
        "p value",
    )
}
