# Phase 5: Snapshot Contract + Descriptive Trends Engine - Pattern Map

**Mapped:** 2026-06-08
**Files analyzed:** 11 (9 new, 2 modified)
**Analogs found:** 10 / 11 (1 role-match-only: `Package.swift`)

Platform: native iOS / SwiftUI / SwiftData, XcodeGen. This phase creates a **new Foundation-only local SPM package `EndoInsights`** + a `Sendable` value contract + a pure `TrendsEngine`. Every pattern to mirror already exists in the app's `Sources/EndoApp/Domain/` layer — the package files are clean lifts of those proven templates, minus the store.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `EndoInsights/Sources/EndoInsights/DaySnapshot.swift` (+ `SymptomPoint`/`PainPoint`) | model (value contract) | transform (read-model) | `Sources/EndoApp/Domain/FactorDay.swift` | exact |
| `EndoInsights/Sources/EndoInsights/Insight.swift` (+ `InsightKind`/`TrendWindow`/`InsightConfidence`/`InsightPoint`) | model (result value) | transform (engine output) | `Sources/EndoApp/Domain/CycleSummary.swift` | exact |
| `EndoInsights/Sources/EndoInsights/TrendsEngine.swift` | service (pure engine) | transform / batch | `Sources/EndoApp/Domain/CycleMath.swift` | exact |
| `EndoInsights/Package.swift` | config (SPM manifest) | — | RESEARCH recipe (no in-repo analog) | role-match |
| `project.yml` (MODIFY) | config (build wiring) | — | existing `project.yml` target/scheme block | exact |
| `EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift` | test (fixture builders) | — | `Tests/EndoAppTests/CycleMathTests.swift` (fixture helpers) | exact |
| `EndoInsights/Tests/EndoInsightsTests/TrendsEngineTests.swift` | test | — | `Tests/EndoAppTests/CycleMathTests.swift` | exact |
| `EndoInsights/Tests/EndoInsightsTests/SuppressedFixtureTests.swift` | test (SC3) | — | `CycleMathTests` suppressed case | exact |
| `EndoInsights/Tests/EndoInsightsTests/BoundaryLintTests.swift` | test (D-12 lint) | file-I/O | RESEARCH Form A (no in-repo analog) | role-match |
| `Sources/EndoApp/Persistence/Repository.swift` (MODIFY) — add `daySnapshots()` | service (store projection) | transform (CRUD read) | `factorDays()` + `makeFactorDay` + `bleedingDays()` in same file | exact |
| 7 Domain enums (Option C move + re-export) | model (enum) | — | `Sources/EndoApp/Domain/SymptomType.swift` et al. | exact (⚠ HIGHEST RISK) |

## Pattern Assignments

### `EndoInsights/Sources/EndoInsights/DaySnapshot.swift` (model, transform)

**Analog:** `Sources/EndoApp/Domain/FactorDay.swift`

**Imports + type declaration pattern** (FactorDay.swift lines 15, 21):
```swift
import Foundation   // ONLY import — no SwiftData, no SwiftUI (SC1 boundary)

struct FactorDay: Sendable, Equatable {   // package version: make `public`
```
Mirror exactly: `public struct DaySnapshot: Sendable, Equatable`. The package needs `public` on every type/member (cross-module); `FactorDay` is internal because it lives in the app module — this is the one mechanical delta when lifting.

**Honesty-contract field pattern** (FactorDay.swift lines 52-55):
```swift
/// Stress on a 0...10 scale. `nil` = NOT logged (absence ≠ 0, D-07).
var stress: Int?
/// Overall day mood on a 1...5 valence scale. `nil` = NOT logged (absence ≠ 0/neutral, D-08).
var mood: Int?
```
Carry `stress: Int?` / `mood: Int?` verbatim — the engine NEVER coerces nil→0 (D-03). Add `flow: FlowIntensity?` (nil = no bleeding that day), `clots: Bool`, `hasNote: Bool`, `suppressed: Bool`, plus `symptoms: [SymptomPoint]` and `pain: [PainPoint]`. `SymptomPoint`/`PainPoint` are tiny `public struct ...: Sendable, Equatable` value types referencing the enums (per RESEARCH "Concrete Contract Design").

