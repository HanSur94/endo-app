---
phase: 09-healthkit-import
plan: 01
subsystem: health-import
tags: [healthkit, swift6-sendable, swiftdata, additive-schema, cloudkit-safe, boundary-lint]
requires:
  - "Phase 8 SchemaV5 + CachedInsight additive-schema pattern (cloned)"
  - "Domain enums: ActivityType / SleepQuality / FlowIntensity / FoodTag"
  - "EndoInsights Foundation-only package boundary lint (Phase 5)"
provides:
  - "HealthDomain enum with per-domain HK read-type sets (D-14/D-15/D-16)"
  - "ImportedSampleValue / ImportedPayload / ValueSource Sendable value contracts (no HK* escapes)"
  - "HealthDataReading protocol seam (injectable HealthKit boundary)"
  - "HealthSampleMapping pure HK→app value tables (workout/sleep/flow/nutrition)"
  - "ImportedSample CloudKit-safe @Model under SchemaV6 + lightweight V5→V6 migration"
  - "HealthKit added to the EndoInsights forbidden-import set"
affects:
  - "Sources/EndoApp/Persistence/MigrationPlan.swift (now V1..V6)"
  - "Sources/EndoApp/Persistence/ModelContainer+Config.swift (now SchemaV6)"
tech-stack:
  added: [HealthKit (first-party system framework; no SPM install)]
  patterns: [actor-isolation-seam-contract, additive-cloudkit-safe-schema, pure-defensive-mapping]
key-files:
  created:
    - Sources/EndoApp/Health/HealthDomain.swift
    - Sources/EndoApp/Health/ImportedSampleValue.swift
    - Sources/EndoApp/Health/HealthDataReading.swift
    - Sources/EndoApp/Health/HealthSampleMapping.swift
    - Sources/EndoApp/Persistence/Models/ImportedSample.swift
    - Sources/EndoApp/Persistence/SchemaV6.swift
    - Tests/EndoAppTests/HealthSampleMappingTests.swift
  modified:
    - Sources/EndoApp/Persistence/MigrationPlan.swift
    - Sources/EndoApp/Persistence/ModelContainer+Config.swift
    - Tests/EndoAppTests/SchemaMigrationTests.swift
    - EndoInsights/Tests/EndoInsightsTests/BoundaryLintTests.swift
decisions:
  - "HKCategoryValueMenstrualFlow (not vaginalBleeding) is the value enum for the D-14 .menstrualFlow read type"
  - "Menstrual-flow .unspecified maps to .spotting (lightest bucket); .none/unknown map to nil (never fabricated)"
  - "Sleep thresholds: poorHoursCeiling=5h, okayHoursCeiling=7h; boundary lands on the upper band (>=)"
  - "Anchored-query asleep-set API is HKCategoryValueSleepAnalysis.allAsleepValues (not asleepValues) on the Xcode 26 SDK"
metrics:
  duration: "~9 min"
  completed: "2026-06-09"
  tasks: 3
  files: 11
---

# Phase 9 Plan 01: HealthKit Import — Seam + Persistence Spine Summary

The contract-and-storage slice for HealthKit import (DATA-05): an injectable `HealthDataReading`
seam whose escaping positions are Swift-6 `Sendable` value structs (no `HKSample`/`HKHealthStore`
ever crosses), pure defensive HK-enum→app-value mapping tables (D-14/D-15/D-16), and a CloudKit-safe
`ImportedSample` `@Model` under an additive `SchemaV6` with a lightweight V5→V6 migration — plus a
boundary-lint extension proving HealthKit never leaks into the Foundation-only `EndoInsights` package.

## What Was Built

### Task 1 — HealthKit boundary contracts (`03be227`)
- `HealthDomain` (`String`/`CaseIterable`/`Sendable`) with per-domain `readTypes: Set<HKObjectType>`
  and `sampleType: HKSampleType` — cycle = menstrualFlow only (D-14), sleep = sleepAnalysis,
  workouts = workoutType, nutrition = dietaryEnergyConsumed (D-16 presence proxy).
- `ImportedSampleValue` / `ImportedPayload` / `ValueSource` — `Sendable`, `Foundation`-only value
  structs. `ImportedPayload` carries one value-typed case per domain; `ImportedSampleValue` carries
  the `hkUUID` dedupe key, day, domain, and payload. No `HK*` type appears in this file.
- `HealthDataReading: Sendable` protocol — `requestAuthorization(for:)` + `pull(_:since:)` returning
  the Sendable triple `([ImportedSampleValue], [UUID], HKQueryAnchor?)`. The only `HK*` type is the
  opaque `HKQueryAnchor` cursor. Doc comment encodes the Pitfall-1 read-status honesty contract
  (denied == empty; never fabricate "no symptoms"). Real `HKHealthStore` conformer deferred to 09-02.

### Task 2 — Pure HK→app value mapping tables, TDD (`80fcdb9` RED, `84cc749` GREEN)
- `HealthSampleMapping` namespace + `SleepSegmentValue` test helper.
- `activityType(from:)` — walking/cardio/strength/yoga families with explicit `default: .other` (D-15).
- `nightlyAsleepHours(_:)` — sums `allAsleepValues` segments only (inBed/awake/unknown excluded); empty→0.
- `sleepQuality(forAsleepHours:)` — named thresholds (`poorHoursCeiling=5`, `okayHoursCeiling=7`),
  boundary lands on the upper band.
- `flowIntensity(fromMenstrualFlowValue:)` — recognized HK menstrual-flow values → `FlowIntensity`;
  `.none`/unknown → `nil` (never fabricated, mirrors `FlowIntensity(raw:)`).
