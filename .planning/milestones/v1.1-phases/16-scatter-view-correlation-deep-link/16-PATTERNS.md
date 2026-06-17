# Phase 16: Scatter View & Correlation Deep-Link - Pattern Map

**Mapped:** 2026-06-16
**Files analyzed:** 13 (4 new app-target, 2 new test, 7 edited)
**Analogs found:** 13 / 13 (all exact or strong role-match — this is a compose-shipped-primitives phase)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `Sources/EndoApp/Features/Explore/ScatterPlotView.swift` | component (Swift Charts view) | transform / request-response | `Features/Insights/InsightTrendChart.swift` (`chart` body, lines 178-234) | exact (Chart + PointMark + chartX/YScale recipe) |
| `Sources/EndoApp/Features/Insights/ScatterDeepLink.swift` | utility (pure resolver) | transform (string → typed value) | `Features/Explore/ExploreSelection.swift` `offered(suppressed:)` (lines 60-67) | role-match (pure app-target value mapper over the catalog) |
| `Sources/EndoApp/App/ExploreScatterRouter.swift` | provider (@Observable env router) | event-driven (intent flag) | `App/CheckInRouter.swift` (lines 16-45, whole file) | exact (mirror the @Observable + EnvironmentKey + assumeIsolated default) |
| `Sources/EndoApp/Features/Explore/ExploreView.swift` (EDIT) | component (screen) | request-response | self (existing structure: `body` 60-103, `honestState` 233-253, `isTooSparse` 197-207, `suppressed` 56-58) | exact (in-place extension) |
| `Sources/EndoApp/Features/Explore/ExploreCopy.swift` (EDIT) | config (audited copy) | — | self (lines 70-72 `eventLaneSummary`, 91-104 `auditableStrings`) | exact |
| `Sources/EndoApp/Features/Explore/SeriesPickerView.swift` (EDIT or sibling axis picker) | component | request-response | self (segmented `Picker` 78-86; chip a11y-id pattern 105-143) | exact |
| `Sources/EndoApp/Features/Insights/InsightCardView.swift` (EDIT) | component (card) | request-response | self (`supportingData` 151-178; `idRoot` 33; info-button affordance 120-145; dismiss affordance 209-218) | exact |
| `Sources/EndoApp/App/AppGate.swift` (EDIT) | route (TabView shell) | request-response | self (`ConsentedShell` 50-76 — the bare `TabView`) | exact (add `selection:` + `.tag`) |
| `Sources/EndoApp/App/AppDependencies.swift` (EDIT) | config (test seed seam) | batch | self (`-uitest-seed-*` flags 126-133; `seedRecommendationCorrelation` 491-505; defensive `-uitest-select-day` parse 142-153) | exact |
| `Tests/EndoAppUITests/ScatterUITests.swift` | test (E2E) | request-response | `Tests/EndoAppUITests/ExploreUITests.swift` (harness 47-86) | exact (clone the launch+consent+tab harness) |
| `Tests/EndoAppTests/ScatterDeepLinkTests.swift` | test (pure unit) | transform | (Swift Testing `@Test`/`#expect`; resolver mirrors `ExploreSelection.offered` testability) | role-match |
| `Tests/EndoAppTests/CopyAuditTests.swift` (EDIT) | test (copy scan) | — | self (`allExploreCopy` block 534-601) | exact |

### EndoInsights — consumed UNCHANGED (NOT new files; reuse only)
- `ScatterPairing.pairs(_:x:y:calendar:) -> [ScatterPoint]` — `ScatterPairing.swift:66`; `ScatterPoint` is `Sendable, Equatable, Hashable` with `public let x/y/count` at `:37-43` (the `id: \.self` `ForEach` and the count caption depend on this shape).
- `SeriesNormalization.domain(for:) -> ClosedRange<Double>?` and `.readout(_:kind:unit:)`.
- `ExploreSeriesCatalog.all` / `ExploreSeries` / `ScaleKind.isQuantitative` (= `self != .presenceEvent`).
- `CorrelationEngine` signalKey grammar (parsed, never reconstructed) + `Factor.key` (input grammar only).

