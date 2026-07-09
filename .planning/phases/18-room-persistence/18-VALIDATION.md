---
phase: 18
slug: room-persistence
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-07-09
---

# Phase 18 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 (`:app` Android unit + Robolectric where a Context is needed) + JUnit Jupiter 6.1.1 (pure-JVM `SnapshotProjector` / ClaimsPolicy tests); Room `MigrationTestHelper` (androidTest) |
| **Config file** | `android/app/build.gradle.kts` (Room + KSP + DataStore + test wiring); `android/gradle/libs.versions.toml` |
| **Quick run command** | `cd android && ./gradlew :app:testDebugUnitTest` |
| **Full suite command** | `cd android && ./gradlew :insights:test :app:testDebugUnitTest :app:check` |
| **Estimated runtime** | ~1–3 min (unit + Robolectric); migration androidTest is emulator-gated |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :app:testDebugUnitTest`
- **After every plan wave:** Run the full suite command above
- **Before `/gsd-verify-work`:** Full JVM/unit suite green; `:insights` suite still 176-green (no regression)
- **Max feedback latency:** ~180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 18-01-xx | 01 | 1 | APER-01 | — | `:app` module + Room 2.8.4/KSP wire; version-catalog audit still green | unit | `./gradlew :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |
| 18-02-xx | 02 | 2 | APER-01, APER-03 | T-18-01 | 10 entities + DAOs; `exportSchema` on, `schemas/` committed; no `fallbackToDestructiveMigration` | unit | `./gradlew :app:test* --tests *SchemaExportTest *DestructiveMigrationBanTest` | ❌ W0 | ⬜ pending |
| 18-03-xx | 03 | 3 | APER-02 | — | delete-day purges ALL 6 day-scoped rows incl. ImportedSample; Medication/TreatmentState survive; reflection meta-test | unit | `./gradlew :app:test* --tests *DeleteDayCascade* *CascadeCompleteness*` | ❌ W0 | ⬜ pending |
| 18-03-xx | 03 | 3 | APER-01 | T-18-02 | repository throws/empties pre-consent, serves post-consent | unit | `./gradlew :app:test* --tests *ConsentGate*` | ❌ W0 | ⬜ pending |
| 18-04-xx | 04 | 4 | APER-05 | — | SnapshotProjector pure fold matches iOS daySnapshots() (~24 cases); notes → hasNote only | unit | `./gradlew :app:test* --tests *SnapshotProjector*` | ❌ W0 | ⬜ pending |
| 18-05-xx | 05 | 5 | APER-06 | — | ClaimsPolicy reuses :insights ForbiddenTerms; CopyAudit scans registered catalog; drift red on divergence | unit | `./gradlew :app:test* --tests *CopyAudit* *ClaimsPolicy*` | ❌ W0 | ⬜ pending |
| 18-06-xx | 06 | 6 | APER-04, APER-07 | T-18-03 | Room DB excluded from Auto Backup (assertion); versioned ExportEnvelope + CryptoHeader DTO round-trips | unit | `./gradlew :app:test* --tests *BackupRules* *ExportFormat*` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky. Exact task IDs assigned by the planner.*

---

## Wave 0 Requirements

- [ ] `:app` module skeleton (`com.android.application`, built-in Kotlin — NO `org.jetbrains.kotlin.android` under AGP 9), Room+KSP+DataStore added to the catalog, minimal manifest
- [ ] `RoomDatabase` + entity/DAO scaffolds so `MigrationTestHelper` and schema export have a target
- [ ] `DeleteDayCascadeTest`, `CascadeCompletenessTest`, `ConsentGateTest`, `SnapshotProjectorTest`, `CopyAuditTest`, `BackupRulesTest`, `ExportFormatTest` scaffolds (red until sources land)
- [ ] iOS `daySnapshots()` golden fixtures captured for the projector parity suite (mirror the ~24 iOS cases)

*The ImportedSample cascade inclusion (beyond the iOS omission) is a Wave-0-visible acceptance in CascadeCompletenessTest.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Room migration on a real device/emulator | APER-03 | `MigrationTestHelper` needs an Android runtime; not a pure-JVM unit test | Run `./gradlew :app:connectedCheck` on an emulator once available |
| Auto Backup actually excludes the DB on-device | APER-04 | True backup behavior is OS-mediated; the rules file is asserted in-unit but end-to-end needs a device | Trigger a backup on-device, confirm DB not restored |

*All schema/cascade/consent/projection/copy-audit/format behaviors have automated JVM/unit verification; only true Android-runtime migration + OS backup are device-gated.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (:app module, Room scaffolds, daySnapshots goldens)
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
