# Architecture Research

**Domain:** Native SwiftUI iOS health-tracking app (endometriosis symptom/cycle/nutrition/activity logging + on-device insights + private CloudKit sync + HealthKit import + PDF report export)
**Researched:** 2026-06-03
**Confidence:** HIGH (SwiftData/CloudKit constraints, HealthKit query model, and architecture-pattern guidance are all corroborated by official docs + multiple credible community sources)

---

## Executive Recommendation (read this first)

**Use MV + a thin Repository/Service layer, NOT MVVM-per-screen and NOT TCA.**

- The app is an indie/solo, single-developer-sized SwiftUI app. SwiftData's `@Model` types are already observable and `@Query` already binds the store to views, so per-screen `ObservableObject` ViewModels add boilerplate with little benefit (the "MV" / model-view consensus for SwiftUI in 2025). [SwiftUI architecture sources]
- **BUT** the insights engine is the product centerpiece and must be testable in isolation. That requirement is what dictates the architecture: keep insights, HealthKit, sync, and reporting as **plain Swift modules behind protocols**, decoupled from both SwiftData and SwiftUI. The UI stays "MV" (views read SwiftData directly via `@Query` for logging screens), while the heavy logic lives in injectable services.
- TCA is overkill here (steep learning curve, boilerplate, no team to amortize it) and fights SwiftData's own observation model.

The single most important structural decision: **the insights engine consumes value-type snapshots, not `@Model` objects.** This is what makes it unit-testable without a database or UI.

---

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                      PRESENTATION (SwiftUI, "MV")                      │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌──────────┐  ┌────────┐  │
│  │  Logging │  │  Cycle   │  │  Insights │  │ Settings │  │ Report │  │
│  │  Screens │  │  Screens │  │ Dashboard │  │  /Perms  │  │ Export │  │
│  └────┬─────┘  └────┬─────┘  └─────┬─────┘  └────┬─────┘  └───┬────┘  │
│   @Query/write   @Query        reads InsightStore  perms    ShareLink │
├───────┴─────────────┴──────────────┴──────────────┴───────────┴───────┤
│                  APPLICATION / SERVICE LAYER (protocols)               │
│  ┌────────────────┐ ┌──────────────┐ ┌───────────────┐ ┌────────────┐ │
│  │  Repository    │ │  HealthKit   │ │   Insights    │ │  Reporting │ │
│  │ (CRUD facade   │ │  Import      │ │   Engine      │ │  /PDF      │ │
│  │  over store)   │ │  Service     │ │ (pure Swift)  │ │  Builder   │ │
│  └───────┬────────┘ └──────┬───────┘ └───────┬───────┘ └─────┬──────┘ │
│          │                 │                 │ snapshots     │        │
│          │ writes/reads    │ writes          │ in / insights │ reads  │
│          │                 │                 │ out           │        │
├──────────┴─────────────────┴─────────────────┴───────────────┴────────┤
│                        DATA / PERSISTENCE LAYER                        │
│  ┌─────────────────────────────────────────────┐   ┌───────────────┐  │
│  │  SwiftData ModelContainer  (local SQLite)    │   │  HealthKit    │  │
│  │  + private CloudKit sync (automatic)         │   │  (read-only   │  │
│  │  Models: SymptomEntry, CycleEvent, FoodLog,  │   │  external src)│  │
│  │  ActivityLog, ImportedSample, CachedInsight  │   └───────────────┘  │
│  └──────────────────────┬──────────────────────┘                      │
│                         │ NSPersistentCloudKitContainer under the hood │
│              ┌──────────┴──────────┐                                   │
│              │ Apple iCloud (user's │                                  │
│              │ PRIVATE CloudKit DB) │                                  │
│              └─────────────────────┘                                   │
└──────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| **SwiftData store** | Single source of truth for all user data; local persistence + automatic private CloudKit sync | `ModelContainer` configured with `cloudKitDatabase: .automatic`; `@Model` classes |
| **Repository** | Thin CRUD facade + queries that aren't trivially expressible as `@Query`; produces value-type snapshots for the engine; owns a background `ModelContext` for bulk writes | `protocol DataRepository`, default impl wraps `ModelContext` |
| **HealthKit Import Service** | Authorize, run anchored/observer queries, deduplicate, map HK samples → `ImportedSample` records; never writes user-authored data | `protocol HealthDataImporter`; `HKHealthStore`, `HKObserverQuery`, `HKAnchoredObjectQuery` |
| **Insights Engine** | Pure functions: correlation detection, trend/cycle-phase aggregation, flare prediction, recommendation generation. No I/O. | Plain Swift package; takes `[Snapshot]` value types in, returns `[Insight]` out |
| **Insights Coordinator** | Orchestrates: pull snapshots from Repository → run engine off-main → persist `CachedInsight` records | Actor or `@MainActor` service calling engine on a background task |
| **Reporting / PDF Builder** | Compose date-ranged data + charts into a doctor-ready PDF; share via `ShareLink` | `ImageRenderer` over a SwiftUI report view → PDF context |
| **Presentation** | Logging forms, cycle UI, Swift Charts dashboards, report preview; reads store via `@Query`, reads insights via injected store | SwiftUI views + `@Environment` injection |