**Header-comment honesty posture to replicate** (FactorDay.swift lines 9-14): the "Foundation only — NO import SwiftData/SwiftUI" + "HONESTY CONTRACT: absence ≠ 0" doc block. Re-author the same posture for `DaySnapshot`.

---

### `EndoInsights/Sources/EndoInsights/Insight.swift` (model, transform)

**Analog:** `Sources/EndoApp/Domain/CycleSummary.swift`

**Int-backed Comparable confidence enum** (CycleSummary.swift lines 18-27) — `InsightConfidence` is a direct lift:
```swift
enum Confidence: Int, Sendable, Comparable, CaseIterable {
    case none = 0
    case low = 1
    case medium = 2
    case high = 3
    static func < (lhs: Confidence, rhs: Confidence) -> Bool {
        lhs.rawValue < rhs.rawValue
    }
}
```
Mirror as `public enum InsightConfidence: Int, Sendable, Equatable, Comparable, CaseIterable` with the same `<`. Do NOT invent a new confidence shape — reuse this one for cross-codebase consistency (D-02 / RESEARCH note).

**Equatable + Sendable result value** (CycleSummary.swift line 30):
```swift
struct CycleSummary: Equatable, Sendable {
```
Mirror as `public struct Insight: Sendable, Equatable, Identifiable` with `id`, `kind: InsightKind`, `headline`, `detail`, `window: TrendWindow`, `confidence: InsightConfidence`, `points: [InsightPoint]`. `InsightKind`/`TrendWindow` are `String`-backed `Sendable, Equatable, CaseIterable` enums (reserve `.cyclePhase/.correlation/.recommendation/.flare` cases now per D-17).

**Static-constant honesty values pattern** (CycleSummary.swift lines 45-54): `CycleSummary.suppressed` / `.notEnoughData` as named statics. Consider a parallel `Insight` factory or a `notEnoughData(window:)` helper for the min-data-threshold case (D-08).

> **⚠ Determinism caveat (Pitfall 4):** `id: UUID` must be DERIVED deterministically (hash of kind+window+signal key), NOT `UUID()`, or `Equatable` fixture assertions break. No app analog — this is a package-specific implementation note for the planner.

---

### `EndoInsights/Sources/EndoInsights/TrendsEngine.swift` (service, transform)

**Analog:** `Sources/EndoApp/Domain/CycleMath.swift`

**Pure enum-namespace + injected `today` + injected `calendar` signature** (CycleMath.swift lines 23, 32-37):
```swift
enum CycleMath {
    static func summarize(
        bleedingDays: [Date],
        isSuppressed: Bool,
        today: Date,
        calendar: Calendar = .current
    ) -> CycleSummary {
```
Mirror exactly: `public enum TrendsEngine { public static func trends(from snapshots: [DaySnapshot], asOf today: Date, calendar: Calendar = .current) -> [Insight] }`. NO `Date.now`/`Date()`/randomness inside (RESEARCH anti-pattern).

**Suppressed short-circuit** (CycleMath.swift lines 38-39) — the SC3 proof pattern:
```swift
// 1. D-09 hard short-circuit: suppressed → emit nothing derivable (the no-phantom-cycle proof).
if isSuppressed { return .suppressed }
```
TrendsEngine gates the bleeding/cycle trend on `!suppressed && bleedingDayCount >= 1` (RESEARCH A7). A suppressed/no-bleeding stream yields NO bleeding/cycle Insight.

**DST-safe day/window math** (CycleMath.swift lines 63-65, 81-83, 147) — reuse, do NOT hand-roll epoch math:
```swift
let today0 = calendar.startOfDay(for: today)
// ...
calendar.date(byAdding: .day, value: median, to: lastStart0)!
calendar.dateComponents([.day], from: lastStart0, to: today0).day
```
Window helper: `start = calendar.date(byAdding: .day, value: -(N-1), to: startOfDay(today))!; start...end`.

