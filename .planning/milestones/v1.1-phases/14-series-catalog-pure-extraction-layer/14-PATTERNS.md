# Phase 14: Series Catalog & Pure Extraction Layer - Pattern Map

**Mapped:** 2026-06-15
**Files analyzed:** 8 (4 sources + 4 tests, all new)
**Analogs found:** 8 / 8 (all have a strong in-package analog)

This is a PURE Foundation-only EndoInsights package phase. Every new file copies an existing
package file's playbook: pure `enum`/`struct` namespaces, `Sendable`/`Equatable` value types,
calendar-injected day math, UTC-fixed Swift Testing fixtures. No SwiftUI, no app-target file,
no `xcodegen`. The `BoundaryLintTests` Foundation-only guard already exists and must stay green.

## File Classification

| New File | Role | Data Flow | Closest Analog | Match Quality |
|----------|------|-----------|----------------|---------------|
| `EndoInsights/Sources/EndoInsights/ExploreSeries.swift` | model (enum/value catalog) | transform | `SnapshotEnums.swift` (CaseIterable domains) + `Insight.swift` (signalKey id-style) | role-match (value-type style) |
| `EndoInsights/Sources/EndoInsights/ExploreSeriesExtraction.swift` | utility (pure helper namespace) | transform | `ChartScrubSelection.swift` | exact (pure-helper playbook) |
| `EndoInsights/Sources/EndoInsights/ScatterPairing.swift` | utility (pure helper namespace) | transform / batch | `ChartScrubSelection.swift` | exact (pure-helper playbook) |
| `EndoInsights/Sources/EndoInsights/SeriesNormalization.swift` | utility (pure helper namespace) | transform | `ChartScrubSelection.swift` | exact (pure-helper playbook) |
| `EndoInsights/Tests/EndoInsightsTests/ExploreSeriesCatalogTests.swift` | test | unit | `ChartScrubSelectionTests.swift` | exact (Swift Testing fixture style) |
| `EndoInsights/Tests/EndoInsightsTests/ExploreSeriesExtractionTests.swift` | test | unit | `ChartScrubSelectionTests.swift` | exact |
| `EndoInsights/Tests/EndoInsightsTests/ScatterPairingTests.swift` | test | unit | `ChartScrubSelectionTests.swift` | exact |
| `EndoInsights/Tests/EndoInsightsTests/SeriesNormalizationTests.swift` | test | unit | `ChartScrubSelectionTests.swift` | exact |

**Reused unchanged (NOT created here):**
- `EndoInsights.InsightPoint(date:value:)` — `Insight.swift` lines 53–61 — the quantitative output type.
- `ChartSeriesGrouping.runs(from:calendar:)` — `Sources/EndoApp/Features/Insights/ChartSeriesGrouping.swift` — downstream (Phase 15) gap-breaks the emitted `[InsightPoint]`; confirmed its input IS `[InsightPoint]` (line 42).
- `ChartScrubSelection.resolve(_:in:calendar:)` — downstream scrub resolver, reused unchanged.

## Pattern Assignments

### `ExploreSeries.swift` (model — catalog + ScaleKind)

**Analogs:** `SnapshotEnums.swift` (CaseIterable domains to enumerate); `Insight.swift` (Sendable value struct + dotted signalKey id-style).

**Import + file-header pattern** — every package source opens Foundation-only with a `// EndoInsights — ...` purpose header (`SnapshotEnums.swift` lines 1–17):
```swift
// EndoInsights — <purpose> (Phase 14).
// Foundation only — NO SwiftData/SwiftUI/UIKit (SC1).
import Foundation
```

