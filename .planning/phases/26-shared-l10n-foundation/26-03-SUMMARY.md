---
phase: 26-shared-l10n-foundation
plan: 03
subsystem: l10n
tags: [l10n, claims-policy, glossary, register, tripwire, kotlin, junit4, app, insights]

requires:
  - phase: 26-02
    provides: "ForbiddenTerms.forLanguage(lang), L10nText, LanguageClaimsPolicy + RuleKinds predicates, mask(), violations(text, applyAllow)"
  - phase: 26-01
    provides: "l10n/claims-policy.{de,fr,es}.json, l10n/glossary.json, l10n/README.md, syncL10nPolicy classpath wiring"
provides:
  - "fun ClaimsPolicy.forLanguage(lang) in :app: one-line delegate to ForbiddenTerms.forLanguage (same instance, assertSame per language)"
  - "com.endoapp.insights.l10n.Glossary: strict model, registerViolations/registerViolation, conceptBannedViolations, conformance() -> GlossaryVerdict"
  - "L10nSourceRegistry (:app test): COPY sources for policy framing + glossary approved forms, DOCUMENTATION source for README, looksLocalized/localizedCandidates/unregisteredLocalizedFiles, one shared auditFindings helper"
  - "L10nAuditTest, GlossaryConformanceTest, AllowListHygieneTest, L10nTripwireTest, ClaimsPolicyLocalizedReuseTest (permanent regression net, SC4)"
  - ":app unit tests: endo.repoRoot system property + l10n/ and localized-file fileTree inputs"
  - "l10n/ frozen for the iOS copy (SHA-256 manifest + commit below)"
affects: [26-04, 26-05, 27, 28, 29, 30, 32, 33]

actuals:
  tokens: 15600
  tasks: 3
  commits: 4
plan_head_before: dcaf11739910c4840288cf151d8d4314c5a056d1
plan_head_after: 411463b076ac938a7e334f83106fdb0e7df9a44b

tech-stack:
  added: []
  patterns:
    - "Test-side source registry: a path pattern maps to an extractor (COPY) or an exemption with a reason (DOCUMENTATION); a tripwire fails on any localized-looking file no source claims"
    - "One audit helper for real scans and red controls; mutated temp copies of l10n/ are judged by their own glossary (same root)"
    - "Glossary banned rules reuse the policy's tokens/fold/mask and the RuleKinds predicates; cased kinds compare the NFC typography view exactly"
    - "Gradle test inputs: a repo-wide fileTree of localized-looking patterns, so a new translation file reruns cached unit tests"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/content/ClaimsPolicyLocalized.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/l10n/Glossary.kt
    - android/app/src/test/kotlin/com/endoapp/app/l10n/L10nSourceRegistry.kt
    - android/app/src/test/kotlin/com/endoapp/app/l10n/L10nAuditTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/l10n/ClaimsPolicyLocalizedReuseTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/l10n/GlossaryConformanceTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/l10n/AllowListHygieneTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/l10n/L10nTripwireTest.kt
  modified:
    - android/app/build.gradle.kts
    - l10n/glossary.json

key-decisions:
  - "Conformance order: register first, then the first banned hit over ALL triggered concepts (concept order, mask on), then the first triggered concept without an approved form (mask off). Banned outranks MissingApproved because it is the more severe finding"
  - "No data tuning was needed: every approved form, framing entry and allow entry passed on the first run; the only data change is the plan-required DE lowercase mid-sentence 'sie' pass pair. policyRevision stays 1 (seed tuning ends at this freeze)"
  - "Tripwire patterns are slightly wider than the plan list: any de/fr/es region .lproj (fr-CA.lproj, es_419.lproj) and any <tag>-<REGION>/<tag>-419 store directory, not only de-DE/fr-FR/es-ES/es-419. Android res dirs are limited to the 14 real resource types, so ordinary hyphenated directories cannot false-positive"
  - "SAFE-01..04 are not marked complete: they are both-platform requirements and the iOS half (26-04/26-05) is not written yet"

patterns-established:
  - "A later phase registers its localized source with ONE L10nSource line (pattern, role, reason, extractor); the tripwire forces it"
  - "Repo reads in :app tests only via endo.repoRoot, with an AssertionError naming the Gradle wiring; missing files fail, never skip"

requirements-completed: []

