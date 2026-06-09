---
phase: 07-correlation-trigger-discovery
plan: 03
subsystem: EndoInsights (compose the .correlation producer into the public InsightsEngine behind the HonestyGate)
tags: [correlation, honesty-gate, composition, withholding, foundation-only, tdd, phase-close]
requires:
  - "HonestyGate.apply / minSample(for:) (Phase 6, INST-04 — the single choke-point)"
  - "InsightsEngine.insights(from:asOf:dismissed:calendar:) (Phase 6 — the one public producer, signature LOCKED)"
  - "CorrelationEngine.insights(from:asOf:calendar:) (Plan 07-02 — the .correlation producer, self-gates off HonestyGate.minSample(.correlation))"
  - "Correlation fixtures (Plan 07-02): strong / under-sampled / spurious / multiple-comparison-flood / suppressed-user"
provides:
  - "minSample(.correlation) = 10 LOCKED (single source of truth; the engine self-gate reads the same function)"
  - "InsightsEngine composes CorrelationEngine as one additive term routed through the HonestyGate"
  - "End-to-end honesty acceptance: strong surfaced / under-sampled+spurious+flood WITHHELD / suppressed has no cyclePhase correlation — all through the PUBLIC API"
affects:
  - "08 (the dashboard renders InsightsEngine output — correlations now flow through the gated public path)"
tech-stack:
  added: []
  patterns:
    - "Additive composition: one + CorrelationEngine.insights(...) term, public signature unchanged (D-16)"
    - "Single source of truth for the per-pair sample floor — no second min-sample literal"
    - "Withholding-as-value proven through the PUBLIC producer (not just the internal engine)"
key-files:
  created: []
  modified:
    - EndoInsights/Sources/EndoInsights/HonestyGate.swift
    - EndoInsights/Sources/EndoInsights/InsightsEngine.swift
    - EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift
    - EndoInsights/Tests/EndoInsightsTests/InsightsEngineTests.swift
decisions:
  - "minSample(.correlation) locked at 10 in the HonestyGate table (was 14 placeholder); the Plan-02 engine self-gate reads HonestyGate.minSample(for: .correlation) — no second literal added (D-14/A1)"
  - "Composition is a single additive term after CyclePhaseMapper.insights; the public signature (from:asOf:dismissed:calendar:) stays byte-identical (D-13/D-16/D-17)"
  - "Suppressed-user no-cyclePhase-correlation asserted via signalKey substring 'cyclePhase' (correlation keys are correlation.cyclePhase.*); strong food correlations still surface for that user — only the cyclePhase factor is suppressed (D-04)"
  - "NO new withholding logic — the existing gate steps (a)-(d) already drop dismissed/.none/under-sample/causal correlations (D-11)"
metrics:
  duration: ~6 min
  completed: 2026-06-09
  tasks: 3
  files: 4
  tests: 5 new (88 total package tests GREEN)
---

# Phase 7 Plan 03: Compose CorrelationEngine through the HonestyGate Summary

The phase-closing slice: `minSample(.correlation)` is locked to **10** in `HonestyGate.minSample(for:)` (the single source of truth the Plan-02 engine already self-gates against), `CorrelationEngine` is composed into the public `InsightsEngine` as one additive term routed through the existing `HonestyGate.apply`, and the MANDATORY honesty acceptance is proven end-to-end through the PUBLIC producer — a strong association surfaces a `.correlation` while under-sampled / spurious / multiple-comparison-flood correlations are WITHHELD, the suppressed user has no cycle-phase correlation, and every emitted correlation headline+detail passes the zero-forbidden-terms scan. The change is purely additive: trends + cycle-phase are unaffected and the Phase-5 no-regression superset still holds.

## What Was Built

- **`HonestyGate.swift`** — one-line table edit: `case .correlation: return 14` → `return 10` (D-14/A1), plus the doc-comment updated from `→ 14 (Phase 7 placeholder)` to `→ 10 (Phase 7 LOCKED — D-14/A1; == the engine self-gate floor)`. No other case, the confidence floor, or steps (a)–(d) touched; no new withholding logic (the existing min-sample backstop + `.none`-confidence floor already withhold below-floor correlations, D-11). The Plan-02 engine reads `HonestyGate.minSample(for: .correlation)` exclusively — no second `10` literal exists.
- **`InsightsEngine.swift`** — one additive composition term: `+ CorrelationEngine.insights(from: snapshots, asOf: today, calendar: calendar)` after the `CyclePhaseMapper.insights(...)` term. The public signature `insights(from:asOf:dismissed:calendar:)` is byte-identical (LOCKED, D-16); the `return HonestyGate.apply(candidates, dismissed: dismissed)` is unchanged.
- **`HonestyGateTests.swift`** — appended the three correlation fixtures (`strongAssociation`, `multipleComparisonFlood`, `suppressedUserCorrelation`) to the `streams` array of the mandatory `allEmittedInsights_haveZeroForbiddenTerms` scan, satisfying the file's own contract (every new kind adds its fixtures). The existing loop scans each emitted headline/detail through `InsightsEngine.insights(...)` for `ForbiddenTerms.violation`.
- **`InsightsEngineTests.swift`** — 5 new tests through the public `engine(_:)` helper: `composition_surfacesCorrelation_overStrongFixture` (a strong association surfaces `.correlation`), `gate_withholdsUnderSampledCorrelation` (n=9 < 10 → zero), `gate_withholdsSpuriousCorrelation` (independent → zero), `gate_withholdsMultipleComparisonFlood` (K≈100+ noise → zero, the load-bearing SC3), `suppressedUser_noCyclePhaseCorrelation_throughGate` (no emitted signalKey contains `cyclePhase`, D-04). The existing `noRegression_insightsAreSupersetOfRawTrends` still passes unchanged with the additive term.

