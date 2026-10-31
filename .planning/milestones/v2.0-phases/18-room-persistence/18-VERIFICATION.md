---
phase: 18-room-persistence
verified: 2026-07-09T00:00:00Z
status: passed
score: 5/5 success criteria verified (APER-01..07 all satisfied)
behavior_unverified: 0
overrides_applied: 0
device_gated_verifications:  # Documented device/host-gated manual runs — NOT gaps (per phase verification method)
  - test: "Run instrumented DeleteDayCascadeTest on an emulator/device"
    expected: "deleteDay() purges all 6 day-scoped tables incl. ImportedSample in one transaction; Medication/TreatmentState/DismissedInsight rows survive"
    why_device: "androidTest requires an Android runtime; this CI host has the SDK but no running emulator. Structural completeness is already JVM-proven by CascadeCompletenessTest; the SQL DELETE behavior is the only device-gated portion."
  - test: "Run instrumented MigrationTest (MigrationTestHelper) on an emulator/device"
    expected: "createDatabase(TEST_DB, 1) opens schemas/1.json against a real SQLite instance without error"
    why_device: "MigrationTestHelper needs an Android SQLite runtime; scaffold + committed 1.json are present and JVM-verified structurally."
  - test: "Confirm OS-level Auto Backup / device-transfer actually excludes endo.db on a real device"
    expected: "Health DB (endo.db + -wal/-shm) never appears in a Google-account backup or device-to-device transfer; DataStore consent prefs do restore"
    why_device: "Backup behavior is an OS runtime observation. Rules XML + manifest wiring + BackupRulesTest (red-capable) are all JVM-verified."
---

# Phase 18: Room Persistence Verification Report

**Phase Goal:** All user data persists locally in Room with the iOS data-lifecycle guarantees — consent-gated, delete-complete, migration-disciplined, backup-excluded — from day one; plus the SnapshotProjector bridge, ClaimsPolicy/CopyAudit harness, and export-format contract.
**Verified:** 2026-07-09
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

