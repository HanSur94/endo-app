---
phase: 13
slug: trend-chart-scrubbing
status: planned
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-10
planned: 2026-06-10
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
- **After every plan wave:** Run the targeted suites touched by the wave + `-only-testing:EndoAppUITests/InsightsDashboardUITests` (NOT the full `-only-testing:EndoAppTests` target — known stall >15 min in this environment, STATE.md 08-03/08-04)
- **Before `/gsd-verify-work`:** All phase suites green: EndoInsights package + CopyAuditTests + InsightsDashboardUITests + ReportFlowUITests + ChartScrubUITests
- **Max feedback latency:** ~720 seconds (UI-suite ceiling)

---

## Per-Task Verification Map

*Every task maps to one row. Task IDs from 13-01/02/03-PLAN.md.*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 13-01-T1 | 13-01 | 1 | CHART-01, CHART-02 | T-13-05 | exact-match resolver — no fabricated values on gaps; severity-0 → literal 0 | unit (Swift Testing, EndoInsights) | `swift test --package-path EndoInsights --filter ChartScrubSelectionTests` | ❌ W0 (created by this task) | ⬜ pending |
| 13-01-T2 | 13-01 | 1 | CHART-01, CHART-02 | T-13-06 | tooltip copy registered in `auditableStrings` + passes all ClaimsPolicy scans BEFORE any view renders it | unit (CopyAuditTests + pin test) | `xcodebuild test … -only-testing:EndoAppTests/CopyAuditTests` | ✅ (extends) | ⬜ pending |
| 13-02-T1 | 13-02 | 2 | CHART-01, CHART-02 | T-13-03, T-13-05 | `chartXSelection` attached ONLY when `allowsSelection`; chrome renders iff resolver non-nil; no custom gestures / proxy lookups / tooltip literals (grep gates) | build + grep gates | `xcodebuild build …` + grep gates (see plan) | ✅ | ⬜ pending |
| 13-02-T2 | 13-02 | 2 | CHART-03, CHART-04 | T-13-01, T-13-04 | day-change-only haptic; quiet a11y (no `UIAccessibility.post`); Audio Graph discrete/logged-only; dashboard regression green | build + E2E regression | `xcodebuild test … -only-testing:EndoAppUITests/InsightsDashboardUITests` | ✅ | ⬜ pending |
| 13-03-T1 | 13-03 | 3 | CHART-01..04 (E2E enabler) | T-13-02, T-13-02b | seam inert without `-uitest-reset` pairing + `#if DEBUG`; garbage parse → nil never crash; `xDomain.contains` guard | build + grep gates | `xcodebuild build …` + grep gates (see plan) | ✅ | ⬜ pending |
| 13-03-T2 | 13-03 | 3 | CHART-01, CHART-02, CHART-04 | T-13-02 | deterministic seam E2E — zero coordinates/sleeps; gap vs severity-0 distinguishable by ids + values | E2E (XCUITest via `-uitest-select-day` + `-uitest-seed-scrub`) | `xcodebuild test … -only-testing:EndoAppUITests/ChartScrubUITests` | ❌ W0 (created by this task; `xcodegen generate`) | ⬜ pending |
| 13-03-T3 | 13-03 | 3 | PDF guard (cross-cutting) + phase regression | T-13-03 | `ReportPDFRenderer.swift` zero commits since v1.0 + zero working-tree diff; report flow + dashboard + package green | E2E regression + git gate | `git log v1.0..HEAD -- …/ReportPDFRenderer.swift` empty + `xcodebuild test … -only-testing:EndoAppUITests/ReportFlowUITests -only-testing:EndoAppUITests/InsightsDashboardUITests` + `swift test --package-path EndoInsights` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `EndoInsights/Tests/EndoInsightsTests/ChartScrubSelectionTests.swift` — created by 13-01-T1 together with the resolver (CHART-01/02 invariants; no xcodegen)
- [ ] `Tests/EndoAppUITests/ChartScrubUITests.swift` — created by 13-03-T2 (CHART-01/02/04 E2E via the seam; **requires `xcodegen generate`**)

*Existing infrastructure covers everything else (CopyAuditTests, InsightsDashboardUITests, ReportFlowUITests all exist).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Scrub-vs-List-scroll feel; tooltip live-while-touching + release dismissal | CHART-01/03 | OS-version-volatile gesture arbitration (no API contract for nil-on-release timing — RESEARCH A1/OQ1); a recurring release-checklist item per STATE.md | On iPhone 16 sim/device: scrub a dashboard chart — tooltip follows finger, vertical swipe still scrolls the List, release dismisses; also tap once and observe dismiss behavior |
| Haptic on day change | CHART-03 | Haptics unverifiable in Simulator/XCUITest | On a real device: scrub across days — selection tick per day boundary only (none on initial touch or release) |
| Exported PDF visually unchanged | PDF guard | Visual identity beyond test assertions | Generate a report → compare chart pages to a v1.0 export (no rule mark, no tooltip, no chrome) |
| VoiceOver per-mark swipe-through + Audio Graph | CHART-04 | VoiceOver/Audio Graph not XCUITest-automatable | With VoiceOver: swipe through logged days (date + "severity n of 10"); play the Audio Graph — discrete tones, gaps silent (never zeros) |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (7/7 rows have automated commands; Wave 0 test files are created by the same tasks that need them)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify (every task has one)
- [x] Wave 0 covers all MISSING references (both ❌ W0 files are created in-task)
- [x] No watch-mode flags
- [x] Feedback latency < 720s (targeted suites; full EndoAppTests target deliberately excluded per known stall)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** planned — pending execution sign-off
