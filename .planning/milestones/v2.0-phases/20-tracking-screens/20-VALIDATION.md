---
phase: 20
slug: tracking-screens
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-09
---

# Phase 20 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Robolectric + Compose UI test (JVM) for screens/round-trips/bodymap semantics; in-memory Room for persistence round-trips; JUnit 4 for manifest/copy/reminder-structure scans; instrumented Compose on the main-only CI emulator lane |
| **Config file** | `android/app/build.gradle.kts` (WorkManager + workmanager-testing added); `android/gradle/libs.versions.toml` |
| **Quick run command** | `cd android && ./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `cd android && ./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` |
| **Estimated runtime** | ~3–5 min |

---

## Sampling Rate

- **After every task commit:** `./gradlew :app:testDebugUnitTest`
- **After every plan wave:** full suite command above
- **Before `/gsd-verify-work`:** full JVM/unit suite green; `:insights` passes (no regression); CopyAudit green over all newly-registered copy
- **Max feedback latency:** ~300 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 20-01-xx | 01 | 1 | ATRK-05 | — | draft/persistence model keeps nil vs real-0 through round-trip | unit(room) | `./gradlew :app:test* --tests *DayLogRoundTrip* *AbsenceNotZero*` | ❌ W0 | ⬜ pending |
| 20-02-xx | 02 | 2 | ATRK-01 | T-20-01 | Daily Check-In nothing mandatory; empty confirm = adherence, no invented data; save suppresses reminder | unit(compose) | `./gradlew :app:test* --tests *CheckIn*` | ❌ W0 | ⬜ pending |
| 20-03-xx | 03 | 3 | ATRK-02 | — | Day Log editor + BodyMap: per-region ≥48dp focusable hotspot w/ TalkBack semantics; severity control | unit(compose) | `./gradlew :app:test* --tests *DayLogEditor* *BodyMap* *Severity*` | ❌ W0 | ⬜ pending |
| 20-04-xx | 04 | 4 | ATRK-03 | — | medication add/archive + treatment state; survive round-trip | unit(compose+room) | `./gradlew :app:test* --tests *Medication* *Settings*` | ❌ W0 | ⬜ pending |
| 20-05-xx | 05 | 5 | ATRK-04 | T-20-02 | reminder inexact only (no exact alarm), JIT POST_NOTIFICATIONS, reboot reschedule, deep-link lands on gate, code physically separate from flare | unit(workmanager+manifest) | `./gradlew :app:test* --tests *Reminder* *ManifestPermissions* *NotificationSeparation*` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky. Exact task IDs assigned by the planner.*

---

## Wave 0 Requirements

- [ ] WorkManager (+ workmanager-testing) added to the catalog and `:app` (first-party androidx — catalog audit stays green)
- [ ] Log-home host that replaces the Phase-19 `tab.log` placeholder + an intra-Log rememberSaveable navigation stack (no NavHost)
- [ ] Test scaffolds: DayLogRoundTripTest, CheckInFlowTest, BodyMapSemanticsTest, MedicationTest, ReminderSchedulingTest, NotificationSeparationTest, ManifestPermissionsTest extension (red until sources land)
- [ ] Register all new UI strings (screen/section/control/enum-label/reminder/settings) in CopyAuditRegistry — dodging ClaimsPolicy substrings (the 3 reworded strings from the UI-SPEC: here→her, stops→stop, treatment→treat)

*The absence≠0 round-trip and the reminder-code/flare-code physical separation are Wave-0-visible acceptances.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Actual notification delivery + tap→check-in on a device | ATRK-04 | Real notification post + deep-link is OS/device-mediated | Enable reminder on an Android 14+ device; wait for/trigger the fire; tap → lands on gate → check-in |
| BodyMap TalkBack sweep + silhouette legibility | ATRK-02 | Screen-reader UX + pixel legibility are experiential | Enable TalkBack; sweep every region hotspot; confirm labels + selected state announced |
| Check-in speed/feel vs iOS | ATRK-01 | Perceived speed is experiential | Log a full day on-device; compare to iOS flow |

*Flow logic, honesty round-trips, bodymap semantics presence, reminder scheduling type, and manifest permissions all have automated JVM verification; only on-device delivery + experiential feel are device-gated.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (WorkManager, log-home host, test scaffolds, copy registration)
- [ ] No watch-mode flags
- [ ] Feedback latency < 300s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
