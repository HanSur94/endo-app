# Phase 6: Cycle-Phase Mapping + Insight-Honesty Layer - Pattern Map

**Mapped:** 2026-06-08
**Files analyzed:** 13 (8 new, 5 modified) + tests
**Analogs found:** 13 / 13 (every file has an in-repo analog — "port-and-extend" phase, no novel patterns)

## Sequencing Flags (do these FIRST)

1. **`Insight.signalKey` addition** (MODIFY `Insight.swift`) — load-bearing gap (RESEARCH OQ1). Every downstream file (`HonestyGate`, `InsightsEngine`, dismissal tests) depends on `signalKey` being a stored `public let`. Sequence as task 1; nothing else compiles against the dismissal contract until it exists.
2. **`TrendsEngine` internal-demotion** (`public enum` → `enum`) — sequence early but AFTER `InsightsEngine` exists to call it, so there is never a window with no public producer. Verified zero external call sites (RESEARCH Pitfall 4); all package tests use `@testable import`, so this breaks nothing.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `EndoInsights/.../Insight.swift` (MODIFY) | model | transform | (itself — additive field on existing struct) | exact (self) |
| `EndoInsights/.../CyclePhase.swift` (NEW) | model | transform | `Insight.swift` enums (`InsightKind`/`TrendWindow`) | exact (enum shape) |
| `EndoInsights/.../CyclePhaseMapper.swift` (NEW) | service | transform | `Sources/EndoApp/Domain/CycleMath.swift` + `TrendsEngine.swift` | exact (role+flow) |
| `EndoInsights/.../HonestyGate.swift` (NEW) | service/middleware | transform (filter) | `TrendsEngine.swift` inline thresholds + `ClaimsPolicy.swift` | role-match |
| `EndoInsights/.../ForbiddenTerms.swift` (NEW) | utility/config | transform | `Sources/EndoApp/Content/ClaimsPolicy.swift` | exact (static-list mirror) |
| `EndoInsights/.../InsightsEngine.swift` (NEW) | service (composition root) | transform | `TrendsEngine.swift` (public entry-point shape) | exact (role+flow) |
| `EndoInsights/.../TrendsEngine.swift` (MODIFY) | service | transform | (itself — visibility change only) | exact (self) |
| `Sources/EndoApp/Persistence/Models/DismissedInsight.swift` (NEW) | model | CRUD | `Models/FactorReading.swift` | exact (CloudKit-safe @Model) |
| `Sources/EndoApp/Persistence/SchemaV4.swift` (NEW) | config | — | `SchemaV3.swift` | exact |
| `Sources/EndoApp/Persistence/MigrationPlan.swift` (MODIFY) | config | — | existing `v2toV3` stage | exact |
| `Sources/EndoApp/Persistence/ModelContainer+Config.swift` (MODIFY) | config | — | itself (`SchemaV3` → `SchemaV4` bump) | exact (self) |
| `Sources/EndoApp/Persistence/Repository.swift` (MODIFY) | repository | CRUD | existing `add`/`allDayEntries` consent-gated CRUD | exact |
| Tests (NEW/EXTEND) | test | — | `SnapshotFixtures.swift` + `SchemaMigrationTests.swift` | exact |

## Pattern Assignments

### `Insight.swift` — MODIFY: add `public let signalKey: String` (service/model, transform)

**Analog:** itself. `signalKey` already flows into `derived(...)` → `DeterministicUUID.make(...)` and is discarded (lines 103-121). Surface it as a stored property.

**Current struct fields** (lines 65-74) — add `signalKey` alongside, keep `Equatable`/`Sendable`/`Identifiable`:
```swift
public let id: UUID
public let kind: InsightKind
public let headline: String
public let detail: String
public let window: TrendWindow
public let confidence: InsightConfidence
public let points: [InsightPoint]
// ADD: public let signalKey: String
```

**Set it inside `derived(...)`** (lines 103-121) — the value is already in scope as the `signalKey` parameter; thread it into the `Insight(...)` init call (and add it to the internal memberwise init at lines 79-95). No call-site change for producers — they already pass `signalKey:`.

---

### `CyclePhase.swift` — NEW: the phase enum (model, transform)

**Analog:** the `public enum` shape of `InsightKind`/`TrendWindow` in `Insight.swift` (lines 16-36) — `String`-backed, `Sendable, Equatable, CaseIterable`, doc-commented per case.

