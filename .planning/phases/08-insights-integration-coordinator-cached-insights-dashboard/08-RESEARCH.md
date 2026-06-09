# Phase 8: Insights Integration — Coordinator, Cached Insights & Dashboard - Research

**Researched:** 2026-06-09
**Domain:** Swift 6 strict-concurrency off-main compute · SwiftData CloudKit-safe `@Model` serialization + additive migration · Swift Charts (absolute-date axis, honest gaps, a11y) · XCUITest seeding
**Confidence:** HIGH (architecture grounded in existing repo patterns; Charts gap mechanism CITED + cross-verified)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Debounced recompute on data-change (coalesce rapid edits → recompute after a short quiet period) + on app foreground. Off the main thread.
- **D-02:** A CloudKit-safe SwiftData `CachedInsight` `@Model` (new `SchemaV5`) storing the gated insights (serialized payload: kind, headline, detail, confidence, window, points, signalKey, sampleSize) + `computedAt` + an input fingerprint. Loads instantly on open; syncs across devices (Phase 4).
- **D-03:** Off-main execution: map store → `[DaySnapshot]` (+ `dismissedSignalKeys()`) on the model context, hand the `Sendable` snapshots to the pure `InsightsEngine` in a background `Task`/detached actor, write `CachedInsight` back on the main context. The engine is `Sendable`/Foundation-only (SC2 — no UI jank).
- **D-04:** Cache invalidation by input fingerprint (log count + latest-edit timestamp + dismissed-set hash). Show cached instantly; recompute when the fingerprint changes; update the UI when recompute lands.
- **D-05:** Insights screen with sections Trends / Cycle-phase / Correlations — each a card. Only honesty-gated insights shown.
- **D-06:** Swift Charts — line (severity over time), bar (frequency) — on an absolute-date x-axis (SC3, never cycle-relative). Cycle-phase + correlation render as text + confidence cards.
- **D-07:** Honest states: empty / sparse → "not enough data yet" / suppressed → NO cycle-phase section. Accessible, color-independent.
- **D-08:** Each card: headline (non-causal) + plain-language confidence + effect/detail + supporting data points + dismiss.
- **D-09:** Per-insight "This isn't right" → `Repository.dismissInsight(signalKey:)` → card disappears + stays dismissed (syncs via Phase-6 `DismissedInsight`).
- **D-10:** Coordinator passes `dismissedSignalKeys()` into engine → dismissed excluded → recompute after dismiss.
- **D-11:** Plain-language confidence — never a false-precise number/percentage.
- **D-12:** Persistent "informational, not medical advice" disclaimer (reuse `ClaimsPolicy`/`Disclaimer`); non-causal copy throughout.
- **D-13:** `@Observable @MainActor InsightsCoordinator` holding cached insights (UI state), injected via `AppDependencies`/Environment; engine runs off-main inside it (a `Task`); results published on the main actor.
- **D-14:** `CachedInsight` in a new additive `SchemaV5` + lightweight V4→V5 migration + a `SchemaMigrationTests` V4→V5 survival case. Repository CRUD for cached insights (load / replace-all).
- **D-15:** Coordinator unit tests (debounce, cache hit/miss, off-main recompute, fingerprint invalidation) with an injected engine + clock; migration survival; XCUITest E2E (open dashboard → see rendered insights from seeded data; empty state; dismiss → card gone).
- **D-16:** Automatable: coordinator/cache/migration logic + the dashboard E2E. Manual/human_needed: Swift Charts visual quality (UI-review/screenshots) + on-device `CachedInsight` SYNC across devices (Phase-4 device dependency).
- **D-17:** Reuse: `Repository.daySnapshots()` + `dismissedSignalKeys()` + `dismissInsight(signalKey:)`; `InsightsEngine.insights(from:asOf:dismissed:)`; the `DismissedInsight` model; the CloudKit-safe `@Model` + additive-migration pattern (→ `SchemaV5`); the a11y/`ClaimsPolicy` posture; `HomeView` (add an Insights entry/tab).
- **D-18:** The engine returns `[Insight]` (`Sendable`); the coordinator serializes them into `CachedInsight` rows for persistence/sync and deserializes for the dashboard — the serialization must round-trip the `Insight` value (a round-trip test).

### Claude's Discretion
- The exact debounce interval, the `CachedInsight` payload encoding (per-row vs one blob; `Codable` vs flat columns — keep CloudKit-safe), the dashboard layout + chart styling, the fingerprint formula, and the navigation entry point are left to UI-SPEC + research/planning.
- **UI-SPEC has already decided:** navigation entry point = bottom `TabView` "Insights" tab (fallback: `HomeView` CTA); dismiss is NOT `.destructive` (gray/secondary, not red). Remaining engineering discretion (debounce interval, payload encoding, fingerprint formula) is resolved by this research below.

### Deferred Ideas (OUT OF SCOPE)
- Personalized recommendations (Phase 11) + flare early-warning (Phase 12) — new insight kinds that route through the SAME engine/gate and render in this dashboard (the card layout MUST stay kind-agnostic so they slot in).
- HealthKit import (Phase 9); doctor-ready PDF report (Phase 10).
- Advanced chart interactions (drill-down, custom ranges) → future.
</user_constraints>

---

## Summary

