---
phase: 17-insights-engine-port
plan: 08
subsystem: insights
tags: [kotlin, jvm, honesty-gate, forbidden-terms, insights-engine, claims-drift, dst, parity, junit]

# Dependency graph
requires:
  - phase: 17-04
    provides: TrendsEngine + CyclePhaseMapper (the composed spine + proximity signals)
  - phase: 17-06
    provides: CorrelationEngine (factor↔symptom associations + typed survivors)
  - phase: 17-07
    provides: RecommendationsEngine + FlareEngine (composite producers) + deferred ForbiddenTerms forwarders / local minSample mirror consts
provides:
  - ForbiddenTerms (object) — causal/imperative/alarmist substring policy + violation(text)
  - HonestyGate (object) — the single choke point: per-kind minSample table, confidence floor with the typed .insufficient carve-out, dismissal exclusion, fail-closed forbidden-term filter (apply = loud+drop, applyReleaseScan = silent drop)
  - InsightsEngine (object) — the ONE public producer composing all five producers behind HonestyGate.apply
  - ClaimsPolicyDriftTests — cross-platform drift guard scanning the Swift ClaimsPolicy source (AENG-05)
  - NumericDivergenceTests DST/non-UTC section — LocalDate purity across UTC/Berlin/Apia (AENG-06)
  - PARITY.md reconciled to exactly 157 Swift-mirror cases (AENG-03, phase exit)
affects: [phase-18-android-app]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single choke point: InsightsEngine composes five producers → HonestyGate.apply — the one mandatory ethical filter"
    - "Fail-closed forbidden-term filter: apply() makes a violation LOUD (error) + drops; applyReleaseScan() drops silently (the durable release guarantee a test can assert without throwing)"
    - "Single source of truth: the deferred local mirror consts (correlationMinSample, flareMinSample) now read HonestyGate.minSample(...) — the producer self-gates at the SAME floor the gate enforces"
    - "Assumption-guarded cross-platform drift scan: regex-extract the Swift ClaimsPolicy lists, assumeTrue-skip outside the monorepo"
    - "Zone-invariance proof: TimeZone.setDefault(UTC/Berlin/Apia) around each engine call (restored in finally) → byte-identical output proves no epoch/Instant math leaked past LocalDate"

key-files:
  created:
    - android/insights/src/main/kotlin/com/endoapp/insights/ForbiddenTerms.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/HonestyGate.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/InsightsEngine.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/HonestyGateTests.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/ClaimsPolicyDriftTests.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/InsightsEngineTests.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/ContractTests.kt
  modified:
    - android/insights/src/main/kotlin/com/endoapp/insights/CorrelationEngine.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/FlareEngine.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/RecommendationsEngine.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/NumericDivergenceTests.kt
    - android/insights/PARITY.md

key-decisions:
  - "ClaimsPolicy causal drift asserted by SEMANTIC catch (ForbiddenTerms.violation(verb) != null) rather than literal set-subset — the package mirrors app verbs by STEM ('causes' caught by 'cause') so exact-set containsAll would be falsely RED on the un-drifted state. The three app-chrome marketing phrases (clinically validated/proven, medical-grade) are a documented carve-out: they are audited app-side and can never appear in templated insight copy."
  - "apply() uses error(...) as the JVM analogue of Swift's debug assertionFailure (loud copy-bug signal + drop); applyReleaseScan() drops silently so the release fail-closed guarantee is testable without throwing."
  - "The deferred local mirror consts and ForbiddenTerms DriftGuard forwarders from 17-06/17-07 are now wired to the real HonestyGate/ForbiddenTerms — single source of truth."

patterns-established:
  - "Pattern: the honesty gate is a genuine SUPERSET filter — producers self-gate at HonestyGate.minSample(kind) so the gate never count-drops a valid producer output (no-regression, Pitfall 1)"

requirements-completed: [AENG-02, AENG-03, AENG-05, AENG-06]

