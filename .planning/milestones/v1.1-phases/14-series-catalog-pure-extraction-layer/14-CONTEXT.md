# Phase 14: Series Catalog & Pure Extraction Layer - Context

**Gathered:** 2026-06-15
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — infrastructure phase, grey-area questions skipped (no user-facing behavior; success criteria are all code-level)

<domain>
## Phase Boundary

Build the pure, Foundation-only, unit-tested helpers that serve every data need of the Explore overlay (Phase 15) and the scatter view (Phase 16), so the honesty invariants are locked in code BEFORE any Explore pixel exists. Deliverables (per research SUMMARY/ARCHITECTURE): a unified `ExploreSeries` catalog (every loggable series with id/displayName/unit/scale-kind metadata), `ExploreSeriesExtraction` (`[DaySnapshot]` + a series choice → `[InsightPoint]`), `ScatterPairing` (both-logged-day intersection with identical-pair aggregation), and `SeriesNormalization` (real-unit-preserving readout helper). NO SwiftUI, NO views, NO new app-target files — everything lives in the EndoInsights package and is verified by its Swift Testing suite. This phase carries no direct requirement IDs; it unblocks EXPL-02/03/04/05/07 + SCAT-01/02 in the next two phases.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion (infrastructure phase)
All implementation choices are at Claude's discretion — discuss grey-areas skipped per infrastructure detection. The binding constraints come from the codebase + research, not from new user preferences:

- **Honesty invariants are LOCKED (non-negotiable, encode in types):** unlogged day → NO point emitted (nil → gap, absence ≠ 0); a logged literal 0 (severity 0, etc.) → a REAL point at 0 (distinct from a gap); presence-kind series (food tags, clots, PRN, medication-taken, and bleeding-as-events) extract as OCCURRENCE days, never coerced into 0/1 values — the series-kind model must make that coercion unrepresentable; scatter pairing returns ONLY days where BOTH factors are logged, with identical day-pairs aggregated/counted (never jittered into fake fractional values).
- **Scale taxonomy:** model each series' kind explicitly (e.g. ordinal 0–10 severity/stress, ordinal 1–5 mood, continuous hours/minutes, ordinal bleeding intensity, presence/event tags). The mixed-scale PRESENTATION decision (lanes vs labeled normalization) and the bleeding-under-suppression product call belong to Phase 15 (flagged research:true there) — Phase 14 only supplies honest, presentation-agnostic primitives + the metadata they need.
- **Reuse, don't reinvent:** emit the existing `EndoInsights.InsightPoint(date:value:)` so the shipped app-side gap-breaking `ChartSeriesGrouping` is reused UNCHANGED downstream. Follow the `ChartSeriesGrouping`/`ChartScrubSelection` pure-helper playbook (Foundation-only, calendar-injected for determinism, UTC-fixed test fixtures).
- **Suppression:** honor the per-`DaySnapshot.suppressed` flag for bleeding/cycle-derived series so a suppressed user's bleeding series cannot leak (the per-snapshot flag already exists; extraction must respect it, leaving the final UI treatment to Phase 15).
- **Boundary lint:** the package's BoundaryLintTests (no SwiftData/SwiftUI/HealthKit) must stay green — all new code is Foundation-only.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `EndoInsights.DaySnapshot` — the single data contract (verified field surface): `symptoms: [SymptomPoint(type, severity: Int?)]`, `pain: [PainPoint(region, severity: Int?)]`, `flow: FlowIntensity?`, `clots: Bool`, `foodTags: [FoodTag]`, `activityType/Minutes/Intensity`, `sleepHours: Double?`, `sleepQuality: SleepQuality?`, `medicationsTaken: [UUID]`, `tookPRN: Bool`, `stress: Int? (0–10)`, `mood: Int? (1–5)`, `hasNote: Bool`, `suppressed: Bool`, `day: Date (start-of-day)`. nil = unlogged throughout.
- `EndoInsights.InsightPoint(date:value:)` — the plottable value type to emit (reused by `ChartSeriesGrouping`).
- `ChartSeriesGrouping` (app target) — gap-run grouping helper that downstream views reuse on the emitted points.
- `ChartScrubSelection` (EndoInsights, Phase 13) — the pure-helper playbook to mirror (Foundation-only, calendar-injected, exact-match honesty, UTC-fixed Swift Testing fixtures).
- `Repository.daySnapshots()` (app target) — already projects the live store into `[DaySnapshot]`; Phase 15/16 feed its output into these helpers. NOT modified here.

### Established Patterns
- Pure logic lives in EndoInsights with Swift Testing; honesty properties become unit tests BEFORE any rendering exists.
- New EndoInsights package files do NOT need `xcodegen generate` (only app-target files do).
- Swift 6 strict concurrency; `Sendable`/`Equatable` value types.

### Integration Points
- Downstream only (Phases 15/16) — this phase adds package types and tests, touches no UI and no app target.

</code_context>

<specifics>
## Specific Ideas

- The series catalog is the keystone: build it once, both Explore and Scatter consume it. Each entry maps a `DaySnapshot` field (or per-type/per-region sub-series) to extractable points + display/scale metadata.
- Per-symptom and per-pain-region are distinct selectable series (the catalog enumerates them), not a single lumped "symptoms" series.

</specifics>

<deferred>
## Deferred Ideas

- Mixed-scale lane layout vs labeled normalization (Phase 15, research-flagged).
- Bleeding-series-under-suppression UI treatment (Phase 15 product call).
- Any view/SwiftUI code (Phases 15/16).

</deferred>
