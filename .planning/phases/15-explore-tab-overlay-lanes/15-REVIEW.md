---
phase: 15-explore-tab-overlay-lanes
reviewed: 2026-06-15T00:00:00Z
depth: standard
files_reviewed: 12
files_reviewed_list:
  - Sources/EndoApp/Features/Explore/ExploreSelection.swift
  - Sources/EndoApp/Features/Explore/ExploreCopy.swift
  - Sources/EndoApp/Features/Explore/ExploreLaneChart.swift
  - Sources/EndoApp/Features/Explore/EventLaneChart.swift
  - Sources/EndoApp/Features/Explore/ExploreLanesView.swift
  - Sources/EndoApp/Features/Explore/SeriesPickerView.swift
  - Sources/EndoApp/Features/Explore/ExploreView.swift
  - Sources/EndoApp/App/AppGate.swift
  - Sources/EndoApp/App/AppDependencies.swift
  - Tests/EndoAppTests/CopyAuditTests.swift
  - Tests/EndoAppTests/ExploreSelectionTests.swift
  - Tests/EndoAppUITests/ExploreUITests.swift
findings:
  critical: 0
  warning: 3
  info: 4
  total: 7
status: findings_found
---

# Phase 15: Code Review Report

**Reviewed:** 2026-06-15T00:00:00Z
**Depth:** standard
**Files Reviewed:** 12
**Status:** findings_found

## Narrative Findings (AI reviewer)

### Summary

Reviewed the Phase 15 Explore-tab / overlay-lanes work: the pure `ExploreSelection`
cap/toggle/suppression value type, the audited `ExploreCopy` surface, the two lane primitives
(`ExploreLaneChart`, `EventLaneChart`), the lane-stack + multi-series tooltip + a11y proxy
(`ExploreLanesView`), the chip picker (`SeriesPickerView`), the screen (`ExploreView`), the tab
wiring (`AppGate`), the test seam (`AppDependencies`), and the three test files. Cross-referenced the
Phase-14 honesty helpers (`ExploreSeries`, `ChartScrubSelection`, `SeriesNormalization`,
`ExploreSeriesExtraction`) and the repository (`setSuppressed`, `daySnapshots`).

The honesty invariants are largely well-constructed and structurally enforced: gaps break the line
per (series × run); the resolver is exact-day-only with no interpolation; event lanes return `[Date]`
so 0/1 coercion is unrepresentable; the multi-series tooltip resolves event series via `isPresent(in:)`
keyed by day (NOT always "Not logged"); the suppression filter removes both bleeding chips; the copy
surface is registered in `CopyAuditTests`; the test seam is DEBUG + `-uitest-reset`-paired and inert in
production; selection state is ephemeral `@State` with no persistence wrapper. No Critical defects found.

Three Warnings surface real behavior gaps: a stale-suppression edge case where a selected bleeding
series renders an empty titled lane; a sparse-threshold that under-counts a logged-zero-only selection;
and an event lane missing the Audio Graph / text-summary parity the quantitative lane carries.

## Warnings

### WR-01: A selected bleeding series renders an empty, titled lane after suppression flips

**File:** `Sources/EndoApp/Features/Explore/ExploreView.swift:116-145`
**Issue:** `recompute()` splits `selection.series` by scale kind and builds a lane for EVERY selected
series, with no filter on the current `suppressed` flag. The suppression filter
(`ExploreSelection.offered`) only governs which chips the PICKER offers — it does not prune the
SELECTION set. If a user selects `bleeding.intensity` while not suppressed, then suppression flips on
(e.g. seeded via the suppressed variant, or set in Settings during the same session before the
ephemeral selection clears), the Phase-14 accessor gates `quantitativeValue` on `!snap.suppressed` and
returns `nil`, so `points == []` — but the lane is still constructed. `ExploreLanesView` then renders
the lane TITLE ("Bleeding intensity — …") plus an empty `ExploreLaneChart` (empty axes) whenever it is
accompanied by other logged series that keep the selection out of the sparse state
(`isTooSparse` only trips when the TOTAL logged count across all lanes is < 2). The result is an empty
labelled bleeding lane for a suppressed user — a privacy/honesty regression the accessor gate was meant
to prevent end-to-end. (When bleeding is the ONLY selection the sparse state correctly hides it, so the
defect is masked in the single-series E2E.)
**Fix:** Prune suppressed bleeding from the selection at the lane-build boundary, mirroring the picker
filter, so no empty bleeding lane can render:
```swift
private func recompute() {
    let offeredIDs = Set(ExploreSelection.offered(suppressed: suppressed).map(\.id))
    let selectedOrdered = ExploreSeriesCatalog.all.filter {
        selection.series.contains($0) && offeredIDs.contains($0.id)
    }
    // …unchanged…
}
```
Equivalently, drop any selected series whose extraction yields zero points AND zero days from lane
construction (a general "no empty lane" guard).

### WR-02: Sparse threshold can hide a legitimately-plottable logged-zero-only selection

