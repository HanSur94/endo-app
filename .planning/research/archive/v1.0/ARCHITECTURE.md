# Architecture Research — v1.1 Interactive Charts & Explore

**Domain:** SwiftUI + Swift Charts interactivity (scrub/tooltip), multi-series overlay plotting, and scatter views inside an existing, feature-complete iOS health-tracking app
**Researched:** 2026-06-10
**Confidence:** HIGH (every integration claim below was verified directly against the v1.0 codebase; the two external claims — `chartXSelection` availability and the no-dual-y-axis limitation — are corroborated by Apple docs/WWDC + multiple community sources)

> **Milestone scope:** This document covers ONLY how the v1.1 features (chart scrubbing, Explore overlay, scatter view) integrate with the shipped v1.0 architecture. The v1.0 whole-app architecture research it replaces is superseded by the shipped code itself (the code won; see `Sources/EndoApp/` + `EndoInsights/`).

---

## Executive Recommendation (read this first)

1. **Explore reads `Repository.daySnapshots()` directly — no new persistence seam, no engine changes.** `DaySnapshot` already carries every series the milestone needs (verified field-by-field below), already includes the manual-wins HealthKit merge, and already encodes the nil-honest "absence ≠ 0" contract. Explore is a *descriptive raw-data surface*: it deliberately bypasses the `InsightsEngine`/`HonestyGate` pipeline (which gates *claims*, not *raw plots*) but inherits the honesty contract from the snapshot itself.
2. **The new logic is three pure Foundation helpers + four SwiftUI views.** The pure series-extraction mapper should emit the existing `EndoInsights.InsightPoint` (`date` + `value`) so the shipped gap-breaking helper `ChartSeriesGrouping` is reused **unchanged** — honest gaps in Explore come for free.
3. **Explore is a third tab**, constructed exclusively inside `ConsentedShell` (preserving the ONBD-01 consent-by-construction invariant), with the a11y id on the tab-item **label** (the documented XCUITest pitfall).
4. **Scrubbing is an additive parameter on `InsightTrendChart`**, following the exact precedent of the Phase-10 `inkColor` parameter: dashboard behavior changes only by gaining the gesture; the PDF render path passes `allowsSelection: false` and stays byte-identical.
5. **Mixed scales get per-series normalization with a unit-honest scrub readout** (Swift Charts has no dual y-axis); binary/presence series (food tags, bleeding) render as occurrence marks in a lane, never as fake 0/1 lines.

---

## Integration Overview

### Where v1.1 attaches to v1.0

```
┌────────────────────────────────────────────────────────────────────────┐
│ PRESENTATION (consented-only, built inside ConsentedShell)             │
│                                                                        │
│  ┌─────────┐   ┌───────────────────────┐   ┌────────────────────────┐ │
│  │ HomeView│   │ InsightsView          │   │ ExploreView  ★NEW TAB  │ │
│  │ ("Log") │   │ ├ InsightCardView     │   │ ├ SeriesPickerView ★   │ │
│  │         │   │ │  └ InsightTrendChart│   │ ├ ExploreOverlayChart ★│ │
│  │         │   │ │     ✎ + scrub layer │   │ └ ScatterChartView ★   │ │
│  └─────────┘   └───────────┬───────────┘   └───────────┬────────────┘ │
│                            │ insight.points            │              │
│                            │ (cached Insight payload)  │ [DaySnapshot]│
├────────────────────────────┴───────────────────────────┼──────────────┤
│ PURE LOGIC (Foundation-only, unit-tested, no UI/store) │              │
│                                                        │              │
│  ChartSeriesGrouping (shipped — REUSED unchanged)      │              │
│  ChartScrubSelection ★ (nearest-logged-point resolver) │              │
│  ExploreSeries ★ (series catalog + scale metadata)     │              │
│  ExploreSeriesExtraction ★ ([DaySnapshot]+series →     │              │
│                             [InsightPoint])            │              │
│  ScatterPairing ★ ([DaySnapshot]+x+y → paired days)    │              │
├────────────────────────────────────────────────────────┴──────────────┤
│ DATA (unchanged)                                                       │
│                                                                        │
│  Repository.daySnapshots()  ← the SINGLE data source for Explore       │
│  (SwiftData @Model → DaySnapshot projection; manual-wins HK merge;     │
│   nil-honest; consent-asserted)                                        │
└────────────────────────────────────────────────────────────────────────┘
★ = new in v1.1   ✎ = modified in v1.1
```

