---
phase: 26-shared-l10n-foundation
plan: 01
subsystem: l10n
tags: [l10n, claims-policy, glossary, gradle, ci, json]

requires: []
provides:
  - "l10n/claims-policy.{de,fr,es}.json: 8 categories x 5 kinds, allow-list, framing, selfTest fixtures (schemaVersion 1, policyRevision 1)"
  - "l10n/glossary.json: 35 concepts x en/de/fr/es, register du/vous/tú, selfTest pairs (test-only)"
  - "l10n/README.md: normative cross-platform matching spec + schemas + translator/branch rules"
  - "syncL10nPolicy Gradle Sync task: policy files on the :insights main classpath at /l10n/claims-policy.<tag>.json"
  - ":insights test task: inputs.dir(l10n) + systemProperty endo.repoRoot"
  - "CI: android workflow triggers on l10n/**"
affects: [26-02, 26-03, 26-04, 26-05, 27, 29, 30, 33]

actuals:
  tokens: 12189
  tasks: 3
  commits: 4
plan_head_before: 1f5c2158acd33eb1ddf7d415b95198a01af6cc7b
plan_head_after: 725686ea06b418658c384e21c06380731c039278

tech-stack:
  added: []
  patterns:
    - "Repo-root shared data folder consumed by both platforms; Android is the reference branch"
    - "Gradle Sync task as resources srcDir to put repo-root data on the JVM classpath"
    - "Fixtures embedded in the data file (selfTest) so both platforms assert the same {category, term}"
    - "Invisible/combining/typographic fixture chars stored as JSON \\u escapes"

key-files:
  created:
    - l10n/claims-policy.de.json
    - l10n/claims-policy.fr.json
    - l10n/claims-policy.es.json
    - l10n/glossary.json
    - l10n/README.md
    - .planning/phases/26-shared-l10n-foundation/deferred-items.md
  modified:
    - android/insights/build.gradle.kts
    - .github/workflows/android.yml

key-decisions:
  - "DE certainty uses stems vorhersag/vorhergesag/vorherseh (not vorher), so allow token vorher is a pure EN-trap control (collidesWith her) and never equals a DE rule term"
  - "Every selfTest mustFail expected {category, term} was checked against a scratch Python reference matcher implementing the README spec (all 35 fixtures + 25 mustPass + 11 glossary pairs green); 26-02 Kotlin matcher must reproduce these"
  - "Glossary FR register pair uses 'Tu peux en parler avec un professionnel de santé.' so the register hit is its only failure (clinician approved form present)"
  - "SAFE-01..03 not marked complete in REQUIREMENTS.md: this plan delivers data only; the failing tests that satisfy them land in 26-02/26-03"

patterns-established:
  - "Policy JSON top-level key order: schemaVersion, policyRevision, language, reviewStatus, folding, categories, allow, framing, selfTest"
  - "Allow entries carry {token|phrase, reason, collidesWith}; collidesWith names the real rule or EN code-list term inside the token"

requirements-completed: []

coverage:
  - id: D1
    description: "Three policy files with exact schema, 8x5 kinds, non-empty SC1 categories, mustFail per category, draft-unreviewed"
    requirement: SAFE-01
    verification:
      - kind: other
        ref: "Task 2 python3 structural check (L10N_DATA_OK)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Only the three policy files reach the :insights classpath under /l10n/, byte-identical; glossary/README excluded"
    requirement: SAFE-01
    verification:
      - kind: other
        ref: "./gradlew --offline -q :insights:processResources && cmp x3 (CLASSPATH3_OK) + glossary/README absence"
        status: pass
    human_judgment: false
  - id: D3
    description: "Glossary with 35 concepts, register du/vous/tú, pass/banned/register pairs per language"
    requirement: SAFE-03
    verification:
      - kind: other
        ref: "Task 2 python3 structural check (L10N_DATA_OK)"
        status: pass
    human_judgment: false
  - id: D4
    description: "CI android workflow triggers on l10n/** for push and pull_request"
    verification:
      - kind: other
        ref: "Task 3 grep check (CI_README_OK)"
        status: pass
    human_judgment: false
  - id: D5
    description: "DE/FR/ES seed rule and glossary wording are linguistically appropriate"
    verification: []
    human_judgment: true
    rationale: "Seed content is LOW-confidence draft; native-speaker and legal review is scheduled for Phase 33 (LEGL-03)"

duration: 9min
completed: 2026-10-31
status: complete
---

# Phase 26 Plan 01: Shared l10n Data Foundation Summary

**DE/FR/ES claims-policy JSON (8 categories x 5 rule kinds, allow-lists, framing, embedded self-test fixtures), a 35-concept glossary with du/vous/tú register, a normative matching-spec README, a Gradle Sync task that puts only the policy files on the :insights classpath, and a CI trigger on l10n/\*\*.**

## Performance

- **Duration:** about 9 min
- **Started:** 2026-10-31T14:03:34Z
- **Completed:** 2026-10-31T14:12Z
- **Tasks:** 3
- **Files modified:** 8 (5 l10n data/docs, 2 build/CI, 1 deferred-items log)

## Accomplishments

- German, French and Spanish policy files seeded from RESEARCH, all lowercase and pre-folded. Each has every category in the fixed order, a mustFail case per category (13 DE, 11 FR, 11 ES), and at least one uppercase and one NFD mustFail. DE also has ẞ and NBSP mustFail cases, and FR has NNBSP ones. The mustPass host words are covered.
- Glossary: 35 concepts with EN triggers and de/fr/es approved and banned forms; register block (DE wordCased/wordCasedMidSentence, FR tu-forms, ES usted); 11 self-test pairs covering pass, banned and register in every language.
- Tracer proven: `:insights:processResources` places `claims-policy.{de,fr,es}.json` at `/l10n/` byte-identical; glossary.json and README.md never reach the classpath. `:insights:test` declares `l10n/` as input and receives `endo.repoRoot`.
- README states the normative matching spec (typography view, root-locale fold, token categories, masking, kinds, fixed order) that 26-02 (Kotlin) and 26-04/05 (Swift) implement.

