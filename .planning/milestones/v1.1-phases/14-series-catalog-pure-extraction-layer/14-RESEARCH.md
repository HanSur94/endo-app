# Phase 14: Series Catalog & Pure Extraction Layer - Research

**Researched:** 2026-06-15
**Domain:** Pure Swift type design — Foundation-only SPM helpers that encode honesty invariants in the type system (no UI)
**Confidence:** HIGH (every contract verified against the actual source: `DaySnapshot.swift`, `Insight.swift`, `SnapshotEnums.swift`, `ChartScrubSelection.swift` + tests, `ChartSeriesGrouping.swift`, `BoundaryLintTests.swift`)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

This is an infrastructure phase — **all implementation choices are at Claude's discretion**. The binding constraints come from the codebase + research, not new user preferences. The following honesty invariants are LOCKED (non-negotiable, MUST be encoded in types):

1. **Unlogged day → NO point emitted** (nil → gap; absence ≠ 0).
2. **Logged literal 0 → a REAL point at 0** (severity 0, stress 0, etc. — distinct from a gap).
3. **Presence-kind series** (food tags, clots, PRN, medication-taken, bleeding-as-events) **extract as OCCURRENCE days, never coerced into 0/1 values** — the series-kind model must make that coercion **unrepresentable in the type system**.
4. **Scatter pairing returns ONLY days where BOTH factors are logged**, with identical day-pairs **aggregated/counted** (never jittered into fake fractional values).
5. **Scale taxonomy modeled explicitly** (ordinal 0–10 severity/stress, ordinal 1–5 mood, continuous hours/minutes, ordinal bleeding intensity, presence/event tags).
6. **Reuse, don't reinvent:** emit the existing `EndoInsights.InsightPoint(date:value:)` so the shipped app-side `ChartSeriesGrouping` is reused UNCHANGED downstream.
7. **Suppression:** honor the per-`DaySnapshot.suppressed` flag for bleeding/cycle-derived series so a suppressed user's bleeding series cannot leak.
8. **Boundary lint:** the package's `BoundaryLintTests` (Foundation-only) MUST stay green.
9. **Playbook to mirror:** Phase-13 `ChartScrubSelection` — Foundation-only, calendar-injected for determinism, UTC-fixed Swift Testing fixtures, value-in/value-out.

### Claude's Discretion

The PRESENTATION decision (lanes vs labeled normalization) and the bleeding-under-suppression product call belong to **Phase 15** (flagged `research: true` there). Phase 14 supplies only honest, presentation-agnostic primitives + the metadata they need. Pick the concrete Swift shapes.

### Deferred Ideas (OUT OF SCOPE)

- Mixed-scale lane layout vs labeled normalization (Phase 15).
- Bleeding-series-under-suppression UI treatment (Phase 15 product call).
- Any view/SwiftUI code (Phases 15/16).
</user_constraints>

<phase_requirements>
## Phase Requirements

Phase 14 carries **no direct requirement IDs** — it is the foundational pure-helper keystone. Its deliverables unblock the following downstream requirements (verified in `.planning/REQUIREMENTS.md`):

| Downstream ID | Phase | Description | Phase-14 Helper That Unblocks It |
|----|----|-------------|------------------|
| EXPL-02 | 15 | Toggle series via chips from a unified catalog | `ExploreSeries` catalog (`Identifiable`/`Hashable`/`CaseIterable`) + scale-kind metadata |
| EXPL-03 | 15 | Quantitative series render as per-scale lanes in real units | `ExploreSeriesExtraction` → `[InsightPoint]` + `ScaleKind.domain` honest min/max |
| EXPL-04 | 15 | Binary food tags / bleeding render as event-marker lanes, never 0/1 lines | `presence` extraction returns a distinct `[Date]` occurrence type, NOT `[InsightPoint]` |
| EXPL-05 | 15 | Scrub reads every series' value or "not logged" per day | quantitative `[InsightPoint]` feeds the shipped `ChartScrubSelection.resolve` unchanged |
| EXPL-07 | 15 | Honest empty/sparse states; unlogged days stay gaps | extraction emits no point on nil; `ChartSeriesGrouping` breaks runs |
| SCAT-01 | 16 | Scatter: one factor vs another, point = day where BOTH logged | `ScatterPairing.pairs(...)` both-logged-only intersection |
| SCAT-02 | 16 | Identical day-pairs stack via count-encoding, no jitter | `ScatterPoint(x:y:count:)` aggregation in the pure helper |
</phase_requirements>

## Summary

Phase 14 is a **pure type-design phase**: build four (possibly five) Foundation-only helper types in the `EndoInsights` SPM package that serve every data need of Explore (Phase 15) and Scatter (Phase 16), with the honesty invariants encoded structurally so a downstream developer **cannot** violate them by accident. There is no UI, no app-target file, no `xcodegen`, no schema change. The single verification surface is the package's Swift Testing suite (`swift test --package-path EndoInsights`), plus the already-green `BoundaryLintTests`.

The load-bearing design insight is that **the type system itself is the honesty enforcement** — not runtime checks, not copy audits. The three structural moves: (1) every quantitative extraction returns `[InsightPoint]` and simply *skips nil*, so a fabricated zero is never written; (2) **presence/event series return a distinct `[Date]` occurrence type, NOT `[InsightPoint]`**, so "coerce a tag to a 0/1 value" is literally not expressible — there is no `value` field to set to 0 or 1; (3) scatter pairing returns `[ScatterPoint(x:y:count:)]` where a missing factor produces no pair at all and identical integer pairs increment `count`, so neither imputation nor jitter has a code path.

