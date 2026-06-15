# Phase 15: Explore Tab & Overlay Lanes - Research

**Researched:** 2026-06-15
**Domain:** SwiftUI + Swift Charts (iOS 18) — N stacked per-scale lanes sharing ONE absolute-date X domain and ONE `chartXSelection` scrub state; single multi-series tooltip; event-marker lanes; chip picker with cap; deterministic E2E seam
**Confidence:** HIGH (every reuse contract verified against the shipped source this session; the one external API question — sibling-Chart selection sharing on iOS 18 — corroborated by Apple WWDC/docs + dev-forum threads, MEDIUM-HIGH)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Aligned per-scale lanes (THE central decision).** Each selected quantitative series renders in its OWN horizontal lane (real-unit Y domain from `SeriesNormalization.domain`), lanes stacked vertically, ALL sharing one absolute-date X axis. NO dual independent y-axis, NO silent 0–100 normalization in the default view. A normalized "shape view" is OUT of scope for v1.1 (not "both").
- **Event-marker lane.** Binary food tags (and bleeding-as-event) render as a row of date-aligned ticks/dots UNDER the quantitative lanes (Bearable day-highlight pattern), never a fake 0/1 line. Source: `ExploreSeriesExtraction.occurrenceDays(...) -> [Date]`.
- **Suppression → hide the bleeding series entirely** for a suppressed user. The Explore picker must ALSO not offer bleeding series when data is suppressed (no empty lane, no offered-but-empty chip). Phase-14 accessors already gate bleeding under `!snap.suppressed`.
- **Honesty / accessibility invariants (carry over):** unlogged days stay GAPS in every lane (absence ≠ 0); reuse `ChartSeriesGrouping` per (series × contiguous-run). Series colors colorblind-safe + never the sole signal; chart never the sole carrier of meaning (text summary parity). PDF report path stays UNTOUCHED. New app-target files → `xcodegen generate`.

### Claude's Discretion
- **Series picker:** toggle chips (catalog `displayName`), add/remove per tap. Quantitative series capped at 3–4 simultaneously with a clear "remove one to add a new one" affordance; event lanes a separate small allowance. Selection state is view-local `@State` (EPHEMERAL — do NOT `@AppStorage` it; persisting series keys leaks health-interest metadata outside the consent-gated store).
- **Multi-series scrub tooltip:** reuse the Phase-13 `ChartScrubSelection` resolver per series; one tooltip for the scrubbed day listing EACH visible series' value in its real unit — or a per-series "Not logged" line (gap-honest, never 0). Free scrub OK here (Explore's chart is NOT inside a scrolling List).
- **Time range:** reuse dashboard presets / axis vocabulary (absolute dates, gap breaks). Same `TrendWindow` family (EXPL-06).
- **Honest empty/sparse states:** "pick a series" / "not enough data" (EXPL-07). All new chrome copy through a NEW `ExploreCopy` enum REGISTERED in `CopyAuditTests` BEFORE written.
- **Tab:** third tab labeled "Explore"; a11y id on the tab-item Label (`tab.explore`). Lanes-evocative SF Symbol distinct from Insights' `chart.line.uptrend.xyaxis` (UI-SPEC chose `square.stack.3d.up`).

### Deferred Ideas (OUT OF SCOPE)
- Scatter view + correlation-card deep-link → Phase 16.
- Normalized "shape view" toggle → not in v1.1 (revisit on demand).
- Saved/recent Explore configurations (EXPL-09), tap-point→day-log, landscape, lag-shift → v1.1.x/v2.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| EXPL-01 | Open a new Explore screen as a third consent-gated tab | §Architecture Patterns "Tab seam"; `AppGate.ConsentedShell` add third `.tabItem` with `tab.explore` id on the `Label`; built only in `.consented` branch [VERIFIED: AppGate.swift] |
| EXPL-02 | Toggle series via chips from the catalog, capped 3–4 quantitative with remove-to-add | §"ExploreSelection (pure cap model)"; `ExploreSeriesCatalog.all` + `Set<ExploreSeries>` (Hashable from id) [VERIFIED: ExploreSeries.swift] |
| EXPL-03 | Quantitative series → aligned per-scale lanes, ONE time axis, real units (no dual-y, no normalization) | §"Lane composition recipe"; one `Chart` per series at `lane-quant-height`, `chartYScale(domain: SeriesNormalization.domain(for:))`, shared `chartXScale(domain:)` [VERIFIED: SeriesNormalization.swift, InsightTrendChart.swift] |
| EXPL-04 | Binary tags + bleeding-as-event → event-marker lanes, never 0/1 lines | §"Event-marker lane recipe"; `PointMark`/`RuleMark` at each `occurrenceDays(...)` date, `chartYScale` hidden, `lane-event-height` 44 [VERIFIED: ExploreSeriesExtraction.swift] |
| EXPL-05 | Scrub reads every visible series' value (or "not logged") for the selected day | §"Shared scrub + multi-series tooltip"; ONE `@State Date?` bound to every lane's `chartXSelection`; resolve per series via `ChartScrubSelection.resolve` [VERIFIED: ChartScrubSelection.swift] |
| EXPL-06 | Switch time range with dashboard presets (absolute dates, gap breaks) | §"Range control"; reuse `TrendWindow.allCases` + the `xDomain` derivation copied from `InsightTrendChart` [VERIFIED: Insight.swift, InsightTrendChart.swift] |
| EXPL-07 | Honest empty/sparse states; unlogged days stay gaps in every lane | §"Empty / sparse states"; `ExploreCopy` empty/sparse strings; gaps via `ChartSeriesGrouping` per series×run [VERIFIED: ChartSeriesGrouping.swift] |
</phase_requirements>

## Summary

Phase 15 is **pure composition of already-shipped, already-tested pieces** plus one genuinely new SwiftUI assembly: a vertical stack of sibling `Chart` views that share one X domain and one selection binding. There are no new dependencies, no new persistence, no engine/gate/PDF changes. Every honesty-critical primitive (gap-run grouping, exact-day scrub resolution, per-series extraction, fixed honest domains, suppression gating) was delivered and unit-tested in Phases 13–14 and is reused **unchanged**. The Phase-15 work is: the `ExploreView` screen, the chip picker, a pure `ExploreSelection` cap model, the lane composition, the single multi-series tooltip overlay, the `ExploreCopy` audited-copy surface, and the `-uitest-seed-explore` / `-uitest-select-day` E2E seam.