## Task Commits

1. **Task 1 (tracer): German policy file reaches the :insights runtime classpath** - `a977ab6` (feat)
2. **Task 2: French and Spanish policy files plus the shared glossary** - `717e426` (feat)
3. **Task 3: Normative README and CI path filter for l10n/\*\*** - `32092f1` (docs)
4. **Deviation fix: non-deprecated task registration** - `725686e` (fix)

## Files Created/Modified

- `l10n/claims-policy.de.json` - DE rules (ß→ss folding), 9 allow tokens, framing, selfTest
- `l10n/claims-policy.fr.json` - FR rules, entraînement allow tokens, allow phrase `a t il`, selfTest
- `l10n/claims-policy.es.json` - ES rules incl. causal phrase `se debe a`, empty allow lists, selfTest
- `l10n/glossary.json` - concepts, register, doNotTranslate, selfTest pairs (test-only)
- `l10n/README.md` - 8 sections: Purpose, Matching spec, Policy file schema, Glossary schema, Translator rules, Registering a new string source, Branch rule, Review status
- `android/insights/build.gradle.kts` - `syncL10nPolicy` Sync task, `sourceSets.main.resources.srcDir`, test inputs + `endo.repoRoot`
- `.github/workflows/android.yml` - `'l10n/**'` in push and pull_request paths, header clause
- `.planning/phases/26-shared-l10n-foundation/deferred-items.md` - pre-existing `:app` Gradle deprecation

## Decisions Made

- I checked every fixture's first-hit `{category, term}` with a scratch Python reference matcher. It is not committed: it lives in the session scratchpad and only implements the README spec. It also checked the schema rules: pre-folded terms, token shape, allow hygiene, framing and approved forms clean under their own policy and register, EN triggers clean under the EN code lists, and escapes. All checks passed with 0 errors. Some cases depend on rule order, and 26-02 must reproduce them:
  - DE "Du solltest … vermeiden." expects `vermeid`, not `sollt`.
  - FR `soulagée` and ES `aliviada` are shadowed by the treatment prefixes `soulag` and `alivi`, which are evaluated before gendered.
  - FR `réduisez` hits causal `rédui` before imperative.
  - ES "Un diagnóstico preciso." expects `diagnós`, because NFD input becomes `ó` after NFC.
- The DE `vorher` allow entry declares `collidesWith: ["her"]`. It is an EN code-list term, not a DE rule, and the README spells out this EN-trap allowance.
- I did not mark SAFE-01..03 complete. The requirements say tests fail on a hit, and those tests arrive in 26-02/26-03.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Deprecated Gradle delegate syntax for the Sync task**
- **Found during:** overall verification (`--warning-mode all`)
- **Issue:** `val syncL10nPolicy by tasks.registering(Sync::class)` is deprecated in Gradle 9.6.1 and scheduled for removal in Gradle 10.
- **Fix:** `val syncL10nPolicy = tasks.register<Sync>("syncL10nPolicy") { ... }`. Output is identical.
- **Files modified:** android/insights/build.gradle.kts
- **Verification:** clean `:insights:clean :insights:processResources` shows no :insights deprecation; CLASSPATH3_OK; `:insights:test` green
- **Committed in:** `725686e`

**2. [Rule 3 - Blocking] Write tool decodes `\u` escapes into raw characters**
- **Found during:** Task 1
- **Issue:** The plan requires combining marks, ẞ, NBSP/NNBSP and U+2019 to be stored as JSON `\u` escapes. The Write tool wrote them as raw characters, so the DE file was not NFC.
- **Fix:** A scratch post-processor rewrites exactly those characters as `\uXXXX` and asserts the parsed JSON is unchanged. I ran it on all four JSON files.
- **Files modified:** l10n/*.json (before each task commit)
- **Verification:** the reference checker reports no raw special characters and NFC file text for every file

---

**Total deviations:** 2 auto-fixed (1 Rule 1, 1 Rule 3)
**Impact on plan:** No schema or scope change.

## Issues Encountered

- A pre-existing `:app` Gradle deprecation (Project-object dependency notation) is logged in deferred-items.md and was not fixed (out of scope).

## Verification

- Task 1: CLASSPATH_OK; key list exact; `syncL10nPolicy` count 2; `endo.repoRoot` inside `tasks.test`; `useJUnitPlatform()` kept
- Task 2: L10N_DATA_OK; CLASSPATH3_OK; `"a t il"` 1, `"se debe a"` 2; four files `draft-unreviewed`
- Task 3: CI_README_OK; `'l10n/**'` at workflow lines 10 (push) and 15 (pull_request); 8 README sections in order
- `./gradlew --offline :insights:test`: BUILD SUCCESSFUL
- SAFE-04 guard: SAFE04_CLEAN (zero diff in every English list/test file)

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 26-02 can load `/l10n/claims-policy.<tag>.json` from the classpath and resolve the repo root from `endo.repoRoot`. Its matcher must reproduce the embedded selfTest fixtures. The scratch reference matcher shows the data is internally consistent with the README spec.
- All data stays `draft-unreviewed` (LOW confidence) until the Phase 33 native and legal review.

---
*Phase: 26-shared-l10n-foundation*
*Completed: 2026-10-31*

## Self-Check: PASSED

- Files: l10n/claims-policy.{de,fr,es}.json, l10n/glossary.json, l10n/README.md all FOUND
- Commits: a977ab6, 717e426, 32092f1, 725686e all FOUND on HEAD
