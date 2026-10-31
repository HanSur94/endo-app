package com.endoapp.insights

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The import-boundary lint — the Kotlin mirror of
 * `EndoInsights/Tests/EndoInsightsTests/BoundaryLintTests.swift` (AENG-04).
 *
 * `:insights` is a pure-Kotlin-JVM module: it must NEVER import `android.*` or `androidx.*`
 * (the Swift package enforced "Foundation-only"; the Kotlin equivalent is "no platform runtime").
 * This test resolves `insights/src/main/kotlin`, scans every `.kt` file, and fails on any
 * `import android.` / `import androidx.` line. It is a standing guard from day one — an
 * empty/near-empty main source set (Phase 17 lands no engine sources yet) legitimately passes.
 */
class BoundaryLintTests {

    // `import android.foo` / `import androidx.foo`, allowing leading whitespace.
    private val forbiddenImport = Regex("""^\s*import\s+(android\.|androidx\.)""")

    /** Walk up from the test working directory until an ancestor holds `insights/src/main/kotlin`. */
    private fun resolveMainSources(): File? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "insights/src/main/kotlin")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        return null
    }

    @Test
    fun mainSourcesImportNoAndroidOrAndroidx() {
        val sources = resolveMainSources()
        // Assumption-guard: when built outside the monorepo the module dir may be unresolvable —
        // skip rather than fail so `:insights` still passes in isolation.
        assumeTrue(sources != null, "Could not resolve insights/src/main/kotlin — skipping boundary lint")

        val ktFiles = sources!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        for (file in ktFiles) {
            file.readLines().forEachIndexed { idx, line ->
                assertFalse(
                    forbiddenImport.containsMatchIn(line),
                    "Forbidden android./androidx. import in ${file.name}:${idx + 1} — " +
                        ":insights must stay pure-JVM (AENG-04)",
                )
            }
        }
    }
}
