---
phase: 07-correlation-trigger-discovery
plan: 02
subsystem: EndoInsights (the .correlation producer — factor binarization + correlation engine)
tags: [correlation, factor-model, fdr, effect-size, foundation-only, tdd, honesty-gate]
requires:
  - "CorrelationStats (Plan 01): cliffsDelta, meanDifference, riskDifference, riskRatio, fisherExactTwoTailed, mannWhitneyP, benjaminiHochbergPasses, confidence"
  - "CyclePhaseMapper.phaseWindows / phase(for:in:calendar:) (Phase 6, reused verbatim for the cycle-phase factor)"
  - "HonestyGate.minSample(for: .correlation) (single source of truth for the per-pair sample floor)"
  - "Insight.derived / InsightConfidence / ForbiddenTerms"
provides:
  - "Factor: three-state binarization (present/absent/unobserved) with named, unit-asserted thresholds"
  - "CorrelationEngine.insights(from:asOf:calendar:): the pure injected-today .correlation producer"
  - "Correlation fixtures: strong / under-sampled / spurious / multiple-comparison-flood / suppressed-user"
affects:
  - "07-03 (InsightsEngine composes CorrelationEngine.insights behind the HonestyGate; locks minSample 14→10)"
tech-stack:
  added: []
  patterns:
    - "Pure injected-today engine enum mirroring CyclePhaseMapper (no store/UI/clock/RNG)"
    - "Three-state factor binarization (unobserved excluded from both groups — absence ≠ 0)"
    - "BH-FDR computed ONCE over the full candidate set, never per-pair"
    - "Cycle-phase factor by construction: suppressed → [] windows → no cyclePhase pair tested (D-04)"
    - "Deterministic per-(signal,day) splitmix hash to build independent ~50% noise streams (flood fixture)"
    - "Top-N cap POST gate-eligibility — emit only top-5 with confidence ≥ .low"
key-files:
  created:
    - EndoInsights/Sources/EndoInsights/Factor.swift
    - EndoInsights/Sources/EndoInsights/CorrelationEngine.swift
  modified:
    - EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift
    - EndoInsights/Tests/EndoInsightsTests/CorrelationEngineTests.swift
decisions:
  - "Severities are Int? on SymptomPoint → explicitly compactMap non-nil + cast Double($0); nil severity excluded (absence ≠ 0)"
  - "Emit ONLY the top-5 gate-eligible survivors (confidence ≥ .low); below-gate (.none) pairs are NOT emitted at all — makes 'zero false positives' direct"
  - "Self-gate reads HonestyGate.minSample(for: .correlation) (currently 14) — no second magic number; Plan 03 lowers it to 10"
  - "Flood fixture uses independent per-signal splitmix hashes, NOT small modular toggles (which manufacture spurious correlations)"
metrics:
  duration: ~7 min
  completed: 2026-06-09
  tasks: 3
  files: 4
  tests: 10 new (83 total package tests GREEN)
---

# Phase 7 Plan 02: CorrelationEngine + Factor Model Summary

The `.correlation` producer (D-13) and its fixture proof: a three-state `Factor` binarization of `DaySnapshot` (with named, unit-asserted thresholds) and a pure injected-`today` `CorrelationEngine` that enumerates the bounded factor×symptom-or-bleeding pairs, computes the Plan-01 stats over paired/observed days, applies Benjamini–Hochberg FDR (q=0.10) ONCE over the full candidate set, maps to `InsightConfidence` via the three-AND gate, and emits the top-5 surviving `.correlation` Insights with honest `sampleSize`/confidence and hand-written non-causal copy. The deliverable is the SUPPRESSION: under-sampled, spurious, and multiple-comparison-flood inputs surface ZERO.

## What Was Built

