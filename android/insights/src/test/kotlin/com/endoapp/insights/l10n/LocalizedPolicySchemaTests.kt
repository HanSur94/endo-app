// EndoInsights (:insights) — schema, strict-decode and fail-closed proofs for the DE/FR/ES claims
// policies (SAFE-01/SAFE-02/SAFE-04).
//
// iOS mirror: the 26-04 Swift schema tests check the same rules (key sets via JSONSerialization,
// because Swift JSONDecoder ignores unknown keys). Pure-JVM, no android./androidx. (BoundaryLintTests).
//
// Contract: the policy data can be neither silently empty nor silently mistyped. Key sets are walked on
// the raw JSON (not on the decoded model, which would hide an unknown key behind a decode error), every
// term is pre-folded and well-shaped, every category is exercised, allow entries cannot smuggle a rule
// term, and a misspelled key, a missing kind or a missing resource throws. Missing data FAILS, never skips.
package com.endoapp.insights.l10n

import com.endoapp.insights.ForbiddenTerms
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.text.Normalizer

class LocalizedPolicySchemaTests {

    private fun resourcePath(lang: PolicyLanguage) = "/l10n/claims-policy.${lang.tag}.json"

    /** The raw UTF-8 text from the same classpath path the loader uses. Missing is a failure. */
    private fun rawText(lang: PolicyLanguage): String {
        val stream = ForbiddenTerms::class.java.getResourceAsStream(resourcePath(lang))
        assertNotNull(stream, "[$lang] ${resourcePath(lang)} is not on the classpath (run :insights:processResources)")
        return stream!!.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun rawJson(lang: PolicyLanguage): JsonObject = Json.parseToJsonElement(rawText(lang)).jsonObject

    private fun policy(lang: PolicyLanguage) = ForbiddenTerms.forLanguage(lang)

    private fun assertKeys(expected: List<String>, element: JsonElement, where: String) {
        assertEquals(expected, element.jsonObject.keys.toList(), "exact ordered key set at $where")
    }

    // ---- key sets and header -------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun exactKeySets(lang: PolicyLanguage) {
        val root = rawJson(lang)
        assertKeys(
            listOf(
                "schemaVersion", "policyRevision", "language", "reviewStatus",
                "folding", "categories", "allow", "framing", "selfTest",
            ),
            root, "[$lang] top level",
        )
        assertKeys(listOf("replace"), root.getValue("folding"), "[$lang] folding")
        val categories = root.getValue("categories")
        assertKeys(RuleKinds.CATEGORY_ORDER, categories, "[$lang] categories")
        for (category in RuleKinds.CATEGORY_ORDER) {
            assertKeys(RuleKinds.KIND_ORDER, categories.jsonObject.getValue(category), "[$lang] categories.$category")
        }
        val allow = root.getValue("allow")
        assertKeys(listOf("tokens", "phrases"), allow, "[$lang] allow")
        allow.jsonObject.getValue("tokens").jsonArray.forEachIndexed { i, e ->
            assertKeys(listOf("token", "reason", "collidesWith"), e, "[$lang] allow.tokens[$i]")
        }
        allow.jsonObject.getValue("phrases").jsonArray.forEachIndexed { i, e ->
            assertKeys(listOf("phrase", "reason", "collidesWith"), e, "[$lang] allow.phrases[$i]")
        }
        val selfTest = root.getValue("selfTest")
        assertKeys(listOf("normalization", "tokenize", "mustFail", "mustPass"), selfTest, "[$lang] selfTest")
        val fixtureKeys = mapOf(
            "normalization" to listOf("input", "folded"),
            "tokenize" to listOf("input", "tokens", "sentenceInitial"),
            "mustFail" to listOf("text", "category", "term"),
        )
        for ((fixture, keys) in fixtureKeys) {
            selfTest.jsonObject.getValue(fixture).jsonArray.forEachIndexed { i, e ->
                assertKeys(keys, e, "[$lang] selfTest.$fixture[$i]")
            }
        }
        selfTest.jsonObject.getValue("mustPass").jsonArray.forEachIndexed { i, e ->
            assertTrue(e is JsonPrimitive && e.isString, "[$lang] selfTest.mustPass[$i] must be a string")
        }
    }

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun headerValues(lang: PolicyLanguage) {
        val root = rawJson(lang)
        assertEquals(1, root.getValue("schemaVersion").jsonPrimitive.int, "[$lang] schemaVersion")
        assertTrue(root.getValue("policyRevision").jsonPrimitive.int >= 1, "[$lang] policyRevision must be >= 1")
        assertEquals(lang.tag, root.getValue("language").jsonPrimitive.content, "[$lang] language must equal the filename tag")
        // Asserted, never written: only the Phase 33 native-speaker and legal review may change it.
        assertEquals("draft-unreviewed", root.getValue("reviewStatus").jsonPrimitive.content, "[$lang] reviewStatus")
        val p = policy(lang)
        assertEquals(lang.tag, p.language)
        assertEquals("draft-unreviewed", p.reviewStatus)
    }

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun replacePairsArePairs(lang: PolicyLanguage) {
        val replace = rawJson(lang).getValue("folding").jsonObject.getValue("replace").jsonArray
        replace.forEachIndexed { i, pair ->
            val items = pair.jsonArray
            assertEquals(2, items.size, "[$lang] folding.replace[$i] must have exactly 2 entries")
            for (item in items) {
                assertTrue(item is JsonPrimitive && item.isString && item.content.isNotEmpty(), "[$lang] folding.replace[$i] entries must be non-empty strings")
            }
        }
    }

