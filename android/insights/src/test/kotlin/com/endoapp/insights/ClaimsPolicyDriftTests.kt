// EndoInsights (:insights) — the cross-platform ClaimsPolicy drift guard (AENG-05).
//
// NO Swift mirror; NOT part of the 157-case parity count. This is a standing structural drift guard:
// it regex-scans the APP's read-only source of truth,
// `Sources/EndoApp/Content/ClaimsPolicy.swift`, and asserts the Kotlin `ForbiddenTerms` lists have not
// drifted out of sync with the app's forbidden-substring policy. Silent divergence in these lists would
// let a causal / imperative / alarmist claim through on ONE platform only (T-17-23) — this test makes
// that divergence a RED build, not a convention.
//
// Three assertions, mirroring the RESEARCH ClaimsPolicy/ForbiddenTerms parity contract:
//   1. app `forbiddenVerbs` ⊆ package `causal`   — the package CATCHES every causal verb the app forbids
//      (a substring superset, not exact-set equality: the package mirrors app verbs by STEM — e.g. the
//      app's "causes" is caught by the package's "cause" — and extends the list with the D-08 causal
//      connectives). Three multi-word MARKETING phrases ("clinically validated/proven", "medical-grade")
//      are app-CHROME copy audited by the app's own CopyAuditTests; the insights engine emits only
//      templated insight copy that can never contain them, so the package deliberately does not mirror
//      them. They are the ONLY documented carve-out; any OTHER new app verb the package fails to catch is
//      a RED drift.
//   2. app `forbiddenImperatives` == package `imperative`  — BYTE-EQUAL (same values, same order).
//   3. app `forbiddenAlarmist` set-equal package `alarmist` — SET-EQUAL.
//
// Assumption-guarded (`assumeTrue`): when built outside the monorepo (the Swift source is absent), the
// scan is SKIPPED rather than failing — exactly like `EnumRawValueParityTests`.
package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

class ClaimsPolicyDriftTests {

    /** Walk up from the test working dir until an ancestor holds the given relative path. */
    private fun resolveFromRoot(relative: String): File? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        return null
    }

    /** The full `ClaimsPolicy.swift` source, or `null` when built outside the monorepo. */
    private fun claimsPolicySource(): String? =
        resolveFromRoot("Sources/EndoApp/Content/ClaimsPolicy.swift")?.readText()

    /** Extract the string literals from a `static let <property>: [String] = [ ... ]` flat array
     *  declaration, in source order (handles multiple literals per line). `null` if absent. */
    private fun swiftStringList(source: String, property: String): List<String>? {
        val header = Regex("""static\s+let\s+$property\s*:\s*\[String\]\s*=\s*\[""")
        val m = header.find(source) ?: return null
        val start = m.range.last + 1
        val end = source.indexOf(']', start)
        if (end < 0) return null
        val body = source.substring(start, end)
        return Regex("\"([^\"]*)\"").findAll(body).map { it.groupValues[1] }.toList()
    }

    // The app-CHROME marketing phrases the insights engine's templated copy can never emit; audited
    // app-side by CopyAuditTests, deliberately NOT mirrored into the package. The ONLY causal carve-out.
    private val appOnlyMarketingPhrases = setOf("clinically validated", "clinically proven", "medical-grade")

    @Test
    fun appForbiddenVerbs_areAllCaughtByPackageCausalSuperset() {
        val source = claimsPolicySource()
        assumeTrue(source != null, "ClaimsPolicy.swift not resolvable — skipping ClaimsPolicy drift scan (built outside the monorepo)")
        val appVerbs = swiftStringList(source!!, "forbiddenVerbs")
        assertNotNull(appVerbs, "Could not parse ClaimsPolicy.forbiddenVerbs — the drift scan cannot proceed (AENG-05).")

        for (verb in appVerbs!!) {
            if (verb in appOnlyMarketingPhrases) continue // documented app-chrome carve-out.
            assertNotNull(
                ForbiddenTerms.violation(verb),
                "Cross-platform drift (AENG-05): the app forbids the causal verb '$verb' but the Kotlin " +
                    "ForbiddenTerms package would NOT catch it. Add '$verb' (or its stem) to ForbiddenTerms.causal.",
            )
        }
    }

    @Test
    fun appForbiddenImperatives_areByteEqualToPackageImperative() {
        val source = claimsPolicySource()
        assumeTrue(source != null, "ClaimsPolicy.swift not resolvable — skipping ClaimsPolicy drift scan (built outside the monorepo)")
        val appImperatives = swiftStringList(source!!, "forbiddenImperatives")
        assertNotNull(appImperatives, "Could not parse ClaimsPolicy.forbiddenImperatives (AENG-05).")

        assertEquals(
            appImperatives,
            ForbiddenTerms.imperative,
            "Cross-platform drift (AENG-05): the app's forbiddenImperatives must be BYTE-EQUAL (same values, " +
                "same order) to the Kotlin ForbiddenTerms.imperative list.",
        )
    }

    @Test
    fun appForbiddenAlarmist_isSetEqualToPackageAlarmist() {
        val source = claimsPolicySource()
        assumeTrue(source != null, "ClaimsPolicy.swift not resolvable — skipping ClaimsPolicy drift scan (built outside the monorepo)")
        val appAlarmist = swiftStringList(source!!, "forbiddenAlarmist")
        assertNotNull(appAlarmist, "Could not parse ClaimsPolicy.forbiddenAlarmist (AENG-05).")

        assertEquals(
            appAlarmist!!.toSet(),
            ForbiddenTerms.alarmist.toSet(),
            "Cross-platform drift (AENG-05): the app's forbiddenAlarmist must be SET-EQUAL to the Kotlin " +
                "ForbiddenTerms.alarmist list.",
        )
    }
}
