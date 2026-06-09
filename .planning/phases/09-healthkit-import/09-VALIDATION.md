---
phase: 9
slug: healthkit-import
status: draft
nyquist_compliant: true
wave_0_complete: true
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
| **Quick run command** | `xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppTests` |
| **Full suite command** | `xcodebuild test -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |
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
| 09-01-T1 | 09-01 | 1 (Wave 0) | DATA-05 | T-09-04/05 | HealthDataReading seam: no HKSample/HKHealthStore escapes; HealthKit absent from package | build | `xcodebuild build -scheme EndoApp` | ❌ Wave 0 | ⬜ pending |
| 09-01-T2 | 09-01 | 1 (Wave 0) | DATA-05 | T-09-02 | Pure HK->app mapping; defensive decode (unknown->.other/nil), never crash/fabricate | unit | `-only-testing:EndoAppTests/HealthSampleMappingTests` | ❌ Wave 0 | ⬜ pending |
| 09-01-T3 | 09-01 | 1 (Wave 0) | DATA-05 | T-09-01/03/05 | CloudKit-safe ImportedSample; additive V5->V6 lightweight migration survives; package boundary lint | unit | `-only-testing:EndoAppTests/SchemaMigrationTests -only-testing:EndoInsights/EndoInsightsTests/BoundaryLintTests` | ❌ Wave 0 | ⬜ pending |
| 09-02-T1 | 09-02 | 2 | DATA-05 | T-09-06/07/08/09 | Anchored pull dedupe+deletions; recompute-once (D-12); consent-guarded; corrupt anchor degrades safely | unit | `-only-testing:EndoAppTests/HealthAnchorPullTests -only-testing:EndoAppTests/HealthImportServiceTests` | ❌ Wave 0 | ⬜ pending |
| 09-02-T2 | 09-02 | 2 | DATA-05 | T-09-10/11 | Manual-wins + nil-honest merge (empty/denied never reads as "no symptoms", SC3); imported-only days reach engine; source flag for D-03 | unit | `-only-testing:EndoAppTests/HealthImportMergeTests` | ❌ Wave 0 | ⬜ pending |
| 09-03-T1 | 09-03 | 3 | DATA-05 | T-09-14 | Read-only entitlement + NSHealthShareUsageDescription (NO update key); isolated real reader | build/grep | `grep NSHealthShareUsageDescription Info.plist` + `xcodebuild build` | ✅ | ✅ green |
| 09-03-T2 | 09-03 | 3 | DATA-05 | T-09-12/13 | Rationale-before-sheet; passive denial deep-link; provenance badge; Refresh action; health UI consent-gated | build | `xcodebuild build -scheme EndoApp` | ✅ | ✅ green |
| 09-03-T3 | 09-03 | 3 | DATA-05 (SC2/SC3) | T-09-06/15 | Copy ClaimsPolicy-audited; E2E rationale-before-sheet / denied-usable / badge / refresh via fixture seam | unit+E2E | `-only-testing:EndoAppTests/HealthConnectCopyAuditTests -only-testing:EndoAppUITests/HealthConnectUITests` | ✅ | ✅ green |
| 09-03-T4 | 09-03 | 3 | DATA-05 (SC2/SC3) | — | Real-device read + system auth sheet (deferred human-verify, like Phase 4 HUMAN-UAT) | manual | (human-verify checkpoint) | n/a | ⬜ manual (deferred, auto-approved) |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] `HealthDataReading` protocol seam (09-01-T1) + fixture (09-02) / real (09-03) implementations (injectable `HKHealthStore` boundary)
- [x] `-uitest-health-seed` / `-uitest-health-denied` / `-uitest-health-empty` launch-arg fixtures (09-02 fixture + 09-03 wiring; mirror existing `-uitest-reset`/`-uitest-seed-insights`)
- [x] `ImportedSample` + SchemaV5→V6 lightweight-migration survival test (09-01-T3; clones the CachedInsight V4→V5 survival test +1 version)

*Existing Swift Testing + XCUITest infrastructure covers the rest.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real-device HealthKit read (actual menstrual/sleep/workout/nutrition samples) | DATA-05 | HealthKit on the Simulator cannot supply realistic menstrual/sleep/workout data; needs a real device with Health sample data | On a real iPhone with Health data: connect each type, confirm imported values appear labeled "from Apple Health" and flow into insights. Deferred human-verify (same posture as Phase 4 CloudKit). |
| System authorization sheet appearance + denial behavior | DATA-05 (SC2/SC3) | The system HealthKit auth sheet and its denied state are OS-driven and not fully reproducible in CI | Trigger a contextual connect; confirm in-app rationale shows BEFORE the system sheet; deny; confirm app stays fully usable and empty reads never render as "no symptoms". |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies (the human-verify checkpoint is the only manual item, by design)
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references (the `HealthDataReading` seam + fixtures + ImportedSample schema/migration)
- [x] No watch-mode flags
- [x] Feedback latency < 180s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** planner-complete (per-task map populated; Wave-0 = plan 09-01)
