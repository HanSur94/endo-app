# Phase 16: Scatter View & Correlation Deep-Link - Context

**Gathered:** 2026-06-16
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — decisions grounded in the milestone research + the user's earlier choices ("scatter AND overlay", "card → scatter link"); run autonomously per the user's standing "adopt recommendations + continue" preference.

<domain>
## Phase Boundary

The final v1.1 phase: a descriptive-only factor-vs-factor SCATTER view where each point is one day on which BOTH factors were logged, reachable as a mode within Explore AND directly from a "Possible association" dashboard card. Delivers SCAT-01..04. Composes the SHIPPED `ScatterPairing` (Phase 14 — both-logged-only intersection + count aggregation + quantitative-axis-only) UNCHANGED; reuses the Explore screen/picker (Phase 15) + the audited `ExploreCopy` surface. NO new persistence, NO schema/engine/CloudKit changes, NO PDF-path changes. The scatter is the LEAST causally-suggestive encoding (VIS research) — a perfect fit for the descriptive-only honesty posture.

</domain>

<decisions>
## Implementation Decisions

### Where scatter lives (SCAT-01) — LOCKED
- Scatter is a **second MODE inside Explore**: a segmented `Lanes | Scatter` toggle at the top of `ExploreView`. In Scatter mode the user picks exactly TWO quantitative axes (X and Y) from the catalog; the lanes picker is reused/adapted (the scatter axis-picker offers only quantitative series — `ScaleKind.isQuantitative` — since `ScatterPairing` is quantitative-axis-only; presence/event series are not scatter axes).
- The mode toggle uses the reserved `chart.dots.scatter` SF Symbol (UI-SPEC Phase 15 reserved it).

### Scatter rendering (SCAT-01/02) — LOCKED
- Each point = one day where BOTH chosen factors were logged — `ScatterPairing.pairs(snapshots, x:, y:)` (Phase 14) → `[ScatterPoint(x:y:count:)]`. A day missing either factor → NO point (absence ≠ 0, never imputed/zero-filled).
- Identical (x,y) day-pairs STACK honestly via **count-encoded point size AND/OR opacity** (the `count` field) — NEVER jitter that fabricates non-integer coordinates. Discrete 0–10 × 0–10 data collides on a grid; count-encoding is the honest overplotting fix.
- `PointMark` on a Swift Charts scatter; both axes carry their real-unit domain (`SeriesNormalization.domain`) + axis labels. A logged-0 on both axes IS a real point (not excluded).

### Descriptive-only chrome (SCAT-03) — LOCKED
- NO fitted/trend/regression line. NO correlation coefficient / r-value / "% impact" / numeric confidence. A NEUTRAL caption only — e.g. "Based on N days where both were logged" (audited `ExploreCopy`, substring-clean; the count digit is a factual tally like `supportingEvidence`). An honest SPARSE state when too few both-logged days exist ("not enough overlapping days to compare" — audited). The gated correlation engine remains the ONLY thing allowed to say "possible association"; the scatter shows raw points only.

### Correlation-card deep-link (SCAT-04) — LOCKED
- A "Possible association" dashboard card (`.correlation` kind) gains a "see the days behind this" affordance that opens Explore in SCATTER mode PREFILLED with the card's two factors as X/Y. The mapping from the correlation insight to two `ExploreSeries` is the key mechanic — RESEARCH must work out whether to parse the `signalKey` (`correlation.<factor>.symptom.<type>` / `correlation.<factor>.<target.keyFragment>`) or thread the typed `CorrelationFinding` (factor + symptom/target). Prefer a TYPED carrier over copy/string parsing (the Phase-11 "direction from SIGNED effect, never parsed copy" discipline). The affordance copy is audited `ExploreCopy`; the deep-link carries a value, never re-derives.

### Honesty / a11y invariants (carry over) — LOCKED
- Overplotting handled by COUNT, never jitter. Axes in real units, honest fixed domains. Colors/encoding never the sole signal; a text-summary / a11y descriptor so the scatter is never the sole carrier of meaning (point count + axes described). All new chrome via audited `ExploreCopy`. PDF report path UNTOUCHED. New app-target files → `xcodegen generate`. Swift 6 strict concurrency.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ScatterPairing` (EndoInsights, Phase 14): `pairs(_:x:y:calendar:) -> [ScatterPoint]` (both-logged-only, count-aggregated, quantitative-axis-only graceful-degrade), `ScatterPoint(x:y:count:)`. Consumed UNCHANGED.
- `ExploreSeriesCatalog.all` / `ExploreSeries` / `ScaleKind.isQuantitative` (Phase 14) — the axis catalog (filter to quantitative for scatter axes).
- `SeriesNormalization.domain(for:)` / `.readout(...)` (Phase 14) — axis domains + the real-unit point readout.
- `ExploreView` / `SeriesPickerView` / `ExploreSelection` / `ExploreCopy` (Phase 15) — the screen to add the mode toggle to, the picker to adapt for axis selection, the audited copy surface to extend.
- `InsightCardView` (.correlation kind) + the dashboard (`InsightsView`) — where the "see the days behind this" affordance is added; correlation insights carry the two factors (signalKey / `CorrelationFinding`).
- `-uitest-reset`/`-uitest-seed-*`/`-uitest-seed-explore` launch-arg seams + the a11y-id E2E pattern (`ExploreUITests`) — the deterministic E2E approach to mirror for the scatter + deep-link.

### Established Patterns
- SwiftUI + Swift Charts `PointMark`; audited `ExploreCopy` choke-point; XCUITest E2E via seeded launch args + a11y ids; new app-target files need `xcodegen generate`.

### Integration Points
- Add a `Lanes | Scatter` mode toggle + scatter view to `ExploreView`. Add the deep-link affordance to the `.correlation` card → a navigation path that opens Explore in scatter mode with the two factors. Touch NO persistence, NO engine, NO PDF path.

</code_context>

<specifics>
## Specific Ideas

- Scatter is the honesty-aligned differentiator: no mainstream cycle app shows a raw scatter; VIS research says scatter is the least causally-suggestive encoding.
- The correlation-card → scatter link is the high-leverage payoff: the gated engine says "possible association", the scatter lets the user inspect the RAW evidence days behind it.
- Count-encoding (size/opacity) is the ONLY honest overplotting fix for discrete-scale collisions — jitter would fabricate fractional values.

</specifics>

<deferred>
## Deferred Ideas

- Tap a scatter point → that day's log (EXPL-08) — v1.1.x.
- Saved/recent Explore+scatter configs (EXPL-09), landscape, lag-shift — v2.
- Any fitted line / r-value / "% impact" — OUT OF SCOPE permanently (honesty posture).

</deferred>
