# Phase 9: HealthKit Import - Research

**Researched:** 2026-06-09
**Domain:** Apple HealthKit read-only import (iOS 18 / Xcode 26 / Swift 6) into a SwiftData store, merged through a value-type `DaySnapshot` pipeline; contextual permission UX; testability under Simulator HealthKit limits.
**Confidence:** HIGH (architecture mirrors proven Phase 5–8 seams; HealthKit API facts CITED to Apple docs, version-flagged where the registry could not be deep-fetched)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Imported samples persist in a **separate, additive, CloudKit-safe `ImportedSample` @Model** (new SchemaV(N+1), lightweight migration) — it NEVER mutates or overwrites manual-logging rows.
- **D-02:** **Manual logging always wins.** Imported data fills only days/fields where the user has NO manual entry. A manual entry is never displaced by a Health sample.
- **D-03:** **Provenance is visible** — any value sourced from Apple Health is labeled "from Apple Health" in the UI (badge/source tag).
- **D-04:** In the `DaySnapshot` mapping, a Health-sourced value is used ONLY where the manual value is absent, and it **respects `nil` = unlogged** (never coerce absence to 0). Once mapped, the engine treats a Health-sourced value identically to a manual one. A denied/empty Health read contributes nothing — must never read as "no symptoms" (SC3).
- **D-05:** **Contextual, per-domain permission** — request a category's read access at the moment the user opts into that data, NOT one mega-prompt at launch.
- **D-06:** Each request shows an **in-app rationale screen BEFORE Apple's system sheet**: plain-language statement of what's read, that it's **read-only**, stays on-device / in private iCloud, and is **optional** — then "Continue" triggers `HKHealthStore.requestAuthorization`.
- **D-07:** **Entry points:** a "Connect Apple Health" section in **Settings** PLUS contextual connect affordances on the relevant logging surfaces.
- **D-08:** **On denial: never nag.** Passive "Connect Apple Health" affordance; if previously denied, deep-link to the Settings app (HK read auth can't be re-requested in-app once decided). App stays fully usable via manual logging (SC3).
- **D-09:** **Foreground incremental pull** via `HKAnchoredObjectQuery` per granted type — fetch only new/changed samples since the last persisted anchor.
- **D-10:** Also provide a **manual "Refresh from Health"** action.
- **D-11:** **Defer background delivery** (`HKObserverQuery` + `enableBackgroundDelivery`) — future enhancement.
- **D-12:** After an import writes new `ImportedSample`s, call the Phase-8 **`InsightsCoordinator.dataChanged()`** (reuse existing seam; no new recompute machinery).
- **D-13:** **First-grant backfill = last 365 days** via a bounded anchored query; subsequent syncs are incremental from the stored anchor.
- **D-14:** **Cycle = menstrual flow** (`HKCategoryType(.menstrualFlow)`) only; defer basal body temp / cervical mucus.
- **D-15:** **Sleep** = `HKCategoryType(.sleepAnalysis)` → nightly asleep **hours + coarse quality** mapped onto app `SleepQuality`. **Workouts** = `HKWorkout` → app `ActivityType` + minutes (map common `HKWorkoutActivityType`; "other" fallback).
- **D-16:** **Nutrition** = **meal-event presence** ("ate that day", from dietary energy / meal samples) matching the tag-based `FoodTag` model — NOT a micronutrient breakdown.

### Claude's Discretion
- Exact `ImportedSample` schema shape, the `HKWorkoutActivityType → ActivityType` mapping table, the sleep-stage → `SleepQuality` thresholds, the rationale-screen copy (must pass `ClaimsPolicy`), the anchor-persistence mechanism, and the precise Settings/contextual UI layout are left to research/planning.

### Deferred Ideas (OUT OF SCOPE)
- HealthKit write-back / export (PLAT-01).
- Background delivery (`HKObserverQuery` + `enableBackgroundDelivery`).
- Basal body temp / cervical mucus cycle signals.
- Full micronutrient nutrition import.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DATA-05 | User can import cycle/sleep/workouts/nutrition from Apple HealthKit (read-only); the app stays fully usable if access is denied. | Standard Stack (HealthKit read APIs, anchored query, per-type read auth); Architecture Patterns (injectable `HealthDataReading` seam → `ImportedSample` → `daySnapshots()` manual-wins merge → `InsightsCoordinator.dataChanged()`); Pitfall 1 (read-auth status is hidden — "denied" and "empty" are indistinguishable, so the app must degrade identically and never render absence as "no symptoms" = SC3); Validation Architecture (fixture-provider seam behind a `-uitest` launch arg makes the whole flow CI-testable without a real device). |
</phase_requirements>

## Summary

Phase 9 is an **additive read-source** phase, not a new-architecture phase. The hard architectural problems (the `DaySnapshot` value contract, the consent gate, the additive CloudKit-safe schema pattern, the off-main recompute coordinator) are all already solved and proven in Phases 1–8. Phase 9's job is to (1) add a HealthKit read service **app-side, outside the Foundation-only `EndoInsights` package** — mirroring the existing SwiftData boundary exactly; (2) persist imported samples in a new additive `ImportedSample` `@Model` under `SchemaV6` (clone the V4→V5 `CachedInsight` checklist); (3) merge those samples into `daySnapshots()` with **manual-always-wins** semantics; and (4) call the existing `InsightsCoordinator.dataChanged()` after an import.

The dominant *correctness* risk is **HealthKit's privacy-preserving read-authorization model**: an app **cannot tell** "user denied read access" from "no data exists" from "not yet determined" — `authorizationStatus(for:)` is deliberately uninformative for read types `[CITED: developer.apple.com/.../authorizationStatus(for:)]`. This is not a bug to work around — it is the exact mechanism that makes SC3 honest: denied and empty must be handled **identically**, and neither may ever fabricate a "no symptoms" reading. The dominant *engineering* risk is **Swift 6 strict concurrency**: `HKHealthStore` is **not `Sendable`**, and the legacy completion-handler query results (`[HKSample]?, [HKDeletedObject]?, HKQueryAnchor?`) trip "task-isolated value passed as strongly transferred" errors under the Swift 6 language mode `[VERIFIED: Apple Developer Forums thread 763998 + the project already builds in Swift 6 language mode]`. The mitigation is to wrap HealthKit behind an `actor`/`@HealthKitActor`-isolated service that exposes `async` methods returning **plain Sendable value types** (never `HKSample` objects), so nothing non-`Sendable` crosses the seam.

**Primary recommendation:** Build a single app-side `HealthImportService` (protocol-injected, like `Repository` and `NotificationScheduler`) that wraps a non-`Sendable` `HKHealthStore` inside an isolation domain, exposes `async` per-domain "request auth" + "pull since anchor" methods returning `Sendable` value structs, persists those into `ImportedSample` rows, and calls `InsightsCoordinator.dataChanged()`. Gate every HealthKit call behind a `HealthDataReading` protocol whose fixture implementation is selected by a `-uitest-health-*` launch arg (mirroring `-uitest-reset`/`-uitest-seed-insights`), so the entire import path is E2E-testable on the Simulator with zero real-device data.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| HealthKit read authorization (rationale → system sheet) | App UI + app-side service | — | `HKHealthStore` is iOS-only and non-`Sendable`; must NOT enter the Foundation-only `EndoInsights` package (mirrors the SwiftData boundary). |
| Reading samples (anchored query, backfill) | App-side service (isolated) | — | Touches `HKHealthStore`; returns `Sendable` value types to the rest of the app. |
| Anchor persistence | App-side service / `UserDefaults` (or SwiftData) | — | A per-type cursor; lives outside the synced health store (like `SyncMode`/`ReminderSettings`), NOT in CloudKit. See Open Question 2. |
| Imported-sample persistence | SwiftData (`ImportedSample` `@Model`, SchemaV6) | CloudKit (private) | Additive, CloudKit-safe; merges through the existing store. |
| Manual-wins merge into `[DaySnapshot]` | `Repository.daySnapshots()` (app-side) | `EndoInsights` (consumer) | The ONLY place `@Model` rows become the value contract; the package never sees HealthKit. |
| Insights recompute after import | `InsightsCoordinator.dataChanged()` (Phase 8) | `EndoInsights` engine (off-main) | Reuse the existing debounced off-main seam (D-12). |
| Provenance labelling ("from Apple Health") | App UI (badge/source tag) | `DaySnapshot` (carries a per-field source flag) | D-03 visibility requirement; copy routed through `ClaimsPolicy`. |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| **HealthKit** | iOS 18+ SDK (Xcode 26) | Read cycle/sleep/workout/nutrition samples | The only first-party, privacy-gated source for this data; read-only auth keeps scope minimal. `[CITED: developer.apple.com/documentation/healthkit]` |
| **SwiftData** (existing) | iOS 18+ | `ImportedSample` persistence + private CloudKit sync | Reuse the proven additive-schema path. |
| **EndoInsights** (existing local SPM) | — | Consumes `[DaySnapshot]` only | HealthKit must NOT enter it (Foundation-only boundary). |
| **Swift Testing** (existing) | Xcode 26 | Unit tests (mapping, merge, sleep/workout/nutrition projection, schema survival) | Project default for new test code. |
| **XCUITest** (existing) | Xcode 26 | E2E import flow via the fixture seam | Only native UI-automation option. |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **HealthKitUI** | iOS 18+ | (Optional) `healthDataAccessRequest` SwiftUI modifier for the auth sheet | Only if the team prefers the SwiftUI-native sheet trigger over calling `HKHealthStore.requestAuthorization(toShare:read:)` directly. Either is acceptable; the rationale screen (D-06) is bespoke regardless. `[CITED: developer.apple.com/documentation/healthkitui]` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `HKAnchoredObjectQuery` (D-09 locked) | `HKSampleQuery` | Sample query re-scans the whole window every time; anchored query returns only deltas + a resumable cursor. D-09 locks anchored — use it. Sample query is acceptable ONLY for the initial bounded 365-day backfill if the team finds anchored backfill awkward, but anchored handles both (initial + incremental) with one mechanism, so prefer it throughout. |
| `HKCategoryTypeIdentifier.menstrualFlow` (D-14 locked) | `HKCategoryTypeIdentifier.vaginalBleeding` (newer) | iOS introduced a `vaginalBleeding` identifier alongside `menstrualFlow`. **D-14 locks `menstrualFlow`.** `menstrualFlow` is **not** marked deprecated in current docs and is the type Apple's Cycle Tracking has historically populated. See Open Question 1 — the planner should add a one-line read of BOTH types only if cheap; otherwise honor D-14 as written. `[CITED: developer.apple.com/.../menstrualFlow]` `[ASSUMED]` that `menstrualFlow` remains populated on iOS 18 devices. |
| Anchor in `UserDefaults` | Anchor in a SwiftData `@Model` | `UserDefaults` (like `ReminderSettings`/`SyncMode`) keeps the cursor OUT of CloudKit and avoids syncing a device-specific cursor across devices (each device should re-backfill its own view). See Open Question 2. |

**Installation:** No SPM packages. HealthKit + HealthKitUI are system frameworks linked by adding the **HealthKit capability** (entitlement) in `project.yml` / Signing & Capabilities, plus the Info.plist usage string. No `npm`/`pip`/`cargo` equivalent.

**Version verification:** HealthKit is a system framework — there is no registry version to `npm view`. API availability was confirmed against Apple's documentation (see Sources). Sleep-stage enum availability is version-flagged in the Code Examples section.

## Package Legitimacy Audit

> Phase 9 installs **no external packages**. All frameworks are first-party Apple system frameworks (HealthKit, HealthKitUI) linked via the HealthKit capability. No registry packages → no slopsquatting surface.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| HealthKit | Apple system framework | — | — | Apple | OK | First-party; no install |
| HealthKitUI | Apple system framework | — | — | Apple | OK | First-party; no install (optional) |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

> Community tools surfaced during research (e.g. `HealthKitSampleGenerator`, `HealthKitExtensions`) are **NOT recommended** — they add a dependency and conflict with the project's near-zero-dependency / privacy-first mandate. The fixture seam (below) is hand-rolled in-repo and is the standard testability pattern here.

## Architecture Patterns

### System Architecture Diagram

```
                          ┌─────────────────────────────────────────────┐
   User taps "Connect     │  Settings "Connect Apple Health" (D-07)      │
   sleep from Health"     │  + contextual connect affordances on the     │
   (contextual, D-05)     │  sleep/cycle/activity/food surfaces          │
          │               └───────────────────┬─────────────────────────┘
          ▼                                   │
   ┌──────────────────────┐                   │
   │ In-app RATIONALE     │  (D-06: read-only, on-device/private iCloud, │
   │ screen (ClaimsPolicy │   optional — "Continue")                     │
   │ copy)                │                                              │
   └─────────┬────────────┘                                              │
             │ Continue                                                  │
             ▼                                                           │
   ┌───────────────────────────────────────────────────────────────────┴──┐
   │  HealthImportService  (app-side, isolated; NOT in EndoInsights)        │
   │  ── protocol HealthDataReading (injected; fixture impl for tests) ──   │
   │                                                                        │
   │  requestAuthorization(domain) ──► HKHealthStore.requestAuthorization   │  ← system sheet
   │                                    (toShare: [], read: [type])         │
   │                                                                        │
   │  pull(domain) ──► HKAnchoredObjectQuery(since: persistedAnchor)        │
   │       │              ├─ first grant: 365-day bounded predicate (D-13)  │
   │       │              └─ subsequent: incremental from anchor (D-09)     │
   │       │                                                                │
   │       ▼ returns Sendable value structs (NEVER HKSample objects)        │
   │  [ImportedSampleValue]  +  newAnchor ──► persist anchor (D-09)         │
   └───────────────────────────────────────┬────────────────────────────────┘
                                            │ map value→@Model
                                            ▼
   ┌────────────────────────────────────────────────────────────────────────┐
   │  SwiftData store:  ImportedSample @Model  (SchemaV6, additive, D-01)     │
   │  (separate rows; NEVER mutates manual SymptomReading/FactorReading/…)    │
   └───────────────────────────────────────┬────────────────────────────────┘
                                            │
                                            ▼
   ┌────────────────────────────────────────────────────────────────────────┐
   │  Repository.daySnapshots()  — MANUAL-WINS MERGE (D-02/D-04)              │
   │  per day, per field:  manual value present? → use it                    │
   │                       else imported present? → use it (+ source flag)   │
   │                       else → nil  (absence stays nil, NEVER 0 — SC3)     │
   └───────────────────────────────────────┬────────────────────────────────┘
                                            │ [DaySnapshot]  (Sendable)
                                            ▼
   ┌────────────────────────────────────────────────────────────────────────┐
   │  InsightsCoordinator.dataChanged()  (Phase 8, D-12)                      │
   │  debounced → InsightsEngine off-main → cache → dashboard                 │
   └────────────────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure
```
Sources/EndoApp/
├── Health/                          # NEW — app-side HealthKit boundary (NOT in EndoInsights)
│   ├── HealthDataReading.swift      # protocol seam (real + fixture impls)
│   ├── HealthImportService.swift    # isolated wrapper over HKHealthStore; async pull/auth
│   ├── HealthDomain.swift           # enum: .cycle/.sleep/.workouts/.nutrition (read-type sets)
│   ├── ImportedSampleValue.swift    # Sendable value structs returned by the service
│   ├── HealthSampleMapping.swift    # HKWorkoutActivityType→ActivityType, sleep-stage→SleepQuality,
│   │                                #   menstrual flow→FlowIntensity, dietary→FoodTag presence
│   └── HealthAnchorStore.swift      # per-type HKQueryAnchor persistence (UserDefaults/NSKeyedArchiver)
├── Persistence/
│   ├── SchemaV6.swift               # NEW — additive: V5 set + ImportedSample (clone SchemaV5)
│   ├── MigrationPlan.swift          # ADD v5toV6 lightweight stage
│   ├── ModelContainer+Config.swift  # BUMP schema to SchemaV6
│   └── Repository.swift             # EXTEND: importedSamples CRUD + manual-wins merge in daySnapshots()
├── Features/
│   ├── Settings/SettingsView.swift  # ADD "Connect Apple Health" section + "Refresh from Health" (D-07/D-10)
│   └── Health/                       # NEW — rationale screen + connect affordances + provenance badge
│       ├── HealthRationaleView.swift
│       └── HealthSourceBadge.swift   # "from Apple Health" label (D-03)
└── Resources/
    ├── Info.plist                    # ADD NSHealthShareUsageDescription (read-only; NO update key)
    └── EndoApp.entitlements          # ADD com.apple.developer.healthkit (+ access array)
```

### Pattern 1: Isolate the non-Sendable HKHealthStore behind an async value-returning seam
**What:** `HKHealthStore` is not `Sendable` and HealthKit's completion handlers return non-`Sendable` `HKSample` arrays. Wrap the store in an isolation domain (a global actor or a dedicated `actor`) and expose `async` methods that return **plain Sendable structs** mapped from the samples *inside* the isolation domain. Nothing `HK*` crosses the boundary.
**When to use:** Always, for every HealthKit touch — this is what makes the rest of the app (and the tests) Swift-6-clean.
**Example:** see Code Examples → "Anchored query wrapped as async returning Sendable values."

### Pattern 2: Protocol-inject the reader, select fixture impl by launch arg (mirrors -uitest-reset)
**What:** Define `protocol HealthDataReading`. The real impl wraps `HKHealthStore`; a `FixtureHealthDataReading` returns deterministic in-memory samples. `AppDependencies` picks the fixture when a `-uitest-health-*` launch arg is present (exactly like `-uitest-reset`/`-uitest-seed-insights` at AppDependencies.swift:46–55,127). This makes the import flow E2E-testable on the Simulator with no real Health data and no real authorization sheet.
**When to use:** For all CI/UI tests and unit tests of the import → merge → recompute path.

### Pattern 3: Manual-wins, per-field, nil-honest merge in daySnapshots()
**What:** Extend `makeDaySnapshot` so each field resolves: manual value (from `SymptomReading`/`FactorReading`/`BleedingEvent`) takes priority; only when the manual value is **absent** is the imported value substituted; if neither exists the field stays `nil` (never 0). Stamp a per-field "source = .appleHealth" flag so the UI can render the D-03 badge. The merge is in the SAME projection function that already enforces nil≠0 (Repository.swift:618 `makeDaySnapshot`), so the honesty contract is inherited.
**When to use:** The single merge point — do NOT merge anywhere else.

### Anti-Patterns to Avoid
- **Reading `authorizationStatus(for:)` to decide whether to show data.** For read types it cannot tell denied from empty (Pitfall 1). Use it ONLY to decide whether to show the in-app rationale (notDetermined) vs. the deep-link-to-Settings affordance — and even then treat any "we already asked" state as "attempt the read; show nothing gracefully if empty."
- **Letting `HKSample`/`HKHealthStore` cross into `EndoInsights` or into a `@Sendable` closure.** Breaks the package boundary and the Swift 6 concurrency model. Map to value types inside the isolation domain.
- **Coercing a denied/empty Health read to 0 or to a "no symptoms" reading.** Violates SC3. Absence stays `nil`.
- **A `.custom` migration stage for SchemaV6.** Custom `willMigrate`/`didMigrate` hooks DO NOT FIRE under CloudKit (MigrationPlan.swift:10–13). SchemaV6 is purely additive → `.lightweight` only.
- **Requesting `NSHealthUpdateUsageDescription` / write/share types.** Write-back is PLAT-01, deferred. Read-only: `toShare: []`, `NSHealthShareUsageDescription` ONLY.
- **A second mega-prompt at launch.** D-05 requires per-domain contextual requests.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Incremental "what changed since last sync" | A timestamp-diff scanner over `HKSampleQuery` | `HKAnchoredObjectQuery` + `HKQueryAnchor` | The anchor is HealthKit's own resumable cursor; it returns inserts AND deletions and survives across launches when persisted. `[CITED: developer.apple.com/.../HKAnchoredObjectQuery]` |
| Persisting the anchor | Custom serialization of internal anchor state | `NSKeyedArchiver`/`NSKeyedUnarchiver` (anchor conforms to `NSSecureCoding`) | `HKQueryAnchor` implements `NSSecureCoding`; archive to `Data`, store in `UserDefaults`. `[VERIFIED: Apple Developer Forums (DevFright/forum thread 13942) cross-checked with NSSecureCoding conformance]` |
| Sleep nightly aggregation | A bespoke sleep-stage state machine from scratch | Sum `asleepValues` segments per night; map total hours + dominant/quality heuristic to `SleepQuality` | `HKCategoryValueSleepAnalysis.asleepValues` (iOS 16+) already groups the asleep stages; aggregate those. `[CITED: developer.apple.com/.../HKCategoryValueSleepAnalysis]` |
| Day bucketing of samples | Epoch math | `Calendar.startOfDay(for:)` (as the Repository already does everywhere) | The codebase standard; DST/timezone-safe. |
| Detecting "user denied read" | Probing status / inferring from query failure | Treat denied == empty; degrade identically | Apple deliberately hides read status (Pitfall 1). |

**Key insight:** Almost everything tricky here (deltas, deletions, resumable cursor, secure anchor coding, sleep-stage grouping) is already provided by HealthKit. The only genuinely bespoke logic is the **mapping tables** (HK enums → app enums) and the **manual-wins merge** — both pure, value-type, unit-testable functions that belong app-side, not in the package.

## Runtime State Inventory

> Phase 9 is additive/greenfield for HealthKit (no rename/refactor of existing runtime state). One forward-looking item applies — the anchor cursor — included for completeness.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | New `ImportedSample` rows (SwiftData, CloudKit-synced). No existing data is renamed or migrated. | New `@Model` + lightweight SchemaV6 migration (additive). |
| Live service config | HealthKit authorization state is owned by iOS (the Health app / Settings), NOT by this app — the app cannot read or set it programmatically beyond the request. | None — by design; handle via deep-link-to-Settings (D-08). |
| OS-registered state | The HealthKit **capability/entitlement** + **Info.plist usage string** are OS-recognized at install/launch; absence → crash on first HK access. | Add entitlement + `NSHealthShareUsageDescription` (read-only). |
| Secrets/env vars | None. No keys, no env vars. | None. |
| Build artifacts | `EndoApp.xcodeproj` is regenerated by `xcodegen generate` from `project.yml`; the HealthKit capability must be expressed in `project.yml` (entitlement file + capability) so the generated project carries it. | Add to `project.yml` + entitlements file; regenerate. |

**Nothing found in category:** Secrets/env vars — None (verified: no `.env`, no SOPS in repo; HealthKit needs no secrets).

## Common Pitfalls

### Pitfall 1: Read-authorization status is intentionally uninformative (the SC3 anchor)
**What goes wrong:** Developers call `authorizationStatus(for:)` for a read type, see `.sharingDenied`/`.notDetermined`, and branch UI on it — or worse, render "no data" as a real zero.
**Why it happens:** Apple hides read status for privacy: an app must not be able to infer what health data a user has by probing permissions. `denied`, `not determined`, and `no data` are **indistinguishable** for reads. `[CITED: developer.apple.com/.../authorizationStatus(for:)]`
**How to avoid:** Treat denied and empty **identically** — attempt the read; if it returns nothing, contribute nothing to `[DaySnapshot]` (leave fields `nil`). Use status ONLY to choose the *entry affordance* (notDetermined → show rationale; "already asked" → deep-link to Settings, D-08). NEVER coerce absence to 0 (SC3).
**Warning signs:** A test asserting "denied shows X" that depends on distinguishing denied from empty; a snapshot field that becomes 0 after a denied import.

### Pitfall 2: Swift 6 "task-isolated value … strongly transferred" on query handlers
**What goes wrong:** Building `HKAnchoredObjectQuery` with a results handler `(query, samples, deletedSamples, newAnchor, error)` errors under Swift 6 strict concurrency because the tuple of non-`Sendable` results is captured across isolation. `[VERIFIED: Apple Developer Forums thread 763998; project builds under SWIFT_VERSION 6.0]`
**Why it happens:** `HKHealthStore` and `HKSample` are non-`Sendable`; the handler runs on an arbitrary queue and the captured values cross into your isolation domain.
**How to avoid:** Wrap the query in `withCheckedThrowingContinuation` **inside** the isolation domain (actor / global-actor-isolated service), and map `HKSample` → `Sendable` value structs **before** resuming the continuation. Only `Sendable` values escape. The `HealthImportService` actor is that domain.
**Warning signs:** Compiler diagnostics mentioning "transferred"/"task-isolated"; tempting `@unchecked Sendable` band-aids (avoid).

### Pitfall 3: A `.custom` SchemaV6 migration silently never runs under CloudKit
**What goes wrong:** Putting backfill/transform logic in a `.custom` stage; it never executes for synced users.
**Why it happens:** `willMigrate`/`didMigrate` hooks don't fire once CloudKit mirroring is active (the project documents this at MigrationPlan.swift:10–13, Apple Forums 742899/744491/748049).
**How to avoid:** SchemaV6 is purely additive (one new `ImportedSample` model) → `.lightweight` ONLY. No backfill is needed; imports populate `ImportedSample` at runtime, not via migration.
**Warning signs:** Any data-transform code in a migration stage; a V5→V6 stage that isn't `.lightweight`.

### Pitfall 4: ImportedSample not CloudKit-safe → sync breaks silently
**What goes wrong:** Adding `@Attribute(.unique)` or a non-optional/undefaulted property to `ImportedSample`, which CloudKit rejects.
**Why it happens:** CloudKit private-DB mirroring requires every property optional-or-defaulted and forbids unique constraints (the project's locked rule, see CLAUDE.md "What NOT to Use"; enforced across SchemaV1–V5).
**How to avoid:** Clone the `CachedInsight`/`DismissedInsight` shape: app-generated `UUID` id, all properties optional or defaulted, no `.unique`, idempotent upsert keyed by an app-generated dedupe key (e.g. HK sample UUID stored as a `String`).
**Warning signs:** A new `+1 survival test` that passes locally but the model has a non-optional column; CloudKit "mirroring" errors at runtime on a real device.

### Pitfall 5: Duplicate imports on re-pull / cross-device
**What goes wrong:** Re-running the pull (or a second device syncing) inserts duplicate `ImportedSample` rows for the same HK sample.
**Why it happens:** Anchored queries can re-deliver on a fresh anchor; CloudKit can mirror the same import from two devices.
**How to avoid:** Store HealthKit's stable per-sample `UUID` (`HKSample.uuid`) as a dedupe key on `ImportedSample`; upsert-by-key on insert (like the `setMedicationMarker` "exists?" guard). Also honor anchored-query **deletions** (`HKDeletedObject`) by removing the matching `ImportedSample`.
**Warning signs:** Day counts doubling after a manual "Refresh from Health"; the merge showing the same imported value twice.

## Code Examples

> The HealthKit-API specifics below are CITED to Apple documentation; treat exact method signatures as guidance to confirm against the iOS 18 SDK in Xcode 26 at implementation time. The structural patterns (isolation, value mapping, manual-wins) are the load-bearing parts.

### Read-type sets per domain (D-14/D-15/D-16)
```swift
// Source: developer.apple.com/documentation/healthkit (HKObjectType / HKCategoryType / HKQuantityType / HKWorkoutType)
import HealthKit

enum HealthDomain: CaseIterable {
    case cycle, sleep, workouts, nutrition

    var readTypes: Set<HKObjectType> {
        switch self {
        case .cycle:
            // D-14: menstrual flow ONLY (defer basal temp / cervical mucus).
            return [HKCategoryType(.menstrualFlow)]
        case .sleep:
            // D-15: sleep analysis → nightly hours + coarse quality.
            return [HKCategoryType(.sleepAnalysis)]
        case .workouts:
            // D-15: HKWorkout → ActivityType + minutes.
            return [HKObjectType.workoutType()]
        case .nutrition:
            // D-16: meal-event PRESENCE (dietary energy as the "ate that day" signal), NOT micronutrients.
            return [HKQuantityType(.dietaryEnergyConsumed)]
        }
    }
}
```

### Authorization: rationale FIRST, then the system sheet (D-06), read-only (toShare: [])
```swift
// Source: developer.apple.com/.../requestAuthorization(toShare:read:)  (async overload, iOS 15+)
// Called ONLY after the in-app HealthRationaleView "Continue" (D-06). Read-only: toShare is empty.
func requestAuthorization(for domain: HealthDomain) async throws {
    guard HKHealthStore.isHealthDataAvailable() else { return }     // iPad/Mac etc. → no-op, app stays usable
    try await store.requestAuthorization(toShare: [], read: domain.readTypes)
    // NOTE: a successful return does NOT mean "granted" for reads — status is hidden (Pitfall 1).
    // Do not branch on authorizationStatus(for:) for reads. Just attempt the pull next.
}
```

### Anchored query wrapped as async returning Sendable values (Pitfalls 2 & 5)
```swift
// Source: developer.apple.com/.../HKAnchoredObjectQuery + Apple Forums (anchor NSSecureCoding persistence).
// The store + samples never leave this isolation domain; only `Sendable` value structs escape.
struct ImportedSampleValue: Sendable, Equatable {
    let hkUUID: UUID          // dedupe key (HKSample.uuid) — Pitfall 5
    let day: Date             // Calendar.startOfDay
    let domain: HealthDomainRaw
    let payload: ImportedPayload   // value-typed: flow / sleepHours+quality / activityType+minutes / ateThatDay
}

func pull(_ domain: HealthDomain, since anchor: HKQueryAnchor?) async throws
    -> (values: [ImportedSampleValue], deleted: [UUID], newAnchor: HKQueryAnchor?) {

    let sampleType = domain.sampleType
    // First grant (anchor == nil) → bounded 365-day backfill (D-13); else incremental from the anchor (D-09).
    let predicate: NSPredicate? = anchor == nil
        ? HKQuery.predicateForSamples(withStart: Calendar.current.date(byAdding: .day, value: -365, to: .now),
                                      end: nil)
        : nil

    return try await withCheckedThrowingContinuation { cont in
        let query = HKAnchoredObjectQuery(
            type: sampleType, predicate: predicate,
            anchor: anchor, limit: HKObjectQueryNoLimit
        ) { _, samples, deleted, newAnchor, error in
            if let error { cont.resume(throwing: error); return }
            // MAP TO VALUE TYPES HERE — inside the handler, before resuming. No HKSample escapes.
            let values = (samples ?? []).compactMap(Self.mapToValue)   // pure mapping (see tables below)
            let deletedUUIDs = (deleted ?? []).map(\.uuid)
            cont.resume(returning: (values, deletedUUIDs, newAnchor))
        }
        store.execute(query)
    }
}
```

### Sleep-stage availability (version-flag the iOS 16+ cases) — D-15
```swift
// Source: developer.apple.com/.../HKCategoryValueSleepAnalysis  (asleepValues iOS 16+; inBed/awake iOS 8)
// Sum asleep segments per night → hours; derive a coarse SleepQuality. asleepValues already groups the
// asleep stages (asleepUnspecified/asleepCore/asleepDeep/asleepREM). inBed/awake are NOT "asleep".
func nightlyAsleepHours(_ samples: [SleepSegmentValue]) -> Double {
    samples
        .filter { HKCategoryValueSleepAnalysis.asleepValues.map(\.rawValue).contains($0.value) }
        .reduce(0) { $0 + $1.duration } / 3600.0
}
// Map hours (+ optional fragmentation) → SleepQuality.{poor|okay|good}. Thresholds = Claude's discretion.
```

### Manual-wins merge in daySnapshots() (D-02/D-04, SC3)
```swift
// Source: in-repo pattern — extends Repository.makeDaySnapshot (Repository.swift:618). Per FIELD:
//   manual present? use manual.  else imported present? use imported (+ source=.appleHealth, D-03).
//   else nil  (NEVER 0 — the existing nil≠0 honesty contract, SC3).
let sleepHours = manual.sleepHours ?? imported.sleepHours          // manual wins
let sleepSource: ValueSource = manual.sleepHours != nil ? .manual
                              : (imported.sleepHours != nil ? .appleHealth : .none)
// A denied/empty import → imported.* all nil → fields stay nil → engine emits no fabricated "no symptoms".
```

### Workout activity-type mapping (D-15) — illustrative, pure & unit-tested
```swift
// Source: developer.apple.com/.../HKWorkoutActivityType  (map common types; everything else → .other)
func activityType(from hk: HKWorkoutActivityType) -> ActivityType {
    switch hk {
    case .walking, .hiking:                          return .walking
    case .running, .cycling, .swimming,
         .highIntensityIntervalTraining, .rowing:    return .cardio
    case .traditionalStrengthTraining,
         .functionalStrengthTraining, .coreTraining: return .strength
    case .yoga, .flexibility, .pilates, .mindAndBody:return .yogaStretching
    default:                                         return .other     // explicit fallback (D-15)
    }
}
// Workout minutes = workout.duration / 60.
```

### Entitlement + Info.plist (read-only)
```xml
<!-- EndoApp.entitlements — ADD (read-only HealthKit; NO health-records, NO write) -->
<key>com.apple.developer.healthkit</key>
<true/>
<key>com.apple.developer.healthkit.access</key>
<array/>   <!-- empty: standard HealthKit; clinical-records would go here, not needed -->

<!-- Info.plist — ADD read usage string ONLY (NSHealthUpdateUsageDescription is OMITTED: read-only) -->
<key>NSHealthShareUsageDescription</key>
<string>{ClaimsPolicy-audited copy: read-only import of cycle, sleep, workouts, and meal days
to reduce double-logging; stays on your device and in your private iCloud; entirely optional.}</string>
```

## State of the Art

| Old Approach | Current Approach (iOS 18 / Xcode 26) | When Changed | Impact |
|--------------|--------------------------------------|--------------|--------|
| `HKObjectType.categoryType(forIdentifier:)?` force-unwrap | `HKCategoryType(.menstrualFlow)` / `HKQuantityType(.dietaryEnergyConsumed)` typed initializers | iOS 16+ | Non-failable, cleaner; no optional dance. `[CITED: developer.apple.com/documentation/healthkit]` |
| Completion-handler `requestAuthorization(toShare:read:completion:)` | `async` `requestAuthorization(toShare:read:)` | iOS 15+ | Composes with the actor-isolated service; no continuation needed for auth. `[CITED]` |
| Coarse sleep (`inBed`/`asleep`) | Sleep stages `asleepCore`/`asleepDeep`/`asleepREM` + `asleepValues` set | iOS 16 | Richer nightly aggregation; but D-15 only needs hours + coarse quality, so the stage set is a convenience, not a requirement. `[CITED]` |
| Pre-Swift-6 free capture of `HKSample` in handlers | Isolate the store; map to `Sendable` values before escaping | Swift 6 language mode | Mandatory here — the project builds in Swift 6 mode. `[VERIFIED: Forums 763998 + project SWIFT_VERSION 6.0]` |

**Deprecated/outdated:**
- Probing read authorization to gate UI — never reliable for reads; doubly emphasized for the SC3 honesty contract.
- `HealthKitSampleGenerator` / third-party HK helper pods — rejected (dependency + privacy mandate); use the in-repo fixture seam.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `HKCategoryTypeIdentifier.menstrualFlow` is NOT deprecated on iOS 18 and remains populated by Apple's Cycle Tracking (vs. needing `vaginalBleeding`). | Standard Stack / Open Q1 | LOW–MED: if `menstrualFlow` is empty on iOS 18 devices, cycle import returns nothing (graceful — app still usable; SC3 holds). Planner can cheaply read BOTH types to de-risk. Honors D-14 as written either way. |
| A2 | Reading `HKQuantityType(.dietaryEnergyConsumed)` presence is a sufficient proxy for "ate that day" to map to a `FoodTag` presence signal (D-16). | Code Examples (nutrition) | LOW: if energy samples are sparse, nutrition import is thin — but D-16 is presence-only and the app stays fully usable; the exact dietary type(s) read are Claude's discretion. |
| A3 | `HKQueryAnchor` `NSSecureCoding` → archive to `Data` in `UserDefaults` is the right anchor-persistence mechanism (vs. a SwiftData row). | Don't Hand-Roll / Open Q2 | LOW: both work; `UserDefaults` keeps the device-specific cursor out of CloudKit (preferred). |
| A4 | The exact iOS 18 SDK signatures for `HKAnchoredObjectQuery` init and `requestAuthorization` async overload match the cited docs. | Code Examples | LOW: structural pattern is stable; confirm signatures against Xcode 26 SDK at implementation (standard practice). |

**If this table is empty:** it is not — A1–A4 above need confirmation, but none block planning; each degrades gracefully and honors the locked decisions.

## Open Questions (RESOLVED)

> All three resolved during planning; the Phase 9 plans commit to these answers.
> **Q1 → RESOLVED:** `menstrualFlow` only per locked D-14; `vaginalBleeding` is NOT added (kept as a future belt-and-suspenders option, not implemented).
> **Q2 → RESOLVED:** anchor persisted in `UserDefaults` (NSKeyedArchiver), per-`HealthDomain` — out of the CloudKit-synced store (locked by 09-02 T1).
> **Q3 → RESOLVED:** `ImportedSample` IS CloudKit-safe per D-01, with dedupe-by-`HKSample.uuid` so cross-device mirroring collapses harmlessly (locked by 09-01 T3 + 09-02 T1).

1. **`menstrualFlow` vs `vaginalBleeding` on iOS 18 (D-14 is locked to `menstrualFlow`).**
   - What we know: both identifiers exist; `menstrualFlow` shows no deprecation banner in current docs and has historically been the Cycle-Tracking-populated type. `[CITED: developer.apple.com/.../menstrualFlow]`
   - What's unclear: whether iOS 18 devices writing via the modern Health UI populate `menstrualFlow`, `vaginalBleeding`, or both.
   - Recommendation: honor D-14 (`menstrualFlow`). OPTIONALLY, if cheap, add `vaginalBleeding` to the cycle read set too and union the results — this is purely additive and risks nothing. Surface to the user only if the team wants belt-and-suspenders cycle coverage.

2. **Anchor persistence location: `UserDefaults` vs a SwiftData `@Model`.**
   - What we know: anchors are device-specific cursors; CloudKit-syncing a cursor across devices would mean each device skips data the OTHER device already pulled into its own anchor window.
   - What's unclear: whether the team wants per-device independent backfill (favors `UserDefaults`) or a shared cursor.
   - Recommendation: `UserDefaults` (NSKeyedArchiver), keyed per `HealthDomain` — keeps the cursor out of the synced store and lets each device backfill its own 365-day view. (Matches how `SyncMode`/`ReminderSettings` keep device/runtime state out of CloudKit.)

3. **Does `ImportedSample` itself sync via CloudKit, or stay local-only?**
   - What we know: D-01 says "CloudKit-safe `ImportedSample`" — implying it CAN sync. But imported samples are re-derivable from HealthKit on each device, and syncing them risks cross-device duplication (Pitfall 5).
   - Recommendation: make it CloudKit-safe (honor D-01 literally) AND dedupe by `HKSample.uuid` so cross-device mirroring collapses harmlessly (the same append-only/dedupe posture as `DismissedInsight`). The planner should confirm this is the intended reading of D-01.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| HealthKit framework (SDK) | All of Phase 9 | ✓ (Xcode 26 / iOS 18 SDK) | iOS 18 SDK | — |
| HealthKit capability/entitlement | Runtime HK access | Must be ADDED to `project.yml` + entitlements | — | App stays usable WITHOUT it (manual logging); but HK access crashes without the usage string, so the string is mandatory once the capability ships. |
| Real iOS device with Health data | Realistic end-to-end HK validation | ✗ (CI = Simulator) | — | **Fixture seam** (Pattern 2) makes the import→merge→recompute path fully testable on Simulator; real-device read is a human-verify checkpoint at the phase gate (like Phase 4's HUMAN-UAT). |
| Apple Developer team (signing) | Device install of an entitled build | ✗ (no DEVELOPMENT_TEAM set; see project.yml:41) | — | Simulator builds are unsigned; the HealthKit capability is inert/untestable on a real device until a team is set — deferred to the same enrollment gate as CloudKit (D-09/Phase 4). |

**Missing dependencies with no fallback:** None that block planning. Real-device HK validation is gated on Apple Developer enrollment (same posture as Phase 4 CloudKit) and is a human-verify checkpoint, NOT an executable CI blocker.

**Missing dependencies with fallback:**
- Real Health data → fixture seam (Simulator-testable).
- Signing/team → unsigned Simulator builds for CI; device path deferred to enrollment.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (unit) + XCUITest (E2E), Xcode 26 — existing setup |
| Config file | `project.yml` (XcodeGen); test targets `EndoAppTests`, `EndoAppUITests` (project.yml:49–66) |
| Quick run command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppTests/HealthImportTests` |
| Full suite command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DATA-05 | Imported cycle/sleep/workout/nutrition values flow into `[DaySnapshot]` and reach the engine (SC1) | unit | `…-only-testing:EndoAppTests/HealthImportMergeTests/importedFillsAbsentManualFields` | ❌ Wave 0 |
| DATA-05 | **Manual always wins** — a manual value is never displaced by an imported one (D-02) | unit | `…/HealthImportMergeTests/manualWinsOverImported` | ❌ Wave 0 |
| DATA-05 | **Denied/empty read never reads as 0 / "no symptoms"** (SC3, D-04) | unit | `…/HealthImportMergeTests/emptyImportLeavesFieldsNil` | ❌ Wave 0 |
| DATA-05 | HK sample → app value mapping (sleep hours+quality, workout type+minutes, flow, meal presence) (D-15/D-16) | unit | `…/HealthSampleMappingTests` | ❌ Wave 0 |
| DATA-05 | Anchored incremental pull: dedupe by `HKSample.uuid`; honor deletions (Pitfall 5) | unit | `…/HealthAnchorPullTests/dedupesAndAppliesDeletions` (via `HealthDataReading` fixture) | ❌ Wave 0 |
| DATA-05 | `ImportedSample` SchemaV5→V6 lightweight migration preserves V5 data + new model queryable (D-01) | unit | `…/SchemaMigrationTests/v5Store_survivesLightweightMigrationToV6` (extend existing pattern) | ❌ Wave 0 |
| DATA-05 | After import, `InsightsCoordinator.dataChanged()` is invoked (D-12) | unit | `…/HealthImportServiceTests/callsDataChangedAfterImport` (injected spy) | ❌ Wave 0 |
| DATA-05 / SC2 | Contextual connect → in-app rationale screen shown BEFORE any system sheet (D-05/D-06) | E2E | `…-only-testing:EndoAppUITests/HealthConnectUITests/rationalePrecedesSystemSheet` (fixture seam; no real sheet) | ❌ Wave 0 |
| DATA-05 / SC3 | App fully usable with HealthKit denied; no "no symptoms" rendered on empty import (D-08) | E2E | `…/HealthConnectUITests/deniedHealthKeepsAppUsable` (launch arg `-uitest-health-denied`) | ❌ Wave 0 |
| DATA-05 / D-03 | Imported values render the "from Apple Health" provenance badge | E2E | `…/HealthConnectUITests/importedValueShowsAppleHealthBadge` (launch arg `-uitest-health-seed`) | ❌ Wave 0 |
| DATA-05 / D-10 | "Refresh from Health" action triggers a pull and recompute | E2E/unit | `…/HealthConnectUITests/refreshFromHealthPullsAndRecomputes` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `…-only-testing:EndoAppTests/HealthImportMergeTests` (+ the unit suite touched by the task)
- **Per wave merge:** full `EndoAppTests` unit suite + the `EndoInsights` package boundary lint (proves HealthKit did NOT leak into the package)
- **Phase gate:** full suite green (`EndoAppTests` + `EndoAppUITests` + `EndoInsights/EndoInsightsTests`) before `/gsd-verify-work`; real-device HK read is a human-verify checkpoint (deferred, like Phase 4 HUMAN-UAT).

### Wave 0 Gaps
- [ ] `Tests/EndoAppTests/HealthImportMergeTests.swift` — manual-wins + nil-honest merge (DATA-05, D-02/D-04, SC3) — the make-or-break suite.
- [ ] `Tests/EndoAppTests/HealthSampleMappingTests.swift` — pure HK→app enum/value mapping (D-15/D-16).
- [ ] `Tests/EndoAppTests/HealthAnchorPullTests.swift` — anchored pull via `HealthDataReading` fixture: dedupe + deletions (Pitfall 5).
- [ ] `Tests/EndoAppTests/HealthImportServiceTests.swift` — service orchestration + `dataChanged()` spy (D-12).
- [ ] Extend `Tests/EndoAppTests/SchemaMigrationTests.swift` — add the V5→V6 survival test (clone the existing V1→V2/…/V4→V5 pattern).
- [ ] `Tests/EndoAppUITests/HealthConnectUITests.swift` — rationale-before-sheet, denied-stays-usable, provenance badge, refresh (DATA-05, SC2/SC3, D-03/D-08/D-10).
- [ ] Fixture seam: `FixtureHealthDataReading` + `AppDependencies` launch-arg wiring (`-uitest-health-seed`, `-uitest-health-denied`, `-uitest-health-empty`) — mirrors `-uitest-reset`/`-uitest-seed-insights` (AppDependencies.swift:46–55,127).
- [ ] `EndoInsights` boundary lint: assert no `import HealthKit` anywhere in the package (extend the existing Foundation-only boundary lint from Phase 5).

## Security Domain

> `security_enforcement: true`, ASVS L1, block on high. Health data is GDPR Art. 9 special-category data — the threat surface is disclosure + integrity of imported health values.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No accounts/login (iCloud identity is implicit); HealthKit auth is OS-owned. |
| V3 Session Management | no | No sessions. |
| V4 Access Control | yes | Two gates: app-level **consent** (ONBD-01, `assertConsented()` — the import service MUST be consent-gated like every Repository call) AND OS-level **HealthKit read authorization** (per-type, requested after the rationale). Imported data must never be processed pre-consent. |
| V5 Input Validation | yes | Defensive decode of every HK sample (unknown enum → app `.other`/dropped, never crash — mirror `FoodTag(raw:)`/`ActivityType(raw:)`); clamp/bound derived numeric values (hours, minutes); dedupe-by-UUID; reject malformed samples silently (never fabricate). |
| V6 Cryptography | no (use system) | No custom crypto. At-rest protection rides on iOS Data Protection + CloudKit private DB (existing posture); the anchor blob is non-sensitive (a cursor). |
| V7 Error Handling & Logging | yes | **Never log health values** (mirror InsightsCoordinator T-08-05: log only error TYPE, never sample contents). |
| V8 Data Protection | yes | Imported health values stay on-device + private iCloud; no third-party egress; provenance labelled (D-03). Read-only — no write-back surface (PLAT-01 deferred). |

### Known Threat Patterns for {iOS HealthKit read import + SwiftData/CloudKit}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Health-data disclosure (logging/diagnostics leak imported values) | Information Disclosure | No-content logging (error TYPE only); no analytics SDKs; values never cross into `print`/crash reports. |
| Authorization bypass (processing HK data before app consent) | Elevation of Privilege | `assertConsented()` on the import service (defence-in-depth, like Repository); UI gated behind the `.consented` AppGate branch. |
| Read-status probing to infer user health state | Information Disclosure | Honor Apple's hidden read status — treat denied==empty; never branch UI to reveal "user has/denied this data" (Pitfall 1). |
| Provenance integrity (imported value silently masquerades as manual, or overwrites manual) | Tampering / Repudiation | Separate `ImportedSample` store (D-01, never mutates manual rows); per-field source flag + "from Apple Health" badge (D-03); manual-wins merge (D-02) is the only blend point. |
| Anchor / sample tampering (corrupt anchor or sample causes crash or bad data) | Tampering / DoS | Defensive decode everywhere (V5); a corrupt anchor → discard and re-backfill (never crash); dedupe-by-UUID; honor deletions. |
| In-memory test seams leaking into production | Tampering | Fixture `HealthDataReading` selected ONLY under `-uitest-health-*` launch args, paired with the in-memory store (T-08-06 pattern) so seed/denied fixtures can NEVER touch a real store or real HealthKit. |
| SC3 honesty violation (empty/denied read renders as "no symptoms") | (Product-integrity / trust) | nil-honest merge — absence stays `nil`, never 0; the engine's absence-by-omission (Phase 8) then emits nothing. The make-or-break test. |

## Sources

### Primary (HIGH confidence)
- developer.apple.com/documentation/healthkit/hkhealthstore/authorizationstatus(for:) — read-status is hidden; denied==not-determined==no-data for reads.
- developer.apple.com/documentation/healthkit/hkcategoryvaluesleepanalysis — sleep-stage cases + `asleepValues` (iOS 16+) / inBed/awake (iOS 8).
- developer.apple.com/documentation/healthkit/hkcategorytypeidentifier/menstrualflow — `menstrualFlow` identifier (no deprecation banner found).
- developer.apple.com/documentation/healthkit (HKAnchoredObjectQuery, HKWorkout, HKWorkoutActivityType, typed `HKCategoryType`/`HKQuantityType` initializers, requestAuthorization async overload).
- developer.apple.com/documentation/healthkit/accessing-sample-data-in-the-simulator — Simulator sample-data limits.
- In-repo (HIGH): Repository.swift (daySnapshots/makeDaySnapshot merge point, nil≠0), SchemaV5.swift + MigrationPlan.swift + ModelContainer+Config.swift (additive A1 checklist), InsightsCoordinator.swift (dataChanged seam), AppDependencies.swift (launch-arg fixture pattern), domain enums (defensive `init(raw:)`), CLAUDE.md (CloudKit-safe model rules, "what NOT to use").

### Secondary (MEDIUM confidence)
- Apple Developer Forums 763998 — Swift 6 "task-isolated value … strongly transferred" with anchored-query handlers (cross-checked with the project building under Swift 6).
- Apple Developer Forums (DevFright write-up; thread 13942) — `HKQueryAnchor` NSSecureCoding persistence via NSKeyedArchiver.

### Tertiary (LOW confidence)
- Community tutorials (createwithswift.com, brightdigit.com, medium articles) — corroborating async-auth + workout/sleep read patterns; used only to confirm shapes already CITED to Apple docs. NOT relied on for any locked claim.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all first-party; only the `menstrualFlow` vs `vaginalBleeding` nuance is flagged (A1/Open Q1) and degrades gracefully.
- Architecture: HIGH — directly mirrors proven Phase 5–8 seams (boundary, additive schema, coordinator, fixture launch-arg).
- Pitfalls: HIGH — read-status hiding (CITED Apple), Swift 6 transfer (VERIFIED forums + project mode), CloudKit `.custom`/model-safety rules (in-repo proven).

**Research date:** 2026-06-09
**Valid until:** 2026-07-09 (HealthKit APIs are stable; re-confirm exact iOS 18 SDK signatures in Xcode 26 at implementation — standard practice, low churn).
