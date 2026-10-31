---
phase: 21-health-connect-import
plan: 02
subsystem: health-connect
status: complete
tags: [health-connect, mapper, health-reader, bucketing, idempotency, defensive-decode, tdd, read-only]
requires:
  - "21-01 — connect-client 1.1.0 in :app, HealthConnectPermissions, availability gate, dual rationale"
  - "18-04 — ImportedSample (hcRecordId unique index), ImportedSignals (sleep-Σ→band / workout-longest-type), HealthDomain (cycle/sleep/workouts/nutrition)"
  - "17 — :insights FlowIntensity/ActivityType/SleepQuality raws (the mapper emits these raw strings)"
provides:
  - "HealthReader — the injectable read seam (interface + ReadResult), NO androidx.health.* type in the signature"
  - "HealthRecordMapping — the pure record→ImportedSample mapper + shared epochDay bucketing + stableId (iOS HealthSampleMapping analog)"
  - "HealthConnectReader — the real thin HealthConnectClient adapter (changes-token differential sync, own-package guard, per-session grant read)"
  - "HealthRecordMappingTest — 15 pure-JVM cases: 4 field maps + defensive decode + deterministic stableId + DST/travel/midnight bucketing + SDK-constant parity"
affects:
  - "Plan 03 injects FakeHealthReader against this seam to prove import/merge/idempotency/deletion; the coordinator owns the changes-token store + reseed policy"
  - "Plan 04 renders import states; provenance labelling (dataOrigin) surfaces there"
tech-stack:
  added: []
  patterns:
    - "Value-in/value-out pure mapper (no HealthConnectClient, no androidx.health.* import) — every field map + bucketing vector unit-testable off plain values, no emulator"
    - "Injectable HealthReader seam maps records to ImportedSample INSIDE the boundary → import JVM-testable off a fake"
    - "epochDay = startTime.atZone(deviceZone).toLocalDate().toEpochDay() — the single iOS Calendar.startOfDay parity rule"
    - "stableId = UUID.nameUUIDFromBytes(\"hc:<id>\") for byte-identical re-import"
    - "Assumed SDK ints as named constants, pinned to the real androidx.health.* constants by an SDK-parity test (assumption → test-enforced fact)"
    - "Token expiry via ChangesResponse.changesTokenExpired boolean (connect-client 1.1.0 has no ChangesTokenExpiredException)"
key-files:
  created:
    - "android/app/src/main/kotlin/com/endoapp/app/health/HealthReader.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/health/HealthRecordMapping.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/health/HealthConnectReader.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/health/HealthRecordMappingTest.kt"
  modified: []
decisions:
  - "A1/A2/A3 integer constants CONFIRMED at impl against the connect-client 1.1.0 AAR (javap) and pinned by an SDK-parity test — no unverified constant is baked."
  - "Token expiry surfaced as ReadResult(tokenExpired=true, nextToken=null) rather than catching ChangesTokenExpiredException — that exception does NOT exist in connect-client 1.1.0; expiry is a ChangesResponse.changesTokenExpired boolean (deviation Rule 3)."
  - "Mapper takes EXTRACTED record values (not androidx.health.* records) so both the mapper and its tests stay pure; the adapter is the sole place records are pulled apart (iOS HealthSampleMapping vs the service split)."
  - "provenance (metadata.dataOrigin) is CONSUMED at the adapter (own-package guard) but NOT persisted — ImportedSample has no provenance column; the 'from Health Connect' label is a Plan 04 UI concern. No schema change made (would be Rule 4)."
  - "ownPackageName injected into the adapter (not read from a Context) to keep HealthConnectReader free of Android context and construction-testable."
requirements: [AHC-03]
metrics:
  duration: "~40m"
  completed: "2026-07-09"
  tasks: 2
  files_created: 4
  files_modified: 0
  app_tests: 202
---

# Phase 21 Plan 02: HealthReader Seam + Record→ImportedSample Mapper Summary

Built the injectable `HealthReader` read seam, the pure `HealthRecordMapping` (the ONLY genuinely bespoke logic in this phase — the Android analog of the iOS `HealthSampleMapping`), and the real `HealthConnectReader` adapter over `HealthConnectClient`. The four Health Connect record types map to the exact `ImportedSample` field sets the Phase-18 `ImportedSignals`/`SnapshotProjector` merge already folds; day-attribution matches iOS (`startOfDay` in the device zone) under DST, travel, and midnight-crossing vectors; re-import is byte-identical via a deterministic `stableId`; and every decode is defensive. All four A1/A2/A3 SDK integer assumptions were CONFIRMED against the connect-client 1.1.0 AAR and are pinned by a test. Verified green on a real Android SDK build.

## What Was Built

