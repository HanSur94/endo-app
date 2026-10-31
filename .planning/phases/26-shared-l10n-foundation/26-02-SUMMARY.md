---
phase: 26-shared-l10n-foundation
plan: 02
subsystem: l10n
tags: [l10n, claims-policy, kotlin, kotlinx-serialization, junit5, insights]

requires:
  - phase: 26-01
    provides: "l10n/claims-policy.{de,fr,es}.json seed data with selfTest fixtures, README matching spec, syncL10nPolicy classpath wiring, endo.repoRoot test property"
provides:
  - "com.endoapp.insights.l10n: PolicyLanguage (DE/FR/ES), L10nText (typography/fold/tokenize), LanguageClaimsPolicy strict model, RuleKinds predicates, Violation"
  - "fun ForbiddenTerms.forLanguage(lang): lazy, cached, fail-closed classpath loader (extension in a new file; ForbiddenTerms.kt untouched)"
  - "LocalizedPolicySelfTests: every shared fixture verdict for DE/FR/ES, also under tr-TR default locale"
  - "LocalizedPolicySchemaTests: key sets, header, pre-folded terms, shapes, anti-vacuity, allow structure, strict-decode and fail-closed proofs"
  - "BundledPolicyParityTests: classpath bytes == repo l10n bytes per language, fail (never skip)"
  - "README: policyRevision stays 1 through Phase 26 seed tuning, bumped from the 26-03 freeze onward"
affects: [26-03, 26-04, 26-05, 28, 30, 33]

actuals:
  tokens: 12028
  tasks: 3
  commits: 3
plan_head_before: eb980bed66d5fbc128cb5ffb7c5d68895ce928e3
plan_head_after: 3d638ada1bbbfd1e5fa13f63fe4734e08c6276ae

tech-stack:
  added: []
  patterns:
    - "Fail-closed lazy map of all languages: one broken policy file fails every lookup"
    - "All-required @Serializable fields + default strict Json = unknown and missing keys both reject"
    - "Schema tests walk the raw JSON (parseToJsonElement) for exact ordered key sets, mirroring the iOS JSONSerialization check"
    - "Strict-decode rejection tests carry a valid-rebuild control so the throw is attributable to the mutation"
    - "Check helpers return problem lists, with a synthetic broken-policy control proving the check is not vacuous"

key-files:
  created:
    - android/insights/src/main/kotlin/com/endoapp/insights/l10n/PolicyLanguage.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/l10n/L10nText.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/l10n/LanguageClaimsPolicy.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/l10n/ForbiddenTermsLocalized.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/l10n/LocalizedPolicySelfTests.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/l10n/LocalizedPolicySchemaTests.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/l10n/BundledPolicyParityTests.kt
  modified:
    - l10n/README.md

key-decisions:
  - "No seed-data tuning was needed in 26-02: every DE/FR/ES fixture was green against the Kotlin matcher on first run (data pre-checked by the 26-01 reference matcher); policyRevision stays 1, reviewStatus untouched"
  - "README clarified (wording only): policyRevision stays 1 through the Phase 26 seed tuning and is bumped on every data change from the 26-03 freeze onward"
  - "Schema tests also enforce the README file rules (NFC, LF, \\u escapes for invisible/confusable characters) and that each mustFail expected term is a real rule of its category"

patterns-established:
  - "Per-language JUnit @ParameterizedTest @EnumSource(PolicyLanguage::class) for every l10n data test"
  - "Repo-root reads only via System property endo.repoRoot, with an explicit fail() naming the Gradle wiring"

requirements-completed: [SAFE-01, SAFE-02, SAFE-04]