## Pattern Assignments

### `Sources/EndoApp/Features/Explore/ScatterPlotView.swift` (component, transform)

**Analog:** `Features/Insights/InsightTrendChart.swift` — the `chart` body (lines 178-234).

**Core chart pattern** (mirror `InsightTrendChart.chart` 178-234 — swap `LineMark`/`PointMark` pairs for a single `PointMark` cloud, swap the date X domain for a fixed real-unit domain):
```swift
// Analog InsightTrendChart.swift:199-217 — PointMark loop + fixed Y domain + axis blocks:
Chart {
    ForEach(points, id: \.date) { point in
        PointMark(x: .value("Date", point.date), y: .value("Severity", point.value))
            .foregroundStyle(inkColor)
            .accessibilityLabel(Text(point.date, format: .dateTime.month().day()))
            .accessibilityValue(Text(InsightsCopy.scrubSeverity(Int(point.value.rounded()))))
    }
}
.chartXScale(domain: xDomain)
.chartYScale(domain: 0...10)
.chartXAxis { AxisMarks { AxisGridLine(); AxisTick(); AxisValueLabel(format: .dateTime.month().day()) } }
.chartYAxis { AxisMarks(values: [0, 5, 10]) }
.animation(reduceMotion ? nil : .default, value: points)   // ← Reduce Motion parity (line 233)
```
**For the scatter, change:** `id: \.self` over `[ScatterPoint]` (it is `Hashable`); both axes get `.chartXScale(domain: SeriesNormalization.domain(for: xSeries.scaleKind) ?? 0...10)` / `.chartYScale(...)`; add `.symbolSize(by: .value("Days", p.count))`; per-mark a11y via `SeriesNormalization.readout`; `.foregroundStyle(Color.accentColor)` single hue; `.frame(height: 320)` + `.chartPlotStyle { $0.padding(8) }`. Keep the `.animation(reduceMotion ? nil : …)` line verbatim.

**Honesty constraint:** NO `LineMark`, NO regression, NO `.chartYScale` data-driven domain. `ScatterPoint` carries only x/y/count — no statistic can leak.

---

### `Sources/EndoApp/Features/Insights/ScatterDeepLink.swift` (utility, pure resolver)

**Analog (pure app-target value mapper):** `Features/Explore/ExploreSelection.swift` `offered(suppressed:)` (lines 60-67) — a `static func` over `ExploreSeriesCatalog.all` returning a filtered/typed value, with the bleeding-suppression gate inline.

```swift
// ExploreSelection.swift:60-67 — the suppression-gated catalog filter to mirror:
static func offered(suppressed: Bool, catalog: [ExploreSeries] = ExploreSeriesCatalog.all) -> [ExploreSeries] {
    catalog.filter { s in
        !(suppressed && (s.id == "bleeding.intensity" || s.id == "bleeding.event"))
    }
}
```

**Input grammar to parse (NOT reconstruct) — `CorrelationEngine.swift`:**
```swift
// :279  severity arm:  signalKey: "correlation.\(factor.key).symptom.\(symptom.rawValue)"
// :327  presence arm:  signalKey: "correlation.\(factor.key).\(target.keyFragment)"
// :242-245 keyFragment: .bleeding → "bleeding"  (symptom targets → "symptom.<rawValue>")
```
**`Factor.key` (input keys) — `Factor.swift:51-63`:** `stress.high`, `mood.low`, `sleep.poor`, `food.<tag>`, `activity.present`/`activity.<type>`, `medication.taken`, `prn.taken`, `cyclePhase.<phase>`. Per A1/RESOLVED-Q1: ONLY `stress.high → "stress"` and `mood.low → "mood"` map to a quantitative axis; everything else (including `sleep.poor`) returns `nil` → HIDE affordance.