**File:** `Sources/EndoApp/Features/Explore/ExploreView.swift:176-187`
**Issue:** `isTooSparse` counts logged points/days inside the domain and trips when the total is `< 2`.
The count is purely cardinality — it does not distinguish a series whose two-or-more logged points are
all literal zeros from a gap. That is correct for the honesty goal (a literal logged 0 IS a real point
and SHOULD count, which it does). However, the threshold is a magic `2` applied to the SUM across ALL
selected series, so a selection of two distinct series each with exactly one logged day (e.g. one
quant point + one event day, two genuinely-loggable signals on different days) reads as plottable
(sum == 2), while a single series with one logged point reads as sparse. The "a single isolated point
can't read as a trend" rationale in the doc comment is per-series, but the implementation is a global
sum — so the sparse decision is inconsistent across selection shapes (one series with 1 point = sparse;
two series with 1 point each = lanes, each showing a single isolated point). This is a logic/threshold
mismatch between the documented intent (per-series density) and the code (global count).
**Fix:** Decide sparseness per-series and render lanes only when at least one selected series clears the
per-series density floor, or document the global-sum semantics explicitly. For per-series:
```swift
private var isTooSparse: Bool {
    let domain = prepared.xDomain
    let maxPerSeries = max(
        prepared.quantitativeLanes.map { $0.points.filter { domain.contains($0.date) }.count }.max() ?? 0,
        prepared.eventLanes.map { $0.days.filter { domain.contains($0) }.count }.max() ?? 0
    )
    return maxPerSeries < 2
}
```

### WR-03: Event lane omits the Audio Graph descriptor + text-summary parity the quant lane carries

**File:** `Sources/EndoApp/Features/Explore/EventLaneChart.swift:86-89`
**Issue:** `ExploreLaneChart` provides both a one-line text summary (`.accessibilityLabel(summaryText)`)
AND an `AXChartDescriptor` (`.accessibilityChartDescriptor(self)`) so VoiceOver and the Audio Graph can
read the lane without scrubbing — satisfying "charts not the sole carrier of meaning." `EventLaneChart`
carries only `.accessibilityLabel(series.displayName)` (a bare name, no occurrence count / summary) and
NO chart descriptor. A VoiceOver user lands on the event lane container and hears only its name with no
non-gesture path to "how many occurrences / which days" — the event marks' per-tick labels are reachable
only via the chart-content traversal, and there is no Audio Graph fallback. This is an a11y-parity gap
versus the quantitative lane (and the multi-series tooltip proxy covers only the scrubbed day, not the
lane as a whole).
**Fix:** Give the event lane a text summary that names the occurrence count (audited via `ExploreCopy`
if it becomes user-facing copy), e.g. `.accessibilityLabel(Text("\(series.displayName), \(days.count)
logged"))` — or, for full parity, add an `AXChartDescriptor` with a categorical day axis over `days`.
Keep "Logged"/occurrence wording routed through `ExploreCopy` so it stays inside the audit surface.

## Info

### IN-01: `var rows` should be `let` in FlowLayout.sizeThatFits

**File:** `Sources/EndoApp/Features/Explore/SeriesPickerView.swift:156`
**Issue:** `var rows = rowsLayout(...)` is never mutated in `sizeThatFits` — it is only read
(`rows.totalHeight`, `rows.maxRowWidth`). Declaring it `var` will draw a compiler "never mutated;
consider `let`" warning and is misleading.
**Fix:** `let rows = rowsLayout(maxWidth: maxWidth, subviews: subviews)`.

### IN-02: FlowLayout recomputes the row layout on every sizing/placement pass without using the cache

**File:** `Sources/EndoApp/Features/Explore/SeriesPickerView.swift:154-179`
**Issue:** The `Layout` protocol's `cache` parameter is `()` (unused), so `rowsLayout` (which calls
`sizeThatFits(.unspecified)` on every subview) runs in full on both `sizeThatFits` and `placeSubviews`,
and again on each re-measure. This is correctness-neutral (out of v1 perf scope) but is a missed use of
the provided cache seam and worth noting for the chip cluster as it grows.
**Fix:** Optionally populate `cache` with the computed `Rows` keyed on the proposal; not required for v1.

### IN-03: Tooltip/proxy re-resolve every series per render instead of reading a prepared map

**File:** `Sources/EndoApp/Features/Explore/ExploreLanesView.swift:169-218`
**Issue:** `quantitativeLine`, `eventLine`, and the proxy each call `ChartScrubSelection.resolve` /
`isPresent(in:)` per render while a scrub is active. The expensive extraction is correctly cached in
`ExploreView.prepared` (Pitfall 4 satisfied), and resolution is O(points) exact-day lookup, so this is
not a perf defect — but the same line is computed twice (once for the visible card, once for the proxy
value) per series per frame. Minor duplication.
**Fix:** Compute each series' line once into a local `[seriesID: String]` for the scrubbed day and feed
both the card and the proxy from it.

### IN-04: `xDomain` fallbacks silently collapse to `end…end` on calendar failure

**File:** `Sources/EndoApp/Features/Explore/ExploreView.swift:160-169`
**Issue:** Each `calendar.date(byAdding:…) ?? end` falls back to `end` on the (practically impossible)
nil, yielding a degenerate `end…end` single-day domain rather than a sensible window. This never fires
with `.current` Gregorian arithmetic, so it is defensive-only, but a degenerate domain would render all
lanes as a single-day strip with no signal. Acceptable as written; flagged for awareness.
**Fix:** None required; if hardened, fall back to a fixed `-30` day offset (as the `.allTime` branch
already does) rather than `end`.

---

_Reviewed: 2026-06-15T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
