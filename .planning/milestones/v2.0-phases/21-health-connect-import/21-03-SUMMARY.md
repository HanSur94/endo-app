---
phase: 21-health-connect-import
plan: 03
subsystem: health-connect
status: complete
tags: [health-connect, import-coordinator, idempotency, deletion-propagation, changes-token, nil-honest, provenance, consent-gate, tdd]
requires:
  - "21-02 — HealthReader seam (ReadResult with tokenExpired boolean), pure record→ImportedSample mapper (stableId), HealthConnectReader adapter"
  - "18-04 — ImportedSample (hcRecordId unique index), ImportedSignals (sleep-Σ / workout-longest), HealthDomain; SnapshotProjector manual-wins/nil-honest merge (fed, not rebuilt)"
  - "18-03 — EndoRepository consent gate (checkConsented fail-closed), upsertImported gated write"
provides:
  - "SymptomDao.deleteImportedByHcRecordId — the HC-deletion-propagation DAO query"
  - "EndoRepository.deleteImportedByHcRecordId — consent-gated (fail-closed) deletion path"
  - "ChangesTokenStore — per-domain changes cursor in its own Preferences DataStore (iOS HealthAnchorStore analog), imports no Room/entity type"
  - "HealthImportCoordinator — consent-gated read→persist→advance-cursor orchestrator (iOS HealthImportService analog); ImportOutcome (granted perms + fullHistoryGranted for AHC-04)"
  - "FakeHealthReader — the scripted JVM HealthReader double (per-domain ReadResult queues; EMPTY models denied/granted-empty identically)"
affects:
  - "Plan 04 renders import states + the provenance ('from Health Connect') badge + the AHC-04 window label (full-history vs 30-day) off ImportOutcome"
  - "Plan 05 triggers importAll() from the foreground UI (no WorkManager); wires the real HealthConnectReader + DataStore-backed ChangesTokenStore"
tech-stack:
  added: []
  patterns:
    - "Differential changes-token sync: read since a persisted per-domain cursor; advance the cursor ONLY after a successful persist (no data loss on mid-sync failure)"
    - "Idempotent import by hcRecordId — deterministic stableId + REPLACE collapses both the primary key and the unique index on re-import (sums do not double)"
    - "Deletion propagation — a DeletionChange → deleteImportedByHcRecordId removes the row on re-sync"
    - "Cursor state lives OUTSIDE the Room health DB (its own DataStore file), excluded from the export contract (device-runtime state, not health data)"
    - "Scripted injectable-fake reader (per-domain ReadResult queues) makes import/merge/idempotency/deletion/expiry JVM-testable with no emulator"
    - "Belt+suspenders consent: repo checkConsented throws AND the coordinator refuses to run (never reads) pre-consent"
key-files:
  created:
    - "android/app/src/main/kotlin/com/endoapp/app/health/ChangesTokenStore.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/health/HealthImportCoordinator.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/health/FakeHealthReader.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/health/HealthImportCoordinatorTest.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/health/ImportedSampleDaoTest.kt"
  modified:
    - "android/app/src/main/kotlin/com/endoapp/app/data/daos/SymptomDao.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/data/EndoRepository.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/data/RepositoryConsentTest.kt"
decisions:
  - "Coordinator injects ConsentStore for the belt+suspenders pre-consent refusal (fails closed BEFORE any read), on top of the repo's checkConsented throw — the plan's sanctioned 'consent check seam' option."
  - "Provenance is proven at the row-identity level (hcRecordId + domainRaw retained), NOT a dataOrigin package string — ImportedSample has no provenance column (documented 21-02 deviation #2). The 'from Health Connect' label is derived from a value's imported-origin at the Plan-04 UI, unchanged."
  - "Token-expiry uses the ReadResult.tokenExpired boolean (21-02 decision — connect-client 1.1.0 has no ChangesTokenExpiredException); the coordinator's clear+return is the single reseed point."
  - "ChangesTokenStore imports only the pure HealthDomain enum (a key selector) — no Room/entity/DB type (grep-clean like ConsentStore/ReminderSettings)."
requirements: [AHC-03, AHC-04]
metrics:
  duration: "~35m"
  completed: "2026-07-09"
  tasks: 2
  files_created: 5
  files_modified: 3
  app_tests: 215
---

# Phase 21 Plan 03: Import Coordinator + Deletion/Idempotency/Token Store Summary

Built the consent-gated `HealthImportCoordinator` that turns `HealthReader` output into persisted `ImportedSample` rows through the Phase-18 `EndoRepository` — with true idempotency (deterministic `stableId` + REPLACE), HC-deletion propagation (a new `deleteImportedByHcRecordId` DAO + gated repo method), nil-honest handling of denied/empty reads, retained provenance, and an incremental per-domain changes-token cursor stored OUTSIDE the health DB (`ChangesTokenStore`). The `FakeHealthReader` makes the whole data-integrity core JVM-testable on a real in-memory Room + DataStore lane, no emulator. All five must-have truths are proven RUNNABLY (idempotency, deletion, nil-honest/manual-wins, provenance, token-expiry reseed) — verified on the real Android SDK.

## What Was Built

