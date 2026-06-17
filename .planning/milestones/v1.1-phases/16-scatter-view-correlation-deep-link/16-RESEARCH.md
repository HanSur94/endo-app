# Phase 16: Scatter View & Correlation Deep-Link - Research

**Researched:** 2026-06-16
**Domain:** iOS / SwiftUI + Swift Charts `PointMark` scatter; in-app cross-tab deep-link; pure resolver from `Insight.signalKey` → two `ExploreSeries`
**Confidence:** HIGH (every contract verified against shipped source in this session)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Where scatter lives (SCAT-01):** Scatter is a second MODE inside Explore — a segmented `Lanes | Scatter` toggle at the top of `ExploreView`. In Scatter mode the user picks exactly TWO quantitative axes (X and Y) from the catalog; the axis-picker offers only quantitative series (`ScaleKind.isQuantitative`). Mode toggle uses the reserved `chart.dots.scatter` SF Symbol.
- **Scatter rendering (SCAT-01/02):** Each point = one day where BOTH chosen factors were logged — `ScatterPairing.pairs(...)` → `[ScatterPoint(x:y:count:)]`. A day missing either factor → NO point (absence ≠ 0, never imputed/zero-filled). Identical `(x,y)` day-pairs STACK via count-encoded point size AND/OR opacity — NEVER jitter. `PointMark`; both axes carry their real-unit domain (`SeriesNormalization.domain`) + labels. A logged-0 on both axes IS a real point.
- **Descriptive-only chrome (SCAT-03):** NO fitted/trend/regression line. NO correlation coefficient / r-value / "% impact" / numeric confidence. A NEUTRAL caption only ("Based on N days where both were logged"). An honest SPARSE state when too few both-logged days exist. The gated correlation engine remains the ONLY thing allowed to say "possible association"; the scatter shows raw points only.
- **Correlation-card deep-link (SCAT-04):** A "Possible association" `.correlation` card gains a "see the days behind this" affordance that opens Explore in SCATTER mode PREFILLED with the card's two factors as X/Y. Prefer a TYPED carrier over copy/string parsing. The affordance copy is audited `ExploreCopy`; the deep-link carries a value, never re-derives.
- **Honesty / a11y invariants:** Overplotting handled by COUNT, never jitter. Axes in real units, honest fixed domains. Colors/encoding never the sole signal; a text-summary / a11y descriptor. All new chrome via audited `ExploreCopy`. PDF report path UNTOUCHED. New app-target files → `xcodegen generate`. Swift 6 strict concurrency. NO new persistence, NO schema/engine/CloudKit changes.

### Claude's Discretion
- Smart-discuss autonomous mode: discretion items decided in the UI-SPEC (scatter-plot-height 320 square, chart-plot-inset 8, single-accent-hue + size/opacity count-encoding, `arrow.up.forward.square` deep-link glyph, resolver HIDE-the-affordance behavior for presence-only factors, empty/sparse/caption/summary/deep-link wording, worded-text-summary over an Audio Graph). RESEARCH must work out whether to parse the `signalKey` or thread the typed `CorrelationFinding`.

