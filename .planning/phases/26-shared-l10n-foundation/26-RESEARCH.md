# Phase 26: Shared l10n Foundation - Research

**Researched:** 2026-10-31
**Domain:** Per-language claims-safety policy (DE/FR/ES) + shared glossary as repo-root JSON, enforced by Kotlin (JVM) and Swift tests with byte-identical matching semantics
**Confidence:** HIGH for the code integration points and the matching/normalization mechanics (read in this session, and probed on JDK 21 and Swift 6.4); MEDIUM for the Gradle and XcodeGen resource wiring (official docs, not yet built); LOW for the seeded DE/FR/ES term lists and glossary content (draft, native review in Phase 33)

## Summary

Phase 26 adds data and a checker. It does not translate anything yet. The English policy stays as it is: Kotlin `ForbiddenTerms` and `ClaimsPolicy`, Swift `ClaimsPolicy.swift`, and the drift tests that regex-parse the Swift `static let … : [String] = [` declarations. Every DE/FR/ES rule goes in new repo-root JSON (`l10n/claims-policy.{de,fr,es}.json`, `l10n/glossary.json`). Both platforms get one new, additive matcher that reads that JSON and returns the same verdict for the same input. The safest way to keep both platforms identical is to make the fixtures shared data too: the seeded bad sentences, the host-word controls and the normalization cases live in the same JSON files, so Android and iOS assert the same expected `{category, term}` for every case.

The matcher has to be defined precisely, because the obvious implementations already differ between the two runtimes. Probes run this session showed: (1) JVM `String.contains` does not match NFD text against an NFC term, while Swift `String.contains` does (canonical equivalence), so without explicit NFC the two platforms disagree. (2) Swift `folding(.caseInsensitive)` maps `ß→ss`; JVM `lowercase` does not. (3) Swift `JSONDecoder` silently ignores a misspelled key. A typo like `"prefx"` would quietly drop a whole rule class on iOS while kotlinx-serialization (strict by default) would throw on Android. The spec below fixes these: NFC, then `lowercase(ROOT)`, then a per-language replace map from JSON; tokenizing by explicit Unicode general category; five match kinds (`raw`, `word`, `prefix`, `stem`, `phrase`) plus case-sensitive register kinds; whole-token allow-lists; and a schema key-set test on both platforms.

The wiring is different on each platform. Android puts the matcher and loader in pure-JVM `:insights` (`ForbiddenTerms.forLanguage`, a new file that extends the object) and ships the policy JSON as classpath resources through a Gradle `Sync` task. `:app` `ClaimsPolicy.forLanguage` delegates to it, following the existing "reuse, never fork" pattern. iOS cannot bundle repo-root files into the `EndoInsights` SPM package, so `ClaimsPolicy.forLanguage` goes into the app target as `ClaimsPolicy+Localized.swift`, and `project.yml` copies the JSON into the app bundle. The glossary is test-only on both platforms. A tripwire test on each platform finds every localized-looking file in the repo and fails unless an extractor is registered for it. That is how SC4 ("no later phase can bypass it") holds by construction.

**Primary recommendation:** Build the Android half first in this worktree as the reference implementation, with the data plus `:insights` matcher plus `:app` wiring, tripwire and glossary tests. Freeze `l10n/` there. Then copy it byte-for-byte onto a new iOS branch from `main` (`git checkout claude/endo-android-version-e608a7 -- l10n`) and port the matcher to Swift against the same fixtures. Do not edit a single English list or English test.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| DE/FR/ES forbidden lists, allow-lists, self-test fixtures | Repo-root data (`l10n/claims-policy.*.json`) | — | One file per language so a reviewer signs off per file; byte-identical on both branches |
| Glossary (approved and banned terms per concept, register) | Repo-root data (`l10n/glossary.json`), test-only | — | Never ships at runtime (ARCHITECTURE: "The glossary is test-only data") |
| Text folding, tokenizing, rule matching (Android) | `:insights` pure JVM (`com.endoapp.insights.l10n`) | `:app` delegates | Same single-source pattern as the English `ForbiddenTerms` → `ClaimsPolicy` reuse |
| Text folding, tokenizing, rule matching (iOS) | App target `Sources/EndoApp/Content/` | — | An SPM target cannot bundle files outside its own directory; HonestyGate stays English, so the package does not need it |
| Runtime availability of the policy | Android: `:insights` jar Java resources; iOS: app bundle resources | — | Phase 28/30 renderers need a fail-closed guard at runtime, so the policy must be loadable now |
| Audit of all DE/FR/ES string sources + tripwire | Test tier (`:app` unit tests; `EndoAppTests`) | — | The test sees the whole repo: future `values-*/strings.xml`, `*.xcstrings`, store metadata |
| English audit (unchanged) | Existing `ForbiddenTerms` / `ClaimsPolicy` / `HonestyGate` / drift tests | — | SAFE-04: zero edits |

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SAFE-01 | Every DE/FR/ES string the app can show is checked against a curated per-language forbidden list (diagnostic, causal, imperative, alarmist, gendered); build fails on a hit | §Policy schema (8 categories), §Matching spec, §Seed lists, §Tripwire/registry (SC4), shared `selfTest.mustFail` fixtures |
| SAFE-02 | Per-language allow-list for innocent host words; seeded bad sentences must make the check red | §Allow-list semantics (whole-token, `collidesWith` verified), `selfTest.mustPass` / `mustFail`, NFC + case-fold fixtures |
| SAFE-03 | One shared glossary, approved term per concept in 4 languages, register DE du / FR vous / ES tú; both platforms fail on banned variant | §Glossary schema, §Register rules (case-sensitive kinds), §Glossary seed, conformance self-test pairs |
| SAFE-04 | English claims audit, HonestyGate and drift guards keep passing unchanged | §SAFE-04 isolation rules: new files only, extension functions, `git diff --exit-code` guard over the English files |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Native iOS SwiftUI + Android Kotlin; **no third-party backend, no network, no analytics**. The l10n data is bundled, never fetched. [VERIFIED: CLAUDE.md]
- **Keep dependencies near zero.** Phase 26 adds no package on either platform. kotlinx-serialization is already in `:insights` (`implementation(libs.kotlinx.serialization.json)`, `android/insights/build.gradle.kts:17`), and Swift uses Foundation `JSONDecoder`. [VERIFIED: android/insights/build.gradle.kts:15-23]
- **Positioning is wellness, not a medical device.** No diagnosis claims and "not medical advice" framing. This phase is the guard for that line in DE/FR/ES. [VERIFIED: CLAUDE.md]
- **Swift Testing** for new iOS logic tests; XCTest only for UI. **Swift 6 language mode** (`SWIFT_VERSION: "6.0"` in `project.yml`), so mutable global caches will not compile. [VERIFIED: project.yml]
- **GSD workflow enforcement:** edits only through GSD execute. **Do not commit from this research step** (orchestrator instruction).
- Carried invariants (STATE.md): the single `HonestyGate`, the ClaimsPolicy copy choke-point, no-`INTERNET` manifest, zero analytics. All must stay green.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| kotlinx-serialization-json | 1.11.0 (already pinned) | Strict decode of the l10n JSON in `:insights` | Already a dependency (`kotlinxSerialization = "1.11.0"`, `gradle/libs.versions.toml`). Unknown keys throw by default. [VERIFIED: android/gradle/libs.versions.toml] [CITED: github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md — `ignoreUnknownKeys` defaults to false] |
| `java.text.Normalizer` (JDK 21) | system | NFC normalization | Part of the JDK; probe showed NFD `"guérir".contains("guérir") == false` until normalized [VERIFIED: local JDK 21.0.2 probe] |
| Foundation `precomposedStringWithCanonicalMapping` | system | NFC on Swift | Probe: NFD 7 scalars → NFC 6 scalars [VERIFIED: local Swift 6.4.2 probe] |
| Foundation `JSONDecoder` + `JSONSerialization` | system | Decode policy/glossary; key-set check | `JSONDecoder` ignores unknown keys, so a separate key-set assertion is needed [VERIFIED: local Swift probe — `"prefx"` silently ignored, optional became nil] |
| JUnit Jupiter 6.1.1 (`:insights`), JUnit 4.13.2 (`:app`), Swift Testing (iOS) | already pinned | Tests | Existing frameworks per module [VERIFIED: android/gradle/libs.versions.toml; Tests/EndoAppTests/CopyAuditTests.swift `import Testing`] |

