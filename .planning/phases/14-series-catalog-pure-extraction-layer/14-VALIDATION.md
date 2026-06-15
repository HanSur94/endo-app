---
phase: 14
slug: series-catalog-pure-extraction-layer
status: draft
nyquist_compliant: false
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

*Filled by the planner with real task IDs. Expected invariant→assertion coverage from RESEARCH §Validation Architecture:*

| Invariant | Test Type | Automated Command | Status |
|-----------|-----------|-------------------|--------|
| Catalog enumerates every series (per-symptom, per-region, singletons) — coverage vs enum `.allCases` | unit | `swift test --package-path EndoInsights` | ⬜ pending |
| Unlogged day → NO point (nil → gap); logged literal 0 → real point at 0 (absence ≠ 0) | unit (gap + logged-0 fixtures) | same | ⬜ pending |
| Presence-kind series extract as occurrence `[Date]`, NOT `[InsightPoint]` — 0/1 coercion unrepresentable | unit (type-level + fixtures) | same | ⬜ pending |
| Scatter pairing: only both-logged days; identical integer pairs aggregated to `count` (no jitter/imputation) | unit | same | ⬜ pending |
| Suppression: bleeding-derived series emit nothing for a suppressed snapshot (no leak) | unit | same | ⬜ pending |
| Normalization preserves real-unit readout (round-trip) + honest per-scale domain | unit | same | ⬜ pending |
| Boundary lint stays green (no SwiftData/SwiftUI/HealthKit imports) | unit (BoundaryLintTests) | same | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] New test files in `EndoInsights/Tests/EndoInsightsTests/` (e.g. `ExploreSeriesCatalogTests`, `ExploreSeriesExtractionTests`, `ScatterPairingTests`, `SeriesNormalizationTests`) — created alongside each helper (no xcodegen; package target auto-discovers).

*Existing infrastructure (BoundaryLintTests, the package test target) covers everything else.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| — | — | — | — |

*None — this phase is pure logic with full automated coverage. No device, no UI, no human verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 15s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
