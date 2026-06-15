---
phase: 16
slug: scatter-view-correlation-deep-link
status: draft
nyquist_compliant: false
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

*Filled by the planner with real task IDs. Expected coverage:*

| Requirement / Invariant | Test Type | Automated Command | Status |
|--------------------------|-----------|-------------------|--------|
| SCAT-01 Lanes\|Scatter mode toggle; scatter renders with 2 quantitative axes (real-unit domains) | E2E | ScatterUITests (mode switch + axis pick + plot) | ⬜ pending |
| SCAT-02 each point = one both-logged day; identical pairs count-encoded (size/opacity), no jitter | unit (ScatterPairing, Phase 14 — re-confirm) + E2E | `swift test --package-path EndoInsights` + ScatterUITests | ⬜ pending |
| SCAT-03 descriptive-only: no fitted line / no r-value, neutral caption, honest sparse state | build + grep gates (no regression/LinearRegression/rValue) + E2E | grep + ScatterUITests | ⬜ pending |
| SCAT-04 deep-link from a .correlation card prefills the scatter with the two factors | unit (ScatterDeepLink resolver) + E2E | EndoAppTests/ScatterDeepLinkTests + ScatterUITests | ⬜ pending |
| Deep-link HIDDEN for presence-only / suppressed / sleep-quality factors (resolver → nil) | unit + E2E (negative: dairy correlation = no affordance) | ScatterDeepLinkTests + ScatterUITests | ⬜ pending |
| Scatter chrome via audited ExploreCopy + registered in CopyAuditTests + substring-clean | unit | EndoAppTests/CopyAuditTests | ⬜ pending |
| PDF report path untouched; Explore lanes + dashboard unaffected | regression + git zero-diff | ReportFlowUITests + ExploreUITests + InsightsDashboardUITests | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `EndoAppTests/ScatterDeepLinkTests.swift` — the signalKey → (xSeries, ySeries)? resolver unit suite (quantitative→quantitative maps; presence-only/suppressed/sleep-quality → nil).
- [ ] A NEW `-uitest-seed-scatter-link` seed in AppDependencies (a `stress→pelvicPain` quantitative correlation so the deep-link affordance APPEARS; the shipped dairy seed stays the negative case). DEBUG + `-uitest-reset`-paired.
- [ ] `Tests/EndoAppUITests/ScatterUITests.swift` — seeded E2E (requires `xcodegen generate`).
- [ ] `ExploreScatterRouter` (the cross-tab routing seam, mirroring CheckInRouter) + the `ConsentedShell` `TabView(selection:)` edit.
- [ ] New ExploreCopy scatter strings registered in CopyAuditTests.

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

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers the resolver suite + the new seed + the router + ScatterUITests + copy registration
- [ ] No watch-mode flags
- [ ] Feedback latency < 720s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