coverage:
  - id: D1
    description: "InsightsEngine composes all five producers behind the single HonestyGate.apply; composition is a superset of raw trends; dismissal idempotent; deterministic; public-boundary flood → zero"
    requirement: AENG-02
    verification:
      - kind: unit
        ref: "android/insights/src/test/kotlin/com/endoapp/insights/InsightsEngineTests.kt (14 @Test) + ContractTests.kt (7 @Test)"
        status: pass
    human_judgment: false
  - id: D2
    description: "HonestyGate withholds dismissed / below-floor / under-sampled insights and fail-closed-drops any surviving forbidden-term copy; ForbiddenTerms ported verbatim; zero-forbidden-terms scan over every fixture"
    requirement: AENG-02
    verification:
      - kind: unit
        ref: "android/insights/src/test/kotlin/com/endoapp/insights/HonestyGateTests.kt (16 @Test)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Cross-platform ClaimsPolicy drift is RED-capable: app forbiddenVerbs all caught by ForbiddenTerms.causal, imperative byte-equal, alarmist set-equal — scanned against the real Swift source (not skipped)"
    requirement: AENG-05
    verification:
      - kind: unit
        ref: "android/insights/src/test/kotlin/com/endoapp/insights/ClaimsPolicyDriftTests.kt (3 @Test, ran against Sources/EndoApp/Content/ClaimsPolicy.swift)"
        status: pass
    human_judgment: false
  - id: D4
    description: "DST/non-UTC fixture proves engine outputs are byte-identical across UTC + Europe/Berlin + Pacific/Apia (LocalDate purity — no epoch/Instant math leaked)"
    requirement: AENG-06
    verification:
      - kind: unit
        ref: "android/insights/src/test/kotlin/com/endoapp/insights/NumericDivergenceTests.kt DST section (3 @Test: Trends, CyclePhase, InsightsEngine composition)"
        status: pass
    human_judgment: false
  - id: D5
    description: "PARITY.md reconciled to exactly 157 Swift-mirror cases (every Kotlin count == Swift count); the 4/5 guard tests tracked separately; full suite green"
    requirement: AENG-03
    verification:
      - kind: build
        ref: "cd android && ./gradlew clean :insights:test :insights:check → 176 testcases, 0 failures (157 mirror + 19 guard)"
        status: pass
    human_judgment: false

# Metrics
duration: 45min
completed: 2026-07-09
status: complete
---

# Phase 17 Plan 08: ForbiddenTerms + HonestyGate + InsightsEngine (Capstone) Summary

**The ethical choke point and single public producer landed — ForbiddenTerms, HonestyGate, and InsightsEngine (the ONE producer composing all five engines behind one gate) — plus the ClaimsPolicy cross-platform drift scan (red against the real Swift source), the DST/non-UTC LocalDate-purity proof, and the PARITY.md ledger reconciled to exactly 157 Swift-mirror cases; full `:insights` suite 176/176 green (157 mirror + 19 guard).**

## Performance

- **Duration:** ~45 min
- **Tasks:** 3 (all TDD — the Swift test is the spec)
- **Files created:** 7 (3 engines/policy, 4 test suites)
- **Files modified:** 5 (3 producers wired to the gate, NumericDivergenceTests, PARITY.md)

## Accomplishments