**Small pure unit-testable helpers + median pattern** (CycleMath.swift lines 81-135): each helper (`periodRuns`, `consecutiveGaps`, `medianInt`, `spread`, `confidenceLevel`) is independently testable. If a trend needs a median, reuse the `medianInt` shape (lines 111-117) rather than a second implementation. Means must exclude nil from the denominator (honesty).

---

### `EndoInsights/Package.swift` (config) — role-match, NO in-repo analog

**Source:** RESEARCH "The XcodeGen Recipe" (verified against XcodeGen 2.45.4 / ProjectSpec). Lift verbatim:
```swift
// swift-tools-version:6.0
import PackageDescription
let package = Package(
    name: "EndoInsights",
    platforms: [.iOS(.v18)],                     // match app deployment target (project.yml: iOS 18.0)
    products: [ .library(name: "EndoInsights", targets: ["EndoInsights"]) ],
    targets: [
        .target(name: "EndoInsights"),           // NO dependencies = the SC1 compiler boundary
        .testTarget(name: "EndoInsightsTests", dependencies: ["EndoInsights"]),
    ]
)
```
`platforms: [.iOS(.v18)]` MUST match `project.yml`'s `deploymentTarget.iOS: "18.0"`. Zero `.package(...)` deps is the whole point (D-09).

---

### `project.yml` (MODIFY, config)

**Analog:** the existing `targets.EndoApp` + `scheme.testTargets` block (project.yml lines 21-37).

**Existing scheme block to extend** (project.yml lines 34-37):
```yaml
    scheme:
      testTargets:
        - EndoAppTests
        - EndoAppUITests
```
Three additions per RESEARCH (verified syntax):
1. New top-level `packages:` key — `EndoInsights: { path: EndoInsights }`.
2. Under `targets.EndoApp` add `dependencies: - package: EndoInsights` (note: EndoApp has no `dependencies:` key yet — add it).
3. Append to the scheme testTargets: `- package: EndoInsights/EndoInsightsTests` (the `Name/TestTarget` string form, XcodeGen ≥ 2.27.0).

Existing test-target style to mirror for reference: `EndoAppTests` is `type: bundle.unit-test` with `dependencies: - target: EndoApp` (lines 38-46) — that is the *fallback* framework/bundle shape if the `package:` scheme entry fails the verification spike.

---

### Test files (`EndoInsightsTests/*`)

**Analog:** `Tests/EndoAppTests/CycleMathTests.swift` (fixture-driven pure-engine suite).

**Fixed deterministic calendar + anchor + day-offset builders** (CycleMathTests.swift lines 18-37):
```swift
private static let calendar: Calendar = {
    var c = Calendar(identifier: .gregorian)
    c.timeZone = TimeZone(identifier: "UTC")!
    return c
}()
private static let anchor = Date(timeIntervalSince1970: 1_735_689_600)  // 2025-01-01 UTC
private static func day(_ offset: Int) -> Date {
    calendar.date(byAdding: .day, value: offset, to: anchor)!
}
```
`SnapshotFixtures.swift` lifts this DSL: `day(_:)`, `suppressedNoBleedingFixture(monthsBack:anchor:)`, `regularSymptomFixture(...)` returning `[DaySnapshot]`. Use `@Test(arguments:)` for the window × threshold matrix.

**Suite + injected-today + exact-equality assertion style** (CycleMathTests.swift lines 13-14, 38-66):
```swift
@Suite("CYCL-02/03/04 cycle math")
struct CycleMathTests {
    @Test func regularCycler_medianLength_sensibleRange() async throws {
        let summary = CycleMath.summarize(bleedingDays: days, isSuppressed: false,
                                          today: Self.day(90), calendar: Self.calendar)
        #expect(summary.hasEnoughData == true)
        let next = try #require(summary.nextPeriod)
```
`SuppressedFixtureTests` mirrors the suppressed case asserting `!insights.contains { ...bleed... }` and no `.cyclePhase` kind (RESEARCH SC3 example).

