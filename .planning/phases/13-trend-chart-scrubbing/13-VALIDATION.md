---
phase: 13
slug: trend-chart-scrubbing
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-10
---

# Phase 13 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Swift Testing (EndoInsights package + EndoAppTests) · XCTest/XCUITest (EndoAppUITests) |
| **Config file** | EndoApp.xcodeproj (generated — `xcodegen generate` after app-target file adds) |
| **Quick run command** | `swift test --package-path EndoInsights` (pure helpers, seconds) |
| **Full suite command** | `xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -derivedDataPath .build/dd` |
| **Estimated runtime** | package ~10s · full app suite ~8–12 min |

---

## Sampling Rate

- **After every task commit:** Run the package quick command when the resolver/pure layer changed; targeted `-only-testing:` app suites when app copy/UI changed
- **After every plan wave:** Run the full suite command
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~720 seconds (full suite ceiling)

---

## Per-Task Verification Map

*Filled by the planner — every task maps to one row. Expected coverage from RESEARCH §Validation Architecture:*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| (resolver exact-match: logged day → value, unlogged → nil, severity-0 → 0) | — | 1 | CHART-02 | — | no fabricated values on gaps | unit (Swift Testing, EndoInsights) | `swift test --package-path EndoInsights` | ❌ W0 | ⬜ pending |
| (tooltip copy registered + scan-clean) | — | 1 | CHART-01/02 | — | copy passes ClaimsPolicy scans | unit (CopyAuditTests) | `xcodebuild test … -only-testing:EndoAppTests/CopyAuditTests` | ✅ | ⬜ pending |
| (selection chrome renders for seeded selection; "Not logged" variant) | — | 3 | CHART-01/02/03 | — | deterministic seam, no gesture flake | E2E (XCUITest via `-uitest-select-day` + `-uitest-seed-scrub`) | `xcodebuild test … -only-testing:EndoAppUITests/InsightsDashboardUITests` | ✅ (extend) | ⬜ pending |
| (PDF path untouched) | — | 3 | CHART regression | — | `allowsSelection` default false ⇒ renderer unchanged | E2E (ReportFlowUITests re-run) | `xcodebuild test … -only-testing:EndoAppUITests/ReportFlowUITests` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `EndoInsights/Tests/EndoInsightsTests/ChartScrubSelectionTests.swift` — stubs for CHART-02 (exact-match resolver invariants)

*Existing infrastructure covers everything else (CopyAuditTests, InsightsDashboardUITests, ReportFlowUITests all exist).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Scrub-vs-List-scroll feel; tooltip live-while-touching + release dismissal | CHART-01/03 | OS-version-volatile gesture arbitration (no API contract for nil-on-release timing) | On iPhone 16 sim/device: scrub a dashboard chart — tooltip follows finger, vertical swipe still scrolls the List, release dismisses |
| Haptic on day change | CHART-03 | Haptics unverifiable in Simulator/XCUITest | On a real device: scrub across days — selection tick per day boundary |
| Exported PDF visually unchanged | PDF guard | Visual identity beyond test assertions | Generate a report → compare chart pages to a v1.0 export |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 720s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
