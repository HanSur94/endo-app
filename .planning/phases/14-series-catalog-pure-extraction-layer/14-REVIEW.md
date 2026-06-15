---
phase: 14-series-catalog-pure-extraction-layer
reviewed: 2026-06-15T00:00:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - EndoInsights/Sources/EndoInsights/ExploreSeries.swift
  - EndoInsights/Sources/EndoInsights/ExploreSeriesExtraction.swift
  - EndoInsights/Sources/EndoInsights/ScatterPairing.swift
  - EndoInsights/Sources/EndoInsights/SeriesNormalization.swift
  - EndoInsights/Tests/EndoInsightsTests/SeriesTestSupport.swift
  - EndoInsights/Tests/EndoInsightsTests/ExploreSeriesCatalogTests.swift
  - EndoInsights/Tests/EndoInsightsTests/ExploreSeriesExtractionTests.swift
  - EndoInsights/Tests/EndoInsightsTests/ScatterPairingTests.swift
  - EndoInsights/Tests/EndoInsightsTests/SeriesNormalizationTests.swift
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
status: findings_found
---

# Phase 14: Code Review Report

**Reviewed:** 2026-06-15T00:00:00Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** findings_found

## Summary

Phase 14 delivers a pure, Foundation-only computation layer (catalog + extraction + scatter pairing + normalization) for the Explore/Scatter views, with the LOCKED honesty invariants encoded primarily in the type system. I reviewed every source file and traced each honesty invariant against the real underlying model types (`DaySnapshot`, `SymptomPoint`/`PainPoint`, `InsightPoint`, the snapshot enums).

**The honesty core is correct and provably so.** I verified field types directly:
- `stress`, `mood`, `SymptomPoint.severity`, `PainPoint.severity` are all `Int?`; `sleepHours`, `activityMinutes` are `Double?`. Every accessor uses `.map(Double.init)` (or returns the optional `Double` directly) with NO `?? 0`, NO `> 0`, NO truthiness filter. A logged literal 0 therefore survives as a real `0.0` and a nil field emits no point. Confirmed in `quantitativeValue(from:)` (ExploreSeries.swift:121-144).
- The presence path (`occurrenceDays`) returns `[Date]` with no value field, so 0/1 coercion is structurally unrepresentable (ExploreSeriesExtraction.swift:53-60). `isPresent(in:)` correctly distinguishes presence-absent from logged-absent.
- Suppression gates live inside the accessors (`!snap.suppressed` at ExploreSeries.swift:138 and :162), so both bleeding-derived series (intensity AND event) emit nothing under suppression, and the gate carries through extraction AND scatter for free.
- Scatter pairs only both-logged days (`guard let xv, let yv else { continue }`, ScatterPairing.swift:74-75), aggregates identical pairs by count with no jitter/random/rounding, and is quantitative-axis-only.
- Normalization domains are FIXED literal ranges per kind, never computed from data; `readout` returns the real value+unit, never the normalized number.
- `ExploreSeries` derives Equatable/Hashable/Identifiable from `id` alone with no stored closure; the catalog is built from `.allCases`; ids are unique (verified no `food.other`/`symptom.other` collision — they are `food.other` vs `symptom.other.severity`).
- All four source files import Foundation only; `BoundaryLintTests` scans every package source and asserts `module == "Foundation"`.

The float-key aggregation flag from the plan-checker is **safe**: every quantitative axis value is either `Double(Int)` (stress/mood/severity/bleeding-rank — exact) or a user-logged `Double` carried verbatim with NO intervening arithmetic, so two equal logged values produce bit-identical keys. There is no silent under-count path.

No Critical findings. The findings below are robustness/quality concerns that should be addressed before Phase 15 builds on this layer, plus minor test-rigor notes.

## Warnings

### WR-01: `pairs`/`quantitativePoints`/`occurrenceDays` trap (crash) on a misrouted series instead of failing safely

