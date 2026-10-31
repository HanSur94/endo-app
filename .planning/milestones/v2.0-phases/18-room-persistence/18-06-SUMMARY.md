---
phase: 18-room-persistence
plan: 06
subsystem: data
tags: [android, backup, data-extraction-rules, export, kotlinx-serialization, crypto-contract, gdpr, junit4]

# Dependency graph
requires:
  - phase: 18-room-persistence
    plan: 01
    provides: ":app Android Application module (kotlinx-serialization plugin + json dep, JUnit4 test lane)"
  - phase: 18-room-persistence
    plan: 02
    provides: "10 Room @Entity types + EndoDatabase.DB_NAME (endo.db) — the field source the export DTOs mirror and the DB name the backup rules exclude"
provides:
  - "data_extraction_rules.xml (API 31+) + full_backup_content.xml (API 26-30) excluding the Room health DB (+ -wal/-shm) from cloud Auto Backup AND device transfer; DataStore consent prefs stay backup-eligible"
  - "AndroidManifest wired to both backup rules; still zero permissions (no INTERNET)"
  - "BackupRulesTest — pure-JVM XML-parse assertion pinning the DB exclusion (red-capable)"
  - "ExportEnvelope + 9 per-entity @Serializable DTOs (versioned, UUID id, shared enum raws, yyyy-MM-dd days, ISO instants, nullable health values) — derived insight cache EXCLUDED; imports no Room type"
  - "CryptoHeader + KdfParams @Serializable crypto-header SPEC (contract only — no encrypt/decrypt/SAF/javax.crypto)"
  - "ExportFormatTest — JSON round-trip + golden-shape + KdfParams-defaults + no-crypto-implementation source guard"
affects: [19-ui-shell, 23-export-import]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Backup exclusion pinned by a pure-JVM XML-parse test (DocumentBuilderFactory) using the walk-up file-resolution pattern shared with SchemaExportTest"
    - "Export DTOs are a standalone contract layer — no Room entity import; day identity as yyyy-MM-dd String, timestamps as ISO-8601 instants, absence stays null"
    - "Crypto header is a versioned params-read-from-file SPEC; a source-scan test guards that no crypto implementation leaks into the contract phase"

key-files:
  created:
    - android/app/src/main/res/xml/data_extraction_rules.xml
    - android/app/src/main/res/xml/full_backup_content.xml
    - android/app/src/test/kotlin/com/endoapp/app/data/BackupRulesTest.kt
    - android/app/src/main/kotlin/com/endoapp/app/export/ExportEnvelope.kt
    - android/app/src/main/kotlin/com/endoapp/app/export/CryptoHeader.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/ExportFormatTest.kt
  modified:
    - android/app/src/main/AndroidManifest.xml

key-decisions:
  - "Added full_backup_content.xml (legacy Auto Backup, API 26-30) alongside the API-31+ data_extraction_rules.xml — the plan left it optional ('if desired'), but minSdk 26 means pre-12 devices are in scope, so excluding the health DB from legacy Auto Backup too is a privacy correctness requirement, not a nicety"
  - "BackupRulesTest references EndoDatabase.DB_NAME (a compile-time const val, inlined) so the assertion is tied to the real DB name without loading the Android-dependent EndoDatabase class in the JVM test"
  - "Export day fields serialize as yyyy-MM-dd Strings (not epochDay Long) and timestamps as full ISO-8601 instants — the platform-neutral wire form Phase 23 imports; LocalDate.parse/Instant.parse in the test enforce strictness"
  - "no-crypto guard scans for implementation TOKENS (import javax.crypto, Cipher.getInstance, SecretKeyFactory, .doFinal(, GCMParameterSpec, PBEKeySpec) rather than prose, so the header KDoc may describe the Phase-23 crypto without tripping the gate"

requirements-completed: [APER-04, APER-07]

# Metrics
duration: ~20min
completed: 2026-07-09
status: complete
---

# Phase 18 Plan 06: Backup Exclusion + Export FORMAT Contract Summary

