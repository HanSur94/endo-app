# Stack Research — v1.1 Interactive Charts & Explore

**Domain:** iOS chart interactivity (scrub/tooltip), multi-series overlay plots with mixed scales, scatter plots — additions to an existing Swift Charts surface
**Researched:** 2026-06-10
**Confidence:** HIGH (every load-bearing API verified against Apple's official documentation availability metadata today; community idioms cross-checked are marked MEDIUM)

> **Milestone context:** v1.0 stack (SwiftUI + SwiftData/CloudKit + Swift Charts + EndoInsights SPM, iOS 18 min, Swift 6.0, Xcode 26.5) is validated and out of scope here. This file covers ONLY what the v1.1 chart-interactivity/Explore features need. The previous full-project STACK.md (2026-06-03) is superseded by this milestone-scoped version.

---

## TL;DR Verdict

**Plain Swift Charts on iOS 18 is sufficient. Zero new dependencies, zero SPM additions, zero schema changes implied by the stack.** Every needed capability ships in the OS at or below the project's iOS 18 floor:

1. **Scrub/tap-to-read (lollipop):** `chartXSelection(value:)` (iOS 17+) is the modern idiom — the framework owns gesture recognition and writes the raw X value into a binding; you snap to the nearest *logged* day and draw a `RuleMark` + `.annotation(overflowResolution:)` tooltip.
2. **Multi-series overlay:** plain mark composition + `chartForegroundStyleScale` (iOS 16+) for explicit series→color mapping and an automatic legend.
3. **Mixed scales:** Swift Charts has **no secondary Y axis** — one Y scale per `Chart`. This is a confirmed framework limitation, not a missing library. Handle it with normalization or aligned stacked panels (architecture decision, not a stack gap — see "Stack Patterns by Variant").
4. **Scatter:** `PointMark` (already in the codebase) with both axes value-typed; the iOS 18 vectorized `PointPlot` is available headroom if point counts ever grow large.

No capability gap exists that a third-party charting library would close. Do not add one.

---

## Recommended Stack

### Core Technologies (all already present — no installs)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **Swift Charts** | iOS 18 SDK (Xcode 26.5) | All three features: interactivity, overlay, scatter | Already the project's charting layer; every needed API verified ≤ iOS 18 (see table below). First-party = no dependency risk, composes with the existing `InsightTrendChart`, keeps the PDF vector-render path working. **Confidence: HIGH** |
| **SwiftUI** | iOS 18+ | Selection state (`@State` binding), Explore screen, series pickers | Selection is a plain optional `@State` bound through `chartXSelection` — no new state machinery, Swift 6 strict-concurrency clean (main-actor view state). **Confidence: HIGH** |
| **EndoInsights (existing SPM package)** | local | Pure helpers for the new math: nearest-logged-day snapping, series normalization, scatter day-pairing | Same pattern as `ChartSeriesGrouping`: keep the snap/normalize/pair logic Foundation-only and unit-testable, feed views plain values. No package changes needed beyond new pure functions. **Confidence: HIGH** |

### The exact APIs (verified against Apple docs availability metadata, 2026-06-10)

| API | iOS | Verified signature / note | Use for |
|-----|-----|---------------------------|---------|
| `chartXSelection(value:)` | **17.0** | `func chartXSelection<P>(value: Binding<P?>) -> some View where P: Plottable` | THE selection idiom. Bind `Date?`; framework handles tap + drag-to-scrub natively. |
| `chartXSelection(range:)` | 17.0 | `Binding<ClosedRange<P>?>` | Optional: two-finger/drag range selection. Not needed for v1.1 core; know it exists. |
| `chartGesture(_:)` | 17.0 | `(ChartProxy) -> some Gesture` | Escape hatch for custom gestures via `proxy.selectXValue(at:)`. Only if `chartXSelection`'s default behavior proves insufficient — start without it. |
| `ChartProxy` | 16.0 | `value(atX:as:)`, `position(forX:)`, `selectXValue(at:)`, `plotFrame` (17.0, `Anchor<CGRect>?`) | Coordinate↔value conversion if drawing custom tooltip chrome in `chartOverlay`. Prefer `plotFrame` over the iOS 16-era `plotAreaFrame`. |
| `chartOverlay(content:)` | 16.0 | `(ChartProxy) -> some View` | Legacy iOS 16 selection pattern (manual `DragGesture`). **Do not use for selection** — `chartXSelection` replaces it. Still fine for purely visual overlays. |
| `.annotation(position:alignment:spacing:overflowResolution:content:)` | **17.0** (`AnnotationOverflowResolution` verified iOS 17.0) | Overflow resolution e.g. `.fit(to: .chart)` | The lollipop tooltip card on the selection `RuleMark`/`PointMark` — `overflowResolution` keeps it inside the plot at the window edges instead of clipping. |
| `RuleMark` | 16.0 | already known | The vertical scan line; set `.zIndex(-1)` so it draws behind the data line. |
| `PointMark` | 16.0 | already in `InsightTrendChart` | Scatter view: `PointMark(x: .value("Factor A", a), y: .value("Factor B", b))`, one mark per logged day-pair. |
| `chartForegroundStyleScale(_:)` | 16.0 | `KeyValuePairs<DataValue, S>` mapping | Explore overlay: explicit, stable series→color mapping + free legend. Deterministic colors matter for cross-session recognizability. |
| `chartScrollableAxes(_:)` + `chartXVisibleDomain` / `chartScrollPosition` | 17.0 | `Axis.Set` | Optional: pan a long all-time Explore axis instead of cramming it. Nice-to-have, not required. |
| `PointPlot` / `LinePlot` (vectorized plots) | **18.0** | `struct PointPlot<Content>`; takes a whole collection + KeyPaths | Performance headroom for scatter if points ever exceed ~1k. Daily logging ≈ 365 points/year — `PointMark` + `ForEach` is fine at launch; `PointPlot` is available at the project floor if profiling says otherwise. |

All iOS 17 APIs are unconditionally usable: project minimum is iOS 18.

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| *(none new)* | — | — | The entire feature set is OS-provided. Adding a charting dependency would break the v1.0 "near-zero dependencies" privacy/maintenance posture for zero capability gain. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Xcode 26.5 (existing) | Builds against current SDK | No toolchain change. All listed APIs are years-stable (WWDC22–24 vintage). |
| Swift Testing (existing) | Unit-test the new pure helpers | Nearest-logged-day snap, normalization mapping, scatter pairing — all Foundation-only, same harness as `ChartSeriesGrouping` tests. |
| iOS Simulator | Scrub-gesture verification | Drag-scrub works in Simulator; only haptics (if added via `sensoryFeedback`) need a device. |

## Installation

```bash
# Nothing to install. No SPM dependencies added, no project.yml changes
# beyond new source files. All APIs ship in the iOS 18 SDK already targeted.
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| `chartXSelection(value:)` | `chartOverlay` + manual `DragGesture` + `ChartProxy.value(atX:)` (iOS 16 idiom) | Only if you needed iOS 16 support (you don't) or exotic gesture arbitration. The manual pattern is more code, fights scroll gestures, and duplicates what the framework now owns. |
| `chartXSelection(value:)` | `chartGesture(_:)` + `proxy.selectXValue(at:)` | If the default tap/drag recognition conflicts with an enclosing ScrollView in practice. Keep as fallback, don't start here. |
| Aligned stacked panels or labeled normalization for mixed scales | True dual Y axis | Never available in Swift Charts — confirmed limitation (Apple Dev Forums thread 742659; `AxisMarks(position: .trailing)` only repositions marks of the *same single* Y scale). |
| `PointMark` scatter | `PointPlot` (iOS 18 vectorized) | Switch only if a profiled performance problem appears at large point counts; `PointPlot`'s KeyPath styling is less ergonomic for per-point accessibility labels. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **DGCharts** (ex-ios-charts) | UIKit-era, maintenance-mode, huge dependency; duplicates everything Swift Charts now does; breaks the existing vector-PDF chart reuse | Swift Charts |
| **SciChart / commercial chart SDKs** | Paid license for a free app; only "advantage" is true multi-axis, which the honesty model argues against anyway | Stacked panels / normalization |
| **`chartOverlay` + DragGesture for selection** | Pre-iOS-17 workaround; more code, worse gesture coexistence | `chartXSelection(value:)` |
| **Interpolating tooltip values between logged days** | `chartXSelection` returns a *raw* X (any date in the domain). Reading a value at an unlogged date would fabricate data — the exact Pitfall-1 class the gap-break design exists to prevent | Snap to nearest **logged** day within a tap-tolerance; outside tolerance/inside a gap → "no entry" state or no lollipop |
| **A second fake Y axis via overlaid `AxisMarks` labels with hidden rescaling** | Silently plotting two scales on one axis misleads — violates the honest-axis invariant | Explicit per-series normalization labeled as such, or per-scale stacked panels with synchronized X selection |

## Stack Patterns by Variant

**Trend-chart interactivity (existing `InsightTrendChart`):**
- Add `@State private var rawSelectedDate: Date?` + `.chartXSelection(value: $rawSelectedDate)`; derive `selectedPoint` by snapping to the nearest logged `InsightPoint` (pure helper in EndoInsights, unit-tested like `ChartSeriesGrouping`).
- Selection chrome = `RuleMark(x:)` + highlighted `PointMark` + `.annotation(position: .top, overflowResolution: .init(x: .fit(to: .chart), y: .disabled))`.
- Keep the PDF path untouched: the PDF renderer constructs a fresh chart — simply never attach the selection modifier there (selection state lives in the live view, additive exactly like the `inkColor` pattern).
- Keep `.animation(..., value: points)` scoped to `points` so selection-state changes don't re-animate the series; respect the existing Reduce Motion gate for any selection transition.
- Accessibility: selection is a *pointer-user affordance*; per-mark `accessibilityLabel/Value` + Audio Graph already carry the values for VoiceOver — don't remove them, and mirror the selected value into visible text so the lollipop isn't gesture-only information.

**Explore overlay (mixed scales — the one real design problem):**
- Because one `Chart` = one Y scale: either (a) **normalize** each series to a shared 0–1/0–10 band with an explicit "normalized for comparison — scales differ" label and per-series true-value readout in the selection tooltip, or (b) **stacked small-multiple panels** sharing the X domain, one panel per scale family (0–10 severity, hours, minutes, 1–5, binary lane), with one `chartXSelection` binding shared across panels so a single scrub reads all series. (b) is the honesty-preserving default; (a) is acceptable if labeled. Roadmap/UX decision — stack supports both with zero additions.
- Binary food tags: render as a categorical occurrence lane (`PointMark`/`RectangleMark` at a fixed band), never as a 0/1 line — a connecting line between binary days fabricates intermediate states.
- Gap honesty carries over per series: reuse the `ChartSeriesGrouping` run-splitting per selected series.

**Scatter view:**
- Pure pairing helper: for factors A and B, emit one `(a, b, date)` per day where **both** are logged (days with only one logged are absent, not zero).
- `PointMark` + per-point `accessibilityLabel` (date) / `accessibilityValue` (both values); descriptive copy only — ClaimsPolicy scan applies to all new chrome strings (no causal framing).

## Version Compatibility

| Capability | Needs | Project floor | Status |
|------------|-------|---------------|--------|
| `chartXSelection(value:/range:)`, `chartGesture`, `plotFrame`, annotation `overflowResolution`, scrollable axes | iOS 17.0 | iOS 18.0 | ✅ unconditional |
| `chartForegroundStyleScale`, `ChartProxy`, `RuleMark`, `PointMark`, `chartOverlay` | iOS 16.0 | iOS 18.0 | ✅ unconditional |
| `PointPlot` / `LinePlot` vectorized | iOS 18.0 | iOS 18.0 | ✅ available (optional perf path) |
| True secondary Y axis | — | — | ❌ does not exist in Swift Charts (any version incl. iOS 26 SDK); design around it |

Swift 6.0 strict concurrency: no impact — selection bindings are main-actor SwiftUI state; the new pure helpers stay in the Foundation-only package.

## Sources

- Apple Developer Documentation (availability metadata fetched via docs JSON endpoints, 2026-06-10 — **HIGH**): [`chartXSelection(value:)`](https://developer.apple.com/documentation/swiftui/view/chartxselection(value:)) (iOS 17.0, signature verified), [`chartXSelection(range:)`](https://developer.apple.com/documentation/swiftui/view/chartxselection(range:)) (17.0), [`chartGesture(_:)`](https://developer.apple.com/documentation/swiftui/view/chartgesture(_:)) (17.0), [`chartForegroundStyleScale(_:)`](https://developer.apple.com/documentation/swiftui/view/chartforegroundstylescale(_:)) (16.0), [`chartScrollableAxes(_:)`](https://developer.apple.com/documentation/swiftui/view/chartscrollableaxes(_:)) (17.0), [`ChartProxy`](https://developer.apple.com/documentation/charts/chartproxy) (16.0; `plotFrame` 17.0, `plotAreaFrame` 16.0 legacy), [`PointPlot`](https://developer.apple.com/documentation/charts/pointplot) (18.0), [`AnnotationOverflowResolution`](https://developer.apple.com/documentation/charts/annotationoverflowresolution) (17.0)
- [WWDC23 — Explore pie charts and interactivity in Swift Charts](https://developer.apple.com/videos/play/wwdc2023/10037/) — selection-idiom origin (**HIGH**)
- [WWDC24 — Swift Charts: Vectorized and function plots](https://developer.apple.com/videos/play/wwdc2024/10155/) — `PointPlot`/`LinePlot` rationale (**HIGH**)
- [Swift with Majid — Mastering charts in SwiftUI: Selection](https://swiftwithmajid.com/2023/07/18/mastering-charts-in-swiftui-selection/) — lollipop + snap-to-datapoint idiom (**MEDIUM**, cross-checked against Apple docs)
- [Apple Developer Forums thread 742659 — secondary Y axis](https://developer.apple.com/forums/thread/742659) — confirms no dual-axis support (**MEDIUM**, consistent with absence of any such API in official docs)

---
*Stack research for: v1.1 Interactive Charts & Explore (Endo-App)*
*Researched: 2026-06-10*
