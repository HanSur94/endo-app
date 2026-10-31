// EndoInsights (:insights) — the languages that carry a shared l10n claims policy (SAFE-01/SAFE-02/SAFE-04).
//
// iOS mirror: `Sources/EndoApp/Content/L10n/*` (ported in 26-04). Pure-JVM, no android./androidx.
// (BoundaryLintTests is the proof).
//
// Contract: one entry per `l10n/claims-policy.<tag>.json` file. There is deliberately NO English entry:
// the English lists stay in code (`ForbiddenTerms`, `ClaimsPolicy`) and are never replaced (SAFE-04).
package com.endoapp.insights.l10n

/** A language with a shared, data-driven claims policy. [tag] is the filename tag. */
enum class PolicyLanguage(val tag: String) {
    DE("de"),
    FR("fr"),
    ES("es"),
    ;

    companion object {
        /** The language for [tag]. Throws on an unknown tag: there is no fallback policy. */
        fun fromTag(tag: String): PolicyLanguage =
            entries.firstOrNull { it.tag == tag }
                ?: throw IllegalArgumentException("No l10n claims policy for language tag '$tag' (known: ${entries.joinToString { it.tag }})")
    }
}