**CaseIterable enum domains to enumerate** (`SnapshotEnums.swift` lines 20–97). The catalog's `.all` is built FROM these `.allCases` so it stays in sync by construction (Pattern 2 / Pitfall 3):
```swift
public enum SymptomType: String, CaseIterable, Sendable { case pelvicPain, dysmenorrhea, ... , other }
public enum BodyRegion: String, CaseIterable, Sendable { case lowerAbdomenPelvis, ... , shoulder }
public enum FlowIntensity: String, CaseIterable, Sendable { case spotting, light, medium, heavy }
public enum FoodTag: String, CaseIterable, Sendable { case dairy, glutenWheat, ... , other }
```

**Value-struct conformance style** — copy `InsightPoint` (`Insight.swift` lines 53–61): `public struct ... : Sendable, Equatable, Hashable` with explicit `public init`. For `ExploreSeries`, add `Identifiable` and derive ALL conformances from `id: String` (Open Question 1 / Pitfall 3 — NO stored extraction closure):
```swift
public struct InsightPoint: Sendable, Equatable, Hashable, Codable {
    public let date: Date
    public let value: Double
    public init(date: Date, value: Double) { self.date = date; self.value = value }
}
```

**Dotted, value-independent id-string convention** — mirror `Insight.signalKey` (`Insight.swift` lines 74–78; usage in `TrendsEngine.swift`): `"symptom.\(type.rawValue).severity"`, `"pain.\(region.rawValue).severity"`, `"factor.stress.mean"`, `"bleeding.frequency"`. Phase-14 ids follow the same shape: `symptom.pelvicPain.severity`, `pain.lowerBack.severity`, `sleep.hours`, `stress`, `mood`, `bleeding.intensity`, `bleeding.event`, `food.dairy`, `clots`, `prn`.

`ScaleKind` enum: copy the `String`-raw `Sendable, Equatable, Hashable, ... , CaseIterable` shape of `InsightConfidence`/`InsightKind` (`Insight.swift` lines 16–50). Add `var isQuantitative: Bool { self != .presenceEvent }` (RESEARCH Pattern 2).

---

### `ExploreSeriesExtraction.swift` (utility — the two-return-type honesty move)

**Analog:** `ChartScrubSelection.swift` (the pure-helper playbook in full).

**Namespace + purpose-header + Foundation-only import** (`ChartScrubSelection.swift` lines 17–21):
```swift
import Foundation

/// <one-line honesty contract>. NEVER interpolates, NEVER snaps (the honesty choke-point).
public enum ChartScrubSelection {
```
Copy the `public enum <Name> { public static func ... }` namespace shape (no instances, pure static funcs).

**Calendar-injected day math + nil-honesty core** (`ChartScrubSelection.swift` lines 45–51) — `calendar.startOfDay(for:)`, `points.first { calendar.isDate($0.date, inSameDayAs: day) }`, NEVER epoch arithmetic. Phase-14 quantitative extraction mirrors this with `compactMap` so nil → NO point (gap) and a logged literal 0 → a real `InsightPoint(value: 0)`:
```swift
public static func quantitativePoints(
    _ snapshots: [DaySnapshot], series: ExploreSeries, calendar: Calendar
) -> [InsightPoint] {
    precondition(series.scaleKind.isQuantitative)
    return snapshots.compactMap { snap in
        guard let value = series.quantitativeValue(from: snap) else { return nil } // nil → gap
        return InsightPoint(date: calendar.startOfDay(for: snap.day), value: value) // logged 0 → real 0
    }
}
```

**Logged-0-vs-gap idiom** — `snap.stress.map(Double.init)` (NEVER `> 0` / truthiness; Pitfall 2). Field surface to switch over is `DaySnapshot.swift` lines 50–101 (`stress: Int?`, `mood: Int?`, `sleepHours: Double?`, `activityMinutes: Double?`, `flow: FlowIntensity?`, `suppressed: Bool`, `foodTags`, `clots`, `tookPRN`, `medicationsTaken`).