**Primary recommendation:** Model `ExploreSeries` as a **struct catalog** (id + displayName + unit + `ScaleKind`) generated from `CaseIterable` enum domains (per-symptom-type, per-pain-region sub-series enumerated), and split extraction into **two kind-gated entry points returning two different types** — `quantitativePoints(...) -> [InsightPoint]` and `occurrenceDays(...) -> [Date]` — so the presence-as-0/1 coercion is unrepresentable. Mirror the `ChartScrubSelection` playbook exactly (calendar-injected, UTC-fixed fixtures, value-in/value-out, `Sendable`/`Equatable`).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Series catalog (id/name/unit/scale-kind) | Pure Logic (EndoInsights package) | — | Presentation-agnostic metadata; consumed by both Explore chips and Scatter axis pickers; must be Foundation-only + Sendable to cross actor boundaries |
| Quantitative extraction (`[DaySnapshot]` → `[InsightPoint]`) | Pure Logic (EndoInsights package) | — | The "absence ≠ 0" honesty contract is structural; unit-provable without rendering (the `ChartSeriesGrouping`/`ChartScrubSelection` precedent) |
| Presence/event extraction (`[DaySnapshot]` → `[Date]`) | Pure Logic (EndoInsights package) | — | Distinct return type makes 0/1 coercion unrepresentable; occurrence days are presentation-agnostic |
| Scatter pairing + aggregation | Pure Logic (EndoInsights package) | — | Both-logged intersection + count-encoding is pure math; jitter/imputation must have no code path |
| Scale-kind domains + normalization primitive | Pure Logic (EndoInsights package) | — | Honest min/max per scale-kind + real-unit-preserving readout; lane-vs-normalize *presentation* deferred to Phase 15 |
| Suppression gating for bleeding series | Pure Logic (EndoInsights package) | Presentation (Phase 15 final UI call) | Phase 14 honors `DaySnapshot.suppressed` so no bleeding point leaks; the final UI treatment is a Phase-15 product call |
| Lane layout / normalization presentation | Presentation (Phase 15) | — | Deferred — Phase 14 supplies only the honest domain metadata |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Swift | 6.x (toolchain 6.3.2 verified locally) | Language | `[VERIFIED: swift --version → 6.3.2]` strict concurrency; `Sendable`/`Equatable` value types are the package idiom |
| Foundation | system | `Date`/`Calendar`/`DateComponents` day math | `[VERIFIED: EndoInsights/Package.swift declares ZERO deps]` — the SC1 compiler boundary; only Foundation is linkable |
| Swift Testing | bundled (Xcode 26 toolchain) | Unit tests (`@Test`/`@Suite`/`#expect`) | `[VERIFIED: ChartScrubSelectionTests.swift uses import Testing]` the package's established test idiom; UTC-fixed calendar fixtures |

### Supporting
None. This phase adds **zero dependencies** by design — adding any dependency would break the SC1 boundary (`BoundaryLintTests`). `[VERIFIED: Package.swift comment "NO dependencies → cannot link SwiftData/SwiftUI. THIS is the SC1 boundary"]`

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `struct` catalog generated from enum domains | A flat `enum ExploreSeries` with one case per series | An enum can't cleanly carry per-symptom-type / per-pain-region sub-series (11 symptoms × ... ) without 30+ hand-written cases that drift from `SymptomType.allCases`. A struct catalog *derived* from `.allCases` stays in sync by construction. **Recommend the struct catalog.** |
| Two kind-gated return types (`[InsightPoint]` vs `[Date]`) | One `[InsightPoint]` API + a runtime `kind` check | A single API forces presence series to invent a `value` (0/1) — exactly the coercion the success criterion forbids. Two types make it **unrepresentable**, not merely discouraged. **Recommend two return types.** |
| `Double`-valued `ScatterPoint` with integer `count` | Jittered `[(x,y)]` pairs | Jitter fabricates non-integer values the user never logged (Pitfall 5). Count-encoding is a factual tally. **Recommend count-encoded `ScatterPoint`.** |

**Installation:** None — new files in `EndoInsights/Sources/EndoInsights/` and `EndoInsights/Tests/EndoInsightsTests/`. No `swift package add`, no `xcodegen generate` (package files only; `[VERIFIED: CONTEXT.md "New EndoInsights package files do NOT need xcodegen"]`).

**Version verification:** `[VERIFIED: swift --version → Apple Swift 6.3.2, target arm64-apple-macosx26.0]`. Test command: `swift test --package-path EndoInsights` `[VERIFIED: CONTEXT.md build notes]`.

## Package Legitimacy Audit

