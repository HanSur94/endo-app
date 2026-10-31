# Phase 21: Health Connect Import - Research

**Researched:** 2026-07-09
**Domain:** Android read-only Health Connect import (androidx.health.connect:connect-client 1.1.0) porting the iOS HealthKit import path
**Confidence:** HIGH on API surface / manifest / permissions / availability / changes-token (verified against developer.android.com + android/snippets); MEDIUM on the exact record-field integer constants (verify against the SDK at implementation time — flagged in the Assumptions Log)

## Summary

This phase is the **Health Connect client that produces `ImportedSample` rows** for the merge machinery already built in Phase 18. Everything downstream — `ImportedSignals` (sleep SUM→coarse band, workout longest-type), the manual-wins/nil-honest merge in `SnapshotProjector`, the day-scoped delete cascade, the consent-gated `EndoRepository.upsertImported` — exists and is test-pinned. Phase 21 adds: (a) the `androidx.health.connect:connect-client` dependency, (b) the three-state availability gate, (c) BOTH permission-rationale manifest declarations, (d) the four READ permissions + the `READ_HEALTH_DATA_HISTORY` decision, (e) an injectable `HealthReader` seam with a real `HealthConnectReader` and a JVM fake, (f) the read→map→upsert→changes-token incremental sync, and (g) a thin Bloom connect/import UI.

The signature invariant is **read-only, auditable in the manifest**: only `android.permission.health.READ_*`, zero `WRITE_*`, no `READ_HEALTH_DATA_IN_BACKGROUND`, no `INTERNET`. The two Play-critical items are the **dual rationale declaration** (miss one and a whole OS generation gets a dead permission flow) and the **30-day history decision** (silent truncation vs. an honest window). The iOS port source (`HealthDataReading`/`FixtureHealthDataReading`/`HealthImportService`/`HealthAnchorStore`/`HealthSampleMapping`/`HealthConnectCopy`) is the parity blueprint; the Android equivalents are HC-idiomatic (changes-token instead of `HKQueryAnchor`, `metadata.id` instead of `HKSample.uuid`, integer record fields instead of `HK*` enums).

**Primary recommendation:** Build an injectable `HealthReader` interface (real `HealthConnectReader` + `FakeHealthReader`), request the four READ permissions **plus** `READ_HEALTH_DATA_HISTORY` with an honest window label, gate every entry point on `getSdkStatus()` per session, sync incrementally with `getChangesToken`/`getChanges` keyed on `metadata.id`, upsert `ImportedSample` rows with an id derived deterministically from `hcRecordId` for true idempotency, and propagate `DeletionChange` to row deletes.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Health Connect client (read-only)**
- Dependency: `androidx.health.connect:connect-client` 1.1.0 (add to the catalog — first-party androidx, catalog audit stays green). Read the four mapped record types ONLY: menstruation flow (`MenstruationFlowRecord`/`MenstruationPeriodRecord`), sleep (`SleepSessionRecord`), exercise (`ExerciseSessionRecord`), nutrition energy (`NutritionRecord` dietary energy). Map to the iOS HealthDomain → `ImportedSample` fields (sleep = SUM of samples → coarse band; workout = longest-type per the Phase-18 `ImportedSignals` rules already ported; menstruation → flow; nutrition → ateThatDay). NO write permissions of ANY kind.
- The read-only invariant is auditable: the manifest declares ONLY `android.permission.health.READ_*` for the four types and ZERO `WRITE_*`. Extend the Phase-19/20 ManifestPermissionsTest to assert zero health-WRITE permissions and that only the four READ permissions are present.

**Three-state availability gating**
- `HealthConnectClient.getSdkStatus()` → three states: SDK_AVAILABLE (Android 14+ built-in module, or 9–13 with the installable Health Connect app present), SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED (installable, prompt update/install), SDK_UNAVAILABLE (impossible — e.g. API 26–27). Render an honest state for each; MANUAL LOGGING IS NEVER BLOCKED in any of them. Do NOT assume availability — gate per session.

**Dual permission-rationale declarations (hard Play requirement)**
- Declare BOTH rationale surfaces: (a) the Android 13- rationale Activity handling `ACTION_SHOW_PERMISSIONS_RATIONALE`, and (b) the Android 14+ activity-alias for `VIEW_PERMISSION_USAGE`/HEALTH_PERMISSIONS. A manifest test asserts BOTH are present. Request permissions via the Health Connect permission contract (`PermissionController.createRequestPermissionResultContract`), never cache grants — call `getGrantedPermissions()` per session.