**Task 1 — HealthReader seam + pure mapper + bucketing vectors (TDD)**
- `HealthReader` (interface) + `ReadResult` — the injectable seam. The signature references NO `androidx.health.*` type (only doc-comment mentions), so import/merge/idempotency/deletion are JVM-testable off Plan 03's `FakeHealthReader`. `read(domain, token)` returns already-mapped `ImportedSample` rows + `deletedHcRecordIds` + `nextToken` + a `tokenExpired` flag. `grantedPermissions()` is per-session (documented never-cache).
- `HealthRecordMapping` (pure `object`) — value-in/value-out, no `HealthConnectClient`, no `androidx.health.*` import:
  - **cycle**: `flowRaw(flow)` — `FLOW_LIGHT→light`, `MEDIUM→medium`, `HEAVY→heavy`, and `FLOW_UNKNOWN` + ANY unrecognised int → `spotting` (A4, iOS `.unspecified→.spotting`; a present record = flow logged, never null/fabricated).
  - **sleep**: `asleepHours` = Σ of asleep-stage segment durations `{SLEEPING, LIGHT, DEEP, REM}`, excluding `AWAKE/OUT_OF_BED/AWAKE_IN_BED/UNKNOWN`; a stage-less session → full `endTime-startTime` (A7). `sleepQualityRaw` left null (band derived by `ImportedSignals` over the SUMMED total).
  - **workouts**: `activityTypeRaw` from the exerciseType family map (walking/cardio/strength/yogaStretching; else `other`), `activityMinutes = (endTime-startTime)`.
  - **nutrition**: `ateThatDay = true` (presence only — never a FoodTag or micronutrient).
  - `epochDayFor(start, zone)` = the shared iOS-parity bucketing rule; `stableId(hcRecordId)` = `UUID.nameUUIDFromBytes("hc:<id>")`; `importedAtMillis` from an injected `clock: () -> Long`.
- `HealthRecordMappingTest` — 15 pure-JVM cases: the four field maps to exact columns, `FLOW_UNKNOWN`/garbage→spotting, asleep-Σ excluding awake, stage-less→full duration, unknown-stage ignored, exerciseType family + `GOLF`/garbage→other, nutrition presence, deterministic + distinct `stableId` (+ byte-identical re-import), DST (Europe/Berlin 2026-03-29), travel (Pacific/Apia vs Berlin — same instant, different day), midnight-spanning sleep→start day, domain-raw alignment with `HealthDomain`, and the SDK-constant parity guard.

**Task 2 — real HealthConnectReader adapter**
- `HealthConnectReader(client, ownPackageName, zone, clock) : HealthReader` — the thin `HealthConnectClient` shell (iOS `SystemHealthDataReading` analog):
  - `grantedPermissions()` → `client.permissionController.getGrantedPermissions()` (per session).
  - `read(domain, null)` seeds: a paged ranged `readRecords` per domain record type (history-gated `TimeRangeFilter.after(EPOCH)`) + a fresh `getChangesToken(ChangesTokenRequest(recordTypes=…))`.
  - `read(domain, token)` pages `getChanges` in a `while(hasMore)` loop, partitioning `UpsertionChange` (mapped via `HealthRecordMapping`, own-package writes skipped — T-21-02c) into `upserts` and `DeletionChange.recordId` into `deletedHcRecordIds`, advancing `nextChangesToken`.
  - Token expiry → `ReadResult(tokenExpired = true, nextToken = null)` (Plan 03 reseeds).
  - `mapRecord` `when`-matches the four record classes; an unrecognised class → null (no HC type escapes).