**Closed the two remaining data-lifecycle contracts: (APER-04) the Room health DB is now provably excluded from Android Auto Backup AND device transfer via `data_extraction_rules.xml` (API 31+) + `full_backup_content.xml` (API 26-30), wired from the manifest and pinned by a red-capable `BackupRulesTest`, while the DataStore consent prefs stay backup-eligible; and (APER-07) the versioned, platform-neutral export FORMAT is committed as standalone `@Serializable` DTOs — `ExportEnvelope` + 9 per-entity DTOs (UUID id, shared enum raws, `yyyy-MM-dd` days, ISO instants, nullable health values, derived insight cache EXCLUDED) + a `CryptoHeader`/`KdfParams` crypto SPEC (contract only) — with a golden-shape round-trip test and a source guard proving NO crypto implementation exists this phase. `:app:testDebugUnitTest :app:assembleDebug` is green for real (63 tests, 0 failures).**

## Performance
- **Duration:** ~20 min
- **Completed:** 2026-07-09
- **Tasks:** 2
- **Files:** 6 created, 1 modified

## Accomplishments
- **Backup exclusion (APER-04, threat T-18-03a):** `data_extraction_rules.xml` excludes `endo.db` + `endo.db-wal` + `endo.db-shm` (domain="database") from BOTH `<cloud-backup>` and `<device-transfer>`; `full_backup_content.xml` mirrors the exclusion for pre-12 legacy Auto Backup. The DataStore consent/prefs file is deliberately NOT excluded (it gates the DB but carries no health rows). Manifest wired via `android:dataExtractionRules="@xml/data_extraction_rules"` and `android:fullBackupContent="@xml/full_backup_content"`; no INTERNET (or any) permission added.
- **`BackupRulesTest` (2 tests, red-capable):** parses the rules XML with `DocumentBuilderFactory` and asserts (a) the DB + WAL/SHM sidecars appear in the exclude set of both cloud-backup and device-transfer, and (b) the consent/prefs file is never excluded and only the DB family is excluded. Keyed off `EndoDatabase.DB_NAME`.
- **Export FORMAT (APER-07):** `ExportEnvelope(schemaVersion=1, exportedAt: ISO-8601, …9 per-entity arrays…)` plus `DayEntryDto, SymptomReadingDto, PainPointDto, BleedingEventDto, FactorReadingDto, MedicationDto, TreatmentStateDto, ImportedSampleDto, DismissedInsightDto` — each field mirrored from the Room entity, `id` a UUID String, enum-backed fields as the shared `*Raw` strings, day identity as `yyyy-MM-dd`, timestamps as full ISO instants, every optional health value nullable. Standalone contract — imports NO Room entity type. The derived insight cache is EXCLUDED (recomputable; threat T-18-13).
- **Crypto header SPEC (contract only, threat T-18-03b):** `CryptoHeader(magic, formatVersion, kdf, ivBase64)` + `KdfParams(alg=PBKDF2WithHmacSHA256, iterations=600_000, saltBase64, keyBits=256)` — params read-from-file, fresh salt+IV per export documented as the Phase-23 invariant. NO `javax.crypto`, no cipher, no SAF, no file I/O.
- **`ExportFormatTest` (7 tests):** lossless JSON round-trip for the envelope and the header; golden shape (schemaVersion present, `exportedAt`/timestamps parse as `Instant`, all ids parse as `UUID`, all day fields strict `LocalDate`); a nullable health value (severity/clots/level) stays `null` through the trip; the derived cache is absent from the serialized JSON; `KdfParams` defaults match the spec; and a source-scan guard asserting the two export files contain no crypto-implementation tokens.

## Task Commits
1. **Task 1: Backup-exclusion rules + manifest wiring + assertion test** — `8977c06` (feat)
2. **Task 2: Export FORMAT DTOs (ExportEnvelope + CryptoHeader) + golden-shape test** — `8b7f731` (feat)

_No STATE.md / ROADMAP.md metadata commit — the orchestrator owns state updates for this run (per objective)._