**Resolver shape** (RESEARCH §The Deep-Link Resolver): `resolve(signalKey:suppressed:catalog:) -> (x: ExploreSeries, y: ExploreSeries)?`. Parse by KNOWN suffix (`bleeding` / `symptom.<SymptomType.rawValue>`), look ids up in `catalog` (never construct `ExploreSeries`), return only if BOTH resolved AND `.isQuantitative`; `bleeding` target under `suppressed` → `nil`. **Defensive parse posture** (ASVS V5) mirrors the `-uitest-select-day` parser (`AppDependencies.swift:142-153`): malformed key → `nil`, never crash.

---

### `Sources/EndoApp/App/ExploreScatterRouter.swift` (provider, event-driven)

**Analog:** `App/CheckInRouter.swift` (whole file, lines 16-45) — copy the structure verbatim, change the payload.

```swift
// CheckInRouter.swift:16-45 — @Observable + EnvironmentKey + assumeIsolated default:
@MainActor @Observable
final class CheckInRouter {
    var presentCheckIn = false
    init(presentCheckIn: Bool = false) { self.presentCheckIn = presentCheckIn }
}
private struct CheckInRouterKey: EnvironmentKey {
    static var defaultValue: CheckInRouter { MainActor.assumeIsolated { CheckInRouter() } }
}
extension EnvironmentValues {
    var checkInRouter: CheckInRouter {
        get { self[CheckInRouterKey.self] }
        set { self[CheckInRouterKey.self] = newValue }
    }
}
```
**For the scatter:** payload is `var pendingScatter: PendingScatter? = nil` where `struct PendingScatter: Equatable { let x: ExploreSeries; let y: ExploreSeries }` (a tuple is not `Equatable`, so wrap it for `.onChange(of:)` — `ExploreSeries` is `Equatable` by id). Same `assumeIsolated` default; env key `exploreScatterRouter`. **Security invariant (T-03-14, copy the comment posture from CheckInRouter:8-11):** the router carries a non-sensitive intent value, observed ONLY inside the consented shell — never presents health UI itself.

---

### `Sources/EndoApp/Features/Explore/ExploreView.swift` (EDIT — screen)

**Analog:** self. Add an ephemeral `@State mode` enum + X/Y `@State` (mirror the existing `@State private var selection` privacy posture, lines 37-38) and branch the `body`.

**Mode toggle / state branch — extend `body` (60-103).** Pin a segmented `Picker` at the top of the existing `ScrollView { VStack(spacing: 24) }` (the scaffold at 62-63). In `.scatter` mode swap the lanes block (74-86) for the axis picker + plot.

**Two honest scatter states — reuse `honestState(heading:body:identifier:)` verbatim (233-253):**
```swift
// ExploreView.swift:212-218 — the existing empty/sparse call shape to clone:
private var emptyState: some View {
    honestState(heading: ExploreCopy.emptyHeading, body: ExploreCopy.emptyBody, identifier: "explore.empty")
}
```
**Sparse threshold — mirror the pure count check** (`isTooSparse` 197-207; for scatter use `points.reduce(0){ $0 + $1.count } < minScatterDays`, default 5 per A3).