### Supporting
| Tool | Version | Purpose | When to Use |
|------|---------|---------|-------------|
| Gradle `Sync` task + `sourceSets.main.resources.srcDir(taskProvider)` | Gradle (AGP 9.2.0 build) | Copy `l10n/claims-policy.*.json` into `:insights` classpath resources | Android runtime + test loading [CITED: docs.gradle.org SourceDirectorySet.srcDir — "evaluated as per Project.files(Object...)"; Project.files — "A Provider … If the provider represents an output of a task, that task is executed if the file collection is used as an input to another task."] |
| XcodeGen target source with `includes` + `buildPhase: resources` | 2.46.0 installed | Copy policy JSON into the iOS app bundle | [CITED: github.com/yonaskolb/XcodeGen/blob/master/Docs/ProjectSpec.md — `resources`: "Copy Bundle Resources phase"; includes are "relative to `path`"] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Classpath resource via `Sync` task | Gradle-generated Kotlin source embedding the JSON | No runtime I/O, but a custom codegen task to maintain. Not worth it. |
| Classpath resource via `Sync` task | `resources.srcDir("../../l10n")` directly | Simpler, but would also ship `glossary.json`/README and put files at the jar root. Use `Sync` with `include(...)` into an `l10n/` subfolder. |
| Swift matcher in app target | Matcher inside `EndoInsights` SPM package | SPM resources must sit inside the target directory. Repo-root `l10n/` would need symlinks or copies (fragile). The package does not need the DE/FR/ES policy in Phase 26. |
| Regex-based rules | Token-based rule kinds (recommended) | Regex dialects differ (ICU vs `java.util.regex`: `\b`, Unicode classes, lookbehind). Token rules are portable and readable by reviewers. |
| Diacritic-insensitive matching | NFC + case fold only (recommended) | Stripping diacritics creates new collisions and destroys ES `tú`/`tu`. List accented and unaccented stems explicitly where morphology alternates (`diagnos`/`diagnós`). |

**Installation:** none. No new packages on either platform.

## Package Legitimacy Audit

This phase installs **no external packages** on either platform. Everything it uses is already pinned (kotlinx-serialization 1.11.0, JUnit) or is a system framework (JDK `java.text`, Foundation). The legitimacy gate is not applicable.

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
                      l10n/  (repo root, byte-identical on Android + iOS branches)
     ┌────────────────────────────┬───────────────────────────┬──────────────────────┐
     │ claims-policy.de.json      │ glossary.json (test-only) │ README.md            │
     │ claims-policy.fr.json      │  concepts × {en,de,fr,es} │  translator rules,   │
     │ claims-policy.es.json      │  register du/vous/tú      │  schema, LOW status  │
     │  rules · allow · folding   │  selfTest pairs           │                      │
     │  selfTest fixtures         │                           │                      │
     └─────────────┬──────────────┴─────────────┬─────────────┴──────────────────────┘
                   │ (bundled)                  │ (read by path in tests only)
     ┌─────────────▼───────────────┐   ┌────────▼──────────────────────────────────┐
     │ Android: Gradle Sync → :insights classpath /l10n/*.json                     │
     │ iOS: project.yml resources → app bundle                                     │
     └─────────────┬───────────────┘   └────────┬──────────────────────────────────┘
                   ▼                            ▼
   text ──► fold(NFC → lowercase(ROOT) → per-lang replace) ──► tokenize (L*/Nd runs)
                   │                                              │
                   │            mask allow.tokens / allow.phrases │
                   ▼                                              ▼
        raw rules on folded string        word / prefix / stem / phrase on unmasked tokens
                   │                      register rules on NFC-only tokens (case-sensitive)
                   └──────────────► first Violation{category, kind, term} | null
                                              │
           ┌──────────────────────────────────┼───────────────────────────────────┐
           ▼                                  ▼                                   ▼
  selfTest fixtures (mustFail/mustPass/  L10n audit over registered       Tripwire: every localized-
  normalization) → same verdict on       sources (glossary approved         looking file in repo must
  both platforms                         terms now; strings.xml, xcstrings, map to a registered
                                         store copy later) → test FAIL      extractor, else FAIL
```

### Recommended Project Structure

```
l10n/
├── README.md                    # translator/reviewer rules, schema reference, "LOW until Phase 33 review"
├── claims-policy.de.json        # DE rules + allow + folding + selfTest
├── claims-policy.fr.json
├── claims-policy.es.json
└── glossary.json                # concepts × en/de/fr/es, register block, selfTest pairs

android/insights/src/main/kotlin/com/endoapp/insights/l10n/
├── PolicyLanguage.kt            # enum DE/FR/ES (tag "de"/"fr"/"es")
├── L10nText.kt                  # fold(), tokenize(), Token(start,end,text,sentenceInitial)
├── LanguageClaimsPolicy.kt      # @Serializable model + violation()/violations()
├── LanguageRules.kt             # RuleSet {raw, word, prefix, stem, phrase} + matcher
├── Glossary.kt                  # @Serializable model + conformance(enSource, lang, text)
└── ForbiddenTermsLocalized.kt   # fun ForbiddenTerms.forLanguage(lang): LanguageClaimsPolicy (lazy, cached)
android/insights/src/test/kotlin/com/endoapp/insights/l10n/
├── LocalizedPolicySelfTests.kt  # mustFail/mustPass/normalization fixtures, per language
├── LocalizedPolicySchemaTests.kt# key sets, pre-folded terms, non-empty categories, allow hygiene
└── BundledPolicyParityTests.kt  # classpath bytes == repo l10n bytes
android/app/src/main/kotlin/com/endoapp/app/content/
└── ClaimsPolicyLocalized.kt     # fun ClaimsPolicy.forLanguage(lang) = ForbiddenTerms.forLanguage(lang)
android/app/src/test/kotlin/com/endoapp/app/l10n/
├── L10nSourceRegistry.kt        # (test source) extractor per localized file pattern
├── L10nAuditTest.kt             # every registered DE/FR/ES string passes policy + register
├── L10nTripwireTest.kt          # unknown localized file → FAIL
├── GlossaryConformanceTest.kt   # selfTest pairs; approved terms pass policy; banned ∩ approved = ∅
└── ClaimsPolicyLocalizedReuseTest.kt # assertSame(ForbiddenTerms.forLanguage(x), ClaimsPolicy.forLanguage(x))