> Not applicable — this phase installs **zero external packages**. All code is Foundation-only inside the existing `EndoInsights` local SPM package. The SC1 boundary (`Package.swift` declares no dependencies) makes any external package un-linkable. No npm/PyPI/crates equivalent exists.

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
                    [DaySnapshot] (from Repository.daySnapshots() — Phase 15/16, NOT built here)
                            │  Foundation-only value snapshots; nil = unlogged
                            ▼
        ┌───────────────────────────────────────────────────────────────┐
        │  ExploreSeries CATALOG  (struct: id, displayName, unit, kind)   │
        │  ExploreSeriesCatalog.all  ← enumerates every loggable series   │
        │    • per SymptomType (11)   • per BodyRegion (10)               │
        │    • sleepHours, activityMinutes, stress, mood                  │
        │    • bleeding intensity     • per FoodTag (presence)            │
        │    • clots / PRN / medsTaken (presence)                         │
        └───────────────────────────────┬───────────────────────────────┘
                                         │ a chosen ExploreSeries + [DaySnapshot]
                        ┌────────────────┴─────────────────┐
                        ▼ (kind == quantitative)            ▼ (kind == presence)
        ┌──────────────────────────────────┐   ┌──────────────────────────────────┐
        │ ExploreSeriesExtraction          │   │ ExploreSeriesExtraction          │
        │   .quantitativePoints(...)       │   │   .occurrenceDays(...)           │
        │   nil → NO point (gap)           │   │   present → a Date                │
        │   logged 0 → real InsightPoint(0)│   │   absent  → NO Date               │
        │   → [InsightPoint]               │   │   → [Date]   (NO value field —    │
        │                                  │   │      0/1 coercion unrepresentable)│
        └────────────────┬─────────────────┘   └──────────────────┬───────────────┘
                         │ [InsightPoint]                          │ [Date]
                         ▼                                          ▼
        ┌──────────────────────────────────┐         (Phase 15 renders these as
        │ ChartSeriesGrouping (app target, │          event-marker tick lanes)
        │   REUSED UNCHANGED) → gap-broken │
        │   runs                           │
        └──────────────────────────────────┘

        ── Scatter path (SCAT-01/02) ────────────────────────────────────────────
        two quantitative ExploreSeries (x, y) + [DaySnapshot]
                         ▼
        ┌──────────────────────────────────────────────────────────────┐
        │ ScatterPairing.pairs(snapshots, x:, y:, calendar:)            │
        │   • a day with EITHER factor nil → NO pair (never imputed 0)  │
        │   • identical (x,y) pairs → ONE ScatterPoint, count++         │
        │   → [ScatterPoint(x: Double, y: Double, count: Int)]          │
        └──────────────────────────────────────────────────────────────┘

        ── Normalization (presentation-agnostic primitive) ──────────────────────
        SeriesNormalization.normalize(value, in: scaleKind) → unit-interval Double
        SeriesNormalization.readout(value, scaleKind) → real value + unit (honest)
        (Phase 15 decides lanes-vs-normalize; Phase 14 only supplies honest domains)
```

### Recommended Project Structure
```
EndoInsights/Sources/EndoInsights/
├── ExploreSeries.swift              # ScaleKind enum + ExploreSeries struct + ExploreSeriesCatalog.all
├── ExploreSeriesExtraction.swift    # quantitativePoints(...) -> [InsightPoint]; occurrenceDays(...) -> [Date]
├── ScatterPairing.swift             # ScatterPoint + pairs(...)
└── SeriesNormalization.swift        # normalize / readout primitives over ScaleKind

EndoInsights/Tests/EndoInsightsTests/
├── ExploreSeriesCatalogTests.swift  # catalog covers every field; ids stable/Hashable; kind correct
├── ExploreSeriesExtractionTests.swift  # gap fixtures, logged-0 fixtures, presence-not-coerced, suppression
├── ScatterPairingTests.swift        # both-logged-only, aggregation, no-imputation
└── SeriesNormalizationTests.swift   # domain min/max per kind; round-trip; readout preserves real unit
```

### Pattern 1: The kind-gated two-return-type extraction (THE structural honesty move)
**What:** Quantitative extraction returns `[InsightPoint]`; presence extraction returns `[Date]`. They are *different methods returning different types* — there is no single API that could coerce a presence series into a numeric value.
**When to use:** Every series extraction. The catalog's `ScaleKind` tells the caller which method to call.
**Example:**
```swift
// Source: design — mirrors ChartScrubSelection.swift's pure value-in/value-out idiom (verified pattern)
public enum ExploreSeriesExtraction {

    /// Quantitative series → plottable points. nil field → NO point (gap, absence ≠ 0).
    /// A logged literal 0 → a REAL InsightPoint(value: 0). One point per logged day.
    public static func quantitativePoints(
        _ snapshots: [DaySnapshot],
        series: ExploreSeries,
        calendar: Calendar
    ) -> [InsightPoint] {
        precondition(series.scaleKind.isQuantitative)  // caller-contract; presence routes elsewhere
        return snapshots.compactMap { snap -> InsightPoint? in
            // suppression gate for bleeding-derived series (see Pattern 3)
            guard let value = series.quantitativeValue(from: snap) else { return nil }  // nil → gap
            return InsightPoint(date: calendar.startOfDay(for: snap.day), value: value)
        }
    }

    /// Presence/event series → the calendar days the thing OCCURRED. No value field exists,
    /// so "coerce to 0/1" is unrepresentable (the success criterion). Absent day → NO Date.
    public static func occurrenceDays(
        _ snapshots: [DaySnapshot],
        series: ExploreSeries,
        calendar: Calendar
    ) -> [Date] {
        precondition(series.scaleKind == .presenceEvent)
        return snapshots.compactMap { snap -> Date? in
            series.isPresent(in: snap) ? calendar.startOfDay(for: snap.day) : nil
        }
    }
}
```

### Pattern 2: Struct catalog derived from `CaseIterable` enum domains
**What:** `ExploreSeries` is a `Hashable`/`Identifiable`/`Sendable` struct carrying `id`, `displayName`, `unit`, `scaleKind`, and a way to extract from a `DaySnapshot`. `ExploreSeriesCatalog.all` is built by enumerating `SymptomType.allCases`, `BodyRegion.allCases`, `FoodTag.allCases`, plus the singleton fields — so it stays in sync with the enums by construction (no hand-maintained case list to drift).
**When to use:** The single source of truth both Explore chips (EXPL-02) and Scatter axis pickers (SCAT-01) read.
**Example:**
```swift
// Source: design
public enum ScaleKind: String, Sendable, Equatable, Hashable, CaseIterable {
    case ordinal0to10      // symptom/pain severity, stress
    case ordinal1to5       // mood
    case continuousHours   // sleepHours
    case continuousMinutes // activityMinutes
    case ordinalBleeding   // FlowIntensity spotting…heavy (mapped to an ordinal rank)
    case presenceEvent     // food tags, clots, PRN, meds-taken, bleeding-as-events

