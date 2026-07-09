---
phase: 17-insights-engine-port
plan: 07
subsystem: insights
tags: [kotlin, jvm, recommendations, flare, honesty-gate, copy-mirror, drift-guard, junit]

# Dependency graph
requires:
  - phase: 17-04
    provides: TrendsEngine (bucket/priorBucket/meanSeverity/direction) + CyclePhaseMapper (phaseWindows/phase/periodRuns) — the composed spine + proximity signals
  - phase: 17-06
    provides: CorrelationEngine.scoredCandidates → CorrelationFinding (signalKey/factorLabel/targetLabel/confidence/pairedDays/direction) + RecommendationDirection
provides:
  - RecommendationsEngine (object) — reframes correlation survivors into ≤3 direction-aware non-prescriptive .recommendation insights (dominant-per-symptom collapse)
  - FlareEngine (object) — calm, uncertainty-first flare early-warning; REQUIRED rising-severity spine + active triggers + cycle-phase proximity, bounded ≤ MEDIUM, withhold-by-default
  - RecommendationCopy / FlareCopyText — package-internal copy mirrors byte-identical to the app RecommendationsCopy / FlareCopy
  - RecommendationsDriftGuard / FlareDriftGuard — public copy forwarders for the Phase-18 byte-identity drift test
affects: [17-08, phase-18-android-app]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Composite producer: consume already-gated typed survivors (CorrelationFinding), re-frame only — re-derive NO raw stats (D-01)"
    - "Local named-constant mirror of a not-yet-ported HonestyGate threshold (flareMinSample=21), one literal, to be wired to HonestyGate in 17-08 (the CorrelationEngine.correlationMinSample precedent)"
    - "App↔package copy mirror + public *DriftGuard copy forwarders (byte-identity contract); ForbiddenTerms list forwarders deferred to the plan that lands ForbiddenTerms"

key-files:
  created:
    - android/insights/src/main/kotlin/com/endoapp/insights/RecommendationsEngine.kt
    - android/insights/src/main/kotlin/com/endoapp/insights/FlareEngine.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/RecommendationsEngineTests.kt
    - android/insights/src/test/kotlin/com/endoapp/insights/FlareEngineTests.kt
  modified:
    - android/insights/PARITY.md

key-decisions:
  - "ForbiddenTerms list forwarders on the DriftGuards (packageForbiddenImperatives/Causal/Alarmist) are OMITTED here — ForbiddenTerms lands in 17-08; adding them now would not compile. The copy forwarders (the plan's must_have) ship."
  - "The exhaustive label / copy-clean scans use inline banned-substring lists (the CorrelationEngineTests precedent) instead of ForbiddenTerms.violation(in:), which arrives in 17-08."
  - "flareMinSample=21 is a local const mirroring HonestyGate.minSample(.flare)==21, exactly like CorrelationEngine.correlationMinSample mirrors the correlation floor until HonestyGate is wired in 17-08."

patterns-established:
  - "Pattern 1: composite producers re-frame typed survivors, never re-derive statistics"
  - "Pattern 2: curated copy dodges forbidden substrings by wording ('more difficult days' not 'higher'; detail omits 'not a prediction' to dodge 'predict')"

requirements-completed: [AENG-02]

coverage:
  - id: D1
    description: "RecommendationsEngine reframes CorrelationEngine survivors into ≤3 direction-aware non-prescriptive .recommendation insights (dominant-per-symptom collapse; signalKey recommendation.<stem>)"
    requirement: AENG-02
    verification:
      - kind: unit
        ref: "android/insights/src/test/kotlin/com/endoapp/insights/RecommendationsEngineTests.kt (7 @Test)"
        status: pass
    human_judgment: false
  - id: D2
    description: "FlareEngine: REQUIRED rising-severity spine (no rise → []), triggers/phase alone never warn, bounded ≤ MEDIUM, sub-21 rise → one insufficiency notice, suppressed/flat → zero (WR-02/WR-03 pinned)"
    requirement: AENG-02
    verification:
      - kind: unit
        ref: "android/insights/src/test/kotlin/com/endoapp/insights/FlareEngineTests.kt (6 @Test)"
        status: pass
    human_judgment: false
  - id: D3
    description: "RecommendationCopy / FlareCopyText byte-identical copy mirrors + public RecommendationsDriftGuard / FlareDriftGuard copy forwarders for the Phase-18 drift test"
    requirement: AENG-02
    verification:
      - kind: unit
        ref: "RecommendationsEngineTests#everyProductionLabel_rendersCleanRecommendationCopy; FlareEngineTests#risingWithEvidence_surfacesOneBoundedWarning (forbidden-term scans)"
        status: pass
    human_judgment: false