- **ForbiddenTerms** ported verbatim: the three case-insensitive substring lists — `causal` (21: the app forbidden-verb mirror by stem + the D-08 causal connectives), `imperative` (9), `alarmist` (14) — and `violation(text)` scanning causal → imperative → alarmist.
- **HonestyGate** ported: the per-kind `minSample` table (trend 3, cyclePhase 2, correlation 10, recommendation 10, flare 21), the confidence floor (`LOW`) with the typed `isInsufficiencyNotice` carve-out, dismissal exclusion, the LIVE min-sample backstop that never count-drops a valid producer output (no-regression, Pitfall 1), and the fail-closed forbidden-term filter. `apply` makes a violation LOUD via `error(...)` (the JVM analogue of Swift's debug `assertionFailure`) AND drops; `applyReleaseScan` drops silently so the durable release guarantee is testable.
- **InsightsEngine** — the ONE public producer: `insights(from, asOf, dismissed = emptySet())` composes TrendsEngine + CyclePhaseMapper + CorrelationEngine + RecommendationsEngine + FlareEngine, then `HonestyGate.apply(...)`. The injected Swift `Calendar` is dropped (LocalDate identity). This is the stable contract Phase 18's SnapshotProjector targets.
- **Single source of truth wiring:** the deferred local mirror consts from prior waves (`CorrelationEngine.correlationMinSample`, `FlareEngine.flareMinSample`) now read `HonestyGate.minSample(...)`, and the deferred `ForbiddenTerms.*` list forwarders were added to `RecommendationsDriftGuard`/`FlareDriftGuard`.
- **ClaimsPolicyDriftTests** (AENG-05, NEW guard, no Swift mirror): assumption-guarded regex scan of the read-only `Sources/EndoApp/Content/ClaimsPolicy.swift` — app `forbiddenVerbs` all caught by the `ForbiddenTerms.causal` superset, `forbiddenImperatives` byte-equal, `forbiddenAlarmist` set-equal. Ran against the real Swift source (3/3, not skipped).
- **NumericDivergenceTests DST/non-UTC section** (AENG-06): TrendsEngine, CyclePhaseMapper, and the full InsightsEngine composition produce byte-identical output whether the JVM default zone is UTC, Europe/Berlin (DST), or Pacific/Apia (date-line) — proving no `Instant`/epoch math leaked past the LocalDate day identity. Compared against non-empty output (not vacuous).
- **PARITY.md reconciled to 157:** every Kotlin Swift-mirror count now equals its Swift count; the 18 mirror rows sum to exactly 157; the 19 guard tests are tracked separately (157 + 19 = 176, the full JUnit XML testcase count).

## Real Test Results (FULL suite)

`cd android && ./gradlew clean :insights:test :insights:check` → **BUILD SUCCESSFUL**.

- **176 testcases, 0 failures / 0 errors / 0 skipped.**
- **157 Swift-mirror cases** (the 18 PARITY rows): SmokeTests 2, BoundaryLintTests 1, ChartScrubSelectionTests 7, ContractTests 7, CorrelationEngineTests 12, CorrelationStatsTests 26, CyclePhaseMapperTests 8, ExploreSeriesCatalogTests 9, ExploreSeriesExtractionTests 10, FlareEngineTests 6, HonestyGateTests 16, InsightCodableTests 6, InsightsEngineTests 14, RecommendationsEngineTests 7, ScatterPairingTests 7, SeriesNormalizationTests 6, SuppressedFixtureTests 2, TrendsEngineTests 11 = **157**.
- **19 guard cases** (no Swift mirror): ClaimsPolicyDriftTests 3, EnumRawValueParityTests 2, NumericDivergenceTests 10, SpecialFunctionsGoldenTests 3, VersionCatalogAuditTests 1.
- New this plan: HonestyGateTests 16, InsightsEngineTests 14, ContractTests 7, ClaimsPolicyDriftTests 3, NumericDivergenceTests +3 (DST) = +43 over the prior 133.

## Task Commits

1. **Task 1: ForbiddenTerms + HonestyGate + InsightsEngine + ClaimsPolicy drift scan** — `e5181f9` (feat)
2. **Task 2: InsightsEngine composition acceptance + ContractTests** — `3df39ec` (test)
3. **Task 3: DST/non-UTC divergence completion + PARITY reconciled to 157** — `ef04d0a` (test)

_All three tasks were TDD; the ported Swift test is the spec, so implementation and its spec landed together._

## Files Created/Modified

- `.../ForbiddenTerms.kt` — causal/imperative/alarmist lists + `violation(text)` (NEW)
- `.../HonestyGate.kt` — the single choke point (NEW)
- `.../InsightsEngine.kt` — the ONE public producer (NEW)
- `.../test/.../HonestyGateTests.kt` — 16 @Test (NEW)
- `.../test/.../ClaimsPolicyDriftTests.kt` — 3 @Test drift guard (NEW)
- `.../test/.../InsightsEngineTests.kt` — 14 @Test (NEW)
- `.../test/.../ContractTests.kt` — 7 @Test (NEW)
- `.../CorrelationEngine.kt`, `.../FlareEngine.kt`, `.../RecommendationsEngine.kt` — wired local consts + DriftGuard forwarders to HonestyGate/ForbiddenTerms
- `.../test/.../NumericDivergenceTests.kt` — DST/non-UTC section (+3 @Test)
- `.../PARITY.md` — reconciled to 157 (Kotlin == Swift on every row)

## Decisions Made

- **ClaimsPolicy causal drift is asserted by SEMANTIC catch, not literal set-subset.** The package mirrors the app's causal verbs by STEM (the app's `causes` is caught by the package's `cause`; the app's `will` by `will`), so a literal `causal.containsAll(forbiddenVerbs)` would be falsely RED on the current, un-drifted state. The meaningful, TRUE, red-capable assertion is: for each app forbidden verb, `ForbiddenTerms.violation(verb) != null` (the package would catch copy containing it). The three multi-word app-chrome marketing phrases (`clinically validated`, `clinically proven`, `medical-grade`) are a single documented carve-out — they are audited app-side by CopyAuditTests and can never appear in the engine's templated insight copy, so the package deliberately does not mirror them. Any OTHER new app verb the package fails to catch is a RED drift.
- **`error(...)` as the debug-assert analogue.** Swift's `apply` traps in debug and drops in release; the JVM has no build split, so `apply` uses `error(...)` (loud copy-bug signal) and `applyReleaseScan` provides the silent-drop path a test uses to prove the durable release fail-closed guarantee.