coverage:
  - id: D1
    description: "ClaimsPolicy.forLanguage(lang) returns the very same instance as ForbiddenTerms.forLanguage(lang) for DE, FR and ES"
    requirement: SAFE-01
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/l10n/ClaimsPolicyLocalizedReuseTest.kt (4 cases)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Every registered DE/FR/ES string (policy framing + glossary approved forms, >= 30 per language) passes its policy and the register; seeded, mutated-framing, mutated-glossary and register-break controls turn the same helper red"
    requirement: SAFE-01
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/l10n/L10nAuditTest.kt (5 cases)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Glossary enforced: strict decode, draft header, >= 30 concepts in 4 languages, approved ∩ banned = ∅, canonical terms, EN triggers clean, case-sensitive register (DE Sie/Ihrem/Ihnen fail, sentence-initial Sie and lowercase sie pass, FR tu, ES usted), every shared selfTest pair"
    requirement: SAFE-03
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/l10n/GlossaryConformanceTest.kt (13 cases)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Every allow entry has a non-blank reason and only real collisions (policy, register or EN list); passes with the allow applied; dead/unjustified/smuggling controls fail"
    requirement: SAFE-02
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/l10n/AllowListHygieneTest.kt (6 cases)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Any localized-looking file no source claims fails the tripwire; exclusions and nested checkouts honored; no dead registrations"
    requirement: SAFE-01
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/l10n/L10nTripwireTest.kt (5 cases)"
        status: pass
      - kind: other
        ref: "Live red proof: a planted android/app/src/main/res/values-de/strings.xml reran the cached test task and failed repoHasNoUnregisteredLocalizedFile; file removed"
        status: pass
    human_judgment: false
  - id: D6
    description: "Debug APK carries l10n/claims-policy.{de,fr,es}.json and no glossary.json"
    requirement: SAFE-01
    verification:
      - kind: other
        ref: "unzip -l app-debug.apk check (APK_L10N_OK)"
        status: pass
    human_judgment: false
  - id: D7
    description: "Zero diff in English claims files and tests since 7065d65; full Android suite green"
    requirement: SAFE-04
    verification:
      - kind: other
        ref: "git diff --exit-code 7065d65 -- <SAFE-04 list> (SAFE04_CLEAN); ./gradlew --offline :insights:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug"
        status: pass
    human_judgment: false
  - id: D8
    description: "DE/FR/ES glossary and policy wording is linguistically and legally appropriate"
    verification: []
    human_judgment: true
    rationale: "All l10n data stays draft-unreviewed until the Phase 33 native-speaker and legal review (LEGL-03)"

duration: 20min
completed: 2026-10-31
status: complete
---

# Phase 26 Plan 03: :app Wiring, Glossary Enforcement, Tripwire and l10n Freeze Summary

**`ClaimsPolicy.forLanguage` now returns the `:insights` policy instance itself, not a copy. A test-side `L10nSourceRegistry` feeds every DE/FR/ES string to one audit helper, which runs the policy check and the du/vous/tú register check. The glossary is enforced through a new `Glossary` matcher in `:insights`. Every allow entry has to show a real collision. A tripwire fails on any localized-looking file that no source claims. `l10n/` is frozen for the iOS copy.**

## Performance

- **Duration:** about 20 min (2026-10-31T14:29Z to 14:49Z)
- **Tasks:** 3/3 (Task 2 ran as TDD: a RED commit, then a GREEN commit)
- **Files:** 8 created, 2 modified

## Accomplishments

- **Delegate (SAFE-01/04):** `ClaimsPolicyLocalized.kt` adds a one-line extension. `ClaimsPolicy.kt` is untouched. `assertSame` passes for DE, FR and ES.
- **Registry and audit (SAFE-01):** COPY sources are the policy `framing` entries and the glossary `approved` forms. `doNotTranslate` brand names are excluded, and the reason is recorded. The audit sees at least 30 strings per language. One `auditFindings(strings, root)` helper runs the policy check plus the register check, using the glossary from the same root. It is red-capable in four ways:
  - seeded mustFail sentences (exactly 3 findings);
  - a mutated DE framing entry in a temp copy;
  - a mutated first DE approved form ("erkennt Schübe");
  - a mid-sentence "Sie".
- **Glossary (SAFE-03, Android half):** a strict `@Serializable` model. The seven banned kinds default to empty lists, and unknown keys still throw. The nullable pair fields are required keys.
  - `registerViolations` uses the language policy's tokens, fold and allow mask, plus the 26-02 `RuleKinds` predicates.
  - The cased kinds are matched exactly on the NFC view. `wordCasedMidSentence` skips sentence-initial tokens.
  - `conformance()` returns `Pass`, `Register`, `Banned` or `MissingApproved`.
  - All 12 shared selfTest pairs give their expected verdict.
- **Allow hygiene (SAFE-02):** every `collidesWith` term is a real hit:
  - DE `behandl`/`erkenn`/`erkannt` and FR `entraîn`/`entrain`: real policy hits.
  - FR `a t il` → `t`: a real register hit.
  - DE `pause` → `use` and `vorher` → `her`: real English-list hits.
  
  Every entry has a reason and passes once the allow is applied. Synthetic dead, unjustified and smuggling entries fail.