**Suppression source — reuse `suppressed` (56-58):** `snapshots.last?.suppressed ?? false` (drives both the quantitative axis filter and the resolver's `suppressed:`).

**Router read:** add `@Environment(\.exploreScatterRouter)` + `.onChange(of: router.pendingScatter)` that sets `mode = .scatter`, prefills X/Y, then clears the payload.

**LOCKED:** mode + X/Y are ephemeral `@State` ONLY — never a persistence wrapper (the file's grep-gate comment 11-14).

---

### `Sources/EndoApp/Features/Explore/ExploreCopy.swift` (EDIT — audited copy)

**Analog:** self. Add the scatter chrome as `static let`s + interpolated funcs, then extend `auditableStrings` (91-104) in the SAME change.

```swift
// ExploreCopy.swift:70-72 + 99-102 — the interpolated-form + representative-registration posture:
static func eventLaneSummary(name: String, occurrences: Int) -> String {
    "\(name) — logged on \(occurrences) days"
}
// auditableStrings:
strings.append(eventLaneSummary(name: "Series", occurrences: 3))
```
**Add (UI-SPEC Copywriting Contract — pre-verified substring-clean):** `modeLanes`/`modeScatter`, `scatterAxisPlaceholder` ("Pick a factor"), `scatterCaption(days:)` ("Based on {N} days when both were logged."), the per-point readout, `scatterSummary(x:y:days:)`, `scatterEmptyHeading`/`Body`, `scatterSparseHeading`/`Body`, the deep-link label ("See the days behind this"). Register interpolated forms with a representative instance (same posture as `eventLaneSummary`). **Trap note:** "where" was rejected (w-**her**-e); use "when both were logged"; "together" rejected → "on too few of the same days".

---

### `Sources/EndoApp/Features/Explore/SeriesPickerView.swift` (EDIT or sibling axis picker)

**Analog:** self. The segmented `Picker` (78-86) is the mode-toggle template; the value-independent a11y-id-per-option pattern (chip 105-143) is the axis-option template.

```swift
// SeriesPickerView.swift:78-86 — segmented Picker with per-option value-independent a11y id:
Picker("Range", selection: $selection.window) {
    ForEach(TrendWindow.allCases, id: \.self) { window in
        Text(ExploreCopy.windowLabel(window)).tag(window)
            .accessibilityIdentifier("explore.range.\(window.rawValue)")
    }
}
.pickerStyle(.segmented)
.accessibilityIdentifier("explore.range")
```
**Quantitative-only axis options:** `ExploreSelection.offered(suppressed:).filter { $0.scaleKind.isQuantitative }`. Per-option ids `explore.scatter.axisX.<series.id>` / `...axisY.<series.id>`, container ids `explore.scatter.axisX`/`axisY`. Touch floor `minHeight: 44` (chip pattern line 122).

---

### `Sources/EndoApp/Features/Insights/InsightCardView.swift` (EDIT — card)

**Analog:** self. Add the deep-link affordance to the `.correlation` arm of `supportingData` (151-178).

```swift
// InsightCardView.swift:166-176 — the .correlation arm to extend; idRoot at :33:
case .cyclePhase, .correlation, .recommendation, .flare:
    VStack(alignment: .leading, spacing: 4) {
        Text(InsightsCopy.supportingEvidence(loggedDays: insight.sampleSize)) …
    }
    .accessibilityIdentifier("\(idRoot).evidence")
```
**Affordance pattern — mirror the borderless info-button (120-145) and the dismiss `Label` (209-218):**
```swift
// InsightCardView.swift:120-129 — borderless secondary-tint button + a11y id off idRoot:
Button { showsConfidenceInfo = true } label: { Image(systemName: "info.circle").font(.subheadline) }
    .buttonStyle(.borderless).foregroundStyle(.secondary)
    .accessibilityIdentifier("\(idRoot).confidence.info")
```
**For SCAT-04:** compute `ScatterDeepLink.resolve(signalKey: insight.signalKey, suppressed: …)` ONCE; render a `Label("See the days behind this", systemImage: "arrow.up.forward.square")` affordance (a11y id `\(idRoot).scatterLink`, accent tint — a tappable "open") IFF non-nil, that sets `router.pendingScatter`. Suppression flows in like `ExploreView.suppressed` (the card's screen/`InsightsView` must supply it). The card must thread the router action (the existing `onDismiss` closure at line 30 is the precedent for screen-supplied actions — add an analogous `onOpenScatter`).

---

### `Sources/EndoApp/App/AppGate.swift` (EDIT — TabView shell)

**Analog:** self — `ConsentedShell` (50-76). The ONE structural edit: the bare `TabView` (line 52) gains a `selection:` binding.

```swift
// AppGate.swift:52-73 — the bare TabView + id-on-Label pattern to extend:
TabView {
    HomeView().tabItem { Label("Log", systemImage: "square.and.pencil") }
    InsightsView().tabItem { Label("Insights", systemImage: "chart.line.uptrend.xyaxis")
        .accessibilityIdentifier("tab.insights") }
    ExploreView().tabItem { Label("Explore", systemImage: "square.stack.3d.up")
        .accessibilityIdentifier("tab.explore") }
}
```
**Edit:** add `@State private var selectedTab` + `TabView(selection: $selectedTab)` + `.tag(...)` on each tab; inject `ExploreScatterRouter` (mirror how the consented branch already injects env stores). The card affordance sets BOTH `router.pendingScatter` AND the selected tab → Explore. Keep the consent-by-construction posture (the router is observed ONLY in this `.consented` branch — comment at AppGate:33-39).

---

### `Sources/EndoApp/App/AppDependencies.swift` (EDIT — test seed seam)

**Analog:** self. Add a `-uitest-seed-scatter-link` flag (mirror 126-133) + a seed method (mirror `seedRecommendationCorrelation` 491-505).

```swift
// AppDependencies.swift:491-504 — the alternating-pair correlation seed shape to mirror:
private static func seedRecommendationCorrelation(into repo: SwiftDataRepository) {
    let calendar = Calendar.current; let today = calendar.startOfDay(for: Date())
    for offset in 0..<22 {
        guard let day = calendar.date(byAdding: .day, value: -offset, to: today) else { continue }
        if offset % 2 == 0 { try? repo.setFood(tags: [.dairy], freeText: nil, on: day)
                             try? repo.upsertSymptom(type: .pelvicPain, severity: 7, on: day) }
        else { try? repo.setFood(tags: [.caffeine], freeText: nil, on: day)
               try? repo.upsertSymptom(type: .pelvicPain, severity: 2, on: day) }
    }
}
```
**For the scatter deep-link (Pitfall 2 — dairy resolves to `nil`/HIDDEN, so a NEW seed is required):** seed `stress.high → pelvicPain` (both quantitative 0…10) — alternate high-stress/high-severity vs low/low across ~22 days (use the stress-logging repo method, not `setFood`). Flag parse + insertion gated by `-uitest-reset` (the in-memory-store safety pairing, comments at 272-273) so it can never touch a real store.

---

### `Tests/EndoAppUITests/ScatterUITests.swift` (test, E2E)

**Analog:** `Tests/EndoAppUITests/ExploreUITests.swift` — clone `launchAndOpenExplore` (47-75) and `dayArg` (79-86).

```swift
// ExploreUITests.swift:47-74 — the launch+consent+tab harness to clone:
private func launchAndOpenExplore(_ arguments: [String]) -> XCUIApplication {
    let app = XCUIApplication(); app.launchArguments = arguments; app.launch()
    let grantButton = app.buttons["consent.grantButton"]
    XCTAssertTrue(grantButton.waitForExistence(timeout: 10)); grantButton.tap()
    var exploreTab = app.tabBars.buttons["tab.explore"]
    if !exploreTab.waitForExistence(timeout: 10) {
        exploreTab = app.descendants(matching: .any)["tab.explore"].firstMatch }
    exploreTab.tap()
    XCTAssertTrue(app.descendants(matching: .any)["explore.screen"].waitForExistence(timeout: 10))
    return app
}
```
**Tests:** scatter mode renders with two axes; suppressed user has no bleeding axis; count-encoded readout; neutral caption; sparse state; correlation card (stress→pain seed) deep-link prefills scatter; presence factor (dairy seed) card has NO `scatterLink`. Use the ENDSWITH-predicate + `firstMatch` id-bleed workaround (documented 88-90). Run via `-only-testing:EndoAppUITests/ScatterUITests/<method>` (Pitfall 5 flaky-runner).

---

### `Tests/EndoAppTests/ScatterDeepLinkTests.swift` (test, pure unit)

**Analog:** Swift Testing `@Test`/`#expect`; the resolver's pure-value testability mirrors `ExploreSelection.offered`. Cover every factor×target incl. all `nil` cases (presence factors, `sleep.poor`, suppressed `bleeding`). Run via `xcodebuild -only-testing:EndoAppTests/ScatterDeepLinkTests`.

---

### `Tests/EndoAppTests/CopyAuditTests.swift` (EDIT — copy scan)

**Analog:** self — the `allExploreCopy` block (534-601). `ExploreCopy.auditableStrings` is already registered (`private var allExploreCopy: [String] { ExploreCopy.auditableStrings }`, line 545), so the new scatter strings are auto-scanned ONCE added to `auditableStrings` — no new registration line needed, but confirm the new interpolated forms appear in the array (Pitfall 3).

---

## Shared Patterns

### @Observable env-router (cross-tab deep-link)
**Source:** `App/CheckInRouter.swift:16-45`
**Apply to:** `ExploreScatterRouter.swift` (new) + the `AppGate` injection + the card affordance + the `ExploreView` read.
Verbatim structure: `@MainActor @Observable final class` + private `EnvironmentKey` with `MainActor.assumeIsolated` default + `EnvironmentValues` extension. Carries a non-sensitive intent value only; observed solely in the consented branch (T-03-14).

### Audited copy choke-point
**Source:** `Features/Explore/ExploreCopy.swift:70-104` + `Tests/EndoAppTests/CopyAuditTests.swift:534-601`
**Apply to:** every new scatter chrome string. Add to `auditableStrings` in the SAME change; register interpolated forms with a representative instance (the `eventLaneSummary` posture). Pre-verified wording lives in the UI-SPEC Copywriting Contract.

### Honest empty/sparse state
**Source:** `Features/Explore/ExploreView.swift:212-253` (`honestState` + the empty/sparse callers) + the pure count check (`isTooSparse` 197-207)
**Apply to:** the scatter empty ("pick two factors") + sparse ("not enough overlapping days") states. Reuse `honestState(heading:body:identifier:)` verbatim; threshold is a pure `[ScatterPoint]` count-sum.

### Value-independent a11y ids hanging off a stable root
**Source:** `InsightCardView.swift:33` (`idRoot`) + `SeriesPickerView.swift:117,136` (per-option `explore.chip.<series.id>`)
**Apply to:** `explore.mode.*`, `explore.scatter.axisX/Y[.<series.id>]`, `explore.scatter.plot/caption/summary/empty/sparse`, `insights.card.<signalKey>.scatterLink`. Stable across state — the E2E contract.

### Deterministic in-memory test seed
**Source:** `AppDependencies.swift:126-133` (flag) + `:491-505` (`seedRecommendationCorrelation`) + `:142-153` (defensive parse) + `:272-273` (`-uitest-reset` pairing safety)
**Apply to:** `-uitest-seed-scatter-link` (stress→pain, quantitative both sides). Always paired with `-uitest-reset` so it touches only the in-memory store.

### Reduce Motion + fixed-domain chart axes
**Source:** `InsightTrendChart.swift:216-233`
**Apply to:** `ScatterPlotView` — `.chartXScale`/`.chartYScale(domain:)` from `SeriesNormalization.domain` (never data-driven), `.chartXAxis`/`.chartYAxis { AxisMarks() }`, `.animation(reduceMotion ? nil : .default, value: points)`.

## No Analog Found

None. Every Phase-16 file maps to an exact-or-strong analog (this is a compose-shipped-primitives phase; the only genuinely new logic is the `ScatterDeepLink` string→catalog resolver, which mirrors the pure `ExploreSelection.offered` value-mapper posture).

## Metadata

**Analog search scope:** `Sources/EndoApp/Features/Explore`, `Sources/EndoApp/Features/Insights`, `Sources/EndoApp/App`, `Tests/EndoAppUITests`, `Tests/EndoAppTests`, `EndoInsights/Sources/EndoInsights`.
**Files scanned:** 13 (read in full or targeted ranges): CheckInRouter, AppGate, ExploreView, ExploreCopy, SeriesPickerView, InsightCardView, InsightTrendChart, ExploreUITests, AppDependencies, plus grep-grounded CorrelationEngine / Factor / ScatterPairing / ExploreSelection / CopyAuditTests.
**Pattern extraction date:** 2026-06-16