### Deferred Ideas (OUT OF SCOPE)
- Tap a scatter point → that day's log (EXPL-08) — v1.1.x.
- Saved/recent Explore+scatter configs (EXPL-09), landscape, lag-shift — v2.
- Any fitted line / r-value / "% impact" — OUT OF SCOPE permanently (honesty posture).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SCAT-01 | Switch Explore to a scatter view; one factor against another (two quantitative axes) | Mode toggle + two-axis quantitative-only picker (Code Examples); `ScatterPairing.pairs` + `SeriesNormalization.domain` (Standard Stack / Don't Hand-Roll); `ScatterPlotView` PointMark recipe (Pattern 1) |
| SCAT-02 | Identical day-pairs stack honestly via count-encoded point size/opacity | `ScatterPoint.count` (shipped) + `.symbolSize(by:)` (Pattern 1); count-never-jitter enforced by type shape + grep gate (Pitfalls 4) |
| SCAT-03 | Descriptive-only: no fitted line, no coefficient; neutral caption; honest sparse state | No-stats discipline (Pitfall 4); `honestState` reuse + count-sum sparse threshold (Code Examples); `CopyAuditTests` over neutral strings |
| SCAT-04 | Open a prefilled scatter from a "Possible association" card | `ScatterDeepLink` resolver from `signalKey` (load-bearing — The Deep-Link Resolver section); `ExploreScatterRouter` cross-tab seam (Pattern 3); NEW `-uitest-seed-scatter-link` seed (Pitfall 2) |
</phase_requirements>

## Summary

Phase 16 adds a descriptive-only scatter mode to the shipped Explore screen and a "see the days behind this" deep-link from a `.correlation` dashboard card into that scatter, prefilled. The pure layer (`ScatterPairing.pairs`, `SeriesNormalization.domain/.readout`, `ExploreSeriesCatalog`/`ScaleKind`) is SHIPPED and consumed UNCHANGED — Phase 16 is **app-target-only** (views + one app-target resolver + one E2E seed + copy strings). The Swift Charts recipe is a direct, idiomatic mirror of the shipped `InsightTrendChart` (`Chart { ForEach… PointMark }` + `.chartXScale`/`.chartYScale(domain:)` + `.chartXAxis`/`.chartYAxis`), with count driving `.symbolSize`.

**The load-bearing finding (the one real unknown, now RESOLVED):** the emitted `.correlation` `Insight` carries **only `signalKey` + copy** — it does NOT carry the typed factor/target. The typed carriers (`Factor`, `CorrelationFinding`) are **package-internal** (`enum Factor`, `struct CorrelationFinding` — no `public`) and never cross the EndoInsights boundary; the only public typed accessor (`CorrelationEngine.scoredCandidates`) is also package-internal and consumed only by the recommendation engine inside the package. Therefore the deep-link **cannot** receive a typed carrier without new public API. The honest, lowest-risk path is a small **app-target resolver that maps the stable `signalKey` string** (`correlation.<factor.key>.symptom.<type>` / `correlation.<factor.key>.bleeding`) to an optional `(xSeries, ySeries)` pair of quantitative `ExploreSeries`. This is parsing a STABLE STRUCTURAL KEY (the `signalKey` is the dismissal/identity contract — Pitfall-4-stable, value-independent), NOT parsing audited human copy — so it does **not** violate the Phase-11 "direction from SIGNED effect, never parsed copy" discipline (that rule forbids re-deriving SEMANTICS from prose; the `signalKey` is a typed-by-convention machine key). See `## The Deep-Link Resolver (SCAT-04)`.

**Second load-bearing finding (E2E gap):** the existing `-uitest-seed-recommendations` seed produces a `dairy→pelvicPain` correlation — but **dairy is a presence-only food factor**, so the resolver correctly returns `nil` and HIDES the affordance. The deep-link E2E therefore needs a **NEW seed** producing a quantitative→quantitative correlation whose BOTH sides map to a scatter axis — the natural choice is `stress.high → pelvicPain` (stress is quantitative `0…10`, pelvicPain severity is quantitative `0…10`). See `## Validation Architecture`.

**Primary recommendation:** Add an ephemeral mode `@State` + an ephemeral X/Y `@State` to `ExploreView`; render the scatter via a new `ScatterPlotView` (mirror `InsightTrendChart`'s `PointMark` recipe with `.symbolSize(by:)`); add a pure `ScatterDeepLink` resolver (app target, signalKey → optional axis pair, unit-tested); route the affordance through a new `@Observable ExploreScatterRouter` injected exactly like `CheckInRouter`, with the Explore tab reading it on appear; add a `-uitest-seed-scatter-link` seed (stress→pain) for the deep-link E2E.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Both-logged pairing + count aggregation | EndoInsights (pure) | — | `ScatterPairing.pairs` SHIPPED, consumed unchanged (no SwiftData/UI) |
| Fixed real-unit axis domains + readouts | EndoInsights (pure) | — | `SeriesNormalization.domain`/`.readout` SHIPPED |
| Quantitative-only axis catalog + suppression | EndoInsights (pure) catalog + app filter | — | `ScaleKind.isQuantitative` + `ExploreSelection.offered(suppressed:)` |
| Scatter rendering (PointMark, count-encode, axes) | App (SwiftUI/Swift Charts view) | — | Presentation only; mirrors shipped `InsightTrendChart` |
| Mode toggle + X/Y axis pick (ephemeral) | App (`ExploreView` `@State`) | — | LOCKED: never persisted (privacy invariant) |
| Deep-link signalKey → axis-pair resolution | App (pure resolver struct) | — | Cannot be in package — `Factor`/`CorrelationFinding` are package-internal; resolver consumes the public `Insight.signalKey` |
| Cross-tab navigation (Insights → Explore scatter) | App (`@Observable` router, env-injected) | — | Mirror the shipped `CheckInRouter` deep-link seam |
| Deterministic E2E seed + a11y-id assertions | App (`AppDependencies` seed + XCUITest) | — | Mirror `ExploreUITests` / `-uitest-seed-*` pattern |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Swift Charts (`Chart` + `PointMark`) | iOS 18+ (system) | The scatter plot | First-party, already used in `InsightTrendChart`/`EventLaneChart`; no dependency [VERIFIED: shipped source `InsightTrendChart.swift:179-234`] |
| SwiftUI (`Picker` segmented, `Menu`, `@State`, `@Observable`, `NavigationStack`) | iOS 18+ (system) | Mode toggle, axis selectors, router | Matches shipped Explore/Insights idioms [VERIFIED: `SeriesPickerView.swift:78`, `CheckInRouter.swift`] |
| EndoInsights (local SPM, zero-dep) | shipped | `ScatterPairing` / `SeriesNormalization` / `ExploreSeriesCatalog` | Phase 14 pure layer, consumed UNCHANGED [VERIFIED: `ScatterPairing.swift`, `SeriesNormalization.swift`, `ExploreSeries.swift`] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Swift Testing (`@Test`, `#expect`) | Xcode 26 | Pure resolver unit tests | New pure logic (the resolver) — app-target test [CITED: STACK.md] |
| XCUITest (XCTest) | Xcode 26 | Scatter render + deep-link E2E | Mirror `ExploreUITests` seeded a11y-id pattern [VERIFIED: `ExploreUITests.swift`] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `signalKey` string resolver (app target) | New public `CorrelationEngine` API returning a typed carrier with factor/target | Larger blast radius: adds public package surface + egress past `BoundaryLintTests`, threads a new type through the coordinator → card. The signalKey is already the stable identity contract; resolving from it is lower-risk and additive. **Recommend the resolver.** |
| `.symbolSize(by:)` count encoding | per-mark opacity only | UI-SPEC permits size AND/OR opacity. Size is the more legible, color-independent cue (opacity reads as hue change to some users). **Recommend size primary; opacity optional secondary.** |
| `@Observable` router (CheckInRouter pattern) | `navigationDestination(item:)` push of a scatter destination from the Insights tab | A push keeps the scatter inside the Insights NavigationStack — but the LOCKED design is "opens Explore in scatter mode" (the Explore TAB). A cross-tab router matches the locked intent and the shipped deep-link precedent. **Recommend the router.** |

**Installation:** No package changes. New app-target files (`ScatterPlotView.swift`, `ScatterDeepLink.swift`, `ExploreScatterRouter.swift`, `ScatterUITests.swift`) → run `xcodegen generate` (the EndoApp target globs `Sources/EndoApp`; the UITest target globs `Tests/EndoAppUITests`).

**Version verification:** No new external packages — Package Legitimacy Audit is N/A (see below). All APIs are Apple system frameworks (Swift Charts, SwiftUI) already in use in shipped code.

## Package Legitimacy Audit

**Not applicable.** Phase 16 installs NO external packages. It uses Apple first-party frameworks (Swift Charts, SwiftUI, XCTest/Swift Testing) already shipping in the project, plus the existing zero-dependency local `EndoInsights` SPM package. No registry lookup is needed.

## Architecture Patterns

### System Architecture Diagram

```
                         ┌─────────────────────────────────────────────┐
   USER taps "Scatter"   │                EXPLORE TAB                   │
   segment  ───────────► │  ExploreView (@State mode: .lanes/.scatter)  │
                         │   ├─ mode == .lanes  → existing lanes path   │
                         │   └─ mode == .scatter →                      │
   USER picks X & Y      │        SeriesPickerView (axis variant) ──┐   │
   (quantitative only) ─►│        @State xSeries / ySeries           │   │
                         │                                           ▼   │
                         │     ScatterPairing.pairs(snapshots, ───► [ScatterPoint(x,y,count)]
                         │        x: xSeries, y: ySeries)             │   │
                         │                                           ▼   │
                         │     ScatterPlotView                       │   │
                         │       Chart { ForEach points {            │   │
                         │         PointMark(x,y).symbolSize(count) } │   │
                         │       .chartXScale(domain:)  ◄── SeriesNormalization.domain(for: xSeries.scaleKind)
                         │       .chartYScale(domain:)  ◄── SeriesNormalization.domain(for: ySeries.scaleKind)
                         │       + neutral caption + a11y summary     │   │
                         │     empty / sparse honest states           │   │
                         └───────────────────────▲───────────────────────┘
                                                 │  router.pendingAxes set
   ┌─────────────────────────────────────────┐  │  → onChange → mode=.scatter, X/Y prefilled, tab switch
   │              INSIGHTS TAB                │  │
   │  InsightCardView (.correlation)          │  │
   │   "See the days behind this" affordance  │  │
   │     shown IFF resolver(signalKey) != nil │  │
   │            │                             │  │
   │            ▼                             │  │
   │  ScatterDeepLink.resolve(signalKey) ─────┼──┘
   │    → (xSeries, ySeries)? quantitative    │     ExploreScatterRouter (@Observable, env-injected
   │    → nil HIDES affordance                │      exactly like CheckInRouter): carries the resolved
   │      (presence factor / suppressed)      │      axis-pair value + a tab-switch intent
   └──────────────────────────────────────────┘
```

A reader can trace the deep-link: card → `ScatterDeepLink.resolve(insight.signalKey)` → (if non-nil) router carries the typed `(ExploreSeries, ExploreSeries)` value → Explore tab switches, sets mode `.scatter`, prefills X/Y → `ScatterPairing.pairs` → `ScatterPlotView`.

### Recommended Project Structure
```
Sources/EndoApp/Features/Explore/
├── ExploreView.swift          # EDIT: add mode @State + X/Y @State + scatter branch + router read
├── SeriesPickerView.swift     # EDIT or sibling: add a two-axis (X/Y) quantitative-only variant
├── ScatterPlotView.swift      # NEW: the Chart{PointMark} scatter + caption + a11y summary + states
├── ScatterAxisPicker.swift    # NEW (optional): the X/Y Menu/Picker pair (quantitative-only)
├── ExploreCopy.swift          # EDIT: add scatter chrome strings + register in CopyAuditTests
Sources/EndoApp/Features/Insights/
├── ScatterDeepLink.swift      # NEW: pure resolver signalKey → (ExploreSeries, ExploreSeries)?
├── InsightCardView.swift      # EDIT: add affordance to .correlation supporting area (iff resolver != nil)
├── InsightsView.swift         # EDIT: thread the router/affordance action through the card
Sources/EndoApp/App/
├── ExploreScatterRouter.swift # NEW: @Observable router + EnvironmentKey (mirror CheckInRouter.swift)
├── AppGate.swift              # EDIT: inject router; Explore tab reads it (cross-tab switch)
├── AppDependencies.swift      # EDIT: add -uitest-seed-scatter-link (stress→pain) seed
Tests/EndoAppUITests/
├── ScatterUITests.swift       # NEW: mirror ExploreUITests harness (seeded a11y-id E2E)
Tests/EndoAppTests/
├── ScatterDeepLinkTests.swift # NEW: pure resolver unit tests (Swift Testing)
```

### Pattern 1: The PointMark scatter (mirror the shipped InsightTrendChart recipe)
**What:** One `PointMark` per `ScatterPoint`; `count` drives `.symbolSize`; fixed real-unit domains from `SeriesNormalization.domain`.
**When to use:** The Component-C scatter plot body (after X and Y are chosen and `pairs(...)` returns ≥ threshold points).
**Example:**
```swift
// Source: shape mirrors shipped InsightTrendChart.swift:179-234 (PointMark + .chartXScale/.chartYScale(domain:))
// xSeries/ySeries are the chosen quantitative ExploreSeries; points = ScatterPairing.pairs(...)
Chart {
    ForEach(points, id: \.self) { p in          // ScatterPoint is Hashable [VERIFIED: ScatterPairing.swift:37]
        PointMark(
            x: .value(xSeries.displayName, p.x),
            y: .value(ySeries.displayName, p.y)
        )
        .symbolSize(by: .value("Days", p.count)) // count-encode — NEVER jitter [VERIFIED: UI-SPEC §Scatter Spec]
        .foregroundStyle(Color.accentColor)       // single hue; count is the only varying encoding
        .accessibilityLabel(Text(SeriesNormalization.readout(p.x, kind: xSeries.scaleKind, unit: xSeries.unit)
            + ", " + SeriesNormalization.readout(p.y, kind: ySeries.scaleKind, unit: ySeries.unit)))
        .accessibilityValue(Text("\(p.count) days"))
    }
}
.frame(height: 320)                               // scatter-plot-height token [CITED: UI-SPEC §Spacing]
.chartXScale(domain: SeriesNormalization.domain(for: xSeries.scaleKind) ?? 0...10)
.chartYScale(domain: SeriesNormalization.domain(for: ySeries.scaleKind) ?? 0...10)
.chartXAxis { AxisMarks() }                       // real-unit ticks
.chartYAxis { AxisMarks() }
.chartPlotStyle { $0.padding(8) }                 // chart-plot-inset so (0,0)/(10,10) corner marks aren't clipped
.animation(reduceMotion ? nil : .default, value: points)   // Reduce Motion parity [VERIFIED: InsightTrendChart.swift:233]
```
Note: `SeriesNormalization.domain(for:)` returns `ClosedRange<Double>?` (nil for `.presenceEvent`) [VERIFIED: `SeriesNormalization.swift:34`]. Because the axis picker is quantitative-only, the `?? 0...10` fallback is unreachable in practice but keeps the call total (no force-unwrap in a health app).

### Pattern 2: The deep-link resolver (signalKey → optional axis pair)
**What:** A pure app-target struct mapping the `.correlation` `Insight.signalKey` to two quantitative `ExploreSeries`, or `nil` (HIDE affordance).
**When to use:** Both in `InsightCardView` (to decide whether to SHOW the affordance) and to compute the payload the router carries.
**Example:** see `## The Deep-Link Resolver (SCAT-04)` below — it is the load-bearing mechanic and is documented in full there.

### Pattern 3: Cross-tab router (mirror CheckInRouter)
**What:** An `@MainActor @Observable` class carrying a pending axis-pair value + tab-switch intent, injected via `EnvironmentKey`.
**When to use:** The Insights-tab affordance sets `router.pendingScatter = (x, y)`; the Explore tab observes it (`.onChange`) and applies mode `.scatter` + X/Y, then clears it.
**Example:**
```swift
// Source: mirror shipped CheckInRouter.swift:16-45 (@MainActor @Observable + EnvironmentKey + assumeIsolated default)
@MainActor @Observable
final class ExploreScatterRouter {
    /// The resolved axis pair to prefill the scatter with (set by the Insights affordance, cleared on consume).
    var pendingScatter: (x: ExploreSeries, y: ExploreSeries)? = nil
    init() {}
}
private struct ExploreScatterRouterKey: EnvironmentKey {
    static var defaultValue: ExploreScatterRouter { MainActor.assumeIsolated { ExploreScatterRouter() } }
}
extension EnvironmentValues {
    var exploreScatterRouter: ExploreScatterRouter {
        get { self[ExploreScatterRouterKey.self] }
        set { self[ExploreScatterRouterKey.self] = newValue }
    }
}
```
**Cross-tab switch nuance:** the shipped `ConsentedShell` uses a bare `TabView` with NO `selection` binding [VERIFIED: `AppGate.swift:52`]. To programmatically switch to the Explore tab the planner must add a `@State selectedTab` + `TabView(selection:)` + `.tag(...)` on each tab in `ConsentedShell`, and the affordance sets both the router payload AND the selected tab. **This is the one structural edit to the tab shell.** (Tuple is not `Equatable`/`Hashable` for `.onChange(of:)`; wrap the pending pair in a small `Equatable` struct `PendingScatter { let x: ExploreSeries; let y: ExploreSeries }` so `.onChange(of: router.pendingScatter)` compiles — `ExploreSeries` is `Equatable` by id [VERIFIED: `ExploreSeries.swift:101`].)

### Anti-Patterns to Avoid
- **Parsing the audited public `.correlation` copy** to recover factor/target. The resolver consumes the STRUCTURAL `signalKey`, never `insight.headline`/`insight.detail` (the Phase-11 discipline — re-deriving semantics from prose is the forbidden fragile-substring pattern).
- **Jitter / `Double.random` to spread overplotted points.** Count-encoding is the ONLY honest fix [VERIFIED: `ScatterPairing.swift:42-43`, UI-SPEC honesty constraint 2].
- **Data-driven axis min/max.** Always `SeriesNormalization.domain(for:)` — a data-driven domain hides that severity never exceeded 6 [VERIFIED: `SeriesNormalization.swift:27`, UI-SPEC §Axis contract].
- **Persisting the mode / X / Y selection.** Ephemeral `@State` only — persisting leaks health-interest metadata [VERIFIED: `ExploreView.swift:11-14`, LOCKED privacy invariant].
- **Reusing `-uitest-seed-recommendations` for the deep-link E2E.** Its `dairy→pelvicPain` correlation resolves to `nil` (dairy is presence-only) — the affordance is correctly HIDDEN, so it cannot assert a prefill. Need a stress→pain seed.
- **`navigationDestination` push from the Insights tab.** That keeps the scatter inside the Insights stack; the LOCKED design opens the Explore TAB.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Both-logged pairing + count aggregation | A new intersection/aggregation loop | `ScatterPairing.pairs(_:x:y:calendar:)` | SHIPPED, unit-tested in Phase 14; absence≠0 + count + quantitative-only graceful-degrade already correct [VERIFIED: `ScatterPairing.swift`] |
| Fixed axis domains | Literal `0...10` etc. scattered in the view | `SeriesNormalization.domain(for: scaleKind)` | Single source of truth; covers all 5 quantitative kinds [VERIFIED: `SeriesNormalization.swift:34-43`] |
| Per-point real-unit readout | String-format the values inline | `SeriesNormalization.readout(_:kind:unit:)` | Honest (real unit, strips trailing `.0`), audit-aligned [VERIFIED: `SeriesNormalization.swift:75-80`] |
| Quantitative-only axis catalog + suppression filter | A new filter | `ExploreSelection.offered(suppressed:)` filtered to `scaleKind.isQuantitative` | Reuses the shipped suppression gate (hides both bleeding series) [VERIFIED: `ExploreSelection.swift:60-67`] |
| Cross-tab deep-link plumbing | A bespoke navigation mechanism | `@Observable` env-router (CheckInRouter shape) | Established, consent-safe precedent [VERIFIED: `CheckInRouter.swift`] |
| Wrapping chip layout | A new layout | `FlowLayout` (already in `SeriesPickerView.swift`) | Shipped system `Layout` [VERIFIED: `SeriesPickerView.swift:155`] |

**Key insight:** Phase 16 writes ZERO new pure-domain logic except the deep-link resolver (which is a string→catalog mapping, fully unit-testable without rendering). Everything else composes shipped, tested primitives.

## Runtime State Inventory

Not a rename/refactor/migration phase — this section is **OMITTED** (greenfield additive feature; no stored data, service config, OS-registered state, secrets, or build artifacts carry a renamed string). The only persistence touched is the in-memory E2E seed store; no schema/engine/CloudKit/PDF changes (LOCKED).

## Common Pitfalls

### Pitfall 1: Assuming the Insight carries the typed factor/target
**What goes wrong:** The planner designs a "thread the typed `CorrelationFinding` to the card" task that cannot compile — `Factor` and `CorrelationFinding` are package-internal and never reach the app target.
**Why it happens:** CONTEXT/UI-SPEC say "prefer a TYPED carrier"; the natural reading is "thread the engine's typed struct." But `Insight` (the only thing the card holds) carries only `signalKey` + copy [VERIFIED: `Insight.swift:65-89`], and `CorrelationFinding`/`Factor` have no `public` [VERIFIED: `CorrelationEngine.swift:27`, `:364`; `Factor.swift:27`].
**How to avoid:** Resolve from `Insight.signalKey` (the public, stable, value-independent structural key) into a TYPED `(ExploreSeries, ExploreSeries)` value. The resolver's OUTPUT is typed (honoring "carries a value, never re-derives by parsing copy"); its INPUT is the structural key, not prose. Document this explicitly in the plan so a reviewer doesn't flag it as copy-parsing.

### Pitfall 2: The deep-link E2E seed resolves to a HIDDEN affordance
**What goes wrong:** The E2E reuses `-uitest-seed-recommendations` (dairy→pelvicPain) and asserts the affordance — but dairy is presence-only, the resolver returns `nil`, the affordance is hidden, and the test fails or (worse) silently asserts nothing.
**Why it happens:** The shipped seed's correlation is a FOOD factor [VERIFIED: `AppDependencies.swift:498`]. Food/activity/medication/PRN/cycle-phase factors all map to NO quantitative axis.
**How to avoid:** Add `-uitest-seed-scatter-link` seeding a QUANTITATIVE-factor correlation — `stress.high → pelvicPain` (stress 0…10 ↔ pelvicPain severity 0…10). Both sides resolve to a quantitative `ExploreSeries`. Mirror `seedRecommendationCorrelation`'s shape (alternate high-stress/high-severity vs low/low across ~22 days). Also add a NEGATIVE E2E case (the dairy seed) asserting the affordance is ABSENT (`insights.card.<signalKey>.scatterLink` does not exist).

### Pitfall 3: Unregistered ExploreCopy string escapes the ClaimsPolicy scan
**What goes wrong:** A new scatter caption/empty/sparse/summary/affordance string is rendered before being added to `ExploreCopy.auditableStrings` + registered in `CopyAuditTests` → it silently escapes the substring scans (gendered tokens, causal verbs, numeric confidence).
**Why it happens:** `auditableStrings` is a hand-maintained array [VERIFIED: `ExploreCopy.swift:91-104`]; a view can reference a `static let` that isn't in the array.
**How to avoid:** In the SAME change that adds each string, append it to `auditableStrings` and register in `CopyAuditTests`. Pre-verified wording is in the UI-SPEC Copywriting Contract (the "together"→"on too few of the same days" trap is already resolved there). The interpolated forms (`scatterCaption(days:)`, `scatterSummary(x:y:days:)`, the per-point readout) register a representative instance — same posture as `eventLaneSummary` [VERIFIED: `ExploreCopy.swift:102`].

### Pitfall 4: Stats drift onto the scatter
**What goes wrong:** A "helpful" fitted line / r-value / "% impact" / trend shading creeps into the view, view-model, or a copy string — crossing the wellness→medical-device line.
**Why it happens:** Scatter plots invite a regression line by convention.
**How to avoid:** No regression math anywhere near the scatter. Grep gate in verification: no `r =`, no `%`, no `p `, no `LinearGradient` "trend", no slope/coefficient near `ScatterPlotView`. `ScatterPoint` deliberately carries only x/y/count [VERIFIED: `ScatterPairing.swift:37-50`]; the caption `{N}` is a factual tally (legal like `supportingEvidence`), never a statistic.

### Pitfall 5: Host-simulator flaky "TEST FAILED" runner crashes
**What goes wrong:** `xcodebuild` reports "TEST FAILED" from a runner crash on the iPhone-16 simulator that is NOT an assertion failure, masking a green suite.
**Why it happens:** Known host-simulator flakiness (per build notes).
**How to avoid:** Use `-only-testing:EndoAppUITests/ScatterUITests/<method>` to run test-by-test; never bare `-only-testing:EndoAppTests`. Treat a runner-crash line distinct from an `#expect`/`XCTAssert` failure (re-run the single method to confirm). Use `swift test --package-path EndoInsights` only for pure-package additions (Phase 16 adds none to the package — the resolver lives in the app target, so its unit test is an `EndoAppTests` Swift Testing file run via `xcodebuild -only-testing:EndoAppTests/ScatterDeepLinkTests`).

## Code Examples

### The two-axis quantitative-only picker (X / Y single-selects)
```swift
// Quantitative-only offered series (suppression hides both bleeding series) — reuse the shipped filter.
// Source: ExploreSelection.offered(suppressed:) [VERIFIED: ExploreSelection.swift:60] + ScaleKind.isQuantitative [VERIFIED: ExploreSeries.swift:41]
private var quantitativeAxisOptions: [ExploreSeries] {
    ExploreSelection.offered(suppressed: suppressed).filter { $0.scaleKind.isQuantitative }
}

// Each axis is ONE single-select Menu/Picker of displayNames. X and Y are independent @State.
Menu {
    ForEach(quantitativeAxisOptions) { s in
        Button(s.displayName) { xSeries = s }
            .accessibilityIdentifier("explore.scatter.axisX.\(s.id)")   // value-independent id [CITED: UI-SPEC §B]
    }
} label: {
    Label(xSeries?.displayName ?? ExploreCopy.scatterAxisPlaceholder, systemImage: "x.circle")
}
.accessibilityIdentifier("explore.scatter.axisX")
```
Picking the same series for both axes is allowed (a self-vs-self diagonal — no special-casing) [CITED: UI-SPEC §B].

### The mode toggle (segmented Picker, ephemeral @State, default .lanes)
```swift
// Source: mirrors the shipped segmented Picker in SeriesPickerView.swift:78-87
enum ExploreMode: String, CaseIterable { case lanes, scatter }   // ephemeral; NEVER persisted
@State private var mode: ExploreMode = .lanes                      // default .lanes (shipped behavior unchanged)

Picker("Mode", selection: $mode) {
    Label(ExploreCopy.modeLanes, systemImage: "square.stack.3d.up").tag(ExploreMode.lanes)
        .accessibilityIdentifier("explore.mode.lanes")
    Label(ExploreCopy.modeScatter, systemImage: "chart.dots.scatter").tag(ExploreMode.scatter)
        .accessibilityIdentifier("explore.mode.scatter")
}
.pickerStyle(.segmented)
.accessibilityIdentifier("explore.mode")
```
Pinned at the TOP of the existing `ScrollView { VStack(spacing: 24) … }` scaffold [VERIFIED: `ExploreView.swift:62-63`]; in `.scatter` mode the lanes block is swapped for the axis-picker + `ScatterPlotView`.

### The two honest scatter states (reuse the shipped honestState helper)
```swift
// Source: ExploreView.honestState(heading:body:identifier:) [VERIFIED: ExploreView.swift:233]
// Empty — neither axis chosen:
if xSeries == nil || ySeries == nil {
    honestState(heading: ExploreCopy.scatterEmptyHeading, body: ExploreCopy.scatterEmptyBody,
                identifier: "explore.scatter.empty")
// Sparse — both chosen but too few both-logged days:
} else if points.reduce(0) { $0 + $1.count } < Self.minScatterDays {     // count-sum threshold (pure, testable)
    honestState(heading: ExploreCopy.scatterSparseHeading, body: ExploreCopy.scatterSparseBody,
                identifier: "explore.scatter.sparse")
} else {
    ScatterPlotView(points: points, xSeries: xSeries!, ySeries: ySeries!)
}
```
The sparse threshold mirrors the Phase-15 per-lane sparse pattern (a pure `[ScatterPoint]` count-sum check) [VERIFIED: `ExploreView.swift:197-207`]. UI-SPEC suggests `< ~5` overlapping days [CITED: UI-SPEC §D].

## The Deep-Link Resolver (SCAT-04) — the load-bearing mechanic

**What the card actually has:** the `.correlation` `Insight`, whose only machine-readable identity is `signalKey` [VERIFIED: `InsightCardView.swift:33` derives `idRoot` from `insight.signalKey`; `Insight.swift:78`].

**The signalKey grammar (the structural key to parse — NOT prose):**
- severity arm: `correlation.<factor.key>.symptom.<SymptomType.rawValue>` [VERIFIED: `CorrelationEngine.swift:279`]
- presence arm (symptom target): `correlation.<factor.key>.symptom.<SymptomType.rawValue>` [VERIFIED: `:327` via `PresenceTarget.keyFragment` `:244`]
- presence arm (bleeding target): `correlation.<factor.key>.bleeding` [VERIFIED: `:327` + `:245`]

**The `factor.key` grammar** [VERIFIED: `Factor.swift:51-63`]:

| factor.key | scatter-axis mapping | ExploreSeries id |
|------------|----------------------|------------------|
| `stress.high` | quantitative → stress | `stress` (0…10) |
| `mood.low` | quantitative → mood | `mood` (1…5) |
| `sleep.poor` | quantitative → sleep hours* | `sleep.hours` (0…24)* |
| `food.<tag>` | **presence-only → nil** | — (HIDE affordance) |
| `activity.present`, `activity.<type>` | **presence-only → nil** | — (HIDE) |
| `medication.taken` | **presence-only → nil** | — (HIDE) |
| `prn.taken` | **presence-only → nil** | — (HIDE) |
| `cyclePhase.<phase>` | **presence-only → nil** | — (HIDE) |

\* **Discrepancy flag for the planner:** the correlation engine binarizes `sleep.poor` from `sleepQuality == .poor` (a categorical), while the scatter quantitative sleep axis is `sleep.hours` (continuous hours). Mapping `sleep.poor → sleep.hours` shows the user the raw hours behind a "poor-sleep" association — defensible and honest, but it is a SEMANTIC bridge (poor-quality ≠ hours). **Recommendation:** in v1.1 map ONLY the unambiguous quantitative factors `stress.high → stress` and `mood.low → mood`; return `nil` for `sleep.poor` too (conservative — no semantic bridge). Confirm with the user during planning if `sleep.poor → sleep.hours` is wanted. [ASSUMED — A1]

**The target grammar:**

| target fragment | scatter-axis mapping | ExploreSeries id |
|-----------------|----------------------|------------------|
| `symptom.<rawValue>` | quantitative → that symptom's severity | `symptom.<rawValue>.severity` (0…10) [VERIFIED: `ExploreSeries.swift:209`] |
| `bleeding` | quantitative → bleeding intensity, IFF not suppressed | `bleeding.intensity` (1…4) [VERIFIED: `ExploreSeries.swift:252`] |

**Resolver shape (app target, pure, unit-testable):**
```swift
// Source: signalKey grammar [VERIFIED: CorrelationEngine.swift:279/327, Factor.swift:51, PresenceTarget.swift:244]
struct ScatterDeepLink {
    /// Map a .correlation signalKey to two quantitative scatter axes, or nil → HIDE the affordance.
    /// `suppressed` carries the Phase-14 bleeding gate: a bleeding target under suppression resolves nil.
    static func resolve(signalKey: String, suppressed: Bool,
                        catalog: [ExploreSeries] = ExploreSeriesCatalog.all) -> (x: ExploreSeries, y: ExploreSeries)? {
        // 1. Strip the "correlation." prefix; split the remainder into factor.key and target fragment.
        // 2. factor.key → optional quantitative X series (stress.high→"stress", mood.low→"mood"; else nil).
        // 3. target fragment → optional quantitative Y series:
        //      "symptom.<raw>" → "symptom.<raw>.severity"
        //      "bleeding"      → suppressed ? nil : "bleeding.intensity"
        // 4. Return (x, y) only if BOTH resolved AND both are .isQuantitative; else nil.
        // Look ids up in `catalog` (never construct ExploreSeries by hand) so a catalog edit can't drift.
        // ...
    }
}
```
**Parsing robustness:** the factor.key itself contains a dot (`stress.high`, `food.dairy`), and the target also (`symptom.pelvicPain`, `bleeding`). Parse by KNOWN suffixes, not a naive `.split(".")`: detect the target by checking whether the tail matches `bleeding` or `symptom.<known rawValue>`, then the prefix (minus `correlation.`) is the factor.key. Enumerate factor keys / symptom rawValues from the catalog + `SymptomType.allCases` rather than free-form splitting, so a multi-dot key can never mis-segment. Unit-test every factor×target combination including the nil cases.

**Affordance gating in the card:** `InsightCardView` computes `ScatterDeepLink.resolve(signalKey: insight.signalKey, suppressed: …)` once; renders the `arrow.up.forward.square` "See the days behind this" affordance (a11y id `\(idRoot).scatterLink`) IFF non-nil [CITED: UI-SPEC §E]. Suppression flows in the same way the picker gets it — from the latest snapshot's `suppressed` (the card's screen/coordinator must supply it; mirror `ExploreView.suppressed` [VERIFIED: `ExploreView.swift:56`]).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `LinearMark`/regression on scatter | `PointMark` cloud + count-encode, NO fit | this milestone's honesty posture | Least causally-suggestive encoding (IEEE VIS 2019) [CITED: UI-SPEC intro] |
| Bare `TabView` (no selection) | `TabView(selection:)` for programmatic cross-tab deep-link | Phase 16 (this phase) | One structural edit to `ConsentedShell` [VERIFIED: `AppGate.swift:52`] |

**Deprecated/outdated:** none relevant — all APIs used are current iOS 18+ system frameworks already in the shipped codebase.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `sleep.poor` should resolve to `nil` (conservative; no quality→hours bridge) rather than to `sleep.hours` | Deep-Link Resolver | If user WANTS sleep deep-links, the affordance is hidden on sleep-correlation cards — confirm during planning |
| A2 | `.symbolSize(by:)` is the primary count cue (opacity optional) | Pattern 1 | If size alone is insufficiently legible at large counts, add opacity — additive, low risk |
| A3 | Sparse threshold `< 5` both-logged days (count-sum) | Code Examples / Validation | A different threshold is a one-line constant change; the UI-SPEC says "~5" |
| A4 | `Factor.swift` threshold constants (`highStressThreshold`, etc.) are correct as shipped | Deep-Link grammar | Tagged `[ASSUMED]` in source itself; does not affect the resolver (it keys on the factor.key string, not the threshold) |

## Open Questions (RESOLVED)

> Autonomous decider resolutions (2026-06-16), binding for the planner:
> 1. **`sleep.poor` deep-link → resolve to `nil`** (conservative). Only `stress.high`→`stress` and `mood.low`→`mood` map cleanly to a quantitative axis; the categorical poor-sleep factor does NOT bridge to `sleep.hours` in v1.1 — the affordance is simply HIDDEN for sleep-quality correlations (cheap to add later if asked). Presence-only factors (food/activity/medication/PRN) and suppressed bleeding likewise resolve to `nil` (affordance hidden).
> 2. **A11y path = the worded `scatterSummary` element is PRIMARY** (point count + both axis names + both-logged-day basis); per-mark `accessibilityLabel`/`accessibilityValue` are attached but secondary. NO Audio Graph (a 2-axis cloud has no single honest value axis to sonify). This is the locked non-gesture path.

1. **Should `sleep.poor` deep-link to `sleep.hours`?**
   - What we know: the engine's `sleep.poor` factor is categorical (`sleepQuality == .poor`); the quantitative sleep axis is hours.
   - What's unclear: whether showing raw hours behind a poor-sleep association is the intended honest mapping or an unwanted semantic bridge.
   - Recommendation: resolve to `nil` in v1.1 (only `stress.high`/`mood.low` map); revisit if the user asks. Cheap to add later.

2. **Per-mark accessibility on a count-aggregated cloud.**
   - What we know: UI-SPEC wants per-mark `accessibilityLabel`/`accessibilityValue` + a one-line worded summary.
   - What's unclear: Swift Charts per-`PointMark` a11y in a dense `ForEach` can be verbose for VoiceOver.
   - Recommendation: lead with the worded `scatterSummary` element (the primary a11y path per UI-SPEC); attach per-mark labels but accept they are secondary. No Audio Graph (a 2-axis cloud implies a single value axis dishonestly).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Swift Charts | Scatter `PointMark` | ✓ (system, already used) | iOS 18+ | — |
| SwiftUI segmented Picker / Menu / @Observable | Mode toggle, axis pick, router | ✓ | iOS 18+ | — |
| EndoInsights (`ScatterPairing` etc.) | Pure pairing/domains | ✓ (shipped) | Phase 14 | — |
| XcodeGen | New app-target + UITest files | ✓ (project uses it) | — | — |
| iPhone 16 simulator | E2E | ✓ (per build notes) | — | run-test-by-test on flaky "TEST FAILED" |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** the simulator's flaky runner-crash "TEST FAILED" — fallback is per-method `-only-testing` runs.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (`@Test`/`#expect`) for the pure resolver; XCUITest for E2E [CITED: STACK.md, VERIFIED: `ExploreUITests.swift`] |
| Config file | none — Xcode targets `EndoAppTests` (unit) + `EndoAppUITests` (E2E); `EndoInsights` SwiftPM for the package (no new package tests this phase) |
| Quick run command | `xcodebuild test -only-testing:EndoAppTests/ScatterDeepLinkTests -destination 'platform=iOS Simulator,name=iPhone 16'` |
| Full suite command | `xcodebuild test -only-testing:EndoAppUITests/ScatterUITests -destination 'platform=iOS Simulator,name=iPhone 16'` (run-by-method on flaky runner crash) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SCAT-01 | Switch Explore to scatter; pick two quantitative axes | E2E | `…/ScatterUITests/testScatterModeRendersWithTwoAxes` | ❌ Wave 0 |
| SCAT-01 | Picker offers quantitative-only; suppression hides bleeding axis | E2E | `…/ScatterUITests/testSuppressedUserHasNoBleedingAxis` | ❌ Wave 0 |
| SCAT-02 | Identical day-pairs stack via count-encoded size (dense fixture → ONE sized mark, readout "N days") | E2E + pure | `ScatterPairing` count already unit-tested (Phase 14); E2E asserts the readout via a11y id | partial (pure ✅ shipped; E2E ❌ Wave 0) |
| SCAT-03 | Descriptive-only: neutral caption, no fit/r-value/% ; honest sparse state | E2E + grep gate | `…/testScatterCaptionIsNeutral`, `…/testSparseStateWhenTooFewDays` + verification grep | ❌ Wave 0 |
| SCAT-04 | Quantitative correlation card → prefilled scatter (stress→pain) | pure + E2E | `ScatterDeepLinkTests` (resolver) + `…/testCorrelationCardDeepLinkPrefillsScatter` | ❌ Wave 0 |
| SCAT-04 | Presence factor (dairy) / suppressed bleeding → affordance HIDDEN | pure + E2E | `ScatterDeepLinkTests` nil cases + `…/testPresenceFactorCardHasNoScatterLink` | ❌ Wave 0 |

### Honesty invariants → test layer
- Absence ≠ 0 (a missing-factor day produces NO mark): pure `ScatterPairing` (shipped Phase 14) + a logged-`(0,0)` E2E assertion that a real corner point exists.
- Count never jitter: `ScatterPoint` type shape (shipped) + grep gate (no `random`/jitter near `ScatterPlotView`).
- No stats drift: verification grep (no `r =` / `%` / `p ` / regression near the scatter) + `CopyAuditTests` over the new `ExploreCopy` strings.
- Deep-link carries a value, never re-derives: `ScatterDeepLinkTests` asserts the resolver maps signalKey→typed pair; the router carries the typed `ExploreSeries` value.

### Sampling Rate
- **Per task commit:** `xcodebuild test -only-testing:EndoAppTests/ScatterDeepLinkTests …` (pure resolver, < 30s).
- **Per wave merge:** the relevant `ScatterUITests` methods.
- **Phase gate:** full `ScatterUITests` + `EndoAppTests` green (run-by-method if the simulator runner flakes) before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `Tests/EndoAppTests/ScatterDeepLinkTests.swift` — covers SCAT-04 resolver (every factor×target incl. nil cases)
- [ ] `Tests/EndoAppUITests/ScatterUITests.swift` — covers SCAT-01/02/03/04 (clone `ExploreUITests` harness)
- [ ] `AppDependencies` `-uitest-seed-scatter-link` seed (stress.high→pelvicPain, quantitative both sides) — REQUIRED for the SCAT-04 prefill E2E (the shipped dairy seed resolves to a hidden affordance)
- [ ] `CopyAuditTests` registration of every new `ExploreCopy` scatter string (in the same change that adds each)
- [ ] `xcodegen generate` after adding `ScatterPlotView.swift` / `ScatterDeepLink.swift` / `ExploreScatterRouter.swift` / `ScatterUITests.swift`

## Security Domain

`security_enforcement: true`, ASVS level 1 [VERIFIED: `.planning/config.json`].

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No accounts (iCloud identity only) — unchanged |
| V3 Session Management | no | N/A |
| V4 Access Control | yes | Consent gate: the Explore tab + Insights cards are built only in `AppGate`'s `.consented` branch [VERIFIED: `AppGate.swift:33-39`]; the router carries a non-sensitive intent value, not health data (mirror `CheckInRouter`'s T-03-14 posture) |
| V5 Input Validation | yes | The `signalKey` resolver must DEGRADE GRACEFULLY on an unrecognized/malformed key (return `nil` → hide affordance), never crash — same defensive parse posture as the `-uitest-select-day` parser [VERIFIED: `AppDependencies.swift:142-153`] |
| V6 Cryptography | no | None introduced |

