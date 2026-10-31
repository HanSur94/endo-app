// EndoInsights (:insights) — `ForbiddenTerms.forLanguage(lang)`: the DE/FR/ES claims policies loaded
// from the classpath (SAFE-01/SAFE-02/SAFE-04).
//
// iOS mirror: `Sources/EndoApp/Content/L10n/*` (ported in 26-04). Pure-JVM, no android./androidx.
// (BoundaryLintTests is the proof).
//
// Contract: an extension in a NEW file, so the English `ForbiddenTerms` object stays byte-identical
// (SAFE-04). Loading is fail-closed: a missing resource, an unknown/missing key or a wrong language
// throws; no code path ever returns an empty or partial policy. All three languages load together, so
// one broken file fails every lookup.
package com.endoapp.insights.l10n

import com.endoapp.insights.ForbiddenTerms
import kotlinx.serialization.json.Json

/** Strictly decodes the policy at classpath [resourcePath] and checks it belongs to [expected].
 *  Throws IllegalStateException on a missing resource or a failed check, SerializationException on a
 *  schema mismatch. */
internal fun loadLanguagePolicy(resourcePath: String, expected: PolicyLanguage): LanguageClaimsPolicy {
    val stream = ForbiddenTerms::class.java.getResourceAsStream(resourcePath)
        ?: error("l10n claims policy '$resourcePath' is not on the classpath (fail-closed: no policy means no check, so loading stops here)")
    val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
    val policy = Json.decodeFromString(LanguageClaimsPolicy.serializer(), text)
    check(policy.language == expected.tag) {
        "l10n claims policy '$resourcePath' declares language '${policy.language}', expected '${expected.tag}' (fail-closed)"
    }
    check(policy.folding.replace.all { it.size == 2 && it.all(String::isNotEmpty) }) {
        "l10n claims policy '$resourcePath' has a folding.replace entry that is not a pair of non-empty strings (fail-closed)"
    }
    return policy
}

/** All three policies, decoded once on first use. `lazy` is synchronized by default. */
private val localizedPolicies: Map<PolicyLanguage, LanguageClaimsPolicy> by lazy {
    PolicyLanguage.entries.associateWith { loadLanguagePolicy("/l10n/claims-policy.${it.tag}.json", it) }
}

/** The cached claims policy for [lang]. Returns the same instance on every call. */
fun ForbiddenTerms.forLanguage(lang: PolicyLanguage): LanguageClaimsPolicy = localizedPolicies.getValue(lang)
