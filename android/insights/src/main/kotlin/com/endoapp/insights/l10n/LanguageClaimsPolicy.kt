// EndoInsights (:insights) — the per-language claims policy model and matcher (SAFE-01/SAFE-02/SAFE-04).
//
// iOS mirror: `Sources/EndoApp/Content/L10n/*` (ported in 26-04). Pure-JVM, no android./androidx.
// (BoundaryLintTests is the proof).
//
// Contract: the strict decode target of `l10n/claims-policy.<tag>.json` plus `l10n/README.md` §Matching
// spec steps 4-6. Every constructor field is required with no default, so the default strict `Json`
// rejects both a missing and an unknown/misspelled key (a dropped rule class can never decode as empty).
// Rules are plain strings evaluated by the RuleKinds predicates, never regex. English text never goes
// through this matcher; the English `ForbiddenTerms.violation` is neither called nor changed.
package com.endoapp.insights.l10n

import kotlinx.serialization.Serializable

/** The full policy file. Top-level keys in file order. Derived lookups are `by lazy` delegates, so they
 *  never become serialized fields. */
@Serializable
data class LanguageClaimsPolicy(
    val schemaVersion: Int,
    val policyRevision: Int,
    val language: String,
    val reviewStatus: String,
    val folding: Folding,
    val categories: Categories,
    val allow: AllowList,
    val framing: List<String>,
    val selfTest: SelfTest,
) {
    /** Allow tokens by folded equality (README step 4). */
    private val allowTokenSet: Set<String> by lazy { allow.tokens.map { it.token }.toSet() }

    /** Allow phrases tokenized with the same tokenizer, as folded-token sequences. */
    private val allowPhraseSequences: List<List<String>> by lazy {
        allow.phrases.map { entry -> tokens(entry.phrase).map { it.folded } }
    }

    /** Every phrase rule term tokenized once, as folded-token sequences. */
    private val phraseTermTokens: Map<String, List<String>> by lazy {
        RuleKinds.CATEGORY_ORDER
            .flatMap { categories.ruleSet(it).phrase }
            .distinct()
            .associateWith { term -> tokens(term).map { it.folded } }
    }

    /** README step 2: the folded string of [text]. */
    fun fold(text: String): String = L10nText.foldString(text, folding.replace)

    /** README step 3: the tokens of [text] with this policy's replace pairs. */
    fun tokens(text: String): List<Token> = L10nText.tokenize(text, folding.replace)

    /** README step 4: true where a token is masked by an allow token (folded equality) or by an
     *  occurrence of an allow phrase (exact folded-token sequence). */
    fun mask(tokens: List<Token>): BooleanArray {
        val masked = BooleanArray(tokens.size) { tokens[it].folded in allowTokenSet }
        for (sequence in allowPhraseSequences) {
            val k = sequence.size
            if (k == 0 || k > tokens.size) continue
            for (i in 0..tokens.size - k) {
                if ((0 until k).all { tokens[i + it].folded == sequence[it] }) {
                    for (j in i until i + k) masked[j] = true
                }
            }
        }
        return masked
    }

    /** Every hit in fixed category -> kind -> list order (README step 6), deduplicated. [applyAllow] =
     *  false disables masking (used by the 26-03 allow-collision proof). */
    fun violations(text: String, applyAllow: Boolean = true): List<Violation> =
        scan(text, applyAllow, firstOnly = false)

    /** The first hit in rule order, or null when [text] is clean. */
    fun violation(text: String, applyAllow: Boolean = true): Violation? =
        scan(text, applyAllow, firstOnly = true).firstOrNull()

    private fun scan(text: String, applyAllow: Boolean, firstOnly: Boolean): List<Violation> {
        val folded = fold(text)
        val toks = tokens(text)
        val masked = if (applyAllow) mask(toks) else BooleanArray(toks.size)
        val hits = LinkedHashSet<Violation>()
        for (category in RuleKinds.CATEGORY_ORDER) {
            val ruleSet = categories.ruleSet(category)
            for (kind in RuleKinds.KIND_ORDER) {
                for (term in ruleSet.terms(kind)) {
                    val hit = when (kind) {
                        RuleKinds.RAW -> RuleKinds.raw(folded, term)
                        RuleKinds.WORD -> RuleKinds.word(toks, masked, term)
                        RuleKinds.PREFIX -> RuleKinds.prefix(toks, masked, term)
                        RuleKinds.STEM -> RuleKinds.stem(toks, masked, term)
                        RuleKinds.PHRASE -> RuleKinds.phrase(toks, masked, phraseTermTokens.getValue(term))
                        else -> error("Unknown rule kind '$kind' (fail-closed)")
                    }
                    if (hit) {
                        hits.add(Violation(category = category, kind = kind, term = term))
                        if (firstOnly) return hits.toList()
                    }
                }
            }
        }
        return hits.toList()
    }
}