> **`BoundaryLintTests.swift` (D-12)** has no in-repo analog — use RESEARCH Form A (scan `Sources/EndoInsights/*.swift` via `#filePath`, assert every `import` is `Foundation`). Imports `Testing` + `Foundation` only. Flag `#filePath`-resolution as a verify-during-planning item.
>
> **NOTE:** the package tests must NOT spin up a `ModelContainer` — unlike `FactorSnapshotTests`. That separation (in-memory `[DaySnapshot]` only) is the SC1 point.

---

### `Sources/EndoApp/Persistence/Repository.swift` (MODIFY, service)

**Analog:** `factorDays()` + `makeFactorDay` + `bleedingDays()` in the SAME file.

**Consent gate + bucket-by-start-of-day + project pattern** (Repository.swift lines 410-419):
```swift
func factorDays() throws -> [FactorDay] {
    assertConsented()                                   // ONBD-01 — every read gates on consent
    let cal = Calendar.current
    var byDay: [Date: [SchemaV1.FactorReading]] = [:]
    for reading in try context.fetch(FetchDescriptor<SchemaV1.FactorReading>()) {
        byDay[cal.startOfDay(for: reading.occurredOn), default: []].append(reading)
    }
    return byDay.keys.sorted().map { Self.makeFactorDay(day: $0, readings: byDay[$0] ?? []) }
}
```
`daySnapshots() throws -> [DaySnapshot]` copies this skeleton: `assertConsented()` first (line 411), bucket each day-scoped row type (`SymptomReading`, `PainPoint`, `BleedingEvent`, `FactorReading`) by `cal.startOfDay`, then project. Reuse the exact factor projection from `makeFactorDay` for the carried `FactorDay` fields.

**Defensive decode in the projection** (Repository.swift lines 438-466) — `FactorKind(raw:)` / `FoodTag(raw:)` / `ActivityType(raw:)` etc. never force-unwrap; unknown raw → `.other`/nil. Honest nil≠0: `stress = reading.level` (line 461) leaves the field nil when no row exists. The `daySnapshots()` symptom/pain mapping must use the same `init(raw:)` defensive decode.

**Suppressed-flag source** (Repository.swift lines 260-265): reuse `isSuppressed()` (latest `TreatmentState` by `startedAt`) — one current value stamped onto every snapshot's `suppressed` (matches `CycleMath`'s single-value usage; per-day is the general shape but a single current value is fine for Phase 5).

**Bleeding mapping** (Repository.swift lines 252-257): a day "had flow" when `FlowIntensity(raw: event.flowRaw) != nil` — map to `DaySnapshot.flow` (richer than `BleedingDay.hadFlow: Bool`; `flow != nil` answers frequency).

**⚠ Name-clash:** `EndoInsights.PainPoint` (value struct) vs `SchemaV1.PainPoint` (@Model) are both in scope here — fully-qualify `EndoInsights.PainPoint(...)` in the mapping. Add `import EndoInsights` to the file. App-side mapping test (`Tests/EndoAppTests/DaySnapshotMappingTests.swift`) mirrors `FactorSnapshotTests` (in-memory `ModelContainer`, asserts nil≠0 + suppressed propagation + no-bleeding→`flow == nil`).

---

## Shared Patterns

### Foundation-only purity (SC1)
**Source:** every `Sources/EndoApp/Domain/*.swift` header — `import Foundation` only, explicit "NO import SwiftData/SwiftUI".
**Apply to:** ALL `EndoInsights/Sources/` files. The package's `Package.swift` (zero deps) is the compiler enforcement; `BoundaryLintTests` is belt-and-suspenders.

