# Phase 15: Explore Tab & Overlay Lanes - Context

**Gathered:** 2026-06-15
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — the milestone's central design decisions were put to the user; recommendations accepted. User chose "adopt recommendations + continue autonomously" (no UI-SPEC pre-approval gate).

<domain>
## Phase Boundary

A new consent-gated **Explore** tab (third tab in `ConsentedShell`) where the user combines multiple logged series into one honest overlay: aligned per-scale lanes + event-marker lanes sharing ONE time axis and ONE scrub state. Delivers EXPL-01..07. Reads `Repository.daySnapshots()` and composes the Phase-14 pure layer (`ExploreSeriesCatalog`, `ExploreSeriesExtraction`, `SeriesNormalization`) + the Phase-13 `ChartScrubSelection` resolver + the existing `ChartSeriesGrouping`. Deliberately bypasses the InsightsEngine/HonestyGate (those gate CLAIMS; Explore shows RAW descriptive data). NO scatter view here (that is Phase 16). NO new persistence, NO schema/CloudKit/engine changes.

</domain>

<decisions>
## Implementation Decisions

### Mixed-scale layout (THE central decision — user chose) — LOCKED
- **Aligned per-scale lanes.** Each selected quantitative series renders in its OWN horizontal lane (its real-unit Y domain from `SeriesNormalization.domain`), lanes stacked vertically, ALL sharing one absolute-date X axis. NO dual independent y-axis, NO silent 0–100 normalization in the default view. This is the honest default (dataviz consensus + the v1.0 honest-axis invariant).
- A normalized "shape view" is OUT of scope for v1.1 (not "both") — keep it simple and honest; revisit only on demand.

### Event/binary series (user chose) — LOCKED
- **Event-marker lane:** binary food tags (and bleeding-as-event) render as a row of date-aligned ticks/dots UNDER the quantitative lanes (the Bearable day-highlight pattern), never as a fake 0/1 line. Source: Phase-14 `ExploreSeriesExtraction.occurrenceDays(...) -> [Date]`.

### Suppression (user chose) — LOCKED
- **Hide the bleeding series entirely** for a suppressed user (consistent with v1.0 suppress→section-omitted). Phase-14 accessors already gate bleeding under `!snap.suppressed`; the Explore picker must ALSO not offer bleeding series when the data is suppressed (no empty lane, no offered-but-empty chip).

### Picker, cap, tooltip, range, states (Claude's discretion per convention) — adopted
- **Series picker:** toggle chips (the catalog's `displayName`) — add/remove per tap. Quantitative series capped at 3–4 simultaneously with a clear "remove one to add another" affordance; event lanes are a separate small allowance. Selection state is view-local `@State` (ephemeral — persisting series keys would leak health-interest metadata outside the consent-gated store; do NOT `@AppStorage` it).
- **Multi-series scrub tooltip:** reuse the Phase-13 `ChartScrubSelection` resolver per series; one tooltip for the scrubbed day listing EACH visible series' value in its real unit — or a per-series "Not logged" line (gap-honest, never 0). Free scrub is OK here (Explore's chart is NOT inside a scrolling List, unlike the dashboard — resolves the Phase-13 scroll-vs-scrub tension).
- **Time range:** reuse the dashboard presets / axis vocabulary (absolute dates, gap breaks) — EXPL-06. Same `TrendWindow` family.
- **Honest empty/sparse states:** "pick a series" (nothing selected) and "not enough data" (selected but too sparse) — EXPL-07. All new chrome copy goes through a NEW `ExploreCopy` enum REGISTERED in `CopyAuditTests` BEFORE it is written (an unregistered copy surface silently escapes the ClaimsPolicy scans), curated against the substring traps ("together"/"whether"/"other"/"higher" host "her"; "treatment" hosts "treat").
- **Tab:** third tab labeled "Explore"; a11y id on the tab-item Label (`tab.explore`) — the dashboard's id-on-label lesson (a content-view id does NOT reach the tab-bar button). Pick a lanes-evocative SF Symbol distinct from Insights' `chart.line.uptrend.xyaxis` (UI researcher chooses; e.g. `square.stack.3d.up` / `chart.bar.xaxis` / `slider.horizontal.below.rectangle`).

### Honesty / accessibility invariants (carry over) — LOCKED
- Unlogged days stay GAPS in every lane (absence ≠ 0); reuse `ChartSeriesGrouping` per (series × contiguous-run) so no lane interpolates across gaps. Series colors colorblind-safe + never the sole signal; chart never the sole carrier of meaning (text summary parity). The PDF report path stays UNTOUCHED. New app-target files → `xcodegen generate`.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ConsentedShell` (Sources/EndoApp/App/AppGate.swift:47) — the `TabView` to add the third tab to; consent-by-construction (built only in the `.consented` branch). a11y id MUST be on the tab-item `Label`.
- Phase-14 pure layer (EndoInsights): `ExploreSeriesCatalog.all`, `ExploreSeries`/`ScaleKind`/`ExploreSeriesField`, `ExploreSeriesExtraction.{quantitativePoints→[InsightPoint], occurrenceDays→[Date]}`, `ScatterPairing` (Phase 16), `SeriesNormalization.{domain,normalize,denormalize,readout}`.
- Phase-13 `ChartScrubSelection` (EndoInsights) — the exact-day resolver for the multi-series tooltip.
- `ChartSeriesGrouping` (app target) — gap-run grouping on `[InsightPoint]`, reused per series-run.
- `Repository.daySnapshots()` — the `[DaySnapshot]` source (already manual-wins/HealthKit-merged, nil-honest, suppressed-flagged).
- `InsightTrendChart` / `InsightsCopy` / `CopyAuditTests` — the chart + audited-copy + scan-registration patterns to mirror for `ExploreView` + `ExploreCopy`.
- `-uitest-reset` / `-uitest-seed-*` launch-arg seams (AppDependencies) — the E2E determinism pattern; add an Explore seed + reuse the demo/insights seeds.

### Established Patterns
- SwiftUI + Swift Charts (iOS 18), system fonts/Dynamic Type, single accent reserved for primary action; List/Form-based screens; XCUITest E2E with seeded launch args.
- New app-target files require `xcodegen generate` (the .xcodeproj is generated). Swift 6 strict concurrency.
- Copy choke-point: every new fixed string in `ExploreCopy.auditableStrings`, scanned by `CopyAuditTests`.

### Integration Points
- Add the Explore tab to `ConsentedShell`. Read `Repository.daySnapshots()` (the existing seam). Compose Phase-14/13 helpers. Touch NO persistence, NO engine, NO PDF path.

</code_context>

<specifics>
## Specific Ideas

- Lanes share ONE `chartXSelection` scrub state so a single finger position reads every lane at once (multi-series tooltip).
- The mixed-scale lanes layout is the phase's central design risk (full-UI-rework recovery cost) — prototype the lane composition early.
- Event lanes sit beneath the quantitative lanes; bleeding-as-event is suppression-gated out.

</specifics>

<deferred>
## Deferred Ideas

- Scatter view + correlation-card deep-link → Phase 16.
- Normalized "shape view" toggle → not in v1.1 (revisit on demand).
- Saved/recent Explore configurations (EXPL-09), tap-point→day-log, landscape, lag-shift → v1.1.x/v2.

</deferred>