**Untouched by this milestone:** `InsightsEngine`, `HonestyGate`, `InsightsCoordinator`, the cache (`CachedInsight`), HealthKit import, sync, PDF report content, all SchemaV1 `@Model` types. No schema migration, no CloudKit change.

---

## Q1 — Data Flow: Can Explore Reuse `Repository.daySnapshots()` / `DaySnapshot`?

**Yes — confirmed field-by-field.** Every series named in the milestone goal maps to an existing `DaySnapshot` field (`EndoInsights/Sources/EndoInsights/DaySnapshot.swift`):

| Milestone series | DaySnapshot field | Scale kind | Notes |
|---|---|---|---|
| Symptoms (per type) | `symptoms: [SymptomPoint]` (type + `severity: Int?`) | Ordinal 0–10 | `severity == nil` = logged-present-without-severity → **excluded from numeric plots**, not coerced to 0 |
| Pain (per region) | `pain: [PainPoint]` | Ordinal 0–10 | Bonus series — same shape as symptoms; cheap to include in the catalog |
| Bleeding | `flow: FlowIntensity?` (+ `clots: Bool`) | Ordinal (spotting…heavy) or presence | `nil` = no bleeding logged. See suppression note below |
| Sleep | `sleepHours: Double?` (+ `sleepQuality`) | Continuous hours | Already merged with Apple Health (manual-wins) |
| Activity | `activityMinutes: Double?` (+ type/intensity) | Continuous minutes | Already merged with Apple Health |
| Stress | `stress: Int?` | Ordinal 0–10 | nil-honest (D-07) |
| Mood | `mood: Int?` | Ordinal 1–5 | nil-honest (D-08) |
| Food tags (per tag) | `foodTags: [FoodTag]` | Binary presence | Per-tag presence; imported "ate that day" surfaces as `.other` |
| Medication / PRN | `medicationsTaken: [UUID]`, `tookPRN: Bool` | Binary presence | Optional catalog entries (PRN is an obvious scatter/overlay factor) |

**Why this is the right source (not new queries, not the insight cache):**

- `daySnapshots()` is the **only** place `@Model` rows become values (the SC1 boundary). Adding a second projection for Explore would create a drift risk between what Insights "sees" and what Explore plots.
- It already performs the **manual-wins, nil-honest HealthKit merge** — Explore inherits imported sleep/activity/cycle for free, with provenance semantics identical to the dashboard.
- The honesty contract is *structural*: an unlogged signal is `nil`/absent in the snapshot, so a pure mapper that simply skips nils can never fabricate a zero.

**Caveats to design around (not blockers):**

