package com.endoapp.app.l10n

import com.endoapp.app.content.ClaimsPolicy
import com.endoapp.app.content.forLanguage
import com.endoapp.insights.ForbiddenTerms
import com.endoapp.insights.l10n.Glossary
import com.endoapp.insights.l10n.PolicyLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every allow entry is a proven, justified collision, never a way to smuggle a claim (SAFE-02, T-26-13).
 *
 * For each allow token and phrase of each language, every `collidesWith` term must be REAL, meaning at
 * least one of:
 *   (a) the language's own policy hits it in the entry with masking OFF,
 *   (b) the glossary register hits it in the entry with masking OFF,
 *   (c) it is an English code-list term (causal / imperative / alarmist / gendered / precision) that the
 *       folded entry contains (the English scan is substring-based; DE `pause` ⊃ `use`, `vorher` ⊃ `her`).
 * An entry needs a non-blank reason and at least one real collision (dead entries fail), and with the
 * allow applied it must pass both the policy and the register.
 *
 * NON-VACUITY: [checkCatchesDeadAndUnjustifiedEntries] feeds synthetic bad entries through the SAME
 * [allowProblems] helper the real check uses.
 */
class AllowListHygieneTest {

    private data class Entry(val text: String, val reason: String, val collidesWith: List<String>)

    private val glossary: Glossary by lazy { L10nSourceRegistry.glossary(L10nSourceRegistry.repoRoot()) }

    private val englishTerms: List<String> =
        ForbiddenTerms.causal + ForbiddenTerms.imperative + ForbiddenTerms.alarmist +
            ClaimsPolicy.forbiddenGenderedTokens + ClaimsPolicy.forbiddenPrecisionTokens

    private fun entriesOf(lang: PolicyLanguage): List<Entry> {
        val allow = ClaimsPolicy.forLanguage(lang).allow
        return allow.tokens.map { Entry(it.token, it.reason, it.collidesWith) } +
            allow.phrases.map { Entry(it.phrase, it.reason, it.collidesWith) }
    }

    /** The real collisions of [term] in [entry]: which of (a), (b), (c) hold. Empty means not real. */
    private fun realCollisions(lang: PolicyLanguage, entry: String, term: String): List<String> {
        val policy = ClaimsPolicy.forLanguage(lang)
        val proofs = mutableListOf<String>()
        if (policy.violations(entry, applyAllow = false).any { it.term == term }) proofs += "policy"
        if (glossary.registerViolations(lang, entry, applyAllow = false).any { it.term == term }) proofs += "register"
        if (term in englishTerms && policy.fold(entry).contains(term)) proofs += "english"
        return proofs
    }

    /** Every hygiene problem of one allow [entry] in [lang]. Empty means the entry is justified. */
    private fun allowProblems(lang: PolicyLanguage, entry: Entry): List<String> {
        val where = "${lang.tag} allow \"${entry.text}\""
        val problems = mutableListOf<String>()
        if (entry.reason.isBlank()) problems += "$where: reason is blank"
        val real = entry.collidesWith.filter { realCollisions(lang, entry.text, it).isNotEmpty() }
        for (term in entry.collidesWith - real.toSet()) {
            problems += "$where: collidesWith \"$term\" is not a real collision (no policy, register or English hit)"
        }
        if (real.isEmpty()) problems += "$where: dead entry (no real collision)"
        val policyHits = ClaimsPolicy.forLanguage(lang).violations(entry.text)
        val registerHits = glossary.registerViolations(lang, entry.text)
        if (policyHits.isNotEmpty() || registerHits.isNotEmpty()) {
            problems += "$where: still fails with the allow applied ($policyHits $registerHits)"
        }
        return problems
    }

    @Test
    fun everyAllowEntryIsARealJustifiedCollision() {
        val problems = PolicyLanguage.entries.flatMap { lang -> entriesOf(lang).flatMap { allowProblems(lang, it) } }
        assertTrue("Allow-list hygiene:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun allowListsAreNotVacuous() {
        val total = PolicyLanguage.entries.sumOf { entriesOf(it).size }
        assertTrue("The allow lists must hold entries to check (saw $total).", total >= 1)
    }

    @Test
    fun englishTrapControlsAreRealEnglishCollisions() {
        assertEquals(listOf("english"), realCollisions(PolicyLanguage.DE, "pause", "use"))
        assertEquals(listOf("english"), realCollisions(PolicyLanguage.DE, "vorher", "her"))
    }

    @Test
    fun frenchEuphonicTIsARealRegisterCollision() {
        assertEquals(listOf("register"), realCollisions(PolicyLanguage.FR, "a t il", "t"))
    }

    @Test
    fun germanCareTeamIsARealPolicyCollision() {
        assertEquals(listOf("policy"), realCollisions(PolicyLanguage.DE, "behandlungsteam", "behandl"))
    }

    @Test
    fun checkCatchesDeadAndUnjustifiedEntries() {
        val de = PolicyLanguage.DE
        assertTrue(
            "A collidesWith term the entry does not hit must fail.",
            allowProblems(de, Entry("pause", "noun 'break'", listOf("behandl"))).any { "not a real collision" in it },
        )
        assertTrue(
            "An entry with no collision at all is dead and must fail.",
            allowProblems(de, Entry("hallo", "greeting", emptyList())).any { "dead entry" in it },
        )
        assertTrue(
            "An entry without a reason must fail.",
            allowProblems(de, Entry("vorher", " ", listOf("her"))).any { "reason is blank" in it },
        )
        // An entry that only "allows" text the policy still catches (it is not in the allow list) fails.
        assertTrue(
            "An entry that still fails with the allow applied must fail.",
            allowProblems(de, Entry("erkennt", "smuggled claim", listOf("erkenn"))).any { "still fails" in it },
        )
    }
}
