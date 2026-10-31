# l10n — shared localization safety data

## Purpose

This folder is the per-language claims safety net for German, French and Spanish (SAFE-01..03).
It holds curated data, not code. Both platforms read it and must reach the same verdict for the
same input:

| File | Shipped? | What it is |
|------|----------|------------|
| `claims-policy.de.json`, `claims-policy.fr.json`, `claims-policy.es.json` | yes (Android `:insights` classpath at `/l10n/`, iOS app bundle) | Forbidden-claim rules, allow-list, approved framing and self-test fixtures, one file per language |
| `glossary.json` | no (test-only) | Concept translations, the register (du / vous / tú) and conformance self-test pairs |
| `README.md` | no | This normative spec |

The English lists stay in code (`ForbiddenTerms.kt`, `ClaimsPolicy.kt`, `ClaimsPolicy.swift`) and are
not in this folder. Nothing here edits or replaces them (SAFE-04). English rules are never run on
DE/FR/ES text, and these rules are never run on English text.

## Matching spec

This is the normative algorithm. Android (`:insights`, Kotlin) and iOS (app target, Swift) implement it
identically. If the two disagree, the fixtures in `selfTest` decide.

1. **Typography view.** Normalize to NFC. Then map the apostrophes U+2019, U+2018 and U+02BC to
   U+0027 (`'`), and the spaces U+00A0, U+202F, U+2009 and U+2007 to U+0020 (` `).
2. **Folded string.** Take the typography view, lowercase it with the root locale (JVM
   `lowercase(Locale.ROOT)`, Swift `lowercased()`), then apply the file's `folding.replace` pairs in
   array order (DE: `ß` → `ss`, so `ẞ` → `ß` → `ss`). Never use locale-default lowercasing
   (`Locale.getDefault()` turns `DIAGNOSE` into `dıagnose` under Turkish) and never use
   case-insensitive folding APIs (Swift `.folding(.caseInsensitive)`,
   `localizedCaseInsensitiveContains`), which fold `ß` on one platform only.
3. **Tokens.** A token is a maximal run of code points whose Unicode general category is `Lu`, `Ll`,
   `Lt`, `Lm`, `Lo` or `Nd`, taken from the typography view. Everything else (spaces, punctuation,
   apostrophes, hyphens, combining marks left after NFC) separates tokens, so `l'entraîne` → `l`,
   `entraîne` and `Schub-Frühwarnung` → `schub`, `frühwarnung`. Work on code points (JVM) or Unicode
   scalars (Swift), never on Swift `Character`, and never on `CharacterSet.letters`/`.alphanumerics`
   (they include combining marks). Each token keeps:
   - its **cased** text (typography view, no lowercasing),
   - its **folded** form (root lowercase plus the `folding.replace` pairs applied to that token alone),
   - a **sentenceInitial** flag: true for the first token, or when the gap since the previous token
     contains `.`, `!`, `?`, `:` or a newline.
4. **Masking.** A token whose folded form equals an `allow.tokens[].token` is masked. Every
   occurrence of an `allow.phrases[].phrase` (tokenized the same way, all tokens exactly equal to
   consecutive folded tokens) masks those tokens. Masked tokens are invisible to every token-based kind.