- **`Factor.swift`** — `enum Factor` (food/activity-present/activity/sleep.poor/stress.high/mood.low/medication/prn/cyclePhase) + `FactorState { present, absent, unobserved }`. Three-state binarization mirroring `DaySnapshot`'s absence≠0 contract and `TrendsEngine.factorTrends`. `nil` context scales / `nil` sleepQuality / a truly-empty day → `.unobserved` (never coerced to 0); an empty food/med/activity on a day-with-any-log → `.absent` (A7). The cycle-phase factor takes the engine-supplied per-day `CyclePhase?` label (window logic stays in `CyclePhaseMapper`, D-04/D-17). Named constants `highStressThreshold = 7`, `lowMoodThreshold = 2`, `perGroupMinimum = 3` — the only judgement calls, asserted by a test.
- **`CorrelationEngine.swift`** — `enum CorrelationEngine` with one `static func insights(from:asOf:calendar:)` mirroring `CyclePhaseMapper.insights` exactly. Labels each day via `CyclePhaseMapper.phaseWindows`/`phase` (reused verbatim — suppressed/irregular → `[]` windows → no cyclePhase pair tested, D-04 by construction). Enumerates bounded pairs (food per `FoodTag`, activity present + per `ActivityType`, sleep/stress/mood/medication/prn, cyclePhase per `CyclePhase` only when labels exist) × each `SymptomType` (severity arm + presence arm) + bleeding (presence only, OQ3). Restricts to paired/observed days; drops a pair below `HonestyGate.minSample(for: .correlation)` or with either group < `perGroupMinimum`. Computes Cliff's δ + mean-diff + Mann–Whitney p (severity) or RD + RR + Fisher p (presence), runs `benjaminiHochbergPasses(_:q:0.10)` ONCE, maps each via `CorrelationStats.confidence`, sorts `(confidence desc, effectMag desc, pairedDays desc, signalKey asc)`, and emits ONLY the top-5 with `confidence ≥ .low`. Copy is hand-written non-causal ("possibly associated with" + the "— a descriptive co-occurrence only." disclaimer).
- **`SnapshotFixtures.swift`** — five new correlation fixtures on the existing `snapshot(offset:...)` builder: `strongAssociationFixture` (n=40), `underSampledAssociationFixture` (n=9), `spuriousAssociationFixture` (independent), `multipleComparisonFloodFixture` (K≈100+ pure noise), `suppressedUserCorrelationFixture`.
- **`CorrelationEngineTests.swift`** — 10 tests: 4 Factor (constants + three-state binarization), 6 engine (strong surfaced with effect/confidence/non-causal copy, under-sampled/spurious/flood each ZERO, suppressed user no cyclePhase correlation, deterministic).

## TDD Gate Compliance

- **Task 1 RED** (`b997ee6` preceding state): the constants + binarization tests were written first; the build failed ONLY because `Factor`/`CorrelationEngine` did not exist (`cannot find 'Factor' in scope`). **GREEN** `b997ee6` (`feat(07-02)`): `Factor.swift` lands; the 4 Factor tests pass.
- **Task 2 GREEN** `56942e0` (`feat(07-02)`): `CorrelationEngine.swift` compiles (`TEST BUILD SUCCEEDED`), self-gates against `HonestyGate.minSample(for: .correlation)`, runs BH-FDR once, Foundation-only.
- **Task 3** `03def4f` (`test(07-02)`): fixtures + the 6 engine fixture tests; all 10 `CorrelationEngineTests` pass, full 83-test package suite GREEN.

