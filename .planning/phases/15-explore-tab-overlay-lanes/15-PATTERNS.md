# Phase 15: Explore Tab & Overlay Lanes - Pattern Map

**Mapped:** 2026-06-15
**Files analyzed:** 10 (8 new source + 2 new/edited tests + 1 edited shell + 1 edited seam + project.yml)
**Analogs found:** 10 / 10 (every new file has a strong shipped analog — this phase composes shipped primitives)

> Platform: native iOS, SwiftUI + Swift Charts, Swift 6 strict concurrency. The `.xcodeproj` is XcodeGen-generated — any added file requires a `project.yml` entry + `xcodegen generate`. There is NO web/registry surface.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `Sources/EndoApp/Features/Explore/ExploreView.swift` | component (screen) | request-response (read snapshots → render) | `Features/Insights/InsightsView.swift` | role-match (screen scaffold; List→ScrollView) |
| `Sources/EndoApp/Features/Explore/ExploreLanesView.swift` | component (lane stack + tooltip overlay) | event-driven (shared scrub) | `Features/Insights/InsightTrendChart.swift` | role-match (chart vocab, no `Chart` analog elsewhere) |
| `Sources/EndoApp/Features/Explore/ExploreLaneChart.swift` | component (one quant lane) | event-driven | `Features/Insights/InsightTrendChart.swift` (`chart` body, runs, xDomain, selectionMarks) | exact |
| `Sources/EndoApp/Features/Explore/EventLaneChart.swift` | component (one event lane) | event-driven | `InsightTrendChart.swift` (Chart scaffold) + `ExploreSeriesExtraction.occurrenceDays` | role-match |
| `Sources/EndoApp/Features/Explore/SeriesPickerView.swift` | component (chip picker) | transform (catalog → chips) | `InsightsView.swift` section/state helpers + `ExploreSeriesCatalog.all` | role-match |
| `Sources/EndoApp/Features/Explore/ExploreSelection.swift` | model (pure value type) | transform (cap + suppression policy) | `Domain/CycleSummary.swift` / `Domain/FactorDay.swift` (app-target pure struct); cap shape in RESEARCH Pattern 4 | role-match |
| `Sources/EndoApp/Features/Explore/ExploreCopy.swift` | config (audited copy enum) | n/a | `Features/Insights/InsightsCopy.swift` | exact |
| `Sources/EndoApp/App/AppGate.swift` (EDIT) | component (shell) | n/a | `AppGate.swift` `ConsentedShell` itself | exact (insertion point) |
| `Sources/EndoApp/App/AppDependencies.swift` (EDIT) | provider (composition root + E2E seam) | n/a | `AppDependencies.swift` `-uitest-seed-scrub` + `-uitest-select-day` seams | exact |
| `Tests/EndoAppTests/CopyAuditTests.swift` (EDIT) | test (copy scan registration) | n/a | `CopyAuditTests.swift` `insightsCopy_*` suite | exact |
| `Tests/EndoAppTests/ExploreSelectionTests.swift` | test (unit, Swift Testing) | n/a | (none — new; Swift Testing `@Suite` shape from `CopyAuditTests`) | partial |
| `Tests/EndoAppUITests/ExploreUITests.swift` | test (E2E) | n/a | `Tests/EndoAppUITests/ChartScrubUITests.swift` + `InsightsDashboardUITests.swift` | exact |

**Reused UNCHANGED (note as reuse, NOT new — do NOT re-implement or re-test):**
- `EndoInsights`: `ExploreSeriesCatalog.all`, `ExploreSeries`/`ScaleKind`/`ExploreSeriesField`, `ExploreSeriesExtraction.{quantitativePoints,occurrenceDays}`, `SeriesNormalization.{domain,normalize,denormalize,readout}`, `ChartScrubSelection.resolve`, `TrendWindow`, `InsightPoint`, `DaySnapshot` (Phase 13/14, unit-tested).
- App target: `ChartSeriesGrouping.runs(from:calendar:)` (gap-run grouping), `DisclaimerBanner`/`Disclaimer.text` (reused verbatim), `Repository.daySnapshots()` / `SwiftDataRepository`.

---

## Pattern Assignments

### `ExploreCopy.swift` (config, audited copy enum) — EXACT analog

**Analog:** `Sources/EndoApp/Features/Insights/InsightsCopy.swift`