**File:** `EndoInsights/Sources/EndoInsights/ScatterPairing.swift:64`, `EndoInsights/Sources/EndoInsights/ExploreSeriesExtraction.swift:38`, `:55`
**Issue:** All three entry points enforce their kind contract with `precondition(...)`. `precondition` is active in release builds (unlike `assert`), so passing a `.presenceEvent` series to `pairs`/`quantitativePoints`, or a quantitative series to `occurrenceDays`, traps and crashes the app. The kind contract is enforced only at runtime — nothing at the type level prevents a caller (Phase 15/16 axis pickers) from handing in the wrong series. A picker bug or a future catalog edit would turn a routing mistake into a hard crash in a shipping health app rather than an empty/handled result. This is the one place the otherwise type-system-encoded honesty design falls back to a runtime trap.
**Fix:** Either (a) keep the precondition but add a Phase-15-facing note that callers MUST gate on `scaleKind.isQuantitative` before calling (and add a test that the picker layer only ever passes valid kinds), or (b) make misuse non-fatal by returning `[]` for a wrong-kind series so a routing bug degrades to "no data" rather than a crash:
```swift
public static func pairs(_ snapshots: [DaySnapshot], x: ExploreSeries, y: ExploreSeries,
                         calendar: Calendar) -> [ScatterPoint] {
    guard x.scaleKind.isQuantitative, y.scaleKind.isQuantitative else { return [] }
    // ...
}
```
Given the "privacy-first, must-not-crash" posture, (b) is the safer default for a pure data layer; keep `precondition` only if a crashing contract violation is genuinely preferred over silent emptiness and is covered by a debug-only test.

### WR-02: `normalize`/`denormalize` do not clamp, so an out-of-domain logged value escapes `[0,1]`

**File:** `EndoInsights/Sources/EndoInsights/SeriesNormalization.swift:48-51`
**Issue:** `normalize` is a raw linear map with no clamping. The fixed domains are honest ceilings (e.g. `continuousHours → 0...24`, `continuousMinutes → 0...240`), but `sleepHours`/`activityMinutes` are free user-entered `Double?` with no upstream cap in `DaySnapshot`. A user who logs 26 hours of sleep (or a fat-fingered 1000 minutes of activity) yields `normalize == 1.083...` (or `>4`), i.e. a value outside the `[0,1]` unit interval the function's contract and doc comment ("map ... onto the unit interval `[0,1]`") promise. Phase 15 will feed this straight into overlay positioning, where a >1 value would render off the lane/axis. The round-trip stays exact, but the "unit interval" guarantee is violated for out-of-domain inputs.
**Fix:** Clamp the normalized output to the unit interval (the readout already preserves the honest real value, so clamping the *shape* coordinate does not lie about the data):
```swift
public static func normalize(_ value: Double, in kind: ScaleKind) -> Double? {
    guard let d = domain(for: kind) else { return nil }
    let t = (value - d.lowerBound) / (d.upperBound - d.lowerBound)
    return min(max(t, 0), 1)
}
```
Note this makes `denormalize(normalize(v))` no longer recover an out-of-domain `v` — that is acceptable (and arguably correct) for shape coordinates, but update the round-trip doc/test to scope the guarantee to in-domain values. Alternatively, leave unclamped but document that Phase 15 must clamp before positioning. Either way the current "maps onto `[0,1]`" doc is inaccurate for the unbounded continuous kinds.

### WR-03: `quantitativeValue` silently takes the FIRST matching symptom/pain entry, hiding duplicate-type logs

**File:** `EndoInsights/Sources/EndoInsights/ExploreSeries.swift:124`, `:126`
**Issue:** `snap.symptoms.first { $0.type == type }?.severity` (and the pain equivalent) assumes at most one entry per `SymptomType`/`BodyRegion` per day. `DaySnapshot.symptoms` is a plain `[SymptomPoint]` with no uniqueness guarantee; the Repository mapping (Plan 03, app-side) is what enforces one-per-type, and that contract is not visible or asserted in this package. If a day ever carries two `.pelvicPain` entries (e.g. severity 2 and severity 8 — a repository or fixture bug), `.first` silently reports only one and discards the other with no signal. For a series whose entire purpose is honest representation of logged severity, silently dropping a second logged value is a quiet honesty gap. (`CorrelationEngine.swift:81` shows the codebase is already aware that duplicate-`day` repository bugs are a real concern.)
**Fix:** This is a defensible boundary assumption, but make it explicit so it cannot rot silently. Either document the one-entry-per-type precondition on the accessor, or harden against duplicates with a deterministic, honest reduction (e.g. take the max logged severity rather than positional-first, which at least never under-reports):
```swift
case .symptomSeverity(let type):
    return snap.symptoms.filter { $0.type == type }.compactMap(\.severity).map(Double.init).max()
```
At minimum add a package-side note that one-`SymptomPoint`-per-type-per-day is a Repository invariant this accessor relies on.