# iOS branch (from main)
Sources/EndoApp/Content/
├── ClaimsPolicy+Localized.swift # extension ClaimsPolicy { static func forLanguage(_:) }
└── L10n/ (or Content/Localized*) # L10nText, LanguageClaimsPolicy, Glossary (Codable, Sendable)
Tests/EndoAppTests/
├── LocalizedPolicySelfTests.swift
├── LocalizedPolicySchemaTests.swift
├── BundledPolicyParityTests.swift
├── L10nAuditTests.swift          # + tripwire
└── GlossaryConformanceTests.swift
```

Folder and type names are at the planner's discretion. The split matters: matcher in the pure tier, audit/tripwire in tests, glossary not bundled. [ASSUMED — recommended layout]

### Pattern 1: Normative matching spec (both platforms implement this identically)

**What:** One deterministic algorithm, defined here and in `l10n/README.md`, so both platforms reach the same verdict without sharing code.

1. **Typography normalization** (applies to every view): NFC first. Then map apostrophes `U+2019 U+2018 U+02BC → U+0027` and space separators `U+00A0 U+202F U+2009 U+2007 → U+0020`. The probe confirmed U+00A0 and U+202F are not letters on either runtime. [VERIFIED: JDK + Swift probes]
2. **Case-folded view** (used by every rule except register kinds): `lowercase` with `Locale.ROOT` on JVM and `lowercased()` in Swift. Then apply the language's `folding.replace` map from JSON (DE: `"ß" → "ss"`). Lowercase first, so `ẞ → ß → ss`. Never use `Locale.getDefault()`: the probe showed `"DIAGNOSE".toLowerCase(tr)` gives `"dıagnose"`. Never use Swift `.folding(.caseInsensitive)` or `localizedCaseInsensitiveContains`, which fold `ß→ss` on Swift only. [VERIFIED: probes]
3. **Tokenize:** maximal runs of code points whose Unicode general category is `Lu, Ll, Lt, Lm, Lo, Nd`. On the JVM, `Character.isLetterOrDigit(cp)` is defined as exactly these categories. In Swift, switch on `scalar.properties.generalCategory` with the same six cases. Do **not** use `CharacterSet.alphanumerics`/`.letters`: they include combining marks (M*), which the JVM excludes. Apostrophes and hyphens are separators, so `l'entraîne` → `l`, `entraîne` and `Schub-Frühwarnung` → `schub`, `frühwarnung`. Work on Unicode-scalar arrays in Swift (`Array(s.unicodeScalars)`) and on code points on the JVM. Never on Swift `Character`. [CITED: Java SE 21 `Character.isLetter`/`isDigit` javadoc] [ASSUMED: Swift category list is equivalent for Latin script — prove with the shared normalization fixtures]
4. **Sentence-initial flag** per token: true if it is the first token, or if the gap since the previous token contains `.`, `!`, `?`, `:` or `\n`.
5. **Masking:** a token equal to any `allow.tokens` entry is masked. Every occurrence of an `allow.phrases` sequence (all tokens exact) masks its tokens.
6. **Rule kinds** (data, per category):
   - `raw`: the folded *string* contains the term. For symbol tokens such as `%`, `p =`. Ignores masking.
   - `word`: an unmasked token equals the term.
   - `prefix`: an unmasked token starts with the term (FR/ES inflection, elision-safe after tokenizing).
   - `stem`: an unmasked token contains the term (DE compounds: `Schmerzbehandlung`).
   - `phrase`: the term is tokenized with the same tokenizer into k tokens. It matches k consecutive unmasked tokens where the first k−1 are equal and the **last is prefix-matched** (`führt zu` → zu/zum/zur; `parce qu` → que/qu').
   - `wordCased` / `wordCasedMidSentence` (register only): exact match on the NFC-only, typography-normalized view (no lowercasing). The `MidSentence` variant skips sentence-initial tokens (DE formal `Sie`/`Ihr*` vs sentence-initial `Sie` = "they").
7. **Evaluation order** (fixed, so both platforms report the same first hit): categories `diagnostic, treatment, causal, certainty, alarmist, imperative, gendered, precision`. Within a category, kinds `raw, word, prefix, stem, phrase`. Within a kind, list order. `violation(text)` returns the first hit. `violations(text)` returns all hits (for error messages).

**When to use:** every DE/FR/ES string check, on both platforms, now and in later phases.

```kotlin
// Proposed sketch (no in-repo values) — :insights, pure JVM. [ASSUMED design]
object L10nText {
    private val tokenCategories = setOf(
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
        Character.MODIFIER_LETTER, Character.OTHER_LETTER, Character.DECIMAL_DIGIT_NUMBER,
    ).map { it.toInt() }.toSet()

    fun typography(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)
        .replace('’', '\'').replace('‘', '\'').replace('ʼ', '\'')
        .replace(' ', ' ').replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')

    fun fold(s: String, replace: Map<String, String>): String =
        replace.entries.fold(typography(s).lowercase(Locale.ROOT)) { acc, (k, v) -> acc.replace(k, v) }

    fun isTokenChar(cp: Int) = Character.getType(cp) in tokenCategories
}
```

```swift
// Proposed sketch — app target, Swift 6. [ASSUMED design]
enum L10nText {
    static func typography(_ s: String) -> String {
        var t = s.precomposedStringWithCanonicalMapping
        for (from, to) in [("\u{2019}", "'"), ("\u{2018}", "'"), ("\u{02BC}", "'"),
                           ("\u{00A0}", " "), ("\u{202F}", " "), ("\u{2009}", " "), ("\u{2007}", " ")] {
            t = t.replacingOccurrences(of: from, with: to)
        }
        return t
    }
    static func fold(_ s: String, replace: [String: String]) -> String {
        replace.reduce(typography(s).lowercased()) { $0.replacingOccurrences(of: $1.key, with: $1.value) }
    }
    static func isTokenScalar(_ u: Unicode.Scalar) -> Bool {
        switch u.properties.generalCategory {
        case .uppercaseLetter, .lowercaseLetter, .titlecaseLetter, .modifierLetter, .otherLetter, .decimalNumber: true
        default: false
        }
    }
}
```

Note: with several `replace` entries, apply them in a **sorted key order**, not dictionary order. Swift `Dictionary` iteration order is not stable. Better still, make `replace` a JSON **array of pairs** so order is explicit.

### Pattern 2: Policy file schema (`l10n/claims-policy.<lang>.json`)

```json
{
  "schemaVersion": 1,
  "language": "de",
  "reviewStatus": "draft-unreviewed",
  "folding": { "replace": [["ß", "ss"]] },
  "categories": {
    "diagnostic": { "raw": [], "word": ["stadium"], "prefix": [], "stem": ["diagnos", "erkenn", "erkannt", "feststell", "nachweis", "befund"], "phrase": [] },
    "treatment":  { "raw": [], "word": ["geheilt"], "prefix": ["heil"], "stem": ["behandl", "behandel", "therap", "linder", "vorbeug", "verhinder"], "phrase": ["hilft gegen", "wirkt gegen"] },
    "causal":     { "...": "..." },
    "certainty":  { "...": "..." },
    "alarmist":   { "...": "..." },
    "imperative": { "...": "..." },
    "gendered":   { "...": "..." },
    "precision":  { "raw": ["%", "p =", "p=", "r =", "r=", "p-wert", "p wert"], "word": [], "prefix": [], "stem": [], "phrase": [] }
  },
  "allow": {
    "tokens": [
      { "token": "behandlungsteam", "reason": "care team — approved clinician term", "collidesWith": ["behandl"] },
      { "token": "vorher", "reason": "adverb 'before'", "collidesWith": ["vorher", "her"] },
      { "token": "pause", "reason": "EN-trap control: EN imperative 'use' ⊂ Pause; DE text is never scanned with EN lists", "collidesWith": ["use"] }
    ],
    "phrases": [
      { "phrase": "kein diagnoseinstrument", "reason": "negated disclaimer framing (needs legal sign-off)", "collidesWith": ["diagnos"] }
    ]
  },
  "framing": ["keine medizinische beratung", "kein medizinprodukt", "möglicher zusammenhang"],
  "selfTest": {
    "normalization": [ { "input": "STRAẞE", "folded": "strasse" }, { "input": "Guérir", "folded": "guérir" } ],
    "mustFail": [ { "text": "Die App erkennt Schübe frühzeitig.", "category": "diagnostic", "term": "erkenn" } ],
    "mustPass": [ "Dein Behandlungsteam kann mit dir über Muster sprechen.", "Vorher", "Pause" ]
  }
}
```

Schema rules the tests enforce (both platforms):
- **Exact key sets** at every level (`schemaVersion, language, reviewStatus, folding, categories, allow, framing, selfTest`). All 8 categories present, and all 5 kinds present in each (empty arrays allowed). On Swift, check via `JSONSerialization` key sets, because `JSONDecoder` ignores unknown keys. [VERIFIED: Swift probe]
- **Pre-folded terms:** `fold(term) == term` for every rule term, allow token and framing entry. This catches a reviewer typing `Behandlung`, and keeps the data canonical so a fold divergence cannot hide.
- **Anti-vacuity:** the five SC1 categories (`diagnostic, causal, imperative, alarmist, gendered`) are each non-empty per language. `mustFail` has at least one case per non-empty category.
- **Allow hygiene:** each allow token is one token, not equal to any `word`/`prefix`/`stem` term of the same language (never exempt the forbidden word itself), and has a non-empty `reason`. Every `collidesWith` term really matches the token, either under a rule of that language or under the EN code lists (`ClaimsPolicy.causal/imperative/alarmist/forbiddenGenderedTokens`, which are read, not edited). Dead or unjustified allow entries fail.
- `language` equals the filename tag. `reviewStatus` stays `"draft-unreviewed"` until Phase 33. No test may set it to reviewed (LEGL-03).

**About `"pause"`:** the SC2 wording says "pass via each language's allow-list". Today no DE rule collides with "Pause"; only the English `"use"` does. Keeping it as a documented `collidesWith: ["use"]` control entry satisfies SC2 literally and records the EN-trap rationale. The alternative is to treat it as a pure `mustPass` control. Planner's choice. Recommend the allow entry. [ASSUMED]

### Pattern 3: Glossary schema (`l10n/glossary.json`, test-only)

```json
{
  "schemaVersion": 1,
  "reviewStatus": "draft-unreviewed",
  "register": {
    "de": { "form": "du",   "banned": { "wordCased": ["Ihnen"], "wordCasedMidSentence": ["Sie", "Ihr", "Ihre", "Ihren", "Ihrem", "Ihrer", "Ihres"] } },
    "fr": { "form": "vous", "banned": { "word": ["tu", "toi", "ton", "ta", "tes", "te", "t"] } },
    "es": { "form": "tú",   "banned": { "word": ["usted", "ustedes"] } }
  },
  "concepts": [
    {
      "id": "flare",
      "context": "a period of worse symptoms; never an attack/crisis",
      "en": { "triggers": ["flare"] },
      "de": { "approved": ["schub", "schübe", "schubs"], "banned": { "word": ["anfall", "krise"], "stem": ["attacke"] } },
      "fr": { "approved": ["poussée", "poussées"],       "banned": { "word": ["crise", "crises", "attaque"] } },
      "es": { "approved": ["brote", "brotes"],           "banned": { "word": ["crisis", "ataque"] } }
    }
  ],
  "selfTest": {
    "pairs": [
      { "en": "Possible flare pattern", "lang": "de", "text": "Mögliches Schub-Muster", "expect": "pass" },
      { "en": "Possible flare pattern", "lang": "de", "text": "Möglicher Anfall", "expect": "banned", "concept": "flare", "term": "anfall" },
      { "en": "Worth discussing with your clinician", "lang": "de", "text": "Das könnten Sie mit Ihrem Behandlungsteam besprechen.", "expect": "register", "term": "Sie" }
    ]
  }
}
```

Semantics (matching ARCHITECTURE §audit item 5):
- **Conformance(en, lang, text):** for each concept whose `en.triggers` match `en` (phrase kind, folded), `text` must match one `approved` form (phrase kind) and none of `banned`. Banned checks are **conditional on the EN trigger**. This lets FR "certains jours" (some days) coexist with a banned FR "certain" for the confidence concept.
- **Register (unconditional):** every DE/FR/ES string, with or without an EN pair, must not match its language's `register.banned`. The register kinds use the same matcher with the same allow masking from the language's policy file. For example the FR `allow.phrases` entry `"a t il"` keeps the formal-compatible inversion "Y a-t-il" from tripping word `t`.
- **Hygiene:** all 4 languages present for every concept. Every `approved` form passes `ClaimsPolicy.forLanguage(lang)` and the register check (this is the first real DE/FR/ES string source, see SC4). `approved ∩ banned = ∅`. Every EN trigger passes the **existing** EN scan (read-only use of `ClaimsPolicy` lists). Self-test pairs cover pass, banned and register for each of de/fr/es.

### Pattern 4: SC4 — registry plus tripwire ("audit by construction")

- `L10nSourceRegistry` (test code, on each platform) maps a **path pattern** to an **extractor** returning `(lang, string, origin)` triples:
  - Phase 26: `l10n/glossary.json` → every `approved` form per de/fr/es; `l10n/claims-policy.<lang>.json` → `framing` entries (rules and `selfTest` are explicitly registered as *policy data, not copy*, the same exemption the English lists have); `l10n/README.md` → registered as documentation, exempt.
  - Later phases add a line, not a mechanism: `l10n/insight-messages.json` (27), `android/app/src/main/res/values-{de,fr,es}*/strings.xml` (29), `**/*.xcstrings` + `InfoPlist.xcstrings` (30), store metadata (33).
- **Tripwire test:** walk the repo root and collect every file that looks localized: any path segment `values-de*|values-fr*|values-es*`, `de.lproj|fr.lproj|es.lproj`, extension `.xcstrings`, anything under `l10n/`, filenames matching `*.{de,fr,es}.*` or `*-{de,fr,es}.*`, and fastlane-style dirs `de-DE|fr-FR|es-ES|es-419`. Any file not claimed by a registered pattern → **FAIL** with "register an extractor for <path>". Exclude `.git/`, `.planning/` (research docs contain DE/FR/ES examples on purpose), `.claude/`, every `build/`, `.gradle/`, `DerivedData/`, `.build/`, `.swiftpm/`, and any nested directory that holds its own `.git` entry (worktree marker).
- **Audit test:** for every registered `(lang, string)`, `ClaimsPolicy.forLanguage(lang).violation(string) == null` and there is no register violation. Plus a red-capable control: run the same audit function over a synthetic source carrying one `mustFail` sentence per language and assert it reports a failure (mirrors `CopyAuditTest.scanCatchesAForbiddenString`).

### Pattern 5: Loading the shared files

**Android, runtime and tests** (policy only):

```kotlin
// android/insights/build.gradle.kts — proposed addition [ASSUMED syntax; CITED semantics above]
val l10nDir = rootProject.layout.projectDirectory.dir("../l10n")
val syncL10nPolicy by tasks.registering(Sync::class) {
    from(l10nDir) { include("claims-policy.*.json") }
    into(layout.buildDirectory.dir("generated/l10n-resources/l10n"))
}
sourceSets.main { resources.srcDir(syncL10nPolicy.map { it.destinationDir.parentFile }) }
tasks.test {
    useJUnitPlatform()
    inputs.dir(l10nDir).withPathSensitivity(PathSensitivity.RELATIVE)   // glossary is read by path → must be a declared input
    systemProperty("endo.repoRoot", rootDir.parentFile.absolutePath)
}
```

Runtime load: `ForbiddenTerms::class.java.getResourceAsStream("/l10n/claims-policy.de.json")`, decoded once (`by lazy`, thread-safe by default). A missing resource or a decode error **throws**. That is fail-closed: never return an empty policy. Java resources from the `:insights` jar are packaged into the APK and readable through the class loader. Verify once with `unzip -l app/build/outputs/apk/debug/*.apk | grep claims-policy`. [ASSUMED — standard AGP Java-resource merging; verify in execution]

The `srcDir(...destinationDir.parentFile)` mapping above is fiddly. A simpler equivalent: `into(layout.buildDirectory.dir("generated/l10n-resources"))` with `from(l10nDir) { include(...); into("l10n") }`, then `resources.srcDir(syncL10nPolicy)`. Either way, assert the classpath path in a test.

**Android `:app` tests:** AGP unit tests get the same system property through `testOptions.unitTests.all { it.systemProperty("endo.repoRoot", rootDir.parentFile.absolutePath); it.inputs.dir(...) }`. Resolve the repo root from that property, **not** by walking up `user.dir`. This worktree sits inside the main checkout (`/…/endo-app/.claude/worktrees/endo-android-e608a7`). A walk-up that misses `l10n/` here would silently pick up `/…/endo-app/l10n/` from another branch once main has one. [VERIFIED: `git worktree list` output this session]

**iOS:**

```yaml
# project.yml — EndoApp target sources, proposed addition [CITED: XcodeGen ProjectSpec]
    sources:
      - Sources/EndoApp
      - path: l10n
        includes: ["claims-policy.*.json"]
        buildPhase: resources
```

Runtime: `Bundle.main.url(forResource: "claims-policy.de", withExtension: "json")`. In hosted `EndoAppTests`, `Bundle.main` is the host app. Tests read `glossary.json` and the repo copies via `#filePath`: three `deletingLastPathComponent()` calls to reach the project root. This is the existing pattern from `ReportNoNetworkTests.swift:21-24`. After editing `project.yml`, **`xcodegen generate` must run** (the `.xcodeproj` is gitignored), or the bundle lacks the files. The bundled-bytes test catches that.

Swift 6: keep the cache immutable, e.g. `private static let policies: [PolicyLanguage: LanguageClaimsPolicy] = PolicyLanguage.allCases.reduce(into: [:]) { … }`. Make every model `Sendable` (all-`let` structs). A `static var` cache fails Swift 6 strict concurrency.

### Pattern 6: SAFE-04 isolation (zero English edits)

- **No edits** to: `android/insights/.../ForbiddenTerms.kt`, `HonestyGate.kt`, `android/app/.../content/ClaimsPolicy.kt`, `AuditableCopy.kt`, `CopyAuditTest.kt`, `ClaimsPolicyReuseTest.kt`, `ClaimsPolicyDriftTests.kt`, `HonestyGateTests.kt`, `EnumRawValueParityTests.kt`; iOS `Sources/EndoApp/Content/ClaimsPolicy.swift`, `EndoInsights/**`, `Tests/EndoAppTests/CopyAuditTests.swift`, `FlareCopyDriftTests.swift`, `RecommendationsCopyDriftTests.swift`.
- Add `forLanguage` as a **Kotlin extension on the object** in a new file (`fun ForbiddenTerms.forLanguage(lang: PolicyLanguage): LanguageClaimsPolicy`, called as `ForbiddenTerms.forLanguage(...)`) and as a **Swift extension in a new file** (`extension ClaimsPolicy { static func forLanguage(_:) }`). That way the drift regex target stays untouched. The drift test parses `Sources/EndoApp/Content/ClaimsPolicy.swift` with the regex `static\s+let\s+$property\s*:\s*\[String\]\s*=\s*\[` and reads up to the first `]`. A new `static let … : [String] = [` in that file would be parsed. A new file is never read. [VERIFIED: android/insights/src/test/kotlin/com/endoapp/insights/ClaimsPolicyDriftTests.kt:46-59]
- The DE/FR/ES check is a **separate** function. The English `ForbiddenTerms.violation(text)`, which `HonestyGate` uses, is not routed through it and not changed.
- Guard: `git diff --exit-code <phase-base-sha> -- <the English file list above>` in the phase verification.

### Anti-Patterns to Avoid
- **Literal translation of the EN list.** Explicitly out of scope (REQUIREMENTS). "wird", "va", "se trata de" would over-match.
- **Running EN lists on DE/FR/ES text.** False positives ("her" ⊂ "vorher", "use" ⊂ "Pause") push translators toward worse wording.
- **`assumeTrue(file != null)` skip-when-absent** (the drift-test pattern). For `l10n/` that would make a missing or moved policy file pass. l10n tests must **fail** when a file is missing. [VERIFIED: ClaimsPolicyDriftTests.kt:68 uses `assumeTrue`]
- **Regex rules shared across runtimes.** ICU and Java regex differ on `\b`, Unicode classes and case-insensitive flags.
- **Weakening a stem to dodge a collision.** Add an allow token with a reason instead (ARCHITECTURE anti-pattern).
- **Case-folding before register checks.** DE `Sie` (formal) vs `sie` (she/they) disappears after lowercasing.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Unicode normalization | Custom accent composition | `java.text.Normalizer` NFC / `precomposedStringWithCanonicalMapping` | Full canonical composition tables |
| Case folding | Custom ASCII lowercase | `lowercase(Locale.ROOT)` / `lowercased()` + explicit JSON replace map | Locale-stable; `ß` handled by data |
| JSON parsing | Hand parser | kotlinx-serialization (strict) / `JSONDecoder` + `JSONSerialization` key-set check | Both already available |
| Cross-platform equality proof | Manual review | Shared `selfTest` fixtures in the JSON, same expected `{category, term}` on both platforms | Behavioral parity by data, not by code review |
| Bundling repo-root files | Copy-paste JSON into each platform tree | Gradle `Sync` / XcodeGen `buildPhase: resources` + bundled-bytes == repo-bytes test | Avoids a third copy that can drift |

**Key insight:** the risk in this phase is not the algorithm. It is **silent vacuity**: a check that stays green because a key was misspelled, a file was missing and skipped, Gradle did not rerun a test, or two runtimes fold `ß` differently. Every mitigation above is a test that makes one of those failures loud.

## Seed Content (LOW confidence — draft, native-speaker + legal review in Phase 33)

These seeds are **[ASSUMED]**: drafted from FEATURES.md §"Claims boundary by language" plus collision analysis this session. The executor tunes them until `mustFail`/`mustPass` pass. The fixtures decide, and every tuning change needs a reason in the allow entry or in README. Notation: `w:` word, `p:` prefix, `s:` stem, `ph:` phrase, `r:` raw.

### German (register "du")

| Category | Seed rules | Known collisions → handling |
|----------|-----------|-----------------------------|
| diagnostic | s: `diagnos`, `erkenn`, `erkannt`, `feststell`, `festgestell`, `nachweis`, `befund`; w: `stadium`, `stadien` | `Erkenntnis(se)` (would be a natural "Insights") → allow tokens `erkenntnis`, `erkenntnisse`, `erkenntnissen`; glossary uses **"Einblicke"**. `anerkannt`/`anerkennen` → allow. |
| treatment | s: `behandl`, `behandel`, `therap`, `linder`, `vorbeug`, `verhinder`; p: `heil`; w: `geheilt`; ph: `hilft gegen`, `wirkt gegen` | `Behandlungsteam(s)` → allow. Stem alternation behandeln/Behandlung needs both stems. `Verhütung` (contraception, a legit factor) is deliberately **not** listed. |
| causal | s: `verursach`, `ursach`, `ursäch`, `auslös`, `ausgelöst`, `trigger`, `verschlimmer`, `verschlechter`, `verbesser`, `reduzier`, `bewirk`; w: `wegen`, `weil`, `aufgrund`, `infolge`, `wirkt`; p: `wirksam`; ph: `führt zu`, `führen zu`, `sorgt für` | `wegen` ⊂ **`Bewegung`/`bewegen`** (activity copy!) → word, not stem. `weil` ⊂ `jeweils`/`Weile`/`langweilig` → word. `wirk` ⊂ `wirklich` → only `wirkt` word / `wirksam` prefix. |
| certainty | s: `vorher`, `prognos`, `voraussag`, `garantier`; w: `definitiv`, `zweifellos`, `gewiss`; ph: `du wirst`, `wirst du`, `mit sicherheit` | `vorher`/`vorherig*` (before/previous cycle) → allow tokens `vorher`, `vorherig`, `vorherige`, `vorherigen`, `vorheriger`, `vorheriges`, `vorherigem` (catches `Vorhersage`, `vorhergesagt`). **Not** `sicher`: it collides with "Bist du sicher?" and `Sicherung` (backup). |
| alarmist | s: `gefahr`, `gefähr`, `notfall`, `dringend`, `alarm`, `warn`, `attacke`, `anfall`, `bedroh`; w: `akut`, `droht`, `drohend` | Umlaut alternation `Gefahr`/`gefährlich` needs both stems. Glossary flare header uses **"Hinweis auf einen möglichen Schub"**, not "Frühwarnung", so `warn` can be a stem. |
| imperative | w: `nimm`, `iss`, `trink`, `geh`, `beginne`, `starte`, `stoppe`, `frag`; p: `vermeid`, `versuch`, `sollt`, `musst`, `müss`, `verzicht`, `absetz`; ph: `hör auf`, `höre auf`, `achte auf` | `nimm` ⊂ `zunimmt`/`nimmt` (increases) → word. Separable verbs ("setze … ab") cannot be caught by contiguous phrases; documented limitation, covered by native review. |
| gendered | s: `frau`, `weiblich`, `patientin`; w: `mädchen`, `dame`, `damen` | `Frauenarzt` is **intentionally caught**; glossary gynecologist = "gynäkologische Praxis" / "Gynäkologin oder Gynäkologe". `sie` cannot be listed (= they). |
| precision | r: `%`, `p =`, `p=`, `r =`, `r=`, `p-wert`, `p wert` | — |

### French (register "vous")

| Category | Seed rules | Known collisions → handling |
|----------|-----------|-----------------------------|
| diagnostic | p: `diagnos`, `détect`, `dépist` | — |
| treatment | p: `trait`, `thérap`, `guér`, `soulag`, `prévent`; w: `prévenir`, `remède` | `trait` (a feature) is a rare false positive; acceptable. **`prévenir` = "notify"** → reminder copy must use "rappeler"; README rule. `guère` (è) ≠ `guér` (é); no collision under NFC without diacritic stripping. |
| causal | p: `caus`, `provoqu`, `déclench`, `entraîn`, `entrain`, `aggrav`, `amélior`, `rédui`; ph: `parce qu`, `en raison d`, `grâce à`, `lié à`, `liée à`, `liés à`, `liées à` | **`entraînement(s)`** (workout) → allow; also 1990-reform spelling `entrainement(s)` → allow. The verb `s'entraîner` stays caught; activity copy uses "entraînement"/"activité physique". `entrain` (pep) is a rare false positive. |
| certainty | p: `prédi`, `prévis`, `garanti`, `inévitabl`; w: `certainement`, `certitude`, `sûrement`, `assurément`, `forcément`; ph: `vous allez avoir`, `c est certain` | **Not** `certain`: "certains jours" (some days) is everywhere. |
| alarmist | p: `dang`, `urgen`, `alert`, `alarm`, `attaqu`, `imminen`; w: `crise`, `crises`, `grave` | `gravité` (severity) → glossary severity = **"intensité"**; `grave` is word-only. |
| imperative | w: `prenez`, `arrêtez`, `évitez`, `essayez`, `commencez`, `réduisez`, `consultez`, `devez`, `devriez`; ph: `il faut`, `vous devez` | Tu-forms are caught by the glossary register block. |
| gendered | w: `femme`, `femmes`, `patiente`, `patientes`, `madame`, `fille`, `filles`, `fatiguée`, `épuisée`, `stressée`, `inquiète`, `prête`, `reposée`, `soulagée`; p: `fémin` | **Not** `elle` (pronoun for feminine nouns), `seule` ("une seule fois"), `sûre`, `atteintes` ("personnes atteintes" is the approved neutral phrase). |
| precision | r: `%`, `p =`, `p=`, `r =`, `r=`, `valeur p`, `p-value` | FR "15 %" with NNBSP folds to a space; `%` is raw. |

Allow phrase: `a t il` (from "Y a-t-il", so the register word `t` does not trip).

### Spanish (register "tú")

| Category | Seed rules | Known collisions → handling |
|----------|-----------|-----------------------------|
| diagnostic | p: `diagnos`, `diagnós`, `detect`, `detecc` | Accent alternation (`diagnosticar`/`diagnóstico`) needs both. |
| treatment | p: `tratamient`, `terapi`, `terapéutic`, `alivi`, `preven`, `curaci`; w: `curar`, `cura` | **Not bare `trat`/`tratar`**: "se trata de" (it's about), "tratar de" (to try). `cur` would hit `curva` (chart curve) and `curso`; word forms only. |
| causal | p: `caus`, `provoc`, `desencaden`, `empeor`, `agrav`, `vinculad`; w: `porque`, `mejora`, `mejoran`, `mejorar`, `mejoró`, `reduce`, `reducen`, `reducir`; ph: `debido a`, `gracias a` | **Not `mejor`** ("better", "mejor día"). `por qué` → two tokens ≠ `porque`; `porqué` ≠ `porque` (accent). No collision. |
| certainty | p: `predic`, `predec`, `predij`, `pronóstic`, `pronostic`, `garantiz`; w: `definitivamente`; ph: `vas a tener`, `sin duda` | **Not `seguro`** (also "safe"/"insurance"; `datos seguros`). **Not `cierto`** ("por cierto"). |
| alarmist | p: `peligr`, `urgen`, `alert`, `alarm`, `emergenc`, `inminen`; w: `ataque`, `crisis`, `grave` | Glossary severity = **"intensidad"** (not "gravedad"). |
| imperative | w: `toma`, `evita`, `debes`, `deberías`, `intenta`; ph: `deja de`, `tienes que` | `toma` also = intake (noun) → glossary medication = "dosis"/"registro de medicación" (LOW). `prueba`, `empieza`, `consulta` are left out (noun/3rd-person collisions). |
| gendered | w: `mujer`, `mujeres`, `chica`, `chicas`, `señora`, `cansada`, `agotada`, `estresada`, `preocupada`, `nerviosa`, `aliviada`, `afectada`; p: `femenin` | **Not** `ella`, `sola` ("una sola vez"), `lista` (= list), `segura`, `afectadas` (plural agrees with "personas"). |
| precision | r: `%`, `p =`, `p=`, `r =`, `r=`, `valor p`, `p-value` | — |

Register block: `usted`, `ustedes` (word). `vosotros`/`vuestr*` (Spain-only plural) is discretionary. Recommend leaving it out of the register check and covering it in the README as pan-regional guidance.

### Seeded fixtures (minimum per language)

| Lang | mustFail (expected category / term) | mustPass |
|------|-----------------------------------|----------|
| de | "Die App erkennt Schübe frühzeitig." (diagnostic / `erkenn`) · "Stress verursacht Schmerzen." (causal / `verursach`) · "Du solltest Milchprodukte vermeiden." (imperative / `vermeid`: both `vermeid` and `sollt` are prefix rules, and with the seed list order `vermeid` is evaluated first. The first hit follows rule-list order, not word order in the sentence) · "Achtung: gefährlicher Schub!" (alarmist / `gefähr`) · "Für Frauen mit Endometriose" (gendered / `frau`) · "Morgen wirst du einen Schub haben." (certainty / `wirst du`) | "Behandlungsteam", "vorher", "Pause", "Bewegung", "jeweils", "Der Schmerz nimmt zu.", "Bist du sicher?", "Einblicke in deine Einträge" |
| fr | "L'application détecte vos poussées." (diagnostic / `détect`) · "Le stress provoque des douleurs." (causal / `provoqu`) · "Le café l'entraîne." (causal / `entraîn`) · "Évitez les produits laitiers." (imperative / `évitez`) · "Alerte : poussée imminente !" (alarmist / `alert`) · "Êtes-vous fatiguée ?" (gendered / `fatiguée`) | "entraînement", "entrainement", "Certains jours", "Y a-t-il des tendances ?", "Une seule fois", "Personnes atteintes d'endométriose" |
| es | "La app detecta tus brotes." (diagnostic / `detect`) · "El estrés provoca dolor." (causal / `provoc`) · "Debes evitar los lácteos." (imperative / `debes`) · "¡Alerta de brote!" (alarmist / `alert`) · "¿Estás cansada?" (gendered / `cansada`) · "Esto se debe a…" → **add** ph `se debe a` (causal) | "Se trata de tus registros", "Mejor día", "Datos seguros", "Una sola vez", "Lista de medicamentos", "Por cierto" |

Every fixture sentence above is **[ASSUMED]**. The executor must check the exact first-hit term against the final rule order and adjust the expected term or the rule, never the meaning.

### Glossary seed concepts (LOW)

Required concept ids (≈30), each with en triggers + de/fr/es approved (+ banned where a risky variant exists):
`people_with_endometriosis` (DE "Menschen mit Endometriose" ✗ "Frauen mit Endometriose"/"Patientinnen"; FR "personnes atteintes d'endométriose" ✗ "femmes atteintes"; ES "personas con endometriosis" ✗ "mujeres con endometriosis") · `possible_association` (DE "möglicher Zusammenhang"/"mögliche Zusammenhänge" ✗ "Ursache"; FR "association possible" ✗ "cause"/"lien de cause"; ES "posible asociación" ✗ "causa") · `confidence_low/medium/high` (EN "Low — possible association" etc.; DE "niedrig/mittel/hoch" ✗ "sicher"/"Gewissheit"; FR "faible/moyenne/élevée" ✗ "certain"/"certitude"; ES "baja/media/alta" ✗ "seguro"/"certeza") · `clinician` (DE "Behandlungsteam"/"Ärztin oder Arzt" ✗ "Frauenarzt"; FR "professionnel de santé"/"votre médecin"; ES "profesional de la salud"/"tu médico") · `flare` · `flare_early_warning` (DE "Hinweis auf einen möglichen Schub" ✗ "Warnung"/"Alarm"; FR "signe d'une possible poussée" ✗ "alerte"; ES "indicio de un posible brote" ✗ "alerta") · `not_medical_advice` (DE "keine medizinische Beratung"; FR "ne remplace pas un avis médical"; ES "no es consejo médico") · `not_a_medical_device` (DE "kein Medizinprodukt"; FR "pas un dispositif médical"; ES "no es un producto sanitario" (EU MDR wording; LatAm "dispositivo médico" is an open point)) · `self_tracking` · `insights` (DE "Einblicke" ✗ "Erkenntnisse"/"Diagnose"; FR "observations"; ES "observaciones") · `patterns` (Muster/tendances/patrones) · `trend` · `check_in` · `symptom` · `severity` (DE "Stärke"/"Intensität" ✗ "Schweregrad"; FR "intensité" ✗ "gravité"; ES "intensidad" ✗ "gravedad") · `period`/`bleeding`/`menstruation` (TerminologyPreference terms) · `spotting` · `endo_belly` · `painful_sex`, `period_pain`, `bowel_pain`, `urination_pain` (UI lay terms; clinical terms Dyspareunie/dysménorrhée/… are **banned in UI**) · `pelvic_pain` · `fatigue` · `brain_fog` · `medication` (✗ Therapie/traitement/tratamiento) · `pain_relief` (✗ "Schmerzbehandlung"/"traitement de la douleur"/"tratamiento del dolor") · `activity` (DE "Aktivität"/"Bewegung"; FR "activité physique"/"entraînement"; ES "actividad física") · `report` (DE "Bericht" ✗ "Befund"; FR "rapport" ✗ "compte rendu médical"; ES "informe") · `not_enough_data` · `encrypted` (DE "verschlüsselt"; FR "chiffré" ✗ "crypté"; ES "cifrado").
A `doNotTranslate` list: "Health Connect", "Endo-App". iOS-side naming of Apple Health per locale (the Health app is localized in FR/ES) is **[ASSUMED]**; Phase 30 confirms.

## Common Pitfalls

### Pitfall 1: The two runtimes disagree without NFC
**What goes wrong:** the JVM misses NFD text and Swift matches it, or `é` NFD contains `e` on the JVM only.
**Why it happens:** Swift `String` compares by canonical equivalence; the JVM compares UTF-16 units. [VERIFIED: probes — JVM `nfd.contains("guérir")=false`, Swift `true`]
**How to avoid:** NFC first on both; shared `selfTest.normalization` fixtures.
**Warning signs:** a fixture passes on one platform only.

### Pitfall 2: `ß` folding diverges
**What goes wrong:** Swift `.folding(.caseInsensitive)` returns `strasse`; JVM lowercase keeps `ß`. [VERIFIED: probes]
**How to avoid:** never use Swift case-insensitive folding APIs. Apply the explicit JSON replace pairs after `lowercase`. Store terms pre-folded and test it.

### Pitfall 3: Silent schema typos on iOS
**What goes wrong:** `"prefx": [...]` decodes fine and drops all prefix rules on iOS, while Android throws. [VERIFIED: Swift probe]
**How to avoid:** non-optional Codable fields plus a `JSONSerialization` exact key-set test; kotlinx strict default on Android; anti-vacuity counts per category.

### Pitfall 4: Gradle does not rerun the tests when only `l10n/` changes
**What goes wrong:** the glossary is read through `java.io.File` at test time, which is not a declared input, so `:app:testDebugUnitTest` stays UP-TO-DATE (or comes from the build cache) and stays green on a changed file.
**How to avoid:** `inputs.dir(<repo>/l10n)` on both test tasks. The policy resources are covered through the `Sync` task on the classpath.
**Warning signs:** `> Task :app:testDebugUnitTest UP-TO-DATE` after editing JSON.

### Pitfall 5: CI does not trigger on `l10n/**`
**What goes wrong:** `.github/workflows/android.yml` only triggers on Android paths, so a PR that edits only `l10n/` runs no Android tests. [VERIFIED: .github/workflows/android.yml:6-13 — `paths:` / `- 'android/**'` / `- '.github/workflows/android.yml'` for both `push` and `pull_request`]
**How to avoid:** add `- 'l10n/**'` to both path lists. (Out of scope: `main` has no `.github/` at all, so the iOS side has no CI to wire.)

### Pitfall 6: Walk-up path resolution reads another branch's files
**What goes wrong:** this worktree is nested in the main checkout. A walk-up that misses `l10n/` here continues to `/…/endo-app/l10n/`. [VERIFIED: `git worktree list`]
**How to avoid:** a repo-root system property (Gradle) and fixed `#filePath` depth (iOS). The tripwire excludes `.claude/` and nested `.git` dirs.

### Pitfall 7: Skip-instead-of-fail
**What goes wrong:** copying `assumeTrue(source != null)` from `ClaimsPolicyDriftTests` means a deleted or renamed `l10n` file makes the suite skip and stay green.
**How to avoid:** a missing `l10n` file is an assertion **failure**. Count scanned strings and assert a minimum (anti-vacuity).

### Pitfall 8: Stale iOS bundle
**What goes wrong:** `project.yml` was edited but `xcodegen generate` was not re-run, or JSON was edited after the build, so the app bundle carries old policy data.
**How to avoid:** a `BundledPolicyParityTests` test (bundle bytes == `#filePath` repo bytes, per language). On Android, the same test compares classpath bytes with the repo file.

### Pitfall 9: The two branches drift
**What goes wrong:** someone edits `l10n/` on the iOS branch to make a Swift test pass.
**How to avoid:** Android is the reference. iOS takes `l10n/` only through `git checkout claude/endo-android-version-e608a7 -- l10n`. Verification: `git diff --quiet claude/endo-android-version-e608a7 <ios-branch> -- l10n`. A data fix goes to Android first and is re-copied. Add a `policyRevision` integer to each file so a mismatch is visible in test output.

### Pitfall 10: Register lost by case folding
**What goes wrong:** DE `Sie`/`Ihnen` (formal) cannot be told apart from `sie`/`ihnen` once lowercased.
**How to avoid:** the `wordCased`/`wordCasedMidSentence` kinds run on the NFC-only view. Fixture: "Das könnten Sie mit Ihrem Behandlungsteam besprechen." → register violation; "Sie haben Schmerzen eingetragen?" at sentence start is ambiguous and passes `MidSentence`. Native review covers the residue.

### Pitfall 11: Allow-list becomes an escape hatch
**What goes wrong:** a translator allow-lists `behandlung` itself to get a sentence through.
**How to avoid:** a hygiene test (an allow token ≠ any rule term; `collidesWith` must really collide; reason required). Native-reviewer sign-off on every allow entry is a Phase 33 checklist item.

### Pitfall 12: Swift 6 global state
**What goes wrong:** `static var cache` produces "not concurrency-safe" compile errors under `SWIFT_VERSION: "6.0"`.
**How to avoid:** `static let` immutable dictionaries; all-`let` `Sendable` structs.

## Code Examples

### Existing pattern to mirror: red-capable non-vacuity control (Android)
```kotlin
// Source: android/app/src/test/kotlin/com/endoapp/app/content/CopyAuditTest.kt:95-111 (verbatim excerpt)
@Test
fun scanCatchesAForbiddenString() {
    val deliberatelyBad = listOf(
        "this will diagnose your flare",   // causal: "will", "diagnose"
        "you should avoid dairy",          // imperative: "should", "avoid"
        "a flare attack is imminent",      // alarmist: "attack", "imminent"
        "she logged her symptoms",         // gendered: "she", "her"
    )
```
[VERIFIED: read this session]

### Existing pattern to mirror: reuse-not-fork (Android)
`ClaimsPolicyReuseTest` asserts `assertSame(ForbiddenTerms.causal, ClaimsPolicy.causal)` [VERIFIED: android/app/src/test/kotlin/com/endoapp/app/content/ClaimsPolicyReuseTest.kt]. The new `ClaimsPolicyLocalizedReuseTest` (a new file, the old one untouched) asserts `assertSame(ForbiddenTerms.forLanguage(PolicyLanguage.DE), ClaimsPolicy.forLanguage(PolicyLanguage.DE))` for all three languages.

### Existing pattern to mirror: repo-root path on iOS
```swift
// Source: Tests/EndoAppTests/ReportNoNetworkTests.swift:21-24 (verbatim)
let projectRoot = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent()   // EndoAppTests/
    .deletingLastPathComponent()   // Tests/
    .deletingLastPathComponent()   // <project root>
```
[VERIFIED: read this session]

### English values that must remain byte-identical (SAFE-04 reference)
- Kotlin `ForbiddenTerms.imperative`: `"take", "stop", "avoid", "start", "should", "must", "use", "try", "cut",` [VERIFIED: android/insights/src/main/kotlin/com/endoapp/insights/ForbiddenTerms.kt:69-79]
- Swift `static let forbiddenVerbs: [String] = [` … `"diagnose", "detect", "treat", "cure", "prevent", "clinically validated", "clinically proven", "medical-grade", "trigger", "causes", "will",` [VERIFIED: Sources/EndoApp/Content/ClaimsPolicy.swift:27-39]
- Kotlin `ClaimsPolicy.forbiddenGenderedTokens`: `"she", "her", "women", "woman", "lady", "ladies",` [VERIFIED: android/app/src/main/kotlin/com/endoapp/app/content/ClaimsPolicy.kt:62-69]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| English substring scan for all copy | Per-language curated data + token rules + allow-lists | This phase | DE/FR/ES checked without false positives from EN lists |
| `CopyAuditRegistry` registration by memory ("load-bearing rule") | File-pattern registry + tripwire | This phase (extended in 29/30/33) | A new localized file cannot bypass the audit |

**Deprecated/outdated:** nothing is removed. The English mechanisms stay as they are (SAFE-04).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | All DE/FR/ES seed terms, collisions and fixtures | Seed Content | False negatives/positives; fixed by tuning in execution and by Phase 33 review. The phase's mechanism is unaffected. |
| A2 | Glossary approved/banned terms (≈30 concepts) and the choice "Einblicke"/"Hinweis auf einen möglichen Schub"/"intensité"/"intensidad" | Glossary seed | Translation wording later; low cost to change now, high cost after Phase 32 |
| A3 | AGP packages `:insights` jar Java resources into the APK and `getResourceAsStream` works on device | Pattern 5 | Runtime guard (Phase 28) cannot load the policy; verify with `unzip -l` on the debug APK in this phase |
| A4 | Swift general-category token rule equals JVM `isLetterOrDigit` for DE/FR/ES text | Pattern 1 | Token boundary mismatch; caught by the shared normalization/token fixtures |
| A5 | Exact Gradle Kotlin-DSL syntax for `Sync` + `srcDir(taskProvider)` on a `kotlin("jvm")` module | Pattern 5 | Build-script iteration; semantics are CITED |
| A6 | "pause" kept as an allow entry with `collidesWith: ["use"]` to satisfy SC2 literally | Pattern 2 | Wording only; the alternative is a pure mustPass control |
| A7 | `forLanguage` covers de/fr/es only (no EN adapter in Phase 26) | Open Questions | Phase 28/29 may want a uniform `forLanguage(en)`; add then as an adapter over the unchanged EN lists |
| A8 | Hosted `EndoAppTests` sees `Bundle.main` = app bundle | Pattern 5 | Use `Bundle(for:)`/a token type instead; the bundled-bytes test reveals it |
| A9 | Localized Apple Health app names per locale | Glossary seed | Glossary entry corrected in Phase 30 |

## Open Questions

1. **Should `forLanguage(.en)` exist?**
   - What we know: the EN lists must stay in code; ARCHITECTURE allows "leave EN out of the JSON altogether".
   - Recommendation: Phase 26 exposes de/fr/es only (`PolicyLanguage` without EN). Phase 28/29 adds an EN adapter that delegates to the existing lists if a uniform loop is needed.
2. **iOS branch logistics.**
   - What we know: there is no iOS branch yet. `main` (= `master`, dc5c27f) has no `android/` and no `.github/`. The iOS sources in this worktree equal `main` (`git diff main -- Sources Tests EndoInsights project.yml` is empty).
   - Recommendation: the iOS plans execute in a separate worktree on a new branch from `main`, e.g. `claude/endo-ios-l10n`. The GSD phase artifacts stay on the Android branch's `.planning/`. The iOS SUMMARY is written back here.
3. **Negated disclaimer phrases** ("nicht diagnostisch", "non diagnostique", "no diagnóstico") are needed for the Phase 31 PDF footer.
   - Recommendation: the schema supports `allow.phrases` now. Seed only what is certain, or leave them empty, and flag them for legal review. EN keeps its existing exemption-plus-must-contain approach unchanged.
4. **Spain vs LatAm terms** ("producto sanitario" vs "dispositivo médico", `vosotros`).
   - Recommendation: EU MDR wording now; README note; Phase 33 review.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | Android Gradle tests | ✓ | OpenJDK 21.0.2 | — |
| Gradle wrapper | Android tests | ✓ | (wrapper; `:insights:test` drift test ran green in ~40 s this session) | — |
| Xcode | iOS tests | ✓ | 27.2 (27B52) | — |
| Swift | iOS | ✓ | 6.4.2 | — |
| XcodeGen | regenerate project after `project.yml` edit | ✓ | 2.46.0 | — |
| iOS Simulator | `xcodebuild test` | ✓ | iPhone 16 / iPhone 17 Pro on iOS 26.5 | — |
| git (branches) | byte-identity check across branches | ✓ | branches `main`, `master`, `claude/endo-android-version-e608a7` | — |

**Missing dependencies with no fallback:** none.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Android `:insights`: JUnit Jupiter 6.1.1; Android `:app`: JUnit 4.13.2 (+ Robolectric not needed here); iOS: Swift Testing (`import Testing`) in `EndoAppTests` |
| Config file | `android/insights/build.gradle.kts`, `android/app/build.gradle.kts`, `project.yml` |
| Quick run (Android) | `cd android && ./gradlew :insights:test --tests 'com.endoapp.insights.l10n.*' :app:testDebugUnitTest --tests 'com.endoapp.app.l10n.*'` |
| Quick run (iOS) | `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppTests/LocalizedPolicySelfTests -only-testing:EndoAppTests/GlossaryConformanceTests -only-testing:EndoAppTests/L10nAuditTests -only-testing:EndoAppTests/BundledPolicyParityTests -only-testing:EndoAppTests/LocalizedPolicySchemaTests` |
| Full suite (Android) | `cd android && ./gradlew :insights:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` |
| Full suite (iOS) | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` (includes `EndoInsightsTests` via the scheme) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SAFE-01 | Every `mustFail` sentence (de/fr/es) yields exactly the expected `{category, term}` | unit (both) | `./gradlew :insights:test --tests '*LocalizedPolicySelfTests*'` / `-only-testing:EndoAppTests/LocalizedPolicySelfTests` | ❌ Wave 0 |
| SAFE-01 | All 5 SC1 categories non-empty per language; mustFail covers each | unit (both) | `*LocalizedPolicySchemaTests*` | ❌ |
| SAFE-01 / SC4 | Every registered DE/FR/ES string (glossary approved, framing) passes; synthetic bad source makes the audit report failure | unit (both) | `./gradlew :app:testDebugUnitTest --tests '*L10nAuditTest*'` / `L10nAuditTests` | ❌ |
| SC4 | Unknown localized file anywhere in repo → fail; seeded temp-dir case proves it | unit (both) | `*L10nTripwireTest*` (Android) / inside `L10nAuditTests` (iOS) | ❌ |
| SAFE-02 | `mustPass` host words (Behandlungsteam, entraînement, vorher, Pause, …) pass; normalization fixtures (NFD, ẞ, NBSP, ’) fold as expected | unit (both) | `*LocalizedPolicySelfTests*` | ❌ |
| SAFE-02 | Allow hygiene: not a rule term; `collidesWith` really collides; reason non-empty; terms pre-folded | unit (both) | `*LocalizedPolicySchemaTests*` | ❌ |
| SAFE-03 | Glossary: 4 languages per concept; approved passes policy + register; approved ∩ banned = ∅; EN triggers pass the EN scan | unit (both) | `./gradlew :app:testDebugUnitTest --tests '*GlossaryConformanceTest*'` / `GlossaryConformanceTests` | ❌ |
| SAFE-03 | Self-test pairs: pass / banned / wrong register (DE "Sie", FR "tu", ES "usted") all produce the expected verdict | unit (both) | same | ❌ |
| SAFE-01/02 | Bundled/classpath policy bytes == repo `l10n/` bytes | unit (both) | `*BundledPolicyParityTests*` | ❌ |
| SAFE-04 | English suites unchanged and green | regression (both) | Android full suite; iOS full suite; `git diff --exit-code <base> -- <EN file list>` | ✅ existing |
| SAFE-04 | `ClaimsPolicy.forLanguage(x)` is the same instance as `ForbiddenTerms.forLanguage(x)` | unit (Android) | `*ClaimsPolicyLocalizedReuseTest*` | ❌ |
| Cross-branch | `l10n/` byte-identical on both branches | manual/scripted gate | `git diff --quiet claude/endo-android-version-e608a7 <ios-branch> -- l10n && echo IDENTICAL` | n/a |

**What each test proves:** the self-tests prove the check is red-capable and precise per language. The schema tests prove the data cannot be silently vacuous or mistyped. The audit and tripwire prove nothing localized ships unscanned (SC4). The glossary tests prove term and register enforcement (SC3). The parity tests prove the runtime copy equals the reviewed file. The diff guard proves SAFE-04 holds by construction.

### Sampling Rate
- **Per task commit:** Android quick run (≈40–90 s), or iOS quick run on the iOS branch.
- **Per wave merge:** Android full suite; iOS full suite on the iOS branch.
- **Phase gate:** both full suites green, the SAFE-04 diff guard empty, the cross-branch `l10n/` diff empty, and one recorded manual red-proof per platform (temporarily add "erkennt Schübe" to a glossary DE approved form → audit red → revert).

### Wave 0 Gaps
- [ ] `l10n/claims-policy.{de,fr,es}.json`, `l10n/glossary.json`, `l10n/README.md`: data plus schema doc
- [ ] `android/insights/build.gradle.kts`: `Sync` resources task, `inputs.dir`, `endo.repoRoot` system property
- [ ] `android/app/build.gradle.kts`: `testOptions.unitTests.all { systemProperty; inputs.dir }`
- [ ] `.github/workflows/android.yml`: add `'l10n/**'` to the push and pull_request paths
- [ ] `project.yml` (iOS branch): `l10n` resources source entry, then `xcodegen generate`
- [ ] Test files listed in the map above (all new; no existing test edited)

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | — (no accounts) |
| V3 Session Management | no | — |
| V4 Access Control | no | — |
| V5 Input Validation | yes | Strict JSON decoding (kotlinx default strict; Swift key-set check); policy files are trusted build inputs, still schema-validated; fail-closed when missing/corrupt |
| V6 Cryptography | no | — |
| V14 Configuration / build integrity | yes | Bundled-bytes == repo-bytes tests; CI path filter includes `l10n/**`; Gradle test inputs declared |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Allow-list entry used to smuggle a forbidden claim | Tampering | Allow hygiene test (no rule term, verified `collidesWith`, reason); reviewer sign-off per entry (Phase 33) |
| Policy silently empty/mistyped → every string passes | Tampering / Elevation (bypass) | Exact key-set + non-empty category tests; fail on missing file (no `assumeTrue`) |
| Stale runtime copy differs from reviewed file | Repudiation (reviewed ≠ shipped) | Bundled/classpath parity tests per language |
| Branch divergence (Android vs iOS enforce different policy) | Tampering | Android-reference copy rule + cross-branch `git diff --quiet` gate + `policyRevision` |
| Translated claim reaches store/UI unscanned | Elevation (regulatory bypass) | Registry + tripwire; later phases register their sources |
| Test marks native/legal review as done | Repudiation | `reviewStatus` must stay `draft-unreviewed`; tests assert it, never set it (LEGL-03) |

## Sources

### Primary (HIGH confidence)
- Codebase, read this session: `android/insights/src/main/kotlin/com/endoapp/insights/ForbiddenTerms.kt`, `HonestyGate.kt`; `android/app/src/main/kotlin/com/endoapp/app/content/ClaimsPolicy.kt`, `AuditableCopy.kt`; `android/app/src/test/kotlin/com/endoapp/app/content/CopyAuditTest.kt`, `ClaimsPolicyReuseTest.kt`; `android/insights/src/test/kotlin/com/endoapp/insights/ClaimsPolicyDriftTests.kt`, `BoundaryLintTests.kt`; `android/insights/build.gradle.kts`, `android/app/build.gradle.kts`, `android/gradle/libs.versions.toml`, `.github/workflows/android.yml`; `Sources/EndoApp/Content/ClaimsPolicy.swift`; `Tests/EndoAppTests/CopyAuditTests.swift`, `ReportNoNetworkTests.swift`, `FlareCopyDriftTests.swift`; `project.yml`; `EndoInsights/Package.swift`
- Local runtime probes (JDK 21.0.2, Swift 6.4.2): NFC behavior, `ß`/`ẞ` folding, Turkish-locale trap, letter/space classes, `JSONDecoder` unknown-key behavior
- `.planning/research/{SUMMARY,ARCHITECTURE,FEATURES,PITFALLS}.md`, `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`, `.planning/STATE.md`

### Secondary (MEDIUM confidence)
- docs.gradle.org: `SourceDirectorySet.srcDir`, `Project.files` (Provider/task-output semantics)
- github.com/yonaskolb/XcodeGen Docs/ProjectSpec.md: target source `includes`, `buildPhase: resources`
- kotlinx.serialization docs/json.md (`ignoreUnknownKeys` default false)

### Tertiary (LOW confidence)
- DE/FR/ES forbidden-term seeds, collisions, glossary renderings: drafted from FEATURES.md plus analysis this session; native-speaker and legal review required (Phase 33)

## Recommended Plan Split and Wave Ordering

| Plan | Platform / branch | Wave | Content | Depends on |
|------|-------------------|------|---------|-----------|
| 26-01 | Shared data, Android branch | 1 | `l10n/` JSON (schema + seeds + selfTest) + `README.md`; CI path filter `l10n/**` | — |
| 26-02 | Android `:insights` | 2 | `PolicyLanguage`, `L10nText` (fold/tokenize), `LanguageClaimsPolicy` + matcher, `ForbiddenTerms.forLanguage` (extension, new file), Gradle `Sync` + `inputs.dir` + repo-root property; self/schema/bundled-parity tests (JUnit 5); data tuned until green | 26-01 |
| 26-03 | Android `:app` | 3 | `ClaimsPolicy.forLanguage` delegate + reuse test; `Glossary` model (in `:insights` main, pure) + conformance tests; `L10nSourceRegistry`, audit + tripwire tests; APK resource check; SAFE-04 diff guard. **Freeze `l10n/`** (`policyRevision`) | 26-02 |
| 26-04 | iOS branch from `main` | 4 | Copy `l10n/` (`git checkout <android-branch> -- l10n`); `project.yml` resources + `xcodegen generate`; Swift `L10nText`/models/matcher; `ClaimsPolicy+Localized.swift`; self/schema/bundled-parity tests | 26-03 (frozen data) |
| 26-05 | iOS branch | 5 | Swift glossary conformance + registry/audit/tripwire tests; full iOS suite; cross-branch `l10n/` identity check | 26-04 |

26-04 and 26-05 can merge into one plan if the planner prefers fewer plans. Under the `granularity: fine` config, keep them separate. **Do not run the iOS half in parallel with 26-02/26-03.** Data tuning happens during the Android waves, and a parallel copy would diverge (Pitfall 9). If a Swift parity failure shows a data bug, fix it on the Android branch, re-run Android, and re-copy.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. No new dependencies; the JDK/Foundation behavior was probed.
- Architecture: HIGH for placement and wiring (code read); MEDIUM for the Gradle/XcodeGen syntax (docs, not built).
- Matching spec: HIGH for the normalization/folding traps (probed); MEDIUM for the Swift token-category equivalence (fixture-proved in execution).
- Seed content: LOW. Draft, pending native and legal review.

**Research date:** 2026-10-31
**Valid until:** 2026-11-30 (stable platform APIs; seeds valid until Phase 33 review)