### Known Threat Patterns for this stack
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Health-interest metadata leak via persisted mode/axis selection | Information disclosure | Ephemeral `@State` only; NO persistence wrapper (LOCKED) [VERIFIED: `ExploreView.swift:11-14`] |
| Suppressed user sees a bleeding axis or bleeding deep-link | Information disclosure | `ExploreSelection.offered(suppressed:)` filter for the picker + resolver returns `nil` for a `bleeding` target under suppression [VERIFIED: `ExploreSelection.swift:60`, `ExploreSeries.swift:148`] |
| Causal/stats claim creeps onto the scatter | Tampering (with the honesty contract) | `CopyAuditTests` over new strings + verification grep gate; `ScatterPoint` carries no statistic [VERIFIED] |
| Deep-link surfaces health UI before consent | Elevation of privilege | Cross-tab router observed only inside the consented shell (like `CheckInRouter`) [VERIFIED: `AppGate.swift:33`] |
| Test seed writes to a real store | Tampering | `-uitest-seed-scatter-link` paired with `-uitest-reset` in-memory store (mirror every existing seed) [VERIFIED: `AppDependencies.swift:88`] |

## Sources

### Primary (HIGH confidence)
- Shipped source (read this session): `ScatterPairing.swift`, `SeriesNormalization.swift`, `ExploreSeries.swift`, `CorrelationEngine.swift`, `Factor.swift`, `Insight.swift`, `InsightCardView.swift`, `InsightsView.swift`, `ExploreView.swift`, `SeriesPickerView.swift`, `ExploreSelection.swift`, `ExploreCopy.swift`, `AppGate.swift`, `CheckInRouter.swift`, `AppDependencies.swift`, `InsightTrendChart.swift`, `ExploreUITests.swift` — the exact reuse + deep-link + E2E contracts.
- `16-CONTEXT.md`, `16-UI-SPEC.md` — LOCKED decisions + the approved design contract (scatter spec, resolver intent, copy strings).
- `.planning/config.json` — nyquist_validation true, security_enforcement true (ASVS 1).
- `.planning/REQUIREMENTS.md` — SCAT-01..04 acceptance criteria.

### Secondary (MEDIUM confidence)
- `STACK.md` — Swift Charts / Swift Testing / XCUITest stack (corroborated by shipped usage).

### Tertiary (LOW confidence)
- None — every claim is grounded in shipped source or the locked design docs.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all APIs are shipped first-party frameworks already used in the codebase.
- Architecture (resolver + router): HIGH — the load-bearing unknown (Insight carries only signalKey) is resolved by direct source inspection; the router mirrors a shipped precedent.
- Pitfalls: HIGH — the E2E-seed mismatch and the package-internal-typed-carrier facts are verified, not assumed.

**Research date:** 2026-06-16
**Valid until:** 2026-07-16 (stable — first-party frameworks + shipped local contracts; re-verify only if EndoInsights public surface changes)
