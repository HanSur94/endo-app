# Phase 18: Room Persistence - Research

**Researched:** 2026-07-09
**Domain:** Room 2.8.4 persistence data layer + insightsbridge/SnapshotProjector + ClaimsPolicy/CopyAudit harness + versioned export-format contract — the `:app` module data half, mirroring the shipped iOS SwiftData layer (`Sources/EndoApp/Persistence/*`)
**Confidence:** HIGH (this is a port/mirror phase; the iOS source was read line-by-line and is the spec — the "research" is the source inventory, not a design search)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

Architecture is LOCKED in ANDROID-PORT-PLAN.md §3, ARCHITECTURE.md, STACK.md, PITFALLS.md. Do not re-open. Key locked decisions:

**Module & build**
- Create the `:app` module (`com.android.application` + built-in Kotlin — do NOT apply `org.jetbrains.kotlin.android` under AGP 9). This phase adds ONLY the data layer; the Compose UI shell arrives in Phase 19.
- `:app` depends on `:insights`, never the reverse. Add Room 2.8.4 + KSP 2.3.9 (never kapt; no `room-ktx`), DataStore 1.2.0 to the version catalog. compileSdk 36 / minSdk 26 / targetSdk 36. The version-catalog audit test (Phase 17) must still pass — no analytics/auth/backend/networking coordinate.

**Room schema (10 entities, iOS model names)**
- Mirror the 10 SwiftData models by name: DayEntry, SymptomReading, PainPoint, BleedingEvent, FactorReading, Medication, TreatmentState, ImportedSample, CachedInsight, DismissedInsight. App-generated UUID string primary keys; `epochDay: Long` (or ISO date) day identity; enum values stored as raw strings matching the `:insights`/domain raw values (reuse the ported enums; `FactorKind` lives here in `:app`, NOT in `:insights` — carry its 7 raw values verbatim, cross-checked against the Phase-17 golden-JSON header).
- Room v1 migration discipline is IRREVERSIBLE and must be born correct: `exportSchema = true`, committed `android/app/schemas/`, a `MigrationTestHelper` scaffold, and `fallbackToDestructiveMigration` structurally BANNED (a test asserts it is never called).
- CloudKit's constraints do NOT apply on Android — Room permits non-null columns and unique indices. Still keep app-generated UUID identity so export/import round-trips are stable (Phase 23).

**Consent gating (consent-by-construction, data layer half)**
- Consent flag + reminder prefs in DataStore, OUTSIDE the health database (mirrors the iOS UserDefaults split). `ConsentStore` exposes the flag.
- `EndoRepository` is a consent-gated facade — every health read/write asserts consent (throws / returns empty pre-consent), mirroring the iOS repository `assertConsented`. The UI-level gate lands in Phase 19; this phase makes the repository itself refuse pre-consent.

**Delete-day cascade (iOS GDPR Art. 17 lesson — born complete)**
- Deleting a day purges EVERY day-scoped row type in ONE `@Transaction`, while non-day-scoped definitions (Medication, and TreatmentState per its semantics) survive. Enforce with (a) a cascade test proving all day-scoped rows are purged, (b) a survival test for definitions, and (c) a reflection/meta-test that fails if a new day-scoped entity is added without being included in the cascade.

**insightsbridge / SnapshotProjector**
- `insightsbridge/SnapshotProjector` maps fetched Room rows → `:insights` `DaySnapshot` values as a PURE function — the ONLY place platform persistence types and pure domain types meet. Runs as JVM unit tests (no emulator). Free text (notes) never crosses the bridge — only `hasNote`. Port the iOS `daySnapshots()` mapping + its parity test suite (the 24-case suite).

**ClaimsPolicy + CopyAudit harness (before UI copy)**
- Port the Kotlin `ClaimsPolicy` (sharing the SAME substring lists as the Swift `Content/ClaimsPolicy.swift`; the Phase-17 drift test already guards the `:insights` copy — here add the `:app` ClaimsPolicy + a `CopyAudit` JVM test scanning a registered copy catalog). Rule carried verbatim: every user-facing string is registered for audit IN THE SAME CHANGE that introduces it.

**Export FORMAT contract (implementation deferred to Phase 23)**
- Define the versioned, platform-neutral export JSON schema now (schema-version field, ISO-8601 dates, enum raw values matching the shared domain vocabulary, UUID identity) plus the crypto header spec (PBKDF2 params, AES-GCM, salt/nonce layout) as a DATA CONTRACT + kotlinx-serialization DTOs. Do NOT implement encrypt/decrypt/SAF here — that is Phase 23.