coverage:
  - id: D1
    description: "ForbiddenTerms.forLanguage(DE|FR|ES) loads the classpath policy, is cached and fails closed"
    requirement: SAFE-01
    verification:
      - kind: unit
        ref: "android/insights/src/test/kotlin/com/endoapp/insights/l10n/LocalizedPolicySelfTests.kt#forLanguageIsCached; LocalizedPolicySchemaTests#missingResourceFailsClosed, #wrongLanguageFailsClosed"
        status: pass
    human_judgment: false
  - id: D2
    description: "Every seeded mustFail sentence in DE/FR/ES yields its expected {category, term} first hit, also under tr-TR"
    requirement: SAFE-01
    verification:
      - kind: unit
        ref: "LocalizedPolicySelfTests#mustFailYieldsExpectedFirstHit, #verdictsIgnoreDefaultLocale, #everyCategoryHasAMustFail, #frenchVerdicts, #spanishVerdicts, #germanTracerVerdict"
        status: pass
    human_judgment: false
  - id: D3
    description: "Host words and everyday phrases pass; NFD, ẞ, NBSP/NNBSP and U+2019 fold to the fixture NFC form"
    requirement: SAFE-02
    verification:
      - kind: unit
        ref: "LocalizedPolicySelfTests#mustPassHasNoViolation, #normalizationFixturesFold, #tokenizeFixturesMatch"
        status: pass
    human_judgment: false
  - id: D4
    description: "Policy data cannot be silently vacuous, mistyped or stale (key sets, strict decode, pre-folded terms, allow hygiene, byte parity)"
    requirement: SAFE-01
    verification:
      - kind: unit
        ref: "LocalizedPolicySchemaTests (32 cases); BundledPolicyParityTests (4 cases)"
        status: pass
    human_judgment: false
  - id: D5
    description: "English ForbiddenTerms/HonestyGate and drift tests untouched; full :insights suite green"
    requirement: SAFE-04
    verification:
      - kind: other
        ref: "git diff --exit-code 7065d65 -- <SAFE-04 file list> && ./gradlew --offline :insights:test"
        status: pass
    human_judgment: false

duration: 12min
completed: 2026-10-31
status: complete
---

# Phase 26 Plan 02: DE/FR/ES Claims Matcher and Proof Suites Summary

**`ForbiddenTerms.forLanguage(lang)` gives a strict, fail-closed DE/FR/ES claims matcher in pure-JVM `:insights`. It is the reference implementation of the `l10n/README.md` spec. Three suites prove it: shared-fixture verdicts, schema/strict-decode checks, and byte parity between the classpath copy and the repo copy. None of the seed data needed tuning.**

## Performance

- **Duration:** about 12 min across two executor sessions. Task 1 ran in the first session, which the user stopped. Tasks 2-3 ran in this resumed session.
- **Completed:** 2026-10-31
- **Tasks:** 3/3
- **Files:** 7 created, 1 modified

## Accomplishments

- Four main files in `com.endoapp.insights.l10n`:
  - `PolicyLanguage`
  - `L10nText`: NFC typography, a `Locale.ROOT` fold and a code-point tokenizer.
  - `LanguageClaimsPolicy`: an all-required strict model, the `RuleKinds` predicates and fixed-order `violation()`/`violations()`.
  - `ForbiddenTermsLocalized`: the lazy, cached loader. It loads all three languages together and fails closed.
- Self tests now run over all three languages (28 cases). Every mustFail, mustPass, normalization and tokenize fixture gives the expected result, also with the default locale set to tr-TR. The plan's FR/ES behaviors are pinned as explicit tests.
- Schema tests (32 cases):
  - exact ordered key sets on the raw JSON
  - header values
  - NFC, LF and `\u`-escape file rules
  - pre-folded terms, with a control case using a capitalized term
  - term shapes and no duplicates
  - anti-vacuity
  - allow hygiene
  - strict-decode rejections: misspelled key, missing kind, missing category, wrong type
  - fail-closed loads for a missing resource and for a wrong language
- Parity tests (4 cases): the classpath bytes equal the repo `l10n/` bytes. A missing property, a wrong root or a missing file fails the test instead of skipping it. A tampered classpath copy was checked and turned the ES case red. The glossary and README are not bundled.
- Full `:insights` suite: 26 classes, 0 failures, 0 skipped. The English guards are byte-unchanged (SAFE04_CLEAN, L10N_STRICT_OK).

## Task Commits

1. **Task 1 (tracer): German end to end through ForbiddenTerms.forLanguage(DE)**: `8d86690` (feat). Committed by the previous executor.
2. **Task 2: French and Spanish verdicts green, seed data tuned**: `1a6bb56` (test)
3. **Task 3: Schema, strict-decode, fail-closed and classpath-parity tests**: `3d638ad` (test)

## Files Created/Modified

