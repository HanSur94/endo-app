---
phase: 07-correlation-trigger-discovery
plan: 01
subsystem: EndoInsights (correlation stats math foundation)
tags: [statistics, foundation-only, tdd, correlation, fdr, effect-size]
requires:
  - EndoInsights package (Foundation-only boundary, InsightConfidence)
provides:
  - "CorrelationStats: cliffsDelta, meanDifference, riskDifference, riskRatio, fisherExactTwoTailed, normalCDF, mannWhitneyP, benjaminiHochbergPasses, confidence"
affects:
  - 07-02 (CorrelationEngine consumes every CorrelationStats helper)
tech-stack:
  added: []
  patterns:
    - "Pure package-internal enum of static helpers (mirrors TrendsEngine 'each helper independently testable')"
    - "never-coerce-empty-to-0 (guard !isEmpty / guard arm>0 → nil)"
    - "bound (lgamma as (Double) -> Double) scalar overload to disambiguate Foundation lgamma"
    - "erf-based standard-normal CDF (0.5·(1+erf(x/√2)))"
    - "deterministic BH-FDR step-up (value-then-index sort)"
key-files:
  created:
    - EndoInsights/Sources/EndoInsights/CorrelationStats.swift
    - EndoInsights/Tests/EndoInsightsTests/CorrelationStatsTests.swift
  modified: []
decisions:
  - "D-18 IP landed as independently-testable pure functions, proven against hand-computed values before any insight is built on them"
  - "lgammaScalar is a computed static (not stored let) to satisfy Swift 6 strict-concurrency Sendable"
  - "Corrected two wrong plan test expectations to the mathematically-correct values (Rule 1)"
metrics:
  duration: ~5 min
  completed: 2026-06-09
  tasks: 2
  files: 2
  tests: 26 new (73 total package tests GREEN)
---

# Phase 7 Plan 01: CorrelationStats Math Foundation Summary

Pure Foundation-only `enum CorrelationStats` — Cliff's delta, mean-difference, risk difference/ratio, Fisher's exact two-tailed p (lgamma bound correctly), Mann–Whitney U normal-approx p with pooled-tie correction (erf-based CDF), Benjamini–Hochberg FDR step-up, and the confidence-band mapping — each PROVEN against hand-computed known values via TDD RED→GREEN before any insight is built on it.

## What Was Built

- **`CorrelationStats.swift`** (the new IP, D-18): nine pure, deterministic, Foundation-only static helpers mirroring `TrendsEngine`'s independently-testable discipline and its never-coerce-empty-to-0 honesty guard. No `Date.now`/`UUID()`/RNG/`print`. The Fisher path binds `(lgamma as (Double) -> Double)` (a bare `lgamma` is ambiguous and won't compile); `erf` is used directly.
- **`CorrelationStatsTests.swift`**: 26 known-value `#expect`s, each citing its hand computation in-comment — exact `==` on integer-derived deltas, the `Set<Int>` BH result, and the `InsightConfidence` band; tolerances only on the `Double`-valued p/CDF asserts.

## TDD Gate Compliance

- RED commit `c142613` (`test(07-01)`): suite written first; build failed ONLY because `CorrelationStats` did not exist (confirmed via `cannot find 'CorrelationStats' in scope`).
- GREEN commit `39633c1` (`feat(07-01)`): implementation; all 26 tests pass, full 73-test package suite GREEN, no REFACTOR needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Swift 6 strict-concurrency rejected the `static let` lgamma bind**
- **Found during:** Task 2 (first GREEN build).
- **Issue:** `private static let lgammaScalar = (lgamma as (Double) -> Double)` failed to compile — `Static property 'lgammaScalar' is not concurrency-safe because non-'Sendable' type '(Double) -> Double' may have shared mutable state`.
- **Fix:** Changed it to a computed `private static var lgammaScalar: (Double) -> Double { (lgamma as (Double) -> Double) }`. The load-bearing overload bind (and the `lgamma as (Double) -> Double` grep target) is preserved; only the storage form changed.
- **Files modified:** `CorrelationStats.swift`. **Commit:** `39633c1`.

**2. [Rule 1 - Bug] `normalCDF(1/√2)` expected value in the plan was wrong**
- **Found during:** Task 1 (writing the RED asserts).
- **Issue:** The plan's `must_haves` and Task-1 prose claim `normalCDF(1/√2) ≈ 0.6826894921`. That number is `erf(1/√2)` (the ±1σ probability mass), NOT a CDF value. The actual `Φ(1/√2) ≈ 0.7602499389`; `Φ(1) ≈ 0.8413447461`.
- **Fix:** Asserted the mathematically-correct standard-normal CDF known values (`Φ(0)==0.5`, `Φ(1)≈0.8413447461`, `Φ(1/√2)≈0.7602499389`) plus the symmetry property `Φ(−x)+Φ(x)==1`. The implementation formula `0.5·(1+erf(x/√2))` was unchanged — only the wrong *expected test value* was corrected (cross-checked in Python this session).
- **Files modified:** `CorrelationStatsTests.swift`. **Commit:** `39633c1`.

**3. [Rule 1 - Bug] Mann–Whitney expected p in the plan omitted the tie correction it mandates**
- **Found during:** Task 2 (first GREEN run — the one failing test).
- **Issue:** Task-1 prose expects `mannWhitneyP([8,8,8,8],[1,1,1,1]) ≈ 0.0209213353`, computed with NO tie correction (σ_U = √12). But the pooled set has ties (four 8s, four 1s) and Task 2 explicitly mandates the tie-corrected σ_U — so the two tasks contradicted each other. The correct tie-corrected value is `p ≈ 0.0081509716` (σ_U² = (16/12)·(9 − 120/56) = 9.142857, z = 2.6457513).
- **Fix:** Corrected the expected value to `0.0081509716` (tolerance 1e-6), cross-checked in BOTH Python and isolated Swift. The `< 0.05` "small p" assertion still holds.
- **Files modified:** `CorrelationStatsTests.swift`. **Commit:** `39633c1`.

## Known Stubs

None. Every helper is fully implemented and proven; nothing is hardcoded or placeholdered.

## Threat Flags

None. The plan's `<threat_model>` mitigations are all satisfied: every helper guards empty/degenerate input (no force-unwrap, no divide-by-zero — proven by the empty-group/empty-arm/zero-reference test cases); the file contains no logging of any value (T-07-02); the math is proven against hand-computed values before anything is built on it (T-07-03); zero external packages installed (T-07-SC).

## Verification

- `xcodebuild test … -only-testing:EndoInsightsTests/CorrelationStatsTests` → 26 tests GREEN.
- Full `EndoInsightsTests` target → 73 tests in 9 suites GREEN (BoundaryLint included; no regression).
- `grep "^import" CorrelationStats.swift` → only `import Foundation` (SC1 boundary intact).
- Determinism grep (`Date()|Date.now|UUID()|random|print(`) → matches only comment prose, no code.
- `grep "lgamma as (Double) -> Double"` → matches (overload bound). `grep "erf("` → used directly.

## Self-Check: PASSED
- FOUND: EndoInsights/Sources/EndoInsights/CorrelationStats.swift
- FOUND: EndoInsights/Tests/EndoInsightsTests/CorrelationStatsTests.swift
- FOUND commit: c142613 (RED test)
- FOUND commit: 39633c1 (GREEN impl)
