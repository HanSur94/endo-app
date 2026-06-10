# Phase 8: Insights Integration — Coordinator, Cached Insights & Dashboard - Pattern Map

**Mapped:** 2026-06-09
**Files analyzed:** 17 (5 new logic/persistence, 4 new UI, 5 modified, 5 new test files)
**Analogs found:** 16 / 17 (the only role-gap: Swift Charts views — NO existing Chart in repo; cited from RESEARCH Pattern 5)

> Platform: native iOS / SwiftUI + Swift Charts / SwiftData (CloudKit-safe), XcodeGen. This is an integration phase — nearly every pattern already exists in-repo and should be cloned, not invented.
>
> **MAKE-OR-BREAK (flagged, read first):**
> 1. **`Insight` is NOT `Codable` yet** — `EndoInsights/.../Insight.swift` declares only `Sendable, Equatable, Identifiable`. Adding `Codable` is the prerequisite for the whole `CachedInsight` serialization path. The `internal` memberwise `init` (line 95) already exists, so the decode path needs no new random-UUID initializer — but `Codable` synthesis must be verified to preserve the stored `id` (never regenerate via `DeterministicUUID`). See entry 1.
> 2. **Swift Charts gap rendering** — NO `Chart`/`LineMark` exists anywhere in the repo (verified: zero hits). The "absence ≠ zero" honesty rule requires grouping contiguous logged runs into **distinct `series` values** so the line breaks at each gap. This is the one genuinely-new, no-analog pattern; cite RESEARCH Pattern 5 + UI-SPEC "Honest absence in charts". See entry 9.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `EndoInsights/Sources/EndoInsights/Insight.swift` (MODIFY) | model (value contract) | transform/serialize | self (additive `Codable`); enums already raw-backed | exact (additive) |
| `Sources/EndoApp/Persistence/Models/CachedInsight.swift` (NEW) | model (`@Model`) | CRUD / file-I/O | `Models/DismissedInsight.swift` + `Models/FactorReading.swift` | exact |
| `Sources/EndoApp/Persistence/SchemaV5.swift` (NEW) | config (versioned schema) | batch/migration | `SchemaV4.swift` | exact |
| `Sources/EndoApp/Persistence/MigrationPlan.swift` (MODIFY) | config (migration) | batch/migration | existing `v3toV4` stage | exact |
| `Sources/EndoApp/Persistence/ModelContainer+Config.swift` (MODIFY) | config | batch | existing `Schema(versionedSchema: SchemaV4.self)` | exact |
| `Sources/EndoApp/Persistence/Repository.swift` (MODIFY) | service (repository) | CRUD | existing `dismissInsight`/`dismissedSignalKeys`/`daySnapshots` | exact |
| `Sources/EndoApp/Features/Insights/InsightFingerprint.swift` (NEW) | utility (pure) | transform | `EndoInsights` `DeterministicUUID` (FNV folding) | role-match |
| `Sources/EndoApp/Features/Insights/CachedInsightCoding.swift` (NEW) | utility | transform/serialize | RESEARCH Repository CRUD example (JSONEncoder/Decoder) | role-match |
| `Sources/EndoApp/Features/Insights/InsightsCoordinator.swift` (NEW) | store (`@Observable @MainActor`) | event-driven (debounce) | `App/CheckInRouter.swift` (`@Observable @MainActor` + env key) | role-match |
| `Sources/EndoApp/Features/Insights/InsightsView.swift` (NEW) | component (screen) | request-response | `Features/Home/HomeView.swift` (List/Section) + `CycleSummaryView.swift` (3-state) | role-match |
| `Sources/EndoApp/Features/Insights/InsightCardView.swift` (NEW) | component (card) | request-response | `Features/Home/CycleSummaryView.swift` (state + copy surface + a11y) | role-match |
| `Sources/EndoApp/Features/Insights/InsightTrendChart.swift` (NEW) | component (chart) | streaming/transform | **NO analog** — cite RESEARCH Pattern 5 + UI-SPEC chart spec | NO ANALOG |
| `Sources/EndoApp/Features/Insights/InsightsCopy.swift` (NEW) | utility (copy surface) | — | `CycleSummaryCopy` (in `CycleSummaryView.swift`) | exact |
| `Sources/EndoApp/App/AppDependencies.swift` (MODIFY) | config (composition root) | event-driven | existing `checkInRouter` construction + env key + `-uitest-reset` branch | exact |
| `Sources/EndoApp/App/AppGate.swift` / `HomeView.swift` (MODIFY) | component (shell) | request-response | `AppGate` `.consented` branch; `HomeView` Section/CTA | role-match |
| `Tests/EndoAppTests/SchemaMigrationTests.swift` (MODIFY) | test | batch | existing `v3Store_survivesLightweightMigrationToV4` | exact |
| `Tests/EndoApp{,UI}Tests/*` (NEW) | test | unit/e2e | `LoggingFlowUITests` + Swift Testing suites | role-match |

