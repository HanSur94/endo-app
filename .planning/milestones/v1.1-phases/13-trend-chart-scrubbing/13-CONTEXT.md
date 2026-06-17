# Phase 13: Trend-Chart Scrubbing - Context

**Gathered:** 2026-06-10
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — recommendations accepted (Area 2 explicitly left to Claude's discretion)

<domain>
## Phase Boundary

Users can read the exact day and value behind any dashboard trend chart by touch — gap-honest, haptic, and accessible — while the PDF report render path stays byte-identical. Covers CHART-01..04 on the EXISTING `InsightTrendChart` (dashboard cards only). No Explore screen, no multi-series anything — those are Phases 15/16; this phase only ships the reusable selection foundations (pure resolver, a11y pattern, E2E seam) they consume.

</domain>

<decisions>
## Implementation Decisions

### Dashboard-Interaktion (CHART-01/03) — accepted
- Gesture: framework-standard `chartXSelection` — the tooltip lives while touching and dismisses on release (CHART-03 verbatim); vertical List scrolling keeps priority (framework arbitration; no custom gesture fighting)
- Tooltip content: absolute date + value with unit (e.g. "Jun 5 — severity 6 of 10"); on gap days the explicit "Not logged" state
- Tooltip position: pinned at the chart top via RuleMark + `.annotation(overflowResolution: .fit)` so it never clips at window edges and the finger never occludes it (Health pattern)
- Haptic: `UISelectionFeedbackGenerator` fires on day CHANGE only (not on initial touch, not on release)

### Gap-Ehrlichkeit & Resolver (CHART-02) — user had no preference; recommendations stand as Claude's discretion
- Day resolution: the EXACT calendar day under the finger resolved through a pure resolver (selected raw date → logged-day-or-nil); an unlogged day yields the explicit "Not logged" state — NEVER interpolation, NEVER snap-to-nearest-logged (position-based selection APIs interpolate by default; the resolver is the honesty choke-point Phases 15–16 reuse)
- Gap-day tooltip: shows the date + "Not logged" (the gap is information, not an error)
- Logged severity-0: reads "0 of 10" — visually and textually distinct from "Not logged" (absence ≠ 0)
- Selection state: per-card, view-local `@State`, ephemeral — never persisted

### Accessibility-Alternative (CHART-04) — accepted
- Mechanism: keep/extend the existing per-mark `accessibilityLabel`/`accessibilityValue` + adopt `AXChartDescriptor` (Audio Graph) so VoiceOver users navigate per-day values WITHOUT the drag gesture
- Announcements: quiet — selection surfaces via accessibilityValue; no `UIAccessibility.post` chatter on every day change

### Test-Seam & PDF-Schutz — accepted
- E2E determinism: DEBUG-gated `-uitest-select-day` launch-arg seam sets a deterministic selection; XCUITests assert via a11y ids (tooltip id, "Not logged" variant) — no gesture-coordinate flakes
- API shape: additive defaulted parameter `allowsSelection: Bool = false` on `InsightTrendChart` — the dashboard card passes `true`; `ReportPDFRenderer` is NOT edited (default `false` protects the PDF path by construction, mirroring the `inkColor` precedent)
- PDF verification: ReportFlowUITests re-run green + a visual PDF check noted as a manual checkpoint item

### Claude's Discretion
- All Area-2 details above (user expressed no preference)
- Exact tooltip copy wording (must register in `InsightsCopy.auditableStrings` BEFORE writing; curate against the substring scans — no "her"/"she"/"will"/"causes" hosts)
- Resolver naming/placement (research suggests pure `ChartScrubSelection` in EndoInsights with unit tests, following the `ChartSeriesGrouping` playbook)
- Haptic generator lifecycle details

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `InsightTrendChart` (Sources/EndoApp/Features/Insights/InsightTrendChart.swift) — the ONLY Chart in the repo; already has per-mark a11y labels/values, fixed 0–10 Y domain, absolute-date X domain per window, honest gap-breaking via distinct `series:` per run
- `ChartSeriesGrouping.runs(from:calendar:)` — pure, unit-tested gap-run grouping (EndoInsights)
- `InsightsCopy` + `auditableStrings` + `CopyAuditTests` — the audited chrome-copy choke point for new tooltip strings
- `inkColor` additive-param precedent (Phase 10) — the exact pattern for `allowsSelection`
- Existing `-uitest-*` launch-arg seams in `AppDependencies` — the pattern for `-uitest-select-day`

### Established Patterns
- Honesty: absence ≠ 0; unlogged = gap; charts never sole carrier of meaning (text summary above chart)
- Swift 6 strict concurrency; iOS 18 floor (chartXSelection is iOS 17+, fine)
- New app-target files require `xcodegen generate` (the .xcodeproj is generated)

### Integration Points
- `InsightCardView.supportingData` (.trend case) instantiates the chart — passes `allowsSelection: true` there
- `ReportPDFRenderer` builds a FRESH `InsightTrendChart` with `inkColor: .black` — must remain untouched (default `allowsSelection: false`)
- Dashboard cards live inside a `List` (`InsightsView`) — scroll-vs-scrub arbitration is the known OS-sensitive spot (manual check item)

</code_context>

<specifics>
## Specific Ideas

- Tooltip mirrors Apple Health's scrub affordance (pinned readout, live-while-touching) — the platform reflex users already know
- The pure resolver is deliberately the SHARED foundation: Phases 15 (multi-series tooltip) and 16 (scatter selection) consume it unchanged

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. (Explore/scatter interactivity is already Phases 15/16.)

</deferred>