**Task 1 — delete-by-hcRecordId DAO + gated repo method + ChangesTokenStore (TDD)**
- `SymptomDao.deleteImportedByHcRecordId(hcRecordId)` — `DELETE FROM ImportedSample WHERE hcRecordId = :hcRecordId`. The deletion half of differential sync: an `UpsertionChange` re-imports idempotently, a `DeletionChange` removes here.
- `EndoRepository.deleteImportedByHcRecordId(hcRecordId)` — gates FIRST (`checkConsented()` throws under `Unknown`/`NotConsented`), then delegates — the same fail-closed posture as `upsertImported`.
- `ChangesTokenStore` — a per-domain opaque cursor over its OWN Preferences `DataStore` (`FILE_NAME = "health_changes_tokens"`), mirroring `ReminderSettings`. `token(domain)` (null unseeded), `setToken(domain, token?)` (null removes), `clear(domain)`. Imports only the pure `HealthDomain` enum — no Room/entity/DB type. The iOS `HealthAnchorStore` analog; excluded from the export contract (device-runtime state, not health data).
- `ImportedSampleDaoTest` (Robolectric + in-memory Room + real DataStore): delete removes exactly the matching row / an absent id is a no-op / the token store round-trips per-domain, isolates domains, and clears. `RepositoryConsentTest` extended so `deleteImportedByHcRecordId` is proven to throw under `Unknown` AND `NotConsented` and not under `Consented`.

**Task 2 — HealthImportCoordinator + FakeHealthReader (TDD)**
- `HealthImportCoordinator(reader, repository, changesTokenStore, consent)` — off-main-thread (`withContext(Dispatchers.Default)`). `importAll()`: `check` consent first (fail closed before any read); for each `HealthDomain` read the persisted cursor → `reader.read(domain, token)` → on `tokenExpired` `clear(domain)` and skip (re-seed, no partial advance) → else `upsertImported` each row (idempotent) → `deleteImportedByHcRecordId` each deletion → advance the cursor via `setToken` ONLY after the persist. Returns `ImportOutcome(grantedPermissions, fullHistoryGranted)` — `fullHistoryGranted = granted.contains(HISTORY)` drives the AHC-04 window label. No WorkManager, no notification API (T-20-02d separation invariant).
- `FakeHealthReader` — a scripted `HealthReader`: per-domain `ArrayDeque<ReadResult>`; each `read` pops the next; a drained/absent queue yields `EMPTY` (models denied-or-granted-but-empty identically, the iOS `.empty` fixture). A scripted `ReadResult(tokenExpired = true)` reproduces a stale cursor. `calls` records every `(domain, token)` for assertions.
- `HealthImportCoordinatorTest` — 9 cases against `FakeHealthReader` + a REAL in-memory Room `EndoRepository` (consented) + a REAL DataStore `ChangesTokenStore`.