The **central implementation risk** (flagged research:true) is the mixed-scale lane composition. The locked design — separate per-lane `Chart`s, shared X domain, shared scrub binding — is sound and supported on iOS 18: a single `@State var selectedDate: Date?` bound to every sibling chart's `chartXSelection(value:)` makes one finger read all lanes. The non-obvious gotcha is **horizontal plot-area alignment across siblings**: different Y-axis label widths shift each chart's plot leading edge, so a single vertical scrub line would NOT cross all lanes at the same date unless the plot leading insets are forced identical. This is the one thing to prototype first (§Common Pitfall 1).

**Primary recommendation:** Build the lane stack as `ForEach(selectedQuantSeries) { LaneChart(...) }` inside a `VStack`, each `LaneChart` a separate `Chart` with an IDENTICAL `chartXScale(domain: sharedXDomain)`, a `chartXSelection(value: $selectedDate)` bound to the SAME parent `@State`, and a **fixed-width Y axis** (`chartYAxis { AxisMarks().frame(width: laneYAxisWidth) }` via a constant) so every plot leading edge aligns. Put ONE multi-series tooltip card + ONE shared `RuleMark` above/inside the stack. Resolve each series' value through `ChartScrubSelection.resolve(selectedDate, in: seriesPoints, calendar:)`. Register `ExploreCopy` in `CopyAuditTests` in the SAME task that creates it, before any view renders a string.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| "What series exist" + per-day value/presence | Pure logic (EndoInsights) | — | `ExploreSeriesCatalog`/`ExploreSeries` shipped Phase 14; id-only Hashable enables `Set<ExploreSeries>` selection |
| Quantitative point extraction (gap-honest) | Pure logic (EndoInsights) | — | `ExploreSeriesExtraction.quantitativePoints → [InsightPoint]`; reused by `ChartSeriesGrouping` |
| Occurrence-day extraction (event lanes) | Pure logic (EndoInsights) | — | `ExploreSeriesExtraction.occurrenceDays → [Date]`; no value field = 0/1 unrepresentable |
| Fixed honest Y domain per scale | Pure logic (EndoInsights) | — | `SeriesNormalization.domain(for:)` returns fixed literals (never per-window min/max) |
| Exact-day scrub resolution | Pure logic (EndoInsights) | — | `ChartScrubSelection.resolve`; the ONLY legal raw-date→value path |
| Gap-run line breaking | App pure helper | — | `ChartSeriesGrouping.runs(from:calendar:)` reused per series×run |
| Cap / selection rules | Pure value type (NEW, app or package) | — | `ExploreSelection` value model so cap logic is unit-testable, view stays dumb |
| Lane composition + shared scrub + tooltip | SwiftUI view (NEW) | Swift Charts | `ExploreView` / `ExploreLanesView` / lane subview — the new assembly |
| Chip picker | SwiftUI view (NEW) | — | `SeriesPickerView` over `ExploreSeriesCatalog.all`, suppression-filtered |
| Data source | Data (unchanged) | — | `Repository.daySnapshots() -> [DaySnapshot]`; read on main actor per-view (mirror `InsightsView` dismiss path) |
| Tab placement | App shell | — | `AppGate.ConsentedShell` TabView; built only in `.consented` |
| Chrome copy audit | App copy surface (NEW) | — | `ExploreCopy` registered in `CopyAuditTests` |
| E2E determinism | App composition root | — | `AppDependencies` `-uitest-seed-explore` + reuse `-uitest-select-day` seam |

## Standard Stack

No new packages. Everything ships in the iOS 18 SDK or is already in the repo. Version-verification of registry packages is **N/A** (Apple first-party frameworks + local SPM; the milestone STACK.md already confirmed zero additions). [VERIFIED: STACK.md, codebase]

### Core (all already present)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Swift Charts | iOS 18 SDK | Lanes, scrub, event marks | First-party; `Chart`/`LineMark`/`PointMark`/`RuleMark`/`RectangleMark`, `chartXSelection(value:)`, `chartXScale`, `chartYScale`, `chartForegroundStyleScale` all ≤ iOS 17, unconditional at the iOS 18 floor [CITED: developer.apple.com/documentation/charts] |
| SwiftUI | iOS 18 | `ExploreView`, `@State` selection, chips, `ScrollView` | Selection is a plain `@State Date?` binding; Swift 6 main-actor-clean |
| EndoInsights (local SPM) | local | `ExploreSeriesCatalog`, `ExploreSeriesExtraction`, `SeriesNormalization`, `ChartScrubSelection`, `TrendWindow`, `InsightPoint`, `DaySnapshot` | The pure layer Phase 15 composes — all shipped + tested [VERIFIED: EndoInsights sources] |
| App target | — | `ChartSeriesGrouping`, `InsightsCopy` (pattern), `DisclaimerBanner`, `ConsentedShell`, `Repository.daySnapshots()` | Reuse contracts [VERIFIED: codebase] |

### The exact Swift Charts APIs this phase uses
| API | iOS | Use in Phase 15 |
|-----|-----|-----------------|
| `chartXSelection(value: Binding<Date?>)` | 17.0 | ONE parent `@State Date?` bound to EVERY lane chart → shared scrub (EXPL-05) [CITED: developer.apple.com/documentation/swiftui/view/chartxselection(value:)] |
| `chartXScale(domain: ClosedRange<Date>)` | 16.0 | IDENTICAL on every lane → shared absolute-date X domain (EXPL-03/06) |
| `chartYScale(domain: ClosedRange<Double>)` | 16.0 | Per-lane fixed honest domain via `SeriesNormalization.domain(for:)` (EXPL-03) |
| `chartForegroundStyleScale(_:)` | 16.0 | Stable per-series hue + auto legend; pair with symbol/label (never hue alone) |
| `RuleMark(x:)` + `.annotation(overflowResolution:)` | 16.0 / 17.0 | The scrub scan line; tooltip kept ABOVE the stack (not per-lane) |
| `RectangleMark` / `PointMark` | 16.0 | Event-marker ticks at occurrence dates (EXPL-04) |
| `chartPlotStyle` / fixed Y-axis width | 16.0 | Force identical plot leading inset so the scrub line aligns across lanes (Pitfall 1) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| N sibling `Chart`s, shared binding | ONE `Chart` with multiple plot areas | Swift Charts has no first-class multi-plot-area / dual-Y; faking it ZStacks charts and DESYNCS gestures + a11y (Pitfall 4, ARCHITECTURE Anti-Pattern 2). Sibling charts is the locked + correct choice. |
| `chartXSelection(value:)` | `chartGesture` + `proxy.selectXValue` | Only if default tap/drag arbitration fails; Explore is NOT in a List so the Phase-13 scroll-vs-scrub tension is gone — start without it (STACK.md). |
| `RectangleMark` event ticks | `PointMark` symbols | Either works; `RectangleMark` reads more like a day-band tick, `PointMark` like a dot — UI choice, both honest (occurrence-only). |

