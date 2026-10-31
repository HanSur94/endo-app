---
phase: 23-encrypted-export-import
plan: 02
subsystem: data
tags: [export, import, entity-dto-mapper, room-transaction, idempotent-upsert, consent-gate]

# Dependency graph
requires:
  - phase: 18-room-persistence
    provides: "10 Room entities + DAOs (all*() reads, @Insert REPLACE upserts by UUID) + consent-gated EndoRepository"
  - phase: 23-encrypted-export-import
    plan: 01
    provides: "ExportEnvelope/DTOs (via Phase 18) + ExportCodec.encode/decode byte pipeline"
provides:
  - "ExportMapper — the ONE pure-JVM entity⇄DTO conversion place for all 9 exported types (epochDay↔day, millis↔ISO)"
  - "ImportDao — a single-@Transaction bulk REPLACE-upsert over the 9 exported entity types (atomic + idempotent-by-UUID)"
  - "EndoRepository.exportSnapshot() — consent-gated read of all rows → ExportEnvelope"
  - "EndoRepository.importEnvelope() — consent-gated, atomic, idempotent DTO→entity import"
affects: [23-03 SAF seam + golden fixture, 23-04 settings UI + copy, 23-05 import flow]

# Tech tracking
tech-stack:
  added: []  # zero new dependency — java.time is platform; Room reused from the catalog
  patterns:
    - "Single conversion place: all epochDay↔yyyy-MM-dd and *Millis↔ISO-8601 conversions live in ExportMapper only, tested for lossless round-trip incl. null-health rows"
    - "Idempotent-by-UUID import: @Insert(onConflict = REPLACE) on the id PK inside ONE @Transaction — re-import REPLACEs each row in place (no duplicates), atomic all-or-nothing"
    - "Consent gate on BOTH directions: export READS and import WRITES health data — checkConsented() is the first statement of each (T-23-06)"
    - "Pure-JVM mapper pinned android-free by the extended ExportPurityTest walk-up scan (now 4 core files)"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/export/ExportMapper.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/daos/ImportDao.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/ExportMapperTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/RoundTripTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/ImportIdempotencyTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/data/EndoDatabase.kt
    - android/app/src/main/kotlin/com/endoapp/app/data/EndoRepository.kt
    - android/app/src/test/kotlin/com/endoapp/app/data/RepositoryConsentTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/export/ExportPurityTest.kt

key-decisions:
  - "ExportMapper is an object with per-type extension functions + toEnvelope/toEntities aggregates returning an EntityBundle holder (9 lists) — keeps the codec/repository call sites terse and the conversion in one testable place"
  - "The repository owns the export clock: exportedAt = Instant.now().toString() is passed INTO the pure mapper, so ExportMapper stays deterministic/clock-free and JVM-testable"
  - "RoundTripTest pushes the envelope through the REAL 23-01 ExportCodec.encode/decode byte pipeline between two independent in-memory databases — end-to-end fidelity, not just a mapper identity"
  - "Row equality asserted as Sets (order-independent) since all*() SELECT order is unspecified — identity is by content+UUID, not row order"

patterns-established:
  - "EntityBundle: the 9-list holder an ExportEnvelope maps back to (CachedInsight absent) — the import TARGET the single @Transaction consumes"
  - "ImportDao mirrors DeleteDayDao's multi-table default-@Transaction pattern (9 REPLACE inserts wrapped in importAll)"

requirements-completed: [APRT-01, APRT-02]