## Deviations from Plan

### Auto-fixed / adapted

**1. [Rule 3 - Blocking-adjacent] ClaimsPolicy causal drift assertion is semantic-catch, not literal set-subset**
- **Found during:** Task 1 (writing ClaimsPolicyDriftTests).
- **Issue:** The plan/RESEARCH phrase the causal drift as `ForbiddenTerms.causal ⊇ app forbiddenVerbs` (literal set-superset). That is factually FALSE on the current sources — the package mirrors app verbs by stem (`cause` vs the app's `causes`) and omits three multi-word marketing phrases, so a literal `containsAll` would fail RED on the un-drifted state (a broken test).
- **Fix:** Asserted the only TRUE, meaningful, red-capable reading — the package CATCHES every app forbidden verb via `ForbiddenTerms.violation(verb) != null`, with a documented carve-out for the three app-chrome marketing phrases. Imperative byte-equal and alarmist set-equal hold exactly as specified.
- **Files:** ClaimsPolicyDriftTests.kt
- **Verification:** 3/3 green, ran against the real Swift source (not skipped).
- **Committed in:** `e5181f9`

**2. [Rule 3 - Blocking] Wired prior-wave deferred forwarders/consts (cross-plan dependency closure)**
- **Found during:** Task 1.
- **Issue:** 17-06/17-07 left `correlationMinSample`/`flareMinSample` as local `const` mirrors and omitted the `ForbiddenTerms.*` DriftGuard forwarders because ForbiddenTerms did not yet exist. This plan owns ForbiddenTerms/HonestyGate, so the single-source-of-truth wiring is due now.
- **Fix:** Changed the two consts to `val = HonestyGate.minSample(...)` and added `packageForbiddenCausal/Imperative/Alarmist` forwarders to the DriftGuards.
- **Files:** CorrelationEngine.kt, FlareEngine.kt, RecommendationsEngine.kt
- **Verification:** full suite 176/176 green.
- **Committed in:** `e5181f9`

**Total deviations:** 2 adaptations. No scope creep; no architectural changes.

## Known Stubs

None. All producers wired; no placeholder data. The DST tests compare non-empty engine output (regularCycle → trend+cyclePhase, risingFlare → flare, strongAssociation → correlation+recommendation), so the zone-invariance assertions are not vacuous.

## Issues Encountered

- **STATE.md carries an unstaged modification** made by the spawning execute-phase orchestrator (its own position bookkeeping, timestamped at orchestrator start). Per the objective ("Do NOT update STATE.md or ROADMAP.md") it was left untouched and excluded from all three task commits.
- **Working directory is the worktree** (`/Users/hannessuhr/Projects/endo-app/.claude/worktrees/eager-wescoff-3b405c/`); `.planning/` and `android/` both resolve there. No functional impact.

## User Setup Required
None — no external service configuration required.

## Next Phase Readiness
- **AENG-02 closed:** InsightsEngine is the single public producer behind the single HonestyGate; the public signature `insights(from, asOf, dismissed)` is LOCKED for Phase 18's SnapshotProjector.
- **AENG-05 closed:** enum + ClaimsPolicy cross-platform drift are both red-capable and green against the live Swift sources.
- **AENG-06 closed:** the stats-tolerance, boundary, and DST/non-UTC divergence halves of NumericDivergenceTests are all in place; LocalDate purity is proven.
- **AENG-03 closed (phase exit):** PARITY.md reaches exactly 157 with every Kotlin count matching its Swift count; the `:insights` engine is provably equivalent to the Swift EndoInsights engine.
- Manual-only remaining (17-VALIDATION): CI merge-gate green on a real push (host-gated).

## Self-Check: PASSED

All 7 created files exist on disk; all 3 task commits (`e5181f9`, `3df39ec`, `ef04d0a`) are present in git history; the full `:insights:test :insights:check` suite is green (176/176) with PARITY.md at 157.

---
*Phase: 17-insights-engine-port*
*Completed: 2026-07-09*