Everything JVM-automatable is green and was RUN, not trusted from SUMMARY:
- `./gradlew :insights:test :app:testDebugUnitTest :app:assembleDebug` → **BUILD SUCCESSFUL**, exit 0.
- **:app JVM tests = 63** (0 failures, 0 errors, 0 skipped). **:insights tests = 176** (0 failures). Both match the expected counts exactly.
- iOS tree (`Sources/`, `EndoInsights/`, `project.yml`, `Tests/EndoApp*`) is **completely untouched** across the entire phase-18 commit range (`6769c35^..HEAD`); only `android/`, `.planning/`, `.github/` changed.

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | All 10 models persist behind consent-gated repo; health reads/writes fail pre-consent, succeed post-consent (test-proven) | ✓ VERIFIED | 10 `@Entity` classes present; `EndoRepository.checkConsented()` is first statement of every health method, throws `IllegalStateException` unless `ConsentState.Consented`. `RepositoryConsentTest` (3 tests) iterates every health method under **both** `Unknown` and `NotConsented` (fail-closed) and asserts no-throw under `Consented`. Green. |
| 2 | Delete-day purges all 6 day-scoped rows (incl. ImportedSample) in one transaction; definitions survive; completeness meta-test green | ✓ VERIFIED | `DeleteDayDao.deleteDay` = single `@Transaction` deleting DayEntry/Symptom/Pain/Bleeding/Factor/**ImportedSample** + cache invalidation; Medication/TreatmentState/DismissedInsight absent by design. `CascadeCompletenessTest` reflects DayScoped classes off the classpath and asserts cascade covers **exactly** that set (red-capable, with non-vacuity guard). On-device SQL run is device-gated (documented). |
| 3 | Room v1 discipline: exportSchema on, committed schemas/, migration scaffold, destructive migration banned; health DB backup-excluded with assertion | ✓ VERIFIED | `EndoDatabase(exportSchema=true)`; `room.schemaLocation` KSP arg → committed `android/app/schemas/.../1.json` (optional health columns nullable). `DestructiveMigrationBanTest` scans main sources for `fallbackToDestructiveMigration` (red-capable). `MigrationTest` uses `MigrationTestHelper` (scaffold, device-gated run). `BackupRulesTest` asserts endo.db+wal+shm excluded from cloud-backup AND device-transfer, prefs not excluded. |
| 4 | SnapshotProjector is the ONLY persistence↔domain seam; mapping tests green; free text never crosses (hasNote only) | ✓ VERIFIED | `SnapshotProjector` has **no android/androidx imports** (pure JVM fold); notes reduced to `hasNote: Boolean` (prose never crosses). `SnapshotProjectorTest` = 34 `@Test` (33+ parity cases, exceeds the ~24 target), green. `EndoRepository.daySnapshots()` is consent-gated and delegates to the pure projector. |
| 5 | Kotlin ClaimsPolicy reuses :insights ForbiddenTerms + CopyAudit harness green before UI copy; schema-versioned export contract committed | ✓ VERIFIED | `:insights ForbiddenTerms` widened to `public object`; `:app ClaimsPolicy.causal/imperative/alarmist` delegate to it; `ClaimsPolicyReuseTest` uses `assertSame` (same instance, no fork). `CopyAuditTest` (7 tests) green with empty catalog — harness ready for Phase 19. `ExportEnvelope`+`CryptoHeader` `@Serializable` DTOs committed, schemaVersion field, CachedInsight excluded, format-only. |

**Score:** 5/5 success criteria verified (0 present-behavior-unverified)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `data/entities/*.kt` (10) | 10 Room entities, iOS names, UUID PKs | ✓ VERIFIED | All 10 present; 6 implement `DayScoped` (DayEntry, SymptomReading, PainPoint, BleedingEvent, FactorReading, ImportedSample) |
| `data/EndoDatabase.kt` | v1, exportSchema=true | ✓ VERIFIED | `@Database(version=1, exportSchema=true)` |
| `app/schemas/.../1.json` | committed schema | ✓ VERIFIED | Present, identityHash set, optional columns nullable |
| `data/daos/DeleteDayDao.kt` | one-transaction cascade incl ImportedSample | ✓ VERIFIED | `@Transaction`, 6 deletes + cache clear |
| `data/EndoRepository.kt` | consent-gated facade | ✓ VERIFIED | `checkConsented()` on every method |
| `data/ConsentStore.kt` / `ConsentState.kt` | 3-state, in DataStore outside DB | ✓ VERIFIED | Unknown/NotConsented/Consented; fail-closed |
| `insightsbridge/SnapshotProjector.kt` | pure fold, hasNote only | ✓ VERIFIED | No android imports; notes→hasNote |
| `insightsbridge/FactorKind.kt` | 7 raws | ✓ VERIFIED | food/activity/sleep/medication/stress/mood/other |
| `content/ClaimsPolicy.kt` | reuse :insights ForbiddenTerms | ✓ VERIFIED | Delegating getters, no fork |
| `content/AuditableCopy.kt` + CopyAuditTest | harness before UI copy | ✓ VERIFIED | Registry + scan, green empty |
| `res/xml/data_extraction_rules.xml` | excludes health DB | ✓ VERIFIED | endo.db+wal+shm excluded, prefs retained |
| `export/ExportEnvelope.kt` + `CryptoHeader.kt` | format-only DTOs | ✓ VERIFIED | @Serializable, no crypto impl |

### Key Link Verification

| From | To | Via | Status |
|------|-----|-----|--------|
| settings.gradle include(:app,:insights) | :app build.gradle | one-way `implementation(project(:insights))` | ✓ WIRED |
| EndoDatabase entities | schemas/1.json | KSP room.schemaLocation export | ✓ WIRED |
| DayScoped marker | CascadeCompletenessTest | reflection over classpath = cascade set | ✓ WIRED |
| EndoRepository.checkConsented | ConsentStore.state | throws pre-consent (Unknown fail-closed) | ✓ WIRED |
| EndoRepository.daySnapshots | SnapshotProjector.project | consent-gated delegation | ✓ WIRED |
| :app ClaimsPolicy | :insights ForbiddenTerms | delegating getters (assertSame) | ✓ WIRED |
| AndroidManifest dataExtractionRules | res/xml rules | excludes Room DB | ✓ WIRED |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| :app JVM tests green | `./gradlew :app:testDebugUnitTest` | 63 tests, 0 fail/err/skip | ✓ PASS |
| :insights tests green | `./gradlew :insights:test` | 176 tests, 0 failures | ✓ PASS |
| App assembles | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL | ✓ PASS |
| No android imports in bridge | grep insightsbridge | none | ✓ PASS |
| No crypto impl in export DTOs | grep javax.crypto/Cipher/encrypt (non-comment) | none | ✓ PASS |
| No INTERNET permission | grep manifest | none | ✓ PASS |
| iOS tree untouched | git diff 6769c35^..HEAD | android/.planning/.github only | ✓ PASS |
| version-catalog audit + boundary lint | insights test results | 1/1 each, 0 failures | ✓ PASS |

### Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| APER-01 | 10 models in Room behind consent-gated repo | ✓ SATISFIED | 10 entities + EndoRepository consent gate + RepositoryConsentTest |
| APER-02 | Delete-day purges all day-scoped rows; definitions survive | ✓ SATISFIED | DeleteDayDao @Transaction (incl ImportedSample) + CascadeCompletenessTest |
| APER-03 | Migration discipline v1, destructive banned | ✓ SATISFIED | exportSchema + schemas/1.json + MigrationTestHelper + DestructiveMigrationBanTest |
| APER-04 | Health DB backup-excluded with assertion | ✓ SATISFIED | data_extraction_rules.xml + BackupRulesTest |
| APER-05 | SnapshotProjector only seam, free text never crosses | ✓ SATISFIED | Pure projector + hasNote + 33-case suite |
| APER-06 | ClaimsPolicy port + CopyAudit before UI copy | ✓ SATISFIED | ClaimsPolicy reuse (assertSame) + CopyAudit harness |
| APER-07 | Export format contract, schema-versioned | ✓ SATISFIED | ExportEnvelope + CryptoHeader DTOs, format-only |

### Anti-Patterns Found

None. Crypto keyword matches in `export/*.kt` are all in KDoc comments explicitly disclaiming implementation (format-only by design). No TBD/FIXME/XXX debt markers in phase files. Export DTOs' empty-list defaults are DTO shape defaults (Phase 23 fills them), not hollow rendering stubs.

### Device-Gated Verification (documented, NOT gaps)

Three items require an Android runtime that this CI host lacks (SDK present, no running emulator; OS backup is a runtime behavior). Per the phase verification method these are documented device/host-gated manual verifications, not gaps — every JVM-automatable equivalent is already green:
1. Instrumented `DeleteDayCascadeTest` on-device (structural completeness already JVM-proven).
2. Instrumented `MigrationTest` / `MigrationTestHelper` run (scaffold + committed 1.json present).
3. OS-level Auto Backup / device-transfer exclusion of endo.db (rules + manifest + BackupRulesTest verified).

### Gaps Summary

None. All 5 ROADMAP success criteria and all 7 requirements (APER-01..07) are satisfied by shipped, wired, tested artifacts. 239 JVM tests green (63 :app + 176 :insights), assembleDebug succeeds, iOS tree untouched, no INTERNET permission, export contract is format-only. The only outstanding items are pre-planned emulator/OS-runtime verifications, explicitly classified as device-gated (not gaps) by the phase's own verification method.

---

_Verified: 2026-07-09_
_Verifier: Claude (gsd-verifier)_