    /** README §Policy file schema: UTF-8, LF, NFC, and the invisible/confusable characters written as
     *  JSON `\u` escapes so no editor can normalize them away. */
    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun fileIsNfcLfAndEscaped(lang: PolicyLanguage) {
        val text = rawText(lang)
        assertEquals(Normalizer.normalize(text, Normalizer.Form.NFC), text, "[$lang] file text must be NFC")
        assertTrue('\r' !in text, "[$lang] file must use LF line endings")
        val mustBeEscaped = setOf(0x1E9E, 0x00A0, 0x202F, 0x2009, 0x2007, 0x2019, 0x2018, 0x02BC)
        val offending = text.codePoints().toArray().filter { cp ->
            cp in mustBeEscaped || Character.getType(cp) == Character.NON_SPACING_MARK.toInt()
        }
        assertTrue(offending.isEmpty(), "[$lang] raw characters that must be \\u escapes: ${offending.map { "U+%04X".format(it) }}")
    }

    // ---- term hygiene --------------------------------------------------------------------------

    private fun ruleTerms(p: LanguageClaimsPolicy): List<Triple<String, String, String>> =
        RuleKinds.CATEGORY_ORDER.flatMap { category ->
            RuleKinds.KIND_ORDER.flatMap { kind ->
                p.categories.ruleSet(category).terms(kind).map { Triple(category, kind, it) }
            }
        }

    /** Every entry that must be pre-folded but is not. Empty means clean. */
    private fun notPreFolded(p: LanguageClaimsPolicy): List<String> {
        val entries = ruleTerms(p).map { (c, k, t) -> "$c.$k" to t } +
            p.allow.tokens.map { "allow.token" to it.token } +
            p.allow.phrases.map { "allow.phrase" to it.phrase } +
            p.framing.map { "framing" to it }
        return entries.filter { (_, t) -> p.fold(t) != t }.map { (where, t) -> "$where '$t' folds to '${p.fold(t)}'" }
    }

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun termsArePreFolded(lang: PolicyLanguage) {
        val problems = notPreFolded(policy(lang))
        assertTrue(problems.isEmpty(), "[$lang] entries that are not pre-folded: $problems")
    }

    /** The pre-folded check is not vacuous: a capitalized rule term is reported. */
    @Test
    fun preFoldedCheckCatchesCapitalizedTerm() {
        val de = policy(PolicyLanguage.DE)
        val broken = de.copy(
            categories = de.categories.copy(
                diagnostic = de.categories.diagnostic.copy(stem = de.categories.diagnostic.stem + "Erkenn"),
            ),
        )
        assertEquals(listOf("diagnostic.stem 'Erkenn' folds to 'erkenn'"), notPreFolded(broken))
    }

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun termShapes(lang: PolicyLanguage) {
        val p = policy(lang)
        for ((category, kind, term) in ruleTerms(p)) {
            val folded = p.tokens(term).map { it.folded }
            when (kind) {
                RuleKinds.RAW -> assertTrue(term.isNotBlank(), "[$lang] $category.raw has a blank term")
                RuleKinds.PHRASE -> assertTrue(folded.size >= 2, "[$lang] $category.phrase '$term' must be 2+ tokens, is $folded")
                else -> assertEquals(listOf(term), folded, "[$lang] $category.$kind '$term' must be exactly one token equal to itself")
            }
        }
        for (category in RuleKinds.CATEGORY_ORDER) {
            for (kind in RuleKinds.KIND_ORDER) {
                val terms = p.categories.ruleSet(category).terms(kind)
                val dupes = terms.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
                assertTrue(dupes.isEmpty(), "[$lang] duplicate terms in $category.$kind: $dupes")
            }
        }
    }