## Real Test Results (this host, Android SDK platform 36, JDK 21)
- `./gradlew :app:testDebugUnitTest --tests "*HealthRecordMappingTest"` — **GREEN, 15/15** (mapping + DST/travel/midnight + SDK-parity), 0 failures.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest` — **BUILD SUCCESSFUL**; full app unit suite **202 tests, 0 failures / 0 errors** (187 from 21-01 + 15 new). No regression.
- `./gradlew :insights:test` — **BUILD SUCCESSFUL** (kept green).
- Seam check: `grep androidx.health android/.../HealthReader.kt` → only doc-comment mentions; no import, no type reference in the interface/`ReadResult` (the mapping stays inside `HealthConnectReader`).

### RED-first proven (not fabricated)
- `HealthRecordMappingTest` was authored and run FIRST against the missing implementation: the RED run failed with exactly `Unresolved reference 'HealthRecordMapping'` (the only missing symbol; the referenced `androidx.health.connect.client.records.*` SDK constants and `ImportedSample` resolved). GREEN after implementing the seam + mapper. Commit `5a4dd93` (test, RED) precedes `5a93051` (impl, GREEN).

## Assumptions Surfaced (confirmed, not silently baked)
The RESEARCH A1/A2/A3 integer constants were extracted from the connect-client 1.1.0 AAR (`javap -constants`) and are held as NAMED constants in `HealthRecordMapping` (the single place to correct one), each pinned to the REAL SDK constant by `sdkConstantsMatchMappersNamedConstants` — turning each assumption into a test-enforced fact:
- **A1** `MenstruationFlowRecord.FLOW_UNKNOWN=0/LIGHT=1/MEDIUM=2/HEAVY=3` — CONFIRMED.
- **A2** `SleepSessionRecord.STAGE_TYPE_SLEEPING=2/LIGHT=4/DEEP=5/REM=6` (asleep); `AWAKE=1/OUT_OF_BED=3/AWAKE_IN_BED=7/UNKNOWN=0` (excluded) — CONFIRMED.
- **A3** `ExerciseSessionRecord.EXERCISE_TYPE_*` family membership (WALKING=79, HIKING=37, RUNNING=56, BIKING=8, ELLIPTICAL=25, STRENGTH_TRAINING=70, WEIGHTLIFTING=81, YOGA=83, PILATES=48, …); GOLF=32 unmapped→other — CONFIRMED.
- **A4** `FLOW_UNKNOWN→spotting` (iOS `.unspecified` parity) — implemented as the honest lightest-bucket fallback for a present record.
- **A5** device-zone `startOfDay` reproduces iOS `Calendar.startOfDay` — pinned by the DST (Berlin) + travel (Apia) + midnight vectors.
- **A7** stage-less `SleepSessionRecord` → full session duration as asleep hours — implemented + pinned.

## Deviations from Plan

**1. [Rule 3 — API correctness] Token expiry via a boolean, not an exception.**
- **Found during:** Task 2, verifying the changes API against the connect-client 1.1.0 AAR.
- **Issue:** The plan's Pattern 4 / Task 2 says "catch `ChangesTokenExpiredException`". That class does NOT exist in connect-client 1.1.0 — expiry is signalled by `ChangesResponse.changesTokenExpired: Boolean` (verified: no `*TokenExpired*` class in the AAR).
- **Fix:** The adapter checks `response.changesTokenExpired` and surfaces expiry as `ReadResult(tokenExpired = true, nextToken = null)` — the plan's own sanctioned alternative ("surface it as a typed result … Plan 03 owns the reseed policy"). Added `tokenExpired` as an additive `ReadResult` field so the FakeHealthReader can model it.
- **Files:** `HealthConnectReader.kt`, `HealthReader.kt`.

**2. [Rule 3 — missing column, documented not built] provenance not persisted.**
- **Issue:** The plan/objective says "carry `metadata.dataOrigin`→provenance", but `ImportedSample` has NO provenance column.
- **Fix:** `dataOrigin.packageName` is CONSUMED at the adapter (own-package guard) but not stored; the "from Health Connect" / per-app label is a Plan 04 UI concern. No schema column added (that would be a Rule 4 architectural change). Documented rather than silently dropped.

Otherwise the plan executed as written. No auth gates. No package installs.

## Known Stubs
None. `HealthConnectReader` is the real adapter (device-gated behavior, compiles + typechecks); its import/merge/idempotency/deletion behavior is proven via `FakeHealthReader` in Plan 03, by design.

## Threat Model Compliance
- **T-21-02a (Tampering — defensive decode, medium):** mitigated — unknown flow int→spotting, unknown sleep stage→excluded, unmapped exerciseType→other, malformed end<start duration→coerced to 0; `menstruationFieldMap_defensiveUnknownFlowIntFallsToSpotting`, `sleepFieldMap_ignoresUnknownStageIntsDefensively`, `exerciseFieldMap_unmappedTypeFallsToOther_A3` pin it. No throw path; a present record is never coerced to 0.
- **T-21-02b (Tampering — bucketing correctness, high):** mitigated — bucket by START instant in the supplied device zone, never the record `zoneOffset`; `bucketing_dstSpringForwardDayStillBucketsToThatCalendarDay`, `bucketing_isByDeviceZoneNotUtc_travelVector`, `bucketing_midnightSpanningSleepLandsOnStartDay` pin iOS parity.
- **T-21-02c (Spoofing/loop — own-package guard, low):** mitigated — `UpsertionChange` from `ownPackageName` is skipped in `HealthConnectReader.differential` (device-gated; exercised via the fake in Plan 03).

## Threat Flags
None. No new security surface beyond the plan's threat_model was introduced (read-only, no network, no new permission, no schema change).

## Self-Check: PASSED
- All 4 created files exist on disk (`HealthReader.kt`, `HealthRecordMapping.kt`, `HealthConnectReader.kt`, `HealthRecordMappingTest.kt`); 0 modified.
- All 3 task commits present in git history: `5a4dd93` (test RED), `5a93051` (mapper GREEN), `f77bceb` (adapter).
- Real build/test results captured above (202 app tests / 0 failures, `:insights` green, `assembleDebug` SUCCESSFUL) — not fabricated.