**Shape to copy** (`InsightsCopy.swift:17-22`, `124-145`): `import Foundation` + `import EndoInsights`; an `enum` of `static let` strings; a `static func windowLabel(_:) -> String` over `TrendWindow`; and the mandatory `static var auditableStrings: [String]` that flattens every fixed string. Mirror `InsightsCopy.windowLabel` exactly (`InsightsCopy.swift:73-80`) — reuses the audited "last N days" wording for EXPL-06:
```swift
static func windowLabel(_ window: TrendWindow) -> String {
    switch window {
    case .last7:   return "last 7 days"
    case .last30:  return "last 30 days"
    case .last90:  return "last 90 days"
    case .allTime: return "all time"
    }
}
```
**`auditableStrings` pattern** (`InsightsCopy.swift:124-144`) — register EVERY fixed string + `TrendWindow.allCases.map(windowLabel)`; representative `func`-built strings appended once. **Reuse the gap wording verbatim:** `InsightsCopy.scrubNotLogged = "Not logged"` (`InsightsCopy.swift:110`) — the UI-SPEC tooltip-gap line reuses this posture; either reference it or define `ExploreCopy`'s own equal constant and pin it (see CopyAuditTests below).

**Pre-verified strings to encode** (UI-SPEC Copywriting Contract, all curated clean): `"Pick a series to begin."`, the empty body, `"Not enough data yet."`, the sparse body, `"Up to four series at once. Tap a selected chip to remove one, then add a new one."` (NOTE: "another"→"a new one" — "another" ⊃ "her"), `"Remove series"`, `"Add to the timeline."`, `eventTagged = "Logged"` (RESEARCH Pattern 2 uses `ExploreCopy.eventTagged`).

---

### `ExploreCopy` registration in `CopyAuditTests.swift` (EDIT) — EXACT analog

**Analog:** the `insightsCopy_*` suite, `Tests/EndoAppTests/CopyAuditTests.swift:142-230`.

Add a parallel block (clone `:149-211`). Concrete excerpts to mirror:
```swift
private var allExploreCopy: [String] { ExploreCopy.auditableStrings }   // mirror :150

@Test func exploreCopy_containsNoForbiddenVerb() { /* clone :153-161 — ClaimsPolicy.forbiddenVerbs */ }
@Test func exploreCopy_containsNoGenderedToken() { /* clone :164-172 — forbiddenGenderedTokens (the "her" trap) */ }
@Test func exploreCopy_hasNoNumericConfidence() { /* clone :176-198 — forbiddenPrecisionTokens %, p=, r= */ }
@Test func exploreCopy_isNonEmpty() { /* clone :201-211 */ }
```
**Pitfall 3 (LOAD-BEARING):** this edit ships in the SAME task that creates `ExploreCopy`, BEFORE any view renders a string — an unregistered enum escapes all scans. Pin the locked tooltip wording exactly as `insightsCopy_scrubTooltipStrings_areLockedVerbatim` (`:219-230`) does: `#expect(... == "Not logged")` + `#expect(ExploreCopy.auditableStrings.contains(...))`. The forbidden imperative/alarmist scans (`firstImperative`/`firstAlarmist`, `:305-308`/`:406-409`) already exist in this file — apply them to `allExploreCopy` too (Explore is descriptive-only).

---

### `ExploreSelection.swift` (model, pure value type) — APP TARGET (RESOLVED, RESEARCH OQ2)

**Analog:** an app-target pure value struct (`Domain/CycleSummary.swift`, `Domain/FactorDay.swift` for the `struct: Equatable`/`Sendable` style); the cap/suppression policy shape is given verbatim in RESEARCH Pattern 4. `import EndoInsights` for `ExploreSeries`/`ScaleKind`/`TrendWindow`.

**Core pattern** (RESEARCH Pattern 4, lines 251-269) — copy the cap + toggle directly:
```swift
struct ExploreSelection: Equatable {
    private(set) var series: Set<ExploreSeries> = []
    var window: TrendWindow = .last30
    static let quantitativeCap = 4
    var quantitativeCount: Int { series.filter { $0.scaleKind.isQuantitative }.count }
    func canAddQuantitative(_ s: ExploreSeries) -> Bool {
        !s.scaleKind.isQuantitative || series.contains(s) || quantitativeCount < Self.quantitativeCap
    }
    mutating func toggle(_ s: ExploreSeries) {
        if series.contains(s) { series.remove(s) }
        else if canAddQuantitative(s) { series.insert(s) }   // event series never blocked
    }
}
```
`Set<ExploreSeries>` works because `ExploreSeries` is `Hashable` from `id` alone (`ExploreSeries.swift:97-110`). `scaleKind.isQuantitative` is `ScaleKind.isQuantitative` (`ExploreSeries.swift:41`).