---

## Recommended Project Structure

```
EndoApp/
├── App/
│   ├── EndoAppApp.swift          # @main; builds ModelContainer, injects services
│   └── AppDependencies.swift     # composition root: wires protocols → impls
├── Models/                       # SwiftData @Model types (the persistence schema)
│   ├── SymptomEntry.swift
│   ├── CycleEvent.swift
│   ├── FoodLog.swift
│   ├── ActivityLog.swift
│   ├── ImportedSample.swift      # HealthKit-sourced data, kept separate
│   └── CachedInsight.swift       # derived insights persisted for fast display
├── Persistence/
│   ├── ModelContainer+CloudKit.swift  # container config, schema versions
│   ├── DataRepository.swift           # protocol + SwiftData impl
│   └── Snapshots.swift                # value-type DTOs the engine consumes
├── HealthKit/
│   ├── HealthDataImporter.swift  # protocol
│   ├── HealthKitImporter.swift   # impl: queries, anchors, dedup
│   └── HealthKitMapping.swift    # HK sample → ImportedSample / Snapshot
├── Insights/                     # ← STANDALONE SWIFT PACKAGE (no UIKit/SwiftData import)
│   ├── InsightsEngine.swift      # entry: ([Snapshot]) -> InsightsResult
│   ├── Correlation/              # symptom↔factor correlation
│   ├── Trends/                   # cycle-phase aggregation, moving trends
│   ├── FlarePrediction/          # personal-history early-warning model
│   ├── Recommendations/          # rule/data-driven suggestions
│   └── Domain/                   # Snapshot, Insight, CyclePhase value types
├── Insights+App/
│   └── InsightsCoordinator.swift # bridges Repository ↔ engine ↔ CachedInsight
├── Reporting/
│   ├── ReportData.swift          # date-ranged aggregate for the PDF
│   ├── ReportView.swift          # SwiftUI layout rendered to PDF
│   └── PDFExporter.swift         # ImageRenderer → PDF
└── Features/                     # SwiftUI screens, grouped by feature
    ├── Logging/
    ├── Cycle/
    ├── InsightsDashboard/
    ├── Report/
    └── Settings/
```

### Structure Rationale

- **`Insights/` is a separate Swift package (or at minimum a folder that imports neither SwiftData nor SwiftUI).** This is the keystone: it guarantees the engine can be exercised by unit tests with hand-built `[Snapshot]` arrays, no simulator, no iCloud, no UI. If the engine ever imports `@Model` types, this property is lost.
- **`Snapshots.swift` (value-type DTOs) is the contract** between persistence and the engine. The Repository converts `@Model` rows → `Snapshot` structs (`Sendable`, `Codable`). The engine never touches the database.
- **`ImportedSample` is a distinct model from user-authored entries** so HealthKit-sourced data is traceable, de-duplicable, and never confused with manual logs (important for both insights provenance and report honesty).
- **`CachedInsight` persists engine output** so the dashboard renders instantly and insights sync across devices like any other record, rather than recomputing on every launch.