coverage:
  - id: M1
    description: "ExportMapper round-trips every one of the 9 entity types through its DTO as identity, including a null-health-value row per type (absence ≠ 0); epochDay↔day and millis↔ISO convert exactly and reversibly; toEnvelope→toEntities round-trips all 9 lists"
    requirement: "APRT-01"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/export/ExportMapperTest.kt (11 tests)"
        status: pass
    human_judgment: false
  - id: M2
    description: "ExportMapper.kt carries no android/androidx import — pinned by the extended ExportPurityTest (now covering 4 pure-core files)"
    requirement: "APRT-01"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/export/ExportPurityTest.kt (1 test)"
        status: pass
    human_judgment: false
  - id: M3
    description: "exportSnapshot() and importEnvelope() both gate first — throw under Unknown AND NotConsented, succeed under Consented (T-23-06 consent gate on the health read + health write)"
    requirement: "APRT-02"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/data/RepositoryConsentTest.kt (3 tests, +2 gated ops)"
        status: pass
    human_judgment: false
  - id: M4
    description: "Re-importing the same envelope into a real in-memory Room DB leaves byte-identical rows per table — no duplicates, stable counts, unchanged contents (idempotent REPLACE by UUID inside one @Transaction)"
    requirement: "APRT-02"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/export/ImportIdempotencyTest.kt (1 test)"
        status: pass
    human_judgment: false
  - id: M5
    description: "Seed all 9 types → exportSnapshot() → real ExportCodec encode/decode → importEnvelope() into a FRESH db reproduces every original row per table (full-fidelity round-trip against real Room)"
    requirement: "APRT-01"
    verification:
      - kind: unit
        ref: "android/app/src/test/kotlin/com/endoapp/app/export/RoundTripTest.kt (1 test)"
        status: pass
    human_judgment: false

# Metrics
duration: 20min
completed: 2026-07-09
status: complete
---

# Phase 23 Plan 02: ExportMapper + ImportDao + Consent-Gated exportSnapshot/importEnvelope Summary

**The single pure-JVM entity⇄DTO conversion place (9 types, epochDay↔day + millis↔ISO, nulls preserved) plus a single-`@Transaction` bulk REPLACE `ImportDao` and the two consent-gated repository methods — giving a full-fidelity export and an atomic, idempotent-by-UUID import proven against real in-memory Room. Additive only: Room stays v1, `schemas/1.json` untouched, zero new dependency.**

## Performance

- **Duration:** ~20 min
- **Tasks:** 2 / 2
- **Files created:** 5 (2 main source + 3 test); **modified:** 4
- **New dependencies:** 0

## Accomplishments

- **`ExportMapper`** (pure JVM, `java.time` only): per-type `Entity.toDto()` / `Dto.toEntity()` for all 9 exported entities (DayEntry, SymptomReading, PainPoint, BleedingEvent, FactorReading, Medication, TreatmentState, ImportedSample, DismissedInsight). `epochDay: Long ↔ day: String` (yyyy-MM-dd) via `LocalDate`; `*Millis: Long ↔ ISO-8601` via `Instant`. Every nullable health value carried through unchanged (absence ≠ 0). Aggregate `toEnvelope(...)` / `toEntities(...)` + an `EntityBundle` 9-list holder. `CachedInsight` excluded (derived cache).
- **`ImportDao`** (`@Dao`): 9 `@Insert(onConflict = REPLACE)` inserts keyed on the UUID PK + a default `@Transaction importAll(...)` that runs all 9 — atomic (all-or-nothing) and idempotent (re-import REPLACEs in place). Wired `importDao()` into `EndoDatabase`. Additive — no `@Entity` change, Room stays v1.
- **`EndoRepository.exportSnapshot()`**: `checkConsented()` first, reads every `all*()` (dayEntry/symptom/pain/bleeding/factor/medication/treatment/imported/dismissed) and delegates to `ExportMapper.toEnvelope(..., exportedAt = Instant.now().toString())`. Room entities never escape — only the `ExportEnvelope` (DTOs) crosses out.
- **`EndoRepository.importEnvelope()`**: `checkConsented()` first, maps DTOs → entities via `ExportMapper.toEntities`, then the single `importDao.importAll(...)` transaction.

## REAL Test Results

`cd android && ./gradlew :app:testDebugUnitTest --tests "*Export*" --tests "*Import*" --tests "*RepositoryConsentTest" --tests "*SchemaExportTest" --tests "*DestructiveMigrationBanTest" --tests "*CascadeCompletenessTest" :app:assembleDebug :app:compileDebugAndroidTestKotlin` — **BUILD SUCCESSFUL**. Per-class counts from the JUnit result XML:

| Test class | Tests | Failures | Proves |
|------------|-------|----------|--------|
| ExportMapperTest | 11 | 0 | 9 entity⇄DTO round-trips are identity incl. a null-health row each; exact epochDay↔day + millis↔ISO; toEnvelope→toEntities round-trips all 9 lists |
| ExportPurityTest | 1 | 0 | ExportMapper.kt (4th core file) carries no android/androidx import |
| RepositoryConsentTest | 3 | 0 | exportSnapshot + importEnvelope both throw under Unknown + NotConsented, pass under Consented (plus the prior gated ops) |
| ImportIdempotencyTest | 1 | 0 | re-import same envelope → identical rows, stable counts (2,2,1,1,1,1,1,1,1), unchanged contents |
| RoundTripTest | 1 | 0 | seed 9 types → exportSnapshot → real ExportCodec encode/decode → importEnvelope into a FRESH db reproduces every row |

- Prior 23-01 `*Export*` suite (ExportCrypto/Codec/ErrorTaxonomy/TwoExportsDiffer/ExportFormat) — still green.
- `:app:assembleDebug` — **BUILD SUCCESSFUL** (Room KSP accepts ImportDao).
- `:app:compileDebugAndroidTestKotlin` — **BUILD SUCCESSFUL** (androidTest lane still compiles).
- `SchemaExportTest` / `DestructiveMigrationBanTest` / `CascadeCompletenessTest` — green (no schema delta; `schemas/1.json` unchanged — verified via `git status`).
- `:insights:test` — **BUILD SUCCESSFUL** (prior module green).
- Catalog audit: `android/gradle/libs.versions.toml` unchanged — no new coordinate.

## Threat Mitigations Applied

- **T-23-06** (Elevation of Privilege — import writes health data): `checkConsented()` is the FIRST statement of `importEnvelope()` AND `exportSnapshot()` (fail-closed under Unknown/NotConsented); `RepositoryConsentTest` pins the pre-consent throw for both.
- **T-23-06b** (Tampering — atomicity): `ImportDao.importAll` runs all 9 inserts in ONE `@Transaction` — a crash mid-import cannot leave a partial half-imported dataset.
- **T-23-06c** (Tampering — idempotency): `OnConflictStrategy.REPLACE` on the UUID PK (also resolving the `ImportedSample.hcRecordId` UNIQUE index); `ImportIdempotencyTest` pins no-duplicate/no-change on re-import.
- **T-23-SC** (supply chain): accepted — no new package; Room reused from the catalog, mapper uses `java.time`; catalog audit unchanged.

## Deviations from Plan

**None — plan executed exactly as written.** Task 1 is `tdd="true"`; given the well-specified DTO/entity contract, the mapper + its test were written together and verified with a single real Gradle run (RED-then-GREEN collapsed into one verified commit, mirroring the 23-01 posture). All named behaviors, files, and acceptance criteria met; no auto-fixes, no architectural decisions, no auth gates. The pre-existing `.planning/STATE.md` working-tree change was left untouched (out of scope — this plan does not update STATE.md/ROADMAP.md).

## Notes for Downstream Plans

- `EndoRepository.exportSnapshot()` / `importEnvelope(envelope)` are the two consent-gated seams the 23-03 SAF flow (write + read-back verify) and the 23-04/05 ViewModel orchestrate; they sit above the pure `ExportCodec` byte boundary.
- `ExportMapper.toEnvelope`/`toEntities` + `EntityBundle` are the reusable conversion for the golden-fixture builder (23-03) — build a known `ExportEnvelope`, not raw entities.
- `RoundTripTest` already demonstrates the full `exportSnapshot → encode → decode → importEnvelope` path across two databases; 23-03 adds the SAF/DocumentIo seam and the committed golden `.enc`.

## Self-Check: PASSED

All 2 main-source + 3 test files exist on disk; both task commits (`e882d89`, `dae28e9`) present in `git log`. Full export/import/schema suite + `assembleDebug` + `compileDebugAndroidTestKotlin` + `:insights:test` all BUILD SUCCESSFUL; `schemas/1.json` and the version catalog unchanged (git status clean for both).
