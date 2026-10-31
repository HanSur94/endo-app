// EndoApp — l10n text normalization, folding and tokenization (SAFE-01/SAFE-02/SAFE-04).
//
// Mirror counterpart: `android/insights/src/main/kotlin/com/endoapp/insights/l10n/L10nText.kt`.
// Contract: implements `l10n/README.md` §Matching spec steps 1-3 exactly. Any change here needs the
// same change in Kotlin — the shared `selfTest` fixtures are what proves the two agree.
//
// Rules this file obeys (Pitfall 2): every case mapping is locale-independent (`lowercased()` is the
// Swift counterpart of the JVM root locale), tokens are found by walking Unicode SCALARS with an
// explicit general-category set, and comparisons never go through a Character (grapheme) view. No
// Foundation case-insensitive or diacritic-insensitive folding API is used anywhere: they fold `ß`
// on one platform only and would desynchronize the two runtimes.
import Foundation

/// One token of the typography view. `text` keeps its case, `folded` is lowercased plus the policy's
/// replace pairs, `sentenceInitial` is true for the first token or when the gap before it contained
/// `.`, `!`, `?`, `:` or a newline.
struct L10nToken: Equatable, Sendable {
    let text: String
    let folded: String
    let sentenceInitial: Bool
}

enum L10nText {

    /// Typographic apostrophes mapped to U+0027: U+2019, U+2018, U+02BC.
    private static let apostrophes: Set<UInt32> = [0x2019, 0x2018, 0x02BC]

    /// Typographic spaces mapped to U+0020: NBSP U+00A0, NNBSP U+202F, thin U+2009, figure U+2007.
    private static let spaces: Set<UInt32> = [0x00A0, 0x202F, 0x2009, 0x2007]

    /// Gap characters that make the next token sentence-initial: `.` `!` `?` `:` and newline.
    private static let sentenceBreaks: Set<UInt32> = [0x002E, 0x0021, 0x003F, 0x003A, 0x000A]

    /// Step 1: NFC, then the 3 apostrophe and 4 space mappings.
    static func typography(_ text: String) -> String {
        let nfc = text.precomposedStringWithCanonicalMapping
        var out = String.UnicodeScalarView()
        for scalar in nfc.unicodeScalars {
            if apostrophes.contains(scalar.value) {
                out.append("'")
            } else if spaces.contains(scalar.value) {
                out.append(" ")
            } else {
                out.append(scalar)
            }
        }
        return String(out)
    }

    /// Step 2: the folded string. Typography view, lowercased, then `replace` pairs in list order.
    static func foldString(_ text: String, replace: [[String]]) -> String {
        foldToken(typography(text), replace: replace)
    }

    /// Lowercasing plus the `replace` pairs for one already-typography-normalized token.
    static func foldToken(_ tokenText: String, replace: [[String]]) -> String {
        var folded = tokenText.lowercased()
        for pair in replace where pair.count == 2 {
            folded = folded.replacingOccurrences(of: pair[0], with: pair[1])
        }
        return folded
    }

    /// True when the scalar belongs to a token: general category Lu, Ll, Lt, Lm, Lo or Nd.
    /// Combining marks (Mn/Mc/Me) are deliberately absent — after NFC a leftover mark separates tokens.
    static func isTokenScalar(_ scalar: Unicode.Scalar) -> Bool {
        switch scalar.properties.generalCategory {
        case .uppercaseLetter, .lowercaseLetter, .titlecaseLetter, .modifierLetter,
             .otherLetter, .decimalNumber:
            return true
        default:
            return false
        }
    }

    /// Step 3: maximal runs of token scalars of the typography view, with folded form and
    /// sentence-initial flag. Walks scalars, never Characters.
    static func tokenize(_ text: String, replace: [[String]]) -> [L10nToken] {
        let scalars = Array(typography(text).unicodeScalars)
        var tokens: [L10nToken] = []
        var start = -1
        var breakInGap = false

        func emit(_ range: ArraySlice<Unicode.Scalar>) {
            let tokenText = String(String.UnicodeScalarView(range))
            tokens.append(L10nToken(text: tokenText,
                                    folded: foldToken(tokenText, replace: replace),
                                    sentenceInitial: tokens.isEmpty || breakInGap))
        }

        for (index, scalar) in scalars.enumerated() {
            if isTokenScalar(scalar) {
                if start < 0 { start = index }
            } else {
                if start >= 0 {
                    emit(scalars[start..<index])
                    start = -1
                    breakInGap = false
                }
                if sentenceBreaks.contains(scalar.value) { breakInGap = true }
            }
        }
        if start >= 0 { emit(scalars[start...]) }
        return tokens
    }
}
