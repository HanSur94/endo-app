---
phase: 07-correlation-trigger-discovery
verified: 2026-06-09T09:30:00Z
status: passed
score: 3/3 success criteria verified (10/10 plan must-have truths verified)
overrides_applied: 0
re_verification:
  previous_status: issues_found (07-REVIEW.md — 1 Critical + 4 Warnings)
  note: "07-REVIEW.md is a code review, not a prior VERIFICATION.md. All review findings re-checked in source + behaviorally."
  gaps_closed:
    - "CR-01: duplicate-day collapse (no crash from public InsightsEngine)"
    - "WR-01: minSample(.correlation)=10 single source of truth (no second literal)"
    - "WR-02: severity arm counts DAYS not observations (independence preserved)"
    - "WR-03: Mann–Whitney doc corrected (no continuity correction; matches scipy use_continuity=False)"
    - "WR-04: both engine maps use uniquingKeysWith (duplicate-tolerant idiom unified)"
  gaps_remaining: []
  regressions: []
---

# Phase 7: Correlation & Trigger Discovery — Verification Report

**Phase Goal:** Discover correlations/triggers across logged factors with statistical rigor (min-sample, effect size, multiple-comparison awareness) routed entirely through the honesty layer.
**Verified:** 2026-06-09T09:30:00Z
**Status:** passed
**Re-verification:** Yes — after the 07-REVIEW.md adversarial code review (1 Critical + 4 Warnings); all findings confirmed FIXED in source and behaviorally.

This is a PURE-LOGIC, package-only phase (EndoInsights, Foundation-only). User-visible rendering is explicitly Phase 8 (ROADMAP, REQUIREMENTS.md INST-03 row). The phase goal is engine-level discovery routed through the honesty layer — verified against the actual source and a live run of the package test suite (90 tests, 10 suites, all GREEN). The phase is `Mode: mvp`, but the ROADMAP goal is a capability statement (not an "As a..." User Story); the three ROADMAP Success Criteria are the contract and were verified directly.

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| #   | Truth (Success Criterion)                                                                                                              | Status     | Evidence |
| --- | -------------------------------------------------------------------------------------------------------------------------------------- | ---------- | -------- |
| SC1 | The engine surfaces correlations/triggers across logged factors using the user's own data (effect-size + rate methods; same-day; cycle phase included). | ✓ VERIFIED | `CorrelationEngine.swift` enumerates the bounded factor families (food/activity/sleep/stress/mood/medication/prn + cycle-phase) × every `SymptomType` (severity arm: Cliff's δ + mean-diff + Mann–Whitney; presence arm: RD + RR + Fisher) + bleeding presence. Cycle-phase factor derived from `CyclePhaseMapper.phaseWindows` (lines 60-67, 138-173). Surfaced through the public `InsightsEngine.insights` (InsightsEngine.swift:38). Behavioral: `strongAssociation_isSurfaced` PASSED (exactly one `.correlation` at ≥.medium, signalKey `correlation.food.dairy.symptom.pelvicPain`); `composition_surfacesCorrelation_overStrongFixture` PASSED through the public engine. |
| SC2 | No correlation below the min-sample threshold; every shown finding carries effect size + plain-language confidence.                     | ✓ VERIFIED | `passesSampleFloor` (CorrelationEngine.swift:310-314) requires `pairedDays ≥ HonestyGate.minSample(.correlation)==10` AND each group ≥ `Factor.perGroupMinimum==3`. `confidence(...)` returns `.none` unless `passedFDR && effectMag≥mediumFloor && n≥minN` (CorrelationStats.swift:200). Emitted copy carries effect (mean-diff "points higher" / "×as often" lift) + confidence band. Behavioral: `underSampled_isWithheld` (n=9) PASSED → ZERO; `gate_withholdsUnderSampledCorrelation` PASSED. |
| SC3 | Spurious/low-confidence correlations are WITHHELD (not shown); shown correlations use non-causal "possible association" framing.        | ✓ VERIFIED | Three-AND gate (n & each group≥3, medium+ effect, BH-FDR q=0.10 over the full K set ONCE) → `.none` → `HonestyGate` withholds (steps b/c/d). Behavioral: `spurious_isWithheld` PASSED → ZERO; `multipleComparisonFlood_surfacesNothing` (K≈100+ pure noise via splitmix-hash independence) PASSED → ZERO; `gate_withholdsSpuriousCorrelation` + `gate_withholdsMultipleComparisonFlood` PASSED through the public engine. Non-causal copy: `allEmittedInsights_haveZeroForbiddenTerms` (now covers correlation fixtures) PASSED. |

