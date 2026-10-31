// The registered DE/FR/ES string sources — the l10n counterpart of `AuditableCopy`/`CopyAuditRegistry`
// (SAFE-01/SAFE-03, SC4; `l10n/README.md` §Registering a new string source).
//
// THE LOAD-BEARING RULE: every localized file in the repo is REGISTERED here IN THE SAME CHANGE that
// introduces it. A registered COPY source maps a path pattern to an extractor that returns
// `(lang, text, origin)` triples, and `L10nAuditTest` runs every extracted string through
// `ClaimsPolicy.forLanguage(lang)`. An unregistered string silently escapes the audit, so registration
// IS the enforcement, not a convenience. Later phases add one source line each (27 insight messages,
// 29 Android `values-*` strings, 30 xcstrings, 33 store copy), never a new mechanism.
package com.endoapp.app.l10n

import com.endoapp.app.content.ClaimsPolicy
import com.endoapp.app.content.forLanguage
import com.endoapp.insights.l10n.PolicyLanguage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** One localized string to audit: its [lang], the [text] as shipped, and the [origin] that names the
 *  repo-relative file plus the location inside it. */
data class L10nString(val lang: PolicyLanguage, val text: String, val origin: String)

/** COPY sources are extracted and audited. DOCUMENTATION sources are claimed (so the tripwire accepts
 *  them) but exempt, with a reason. */
enum class SourceRole { COPY, DOCUMENTATION }

/** A registered source. [pathPattern] matches repo-relative paths with `/` separators. [extract] is only
 *  called for COPY sources. */
data class L10nSource(
    val pathPattern: Regex,
    val role: SourceRole,
    val reason: String,
    val extract: (File) -> List<L10nString>,
)

object L10nSourceRegistry {

    /** Directory names never walked: VCS and tool state, planning notes, build output. */
    private val EXCLUDED_DIRS: Set<String> = setOf(
        ".git", ".planning", ".claude", "build", ".gradle", ".kotlin", ".idea", "DerivedData", ".build",
        ".swiftpm", "node_modules",
    )

    private val POLICY_FILE = Regex("""l10n/claims-policy\.(de|fr|es)\.json""")

    /** Every registered source. A new localized file needs a line here in the same change. */
    val sources: List<L10nSource> = listOf(
        L10nSource(
            pathPattern = POLICY_FILE,
            role = SourceRole.COPY,
            reason = "Only the `framing` entries are copy (approved disclaimer strings). The rules, allow-list " +
                "and selfTest fixtures are policy data that legitimately CONTAIN forbidden terms, the same " +
                "exemption the English ForbiddenTerms/ClaimsPolicy lists have.",
            extract = ::extractPolicyFraming,
        ),
    )

    /** The source claiming [relPath] (repo-relative, `/` separators), or null when none does. */
    fun claim(relPath: String): L10nSource? = sources.firstOrNull { it.pathPattern.matches(relPath) }

    /** Every string extracted from the COPY sources found under [root]. */
    fun collect(root: File): List<L10nString> =
        repoFiles(root).flatMap { rel ->
            val source = claim(rel)
            if (source == null || source.role != SourceRole.COPY) emptyList() else source.extract(File(root, rel))
        }

    /** The repo root from the `endo.repoRoot` system property. Missing means FAIL, never skip. */
    fun repoRoot(): File {
        val path = System.getProperty("endo.repoRoot")
            ?: throw AssertionError(
                "System property endo.repoRoot is not set. android/app/build.gradle.kts must set it in " +
                    "testOptions.unitTests.all { systemProperty(\"endo.repoRoot\", ...) }.",
            )
        val root = File(path)
        if (!File(root, "android/settings.gradle.kts").isFile) {
            throw AssertionError("endo.repoRoot '$path' is not the repo root (no android/settings.gradle.kts).")
        }
        return root
    }

    /**
     * THE audit helper. Every real scan and every red-capable control goes through it, so a control that
     * turns red proves the real scan would too. Returns one "origin: text → category/term" line per
     * string that hits its language's policy.
     */
    fun auditFindings(strings: List<L10nString>): List<String> =
        strings.mapNotNull { s ->
            ClaimsPolicy.forLanguage(s.lang).violation(s.text)?.let { v ->
                "${s.origin}: \"${s.text}\" → ${v.category}/${v.term}"
            }
        }

    /** Repo-relative paths (`/` separators) of every file under [root], skipping [EXCLUDED_DIRS]. */
    internal fun repoFiles(root: File): List<String> {
        val out = ArrayList<String>()
        fun walk(dir: File, prefix: String) {
            val children = dir.listFiles()?.sortedBy { it.name } ?: return
            for (child in children) {
                val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                if (child.isDirectory) {
                    if (child.name !in EXCLUDED_DIRS) walk(child, rel)
                } else {
                    out.add(rel)
                }
            }
        }
        walk(root, "")
        return out
    }

    private fun extractPolicyFraming(file: File): List<L10nString> {
        val tag = POLICY_FILE.find("l10n/${file.name}")?.groupValues?.get(1)
            ?: throw AssertionError("Policy file name '${file.name}' carries no de/fr/es tag.")
        val lang = PolicyLanguage.fromTag(tag)
        val framing: JsonArray = Json.parseToJsonElement(file.readText(Charsets.UTF_8))
            .jsonObject.getValue("framing").jsonArray
        return framing.mapIndexed { i, entry ->
            L10nString(lang, entry.jsonPrimitive.content, "l10n/${file.name}#framing[$i]")
        }
    }
}
