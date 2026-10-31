package com.endoapp.app.content

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Statically pins the release-relevant build configuration to Play's requirements (AREL-05, threat
 * T-25-03): `targetSdk` / `compileSdk` == 36, `minSdk` == 26, and NO debuggable release.
 *
 * WHY A STATIC SCAN (not Robolectric): a Robolectric/`Build.VERSION` read reports the EMULATED SDK the
 * test harness runs under, NOT the app's declared `targetSdk` — so it cannot honestly assert the ship
 * value. The build script `app/build.gradle.kts` is the single source of the pins; scanning its text is
 * the honest, red-capable check (RESEARCH Alternatives Considered).
 *
 * `targetSdk 36` satisfies ANY Play target-API floor ≤ 36 — it is NEVER lowered to "match" a documented
 * lower floor (ASSUMED A1). The exact 2026 floor is re-verified live in Console at submission.
 *
 * NON-DEBUGGABLE RELEASE: the script currently declares NO `buildTypes { release { ... } }` block, so
 * the release build inherits AGP's default `isDebuggable = false` (compliant). This test asserts the
 * script never sets `isDebuggable = true` anywhere — adding a debuggable release turns it RED.
 *
 * The scan targets `build.gradle.kts` (a DIFFERENT file), so naming the pins in this test source is
 * self-consistent — no self-invalidation trap (the [com.endoapp.insights.VersionCatalogAuditTests] /
 * [com.endoapp.app.ManifestPermissionsTest] precedent).
 */
class TargetSdkComplianceTest {

    private companion object {
        val BUILD_SCRIPT_CANDIDATES = listOf(
            "build.gradle.kts",
            "app/build.gradle.kts",
            "android/app/build.gradle.kts",
        )
        val TARGET_SDK_36 = Regex("""targetSdk\s*=\s*36\b""")
        val COMPILE_SDK_36 = Regex("""compileSdk\s*=\s*36\b""")
        val MIN_SDK_26 = Regex("""minSdk\s*=\s*26\b""")
        val DEBUGGABLE_TRUE = Regex("""isDebuggable\s*=\s*true\b""")
    }

    /** Walk up from the test working dir until an ancestor holds the :app build script. */
    private fun resolveAppBuildScript(): File? {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (rel in BUILD_SCRIPT_CANDIDATES) {
                val candidate = File(dir, rel)
                // Guard against resolving a :insights or root build script: require it to carry the
                // application plugin + the defaultConfig pins so we only match the :app module.
                if (candidate.isFile) {
                    val text = candidate.readText()
                    if (text.contains("com.android.application") || text.contains("android.application")) {
                        return candidate
                    }
                }
            }
            dir = dir.parentFile
        }
        return null
    }

    @Test
    fun appBuildScriptPinsTargetSdk36_minSdk26_nonDebuggableRelease() {
        val script = resolveAppBuildScript()
        assertNotNull(
            "android/app/build.gradle.kts (the :app application module) not found — the Play " +
                "targetSdk/compileSdk == 36 + minSdk 26 + non-debuggable-release pins must be committed " +
                "and auditable (AREL-05, T-25-03).",
            script,
        )
        val text = script!!.readText()

        assertTrue(
            "app/build.gradle.kts must pin `targetSdk = 36` (Play API-level floor; ASSUMED A1 — never " +
                "lowered). Not found via $TARGET_SDK_36.",
            TARGET_SDK_36.containsMatchIn(text),
        )
        assertTrue(
            "app/build.gradle.kts must pin `compileSdk = 36`. Not found via $COMPILE_SDK_36.",
            COMPILE_SDK_36.containsMatchIn(text),
        )
        assertTrue(
            "app/build.gradle.kts must pin `minSdk = 26`. Not found via $MIN_SDK_26.",
            MIN_SDK_26.containsMatchIn(text),
        )
        assertTrue(
            "app/build.gradle.kts must NOT set `isDebuggable = true` anywhere — the shipped release " +
                "must not be debuggable (AREL-05, T-25-03).",
            !DEBUGGABLE_TRUE.containsMatchIn(text),
        )
    }
}
