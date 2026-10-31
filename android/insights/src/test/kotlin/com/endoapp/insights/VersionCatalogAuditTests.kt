package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The version-catalog supply-chain audit (AENG-07) — a NEW guard with no Swift mirror.
 *
 * `android/gradle/libs.versions.toml` is the zero-dependency audit surface: it must never
 * carry an analytics, auth/identity, or networking/backend coordinate. This test resolves the
 * catalog, strips comment prose, and fails the build if any forbidden coordinate substring
 * appears. It scans the `.toml` (a separate file), never its own source — so the forbidden
 * tokens can be named here safely (no self-invalidation trap).
 *
 * Red-capable: adding e.g. a fake `okhttp` or `firebase-analytics` library to the catalog
 * turns this test red.
 */
class VersionCatalogAuditTests {

    private companion object {
        // Policy: NONE of these substrings may appear in any catalog coordinate (case-insensitive).
        val FORBIDDEN_COORDINATE_SUBSTRINGS = listOf(
            // analytics / telemetry
            "analytics", "firebase", "crashlytics", "amplitude", "mixpanel", "segment",
            // auth / identity
            "oauth", "sign-in", "credential-manager", "auth",
            // networking / backend clients
            "retrofit", "okhttp", "ktor-client", "grpc", "apollo",
        )
    }

    /** Walk up from the test working directory until an ancestor holds `gradle/libs.versions.toml`. */
    private fun resolveCatalog(): File? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "gradle/libs.versions.toml")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        return null
    }

    @Test
    fun catalogContainsNoForbiddenCoordinate() {
        val catalog = resolveCatalog()
        assumeTrue(catalog != null, "Could not resolve android/gradle/libs.versions.toml — skipping catalog audit")

        // Strip line comments (# .. end-of-line) so the audit inspects only coordinate
        // declarations, never prose that legitimately names the forbidden tokens.
        val codeOnly = catalog!!.readLines()
            .joinToString("\n") { it.substringBefore("#") }
            .lowercase()

        for (token in FORBIDDEN_COORDINATE_SUBSTRINGS) {
            assertTrue(
                !codeOnly.contains(token),
                "Forbidden supply-chain coordinate substring '$token' found in libs.versions.toml — " +
                    "the :insights catalog is a zero-dependency audit surface (AENG-07)",
            )
        }
    }
}
