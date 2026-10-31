// The registered DE/FR/ES string sources — the l10n counterpart of `AuditableCopy`/`CopyAuditRegistry`
// (SAFE-01/SAFE-03, SC4; `l10n/README.md` §Registering a new string source).
//
// THE LOAD-BEARING RULE: every localized file in the repo is REGISTERED here IN THE SAME CHANGE that
// introduces it. A registered COPY source maps a path pattern to an extractor that returns
// `(lang, text, origin)` triples, and `L10nAuditTest` runs every extracted string through
// `ClaimsPolicy.forLanguage(lang)`. An unregistered string silently escapes the audit, so registration
// IS the enforcement, not a convenience. Later phases add one source line each (27 insight messages,
// 29 Android `values-*` strings, 30 xcstrings, 33 store copy), never a new mechanism.
//
// TRIPWIRE (SC4, T-26-11): `looksLocalized` recognises every localized-looking path, and
// `L10nTripwireTest` fails on any such file no source claims. The include patterns of the
// `localizedFiles` test input in `android/app/build.gradle.kts` mirror `looksLocalized`, so a new file
// reruns the tests.
package com.endoapp.app.l10n

import com.endoapp.app.content.ClaimsPolicy
import com.endoapp.app.content.forLanguage
import com.endoapp.insights.l10n.Glossary
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

    /** Directory names never walked: VCS and tool state, planning notes, build output. A directory below
     *  the root that holds its own `.git` (nested worktree or checkout, Pitfall 6) is skipped too. */
    private val EXCLUDED_DIRS: Set<String> = setOf(
        ".git", ".planning", ".claude", "build", ".gradle", ".kotlin", ".idea", "DerivedData", ".build",
        ".swiftpm", "node_modules",
    )

    private val POLICY_FILE = Regex("""l10n/claims-policy\.(de|fr|es)\.json""")

    private const val GLOSSARY_PATH = "l10n/glossary.json"

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
        L10nSource(
            pathPattern = Regex("""l10n/glossary\.json"""),
            role = SourceRole.COPY,
            reason = "Every de/fr/es `approved` form is copy and must pass its policy and the register. The " +
                "`banned` lists and selfTest pairs are fixtures that CONTAIN the forms they ban. The " +
                "`doNotTranslate` brand names (Health Connect, Endo-App) are kept verbatim in every language, " +
                "so they are not DE/FR/ES text and are not extracted.",
            extract = ::extractGlossaryApproved,
        ),
        L10nSource(
            pathPattern = Regex("""l10n/README\.md"""),
            role = SourceRole.DOCUMENTATION,
            reason = "Translator documentation and the normative matching spec. It is never shipped, and it " +
                "quotes forbidden terms on purpose to explain the rules.",
            extract = { emptyList() },
        ),
    )

    private const val TAGS = "de|fr|es"

    private const val ANDROID_RES_TYPES =
        "values|drawable|mipmap|layout|raw|xml|menu|font|anim|animator|color|navigation|transition|interpolator"

    /** An Android resource directory with a de/fr/es qualifier: `values-de`, `values-es-rUS`,
     *  `values-night-fr`, BCP-47 `values-b+es+419`. */
    private val ANDROID_RES_DIR = Regex(
        """($ANDROID_RES_TYPES)(-[A-Za-z0-9]+)*-(($TAGS)(-r[A-Z]{2})?|b\+($TAGS)(\+[A-Za-z0-9]+)*)(-[A-Za-z0-9]+)*""",
    )

    /** An Apple localization directory: `de.lproj`, `fr-CA.lproj`, `es_419.lproj`. */
    private val LPROJ_DIR = Regex("""($TAGS)([-_][A-Za-z0-9]+)*\.lproj""")

    /** A store-metadata locale directory: `de-DE`, `fr-FR`, `es-ES`, `es-419` (and other regions). */
    private val REGION_DIR = Regex("""($TAGS)-([A-Z]{2}|419)""")

    /** A file name tagged with a language: `store-copy.es.txt`, `release-fr.md`. */
    private val TAGGED_FILE = Regex(""".+[.-]($TAGS)\.[^/]+""")

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

    /** The strictly decoded glossary under [root]. Missing means FAIL, never skip. */
    fun glossary(root: File): Glossary {
        val file = File(root, GLOSSARY_PATH)
        if (!file.isFile) throw AssertionError("$GLOSSARY_PATH is missing under '$root' (fail, never skip).")
        return Glossary.decode(file.readText(Charsets.UTF_8))
    }

    /**
     * THE audit helper. Every real scan and every red-capable control goes through it, so a control that
     * turns red proves the real scan would too. Returns one "origin: text → category/term" line per policy
     * hit and per register hit. The register comes from the glossary under [root], the SAME root the
     * strings were collected from, so a mutated temp copy is judged by its own data.
     */
    fun auditFindings(strings: List<L10nString>, root: File = repoRoot()): List<String> {
        val glossary = glossary(root)
        return strings.flatMap { s ->
            listOfNotNull(
                ClaimsPolicy.forLanguage(s.lang).violation(s.text),
                glossary.registerViolation(s.lang, s.text),
            ).map { v -> "${s.origin}: \"${s.text}\" → ${v.category}/${v.term}" }
        }
    }

    /** True when [relPath] (repo-relative, `/` separators) looks like a localized file. */
    fun looksLocalized(relPath: String): Boolean {
        val segments = relPath.split('/')
        val dirs = segments.dropLast(1)
        val name = segments.last()
        return relPath.startsWith("l10n/") ||
            name.endsWith(".xcstrings") ||
            TAGGED_FILE.matches(name) ||
            dirs.any { ANDROID_RES_DIR.matches(it) || LPROJ_DIR.matches(it) || REGION_DIR.matches(it) }
    }

    /** Every localized-looking file under [root], walked top-down with the exclusions. */
    fun localizedCandidates(root: File): List<String> = repoFiles(root).filter(::looksLocalized)

    /** The localized-looking files under [root] that no registered source claims. */
    fun unregisteredLocalizedFiles(root: File): List<String> = localizedCandidates(root).filter { claim(it) == null }

    /** Repo-relative paths (`/` separators) of every file under [root], skipping [EXCLUDED_DIRS] and any
     *  nested checkout below the root. */
    internal fun repoFiles(root: File): List<String> {
        val out = ArrayList<String>()
        fun walk(dir: File, prefix: String) {
            val children = dir.listFiles()?.sortedBy { it.name } ?: return
            for (child in children) {
                val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                if (child.isDirectory) {
                    val nestedCheckout = File(child, ".git").exists()
                    if (child.name !in EXCLUDED_DIRS && !nestedCheckout) walk(child, rel)
                } else {
                    out.add(rel)
                }
            }
        }
        walk(root, "")
        return out
    }

    private fun extractGlossaryApproved(file: File): List<L10nString> {
        val glossary = Glossary.decode(file.readText(Charsets.UTF_8))
        return glossary.concepts.flatMap { concept ->
            PolicyLanguage.entries.flatMap { lang ->
                concept.forLanguage(lang).approved.map { form ->
                    L10nString(lang, form, "$GLOSSARY_PATH#${concept.id}.${lang.tag}")
                }
            }
        }
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