## Info

### IN-01: `readout` renders integer-domain values as `"7.0"`, `"4.0"` via default `Double` interpolation

**File:** `EndoInsights/Sources/EndoInsights/SeriesNormalization.swift:63-65`
**Issue:** `"\(value)"` on a `Double` yields `"7.0 of 10"` for an ordinal severity, `"4.0"` for a bleeding rank, `"1.0"` for the presence readout test (SeriesNormalizationTests.swift:66). For ordinal/integer-valued scales the trailing `.0` reads awkwardly. This is honest (it is the real value, not the normalized number) and the doc comment explicitly defers final chrome formatting to the Phase-15 `ExploreCopy` audit, so it is not a defect — but the descriptive default is slightly off for the integer kinds and worth a deliberate decision rather than an accident.
**Fix:** If a cleaner default is wanted before Phase 15, format integer-domain kinds without the decimal (e.g. via `value.rounded() == value` check or a per-kind formatter), or explicitly note that `readout` is a debug/placeholder string and all user-facing copy goes through Phase 15. No change required for correctness.

### IN-02: Float-`Double` aggregation key is safe today but depends on an unstated "no arithmetic on axis values" invariant

**File:** `EndoInsights/Sources/EndoInsights/ScatterPairing.swift:69-76`, `:86-89`
**Issue:** `PairKey` hashes raw `Double` x/y for exact-equality bucketing. This is correct for the current domains (integer ordinals via `Double(Int)`, and user-logged `Double` hours/minutes carried verbatim — equal logs produce bit-identical keys, so no under-count). The safety is entirely contingent on axis values never being *computed* (no scaling, rounding, unit conversion) before keying. If a future change normalizes or rescales before pairing, exact-`Double` keys would silently under-count (e.g. two "equal" values differing in the last ULP land in separate cells). The comment at :67-68 asserts the property but nothing enforces it.
**Fix:** Add a one-line note at the `PairKey` definition that it relies on raw, never-arithmetic axis values, so a future refactor that introduces pre-key arithmetic is forced to reconsider keying. No code change needed now.

### IN-03: `isPresent`/`quantitativeValue` `else`-return-`false`/`nil` for the off-path kinds is correct but untested for the cross-routing case

**File:** `EndoInsights/Sources/EndoInsights/ExploreSeries.swift:140-142`, `:163-167`
**Issue:** `quantitativeValue` returns `nil` for presence-kind fields and `isPresent` returns `false` for quantitative-kind fields — the correct "wrong door" behavior. The tests exercise each accessor on its intended kind, but there is no fixture proving the cross case directly (e.g. `dairySeries.quantitativeValue(from: loggedDairyDay) == nil`, or `stressSeries.isPresent(in: loggedStressDay) == false`). The invariant holds by code inspection, but a test would lock it against a future edit that accidentally wires a presence field into the quantitative switch.
**Fix:** Add two small assertions: a presence series returns `nil` from `quantitativeValue` even when the underlying thing is logged, and a quantitative series returns `false` from `isPresent` even when logged. Cheap insurance for the structural-honesty contract.

### IN-04: `readout` honesty assertion `!readout.contains("0.7")` is weakly chosen

**File:** `EndoInsights/Tests/EndoInsightsTests/SeriesNormalizationTests.swift:62`
**Issue:** The guard asserts the readout for value 7 does not contain `"0.7"` (the normalized form). Because `readout` interpolates the real value as `"7.0 of 10"`, this string never contains `"0.7"` regardless of implementation — the assertion would pass even for a buggy readout that, say, dropped the unit. It documents intent but proves little. The stronger, already-present assertion (`readout == "7.0 of 10"`) does the real work.
**Fix:** Either drop the `0.7` substring check (it is near-tautological) or replace it with a more pointed negative: assert the readout equals the exact expected string AND that `normalize(7)`'s string form is genuinely absent — e.g. compute `normalize(7, in: .ordinal0to10)!` and assert `!readout.contains("\(thatValue)")`, so the test actually couples to the normalized number rather than a hardcoded `"0.7"`.

---

_Reviewed: 2026-06-15T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