**Backup exclusion**
- Exclude the Room health database from Android Auto Backup via backup rules (`dataExtractionRules` / `fullBackupContent`); only the consent flag / prefs may back up. Assert with a test that the DB file/path is in the exclusion set. No `INTERNET` permission (that manifest test lands in Phase 19; keep `:app`'s manifest minimal here).

### Claude's Discretion
- Exact DAO/entity/repository package layout under `com.endoapp.app.data`, TypeConverters, and Flow-vs-suspend accessor choices — Claude's discretion, guided by ARCHITECTURE.md. Prefer suspend DAOs + Flow observation.
- Whether `:app` unit tests use JUnit 4 (AGP-native) for Android-touching tests and JUnit 5 for pure-JVM bridge tests, or Robolectric where a Context is needed — Claude's discretion; keep the pure `SnapshotProjector`/ClaimsPolicy tests as fast JVM tests.

### Deferred Ideas (OUT OF SCOPE)
- Encrypted export/import IMPLEMENTATION (encrypt/decrypt/SAF/passphrase UX) → Phase 23. This phase defines only the format + crypto-header contract.
- The `AppGate` consent-by-construction UI + manifest no-INTERNET test → Phase 19 (this phase gates the repository, not the composables).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| APER-01 | All 10 data models persist in Room (iOS model names, app-generated UUID identity) behind a consent-gated repository (health reads/writes fail pre-consent) | §Standard Stack (Room/KSP/DataStore pins), §Field-Level Entity Mapping (all 10 entities), §Consent (`assertConsented` → `checkConsented()` throw) |
| APER-02 | User can delete a day and EVERY day-scoped row is purged in one transaction, while non-day-scoped definitions (Medication) survive — cascade + survival tests | §Delete-Day Cascade (day-scoped classification, the ImportedSample gap finding, `@Transaction`, reflection meta-test) |
| APER-03 | Room migration discipline from schema v1 — `exportSchema` on, `schemas/` committed, migration test scaffold, destructive migration structurally banned | §Room 2.8.4 Specifics (exportSchema/KSP arg, MigrationTestHelper, destructive-fallback ban test) |
| APER-04 | Health database excluded from Android Auto Backup via backup rules, assertion test; only the consent flag/prefs may back up | §Backup Exclusion (dataExtractionRules XML + assertion test) |
| APER-05 | SnapshotProjector bridge maps Room rows → `:insights` DaySnapshot as the ONLY persistence↔domain meeting point, ported mapping tests (free text never crosses) | §The daySnapshots() Projection (fold rules, manual-wins merge, 24-case parity suite) |
| APER-06 | Kotlin ClaimsPolicy port + CopyAudit JVM harness BEFORE any UI copy — every user-facing string registers for audit in the change that introduces it | §ClaimsPolicy + CopyAudit (exact substring lists, reuse-vs-add analysis, registration mechanism) |
| APER-07 | Export format defined — schema-versioned, platform-neutral JSON with crypto header spec and UUID identity (format contract precedes implementation) | §Export Format Contract (versioned JSON DTO shape + crypto header layout) |
</phase_requirements>

## Summary

Phase 18 is a **mirror port**, not a design search. The shipped iOS SwiftData layer (`Sources/EndoApp/Persistence/`) is the exact specification: 10 `@Model` types, one `daySnapshots()` projection seam, one `deleteDay` cascade, one `assertConsented` guard, and the `Content/ClaimsPolicy.swift` substring lists. This document enumerates each of those sources field-by-field so the planner can produce mechanical, TDD-first plans. Every value below was read directly from the Swift source in this session and is tagged `[VERIFIED: iOS source]`; version pins are `[CITED: STACK.md]` (verified against developer.android.com on 2026-07-08).

Three findings deserve the planner's attention. **First**, the iOS `deleteDay` (Repository.swift lines 282–295) purges symptoms, pain, bleeding, factors, and day-entries — but **NOT** `ImportedSample`, even though `ImportedSample` carries a `day` column and is day-scoped. This is the *same class of orphan bug* the iOS 04-02 fix closed for `FactorReading`. The Android cascade must be **more complete than current iOS**: include `ImportedSample`. The reflection meta-test (day-scoped-by-column-convention) is precisely what forces this. **Second**, the enum raw-value vocabulary is already ported and drift-guarded in `:insights` (Phase 17) — the `:app` layer reuses those enums via `fromRaw`/`fromRawOrNull` at the bridge and stores raw strings in Room columns; only `FactorKind` (7 raws) is new and lives in `:app`. **Third**, the three forbidden-substring lists (`causal`/`imperative`/`alarmist`) already exist as the single drift-guarded source in `:insights` `ForbiddenTerms`; the `:app` `ClaimsPolicy`/`CopyAudit` must **reuse** those and add only the app-only lists (`forbiddenGenderedTokens`, false-precision tokens, `allowedFraming`) plus the registered-catalog scan mechanism — never a second divergent copy of the shared three.

**Primary recommendation:** Build entities → TypeConverters → DAOs → `@Transaction` DeleteDayDao (with `ImportedSample` included and a reflection completeness meta-test) → `ConsentStore` (DataStore) → consent-gated `EndoRepository` → `insightsbridge/SnapshotProjector` (+ the 24-case JVM parity suite ported case-for-case) → migration scaffold (`exportSchema`, committed `schemas/1.json`, `MigrationTestHelper`, destructive-fallback ban test) → backup-rules XML + assertion test → `:app` `ClaimsPolicy` reusing `:insights` `ForbiddenTerms` + `CopyAudit` registered-catalog harness → export-format DTOs + crypto-header contract (data only).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Local health persistence (10 entities) | Database/Storage (`:app` `data/`) | — | Room over SQLite; no server tier exists (no-backend invariant) |
| Consent flag storage | Database/Storage (DataStore, separate file) | — | Deliberately OUTSIDE the health DB (mirrors iOS UserDefaults split); it gates the DB, so it must not live in it |
| Consent enforcement | API/Backend (`EndoRepository` facade) | — | The repository is the only health-data access path; `checkConsented()` is the first statement of every health method |
| Delete-day cascade (GDPR Art. 17) | Database/Storage (`@Transaction` DAO) | API/Backend (repo delegates) | Atomicity requires one transaction at the DAO layer, not N repo calls |
| Rows → domain projection | API/Backend (`insightsbridge/SnapshotProjector`) | Pure domain (`:insights` `DaySnapshot`) | The ONLY place Room entity types and `:insights` types both appear; pure function → JVM-testable |
| Copy-policy enforcement | Build/test surface (`:app` test/) | Pure domain (`:insights` `ForbiddenTerms`) | Reuses the drift-guarded shared lists; adds app-only scans + registered catalog |
| Export format contract | Data contract (kotlinx-serialization DTOs) | — | Pure DTO + header spec; crypto/SAF implementation is Phase 23 |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.room:room-runtime` | 2.8.4 | 10 mirrored entities + DAOs; coroutines/Flow included (no `room-ktx`) | `[CITED: STACK.md]` Latest stable of the `androidx.room` line; deliberately NOT `androidx.room3` (KMP-focused new coordinates) — Google recommends 2.x for Android-only apps |
| `androidx.room:room-compiler` | 2.8.4 (via `ksp(...)`) | Room codegen | `[CITED: STACK.md]` KSP only — kapt banned |
| `com.google.devtools.ksp` | 2.3.9 | Room annotation processing | `[CITED: STACK.md]` Standalone versioning since KSP 2.3.0; pin with Kotlin in the same catalog entry |
| `androidx.datastore:datastore-preferences` | 1.2.0 | Consent flag + reminder prefs (outside the health DB) | `[CITED: STACK.md]` Preferences DataStore only — a boolean + timestamp; NOT 1.3.0-alpha (alpha crypto) |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | Versioned export/import JSON DTOs (contract only this phase) | `[CITED: STACK.md]` First-party-adjacent, compile-time-safe; plugin version must equal Kotlin (2.3.21) |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.room:room-testing` | 2.8.4 | `MigrationTestHelper` | androidTest — the migration scaffold this phase requires |
| `org.junit.jupiter:junit-jupiter` | 6.1.1 | pure-JVM tests (SnapshotProjector, ClaimsPolicy) if placed in `:insights`-style JVM module | `[CITED: STACK.md]` `:insights` already uses Jupiter; `:app` unit tests are JUnit4-native (see Discretion) |
| `junit:junit` | 4.13.2 | `:app` unit tests (CopyAudit, structural scans) + androidTest runner base | `[CITED: STACK.md]` AGP unit-test + instrumentation stack is JUnit4-native |
| `androidx.test.ext:junit` | 1.3.0 | `AndroidJUnit4` runner for androidTest | Room migration + cascade instrumentation tests |
| `javax.crypto` / `java.security` | platform (API 26+) | Export crypto header *contract* (PBKDF2 params, AES-GCM layout) — NO implementation this phase | `[CITED: STACK.md]` Zero-dependency; `PBKDF2WithHmacSHA256` since API 26; implementation is Phase 23 |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Room 2.8.4 | Room 3.0.0 (`androidx.room3`) | Only if this ever goes KMP (rejected for v1); entities port mechanically (`androidx.room.*` → `androidx.room3.*`) later — choosing 2.8.4 now burns nothing |
| `epochDay: Long` day key | ISO-8601 String day key | `epochDay` is lossless, orderable, timezone-proof, and cheap to index; ISO is used ONLY at the export-JSON boundary (`java.time.LocalDate.toEpochDay()` / `LocalDate.ofEpochDay()`) |
| Reflection completeness meta-test | Hand-enumerated cascade list | Hand list goes stale silently when Phase 21 adds/extends day-scoped rows; reflection over a `DayScoped` marker (or `epochDay` column convention) fails loudly — locked decision |

**Installation:** Add to `android/gradle/libs.versions.toml` (already partly present from Phase 17). New coordinates this phase:
```toml
[versions]
room = "2.8.4"
ksp = "2.3.9"            # already present
datastore = "1.2.0"
kotlinxSerialization = "1.11.0"
[libraries]
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```
`:app/build.gradle.kts` applies `com.android.application` (built-in Kotlin — NO `org.jetbrains.kotlin.android`), `kotlin-compose` (Phase 19), `kotlin-serialization`, `ksp`; `implementation(project(":insights"))`; `ksp(libs.room.compiler)`.

**Version verification:** All coordinates are first-party AndroidX / JetBrains **Maven** artifacts pinned and verified against developer.android.com in STACK.md (2026-07-08). Confirm each is still current at plan time with `./gradlew :app:dependencies` after the catalog is written; the version-catalog audit test (Phase 17) additionally proves no analytics/auth/backend coordinate slipped in.

## Package Legitimacy Audit

> All Phase-18 dependencies are first-party AndroidX (Google) / JetBrains **Maven** coordinates. The `gsd-tools query package-legitimacy check` seam covers npm/pypi/crates, not Maven, so legitimacy here is established by (a) official developer.android.com / kotlinlang.org provenance in STACK.md and (b) the existing version-catalog audit test that fails on any non-allowlisted coordinate.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| androidx.room:room-runtime / -compiler / -testing 2.8.4 | Maven (Google) | mature | ubiquitous | github.com/androidx/androidx | OK | Approved (official AndroidX) |
| androidx.datastore:datastore-preferences 1.2.0 | Maven (Google) | mature | ubiquitous | github.com/androidx/androidx | OK | Approved (official AndroidX) |
| com.google.devtools.ksp 2.3.9 | Maven (Google) | mature | ubiquitous | github.com/google/ksp | OK | Approved |
| org.jetbrains.kotlinx:kotlinx-serialization-json 1.11.0 | Maven (JetBrains) | mature | ubiquitous | github.com/Kotlin/kotlinx.serialization | OK | Approved |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
                 Phase 19+ UI (NOT this phase)
                        │  reads DaySnapshot / observes Flows
                        ▼
   ┌──────────────────────────────────────────────────────────────┐
   │ EndoRepository  (consent-gated facade — the ONLY health path) │
   │   checkConsented() is the FIRST statement of every method     │
   └───────┬───────────────────────────────────┬──────────────────┘
           │ writes/reads (suspend)             │ project once per read
           ▼                                    ▼
   ┌─────────────────┐              ┌───────────────────────────────┐
   │ Room DAOs (10)  │              │ insightsbridge/                │
   │  + DeleteDayDao │─fetch rows──▶│ SnapshotProjector.project(...) │ PURE fn
   │   @Transaction  │              │  rows → List<DaySnapshot>      │
   └───────┬─────────┘              │  manual-wins merge, hasNote,   │
           │                        │  suppressed stamp, nil≠0       │
           ▼                        └──────────────┬────────────────┘
   ┌─────────────────┐                             │ DaySnapshot (domain)
   │ EndoDatabase    │                             ▼
   │ @Database v1    │              ┌───────────────────────────────┐
   │ exportSchema=on │              │ :insights (pure JVM, Phase 17) │
   │ schemas/1.json  │              │  DaySnapshot · enums · Factor  │
   └─────────────────┘              │  ForbiddenTerms · InsightsEngine│
                                    └───────────────────────────────┘
   ┌─────────────────┐   consent flag is OUTSIDE the health DB
   │ ConsentStore    │── DataStore (consent.preferences_pb) ── excluded-from-backup? NO: prefs MAY back up
   └─────────────────┘   Room DB file ── dataExtractionRules EXCLUDES it from Auto Backup

   ┌─────────────────────────────────────────────────────────────┐
   │ :app test/  ClaimsPolicy (reuses :insights ForbiddenTerms)   │
   │             + CopyAudit (registered-catalog scan)            │
   │ export/  ExportEnvelope + CryptoHeader DTOs  (CONTRACT ONLY) │
   └─────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure (Claude's Discretion — guided by ARCHITECTURE.md)
```
android/app/src/
├── main/
│   ├── res/xml/data_extraction_rules.xml    # EXCLUDE Room DB; allow DataStore prefs
│   └── kotlin/com/endoapp/app/
│       ├── data/
│       │   ├── EndoDatabase.kt              # @Database(version=1, exportSchema=true), 10 entities
│       │   ├── Converters.kt                # LocalDate<->epochDay Long, Instant<->epochMillis
│       │   ├── entities/                    # 10 entity files, iOS model names
│       │   ├── daos/                        # per-aggregate DAOs + DeleteDayDao (@Transaction)
│       │   ├── EndoRepository.kt            # consent-gated facade (checkConsented first)
│       │   ├── ConsentStore.kt             # DataStore-backed; ConsentState 3-state
│       │   └── DayScoped.kt                 # marker interface driving the cascade meta-test
│       ├── insightsbridge/
│       │   ├── SnapshotProjector.kt         # rows → List<DaySnapshot> (daySnapshots() port)
│       │   ├── ImportedSignals.kt           # per-day imported fold (sleep SUM, workout longest-type)
│       │   └── FactorKind.kt                # 7 raws — lives in :app, NOT :insights
│       ├── content/
│       │   └── ClaimsPolicy.kt              # app-only lists; REUSES :insights ForbiddenTerms
│       └── export/
│           ├── ExportEnvelope.kt            # @Serializable versioned DTO tree (CONTRACT)
│           └── CryptoHeader.kt              # @Serializable header spec (CONTRACT)
├── schemas/1.json                           # committed Room schema baseline
├── test/kotlin/…                            # JVM: SnapshotProjector 24-case, CopyAudit, ClaimsPolicy, export golden-shape
└── androidTest/kotlin/…                     # instrumentation: migration scaffold, delete-day cascade on real SQLite
```

### Pattern 1: Field-Level Entity Mapping (the 10 models)

Each iOS `@Model` maps to a Room `@Entity` with the same class name. Mapping rules `[VERIFIED: iOS source, Sources/EndoApp/Persistence/Models/*.swift]`:
- Swift `id: UUID` (app-generated, default `UUID()`, NOT `.unique`) → Room `@PrimaryKey val id: String` (store `uuid.toString()`; keep app-generated identity for export round-trips).
- Swift `Date` day/timestamp → Room `epochDay: Long` for day-bucketed columns (`LocalDate.toEpochDay()`; the iOS `Calendar.startOfDay` bucketing becomes `instant.atZone(zone).toLocalDate()`). Non-day timestamps (`createdAt`, `importedAt`, `computedAt`, `dismissedAt`, `startedAt`) → `epochMillis: Long` via a TypeConverter.
- Swift optional enum-backing `*Raw: String?` → Room nullable `String?` column storing the raw verbatim. **Do not store ordinals.**
- Swift `Int?` / `Double?` / `Bool?` optionals → Room nullable columns. **CloudKit forced iOS optional; on Android keep health-value columns nullable to preserve nil≠0**, but `id`/day-key/timestamps may be non-null (Anti-Pattern 3).

**Day-scoped classification (the cascade contract):**

| # | Entity | iOS stored properties (Swift type) | Room columns | Class | In deleteDay cascade? |
|---|--------|------------------------------------|--------------|-------|------------------------|
| 1 | **DayEntry** | `id:UUID`, `date:Date`, `note:String?` | id String PK, epochDay Long, note String? | **Day-scoped** | ✅ purge |
| 2 | **SymptomReading** | `id:UUID`, `occurredOn:Date`, `typeRaw:String?`, `severity:Int?`, `note:String?` | id PK, epochDay, typeRaw String?, severity Int?, note String? | **Day-scoped** | ✅ purge |
| 3 | **PainPoint** | `id:UUID`, `occurredOn:Date`, `regionRaw:String?`, `severity:Int?` | id PK, epochDay, regionRaw String?, severity Int? | **Day-scoped** | ✅ purge |
| 4 | **BleedingEvent** | `id:UUID`, `occurredAt:Date`, `flowRaw:String?`, `note:String?`, `clots:Bool?` | id PK, epochDay, flowRaw String?, note String?, clots Boolean? | **Day-scoped** | ✅ purge |
| 5 | **FactorReading** | `id:UUID`, `occurredOn:Date`, `kindRaw:String?`, `valueRaw:String?`, `amount:Double?`, `level:Int?`, `note:String?` | id PK, epochDay, kindRaw String?, valueRaw String?, amount Double?, level Int?, note String? | **Day-scoped** | ✅ purge |
| 6 | **ImportedSample** | `id:UUID`, `hkSampleUUID:String?`, `day:Date`, `domainRaw:String?`, `flowRaw:String?`, `sleepHours:Double?`, `sleepQualityRaw:String?`, `activityTypeRaw:String?`, `activityMinutes:Double?`, `ateThatDay:Bool?`, `importedAt:Date` | id PK, hcRecordId String? (unique index — see below), epochDay, domainRaw String?, flowRaw String?, sleepHours Double?, sleepQualityRaw String?, activityTypeRaw String?, activityMinutes Double?, ateThatDay Boolean?, importedAtMillis Long | **Day-scoped** | ✅ purge (**NEW vs iOS — see finding below**) |
| 7 | **Medication** | `id:UUID`, `name:String?`, `note:String?`, `createdAt:Date`, `isArchived:Bool?` | id PK, name String?, note String?, createdAtMillis Long, isArchived Boolean? | **Definition** | ❌ survives |
| 8 | **TreatmentState** | `id:UUID`, `startedAt:Date`, `modeRaw:String?` | id PK, startedAtMillis Long, modeRaw String? | **Standalone/definition** | ❌ survives |
| 9 | **CachedInsight** | `id:UUID`, `signalKey:String?`, `kindRaw:String?`, `windowRaw:String?`, `payload:Data?`, `computedAt:Date`, `fingerprint:String?` | id PK, signalKey String?, kindRaw String?, windowRaw String?, payload ByteArray? (or String JSON), computedAtMillis Long, fingerprint String? | **Cache/derived** (replace-all on recompute) | ⚠️ delete-all on any day-delete (cache invalidation), not day-keyed |
| 10 | **DismissedInsight** | `id:UUID`, `signalKey:String?`, `dismissedAt:Date` | id PK, signalKey String?, dismissedAtMillis Long | **Category-keyed append-only** | ❌ survives (dismissals are by signalKey, not day) |

**`ImportedSample` uses Room's freedoms:** iOS deduped in code by `hkSampleUUID`; on Android add `@Index(value=["hcRecordId"], unique=true)` and still upsert-by-id in code (Pitfall 7). Rename the column `hcRecordId` for Phase 21 (Health Connect `metadata.id`), storing the same dedupe semantics.

**FactorReading kind→column encoding** `[VERIFIED: iOS FactorReading.swift + FactorKind.swift]` — one model discriminated by `kindRaw` (`FactorKind` 7 raws: `food`, `activity`, `sleep`, `medication`, `stress`, `mood`, `other`):
| kind | valueRaw | amount | level | note |
|------|----------|--------|-------|------|
| food | `FoodTag.raw` (one row per tag) | — | — | free-text (ONE extra `.food` row holds free-text) |
| activity | `ActivityType.raw` | minutes | intensity 0/1/2 (low/medium/high) | — |
| sleep | `SleepQuality.raw` (optional) | hours | — | — |
| medication | saved-med `id.uuidString`, or `"prn"` marker | — | — | — |
| stress | — | — | 0…10 | — |
| mood | — | — | 1…5 | — |

### Pattern 2: The `daySnapshots()` Projection (SnapshotProjector)

`[VERIFIED: iOS source, Repository.swift daySnapshots() lines 533–591, makeDaySnapshot lines 864–939, projectFactors lines 945–1000, ImportedSignals lines 793–859]`

**Shape (pure function):** takes the fetched row lists + `suppressed: Boolean`, returns `List<DaySnapshot>` ascending by day. Port signature:
```kotlin
object SnapshotProjector {
    fun project(
        symptoms: List<SymptomReading>, pain: List<PainPoint>,
        bleeding: List<BleedingEvent>, factors: List<FactorReading>,
        entries: List<DayEntry>, imported: List<ImportedSample>,
        suppressed: Boolean,
    ): List<DaySnapshot>
}
```

**Union-of-days:** bucket every day-scoped type by `epochDay` (iOS uses `Calendar.startOfDay`), then `dayKeys = union(symptoms, pain, bleeding, factors, noteDays, imported)`, sorted ascending → one `DaySnapshot` per day carrying ANY signal. `noteDays` = the set of days whose `DayEntry.note != null`.

**Per-field fold rules (each Room column → DaySnapshot field):**
| DaySnapshot field | Source | Fold rule |
|-------------------|--------|-----------|
| `symptoms: [SymptomPoint]` | SymptomReading rows | `compactMap`: nil/undecodable `typeRaw` → **DROP** (never fabricate `.other`); nil `severity` stays nil |
| `pain: [PainPoint]` | PainPoint rows | `compactMap`: unknown `regionRaw` → **DROP** (via `BodyRegion.fromRawOrNull`) |
| `flow: FlowIntensity?` | first flow-bearing BleedingEvent | `FlowIntensity.fromRawOrNull(flowRaw)`; else manual nil, then imported |
| `clots: Bool` | that event's `clots` | `clots ?? false` |
| `foodTags: [FoodTag]` | FactorReading `.food` rows | one tag per non-note row via `FoodTag.fromRaw` (unknown→OTHER); note-bearing `.food` row is **NOT carried** (privacy); if empty AND imported `ateThatDay==true` → `[OTHER]` |
| `activityType/Minutes/Intensity` | FactorReading `.activity` | `ActivityType.fromRaw(valueRaw)`, `amount`→minutes, `level` 0/1/2→`ActivityIntensity` low/medium/high |
| `sleepHours/Quality` | FactorReading `.sleep` | `amount`→hours, `SleepQuality.fromRawOrNull(valueRaw)` |
| `medicationsTaken: [UUID]` | FactorReading `.medication` | `valueRaw` parsed as UUID (skip `"prn"`) |
| `tookPRN: Bool` | FactorReading `.medication` valueRaw=="prn" | true if present |
| `stress: Int?` / `mood: Int?` | FactorReading `.stress`/`.mood` `level` | **nil≠0** — missing row ⇒ nil; a level-0 row ⇒ 0 |
| `hasNote: Bool` | DayEntry.note presence | `noteDays.contains(day)` — **presence only, prose never crosses** |
| `suppressed: Bool` | latest TreatmentState | `latest.modeRaw == "suppressed"`, stamped on EVERY snapshot |

**Manual-wins, per-field, nil-honest merge** (`[VERIFIED: makeDaySnapshot lines 898–919]`): for each imported-capable field (flow, sleepHours, sleepQuality, activity type+minutes, food-presence): **manual present ⇒ manual; else imported present ⇒ imported; else nil.** Activity type+minutes are one unit — imported fills both ONLY when `manual activityType == null`. Imported food contributes a single `[OTHER]` tag ONLY when there are no manual food tags.

**ImportedSignals per-day fold** (`[VERIFIED: ImportedSignals lines 793–859]`, matters for Phase 21 but the bridge shape is defined now): decode rows by `HealthDomain` (`cycle`/`sleep`/`workouts`/`nutrition`, 4 raws) — **sleep = SUM of the night's segment rows** then derive coarse quality from the summed total; **workouts = SUM minutes, keep the LONGEST session's type** (deterministic); cycle/nutrition single-valued (deduped upstream). Unknown domain raw → contribute nothing (never crash).

**Enum bridge:** all catalog enums already exist in `:insights` with `fromRaw`/`fromRawOrNull` (Phase 17). The bridge decodes Room raw strings via those defensive decoders — unknown → OTHER sink (SymptomType/FoodTag/ActivityType) or null (BodyRegion/FlowIntensity/ActivityIntensity/SleepQuality), never throws. `FactorKind` (new, `:app`) needs its own `fromRaw(raw): FactorKind` (unknown/null → `OTHER`), verbatim from `Sources/EndoApp/Domain/FactorKind.swift`.

**24-case parity suite to mirror** (`[VERIFIED: Tests/EndoAppTests/DaySnapshotMappingTests.swift, 24 @Test cases]`) — port case-for-case as JVM tests with hand-built entity instances (no DB):
1. `sameDay_collapsesIntoOneSnapshot`
2. `differentDays_distinctSnapshots_sortedAscending`
3. `symptom_mapsTypeAndSeverity`
4. `symptom_nilSeverity_staysNil`
5. `pain_mapsRegionAndSeverity`
6. `pain_unknownRegion_isDropped_noCrash`
7. `bleeding_flowAndClots_mapped`
8. `bleeding_nilClots_coalescesToFalse`
9. `bleeding_noEvent_flowIsNil`
10. `unloggedStressAndMood_areNil_notZero`
11. `loggedStressZero_isZero_notNil`
12. `factorFields_mapToPackageEnums`
13. `hasNote_reflectsPresenceOnly`
14. `suppressed_propagatesOntoEverySnapshot`
15. `unknownRaws_decodeDefensively_noCrash`
16. `unknownSymptomType_isDropped_notFabricatedAsOther`
17. `nilSymptomType_isDropped_notFabricatedAsOther`
18–24. `enumParity_{symptomType,bodyRegion,flowIntensity,foodTag,activityType,activityIntensity,sleepQuality}`

### Pattern 3: Delete-Day Cascade + completeness meta-test

`[VERIFIED: iOS deleteDay Repository.swift lines 282–295]` — the iOS cascade deletes: SymptomReading, PainPoint, BleedingEvent, FactorReading, DayEntry (by same-day). It explicitly leaves Medication + TreatmentState.

```kotlin
@Dao interface DeleteDayDao {
    @Transaction
    suspend fun deleteDay(epochDay: Long) {
        deleteSymptoms(epochDay); deletePain(epochDay); deleteBleeding(epochDay)
        deleteFactors(epochDay); deleteImported(epochDay); deleteEntries(epochDay)
        // CachedInsight: delete-all (cache invalidation — fingerprint recompute rebuilds)
    }
}
```

**Room equivalent:** one `@Transaction` method so a crash mid-cascade cannot orphan (Pitfall 9 — the iOS bug was *coverage*, Room adds an *atomicity* trap). Repository delegates; the cascade is never N sequential repo calls.

**Reflection/meta-test (the signature carry-over):** drive from a single canonical source — a `DayScoped` marker interface on entities (or the `epochDay` column convention). The test reflects over `EndoDatabase` entities, computes the day-scoped set, and asserts the cascade covers exactly it. It **fails when a new day-scoped entity is added but not cascaded** (e.g., a future Phase-21 extension). This is what makes the cascade "born complete" and what forces the ImportedSample inclusion.

### Anti-Patterns to Avoid
- **Coercing nil→0 anywhere** (`severity ?: 0`, non-null `DEFAULT 0` columns for optional health signals): breaks absence≠0. Room *allows* non-null, so the temptation is bigger than on iOS — spend non-null only on id/day-key/timestamps `[VERIFIED: DaySnapshot.swift honesty contract, ARCHITECTURE Anti-Pattern 3]`.
- **Exposing Room entities or `Flow<Entity>` to `ui/`** (Phase 19+): entity types must appear only in `data/` and `insightsbridge/`; `ui/` consumes `DaySnapshot`/`Insight` `[ARCHITECTURE Anti-Pattern 2]`.
- **A second divergent copy of the forbidden-substring lists**: `causal`/`imperative`/`alarmist` live once in `:insights` `ForbiddenTerms` (drift-guarded against Swift). The `:app` ClaimsPolicy REUSES them (see §ClaimsPolicy).
- **`fallbackToDestructiveMigration*`**: banned by a structural test — it would wipe user health history on the first schema bump `[PITFALLS Pitfall 8]`.
- **`allowMainThreadQueries()`**: never — all DAO methods `suspend`/`Flow` from the start.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Schema history baseline | A hand-written migration list | Room `exportSchema=true` + committed `schemas/1.json` + `MigrationTestHelper` | Auto-migration + migration tests are impossible later if v1 schema was never exported `[PITFALLS Pitfall 8]` |
| Day identity from timestamps | `epochMillis / 86_400_000` bucketing | `java.time.LocalDate` / `toEpochDay()` at the `:app` boundary | DST days are 23/25h; some zones have no midnight; UTC bucketing shifts evening logs to tomorrow `[PITFALLS Pitfall 11]` |
| Transactional cascade | N sequential suspend deletes in the repo | One `@Transaction` DAO method | A crash mid-cascade orphans rows (the exact iOS GDPR bug) `[PITFALLS Pitfall 9]` |
| Enum raw decoding | New Kotlin enums in `:app` | Reuse `:insights` enums + `fromRaw`/`fromRawOrNull` | Already drift-guarded against Swift (Phase 17); a second copy would diverge |
| Forbidden-substring lists | New `:app` copies of causal/imperative/alarmist | Reference `:insights` `ForbiddenTerms` | Single source of truth already exists + is drift-guarded |
| Consent-flag persistence | A row in the health DB | Preferences DataStore in a separate file | A synced/backed-up consent record couples "may I process" to the store it gates `[ARCHITECTURE Pattern 5]` |
| JSON codec for export | Gson/Moshi/hand-rolled | kotlinx-serialization | First-party-adjacent, compile-time-safe, already the pinned choice |

**Key insight:** Almost nothing here should be *invented* — the iOS source is the spec and `:insights` already owns the shared vocabulary. The one place the port must **exceed** iOS is including `ImportedSample` in the cascade (below).

## Runtime State Inventory

> This is a greenfield Android data layer (no prior Android data exists to migrate) but it is a *port* of a live iOS layer, so the relevant "runtime state" is the iOS behavior the Android layer must reproduce — plus one iOS gap to close.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data (iOS → Android identity) | iOS uses app-generated `UUID` PKs, `Calendar.startOfDay` day buckets, raw-string enum columns. No iOS↔Android data migration exists (no cross-platform sync — Out of Scope). | Reproduce identity semantics (UUID string PK, `epochDay`, raw strings) so Phase 23 export/import round-trips are stable. No data migration. |
| Live service config | None — no external service holds Phase-18 state (no backend, no CloudKit on Android). | None. |
| OS-registered state | Android Auto Backup would, by default, copy the Room DB to a Google-account-tied backup. | `dataExtractionRules.xml` must EXCLUDE the Room DB path; assertion test pins it (APER-04). |
| Secrets/env vars | None this phase (crypto passphrase is user-supplied at Phase-23 runtime, never stored). | None. |
| Build artifacts | Room KSP generates `schemas/1.json` — this is a **committed source artifact**, not a throwaway build output. | Wire `room.schemaLocation` KSP arg, commit `android/app/schemas/`, register as androidTest asset source. |
| **iOS cascade gap (finding)** | iOS `deleteDay` does **NOT** purge `ImportedSample` (Repository.swift 282–295), though `ImportedSample.day` is day-scoped — the same orphan class the iOS 04-02 fix closed for `FactorReading`. | Android cascade must **include** `ImportedSample`; the reflection completeness meta-test forces it. Do NOT faithfully copy the iOS omission. |

## Common Pitfalls

### Pitfall 1: Faithfully copying the iOS deleteDay omission of ImportedSample
**What goes wrong:** A literal port of `deleteDay` (Repository.swift 282–295) leaves imported rows for a deleted day — a GDPR Art. 17 orphan, the same bug the iOS 04-02 fix closed for FactorReading.
**Why it happens:** The iOS source is treated as gospel; the omission is invisible because dev test data rarely has imported rows on a deleted day.
**How to avoid:** Include `ImportedSample` in the cascade; drive the cascade from the `DayScoped` marker so the completeness meta-test fails if it is ever dropped.
**Warning signs:** Cascade enumerates entities by hand; `ImportedSample` absent from the delete `@Transaction`; meta-test reflects nothing.

### Pitfall 2: A second, divergent copy of the forbidden-substring lists
**What goes wrong:** `:app` re-declares `causal`/`imperative`/`alarmist` → the two platforms silently enforce different policies on the first iOS update; both suites stay green.
**Why it happens:** It feels natural for `:app` to own its own ClaimsPolicy.
**How to avoid:** `:app` ClaimsPolicy references `com.endoapp.insights.ForbiddenTerms` for the shared three; adds ONLY app-only lists (gendered tokens, false-precision tokens, allowedFraming). Phase 17's `ClaimsPolicyDriftTests` already guards the shared three against the Swift source.
**Warning signs:** `listOf("attack", "alarm", …)` reappearing in `:app`.

### Pitfall 3: exportSchema skipped "because we're greenfield"
**What goes wrong:** No `schemas/1.json` → `MigrationTestHelper` has no baseline forever; auto-migrations impossible; the P2 migration scaffold literally cannot be built later.
**How to avoid:** Day one — `exportSchema=true`, `room.schemaLocation` KSP arg, `schemas/` committed, registered as androidTest asset. Ban `fallbackToDestructiveMigration*` with a structural test. Pin Kotlin+KSP+Room together `[PITFALLS Pitfall 8]`.
**Warning signs:** No `schemas/` dir after the first commit; a Kotlin bump PR that doesn't touch KSP.

### Pitfall 4: nil→0 coercion via Room non-null freedom
**What goes wrong:** Room allows non-null, so `severity INTEGER NOT NULL DEFAULT 0` looks tidy — and destroys absence≠0 across every engine and chart rule.
**How to avoid:** Optional health signals are nullable columns AND nullable DaySnapshot fields end-to-end; spend non-null only on id/day-key/timestamps.
**Warning signs:** any `DEFAULT 0` on a severity/level/flow column; `?: 0` in the projector.

### Pitfall 5: Day bucketing via epoch math inside the bridge
**What goes wrong:** `instant.toEpochMilli() / 86_400_000` reintroduces DST/timezone bugs iOS's `Calendar.startOfDay` avoided.
**How to avoid:** Convert instants to days ONLY via `instant.atZone(zone).toLocalDate().toEpochDay()`; `LocalDate.ofEpochDay()` back. Test in a non-UTC zone (`Europe/Berlin`) `[PITFALLS Pitfall 11]`.

## Code Examples

### ClaimsPolicy — reuse the shared three, add only app-only lists
`[VERIFIED: iOS Content/ClaimsPolicy.swift + :insights ForbiddenTerms.kt]`
```kotlin
// android/app/.../content/ClaimsPolicy.kt
object ClaimsPolicy {
    // REUSE the drift-guarded shared source — do NOT re-declare:
    val causal      get() = com.endoapp.insights.ForbiddenTerms.causal
    val imperative  get() = com.endoapp.insights.ForbiddenTerms.imperative
    val alarmist    get() = com.endoapp.insights.ForbiddenTerms.alarmist

    // APP-ONLY lists (not needed by :insights engine copy):
    val allowedFraming = listOf(
        "informational", "patterns in your own data", "possible association",
        "worth discussing with your clinician", "not medical advice",
        "not a medical device", "self-tracking",
    )
    val forbiddenGenderedTokens = listOf("she", "her", "women", "woman", "lady", "ladies")
    val forbiddenPrecisionTokens = listOf("%", "p =", "p=", "r =", "r=", "p-value", "p value")
}
```
**Exact iOS lists** `[VERIFIED: ClaimsPolicy.swift]` — for the app-only lists above and to confirm the shared three already ported in `:insights`:
- `forbiddenVerbs` (11): diagnose, detect, treat, cure, prevent, clinically validated, clinically proven, medical-grade, trigger, causes, will
- `forbiddenImperatives` (9): take, stop, avoid, start, should, must, use, try, cut
- `forbiddenAlarmist` (14): attack, alarm, alert, emergency, urgent, danger, diagnos, predict, imminent, certain, definitely, episode, will flare, guarantee
- `forbiddenGenderedTokens` (6): she, her, women, woman, lady, ladies
- `allowedFraming` (7): informational, patterns in your own data, possible association, worth discussing with your clinician, not medical advice, not a medical device, self-tracking

> **Reuse-vs-add analysis:** `:insights` `ForbiddenTerms` already ports `causal`(21, a stem superset of `forbiddenVerbs`), `imperative`(9 = `forbiddenImperatives`), `alarmist`(14 = `forbiddenAlarmist`) — drift-guarded against the Swift source by `ClaimsPolicyDriftTests` (Phase 17, note the SEMANTIC-catch nuance in 17-08-SUMMARY: `causal` catches app verbs by stem, and the 3 marketing phrases `clinically validated/proven`, `medical-grade` are an app-side carve-out audited by CopyAudit, never in engine copy). The `:app` layer therefore **adds** `forbiddenGenderedTokens`, `forbiddenPrecisionTokens`, and `allowedFraming` (none of which the engine needs), plus the **registered-catalog scan** below.

### CopyAudit — registered catalog scan (the 40-case iOS harness shape)
`[VERIFIED: Tests/EndoAppTests/CopyAuditTests.swift, 40 @Test]`
The iOS harness scans a **registered surface**: each copy-owning type exposes `auditableStrings: [String]`, and tests assert (a) no string contains a forbidden verb/imperative/alarmist/gendered token, (b) no false-precision token, (c) confidence words carry no digit/`%`, and — the load-bearing rule — (d) **every user-facing string is registered in `auditableStrings`** (an unregistered string silently escapes the scans). Port the *mechanism* now (an `AuditableCopy` interface + a `CopyAudit` JVM test) even though no UI copy exists yet, so Phase 19's first screen copy is audited from birth. The rule from CLAUDE.md/MEMORY (`endo-copy-audit-substring-gate`): new user-facing copy must dodge the substrings AND register in the same change.

### Consent gate
`[VERIFIED: iOS ConsentStore.swift + assertConsented Repository.swift 1122–1124]`
iOS `assertConsented()` is a debug `assert(consentGuard(), …)`. Android has no debug-only assert that survives R8, so the repository **throws** (ARCHITECTURE Pattern 2):
```kotlin
class EndoRepository(private val consent: ConsentStore, private val db: EndoDatabase) {
    private fun checkConsented() {
        if (consent.state.value !is ConsentState.Consented)
            throw IllegalStateException("Repository accessed before consent (ONBD-01).")
    }
    suspend fun daySnapshots(): List<DaySnapshot> {
        checkConsented()
        return SnapshotProjector.project(/* fetch all day-scoped rows */, suppressed = isSuppressed())
    }
}
```
`ConsentState` is 3-state on Android (`Unknown`/`NotConsented`/`Consented`) because DataStore is async — `Unknown` counts as not-consented (fail closed). iOS is 2-state (`notConsented`/`consented(Date)`) because UserDefaults is synchronous. The consent grant date carries over (`Consented(grantedAtMillis)`).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| SwiftData `@Model` + CloudKit private sync | Room 2.8.4 local-only (no sync) | Android port | No CloudKit on Android; UUID identity kept for export round-trips, not sync |
| CloudKit all-optional/`.unique`-banned columns | Room permits non-null + unique indices | Android port | Spend the freedom on id/day-key/timestamps + `ImportedSample.hcRecordId` unique index; keep health values nullable |
| iOS `deleteDay` (5 types, omits ImportedSample) | Android cascade (6 types, includes ImportedSample) + reflection meta-test | This phase | Closes the latent orphan gap; cascade cannot silently fall behind schema |
| kapt annotation processing | KSP 2.3.9 only | Room 2.8/AGP 9 | Faster; Room 3 dropped kapt entirely |

**Deprecated/outdated:** `androidx.security:security-crypto` (deprecated April 2025 — NOT for the export file, that's `javax.crypto` in Phase 23); `org.jetbrains.kotlin.android` plugin (incompatible with AGP 9 built-in Kotlin); `room-ktx` (merged into `room-runtime` as of 2.7).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `epochDay: Long` is preferable to ISO-8601 String for day columns | Field-Level Mapping | LOW — both work; `epochDay` is the recommended default, ISO only at export boundary (Claude's Discretion per CONTEXT) |
| A2 | `payload: Data?` (CachedInsight) maps to Room `ByteArray?` (or String-JSON) | Entity table row 9 | LOW — either is valid; kotlinx-serialization can produce a String column; the blob carries only the gated Insight (never a DaySnapshot) |
| A3 | The reflection meta-test keys off a `DayScoped` marker interface (vs an `epochDay` column-name convention) | Delete-Day Cascade | LOW — both approaches were named in ARCHITECTURE/PITFALLS; the marker is cleaner but either satisfies APER-02 |
| A4 | `:insights` `ForbiddenTerms` exposes `causal`/`imperative`/`alarmist` as `public`/module-visible `List<String>` for `:app` reuse | ClaimsPolicy code example | LOW — confirmed present in ForbiddenTerms.kt (17-08); if visibility blocks reuse, widen it in `:insights` (still single-source) |
| A5 | `CachedInsight` should be delete-all on any day-delete (cache invalidation) | Cascade table row 9 | LOW — ARCHITECTURE.md states this; alternatively the fingerprint short-circuit already forces recompute, making explicit invalidation optional |

**Note:** No assumption here touches compliance, retention, or crypto *values* — the crypto parameters below are `[CITED: STACK.md/PITFALLS.md]` from OWASP-current guidance, not assumed.

## Open Questions

1. **`:app` test framework split (JUnit5 vs JUnit4 for pure-JVM bridge tests)**
   - What we know: `:insights` uses Jupiter; AGP `:app` unit tests are JUnit4-native; Jupiter in an AGP module needs the mannodermaus plugin.
   - What's unclear: whether to place the pure `SnapshotProjector`/`ClaimsPolicy`/`CopyAudit` tests in `:app/test` (JUnit4) or extract a tiny pure-JVM module for Jupiter parity with `:insights`.
   - Recommendation: keep them in `:app/test` on JUnit4 to avoid a plugin dependency (CONTEXT Discretion allows either); they are plain-logic tests where JUnit4 assertions suffice. Reserve Jupiter for `:insights`.

2. **`CachedInsight.payload` column type**
   - What we know: iOS stores a `Codable`-encoded `Data` blob carrying the whole `Insight`.
   - What's unclear: Room `ByteArray?` vs a kotlinx-serialization `String` column.
   - Recommendation: `String` column holding the serialized `Insight` JSON (readable in schema dumps, aligns with the export DTO codec) — decode defensively (corrupt/undecodable → skip, never crash, mirroring iOS `compactMap`).

## Export Format Contract (APER-07 — DATA CONTRACT ONLY, no crypto/SAF this phase)

`[CITED: STACK.md crypto spec + PITFALLS Pitfalls 12–13; DTO shape derived from the 10-entity schema above]`

**Versioned JSON envelope** (kotlinx-serialization `@Serializable` DTOs):
```kotlin
@Serializable data class ExportEnvelope(
    val schemaVersion: Int,                 // top-level format version (start at 1)
    val exportedAt: String,                 // ISO-8601 instant (UTC)
    val dayEntries: List<DayEntryDto>,
    val symptomReadings: List<SymptomReadingDto>,
    val painPoints: List<PainPointDto>,
    val bleedingEvents: List<BleedingEventDto>,
    val factorReadings: List<FactorReadingDto>,
    val medications: List<MedicationDto>,
    val treatmentStates: List<TreatmentStateDto>,
    val importedSamples: List<ImportedSampleDto>,
    val dismissedInsights: List<DismissedInsightDto>,
    // CachedInsight is derived cache — EXCLUDED from export (recomputable; keeps the file minimal & honest)
)
```
**DTO rules:** per-entity arrays keyed by the shared enum **raw** values (`typeRaw`, `regionRaw`, `flowRaw`, `kindRaw`, `domainRaw`, `modeRaw` — the same strings stored in Room and used by `:insights`); **UUID identity** as the `id` String (idempotent upsert-by-UUID on import, Phase 23); **all dates ISO-8601** (day fields as `yyyy-MM-dd` from `LocalDate.ofEpochDay`; timestamps as full ISO instants). Optional health values stay nullable in the DTO (absence≠0 survives the round trip). `CachedInsight` is excluded (derived cache).

**Crypto header spec** (contract only — Phase 23 implements) `[CITED: STACK.md + PITFALLS Pitfall 12]`:
```kotlin
@Serializable data class CryptoHeader(
    val magic: String,        // fixed magic bytes/string identifying an endo export (ciphertext must NOT be greppable JSON)
    val formatVersion: Int,   // header/crypto version, independent of ExportEnvelope.schemaVersion
    val kdf: KdfParams,       // params READ FROM THE FILE on import — never hard-coded constants
    val ivBase64: String,     // 12-byte AES-GCM IV, fresh per export
)
@Serializable data class KdfParams(
    val alg: String = "PBKDF2WithHmacSHA256",  // available since API 26 (minSdk 26)
    val iterations: Int = 600_000,             // OWASP-current PBKDF2-SHA256 guidance; upgradeable via header
    val saltBase64: String,                    // 16-byte salt, fresh per export from SecureRandom
    val keyBits: Int = 256,
)
```
File layout: **cleartext** `CryptoHeader` + **ciphertext** of the `ExportEnvelope` JSON (`AES/GCM/NoPadding`, 128-bit tag). Fresh random salt AND IV per export (fresh salt→fresh key→nonce-reuse risk collapses). Header carries KDF params so iterations can rise without breaking old files. This phase ships the DTOs + a golden-**shape** test (fields present, dates ISO, ids UUID); the golden **encrypted fixture** + read-back-decrypt verification land in Phase 23.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | Gradle build/test | ✓ | 21 (host, per CONTEXT) | — |
| Android SDK (compileSdk 36) | `:app` build | Assumed via Android Studio/CI | 36 | — |
| Room/KSP/DataStore/kotlinx-serialization | this phase | Maven-resolvable | pinned above | — |
| Android emulator (API 26–34) | androidTest (migration + cascade on real SQLite) | CI: `reactivecircus/android-emulator-runner` (main-only per ARCHITECTURE CI) | api-level 34 | JVM tests cover projector/copyaudit/export without emulator |

**Missing dependencies with no fallback:** none identified — JVM tests (SnapshotProjector, ClaimsPolicy, CopyAudit, export shape) need no emulator; only the migration scaffold + cascade instrumentation need a device/emulator, already the ARCHITECTURE CI story.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 6.1.1 (`:insights` pattern) for pure-JVM; JUnit 4.13.2 for `:app` unit/instrumentation; `androidx.test.ext:junit` 1.3.0 + `room-testing` 2.8.4 for androidTest |
| Config file | `android/app/build.gradle.kts` (test deps + `room.schemaLocation` KSP arg); `android/gradle/libs.versions.toml` |
| Quick run command | `cd android && ./gradlew :app:testDebugUnitTest` (JVM — projector, copyaudit, claims, export shape) |
| Full suite command | `cd android && ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :insights:test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| APER-01 | 10 entities persist; health read/write throws pre-consent, succeeds post-consent | instrumentation + unit | `./gradlew :app:connectedDebugAndroidTest --tests *RepositoryConsentTest` | ❌ Wave 0 |
| APER-02 | delete-day purges all 6 day-scoped types in one txn; Medication/TreatmentState survive; meta-test fails on uncascaded day-scoped entity | instrumentation + unit | `./gradlew :app:connectedDebugAndroidTest --tests *DeleteDayCascadeTest` + `:app:testDebugUnitTest --tests *CascadeCompletenessTest` | ❌ Wave 0 |
| APER-03 | `schemas/1.json` committed; `MigrationTestHelper` opens it; destructive-fallback ban | instrumentation + unit (structural scan) | `./gradlew :app:connectedDebugAndroidTest --tests *MigrationTest` + `:app:testDebugUnitTest --tests *DestructiveMigrationBanTest` | ❌ Wave 0 |
| APER-04 | `data_extraction_rules.xml` excludes the Room DB path | unit (XML parse) | `./gradlew :app:testDebugUnitTest --tests *BackupRulesTest` | ❌ Wave 0 |
| APER-05 | SnapshotProjector 24-case parity; free text never crosses; nil≠0; manual-wins merge | unit (JVM, hand-built rows) | `./gradlew :app:testDebugUnitTest --tests *SnapshotProjectorTest` | ❌ Wave 0 |
| APER-06 | ClaimsPolicy reuses `:insights` lists; CopyAudit registered-catalog scan (no forbidden substrings, all strings registered) | unit (JVM) | `./gradlew :app:testDebugUnitTest --tests *CopyAuditTest` | ❌ Wave 0 |
| APER-07 | export envelope + crypto-header DTOs; golden-shape test (fields present, ISO dates, UUID ids); UUID identity | unit (JVM) | `./gradlew :app:testDebugUnitTest --tests *ExportFormatTest` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `cd android && ./gradlew :app:testDebugUnitTest` (fast JVM — projector/copyaudit/claims/export)
- **Per wave merge:** `cd android && ./gradlew :app:testDebugUnitTest :insights:test` + (device/emulator) `:app:connectedDebugAndroidTest`
- **Phase gate:** full suite green (JVM + instrumentation) before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/kotlin/.../SnapshotProjectorTest.kt` — the 24-case parity suite (mirror `DaySnapshotMappingTests`) — covers APER-05
- [ ] `app/src/test/kotlin/.../CascadeCompletenessTest.kt` — reflection meta-test over day-scoped entities — covers APER-02
- [ ] `app/src/androidTest/kotlin/.../DeleteDayCascadeTest.kt` — insert one row of all 10 types, assert exact survival set on real SQLite — covers APER-02
- [ ] `app/src/androidTest/kotlin/.../MigrationTest.kt` + committed `app/schemas/1.json` — covers APER-03
- [ ] `app/src/test/kotlin/.../DestructiveMigrationBanTest.kt` — structural source scan — covers APER-03
- [ ] `app/src/test/kotlin/.../BackupRulesTest.kt` — parse `data_extraction_rules.xml`, assert DB excluded — covers APER-04
- [ ] `app/src/test/kotlin/.../CopyAuditTest.kt` + `AuditableCopy` interface — covers APER-06
- [ ] `app/src/test/kotlin/.../ExportFormatTest.kt` — golden-shape assertions — covers APER-07
- [ ] `app/src/androidTest/kotlin/.../RepositoryConsentTest.kt` — throws pre-consent, succeeds post — covers APER-01
- [ ] Framework install: add `room-testing`, `androidx.test.ext:junit`, `datastore-preferences`, `kotlinx-serialization-json` to the catalog + `ksp(room-compiler)` + `room.schemaLocation` KSP arg

## Security Domain

> `security_enforcement: true`, ASVS level 1 (per `.planning/config.json`). This is a health-data persistence layer handling GDPR Art. 9 special-category data — security is load-bearing.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No accounts/login by design (no-backend) |
| V3 Session Management | no | No sessions; consent is a local gate, not a session |
| V4 Access Control | yes | `EndoRepository.checkConsented()` gates every health access (fail-closed throw); the consent flag lives outside the health DB |
| V5 Input Validation | yes | Defensive enum decode (`fromRaw`/`fromRawOrNull` — unknown/null → OTHER-sink or null, never throws); note length bounded (iOS `maxNoteLength = 280`); UUID parse guarded (skip non-UUID valueRaw) |
| V6 Cryptography | yes (contract only) | Export crypto header spec: `PBKDF2WithHmacSHA256` ≥600k iters, `AES/GCM/NoPadding`, fresh 16-byte salt + 12-byte IV per export — NEVER hand-rolled, `javax.crypto` (implementation Phase 23) |
| V8 Data Protection | yes | Room DB excluded from Auto Backup (`dataExtractionRules`, APER-04); free text (`note`) never crosses the projection bridge (`hasNote` only); `CachedInsight`/`DismissedInsight` carry only non-identifying category `signalKey`, never prose |

### Known Threat Patterns for Room + local health data

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Health DB silently copied to Google-account Auto Backup | Information disclosure | `dataExtractionRules.xml` excludes the DB path; assertion test (APER-04) |
| Free-text health prose leaking into the analysis/cache layer | Information disclosure | Projector carries `hasNote: Bool` only; note-bearing `.food` row NOT carried; cache stores only gated `Insight` category labels `[VERIFIED: makeDaySnapshot/projectFactors]` |
| Pre-consent health read/write | Elevation of privilege / policy violation | `checkConsented()` throws fail-closed (Unknown = not consented) |
| Corrupt/future stored raw crashing the app | DoS | Defensive decode everywhere — unknown raw sinks to OTHER/null, undecodable cache blob is skipped (never fatal) |
| Orphaned day-scoped rows after delete-day (GDPR Art. 17) | Repudiation / policy violation | One `@Transaction` cascade incl. `ImportedSample` + reflection completeness meta-test (APER-02) |
| Destructive migration wiping health history | Data loss | `fallbackToDestructiveMigration*` structurally banned by test (APER-03) |
| Weak/nonce-reused export crypto (contract sets the guardrails) | Tampering / information disclosure | Header spec mandates fresh salt+IV per export, ≥600k PBKDF2 iters, params read from file (Phase 23 enforces) |

## Sources

### Primary (HIGH confidence)
- iOS SwiftData layer read directly this session — `Sources/EndoApp/Persistence/Models/{DayEntry,SymptomReading,PainPoint,BleedingEvent,FactorReading,Medication,TreatmentState,ImportedSample,CachedInsight,DismissedInsight}.swift`; `Repository.swift` (`daySnapshots()` 533–591, `makeDaySnapshot` 864–939, `projectFactors` 945–1000, `ImportedSignals` 793–859, `deleteDay` 282–295, `assertConsented` 1122–1124); `Domain/FactorKind.swift`; `Health/HealthDomain.swift`; `Consent/ConsentState.swift` + `ConsentStore.swift`; `Content/ClaimsPolicy.swift`; `Tests/EndoAppTests/{DaySnapshotMappingTests(24),CopyAuditTests(40)}.swift`; `EndoInsights/Sources/EndoInsights/DaySnapshot.swift`
- Ported `:insights` sources — `android/insights/src/main/kotlin/com/endoapp/insights/{SnapshotEnums.kt,ForbiddenTerms.kt}` (enum raws + shared substring lists, Phase 17)
- `.planning/phases/17-insights-engine-port/17-08-SUMMARY.md` (InsightsEngine public signature, ClaimsPolicy drift nuance)

### Secondary (MEDIUM confidence)
- `.planning/research/{ARCHITECTURE.md,STACK.md,PITFALLS.md}` (locked layout, version pins verified against developer.android.com 2026-07-08, pitfall catalog)
- `.planning/phases/18-room-persistence/18-CONTEXT.md` (locked decisions)

### Tertiary (LOW confidence)
- none — this phase's claims are all sourced from first-party iOS code or the verified research docs.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every coordinate pinned + verified in STACK.md against official docs
- Field-level entity mapping: HIGH — read line-by-line from the 10 iOS model files this session
- Projection/cascade/consent: HIGH — read from Repository.swift this session; the ImportedSample-cascade finding is a direct source comparison
- ClaimsPolicy/CopyAudit reuse: HIGH — both the iOS lists and the ported `:insights` ForbiddenTerms were read this session
- Export format contract: MEDIUM — DTO shape derived from the verified schema; crypto params cited from STACK.md/OWASP (not implemented this phase)

**Research date:** 2026-07-09
**Valid until:** 2026-08-08 (stable — port of frozen iOS source; re-check Room/KSP/DataStore point releases at plan time)