**30-day history window (deliberate decision — DUE THIS PHASE)**
- Default recommendation: request `READ_HEALTH_DATA_HISTORY` (the app's value is longitudinal patterns) AND label the window honestly. The planner/executor confirms current semantics against docs at implementation time.

**Import service + provenance (manual-wins / nil-honest)**
- Port the iOS import path: read HC records → map to `ImportedSample` provenance rows (unique-indexed by the HC record id) → merge with MANUAL-WINS + NIL-HONEST. Idempotent re-import (dedupe by hcRecordId). Incremental sync via a changes token if available; otherwise a bounded re-read. Deletions in HC propagate. All through the consent-gated `EndoRepository`.

**UX (connect + import states)**
- A "Connect Health data" entry (Settings and/or Log) → a rationale-BEFORE-permission screen → the HC permission request → an import screen showing progress + honest window label + "from Health Connect" provenance labels. Graceful denial: manual logging unaffected, explained gently (no nag). Availability-unavailable states explained honestly.

**Copy discipline**
- Every new user-facing string registered in CopyAudit in the same change; dodge ClaimsPolicy forbidden SUBSTRINGS (should/use/her/start/treat/stop/take — watch "start"/"use" especially); neutral/non-diagnostic; "informational, on-device, read-only" framing.

### Claude's Discretion
- Exact package layout under `com.endoapp.app.health`, whether the import runs in a coroutine/WorkManager one-shot vs foreground, the connect-screen decomposition, and the incremental-sync token strategy — guided by the iOS HealthImportService + FEATURES.md/PITFALLS.md. Keep it read-only and consent-gated.

### Deferred Ideas (OUT OF SCOPE)
- Insights/charts over the imported data → Phase 22.
- Encrypted export includes imported rows via the Phase-18 format contract → Phase 23.
- Any real-device HC provider behavior (actual permission dialog, real provider data) → device-gated manual verification.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AHC-01 | Gate on the three HC availability states (built-in 14+ / installable 9–13 / impossible on 8.x) with honest empty/unavailable states; manual logging never blocked | `getSdkStatus()` three-state gate (Standard Stack + Architecture Patterns "Availability Gate"); UX section renders all three; per-session re-check |
| AHC-02 | Read-only access with BOTH rationale surfaces declared; manifest test proves zero write permissions and both declarations | Exact dual-manifest snippets (Architecture Patterns "Dual Rationale"); ManifestPermissionsTest extension (Validation Architecture) |
| AHC-03 | Import menstruation/sleep/exercise/nutrition with provenance rows, manual-wins/nil-honest merge, idempotent re-import | Record→`ImportedSample` field map (Architecture Patterns "Field Mapping"); deterministic-id-from-`hcRecordId` idempotency; changes-token incremental sync + deletion propagation |
| AHC-04 | 30-day history handled deliberately — `READ_HEALTH_DATA_HISTORY` requested with rationale OR window honestly labeled | 30-day window semantics (State of the Art); recommendation: request history + label (Architecture Patterns "History Window"); honest window-label copy (UX) |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Availability detection (`getSdkStatus`) | Client / Device (HC provider IPC) | UI (renders state) | HC is a local provider (built-in module 14+ / installable app 9–13); status is a device fact, gated per session |
| Permission request/grant read | Client / Device (HC permission UI) | App (contract launcher) | Grants live in the HC provider; app requests via the result contract and reads via `getGrantedPermissions()` — never cached |
| Record read + differential sync | App (`HealthConnectReader` over `HealthConnectClient`) | Device (HC datastore) | HC vends records over IPC; app maps them to value rows inside the reader boundary |
| Record → `ImportedSample` mapping | App (`HealthConnectReader` / mapper) | — | Bespoke value-in/value-out mapping (the iOS `HealthSampleMapping` analog); defensive decode, never fabricates |
| Provenance + idempotency | App (Room `ImportedSample`, `hcRecordId` unique index) | Persistence | `metadata.id`→`hcRecordId`, `metadata.dataOrigin`→provenance; deterministic id → idempotent upsert |
| Merge into day model | Persistence (`SnapshotProjector` + `ImportedSignals`, Phase 18) | — | Already built + test-pinned; Phase 21 only produces the rows it consumes |
| Sync-cursor persistence | App (DataStore, outside Room health DB) | — | Changes-token is per-device runtime state, not health data — mirror iOS `HealthAnchorStore` living in UserDefaults |
| Connect/import UI | UI (Compose, Bloom) | App (repository + reader) | Thin surface; no separate UI-SPEC — see Health Connect UX section |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.health.connect:connect-client` | 1.1.0 | Read-only import of the 4 record types + changes-token sync | First & current stable (Oct 2025); the only sanctioned Health Connect broker; all four record types present. `[VERIFIED: STACK.md + developer.android.com/jetpack/androidx/releases/health-connect]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.datastore:datastore-preferences` | 1.2.0 (already in catalog) | Persist the per-domain changes-token OUTSIDE the Room health DB | Mirror iOS `HealthAnchorStore` (cursor is device runtime state, not health data). `[VERIFIED: catalog]` |
| `androidx.activity:activity-compose` | current (already in catalog) | `rememberLauncherForActivityResult` for the HC permission contract | Permission request flow. `[VERIFIED: catalog]` |
| Room `SymptomDao` + `EndoRepository.upsertImported` | 2.8.4 (Phase 18) | Persist `ImportedSample` rows | Already built + consent-gated. `[VERIFIED: codebase]` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `getChangesToken`/`getChanges` differential sync | Full ranged `readRecords` on every foreground | Full re-read burns HC rate-limit quota for the heaviest users and re-does work; changes-token is the sanctioned incremental path. Keep a bounded ranged read only as the token-expiry fallback. `[CITED: developer.android.com/health-and-fitness/health-connect/sync-data]` |
| `READ_HEALTH_DATA_HISTORY` (request it) | Accept the 30-day window, label honestly | History adds one Play-declaration justification; without it, a multi-year cycle history silently truncates to ~30 days — the wrong outcome for a longitudinal tracker. Recommendation: request it AND label. `[VERIFIED: developer.android.com/health-and-fitness/health-connect/read-data]` |
| DataStore for the changes-token | Room row | Token is per-device runtime cursor, not user health data; keeping it out of the synced/exported health DB mirrors iOS and avoids the export contract carrying a cursor. `[CITED: iOS HealthAnchorStore.swift]` |

**Installation:**
```toml
# android/gradle/libs.versions.toml — ADD (Phase 21). connect-client is NOT yet in the catalog.
[versions]
healthConnect = "1.1.0"

[libraries]
health-connect-client = { group = "androidx.health.connect", name = "connect-client", version.ref = "healthConnect" }
```
```kotlin
// android/app/build.gradle.kts
dependencies {
    implementation(libs.health.connect.client)
}
```

**Version verification:** `androidx.health.connect:connect-client:1.1.0` is the first & current **stable** release (Oct 2025), confirmed in STACK.md and against the AndroidX Health Connect releases page. `1.2.0-alpha04` exists but only adds matchmaking APIs this app does not need. `[VERIFIED: developer.android.com/jetpack/androidx/releases/health-connect]`

## Package Legitimacy Audit

> The npm/pypi/crates legitimacy seam does not apply — `connect-client` is a first-party **AndroidX Maven** artifact. Verified against the official AndroidX release page + STACK.md instead.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `androidx.health.connect:connect-client` 1.1.0 | Google Maven (AndroidX) | Stable since Oct 2025 | First-party Google | android.googlesource.com/platform/frameworks/support | OK | Approved (first-party) |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
                         ┌─────────────────────────────────────────────┐
   Settings / Log entry  │  "Connect Health data" (Bloom entry point)   │
   ("Connect Health      └───────────────────┬─────────────────────────┘
    data")                                    │  onClick
                                              ▼
                              ┌───────────────────────────────┐
                              │  getSdkStatus(context)  (per   │
                              │  session, never cached)        │
                              └───────┬───────────┬───────────┬┘
                     SDK_AVAILABLE    │           │           │  SDK_UNAVAILABLE
                                      │  PROVIDER_UPDATE_REQUIRED
                                      ▼           ▼           ▼
                        ┌─────────────────┐ ┌──────────┐ ┌──────────────────┐
                        │ Rationale-before│ │ Update/  │ │ Honest "not on   │
                        │ -permission     │ │ install  │ │ this device"     │
                        │ screen (Bloom)  │ │ prompt → │ │ state; manual    │
                        └────────┬────────┘ │ Play deep│ │ logging = hero   │
                                 │ Continue │ link     │ └──────────────────┘
                                 ▼          └──────────┘
                   ┌────────────────────────────────┐
                   │ PermissionController contract   │  (system HC permission UI)
                   │ .createRequestPermission…       │
                   └────────────┬───────────────────┘
                                │ result: granted set (read per session)
                                ▼
       ┌──────────────────────────────────────────────────────────────────┐
       │  HealthReader (interface)  ── real: HealthConnectReader ──────────┐│
       │                            ── fake: FakeHealthReader (JVM tests)  ││
       │  read(domain, token?) →                                          ││
       │    HealthConnectClient.readRecords / getChanges                  ││
       │      ├─ UpsertionChange → map record → ImportedSample row        ││
       │      └─ DeletionChange  → hcRecordId to delete                   ││
       └───────────────────────────┬──────────────────────────────────────┘
                                    │ values + deletions + nextChangesToken
                                    ▼
       ┌──────────────────────────────────────────────────────────────────┐
       │  HealthImportCoordinator (consent-gated; Dispatchers.Default)     │
       │   persist upserts + deletions via EndoRepository (checkConsented) │
       │   then advance + persist the changes-token (DataStore)            │
       └───────────────────────────┬──────────────────────────────────────┘
                                    ▼
       ┌──────────────────────────────────────────────────────────────────┐
       │  Room ImportedSample rows (hcRecordId unique index, dataOrigin)   │
       │        │ (Phase 18, already built)                                │
       │        ▼                                                          │
       │  ImportedSignals.from(rows) → SnapshotProjector (manual-wins /    │
       │  nil-honest merge) → DaySnapshot → :insights (Phase 22 consumes)  │
       └──────────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure
```
android/app/src/main/kotlin/com/endoapp/app/health/
├── HealthAvailability.kt        # 3-state enum wrapping getSdkStatus results
├── HealthReader.kt              # injectable seam (interface) — the iOS HealthDataReading analog
├── HealthConnectReader.kt       # real impl over HealthConnectClient (maps records → ImportedSample)
├── HealthRecordMapping.kt       # pure value-in/value-out mapping (the iOS HealthSampleMapping analog)
├── HealthConnectPermissions.kt  # the 4 READ perm strings + READ_HEALTH_DATA_HISTORY, as a Set
├── ChangesTokenStore.kt         # per-domain token in DataStore (iOS HealthAnchorStore analog)
├── HealthImportCoordinator.kt   # consent-gated read→persist→advance-token (iOS HealthImportService analog)
├── HealthConnectCopy.kt         # auditable copy surface (iOS HealthConnectCopy analog)
└── ui/
    ├── HealthConnectSection.kt  # Settings/Log entry + availability rendering
    ├── HealthRationaleScreen.kt # rationale-before-permission
    ├── HealthImportScreen.kt    # progress / window label / empty / denial states
    └── HealthSourceBadge.kt     # "from Health Connect" provenance chip
android/app/src/main/kotlin/com/endoapp/app/data/entities/ImportedSample.kt  # EXISTS (Phase 18)
android/app/src/test/kotlin/com/endoapp/app/health/                          # JVM tests via FakeHealthReader
```
Rationale Activity (`PermissionsRationaleActivity`) hosts `HealthRationaleScreen`; both manifest declarations target it.

### Pattern 1: Availability Gate (three-state, per session)
**What:** Never call `getOrCreate()` blindly — resolve `getSdkStatus()` first, every foreground.
**When to use:** Every HC entry point.
```kotlin
// Source: developer.android.com/health-and-fitness/health-connect/get-started [VERIFIED]
enum class HealthAvailability { AVAILABLE, PROVIDER_UPDATE_REQUIRED, UNAVAILABLE }

fun availability(context: Context): HealthAvailability =
    when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            HealthAvailability.PROVIDER_UPDATE_REQUIRED   // installable app absent or stale (API 28–33)
        else -> HealthAvailability.UNAVAILABLE            // SDK_UNAVAILABLE — impossible (API 26–27)
    }