- `android/insights/src/main/kotlin/com/endoapp/insights/l10n/PolicyLanguage.kt`: the DE/FR/ES enum and `fromTag`.
- `android/insights/src/main/kotlin/com/endoapp/insights/l10n/L10nText.kt`: README spec steps 1-3.
- `android/insights/src/main/kotlin/com/endoapp/insights/l10n/LanguageClaimsPolicy.kt`: the strict model, masking, the `RuleKinds` predicates and the matcher.
- `android/insights/src/main/kotlin/com/endoapp/insights/l10n/ForbiddenTermsLocalized.kt`: `loadLanguagePolicy` and `ForbiddenTerms.forLanguage`.
- `android/insights/src/test/kotlin/com/endoapp/insights/l10n/LocalizedPolicySelfTests.kt`: shared-fixture verdicts for all languages.
- `android/insights/src/test/kotlin/com/endoapp/insights/l10n/LocalizedPolicySchemaTests.kt`: schema, strict-decode and fail-closed proofs.
- `android/insights/src/test/kotlin/com/endoapp/insights/l10n/BundledPolicyParityTests.kt`: byte parity between the classpath and the repo.
- `l10n/README.md`: clarifies when `policyRevision` is bumped. Wording only.

## Decisions Made

- **No data tuning.** Widening the self tests to FR/ES gave all green on the first run, so no rule was reordered and no term or allow entry changed. `policyRevision` stays 1 and `reviewStatus` is untouched. The rule-order-dependent fixtures listed in the 26-01 notes behave as described there: DE vermeid, FR rédui, ES diagnós, and FR soulagée / ES aliviada caught by the treatment prefixes.
- **README `policyRevision` wording.** The schema table and the Branch rule both said "bumped on every data change". That conflicted with the plan's rule that the revision stays 1 during Phase 26 tuning. Both places now say it is bumped from the 26-03 freeze onward. No behavior changed.
- **Two schema checks beyond the plan.** Every mustFail expected term must be a real rule of its category, and the file must follow the README's NFC/LF/escape rules. Both come from the README's list of rules the tests enforce.

## Deviations from Plan

### Auto-fixed Issues

None. Additions within scope:
- **[Rule 2 - Critical] Extra fail-closed and non-vacuity coverage in Task 3:**
  - `missingCategoryIsRejected`, `wrongTypeIsRejected` and `wrongLanguageFailsClosed`.
  - An `unmodifiedRebuildDecodes` control.
  - A `preFoldedCheckCatchesCapitalizedTerm` control.
  
  These close T-26-06 and T-26-07 more tightly and show the rejection tests are not vacuous. Commit `3d638ad`.
- **Task 2 explicit behavior tests:** `frenchVerdicts`, `spanishVerdicts` and `everyCategoryHasAMustFail` pin the plan's `<behavior>` bullets directly, so they do not depend only on fixture content. Commit `1a6bb56`.

The uncommitted change left by the stopped executor (removing the DE-only `names = ["DE"]` filter in 7 places) matched Task 2. I kept it as-is and included it in `1a6bb56`.

## TDD Gate Compliance

Both TDD tasks went green on the first run. The matcher (Task 1) and the 26-01 data, pre-checked with the reference matcher, already met the spec. To show the new tests can fail:
- The parity test turned red when one byte was appended to the classpath ES copy, with resource processing skipped. The copy was then restored and checked with `cmp`.
- A synthetic capitalized term was reported by the pre-fold check.
- The mutated JSON (renamed key, removed kind or category, wrong types) threw SerializationException.

## Issues Encountered

- The first self-test run reported UP-TO-DATE because the previous session had already run the widened tests. I re-ran with `--rerun` to get a fresh verdict.
- One expected `kind` in my new FR test was wrong: FR `entraîn` is a `prefix` rule, not a `stem` rule. I fixed the test assertion. The data was not changed.

## Known Stubs

None.

## User Setup Required

None. No external service configuration is required.

## Next Phase Readiness

- 26-03 can delegate `ClaimsPolicy.forLanguage` to `ForbiddenTerms.forLanguage`. It can reuse the `RuleKinds` predicates for the glossary and call `violations(text, applyAllow = false)` for the allow-collision proof. The real-collision proof for `collidesWith` (AllowListHygieneTest) is still 26-03's job.
- 26-04 (Swift) can port the self, schema and parity suites fixture for fixture. Its key-set checks must use JSONSerialization.
- The untracked `.planning/state.json` predates this session and was left alone.

---
*Phase: 26-shared-l10n-foundation*
*Completed: 2026-10-31*

## Self-Check: PASSED