## Real Test Results (this host, Android SDK platform 36, JDK 21)
- `./gradlew :app:testDebugUnitTest --tests "*ImportedSampleDaoTest" --tests "*RepositoryConsentTest"` — **GREEN** (DAO delete + token round-trip + extended consent coverage).
- `./gradlew :app:testDebugUnitTest --tests "*HealthImportCoordinatorTest"` — **GREEN, 9/9, 0 failures**:
  - `idempotentReimport_oneRowPerHcRecordId_sleepSumDoesNotDouble` — same seed read twice → exactly 1 row per hcRecordId; merged sleep Σ = 8.0 (not 16.0).
  - `deletionChange_propagates_removesTheImportedRow` — a DeletionChange on re-sync removes the row.
  - `deniedOrEmptyRead_persistsNothing_leavesManualUntouched` — empty read imports nothing; manual sleep 6.5 untouched (no fabricated 0).
  - `manualWins_manualBeatsImported_throughPhase18Merge` — imported row persists (provenance) but the snapshot shows the MANUAL 6.5.
  - `provenance_importedRowKeepsHealthConnectIdentity` — persisted row retains `hcRecordId` + `domainRaw`.
  - `tokenExpiry_clearsCursor_completesWithoutCrash_thenReseeds` — expiry clears the cursor, no crash; a subsequent seed re-imports + persists the fresh cursor.
  - `tokenAdvancesOnlyAfterSuccessfulPersist` — cursor advances to `nextToken` only after the persist.
  - `windowLabel_reflectsGrantedHistory_ahc04` — HISTORY granted → `fullHistoryGranted`; absent → capped.
  - `refusesToRunPreConsent_beltAndSuspenders` — throws pre-consent and NEVER reads (`reader.calls` empty).
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :insights:test` — **BUILD SUCCESSFUL**; full app unit suite **215 tests, 0 failures / 0 errors** (202 from 21-02 + 13 new); `:insights` **176 tests, 0 failures**; `assembleDebug` SUCCESSFUL. No regression.
- Separation invariant: `NotificationSeparationTest` **3/3 GREEN** — the coordinator + ChangesTokenStore import no notification/alarm/WorkManager API (grep-verified in `HealthImportCoordinator.kt`).
- Seam check: `ChangesTokenStore.kt` imports no Room/entity/DB type (grep-clean).

### RED-first proven (not fabricated)
- Task 1: `ImportedSampleDaoTest` + the `RepositoryConsentTest` extension were authored and run FIRST — the RED run failed with exactly `Unresolved reference 'ChangesTokenStore'`, `Unresolved reference 'deleteImportedByHcRecordId'`, and `'deleteImportedByHcRecordId' overrides nothing` (the only missing symbols). GREEN after the DAO/repo/store impl. Commit `34a64c5` (test RED) precedes `575487a` (impl GREEN).
- Task 2: `HealthImportCoordinatorTest` + `FakeHealthReader` run FIRST — the RED run failed with exactly `Unresolved reference 'HealthImportCoordinator'` (the sole missing symbol; `FakeHealthReader`, `ReadResult`, `HealthConnectPermissions`, the repo, and the token store all resolved). GREEN after the coordinator impl. Commit `44bbdd0` (test RED) precedes `1fb70c6` (impl GREEN).

## Deviations from Plan

**1. [Rule 3 — carried-forward, provenance proven at row-identity level] No dataOrigin column.**
- **Issue:** The plan's must-haves / Task 2 say "imported rows carry provenance via `dataOrigin`" and "a persisted row exposes its dataOrigin for the badge." `ImportedSample` has NO `dataOrigin`/provenance column — established and documented in the 21-02 SUMMARY (deviation #2): the HC `dataOrigin.packageName` is consumed at the adapter (own-package guard) but not persisted; adding a column would be a Rule 4 schema change.
- **Fix:** Provenance is proven honestly at the level the schema supports — the persisted row retains its Health-Connect identity (`hcRecordId` + `domainRaw`), and the manual-wins merge routes an imported value as imported-origin (surfaced as the "from Health Connect" badge at the Plan-04 UI). No schema column added. `provenance_importedRowKeepsHealthConnectIdentity` pins it.

**2. [Plan-sanctioned option chosen] Coordinator injects ConsentStore for the belt+suspenders gate.**
- The plan offered "accept a consent check seam OR guard via the repo throw." Chose the consent-check seam (`check(consent.state.value is Consented)`) so the coordinator refuses to run — and never reads — pre-consent, on top of the repo's `checkConsented()` throw. `refusesToRunPreConsent_beltAndSuspenders` proves no read happens before consent.

Otherwise the plan executed as written. No auth gates. No package installs.

## Known Stubs
None. `HealthImportCoordinator` + `ChangesTokenStore` are real, compiled, and behaviorally proven on the JVM lane. Plan 05 wires the real `HealthConnectReader` + a DataStore-backed `ChangesTokenStore` and triggers `importAll()` from the foreground UI.

## Threat Model Compliance
- **T-21-02 (Tampering — import + merge integrity, high):** mitigated — deterministic `stableId` + REPLACE ⇒ idempotent re-import (sleep Σ does not double, one row per hcRecordId); the manual-wins/nil-honest merge is the unchanged Phase-18 SnapshotProjector (fed, not rebuilt); `DeletionChange → deleteImportedByHcRecordId` propagates HC deletions; provenance (hcRecordId + domainRaw) retained. Proven via `FakeHealthReader` + in-memory Room (idempotency, deletion, manual-wins, provenance tests).
- **T-21-02d (Elevation of Privilege — consent gate, high):** mitigated — every persistence path is `EndoRepository.checkConsented`; the new `deleteImportedByHcRecordId` is gated + proven in `RepositoryConsentTest` (throws under Unknown/NotConsented); the coordinator refuses to run pre-consent (belt+suspenders, `refusesToRunPreConsent` proves no read fires).
- **T-21-02e (Denial of Service — token expiry / rate limits, medium):** mitigated — differential changes-token sync (not full re-reads); `ReadResult.tokenExpired → clear(domain)` + bounded re-seed; the cursor advances only after a successful persist (no data loss, no crash). `tokenExpiry_clearsCursor...` + `tokenAdvancesOnlyAfterSuccessfulPersist` pin it.
- **T-21-02f (Information disclosure — cursor outside the health DB, low):** mitigated — `ChangesTokenStore` lives in its own Preferences DataStore, imports no Room/entity type, excluded from the export contract (device-runtime state). Grep-verified.

## Threat Flags
None. No new security surface beyond the plan's threat_model (no network, no new permission, no schema change, no notification/WorkManager API).

## Self-Check: PASSED
- All 5 created files exist on disk (`ChangesTokenStore.kt`, `HealthImportCoordinator.kt`, `FakeHealthReader.kt`, `HealthImportCoordinatorTest.kt`, `ImportedSampleDaoTest.kt`); 3 modified (`SymptomDao.kt`, `EndoRepository.kt`, `RepositoryConsentTest.kt`).
- All 4 task commits present: `34a64c5` (T1 test RED), `575487a` (T1 impl GREEN), `44bbdd0` (T2 test RED), `1fb70c6` (T2 impl GREEN).
- Real build/test results captured above (app 215 / 0 failures, insights 176 / 0, coordinator 9/9, NotificationSeparationTest 3/3, `assembleDebug` SUCCESSFUL) — not fabricated.