// Only construct the client when AVAILABLE:
val client = if (availability(ctx) == AVAILABLE) HealthConnectClient.getOrCreate(ctx) else null
```
> **Constant-name note:** the current constant is `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`. PITFALLS.md (2026-07-08) referenced an older `SDK_STATUS_UPDATE_REQUIRED` name — use the verified current constant. `[VERIFIED: developer.android.com get-started]`

### Pattern 2: Dual Rationale Declaration (the item that silently kills an OS generation)
**What:** Declare BOTH the Android 13- rationale Activity AND the Android 14+ activity-alias, pointing at the same rationale screen.
**When to use:** Mandatory — miss either and that OS generation's HC permission dialog never appears (empty grant set), or Play rejects.
```xml
<!-- android/app/src/main/AndroidManifest.xml — Source: developer.android.com/health-and-fitness/health-connect/get-started [VERIFIED] -->

<!-- (a) Android 13 and lower (installable HC app) -->
<activity
    android:name=".health.ui.PermissionsRationaleActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
    </intent-filter>
</activity>

<!-- (b) Android 14+ (platform Health Connect module) -->
<activity-alias
    android:name="ViewPermissionUsageActivity"
    android:exported="true"
    android:targetActivity=".health.ui.PermissionsRationaleActivity"
    android:permission="android.permission.START_VIEW_PERMISSION_USAGE">
    <intent-filter>
        <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
        <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
    </intent-filter>
</activity-alias>
```
> The rationale screen must show the SAME privacy-policy content Play reviewers open. `[VERIFIED: PITFALLS.md Pitfall 1 + developer.android.com]`

### Pattern 3: Permissions — declare, request via contract, read per session
```kotlin
// Source: developer.android.com HealthPermission + get-started [VERIFIED strings]
object HealthConnectPermissions {
    val READ: Set<String> = setOf(
        "android.permission.health.READ_MENSTRUATION",  // covers MenstruationFlowRecord + MenstruationPeriodRecord
        "android.permission.health.READ_SLEEP",         // SleepSessionRecord
        "android.permission.health.READ_EXERCISE",      // ExerciseSessionRecord
        "android.permission.health.READ_NUTRITION",     // NutritionRecord
    )
    const val HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY" // AHC-04 (recommended)
    val REQUESTED: Set<String> = READ + HISTORY
    // Equivalent typed form: HealthPermission.getReadPermission(SleepSessionRecord::class), etc.
}
```
```xml
<!-- manifest <uses-permission> — ALL READ, ZERO WRITE, no background -->
<uses-permission android:name="android.permission.health.READ_MENSTRUATION" />
<uses-permission android:name="android.permission.health.READ_SLEEP" />
<uses-permission android:name="android.permission.health.READ_EXERCISE" />
<uses-permission android:name="android.permission.health.READ_NUTRITION" />
<uses-permission android:name="android.permission.health.READ_HEALTH_DATA_HISTORY" />
```
```kotlin
val launcher = rememberLauncherForActivityResult(
    PermissionController.createRequestPermissionResultContract()
) { granted -> /* granted: Set<String> — proceed to import regardless (read status may be partial) */ }
// NEVER cache — read per session before every import:
val granted = client.permissionController.getGrantedPermissions()
```
> A `MenstruationFlowRecord` (per-day flow intensity) and a `MenstruationPeriodRecord` (the period span) are BOTH gated by the single `READ_MENSTRUATION` permission. `[VERIFIED: ROOK KB + developer.android.com HealthPermission]`

### Pattern 4: Incremental sync via changes-token (mirror iOS anchored queries)
```kotlin
// Source: developer.android.com/health-and-fitness/health-connect/sync-data + android/snippets [VERIFIED]
// Initial: one ranged readRecords per domain to seed; persist a fresh token per domain.
val token = client.getChangesToken(
    ChangesTokenRequest(recordTypes = setOf(SleepSessionRecord::class /* … per domain */))
)
// Subsequent foregrounds:
suspend fun sync(startToken: String): String {
    var next = startToken
    do {
        val response = client.getChanges(next)               // throws ChangesTokenExpiredException if stale
        response.changes.forEach { change ->
            when (change) {
                is UpsertionChange ->
                    if (change.record.metadata.dataOrigin.packageName != context.packageName)
                        upsertRow(mapToImportedSample(change.record))     // ignore our own writes (we write none)
                is DeletionChange ->
                    deleteRowByHcRecordId(change.recordId)                // propagate HC deletion
            }
        }
        next = response.nextChangesToken
    } while (response.hasMore)
    return next   // persist for the next sync
}
// Token expiry (≈30 days): catch ChangesTokenExpiredException → fall back to a bounded ranged read,
// then obtain a fresh token. Persist token per domain in DataStore (device runtime state, not health data).
```

### Pattern 5: Idempotent upsert + deletion propagation
**What:** Derive the `ImportedSample.id` deterministically from `hcRecordId` so re-import produces byte-identical rows (REPLACE collapses both the primary-key `id` and the `hcRecordId` unique index), and add a delete-by-hcRecordId DAO for `DeletionChange`.
```kotlin
// Deterministic id → true idempotency (re-import changes nothing). UUID v5-style stable hash of the HC id.
fun stableId(hcRecordId: String): String =
    UUID.nameUUIDFromBytes("hc:$hcRecordId".toByteArray()).toString()

