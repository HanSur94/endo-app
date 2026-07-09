package com.endoapp.app.export

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pure-JVM boundary guard (threat T-23: keep the crypto/codec android-free so the whole layer runs on
 * the JVM test lane with no emulator). Mirrors `NoDynamicColorScanTest`'s walk-up-and-comment-strip
 * precedent: it locates the committed MAIN-source `export/` directory, then asserts the three crypto
 * files carry NO `import android.` / `import androidx.` line.
 *
 * The allowlist is by filename: only [ExportCrypto.kt], [ExportCodec.kt], [ExportError.kt] are pinned
 * android-free. The SAF seam + UI files that land in later plans (23-03/04) legitimately import
 * `android.*` and are exempt. Comments are stripped first so a KDoc may DOCUMENT `android.util.Base64`
 * by name (as [ExportCodec] does) without tripping the scan — only a real `import` line trips it.
 */
class ExportPurityTest {

    private companion object {
        val ANDROID_FREE_FILES = listOf("ExportCrypto.kt", "ExportCodec.kt", "ExportError.kt")
    }

    /** Strip Kotlin block + line comments so only real code (import lines) is scanned. */
    private fun stripComments(src: String): String {
        val noBlock = src.replace(Regex("(?s)/\\*.*?\\*/"), " ")
        return noBlock.lineSequence().joinToString("\n") { it.substringBefore("//") }
    }

    /** Walk up from the test working dir until an ancestor holds the committed main export dir. */
    private fun resolveExportDir(): File? {
        val relativeCandidates = listOf(
            "src/main/kotlin/com/endoapp/app/export",
            "app/src/main/kotlin/com/endoapp/app/export",
            "android/app/src/main/kotlin/com/endoapp/app/export",
        )
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (rel in relativeCandidates) {
                val candidate = File(dir, rel)
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        return null
    }

    @Test
    fun cryptoAndCodecAndError_importNoAndroidType() {
        val dir = resolveExportDir()
        assertNotNull(
            "Main-source export/ directory not found — the crypto core must be committed under " +
                "com/endoapp/app/export.",
            dir,
        )

        for (name in ANDROID_FREE_FILES) {
            val file = File(dir, name)
            // Non-vacuity guard: the three pinned files must actually exist.
            assertTrue("Expected android-free source $name to exist under $dir.", file.isFile)

            val code = stripComments(file.readText())
            for (line in code.lineSequence()) {
                val trimmed = line.trim()
                assertTrue(
                    "$name must be pure JVM — no `import android.` line (found: \"$trimmed\").",
                    !trimmed.startsWith("import android."),
                )
                assertTrue(
                    "$name must be pure JVM — no `import androidx.` line (found: \"$trimmed\").",
                    !trimmed.startsWith("import androidx."),
                )
            }
        }
    }
}