The `test(...)` (RED, the suite) and `feat(...)` (GREEN, the implementation) gate commits are both present; REFACTOR was not needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The multiple-comparison flood fixture manufactured real correlations**
- **Found during:** Task 3 (first full run — `multipleComparisonFlood_surfacesNothing` failed; the engine correctly surfaced 5 `.high` correlations).
- **Issue:** The initial flood fixture toggled each signal on a small modulus (`i % 2`, `i % 3`, `i % 5`). Those streams are NOT mutually independent — they overlap on a fixed integer lattice, so e.g. `pelvicPain` (logged when `i % 2 == 1`) and `dairy` (logged when `i % 2 == 0`) are perfectly anti-correlated, producing a genuine, FDR-surviving 2×2. The fixture was the bug, not the engine: the BH-FDR gate did exactly its job on data that was not actually noise.
- **Fix:** Replaced the modular toggles with an independent per-`(signal, day)` splitmix-style integer hash (`bit(salt, i)`), giving ~50% present rates that are mutually independent by construction — pure deterministic noise, no RNG. BH-FDR (q=0.10) now crushes all K≈100+ pairs → ZERO surfaced.
- **Files modified:** `SnapshotFixtures.swift`. **Commit:** `03def4f`.

### Note on the min-sample literal grep

The plan's verification suggested `grep -cE '\b14\b|\b10\b'` on the engine should show no bare min-sample literal. It matches twice — but both are legitimate and NOT min-sample magic numbers: `fdrQ = 0.10` (the BH FDR q-target) and `(rr * 10).rounded() / 10` (one-decimal rounding for the lift display copy). The sample floor reads `HonestyGate.minSample(for: .correlation)` exclusively (single source of truth, no second min-sample literal). No code change needed.

## Known Stubs

None. `Factor` and `CorrelationEngine` are fully implemented; every factor family binarizes to three states, every stat is computed from the proven `CorrelationStats` helpers, and nothing is hardcoded or placeholdered.

## Threat Flags

None. The plan's `<threat_model>` mitigations are all satisfied:
- **T-07-04** (false-positive shown as real): the three-AND hard gate + the load-bearing `multipleComparisonFloodFixture` (K≈100+ pure noise → ZERO surfaced) enforce it.
- **T-07-05** (causal claim reaches user): all emitted copy is hand-written non-causal prose with the "— a descriptive co-occurrence only." disclaimer; `ForbiddenTerms.violation` is asserted on every emitted headline + detail in `strongAssociation_isSurfaced`.
- **T-07-06** (health prose in signalKeys/logs): `signalKey` carries only category labels (`correlation.food.dairy.symptom.pelvicPain`); no logging of any value.
- **T-07-07** (cycle-phase leak for suppressed users): cycle-phase labels derive ONLY from `CyclePhaseMapper.phaseWindows` (`[]` under suppression) → `suppressedUser_noCyclePhaseCorrelation` enforces it.
- **T-07-08** (non-determinism): no RNG / `Date.now` / `UUID()`; sorted keys; `signalKey` total-order tiebreaker; `output_isDeterministic` enforces it.
- **T-07-SC**: zero external packages (Foundation-only).

## Verification

- `xcodebuild test … -only-testing:EndoInsightsTests/CorrelationEngineTests` → 10 tests GREEN (all six fixture behaviors + four Factor checks).
- Full `EndoInsightsTests` target → 83 tests in 10 suites GREEN (BoundaryLint included; no regression from the prior 73).
- Foundation-only: `grep "^import"` on both new source files → only `import Foundation`.
- Single source of truth: `grep -c "HonestyGate.minSample(for: .correlation)"` → matches (code reference present).
- BH-FDR once: `grep -c "benjaminiHochbergPasses"` → 1.
- Determinism: `grep -nE "Date\(\)|Date.now|UUID\(\)|random|print\("` on both source files → matches only comment prose, no code.

## Self-Check: PASSED
- FOUND: EndoInsights/Sources/EndoInsights/Factor.swift
- FOUND: EndoInsights/Sources/EndoInsights/CorrelationEngine.swift
- FOUND: EndoInsights/Tests/EndoInsightsTests/CorrelationEngineTests.swift
- FOUND commit: b997ee6 (Task 1 — Factor)
- FOUND commit: 56942e0 (Task 2 — CorrelationEngine)
- FOUND commit: 03def4f (Task 3 — fixtures + suite)
