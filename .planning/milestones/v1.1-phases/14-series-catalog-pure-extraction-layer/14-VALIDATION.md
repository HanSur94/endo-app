---
phase: 14
slug: series-catalog-pure-extraction-layer
status: planned
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-15
---

# Phase 14 — Validation Strategy

> Per-phase validation contract. This is a PURE Foundation-only package phase — every honesty invariant is unit-provable WITHOUT rendering (the `ChartScrubSelectionTests` playbook). No UI, no E2E, no manual checkpoints expected.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Swift Testing (`import Testing`) — EndoInsights package target `EndoInsightsTests` |
| **Config file** | none — SwiftPM target (no xcodegen; package files only) |
| **Quick run command** | `swift test --package-path EndoInsights` |
| **Full suite command** | `swift test --package-path EndoInsights` (package) — and the app scheme re-runs it as a scheme test target if needed |
| **Estimated runtime** | ~10–15s (pure unit tests) |

---

## Sampling Rate

- **After every task commit:** `swift test --package-path EndoInsights` (seconds — run every time)
- **After every plan wave:** full package suite green
- **Before `/gsd-verify-work`:** package suite green incl. BoundaryLintTests (Foundation-only guard)
- **Max feedback latency:** ~15 seconds

---

## Per-Task Verification Map

*Filled by the planner with real task IDs. Every invariant maps to a Swift Testing assertion run by `swift test --package-path EndoInsights` — Nyquist-compliant (every task has an `<automated>` verify; no rendering required).*

| Invariant | Plan · Task | Test File / Assertion | Automated Command | Status |
|-----------|-------------|-----------------------|-------------------|--------|
| Catalog enumerates every series (per-symptom 11, per-region 10, per-food-tag, singletons) — coverage vs enum `.allCases`; ids unique/Hashable | 14-01 · T1–T3 | `ExploreSeriesCatalogTests` (catalogCoversEvery{SymptomType,BodyRegion,FoodTag}, catalogHasSingletonFields, catalogIdsAreUnique, scaleKindsAreCorrect, bleedingAppearsAsBothQuantitativeAndPresence) | `swift test --package-path EndoInsights` | ⬜ pending |
| Unlogged day → NO point (nil → gap); logged literal 0 → real point at 0 (absence ≠ 0) | 14-02 · T1–T2 | `ExploreSeriesExtractionTests` (unloggedQuantitativeDay_emitsNoPoint, loggedZero_emitsRealPointAtZero, loggedZeroSymptomSeverity_emitsRealPointAtZero) | same | ⬜ pending |
| Presence-kind series extract as occurrence `[Date]`, NOT `[InsightPoint]` — 0/1 coercion unrepresentable; presence-absent ≠ logged-absent | 14-02 · T1–T2 | `ExploreSeriesExtractionTests` (presentFoodTag_emitsOccurrenceDate_andReturnsDateArray, loggedDayWithoutTag_emitsNoOccurrence) + type-level (occurrenceDays returns `[Date]`, no value field) | same | ⬜ pending |
| Scatter pairing: only both-logged days; identical integer pairs aggregated to `count` (no jitter/imputation); quantitative-axis-only | 14-03 · T1–T2 | `ScatterPairingTests` (identicalPairs_aggregateIntoOnePointWithCount, dayWithOneFactorMissing_producesNoPair, mixedDays_onlyBothLoggedDaysCounted, distinctPairs_produceDistinctSortedPoints, loggedZeroBothFactors_isARealPair_notExcluded) | same | ⬜ pending |
| Suppression: bleeding-derived series emit nothing for a suppressed snapshot (no leak) | 14-01 · T2 (gate) · 14-02 · T2 (fixture) · 14-03 · T2 (carry-through) | `ExploreSeriesExtractionTests.suppressedBleeding_emitsNoPointsAndNoOccurrences` (+ unsuppressed control) · `ScatterPairingTests.suppressedBleedingAxis_contributesNoPair` | same | ⬜ pending |
| Normalization preserves real-unit readout (round-trip) + honest FIXED per-scale domain (never per-window) | 14-04 · T1–T2 | `SeriesNormalizationTests` (domainPerKindIsFixedAndHonest, presenceKindHasNoDomain, normalizeDenormalizeRoundTrips, normalizeMapsToUnitInterval, readoutReportsRealValueAndUnit_notNormalized) | same | ⬜ pending |
| Boundary lint stays green (no SwiftData/SwiftUI/HealthKit imports in any new file) | 14-01..04 · all tasks | existing `BoundaryLintTests.packageSourcesImportOnlyFoundation` (auto-scans new `Sources/EndoInsights/*.swift`) | same | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `SeriesTestSupport.swift` (shared UTC calendar + `daySnapshot(...)` builder, mirroring `ChartScrubSelectionTests`) — created in **14-01 · Task 3**; the harness all four test files call.
- [ ] `ExploreSeriesCatalogTests.swift` — created in **14-01 · Task 3**.
- [ ] `ExploreSeriesExtractionTests.swift` — created in **14-02 · Task 2**.
- [ ] `ScatterPairingTests.swift` — created in **14-03 · Task 2**.
- [ ] `SeriesNormalizationTests.swift` — created in **14-04 · Task 2**.
- [ ] Framework install: none — Swift Testing is bundled; the package test target already exists.

*Existing infrastructure (BoundaryLintTests, the package test target) covers everything else. No new framework, no xcodegen, no simulator.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| — | — | — | — |

*None — this phase is pure logic with full automated coverage. No device, no UI, no human verification.*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (every task verifies via `swift test --package-path EndoInsights`)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (every task has one)
- [x] Wave 0 covers all MISSING references (all four test files + shared harness scheduled in-plan)
- [x] No watch-mode flags
- [x] Feedback latency < 15s (~10–15s pure unit run)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** planned (set at planning; flips to verified after `/gsd-verify-work`)
