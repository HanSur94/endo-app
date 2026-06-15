---
phase: 16
slug: scatter-view-correlation-deep-link
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-16
---

# Phase 16 — Validation Strategy

> The honesty-critical pairing math is ALREADY pure + unit-proven (`ScatterPairing`, Phase 14). Phase 16's new pure logic is the `ScatterDeepLink` resolver (signalKey → optional (xSeries, ySeries)) — Swift-Testing-unit-proven. The scatter render, count-encoding, neutral caption, sparse state, mode toggle, axis picker, and the deep-link PREFILL are proven by SEEDED XCUITest via a11y ids (no flaky gestures), mirroring the Phase-15 ExploreUITests approach.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Swift Testing (EndoInsights package + app-target pure logic via EndoAppTests) · XCTest/XCUITest (EndoAppUITests) |
| **Config file** | EndoApp.xcodeproj (generated — `xcodegen generate` after EVERY new app-target file: the scatter view, ScatterDeepLink, ScatterUITests, the router) |
| **Quick run command** | `swift test --package-path EndoInsights` (ScatterPairing already green) · targeted `-only-testing:` app suites |
| **Full suite command** | `xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath .build/dd -only-testing:EndoAppTests/<suite> -only-testing:EndoAppUITests/ScatterUITests` |
| **Estimated runtime** | package ~10s · ScatterUITests ~2-4 min · resolver unit suite ~seconds. Host runner is flaky (run test-by-test on a bare "TEST FAILED" with no "Test Case … failed"). |

---

## Sampling Rate

- **After every task commit:** package quick test for pure logic; targeted `-only-testing:` app suite for UI/copy
- **After every plan wave:** the wave's touched suites + `-only-testing:EndoAppUITests/ScatterUITests` (NOT the bare `-only-testing:EndoAppTests` — known stall)
- **Before `/gsd-verify-work`:** ScatterDeepLinkTests + CopyAuditTests + ScatterUITests + the regression suites (ExploreUITests, InsightsDashboardUITests, ReportFlowUITests) all green; EndoInsights package green
- **Max feedback latency:** ~720s (UI-suite ceiling)

---

## Per-Task Verification Map

*Filled by the planner with real task IDs (plan-NN Task-N).*