```swift
public enum CyclePhase: String, Sendable, Equatable, CaseIterable {
    case menstrual, follicular, ovulatory, luteal
}
```
Per RESEARCH Pattern 1 / D-01. Optionally a per-day labelling result type (e.g. `struct PhaseDay { let day: Date; let phase: CyclePhase }`) following the `InsightPoint` struct shape (Insight.swift lines 53-61).

---

### `CyclePhaseMapper.swift` — NEW: ported cycle math + windowing (service, transform)

**Analog:** `Sources/EndoApp/Domain/CycleMath.swift` (the math to PORT verbatim, Foundation-only, D-14/D-17) + `TrendsEngine.swift` (the package idioms: `enum` namespace, injected `today`+`calendar`, `Insight.derived` output).

**PORT verbatim from CycleMath** — these helpers move into the mapper unchanged (D-17 mandates identical):
- `periodRuns(_:calendar:)` (CycleMath.swift lines 81-100) — consecutive-day run grouping
- `consecutiveGaps(_:calendar:)` (lines 103-108)
- `medianInt(_:)` (lines 111-117), `spread(_:)` (lines 120-123)

**The exact gate** — mirror `CycleMath.summarize` lines 38-49:
```swift
if anySuppressed { return [] }                              // CycleMath: `if isSuppressed { return .suppressed }`
let runs   = periodRuns(bleedingDays, calendar: calendar)
let starts = runs.compactMap(\.first)
let gaps   = consecutiveGaps(starts, calendar: calendar)
guard gaps.count >= 2, let lastStart = starts.last else { return [] }  // <2 cycles → no phases
```