5. **Rule kinds.**
   - `raw`: the folded string contains the term. Used for symbols such as `%` and `p =`. Ignores masking.
   - `word`: an unmasked token's folded form equals the term.
   - `prefix`: an unmasked token's folded form starts with the term.
   - `stem`: an unmasked token's folded form contains the term.
   - `phrase`: the term is tokenized into k tokens. It matches k consecutive unmasked tokens where
     the first k−1 are equal and the last is prefix-matched (`führt zu` catches zu/zum/zur,
     `parce qu` catches que/qu').
   - `wordCased` / `wordCasedMidSentence` (glossary register only): an unmasked token's **cased**
     text equals the term exactly. The `MidSentence` variant skips sentence-initial tokens (DE formal
     `Sie` vs sentence-initial `Sie` = "they").
6. **Fixed evaluation order.** Categories `diagnostic`, `treatment`, `causal`, `certainty`,
   `alarmist`, `imperative`, `gendered`, `precision`. Within a category, kinds `raw`, `word`,
   `prefix`, `stem`, `phrase` (the register adds `wordCased`, then `wordCasedMidSentence`). Within a
   kind, list order. `violation(text)` returns the first hit as `{category, term}`; `violations(text)`
   returns every hit (for error messages). The first hit follows rule order, not word order in the
   sentence.

No rule term is a regular expression. ICU and Java regex differ on `\b`, Unicode classes and
case-insensitive flags, so the data uses plain strings only.

## Policy file schema

`claims-policy.<tag>.json`, UTF-8, LF, NFC text. Exact top-level keys, in this order:

| Key | Value |
|-----|-------|
| `schemaVersion` | `1` |
| `policyRevision` | integer, bumped on every data change |
| `language` | equals the filename tag (`de`, `fr`, `es`) |
| `reviewStatus` | `"draft-unreviewed"` (see Review status) |
| `folding` | `{ "replace": [[from, to], ...] }`, applied in array order after lowercasing |
| `categories` | the 8 categories in the fixed order, each with exactly the 5 kinds `raw`, `word`, `prefix`, `stem`, `phrase` (arrays, empty allowed) |
| `allow` | `{ "tokens": [{token, reason, collidesWith}], "phrases": [{phrase, reason, collidesWith}] }` |
| `framing` | approved disclaimer strings, pre-folded |
| `selfTest` | `{ normalization: [{input, folded}], tokenize: [{input, tokens, sentenceInitial}], mustFail: [{text, category, term}], mustPass: [text] }` |

Rules the tests enforce on both platforms:

- **Exact key sets** at every level. An unknown or misspelled key fails (Swift `JSONDecoder` ignores
  unknown keys, so iOS checks key sets with `JSONSerialization`).
- **Pre-folded terms.** `fold(term) == term` for every rule term, allow token, allow phrase and
  framing entry.
- **Token shape.** `word`, `prefix` and `stem` terms are exactly one token; `phrase` terms are two or
  more tokens.
- **Anti-vacuity.** The five SC1 categories (`diagnostic`, `causal`, `imperative`, `alarmist`,
  `gendered`) are non-empty in every language, and `mustFail` has at least one case per category.
- **Allow hygiene.** Each allow token is one token, never equal to a `word`/`prefix`/`stem` term of
  its own language (never exempt the forbidden word itself), has a non-empty `reason`, and every
  `collidesWith` term really occurs in it, either as a rule of that language or as an English code
  list term (an EN-trap control such as DE `pause` ⊃ `use`, `vorher` ⊃ `her`).
- **`language` equals the filename tag.**
- **Escapes.** Combining marks, `ẞ`, U+00A0, U+202F, U+2009, U+2007 and U+2019/U+2018/U+02BC in
  fixtures are written as JSON `\u` escapes, never as raw characters, so no editor can silently
  normalize them away.

## Glossary schema

`glossary.json`, test-only. Exact top-level keys, in this order: `schemaVersion`, `policyRevision`,
`reviewStatus`, `register`, `doNotTranslate`, `concepts`, `selfTest`.

- `register.<tag>` = `{ form, banned }`. `form` is `du` / `vous` / `tú`. `banned` uses any subset of
  the kinds `raw`, `word`, `prefix`, `stem`, `phrase`, `wordCased`, `wordCasedMidSentence`. Cased kinds
  hold exact-case NFC terms; every other kind holds pre-folded terms.
- `doNotTranslate`: brand and platform names kept verbatim (`Health Connect`, `Endo-App`).
- `concepts[]` = `{ id, context, en: {triggers}, de, fr, es }`, each language `{ approved, banned }`.
  `triggers` are non-empty lowercase EN phrases from the shipped EN copy. `approved` holds non-empty,
  display-cased forms as they appear in copy. `banned` holds pre-folded rule-kind lists (`{}` when
  none).
- `selfTest.pairs[]` = `{ en, lang, text, expect, concept, term }`, `expect` in `pass`, `banned`,
  `register`. `concept` and `term` are `null` for `pass`; `concept` is `null` for `register`. Every
  language has at least one pair of each kind.

Semantics:

- **Conformance (conditional).** For each concept whose EN trigger matches the EN source (phrase
  kind, folded), the translation must contain one `approved` form (phrase kind) and none of `banned`.
  Banned checks only run when the trigger matches, so FR "certains jours" (some days) can coexist with
  a banned FR "certain" for the confidence concepts.
- **Register (unconditional).** Every DE/FR/ES string, with or without an EN pair, must not match its
  language's `register.banned`. The register uses the same matcher and the same allow masking as the
  language's policy file (FR allow phrase `a t il` keeps "Y a-t-il" clear of the word `t`).
- **Hygiene.** All four languages present per concept. Every `approved` form passes its language's
  policy and the register check. `approved ∩ banned = ∅`. Every EN trigger passes the existing EN scan.

## Translator rules

- **Register.** DE uses `du`, FR uses `vous`, ES uses `tú`.
- **FR reminders** use "rappeler", never "prévenir" (which also means "prevent" and is a treatment
  rule).
- **FR activity copy** uses "entraînement" or "activité physique". The verb "s'entraîner" stays
  caught by the causal prefix `entraîn`.
- **DE insights** are "Einblicke", never "Erkenntnisse".
- **DE flare hint** is "Hinweis auf einen möglichen Schub", never a "Warnung" or "Frühwarnung".
- **Severity** is FR "intensité" and ES "intensidad", never "gravité"/"gravedad".
- **ES is pan-regional.** Avoid `vosotros`/`vuestr*`; it is guidance, not a register rule.
- **Medical-device wording.** ES uses the EU MDR term "producto sanitario". LatAm "dispositivo
  médico" is an open point for Phase 33.
- **Known limitation.** DE separable verbs ("setze … ab") are not caught by contiguous phrases.
  Native review covers them.
- **Never weaken a stem to dodge a collision.** Add an allow entry with a reason instead, and get it
  signed off in native review.

## Registering a new string source

Every localized file in the repo must be claimed by the test-side `L10nSourceRegistry` on each
platform (added in 26-03), or the tripwire test fails with "register an extractor for <path>". The
registry maps a path pattern to an extractor that returns `(lang, string, origin)` triples, and the
audit runs every extracted string through `forLanguage(lang)` and the register check.

Phase 26 registers `glossary.json` (approved forms), the policy files (`framing` only; rules and
`selfTest` are policy data, not copy) and this README (documentation, exempt). Later phases add one
registry line each, not a new mechanism:

| Phase | Source |
|-------|--------|
| 27 | `l10n/insight-messages.json` |
| 29 | `android/app/src/main/res/values-{de,fr,es}*/strings.xml` |
| 30 | `*.xcstrings`, `InfoPlist.xcstrings` |
| 33 | store metadata |

## Branch rule (Android is the reference)

`l10n/` is edited only on `claude/endo-android-version-e608a7`. The iOS branch takes it only via:

```bash
git checkout claude/endo-android-version-e608a7 -- l10n
```

Any data fix goes to the Android branch first and is then re-copied. Never edit `l10n/` on the iOS
branch to make a Swift test pass. Bump `policyRevision` on every data change, so a mismatch between
the two branches shows up in test output.

## Review status

Every file in this folder is `draft-unreviewed` with LOW confidence until the Phase 33 native-speaker
and legal review (LEGL-03). Tests assert this value and never set it. Nothing in this folder may claim
a native or legal review that has not happened.