- **Tripwire (SC4):**
  - Detects Android res qualifiers (including `b+es+419`, `es-rUS`, `night-fr`), `.lproj`, `.xcstrings`, region store folders, `*.de.*`/`*-fr.*` file names and `l10n/**`.
  - Skips `.git`, `.planning`, `.claude`, build and tool directories, and nested checkouts.
  - The real repo has exactly the five l10n candidates, and all five are claimed.
  - Proven live: a planted `values-de/strings.xml` re-ran the cached test task and failed it.
- **Gradle:** `testOptions.unitTests.all { }` sets `endo.repoRoot` and declares `l10n/` plus a repo-wide `fileTree` of localized-looking patterns as test inputs.

## Task Commits

1. **Task 1 (tracer): ClaimsPolicy.forLanguage delegate and registered policy framing audit**: `53c853b` (feat). The tracer gate re-ran the verify with `--rerun` and it was green, so expansion went ahead.
2. **Task 2 RED: failing glossary conformance, register and allow-hygiene tests**: `3e70c3f` (test)
3. **Task 2 GREEN: glossary register and conformance matcher**: `4936fe7` (feat)
4. **Task 3: tripwire, APK check, SAFE-04 guard**: `411463b` (test)

## Freeze for iOS copy

`l10n/` is frozen at this commit. `git status --porcelain -- l10n` was empty after the Task 3 commit.

- **Freeze commit (HEAD at freeze):** `411463b076ac938a7e334f83106fdb0e7df9a44b`
- **Last commit that changed `l10n/`:** `3e70c3f3911ba149d0b81013c5ea74e37b7a50bc` (added the DE lowercase "sie" glossary pair)
- **`l10n` tree object:** `e54fe59035a253760b81a3eb0e02cb092a33e2f5`
- **policyRevision:** 1 in all four JSON files. The seed tuning ends here.

SHA-256 manifest (`shasum -a 256 l10n/*`):

```
e62bf92ed49760a3d0c587538482b3283e80b1807587faae2db0e00ad3b05501  l10n/README.md
0d3d4d8baa9531ca8704a0b2a8dc3c95208765a10558976bd652a9fdb5caf78f  l10n/claims-policy.de.json
5454976b59988ef51d8d89663b99bb8255b90221548d9e644e4b1a6303348c59  l10n/claims-policy.es.json
0f3763e9957ae16b2a7aec8dee5dced5c5d8736fbbb3f293364cfec8c542edbd  l10n/claims-policy.fr.json
2521e9dc450bc65b8933f0567c04e5838755971306f73751b8497acd85e29ab5  l10n/glossary.json
```

Copy command for iOS 26-04/26-05. Run it on the iOS branch and never edit `l10n/` there:

```bash
git checkout claude/endo-android-version-e608a7 -- l10n
```

**Rule from here on:** any later data change happens on `claude/endo-android-version-e608a7` first. It bumps `policyRevision` in the changed file(s), re-runs the Android suites (`:insights:test`, `:app:testDebugUnitTest`), records a new manifest, and is then re-copied to the iOS branch. 26-05 runs the cross-branch `git diff --quiet` identity gate against these bytes.

## Files Created/Modified

- `android/app/src/main/kotlin/com/endoapp/app/content/ClaimsPolicyLocalized.kt`: `fun ClaimsPolicy.forLanguage`.
- `android/insights/src/main/kotlin/com/endoapp/insights/l10n/Glossary.kt`: the glossary model, banned-rule evaluator, register check and conformance.
- `android/app/src/test/kotlin/com/endoapp/app/l10n/L10nSourceRegistry.kt`: sources, the claim/collect walk, `repoRoot()`, `glossary()`, `auditFindings`, and the tripwire predicate and walk.
- `android/app/src/test/kotlin/com/endoapp/app/l10n/L10nAuditTest.kt`: 5 cases.
- `android/app/src/test/kotlin/com/endoapp/app/l10n/ClaimsPolicyLocalizedReuseTest.kt`: 4 cases.
- `android/app/src/test/kotlin/com/endoapp/app/l10n/GlossaryConformanceTest.kt`: 13 cases.
- `android/app/src/test/kotlin/com/endoapp/app/l10n/AllowListHygieneTest.kt`: 6 cases.
- `android/app/src/test/kotlin/com/endoapp/app/l10n/L10nTripwireTest.kt`: 5 cases.
- `android/app/build.gradle.kts`: the unit-test `all { }` block with `endo.repoRoot` and the `l10nData`/`localizedFiles` inputs.
- `l10n/glossary.json`: one DE pass pair, "Deine Einträge sind gespeichert, du kannst sie später bearbeiten."

## Decisions Made

