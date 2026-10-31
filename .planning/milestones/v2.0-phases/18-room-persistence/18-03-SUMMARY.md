---
phase: 18-room-persistence
plan: 03
subsystem: data
tags: [android, room, delete-cascade, gdpr-art17, consent, datastore, reflection-meta-test, junit4]

# Dependency graph
requires:
  - phase: 18-room-persistence
    plan: 02
    provides: "10 Room entities + DayScoped marker on the 6 day-scoped types, four DAOs (each day-scoped type has a deleteForDay leaf), EndoDatabase v1"
provides:
  - "DeleteDayDao — one @Transaction purging ALL 6 day-scoped tables (incl. ImportedSample) + CachedInsight delete-all; closes the latent iOS deleteDay omission"
  - "COVERED_DAY_SCOPED_ENTITIES — the single canonical cascade-coverage declaration the completeness meta-test pins against the compiled DayScoped set"
  - "CascadeCompletenessTest — reflection/marker meta-test (RED-capable) that fails if a DayScoped entity is uncascaded, derived by scanning compiled entity classes (Room @Database is BINARY-retention, unreflectable)"
  - "ConsentState (3-state Unknown/NotConsented/Consented(grantedAtMillis)) + ConsentStore interface + DataStoreConsentStore (consent.preferences_pb, outside the health DB)"
  - "EndoRepository — consent-gated facade: checkConsented() first in every health method, throws IllegalStateException pre-consent (Unknown fails closed); deleteDay delegates to the atomic cascade"
affects: [18-04, 19-ui-shell, 22-insights-ui, 23-export-import]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Delete-day cascade as one @Transaction default method on a @Dao interface; coverage declared once in COVERED_DAY_SCOPED_ENTITIES"
    - "Completeness meta-test derives the DayScoped set by scanning compiled entity classes off java.class.path (dirs + the AGP-jarred main classes) — no hand list, no annotation reflection"
    - "Consent seam mirrors iOS: ConsentStore interface (mockable) + DataStoreConsentStore impl; 3rd state Unknown for async DataStore, fails closed"
    - "Repository consent gate is a hard IllegalStateException throw (not assert — R8 strips assert), first statement of every health method"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/data/daos/DeleteDayDao.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/ConsentState.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/ConsentStore.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/EndoRepository.kt
    - android/app/src/test/kotlin/com/endoapp/app/data/CascadeCompletenessTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/data/RepositoryConsentTest.kt
    - android/app/src/androidTest/kotlin/com/endoapp/app/data/DeleteDayCascadeTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/data/EndoDatabase.kt

key-decisions:
  - "Completeness meta-test derives the DayScoped set by CLASSPATH SCAN, not @Database reflection: Room's @Database annotation is AnnotationRetention.BINARY (verified via javap) and returns null at runtime — the classpath scan is a stronger, hand-list-free source"
  - "ConsentStore split into an interface (ports the iOS ConsentStore protocol) + DataStoreConsentStore impl, so EndoRepository depends on the mockable seam and RepositoryConsentTest fakes it with a settable StateFlow — no DataStore file / no Robolectric needed"
  - "EndoRepository takes DAOs individually (with a from(consent, db) production factory) rather than the database, so the consent test injects relaxed fakes with zero SQLite"

requirements-completed: [APER-02, APER-01]

# Metrics
duration: ~40min
completed: 2026-07-09
status: complete
---

# Phase 18 Plan 03: Delete-Day Cascade + Consent-Gated Repository Summary

**Delivered the phase's two irreversible correctness invariants — GDPR Art. 17 erasure completeness and consent-by-construction at the data layer. `DeleteDayDao` purges EVERY day-scoped row for a day in ONE `@Transaction` INCLUDING `ImportedSample` (closing the latent iOS `deleteDay` omission), with a reflection/marker `CascadeCompletenessTest` that goes RED if any `DayScoped` entity is uncascaded. `ConsentStore` (interface + `DataStoreConsentStore`) persists a 3-state consent flag in `consent.preferences_pb` OUTSIDE the health DB, and `EndoRepository` refuses every health read/write pre-consent (Unknown fails closed) via a hard `IllegalStateException`. `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `:app:assembleDebug` are green for real on this host.**

## Performance
- **Duration:** ~40 min
- **Completed:** 2026-07-09
- **Tasks:** 3
- **Files:** 7 created, 1 modified