**Installation:** none — `xcodegen generate` after adding the new `Features/Explore/` files (the `.xcodeproj` is generated). [VERIFIED: PITFALLS.md tech-debt row]

## Package Legitimacy Audit

> Not applicable — this phase installs ZERO external packages. All code is Apple first-party frameworks (SwiftUI, Swift Charts) + the existing local `EndoInsights` SPM package + project-owned source. No npm/PyPI/crates registry involved; no slopsquatting surface. (Confirmed by STACK.md "zero new dependencies" and the v1.0 near-zero-dependency privacy posture.)

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
ExploreView (NavigationStack > ScrollView)            ← NEW, built ONLY in ConsentedShell
  │ @State selection: ExploreSelection (Set<ExploreSeries> + TrendWindow)   ← EPHEMERAL, pure value
  │ @State selectedDate: Date?     ← the ONE shared scrub state (all lanes)
  │
  ├─ DisclaimerBanner (reused verbatim)                explore.disclaimer
  ├─ SeriesPickerView (toggle chips, cap, suppression-filtered)             explore.picker
  │        └─ reads ExploreSeriesCatalog.all  ─┐
  ├─ Range control (TrendWindow segmented)      │     explore.range
  │                                             │
  ▼  on appear / selection change (main actor)  │
  Repository.daySnapshots() -> [DaySnapshot] ───┘   ← single data source (unchanged)
  │
  ├── per QUANTITATIVE series ──► ExploreSeriesExtraction.quantitativePoints(snaps, series, cal)
  │        -> [InsightPoint] ──► ChartSeriesGrouping.runs(from:calendar:) -> gap-broken runs
  │        -> LaneChart  (Chart, height 120, chartYScale = SeriesNormalization.domain(for:kind))
  │                       shared chartXScale(domain: xDomain) + chartXSelection($selectedDate)
  │
  ├── per EVENT series ─────────► ExploreSeriesExtraction.occurrenceDays(snaps, series, cal)
  │        -> [Date] ──► EventLaneChart (Chart, height 44, ticks at occurrence dates, Y hidden)
  │                       shared chartXScale(domain: xDomain) + chartXSelection($selectedDate)
  │
  └── ABOVE the lane stack: ONE multi-series tooltip overlay      explore.scrub.tooltip
           for each visible series: ChartScrubSelection.resolve(selectedDate, in: seriesPoints, cal)
             -> point  ► "{displayName}: {SeriesNormalization.readout(value, kind, unit)}"
             -> nil    ► "{displayName}: Not logged"           explore.scrub.tooltip.<id>[.notLogged]