    public var isQuantitative: Bool { self != .presenceEvent }
}

public struct ExploreSeries: Sendable, Equatable, Hashable, Identifiable {
    /// Stable, value-independent key (mirrors Insight.signalKey style): e.g.
    /// "symptom.pelvicPain.severity", "pain.lowerBack.severity", "sleep.hours",
    /// "stress", "mood", "bleeding.intensity", "food.dairy", "clots", "prn".
    public let id: String
    public let displayName: String     // chrome copy — Phase 15 routes through ExploreCopy audit
    public let unit: String            // "of 10", "of 5", "h", "min", "" (presence) — honest readout
    public let scaleKind: ScaleKind
    // extraction is closure- or switch-backed; see Open Question 1 for the recommended shape.
}
```

> **Sendable + closure caveat (Open Question 1):** if `ExploreSeries` stores an extraction *closure* (`@Sendable (DaySnapshot) -> Double?`), the struct can still be `Sendable` but **cannot synthesize `Equatable`/`Hashable`** (closures aren't equatable). Two viable shapes: **(A)** store only the `id` + metadata in the struct (making `Equatable`/`Hashable`/`Identifiable` trivial on `id`) and put extraction in a `switch (series.id)` inside `ExploreSeriesExtraction`; **(B)** store a small `enum Field` discriminator the extraction switches on. **Recommend (A) or (B)** — keep the closure OUT of the stored properties so `Hashable` (needed for Phase-15 chip `Set` selection, EXPL-02) is free. This is the single most important shape decision; flag it as the first plan task.

### Pattern 3: Suppression gate inside extraction (bleeding-derived series)
**What:** For bleeding-derived series (`bleeding.intensity` and bleeding-as-event), extraction must skip any snapshot where `snap.suppressed == true`, so a suppressed user's bleeding never produces a point/occurrence.
**When to use:** Only the bleeding-derived series; all other series ignore `suppressed`.
**Example:**
```swift
// Source: design — honors CONTEXT decision "honor the per-DaySnapshot.suppressed flag"
// inside quantitativeValue(from:) for the bleeding series:
func quantitativeValue(from snap: DaySnapshot) -> Double? {
    switch self.id {
    case "bleeding.intensity":
        guard !snap.suppressed else { return nil }        // suppressed → no leak (D-03/SC3)
        guard let flow = snap.flow else { return nil }    // no bleeding logged → gap
        return Double(flow.ordinalRank)                   // spotting=1 … heavy=4 (honest ordinal)
    case "stress":  return snap.stress.map(Double.init)   // nil → gap; logged 0 → 0.0 (real)
    case "mood":    return snap.mood.map(Double.init)
    case "sleep.hours": return snap.sleepHours
    // ...
    }
}
```

### Anti-Patterns to Avoid
- **A single generic `{date, value: Double}` extraction for all series:** coerces presence tags to 0/1 — the exact double honesty violation (fake continuity + absence-as-zero) called out in Pitfall 6. The two-return-type split (Pattern 1) makes it unrepresentable.
- **Imputing 0 (or last-known value) when one scatter factor is missing:** Pitfall 1's invariant on the pairing logic. A day missing either factor produces NO pair.
- **Jittering identical scatter pairs into fractional values:** fabricates values the user never logged (Pitfall 5). Aggregate with `count` instead.
- **Storing the extraction closure as a stored property and then trying to derive `Hashable`:** won't compile / silently forces a custom conformance that ignores the closure. Keep extraction out of stored props (Pattern 2 caveat).
- **Per-visible-window min/max normalization baked into Phase 14:** that's a *presentation* decision deferred to Phase 15 (Pitfall 4). Phase 14 supplies only the FIXED honest domain per `ScaleKind`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Gap-broken line runs | A new run-grouping helper in the package | The shipped app-target `ChartSeriesGrouping.runs(from:calendar:)` — REUSED UNCHANGED on the emitted `[InsightPoint]` | `[VERIFIED: ChartSeriesGrouping.swift takes [InsightPoint], lives in app target]` — emitting `InsightPoint` is the whole point of the reuse decision |
| Raw-date → logged-value resolution (scrub) | A scatter/explore-specific resolver | The shipped `ChartScrubSelection.resolve(_:in:calendar:)` — already pure, already exact-day-honest | `[VERIFIED: ChartScrubSelection.swift]` — Phase 13 built it to be reused by 15/16 |
| Day-boundary / DST-safe day math | Epoch arithmetic (`86400`-multiples) | `calendar.startOfDay(for:)` + `calendar.isDate(_:inSameDayAs:)` + `dateComponents([.day], from:to:)` | `[VERIFIED: ChartScrubSelection.swift + ChartSeriesGrouping.swift both use calendar APIs]` — the DST spring-forward test would fail otherwise |
| The plottable point value type | A new `SeriesPoint` struct | The existing `EndoInsights.InsightPoint(date:value:)` | `[VERIFIED: Insight.swift]` Sendable/Equatable/Hashable/Codable already; reusing it is a LOCKED decision |
| Determinism in tests | Real `Date()` / device calendar | UTC-fixed `Calendar(identifier: .gregorian)` with `timeZone = UTC` | `[VERIFIED: ChartScrubSelectionTests.swift private var calendar]` — the established fixture pattern |

**Key insight:** Almost everything this phase needs to *render* already exists (InsightPoint, ChartSeriesGrouping, ChartScrubSelection). Phase 14's genuinely-new code is narrow: the **catalog**, the **two-return-type extraction**, the **scatter aggregation**, and the **scale-kind domains**. Reuse keeps the gap-honesty guarantees identical to the dashboard's.

## Runtime State Inventory

> Not a rename/refactor/migration phase. **Omitted** — this is greenfield pure-type design adding new package files only. No stored data, live service config, OS-registered state, secrets, or build artifacts are touched (no schema change, no app-target file, no `xcodegen`).

## Common Pitfalls

### Pitfall 1: Presence series gains a `value` field and gets coerced to 0/1
**What goes wrong:** A unified `[InsightPoint]` extraction forces food-tag/clots/PRN series to invent `value: tagPresent ? 1 : 0`, producing fake continuous 0/1 lines and collapsing "didn't log" with "logged, tag absent."
**Why it happens:** One generic return type is the path of least resistance for a single picker.
**How to avoid:** Two return types — `[InsightPoint]` for quantitative, `[Date]` for presence. No `value` field exists on the presence path, so the coercion is unrepresentable. `precondition`/`scaleKind.isQuantitative` routes callers.
**Warning signs:** any `Double(present ? 1 : 0)`; a `presence` case inside `quantitativePoints`; a test that asserts a tag value of 0 or 1.

### Pitfall 2: A logged literal 0 is dropped as if it were a gap
**What goes wrong:** Using `snap.stress != nil ? snap.stress! : skip` is correct, but `if let s = snap.stress, s > 0` (or a truthiness check) silently drops a real, logged stress-0 day — re-introducing absence-as-zero from the opposite direction.
**Why it happens:** Optional-unwrap and zero-check get conflated.
**How to avoid:** `snap.stress.map(Double.init)` — nil → gap, 0 → `InsightPoint(value: 0)`. Pin a logged-0 fixture that asserts a point at value 0 exists (mirrors `ChartScrubSelectionTests.loggedSeverityZeroDay_resolvesToRealZeroPoint_notNil`).
**Warning signs:** any `> 0`, `!= 0`, or truthiness filter on a severity/stress/mood value before emitting.

### Pitfall 3: `Hashable` conformance silently broken by a stored closure
**What goes wrong:** Storing `let extract: (DaySnapshot) -> Double?` on `ExploreSeries` blocks synthesized `Hashable`/`Equatable`; Phase-15 chip selection (`Set<ExploreSeries>`, EXPL-02) then can't compile or needs a hand-written conformance that ignores extraction (subtly wrong if two series share metadata).
**Why it happens:** Closures aren't `Equatable`/`Hashable`.
**How to avoid:** Keep extraction OUT of stored properties (Pattern 2 caveat) — derive `Equatable`/`Hashable`/`Identifiable` from the stable `id: String`. Add a test that `Set` of all catalog series has count == catalog count (ids unique).
**Warning signs:** a closure-typed stored property on the series struct; a custom `==`/`hash(into:)` that omits a stored field.

### Pitfall 4: Suppression leak in the bleeding series
**What goes wrong:** Extraction ignores `snap.suppressed`, so a suppressed user's logged bleeding still produces points/occurrences — the leak SC3 forbids.
**Why it happens:** Suppression lives on the snapshot, not the series; easy to forget for one of the two bleeding-derived series (intensity AND event).
**How to avoid:** Gate BOTH bleeding-derived series on `!snap.suppressed` inside extraction. Pin a `suppressedBleedingFixture` asserting zero points AND zero occurrence days. (Note: per CONTEXT, only bleeding/cycle-derived series are suppression-gated; symptoms/sleep/etc. are the user's own raw log and are NOT gated.)
**Warning signs:** a bleeding extraction path with no `suppressed` check; a fixture missing the suppressed case.

### Pitfall 5: Scatter imputes or jitters
**What goes wrong:** A day with one factor missing gets a substituted 0/last-known value (fabricates a pair), or identical integer pairs get random jitter (fabricates non-integer values).
**Why it happens:** Scatter tutorials impute and jitter by default; discrete 0–10 data overplots, tempting a jitter "fix."
**How to avoid:** `ScatterPairing.pairs` skips any day where `x` OR `y` extraction returns nil; identical `(x,y)` pairs increment `ScatterPoint.count` (no jitter). Pin a fixture: 10 days all at (7,2) → one `ScatterPoint(x:7,y:2,count:10)`; a day with x logged but y nil → no pair.
**Warning signs:** any `?? 0`, `lastKnown`, `+ Double.random` near the pairing logic; a pair count < the both-logged day count.

### Pitfall 6: Boundary lint regresses on a stray import
**What goes wrong:** A new package file imports anything but Foundation (e.g. a reflexive `import SwiftUI` from a snippet), breaking `BoundaryLintTests`.
**Why it happens:** Copy-paste from a view-layer example.
**How to avoid:** Every new file imports ONLY `Foundation` (tests import `Testing` + `Foundation`). `BoundaryLintTests` scans all `Sources/EndoInsights/*.swift` and asserts `module == "Foundation"`. `[VERIFIED: BoundaryLintTests.swift]`.
**Warning signs:** any non-Foundation import in `Sources/EndoInsights/`.

## Code Examples

### ScatterPairing — both-logged-only + count aggregation (SCAT-01/02)
```swift
// Source: design — mirrors the pure, calendar-injected ChartScrubSelection idiom (verified)
import Foundation

public enum ScatterPairing {

    /// One occupied (x, y) cell. `count` is the number of days that landed on this exact pair —
    /// a factual tally, NOT a statistic (invariant-safe). No jitter, no fitted line, no r-value.
    public struct ScatterPoint: Sendable, Equatable, Hashable {
        public let x: Double
        public let y: Double
        public let count: Int
        public init(x: Double, y: Double, count: Int) { self.x = x; self.y = y; self.count = count }
    }

    /// Pair two QUANTITATIVE series over shared days. A day where EITHER factor is unlogged is
    /// EXCLUDED (never imputed). Identical (x, y) pairs are AGGREGATED into one point with count.
    public static func pairs(
        _ snapshots: [DaySnapshot],
        x: ExploreSeries,
        y: ExploreSeries,
        calendar: Calendar
    ) -> [ScatterPoint] {
        var counts: [PairKey: Int] = [:]
        for snap in snapshots {
            guard let xv = x.quantitativeValue(from: snap),   // nil → day excluded, never 0
                  let yv = y.quantitativeValue(from: snap) else { continue }
            counts[PairKey(x: xv, y: yv), default: 0] += 1
        }
        return counts
            .map { ScatterPoint(x: $0.key.x, y: $0.key.y, count: $0.value) }
            .sorted { ($0.x, $0.y) < ($1.x, $1.y) }   // deterministic order for fixtures
    }

    // Bit-pattern key so identical Doubles aggregate exactly (integer-valued ordinals are exact).
    private struct PairKey: Hashable { let x: Double; let y: Double }
}
```

### SeriesNormalization — honest domain + real-unit readout (presentation-agnostic)
```swift
// Source: design — supplies the FIXED honest domain per kind; Phase 15 decides lanes-vs-normalize.
import Foundation

public enum SeriesNormalization {

    /// The FIXED honest domain for a scale kind (never per-visible-window — Pitfall 4).
    /// Returns nil for presence (no numeric domain).
    public static func domain(for kind: ScaleKind) -> ClosedRange<Double>? {
        switch kind {
        case .ordinal0to10:      return 0...10
        case .ordinal1to5:       return 1...5
        case .ordinalBleeding:   return 1...4          // spotting…heavy ordinal rank
        case .continuousHours:   return 0...24         // honest hour ceiling
        case .continuousMinutes: return 0...240        // honest minute ceiling (tune in plan)
        case .presenceEvent:     return nil
        }
    }

    /// Map a real value onto the unit interval for shape-only overlay. Round-trips via `denormalize`.
    public static func normalize(_ value: Double, in kind: ScaleKind) -> Double? {
        guard let d = domain(for: kind) else { return nil }
        return (value - d.lowerBound) / (d.upperBound - d.lowerBound)
    }

    /// The READOUT always reports the REAL value + unit — never the normalized number (honesty).
    public static func readout(_ value: Double, kind: ScaleKind, unit: String) -> String {
        // descriptive only; final copy routes through Phase-15 ExploreCopy audit.
        unit.isEmpty ? "\(value)" : "\(value) \(unit)"
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Generic `{date, Double}` for all chart series | Kind-tagged series with distinct return types per kind | v1.1 milestone (this phase) | Presence-as-0/1 coercion becomes unrepresentable |
| Snap-to-nearest scrub | Exact-calendar-day resolution (`ChartScrubSelection`) | Phase 13 (shipped) | Reused unchanged here — no new resolver |
| Per-window min/max normalization | Fixed honest domain per scale-kind | v1.1 design (Pitfall 4) | Series shapes stay stable across window switches; presentation deferred to Phase 15 |

**Deprecated/outdated:** none — this is a greenfield package addition; no existing API is replaced.

## Assumptions Log

> The few `[ASSUMED]` items below are tuning/shape choices for the planner to confirm. None are compliance/security claims.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `FlowIntensity` maps to an ordinal rank spotting=1…heavy=4 for the `bleeding.intensity` quantitative series | Pattern 3 / Normalization | LOW — bleeding may instead be presence-only (event) per Phase-15 product call; both paths are cheap. Plan should decide whether bleeding is a quantitative ordinal series, a presence event, or BOTH (catalog can carry both). `[ASSUMED]` |
| A2 | `continuousMinutes` honest domain ceiling = 240 min, `continuousHours` = 24 h | SeriesNormalization | LOW — purely a display-domain default; tune in the plan. `[ASSUMED]` |
| A3 | Per-symptom-type (11) and per-pain-region (10) are distinct selectable series (catalog enumerates each) | Catalog | LOW — CONTEXT "Specific Ideas" confirms per-symptom/per-region are distinct, not lumped; this is `[CITED: 14-CONTEXT.md Specific Ideas]`, only the exact id-string format is `[ASSUMED]` |
| A4 | `medicationsTaken` (per-med) and `tookPRN`/`clots` are presence-event series in the catalog | Catalog | LOW — these are obvious scatter/overlay factors; including them is additive. Plan confirms which presence series ship in v1.1 vs defer. `[ASSUMED]` |

**If this table is empty:** it is not — but every `[ASSUMED]` row is a low-risk tuning/shape choice, not a locked honesty invariant. The honesty invariants are all `[VERIFIED]` against source.

## Open Questions (RESOLVED)

> Autonomous decider adopted the research recommendation for each (2026-06-15). Resolutions, binding for the planner:
> 1. **Extraction shape:** switch on a small `enum Field` (or `id`) INSIDE the extraction helper; NO closure in stored properties — `ExploreSeries` derives `Hashable`/`Identifiable`/`Sendable` from `id`. This is the FIRST plan task; everything composes on top.
> 2. **Bleeding:** the catalog carries BOTH `bleeding.intensity` (quantitative-ordinal) AND `bleeding.event` (presence), each suppression-gated. Phase 15 chooses which to surface — Phase 14 ships both, presentation-agnostic.
> 3. **Scatter axes:** restricted to QUANTITATIVE series (enforced at the `ScatterPairing` API boundary — presence factors have no numeric value and are overlay-only). Documented in the catalog/API.
> 4. **Tuning defaults (A1–A4):** adopt the research defaults — bleeding ordinal rank order, hours/minutes domain ceilings, presence-series inclusion set.

1. **`ExploreSeries` extraction shape (the load-bearing decision).**
   - What we know: the struct must be `Hashable`/`Identifiable`/`Sendable` for Phase-15 chip `Set` selection (EXPL-02); a stored closure blocks synthesized `Hashable`.
   - What's unclear: store an `enum Field` discriminator vs. switch on `id` inside `ExploreSeriesExtraction`.
   - Recommendation: **switch on a small `enum Field` (or `id`) inside the extraction helper; keep NO closure in stored properties.** Derive all conformances from `id`. Make this the FIRST plan task — everything else composes on top.

2. **Is bleeding a quantitative ordinal series, a presence event, or both?**
   - What we know: CONTEXT lists "bleeding-as-events" as presence-kind AND "ordinal bleeding intensity" as a scale-kind. The success criteria mention both framings.
   - What's unclear: whether v1.1 ships bleeding as a quantitative ordinal lane, an event lane, or offers both catalog entries.
   - Recommendation: **make the catalog carry BOTH** (`bleeding.intensity` quantitative-ordinal + `bleeding.event` presence), each suppression-gated; let Phase 15 choose which to surface. Cheap and presentation-agnostic.

3. **Scatter on ordinal-bleeding / mixed-kind axes.**
   - What we know: SCAT-01 pairs two factors; `ScatterPairing.pairs` requires both quantitative (`quantitativeValue`).
   - What's unclear: whether a presence factor can be a scatter axis (it has no numeric value).
   - Recommendation: **restrict scatter axes to quantitative series** (the `precondition`/type can enforce it); presence factors are overlay-only. Document this in the catalog/API.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Swift toolchain | building/testing the package | ✓ | 6.3.2 (arm64-apple-macosx26.0) | — |
| `swift test` (SwiftPM) | running `swift test --package-path EndoInsights` | ✓ | bundled with toolchain | run via the app scheme `xcodebuild test -scheme EndoApp` (EndoInsightsTests is a scheme test target) |
| Swift Testing | `import Testing` in test files | ✓ | bundled (Xcode 26) | — |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none material — both `swift test --package-path EndoInsights` and the in-scheme `xcodebuild test` paths are available.

## Validation Architecture

> `workflow.nyquist_validation` not set to false — section included. Each honesty invariant maps to a Swift Testing assertion that proves it WITHOUT rendering (the `ChartScrubSelectionTests` playbook).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (`import Testing`; `@Suite`/`@Test`/`#expect`) `[VERIFIED: ChartScrubSelectionTests.swift]` |
| Config file | none — SwiftPM target `EndoInsightsTests` `[VERIFIED: Package.swift]` |
| Quick run command | `swift test --package-path EndoInsights` `[VERIFIED: CONTEXT.md build notes]` |
| Full suite command | `swift test --package-path EndoInsights` (package) + `xcodebuild test -scheme EndoApp` (full app + package via scheme test target) |

### Phase Requirements → Test Map
| Honesty Invariant | Behavior | Test Type | Automated Assertion (Swift Testing) | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| Absence ≠ 0 (gap) | unlogged day → NO point | unit | seed a snapshot with `stress = nil`; `#expect(quantitativePoints(...).contains { sameDay } == false)` | ❌ Wave 0 |
| Logged 0 is real | logged stress/severity 0 → `InsightPoint(value: 0)` | unit | seed `stress = 0`; `#expect(points.first { sameDay }?.value == 0)` (mirrors `loggedSeverityZeroDay_...`) | ❌ Wave 0 |
| Presence not coerced | food tag present → a `Date`; the API returns `[Date]` not `[InsightPoint]` | unit | `#expect(occurrenceDays(...) == [expectedDay])`; **type-level**: no `value` field reachable (compile-enforced) | ❌ Wave 0 |
| Presence absent ≠ logged-absent | tag-absent day → NO occurrence Date | unit | seed a logged day without the tag; `#expect(occurrenceDays(...).isEmpty)` | ❌ Wave 0 |
| Scatter both-logged-only | day with one factor nil → no pair | unit | seed x-logged/y-nil day; `#expect(pairs(...).isEmpty)` | ❌ Wave 0 |
| Scatter aggregation | 10 identical (7,2) days → one `ScatterPoint(count:10)` | unit | `#expect(pairs(...) == [ScatterPoint(x:7,y:2,count:10)])` | ❌ Wave 0 |
| Suppression no-leak | suppressed user's bleeding → zero points AND zero occurrences | unit | `#expect(quantitativePoints(bleeding) .isEmpty); #expect(occurrenceDays(bleedingEvent).isEmpty)` | ❌ Wave 0 |
| Catalog coverage | every DaySnapshot field has a series; ids unique/Hashable | unit | `#expect(Set(catalog.map(\.id)).count == catalog.count)`; assert each known field id present | ❌ Wave 0 |
| Normalization round-trip + real readout | normalize→denormalize exact; readout shows REAL value+unit | unit | `#expect(denormalize(normalize(v)) ≈ v)`; `#expect(readout(7, .ordinal0to10, "of 10") == "7.0 of 10")` (tune) | ❌ Wave 0 |
| Boundary stays green | package imports only Foundation | unit | existing `BoundaryLintTests.packageSourcesImportOnlyFoundation` (already passes; new files must not regress) | ✅ exists |

### Sampling Rate
- **Per task commit:** `swift test --package-path EndoInsights` (fast, no simulator).
- **Per wave merge:** `swift test --package-path EndoInsights` full package suite.
- **Phase gate:** full package suite green + `BoundaryLintTests` green before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `ExploreSeriesCatalogTests.swift` — covers catalog coverage + id uniqueness/Hashable
- [ ] `ExploreSeriesExtractionTests.swift` — gap, logged-0, presence-not-coerced, presence-absent, suppression fixtures (UTC-fixed calendar fixture, copied from `ChartScrubSelectionTests`)
- [ ] `ScatterPairingTests.swift` — both-logged-only, aggregation, no-imputation
- [ ] `SeriesNormalizationTests.swift` — domain per kind, round-trip, real-unit readout
- [ ] Shared test fixtures: a `daySnapshot(...)` builder + UTC `calendar` (mirror `ChartScrubSelectionTests`' private helpers)
- [ ] Framework install: none — Swift Testing is bundled

*(No new framework needed; the package's test infrastructure already exists and is green.)*

## Security Domain

> `security_enforcement` posture for this phase: this is a **pure, offline, Foundation-only computation layer** with no I/O, no network, no persistence, no user input parsing beyond already-validated value types. The classic ASVS web categories largely do not apply. The relevant "security" here is the app's **health-data honesty + privacy posture**, addressed structurally.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | n/a — no auth surface (no accounts, no network) |
| V3 Session Management | no | n/a |
| V4 Access Control | no | n/a — operates on already-projected value snapshots |
| V5 Input Validation | partial | inputs are typed `DaySnapshot` value types already defensively decoded at the Repository seam; `nil` is honored, never coerced |
| V6 Cryptography | no | n/a — no secrets; `DeterministicUUID` (existing) is identity derivation, not a security primitive |

### Known Threat Patterns for this stack (health-data honesty, not appsec)

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Fabricated value on an unlogged day (absence-as-zero) | Information disclosure (false data presented as the user's) | Structural: nil → no point; two-return-type model makes 0/1 coercion unrepresentable |
| Suppressed bleeding leaks into a series | Information disclosure (privacy/suppression) | Extraction gates bleeding-derived series on `snap.suppressed`; pinned no-leak fixture |
| Numeric statistics (r, %, p) escaping into the layer | Tampering with the wellness-vs-medical-device line | Phase 14 emits no statistics — only points/days/counts; scatter has NO fitted line or coefficient by construction |
| Free-text health prose entering the analysis layer | Information disclosure | `DaySnapshot` carries `hasNote: Bool` only (presence), never prose `[VERIFIED: DaySnapshot.swift]` |

## Sources

### Primary (HIGH confidence)
- `EndoInsights/Sources/EndoInsights/DaySnapshot.swift` — full field surface, nil-honesty contract, `suppressed`/`hasNote` semantics (read directly)
- `EndoInsights/Sources/EndoInsights/Insight.swift` — `InsightPoint(date:value:)` (Sendable/Equatable/Hashable/Codable), `signalKey` id-style precedent (read directly)
- `EndoInsights/Sources/EndoInsights/SnapshotEnums.swift` — `SymptomType`/`BodyRegion`/`FlowIntensity`/`FoodTag`/`ActivityType`/`SleepQuality` domains + `CaseIterable` (read directly)
- `EndoInsights/Sources/EndoInsights/ChartScrubSelection.swift` + `ChartScrubSelectionTests.swift` — the pure-helper playbook to mirror (calendar-injected, UTC fixtures, exact-day honesty) (read directly)
- `Sources/EndoApp/Features/Insights/ChartSeriesGrouping.swift` — confirms `InsightPoint` is its input; REUSED unchanged (read directly)
- `EndoInsights/Tests/EndoInsightsTests/BoundaryLintTests.swift` — the Foundation-only guard (read directly)
- `EndoInsights/Package.swift` — zero-dep SC1 boundary (read directly)
- `.planning/phases/14-series-catalog-pure-extraction-layer/14-CONTEXT.md` — locked honesty invariants + Claude's-discretion scope (read directly)
- `.planning/research/ARCHITECTURE.md` + `.planning/research/PITFALLS.md` — the milestone helper map + the honesty/statistics traps these helpers must structurally prevent (read directly)
- `.planning/REQUIREMENTS.md` — EXPL-02..07 / SCAT-01/02 downstream mapping (read directly)
- `[VERIFIED: swift --version → Apple Swift 6.3.2, target arm64-apple-macosx26.0]`

### Secondary (MEDIUM confidence)
- none required — every claim is backed by direct source reads.

### Tertiary (LOW confidence)
- none.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Swift 6.3.2 + Foundation + Swift Testing verified locally; zero-dep boundary verified in `Package.swift`.
- Architecture (catalog + two-return-type extraction + scatter aggregation): HIGH — designed directly against verified source contracts; the honesty invariants map 1:1 to existing `ChartScrubSelection`/`ChartSeriesGrouping` patterns.
- Pitfalls: HIGH — derived from `.planning/research/PITFALLS.md` (read directly against v1.0 source) and the phase's locked success criteria.

**Research date:** 2026-06-15
**Valid until:** 2026-07-15 (stable — pure Foundation type design against shipped, frozen package contracts; no fast-moving dependency).
