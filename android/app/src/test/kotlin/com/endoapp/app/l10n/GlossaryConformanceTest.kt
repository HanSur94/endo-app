package com.endoapp.app.l10n

import com.endoapp.app.content.ClaimsPolicy
import com.endoapp.app.content.forLanguage
import com.endoapp.insights.ForbiddenTerms
import com.endoapp.insights.l10n.BannedRules
import com.endoapp.insights.l10n.Glossary
import com.endoapp.insights.l10n.GlossaryVerdict
import com.endoapp.insights.l10n.L10nText
import com.endoapp.insights.l10n.PolicyLanguage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The glossary is enforced, not advisory (SAFE-03, Android half; `l10n/README.md` §Glossary schema).
 *
 * Reads the REAL `l10n/glossary.json` from `endo.repoRoot` and FAILS (never skips) when it is absent.
 * Proves: strict decoding, the draft header, concept coverage in all four languages, approved and banned
 * forms that cannot overlap, canonical banned terms, EN triggers that pass the unchanged English scan,
 * the case-sensitive register check, and every shared selfTest pair (which the iOS port runs too).
 *
 * The test only READS `reviewStatus`: it never sets it (LEGL-03 stays open until Phase 33).
 */
class GlossaryConformanceTest {

    private val glossaryFile: File
        get() = File(L10nSourceRegistry.repoRoot(), "l10n/glossary.json").also {
            assertTrue("l10n/glossary.json must exist under endo.repoRoot (fail, never skip).", it.isFile)
        }

    private val glossaryText: String get() = glossaryFile.readText(Charsets.UTF_8)

    private val glossary: Glossary by lazy { Glossary.decode(glossaryText) }

    /** The concept ids RESEARCH §Glossary seed concepts requires (26-01 Task 2). */
    private val requiredConceptIds = listOf(
        "people_with_endometriosis", "possible_association", "confidence_low", "confidence_medium",
        "confidence_high", "clinician", "flare", "flare_early_warning", "not_medical_advice",
        "not_a_medical_device", "self_tracking", "insights", "patterns", "trend", "check_in", "symptom",
        "severity", "period", "bleeding", "menstruation", "spotting", "endo_belly", "painful_sex", "period_pain",
        "bowel_pain", "urination_pain", "pelvic_pain", "fatigue", "brain_fog", "medication", "pain_relief",
        "activity", "report", "not_enough_data", "encrypted",
    )

    // --- Schema and header ---

    @Test
    fun decodesStrictly_andRejectsUnknownKey() {
        val obj = Json.parseToJsonElement(glossaryText).jsonObject
        // Control: an unmodified rebuild decodes, so the throw below is caused by the mutation alone.
        Glossary.decode(obj.toString())
        val concepts = obj.getValue("concepts").jsonArray
        val misspelled = JsonObject(concepts[0].jsonObject + ("contxet" to concepts[0].jsonObject.getValue("context")))
        val mutated = JsonObject(obj + ("concepts" to JsonArray(listOf(misspelled) + concepts.drop(1))))
        assertThrows(SerializationException::class.java) { Glossary.decode(mutated.toString()) }
    }

    @Test
    fun missingPairKeyIsRejected() {
        // `concept`/`term` are nullable but required: dropping the key must not decode as null.
        val obj = Json.parseToJsonElement(glossaryText).jsonObject
        val selfTest = obj.getValue("selfTest").jsonObject
        val pairs = selfTest.getValue("pairs").jsonArray
        val dropped = JsonObject(pairs[0].jsonObject - "term")
        val mutated = JsonObject(
            obj + ("selfTest" to JsonObject(selfTest + ("pairs" to JsonArray(listOf(dropped) + pairs.drop(1))))),
        )
        assertThrows(SerializationException::class.java) { Glossary.decode(mutated.toString()) }
    }

    @Test
    fun headerValues() {
        assertEquals(1, glossary.schemaVersion)
        assertTrue("policyRevision must be a positive integer.", glossary.policyRevision >= 1)
        assertEquals("draft-unreviewed", glossary.reviewStatus)
        assertEquals("du", glossary.register.de.form)
        assertEquals("vous", glossary.register.fr.form)
        assertEquals("tú", glossary.register.es.form)
        val keys = Json.parseToJsonElement(glossaryText).jsonObject.keys.toList()
        assertEquals(
            listOf("schemaVersion", "policyRevision", "reviewStatus", "register", "doNotTranslate", "concepts", "selfTest"),
            keys,
        )
    }

    // --- Concepts ---

