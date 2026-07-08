# Project Research Summary

**Project:** Endo-App — milestone v1.1 "Interactive Charts & Explore"
**Domain:** iOS Swift Charts interactivity (scrub/tooltip), multi-series overlay, scatter view in an invariant-locked health tracker
**Researched:** 2026-06-10
**Confidence:** HIGH

## Executive Summary

v1.1 adds three chart surfaces to a feature-complete, honesty-invariant-locked v1.0 app: scrub/tap-to-read on the dashboard trend charts, a new Explore tab overlaying multiple logged series, and a factor-vs-factor scatter view. The unanimous research conclusion is that **plain Swift Charts on the existing iOS 18 stack covers everything** — `chartXSelection(value:)` (iOS 17) for selection, `chartForegroundStyleScale` + mark composition for the overlay, `PointMark` for scatter. Zero new dependencies, zero schema changes, zero engine changes. Explore reads `Repository.daySnapshots()` directly (the existing single projection already carries every needed series with the manual-wins HealthKit merge and nil-honest semantics) and deliberately bypasses the InsightsEngine/HonestyGate, which gate claims, not raw data.

The recommended approach follows the codebase's strongest precedent: all honesty-load-bearing logic lives in pure, Foundation-only, unit-tested helpers in EndoInsights (`ChartScrubSelection`, `ExploreSeries` catalog, `ExploreSeriesExtraction`, `ScatterPairing`, `SeriesNormalization`), emitting the existing `InsightPoint` so the shipped gap-breaking `ChartSeriesGrouping` is reused unchanged. SwiftUI views stay thin compositions. Scrubbing is added to `InsightTrendChart` additively (the `inkColor` precedent) so the PDF report path stays byte-identical. Competitively, scrub-to-read is iOS table stakes (Apple Health trained everyone); a user-driven Explore overlay and a raw, stats-free scatter are genuine differentiators no mainstream cycle app offers.

The key risk class is **invariant erosion through the new surfaces while all existing tests stay green**: phantom tooltip values on unlogged days (the selection API is position-based and tutorials interpolate), Explore lines connecting across gaps if `ChartSeriesGrouping` isn't re-keyed per (series × run), and new copy shipping outside the ClaimsPolicy audit. Mitigation: treat guard-surface extension (pure selection mapper, per-series gap runs, `ExploreCopy` registration in `CopyAuditTests`) as first-class phase requirements. The one real design problem is mixed Y scales — Swift Charts has no dual Y axis (confirmed framework limitation) — resolved via aligned per-scale lanes/small multiples sharing one X axis and one scrub state, with binary tags as occurrence-tick lanes, never 0/1 lines.

## Key Findings

### Recommended Stack

Everything ships in the OS at or below the project's iOS 18 floor; no installs, no SPM additions, no project.yml changes beyond new source files. Adding a third-party chart library (DGCharts, commercial SDKs) would break the near-zero-dependency privacy posture for zero capability gain.

**Core technologies:**
- Swift Charts (iOS 18 SDK): `chartXSelection(value:)` (iOS 17) for selection; `RuleMark` + `.annotation(overflowResolution: .fit(to: .chart))` for the lollipop; `chartForegroundStyleScale` for stable series colors + legend; `PointMark` for scatter (`PointPlot` is iOS 18 perf headroom if ever needed)
- SwiftUI: selection as plain optional `@State` bound through `chartXSelection`; Swift 6 strict-concurrency clean
- EndoInsights (existing local SPM): new pure helpers for snap/normalize/pair logic, unit-tested with Swift Testing like `ChartSeriesGrouping`

**What NOT to use:** `chartOverlay` + manual `DragGesture` for selection (pre-iOS-17 idiom), interpolated tooltip values, fake dual Y axes, any third-party chart library.

### Expected Features