## Accomplishments
- **Delete-day cascade, born complete (`DeleteDayDao`):** one `@Transaction suspend fun deleteDay(epochDay)` issuing per-table `DELETE ... WHERE epochDay` for ALL 6 day-scoped tables — `DayEntry, SymptomReading, PainPoint, BleedingEvent, FactorReading, ImportedSample` — plus a wholesale `DELETE FROM CachedInsight` (cache invalidation). `Medication`, `TreatmentState`, `DismissedInsight` are deliberately never touched. Wired into `EndoDatabase` via `deleteDayDao()`.
- **ImportedSample inclusion — the port EXCEEDS iOS:** iOS `Repository.deleteDay` (Repository.swift 282–295) deletes only 5 types and OMITS `ImportedSample` (a latent GDPR orphan of the same class the iOS 04-02 fix closed for `FactorReading`). The Android cascade includes it, and the meta-test structurally forbids ever dropping it.
- **`CascadeCompletenessTest` (JVM, RED-capable):** derives the real `DayScoped` set by reflecting over the COMPILED entity classes and asserts it equals `DeleteDayDao.COVERED_DAY_SCOPED_ENTITIES`. Proven red-capable by inject-and-revert: removing `ImportedSample::class` from the coverage set turned BOTH cases RED; restoring it returned green. Adding a future uncascaded `DayScoped` entity fails it the same way.
- **3-state `ConsentState` + `ConsentStore`:** `Unknown / NotConsented / Consented(grantedAtMillis)`. `DataStoreConsentStore` maps the stored flag to state via `dataStore.data.map{…}.stateIn(scope, Eagerly, Unknown)`; `grant()` writes flag + `System.currentTimeMillis()`, `withdraw()` clears both. The flag lives in `consent.preferences_pb`, and the file imports NO Room/entity/DB type (grep gate passes).
- **Consent-gated `EndoRepository` facade:** `checkConsented()` is the FIRST statement of all 16 health methods (9 writes, 6 per-day reads, `deleteDay`) and throws `IllegalStateException` unless state is `Consented` — a hard throw (R8 strips `assert`). `deleteDay` delegates to the single-transaction `DeleteDayDao.deleteDay` (never N sequential repo deletes). `daySnapshots()` is intentionally deferred to Plan 04.
- **`RepositoryConsentTest` (JVM, no DB):** a fake `ConsentStore` (settable `MutableStateFlow`) + relaxed fake DAOs prove all 16 ops throw under `Unknown` AND `NotConsented`, and none throws under `Consented(123L)`. Ports the iOS `ConsentGateTests` as a fast SQLite-free test.
- **`DeleteDayCascadeTest` (androidTest, compiles):** inserts one row of all 10 entity types (day-scoped on day D), calls `deleteDay(D)`, and asserts the EXACT survival set — all 6 day-scoped tables empty for D incl. `ImportedSample`, `Medication`/`TreatmentState`/`DismissedInsight` present, `CachedInsight` empty, a different day untouched. Compiles; the emulator run is the CI lane (no emulator on this host — matches Plan 02 precedent).

## Task Commits
1. **Task 1: delete-day cascade + completeness meta-test** — `f6d9e5f` (feat)
2. **Task 2: ConsentState + ConsentStore (DataStore, outside the DB)** — `f4be39a` (feat)
3. **Task 3: consent-gated EndoRepository + RepositoryConsentTest** — `bdb0bbd` (feat)

_No STATE.md / ROADMAP.md metadata commit — the orchestrator owns state updates for this run (per objective)._

## Real Build/Test Results (this host, Android SDK platform 36 present)
- `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL. 8 tests, 0 failures:
  - `CascadeCompletenessTest` 2/2 (`cascadeCoversExactlyTheDayScopedEntities`, `importedSampleIsCovered_closingTheIosDeleteDayOmission`)
  - `RepositoryConsentTest` 3/3 (throws-when-Unknown, throws-when-NotConsented, none-throws-when-Consented)
  - Plan-02 tests still green: `SchemaExportTest` 1/1, `DestructiveMigrationBanTest` 1/1, `ModuleSmokeTest` 1/1 (no regression).
- `./gradlew :app:compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL (`DeleteDayCascadeTest` compiles; in-memory Room DB + `ApplicationProvider`).
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL; Room KSP accepts the `@Transaction` cascade default method + the DAO companion object.
- **RED-capability proof:** dropping `ImportedSample::class` from `COVERED_DAY_SCOPED_ENTITIES` turned `CascadeCompletenessTest` 2/2 RED; restoring it returned green.
- **No-Room grep gate:** `grep -nE "com.endoapp.app.data.entities|EndoDatabase|RoomDatabase" ConsentStore.kt` → no match (the consent flag lives outside the health DB).