1. **Suppression honesty (SC3):** the engine emits no bleeding/cycle *claims* under suppression. Explore plots raw data, not claims — a suppressed user plotting their own logged bleeding days is descriptively honest. Recommended posture: the bleeding series stays available (it is the user's own raw log), but `DaySnapshot.suppressed` is already stamped per-snapshot if the roadmap decides to annotate or de-emphasize it. This is a product call to record in the phase plan; the architecture supports either with zero extra plumbing.
2. **Read cost & threading:** `daySnapshots()` fetches every row of five model types and buckets in memory. `InsightsCoordinator` already calls it on the main actor per recompute, so it is within budget today; Explore should follow the **same shipped pattern**: read snapshots on the main actor, run the pure extraction off-main only if profiling ever demands it (the mapper is O(days) and trivial — do **not** pre-build a coordinator/cache for Explore; `recompute`-style machinery is unnecessary because Explore has no engine, no gate, no persistence).
3. **Windowing:** `daySnapshots()` returns all days. Window filtering (7/30/90/all — reuse `TrendWindow`) belongs in the **pure mapper**, mirroring how `InsightTrendChart.xDomain` derives its domain, so "3 points in 30 days reads as sparse" stays true in Explore.

**Data flow (Explore):**

```
ExploreView.onAppear / selection change
    ↓ (main actor)
SwiftDataRepository(context: modelContext).daySnapshots()   ← same per-view
    ↓ [DaySnapshot]                                            construction
ExploreSeriesExtraction.points(snapshots, series, window)     pattern as
    ↓ [InsightPoint] per selected series                      InsightsView's
ChartSeriesGrouping.runs(from:calendar:)  ← REUSED            dismiss path
    ↓ gap-broken runs per series
ExploreOverlayChart (Swift Charts)
```

For the scatter view the middle step is `ScatterPairing.pairs(snapshots, x, y, window)` instead, feeding `ScatterChartView`.

---

## Q2 — Where Series-Selection State Lives

**View-local `@State` in `ExploreView`, modeled as a pure value (`Set<ExploreSeries>` + `TrendWindow` + a `Mode` enum for overlay/scatter).** Do **not** put it in `InsightsCoordinator` (that object is the gated-insights brain — cache, fingerprint, debounce; Explore has none of those concerns) and do **not** create an `@Observable` coordinator for it (there is no async pipeline to coordinate; MV is the app's documented pattern, D-12).

- The selection model should be its own small value type (e.g. `ExploreSelection`) so cap rules ("max 4 series", "scatter needs exactly 2 numeric series") are pure functions with unit tests, leaving the view dumb.
- **Persistence of the last selection is optional and should be deferred or made deliberately ephemeral.** Note the privacy posture: persisting series keys (e.g. `"symptom.pelvicPain"`) via `@AppStorage`/`@SceneStorage` writes health-interest metadata *outside* the consent-gated SwiftData store. Default recommendation: ephemeral `@State` for v1.1; if persistence is wanted later, route it through the Repository like every other health-adjacent datum.

---

## Q3 — New Components (and the Pure/SwiftUI Split)

The split follows the codebase's strongest precedent: **anything that must be provably honest is pure Foundation with unit tests** (`ChartSeriesGrouping` is the template), and SwiftUI views stay thin compositions.

### Pure, Foundation-only (new; unit-tested without rendering)

| Component | Responsibility | Key honesty property under test |
|---|---|---|
| `ExploreSeries` (enum/catalog) | Enumerates every plottable series with metadata: display name (via the existing copy/terminology choke points, ClaimsPolicy-auditable), natural domain (0–10, 1–5, hours, minutes), kind (`ordinal` / `continuous` / `presence`), and an extraction keypath | Catalog covers exactly the DaySnapshot fields — a compile-time-ish exhaustiveness test |
| `ExploreSeriesExtraction` | `([DaySnapshot], ExploreSeries, TrendWindow, asOf, Calendar) → [InsightPoint]` — emits **the existing `InsightPoint`** (`date`+`value`) so `ChartSeriesGrouping` is reused verbatim | A nil field contributes **no point** (never 0); a literal 0 severity **is** a point; presence series emit occurrence days only |
| `ScatterPairing` | `([DaySnapshot], x, y, window) → [(day, x, y)]` — **pairwise-complete days only** | A day missing either factor is excluded, never imputed; output count = honest N (display "n days" with the plot) |
| `ChartScrubSelection` | Resolve a raw selected `Date` to the nearest **logged** point within same-day tolerance, else `nil` | Scrubbing over a gap reads "no entry"/nothing — never an interpolated value |
| `SeriesNormalization` | Map a series' natural domain onto the shared overlay band; inverse-map for readouts | Round-trips exactly; readout always reports the **real** value + unit, never the normalized number |

### SwiftUI (new)

| Component | Responsibility |
|---|---|
| `ExploreView` | `NavigationStack` screen; owns `@State ExploreSelection`; reads `daySnapshots()` on appear/change (same per-view `SwiftDataRepository(context: modelContext)` construction the dismiss path uses); pinned `DisclaimerBanner` (reuse, D-12); warm empty state when <2 logged days; mode toggle overlay/scatter; a11y id `explore.screen`, text summary above charts (the chart-is-never-sole-carrier rule) |
| `SeriesPickerView` | Multi-select from the `ExploreSeries` catalog (cap ~4 for legibility); for scatter, exactly two numeric series |
| `ExploreOverlayChart` | Swift Charts overlay: per-series `ForEach(runs)` `LineMark`s (gap-broken via `ChartSeriesGrouping`), distinct `foregroundStyle(by:)` color + legend; presence series as `PointMark`/tick lanes; shared absolute-date x-axis (never cycle-relative — D-06); scrub via the same selection layer as the trend chart |
| `ScatterChartView` | `PointMark(x:, y:)` per paired day, natural axes (both have real units, so axes are honest here); "Based on N days where both were logged" caption; descriptive-only copy |

### Modified (existing)

| Component | Change |
|---|---|
| `InsightTrendChart` | Additive scrub layer (see Q5) — new defaulted parameters only |
| `AppGate.ConsentedShell` | Third tab (see Q4) |
| ClaimsPolicy audit tests | Extend the scanned-copy surface to the new Explore/scrub copy files (e.g. `ExploreCopy.swift` as the single copy choke point, mirroring `InsightsCopy`) |
| XCUITest E2E | New `-uitest-seed-*` variant seeding multi-series days; assertions on `tab.explore`, `explore.screen`, scrub readout, scatter N-days caption |

---

## Q4 — Navigation: Third Tab vs Entry from Insights

**Recommendation: a third tab, "Explore", between/after Insights.**

- **It is a peer browsing surface, not a drill-down.** Explore answers "let me look at my raw data my way"; Insights answers "what does the app see". Burying Explore behind an Insights toolbar button conflates the *gated claims surface* with the *ungated descriptive surface* — and the separation is itself an honesty feature (no gated copy appears in Explore; no raw overlay implies an insight).
- **The consent invariant is preserved by construction:** add the tab inside `ConsentedShell` only (the documented T-08-12 pattern — no health UI exists outside the `.consented` branch).
- **Known pitfall already documented in `AppGate.swift`:** the a11y identifier must go on the tab-item **`Label`** (`"tab.explore"`), not the content view, or XCUITest can't find the tab button.
- A secondary contextual entry ("Explore this symptom" from a trend card, pre-seeding the selection) is a cheap later enhancement once the tab exists — it's additive routing state, not architecture.

Discoverability/judgment call: a 3-tab shell (Log / Insights / Explore) is well within iOS norms; if the roadmap later adds Settings as a tab, revisit grouping then.

---

## Q5 — Adding Scrubbing to `InsightTrendChart` Without Breaking Dashboard / PDF Usage

**Key fact from the code:** the trend chart plots `insight.points` (the cached `Insight` payload), *not* raw snapshots — so scrubbing needs **zero new data flow**; it reads back exactly the plotted `InsightPoint` values.

**Follow the `inkColor` precedent (Phase 10): additive, defaulted parameters; nothing else changes.**

```swift
// InsightTrendChart — additive only:
var allowsSelection: Bool = true        // PDF renderer passes false → byte-identical print path
@State private var rawSelection: Date?  // internal; nil when not scrubbing

// in `chart`:
.chartXSelection(value: $rawSelection)  // iOS 17+ — fine: the app already requires iOS 17 (@Observable)
// resolved = ChartScrubSelection.resolve(rawSelection, in: points, calendar: calendar)
// if let resolved { RuleMark(x: .value("Date", resolved.date)) + annotation lollipop:
//   date + "severity n of 10" — REAL value, from the logged point only }
```

Integration constraints, all verifiable in tests:

1. **Honesty:** the lollipop renders **only** when `ChartScrubSelection` resolves the touch to a *logged* day (same-day snap). A gap day shows no value (or an explicit "no entry") — never an interpolated number. This is the scrub-side twin of the gap-break invariant and is unit-testable because the resolver is pure.
2. **PDF regression guard:** the report builder constructs a fresh chart; pass `allowsSelection: false` there so the print path provably carries no gesture/annotation chrome. (Selection is `@State`-internal and inert under `ImageRenderer` anyway, but the explicit flag makes the guarantee testable and the intent reviewable.)
3. **Dashboard ergonomics:** the chart lives inside a `List`. `chartXSelection` handles tap and horizontal drag; vertical drags still scroll the list. Acceptable on iOS 17+; flag for UAT, not for redesign.
4. **Accessibility parity:** per-mark `accessibilityLabel/Value` and the text summary already expose every value to VoiceOver/Audio Graph — scrubbing is a pointer-only *addition*, so no a11y regression; keep the summary line untouched.
5. **Copy:** the lollipop label is new user-facing copy → route it through `InsightsCopy` (or `ExploreCopy`) so the ClaimsPolicy scans cover it.

`ExploreOverlayChart` reuses the same `chartXSelection` + `ChartScrubSelection` pattern, with a multi-series readout (one real-unit value per selected series that has a logged point that day, "—" otherwise).

---

## Q6 — Suggested Build Order

Ordered by dependency and risk; each phase is independently shippable and testable.

| # | Phase | Contents | Depends on | Why this order |
|---|---|---|---|---|
| 1 | **Trend-chart scrubbing** | `ChartScrubSelection` (pure + tests) → `InsightTrendChart` scrub layer (`allowsSelection`) → PDF-path regression test → copy scan → E2E scrub assertion | nothing new | Smallest slice; zero new data flow; ships the reusable scrub interaction + the nearest-logged-point honesty resolver that Explore will reuse |
| 2 | **Series catalog + pure extraction** | `ExploreSeries`, `ExploreSeriesExtraction` (emit `[InsightPoint]`), `ScatterPairing`, `SeriesNormalization` — all pure, fully unit-tested; **no UI** | nothing new | The load-bearing honesty layer; proves DaySnapshot field coverage in code before any pixel exists; everything downstream consumes it |
| 3 | **Explore tab + overlay chart** | `ExploreView` + `SeriesPickerView` + `ExploreOverlayChart` + third tab in `ConsentedShell` + `ExploreCopy` + ClaimsPolicy coverage + seeded E2E | 1 (scrub layer), 2 (mappers) | The big visible slice, but by now it's composition: gap-breaking, scrub, extraction, normalization are all pre-tested |
| 4 | **Scatter view** | `ScatterChartView` + mode toggle + N-days caption + E2E | 2 (`ScatterPairing`), 3 (screen/picker) | Pure reuse of the catalog/pairing + an existing screen; the "classic correlation look" copy needs the most careful descriptive framing, so it benefits from the copy infrastructure landing first |

---

## Anti-Patterns (specific to this milestone)

### Anti-Pattern 1: Routing Explore through the InsightsEngine / cache
**What people do:** treat Explore as "another insight kind" and push series data through the engine, gate, and `CachedInsight` payloads.
**Why it's wrong:** the gate exists to withhold *claims* below evidence thresholds; raw user-logged data is not a claim and must never be withheld from its owner. It also bloats cache payloads (T-08-01 forbids snapshots in payloads) and couples a browsing surface to the debounce/fingerprint machinery.
**Instead:** read `daySnapshots()` directly; keep Explore claim-free (descriptive chrome only, ClaimsPolicy-scanned).

### Anti-Pattern 2: A shared numeric y-axis (or fake dual axis) for mixed scales
**What people do:** plot severity (0–10), sleep hours (0–12), and mood (1–5) against one labeled y-axis, or hack a second axis.
**Why it's wrong:** Swift Charts has no dual y-axis; a shared labeled axis fabricates comparability ("sleep 8 looks lower than stress 9"). ZStacked layered charts desynchronize gestures and a11y.
**Instead:** normalize each series to its natural domain for *shape*, suppress numeric y-axis labels on the overlay, and make the **scrub readout + legend** the carrier of real values and units ("Sleep: 7.5 h · Stress: 6 of 10"). Presence series get occurrence-tick lanes, not 0/1 lines. Always offer the text summary line.

### Anti-Pattern 3: Interpolated scrub values
**What people do:** let the selection return a y-value for any x position (lerping between points).
**Why it's wrong:** fabricates data on exactly the gap days the chart was engineered to break on (the v1.0 make-or-break invariant).
**Instead:** pure nearest-logged-point resolution with same-day snap; gaps read as "no entry".

### Anti-Pattern 4: Causal scatter chrome
**What people do:** title the scatter "How sleep affects pain", add trend lines/r-values by default.
**Why it's wrong:** violates the ClaimsPolicy descriptive-only posture; a regression line over self-selected logging days implies causation/strength the data can't support.
**Instead:** "Sleep and pelvic pain, by day" + "Based on N days where both were logged"; no fitted line in v1.1; all copy through the audited choke point.

### Anti-Pattern 5: A new projection or second snapshot type for Explore
**What people do:** write `Repository.exploreSeries()` doing fresh `@Model` queries per series.
**Why it's wrong:** duplicates the SC1 boundary, forks the HealthKit merge and nil-honesty logic, and guarantees drift between Insights and Explore.
**Instead:** one projection (`daySnapshots()`), many pure mappers.

---

## Integration Points (summary table for the roadmap)

| Boundary | Kind | Change |
|---|---|---|
| `Repository.daySnapshots()` → Explore | reuse | none (read-only consumer; per-view `SwiftDataRepository(context:)` like `InsightsView`'s dismiss path) |
| `EndoInsights.InsightPoint` → extraction output | reuse | none (mapper emits it) |
| `ChartSeriesGrouping` → overlay chart | reuse | none |
| `InsightTrendChart` → dashboard + PDF | modify | additive defaulted params only (`allowsSelection`, internal `@State`); PDF passes `false` |
| `AppGate.ConsentedShell` | modify | +1 tab, a11y id on the Label (`tab.explore`) |
| `TrendWindow` | reuse | window picker in Explore reuses the existing enum |
| ClaimsPolicy audit tests | extend | add `ExploreCopy` (and any new chart copy) to the scanned surface |
| XCUITest harness | extend | new seed variant + Explore/scrub assertions; existing `-uitest-reset` reused |
| `InsightsEngine` / `HonestyGate` / `InsightsCoordinator` / SchemaV1 / CloudKit | untouched | — |

---

## Sources

- Codebase (HIGH confidence, read directly): `Sources/EndoApp/App/AppGate.swift`, `Sources/EndoApp/Persistence/Repository.swift` (`daySnapshots()` projection + merge), `EndoInsights/Sources/EndoInsights/DaySnapshot.swift`, `EndoInsights/Sources/EndoInsights/Insight.swift` (`InsightPoint`), `Sources/EndoApp/Features/Insights/{InsightTrendChart,ChartSeriesGrouping,InsightsCoordinator,InsightsView,InsightCardView}.swift`, `.planning/PROJECT.md`
- Swift Charts selection (iOS 17 `chartXSelection` + RuleMark/annotation lollipop pattern) — MEDIUM-HIGH (Apple WWDC23 + multiple corroborating community sources): [WWDC23 — Explore pie charts and interactivity in Swift Charts](https://developer.apple.com/videos/play/wwdc2023/10037/), [Swift with Majid — Mastering charts: Selection](https://swiftwithmajid.com/2023/07/18/mastering-charts-in-swiftui-selection/), [Custom line chart with gestures (iOS 17/18)](https://medium.com/@gerastupakov/swiftui-charts-in-ios-18-custom-line-chart-with-gestures-symbols-more-6e46d8b9c072)
- No native dual y-axis in Swift Charts; normalization / layering workarounds — MEDIUM (Apple dev forum + community consensus): [Apple Developer Forums — secondary Y axis](https://developer.apple.com/forums/thread/742659), [Customizing axes in Swift Charts (Apple docs)](https://developer.apple.com/documentation/charts/customizing-axes-in-swift-charts)

---
*Architecture research for: Endo-App v1.1 — Interactive Charts & Explore*
*Researched: 2026-06-10*