This is an **integration/wiring phase**, not a greenfield one — almost every dependency already exists and the research task is to choose the *concrete shapes* for three engineering-discretion items the CONTEXT left open: the **off-main coordinator**, the **CachedInsight serialization**, and the **Swift Charts gap/absolute-date rendering**. The codebase has already done the hard structural work: `InsightsEngine.insights(from:asOf:dismissed:)` is pure + `Sendable`, `DaySnapshot`/`Insight` are `Sendable`/`Equatable` Foundation value types, and the `Repository` already maps `@Model` rows → `[DaySnapshot]` + `dismissedSignalKeys()`. That means **no `@ModelActor` is needed for the compute** — the SwiftData reads happen on `@MainActor` (where the view's `modelContext` lives), the resulting `Sendable` values cross into a background `Task.detached`, the pure engine runs there, and `[Insight]` is published back on `@MainActor`. This is the minimal Swift-6-clean shape and matches the existing `@Observable @MainActor` router/store conventions exactly.

For serialization, the recommendation is a **single-blob-per-insight Codable encoding stored in flat CloudKit-safe columns** — not a flat-columns-per-field model and not one giant blob for the whole array. Each `CachedInsight` row holds one insight's identity (`signalKey`, `kind`, `window`) as queryable flat columns **plus** a `Data` blob containing the `Codable`-encoded full `Insight` (so `points`/`confidence`/`sampleSize`/`isInsufficiencyNotice` round-trip without 11 columns). **`Insight` is NOT currently `Codable`** — it's `Sendable`/`Equatable`/`Identifiable` only — so a prerequisite task is adding `Codable` conformance in the EndoInsights package (additive, the value stays `Equatable`). This keeps the `@Model` tiny, CloudKit-safe (all-optional/defaulted, no `.unique`, no relationships), and trivially round-trip-testable.

For charts, the make-or-break detail is that **Swift Charts connects every `LineMark` with the same `series` value across gaps** — it does *not* auto-break at missing dates. The honest "absence ≠ zero" rule (UI-SPEC) is satisfied by **(a)** never emitting a `value: 0` for an unlogged day and **(b)** grouping contiguous logged runs into separate `series` so the line breaks at each gap, with `PointMark`s marking actual logged days. The X-axis is an absolute calendar-date axis (`.value("Date", point.date)` + `.chartXAxis { AxisMarks(...) { AxisValueLabel(format: .dateTime.month().day()) } }`), Y-domain fixed per kind (`.chartYScale(domain: 0...10)`), per-mark `.accessibilityLabel`/`.accessibilityValue` for VoiceOver + Audio Graph, plus a one-line text summary so the chart is never the sole carrier of meaning.

**Primary recommendation:** `@Observable @MainActor final class InsightsCoordinator` reads `[DaySnapshot]`+`Set<String>`+fingerprint on the main actor, runs the pure `InsightsEngine` in a `Task.detached`, publishes `[Insight]` back on `@MainActor`, and persists each insight as a `CachedInsight` row (flat identity columns + a `Codable` `Data` blob) in additive `SchemaV5`. Charts use absolute-date axes with contiguous-run `series` grouping to render honest gaps.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Map `@Model` rows → `[DaySnapshot]`, read `dismissedSignalKeys()` | Database/Persistence (`Repository`, `@MainActor`) | — | `ModelContext` is non-`Sendable` and lives on the main actor (the view's `@Environment(\.modelContext)`); reads MUST happen there. Already implemented. |
| Compute `[Insight]` from snapshots | Pure compute (background `Task.detached`) | — | `InsightsEngine` is pure + `Sendable` + Foundation-only; off-main keeps the main thread jank-free (SC2 / D-03). |
| Hold + publish `[Insight]` UI state | Frontend state (`@Observable @MainActor InsightsCoordinator`) | — | SwiftUI observes main-actor state; results are published back on `@MainActor` (D-13). |
| Serialize/deserialize `Insight` ↔ `CachedInsight` | Persistence (Repository CRUD) | Pure (Codable in EndoInsights) | Round-trip must survive CloudKit sync; encoding logic is app-side, `Codable` conformance is package-side (D-18). |
| Cache invalidation (fingerprint) | Frontend state (coordinator) + Persistence (read counts/timestamps) | — | Fingerprint is computed from store reads; the compare/skip decision is coordinator logic (D-04). |
| Render charts/cards + honest states | Frontend (SwiftUI + Swift Charts views) | — | Pure view layer over the coordinator's `[Insight]` (D-05..D-08). |
| Dismiss → persist + re-feed engine | Persistence (`dismissInsight`) → Frontend (coordinator recompute) | — | Write on main context; recompute excludes the dismissed key (D-09/D-10). |

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Swift Concurrency (`Task`, `Task.detached`, `@MainActor`, `@Observable`) | Swift 6 / Xcode 26 | Off-main engine compute + main-actor UI state | First-party; the existing app already uses `@Observable @MainActor` (`CheckInRouter`). `[ASSUMED→VERIFIED in-repo]` |
| SwiftData (`@Model`, `Schema`, `VersionedSchema`, `SchemaMigrationPlan`, `ModelConfiguration`) | iOS 18+ | `CachedInsight` persistence + additive `SchemaV5` + CloudKit sync | The project's persistence layer; SchemaV1–V4 + `EndoMigrationPlan` already established. `[CITED: developer.apple.com/documentation/swiftdata]` |
| Swift Charts (`Chart`, `LineMark`, `BarMark`, `PointMark`, `RuleMark`) | iOS 18+ | The dashboard's trend/frequency visualizations | First-party, no SPM dependency (STACK.md: "no third-party charting"). `[CITED: developer.apple.com/documentation/charts]` |
| `Codable` (Foundation) | system | Round-trip `Insight` → `Data` blob in `CachedInsight` | Standard Swift serialization; `JSONEncoder`/`Decoder` is deterministic enough for a cache blob. `[CITED: Apple Foundation]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Swift Testing (`@Test`, `#expect`) | Xcode 26 | Coordinator/cache/migration/round-trip unit tests | All non-UI logic (the project default for new test code). |
| XCTest / XCUITest | Xcode 26 | The dashboard E2E flow | The only native UI-automation option; reuse the `-uitest-reset` harness. |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `Task.detached` running the pure engine | `@ModelActor` background context | `@ModelActor` is the right tool **only when you need a background `ModelContext`** (the SwiftData reads). Here the reads already produce `Sendable` `[DaySnapshot]` on the main actor, so a `@ModelActor` would add a second context + `PersistentIdentifier` round-trips for **zero benefit** — the compute touches no models. Use plain `Task.detached`. `[CITED: useyourloaf.com/blog/swiftdata-background-tasks]` |
| One `CachedInsight` row per insight (flat columns + Codable blob) | One row holding the whole `[Insight]` as a single blob | Single-array-blob is simpler to write but loses per-insight queryability (e.g. delete one dismissed card) and makes partial CloudKit merges all-or-nothing. Per-insight rows + replace-all-on-recompute is cleaner and matches the append/replace conventions. |
| Codable `Data` blob per insight | 11 flat columns (one per `Insight` field) | Flat-everything explodes the model, forces `points: [InsightPoint]` into a relationship or a separate encoding anyway, and adds CloudKit-optional ceremony for every field. The blob round-trips the *whole* value (incl. `points`) in one `Data` column. |

**Installation:** None — all first-party system frameworks. No SPM packages added. **No Package Legitimacy Audit required (zero external packages).**

**Version verification:** All frameworks ship with the Xcode 26 / iOS 18 SDK the project already targets (`destination 'platform=iOS Simulator,name=iPhone 16'`). No registry lookup applicable.

---

## Architecture Patterns

### System Architecture Diagram

```
                         data-changed signal (logging flows / @Query didSave / foreground)
                                              │
                                              ▼
   ┌──────────────────────────────── @MainActor ────────────────────────────────┐
   │                                                                             │
   │  InsightsCoordinator (@Observable @MainActor)                               │
   │   • holds  insights: [Insight]   (UI state, published)                      │
   │   • holds  isUpdating: Bool      (non-blocking "Updating…" affordance)      │
   │                                                                             │
   │   recompute():                                                              │
   │     1. snapshots  = repo.daySnapshots()        ─┐ reads on main ctx          │
   │        dismissed  = repo.dismissedSignalKeys()  │ (Sendable values out)      │
   │        fingerprint = makeFingerprint(...)       │                            │
   │     2. if fingerprint == lastFingerprint → SKIP (cache hit, D-04)           │
   │                                                                             │
   └──────────────┬──────────────────────────────────────────────▲──────────────┘
                  │ Sendable: [DaySnapshot], Set<String>, asOf     │ [Insight] (Sendable)
                  ▼                                                │ published on @MainActor
        ┌──────── Task.detached (background) ───────┐             │
        │  InsightsEngine.insights(from:asOf:        │─────────────┘
        │     dismissed:)  → [Insight]   (PURE)      │
        └────────────────────────────────────────────┘
                  │
                  ▼ back on @MainActor
        ┌──────────────────────────────────────────┐      ┌────────────────────────┐
        │ repo.replaceCachedInsights([Insight],     │ ───► │ CachedInsight @Model    │
        │   computedAt, fingerprint)                │      │ (SchemaV5, CloudKit-safe)│
        │   • encode each Insight → Codable Data blob│      │ syncs via Phase 4       │
        └──────────────────────────────────────────┘      └────────────────────────┘

   ON OPEN (instant load, D-04):
        repo.loadCachedInsights() → decode blobs → coordinator.insights  (render immediately,
        then recompute lands and updates in place)

   Dashboard (SwiftUI):  observes coordinator.insights → Trends/Cycle/Correlations sections →
                         per-kind card → Swift Chart (absolute date, honest gaps) → dismiss → repo.dismissInsight → recompute
```

### Recommended Project Structure
```
Sources/EndoApp/
├── Insights/                          # NEW — the coordinator + serialization
│   ├── InsightsCoordinator.swift      # @Observable @MainActor; recompute/load; injected clock+engine seam
│   ├── InsightFingerprint.swift       # the pure fingerprint formula (unit-testable)
│   └── CachedInsightCoding.swift      # Insight ↔ CachedInsight encode/decode (app-side)
├── Persistence/
│   ├── Models/CachedInsight.swift     # NEW @Model under extension SchemaV1 { … }
│   ├── SchemaV5.swift                 # NEW additive VersionedSchema (V4 set + CachedInsight)
│   ├── MigrationPlan.swift            # EDIT — add v4toV5 lightweight stage
│   ├── ModelContainer+Config.swift    # EDIT — bump Schema(versionedSchema: SchemaV5.self)
│   └── Repository.swift               # EDIT — add CachedInsight CRUD (load / replaceAll)
├── Features/Insights/                 # NEW — the dashboard UI
│   ├── InsightsDashboardView.swift    # the screen (TabView tab); sections; honest states
│   ├── InsightCardView.swift          # kind-agnostic card (headline/confidence/detail/chart/dismiss)
│   ├── InsightTrendChart.swift        # Swift Charts line/bar; absolute-date axis; honest gaps; a11y
│   └── InsightsCopy.swift             # the chrome copy surface (scanned by CopyAuditTests)
├── App/AppDependencies.swift          # EDIT — construct + inject InsightsCoordinator
└── EndoInsights (package)
    └── Sources/EndoInsights/Insight.swift  # EDIT — add Codable conformance (additive)
```

### Pattern 1: Off-main pure-engine compute (Swift 6 clean)
**What:** Read `Sendable` values on the main actor, run the pure engine off-main, publish back.
**When to use:** The coordinator's `recompute()`.
```swift
// Source: existing repo conventions (CheckInRouter @Observable @MainActor) +
//         [CITED: hackingwithswift.com/quick-start/swiftdata — only Sendable values cross actors]
@MainActor
@Observable
final class InsightsCoordinator {
    private(set) var insights: [Insight] = []
    private(set) var isUpdating = false

    private let loadSnapshots: () throws -> [DaySnapshot]      // repo seam (injectable for tests)
    private let loadDismissed: () throws -> Set<String>
    private let persist: ([Insight], Date, String) -> Void     // repo.replaceCachedInsights
    private let now: () -> Date                                 // injected clock (debounce/test seam)
    private var lastFingerprint: String?
    private var debounceTask: Task<Void, Never>?

    func dataChanged() {                      // called by logging flows / @Query didSave / foreground
        debounceTask?.cancel()
        debounceTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(400))   // coalesce rapid edits (D-01)
            guard !Task.isCancelled else { return }
            await recompute()
        }
    }

    func recompute() async {
        guard let snapshots = try? loadSnapshots(),            // MAIN actor read
              let dismissed = try? loadDismissed() else { return }
        let fp = InsightFingerprint.make(snapshots: snapshots, dismissed: dismissed)
        guard fp != lastFingerprint else { return }            // cache hit → skip (D-04)
        isUpdating = true
        let asOf = now()
        // snapshots/dismissed are Sendable value types → cross into the detached task cleanly.
        let fresh = await Task.detached(priority: .userInitiated) {
            InsightsEngine.insights(from: snapshots, asOf: asOf, dismissed: dismissed)  // PURE, off-main
        }.value
        lastFingerprint = fp
        insights = fresh                                       // published on @MainActor (D-13)
        persist(fresh, asOf, fp)                               // write CachedInsight on main ctx
        isUpdating = false
    }
}
```
**Note (Swift 6):** `snapshots` and `dismissed` are captured by the `Task.detached` closure; both are `Sendable` value types, so the compiler accepts the capture with no `sending`/`@Sendable` friction. `InsightsEngine` is an enum with a static pure func → trivially `Sendable`.

### Pattern 2: Debounce via a cancellable Task (no Combine)
**What:** Coalesce rapid edits into a single recompute after a quiet period (D-01).
**When to use:** `dataChanged()` above — cancel-and-reschedule a `Task.sleep`. Recommended interval **300–500 ms** (use 400 ms). Inject `now`/use an injectable `sleep` seam so tests can drive it deterministically without real time. Tests assert: N rapid `dataChanged()` calls → exactly **one** `recompute`.

### Pattern 3: CachedInsight — flat identity columns + Codable blob (CloudKit-safe)
```swift
// Source: existing DismissedInsight / FactorReading @Model conventions (all-optional/defaulted,
//         no .unique, no relationships, nested under extension SchemaV1) [VERIFIED in-repo]
extension SchemaV1 {
    @Model
    final class CachedInsight {
        var id: UUID = UUID()                 // app-generated, NOT .unique (CloudKit-safe)
        // Flat, queryable identity columns (so a single insight is addressable):
        var signalKey: String? = nil          // the stable dismissal/identity key
        var kindRaw: String? = nil            // InsightKind.rawValue (section routing without decode)
        var windowRaw: String? = nil          // TrendWindow.rawValue
        // The whole Insight value, Codable-encoded — round-trips points/confidence/sampleSize/notice:
        var payload: Data? = nil
        // Cache metadata (D-02/D-04):
        var computedAt: Date = Date()
        var fingerprint: String? = nil

        init(id: UUID = UUID(), signalKey: String? = nil, kindRaw: String? = nil,
             windowRaw: String? = nil, payload: Data? = nil,
             computedAt: Date = .now, fingerprint: String? = nil) {
            self.id = id; self.signalKey = signalKey; self.kindRaw = kindRaw
            self.windowRaw = windowRaw; self.payload = payload
            self.computedAt = computedAt; self.fingerprint = fingerprint
        }
    }
}
```
**Prerequisite:** add `Codable` to `Insight` (and its members `InsightKind`, `TrendWindow`, `InsightConfidence`, `InsightPoint` — the enums are `String`/`Int`-raw so synthesis is free; `InsightPoint` is a plain struct → synthesized). The internal memberwise `init` already lets the package decode without exposing a random-UUID path. This is **additive** — `Insight` stays `Equatable`/`Sendable`. The round-trip test asserts `decode(encode(insight)) == insight` for a fixture covering every field including `points` and `isInsufficiencyNotice`.

### Pattern 4: SchemaV5 additive migration (clone the V4→V5 pattern)
```swift
// Source: existing SchemaV4.swift + EndoMigrationPlan [VERIFIED in-repo]
enum SchemaV5: VersionedSchema {
    static let versionIdentifier = Schema.Version(5, 0, 0)
    static var models: [any PersistentModel.Type] {
        [SchemaV1.DayEntry.self, SchemaV1.BleedingEvent.self, SchemaV1.TreatmentState.self,
         SchemaV1.SymptomReading.self, SchemaV1.PainPoint.self, SchemaV1.FactorReading.self,
         SchemaV1.Medication.self, SchemaV1.DismissedInsight.self,
         SchemaV1.CachedInsight.self]            // the ONE new additive model
    }
}
// MigrationPlan.swift: add SchemaV5 to schemas, add:
static let v4toV5 = MigrationStage.lightweight(fromVersion: SchemaV4.self, toVersion: SchemaV5.self)
// ModelContainer+Config.swift: Schema(versionedSchema: SchemaV5.self)
```
**CRITICAL (Pitfall 3 below):** the stage MUST be `.lightweight`, NEVER `.custom` — custom `willMigrate`/`didMigrate` hooks **do not fire under CloudKit** (Phase 4 live). The migration is purely additive (one new model) so lightweight is correct AND sufficient.

### Pattern 5: Swift Charts — absolute-date axis with honest gaps
```swift
// Source: [CITED: developer.apple.com/documentation/charts/linemark/init(x:y:series:)] +
//         [VERIFIED via cross-search: same `series` value connects across gaps — must group runs]
Chart {
    ForEach(contiguousRuns) { run in                 // each run = a maximal gap-free span of logged days
        ForEach(run.points) { p in
            LineMark(
                x: .value("Date", p.date),
                y: .value("Severity", p.value),
                series: .value("Run", run.id)        // ← breaks the line at each gap (NOT auto)
            )
            .accessibilityLabel(Text(p.date, format: .dateTime.month().day()))
            .accessibilityValue(Text("severity \(Int(p.value)) of 10"))
        }
    }
    ForEach(loggedPoints) { p in                     // actual logged days = points (distinct from gap)
        PointMark(x: .value("Date", p.date), y: .value("Severity", p.value))
    }
}
.chartXScale(domain: window.startDate ... asOf)      // window range even when sparse (UI-SPEC)
.chartYScale(domain: 0...10)                          // fixed per kind (severity)
.chartXAxis { AxisMarks { AxisValueLabel(format: .dateTime.month().day()) } }
.frame(height: 200)
// + a one-line text summary above/below ("Pelvic pain severity, last 30 days — trending up.")
```
- **Bar/frequency:** `BarMark(x: .value("Date", p.date, unit: .weekOfYear), y: .value("Count", p.value))` — a bucket with no logged days has **no `BarMark`** (omit it), never a zero-height bar. Same absolute-date axis.
- **Reduce Motion:** wrap chart appearance changes so no animation runs when `accessibilityReduceMotion` is on — render the final state directly (`.animation(reduceMotion ? nil : .default, value:)`).

### Anti-Patterns to Avoid
- **Zero-filling unlogged days** (`value: 0` for missing dates) — violates "absence ≠ zero" (UI-SPEC); the engine's `DaySnapshot` is already `nil`-for-absent. Never coerce.
- **Single `series` (or no `series`) on a gapped line** — Swift Charts will draw a straight line through the gap, implying data that isn't there.
- **A `@ModelActor` for the compute** — unnecessary; the compute touches no models, only `Sendable` values.
- **`.custom` migration stage** — its hooks silently never run under CloudKit (Phase 4). Always `.lightweight`.
- **`@Attribute(.unique)` / non-optional columns on `CachedInsight`** — CloudKit rejects both; sync breaks.
- **Re-deriving `Insight.id` with `UUID()`** — the package forbids it; identity is derived from `kind`+`window`+`signalKey`. The Codable decode must preserve the stored `id`, not regenerate.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Cross-actor data transfer | A custom `@unchecked Sendable` wrapper for snapshots | The existing `Sendable` `[DaySnapshot]`/`Set<String>` | They're already value types; cross actors for free. |
| Background SwiftData read | A manual `DispatchQueue` + lock around `ModelContext` | Keep reads on `@MainActor`; only the pure compute goes off-main | `ModelContext` is non-`Sendable`; the reads already yield `Sendable` values. |
| Insight serialization | 11 flat columns + a hand-rolled `points` join table | `Codable` `Data` blob (one column) | Round-trips the whole value incl. `points`; far less surface area. |
| Debounce | Combine `debounce` + a `PassthroughSubject` | A cancellable `Task` + `Task.sleep` | No Combine dependency; the app is `@Observable`, not Combine-based; testable with an injected clock. |
| Line-gap rendering | Manually splitting the view into N separate `Chart`s | One `Chart` + `series:`-grouped contiguous runs | Idiomatic; keeps a single shared axis/scale. |
| Chart accessibility tree | A custom `AXChartDescriptor` from scratch | Per-mark `.accessibilityLabel`/`.accessibilityValue` (Swift Charts builds the AX tree + Audio Graph automatically) | Apple builds the audio-graph automatically from labelled marks. |

**Key insight:** This phase is overwhelmingly *assembly of existing, proven seams*. The only genuinely new code is the coordinator's debounce/fingerprint logic, the `CachedInsight` model+coding, and the chart views — everything else (engine, repo mapping, dismissal, migration pattern, disclaimer, a11y posture) is reuse.

---

## Runtime State Inventory

> This is a code/schema-additive phase, not a rename/refactor. Included for the schema-version + cache-invalidation surfaces.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | New `CachedInsight` rows in the SwiftData store (local + synced via CloudKit private DB once Phase 4 active). Existing V1–V4 rows are untouched (additive migration). | Migration survival test (V4→V5); replace-all-on-recompute keeps cache coherent. |
| Live service config | CloudKit schema: the new `CachedInsight` record type must be **deployed Development→Production** in the CloudKit Console before App Store release, or sync silently fails (STACK.md constraint). | Document as a release-time manual step (human_needed; not blocking dev). |
| OS-registered state | None — no Task Scheduler/launchd/background-task registrations added. | None — verified: phase adds no scheduled jobs. |
| Secrets/env vars | None — no new keys/entitlements (CloudKit container + entitlement already shipped in Phase 4). | None. |
| Build artifacts | `EndoInsights` package gains `Codable` conformance — recompiles cleanly; XcodeGen project regenerates via `xcodegen generate`. No stale artifacts. | `xcodegen generate` before build (existing workflow). |

---

## Common Pitfalls

### Pitfall 1: Swift Charts connects line marks across gaps
**What goes wrong:** A `LineMark` series with missing dates draws a straight interpolated line through the gap, fabricating "data" for days the user never logged — the exact honesty violation the UI-SPEC forbids.
**Why it happens:** Swift Charts groups by `series` value; same/absent `series` → one connected path regardless of date gaps. It does **not** auto-break on non-consecutive X values.
**How to avoid:** Group contiguous logged-day runs into distinct `series` values; never emit `value: 0` for an unlogged day. Add `PointMark`s for logged days so a single logged day still shows.
**Warning signs:** A line that slopes smoothly across a multi-day gap; a "0" point on a day with no log.

### Pitfall 2: `Insight` isn't `Codable` yet
**What goes wrong:** The serialization task assumes `JSONEncoder().encode(insight)` compiles; it doesn't — `Insight` is only `Sendable`/`Equatable`/`Identifiable`.
**Why it happens:** The package deliberately exposes only the value contract, not serialization.
**How to avoid:** First task = add `Codable` to `Insight` + its members in EndoInsights (additive, free synthesis since enums are raw-backed and members are value types). Verify the package still builds and `Insight` stays `Equatable`. Then build the `CachedInsight` coding on top.
**Warning signs:** A compile error on `encode(insight)`; a temptation to hand-map fields into columns instead.

### Pitfall 3: `.custom` migration stage silently no-ops under CloudKit
**What goes wrong:** A custom V4→V5 stage's `willMigrate`/`didMigrate` never runs once CloudKit (Phase 4) is active — any transform logic is silently skipped on synced users.
**Why it happens:** Documented SwiftData+CloudKit behavior (Apple Forums 742899/744491/748049, cited in the existing `MigrationPlan.swift`).
**How to avoid:** Keep V4→V5 purely additive (one new model) → `MigrationStage.lightweight`. NEVER `.custom`. The existing plan already enforces this rule for V1→V4.
**Warning signs:** Reaching for a custom stage to "backfill" cached insights — don't; recompute fills the cache at runtime instead.

### Pitfall 4: Recompute on the main actor (UI jank — SC2)
**What goes wrong:** Running `InsightsEngine.insights(...)` directly in `recompute()` on `@MainActor` blocks the main thread during stats crunching → dropped frames.
**Why it happens:** Forgetting to hop to `Task.detached` for the pure compute.
**How to avoid:** Only the SwiftData *reads* are on the main actor; the engine call is inside `Task.detached(priority: .userInitiated)`. Cached insights render instantly; the recompute lands asynchronously with a non-blocking "Updating…" affordance.
**Warning signs:** A spinner that blocks the whole screen; scroll hitches when data changes.

### Pitfall 5: Fingerprint that misses an edit (stale cache)
**What goes wrong:** A fingerprint of "log count only" misses *edits* that don't change the count (e.g. changing a severity), so the cache never invalidates.
**Why it happens:** Under-specified fingerprint.
**How to avoid:** Fingerprint = **log count + latest-edit timestamp + dismissed-set hash** (D-04). Concretely: `"\(snapshots.count)|\(latestModifiedDay)|\(dismissed.sorted().joined().hashValue)"` — but prefer a stable hash (e.g. SHA over a canonical string or a folded FNV like `DeterministicUUID`) over `Set.hashValue` (which is per-process-randomized and won't match across launches). Tests assert: same inputs → same fingerprint across runs; any field change → different fingerprint.

### Pitfall 6: XCUITest can't see insights without seeded data
**What goes wrong:** The E2E opens the dashboard but the engine returns zero insights (fresh `-uitest-reset` store is empty) — the test can only assert the *empty* state, not a rendered insight.
**Why it happens:** Existing UI tests build data *through the UI* (slow, and insufficient to cross the engine's honesty thresholds which need many days).
**How to avoid:** Add a **seeding launch argument** (e.g. `-uitest-seed-insights`) handled in `AppDependencies` (next to `-uitest-reset`) that inserts a deterministic, threshold-crossing fixture into the in-memory store before the UI loads. See Validation Architecture → Wave 0.
**Warning signs:** A dashboard E2E that only ever asserts `insights.empty`.

---

## Code Examples

### Fingerprint (stable across launches)
```swift
// Source: pattern lifted from EndoInsights DeterministicUUID (FNV-1a, stable, not process-randomized)
enum InsightFingerprint {
    static func make(snapshots: [DaySnapshot], dismissed: Set<String>) -> String {
        let latestDay = snapshots.map(\.day).max() ?? .distantPast
        let dismissedCanonical = dismissed.sorted().joined(separator: ",")
        // A canonical string → a deterministic digest (NOT Set.hashValue, which is per-process random).
        return "\(snapshots.count)|\(latestDay.timeIntervalSince1970)|\(dismissedCanonical)"
        // (Hash the string with a stable folder if a fixed-width fingerprint is preferred.)
    }
}
```

### Repository CRUD (load instantly / replace-all)
```swift
// Source: existing SwiftDataRepository conventions (assertConsented + context.fetch/insert/save)
func loadCachedInsights() throws -> [Insight] {
    assertConsented()
    return try context.fetch(FetchDescriptor<SchemaV1.CachedInsight>())
        .compactMap { $0.payload }
        .compactMap { try? JSONDecoder().decode(Insight.self, from: $0) }
}

func replaceCachedInsights(_ insights: [Insight], computedAt: Date, fingerprint: String) throws {
    assertConsented()
    for old in try context.fetch(FetchDescriptor<SchemaV1.CachedInsight>()) { context.delete(old) }
    for insight in insights {
        let payload = try JSONEncoder().encode(insight)
        context.insert(SchemaV1.CachedInsight(
            signalKey: insight.signalKey, kindRaw: insight.kind.rawValue,
            windowRaw: insight.window.rawValue, payload: payload,
            computedAt: computedAt, fingerprint: fingerprint))
    }
    try context.save()
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Background work via `DispatchQueue` + locks | `Task.detached` + `Sendable` value types (compiler-enforced) | Swift 6 strict concurrency | The codebase already adopts this; the coordinator follows suit. |
| Combine `@Published` + `debounce` | `@Observable` + cancellable `Task.sleep` | iOS 17 `@Observable` | Matches the app's `CheckInRouter`/`@Observable` convention; no Combine. |
| Manual `AXChartDescriptor` | Per-mark `.accessibilityLabel`/`.accessibilityValue`; auto AX-tree + Audio Graph | Swift Charts (iOS 16+) | Less code; Audio Graph "for free" from labelled marks. |

**Deprecated/outdated:**
- None relevant — all APIs are current iOS 18 / Swift 6.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `Insight` + its members gain `Codable` via free synthesis (raw-backed enums + value-type members) | Pattern 3 / Pitfall 2 | LOW — if synthesis needs help, a hand-written `Codable` is small; the round-trip test catches it. |
| A2 | A 400 ms debounce is a good default for "coalesce rapid edits" | Pattern 2 | LOW — tunable; the exact value is Claude's discretion (D-01). Tests use an injected clock so the number doesn't affect determinism. |
| A3 | `JSONEncoder`/`Decoder` round-trips `Insight` byte-stably enough for a cache (equality holds, not byte-identity) | Code Examples | LOW — the round-trip test asserts `Equatable`, not bytes; `Date`/`Double` precision is preserved by JSON for these magnitudes. |
| A4 | Swift Charts `series:`-grouped contiguous runs is the cleanest gap mechanism (vs N separate Charts) | Pattern 5 / Pitfall 1 | LOW — CITED via Apple's `LineMark/init(x:y:series:)` + cross-search; if a future iOS adds a native "skip nil" the simpler path opens up. |
| A5 | A new `-uitest-seed-insights` launch arg is the right seeding mechanism for the dashboard E2E | Pitfall 6 / Validation | MEDIUM — confirm the seed fixture crosses the engine's actual honesty thresholds (depends on `HonestyGate.minSample`); the planner must read `HonestyGate.swift` to size the fixture. |
| A6 | CloudKit `CachedInsight` record type needs Console Dev→Prod deployment at release | Runtime State Inventory | LOW — standard CloudKit constraint (STACK.md); release-time human step, not a dev blocker. |

---

## Open Questions

1. **What sample size does the seed fixture need to produce a rendered (non-withheld) insight?**
   - What we know: `HonestyGate` enforces `sampleSize >= minSample(for: kind)`; the fixture must exceed it for at least one `.trend`.
   - What's unclear: the exact `minSample` per kind (in `HonestyGate.swift` / `TrendsEngine.swift` — not read in this research).
   - Recommendation: planner reads `HonestyGate.swift` + `TrendsEngine.swift` to size the seed fixture (e.g. ~14–30 logged days of one symptom severity) so the E2E reliably gets ≥1 trend card + a chart.

2. **Foreground-trigger wiring: `scenePhase` vs an explicit call?**
   - What we know: D-01 wants recompute on app foreground.
   - What's unclear: cleanest hook in the existing `EndoAppApp`/`AppGate` shell.
   - Recommendation: observe `@Environment(\.scenePhase)` in the dashboard (or App root) → call `coordinator.dataChanged()` on `.active`. Trivial; decide at plan time.

3. **Data-changed signal source: `@Query`/`didSave` observation vs explicit calls from logging flows?**
   - What we know: the dashboard already uses the coordinator; logging happens in `DayLogEditorView`/`DailyCheckInView`/dismiss.
   - Recommendation: prefer an **explicit `coordinator.dataChanged()`** from the save/dismiss seams (deterministic, testable) over implicit `ModelContext.didSave` NotificationCenter observation (broad, harder to test). The debounce coalesces bursts either way.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Swift Charts | The dashboard | ✓ | iOS 18 SDK (Xcode 26) | — |
| SwiftData + migration | `CachedInsight`/`SchemaV5` | ✓ | iOS 18 | — |
| iPhone 16 Simulator | build/test destination | ✓ | per project `xcodebuild` invocation | — |
| CloudKit Console (Dev→Prod schema deploy) | `CachedInsight` sync at **release** | n/a in dev | — | Local-only path works without it; manual release step |
| Physical device + iCloud account | on-device `CachedInsight` SYNC verification | ✗ (Simulator) | — | Manual/human_needed (D-16) — deferred to device testing |

**Missing dependencies with no fallback:** None for development. On-device sync verification is a known human_needed item (D-16), not a dev blocker.

---

## Validation Architecture

> `workflow.nyquist_validation` is `true` — section included.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (`@Test`/`#expect`) for logic; XCTest/XCUITest for E2E (Xcode 26) |
| Config file | none — XcodeGen-generated project; `xcodegen generate` then `xcodebuild test` |
| Quick run command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppTests/InsightsCoordinatorTests` |
| Full suite command | `xcodegen generate && xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` |

### Phase Requirements → Test Map

> Integration phase — wires INST-01..04 (no new IDs). Mapped to the D-decisions they realize.

| Decision | Behavior | Test Type | Automated Command | File Exists? |
|----------|----------|-----------|-------------------|-------------|
| D-03/D-13 | Off-main recompute publishes `[Insight]` on main actor | unit | `…-only-testing:EndoAppTests/InsightsCoordinatorTests/recompute_publishesInsights` | ❌ Wave 0 |
| D-01 | N rapid `dataChanged()` → exactly one recompute (debounce) | unit (injected clock) | `…/InsightsCoordinatorTests/debounce_coalesces` | ❌ Wave 0 |
| D-04 | Same fingerprint → recompute skipped (cache hit); changed → recompute (miss) | unit | `…/InsightsCoordinatorTests/fingerprint_cacheHitMiss` | ❌ Wave 0 |
| D-04 | Fingerprint is stable across launches; any input change flips it | unit | `…/InsightFingerprintTests` | ❌ Wave 0 |
| D-02/D-18 | `Insight` → `CachedInsight` payload → `Insight` round-trips equal (all fields incl. points/notice) | unit | `…-only-testing:EndoAppTests/CachedInsightRoundTripTests` | ❌ Wave 0 |
| D-14 | V4 store survives lightweight V4→V5 migration; `CachedInsight` queryable | unit (on-disk) | `…-only-testing:EndoAppTests/SchemaMigrationTests/v4Store_survivesMigrationToV5` | ⚠️ extend existing file |
| D-10 | After dismiss, recompute excludes the dismissed signalKey | unit | `…/InsightsCoordinatorTests/dismiss_excludesFromRecompute` | ❌ Wave 0 |
| D-05/D-15 | E2E: seeded data → open dashboard → rendered insight a11y ids present | e2e | `…-only-testing:EndoAppUITests/InsightsDashboardUITests/seededData_rendersInsight` | ❌ Wave 0 |
| D-07/D-15 | E2E: empty state shown with no data (`insights.empty`) | e2e | `…/InsightsDashboardUITests/noData_showsEmptyState` | ❌ Wave 0 |
| D-07 | E2E: suppressed user → `insights.section.cyclePhase` does NOT exist (no phantom) | e2e | `…/InsightsDashboardUITests/suppressed_noCyclePhaseSection` | ❌ Wave 0 |
| D-09/D-15 | E2E: dismiss a card → card gone + stays gone | e2e | `…/InsightsDashboardUITests/dismiss_removesCard` | ❌ Wave 0 |
| D-12 | Chrome copy passes `ClaimsPolicy` (no forbidden verbs/gendered tokens/numeric confidence) | unit | `…-only-testing:EndoAppTests/CopyAuditTests` | ⚠️ extend with `InsightsCopy` |
| D-06/D-16 | Chart visual quality (gaps render as breaks; absolute-date axis) | **manual / UI-review** | screenshots in the E2E (`saveScreenshot`) → human review | human_needed |
| D-02 | On-device `CachedInsight` sync across devices | **manual / device** | — | human_needed (Phase-4 device dependency) |

### Sampling Rate
- **Per task commit:** the relevant `-only-testing:` Swift Testing suite (coordinator/fingerprint/round-trip/migration) — seconds.
- **Per wave merge:** full `EndoAppTests` + `EndoAppUITests` for the dashboard E2E.
- **Phase gate:** full suite green (`xcodegen generate && xcodebuild test …`) before `/gsd-verify-work`; UI-review of chart screenshots; device sync deferred-but-logged.

### Wave 0 Gaps
- [ ] `Tests/EndoAppTests/InsightsCoordinatorTests.swift` — debounce/cache-hit-miss/off-main/dismiss-exclusion (injected engine + clock + repo seams)
- [ ] `Tests/EndoAppTests/InsightFingerprintTests.swift` — stability + change-detection
- [ ] `Tests/EndoAppTests/CachedInsightRoundTripTests.swift` — full-field `Insight` round-trip (needs `Insight: Codable` first)
- [ ] `Tests/EndoAppTests/SchemaMigrationTests.swift` — **extend** with the V4→V5 survival case (clone the V3→V4 pattern: on-disk V4 store → reopen at V5 → assert V4 rows survive + `CachedInsight` queryable)
- [ ] `Tests/EndoAppUITests/InsightsDashboardUITests.swift` — seeded-render / empty / suppressed-absence / dismiss
- [ ] **Seeding seam:** a `-uitest-seed-insights` launch arg in `AppDependencies` that inserts a threshold-crossing fixture into the in-memory store (size per Open Question 1)
- [ ] **Test seam:** `InsightsCoordinator` must accept injectable `loadSnapshots`/`loadDismissed`/`persist`/`now`/engine closures so unit tests run without a store/clock
- [ ] Framework install: none — Swift Testing + XCUITest already in the project

---

## Security Domain

> `security_enforcement: true`, ASVS L1.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No accounts (CloudKit uses the user's iCloud identity implicitly). |
| V3 Session Management | no | No sessions. |
| V4 Access Control | yes | The consent gate (`assertConsented()` in every Repository read/write) — `CachedInsight` CRUD MUST call it, like every sibling. Dashboard reachable only inside the `.consented` branch. |
| V5 Input Validation | yes (light) | Inputs are the user's own logged values (already validated upstream); the `Codable` decode tolerates a `nil`/corrupt blob (compactMap-skip) rather than crashing. |
| V6 Cryptography | no | No new crypto; the fingerprint is an identity digest (non-security), not a secret. |
| V8 Data Protection (privacy) | yes | `CachedInsight` carries the **engine's gated `[Insight]`** — derived patterns + the non-identifying `signalKey`, NO raw health prose (the engine already strips free-text; `DaySnapshot.hasNote` is presence-only). The blob is the same category-level data, stored in the user's PRIVATE CloudKit DB only. |

### Known Threat Patterns for SwiftData/CloudKit + on-device insights

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Health prose leaking into `CachedInsight` blob | Information disclosure | The blob encodes only `Insight` (headline/detail are engine-generated, gate-audited non-causal copy + a category `signalKey` + numeric points) — NO note text. Verify the encoder serializes only `Insight`, never a `DaySnapshot`. |
| Cache read before consent | Elevation / access control | `loadCachedInsights()`/`replaceCachedInsights()` call `assertConsented()` first (mirror every sibling read); dashboard built only in `.consented` branch. |
| `.custom` migration silently skipped under CloudKit (data integrity) | Tampering/repudiation | `.lightweight` only (Pitfall 3); recompute rebuilds the cache, so a skipped migration can't corrupt derived data. |
| Stale cache surfacing a dismissed insight | Repudiation (user's "this isn't right" ignored) | Fingerprint includes the dismissed-set hash → a dismiss flips the fingerprint → recompute excludes it; the replace-all clears stale rows. |
| Numeric/false-precise confidence leaking to UI | (honesty/compliance, not classic STRIDE) | `InsightsCopy` routed through `ClaimsPolicy`; `CopyAuditTests` scans for `%`/`p =`/`r =`/numeric confidence. |

---

## Sources

### Primary (HIGH confidence)
- In-repo source (VERIFIED): `EndoInsights/.../{Insight,InsightsEngine,DaySnapshot}.swift`, `Sources/EndoApp/Persistence/{SchemaV4,MigrationPlan,ModelContainer+Config,Repository,Models/{DismissedInsight,FactorReading}}.swift`, `App/{AppDependencies,EndoAppApp,CheckInRouter}.swift`, `Features/Home/HomeView.swift`, `Content/Disclaimer.swift`, `Tests/EndoAppTests/SchemaMigrationTests.swift`, `Tests/EndoAppUITests/LoggingFlowUITests.swift` — the concrete patterns this phase clones.
- [CITED: developer.apple.com/documentation/charts] — `Chart`/`LineMark`/`BarMark`/`PointMark`, `chartXAxis`/`chartYScale`, date axis.
- [CITED: developer.apple.com/documentation/charts/linemark/init(x:y:series:)] — the `series:` parameter that separates/breaks lines.
- [CITED: developer.apple.com/documentation/swiftdata] — `VersionedSchema`/`SchemaMigrationPlan`/`ModelConfiguration`/CloudKit constraints.

### Secondary (MEDIUM confidence)
- [CITED: hackingwithswift.com/quick-start/swiftdata — "How SwiftData works with Swift concurrency"] — only `Sendable` values cross actors; `ModelContext`/models are non-`Sendable`.
- [CITED: useyourloaf.com/blog/swiftdata-background-tasks] — `@ModelActor` is for a background `ModelContext`; not needed when reads yield `Sendable` values.
- [CITED: swiftwithmajid.com/2023/02/28/mastering-charts-in-swiftui-accessibility] — `.accessibilityValue` on a mark; Swift Charts auto-builds the AX tree + Audio Graph.
- [CITED: createwithswift.com/making-charts-accessible-with-swift-charts] — chart-level AX tree is automatic.

### Tertiary (LOW confidence — cross-verified, flagged)
- WebSearch corroboration that same/absent `series` connects across gaps (multiple forum/blog hits) — corroborates the Apple `series:` API; treated as MEDIUM after cross-verify.

---

## Metadata

**Confidence breakdown:**
- Off-main coordinator shape: **HIGH** — grounded in the repo's existing `@Observable @MainActor` + `Sendable`-values + pure-engine facts; Swift-6 capture rules verified.
- CachedInsight serialization: **HIGH** — clones the in-repo CloudKit-safe `@Model` pattern; one ASSUMED (free `Codable` synthesis, A1) is low-risk and test-gated.
- SchemaV5 migration: **HIGH** — direct clone of the existing V3→V4 additive/lightweight pattern + test harness.
- Swift Charts gap/axis: **HIGH** for axis/scale/a11y (CITED Apple docs); **MEDIUM→HIGH** for the gap mechanism (CITED `series:` API + cross-search).
- Testing/seeding: **MEDIUM** — the seed-fixture sizing depends on `HonestyGate.minSample` (Open Question 1) the planner must confirm.

**Research date:** 2026-06-09
**Valid until:** 2026-07-09 (stable first-party APIs; ~30 days)
