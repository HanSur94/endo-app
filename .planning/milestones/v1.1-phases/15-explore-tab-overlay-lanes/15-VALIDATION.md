---
phase: 15
slug: explore-tab-overlay-lanes
status: draft
nyquist_compliant: true
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
| **Config file** | EndoApp.xcodeproj (generated — `xcodegen generate` after EVERY new app-target file: ExploreView, ExploreCopy, ExploreSelection, lane/picker subviews, ExploreUITests. `project.yml` itself needs NO edit — the `EndoApp`/`EndoAppTests`/`EndoAppUITests` targets glob the `Sources/EndoApp` & `Tests/EndoApp*` directories) |
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

*Real task IDs filled by the planner (2026-06-15). `15-NN-TM` = plan 15-NN, Task M.*

| Requirement / Invariant | Plan · Task | Test Type | Automated Command | Status |
|--------------------------|-------------|-----------|-------------------|--------|
| Lane alignment (A2): one shared RuleMark crosses 3 different-scale lanes at one date (the central design risk) | 15-01 · T1+T2 | prototype build + screenshot | `xcodebuild build … EndoApp` + `/tmp/e2e/15-lane-alignment.png` eyeball | ⬜ pending |
| EXPL-02 chip picker toggles; 3–4 quantitative cap + remove-to-add; event series uncapped; suppression hides bleeding chips (pure cap/filter logic) | 15-02 · T1 | unit (Swift Testing) | `-only-testing:EndoAppTests/ExploreSelectionTests` | ⬜ pending |
| ExploreCopy registered in CopyAuditTests + substring-clean (forbidden-verb/gendered/numeric/imperative/alarmist) + "Not logged" pinned + EXPL-06 windowLabel | 15-03 · T1 | unit | `-only-testing:EndoAppTests/CopyAuditTests` | ⬜ pending |
| EXPL-03 aligned per-scale quantitative lanes, real units, shared X, no dual-y/no normalization (gap-run keying, fixed domain) | 15-04 · T1 | build + grep gates (no value(atX:); ChartSeriesGrouping.runs; SeriesNormalization.domain) → E2E in 15-06 | `xcodebuild build … EndoApp` (gates) → `-only-testing:EndoAppUITests/ExploreUITests` | ⬜ pending |
| EXPL-04 binary/event series as event-marker lanes (occurrence ticks, never 0/1 lines) | 15-04 · T1 | build + grep gate (no LineMark in EventLaneChart) → E2E | `xcodebuild build … EndoApp` (gate) → `-only-testing:EndoAppUITests/ExploreUITests` | ⬜ pending |
| EXPL-05 shared scrub reads every visible series' real-unit value or per-series "Not logged"; reachable without the drag gesture (multi-series tooltip + a11y proxy) | 15-04 · T2 (build) → 15-06 · T2 (E2E) | build + grep (ChartScrubSelection.resolve; isPresent; .accessibilityElement(children:.contain); uiTestScrubDay) → E2E (seam-driven a11y ids) | `xcodebuild build … EndoApp` → `-only-testing:EndoAppUITests/ExploreUITests` | ⬜ pending |
| EXPL-01 Explore is a consent-gated 3rd tab (`tab.explore`, only in ConsentedShell, id on the Label) | 15-05 · T2 (wiring) → 15-06 · T2 (E2E) | build + grep (tab.explore on Label; ExploreView() in .consented) → E2E | `xcodebuild build … EndoApp` → `-only-testing:EndoAppUITests/ExploreUITests` | ⬜ pending |
| EXPL-02 (UI) chips render; suppression hides bleeding chip; cap hint at the cap | 15-05 · T1 (picker) → 15-06 · T2 (E2E) | build + grep (ExploreSelection.offered; capHint) → E2E | `xcodebuild build … EndoApp` → `-only-testing:EndoAppUITests/ExploreUITests` | ⬜ pending |
| EXPL-06 range presets (absolute dates, gap breaks) re-anchor the shared X domain | 15-05 · T1 (range control) → 15-06 · T2 (E2E) | build + grep (windowLabel) → E2E (range switch) | `xcodebuild build … EndoApp` → `-only-testing:EndoAppUITests/ExploreUITests` | ⬜ pending |
| EXPL-07 empty ("pick a series") + sparse ("not enough data") states; gaps stay gaps in every lane | 15-05 · T2 (states) → 15-06 · T2 (E2E) | build + grep (explore.empty/explore.sparse) → E2E | `xcodebuild build … EndoApp` → `-only-testing:EndoAppUITests/ExploreUITests` | ⬜ pending |
| Deterministic E2E seam (`-uitest-seed-explore`, DEBUG, `-uitest-reset`-paired in-memory only) + reuse `-uitest-select-day` | 15-06 · T1 | seam build + grep (uiTestReset pairing) | `xcodebuild build … EndoApp` | ⬜ pending |
| EXPL-01..07 end-to-end via a11y ids (tab, chips, suppression, tooltip readout + "Not logged", event ticks, range, empty/sparse) | 15-06 · T2 | E2E | `-only-testing:EndoAppUITests/ExploreUITests` | ⬜ pending |
| PDF report path untouched (cross-cut) + Insights dashboard unaffected | 15-06 · T3 | regression + git zero-diff on the Report/PDF render path | git-clean under `Sources/EndoApp/Features/Report` + `-only-testing:EndoAppUITests/ReportFlowUITests -only-testing:EndoAppUITests/InsightsDashboardUITests -only-testing:EndoAppTests/CopyAuditTests` + `swift test --package-path EndoInsights` | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] **Lane-alignment prototype** (A2) — **15-01**: render 3 lanes on different scales + one shared RuleMark, screenshot, pick the inset mechanism (default: `.chartYAxis(.hidden)` + unit-in-title) BEFORE the full lane build. **Records the chosen mechanism in 15-01-SUMMARY.md (15-04 consumes it).**
- [ ] `EndoAppTests/ExploreSelectionTests.swift` — cap/toggle/suppression-filter unit suite (created with ExploreSelection) — **15-02**.
- [ ] `Sources/EndoApp/Features/Explore/ExploreCopy.swift` registered in `CopyAuditTests.auditableStrings` scan — **15-03** (BEFORE any view renders a string).
- [ ] `Sources/EndoApp/App/AppDependencies.swift` — `-uitest-seed-explore` fixture seam (DEBUG, `-uitest-reset`-paired) + reuse `-uitest-select-day` — **15-06 · T1**.
- [ ] `Tests/EndoAppUITests/ExploreUITests.swift` — seeded E2E (requires `xcodegen generate`) — **15-06 · T2**.

*Existing infrastructure (CopyAuditTests harness, the UITest harness, the launch-arg seam pattern) covers the rest.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Lane alignment + multi-series scrub feel on device | EXPL-03/05 | gesture feel + visual alignment beyond a11y assertions | On iPhone 16: select 3 series, scrub — the shared rule mark aligns across lanes; the tooltip reads each lane's real value or "Not logged" |
| VoiceOver / Audio Graph across multiple lanes | EXPL-03/05 | VoiceOver multi-chart navigation not XCUITest-automatable | With VoiceOver: navigate each lane's Audio Graph; gaps silent, never zeros; multi-series readout reachable without the drag gesture |
| Colorblind-safe series distinction | EXPL-03 | visual perception | Grayscale/colorblind filter: series distinguishable by legend word + symbol/dash, not hue alone |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (every task across 15-01..15-06 has an `<automated>` block)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (all 11 tasks carry an automated gate)
- [x] Wave 0 covers the alignment prototype (15-01) + new test files (15-02 ExploreSelectionTests, 15-06 ExploreUITests) + the seam (15-06 · T1) + the copy registration (15-03)
- [x] No watch-mode flags
- [x] Feedback latency < 720s (UI-suite ceiling)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** ready (planner-filled 2026-06-15)
