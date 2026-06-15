---
phase: 15
slug: explore-tab-overlay-lanes
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-15
---

# Phase 15 — Validation Strategy

> Honesty-critical LOGIC is pure + Swift-Testing-unit-proven (cap/selection/suppression-filter, gap-honesty via the shipped helpers); the UI composition (lanes, multi-series tooltip, empty/sparse/cap states, suppression-hides-bleeding) is proven by SEEDED XCUITest via a11y ids — no flaky scrub gestures (mirror the Phase-13 `-uitest-select-day` seam). The lane-alignment unknown gets a Wave-0 prototype-and-screenshot task.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Swift Testing (EndoInsights package + any pure app-target logic via EndoAppTests) · XCTest/XCUITest (EndoAppUITests) |
| **Config file** | EndoApp.xcodeproj (generated — `xcodegen generate` after EVERY new app-target file: ExploreView, ExploreCopy, ExploreSelection, ExploreUITests) |
| **Quick run command** | `swift test --package-path EndoInsights` (pure additions) · targeted `-only-testing:` app suites |
| **Full suite command** | `xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath .build/dd -only-testing:EndoAppTests/<suite> -only-testing:EndoAppUITests/ExploreUITests` |
| **Estimated runtime** | package ~10s · ExploreUITests ~2-4 min · selection/cap unit suite ~seconds |

---

## Sampling Rate

- **After every task commit:** package quick test for pure logic; targeted `-only-testing:` app suite when UI/copy changes
- **After every plan wave:** the wave's touched suites + `-only-testing:EndoAppUITests/ExploreUITests` (NOT the bare `-only-testing:EndoAppTests` target — known stall)
- **Before `/gsd-verify-work`:** ExploreSelection unit suite + CopyAuditTests + ExploreUITests + the dashboard/report regression suites (InsightsDashboardUITests, ReportFlowUITests) all green; EndoInsights package green
- **Max feedback latency:** ~720s (UI-suite ceiling)

---

## Per-Task Verification Map

*Filled by the planner with real task IDs. Expected invariant→assertion coverage:*

| Requirement / Invariant | Test Type | Automated Command | Status |
|--------------------------|-----------|-------------------|--------|
| EXPL-01 Explore is a consent-gated 3rd tab (`tab.explore`, only in ConsentedShell) | E2E | ExploreUITests (tab present after consent) | ⬜ pending |
| EXPL-02 chip picker toggles series; 3–4 quantitative cap + remove-to-add; suppression hides bleeding chips | unit (ExploreSelection cap/filter) + E2E | EndoAppTests/ExploreSelectionTests + ExploreUITests | ⬜ pending |
| EXPL-03 aligned per-scale lanes, real units, shared X axis, no dual-y/no silent normalization | E2E (lanes render, per-lane domain) + prototype screenshot | ExploreUITests + Wave-0 alignment prototype | ⬜ pending |
| EXPL-04 binary/event series as event-marker lanes (never 0/1 lines) | E2E | ExploreUITests (event lane ticks, no line series) | ⬜ pending |
| EXPL-05 shared scrub reads every visible series' real-unit value or per-series "Not logged" | E2E (seam-driven, a11y ids) | ExploreUITests via `-uitest-*` selection seam | ⬜ pending |
| EXPL-06 range presets (absolute dates, gap breaks) reused | E2E | ExploreUITests (range switch) | ⬜ pending |
| EXPL-07 empty ("pick a series") + sparse ("not enough data") states; gaps in every lane | unit (gap via shipped helpers) + E2E | ExploreUITests (empty/sparse) | ⬜ pending |
| ExploreCopy registered in CopyAuditTests + substring-clean | unit | EndoAppTests/CopyAuditTests | ⬜ pending |
| PDF report path untouched | regression | ReportFlowUITests + git zero-diff on ReportPDFRenderer | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] **Lane-alignment prototype** (A2): render 3 lanes on different scales + one shared RuleMark, screenshot, pick the inset mechanism (default: `.chartYAxis(.hidden)` + unit-in-title) BEFORE the full lane build.
- [ ] `EndoAppTests/ExploreSelectionTests.swift` — cap/toggle/suppression-filter unit suite (created with ExploreSelection).
- [ ] `Tests/EndoAppUITests/ExploreUITests.swift` — seeded E2E (requires `xcodegen generate`).
- [ ] An Explore E2E seed/selection seam in AppDependencies (mirror `-uitest-select-day` / reuse `-uitest-seed-demo`).
- [ ] `ExploreCopy` registered in `CopyAuditTests.auditableStrings` scan (extends the existing test).

*Existing infrastructure (CopyAuditTests, the UITest harness, the launch-arg seam pattern) covers the rest.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Lane alignment + multi-series scrub feel on device | EXPL-03/05 | gesture feel + visual alignment beyond a11y assertions | On iPhone 16: select 3 series, scrub — the shared rule mark aligns across lanes; the tooltip reads each lane's real value or "Not logged" |
| VoiceOver / Audio Graph across multiple lanes | EXPL-03/05 | VoiceOver multi-chart navigation not XCUITest-automatable | With VoiceOver: navigate each lane's Audio Graph; gaps silent, never zeros |
| Colorblind-safe series distinction | EXPL-03 | visual perception | Grayscale/colorblind filter: series distinguishable by legend word + symbol/dash, not hue alone |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers the alignment prototype + new test files + seam
- [ ] No watch-mode flags
- [ ] Feedback latency < 720s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
