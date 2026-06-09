---
phase: 12
slug: flare-early-warning
status: draft
nyquist_compliant: false
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
| (planner-filled) | — | — | INST-06 | — | — | — | — | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `FlareEngineTests` scaffold + synthetic fixtures (rising-with-triggers / insufficient-history / suppressed / sparse / flat-no-rise) — RED honesty suite first (absence-by-test is load-bearing)
- [ ] The alarmist/diagnostic word list locked (RESEARCH A4) + the new `ClaimsPolicy`/`ForbiddenTerms` alarmist scan with positive AND negative controls (dodge the substring trap; reword the "her"⊂"higher" trap in any copy)
- [ ] The no-push source-scan guard (`FlareNoNotificationTests`, mirroring `ReportNoNetworkTests`) — exact forbidden tokens: NotificationScheduler / UNUserNotification / UNNotificationRequest / scheduleReminder

*Existing Swift Testing + XCUITest infrastructure (the package test target + the dashboard E2E harness + the `InsightCardView` `.flare` branch already present) covers the rest. NO SwiftData schema bump.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Flare warning reads as calm, uncertainty-first, NON-alarmist on a real history | INST-06 | "Calm / non-alarmist / uncertainty-first" tone is partly human-judgment beyond the automated alarmist scan; needs a real populated longitudinal account | On an account with ≥21 days incl. a recent rising symptom pattern + a logged trigger, open Insights → Flare early-warning; confirm it reads calmly ("your recent pattern looks similar to times before higher-symptom days — something to keep an eye on / discuss"), with a plain-language confidence, pervasive "not medical advice", NO alarm visual, and that an insufficient account shows NO warning. Deferred end-of-phase human check (screenshot saved to /tmp/e2e by the E2E). |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (the engine scaffold + alarmist scan + no-push guard)
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