- `ateThatDay(dietaryEnergySampleCount:)` — presence-only (D-16); 0 samples → false.
- 17 tests GREEN, including both threshold boundaries and every defensive-decode fallback.

### Task 3 — ImportedSample @Model + SchemaV6 + migration + survival test + lint (`2fe38d5`)
- `ImportedSample` cloned from `CachedInsight` shape: nested under `extension SchemaV1`,
  `var id: UUID = UUID()` (NOT `.unique`), every column optional-or-defaulted, no relationships.
  Columns: `hkSampleUUID` (String dedupe key, Pitfall 5), `day`, `domainRaw`, flat value columns
  (`flowRaw`/`sleepHours`/`sleepQualityRaw`/`activityTypeRaw`/`activityMinutes`/`ateThatDay`),
  `importedAt`. Header doc states the T-09-01 privacy boundary + dedupe-by-`hkSampleUUID` upsert rule.
- `SchemaV6` additive VersionedSchema (V5 set + `ImportedSample`, the only delta).
- `MigrationPlan` extended: `SchemaV6` in `schemas`, `v5toV6` in `stages`, `.lightweight` stage (NEVER
  `.custom` — hooks don't fire under CloudKit, Pitfall 3).
- `ModelContainer+Config` bumped to `Schema(versionedSchema: SchemaV6.self)`.
- `v5Store_survivesLightweightMigrationToV6_withNoDataLoss` — writes V5 rows across every V5 model
  (including a `CachedInsight` row), reopens the SAME on-disk file at V6 via `makeModelContainer(url:)`
  (forces `cloudKitDatabase: .none`, the P-2 guard), asserts all rows survive + `ImportedSample`
  queryable (empty). GREEN.
- `BoundaryLintTests` adds `"HealthKit"` to the forbidden-import set (T-09-05 belt-and-suspenders).

## Verification

| Check | Command | Result |
|-------|---------|--------|
| Task 1 contracts compile | `xcodebuild build … -scheme EndoApp` | BUILD SUCCEEDED |
| Task 2 mappings | `…-only-testing:EndoAppTests/HealthSampleMappingTests` | 17 tests GREEN |
| Task 3 migration | `…-only-testing:EndoAppTests/SchemaMigrationTests` | 5 tests GREEN (incl. V5→V6) |
| Task 3 boundary lint | `…-only-testing:EndoInsightsTests/BoundaryLintTests` | GREEN with HealthKit forbidden |
| Final full app build | `xcodebuild build … -scheme EndoApp` | BUILD SUCCEEDED |

Note: the package boundary-lint test selector is `EndoInsightsTests/BoundaryLintTests` (the
`EndoInsights/EndoInsightsTests/...` form in the plan's verify block errors with "isn't a member of
the specified test plan or scheme" under the generated project — the bare target name is correct).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Sleep asleep-set API name on the Xcode 26 / iOS 18 SDK**
- **Found during:** Task 2 (first GREEN build)
- **Issue:** `HealthSampleMapping` referenced `HKCategoryValueSleepAnalysis.asleepValues`, which does
  not exist on the installed SDK (iPhoneSimulator 26.5).
- **Fix:** Inspected the HealthKit `.swiftinterface`; the actual API is `allAsleepValues`. Updated the
  source to use `HKCategoryValueSleepAnalysis.allAsleepValues`. (RESEARCH A4 explicitly flagged
  confirming exact SDK signatures at build time.) The test uses individual stage cases and was
  unaffected.
- **Files modified:** Sources/EndoApp/Health/HealthSampleMapping.swift
- **Commit:** 84cc749 (folded into the GREEN commit)

**2. [Rule 1 - Correctness] Menstrual-flow value enum選択**
- **Found during:** Task 2 (writing the RED test)
- **Issue:** The initial test draft referenced `HKCategoryValueVaginalBleeding` raw values, but D-14
  locks the read type to `.menstrualFlow`, whose value enum is `HKCategoryValueMenstrualFlow`.
- **Fix:** Corrected the test (and aligned the mapping) to `HKCategoryValueMenstrualFlow` before the
  RED commit, so the mapping matches the D-14 read type exactly.
- **Files modified:** Tests/EndoAppTests/HealthSampleMappingTests.swift
- **Commit:** 80fcdb9 (corrected before RED commit)

## TDD Gate Compliance

Task 2 followed RED → GREEN. RED commit `80fcdb9` (`test(09-01): add failing tests …`, confirmed
failing: "cannot find 'HealthSampleMapping' in scope"). GREEN commit `84cc749`
(`feat(09-01): pure HK→app value mapping tables …`, 17 tests passing). No REFACTOR commit needed.

## Known Stubs

None that block the plan goal. By design, the real `HKHealthStore`-backed `HealthDataReading`
conformer is NOT implemented here — the plan explicitly defers it (and the fixture conformer) to
09-02. This is a documented interface-first deferral, not a stub: the protocol + value contracts are
complete and the mapping functions are fully wired and tested.

## Threat Surface Scan

No new security surface beyond the plan's `<threat_model>`. All mitigations land as specified:
T-09-01 (privacy boundary documented in the `ImportedSample` header), T-09-02 (defensive mappings,
tested), T-09-03 (additive `.lightweight` migration, survival-tested), T-09-04 (no `HK*` in escaping
positions), T-09-05 (HealthKit forbidden in the package lint). No package installs (T-09-SC).

## Self-Check: PASSED

All 7 created files present on disk; all 4 task commits present in git history.