---

## Pattern Assignments

### 1. `EndoInsights/Sources/EndoInsights/Insight.swift` (MODIFY — add `Codable`) ⚠️ MAKE-OR-BREAK

**Analog:** self. All members are already value types with raw-backed enums → free synthesis. **Additive only — keep `Equatable, Sendable, Identifiable`.**

**Current declaration** (line 16, 31, 41, 53, 65): `public enum InsightKind: String, Sendable, Equatable, CaseIterable`, `TrendWindow: String, …`, `InsightConfidence: Int, …, Comparable`, `struct InsightPoint: Sendable, Equatable`, `struct Insight: Sendable, Equatable, Identifiable`.

**Add `Codable` to all five** (enums get it free via raw value; structs via memberwise synthesis):
```swift
public enum InsightKind: String, Sendable, Equatable, Codable, CaseIterable { … }
public enum TrendWindow: String, Sendable, Equatable, Codable, CaseIterable { … }
public enum InsightConfidence: Int, Sendable, Equatable, Comparable, Codable, CaseIterable { … }
public struct InsightPoint: Sendable, Equatable, Codable { … }
public struct Insight: Sendable, Equatable, Codable, Identifiable { … }
```

**CRITICAL (Insight.swift lines 7-11, 65-67, anti-pattern in RESEARCH):** `Insight.id` is DERIVED deterministically (`DeterministicUUID.make`), NEVER `UUID()`. The synthesized `Codable` decode reads the **stored** `id` from the blob — it does NOT call `derived(...)`. That is correct and required: the round-trip must preserve the exact stored `id`. Do NOT add a custom `init(from:)` that re-derives the id. The existing `internal init` (line 95) takes an explicit `id`, which is exactly what synthesis uses.

**Round-trip test fixture** must cover every field incl. `points: [InsightPoint]`, `sampleSize`, and `isInsufficiencyNotice` (line 89). Assert `decode(encode(insight)) == insight` (Equatable, not bytes — A3).

---

### 2. `Sources/EndoApp/Persistence/Models/CachedInsight.swift` (NEW `@Model`)

**Analog:** `Sources/EndoApp/Persistence/Models/DismissedInsight.swift` (CloudKit-safe rules) + `FactorReading.swift` (flat discriminator + payload columns).