- **Conformance order.** The plan lists register, then banned, then missing-approved. I check banned across all triggered concepts before missing-approved for any concept, because a banned variant is the more severe and more useful finding. Every shared pair gives the same verdict whichever way this is read.
- **The new "sie" pair is neutral.** The plan's unit-test sentence "Heute hat sie Schmerzen." stays in a Kotlin test. The shared data pair uses "du kannst sie später bearbeiten" ("them"), because a gendered "she" sentence in shared fixtures would go against the inclusive-language policy.
- **The sentence-initial "Sie haben Schmerzen eingetragen?" pair already existed** from 26-01, so only the lowercase pair was added. The acceptance grep matches the existing line.
- **No data tuning.** Every glossary approved form, framing entry and allow entry was clean on the first run. `reviewStatus` was read, never written.

## Deviations from Plan

### Auto-fixed Issues

None. Additions within scope (Rule 2, extra non-vacuity and fail-closed coverage):
- `GlossaryConformanceTest`:
  - `missingPairKeyIsRejected`: the nullable keys are required.
  - `registerMaskingCanBeDisabled`.
  - `selfTestPinsGermanCaseSensitivity`: the shared data must keep both case edges.
  - A key-order assertion in `headerValues`.
  - A `MissingApproved` case and a conditional-banned case in `conformanceVerdicts`.
- `L10nAuditTest.audit_catchesRegisterBreak`: shows the register half of the shared helper is red-capable.
- `AllowListHygieneTest`: per-proof tests (english, register, policy) and a "still fails with allow applied" smuggling control.
- Tripwire patterns are a little wider than the plan list (see key-decisions), with temp-root cases for `es-rUS`, `night-fr`, `es-419.lproj` and `es-419` store folders.

The plan's Task 3 file list included `android/app/build.gradle.kts`. It needed no change in Task 3, because the Task 1 `fileTree` includes already cover every `looksLocalized` pattern.

## TDD Gate Compliance

- **RED** `3e70c3f`. `Glossary.kt` was committed with compiling stubs: `registerViolations` returned an empty list and `conformance` returned `Pass`. Run: `./gradlew --offline :app:testDebugUnitTest --tests 'com.endoapp.app.l10n.*'`, exit 1, 28 tests and 8 assertion failures. Target `GlossaryConformanceTest#selfTestPairsYieldExpectedVerdict`. The classifier returned `RED_EVIDENCE_OK` (`target_test_failed`, JUnit XML, report newer than the run start). The record is at `android/app/build/tdd/red-26-03-task2.json`, which is build output and not committed.
  - *Semantic assessment:* the target failed on its planned assertion. A selfTest banned pair expected `Banned(flare, anfall)` and got `Pass` from the stub. The other failures were the matching behavior assertions: register edges, conformance verdicts, the FR euphonic-t collision, and register-break detection. None were compile, discovery or fixture faults.
- **GREEN** `4936fe7`. The matcher implementation made all 33 `:app` l10n cases pass on the first run.
- **REFACTOR:** none needed.

## Verification

- `./gradlew --offline :insights:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`: **BUILD SUCCESSFUL**.
  - `:insights`: 26 classes, 240 tests, 0 failures, 0 skipped.
  - `:app`: 91 classes, 498 tests, 0 failures, 1 skipped. The skip is the pre-existing ignored `GoldenFixtureGenerator.generateGoldenFixture`, not an l10n test.
  - CopyAuditTest 10/10, ClaimsPolicyReuseTest 6/6, ManifestPermissionsTest 5/5.
  - lintDebug reported 0 errors.
- l10n `:app` classes, all with 0 failures and 0 skipped: L10nTripwireTest 5, GlossaryConformanceTest 13, AllowListHygieneTest 6, L10nAuditTest 5, ClaimsPolicyLocalizedReuseTest 4.
- APK check: `APK_L10N_OK`. The three policy files are present (6028/4058/4967 bytes) and there is no `glossary.json`.
- SAFE-04 guard: `SAFE04_CLEAN`. There is no INTERNET `uses-permission`; the word only appears in manifest comments.

## Known Stubs

None. The RED-phase stubs in `Glossary.kt` were replaced in the GREEN commit.

## User Setup Required

None.

## Next Phase Readiness

- iOS 26-04 copies `l10n/` with the command above and ports the 26-02 suites. 26-05 ports `Glossary`, the register and conformance checks, allow hygiene, the registry and the tripwire, using the same shared fixtures. The iOS key-set checks must use `JSONSerialization`.
- Phases 27, 29, 30 and 33 each register their localized source with one `L10nSource` line. The tripwire fails until they do.
- SAFE-01..04 stay open until the iOS half lands.

---
*Phase: 26-shared-l10n-foundation*
*Completed: 2026-10-31*

## Self-Check: PASSED

- Files: ClaimsPolicyLocalized.kt, Glossary.kt and the six :app l10n test files all FOUND
- Commits: 53c853b, 3e70c3f, 4936fe7, 411463b all FOUND on HEAD
- Freeze manifest matches `shasum -a 256 l10n/*` (MANIFEST_MATCH)