# Metrics
duration: 30min
completed: 2026-07-09
status: complete
---

# Phase 17 Plan 07: RecommendationsEngine + FlareEngine Summary

**Two composite honesty producers ported to Kotlin — RecommendationsEngine (≤3 direction-aware, non-prescriptive discussion prompts collapsed per symptom) and FlareEngine (rising-spine-required, bounded ≤ MEDIUM, withhold-by-default flare early-warning) — each with a byte-identical copy mirror and a public DriftGuard copy shim; full :insights suite 133/133 green.**

## Performance

- **Duration:** ~30 min
- **Tasks:** 2 (both TDD)
- **Files created:** 4 (2 engines, 2 test suites)
- **Files modified:** 1 (PARITY.md)

## Accomplishments
- **RecommendationsEngine**: consumes `CorrelationEngine.scoredCandidates` survivors, recovers direction from the typed `CorrelationFinding.direction` (never parsed copy), collapses to the dominant recommendation per symptom target (WR-01 target-identity grouping key), caps at `topN=3`, and emits non-prescriptive `.recommendation` insights with `signalKey = recommendation.<correlation-stem>`. Suppressed / sparse / multiple-comparison-flood / under-sampled users yield ZERO.
- **FlareEngine**: composes three already-gated signals with NO re-derived stats — the REQUIRED rising-severity spine (`TrendsEngine.direction` over recent-vs-prior `.last7`, no rise → `[]`), +1 confidence notch per active aggravating `CorrelationEngine` trigger still logged on a recent `.last30` day, +1 per near-higher-symptom cycle phase (`CyclePhaseMapper` argmax) — CAP MEDIUM, floor LOW. Evidence self-gated on `flareMinSample=21`: a real sub-21 rise → ONE `isInsufficiencyNotice` card sharing `signalKey "flare.warning"`; suppressed/flat/insufficient → ZERO. WR-02 (cyclePhase omitted from active-trigger keys to avoid double-count) and WR-03 (proximity counts logged-severity days, not bare presence) both ported and pinned.
- **Copy mirrors + drift shims**: `RecommendationCopy` and `FlareCopyText` ported verbatim (byte-identical to the app copy), curated to dodge the alarmist/causal/imperative/gendered/numeric substring traps ("more difficult days" not "higher"; detail omits "not a prediction" to dodge "predict"). Public `RecommendationsDriftGuard` / `FlareDriftGuard` forward the copy strings for the Phase-18 byte-identity test.
- **Real test results**: `RecommendationsEngineTests` 7/7, `FlareEngineTests` 6/6, full `:insights` suite **133/133** (was 120; +13), zero failures/errors. PARITY ledger advanced 95 → 108 / 157.

## Task Commits

1. **Task 1: RecommendationsEngine + copy mirror + drift shim** — `5174670` (feat)
2. **Task 2: FlareEngine + copy mirror + drift shim** — `1398ecb` (feat)
3. **PARITY ledger 95 → 108** — `ed5c4a4` (docs)

_Both engine tasks were TDD; implementation and its ported spec landed together in one commit each (the Swift test is the spec)._

## Files Created/Modified
- `android/insights/.../RecommendationsEngine.kt` — RecommendationsEngine object + RecommendationCopy + RecommendationsDriftGuard
- `android/insights/.../FlareEngine.kt` — FlareEngine object + FlareCopyText + FlareDriftGuard
- `android/insights/.../test/.../RecommendationsEngineTests.kt` — 7 @Test cases
- `android/insights/.../test/.../FlareEngineTests.kt` — 6 @Test cases
- `android/insights/PARITY.md` — ledger row status/counts (95 → 108); 17-07 landed note