## Real Build/Test Results (this host, Android SDK present)
- `./gradlew :app:testDebugUnitTest --tests "*BackupRulesTest"` — BUILD SUCCESSFUL (2/2).
- `./gradlew :app:testDebugUnitTest --tests "*ExportFormatTest"` — BUILD SUCCESSFUL (7/7).
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` — BUILD SUCCESSFUL; full JVM suite **63 tests, 0 failures, 0 errors, 0 skipped** across 10 classes (ModuleSmokeTest, ClaimsPolicyReuseTest, CopyAuditTest, BackupRulesTest, CascadeCompletenessTest, DestructiveMigrationBanTest, RepositoryConsentTest, SchemaExportTest, ExportFormatTest, SnapshotProjectorTest); `app-debug.apk` produced.
- Manifest gates: `grep -q dataExtractionRules` PASS; `! grep -qi android.permission.INTERNET` PASS.
- Contract gate: `! grep -rni cachedinsight export/ExportEnvelope.kt` PASS (derived cache token absent from the envelope source).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical functionality] Added pre-12 legacy Auto Backup parity**
- **Found during:** Task 1
- **Issue:** The plan made `fullBackupContent` optional ("if desired"), but `minSdk` is 26 — API 26-30 devices (which ignore `dataExtractionRules`) would still Auto-Back-Up the health DB via legacy `fullBackupContent`.
- **Fix:** Added `res/xml/full_backup_content.xml` excluding `endo.db` (+ WAL/SHM) and wired `android:fullBackupContent`. The privacy invariant now holds across the whole minSdk-26 range, not just API 31+.
- **Files modified:** `android/app/src/main/res/xml/full_backup_content.xml` (created), `AndroidManifest.xml`.
- **Commit:** `8977c06`

**2. [Rule 3 - Blocking issue] Manifest comment tripped the no-INTERNET grep gate**
- **Found during:** Task 1
- **Issue:** The Phase-18-01 manifest comment literally contained the string `android.permission.INTERNET` (as prose: "critically NO android.permission.INTERNET"), which trips the plan's `! grep -qi "android.permission.INTERNET"` verification gate — a false positive on documentation, not a real permission.
- **Fix:** Reworded the comment to "NO network/INTERNET permission" so the gate measures real `<uses-permission>` declarations, not explanatory prose. No functional change. (Same class of structural-test-hygiene fix as Plan 02's doc-comment reword.)
- **Files modified:** `AndroidManifest.xml`.
- **Commit:** `8977c06`

## Threat Model Compliance
- **T-18-03a (health DB copied to Auto Backup / device transfer, high):** mitigated — DB + WAL/SHM excluded from cloud-backup AND device-transfer (+ legacy full-backup parity); `BackupRulesTest` asserts it (red-capable); consent prefs may back up.
- **T-18-03b (weak/nonce-reused export crypto, high):** mitigated (contract) — `CryptoHeader`/`KdfParams` mandate PBKDF2WithHmacSHA256 ≥600k, AES/GCM/NoPadding 128-bit tag, fresh 16-byte salt + 12-byte IV per export, params read-from-file; `javax.crypto` implementation is Phase 23. A source guard proves no implementation leaked in this phase.
- **T-18-13 (derived cache leaking into export, low):** mitigated — the recomputable insight cache is excluded from `ExportEnvelope`; a grep gate + a serialized-JSON assertion confirm its absence.
- **T-18-04b (INTERNET permission enabling silent upload, high):** mitigated — no INTERNET permission; Task 1 grep gate green (the full no-INTERNET manifest assertion lands Phase 19).

## Known Stubs
None. `CryptoHeader`/`KdfParams` are an intentional, documented CONTRACT (not a stub) — Phase 23 implements encrypt/decrypt/SAF and reads these params from the file, exactly as scoped by CONTEXT/RESEARCH.

## Next Plan Readiness
- Phase 23 (export/import) can proceed mechanically: it encrypts the `ExportEnvelope` JSON under a key derived per `KdfParams`, prepends the cleartext `CryptoHeader`, and imports idempotently by UUID `id`. The UUID identity columns from Plan 02 are now justified by this contract.
- Phase 19 (UI shell) inherits the wired backup rules and the clean, permission-free manifest baseline for its no-INTERNET manifest assertion.
- No blockers.

## Self-Check: PASSED
All 6 created files and the modified `AndroidManifest.xml` exist on disk; both task commits (`8977c06`, `8b7f731`) are in git history; `:app:testDebugUnitTest :app:assembleDebug` verified green (63 tests, 0 failures) on this host.

---
*Phase: 18-room-persistence*
*Completed: 2026-07-09*
