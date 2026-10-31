// EndoInsights (:insights) — classpath-parity proof for the DE/FR/ES claims policies (SAFE-01/SAFE-02).
//
// iOS mirror: the 26-04 bundle-parity test compares the app-bundle copy with the repo `l10n/` file.
// Pure-JVM, no android./androidx. (BoundaryLintTests is the proof).
//
// Contract: the bytes the matcher loads from the classpath are byte-identical to the reviewed repo
// files in `l10n/`, and only the policy files are bundled. The repo root comes ONLY from the
// `endo.repoRoot` system property wired in 26-01 (`android/insights/build.gradle.kts`); a missing
// property, a wrong root or a missing file FAILS. Unlike ClaimsPolicyDriftTests there is no skip.
package com.endoapp.insights.l10n

import com.endoapp.insights.ForbiddenTerms
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

class BundledPolicyParityTests {

    private fun repoRoot(): File {
        val root = System.getProperty("endo.repoRoot")
            ?: fail("System property endo.repoRoot is not set. It is wired in android/insights/build.gradle.kts " +
                "(26-01: tasks.test systemProperty(\"endo.repoRoot\", ...)); run the tests through Gradle.")
        val dir = File(root)
        assertTrue(
            File(dir, "android/settings.gradle.kts").isFile,
            "endo.repoRoot '$root' is not the repo root (no android/settings.gradle.kts there)",
        )
        return dir
    }

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun classpathBytesEqualRepoBytes(lang: PolicyLanguage) {
        val path = "/l10n/claims-policy.${lang.tag}.json"
        val stream = ForbiddenTerms::class.java.getResourceAsStream(path)
        assertNotNull(stream, "[$lang] $path is not on the classpath; re-run :insights:processResources")
        val bundled = stream!!.use { it.readBytes() }
        val repoFile = File(repoRoot(), "l10n/claims-policy.${lang.tag}.json")
        assertTrue(repoFile.isFile, "[$lang] repo file ${repoFile.path} is missing")
        val reviewed = repoFile.readBytes()
        assertTrue(
            bundled.contentEquals(reviewed),
            "[$lang] classpath $path (${bundled.size} bytes) differs from ${repoFile.path} (${reviewed.size} bytes); " +
                "the shipped policy must equal the reviewed file. Re-run :insights:processResources.",
        )
    }

    @Test
    fun classpathCarriesOnlyPolicyFiles() {
        assertNull(ForbiddenTerms::class.java.getResource("/l10n/glossary.json"), "glossary.json is test-only and must not be bundled")
        assertNull(ForbiddenTerms::class.java.getResource("/l10n/README.md"), "README.md must not be bundled")
    }
}