**Clone the CloudKit-safe `@Model` shape verbatim** (DismissedInsight.swift lines 23-44) — nested under `extension SchemaV1`, every property optional-or-defaulted, `id` is `UUID` NOT `.unique`, no relationships:
```swift
extension SchemaV1 {
    @Model
    final class CachedInsight {
        var id: UUID = UUID()                 // app-generated, NOT .unique (CloudKit-safe)
        var signalKey: String? = nil          // flat queryable identity (= DismissedInsight.signalKey)
        var kindRaw: String? = nil            // InsightKind.rawValue — section routing without decode
        var windowRaw: String? = nil          // TrendWindow.rawValue
        var payload: Data? = nil              // the whole Insight, Codable-encoded (entry 1)
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
**Reuse the header-comment privacy posture from DismissedInsight.swift lines 1-19** (no raw health prose; only category signalKey + the engine's gated copy in the blob).

---

### 3. `Sources/EndoApp/Persistence/SchemaV5.swift` (NEW)

**Analog:** `Sources/EndoApp/Persistence/SchemaV4.swift` (lines 18-34) — clone exactly, bump version, append the one new model.
```swift
enum SchemaV5: VersionedSchema {
    static let versionIdentifier = Schema.Version(5, 0, 0)
    static var models: [any PersistentModel.Type] {
        [SchemaV1.DayEntry.self, SchemaV1.BleedingEvent.self, SchemaV1.TreatmentState.self,
         SchemaV1.SymptomReading.self, SchemaV1.PainPoint.self, SchemaV1.FactorReading.self,
         SchemaV1.Medication.self, SchemaV1.DismissedInsight.self,
         SchemaV1.CachedInsight.self]            // the ONE new additive model
    }
}
```
Keep the SchemaV4.swift A1 entity-identity header comment (lines 1-12) — same nesting discipline applies.

---

### 4. `Sources/EndoApp/Persistence/MigrationPlan.swift` (MODIFY)

**Analog:** existing `v3toV4` stage (MigrationPlan.swift lines 17-21, 37-41). Add SchemaV5 to `schemas`, add `v4toV5` to `stages`, define a lightweight stage:
```swift
static var schemas: […] { [SchemaV1.self, SchemaV2.self, SchemaV3.self, SchemaV4.self, SchemaV5.self] }
static var stages: [MigrationStage] { [v1toV2, v2toV3, v3toV4, v4toV5] }