---

## Architectural Patterns

### Pattern 1: MV (Model–View) for the UI, services for logic

**What:** SwiftUI views bind directly to SwiftData via `@Query` for read and call `modelContext.insert/delete` (or a Repository method) for write. No per-screen ViewModel.
**When to use:** Logging/list/detail screens whose state is just "the data in the store." This is the 2025 SwiftUI consensus for indie-sized apps.
**Trade-offs:** + Minimal boilerplate, leverages SwiftData observation. − Complex screens (insights dashboard, report builder) still benefit from a small `@Observable` state object — use one *only there*, not everywhere.

```swift
struct SymptomLogView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \SymptomEntry.date, order: .reverse) private var entries: [SymptomEntry]
    // write directly; no ViewModel needed
}
```

### Pattern 2: Engine as a pure function over value snapshots (Hexagonal core)

**What:** `InsightsEngine.run(_ snapshots: [DaySnapshot], config: InsightsConfig) -> InsightsResult`. No database, no Date.now hidden inside, no I/O. Time and randomness are injected.
**When to use:** All analytics — correlation, trend/cycle-phase aggregation, flare prediction, recommendations.
**Trade-offs:** + Fully unit-testable, deterministic, portable (could later run in a widget/extension). − Requires an explicit mapping step from `@Model` → snapshot (a feature, not a cost).

```swift
public struct DaySnapshot: Sendable, Codable {
    public let date: Date
    public let cyclePhase: CyclePhase?
    public let symptoms: [SymptomReading]
    public let foods: [FoodTag]
    public let sleepHours: Double?
    public let activeMinutes: Double?
}
public protocol InsightsEngine { func run(_ days: [DaySnapshot]) -> InsightsResult }
```

### Pattern 3: Repository facade with a background context for bulk work

**What:** A `DataRepository` protocol exposes snapshot reads and batched writes; HealthKit import and insight caching use a **background `ModelContext`** so large imports/recomputes never block the main actor or UI `@Query` views.
**When to use:** HealthKit import, insight recomputation, report aggregation.
**Trade-offs:** + Keeps heavy work off main thread; mockable for tests. − Must respect SwiftData's actor isolation (pass `PersistentIdentifier`s across contexts, not model objects).

### Pattern 4: Observer + Anchored query pipeline for HealthKit

**What:** Initial `HKAnchoredObjectQuery` for backfill, a persisted **anchor** per type for incremental fetches, and an `HKObserverQuery` with **background delivery** to catch new samples while the app is suspended. On callback: run anchored query → upsert `ImportedSample` → call the observer completion handler.
**When to use:** Importing menstrual cycle, sleep, workouts, nutrition.
**Trade-offs:** + Avoids re-importing everything; survives termination. − Background delivery requires the HealthKit background entitlement; observer queries should be registered at launch.

---

## Data Flow

### The core flow: log → store → sync → insight → report

```
[User logs a symptom]                 [HealthKit has new sleep sample]
        │                                      │
   View writes @Model                   ObserverQuery wakes app
        ▼                                      ▼
  SwiftData ModelContext  ◄──────────  Importer runs AnchoredQuery,
        │                              maps → ImportedSample (bg context)
        ▼
  Local SQLite persisted ──► NSPersistentCloudKitContainer
        │                          └─► user's PRIVATE iCloud DB ──► other devices
        ▼
  InsightsCoordinator (triggered on save / on app-active / scheduled)
        │  1. Repository → [DaySnapshot] value types
        ▼  2. InsightsEngine.run(snapshots)  (off-main, deterministic)
   InsightsResult (correlations, trends, predicted flares, recommendations)
        │  3. persist as CachedInsight (@Model) → also syncs via CloudKit
        ▼
  Insights Dashboard reads CachedInsight via @Query → Swift Charts
        │
        ▼
  Report module aggregates ranged data + charts → ReportView
        │  ImageRenderer → PDF
        ▼
  ShareLink → doctor / Files / Mail
```

### Key Data Flows