## TDD Gate Compliance

This plan is `type: tdd` but its production change is config + a single composition term (not a new feature with a freestanding behavior), so the gate sequence is: the production edits (Task 1, `feat(07-03)` `3f02bff`) are verified by `TEST BUILD SUCCEEDED` and the existing suites, then the new acceptance tests (Task 2, `test(07-03)` `345d291`) are added and the full suite proves both the surfacing and the withholding through the public path. Both a `feat(...)` and a `test(...)` gate commit are present; REFACTOR was not needed. The withholding tests are the durable honesty guarantee (the runtime forbidden-term enforcement is a debug assert).

## Deviations from Plan

None — the plan executed exactly as written. The two production edits and the two test extensions matched 07-PATTERNS.md lines 109–195 verbatim; no auto-fixes (Rules 1–3) were needed and no architectural decision (Rule 4) arose.

## Known Stubs

None. `minSample(.correlation)` returns the real locked value 10; the composition term is live; the acceptance tests assert real surfacing + real withholding through the public API. Nothing is hardcoded or placeholdered.

## Threat Flags

None new. The plan's `<threat_model>` mitigations are all satisfied:
- **T-07-09** (spurious/under-sampled reaches the user): `minSample(.correlation)=10` + the `.none`-confidence floor withhold below-floor correlations through the PUBLIC `InsightsEngine`; `gate_withholdsUnderSampled/Spurious/MultipleComparisonFlood` enforce ZERO correlations surface (SC3). No new withholding code (D-11).
- **T-07-10** (causal claim reaches the user): the mandatory `allEmittedInsights_haveZeroForbiddenTerms` scan now covers the correlation fixtures; the gate's step (d) fail-closed `ForbiddenTerms` scan drops any causal copy in release + asserts in debug.
- **T-07-11** (Phase-5/6 regression): additive change (one table value + one term); `noRegression_insightsAreSupersetOfRawTrends` proves trends survive; the full 88-test package suite confirms cycle-phase + trends unaffected.
- **T-07-06** (health prose at the boundary): composition only adds value-type `[Insight]`s with category `signalKey`s + rounded copy — no free-text, no logging.
- **T-07-SC**: zero external packages (Foundation-only); BoundaryLintTests still GREEN.

## Verification

- `xcodebuild test … -only-testing:EndoInsightsTests` → **88 tests in 10 suites GREEN** (the phase gate; was 83 + 5 new correlation acceptance tests; BoundaryLint included — Foundation-only intact).
- `xcodebuild test … -only-testing:EndoInsightsTests/HonestyGateTests -only-testing:EndoInsightsTests/InsightsEngineTests` → 22 tests GREEN (all 5 new correlation tests pass).
- `grep -nE "case \.correlation:\s*return 10"` HonestyGate.swift → matches at line 37; `grep -c "case .correlation:    return 14"` → 0 (no placeholder remains).
- `grep -c "CorrelationEngine.insights"` InsightsEngine.swift → 1; public signature unchanged.
- `grep -c "AssociationFixture\|FloodFixture\|suppressedUserCorrelationFixture"` HonestyGateTests.swift → 3 (correlation fixtures in the scan).
- Single source of truth: `grep -c "HonestyGate.minSample(for: .correlation)"` CorrelationEngine.swift → 2 (self-gate + doc reference); no second `10` literal.

## Self-Check: PASSED
- FOUND: EndoInsights/Sources/EndoInsights/HonestyGate.swift (minSample(.correlation)=10)
- FOUND: EndoInsights/Sources/EndoInsights/InsightsEngine.swift (CorrelationEngine.insights composition term)
- FOUND: EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift (3 correlation fixtures in the scan)
- FOUND: EndoInsights/Tests/EndoInsightsTests/InsightsEngineTests.swift (5 new correlation tests)
- FOUND commit: 3f02bff (Task 1 — lock minSample + compose)
- FOUND commit: 345d291 (Task 2 — honesty acceptance tests)