/// Phase-8 additive stage (CachedInsight). Lightweight; NEVER `.custom`.
static let v4toV5 = MigrationStage.lightweight(fromVersion: SchemaV4.self, toVersion: SchemaV5.self)
```
**CRITICAL (MigrationPlan.swift lines 8-11):** `.lightweight` ONLY — `.custom` hooks silently never fire under CloudKit (Phase 4 live). Purely additive ⇒ lightweight is correct AND sufficient.

---

### 5. `Sources/EndoApp/Persistence/ModelContainer+Config.swift` (MODIFY)

**Analog:** existing line 50-51. One-line bump:
```swift
let schema = Schema(versionedSchema: SchemaV5.self)   // was SchemaV4.self
```
The `EndoMigrationPlan.self` wiring (lines 78-82) and the P-2 `.none`/`.private` `cloudKitDatabase` logic are unchanged. Note: `discardUnreadableStore` (line 137) still references `SchemaV3` for URL discovery — leave as-is (URL is schema-independent) unless the planner wants to bump it for clarity.

---

### 6. `Sources/EndoApp/Persistence/Repository.swift` (MODIFY — CachedInsight CRUD)

**Analog:** existing `dismissInsight`/`dismissedSignalKeys` (Repository.swift lines 122-131) + the `assertConsented()` + `context.fetch`/`insert`/`delete`/`save` posture (lines 147-187). Repository already `import EndoInsights` (line 17).

Add to the `Repository` protocol (after line 130) and `SwiftDataRepository`:
```swift
// MARK: Phase 8 — cached-insight CRUD (D-02/D-14). Consent-gated; replace-all on recompute.
func loadCachedInsights() throws -> [Insight]
func replaceCachedInsights(_ insights: [Insight], computedAt: Date, fingerprint: String) throws
```
Implementation (mirror the `assertConsented()`-first + fetch/delete/insert/save shape):
```swift
func loadCachedInsights() throws -> [Insight] {
    assertConsented()
    return try context.fetch(FetchDescriptor<SchemaV1.CachedInsight>())
        .compactMap(\.payload)
        .compactMap { try? JSONDecoder().decode(Insight.self, from: $0) }   // V5 §Input Validation: skip corrupt
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
**Security (RESEARCH §Security V4/V8):** both methods MUST call `assertConsented()` first (every sibling read does); the blob encodes only `Insight`, never a `DaySnapshot`.

---

### 7. `Sources/EndoApp/Features/Insights/InsightFingerprint.swift` (NEW — pure)

**Analog:** `EndoInsights` `DeterministicUUID` (Insight.swift lines 163-191) — the FNV-folding, stable-across-launches discipline. Use a canonical string, NOT `Set.hashValue` (per-process randomized — Pitfall 5).
```swift
enum InsightFingerprint {
    static func make(snapshots: [DaySnapshot], dismissed: Set<String>) -> String {
        let latestDay = snapshots.map(\.day).max() ?? .distantPast
        let dismissedCanonical = dismissed.sorted().joined(separator: ",")
        return "\(snapshots.count)|\(latestDay.timeIntervalSince1970)|\(dismissedCanonical)"
    }
}
```
> Verify `DaySnapshot`'s day property name (`\.day` per Repository comment line 116/120) when planning. Test: same inputs → same fingerprint across runs; any field change flips it.

---

### 8. `Sources/EndoApp/Features/Insights/InsightsCoordinator.swift` (NEW `@Observable @MainActor`)

**Analog:** `App/CheckInRouter.swift` (entire file) — the `@MainActor @Observable final class` + `EnvironmentKey` + `assumeIsolated` default pattern. The off-main compute is new (cite RESEARCH Pattern 1).

**Copy the class+env-key shape from CheckInRouter.swift lines 16-45** (class declaration + `CheckInRouterKey: EnvironmentKey { static var defaultValue { MainActor.assumeIsolated { … } } }` + `EnvironmentValues` extension). Then add the recompute logic per RESEARCH Pattern 1 (lines 171-211):
```swift
@MainActor @Observable
final class InsightsCoordinator {
    private(set) var insights: [Insight] = []
    private(set) var isUpdating = false
    // Injectable seams (test seam — RESEARCH Wave 0): loadSnapshots / loadDismissed / persist / now / engine
    private var lastFingerprint: String?
    private var debounceTask: Task<Void, Never>?

    func dataChanged() {                          // logging/dismiss/foreground call this
        debounceTask?.cancel()
        debounceTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(400))   // D-01 coalesce
            guard !Task.isCancelled else { return }
            await recompute()
        }
    }
    func recompute() async {
        guard let snapshots = try? loadSnapshots(), let dismissed = try? loadDismissed() else { return }
        let fp = InsightFingerprint.make(snapshots: snapshots, dismissed: dismissed)
        guard fp != lastFingerprint else { return }          // cache hit → skip (D-04)
        isUpdating = true
        let asOf = now()
        let fresh = await Task.detached(priority: .userInitiated) {
            InsightsEngine.insights(from: snapshots, asOf: asOf, dismissed: dismissed)  // PURE, off-main
        }.value
        lastFingerprint = fp
        insights = fresh                                     // published on @MainActor (D-13)
        persist(fresh, asOf, fp)                             // repo.replaceCachedInsights on main ctx
        isUpdating = false
    }
}
```
**Engine call signature** (InsightsEngine.swift lines 29-34): `InsightsEngine.insights(from: [DaySnapshot], asOf: Date, dismissed: Set<String>, calendar: Calendar = .current) -> [Insight]`. It is a pure `enum` static func → trivially `Sendable`; snapshots/dismissed are `Sendable` value types → cross into `Task.detached` with no friction.
**Anti-pattern (RESEARCH):** do NOT use `@ModelActor`; do NOT run the engine on `@MainActor` (Pitfall 4 jank).
**Test seam (Wave 0):** the four closures (`loadSnapshots`/`loadDismissed`/`persist`/`now`) + an injectable engine must be init params so unit tests run with no store/clock.

---

### 9. `Sources/EndoApp/Features/Insights/InsightTrendChart.swift` (NEW Swift Charts) ⚠️ MAKE-OR-BREAK / NO ANALOG

**Analog:** NONE — `grep` confirms zero `Chart`/`LineMark`/`BarMark` in the repo. Source of truth = RESEARCH Pattern 5 + UI-SPEC "Swift Charts Specification". For a11y/copy posture, borrow `SeverityControl`'s always-visible-numeric convention and `CycleSummaryView` text-fallback discipline.

**The gap mechanism (the whole point):** group contiguous logged-day runs into distinct `series` values so the line BREAKS at each gap; never emit `value: 0` for an unlogged day; add `PointMark` per logged day.
```swift
Chart {
    ForEach(contiguousRuns) { run in                 // maximal gap-free spans
        ForEach(run.points) { p in
            LineMark(x: .value("Date", p.date), y: .value("Severity", p.value),
                     series: .value("Run", run.id))   // ← breaks the line at each gap (NOT auto)
                .accessibilityLabel(Text(p.date, format: .dateTime.month().day()))
                .accessibilityValue(Text("severity \(Int(p.value)) of 10"))
        }
    }
    ForEach(loggedPoints) { p in
        PointMark(x: .value("Date", p.date), y: .value("Severity", p.value))
    }
}
.chartXScale(domain: window.startDate ... asOf)      // window range even when sparse (UI-SPEC)
.chartYScale(domain: 0...10)                          // fixed per kind
.chartXAxis { AxisMarks { AxisValueLabel(format: .dateTime.month().day()) } }
.frame(height: 200)
// + a one-line text summary so the chart is never the sole carrier of meaning (a11y contract)
```
- **Frequency = BarMark** on absolute-date buckets; a bucket with no logged days has **NO bar** (omit), never zero-height.
- **Reduce Motion:** `.animation(reduceMotion ? nil : .default, value:)`.
- **Absolute calendar X-axis ONLY** (SC3 / D-06) — never cycle-relative.
> Open Question 1: the seed fixture must cross `HonestyGate.minSample(.trend) == 3` (HonestyGate.swift line 35); RESEARCH recommends ~14-30 logged days for a reliable rendered card. `.cyclePhase` minSample == 2, `.correlation` == 10.

---

### 10. `Sources/EndoApp/Features/Insights/InsightsView.swift` + `InsightCardView.swift` (NEW UI)

**Analog (screen):** `Features/Home/HomeView.swift` lines 37-92 — `NavigationStack { List { Section { … } } }` + `.navigationTitle` + per-row a11y ids + `.swipeActions` (lines 80-87, exactly the dismiss pattern, but role NOT `.destructive` per UI-SPEC).

**Analog (3 honest states + copy surface):** `Features/Home/CycleSummaryView.swift` — clone the `enum …Copy` auditable-strings surface (lines 26-48) as `InsightsCopy` (CopyAuditTests scans it), and the `@ViewBuilder content` 3-state switch (lines 85-105) for empty/sparse/suppressed. The suppressed→no-section pattern mirrors `home.cycleSummary.suppressed` (no phantom). A11y-id-per-state mirrors lines 93/99/103.

**Card structure** (UI-SPEC §B, kind-agnostic top→bottom): headline (`.headline`) → confidence pill (text word + SF Symbol, NEVER number, D-11) → detail (`.body`) → supporting data (chart for `.trend`, `.caption` evidence line for cyclePhase/correlation) → basis note ("Based on your logs." — reuse `CycleSummaryCopy.basisNote` posture). A11y ids: `insights.card.<signalKey>`, `.confidence`, `.chart`, `.dismiss`; dismiss as VoiceOver custom action.

**Dismiss wiring:** `.swipeActions` + `.contextMenu` → `SwiftDataRepository(context:).dismissInsight(signalKey:)` (Repository.swift line 127) → `coordinator.dataChanged()` (re-feeds `dismissedSignalKeys()`, D-10). Mirror HomeView's `SwiftDataRepository(context: modelContext)` call (HomeView.swift line 138).

---

### 11. `Sources/EndoApp/Features/Insights/InsightsCopy.swift` (NEW copy surface)

**Analog:** `CycleSummaryCopy` (CycleSummaryView.swift lines 26-48) — an `enum` of static strings + an `auditableStrings: [String]` array CopyAuditTests scans. Populate from UI-SPEC §Copywriting Contract (confidence words, section headers incl. "Possible associations", empty/sparse copy, "Seen across {n} logged days", direction words avoiding the "her" substring trap, "This isn't right", "Updating…"). Disclaimer reuses `Disclaimer.text` / `DisclaimerBanner` verbatim (Disclaimer.swift).

---

### 12. `Sources/EndoApp/App/AppDependencies.swift` (MODIFY — inject coordinator + seed arg)

**Analog:** the existing `checkInRouter` story in the SAME file — declaration (line 29), construction (line 107 `self.checkInRouter = CheckInRouter()`), and the `CheckInRouterKey: EnvironmentKey` in CheckInRouter.swift. Add a `let insightsCoordinator: InsightsCoordinator`, construct it in `init()` after the container is built (so it can capture a `SwiftDataRepository(context:)` seam), and expose via an env key.

**Seed launch arg** — clone the `-uitest-reset` branch (AppDependencies.swift lines 41-44):
```swift
let seedInsights = ProcessInfo.processInfo.arguments.contains("-uitest-seed-insights")
if seedInsights { /* insert threshold-crossing fixture into the in-memory store before UI loads */ }
```
Insert the fixture right after `self.modelContainer = …` resolves, on `ModelContext(modelContainer)`, sized to cross `HonestyGate.minSample(.trend)` (≥14-30 logged days — Open Question 1). Pair with `-uitest-reset` (in-memory store) so it never leaks.

---

### 13. `Sources/EndoApp/App/AppGate.swift` / `Features/Home/HomeView.swift` (MODIFY — Insights entry)

**Analog:** `AppGate.swift` `.consented` branch (lines 33-38). UI-SPEC §D decides a bottom `TabView` "Insights" tab (`chart.line.uptrend.xyaxis`, id `tab.insights`) wrapping the consented surface. Wrap `HomeView()` + `InsightsView()` in a `TabView` INSIDE the `.consented` case (preserve the T-01-03 invariant: health UI built only here). **Fallback** = a `NavigationLink`/CTA `Section` in `HomeView` (clone the "Log today" Section, HomeView.swift lines 59-66, id `home.insightsButton`). The coordinator is read via `@Environment(\.insightsCoordinator)` in `InsightsView`; foreground recompute via `@Environment(\.scenePhase)` → `.active` → `coordinator.dataChanged()` (Open Question 2).

---

### 14. Tests (NEW + MODIFY)

| File | Analog | Notes |
|------|--------|-------|
| `Tests/EndoAppTests/SchemaMigrationTests.swift` (MODIFY) | `v3Store_survivesLightweightMigrationToV4_withNoDataLoss` (lines 151-230) | Clone +1 version: open on-disk at `SchemaV3`-style fixture, reopen via `makeModelContainer(url:)` at V5, assert all rows survive + `CachedInsight` queryable (empty). The `url:` overload forces `.none` CloudKit (P-2, line 37). Write V4 rows incl. a `DismissedInsight`. |
| `Tests/EndoAppTests/CachedInsightRoundTripTests.swift` (NEW) | `EndoInsightsTests` Equatable fixtures + RESEARCH | `decode(encode(insight)) == insight` for a full-field fixture (points + isInsufficiencyNotice). Needs entry 1 done first. |
| `Tests/EndoAppTests/InsightsCoordinatorTests.swift` (NEW) | Swift Testing `@Test/#expect`; inject the entry-8 closures + clock | debounce coalesces N→1; fingerprint cache hit/miss; off-main publishes on main; dismiss excludes signalKey. |
| `Tests/EndoAppTests/InsightFingerprintTests.swift` (NEW) | — (pure) | stability across runs + change-detection. |
| `Tests/EndoAppTests/CopyAuditTests.swift` (MODIFY) | existing CopyAuditTests | extend to scan `InsightsCopy.auditableStrings` against `ClaimsPolicy.forbiddenVerbs`/`forbiddenGenderedTokens` + numeric-confidence scan. |
| `Tests/EndoAppUITests/InsightsDashboardUITests.swift` (NEW) | `LoggingFlowUITests.swift` lines 23-55 | `-uitest-reset` + `-uitest-seed-insights` launch args; `waitForExistence`; `saveScreenshot` → `/tmp/e2e/`. Cases: seeded→rendered insight a11y id; no-data→`insights.empty`; suppressed→assert `insights.section.cyclePhase` does NOT exist; dismiss→card gone + stays gone. |

---

## Shared Patterns

### CloudKit-safe `@Model`
**Source:** `Sources/EndoApp/Persistence/Models/DismissedInsight.swift` lines 27-43
**Apply to:** `CachedInsight`
Every property optional-or-defaulted; `id: UUID` NOT `.unique`; no relationships; nested under `extension SchemaV1`. SwiftData keys entity identity on class NAME, so additive listing under a new VersionedSchema is lightweight-compatible.

### Consent gate on every Repository method
**Source:** `Sources/EndoApp/Persistence/Repository.swift` line 148 (`assertConsented()` first in every read/write)
**Apply to:** `loadCachedInsights` / `replaceCachedInsights`

### Additive lightweight migration (never `.custom`)
**Source:** `Sources/EndoApp/Persistence/MigrationPlan.swift` lines 8-11, 37-41
**Apply to:** `v4toV5`, `SchemaV5`, ModelContainer bump, the migration test.

### `@Observable @MainActor` + `EnvironmentKey` injection
**Source:** `Sources/EndoApp/App/CheckInRouter.swift` (whole file) + `AppDependencies.swift` env-key blocks (lines 154-220)
**Apply to:** `InsightsCoordinator`

### Auditable copy surface + 3-state honesty
**Source:** `Sources/EndoApp/Features/Home/CycleSummaryView.swift` (`CycleSummaryCopy` lines 26-48; 3-state `content` lines 85-105; suppressed→no-content)
**Apply to:** `InsightsCopy`, `InsightsView`, `InsightCardView`

### Disclaimer reuse
**Source:** `Sources/EndoApp/Content/Disclaimer.swift` (`Disclaimer.text` line 13, `DisclaimerBanner` line 24, `info.circle`)
**Apply to:** the persistent dashboard disclaimer (D-12), verbatim.

### XCUITest harness
**Source:** `Tests/EndoAppUITests/LoggingFlowUITests.swift` lines 23-55 (`-uitest-reset`, `waitForExistence`, `saveScreenshot` → `/tmp/e2e/`)
**Apply to:** `InsightsDashboardUITests`, plus the new `-uitest-seed-insights` arg.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `Sources/EndoApp/Features/Insights/InsightTrendChart.swift` | component (Swift Charts) | streaming/transform | ZERO `Chart`/`LineMark`/`BarMark` anywhere in the repo (verified). Use RESEARCH Pattern 5 + UI-SPEC "Swift Charts Specification" as source of truth. The gap-via-distinct-`series` rule is the make-or-break detail. Borrow only the a11y/text-fallback posture from `SeverityControl`/`CycleSummaryView`. |

---

## Metadata

**Analog search scope:** `Sources/EndoApp/{Persistence,App,Features,Content}`, `EndoInsights/Sources/EndoInsights`, `Tests/EndoApp{,UI}Tests`
**Files scanned:** ~25 Swift files (full reads of 11 analogs; targeted greps for engine/gate/repo signatures)
**Pattern extraction date:** 2026-06-09