**Re-derive bleeding from snapshots** (D-14, self-contained — RESEARCH Code Examples):
```swift
let bleedingDays  = snapshots.filter { $0.flow != nil }.map(\.day)   // DaySnapshot.flow (line 62)
let anySuppressed = snapshots.contains { $0.suppressed }
```
(Mirrors `TrendsEngine.bleedingTrend`'s `anySuppressed`/`bleedingDayCount` gate, lines 219-221.)

**Confidence cap at `.medium`** — adapt `CycleMath.confidenceLevel` (lines 127-135) but NEVER return `.high` (RESEARCH A2): `(c≥3, s≤3)→.medium`, `(s≤9)→.low`, else `nil` (no phases — the irregular gate, D-03). The day-math idiom for windowing is `calendar.date(byAdding: .day, value:..., to:)` + `startOfDay` (CycleMath.predictRange lines 146-163) — never epoch arithmetic.

**Phase-4 insight emission** uses `Insight.derived(kind: .cyclePhase, window: .allTime, signalKey: "cyclePhase.<phase>.symptom.<type>", ...)` — exactly the `TrendsEngine.symptomTrends` call shape (lines 122-131), descriptive non-causal copy.

---

### `HonestyGate.swift` — NEW: the single choke-point (service/middleware, filter)

**Analog:** `TrendsEngine.swift` inline thresholds — `minLoggedDays` constant (lines 29) + `confidence(forLoggedDays:)` (lines 277-284) + the `notEnoughData` carve-out (lines 234-241, signalKey ends `.insufficient`). Hoist these into one keyed-by-`InsightKind` gate.

**No-regression contract (RESEARCH Pitfall 1):** `minSample(.trend)` MUST equal `TrendsEngine.minLoggedDays` (3); carve out the `.none`-confidence "not enough data yet" insight from the confidence floor (its signalKey ends `.insufficient`, TrendsEngine line 236) or existing `TrendsEngineTests` regress.

```swift
enum HonestyGate {
    static func minSample(for kind: InsightKind) -> Int { /* .trend:3, .cyclePhase:2, ... */ }
    static let confidenceFloor: InsightConfidence = .low
    static func apply(_ candidates: [Insight], dismissed: Set<String>) -> [Insight] {
        candidates.filter { !dismissed.contains($0.signalKey) /* + min-sample + floor + term-scan */ }
    }
}
```
`InsightConfidence` is already `Comparable` (Insight.swift lines 41-49) — use `>= confidenceFloor` directly.

---

### `ForbiddenTerms.swift` — NEW: package-local ClaimsPolicy mirror (utility/config, transform)

**Analog:** `Sources/EndoApp/Content/ClaimsPolicy.swift` — `forbiddenVerbs` (lines 27-39). CANNOT import it (SC1, app target); mirror as a package-local `static let` and EXTEND with the D-08 causal connectives.

```swift
enum ForbiddenTerms {
    static let causal: [String] = [
        "trigger", "causes", "cause", "will",         // from ClaimsPolicy.forbiddenVerbs
        "diagnose", "detect", "treat", "cure", "prevent",
        "because", "due to", "leads to", "results in", "responsible for",  // D-08 connectives
    ]
    static func violation(in text: String) -> String? { causal.first { text.lowercased().contains($0) } }
}
```
**Substring sharp edge (RESEARCH Pitfall 2):** "cause" ⊂ "because". Curate the engine's hand-written copy to avoid substring collisions; the gate `assert`s in debug (mirrors `Repository.assertConsented()` debug-assert), never silently drops in release. The required zero-terms test (D-08) scans `headline`+`detail` of every emitted insight.

---

### `InsightsEngine.swift` — NEW: the public composition root (service, transform)

**Analog:** `TrendsEngine.swift` public entry-point shape (lines 23, 44-62) — `public enum`, injected `today`+`calendar`. This becomes the ONE public API (D-13); lock the signature now (D-16).

```swift
public enum InsightsEngine {
    public static func insights(
        from snapshots: [DaySnapshot], asOf today: Date,
        dismissed: Set<String>, calendar: Calendar = .current
    ) -> [Insight] {
        let candidates = TrendsEngine.trends(from: snapshots, asOf: today, calendar: calendar)
                       + CyclePhaseMapper.insights(from: snapshots, asOf: today, calendar: calendar)
        return HonestyGate.apply(candidates, dismissed: dismissed)
    }
}
```

---

### `TrendsEngine.swift` — MODIFY: demote to internal (service, transform)

**Analog:** itself. ONE change: `public enum TrendsEngine` → `enum TrendsEngine` (line 23). No logic change. RESEARCH Pitfall 4: verified zero external call sites; all package tests `@testable import EndoInsights`, so this is safe.

---

### `DismissedInsight.swift` — NEW: CloudKit-safe @Model (model, CRUD)

**Analog:** `Sources/EndoApp/Persistence/Models/FactorReading.swift` (lines 36-68) — the exact CloudKit-safe append-only template.

**Copy these rules** (FactorReading lines 41-67): `extension SchemaV1 { @Model final class ... }` nesting (A1 stable entity name); every property optional/defaulted; `var id: UUID = UUID()` app-generated, NO `@Attribute(.unique)`; memberwise init with all-defaulted params.
```swift
extension SchemaV1 {
    @Model final class DismissedInsight {
        var id: UUID = UUID()
        var signalKey: String? = nil
        var dismissedAt: Date = Date()
        init(id: UUID = UUID(), signalKey: String? = nil, dismissedAt: Date = .now) { ... }
    }
}
```

---

### `SchemaV4.swift` — NEW: additive versioned schema (config)

**Analog:** `SchemaV3.swift` (lines 18-33). Copy verbatim, bump `Schema.Version(4, 0, 0)`, append `SchemaV1.DismissedInsight.self` to the `models` array (the ONLY delta vs V3).

### `MigrationPlan.swift` — MODIFY (config)

**Analog:** the existing `v2toV3` stage (lines 31-34). Add `SchemaV4.self` to `schemas` (line 17), `v3toV4` to `stages` (line 21), and:
```swift
static let v3toV4 = MigrationStage.lightweight(fromVersion: SchemaV3.self, toVersion: SchemaV4.self)
```
NEVER `.custom` (lines 7-10: hooks don't fire under CloudKit).

### `ModelContainer+Config.swift` — MODIFY (config)

**Analog:** itself, line 51: `Schema(versionedSchema: SchemaV3.self)` → `SchemaV4.self`. Single-line bump; the `EndoMigrationPlan` wiring (line 80) already carries the new stage.

---

### `Repository.swift` — MODIFY: dismissal CRUD (repository, CRUD)

**Analog:** the consent-gated CRUD pair `add`/`allDayEntries` (lines 137-146) — `assertConsented()` first, then `context.insert`/`context.save` (write) or `context.fetch(FetchDescriptor<...>())` (read).

```swift
func dismissInsight(signalKey: String) throws {
    assertConsented()
    context.insert(SchemaV1.DismissedInsight(signalKey: signalKey))   // append-only, never dedupe
    try context.save()
}
func dismissedSignalKeys() throws -> Set<String> {
    assertConsented()
    return Set(try context.fetch(FetchDescriptor<SchemaV1.DismissedInsight>()).compactMap(\.signalKey))
}
```
Add the two declarations to the `Repository` protocol (the seam at lines 64-120) AND the `SwiftDataRepository` impl. Append-only / dedupe-on-read via `Set` (D-10/D-12).

---

### Tests — NEW/EXTEND

**Package fixtures** — analog `EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift`. EXTEND the `Fixtures` enum (lines 13-149) with `regularCycleFixture`/`irregularCycleFixture`/`underSampledCycleFixture` reusing the fixed-UTC-calendar + `day(offset:)` DSL (lines 16-28) and the `snapshot(offset:flow:suppressed:...)` builder (lines 34-72). Model on `suppressedNoBleedingFixture` (lines 83-93) for the suppressed→no-phases case.

**Migration survival test** — analog `Tests/EndoAppTests/SchemaMigrationTests.swift` `v2Store_survivesLightweightMigrationToV3` (lines 80-146). CLONE it +1 version: open at `SchemaV2`/`SchemaV3` on disk, insert rows, reopen via `makeModelContainer(url:)`, `#expect` all rows survive + `FetchDescriptor<SchemaV1.DismissedInsight>()` is queryable/empty (mirror lines 142-145). Force `cloudKitDatabase: .none` on the source store (P-2, line 99).

**Repository dismissal round-trip** — NEW `RepositoryDismissalTests`: insert via `dismissInsight`, read back via `dismissedSignalKeys`, assert Set-deduped (append twice → one key).

## Shared Patterns

### Pure Foundation-only engine idiom
**Source:** `TrendsEngine.swift` (lines 23, 44-62) / `CycleMath.swift` (lines 23, 32-37)
**Apply to:** `CyclePhaseMapper`, `HonestyGate`, `InsightsEngine`
`enum` namespace; injected `today` + `calendar = .current` (never `Date.now`/epoch math); value-in/value-out; NO logging/print (V7); `Equatable` outputs.

### Deterministic insight construction
**Source:** `Insight.derived(...)` (Insight.swift lines 103-121) + `DeterministicUUID` (lines 131-159)
**Apply to:** every producer (CyclePhaseMapper). NEVER construct `Insight` with `UUID()`; always `derived(kind:window:signalKey:...)`. Reuse unchanged for `.cyclePhase` ids.

### CloudKit-safe append-only @Model
**Source:** `FactorReading.swift` (lines 36-68)
**Apply to:** `DismissedInsight`. Nested under `extension SchemaV1`; all properties optional/defaulted; app-generated `id`; NO `.unique`; never mutate/dedupe on write.

### Additive lightweight migration
**Source:** `SchemaV3.swift` + `MigrationPlan.swift` `v2toV3` (lines 31-34) + `SchemaMigrationTests` V2→V3 (lines 80-146)
**Apply to:** `SchemaV4`, `v3toV4`, the new survival test. Additive-only; `.lightweight` never `.custom`; prove with a clone of the V2→V3 test.

### Consent-gated repository CRUD
**Source:** `Repository.swift` `add`/`allDayEntries` (lines 137-146)
**Apply to:** `dismissInsight`/`dismissedSignalKeys`. `assertConsented()` first (V4 access-control, security_enforcement).

### Non-causal language posture
**Source:** `ClaimsPolicy.forbiddenVerbs` (ClaimsPolicy.swift lines 27-39) + `TrendsEngine.directionPhrase` (lines 267-275, non-causal phrasing)
**Apply to:** all emitted insight copy + the `ForbiddenTerms` mirror + the zero-terms test.

## No Analog Found

None. Every file maps to an existing in-repo pattern — this is a closed-world port-and-extend phase (RESEARCH §Summary).

## Metadata

**Analog search scope:** `EndoInsights/Sources/EndoInsights/`, `Sources/EndoApp/{Domain,Persistence,Persistence/Models,Content}/`, `EndoInsights/Tests/`, `Tests/EndoAppTests/`
**Files scanned:** 11 analogs read in full (Insight, CycleMath, TrendsEngine, FactorReading, SchemaV3, MigrationPlan, ModelContainer+Config, ClaimsPolicy, Repository, SchemaMigrationTests, SnapshotFixtures)
**Pattern extraction date:** 2026-06-08