**Suppression filter** is a CATALOG filter applied where chips are built (NOT in the cap model) — RESEARCH Pattern 4 lines 264-268:
```swift
let offered = ExploreSeriesCatalog.all.filter { s in
    !(suppressed && (s.id == "bleeding.intensity" || s.id == "bleeding.event"))
}
```
The bleeding ids are exactly those in the catalog (`ExploreSeries.swift:252,255`). Defense-in-depth: the Phase-14 accessors already gate bleeding under `!snap.suppressed` (`ExploreSeries.swift:148,172`).

---

### `ExploreSelectionTests.swift` (test, unit) — Swift Testing, NEW

**Analog:** the `@Suite`/`@Test`/`#expect` shape in `CopyAuditTests.swift:6-12`. App target (Swift Testing, NOT XCTest — they don't interoperate; keep separate from the XCUITest target).

Required cases (RESEARCH Test Map, EXPL-02): the cap blocks the 5th quantitative; an event series is uncapped; toggle adds then removes; `offered` filters bleeding under suppression (`suppressionFiltersBleeding`). Build `ExploreSeries` fixtures by pulling specific entries from `ExploreSeriesCatalog.all` (e.g. `.first { $0.id == "sleep.hours" }`) — do NOT hand-construct, so the test can't drift from the catalog.

---

### `ExploreView.swift` (screen) — role-match

**Analog:** `Sources/EndoApp/Features/Insights/InsightsView.swift`.

**Screen scaffold to mirror** (`InsightsView.swift:45-93`): `NavigationStack { … }.navigationTitle("Explore").accessibilityIdentifier("explore.screen")`. **DIVERGENCE (LOCKED):** use a `ScrollView`, NOT a `List` (the lanes are a fixed free-scrub surface — Pitfall 7 / UI-SPEC). Pin `DisclaimerBanner()` at the top with `.accessibilityIdentifier("explore.disclaimer")` exactly as `InsightsView.swift:49-51`.

**Data read** (mirror the dismiss-path construction, RESEARCH §"Reading the data source"; `InsightsView` builds `SwiftDataRepository(context: modelContext)` per-view on the main actor):
```swift
@Environment(\.modelContext) private var modelContext
func loadSnapshots() -> [DaySnapshot] {
    (try? SwiftDataRepository(context: modelContext).daySnapshots()) ?? []
}
// suppressed flag for the picker filter: snapshots.last?.suppressed
```
`Repository.daySnapshots() throws -> [DaySnapshot]` is the single source; `DaySnapshot.suppressed` is the flag.

**Empty/sparse states** (EXPL-07) — clone `InsightsView.emptyState` (`:144-167`): a `VStack` with an `Image(systemName:).accessibilityHidden(true)` + heading + body, `.fixedSize(horizontal: false, vertical: true)`, `.accessibilityElement(children: .combine)`, `.accessibilityIdentifier("explore.empty")` (and a sibling `"explore.sparse"`). Use the icon `square.stack.3d.up` and `ExploreCopy` strings.

**Owns two `@State`:** the `ExploreSelection` (ephemeral — NEVER `@AppStorage`, privacy LOCKED) and `selectedDate: Date?` (the ONE shared scrub state, passed by `@Binding` into every lane).

**Cache derived points** once per selection/window change (Pitfall 4) — do NOT re-read snapshots or recompute `runs` per scrub frame.

---

### `ExploreLaneChart.swift` (one quant lane) — EXACT analog

**Analog:** `InsightTrendChart.swift` — the ONLY `Chart` vocabulary in the repo; mirror it closely.

**Imports** (`InsightTrendChart.swift:16-20`): `SwiftUI`, `Charts`, `UIKit`, `Accessibility`, `EndoInsights`.

**xDomain derivation** — copy `InsightTrendChart.xDomain` verbatim (`:86-98`); it is the EXPL-06 absolute-date window math (`TrendWindow` switch, sparse-safe). Lift to a SHARED domain computed once in `ExploreLanesView` and pass identical into every lane (Pitfall 1: identical X scale across siblings).

**Runs (honest gaps)** — `ChartSeriesGrouping.runs(from: points, calendar:)` (`InsightTrendChart.swift:80-82`; signature `ChartSeriesGrouping.runs(from:[InsightPoint], calendar:) -> [Run]`, `Run.points` at `ChartSeriesGrouping.swift:42,28`). One `LineMark(series: .value("Run", run.id))` per run so the line BREAKS at gaps + a `PointMark` per logged day so a literal 0 draws a point (`InsightTrendChart.swift:181-208`). Carry color via `.foregroundStyle(by: .value("Series", series.displayName))` (legend + colorblind-safe palette; never hue alone).

**yDomain** — `SeriesNormalization.domain(for: series.scaleKind)` (FIXED honest range, `SeriesNormalization.swift:34-43`); `.chartYScale(domain:)` per lane in REAL units (no dual-Y, no normalization).

**Shared scrub** — `.chartXSelection(value: $selectedDate)` bound to the parent `@State` on EVERY lane (`InsightTrendChart.swift:120`). Resolve via `ChartScrubSelection.resolve(selectedDate, in: points, calendar:)` (`:75-77`) — the ONLY legal raw-date→value path. The gray `RuleMark` chrome + selection point: clone `selectionMarks` (`:241-264`), `.foregroundStyle(Color.secondary.opacity(0.5)).zIndex(-1)`.

**Per-mark a11y + Audio Graph** — clone the `.accessibilityLabel`(date)/`.accessibilityValue`(readout) pattern (`:189-207`) but route the value through `SeriesNormalization.readout(_:kind:unit:)` instead of `InsightsCopy.scrubSeverity`. Mirror the `@preconcurrency AXChartDescriptorRepresentable` extension (`:357-390`) — ONE descriptor per quantitative lane, logged points only (gaps omitted, never zeros).

**Lane alignment (Pitfall 1, CENTRAL RISK / Wave-0 prototype):** default to `.chartYAxis(.hidden)` and carry the unit in the lane TITLE (RESEARCH Open Question 1 RESOLVED) so plot leading insets match across siblings; fixed-width Y axis is the fallback. Frame height `120` (`lane-quant-height`).

**Haptic** — clone the `.onChange(of: resolvedSelection?.day)` guard verbatim (`:126-133`): one `UISelectionFeedbackGenerator` per view identity, fires on resolved-DAY change only.

---

### `EventLaneChart.swift` (one event lane) — role-match

**Analog:** `InsightTrendChart.swift` Chart scaffold + `ExploreSeriesExtraction.occurrenceDays` (`:57-64`, returns `[Date]`, no value field).

**Core pattern** (RESEARCH Pattern 2, lines 205-228): a `Chart` of `RectangleMark`/`PointMark` at each occurrence `Date`, `.chartYScale(domain: 0...1)` + `.chartYAxis(.hidden)`, shared `.chartXScale(domain: xDomain)` + `.chartXSelection(value: $selectedDate)`, `.frame(height: 44)` (`lane-event-height`, also the 44pt touch floor). Per-tick `.accessibilityValue(Text(ExploreCopy.eventTagged))` — "Logged", never "1".

**Pitfall 2 (event-lane tooltip has NO `ChartScrubSelection` path):** the resolver works on `[InsightPoint]`, not `[Date]`. For the scrubbed day, look up that day's `DaySnapshot` and call `series.isPresent(in: snap)` (`ExploreSeries.swift:159-178`) → "Logged"/"Not logged". Keep a `startOfDay`-keyed `[Date: DaySnapshot]` map from the same `daySnapshots()` read.

---

### `ExploreLanesView.swift` (lane stack + multi-series tooltip) — role-match

**Analog:** `InsightTrendChart.swift` (the tooltip + accessibility-proxy machinery).

**Lane stack:** `VStack(spacing: md) { ForEach(selection quant series) { ExploreLaneChart(...) } ; ForEach(event series) { EventLaneChart(...) } }` — all passed the IDENTICAL shared `xDomain` + the SAME `$selectedDate` binding.

**Single multi-series tooltip** (RESEARCH Pattern 3): ONE card (not per-lane) above the stack, listing the date once + one line per visible series via `SeriesNormalization.readout` (gap → `ExploreCopy.notLogged`). **a11y proxy** — clone `selectionAccessibilityProxy` (`InsightTrendChart.swift:305-336`): a transparent `.allowsHitTesting(false)` overlay carrying the readout + ids (Swift Charts does NOT expose `.annotation` content to the AX tree); the visual card is `.accessibilityHidden(true)`. Per-series id `explore.scrub.tooltip.<series.id>` + `.notLogged` suffix on gap; shield with `.accessibilityElement(children: .contain)` (the id-bleed fix, `:334`).

**E2E seam read:** read `@Environment(\.uiTestScrubDay)` and seed `selectedDate` exactly as `InsightTrendChart` does via the keyed `.task(id: uiTestScrubDay)` guard (`:149-153`) — `xDomain.contains(day)` + `rawSelectedDate == nil` guards are LOAD-BEARING.

---

### `SeriesPickerView.swift` (chip picker) — role-match

**Analog:** `InsightsView` state/section helpers + `ExploreSeriesCatalog.all`.

Toggle chips in a flowing layout over the suppression-filtered `offered` catalog (see `ExploreSelection` excerpt). Selected vs unselected differ by fill + checkmark/`xmark` symbol + weight (NEVER hue — color-independence). Cap-reached → disable unselected quantitative chips + show the `ExploreCopy` cap helper. a11y ids: `explore.picker`, `explore.chip.<series.id>` (value-independent — the series id), `explore.chip.<series.id>.remove`, `explore.picker.capHint`. Range control (EXPL-06): a segmented `Picker` over `TrendWindow.allCases` (`Insight.swift:31-35`), labels via `ExploreCopy.windowLabel`; ids `explore.range`, `explore.range.<window.rawValue>`.

---

### `AppGate.swift` (EDIT — add third tab) — EXACT analog

**Analog:** `ConsentedShell` itself, `AppGate.swift:47-64`.

Add a THIRD `.tabItem` to the existing `TabView` (built ONLY in the `.consented` branch — consent-by-construction). Mirror the `tab.insights` id-on-Label exactly (`:55-61`) — **Pitfall 5:** the id MUST be on the `Label`, not the content view:
```swift
ExploreView().tabItem {
    Label("Explore", systemImage: "square.stack.3d.up")   // UI-SPEC: distinct from chart.line.uptrend.xyaxis
        .accessibilityIdentifier("tab.explore")
}
```

---

### `AppDependencies.swift` (EDIT — E2E seam) — EXACT analog

**Analog:** the `-uitest-seed-scrub` fixture + `-uitest-select-day` seam already in this file.

**Seed seam** — clone `seedScrub`/`seedScrubFixture` (`AppDependencies.swift:114`, `362-371`): a `let seedExplore = ProcessInfo...contains("-uitest-seed-explore")` flag; `if seedExplore && uiTestReset { Self.seedExploreFixture(into: insightsRepo) }` (gated DEBUG-friendly, `-uitest-reset`-paired so it only ever touches the in-memory store — the security invariant). The fixture (RESEARCH §E2E seam) seeds ≥1 quantitative series with an in-window gap + a logged-0 day, a SECOND quantitative series on a different scale (sleep hours via `repo.setSleep`), and an event series (a food tag via `repo.setFood`), ALL inside the chosen window (mirror the Phase-13 "all anomalies in last-7" rule).

**Selection seam** — REUSE the EXISTING `-uitest-select-day` / `\.uiTestScrubDay` machinery UNCHANGED (`:122-135`, `:639-655`). `ExploreLanesView` reads `\.uiTestScrubDay` the same way `InsightTrendChart` does (RESEARCH A4). No new EnvironmentKey needed for selection.

---

### `ExploreUITests.swift` (E2E) — EXACT analog

**Analog:** `Tests/EndoAppUITests/ChartScrubUITests.swift` + `InsightsDashboardUITests.swift`.

**Launch + open tab** — clone `launchAndOpenInsights` (`ChartScrubUITests.swift:38-67`): grant consent (`consent.grantButton`), then resolve the tab with `app.tabBars.buttons["tab.explore"]` falling back to `app.descendants(matching: .any)["tab.explore"].firstMatch` (`:54-57`). Launch args `["-uitest-reset", "-uitest-seed-explore", "-uitest-select-day", dayArg(daysAgo:)]`; clone `dayArg` verbatim (`:71-78`).

**Sub-element id-bleed workaround** — use the ENDSWITH predicate + `firstMatch` pattern (`ChartScrubUITests.swift:87-90`; also `InsightsDashboardUITests.swift:72`):
```swift
let predicate = NSPredicate(format: "identifier ENDSWITH '.scrub.tooltip'")
app.descendants(matching: .any).matching(predicate).firstMatch
```
Assert via `element.value` for the readout (`:125-128`) and `marker.exists`/`waitForExistence` for the `.notLogged` gap marker (`:143`). Cover EXPL-01/02/05/06/07: `tab.explore`, `explore.chip.<id>`, suppression hides bleeding chip, multi-series tooltip lists each series, range switch, empty + sparse states.

---

## Shared Patterns

### Audited copy choke-point (Pitfall 3 — applies to `ExploreCopy`)
**Source:** `InsightsCopy.swift:124-145` + `CopyAuditTests.swift:142-230` + `ClaimsPolicy` token lists.
**Apply to:** every fixed Explore string. Register `auditableStrings` in `CopyAuditTests` in the SAME task that introduces the enum, BEFORE any view renders a string. Substring traps confirmed this session: "her" ⊂ together/whether/other/higher/there/here/she; "treat" ⊂ treatment; forbidden verbs incl. will/causes/triggers/predicts/detect/prevent (`ClaimsPolicy.forbiddenVerbs`). Use `SeriesNormalization.readout` for value lines (digits in a factual readout are legal — the bare-digit scan is scoped to confidence words, which Explore has none of).

### Consent-by-construction (V4 access control)
**Source:** `AppGate.swift:33-40,47`.
**Apply to:** `ExploreView` — built ONLY inside the `.consented` `ConsentedShell` branch; no health UI before opt-in.

### Honest gaps + exact-day scrub (carry-over invariants)
**Source:** `ChartSeriesGrouping.runs` + `ChartScrubSelection.resolve` (reused UNCHANGED) + `InsightTrendChart.swift` mark-keying.
**Apply to:** every quantitative lane. One `series:` per (series × run); never `proxy.value(atX:)`/nearest-point; gap → no mark → "Not logged"; logged 0 → real point.

### E2E determinism seam (no flaky gestures)
**Source:** `AppDependencies.swift` `-uitest-seed-*` + `\.uiTestScrubDay` (DEBUG-only, `-uitest-reset`-paired in-memory store).
**Apply to:** `-uitest-seed-explore` (new) + reuse `-uitest-select-day` (unchanged). Gate seeds behind `&& uiTestReset` so they can never touch a real store.

### Charts a11y parity (chart never the sole carrier of meaning)
**Source:** `InsightTrendChart.swift` per-mark labels (`:189-207`), `AXChartDescriptor` (`:357-390`), transparent-overlay readout proxy (`:305-336`).
**Apply to:** every lane (per-lane descriptor + one-line text summary) and the multi-series tooltip (reachable WITHOUT the drag gesture via the proxy).

---

## No Analog Found

None. Every new file maps to a shipped analog. The two genuinely-new shapes are:
- The **sibling-lane assembly** (`ExploreLanesView`/`ExploreLaneChart`/`EventLaneChart`) — no multi-lane `Chart` exists, but the per-lane vocabulary is `InsightTrendChart` verbatim. The shared-X/shared-selection alignment (Pitfall 1) is the Wave-0 prototype item.
- `ExploreSelectionTests` (no prior Swift Testing unit suite for a pure cap model) — uses the `@Suite`/`@Test`/`#expect` shape from `CopyAuditTests`.

---

## Metadata

**Analog search scope:** `Sources/EndoApp/{App,Features/Insights,Domain}`, `EndoInsights/Sources/EndoInsights`, `Tests/EndoAppTests`, `Tests/EndoAppUITests`.
**Files scanned:** ~14 (3 read in full beyond required reading: ExploreSeries, SeriesNormalization, ExploreSeriesExtraction; grep-confirmed: ChartSeriesGrouping, TrendWindow, InsightsDashboardUITests id-bleed).
**Reuse contracts verified against shipped source this session (HIGH confidence).**
**Pattern extraction date:** 2026-06-15