    @Test
    fun conceptsCoverAllFourLanguages() {
        val ids = glossary.concepts.map { it.id }
        assertTrue("The glossary must carry at least 30 concepts (has ${ids.size}).", ids.size >= 30)
        assertEquals("Concept ids must be unique.", ids.size, ids.toSet().size)
        for (required in requiredConceptIds) {
            assertTrue("Required concept '$required' is missing.", required in ids)
        }
        for (concept in glossary.concepts) {
            assertTrue("${concept.id}: EN triggers must be non-empty.", concept.en.triggers.isNotEmpty())
            for (trigger in concept.en.triggers) {
                assertTrue("${concept.id}: trigger '$trigger' must be non-blank lowercase.", trigger.isNotBlank() && trigger == trigger.lowercase())
            }
            for (lang in PolicyLanguage.entries) {
                val approved = concept.forLanguage(lang).approved
                assertTrue("${concept.id}.${lang.tag}: approved must be non-empty.", approved.isNotEmpty())
                assertTrue("${concept.id}.${lang.tag}: approved forms must be non-blank.", approved.all { it.isNotBlank() })
            }
        }
    }

    @Test
    fun approvedNeverMatchesOwnBanned() {
        val problems = mutableListOf<String>()
        for (concept in glossary.concepts) {
            for (lang in PolicyLanguage.entries) {
                for (form in concept.forLanguage(lang).approved) {
                    // Masking off: an allow token must not be what keeps an approved form clear of its own ban.
                    val hits = glossary.conceptBannedViolations(concept, lang, form, applyAllow = false)
                    if (hits.isNotEmpty()) problems += "${concept.id}.${lang.tag}: approved \"$form\" hits own banned $hits"
                }
            }
        }
        assertTrue("approved ∩ banned must be empty:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun bannedTermsAreCanonical() {
        val problems = mutableListOf<String>()
        fun check(lang: PolicyLanguage, where: String, rules: BannedRules) {
            val policy = ClaimsPolicy.forLanguage(lang)
            for (kind in BannedRules.KIND_ORDER) {
                for (term in rules.terms(kind)) {
                    val canonical = if (kind in BannedRules.CASED_KINDS) L10nText.typography(term) else policy.fold(term)
                    if (term.isBlank() || canonical != term) problems += "$where.$kind: \"$term\" is not canonical (\"$canonical\")"
                }
            }
        }
        for (lang in PolicyLanguage.entries) {
            check(lang, "register.${lang.tag}", glossary.register.forLanguage(lang).banned)
            for (concept in glossary.concepts) check(lang, "${concept.id}.${lang.tag}", concept.forLanguage(lang).banned)
        }
        assertTrue("Folded kinds must be pre-folded, cased kinds NFC-exact:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun enTriggersPassEnglishScan() {
        // Read-only use of the English lists (SAFE-04): the triggers come from shipped EN copy, so they
        // must be clean under the unchanged English scan, gendered tokens matched as whole words.
        for (concept in glossary.concepts) {
            for (trigger in concept.en.triggers) {
                assertNull("${concept.id}: EN trigger '$trigger' hits the English scan.", ForbiddenTerms.violation(trigger))
                val words = L10nText.tokenize(trigger, emptyList()).map { it.folded }
                val gendered = words.filter { it in ClaimsPolicy.forbiddenGenderedTokens }
                assertTrue("${concept.id}: EN trigger '$trigger' carries gendered $gendered.", gendered.isEmpty())
            }
        }
    }

    // --- Register (case-sensitive on the NFC view) ---

    @Test
    fun registerIsCaseSensitive_german() {
        assertEquals("Sie", glossary.registerViolation(PolicyLanguage.DE, "Das könnten Sie mit Ihrem Behandlungsteam besprechen.")?.term)
        assertEquals("Ihrem", glossary.registerViolation(PolicyLanguage.DE, "Sprich mit Ihrem Behandlungsteam.")?.term)
        assertEquals("Ihnen", glossary.registerViolation(PolicyLanguage.DE, "Ihnen fehlen Einträge.")?.term)
        // Sentence-initial "Sie" can mean "they"; lowercase mid-sentence "sie" is "they"/"it".
        assertNull(glossary.registerViolation(PolicyLanguage.DE, "Sie haben Schmerzen eingetragen?"))
        assertNull(glossary.registerViolation(PolicyLanguage.DE, "Heute hat sie Schmerzen."))
        assertNull(glossary.registerViolation(PolicyLanguage.DE, "Schmerzen? Sie stehen im Verlauf."))
    }

    @Test
    fun registerFrenchAndSpanish() {
        assertNull(glossary.registerViolation(PolicyLanguage.FR, "Y a-t-il des tendances ?"))
        assertEquals("tu", glossary.registerViolation(PolicyLanguage.FR, "Est-ce que tu as noté tes douleurs ?")?.term)
        assertEquals("tu", glossary.registerViolation(PolicyLanguage.FR, "Tu peux en parler.")?.term)
        assertNull(glossary.registerViolation(PolicyLanguage.FR, "Vous pouvez en parler avec votre médecin."))
        assertEquals("usted", glossary.registerViolation(PolicyLanguage.ES, "Usted puede comentarlo.")?.term)
        assertNull(glossary.registerViolation(PolicyLanguage.ES, "Puedes comentarlo con tu médico."))
    }

    @Test
    fun registerMaskingCanBeDisabled() {
        // The allow phrase "a t il" is what keeps the euphonic t clear; with masking off it is a hit.
        val hits = glossary.registerViolations(PolicyLanguage.FR, "Y a-t-il des tendances ?", applyAllow = false)
        assertTrue("With masking off the FR word 't' must hit: $hits", hits.any { it.term == "t" && it.category == "register" })
    }

    // --- Conformance ---

    @Test
    fun conformanceVerdicts() {
        assertEquals(
            GlossaryVerdict.Banned("flare", "anfall"),
            glossary.conformance("Possible flare pattern", PolicyLanguage.DE, "Möglicher Anfall"),
        )
        assertEquals(GlossaryVerdict.Pass, glossary.conformance("Possible flare pattern", PolicyLanguage.DE, "Mögliches Schub-Muster"))
        assertEquals(
            GlossaryVerdict.MissingApproved("flare"),
            glossary.conformance("Possible flare pattern", PolicyLanguage.DE, "Mögliches Muster"),
        )
        assertEquals(
            GlossaryVerdict.Register("Sie"),
            glossary.conformance("Possible flare pattern", PolicyLanguage.DE, "Haben Sie ein Schub-Muster?"),
        )
        // Banned checks run only when the trigger matches: FR "certain" is banned for the confidence
        // concepts, so it is a hit under their trigger and nothing without it.
        assertEquals(
            GlossaryVerdict.Banned("confidence_low", "certain"),
            glossary.conformance("Low — possible association", PolicyLanguage.FR, "Faible, certain"),
        )
        assertEquals(GlossaryVerdict.Pass, glossary.conformance("Some days", PolicyLanguage.FR, "Certain"))
        // Approved matching ignores the allow mask: approved "entraînement" is itself an allow token.
        assertEquals(GlossaryVerdict.Pass, glossary.conformance("Activity", PolicyLanguage.FR, "Entraînement"))
    }

    @Test
    fun selfTestPairsYieldExpectedVerdict() {
        val pairs = glossary.selfTest.pairs
        for (lang in PolicyLanguage.entries) {
            for (kind in listOf("pass", "banned", "register")) {
                assertTrue(
                    "selfTest needs at least one '$kind' pair for ${lang.tag}.",
                    pairs.any { it.lang == lang.tag && it.expect == kind },
                )
            }
        }
        for (pair in pairs) {
            val lang = PolicyLanguage.fromTag(pair.lang)
            val expected: GlossaryVerdict = when (pair.expect) {
                "pass" -> {
                    assertNull("pass pair must have concept null: $pair", pair.concept)
                    assertNull("pass pair must have term null: $pair", pair.term)
                    GlossaryVerdict.Pass
                }
                "banned" -> GlossaryVerdict.Banned(requireNotNull(pair.concept) { "banned pair needs a concept: $pair" }, requireNotNull(pair.term))
                "register" -> {
                    assertNull("register pair must have concept null: $pair", pair.concept)
                    GlossaryVerdict.Register(requireNotNull(pair.term) { "register pair needs a term: $pair" })
                }
                else -> throw AssertionError("Unknown expect '${pair.expect}' in $pair")
            }
            assertEquals("selfTest pair \"${pair.text}\" (${pair.lang})", expected, glossary.conformance(pair.en, lang, pair.text))
        }
    }

    @Test
    fun selfTestPinsGermanCaseSensitivity() {
        // Shared data (also run by the iOS port) must pin both case-sensitivity edges as pass pairs.
        val dePass = glossary.selfTest.pairs.filter { it.lang == "de" && it.expect == "pass" }.map { it.text }
        assertTrue("A sentence-initial 'Sie …' pass pair is required.", dePass.any { it.startsWith("Sie ") })
        assertTrue(
            "A lowercase mid-sentence 'sie' pass pair is required.",
            dePass.any { text -> L10nText.tokenize(text, emptyList()).any { it.text == "sie" && !it.sentenceInitial } },
        )
    }
}