**Presence path returns `[Date]`, NOT `[InsightPoint]`** (RESEARCH Pattern 1 — the structural move; no analog because no existing helper does this, but it reuses the same `compactMap`/`startOfDay` idiom):
```swift
public static func occurrenceDays(
    _ snapshots: [DaySnapshot], series: ExploreSeries, calendar: Calendar
) -> [Date] {
    precondition(series.scaleKind == .presenceEvent)
    return snapshots.compactMap { snap in
        series.isPresent(in: snap) ? calendar.startOfDay(for: snap.day) : nil
    }
}
```

**Suppression gate** — gate BOTH bleeding-derived series (`bleeding.intensity` quantitative + `bleeding.event` presence) on `!snap.suppressed` (RESEARCH Pattern 3; honors `DaySnapshot.suppressed`, line 99–100). Only bleeding/cycle-derived series are gated; symptoms/sleep/etc. are NOT.

---

### `ScatterPairing.swift` (utility — both-logged intersection + count aggregation)

**Analog:** `ChartScrubSelection.swift` (pure `enum` namespace, calendar-injected, value-in/value-out).

**Nested value-type + namespace shape** — copy `ChartScrubSelection.Resolution` (lines 24–35) as the inner `ScatterPoint`, conforming `Sendable, Equatable, Hashable` with explicit `public init`. Core per RESEARCH Code Examples (both-logged-only `guard let`/`continue`, dictionary count-aggregation, deterministic `.sorted` for fixtures, NO jitter / NO imputation):
```swift
public enum ScatterPairing {
    public struct ScatterPoint: Sendable, Equatable, Hashable {
        public let x: Double; public let y: Double; public let count: Int
        public init(x: Double, y: Double, count: Int) { self.x = x; self.y = y; self.count = count }
    }
    public static func pairs(_ snapshots: [DaySnapshot], x: ExploreSeries, y: ExploreSeries,
                             calendar: Calendar) -> [ScatterPoint] {
        var counts: [PairKey: Int] = [:]
        for snap in snapshots {
            guard let xv = x.quantitativeValue(from: snap),       // either nil → excluded, never 0
                  let yv = y.quantitativeValue(from: snap) else { continue }
            counts[PairKey(x: xv, y: yv), default: 0] += 1
        }
        return counts.map { ScatterPoint(x: $0.key.x, y: $0.key.y, count: $0.value) }
                     .sorted { ($0.x, $0.y) < ($1.x, $1.y) }
    }
    private struct PairKey: Hashable { let x: Double; let y: Double }
}
```
Scatter axes restricted to QUANTITATIVE series (Open Question 3 — enforce at API boundary).

---

### `SeriesNormalization.swift` (utility — fixed honest domain + real-unit readout)

**Analog:** `ChartScrubSelection.swift` (pure `enum` namespace, value-in/value-out). Core per RESEARCH Code Examples: `domain(for: ScaleKind) -> ClosedRange<Double>?` returns FIXED honest domains (`0...10`, `1...5`, `1...4`, `0...24`, `0...240`, presence→nil); `normalize` round-trips; `readout` always reports the REAL value + unit (never the normalized number). NO per-visible-window min/max (Pitfall 4 — that is Phase 15 presentation).

---

### Test files (all four)

**Analog:** `ChartScrubSelectionTests.swift` — copy the entire fixture harness.

**UTC-fixed deterministic calendar + builders** (`ChartScrubSelectionTests.swift` lines 19–37) — copy verbatim into a shared fixture (per RESEARCH Wave 0: a `daySnapshot(...)` builder + UTC `calendar` mirroring these private helpers):
```swift
private var calendar: Calendar {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = TimeZone(identifier: "UTC")!
    return cal
}
private func day(_ y: Int, _ m: Int, _ d: Int, hour: Int = 12, minute: Int = 0,
                 in cal: Calendar? = nil) -> Date { /* DateComponents */ }
```

**Suite + @Test + #expect structure** (`ChartScrubSelectionTests.swift` lines 12–17, 40–43):
```swift
import Testing
import Foundation
@testable import EndoInsights

@Suite("<honesty invariant name>")
struct <Name>Tests {
    @Test func <behavior>() { #expect(...) }
}
```

