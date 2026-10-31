// EndoInsights (:insights) — the shared l10n glossary: approved terms, banned variants and the register
// du / vous / tú (SAFE-03).
//
// iOS mirror: `Sources/EndoApp/Content/L10n/*` (ported in 26-05). Pure-JVM, no android./androidx.
// (BoundaryLintTests is the proof).
//
// Contract: the strict decode target of `l10n/glossary.json` plus `l10n/README.md` §Glossary schema.
// The glossary is TEST-ONLY data: it is never on the classpath, so there is no loader here; tests read
// it by path and call [Glossary.decode]. Banned rules are evaluated with the language policy's tokens,
// fold and allow mask and the 26-02 [RuleKinds] predicates (reused, never duplicated). The two cased
// kinds compare the NFC typography view exactly.
package com.endoapp.insights.l10n

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The full glossary file. Top-level keys in file order. Every field is required. */
@Serializable
data class Glossary(
    val schemaVersion: Int,
    val policyRevision: Int,
    val reviewStatus: String,
    val register: RegisterByLanguage,
    val doNotTranslate: List<String>,
    val concepts: List<Concept>,
    val selfTest: GlossarySelfTest,
) {

    /** Every register hit for [text] in [lang], in kind then list order. Category is "register". */
    fun registerViolations(lang: PolicyLanguage, text: String, applyAllow: Boolean = true): List<Violation> =
        emptyList()

    /** The first register hit, or null when [text] keeps the language's register. */
    fun registerViolation(lang: PolicyLanguage, text: String): Violation? = null

    /** Every banned hit of [concept] in [lang] for [text]. Category is the concept id. */
    fun conceptBannedViolations(
        concept: Concept,
        lang: PolicyLanguage,
        text: String,
        applyAllow: Boolean = true,
    ): List<Violation> = emptyList()

    /** The conformance verdict of translation [text] in [lang] for EN source [en]. */
    fun conformance(en: String, lang: PolicyLanguage, text: String): GlossaryVerdict = GlossaryVerdict.Pass

    companion object {
        /** Strictly decodes glossary JSON: unknown and missing required keys both throw. */
        fun decode(text: String): Glossary = Json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class RegisterByLanguage(val de: RegisterBlock, val fr: RegisterBlock, val es: RegisterBlock) {
    fun forLanguage(lang: PolicyLanguage): RegisterBlock = when (lang) {
        PolicyLanguage.DE -> de
        PolicyLanguage.FR -> fr
        PolicyLanguage.ES -> es
    }
}

/** One language's register: the address [form] (du / vous / tú) and the [banned] forms. */
@Serializable
data class RegisterBlock(val form: String, val banned: BannedRules)

/**
 * Banned forms in any subset of the seven kinds. Every list defaults to empty because any subset is
 * allowed; an unknown key still throws under the default strict [Json]. Folded kinds hold pre-folded
 * terms, the cased kinds hold exact-case NFC terms.
 */
@Serializable
data class BannedRules(
    val raw: List<String> = emptyList(),
    val word: List<String> = emptyList(),
    val prefix: List<String> = emptyList(),
    val stem: List<String> = emptyList(),
    val phrase: List<String> = emptyList(),
    val wordCased: List<String> = emptyList(),
    val wordCasedMidSentence: List<String> = emptyList(),
) {
    /** The terms for a kind name from [KIND_ORDER]. Throws on an unknown name. */
    fun terms(kind: String): List<String> = when (kind) {
        RuleKinds.RAW -> raw
        RuleKinds.WORD -> word
        RuleKinds.PREFIX -> prefix
        RuleKinds.STEM -> stem
        RuleKinds.PHRASE -> phrase
        WORD_CASED -> wordCased
        WORD_CASED_MID_SENTENCE -> wordCasedMidSentence
        else -> throw IllegalArgumentException("Unknown glossary rule kind '$kind' (fail-closed)")
    }

    companion object {
        const val WORD_CASED = "wordCased"
        const val WORD_CASED_MID_SENTENCE = "wordCasedMidSentence"

        /** The five folded kinds, then the two cased kinds (README §Matching spec step 6). */
        val KIND_ORDER: List<String> = RuleKinds.KIND_ORDER + listOf(WORD_CASED, WORD_CASED_MID_SENTENCE)

        /** The kinds compared on the cased NFC view instead of the folded form. */
        val CASED_KINDS: Set<String> = setOf(WORD_CASED, WORD_CASED_MID_SENTENCE)
    }
}

/** One concept: its EN [en] triggers and the per-language approved and banned forms. */
@Serializable
data class Concept(
    val id: String,
    val context: String,
    val en: EnTriggers,
    val de: ConceptTerms,
    val fr: ConceptTerms,
    val es: ConceptTerms,
) {
    fun forLanguage(lang: PolicyLanguage): ConceptTerms = when (lang) {
        PolicyLanguage.DE -> de
        PolicyLanguage.FR -> fr
        PolicyLanguage.ES -> es
    }
}

@Serializable
data class EnTriggers(val triggers: List<String>)

@Serializable
data class ConceptTerms(val approved: List<String>, val banned: BannedRules)

@Serializable
data class GlossarySelfTest(val pairs: List<GlossaryPair>)

/** A shared conformance fixture. [concept] and [term] are nullable but REQUIRED keys (no default). */
@Serializable
data class GlossaryPair(
    val en: String,
    val lang: String,
    val text: String,
    val expect: String,
    val concept: String?,
    val term: String?,
)

/** The conformance verdict (README §Glossary schema, Semantics). */
sealed interface GlossaryVerdict {
    /** Register kept, no banned variant, every triggered concept has an approved form. */
    data object Pass : GlossaryVerdict

    /** The text breaks the language's register with [term]. */
    data class Register(val term: String) : GlossaryVerdict

    /** The text uses [term], a banned variant of a triggered [concept]. */
    data class Banned(val concept: String, val term: String) : GlossaryVerdict

    /** A triggered [concept] has no approved form in the text. */
    data class MissingApproved(val concept: String) : GlossaryVerdict
}