/** `folding.replace`: `[from, to]` pairs applied in array order after root lowercasing. */
@Serializable
data class Folding(val replace: List<List<String>>)

/** The 8 categories in the fixed evaluation order. */
@Serializable
data class Categories(
    val diagnostic: RuleSet,
    val treatment: RuleSet,
    val causal: RuleSet,
    val certainty: RuleSet,
    val alarmist: RuleSet,
    val imperative: RuleSet,
    val gendered: RuleSet,
    val precision: RuleSet,
) {
    /** The rule set for a category name from [RuleKinds.CATEGORY_ORDER]. Throws on an unknown name. */
    fun ruleSet(category: String): RuleSet = when (category) {
        "diagnostic" -> diagnostic
        "treatment" -> treatment
        "causal" -> causal
        "certainty" -> certainty
        "alarmist" -> alarmist
        "imperative" -> imperative
        "gendered" -> gendered
        "precision" -> precision
        else -> throw IllegalArgumentException("Unknown claims category '$category' (fail-closed)")
    }
}

/** The 5 rule kinds of one category. Arrays may be empty but every key must be present. */
@Serializable
data class RuleSet(
    val raw: List<String>,
    val word: List<String>,
    val prefix: List<String>,
    val stem: List<String>,
    val phrase: List<String>,
) {
    /** The terms for a kind name from [RuleKinds.KIND_ORDER]. Throws on an unknown name. */
    fun terms(kind: String): List<String> = when (kind) {
        RuleKinds.RAW -> raw
        RuleKinds.WORD -> word
        RuleKinds.PREFIX -> prefix
        RuleKinds.STEM -> stem
        RuleKinds.PHRASE -> phrase
        else -> throw IllegalArgumentException("Unknown rule kind '$kind' (fail-closed)")
    }
}

@Serializable
data class AllowList(val tokens: List<AllowToken>, val phrases: List<AllowPhrase>)

@Serializable
data class AllowToken(val token: String, val reason: String, val collidesWith: List<String>)

@Serializable
data class AllowPhrase(val phrase: String, val reason: String, val collidesWith: List<String>)

/** The shared fixtures both platforms must reproduce verdict for verdict. */
@Serializable
data class SelfTest(
    val normalization: List<NormalizationCase>,
    val tokenize: List<TokenizeCase>,
    val mustFail: List<MustFailCase>,
    val mustPass: List<String>,
)

@Serializable
data class NormalizationCase(val input: String, val folded: String)

@Serializable
data class TokenizeCase(val input: String, val tokens: List<String>, val sentenceInitial: List<Boolean>)

@Serializable
data class MustFailCase(val text: String, val category: String, val term: String)

/** One hit: the category, the rule kind and the matched term. Not serialized. */
data class Violation(val category: String, val kind: String, val term: String)

/** The fixed evaluation order and the README step 5 predicates. Shared with the 26-03 glossary
 *  matcher, which must reuse these rather than duplicate them. */
object RuleKinds {
    const val RAW = "raw"
    const val WORD = "word"
    const val PREFIX = "prefix"
    const val STEM = "stem"
    const val PHRASE = "phrase"

    val CATEGORY_ORDER: List<String> = listOf(
        "diagnostic", "treatment", "causal", "certainty", "alarmist", "imperative", "gendered", "precision",
    )

    val KIND_ORDER: List<String> = listOf(RAW, WORD, PREFIX, STEM, PHRASE)

    /** raw: the folded string contains [term]. Ignores masking. */
    fun raw(folded: String, term: String): Boolean = folded.contains(term)

    /** word: an unmasked token's folded form equals [term]. */
    fun word(tokens: List<Token>, masked: BooleanArray, term: String): Boolean =
        tokens.indices.any { !masked[it] && tokens[it].folded == term }

    /** prefix: an unmasked token's folded form starts with [term]. */
    fun prefix(tokens: List<Token>, masked: BooleanArray, term: String): Boolean =
        tokens.indices.any { !masked[it] && tokens[it].folded.startsWith(term) }

    /** stem: an unmasked token's folded form contains [term]. */
    fun stem(tokens: List<Token>, masked: BooleanArray, term: String): Boolean =
        tokens.indices.any { !masked[it] && tokens[it].folded.contains(term) }

    /** phrase: k consecutive unmasked tokens, the first k-1 equal to [termTokens], the last
     *  prefix-matched (`führt zu` catches zum/zur). An empty term never matches. */
    fun phrase(tokens: List<Token>, masked: BooleanArray, termTokens: List<String>): Boolean {
        val k = termTokens.size
        if (k == 0 || k > tokens.size) return false
        for (i in 0..tokens.size - k) {
            if ((i until i + k).any { masked[it] }) continue
            val headEqual = (0 until k - 1).all { tokens[i + it].folded == termTokens[it] }
            if (headEqual && tokens[i + k - 1].folded.startsWith(termTokens[k - 1])) return true
        }
        return false
    }
}