    // ---- anti-vacuity --------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun antiVacuity(lang: PolicyLanguage) {
        val p = policy(lang)
        for (category in listOf("diagnostic", "causal", "imperative", "alarmist", "gendered")) {
            val size = RuleKinds.KIND_ORDER.sumOf { p.categories.ruleSet(category).terms(it).size }
            assertTrue(size > 0, "[$lang] SC1 category $category must not be empty")
        }
        val failing = p.selfTest.mustFail
        for (category in RuleKinds.CATEGORY_ORDER) {
            val size = RuleKinds.KIND_ORDER.sumOf { p.categories.ruleSet(category).terms(it).size }
            if (size > 0) {
                assertTrue(failing.any { it.category == category }, "[$lang] category $category has rules but no mustFail fixture")
            }
        }
        for (case in failing) {
            val terms = RuleKinds.KIND_ORDER.flatMap { p.categories.ruleSet(case.category).terms(it) }
            assertTrue(case.term in terms, "[$lang] mustFail \"${case.text}\" expects term '${case.term}', which is not a rule of ${case.category}")
        }
        assertTrue(
            failing.any { Normalizer.normalize(it.text, Normalizer.Form.NFC) != it.text },
            "[$lang] needs a mustFail fixture that is not NFC (accents must not dodge the check)",
        )
        assertTrue(
            failing.any { it.text.any(Char::isLetter) && it.text == it.text.uppercase(java.util.Locale.ROOT) },
            "[$lang] needs an all-uppercase mustFail fixture (capitals must not dodge the check)",
        )
        val inputs = p.selfTest.normalization.map { it.input }
        assertTrue(inputs.any { ' ' in it || ' ' in it }, "[$lang] normalization needs a U+00A0 or U+202F input")
        assertTrue(inputs.any { '’' in it }, "[$lang] normalization needs a U+2019 input")
        if (lang == PolicyLanguage.DE) {
            assertTrue(inputs.any { 'ẞ' in it }, "[DE] normalization needs a U+1E9E input")
        }
    }

    // ---- allow structure -----------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(PolicyLanguage::class)
    fun allowStructure(lang: PolicyLanguage) {
        val p = policy(lang)
        val singleTokenTerms = RuleKinds.CATEGORY_ORDER.flatMap { c ->
            listOf(RuleKinds.WORD, RuleKinds.PREFIX, RuleKinds.STEM).flatMap { p.categories.ruleSet(c).terms(it) }
        }.toSet()
        for (entry in p.allow.tokens) {
            assertEquals(listOf(entry.token), p.tokens(entry.token).map { it.folded }, "[$lang] allow token '${entry.token}' must be exactly one token")
            assertTrue(entry.token !in singleTokenTerms, "[$lang] allow token '${entry.token}' exempts a forbidden term itself")
            assertTrue(entry.reason.isNotBlank(), "[$lang] allow token '${entry.token}' needs a reason")
            assertTrue(entry.collidesWith.isNotEmpty(), "[$lang] allow token '${entry.token}' needs collidesWith")
            for (c in entry.collidesWith) {
                assertTrue(c.isNotEmpty() && c in entry.token, "[$lang] allow token '${entry.token}': collidesWith '$c' does not occur in it")
            }
            assertNull(p.violation(entry.token), "[$lang] allow token '${entry.token}' must be clean, hit ${p.violations(entry.token)}")
        }
        val phraseTerms = RuleKinds.CATEGORY_ORDER.flatMap { p.categories.ruleSet(it).phrase }.toSet()
        for (entry in p.allow.phrases) {
            assertTrue(p.tokens(entry.phrase).size >= 2, "[$lang] allow phrase '${entry.phrase}' must be 2+ tokens")
            assertTrue(entry.phrase !in phraseTerms, "[$lang] allow phrase '${entry.phrase}' exempts a forbidden phrase itself")
            assertTrue(entry.reason.isNotBlank(), "[$lang] allow phrase '${entry.phrase}' needs a reason")
            assertTrue(entry.collidesWith.isNotEmpty(), "[$lang] allow phrase '${entry.phrase}' needs collidesWith")
            for (c in entry.collidesWith) {
                assertTrue(c.isNotEmpty() && c in entry.phrase, "[$lang] allow phrase '${entry.phrase}': collidesWith '$c' does not occur in it")
            }
            assertNull(p.violation(entry.phrase), "[$lang] allow phrase '${entry.phrase}' must be clean, hit ${p.violations(entry.phrase)}")
        }
    }

    // ---- strict decode and fail-closed ---------------------------------------------------------

    /** Rebuilds the DE policy JSON with categories.diagnostic replaced by [edit] of its object. */
    private fun deWithDiagnostic(edit: (JsonObject) -> JsonObject): String {
        val root = rawJson(PolicyLanguage.DE)
        val categories = root.getValue("categories").jsonObject
        val diagnostic = categories.getValue("diagnostic").jsonObject
        val newCategories = JsonObject(categories.toMutableMap().apply { put("diagnostic", edit(diagnostic)) })
        return JsonObject(root.toMutableMap().apply { put("categories", newCategories) }).toString()
    }

    @Test
    fun unmodifiedRebuildDecodes() {
        // Control for the two rejection tests: the rebuild itself is valid, so their throw is caused
        // by the mutation alone.
        val text = deWithDiagnostic { it }
        assertEquals(policy(PolicyLanguage.DE), Json.decodeFromString(LanguageClaimsPolicy.serializer(), text))
    }

    @Test
    fun unknownKeyIsRejected() {
        val text = deWithDiagnostic { d ->
            JsonObject(d.entries.associate { (k, v) -> (if (k == "prefix") "prefx" else k) to v })
        }
        assertTrue("\"prefx\"" in text, "mutation must have renamed the key")
        assertNotEquals(deWithDiagnostic { it }, text, "mutation must have changed the text")
        assertThrows<SerializationException> { Json.decodeFromString(LanguageClaimsPolicy.serializer(), text) }
    }

    @Test
    fun missingKindIsRejected() {
        val text = deWithDiagnostic { d -> JsonObject(d.filterKeys { it != "stem" }) }
        assertNotEquals(deWithDiagnostic { it }, text, "mutation must have removed the key")
        assertThrows<SerializationException> { Json.decodeFromString(LanguageClaimsPolicy.serializer(), text) }
    }

    @Test
    fun missingCategoryIsRejected() {
        val root = rawJson(PolicyLanguage.DE)
        val categories = root.getValue("categories").jsonObject
        val text = JsonObject(
            root.toMutableMap().apply { put("categories", JsonObject(categories.filterKeys { it != "gendered" })) },
        ).toString()
        assertThrows<SerializationException> { Json.decodeFromString(LanguageClaimsPolicy.serializer(), text) }
    }

    @Test
    fun wrongTypeIsRejected() {
        val text = deWithDiagnostic { d -> JsonObject(d.toMutableMap().apply { put("stem", JsonPrimitive("erkenn")) }) }
        assertThrows<SerializationException> { Json.decodeFromString(LanguageClaimsPolicy.serializer(), text) }
        // A JsonArray of the wrong element type is rejected too.
        val text2 = deWithDiagnostic { d -> JsonObject(d.toMutableMap().apply { put("stem", JsonArray(listOf(JsonObject(emptyMap())))) }) }
        assertThrows<SerializationException> { Json.decodeFromString(LanguageClaimsPolicy.serializer(), text2) }
    }

    @Test
    fun missingResourceFailsClosed() {
        val e = assertThrows<IllegalStateException> { loadLanguagePolicy("/l10n/claims-policy.xx.json", PolicyLanguage.DE) }
        assertTrue("fail-closed" in (e.message ?: ""), "message must say fail-closed: ${e.message}")
    }

    @Test
    fun wrongLanguageFailsClosed() {
        assertThrows<IllegalStateException> { loadLanguagePolicy("/l10n/claims-policy.fr.json", PolicyLanguage.DE) }
    }
}