## Deviations from Plan

### Auto-fixed / design adaptations

**1. [Rule 3 - Blocking technical constraint] Completeness meta-test derives the DayScoped set via classpath scan, not `@Database` reflection**
- **Found during:** Task 1.
- **Issue:** The plan says to "compute the DayScoped entity set from the `@Database` entities list (reflect over classes implementing the DayScoped marker)". Verified via `javap` that Room's `androidx.room.Database` is `AnnotationRetention.BINARY` / `RetentionPolicy.CLASS` — it is NOT visible via runtime reflection, so `EndoDatabase::class.java.getAnnotation(Database::class.java)` returns null. The literal approach cannot work.
- **Fix:** The meta-test derives the canonical `DayScoped` set by scanning the compiled `entities` package off `java.class.path` (both directory entries and the AGP-jarred main classes) and filtering by `DayScoped.isAssignableFrom`. This achieves the SAME structural guarantee, in fact a stronger one — it discovers every day-scoped entity that exists, not only those a developer remembered to list. An initial `ClassLoader.getResource` attempt returned an empty shadowing dir and then found nothing because AGP hands main classes to the unit-test JVM as a jar; the java.class.path walk (dirs + jars) is the robust fix.
- **Files:** `CascadeCompletenessTest.kt`.

**2. [Rule 3 - Testability] `ConsentStore` split into interface + `DataStoreConsentStore` impl**
- **Issue:** The plan's `RepositoryConsentTest` must "construct EndoRepository over a FAKE ConsentStore (a settable StateFlow) … so no DB is needed", but a single concrete `ConsentStore` class backed by DataStore cannot expose a settable `StateFlow` without a real DataStore file.
- **Fix:** Ported the iOS shape faithfully — `ConsentStore` is now an interface (mirrors the iOS `ConsentStore` protocol) and `DataStoreConsentStore` is the concrete impl (mirrors `UserDefaultsConsentStore`). `EndoRepository` depends on the interface; the test fakes it with a `MutableStateFlow`. Both live in `ConsentStore.kt` (the file the plan names).
- **Files:** `ConsentStore.kt`, `EndoRepository.kt`, `RepositoryConsentTest.kt`.

**3. [Discretion] `EndoRepository` takes DAOs individually + a `from(consent, db)` production factory**
- Rather than taking `EndoDatabase` directly, the constructor takes the 5 DAO interfaces so the consent test injects relaxed fakes with zero SQLite; `EndoRepository.from(consent, db)` performs the production wiring off the database. Within Claude's discretion per CONTEXT (repository/DAO layout).

## Threat Model Compliance
- **T-18-02 (EoP — health methods pre-consent, critical):** mitigated — `checkConsented()` is the first statement of every health method and throws `IllegalStateException`; `Unknown` counts as not-consented (fail closed). `RepositoryConsentTest` pins all 16 ops throwing under both `Unknown` and `NotConsented`, none under `Consented`.
- **T-18-07 (Repudiation — orphaned day-scoped rows after deleteDay, high):** mitigated — one `@Transaction` cascade over ALL 6 day-scoped types incl. `ImportedSample`; `CascadeCompletenessTest` (RED-capable) fails if a `DayScoped` entity is uncascaded; `DeleteDayCascadeTest` proves the exact survival set on emulator.
- **T-18-08 (Info disclosure — consent flag coupled to the health store, medium):** mitigated — the consent flag lives in `consent.preferences_pb` via DataStore; the grep gate confirms `ConsentStore.kt` imports no Room/DB/entity type.

## Next Plan Readiness
- Plan 04 (SnapshotProjector bridge) can proceed: it will add `daySnapshots()` to `EndoRepository` (this file is expected to be modified) reusing the existing consent gate + per-day fetch helpers, and the projector consumes the entity row lists.
- Phase 19 (UI shell / AppGate) consumes `ConsentStore` (the 3-state `StateFlow`, incl. the `Unknown` loading arm) and `EndoRepository`.
- No blockers.

## Self-Check: PASSED
All 7 created files and the modified `EndoDatabase.kt` exist on disk; the 3 task commits (`f6d9e5f`, `f4be39a`, `bdb0bbd`) are in git history; `:app:testDebugUnitTest` (8 tests, 0 failures), `:app:compileDebugAndroidTestKotlin`, and `:app:assembleDebug` verified green.

---
*Phase: 18-room-persistence*
*Completed: 2026-07-09*
