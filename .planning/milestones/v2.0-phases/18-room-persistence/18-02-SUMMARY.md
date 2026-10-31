---
phase: 18-room-persistence
plan: 02
subsystem: data
tags: [android, room, ksp, entities, dao, schema-export, migration, dayscoped, junit4]

# Dependency graph
requires:
  - phase: 18-room-persistence
    plan: 01
    provides: ":app Android Application module (Room 2.8.4/KSP wired, room.schemaLocation set), one-way :app -> :insights edge, :app JUnit4 test lane"
provides:
  - "10 Room @Entity types (iOS model names, app-generated UUID String PKs, epochDay/*Millis Long keys, nullable raw-string + nullable health-value columns)"
  - "DayScoped marker interface on the 6 day-scoped entities — the single canonical source the Plan-03 cascade + completeness meta-test key off"
  - "Four thin suspend/Flow DAOs (DayEntryDao, SymptomDao, MedicationDao, InsightsDao); no allowMainThreadQueries"
  - "EndoDatabase @Database(version=1, exportSchema=true) over all 10 entities + committed schemas/<dbFqn>/1.json baseline"
  - "Migration discipline: SchemaExportTest + structural DestructiveMigrationBanTest (green, red-capable) + MigrationTestHelper scaffold"
affects: [18-03, 18-04, 18-06, 19-ui-shell, 22-insights-ui, 23-export-import]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Room entities as Kotlin data classes; DayScoped marker interface drives the day-keyed cascade (not a hand-maintained list)"
    - "Non-null spent ONLY on id/epochDay/*Millis; every health-value column nullable (absence != 0)"
    - "Room schema nests under the DB class FQN: schemas/com.endoapp.app.data.EndoDatabase/1.json (committed source artifact)"
    - "Structural source-scan tests (destructive-fallback ban) keep the forbidden literal ONLY in the test to stay self-consistent"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/data/DayScoped.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/Converters.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/entities/DayEntry.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/entities/SymptomReading.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/entities/PainPoint.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/entities/BleedingEvent.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/entities/FactorReading.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/entities/ImportedSample.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/entities/Medication.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/entities/TreatmentState.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/entities/CachedInsight.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/entities/DismissedInsight.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/daos/DayEntryDao.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/daos/SymptomDao.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/daos/MedicationDao.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/daos/InsightsDao.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/EndoDatabase.kt
    - android/app/schemas/com.endoapp.app.data.EndoDatabase/1.json
    - android/app/src/test/kotlin/com/endoapp/app/data/SchemaExportTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/data/DestructiveMigrationBanTest.kt
    - android/app/src/androidTest/kotlin/com/endoapp/app/data/MigrationTest.kt
  modified:
    - android/app/build.gradle.kts

key-decisions:
  - "Room nests the exported schema under the DB class FQN — the committed baseline is schemas/com.endoapp.app.data.EndoDatabase/1.json (the plan's literal schemas/1.json was a path simplification); tests resolve the real nested path"
  - "Converters made present-and-functional (LocalDate/Instant <-> Long) and wired via @TypeConverters rather than empty — Room ignores an unused converter, so it is harmless now and ready for future columns"
  - "DAO grouping (Claude's discretion): SymptomDao owns all 5 day-scoped event entities so every day-scoped type has a deleteForDay leaf for the Plan-03 cascade; MedicationDao owns the 2 definitions; InsightsDao owns cache+dismissals"
  - "hcRecordId unique index on a nullable column — many null-key rows coexist (SQLite treats NULLs as distinct); Phase 21 fills it"

requirements-completed: [APER-01, APER-03]

# Metrics
duration: ~35min
completed: 2026-07-09
status: complete
---

# Phase 18 Plan 02: Room Entities, Database & Migration Discipline Summary

