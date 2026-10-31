package com.endoapp.app.l10n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * No translated text can enter the repo unscanned (SC4, T-26-11).
 *
 * Any localized-looking file anywhere in the repo (Android `values-de*`/`values-b+es*`, Apple `fr.lproj`,
 * `*.xcstrings`, store-metadata `de-DE`/`es-419` folders, `*.fr.*`/`*-es.*` files, anything under
 * `l10n/`) must be claimed by a source in [L10nSourceRegistry], or this test fails with
 * "register an extractor in L10nSourceRegistry for <path>". Registration is what puts a file's strings
 * through `L10nAuditTest`, so a tripwire hit means unaudited copy.
 *
 * NON-VACUITY: the temp-root controls plant unknown localized files (all must be reported) and excluded
 * or registered ones (none may be), through the SAME [L10nSourceRegistry.unregisteredLocalizedFiles].
 */
class L10nTripwireTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun repoHasNoUnregisteredLocalizedFile() {
        val unregistered = L10nSourceRegistry.unregisteredLocalizedFiles(L10nSourceRegistry.repoRoot())
        assertTrue(
            unregistered.joinToString("\n") { "register an extractor in L10nSourceRegistry for $it" },
            unregistered.isEmpty(),
        )
    }

    @Test
    fun candidatesAreNotVacuous() {
        val candidates = L10nSourceRegistry.localizedCandidates(L10nSourceRegistry.repoRoot())
        for (expected in listOf(
            "l10n/claims-policy.de.json",
            "l10n/claims-policy.fr.json",
            "l10n/claims-policy.es.json",
            "l10n/glossary.json",
            "l10n/README.md",
        )) {
            assertTrue("The real-repo candidates must include $expected (saw $candidates).", expected in candidates)
        }
    }

    @Test
    fun tripwireFlagsUnknownLocalizedFiles() {
        val root = tmp.newFolder("repo")
        val planted = listOf(
            "android/app/src/main/res/values-de/strings.xml",
            "android/app/src/main/res/values-es-rUS/strings.xml",
            "android/app/src/main/res/values-b+es+419/strings.xml",
            "android/app/src/main/res/values-night-fr/strings.xml",
            "Sources/EndoApp/Localizable.xcstrings",
            "Sources/EndoApp/fr.lproj/InfoPlist.strings",
            "Sources/EndoApp/es-419.lproj/InfoPlist.strings",
            "fastlane/metadata/android/de-DE/full_description.txt",
            "fastlane/metadata/es-419/description.txt",
            "l10n/insight-messages.json",
            "docs/store-copy.es.txt",
            "docs/release-fr.md",
        )
        for (path in planted) touch(root, path)
        touch(root, "android/app/src/main/res/values/strings.xml") // English base: not localized
        touch(root, "docs/release-notes.md")
        assertEquals(planted.sorted(), L10nSourceRegistry.unregisteredLocalizedFiles(root).sorted())
    }

    @Test
    fun tripwireHonorsExclusionsAndRegistrations() {
        val root = tmp.newFolder("repo")
        File(L10nSourceRegistry.repoRoot(), "l10n").copyRecursively(File(root, "l10n"))
        touch(root, ".planning/notes.de.md")
        touch(root, "android/app/build/intermediates/res/values-de/strings.xml")
        touch(root, "android/.gradle/values-fr/strings.xml")
        touch(root, "android/.kotlin/values-es/strings.xml")
        touch(root, ".claude/worktrees/other/values-fr/strings.xml")
        touch(root, ".git/values-de/strings.xml")
        // A nested checkout (worktree `.git` file) below the root is its own repo and is skipped.
        touch(root, "nested/.git")
        touch(root, "nested/android/app/src/main/res/values-de/strings.xml")
        val candidates = L10nSourceRegistry.localizedCandidates(root)
        assertEquals(
            "Only the registered l10n files may be candidates in the exclusion root.",
            listOf(
                "l10n/README.md", "l10n/claims-policy.de.json", "l10n/claims-policy.es.json",
                "l10n/claims-policy.fr.json", "l10n/glossary.json",
            ),
            candidates.sorted(),
        )
        assertEquals(emptyList<String>(), L10nSourceRegistry.unregisteredLocalizedFiles(root))
    }

    @Test
    fun everyRegisteredSourceMatchesARealFile() {
        val files = L10nSourceRegistry.repoFiles(L10nSourceRegistry.repoRoot())
        for (source in L10nSourceRegistry.sources) {
            assertTrue(
                "Registered source ${source.pathPattern} matches no file in the repo (dead registration).",
                files.any { source.pathPattern.matches(it) },
            )
        }
    }

    private fun touch(root: File, relPath: String) {
        val file = File(root, relPath)
        file.parentFile?.mkdirs()
        file.writeText("x\n")
    }
}