## Decisions Made
- **flareMinSample=21 as a local const** mirroring `HonestyGate.minSample(.flare)==21`, exactly like `CorrelationEngine.correlationMinSample` mirrors the correlation floor until `HonestyGate` is wired in 17-08. One literal, single source of truth, to be replaced by the gate call in 17-08.
- **DriftGuard copy forwarders only** — the Swift `RecommendationsDriftGuard`/`FlareDriftGuard` also forward `ForbiddenTerms.imperative`/`.causal`/`.alarmist`, but `ForbiddenTerms` does not exist in the Kotlin main source until 17-08. Adding those forwarders now would not compile. The copy forwarders (the plan's must_have for the Phase-18 byte-identity test) ship; the ForbiddenTerms forwarders are deferred to 17-08.

## Deviations from Plan

### Auto-fixed / adapted (Rule 3 — blocking, cross-plan dependency)

**1. [Rule 3 - Blocking] ForbiddenTerms list forwarders + scan deferred to 17-08**
- **Found during:** Tasks 1 & 2 (DriftGuard shims + exhaustive label audit)
- **Issue:** The Swift `RecommendationsDriftGuard`/`FlareDriftGuard` forward `ForbiddenTerms.{imperative,causal,alarmist}`, and the Swift `everyProductionLabel_*` test uses `ForbiddenTerms.violation(in:)`. `ForbiddenTerms` is not ported into `:insights` main until 17-08 (per the 17-08 plan, which owns `ForbiddenTerms.kt`), so referencing it here would not compile.
- **Fix:** Omitted the three `ForbiddenTerms.*` list forwarders from the Kotlin DriftGuards (copy forwarders — the plan's must_have — ship). The exhaustive-label and copy-clean scans use inline banned-substring lists (causal ∪ imperative for recommendations; causal ∪ imperative ∪ alarmist for flare) — the exact precedent already established by `CorrelationEngineTests` (which the 17-06 summary documents as intentional). Documented the deferral inline in both engines and in PARITY.md; 17-08 adds the ForbiddenTerms forwarders/scans.
- **Files modified:** RecommendationsEngine.kt, FlareEngine.kt, RecommendationsEngineTests.kt, FlareEngineTests.kt, PARITY.md
- **Verification:** `:insights:test` 133/133 green; both copy mirrors pass the inline forbidden-term scans.
- **Committed in:** `5174670`, `1398ecb`, `ed5c4a4`

---

**Total deviations:** 1 adaptation (cross-plan dependency — ForbiddenTerms lands in 17-08).
**Impact on plan:** No scope creep. The must_have copy mirrors + copy DriftGuard shims ship exactly as specified; the ForbiddenTerms forwarders are 17-08's own scope by the wave split.

## Issues Encountered
- **Working directory is the worktree, not the repo root.** The init/plan paths resolve under `/Users/hannessuhr/Projects/endo-app/.claude/worktrees/eager-wescoff-3b405c/` (`.planning/` and `android/` both live there); the top-level `/Users/hannessuhr/Projects/endo-app/.planning/` does not exist. Ran all Gradle + git from the worktree. No functional impact.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- **17-08 (ForbiddenTerms + HonestyGate + InsightsEngine)** can now compose all five producers (Trends + CyclePhase + Correlation + Recommendations + Flare) behind `HonestyGate.apply(...)`. When `HonestyGate` lands, wire `FlareEngine.flareMinSample` → `HonestyGate.minSample(.flare)` (single literal to replace) and add the `ForbiddenTerms.*` forwarders to `RecommendationsDriftGuard`/`FlareDriftGuard`.
- Copy mirrors are ready for the 17-08 `ForbiddenTerms` fail-closed scan and the Phase-18 app-side byte-identity drift tests.
- PARITY at 108/157; remaining: 17-08 (HonestyGate 16 + InsightsEngine 14 + Contract 7 + NumericDivergence DST half).

## Self-Check: PASSED

---
*Phase: 17-insights-engine-port*
*Completed: 2026-07-09*