| Requirement / Invariant | Plan · Task | Test Type | Automated Command | Status |
|--------------------------|-------------|-----------|-------------------|--------|
| SCAT-03 scatter chrome via audited ExploreCopy + registered in CopyAuditTests + substring-clean | 16-01 T1 | unit (copy scan) | `xcodebuild test -only-testing:EndoAppTests/CopyAuditTests` | ⬜ pending |
| SCAT-04 deep-link resolver: quantitative factor×target → typed axis pair | 16-01 T2 | unit (resolver) | `xcodebuild test -only-testing:EndoAppTests/ScatterDeepLinkTests` | ⬜ pending |
| Deep-link HIDDEN for presence-only / suppressed-bleeding / sleep-quality factors (resolver → nil) | 16-01 T2 | unit (nil cases) | `xcodebuild test -only-testing:EndoAppTests/ScatterDeepLinkTests` | ⬜ pending |
| SCAT-01 two quantitative-only X/Y axis selectors (suppression hides bleeding) | 16-02 T1 | build + grep gate | `xcodebuild build` + `grep isQuantitative ScatterAxisPicker.swift` | ⬜ pending |
| SCAT-01/02 scatter renders count-encoded PointMarks over fixed real-unit domains, no jitter/stats | 16-02 T2 | build + honesty grep gates | `xcodebuild build` + grep (PointMark/symbolSize present; no LinearRegression/rValue/Double.random/jitter/LineMark; 2× SeriesNormalization.domain) | ⬜ pending |
| SCAT-01/03 Lanes\|Scatter mode toggle + empty/sparse states; mode/X/Y ephemeral | 16-02 T3 | build + E2E (lanes regression) + grep | `xcodebuild build` + `-only-testing:EndoAppUITests/ExploreUITests` + grep (no @AppStorage) | ⬜ pending |
| SCAT-04 cross-tab router (PendingScatter, env-injected) + ConsentedShell TabView(selection:) | 16-03 T1 | build + grep gate | `xcodebuild build` + grep (ExploreScatterRouter/PendingScatter/TabView(selection:)/.tag; no persistence) | ⬜ pending |
| SCAT-04 .correlation card affordance gated by resolver; typed payload, never copy-parsed | 16-03 T2 | build + E2E (dashboard regression) + grep | `xcodebuild build` + `-only-testing:EndoAppUITests/InsightsDashboardUITests` + grep (scatterLink/onOpenScatter/ScatterDeepLink.resolve; no insight.headline near scatter) | ⬜ pending |
| SCAT-04 ExploreView router read: prefill X/Y + mode=.scatter + clear payload | 16-03 T3 | build + E2E (lanes regression) + grep | `xcodebuild build` + `-only-testing:EndoAppUITests/ExploreUITests` + grep (exploreScatterRouter/pendingScatter cleared; no persistence) | ⬜ pending |
| Wave-0 seed: -uitest-seed-scatter-link (stress.high→pelvicPain quantitative); DEBUG + -uitest-reset paired | 16-04 T1 | build + grep gate | `xcodebuild build` + grep (uitest-seed-scatter-link/seedScatterCorrelation/setStress/pelvicPain; #if DEBUG; uitest-reset) | ⬜ pending |
| SCAT-01/02/03/04 E2E: mode switch + axis pick + plot + count + caption + sparse + positive deep-link prefill | 16-04 T2 | E2E | `-only-testing:EndoAppUITests/ScatterUITests` | ⬜ pending |
| SCAT-04 negative: dairy presence-factor card has NO scatterLink; suppressed user has no bleeding axis | 16-04 T2 | E2E (negative) | `-only-testing:EndoAppUITests/ScatterUITests/testPresenceFactorCardHasNoScatterLink` + `…/testSuppressedUserHasNoBleedingAxis` | ⬜ pending |
| PDF report path untouched; Explore lanes + dashboard unaffected | 16-04 T2 | regression | `-only-testing:EndoAppUITests/ReportFlowUITests -only-testing:EndoAppUITests/ExploreUITests -only-testing:EndoAppUITests/InsightsDashboardUITests` | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `Tests/EndoAppTests/ScatterDeepLinkTests.swift` — the signalKey → (xSeries, ySeries)? resolver unit suite (quantitative→quantitative maps; presence-only/suppressed/sleep-quality → nil). **→ Plan 16-01 Task 2.**
- [ ] A NEW `-uitest-seed-scatter-link` seed in AppDependencies (a `stress.high→pelvicPain` quantitative correlation so the deep-link affordance APPEARS; the shipped dairy seed stays the negative case). DEBUG + `-uitest-reset`-paired. **→ Plan 16-04 Task 1.**
- [ ] `Tests/EndoAppUITests/ScatterUITests.swift` — seeded E2E (requires `xcodegen generate`). **→ Plan 16-04 Task 2.**
- [ ] `ExploreScatterRouter` (the cross-tab routing seam, mirroring CheckInRouter) + the `ConsentedShell` `TabView(selection:)` edit. **→ Plan 16-03 Task 1.**
- [ ] New ExploreCopy scatter strings registered in CopyAuditTests. **→ Plan 16-01 Task 1.**

*Existing infrastructure (ScatterPairing unit tests, CopyAuditTests, the UITest harness, the launch-arg seam + CheckInRouter precedent) covers the rest.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Scatter readability + count-encoding legibility on device | SCAT-01/02 | visual density perception beyond a11y assertions | On iPhone 16: pick two factors, confirm denser day-pairs read as larger/darker dots, no overlap confusion |
| Descriptive-only tone (no implied causation) | SCAT-03 | subjective tone review | The caption + axes read as "raw days", never "X causes Y" |
| Colorblind-safe / not-sole-signal | SCAT-01 | visual perception | Grayscale filter: dots distinguishable by size/position, count surfaced in the readout not hue |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (10/10 tasks carry `<automated>`)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers the resolver suite + the new seed + the router + ScatterUITests + copy registration
- [x] No watch-mode flags
- [x] Feedback latency < 720s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** planner-complete (executor flips Per-Task Map statuses to ✅ as suites go green)
