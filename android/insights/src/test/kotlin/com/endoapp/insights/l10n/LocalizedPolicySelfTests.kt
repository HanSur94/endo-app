// EndoInsights (:insights) — the shared-fixture verdict tests for the DE/FR/ES claims policies
// (SAFE-01/SAFE-02/SAFE-04).
//
// iOS mirror: the 26-04 Swift self-tests run the very same `selfTest` fixtures from the same JSON files.
// Pure-JVM, no android./androidx. (BoundaryLintTests is the proof).
//
// Contract: every mustFail sentence yields exactly its expected {category, term} as the FIRST hit, every
// mustPass entry yields none, and normalization/tokenize fixtures reproduce exactly, also under a
// Turkish default locale. A red fixture is fixed in the data, never by loosening these assertions.
package com.endoapp.insights.l10n

import com.endoapp.insights.ForbiddenTerms
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.Locale

class LocalizedPolicySelfTests {

    private fun policy(lang: PolicyLanguage) = ForbiddenTerms.forLanguage(lang)

    private fun assertMustFail(lang: PolicyLanguage, context: String) {
        val p = policy(lang)
        for (case in p.selfTest.mustFail) {
            val actual = p.violation(case.text)
            assertTrue(
                actual != null && actual.category == case.category && actual.term == case.term,
                "[$lang$context] mustFail \"${case.text}\": expected first hit " +
                    "{${case.category}, ${case.term}}, got ${actual?.let { "{${it.category}, ${it.term}} (${it.kind})" } ?: "no violation"}",
            )
        }
    }

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun mustFailYieldsExpectedFirstHit(lang: PolicyLanguage) {
        assertMustFail(lang, "")
    }

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun mustPassHasNoViolation(lang: PolicyLanguage) {
        val p = policy(lang)
        for (text in p.selfTest.mustPass) {
            assertNull(
                p.violation(text),
                "[$lang] mustPass \"$text\" must be clean, but hit ${p.violations(text)}",
            )
        }
    }

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun normalizationFixturesFold(lang: PolicyLanguage) {
        val p = policy(lang)
        for (case in p.selfTest.normalization) {
            assertEquals(
                case.folded,
                p.fold(case.input),
                "[$lang] normalization of \"${case.input}\" (code points ${codePoints(case.input)})",
            )
        }
    }

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun tokenizeFixturesMatch(lang: PolicyLanguage) {
        val p = policy(lang)
        for (case in p.selfTest.tokenize) {
            val tokens = p.tokens(case.input)
            assertEquals(case.tokens, tokens.map { it.folded }, "[$lang] folded tokens of \"${case.input}\"")
            assertEquals(
                case.sentenceInitial,
                tokens.map { it.sentenceInitial },
                "[$lang] sentenceInitial flags of \"${case.input}\"",
            )
        }
    }

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun fixturesAreNotVacuous(lang: PolicyLanguage) {
        val st = policy(lang).selfTest
        assertTrue(st.mustFail.size >= 8, "[$lang] needs at least 8 mustFail fixtures, has ${st.mustFail.size}")
        assertTrue(st.mustPass.isNotEmpty(), "[$lang] needs at least one mustPass fixture")
        assertTrue(st.normalization.isNotEmpty(), "[$lang] needs at least one normalization fixture")
        assertTrue(st.tokenize.isNotEmpty(), "[$lang] needs at least one tokenize fixture")
    }

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun forLanguageIsCached(lang: PolicyLanguage) {
        assertSame(ForbiddenTerms.forLanguage(lang), ForbiddenTerms.forLanguage(lang), "[$lang] forLanguage must return the cached instance")
    }

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun verdictsIgnoreDefaultLocale(lang: PolicyLanguage) {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertMustFail(lang, " @tr-TR")
            val p = policy(lang)
            for (text in p.selfTest.mustPass) {
                assertNull(p.violation(text), "[$lang @tr-TR] mustPass \"$text\" must be clean")
            }
            for (case in p.selfTest.normalization) {
                assertEquals(case.folded, p.fold(case.input), "[$lang @tr-TR] normalization of \"${case.input}\"")
            }
        } finally {
            Locale.setDefault(saved)
        }
    }

    /** The tracer slice end to end: the classpath German policy reports the full Violation, kind
     *  included, and an allow token masks a whole host word in any case. */
    @Test
    fun germanTracerVerdict() {
        val de = ForbiddenTerms.forLanguage(PolicyLanguage.DE)
        assertEquals(
            Violation(category = "diagnostic", kind = "stem", term = "erkenn"),
            de.violation("Die App erkennt Schübe frühzeitig."),
        )
        assertNull(de.violation("Behandlungsteam"))
        assertNull(de.violation("BEHANDLUNGSTEAM"))
        assertEquals(listOf("schub", "frühwarnung"), de.tokens("Schub-Frühwarnung").map { it.folded })
        assertEquals("strasse", de.fold("STRAẞE"))
    }

    /** French: an elided causal verb is caught behind U+2019, the host word entraînement passes in
     *  every spelling and case, and a decomposed (NFD) accent cannot dodge the diagnostic stem. */
    @Test
    fun frenchVerdicts() {
        val fr = ForbiddenTerms.forLanguage(PolicyLanguage.FR)
        assertEquals(
            Violation(category = "causal", kind = "prefix", term = "entraîn"),
            fr.violation("Le café l’entraîne."),
        )
        assertNull(fr.violation("entraînement"))
        assertNull(fr.violation("entrainement"))
        assertNull(fr.violation("ENTRAÎNEMENT"))
        val nfd = "L'application détecte vos poussées."
        assertTrue(nfd != java.text.Normalizer.normalize(nfd, java.text.Normalizer.Form.NFC), "fixture must really be NFD")
        assertEquals("diagnostic", fr.violation(nfd)?.category)
        assertEquals("détect", fr.violation(nfd)?.term)
    }

    /** Spanish: the everyday phrases that contain a forbidden-looking word stay clean, and the
     *  diagnostic stem is caught in any case. */
    @Test
    fun spanishVerdicts() {
        val es = ForbiddenTerms.forLanguage(PolicyLanguage.ES)
        for (clean in listOf("Se trata de tus registros", "Mejor día", "Datos seguros", "Por cierto")) {
            assertNull(es.violation(clean), "[ES] \"$clean\" must be clean, but hit ${es.violations(clean)}")
        }
        for (text in listOf("La app detecta tus brotes.", "LA APP DETECTA TUS BROTES.")) {
            val v = es.violation(text)
            assertEquals("diagnostic", v?.category, "[ES] \"$text\"")
            assertEquals("detect", v?.term, "[ES] \"$text\"")
        }
    }

    /** Every category that holds rules is exercised by at least one mustFail fixture, per language. */
    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun everyCategoryHasAMustFail(lang: PolicyLanguage) {
        val covered = policy(lang).selfTest.mustFail.map { it.category }.toSet()
        for (category in RuleKinds.CATEGORY_ORDER) {
            assertTrue(category in covered, "[$lang] no mustFail fixture for category $category")
        }
    }

    @Test
    fun fromTagRejectsUnknownTag() {
        assertEquals(PolicyLanguage.FR, PolicyLanguage.fromTag("fr"))
        assertThrows<IllegalArgumentException> { PolicyLanguage.fromTag("en") }
    }

    private fun codePoints(s: String): String =
        s.codePoints().toArray().joinToString(" ") { "U+%04X".format(it) }
}