### Honesty: absence ≠ 0 / no nil→0 coercion
**Source:** `FactorDay.swift` lines 52-55 + `makeFactorDay` line 461 + `FactorSnapshotTests` lines 21-39.
**Apply to:** `DaySnapshot` fields, `TrendsEngine` means (nil excluded from denominator), `daySnapshots()` projection. Add a logged-0-vs-unlogged-nil fixture asserting different means.

### Suppressed short-circuit (no phantom cycle/bleeding, SC3)
**Source:** `CycleMath.summarize` lines 38-39 (`if isSuppressed { return .suppressed }`) + `CycleSummary.suppressed` static.
**Apply to:** `TrendsEngine` bleeding/cycle gate + `SuppressedFixtureTests`.

### Injected `today` + injected `Calendar` determinism
**Source:** `CycleMath.summarize` signature (lines 32-37) + `CycleMathTests` fixed UTC calendar/anchor (lines 18-37).
**Apply to:** `TrendsEngine.trends(from:asOf:calendar:)` + all package tests.

### Consent gate on every Repository read
**Source:** `Repository.swift` line 411 `assertConsented()` (used by all sibling reads).
**Apply to:** `daySnapshots()` — first statement.

### Defensive enum decode
**Source:** `SymptomType.init(raw:)` lines 50-56 (unknown/nil → `.other`).
**Apply to:** every enum mapping in `daySnapshots()` — never force-unwrap a decoded raw.

## No Analog Found

| File | Role | Reason | Planner guidance |
|------|------|--------|------------------|
| `EndoInsights/Package.swift` | config | No SPM package exists in the repo yet | Use RESEARCH "XcodeGen Recipe" verbatim |
| `EndoInsights/Tests/.../BoundaryLintTests.swift` | test/lint | No import-boundary lint exists yet | Use RESEARCH Form A (`#filePath` source scan) |

## Highest-Risk Item — flagged for the planner

**Enum sourcing (Option C: move 7 Domain enums into the package + re-export).** `SymptomType`, `BodyRegion`, `FlowIntensity`, `FoodTag`, `ActivityType`, `ActivityIntensity`, `SleepQuality` currently live in `Sources/EndoApp/Domain/*` and are referenced across **17 app source files** (grep count) — Schema mapping, the day-log UI, and copy-audit call sites. Moving them to the package is the **highest blast-radius change in the phase**:
- Analog for each enum file: `Sources/EndoApp/Domain/SymptomType.swift` (already `Sendable`, Foundation-only, `init(raw:)` defensive decode, `CaseIterable` — eligible to live in the package unchanged except adding `public`).
- The risk is NOT the enum bodies (clean lifts) — it is the **re-export ergonomics** keeping 17 existing `EndoApp` call sites compiling after the move (`@_exported import EndoInsights` vs per-enum `typealias` vs adding `import EndoInsights` everywhere). RESEARCH A2 / Open Question 2: needs a small spike.
- **Plan as an explicit task with a whole-suite-green gate** (`xcodegen generate && xcodebuild test -scheme EndoApp`). Option B (duplicate enums) is rejected — drift risk (Pitfall 5) contradicts the D-17 stable contract.

**Second-highest risk:** the `package: EndoInsights/EndoInsightsTests` scheme test target actually executing under `xcodebuild test -scheme EndoApp` on this toolchain (RESEARCH Open Question 1). Sequence a thin walking-skeleton `@Test` + a log-grep verification step BEFORE building the real engine; framework-target fallback documented in RESEARCH.

## Metadata

**Analog search scope:** `Sources/EndoApp/Domain/`, `Sources/EndoApp/Persistence/Repository.swift`, `Tests/EndoAppTests/`, `project.yml`
**Files scanned:** FactorDay.swift, CycleSummary.swift, CycleMath.swift, SymptomType.swift, Repository.swift (lines 1-130, 240-360, 403-469), project.yml, CycleMathTests.swift, FactorSnapshotTests.swift
**Pattern extraction date:** 2026-06-08
