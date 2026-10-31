// The `:app` entry point to the DE/FR/ES claims policies (SAFE-01/SAFE-04).
//
// iOS mirror: `ClaimsPolicy+Localized.swift` (ported in 26-04).
//
// SINGLE SOURCE OF TRUTH (T-26-14): the per-language policies are NOT loaded or re-declared here. They
// are REUSED from `:insights` `ForbiddenTerms.forLanguage`, which decodes the shared repo-root
// `l10n/claims-policy.<tag>.json` files strictly and fails closed. A second loader in `:app` would let
// the app and the engine enforce different DE/FR/ES policies while both suites stayed green;
// `ClaimsPolicyLocalizedReuseTest` asserts no such fork exists. REUSED, never forked.
//
// An extension in a NEW file, so `ClaimsPolicy.kt` and its English lists stay byte-identical (SAFE-04).
package com.endoapp.app.content

import com.endoapp.insights.ForbiddenTerms
import com.endoapp.insights.l10n.LanguageClaimsPolicy
import com.endoapp.insights.l10n.PolicyLanguage
import com.endoapp.insights.l10n.forLanguage

/** The DE/FR/ES claims policy for [lang]: the very same cached instance `:insights` returns. */
fun ClaimsPolicy.forLanguage(lang: PolicyLanguage): LanguageClaimsPolicy = ForbiddenTerms.forLanguage(lang)