**Ported the 10 iOS SwiftData models to Room `@Entity` data classes (iOS names, app-generated UUID String PKs, `epochDay`/`*Millis` Long keys, nullable raw-string enum + nullable health-value columns), defined the `DayScoped` marker that will drive the delete-day cascade, wired four thin suspend/Flow DAOs + `EndoDatabase(version=1, exportSchema=true)`, and locked Room v1 migration discipline — a committed `schemas/<dbFqn>/1.json` baseline, a structural (green, red-capable) destructive-fallback ban, and a `MigrationTestHelper` scaffold. `:app:assembleDebug :app:testDebugUnitTest` is green for real on this host.**

## Performance
- **Duration:** ~35 min
- **Completed:** 2026-07-09
- **Tasks:** 3
- **Files:** 22 created, 1 modified

## Accomplishments
- **10 Room entities, iOS-faithful:** `DayEntry, SymptomReading, PainPoint, BleedingEvent, FactorReading, ImportedSample, Medication, TreatmentState, CachedInsight, DismissedInsight` — each `@PrimaryKey val id: String` (app-generated UUID), day-bucketed columns as `epochDay: Long`, non-day timestamps as `*Millis: Long`, enum-backing columns as nullable `String?` raw. Verified field-for-field against `Sources/EndoApp/Persistence/Models/*.swift`.
- **Honesty preserved (absence != 0):** every optional health value (`severity`, `level`, `amount`, `clots`, `sleepHours`, `activityMinutes`, `ateThatDay`, `note`, ...) is a NULLABLE column. Grep gates confirm zero `DEFAULT 0` / `?: 0` in `data/entities/`. Non-null is spent only on `id`/`epochDay`/`*Millis`.
- **DayScoped marker on exactly the 6 day-scoped entities** (DayEntry, SymptomReading, PainPoint, BleedingEvent, FactorReading, ImportedSample); the 4 definition/cache entities (Medication, TreatmentState, CachedInsight, DismissedInsight) deliberately do NOT implement it. This is the single canonical source the Plan-03 cascade completeness meta-test reflects over — including `ImportedSample`, which closes the latent iOS `deleteDay` orphan gap.
- **`ImportedSample.hcRecordId` unique index** (Room's freedom, unavailable under CloudKit on iOS); renamed from iOS `hkSampleUUID` for the Phase-21 Health Connect `metadata.id` dedupe key. Nullable, so many null-key rows coexist.
- **Four thin DAOs, suspend + Flow, never `allowMainThreadQueries()`:** id-keyed REPLACE upserts, `WHERE epochDay = :epochDay` fetches for the SnapshotProjector, `all()` reads for the day-union projection, and per-day deletes on all 6 day-scoped types for the Plan-03 `@Transaction` cascade.
- **`EndoDatabase v1` exports a committed schema:** `@Database(version=1, exportSchema=true)` over all 10 entities; KSP wrote `android/app/schemas/com.endoapp.app.data.EndoDatabase/1.json` (10 entity tables, version 1), committed as a source artifact. Builder has NO destructive-migration fallback.
- **Migration discipline locked:** `SchemaExportTest` (asserts the committed v1 schema exists, parses, is version 1, covers all 10 tables), `DestructiveMigrationBanTest` (structural scan of `data/` main sources — green now, proven red-capable by inject-and-revert), and `MigrationTest` (androidTest `MigrationTestHelper` opening the v1 schema; `schemas/` registered as an androidTest asset srcDir).

## Task Commits
1. **Task 1: 10 entities + DayScoped marker + Converters** — `ad0ead5` (feat)
2. **Task 2: DAOs + EndoDatabase v1 + committed schemas baseline** — `017dd5e` (feat), plus `85c94f2` (fix — see Deviations)
3. **Task 3: Migration discipline tests** — `a9dcd0f` (test)

_No STATE.md / ROADMAP.md metadata commit — the orchestrator owns state updates for this run (per objective)._

## Real Build/Test Results (this host, Android SDK platform 36 present)
- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL.
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL; `app-debug.apk` (3.49 MB) produced; KSP emitted `schemas/com.endoapp.app.data.EndoDatabase/1.json` (10 tables).
- `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL. `SchemaExportTest` 1/1, `DestructiveMigrationBanTest` 1/1, `ModuleSmokeTest` 1/1 — 0 failures.
- `./gradlew :app:compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL (MigrationTest compiles; emulator run is the main-only CI lane, not executed here).
- Red-capability proof: injecting `.fallbackToDestructiveMigration(false)` into the builder turned `DestructiveMigrationBanTest` RED (1 failed); reverting restored green.

## Schema Confirmation
`android/app/schemas/com.endoapp.app.data.EndoDatabase/1.json` is committed (in `017dd5e`) and lists all 10 entity tables: BleedingEvent, CachedInsight, DayEntry, DismissedInsight, FactorReading, ImportedSample, Medication, PainPoint, SymptomReading, TreatmentState. `database.version == 1`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Structural test hygiene] Doc-comment literals were tripping the structural gates**
- **Found during:** Task 1 (grep gate) and Task 2 (ban-test consistency)
- **Issue:** The honesty grep gate (`! grep "?: 0"` / `"DEFAULT 0"`) matched my own explanatory doc comment in `SymptomReading.kt`; and `EndoDatabase.kt`'s KDoc named `fallbackToDestructiveMigration` verbatim, which would falsely trip `DestructiveMigrationBanTest`'s source scan.
- **Fix:** Reworded both doc comments to describe the rules without the exact forbidden literals, so the structural gates measure real code, not prose. The banned method literal now lives ONLY in the ban test.
- **Files modified:** `SymptomReading.kt` (folded into `ad0ead5`), `EndoDatabase.kt` (separate `85c94f2` after the Task-2 commit surfaced it).