**The logged-0-is-real test to mirror** (`ChartScrubSelectionTests.swift` lines 73–81, `loggedSeverityZeroDay_resolvesToRealZeroPoint_notNil`) → `ExploreSeriesExtractionTests` seeds `stress = 0` and asserts a point at value 0 exists. **The DST test** (lines 97–111) is the template for any calendar-edge fixture. Per-test honesty assertions map 1:1 to RESEARCH "Phase Requirements → Test Map".

## Shared Patterns

### Pure-helper namespace (Foundation-only `enum` of `static func`s)
**Source:** `ChartScrubSelection.swift` lines 17–52.
**Apply to:** `ExploreSeriesExtraction`, `ScatterPairing`, `SeriesNormalization` — `public enum` namespace, no stored state, `public static func`, value-in/value-out.

### Calendar-injected DST-safe day math
**Source:** `ChartScrubSelection.swift` line 48 + `ChartSeriesGrouping.swift` lines 71–76.
**Apply to:** every extraction/pairing helper — `calendar.startOfDay(for:)`, `calendar.isDate(_:inSameDayAs:)`, `calendar.dateComponents([.day], ...)`. NEVER `86400`-multiples.

### nil → no point; logged 0 → real 0 (absence ≠ 0)
**Source:** `DaySnapshot.swift` lines 9–13 (honesty contract) + `ChartScrubSelection.swift` lines 9–10.
**Apply to:** all quantitative extraction — `compactMap` skips nil; `Int?.map(Double.init)` keeps a logged 0.

### Value-type conformances (Sendable/Equatable/Hashable + explicit public init)
**Source:** `Insight.swift` lines 53–61 (`InsightPoint`); `ChartScrubSelection.Resolution` lines 24–35.
**Apply to:** `ExploreSeries`, `ScaleKind`, `ScatterPoint`. Derive `ExploreSeries` conformances from `id: String` only — NO stored closure (Pitfall 3).

### Dotted value-independent id strings
**Source:** `Insight.signalKey` (`Insight.swift` lines 74–78); `TrendsEngine.swift` (`"symptom.\(type.rawValue).severity"`, `"bleeding.frequency"`).
**Apply to:** every `ExploreSeries.id`.

### UTC-fixed Swift Testing fixtures
**Source:** `ChartScrubSelectionTests.swift` lines 19–37.
**Apply to:** all four new test files (shared builder + UTC calendar).

### Foundation-only boundary (no new import)
**Source:** `BoundaryLintTests.swift` lines 33–46 (forbids SwiftData/SwiftUI/UIKit/CoreData/AppKit/HealthKit; asserts `module == "Foundation"`).
**Apply to:** every new `Sources/EndoInsights/*.swift` — import ONLY `Foundation`; tests import `Testing` + `Foundation`. This existing test auto-scans new files; it must stay green (Pitfall 6).

## No Analog Found

None. Every new file maps to a strong in-package analog. The genuinely-new shapes — the
two-return-type extraction (`[InsightPoint]` vs `[Date]`) and count-encoded `ScatterPoint` — have
no prior helper that does exactly this, but each reuses the verified `ChartScrubSelection`
namespace/calendar/nil-honesty idiom; they are new compositions of existing patterns, not new styles.

## Metadata

**Analog search scope:** `EndoInsights/Sources/EndoInsights/`, `EndoInsights/Tests/EndoInsightsTests/`, `Sources/EndoApp/Features/Insights/`.
**Files scanned:** ChartScrubSelection.swift, ChartScrubSelectionTests.swift, SnapshotEnums.swift, Insight.swift, DaySnapshot.swift, BoundaryLintTests.swift, ChartSeriesGrouping.swift (app target), TrendsEngine.swift (id-string convention).
**Pattern extraction date:** 2026-06-15