```

### Recommended Project Structure
```
Sources/EndoApp/Features/Explore/        # NEW folder
├── ExploreView.swift          # screen: NavigationStack > ScrollView; owns both @States; reads snapshots
├── ExploreSelection.swift     # PURE value type: Set<ExploreSeries> + window + cap rules (unit-tested)
├── SeriesPickerView.swift     # toggle chips, cap disable + helper, suppression filter
├── ExploreLanesView.swift     # the VStack of lanes + shared RuleMark + multi-series tooltip overlay
├── ExploreLaneChart.swift     # ONE quantitative lane (Chart, runs, fixed Y domain, shared X + selection)
├── EventLaneChart.swift       # ONE event-marker lane (Chart, occurrence ticks, height 44)
└── ExploreCopy.swift          # audited chrome-copy enum (REGISTER in CopyAuditTests first)
```
`ExploreSelection` MAY live in EndoInsights (pure, testable with `swift test`) or the app target with a Swift Testing suite — either satisfies "cap logic is provably correct without rendering." Recommend the **app target** with a Swift Testing unit suite, because `ExploreSelection` references `ExploreSeries` (in EndoInsights) but the cap policy is UI-presentation logic, and keeping it app-side avoids widening the pure package's surface. (Discrepancy note for planner: ARCHITECTURE Q2 named `ExploreSelection` as "its own small value type" without fixing the target — either is acceptable.)

### Pattern 1: Shared-scrub sibling lanes (THE central pattern — EXPL-03/05)
**What:** N separate `Chart` views in a `VStack`, each bound to the SAME `@State var selectedDate: Date?` via `chartXSelection`, each with the SAME `chartXScale(domain:)`.
**When to use:** the locked mixed-scale lane layout — every quantitative series in its own real-unit lane.
**Example (shape the planner turns into `ExploreLaneChart`):**
```swift
// Source: mirrors InsightTrendChart.swift (shipped) — gap runs + fixed domain + absolute X.
struct ExploreLaneChart: View {
    let series: ExploreSeries
    let points: [InsightPoint]          // ExploreSeriesExtraction.quantitativePoints(...)
    let xDomain: ClosedRange<Date>      // SHARED across all lanes
    @Binding var selectedDate: Date?    // the ONE shared scrub state
    private var calendar: Calendar { .current }
    private var runs: [ChartSeriesGrouping.Run] {
        ChartSeriesGrouping.runs(from: points, calendar: calendar)   // REUSED unchanged
    }
    private var yDomain: ClosedRange<Double> {
        SeriesNormalization.domain(for: series.scaleKind) ?? 0...1   // fixed honest range
    }
    var body: some View {
        Chart {
            ForEach(runs) { run in
                ForEach(run.points, id: \.date) { p in
                    LineMark(x: .value("Date", p.date), y: .value(series.displayName, p.value),
                             series: .value("Run", run.id))           // distinct per run = honest gap
                        .foregroundStyle(by: .value("Series", series.displayName)) // legend/color
                }
            }
            ForEach(points, id: \.date) { p in
                PointMark(x: .value("Date", p.date), y: .value(series.displayName, p.value))
                    .foregroundStyle(by: .value("Series", series.displayName))
            }
            // shared scrub rule (gray chrome, behind data)
            if let day = resolvedDay { RuleMark(x: .value("Selected", day))
                .foregroundStyle(.secondary.opacity(0.5)).zIndex(-1) }
        }
        .chartXScale(domain: xDomain)                       // IDENTICAL on every lane
        .chartYScale(domain: yDomain)                       // per-lane real unit
        .chartXSelection(value: $selectedDate)              // SHARED binding
        .frame(height: 120)                                 // lane-quant-height
        .chartYAxis { AxisMarks() }                         // see Pitfall 1: fix width for alignment
    }
    private var resolvedDay: Date? {
        ChartScrubSelection.resolve(selectedDate, in: points, calendar: calendar)?.day
    }
}
```

### Pattern 2: Event-marker lane (EXPL-04)
**What:** occurrence-only ticks on the shared X axis, NO Y-value semantics, height 44.
**Example (shape for `EventLaneChart`):**
```swift
// Source: ExploreSeriesExtraction.occurrenceDays returns [Date] — no value field by design.
struct EventLaneChart: View {
    let series: ExploreSeries
    let days: [Date]                    // occurrenceDays(...); a day with no tick = "not logged that event"
    let xDomain: ClosedRange<Date>
    @Binding var selectedDate: Date?
    var body: some View {
        Chart {
            ForEach(days, id: \.self) { d in
                RectangleMark(x: .value("Date", d), yStart: .value("lo", 0), yEnd: .value("hi", 1), width: 4)
                    .foregroundStyle(by: .value("Series", series.displayName))
                    .accessibilityLabel(Text(d, format: .dateTime.month().day()))
                    .accessibilityValue(Text(ExploreCopy.eventTagged))   // "Logged", never "1"
            }
            if let day = selectedDate.map({ Calendar.current.startOfDay(for: $0) }) {
                RuleMark(x: .value("Selected", day)).foregroundStyle(.secondary.opacity(0.5)).zIndex(-1)
            }
        }
        .chartXScale(domain: xDomain)
        .chartYScale(domain: 0...1)        // hidden — the lane is a single row of ticks
        .chartYAxis(.hidden)
        .chartXSelection(value: $selectedDate)
        .frame(height: 44)                 // lane-event-height (also the 44pt touch floor)
    }
}
```
> NOTE for planner: the event lane has no `InsightPoint` array, so its scrub contribution is binary. For the tooltip, resolve presence by `series.isPresent(in: snapshotForDay)` — i.e. look up the scrubbed day's `DaySnapshot` and read `isPresent`. There is NO `ChartScrubSelection` path for occurrence days (that resolver works on `[InsightPoint]`). The tooltip line is "{displayName}: Logged" / "{displayName}: Not logged" / (no snapshot that day) "Not logged".

### Pattern 3: The single multi-series tooltip (EXPL-05)
**What:** ONE card (NOT per-lane) listing the scrubbed date once + one line per visible series. Lives in an `.overlay`/section above the lane stack so a single read covers all lanes.
**How (shape):**
```swift
// For each visible QUANTITATIVE series: resolve through the SHIPPED resolver.
func tooltipLine(for series: ExploreSeries, points: [InsightPoint]) -> String {
    let res = ChartScrubSelection.resolve(selectedDate, in: points, calendar: .current)
    guard let res, let p = res.point else { return "\(series.displayName): \(ExploreCopy.notLogged)" }
    let unit = series.unit
    return "\(series.displayName): \(SeriesNormalization.readout(p.value, kind: series.scaleKind, unit: unit))"
}
// date header once:  selectedDay.formatted(.dateTime.month().day())
```
- **a11y (mirror Phase-13 `selectionAccessibilityProxy`):** Swift Charts does NOT expose `.annotation` content to the AX tree (verified Phase 13). So the multi-series readout + ids ride a **transparent overlay proxy**; the visual tooltip card is `.accessibilityHidden(true)`. Each series line gets `explore.scrub.tooltip.<series.id>` and, when a gap, `explore.scrub.tooltip.<series.id>.notLogged`. Shield the proxy with `.accessibilityElement(children: .contain)` so a parent `explore.scrub.tooltip` id cannot mask the per-series ids (the documented id-bleed fix). [VERIFIED: InsightTrendChart.swift selectionAccessibilityProxy]
- The tooltip is `.secondary` chrome, not data. A selection haptic (`UISelectionFeedbackGenerator`, ONE per view identity) fires on resolved-DAY change only — copy the exact `.onChange(of:)` guard from `InsightTrendChart`. [VERIFIED: InsightTrendChart.swift lines 126–133]

### Pattern 4: Pure cap model `ExploreSelection` (EXPL-02)
**What:** a value type so the 3–4 quantitative cap + suppression filter are unit-testable, leaving the view dumb.
```swift
struct ExploreSelection: Equatable {
    private(set) var series: Set<ExploreSeries> = []
    var window: TrendWindow = .last30
    static let quantitativeCap = 4                       // "up to four" (locked copy)
    var quantitativeCount: Int { series.filter { $0.scaleKind.isQuantitative }.count }
    func canAddQuantitative(_ s: ExploreSeries) -> Bool {
        !s.scaleKind.isQuantitative || series.contains(s) || quantitativeCount < Self.quantitativeCap
    }
    mutating func toggle(_ s: ExploreSeries) {
        if series.contains(s) { series.remove(s) }
        else if canAddQuantitative(s) { series.insert(s) }   // event series never blocked
    }
}
// Suppression filter is a CATALOG filter, applied where chips are built (not in the cap model):
let offered = ExploreSeriesCatalog.all.filter { s in
    !(suppressed && (s.id == "bleeding.intensity" || s.id == "bleeding.event"))
}
```
> `Set<ExploreSeries>` works for free because `ExploreSeries` is `Hashable` from its `id` alone (Phase 14 deliberately stored an enum `field`, not a closure). [VERIFIED: ExploreSeries.swift lines 67–110]

### Anti-Patterns to Avoid
- **ZStacking two charts to fake a dual Y axis** — desyncs gestures + a11y; violates honest-axis invariant. Use sibling lanes. [VERIFIED: PITFALLS.md Pitfall 4, ARCHITECTURE Anti-Pattern 2]
- **One `series:` value per data-series** (legend-only) in a lane — Swift Charts then interpolates straight through gaps. Key `series:` by (series × run); carry legend/color via `.foregroundStyle(by:)`. [VERIFIED: PITFALLS.md Pitfall 2]
- **`proxy.value(atX:)` / nearest-point math for the tooltip** — fabricates gap-day values. ONLY `ChartScrubSelection.resolve` (exact-day). [VERIFIED: ChartScrubSelection.swift]
- **`Double(tagPresent ? 1 : 0)` for binary series** — the Phase-14 type system already forbids it (`occurrenceDays -> [Date]`, no value field). Don't reintroduce a value mapping. [VERIFIED: ExploreSeriesExtraction.swift]
- **Putting Explore in a `List`** — re-creates the Phase-13 scroll-vs-scrub fight. Use a `ScrollView` (lanes are a fixed free-scrub surface). [VERIFIED: UI-SPEC Spacing §, PITFALLS.md Pitfall 7]
- **Persisting selection via `@AppStorage`** — leaks health-interest metadata outside the consent-gated store. Keep `@State` ephemeral. [VERIFIED: CONTEXT.md, ARCHITECTURE Q2]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Break lines at unlogged days | Custom gap-detection in the view | `ChartSeriesGrouping.runs(from:calendar:)` | Pure, DST-safe, unit-tested; absence ≠ 0 proven [VERIFIED] |
| Raw scrub date → value | Nearest-point / interpolation | `ChartScrubSelection.resolve` | Exact-day only; gap → nil; severity-0 → real 0 [VERIFIED] |
| Per-day series value/presence | Re-read SwiftData per series | `ExploreSeries.quantitativeValue(from:)` / `isPresent(in:)` via the extraction helpers | Nil-honest, max-not-first, suppression-gated [VERIFIED] |
| Fixed honest Y range | Per-window min/max normalization | `SeriesNormalization.domain(for:)` | Fixed literals; stable shape across windows; no phantom % axis [VERIFIED] |
| Real-unit readout string | Ad-hoc `"\(value) \(unit)"` | `SeriesNormalization.readout(_:kind:unit:)` | Drops trailing `.0`, never the normalized number [VERIFIED] |
| Series catalog | Hand-listed series | `ExploreSeriesCatalog.all` | Enumerated from `CaseIterable` domains — cannot drift [VERIFIED] |
| Absolute-date X domain | New date math | Copy `InsightTrendChart.xDomain` derivation | Same window vocabulary as dashboard (EXPL-06) [VERIFIED] |
| Window labels | New strings | Mirror `InsightsCopy.windowLabel` in `ExploreCopy` | Reuses audited "last N days" wording [VERIFIED] |
| Copy audit | Skip registration | Add `ExploreCopy.auditableStrings` to `CopyAuditTests` FIRST | Unregistered surface silently escapes scans [VERIFIED] |
| Deterministic E2E selection | Coordinate scrub gestures | `-uitest-select-day` + a11y ids | Phase-13 seam; zero flake [VERIFIED] |

**Key insight:** Phase 15 should write almost no algorithmic code. If a task body contains a `for`/`reduce` over snapshots, a min/max, or a date-distance calculation, it is probably re-implementing a shipped pure helper — stop and reuse.

## Runtime State Inventory

> Phase 15 is **greenfield UI composition** — no rename/refactor/migration. This section is included only to discharge the rename-checklist explicitly.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — Explore reads `daySnapshots()` read-only; writes nothing, no new `@Model`, no schema/CloudKit change | none (verified: ARCHITECTURE "untouched: SchemaV1, CloudKit") |
| Live service config | None — no external service touched | none |
| OS-registered state | None | none |
| Secrets/env vars | None new. The only new launch arg is the DEBUG `-uitest-seed-explore` (paired with `-uitest-reset`, in-memory only) | none beyond gating it DEBUG-only |
| Build artifacts | New `Features/Explore/*.swift` + `ExploreUITests` must be added to `project.yml` → `xcodegen generate` before build/test | run `xcodegen generate` as the final task of any file-adding plan |

**Nothing found in categories 1–4:** confirmed — Explore is additive, read-only, ephemeral-state UI.

## Common Pitfalls

### Pitfall 1: Lanes don't vertically align — the scrub line crosses each lane at a different date  **[CENTRAL RISK]**
**What goes wrong:** sibling `Chart`s with different Y-axis label widths get different plot leading insets, so identical `chartXScale(domain:)` still renders the same date at different X pixels per lane. One shared `RuleMark` then looks misaligned, and the "single finger reads every lane" illusion breaks.
**Why it happens:** Swift Charts sizes each chart's plot area after reserving space for that chart's own axis labels. A `0…240` activity lane reserves more left margin than a `0…10` severity lane.
**How to avoid:** force a **fixed, identical plot leading inset** across lanes. Two viable mechanisms (prototype both, pick one):
  1. Fixed-width Y axis: render Y labels in a constant-width container (`.chartYAxis { AxisMarks(...) }` with a fixed leading plot margin via `.chartPlotStyle { $0.frame(...) }` / `.chartXAxis`+`.chartYAxis` width constants), so every lane reserves the same left gutter.
  2. Move per-lane Y scale labels OUT of the chart (the lane TITLE already carries the unit "— of 10"/"— h") and use `.chartYAxis(.hidden)` on quantitative lanes too, leaving only a couple of inline gridline labels — then all plots share the full width and align trivially.
**Warning signs:** the shared `RuleMark` appears shifted between lanes in a screenshot; activity (0…240) lane's plot starts further right than the severity lane.
**Verification:** a seeded multi-lane screenshot at the `/tmp/e2e` checkpoint — eyeball the vertical scrub line crossing all lanes at one date. **Prototype this FIRST** (CONTEXT "prototype the lane composition early"; PITFALLS Pitfall 4 recovery = HIGH-cost UI rework).

### Pitfall 2: Event-lane tooltip has no `ChartScrubSelection` path
**What goes wrong:** the resolver operates on `[InsightPoint]`; event lanes are `[Date]`. Naively trying to resolve an event lane through `ChartScrubSelection` returns `point == nil` always → every event reads "Not logged".
**How to avoid:** for the scrubbed day, look up that day's `DaySnapshot` and call `series.isPresent(in: snap)` → "Logged"/"Not logged". Keep a `[Date: DaySnapshot]` (or `startOfDay`-keyed) map from the same `daySnapshots()` read.
**Warning signs:** an event series always shows "Not logged" in the tooltip even on a tagged day.

### Pitfall 3: `ExploreCopy` ships unregistered, or hosts a substring landmine
**What goes wrong:** an unregistered copy enum escapes ALL `CopyAuditTests` scans (forbidden verbs, gendered tokens, numeric confidence). Separately, Explore vocabulary is collision-rich: "her" ⊂ together/whether/other/higher/there; "treat" ⊂ treatment; "use" ⊂ because; "trigger"/"causes"/"will" forbidden.
**How to avoid:** the UI-SPEC already curated every string (incl. the "another"→"a new one" fix). Create `ExploreCopy` with `auditableStrings` and add a `Phase-15 explore copy` suite to `CopyAuditTests` in the SAME task, BEFORE any view renders a string. Mirror the `InsightsCopy` + the existing `insightsCopy_*` test shape exactly (forbidden-verb / gendered-token / numeric-confidence scans).
**Confirmed forbidden lists (this session):** verbs include `treat`, `trigger`, `causes`, `will`, `detect`, `prevent`; gendered `she/her/women/woman/lady/ladies`. [VERIFIED: ClaimsPolicy.swift lines 27–50]
**Pre-verified Explore strings** (from UI-SPEC Copywriting Contract): "Pick a series to begin.", "Not enough data yet.", "Up to four series at once. Tap a selected chip to remove one, then add a new one.", "Not logged" (reuse `InsightsCopy.scrubNotLogged` posture), "Remove series", "Add to the timeline." — all curated clean.

### Pitfall 4: A scrub frame re-reads `daySnapshots()` or recomputes runs in `body`
**What goes wrong:** snapshot read or `ChartSeriesGrouping.runs` recomputed per scrub frame → CPU spike, sticky scrub.
**How to avoid:** read snapshots once per data/selection change (cache in `@State`/derived once), compute per-series points + runs once when the selection set or window changes (not per frame). Selection date change must only move the `RuleMark` + recompute the cheap tooltip lines. [VERIFIED: PITFALLS.md performance traps]

### Pitfall 5: Tab a11y id on the content view instead of the `Label`
**What goes wrong:** `.accessibilityIdentifier("tab.explore")` on `ExploreView` does NOT reach the tab-bar button; XCUITest can't find the tab.
**How to avoid:** put the id on the `.tabItem { Label(...).accessibilityIdentifier("tab.explore") }` — exactly as `tab.insights` is done. [VERIFIED: AppGate.swift lines 56–61]

### Pitfall 6: Too many series / color-only identity / no VoiceOver scrub path
**How to avoid:** cap quantitative at 4 (the `ExploreSelection` model); each series carries hue + the lane title `displayName` + (if two ever share a lane scale) a symbol/dash — never hue alone; per-lane text summary; the multi-series readout reachable WITHOUT the drag gesture via the transparent overlay proxy (Pattern 3). [VERIFIED: PITFALLS.md Pitfall 10, UI-SPEC Accessibility Contract]

## Code Examples

### Adding the third tab (EXPL-01)
```swift
// Source: AppGate.swift ConsentedShell (shipped) — add a third .tabItem, id on the Label.
TabView {
    HomeView().tabItem { Label("Log", systemImage: "square.and.pencil") }
    InsightsView().tabItem {
        Label("Insights", systemImage: "chart.line.uptrend.xyaxis")
            .accessibilityIdentifier("tab.insights")
    }
    ExploreView().tabItem {                                   // ← NEW, .consented branch only
        Label("Explore", systemImage: "square.stack.3d.up")
            .accessibilityIdentifier("tab.explore")
    }
}
```

### Reading the data source (mirror the dismiss-path construction)
```swift
// Source: InsightsView.swift — per-view SwiftDataRepository(context: modelContext) on the main actor.
@Environment(\.modelContext) private var modelContext
func loadSnapshots() -> [DaySnapshot] {
    (try? SwiftDataRepository(context: modelContext).daySnapshots()) ?? []
}
// suppressed flag for the picker filter: snapshots.last?.suppressed (per-snapshot stamped) — or any snap.
```
[VERIFIED: Repository.swift `func daySnapshots() throws -> [DaySnapshot]` line 533; DaySnapshot.suppressed line 100]

### E2E seam (EXPL-* without flaky gestures)
```swift
// Source: AppDependencies.swift — add a -uitest-seed-explore mirroring -uitest-seed-scrub.
let seedExplore = ProcessInfo.processInfo.arguments.contains("-uitest-seed-explore")
if seedExplore && uiTestReset { Self.seedExploreFixture(into: insightsRepo) }
// Reuse the EXISTING DEBUG `-uitest-select-day <yyyy-MM-dd>` env seam (\.uiTestScrubDay) to set the
// shared selectedDate deterministically — ExploreLanesView reads it the same way InsightTrendChart does.
```
- Seed a multi-series fixture: ≥1 quantitative series with an in-window GAP day + a logged-0 day, a second quantitative series on a DIFFERENT scale (sleep hours), and an event series (a food tag) — all inside the chosen window so a single `-uitest-select-day` resolves deterministically (mirror the Phase-13 "all anomalies in last-7" rule to avoid window ambiguity).
- Assert via a11y ids only: `tab.explore`, `explore.screen`, `explore.picker`, `explore.chip.<id>`, `explore.lane.<id>`, `explore.lane.<id>.event`, `explore.scrub.tooltip`, `explore.scrub.tooltip.<id>`, `explore.scrub.tooltip.<id>.notLogged`, `explore.empty`, `explore.sparse`, `explore.picker.capHint`.
- XCUITest query pattern: `app.tabBars.buttons["tab.explore"]` (fallback `app.descendants(matching:.any)["tab.explore"].firstMatch`); sub-element ids via `NSPredicate(format: "identifier ENDSWITH '…'")` + `firstMatch` (the id-bleed-robust pattern). [VERIFIED: ChartScrubUITests.swift, InsightsDashboardUITests.swift]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `chartOverlay` + manual `DragGesture` for selection | `chartXSelection(value:)` | iOS 17 (WWDC23) | Framework owns gesture; bind one `Date?` per chart; the repo already uses it in Phase 13 |
| Dual Y axis for mixed scales | Aligned per-scale lanes (no dual-Y exists in Swift Charts) | confirmed limitation (any version incl. iOS 26 SDK) | The locked lane design IS the workaround; do not search for a dual-axis API |
| `PointMark` + `ForEach` for many points | `PointPlot`/`LinePlot` (vectorized) | iOS 18 | Only if profiling shows lag (≤4 lanes × ~120 pts is fine) — NOT needed at launch |

**Deprecated/outdated:** none relevant. All APIs are WWDC22–24 vintage, stable at the iOS 18 floor.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | A single `@State Date?` bound to `chartXSelection` on multiple sibling `Chart`s makes one scrub drive all lanes on iOS 18 | Pattern 1 | LOW — corroborated by Apple docs + WWDC + community; if a sibling's gesture conflicts, fall back to `chartGesture` + `proxy.selectXValue` on one "driver" lane and propagate the date. [ASSUMED — verified pattern, not run this session] |
| A2 | Forcing identical plot leading inset (fixed Y-axis width OR `.chartYAxis(.hidden)`) makes lanes vertically align | Pitfall 1 | MEDIUM — the mechanism is sound but the EXACT modifier mix is unverified at runtime; this is the prototype-first item. [ASSUMED] |
| A3 | `RectangleMark`/`PointMark` event ticks render acceptably at `lane-event-height = 44` with `chartYAxis(.hidden)` | Pattern 2 | LOW — standard Swift Charts; cosmetic only. [ASSUMED] |
| A4 | `-uitest-select-day` env seam (Phase-13, `\.uiTestScrubDay`) can be read by `ExploreLanesView` the same way `InsightTrendChart` reads it | E2E seam | LOW — same Environment value; the read pattern is copy-paste. [ASSUMED — seam VERIFIED present in AppDependencies.swift] |

> The planner / discuss-phase should treat A2 as the one item warranting an early prototype task before the full lane build commits.

## Open Questions

1. **Fixed plot inset mechanism (A2).**
   - What we know: identical `chartXScale(domain:)` is necessary but not sufficient; Y-label width shifts the plot leading edge.
   - What's unclear: whether `.chartPlotStyle`/fixed-Y-axis-width or hiding Y axes entirely (relying on the lane title for the unit) gives the cleanest alignment AND keeps the real scale legible.
   - Recommendation: a Wave-0 "prototype lane alignment" task that renders 3 lanes on different scales + one shared `RuleMark` and screenshots it; pick the mechanism before building the picker/tooltip.

2. **Where `ExploreSelection` lives (app vs EndoInsights).**
   - What we know: both compile; ARCHITECTURE left it unfixed.
   - Recommendation: app target + Swift Testing unit suite (keeps the pure package surface narrow; cap logic is presentation policy).

## Environment Availability

> Skipped — Phase 15 has no external runtime dependencies. It is SwiftUI + Swift Charts (Apple system frameworks, present in the iOS 18 SDK already targeted) + the local EndoInsights package + project source. The only toolchain action is `xcodegen generate` (the project's standing requirement for any added file). iPhone 16 simulator; `swift test --package-path EndoInsights` for any pure `ExploreSelection` additions if placed in the package; `xcodebuild -only-testing` (NEVER bare `-only-testing:EndoAppTests`) for app suites.

## Validation Architecture

> `workflow.nyquist_validation: true` [VERIFIED: .planning/config.json] — section included.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Swift Testing (`@Test`/`#expect`) for pure/unit + copy audit; XCTest/XCUITest for E2E (coexist, separate targets) [VERIFIED: STACK.md, CopyAuditTests.swift, ChartScrubUITests.swift] |
| Config file | none — XcodeGen-generated `.xcodeproj`; tests run via `xcodebuild -only-testing` |
| Quick run command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:EndoAppTests/CopyAuditTests` (copy audit) and `swift test --package-path EndoInsights` (pure, if `ExploreSelection` in package) |
| Full suite command | `xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'` (NEVER bare `-only-testing:EndoAppTests` — known stall) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EXPL-02 | Cap blocks the 5th quantitative; event series uncapped; toggle add/remove | unit | `swift test --package-path EndoInsights` or app Swift Testing suite — `ExploreSelectionTests` | ❌ Wave 0 |
| EXPL-02 | Suppressed user → bleeding chips filtered out of `offered` | unit | `ExploreSelectionTests.suppressionFiltersBleeding` | ❌ Wave 0 |
| EXPL-03 | Quantitative lane points are gap-broken per series×run | unit (reuse) | existing `ChartSeriesGroupingTests` + `ExploreSeriesExtractionTests` (shipped) | ✅ |
| EXPL-04 | Event series yields occurrence days only, never 0/1 | unit (reuse) | existing `ExploreSeriesExtractionTests` (shipped) | ✅ |
| EXPL-05 | Scrub on logged day → readout; gap day → "Not logged"; sev-0 → real 0 | unit (reuse) + E2E | `ChartScrubSelectionTests` (shipped) + `ExploreUITests.testScrub_*` | ✅ / ❌ Wave 0 |
| EXPL-05 | Multi-series tooltip lists each visible series once | E2E | `ExploreUITests.testTooltip_multiSeries` (a11y ids, seam-set day) | ❌ Wave 0 |
| EXPL-06 | Range switch re-anchors shared X domain (absolute dates, gaps) | E2E | `ExploreUITests.testRange_switch` (assert lane re-renders; window labels) | ❌ Wave 0 |
| EXPL-07 | Empty "pick a series" + sparse "not enough data" states | E2E | `ExploreUITests.testEmptyState` / `testSparseState` | ❌ Wave 0 |
| EXPL-01/02 | Tab present; chips render; suppression hides bleeding chip | E2E | `ExploreUITests.testTab_andPicker` (`tab.explore`, `explore.chip.<id>`) | ❌ Wave 0 |
| cross-cut | `ExploreCopy` passes forbidden-verb/gendered/numeric scans | unit | `CopyAuditTests` new `explore copy` suite | ❌ Wave 0 |
| cross-cut | Lane alignment + grayscale series distinguishability | manual screenshot | `/tmp/e2e` checkpoint (Pitfall 1 + color-independence) | ❌ Wave 0 (manual) |

### Sampling Rate
- **Per task commit:** the relevant quick command (copy audit OR `swift test` for `ExploreSelection`).
- **Per wave merge:** full app suite + `swift test --package-path EndoInsights`.
- **Phase gate:** full suite green + the manual screenshot checkpoint (lane alignment, grayscale, VoiceOver multi-series readout without drag) before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `Tests/EndoAppTests/ExploreSelectionTests.swift` (or package test) — covers EXPL-02 cap + suppression filter
- [ ] `Tests/EndoAppUITests/ExploreUITests.swift` — covers EXPL-01/02/05/06/07 via a11y ids + seam
- [ ] `CopyAuditTests` — add the `explore copy` suite (register `ExploreCopy.auditableStrings`) BEFORE writing copy
- [ ] `AppDependencies` — add `-uitest-seed-explore` fixture seam (DEBUG, `-uitest-reset`-paired)
- [ ] `project.yml` / `xcodegen generate` — register all new `Features/Explore/*` + `ExploreUITests`
- [ ] Manual: `/tmp/e2e` lane-alignment + grayscale screenshot; VoiceOver multi-series readout without drag

*(Existing pure helpers — `ChartSeriesGrouping`, `ChartScrubSelection`, `ExploreSeriesExtraction`, `SeriesNormalization` — already have passing unit suites and need NO new tests; they are reused verbatim.)*

## Security Domain

> `security_enforcement: true`, `security_asvs_level: 1` [VERIFIED: .planning/config.json] — section included. Endo-App's threat model is health-data privacy + the wellness-not-medical-device positioning, not classic web appsec.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No accounts (iCloud identity only); Explore adds none |
| V3 Session Management | no | No sessions |
| V4 Access Control | yes | Consent-by-construction: `ExploreView` built ONLY in `AppGate`'s `.consented` branch — no health UI before opt-in [VERIFIED: AppGate.swift] |
| V5 Input Validation | yes | The DEBUG `-uitest-select-day` parse must be bounds-checked + strict `yyyy-MM-dd`, garbage → nil, never a crash (already implemented in `AppDependencies`; the new `-uitest-seed-explore` adds no untrusted input path) [VERIFIED: AppDependencies.swift lines 122–134] |
| V6 Cryptography | no | No new crypto; data stays in the existing on-device/private-CloudKit store; Explore is read-only |

### Known Threat Patterns for SwiftUI + health data

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Test-only seam (`-uitest-seed-explore`, `-uitest-select-day`) compiled into Release | Tampering / Info disclosure | Gate DEBUG-only + pair with `-uitest-reset` (in-memory store), exactly like every existing seed; the seam is unreachable in production by construction [VERIFIED: AppDependencies.swift] |
| Health-interest metadata leaking outside the consent store | Info disclosure | Selection state stays ephemeral `@State` — NEVER `@AppStorage`/`@SceneStorage` (LOCKED privacy call) |
| Explore re-introducing claim/association/percentage chrome | Repudiation / mis-positioning | Explore is descriptive-only; `ExploreCopy` passes the same ClaimsPolicy scans (no causal verb, no %/r/p, no confidence) — it bypasses the gate but NOT the copy audit |
| Suppressed user's bleeding surfaced via Explore | Info disclosure | Picker filters bleeding chips under suppression AND the Phase-14 accessors already gate `!snap.suppressed` — defense in depth [VERIFIED: ExploreSeries.swift] |
| Personal health data in `/tmp/e2e` screenshots from a real install | Info disclosure | Keep the screenshot checkpoint simulator + seeded-fixture only (existing pattern) |

## Sources

### Primary (HIGH confidence — read directly this session)
- `Sources/EndoApp/Features/Insights/InsightTrendChart.swift` — the chart vocabulary to mirror: gap-run `series:` keying, fixed Y/absolute X domain, `chartXSelection`, RuleMark+annotation tooltip, `selectionAccessibilityProxy`, haptic guard, `allowsSelection` PDF-protection, `AXChartDescriptor`
- `Sources/EndoApp/Features/Insights/ChartSeriesGrouping.swift` — `runs(from:calendar:)` reuse contract
- `EndoInsights/Sources/EndoInsights/{ExploreSeries,ExploreSeriesExtraction,SeriesNormalization,ChartScrubSelection,DaySnapshot,Insight}.swift` — the Phase-14/13 pure layer (catalog, extraction, fixed domains, resolver, `TrendWindow`, `InsightPoint`, `DaySnapshot` fields)
- `Sources/EndoApp/App/AppGate.swift` — `ConsentedShell` TabView + tab-id-on-Label lesson + consent-by-construction
- `Sources/EndoApp/App/AppDependencies.swift` — the `-uitest-reset`/`-uitest-seed-*`/`-uitest-select-day` seam pattern (the exact thing to mirror)
- `Sources/EndoApp/Features/Insights/{InsightsView,InsightsCopy}.swift` — screen structure + audited-copy enum to mirror
- `Sources/EndoApp/Content/ClaimsPolicy.swift` — the exact forbidden-verb / gendered / imperative / alarmist token lists
- `Tests/EndoAppTests/CopyAuditTests.swift` — the registration + scan pattern `ExploreCopy` must join
- `Tests/EndoAppUITests/ChartScrubUITests.swift` + `InsightsDashboardUITests.swift` — seeded-launch + a11y-id-query E2E harness to mirror
- `.planning/research/{STACK,PITFALLS,ARCHITECTURE}.md` — milestone research (no-dual-Y, sibling-lane-with-shared-selection, the 10 pitfalls, the integration map)
- `.planning/phases/15-explore-tab-overlay-lanes/{15-CONTEXT,15-UI-SPEC}.md` — locked decisions + approved design contract
- `.planning/config.json` — `nyquist_validation: true`, `security_enforcement: true`

### Secondary (MEDIUM-HIGH — corroborated)
- [Apple — chartXSelection(value:)](https://developer.apple.com/documentation/swiftui/view/chartxselection(value:)) — iOS 17 binding-based selection
- [WWDC23 — Explore pie charts and interactivity in Swift Charts](https://developer.apple.com/videos/play/wwdc2023/10037/) — selection idiom origin
- [Apple Developer Forums — SwiftUI Chart Axis Alignment](https://developer.apple.com/forums/thread/722978) and [X-axis label alignment](https://developer.apple.com/forums/thread/770733) — confirm plot-area alignment sensitivity across charts (Pitfall 1)
- [Swift with Majid — Mastering charts: Selection](https://swiftwithmajid.com/2023/07/18/mastering-charts-in-swiftui-selection/) — shared-state selection pattern

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — zero new packages; every API verified against the iOS 18 floor + already used in the shipped Phase-13 chart.
- Architecture / reuse contracts: HIGH — every pure helper, view, and seam read directly this session with exact signatures.
- Lane composition (alignment mechanism, A2): MEDIUM — the design is sound and supported, but the exact plot-inset modifier mix needs an early runtime prototype.
- Pitfalls / copy traps: HIGH — token lists read from `ClaimsPolicy.swift`; pitfalls inherited from milestone PITFALLS.md + Phase-13 source.

**Research date:** 2026-06-15
**Valid until:** 2026-07-15 (stable Apple frameworks; re-verify only on a new iOS SDK)