// Add to SymptomDao (Phase 21):
@Query("DELETE FROM ImportedSample WHERE hcRecordId = :hcRecordId")
suspend fun deleteImportedByHcRecordId(hcRecordId: String)
```
> `SymptomDao.upsertImported` already uses `OnConflictStrategy.REPLACE`, which resolves the `hcRecordId` unique-index conflict — deterministic ids make the primary-key path collapse too. `[VERIFIED: codebase SymptomDao.kt + ImportedSample.kt]`

### Pattern 6: Injectable HealthReader seam (JVM-testable without an emulator)
```kotlin
// The iOS HealthDataReading analog. NO androidx.health.* type escapes — the impl maps records to
// ImportedSample value rows INSIDE the boundary, so import/merge/idempotency/denial are JVM-testable.
interface HealthReader {
    suspend fun grantedPermissions(): Set<String>
    /** Read new/changed rows for [domain] since [token] (null = seed ranged read). Empty = contribute nothing. */
    suspend fun read(domain: HealthDomain, token: String?): ReadResult
}
data class ReadResult(
    val upserts: List<ImportedSample>,      // already mapped — no HC type crosses
    val deletedHcRecordIds: List<String>,
    val nextToken: String?,
)
class HealthConnectReader(private val client: HealthConnectClient, private val zone: () -> ZoneId) : HealthReader
class FakeHealthReader(private val script: /* scripted upserts/deletions/token */) : HealthReader  // JVM tests
```
> Mirrors `FixtureHealthDataReading`: `.empty` models denied-OR-granted-but-empty identically (read status honesty), and a two-read script exercises re-import dedupe + deletion.

### Field Mapping — HC record → `ImportedSample`

Common to every record: `metadata.id` → `hcRecordId` (unique dedupe key) and `id = stableId(metadata.id)`; `metadata.dataOrigin.packageName` → provenance ("from Health Connect", and the specific app if surfaced); `epochDay` computed by the **shared bucketing rule** (below).

| Domain (`domainRaw`) | HC record | Read fields | `ImportedSample` columns set | Notes |
|----------------------|-----------|-------------|------------------------------|-------|
| `cycle` | `MenstruationFlowRecord` | `time: Instant`, `zoneOffset?`, `flow: Int` | `flowRaw` = mapped `FlowIntensity.raw` | `FLOW_LIGHT(1)→"light"`, `FLOW_MEDIUM(2)→"medium"`, `FLOW_HEAVY(3)→"heavy"`, `FLOW_UNKNOWN(0)→"spotting"` (a present record = flow logged, intensity unknown → lightest bucket, mirroring iOS `.unspecified→.spotting`). No record = no flow (never fabricate). `[ASSUMED int constants — verify at impl]` |
| `cycle` | `MenstruationPeriodRecord` | `startTime`, `endTime` | (period span only — NO flow value) | Delimits the period; carries no intensity. Use for period-day presence only if needed; flow intensity comes from `MenstruationFlowRecord`. Optional this phase. |
| `sleep` | `SleepSessionRecord` | `startTime`, `endTime`, `startZoneOffset?`, `stages: List<Stage>` (each `startTime`,`endTime`,`stage: Int`) | `sleepHours` = Σ asleep-stage durations (hours); `sleepQualityRaw` left null (band derived in `ImportedSignals` from the SUMMED total) | Asleep stages = `{SLEEPING(2), LIGHT(4), DEEP(5), REM(6)}`; exclude `AWAKE(1)`, `OUT_OF_BED(3)`, `AWAKE_IN_BED(7)`, `UNKNOWN(0)` — mirrors iOS `allAsleepValues`. If a session has NO stages, use full `endTime-startTime` as asleep hours (best-effort). One row per session; `ImportedSignals` SUMs sessions in a day. `[ASSUMED stage int constants — verify at impl]` |
| `workouts` | `ExerciseSessionRecord` | `startTime`, `endTime`, `exerciseType: Int` | `activityTypeRaw` = mapped `ActivityType.raw`; `activityMinutes` = `(endTime-startTime)` minutes | Family map (mirror iOS): WALKING={WALKING,HIKING}; CARDIO={RUNNING, RUNNING_TREADMILL, BIKING, BIKING_STATIONARY, SWIMMING_POOL, SWIMMING_OPEN_WATER, HIGH_INTENSITY_INTERVAL_TRAINING, ROWING, ROWING_MACHINE, ELLIPTICAL}; STRENGTH={STRENGTH_TRAINING, WEIGHTLIFTING, CALISTHENICS}; YOGA_STRETCHING={YOGA, PILATES, STRETCHING}; everything else → OTHER. `ImportedSignals` SUMs minutes, keeps longest session's type. `[ASSUMED exerciseType constants — verify at impl]` |
| `nutrition` | `NutritionRecord` | `startTime`, `endTime`, `energy: Energy?` | `ateThatDay = true` | Presence-only "ate that day" — NEVER a FoodTag or micronutrient. `true` iff ≥1 NutritionRecord (with dietary energy) that day. `[VERIFIED: iOS HealthSampleMapping.ateThatDay]` |

**Shared day-bucketing rule (MUST match iOS):** iOS buckets each sample by `Calendar.startOfDay(sample.startDate)` in the **device's current timezone**. Android mirror: `epochDay = record.startTime.atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()` — bucket by the record's **start** instant, in the **device's current zone** (NOT the record's stored `zoneOffset`, which would diverge from iOS). A sleep/workout spanning midnight lands entirely on its **start** day (this matches the iOS behavior — including the known iOS midnight-crossing sleep attribution, v1.0 WR-02). Instant→LocalDate is always well-defined across DST/nonexistent-midnight (the nonexistent-midnight hazard only bites date→instant, which this path never does). `[CITED: iOS ImportedSampleValue.day / HealthSampleMapping; PITFALLS.md Pitfall 11]`

### Anti-Patterns to Avoid
- **Calling `getOrCreate()` without `getSdkStatus()`** — crashes or a dead button on API 26–33. `[VERIFIED: PITFALLS.md Pitfall 5]`
- **Caching grant state** (`hcAuthorized` boolean) — `SecurityException` after settings revocation or unused-app auto-reset. Call `getGrantedPermissions()` per session. `[VERIFIED: PITFALLS.md Pitfall 6]`
- **Per-day `readRecords` over the range** — quota burner; use changes-token differential sync. `[VERIFIED: PITFALLS.md Pitfall 6]`
- **Requesting `READ_HEALTH_DATA_IN_BACKGROUND`** — extra sensitive declaration for a feature the plan excludes; foreground-only import (on app-open / import-screen visit). `[VERIFIED: FEATURES.md Anti-Features]`
- **Bucketing by the record's `zoneOffset`** instead of the device zone — diverges from iOS; two platforms disagree which day a period/sleep started. `[VERIFIED: PITFALLS.md Pitfall 11]`
- **Dropping `metadata.id`/`dataOrigin`** at ingestion — loses idempotency, provenance labels, and delete-by-origin. `[VERIFIED: PITFALLS.md Pitfall 7]`
- **Only one rationale declaration** — dead permission flow on the other OS generation. `[VERIFIED: PITFALLS.md Pitfall 1]`

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Incremental "what changed since last sync" | Manual timestamp diffing over full re-reads | `getChangesToken`/`getChanges` (`UpsertionChange`/`DeletionChange`, `hasMore`, `nextChangesToken`) | HC gives an exact commit-history cursor incl. deletions; hand-rolled diffing misses deletions + burns quota |
| Availability detection | Version checks / try-catch around `getOrCreate` | `HealthConnectClient.getSdkStatus(context)` | The sanctioned three-state signal; version heuristics miss the installable-but-stale case |
| Permission request UI | A custom permission dialog | `PermissionController.createRequestPermissionResultContract()` | HC owns the system permission UI; a custom one cannot grant HC permissions |
| Record dedupe identity | A content hash of record fields | `metadata.id` → `hcRecordId` unique index | HC provides a stable per-record id; content hashing breaks on benign edits |
| Deletion detection | Re-reading + set-diffing every sync | `DeletionChange.recordId` from `getChanges` | HC reports deletions directly; set-diffing is O(n) and fragile |
| Sleep "asleep hours" | Summing raw session duration | Σ asleep-stage durations (exclude AWAKE/OUT_OF_BED) | Matches iOS `nightlyAsleepHours`; raw duration over-counts in-bed-awake time |

**Key insight:** The only genuinely bespoke logic is the **record→value mapping** (`HealthRecordMapping`, the iOS `HealthSampleMapping` analog). Everything else — deltas, deletions, the resumable cursor, availability, the permission UI — is Health Connect's own; porting it by hand reintroduces quota, dedupe, and deletion bugs.

## Runtime State Inventory

> This is an additive feature phase, not a rename/refactor — but new runtime cursor/permission state is introduced. Documented for completeness.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | New `ImportedSample` rows written from HC (Room, already-built entity). `hcRecordId` unique index dedupes. | Code (this phase writes them); no migration (entity exists, schema unchanged) |
| Live service config | Health Connect grant state lives in the HC provider app / OS module — NOT in the app. Never mirrored/cached. | Read `getGrantedPermissions()` per session |
| OS-registered state | Manifest `<uses-permission>` health READ perms + the two rationale intent-filter declarations register the app as an HC client. | New manifest declarations (Pattern 2, 3) |
| Secrets/env vars | None — HC is local IPC; no keys, no INTERNET. | None |
| Build artifacts | Adding `connect-client` to the catalog changes the merged manifest (HC queries/permissions merged in). | Rebuild; ManifestPermissionsTest re-runs against the merged manifest |
| Sync cursor | Per-domain changes-token in DataStore (new; outside the Room health DB). Device runtime state, excluded from the export contract. | New DataStore file (or reuse a prefs store); token expiry handled via fallback |

## Common Pitfalls

### Pitfall 1: Only one rationale declaration wired
**What goes wrong:** HC permission dialog silently returns an empty grant set on the uncovered OS generation, or Play rejects.
**Why it happens:** Android 13- uses `ACTION_SHOW_PERMISSIONS_RATIONALE`; Android 14+ uses the `VIEW_PERMISSION_USAGE`+`HEALTH_PERMISSIONS` activity-alias. They look redundant; one gets "cleaned up."
**How to avoid:** Declare BOTH (Pattern 2); manifest test asserts both element/alias declarations exist.
**Warning signs:** Permission launcher resolves instantly with zero grants on one device but works on another.

### Pitfall 2: 30-day history silently truncates the import
**What goes wrong:** A user with 18 months of HC menstruation data imports ~one cycle; cycle-phase insights that had runway on iOS report under-sample withholding on Android.
**Why it happens:** Default read window is ~30 days pre-grant; older data needs `READ_HEALTH_DATA_HISTORY`. Dev test data is freshly seeded so it never shows.
**How to avoid:** Request `READ_HEALTH_DATA_HISTORY` (recommended) AND label the window honestly; test with a seeded store containing records older than 30 days.
**Warning signs:** Earliest imported day is suspiciously recent.

### Pitfall 3: Caching grant state / hammering rate limits
**What goes wrong:** `SecurityException` weeks later after revocation/auto-reset; intermittent import failures from per-day reads hitting quota.
**How to avoid:** `getGrantedPermissions()` per session; changes-token differential sync with a bounded ranged-read fallback; retry-after on rate-limit signals; foreground-only (no background read permission).
**Warning signs:** A persisted `hcAuthorized` boolean; one `readRecords` per day-of-range.

### Pitfall 4: Losing provenance / record identity
**What goes wrong:** Straight record→value mapping discards `metadata.id`/`dataOrigin` → duplicate rows on re-import (double-counted days corrupt insights), no "from Health Connect" label, no delete-by-origin.
**How to avoid:** Store `hcRecordId` (unique index) + `dataOrigin`; deterministic id; propagate `DeletionChange`.
**Warning signs:** Import round-trip run only once; sleep/workout numbers grow after re-import.

### Pitfall 5: Bucketing divergence from iOS (DST / travel / midnight)
**What goes wrong:** A sleep session spanning midnight, or a record written in another timezone, lands on a different day than iOS → users comparing devices see disagreement.
**How to avoid:** Bucket by the record's **start** instant in the **device's current zone** (`ZoneId.systemDefault()`), never the record's `zoneOffset`; run one test in `Europe/Berlin` and one in `Pacific/Apia`.
**Warning signs:** Bucketing tests only run in UTC (CI default).

## Code Examples

### Consent-gated import coordinator (iOS HealthImportService analog)
```kotlin
// Consent defence-in-depth + read-status honesty. Off the main thread.
suspend fun importAll(reader: HealthReader, repo: EndoRepository) = withContext(Dispatchers.Default) {
    // repo methods already checkConsented(); the coordinator also refuses pre-consent (belt + suspenders).
    for (domain in HealthDomain.entries) {
        val token = changesTokenStore.token(domain)          // null = seed
        val result = try { reader.read(domain, token) } catch (e: Exception) {
            changesTokenStore.clear(domain); continue         // corrupt token → re-seed next time; never crash
        }
        result.upserts.forEach { repo.upsertImported(it) }   // idempotent by hcRecordId
        result.deletedHcRecordIds.forEach { repo.deleteImportedByHcRecordId(it) }  // propagate HC deletion
        changesTokenStore.setToken(domain, result.nextToken) // advance only after a successful persist
    }
    // Phase 22 reads via EndoRepository.daySnapshots(); no new recompute machinery here.
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Google Fit APIs | Health Connect (`connect-client`) | Fit deprecated; HC is the sole broker | HC-only; Android 13- install path for the provider app |
| `SDK_STATUS_UPDATE_REQUIRED` (older naming) | `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` | connect-client 1.x | Use the current constant (PITFALLS.md used the old name) |
| Read all history by default | ~30-day pre-grant window unless `READ_HEALTH_DATA_HISTORY` | HC policy | Longitudinal apps must request history or label the window |
| `MenstruationRecord` | `MenstruationFlowRecord` (+ separate `MenstruationPeriodRecord`) | connect-client evolution | Flow intensity + period span are separate records, one `READ_MENSTRUATION` perm |

**Deprecated/outdated:**
- Google Fit — replaced by Health Connect.
- Any single-rationale-declaration tutorial predating Android 14 — replace with the dual declaration.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `MenstruationFlowRecord.flow` int constants: `FLOW_UNKNOWN=0, FLOW_LIGHT=1, FLOW_MEDIUM=2, FLOW_HEAVY=3` | Field Mapping | Wrong flow mapping; MEDIUM — confirm against `MenstruationFlowRecord` SDK source at impl |
| A2 | `SleepSessionRecord.Stage.stage` asleep constants `{SLEEPING=2, LIGHT=4, DEEP=5, REM=6}` (exclude AWAKE=1, OUT_OF_BED=3, AWAKE_IN_BED=7, UNKNOWN=0) | Field Mapping | Over/under-counted sleep hours; MEDIUM — confirm STAGE_TYPE_* ints at impl |
| A3 | `ExerciseSessionRecord.exerciseType` int family membership (WALKING/CARDIO/STRENGTH/YOGA families) | Field Mapping | Mis-typed workouts (still SUMs minutes correctly); LOW — everything unmapped falls to OTHER by design |
| A4 | `FLOW_UNKNOWN(0)→SPOTTING` (parity with iOS `.unspecified→.spotting`) rather than →null | Field Mapping | A present-but-unknown flow shows as spotting vs. nothing; LOW — either is honest; confirm the parity intent |
| A5 | Device-zone (`ZoneId.systemDefault()`) bucketing exactly reproduces iOS `Calendar.startOfDay` for all users | Shared bucketing rule | Cross-platform day disagreement; MEDIUM — pin with DST/travel test vectors |
| A6 | Requesting `READ_HEALTH_DATA_HISTORY` is acceptable Play-declaration friction for this app's longitudinal value | History Window / AHC-04 | Extra review scrutiny; LOW-MEDIUM — user/planner confirms the request-vs-label call |
| A7 | A `SleepSessionRecord` with no stages → use full session duration as asleep hours | Field Mapping | Slight over-count for stage-less providers; LOW — confirm desired behavior |

**If a claim above is load-bearing for a test, the executor must confirm the exact constant against `androidx.health.connect.client.records.*` at implementation time.**

## Open Questions

1. **Should `MenstruationPeriodRecord` contribute anything, or is `MenstruationFlowRecord` sufficient?**
   - What we know: iOS read menstrual flow ONLY (flow intensity per day). `MenstruationPeriodRecord` is a span with no intensity.
   - What's unclear: whether period-day *presence* (span, no flow) adds value beyond flow rows.
   - Recommendation: import `MenstruationFlowRecord` for `flowRaw` (parity); treat `MenstruationPeriodRecord` as optional/deferred unless a period-day-presence signal is wanted. Both share `READ_MENSTRUATION`.

2. **One-shot import trigger: coroutine on app-open vs. a WorkManager one-shot.**
   - What we know: iOS imports on app-open/refresh; no background sync (locked). Claude's discretion.
   - Recommendation: foreground coroutine on import-screen visit + a manual "Refresh from Health Connect" — simplest, stays under the friendlier quota, no extra WorkManager surface (keeps the flare-notification-separation scan clean).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android SDK platform 36 | Build | ✓ | 36 | — |
| `androidx.health.connect:connect-client` | HC import | ✓ (Google Maven) | 1.1.0 | — |
| Real HC provider (device/emulator) | Actual permission dialog + provider data | ✗ (device-gated) | — | `FakeHealthReader` covers all logic in JVM; real provider is manual verification |
| API 34+ emulator with HC module (or API 28–33 with the installable app) | End-to-end permission/import verify | ✗ (not in JVM lane) | — | Deferred to device-gated manual verification (per CONTEXT Deferred) |

**Missing dependencies with fallback:**
- Real HC provider dialog + data → `FakeHealthReader` makes import/merge/idempotency/denial/availability JVM-testable; the real dialog is device-gated manual verification (matches iOS, which gated the real HealthKit sheet).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 (`:app` unit lane) + Robolectric/Compose JVM where a Context is needed; androidTest (`androidx.test.ext:junit` 1.3.0) for device-gated pieces |
| Config file | `android/app/build.gradle.kts` (existing lanes) |
| Quick run command | `./gradlew :app:testDebugUnitTest --tests "com.endoapp.app.health.*"` |
| Full suite command | `./gradlew :app:testDebugUnitTest :app:assembleDebug` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AHC-01 | `getSdkStatus` maps to 3 states; UI renders each; manual logging never blocked | unit | `./gradlew :app:testDebugUnitTest --tests "*HealthAvailabilityTest"` | ❌ Wave 0 |
| AHC-02 | Manifest declares 4 READ perms (+history), ZERO health WRITE, BOTH rationale declarations | unit (DOM-parse) | `./gradlew :app:testDebugUnitTest --tests "*ManifestPermissionsTest"` | ⚠️ EXTEND existing |
| AHC-02 | No `READ_HEALTH_DATA_IN_BACKGROUND`, no `INTERNET` (regression) | unit | same as above | ⚠️ EXTEND |
| AHC-03 | Record→`ImportedSample` mapping (flow/sleep-Σ/exercise/nutrition) exact | unit | `./gradlew :app:testDebugUnitTest --tests "*HealthRecordMappingTest"` | ❌ Wave 0 |
| AHC-03 | Idempotent re-import via `FakeHealthReader` (run twice → identical rows) | unit | `./gradlew :app:testDebugUnitTest --tests "*HealthImportCoordinatorTest"` | ❌ Wave 0 |
| AHC-03 | `DeletionChange` propagates → row removed; deleted-by-hcRecordId | unit | same | ❌ Wave 0 |
| AHC-03 | Denied/empty read contributes nothing (nil-honest; no fabricated 0) | unit | same | ❌ Wave 0 |
| AHC-03 | Provenance `dataOrigin`→ "from Health Connect" surfaced on imported rows | unit | `*HealthImportCoordinatorTest` / mapping | ❌ Wave 0 |
| AHC-03 | Day-bucketing matches iOS under DST (`Europe/Berlin`) + travel (`Pacific/Apia`) + midnight-spanning session | unit (zoned) | `*HealthRecordMappingTest` | ❌ Wave 0 |
| AHC-04 | `READ_HEALTH_DATA_HISTORY` declared; window label rendered honestly | unit + copy audit | `*ManifestPermissionsTest` + `*CopyAuditTest` | ⚠️ EXTEND + ❌ |
| all | New copy strings dodge forbidden substrings + registered in CopyAudit | unit | `./gradlew :app:testDebugUnitTest --tests "*CopyAudit*"` | ⚠️ EXTEND |

### Injectable-Fake Strategy
The `HealthReader` interface is the single seam. `FakeHealthReader` scripts: (a) a seed read + a re-read of the same rows (idempotency), (b) a read with a `DeletionChange` (propagation), (c) an empty read (denied-OR-empty honesty), (d) a token-expiry throw (fallback), (e) per-availability-state rendering (drive `HealthAvailability` directly). No emulator, no real provider — all import/merge/idempotency/denial/availability logic is JVM-testable. The real `HealthConnectReader` (thin adapter over `HealthConnectClient`) is exercised only in device-gated manual verification.

### Manifest Assertions (extend `ManifestPermissionsTest`)
- Source manifest declares EXACTLY the allow-set `{POST_NOTIFICATIONS, READ_MENSTRUATION, READ_SLEEP, READ_EXERCISE, READ_NUTRITION, READ_HEALTH_DATA_HISTORY}` — no more (currently pinned to exactly `{POST_NOTIFICATIONS}`; UPDATE the allow-list).
- ZERO permission with prefix `android.permission.health.WRITE_` (existing assertion — keep).
- NO `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` (new deny assertion).
- NO `INTERNET` (existing — keep).
- BOTH rationale declarations present: an `<activity>` with `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`, AND an `<activity-alias>` with `android.intent.action.VIEW_PERMISSION_USAGE` + category `android.intent.category.HEALTH_PERMISSIONS` (new DOM-parse assertions over `<activity>`/`<activity-alias>` intent-filters).

### Sampling Rate
- **Per task commit:** `./gradlew :app:testDebugUnitTest --tests "com.endoapp.app.health.*" --tests "*ManifestPermissionsTest"`
- **Per wave merge:** `./gradlew :app:testDebugUnitTest`
- **Phase gate:** `./gradlew :app:testDebugUnitTest :app:assembleDebug` green before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `health/HealthAvailabilityTest.kt` — covers AHC-01 (three-state mapping)
- [ ] `health/HealthRecordMappingTest.kt` — covers AHC-03 (field maps + DST/travel/midnight bucketing)
- [ ] `health/HealthImportCoordinatorTest.kt` + `health/FakeHealthReader.kt` — covers AHC-03 (idempotency, deletion, empty/denied, provenance)
- [ ] EXTEND `ManifestPermissionsTest.kt` — covers AHC-02/AHC-04 (allow-list update, dual rationale, no background/write)
- [ ] EXTEND the CopyAudit surface with `HealthConnectCopy.auditableStrings` — covers copy discipline
- [ ] Device-gated (manual, deferred): real permission dialog on an Android 13 + an Android 14+ device + a no-HC emulator

### Device-Gated (inevitably manual, per CONTEXT Deferred)
- Real HC permission dialog appears + grants; real provider data imports; the two rationale intents resolve on-device; Play pre-launch report (Phase 25) checks the rationale handlers.

## Security Domain

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No accounts/auth (locked "no login") |
| V3 Session Management | no | No sessions |
| V4 Access Control | yes | Consent gate (`EndoRepository.checkConsented`) fronts all HC persistence; HC grant checked per session; read-only manifest |
| V5 Input Validation | yes | Defensive decode of every HC record field (unknown flow/stage/exerciseType → null/OTHER, never crash/fabricate); ignore records from own package |
| V6 Cryptography | no | No crypto in this phase (export crypto is Phase 23) |
| V7 Error Handling & Logging | yes | Never log record contents; a failed read contributes nothing (no crash); token errors → re-seed |
| V8 Data Protection | yes | Read-only (zero WRITE), no `INTERNET`, no background read; imported rows are special-category data staying on-device; provenance preserved for delete-by-origin |

### Known Threat Patterns for {Health Connect / Android}
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cached grant → `SecurityException` after revocation | Denial of Service | `getGrantedPermissions()` per session; graceful denial path |
| Rate-limit exhaustion from full re-reads | Denial of Service | changes-token differential sync; retry-after; bounded fallback |
| Fabricating a value from a denied/empty read | Tampering (data integrity) | nil-honest: empty read leaves fields null, never 0; denied == empty |
| Over-broad permissions (incidental access) | Elevation / Play rejection | Exactly 4 READ + history; each maps to a visible feature; zero WRITE; no background |
| Free-text/micronutrient prose crossing into analysis | Information disclosure | Only category-level values on `ImportedSample`; presence-only nutrition; `hasNote` presence-only in the projector (Phase 18) |
| Cross-timezone/DST day misattribution | Tampering (correctness) | Device-zone start-instant bucketing matching iOS; zoned tests |
| Record injected by own package re-imported | Spoofing/loop | `dataOrigin.packageName != context.packageName` guard (we write none anyway) |
| Manifest silently gains a WRITE/INTERNET/background perm | Elevation | `ManifestPermissionsTest` allow-list + deny-list assertions (auditable artifact) |

## Health Connect UX

> This phase has no separate UI-SPEC. This section is the connect/import UI contract: the connect-before-permission → request → import-states flow, in Bloom, with every copy string enumerated. All copy dodges the ClaimsPolicy forbidden SUBSTRINGS **should / use / her / start / treat / stop / take** (incl. the "her" substring trap: no here/there/gather/other/higher/together). Every string below is registered in `HealthConnectCopy.auditableStrings` in the same change (CopyAudit).

### Entry point (Settings and/or Log)
- A Bloom row/card labeled **"Connect Health data"** with a short caption **"Bring in cycle, sleep, workouts, and meals — read-only, on your device."** Tapping resolves `getSdkStatus()` per session and routes to the matching state below. Manual logging is never gated behind this.

### Flow (happy path — SDK_AVAILABLE)
```
[Connect Health data] ──▶ Rationale screen (BEFORE the system sheet)
        ▲                      │ "Continue"
   never blocks manual         ▼
   logging               System HC permission UI (PermissionController contract)
                               │ result (granted set; read per session)
                               ▼
                         Import screen: progress → window label → imported rows
                                                    with "from Health Connect" badges
```

### Screen A — Rationale-before-permission (`HealthRationaleScreen`, also the manifest rationale target)
Bloom layout: headline, three plain lines (read-only / private / optional), a "what it reads" line, primary + dismiss buttons.

| Element | Copy string | Substring check |
|---------|-------------|-----------------|
| Headline | **"Connect Health data"** | clean |
| Read-only line | **"Endo-App only reads from Health Connect. It never changes or adds anything."** | clean (no use/start/her/take) |
| Private line | **"Your data stays on your device. No one else can see it."** | clean |
| Optional line | **"This is optional. You can skip it, and you can turn it off anytime."** | clean (no "start") |
| What it reads | **"It can bring in your cycle, sleep, workouts, and meal days so you do not log them twice."** | clean (no "use") |
| Primary button | **"Continue"** (only path to the system sheet) | clean |
| Dismiss button | **"Not now"** (closes, no nag) | clean |

### Screen B — System permission request
The OS-owned HC permission UI (via `PermissionController.createRequestPermissionResultContract()`). App shows nothing custom here. On return, read the granted set per session and proceed regardless (partial grants are fine — import what is granted).

### Screen C — Import states (`HealthImportScreen`)
| State | Copy string | Notes / substring check |
|-------|-------------|-------------------------|
| In progress | **"Bringing in your data…"** | clean |
| Window label — history granted | **"Bringing in your full history from Health Connect."** | clean (honest: history permission granted) |
| Window label — 30-day only (if history denied/not requested) | **"Bringing in the last 30 days from Health Connect."** | clean; honest window (AHC-04) |
| Done, with data | **"Connected. Imported data shows a Health Connect badge."** | clean |
| Empty result | **"Nothing to bring in yet. When an app writes cycle, sleep, workouts, or meals to Health Connect, it can appear on this screen."** | clean (avoids "here"; matches nil-honest ethos — an empty broker is the common first-run outcome) |
| Per-permission status (optional, gentle) | e.g. **"Sleep: connected · Nutrition: not connected"** | clean; uses grant knowledge only to be truthful, never to nag |
| Refresh action | **"Refresh from Health Connect"** | clean |

### Provenance badge (`HealthSourceBadge`)
- Chip on any imported value: **"from Health Connect"** — pure source tag, never a claim about the value. Read from `dataOrigin`. Consumed later by the PDF report (Phase 24) per-source labels.

### Denial (graceful — no nag)
| Element | Copy string | Notes |
|---------|-------------|-------|
| Denied banner | **"No data was connected. Manual logging works exactly the same."** | Manual logging unaffected; no re-prompt loop |
| Re-open hint | **"You can connect Health data anytime from Settings."** | Path stated plainly (no "start"/"use") |

### Availability-unavailable states (honest, per `getSdkStatus`)
| State | Copy string | Action |
|-------|-------------|--------|
| PROVIDER_UPDATE_REQUIRED — not installed (API 28–33) | **"Health Connect is not on this device yet. You can add it from Google Play, or keep logging by hand."** | Button **"Get Health Connect on Google Play"** → Play deep link |
| PROVIDER_UPDATE_REQUIRED — stale | **"Health Connect needs an update. You can update it on Google Play, or keep logging by hand."** | Button **"Update on Google Play"** → Play deep link |
| SDK_UNAVAILABLE — impossible (API 26–27) | **"Health Connect is not available on this device. Manual logging works exactly the same."** | No action; import surface softened, not an error |

> Every availability-unavailable state reads as **normal**, not broken — manual logging is the hero path everywhere (AHC-01). Re-check `getSdkStatus()` on each foreground; the user can install/update the provider between sessions.

### Copy-audit gate
All strings above live in `HealthConnectCopy` (the iOS `HealthConnectCopy.swift` analog) with an `auditableStrings` array scanned by the CopyAudit harness against `ClaimsPolicy` forbidden substrings in the SAME change. Manual re-check performed above; the automated gate is the enforcing artifact.

## Sources

### Primary (HIGH confidence)
- developer.android.com/health-and-fitness/health-connect/get-started — dual rationale manifest declarations (exact XML), `getSdkStatus` three constants, `getOrCreate`, `PermissionController.createRequestPermissionResultContract`, `getGrantedPermissions` — VERIFIED via WebFetch
- developer.android.com/health-and-fitness/health-connect/read-data — 30-day window, `READ_HEALTH_DATA_HISTORY` manifest string, reinstall reset — VERIFIED via WebFetch
- developer.android.com/health-and-fitness/health-connect/sync-data + github.com/android/snippets HealthConnectManager.kt — `getChangesToken`/`getChanges`, `UpsertionChange`/`DeletionChange`, `hasMore`/`nextChangesToken`, `ChangesTokenExpiredException`, 30-day token expiry, own-package guard — VERIFIED via WebSearch
- support.tryrook.io Health Connect Permissions KB + developer.android.com HealthPermission — exact `READ_MENSTRUATION/READ_SLEEP/READ_EXERCISE/READ_NUTRITION` strings; menstruation flow+period share `READ_MENSTRUATION` — VERIFIED
- Codebase: `ImportedSample.kt`, `ImportedSignals.kt`, `SnapshotProjector.kt`, `SymptomDao.kt`, `EndoRepository.kt`, `ManifestPermissionsTest.kt`, `AppDependencies.kt`; iOS `HealthDataReading.swift`, `FixtureHealthDataReading.swift`, `HealthImportService.swift`, `HealthAnchorStore.swift`, `HealthSampleMapping.swift`, `HealthDomain.swift`, `ImportedSampleValue.swift`, `HealthConnectCopy.swift` — HIGH (first-party)

### Secondary (MEDIUM confidence)
- `.planning/research/STACK.md`, `FEATURES.md`, `PITFALLS.md` — pins, HC UX norms, dual-rationale/30-day/rate-limit/provenance pitfalls
- `.planning/phases/18-room-persistence/18-04-SUMMARY.md` — the ported `ImportedSignals`/`HealthDomain`/merge this phase feeds

### Tertiary (LOW confidence — verify at implementation)
- HC record-class integer constants (flow / sleep-stage / exerciseType) — inferred from training + HC data-types docs; confirm against `androidx.health.connect.client.records.*` at impl (Assumptions A1–A3)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — connect-client 1.1.0 verified against AndroidX releases + STACK.md
- API surface (availability / permissions / manifest / changes-token): HIGH — verified against developer.android.com + android/snippets
- Record field-integer constants: MEDIUM — assumed from training, flagged for impl-time confirmation
- Architecture / pitfalls: HIGH — corroborated by iOS port source + PITFALLS.md
- UX copy: HIGH on substring-safety (manually re-checked) — the CopyAudit harness is the enforcing gate

**Research date:** 2026-07-09
**Valid until:** 2026-08-09 (30 days; HC API + Play health policy evolve — re-verify the 30-day/history semantics and constant names at implementation)
