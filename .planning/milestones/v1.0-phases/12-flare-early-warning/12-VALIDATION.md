---
phase: 12
slug: flare-early-warning
status: planned
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-10
---

# Phase 12 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Swift Testing (`@Test`/`#expect`) — package (`EndoInsights`) logic + app honesty/copy + a dashboard XCUITest |
| **Config file** | `project.yml` (XcodeGen — EndoApp scheme test targets incl. `EndoInsights/EndoInsightsTests`) |
| **Quick run command** | `xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoInsightsTests` |
| **Full suite command** | `xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |
| **Estimated runtime** | ~60–180 seconds (simulator build + test); the Foundation-only package also runs via `swift test` in EndoInsights/ |

---

## Sampling Rate

- **After every task commit:** Run the quick command (package logic tests)
- **After every plan wave:** Run the full suite
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~180 seconds

---

## Per-Task Verification Map

> Populated by the planner. Strategy (per RESEARCH §Validation Architecture): the `FlareEngine` is a PURE producer composing already-gated signals (rising-severity trend + active triggers + cycle-phase proximity) — unit-testable over synthetic fixtures with NO store/clock. The TWO highest-stakes invariants are honesty, proven by test: (a) NEVER fabricate a warning under sparse/suppressed/insufficient data (prove ABSENCE; no "all clear"); (b) NEVER alarmist/diagnostic/certain copy (the new alarmist scan + the existing verb/gendered/numeric/imperative scans over `FlareCopy`). Plus SC2 no-push, proven by a `#filePath` source-scan (no NotificationScheduler/UNUserNotification in the flare module). Foundation-only held by `BoundaryLintTests`.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 12-01-T1 | 12-01 | 0/1 | INST-06 | T-12-01 | Alarmist/diagnostic copy is FLAGGED (positive control) and curated calm words pass (negative control); list mirrored package↔app | unit (Swift Testing) | `-only-testing:EndoAppTests/CopyAuditTests/alarmistScan_flagsDeliberateAlarmistCopy -only-testing:EndoAppTests/CopyAuditTests/alarmistScan_passesCuratedCalmWords` | ⬜ Plan 01 creates | ⬜ pending |
| 12-01-T2 | 12-01 | 0/1 | INST-06 | T-12-03 | The flare module references NO notification/push API (SC2 source scan) | source-scan (`#filePath`) | `-only-testing:EndoAppTests/FlareNoNotificationTests` (RED until 12-02 ships the files) | ⬜ Plan 01 creates | ⬜ pending |
| 12-01-T3 | 12-01 | 0/1 | INST-06 | T-12-02 | RED honesty/absence contract specified BEFORE the producer (suppressed/flat/under-evidence → ZERO; rising≥21 → one bounded ≤.medium); RED E2E + seed seam | unit + XCUITest (RED) | `build-for-testing` (scheme compiles via placeholder shim) | ⬜ Plan 01 creates | ⬜ pending |
| 12-02-T1 | 12-02 | 2 | INST-06 | T-12-06 | FlareCopy↔FlareCopyText byte-identical + alarmist-list parity; FlareCopy passes alarmist∪causal∪imperative∪gendered∪numeric | unit (Swift Testing) | `-only-testing:EndoAppTests/CopyAuditTests -only-testing:EndoAppTests/FlareCopyDriftTests` | ⬜ Plan 02 creates | ⬜ pending |
| 12-02-T2 | 12-02 | 2 | INST-06 | T-12-02 / T-12-04 / T-12-05 | The conservative heuristic: rising-spine REQUIRED, +1 notch each, cap .medium, floor .low, sampleSize==loggedDays vs 21, ≤1 card, stable signalKey "flare.warning"; Foundation-only | unit (Swift Testing) | `-only-testing:EndoInsightsTests/FlareEngineTests -only-testing:EndoInsightsTests/BoundaryLintTests` | ⬜ Plan 02 creates | ⬜ pending |
| 12-03-T1 | 12-03 | 3 | INST-06 | T-12-02 | Composed into the LOCKED InsightsEngine behind the single gate; rising→one flare / suppressed-flat→zero; zero-forbidden-terms over flare fixtures; no Phase-5/6/7/11 regression | unit (Swift Testing) | `-only-testing:EndoInsightsTests` | ⬜ Plan 03 edits | ⬜ pending |
| 12-03-T2 | 12-03 | 3 | INST-06 | T-12-07 | Calm `.flare` section placed FIRST, omit-when-empty, reused InsightCardView (no alarm visual); E2E renders flare before trends | XCUITest (E2E) | `-only-testing:EndoAppUITests/InsightsDashboardUITests/testFlare_rendersFirst` | ⬜ Plan 03 edits | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `FlareEngineTests` scaffold + synthetic fixtures (rising-with-triggers / insufficient-history / suppressed / sparse / flat-no-rise) — RED honesty suite first (absence-by-test is load-bearing) → **Plan 12-01 Task 3**
- [x] The alarmist/diagnostic word list locked (RESEARCH A4) + the new `ClaimsPolicy`/`ForbiddenTerms` alarmist scan with positive AND negative controls (dodge the substring trap; reword the "her"⊂"higher" trap in any copy) → **Plan 12-01 Task 1** (list locked: attack/alarm/alert/emergency/urgent/danger/diagnos/predict/imminent/certain/definitely/episode/will flare/guarantee; "warning" deliberately EXCLUDED for the section header)
- [x] The no-push source-scan guard (`FlareNoNotificationTests`, mirroring `ReportNoNetworkTests`) — exact forbidden tokens: NotificationScheduler / UNUserNotification / UNNotificationRequest / scheduleReminder (+ the full RESEARCH Pattern-5 set) → **Plan 12-01 Task 2**

*Existing Swift Testing + XCUITest infrastructure (the package test target + the dashboard E2E harness + the `InsightCardView` `.flare` branch already present) covers the rest. NO SwiftData schema bump. `wave_0_complete` flips to true once Plan 12-01 lands GREEN controls + the RED suites compile.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Flare warning reads as calm, uncertainty-first, NON-alarmist on a real history | INST-06 | "Calm / non-alarmist / uncertainty-first" tone is partly human-judgment beyond the automated alarmist scan; needs a real populated longitudinal account | On an account with ≥21 days incl. a recent rising symptom pattern + a logged trigger, open Insights → Flare early-warning; confirm it reads calmly ("your recent pattern looks similar to times before more difficult days — something to keep an eye on / discuss"), with a plain-language confidence, pervasive "not medical advice", NO alarm visual, and that an insufficient account shows NO warning. Deferred end-of-phase human check (screenshot saved to /tmp/e2e/insights-12-flare.png by the E2E). |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (the engine scaffold + alarmist scan + no-push guard)
- [x] No watch-mode flags
- [x] Feedback latency < 180s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** planner-populated (per-task map filled; Wave 0 mapped to Plan 12-01)
