// EndoInsights (:insights) — the package-local non-causal language policy.
//
// The Kotlin mirror of `EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift` (Phase 6, INST-04 /
// D-08 / D-16 / SC1). Pure-JVM — no android./androidx. (BoundaryLint is the proof). This is a
// PACKAGE-LOCAL MIRROR of the app's `ClaimsPolicy` forbidden lists (`Sources/EndoApp/Content/
// ClaimsPolicy.swift`): the package CANNOT import the app target (SC1 boundary), so the causal-verb
// posture is duplicated here as data and EXTENDED with the D-08 causal connectives. The `HonestyGate`
// scans every emitted insight's copy against these lists — descriptive, non-causal language is
// ENFORCED, not trusted.
//
// SUBSTRING SHARP EDGE (RESEARCH Pitfall 2): the scan is a case-insensitive SUBSTRING match, so an
// innocent host word can collide (e.g. the verb inside "because"). That is FINE for enforcement (both
// are forbidden), but producers must NEVER hand-write copy whose ONLY forbidden substring is an
// innocent host word — the mitigation is curation: insight copy is written to avoid the forbidden
// substrings entirely. The gate catches violations; the curated copy avoids them.
//
// DRIFT CONTRACT (AENG-05): `ClaimsPolicyDriftTests` scans the Swift `ClaimsPolicy.swift` source and
// asserts app `forbiddenVerbs` ⊆ `causal`, `forbiddenImperatives` == `imperative` (byte-equal), and
// `forbiddenAlarmist` set-equal `alarmist`. The lists below are the single source of truth on the
// Kotlin side.
package com.endoapp.insights

/** The non-causal language policy (mirrors the app's `ClaimsPolicy` forbidden lists, the causal list
 *  extended with the D-08 causal connectives). SINGLE SOURCE OF TRUTH for the shared forbidden
 *  substrings on the Kotlin side. Consumed by `HonestyGate` + the `ClaimsPolicyDriftTests` drift guard
 *  in `:insights`, AND — since Phase 18-05 — REUSED by `:app`'s `ClaimsPolicy` (widened from `internal`
 *  to `public` so the app layer references these lists rather than forking a second divergent copy;
 *  RESEARCH Pitfall 2 / T-18-11). The app layer adds ONLY app-only lists (gendered / false-precision
 *  tokens, allowedFraming); it NEVER re-declares causal/imperative/alarmist. */
public object ForbiddenTerms {

    /** Causal / medical-purpose terms that must NEVER appear in an emitted insight's headline or detail.
     *
     *  The first block mirrors the app's `ClaimsPolicy.forbiddenVerbs` (the MDR/DiGA causal-verb audit
     *  list); the second block adds the D-08 causal CONNECTIVES (a sentence implying causation reads as a
     *  medical claim even without a forbidden verb). All matched case-insensitively as substrings. This is
     *  a SUPERSET of the app list — the drift test asserts app ⊆ package, NOT equality. */
    val causal: List<String> = listOf(
        // --- Mirror of ClaimsPolicy.forbiddenVerbs (causal / medical-purpose verbs) ---
        "trigger",      // also "triggers"/"triggered"/"triggering".
        "cause",        // also "causes"/"caused"/"causing"; note the verb is a substring of "because" too.
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
        "worsen",       // also "worsens"/"worsened"/"worsening".
        "improve",      // also "improves"/"improved"/"improving" (a causal benefit claim).
        "reduce",       // also "reduces"/"reduced"/"reducing" (a causal-effect verb).
        "relieve",      // also "relieves"/"relieved" (a treatment-effect claim).
        "aggravate",    // also "aggravates"/"aggravated".
    )

    /** The directive / IMPERATIVE verbs a RECOMMENDATION must never use (D-04). A recommendation is a
     *  DISCUSSION PROMPT ("something you might explore…"), never an instruction. PACKAGE-LOCAL MIRROR of
     *  the app's `ClaimsPolicy.forbiddenImperatives` — the drift test asserts BYTE-EQUALITY (same values,
     *  same order). Case-insensitive substring scan, so curated copy avoids these entirely. */
    val imperative: List<String> = listOf(
        "take",
        "stop",
        "avoid",
        "start",
        "should",
        "must",
        "use",
        "try",
        "cut",
    )

    /** The ALARMIST / DIAGNOSTIC / CERTAINTY terms a FLARE early-warning must never use (D-04, Phase 12).
     *  A flare card is a CALM, uncertainty-first discussion prompt, NEVER an alarm, a diagnosis, a
     *  prediction, or a certainty. PACKAGE-LOCAL MIRROR of the app's `ClaimsPolicy.forbiddenAlarmist` —
     *  the drift test asserts SET-EQUALITY. `"diagnos"` is a stem (diagnose/diagnosis/diagnostic);
     *  `"predict"` catches predict/prediction; `"will flare"` is the alarmist-specific binary form. The
     *  word for the section header ("Flare early-warning") is DELIBERATELY excluded so the header does not
     *  self-fail; this scan covers the in-card copy. */
    val alarmist: List<String> = listOf(
        "attack", "alarm", "alert", "emergency", "urgent", "danger",
        "diagnos", "predict", "imminent", "certain", "definitely",
        "episode", "will flare", "guarantee",
    )

    /** The first forbidden term `text` contains (case-insensitive substring), or `null` if the copy is
     *  clean. Scans the causal terms, then the imperative directives (D-04), then the alarmist/diagnostic
     *  terms (D-04, Phase 12), so a causal claim OR an instruction OR an alarmist word in any emitted
     *  insight is withheld. Pure — value-in / value-out, no logging (V7). `lowercased()` → Kotlin
     *  `lowercase()` (ASCII-safe here). */
    fun violation(text: String): String? {
        val lowered = text.lowercase()
        return causal.firstOrNull { lowered.contains(it) }
            ?: imperative.firstOrNull { lowered.contains(it) }
            ?: alarmist.firstOrNull { lowered.contains(it) }
    }
}
