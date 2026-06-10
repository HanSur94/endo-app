---
phase: 11-personalized-recommendations
fixed_at: 2026-06-10T01:03:00Z
review_path: .planning/phases/11-personalized-recommendations/11-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 4
skipped: 0
status: all_fixed
---

# Phase 11: Code Review Fix Report

**Fixed at:** 2026-06-10T01:03:00Z
**Source review:** .planning/phases/11-personalized-recommendations/11-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (WR-01..WR-04; Info findings IN-01..IN-03 out of scope)
- Fixed: 4
- Skipped: 0

## Fixed Issues

### WR-01: `dominantPerSymptom` grouping key wrong for `bleeding` targets

**Files modified:** `EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift`, `EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift`, `EndoInsights/Tests/EndoInsightsTests/RecommendationsEngineTests.swift`
**Commit:** 9b29d6a
**Applied fix:** Replaced the fixed `suffix(2)` collapse key with a target-identity key — `parts.dropFirst(3).joined(...)` drops `correlation` + the two factor components, so the key is the actual target (`symptom.<type>` or `bleeding`) regardless of factor or fragment length. Previously `correlation.food.dairy.bleeding` keyed as `dairy.bleeding` and `correlation.sleep.poor.bleeding` as `poor.bleeding`, so two factors on the same bleeding target never collapsed — silently breaking the one-recommendation-per-target invariant (D-02) for bleeding while it held for symptoms. Added `twoFactorsSameBleedingTargetFixture` (dairy + poor sleep both co-occurring with bleeding) and a test asserting the two factors collapse to exactly ONE bleeding recommendation. A probe confirmed the fixture produces 3 distinct bleeding findings pre-collapse (caffeine/dairy/poor-sleep), so the collapse is genuinely exercised, not trivially passing.

### WR-02: Recommendation copy duplicated app↔package with no byte-identity guard

**Files modified:** `EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift`, `Tests/EndoAppTests/RecommendationsCopyDriftTests.swift`
**Commit:** 159cfe9
**Applied fix:** Added a public, test-support-only `RecommendationsDriftGuard` shim in the package that forwards the package-internal `RecommendationCopy` template strings (and nothing else — no behavior, no new import; `BoundaryLintTests` stays green). Added an app-side `RecommendationsCopyDriftTests` suite (mirroring the existing `DaySnapshotMappingTests` "drifted between app and package" precedent) asserting the beneficial/aggravating headlines and the detail line render byte-identically across the boundary for representative interpolation values. The app test target sees only the package's public API (it imports `EndoInsights` as a release product, not `@testable`), so this shim is the only surface that can see both halves. (Committed jointly with WR-03 — same mechanism, one shim, one drift-guard test file.)

### WR-03: Imperative forbidden-list duplicated app↔package with no parity guard

**Files modified:** `EndoInsights/Sources/EndoInsights/RecommendationsEngine.swift`, `Tests/EndoAppTests/RecommendationsCopyDriftTests.swift`
**Commit:** 159cfe9
**Applied fix:** The same `RecommendationsDriftGuard` shim also forwards `ForbiddenTerms.imperative` and `ForbiddenTerms.causal`. `RecommendationsCopyDriftTests` asserts `ClaimsPolicy.forbiddenImperatives` equals the package imperative list (order-independent), so a future edit to one list without the other fails. For the causal lists, the guard asserts substring-coverage rather than equality: the two are intentionally NOT equal — the package extends the app list with the D-08 causal connectives, and the app keeps marketing/medical-device claims ("medical-grade", "clinically validated/proven") that the engine never emits. The initial naive `Set.isSubset` guard correctly FAILED on this (the test caught the real relationship), and was refined to assert every shared causal verb the app forbids is substring-covered by some package term, with the app-only marketing claims explicitly excluded and rationalized.

### WR-04: Templated copy interpolates labels the audit only spot-checks

**Files modified:** `EndoInsights/Tests/EndoInsightsTests/RecommendationsEngineTests.swift`
**Commit:** 186b2f0
**Applied fix:** Added a package-side `everyProductionLabel_rendersCleanRecommendationCopy` test that enumerates EVERY `Factor.displayLabel` (across all parametric families, mirroring `CorrelationEngine.enumerateFactors`) and EVERY `CyclePhaseMapper.displayName` symptom label, renders both direction-aware headlines for every combination (plus the literal `bleeding` target), and scans each against `ForbiddenTerms` — turning the three-interpolation spot-check into exhaustive proof over the real interpolation domain. Placed package-side because `Factor`, `CyclePhaseMapper.displayName`, and the `RecommendationCopy` templates are all package-internal (the app test target cannot enumerate them). A future label containing a forbidden substring now fails this test instead of being silently dropped at runtime by the HonestyGate.

## Verification

**Locked invariants preserved:** compose-not-rederive (no new statistics — the WR-01 fix only changes the in-memory collapse KEY, not any derivation); suppressed/sparse/flood → zero recommendations (the `suppressedUser_yieldsZeroRecommendations` suite stays green); non-prescriptive copy (the imperative + causal scans stay green); EndoInsights Foundation-only (`BoundaryLintTests` green — the new public shim imports nothing); deterministic ordering and stable dismissal `signalKey` (the WR-01 fix preserves incoming order and does not touch the `recommendationSignalKey` scheme; `output_isDeterministic` green).

**Suites run (all PASS):**
- EndoInsights package suite via `swift test` — 109 tests, all pass (incl. `RecommendationsEngineTests`, `HonestyGateTests`, `CorrelationEngineTests`, `BoundaryLintTests`, and the new WR-01/WR-04 tests).
- iPhone 16 simulator (`xcodebuild test`, `name=iPhone 16`):
  - `EndoAppTests/CopyAuditTests` — pass
  - `EndoAppTests/RecommendationsCopyDriftTests` (new, WR-02/WR-03) — pass (6 tests)
  - `EndoInsightsTests/RecommendationsEngineTests` (incl. WR-01 collapse + WR-04 exhaustive audit) — pass
  - `EndoInsightsTests/HonestyGateTests` — pass
  - `EndoAppUITests/InsightsDashboardUITests/testRecommendation_rendersAfterCorrelation` (recommendation E2E) — pass (70.9s)
  - `EndoAppTests/DaySnapshotMappingTests` (drift-guard precedent, no-regression) — pass

**Logic-bug note:** WR-01 is a logic fix (a grouping-key derivation). It is covered by a new test that proves the intended collapse behavior (3 pre-collapse bleeding findings → 1 recommendation) and the full suite is green, so it is reported as fully fixed rather than requiring further human verification.

---

_Fixed: 2026-06-10T01:03:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