**Must have (table stakes):**
- Touch-and-hold/drag scrub with lollipop tooltip matching Apple Health's gesture vocabulary, with selection haptic and clean dismissal
- Gap-honest tooltip: "not logged" on unlogged days, never 0 and never interpolation — the one place generic tutorials mislead
- Series picker as toggle chips, distinct stable colors + legend, shared time axis reusing v1.0 range presets, honest empty/sparse states
- Scrub works on the Explore overlay too (multi-series readout)
- Accessibility parity: text summaries, Audio Graphs, a non-gesture path to "exact day + value"

**Should have (competitive differentiators):**
- Honest mixed-scale overlay via aligned per-scale lanes (no dual-axis spaghetti, no silent normalization) — the core design decision of Explore
- Binary tags as event-marker lanes (Bearable's day-highlight pattern validates this)
- Raw scatter, one point per both-logged day, overplotting handled by count-encoded mark size (never random jitter — it fabricates values)
- Descriptive-only chrome by design: no r-values, no trend lines, no "% impact" (Bearable's "% effect" is exactly what the honesty policy forbids)
- Portrait-first Explore (Bearable forces landscape — known friction)

**Defer (v1.1.x / v2+):**
- Tap-scatter-point → day log; correlation card → prefilled scatter deep-link (both cheap post-scatter glue)
- Saved Explore configs; landscape layout; lag-shifted comparison; pinch-zoom/pan; Explore charts in the PDF report

### Architecture Approach

Explore is a third tab built only inside `ConsentedShell` (consent-by-construction preserved, a11y id on the tab Label). It reads `daySnapshots()` directly — one projection, many pure mappers; no new persistence seam, no second snapshot type, no coordinator/cache. The new logic is three-to-five pure Foundation helpers plus four SwiftUI views. The extraction mapper emits the existing `InsightPoint` so gap-breaking is reused verbatim. Series-selection state is view-local `@State` modeled as a pure value type (cap rules unit-testable); deliberately ephemeral in v1.1 (persisting series keys via `@AppStorage` would leak health-interest metadata outside the consent-gated store).

**Major components:**
1. Pure layer (new, EndoInsights-style): `ChartScrubSelection` (nearest-logged-day resolver), `ExploreSeries` catalog (name/unit/scale/kind metadata per DaySnapshot field), `ExploreSeriesExtraction`, `ScatterPairing` (both-logged days only), `SeriesNormalization` (round-tripping, real-unit readouts)
2. SwiftUI (new): `ExploreView`, `SeriesPickerView`, `ExploreOverlayChart`, `ScatterChartView`
3. Modified: `InsightTrendChart` (additive defaulted `allowsSelection` param; PDF passes `false`), `AppGate.ConsentedShell` (+1 tab), `CopyAuditTests` (+`ExploreCopy`), XCUITest harness (new seed variant + selection seam)
4. Untouched: `InsightsEngine`, `HonestyGate`, `InsightsCoordinator`, SchemaV1, CloudKit, PDF report content

### Critical Pitfalls

1. **Phantom tooltip values on unlogged days [INVARIANT-BREAKING]** — resolve selection through a pure mapper requiring exact logged-day match; gap → no lollipop/"no entry"; unit-test with gap fixtures (gap → nil, logged severity-0 → 0)
2. **Explore lines connecting across gaps [INVARIANT-BREAKING]** — re-key `series:` per (data-series × contiguous-run) via `ChartSeriesGrouping`, with `.foregroundStyle(by:)` carrying legend/color independently
3. **New copy outside the ClaimsPolicy audit [INVARIANT-BREAKING]** — create `ExploreCopy` with `auditableStrings` and register in `CopyAuditTests` before any copy is written; curate against substring collisions ("together", "whether", "higher", "other", "treatment")
4. **Silent normalization / fake dual axis** — prefer aligned small multiples with fixed honest per-unit domains; never per-window min-max, never a % axis, never two `Chart`s in a `ZStack`
5. **Scatter stats drift + overplotting** — no fitted line/r/% by acceptance criterion; aggregate identical discrete pairs with count-encoded mark size, never jitter
6. **Scrub vs List scroll conflict + PDF breakage** — tap-to-select on the in-List dashboard (free scrub only on Explore's fixed-chart layout); `ReportPDFRenderer` untouched in the diff, report visually re-verified; gesture E2Es replaced with a DEBUG-gated `-uitest-select-day` seam + a11y-id assertions

## Implications for Roadmap

The four research files independently converge on the same 4-step build order (ARCHITECTURE Q6 ≡ PITFALLS phase mapping ≡ FEATURES dependency graph).

### Phase 1: Trend-chart scrubbing
**Rationale:** Smallest slice, zero new data flow (reads the already-plotted `insight.points`); builds the reusable honesty resolver, selection a11y pattern, and E2E selection seam that Explore and scatter both reuse.
**Delivers:** `ChartScrubSelection` (pure + tests), additive scrub layer on `InsightTrendChart` (`allowsSelection`, PDF passes false), lollipop with gap-honest "no entry", haptic, copy through audited choke point, `-uitest-select-day` seam, dashboard E2E regression green.
**Addresses:** Table-stakes scrub/tooltip (FEATURES P1).
**Avoids:** Pitfalls 1, 7 (dashboard tap-to-select model), 8 (PDF), 9 (deterministic E2E).

### Phase 2: Series catalog + pure extraction layer
**Rationale:** The keystone dependency both Explore and scatter consume; proves DaySnapshot field coverage in code before any pixel exists. No UI — fully unit-testable.
**Delivers:** `ExploreSeries` catalog (kind: ordinal/continuous/presence), `ExploreSeriesExtraction` emitting `[InsightPoint]`, `ScatterPairing`, `SeriesNormalization` — all pure, all honesty properties under test (nil → no point; literal 0 → point; presence → occurrence days).
**Uses:** EndoInsights package, Swift Testing, existing `InsightPoint`/`TrendWindow`.
**Avoids:** Pitfall 6 root cause (series-kind model prevents 0/1 coercion).

### Phase 3: Explore tab + overlay chart
**Rationale:** The big visible slice, but by now pure composition — gap-breaking, scrub, extraction, normalization are pre-tested. Contains the milestone's central design decision (lanes vs labeled normalized panel).
**Delivers:** `ExploreView` + `SeriesPickerView` (cap 3–4, chips) + `ExploreOverlayChart` (per-scale lanes, event lanes for binary tags, multi-series scrub readout with real units), third tab in `ConsentedShell`, `ExploreCopy` registered in `CopyAuditTests`, seeded E2E, grayscale/sparse-fixture screenshot checks.
**Implements:** The honest mixed-scale overlay differentiator.
**Avoids:** Pitfalls 2, 3, 4, 6, 10.

### Phase 4: Scatter view
**Rationale:** Pure reuse of catalog + pairing on the existing screen; its copy needs the most careful descriptive framing, so it benefits from the copy infrastructure landing first.
**Delivers:** `ScatterChartView` with count-encoded overlapping pairs, "Based on N days where both were logged" caption, mode toggle, empty-state gating, no-stats acceptance criteria, E2E.
**Avoids:** Pitfall 5.
**Sets up (v1.1.x, optional tail):** tap-point → day log; correlation-card → prefilled scatter deep-link.

### Phase Ordering Rationale

- Scrub first: the selection mapper, a11y-of-selection pattern, and test seam are foundational and reused by both later surfaces; it also de-risks the gesture-vs-scroll question early.
- Pure layer before any Explore UI: the load-bearing honesty guarantees become unit tests before rendering exists — the v1.0 `ChartSeriesGrouping` playbook.
- Scatter last: depends only on Phase 2 + Phase 3's screen/picker/copy infrastructure; could run parallel to late Phase 3 if needed.
- Cross-cutting acceptance criteria on every phase: ClaimsPolicy scan coverage for all new strings, `xcodegen generate` after file adds, existing dashboard E2E suite green, accessibility parity (non-gesture value access, grayscale-distinguishable series).

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 3 (Explore overlay):** the lanes-vs-normalized-panel layout decision should be prototyped/decided up front (Pitfall 4 explicitly flags it for phase research — recovery cost is a full UI rework); multi-series tooltip layout is a small design problem of its own.

Phases with standard patterns (skip research-phase):
- **Phase 1:** canonical iOS 17 `chartXSelection` lollipop idiom, verified APIs, in-codebase `inkColor` precedent.
- **Phase 2:** pure Foundation helpers mirroring `ChartSeriesGrouping`; DaySnapshot field mapping already verified field-by-field.
- **Phase 4:** `PointMark` scatter + a pre-tested pairing helper; the hard part (no-stats framing, count encoding) is already specified.

Manual-verification checkpoints to carry in plans: exported PDF visually re-verified (Phase 1); scroll-vs-scrub OS-matrix check (Phases 1, 3, and every OS update); grayscale + sparse-fixture screenshots (Phase 3).

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Every load-bearing API verified against Apple docs availability metadata on 2026-06-10; all ≤ iOS 18 floor |
| Features | MEDIUM | Competitor behavior from vendor support docs + platform conventions, not hands-on audits; UI specifics may have drifted |
| Architecture | HIGH | Every integration claim verified directly against the v1.0 codebase |
| Pitfalls | HIGH/MEDIUM | Codebase-integration pitfalls HIGH (read from source); Swift Charts platform behaviors MEDIUM (forums + community, cross-checked) |

**Overall confidence:** HIGH

### Gaps to Address

- **Bleeding series under suppression:** Explore plots raw data, but whether the bleeding series is shown plainly, annotated, or de-emphasized for suppressed users is a product call — record the decision in the Phase 3 plan (architecture supports either with zero plumbing).
- **Gesture arbitration is OS-version-volatile:** iOS 18/26-beta regressions documented for chart gestures inside scroll containers — make scroll-vs-scrub a recurring release-checklist item, not a one-time check.
- **Explore layout decision (lanes vs labeled normalization):** stack supports both; decide before building Phase 3 (flagged for phase research).
- **Competitor specifics unverified on-device:** Apple Health gesture details partly from platform knowledge — verify the reference interaction on device during Phase 1 UAT.

## Sources

### Primary (HIGH confidence)
- Apple Developer Documentation (availability metadata, fetched 2026-06-10): `chartXSelection(value:/range:)`, `chartGesture`, `chartForegroundStyleScale`, `ChartProxy`/`plotFrame`, `AnnotationOverflowResolution`, `PointPlot`, `chartScrollableAxes`
- v1.0 codebase (read directly): `AppGate.swift`, `Repository.swift` (`daySnapshots()`), `DaySnapshot.swift`, `InsightTrendChart.swift`, `ChartSeriesGrouping.swift`, `ClaimsPolicy.swift`, `CopyAuditTests.swift`, `ReportPDFRenderer.swift`, `InsightsDashboardUITests.swift`
- WWDC23 "Explore pie charts and interactivity in Swift Charts"; WWDC24 "Swift Charts: Vectorized and function plots"

### Secondary (MEDIUM confidence)
- Apple Developer Forums: no secondary Y axis (thread 742659); Swift Charts selection performance and gesture/scroll regressions (threads 740314, 735687, 794212)
- Swift with Majid — chart selection/interaction patterns (cross-checked against Apple docs)
- Competitor docs: Bearable (comparison graph, factor highlighting), Guava (Compare), Apple Health support docs, Clue/Flo baselines
- Dual-axis critiques (PolicyViz, Flourish, Y2Y paper); Xiong et al., IEEE VIS 2019 (scatter plots elicit lowest causality ratings)

### Tertiary (LOW confidence)
- None — no findings rest on a single uncorroborated source.

---
*Research completed: 2026-06-10*
*Ready for roadmap: yes*
