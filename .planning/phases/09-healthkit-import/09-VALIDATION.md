---
phase: 9
slug: healthkit-import
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-09
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Swift Testing (`@Test`/`#expect`) for logic; XCUITest for E2E UI flows |
| **Config file** | `project.yml` (XcodeGen — EndoApp scheme test targets) |
| **Quick run command** | `xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:EndoAppTests` |
| **Full suite command** | `xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 17'` |
| **Estimated runtime** | ~60–180 seconds (simulator build + test) |

---

## Sampling Rate

- **After every task commit:** Run the quick command (logic tests for the touched target)
- **After every plan wave:** Run the full suite
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** ~180 seconds

---

## Per-Task Verification Map

> Populated by the planner from PLAN.md tasks (every task → automated verify or a Wave-0 dependency). The injectable `HealthDataReading` protocol seam + `-uitest-health-*` fixture launch args make the import → manual-wins merge → `dataChanged()` recompute path E2E-testable on the Simulator without real-device HealthKit data.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| (planner-filled) | — | — | DATA-05 | — | — | — | — | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `HealthDataReading` protocol seam + fixture/real implementations (injectable `HKHealthStore` boundary)
- [ ] `-uitest-health-seed` / `-uitest-health-deny` launch-arg fixtures (mirror existing `-uitest-reset`/`-uitest-seed-insights`)
- [ ] `ImportedSample` round-trip + SchemaV5→V6 lightweight-migration survival test (clone the CachedInsight V4→V5 survival test +1 version)

*Existing Swift Testing + XCUITest infrastructure covers the rest.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real-device HealthKit read (actual menstrual/sleep/workout/nutrition samples) | DATA-05 | HealthKit on the Simulator cannot supply realistic menstrual/sleep/workout data; needs a real device with Health sample data | On a real iPhone with Health data: connect each type, confirm imported values appear labeled "from Apple Health" and flow into insights. Deferred human-verify (same posture as Phase 4 CloudKit). |
| System authorization sheet appearance + denial behavior | DATA-05 (SC2/SC3) | The system HealthKit auth sheet and its denied state are OS-driven and not fully reproducible in CI | Trigger a contextual connect; confirm in-app rationale shows BEFORE the system sheet; deny; confirm app stays fully usable and empty reads never render as "no symptoms". |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (the `HealthDataReading` seam + fixtures)
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