1. **Manual entry:** View → `ModelContext.insert` → SwiftData → (async) CloudKit sync → (async) insight recompute → `CachedInsight` → dashboard.
2. **HealthKit import:** ObserverQuery → AnchoredQuery (with stored anchor) → dedup → `ImportedSample` on background context → same insight recompute path. Imported and manual data are merged into the same `DaySnapshot` for analysis but remain distinct records.
3. **Insight generation:** Repository materializes value snapshots → pure engine → result persisted, never computed inline in a view body.
4. **Report export:** User picks date range → `ReportData` aggregate → `ReportView` rendered by `ImageRenderer` to a multi-page PDF → shared.

---

## CloudKit + HealthKit Data-Model Constraints (must shape the schema from day one)

These are hard requirements, not preferences. Designing the schema wrong here forces a migration that **cannot be done safely after launch** (CloudKit's "add-only, no-rename, no-retype" rule).

### SwiftData + private CloudKit rules (HIGH confidence, official + fatbobman)

| Rule | Consequence for this app |
|------|--------------------------|
| **No `@Attribute(.unique)`** — CloudKit has no cross-device atomic uniqueness | Cannot rely on DB-enforced uniqueness. De-dup HealthKit imports in code using a stored HK `UUID`/anchor, not a unique constraint. |
| **Every attribute must be optional or have a default** | All `@Model` properties are `Optional` or `= default`. No non-optional, default-less fields. |
| **Every relationship must be optional + have an inverse** | e.g. `SymptomEntry.day ↔ DaySummary.symptoms` both optional with inverses defined. |
| **No `.deny` delete rule; relationships can't be ordered** | Use `.cascade`/`.nullify`. If order matters (e.g. pain locations), store an explicit `sortIndex` field. |
| **Only the PRIVATE CloudKit DB is supported by SwiftData** | Fits the "no accounts, user-private sync" requirement perfectly. No shared/public DB — good. |
| **Post-launch: add-only.** No deleting/renaming entities or attributes; no type changes | Version the schema (`VersionedSchema`/`SchemaMigrationPlan`) from v1. Treat the model as append-only forever. |

### Conflict resolution (MEDIUM confidence)

- `NSPersistentCloudKitContainer` (under SwiftData) resolves with **last-writer-wins at the attribute level**, and **last-writer-wins + merge at the relationship level**. SwiftData does **not** let you customize merge semantics.
- **Design implication:** model data as **append-only events rather than mutable aggregates** wherever multi-device editing of the same field is plausible. A daily symptom is one record per (day, symptom) — separate devices editing different symptoms won't clobber each other; two devices editing the *same* field on the same day is the only lossy case, which is acceptable for this single-user app. Insights are derived/recomputable, so a lost merge there self-heals on next recompute.

### HealthKit constraints (HIGH confidence)

- **Read-only integration.** The app imports cycle/sleep/workouts/nutrition; it does not write back. Keep imported data in `ImportedSample` tagged with the source HK type and the sample's HK `UUID` for idempotent upserts.
- **Authorization is per-type and opaque** — you cannot detect read-denial (Apple returns "no data" indistinguishably from "denied"). Design UI to degrade gracefully and let users re-prompt.
- **Background delivery** needs the HealthKit background entitlement; register `HKObserverQuery`s at app launch and persist an **anchor per data type** for incremental `HKAnchoredObjectQuery` fetches (don't re-scan the whole store each time).
- **Cycle data must degrade gracefully** (per PROJECT.md): irregular/suppressed cycles mean menstrual-flow samples may be sparse or absent. The cycle-phase logic must treat phase as `Optional` and never assume 28 days — this is why `DaySnapshot.cyclePhase` is optional.

---

## Suggested Build Order (dependency-driven; informs roadmap phases)

The insights engine is the product, but it is **useless without data and a clean snapshot contract.** Build the contract early, the engine against synthetic data early, and integrate last.

1. **Persistence foundation first.** Define `@Model` types under all CloudKit constraints + `VersionedSchema`. Get the `ModelContainer` + private CloudKit sync working with one or two entities. *Nothing else can be safely built until the schema obeys CloudKit rules, because the schema can't be reshaped after launch.*
2. **Manual logging UI (MV).** Symptom/cycle/food/activity entry + lists via `@Query`. Produces real data to design insights against.
3. **Snapshot contract + Insights engine (parallelizable with #2).** Define `DaySnapshot`/`Insight` value types and build the engine against **synthetic** snapshot fixtures in unit tests — no DB or UI dependency. Start with trend/cycle-phase aggregation and correlation; flare prediction and recommendations build on those.
4. **Repository mapping (`@Model` → Snapshot)** + InsightsCoordinator + `CachedInsight` persistence. This is the integration seam that connects #1/#2 to #3.
5. **Insights dashboard** (Swift Charts reading `CachedInsight`).
6. **HealthKit import service.** Adds a second data source into the *existing* snapshot pipeline — deliberately after the pipeline works with manual data, so HealthKit is additive, not foundational.
7. **PDF report export.** Reads the now-rich data + charts; last because it depends on everything upstream.

**Critical sequencing facts:**
- The **snapshot value-type contract (step 3) must precede heavy engine work** and is the single most important boundary.
- **CloudKit-compatible schema (step 1) must be right before any data ships** — it is effectively immutable post-launch.
- HealthKit (step 6) is intentionally *not* a prerequisite for insights; it feeds the same pipeline as manual logs.

---

## Scaling Considerations

This is a single-user, on-device app — "scale" means **personal data volume over years**, not concurrent users.

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Months of logging | Trivial; in-memory snapshot computation is instant. |
| Several years (thousands of days × samples) | Insight recompute should be **incremental/windowed** (recompute only affected ranges) and run off-main on a background context. Cache results in `CachedInsight`. |
| Heavy HealthKit history (years of workouts/sleep) | Anchored queries + stored anchors avoid re-import; map to daily aggregates rather than keeping every raw sample. |

### Scaling Priorities

1. **First bottleneck:** recomputing all insights on every save. Fix with debounced, windowed recompute + persisted `CachedInsight`.
2. **Second bottleneck:** CloudKit initial-sync time on a fresh device with years of records. Mitigate by keeping records lean (daily aggregates, not raw HK firehose) and by not blocking UI on sync completion.

---

## Anti-Patterns

### Anti-Pattern 1: Insights engine that imports SwiftData / `@Model`

**What people do:** Pass `@Model` objects (or `ModelContext`) directly into analysis code.
**Why it's wrong:** Couples the engine to persistence + main-actor isolation; makes unit testing require a live store; risks main-thread stalls and cross-context crashes.
**Do this instead:** Convert to `Sendable` value-type snapshots at the Repository boundary; engine is pure.

### Anti-Pattern 2: Per-screen MVVM ViewModels wrapping SwiftData

**What people do:** Add an `ObservableObject` ViewModel that re-implements fetching SwiftData already does via `@Query`.
**Why it's wrong:** Boilerplate, duplicated state, fights SwiftData observation, no testability gain (the logic worth testing is in the engine, not the view).
**Do this instead:** MV for data screens; reserve a small `@Observable` state object only for genuinely complex screens (dashboard/report).

### Anti-Pattern 3: Using `@Attribute(.unique)` or non-optional fields "to be safe"

**What people do:** Add unique constraints / required fields out of habit.
**Why it's wrong:** Breaks CloudKit sync entirely (silent sync failure) — and the fix may require a non-safe schema change after launch.
**Do this instead:** All optional/defaulted; enforce identity/dedup in code.

### Anti-Pattern 4: Treating HealthKit as the foundation of the data model

**What people do:** Build the whole model around HK types and import on the critical path.
**Why it's wrong:** HK auth is opaque/deniable, data is sparse for suppressed cycles, and it couples core logic to an external source.
**Do this instead:** Manual logging is the foundation; HealthKit is an *additive* second source funneled through the same snapshot pipeline.

### Anti-Pattern 5: Computing insights inside SwiftUI view bodies

**What people do:** Run correlation/aggregation in `body` or `.onAppear`.
**Why it's wrong:** Re-runs on every render, blocks main thread, non-deterministic to test.
**Do this instead:** Coordinator runs the engine off-main; views read persisted `CachedInsight`.

---

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| **CloudKit (private DB)** | Implicit via `ModelContainer(cloudKitDatabase: .automatic)` | No CKRecord code; obey schema rules; enable iCloud + Background Modes (remote notifications) capabilities; can't customize conflict resolution. |
| **HealthKit** | `HKHealthStore` + observer/anchored queries behind `HealthDataImporter` protocol | Read-only; per-type opaque auth; background-delivery entitlement; persist anchors; dedup via HK `UUID`. |
| **Share sheet** | `ShareLink` with generated PDF `URL` | PDF produced by `ImageRenderer`; supports multi-page via `beginPage()`. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| Persistence ↔ Insights engine | **Value-type snapshots** (one-way: snapshots in, `Insight` values out) | The testability seam. Engine never sees `@Model`. |
| Repository ↔ HealthKit importer | Importer writes `ImportedSample` via Repository's background context | Keeps import off main thread; dedup in importer. |
| Coordinator ↔ engine ↔ store | Coordinator pulls snapshots, calls engine, persists `CachedInsight` | Only place that knows about all three. |
| Views ↔ store | `@Query` (read) + `ModelContext`/Repository (write) | MV; no ViewModel layer for simple screens. |
| Reporting ↔ store | Read-only aggregate (`ReportData`) | Renders via SwiftUI + ImageRenderer; depends on nothing downstream. |

---

## Sources

- SwiftData ↔ CloudKit model rules (optionality, no-unique, inverse relationships, post-launch add-only): [fatbobman — Rules for Adapting Data Models to CloudKit](https://fatbobman.com/en/snippet/rules-for-adapting-data-models-to-cloudkit/), [Hacking with Swift — Sync SwiftData with iCloud](https://www.hackingwithswift.com/quick-start/swiftdata/how-to-sync-swiftdata-with-icloud), [Alex Logan — SwiftData meet iCloud](https://alexanderlogan.co.uk/blog/wwdc23/08-cloudkit-swift-data) (HIGH)
- Conflict resolution (last-writer-wins attribute, LWW+merge relationship; SwiftData can't customize): [Apple Developer Forums — resolve conflicts with SwiftData](https://developer.apple.com/forums/thread/751480), [CrunchyBagel — NSPersistentCloudKitContainer findings](https://crunchybagel.com/nspersistentcloudkitcontainer/) (MEDIUM)
- HealthKit observer + anchored query + background delivery pattern: [iTwenty — Read workouts using HealthKit and keep them updated](https://itwenty.me/posts/09-healthkit-workout-updates/), [topolog — HealthKit changes observing](https://dmtopolog.com/healthkit-changes-observing/), [Apple — Getting started with HealthKit (WWDC20)](https://developer.apple.com/videos/play/wwdc2020/10664/) (HIGH)
- SwiftUI architecture (MV vs MVVM vs TCA for indie apps): [DEV — MV vs MVVM in SwiftUI 2025](https://dev.to/yossabourne/mv-vs-mvvm-in-swiftui-2025-which-architecture-should-you-use-video-26nb), [Apple Developer Forums — Stop using MVVM for SwiftUI](https://developer.apple.com/forums/thread/699003), [Curated iOS — SwiftUI architecture](https://curatedios.substack.com/p/20-swiftui-architecture) (MEDIUM)
- PDF export from SwiftUI + Swift Charts via ImageRenderer: [Hacking with Swift — render a SwiftUI view to PDF](https://www.hackingwithswift.com/quick-start/swiftui/how-to-render-a-swiftui-view-to-a-pdf), [Swift with Majid — ImageRenderer in SwiftUI](https://swiftwithmajid.com/2023/04/18/imagerenderer-in-swiftui/) (HIGH)
- SwiftData framework reference: [Context7 /websites/developer_apple_swiftdata] (HIGH)

---
*Architecture research for: native SwiftUI on-device endometriosis tracking + insights app*
*Researched: 2026-06-03*
