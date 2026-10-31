// EndoInsights (:insights) — l10n text normalization, folding and tokenization (SAFE-01/SAFE-02/SAFE-04).
//
// iOS mirror: `Sources/EndoApp/Content/L10n/*` (ported in 26-04). Pure-JVM, no android./androidx.
// (BoundaryLintTests is the proof).
//
// Contract: implements `l10n/README.md` §Matching spec steps 1-3 exactly. Every case mapping uses
// Locale.ROOT (a Turkish default locale must not turn DIAGNOSE into dıagnose), and tokens are found by
// walking code points with an explicit Unicode general-category set, never by regex.
package com.endoapp.insights.l10n

import java.text.Normalizer
import java.util.Locale

/** One token of the typography view. [text] keeps its case, [folded] is root-lowercased plus the
 *  policy's replace pairs, [sentenceInitial] is true for the first token or after `.`/`!`/`?`/`:`/newline. */
data class Token(val text: String, val folded: String, val sentenceInitial: Boolean)

object L10nText {

    /** Typographic apostrophes mapped to U+0027: U+2019, U+2018, U+02BC. */
    private val APOSTROPHES: Set<Int> = setOf(0x2019, 0x2018, 0x02BC)

    /** Typographic spaces mapped to U+0020: NBSP U+00A0, NNBSP U+202F, thin U+2009, figure U+2007. */
    private val SPACES: Set<Int> = setOf(0x00A0, 0x202F, 0x2009, 0x2007)

    /** Gap characters that make the next token sentence-initial: `.` `!` `?` `:` and newline. */
    private val SENTENCE_BREAKS: Set<Int> = setOf(0x002E, 0x0021, 0x003F, 0x003A, 0x000A)

    /** Token general categories Lu, Ll, Lt, Lm, Lo, Nd (mirrors the Swift switch in 26-04). Combining
     *  marks (Mn/Mc/Me) are deliberately absent: after NFC a leftover mark separates tokens. */
    private val TOKEN_CATEGORIES: Set<Int> = setOf(
        Character.UPPERCASE_LETTER.toInt(),
        Character.LOWERCASE_LETTER.toInt(),
        Character.TITLECASE_LETTER.toInt(),
        Character.MODIFIER_LETTER.toInt(),
        Character.OTHER_LETTER.toInt(),
        Character.DECIMAL_DIGIT_NUMBER.toInt(),
    )

    /** Step 1: NFC, then the 3 apostrophe and 4 space mappings. */
    fun typography(text: String): String {
        val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)
        val out = StringBuilder(nfc.length)
        var i = 0
        while (i < nfc.length) {
            val cp = nfc.codePointAt(i)
            val mapped = when (cp) {
                in APOSTROPHES -> 0x0027
                in SPACES -> 0x0020
                else -> cp
            }
            out.appendCodePoint(mapped)
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    /** Step 2: the folded string. Typography view, root lowercase, then [replace] pairs in list order. */
    fun foldString(text: String, replace: List<List<String>>): String = foldToken(typography(text), replace)

    /** Root lowercase plus [replace] pairs for one already-typography-normalized token. */
    fun foldToken(tokenText: String, replace: List<List<String>>): String {
        var folded = tokenText.lowercase(Locale.ROOT)
        for (pair in replace) {
            folded = folded.replace(pair[0], pair[1])
        }
        return folded
    }

    /** True when [codePoint] belongs to a token (general category Lu/Ll/Lt/Lm/Lo/Nd). */
    fun isTokenCodePoint(codePoint: Int): Boolean = Character.getType(codePoint) in TOKEN_CATEGORIES

    /** Step 3: maximal runs of token code points of the typography view, with folded form and
     *  sentence-initial flag. Walks by code point, never by char. */
    fun tokenize(text: String, replace: List<List<String>>): List<Token> {
        val view = typography(text)
        val tokens = ArrayList<Token>()
        var start = -1
        var breakInGap = false
        var i = 0
        while (i < view.length) {
            val cp = view.codePointAt(i)
            if (isTokenCodePoint(cp)) {
                if (start < 0) start = i
            } else {
                if (start >= 0) {
                    tokens.add(makeToken(view.substring(start, i), replace, tokens.isEmpty() || breakInGap))
                    start = -1
                    breakInGap = false
                }
                if (cp in SENTENCE_BREAKS) breakInGap = true
            }
            i += Character.charCount(cp)
        }
        if (start >= 0) {
            tokens.add(makeToken(view.substring(start), replace, tokens.isEmpty() || breakInGap))
        }
        return tokens
    }

    private fun makeToken(text: String, replace: List<List<String>>, sentenceInitial: Boolean): Token =
        Token(text = text, folded = foldToken(text, replace), sentenceInitial = sentenceInitial)
}