**2. [Rule 3 - Path correction] Real Room schema path differs from the plan's literal**
- **Issue:** The plan lists `android/app/schemas/1.json`, but Room always nests the exported schema under the database class FQN.
- **Fix:** The committed artifact and all tests target the real path `android/app/schemas/com.endoapp.app.data.EndoDatabase/1.json`. `SchemaExportTest` and the androidTest asset registration resolve the nested layout.
- **Impact:** None functionally — this is Room's canonical, correct behavior; only the plan's path string was a simplification.

### Discretionary decisions (within Claude's Discretion per CONTEXT)
- Converters wired via `@TypeConverters(Converters::class)` with functional `LocalDate`/`Instant` <-> `Long` converters rather than an empty class — Room ignores an unused converter, so it is harmless today and ready for any future direct-`LocalDate`/`Instant` column.
- DAO grouping as described above (5-event `SymptomDao`, definitions `MedicationDao`, insights `InsightsDao`, `DayEntryDao`).

## Threat Model Compliance
- **T-18-01 (schema-migration data loss, critical):** mitigated — `exportSchema=true` + committed `1.json` baseline + `MigrationTestHelper` scaffold; destructive-migration fallback structurally banned (green, red-capable). A schema bump can never silently wipe health history.
- **T-18-05 (nil->0 coercion, high):** mitigated — all health-value columns nullable; grep gates confirm no `DEFAULT 0` / `?: 0`; non-null spent only on id/day-key/timestamps.
- **T-18-06 (corrupt/future raw string, medium — accept):** raw enum strings stored verbatim at the entity layer as designed; defensive decode is the Plan-04 SnapshotProjector's job, not duplicated here.

## Next Plan Readiness
- Plan 03 (delete-day cascade + completeness meta-test) can proceed: the `DayScoped` marker is the canonical day-scoped source, every day-scoped type has a `deleteForDay` DAO leaf, and `EndoDatabase` exposes the entity list for reflection.
- Plan 04 (SnapshotProjector bridge) has the entity types + raw columns it will decode via `:insights` `fromRaw`/`fromRawOrNull`.
- No blockers.

## Self-Check: PASSED
All 22 created files and the modified `build.gradle.kts` exist on disk; the committed schema `com.endoapp.app.data.EndoDatabase/1.json` is present; all 4 task commits (`ad0ead5`, `017dd5e`, `85c94f2`, `a9dcd0f`) are in git history; `:app:assembleDebug :app:testDebugUnitTest` verified green.

---
*Phase: 18-room-persistence*
*Completed: 2026-07-09*