**Score:** 3/3 ROADMAP Success Criteria verified. 10/10 plan must-have truths (across 07-01/02/03) verified.

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `EndoInsights/Sources/EndoInsights/CorrelationStats.swift` | Pure Foundation-only stats (Cliff's δ, mean-diff, risk diff/ratio, Fisher exact, Mann–Whitney p, normalCDF, BH-FDR, confidence band) | ✓ VERIFIED | `enum CorrelationStats`; 206 lines; lgamma bound to `(Double)->Double` scalar (line 23); erf-based normalCDF; BH step-up once; confidence floors as PARAMETERS incl. `minN` (WR-01 fix). Foundation-only. |
| `EndoInsights/Sources/EndoInsights/Factor.swift` | Three-state binarization with named, unit-asserted thresholds | ✓ VERIFIED | `enum Factor` + `FactorState{present/absent/unobserved}`; named `highStressThreshold=7`/`lowMoodThreshold=2`/`perGroupMinimum=3`; nil never coerced to 0; cycle-phase factor via engine-supplied label. |
| `EndoInsights/Sources/EndoInsights/CorrelationEngine.swift` | The pure injected-today `.correlation` producer | ✓ VERIFIED | `enum CorrelationEngine`; CR-01 duplicate-day collapse at entry (lines 56-58); BH-FDR called ONCE (line 101); self-gates off `HonestyGate.minSample(.correlation)` (line 109); top-N=5 over gate-eligible; non-causal copy. |
| `EndoInsights/Sources/EndoInsights/HonestyGate.swift` | minSample(.correlation)=10 (single source of truth) | ✓ VERIFIED | `case .correlation: return 10` (line 37); doc updated to "LOCKED — D-14/A1". Steps (a)-(d) unchanged; fail-closed ForbiddenTerms drop. |
| `EndoInsights/Sources/EndoInsights/InsightsEngine.swift` | Composition of CorrelationEngine through the gate | ✓ VERIFIED | `+ CorrelationEngine.insights(...)` additive term (line 38); public signature `(from:asOf:dismissed:calendar:)` unchanged; routed through `HonestyGate.apply` (line 39). |
| `CorrelationStatsTests.swift` / `CorrelationEngineTests.swift` / `SnapshotFixtures.swift` (5 fixtures) / extended `HonestyGateTests` + `InsightsEngineTests` | Known-value + fixture proof suites | ✓ VERIFIED | All present and substantive. Fixtures encode real scenarios (flood uses splitmix-hash independence, not correlated `i%2`/`i%3` lattice). |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| CorrelationEngine | CorrelationStats | cliffsDelta/riskDifference/fisherExactTwoTailed/mannWhitneyP/benjaminiHochbergPasses/confidence | ✓ WIRED | All six called in engine (lines 100-112, 243-287). |
| CorrelationEngine (cycle-phase) | CyclePhaseMapper.phaseWindows/phase | verbatim reuse — suppressed → [] windows → no phase factor | ✓ WIRED | `cyclePhaseLabels` (lines 143-173); suppressed short-circuit line 153. |
| CorrelationEngine | Insight.derived | emit .correlation, signalKey, sampleSize=pairedDays, non-causal copy | ✓ WIRED | `makeInsight()` (lines 373-382). |
| CorrelationEngine self-gate | HonestyGate.minSample(.correlation) | single source of truth = 10 | ✓ WIRED | line 109/311; no bare `10`/`14` literal in engine code. |
| InsightsEngine.insights | CorrelationEngine.insights | additive composition through HonestyGate.apply | ✓ WIRED | InsightsEngine.swift:38-39. |
| HonestyGate.minSample(.correlation) | engine self-gate | single source of truth = 10 | ✓ WIRED | `case .correlation: return 10` (HonestyGate.swift:37). |

### Code-Review (07-REVIEW.md) Finding Closure — Re-verified in source + behaviorally

| Finding | Severity | Status | Evidence |
| ------- | -------- | ------ | -------- |
| CR-01 duplicate-day traps the public pipeline (hard crash) | Critical | ✓ FIXED | Step (0) collapses to one snapshot per day via `Dictionary(...uniquingKeysWith:{_,last in last}).values.sorted` (CorrelationEngine.swift:56-58); no `uniqueKeysWithValues:` trap remains (grep: only in an explanatory comment). Test `duplicateDay_doesNotCrash_andProducesSensibleResult` PASSED through both the engine and the public `InsightsEngine`. |
| WR-01 second hardcoded `10` (SSOT violation) | Warning | ✓ FIXED | `confidence(...)` now takes `minN` as a parameter; engine passes `HonestyGate.minSample(.correlation)` (line 109). No bare min-sample literal in engine code. |
| WR-02 severity arm counts observations not days (pseudo-replication) | Warning | ✓ FIXED | `daySeverities.max()` reduces each day to one value (lines 237-239) → `n` counts days. Test `severityArm_countsDaysNotObservations` asserts sampleSize==40 (not 60) — PASSED. |
| WR-03 misleading continuity-correction doc | Warning | ✓ FIXED | Doc corrected (CorrelationStats.swift:117-122): explicitly states NO continuity correction, matches `scipy use_continuity=False`. |
| WR-04 inconsistent duplicate-key handling across maps | Warning | ✓ FIXED | `stateByDay` now uses `uniquingKeysWith:` (last-wins) matching `cyclePhaseLabels` (lines 76-81). |

### Behavioral Spot-Checks (live test run)

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Full EndoInsights package suite (phase gate) | `xcodebuild test -only-testing:EndoInsightsTests` | 90 tests, 10 suites, `** TEST SUCCEEDED **` | ✓ PASS |
| Strong association surfaces | `strongAssociation_isSurfaced` + `composition_surfacesCorrelation_overStrongFixture` | passed | ✓ PASS |
| Under-sampled (n=9) withheld | `underSampled_isWithheld` + `gate_withholdsUnderSampledCorrelation` | passed → ZERO | ✓ PASS |
| Spurious withheld | `spurious_isWithheld` + `gate_withholdsSpuriousCorrelation` | passed → ZERO | ✓ PASS |
| Multiple-comparison flood (K≈100+) surfaces nothing | `multipleComparisonFlood_surfacesNothing` + `gate_withholdsMultipleComparisonFlood` | passed → ZERO | ✓ PASS |
| Suppressed user → no cycle-phase correlation | `suppressedUser_noCyclePhaseCorrelation` (+ `_throughGate`) | passed | ✓ PASS |
| CR-01 duplicate-day no crash | `duplicateDay_doesNotCrash_andProducesSensibleResult` | passed | ✓ PASS |
| WR-02 severity arm counts days | `severityArm_countsDaysNotObservations` (sampleSize==40) | passed | ✓ PASS |
| Determinism | `output_isDeterministic` | passed | ✓ PASS |
| Phase-5/6 no-regression superset | `noRegression_insightsAreSupersetOfRawTrends` | passed | ✓ PASS |
| Non-causal copy on all emitted correlations | `allEmittedInsights_haveZeroForbiddenTerms` (covers correlation fixtures) | passed | ✓ PASS |
| Foundation-only boundary | `BoundaryLintTests` ("D-12 package import boundary") | passed | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| INST-03 | 07-01/02/03 | User can discover correlations/triggers across logged factors, gated by minimum-sample size and confidence | ✓ SATISFIED (engine scope) | Engine + gate complete and proven end-to-end through the public `InsightsEngine`. User-visible rendering is explicitly Phase 8 (REQUIREMENTS.md INST-03 row, ROADMAP Phase 8 SC1). The discovery + statistical rigor + honesty-routing is delivered and tested here. |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| (none) | — | No TBD/FIXME/XXX/TODO/PLACEHOLDER in new source or tests; no stub returns; no Date.now/UUID/random/print (only comment mentions); only `import Foundation` | — | Clean |

### Human Verification Required

None. This is a pure-logic, package-only phase with no device/UI surface (rendering is Phase 8). All success criteria are observable via the deterministic test suite, which was executed and passed. The one known-flaky app-level UI test is a tap-delivery issue unrelated to this Foundation-only package and outside phase scope.

### Gaps Summary

No gaps. All three ROADMAP Success Criteria are verified in the actual codebase and by a live run of the 90-test EndoInsights suite. The adversarial 07-REVIEW.md findings (1 Critical + 4 Warnings) are all confirmed fixed in source and guarded by dedicated regression tests (`duplicateDay_doesNotCrash`, `severityArm_countsDaysNotObservations`). The statistical rigor (BH-FDR once over the full set, three-AND hard gate, per-pair sample floor as single source of truth), the multiple-comparison flood suppression (ZERO false positives over K≈100+ pure noise), determinism, non-causal copy, suppressed-user cycle-phase suppression, Foundation-only boundary, and Phase-5/6 no-regression superset all hold.

---

_Verified: 2026-06-09T09:30:00Z_
_Verifier: Claude (gsd-verifier)_
