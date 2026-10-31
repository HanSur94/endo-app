---
phase: 22-insights-explore-charts
plan: 05
subsystem: insights-explore
tags: [compose, canvas, explore, overlay-lanes, event-lanes, scrub, chart-honesty, suppression, copy-audit, a11y]

# Dependency graph
requires:
  - phase: 17-insights-engine-port
    provides: "ExploreSeries/Catalog/ScaleKind, ExploreSeriesExtraction (quantitativePoints/occurrenceDays), SeriesNormalization (fixed per-scale domain + readout), ChartScrubSelection.resolve, DaySnapshot"
  - phase: 22-insights-explore-charts (Plan 02)
    provides: "ChartPrimitives/ChartCoordinateMapper + ChartSeriesGrouping.runs (gap-honest drawing), ChartsCopy.NOT_LOGGED"
  - phase: 22-insights-explore-charts (Plan 03)
    provides: "ChartSemanticsOverlay + trendSemanticNodes + ChartSemanticNode (reusable per-point TalkBack overlay)"
  - phase: 18-persistence-projection
    provides: "EndoRepository.daySnapshots() consent-gated projection read"
  - phase: 19-bloom-design-system
    provides: "BloomColors (rose/indigo/plum/gold/teal), BloomTypography, DisclaimerBanner, CopyAudit harness, ConsentedShell Explore slot"
provides:
  - "ExploreScreen (Lanes mode): suppression-filtered picker + per-scale overlay lanes + event lanes on ONE shared time axis + ONE scrub + multi-series tooltip + honest empty/sparse states"
  - "ExploreSelection.offered(suppressed): pure bleeding-filter (defense-in-depth over the accessor gate)"
  - "SeriesPicker + quantitativeSelectionAtCap/chipEnabled: <=4 quantitative cap (event chips uncapped)"
  - "ExploreLaneChart + EventLaneChart: reusable per-scale quant lane + occurrence-tick event lane (Canvas)"
  - "quantLaneDomain(series): the single per-lane fixed-domain source (H3/H4)"
  - "ExploreCopy (registered): Explore chrome strings"
affects: [22-06 scatter mode + scatter deep-link into this screen, 24 pdf-report reuses lane drawing]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Aligned per-scale overlay lanes: each quantitative series a SEPARATE Canvas on its OWN fixed SeriesNormalization.domain (no shared/dual y-axis); the scale lives in the lane title, the numeric y-axis is hidden so lanes share full width and the one scrub rule aligns"
    - "Event lanes render List<LocalDate> occurrence ticks (never a 0/1 line) + a summary node instead of a value axis — a 0/1 line is structurally unrepresentable"
    - "ONE shared scrub: a single scrub surface over the whole stack maps x->day; each lane reads the shared day in the DRAW phase (draw-only invalidation); the multi-series tooltip is isolated so only it recomposes on a day-crossing"
    - "Multi-series tooltip: quantitative lines resolve via ChartScrubSelection.resolve (exact day/gap), event lines via series.isPresent (events have no scrub-resolver path); each line carries its own un-masked testTag"
    - "Ephemeral-only selection/mode/range (never persisted) — no health-interest metadata leaves the consent-gated store"
    - "Defense-in-depth suppression: ExploreSelection.offered removes bleeding chips AND the :insights accessors gate every bleeding value/occurrence"

key-files:
  created:
    - "android/app/src/main/kotlin/com/endoapp/app/ui/explore/ExploreScreen.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/explore/SeriesPicker.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/explore/ExploreSelection.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/charts/ExploreLaneChart.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/charts/EventLaneChart.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/content/ExploreCopy.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/ui/explore/ExploreLanesTest.kt"
    - "android/app/src/test/kotlin/com/endoapp/app/ui/charts/LaneHonestyTest.kt"
  modified:
    - "android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/AppGate.kt"
    - "android/app/src/main/kotlin/com/endoapp/app/ui/shell/ConsentedShell.kt"

key-decisions:
  - "quantLaneDomain(series) is the SINGLE per-lane domain source = SeriesNormalization.domain(scaleKind); a sleep lane draws against 0..24 and a stress lane against 0..10 INDEPENDENTLY — asserted both purely and via distinct real-unit a11y readouts (a normalized axis could not produce two different real-unit readouts)"
  - "The mode/range toggle is a custom Bloom segmented control (ExploreSegmentedToggle) rather than the experimental M3 SingleChoiceSegmentedButtonRow — avoids experimental-API coupling while keeping active-plum/inactive-petal visual parity + full testTag/semantics control"
  - "The Explore snapshot seam is bound in AppGate's Consented arm (repository::daySnapshots), derived from the repository AppGate already receives — so MainActivity needed no change (the seam binds one layer down, mirroring loadSuppressed/healthConnect)"
  - "ExploreScreen fails closed: suppressed defaults to true until the load resolves, so a bleeding chip can never flash pre-load"
  - "The multi-series tooltip is isolated in a BoxScope child reading the scrub state, so a day-crossing recomposes only the tooltip; lanes read the shared day in the draw phase and stay put"

requirements-completed: [AINS-04]

coverage:
  - id: H3-H4
    description: "No dual/shared y-axis, no silent normalization — each quantitative lane on its own fixed real-unit domain"
    requirement: "AINS-04"
    verification:
      - kind: unit
        ref: "LaneHonestyTest.eachLaneDrawsOnItsOwnFixedDomain_noSharedOrNormalizedAxis (quantLaneDomain 0..24 vs 0..10 + distinct real-unit a11y readouts 8 h vs 8 of 10)"
        status: pass
    human_judgment: false
  - id: H1-H2
    description: "Gaps stay gaps (line break) and a logged 0 is a real point in a quantitative lane"
    requirement: "AINS-04"
    verification:
      - kind: unit
        ref: "LaneHonestyTest.quantLane_gapIsABreak_andLoggedZeroIsARealPoint"
        status: pass
    human_judgment: false
  - id: EVENT-LANE
    description: "Event lane = occurrence ticks + summary node, never a 0/1 line or value axis"
    requirement: "AINS-04"
    verification:
      - kind: unit
        ref: "LaneHonestyTest.eventLane_rendersOccurrenceTicksAndASummary_notAValueAxis"
        status: pass
    human_judgment: false
  - id: SHARED-SCRUB
    description: "ONE shared scrub across lanes feeds one multi-series tooltip reading each lane's exact day (gap -> Not logged)"
    requirement: "AINS-04"
    verification:
      - kind: unit
        ref: "ExploreLanesTest.oneSharedScrub_readsEveryVisibleLane + LaneHonestyTest.multiSeriesTooltip_showsOneLinePerSeries_withNotLoggedForAGap"
        status: pass
    human_judgment: false
  - id: SUPPRESSION
    description: "A suppressed user is offered no bleeding chip and no bleeding lane renders"
    requirement: "AINS-04"
    verification:
      - kind: unit
        ref: "ExploreLanesTest.offered_underSuppression_excludesBothBleedingSeries + suppressedUser_isOfferedNoBleedingChip"
        status: pass
    human_judgment: false
  - id: CAP
    description: "<=4 quantitative series; unselected quant chips disable at cap + hint shows; event chips uncapped"
    requirement: "AINS-04"
    verification:
      - kind: unit
        ref: "ExploreLanesTest.capArithmetic_isQuantitativeOnly + atQuantitativeCap_unselectedQuantChipsDisable_eventChipsStayEnabled"
        status: pass
    human_judgment: false
  - id: DEVICE-FEEL
    description: "Device scrub feel / haptic / TalkBack sweep + flat-recomposition during scrub"
    requirement: "AINS-04"
    verification:
      - kind: manual
        ref: "22-VALIDATION Manual-Only — deferred to device pass"
        status: deferred
    human_judgment: true

# Metrics
duration: 55min
completed: 2026-07-09
status: complete
---

# Phase 22 Plan 05: Explore Lanes — Per-Scale Overlay + Event Lanes + Shared Scrub Summary

**The Explore tab's Lanes mode — a suppression-filtered series picker (<=4 quantitative cap) feeding aligned per-scale OVERLAY lanes (each quantitative series a SEPARATE Canvas on its OWN fixed real-unit domain — no dual y-axis, no shared axis, no silent normalization; H3/H4) plus event-marker lanes (occurrence ticks, never a 0/1 line) on ONE shared time axis with ONE scrub and a single multi-series tooltip reading each lane's exact-day value (gap -> "Not logged"). Reuses the Plan-02 ChartPrimitives + the Plan-03 ChartSemanticsOverlay for per-point TalkBack, with honest empty/sparse states and registered ExploreCopy.**

## Performance
- **Duration:** ~55 min
- **Completed:** 2026-07-09
- **Tasks:** 2 (delivered across 3 atomic commits — see Deviations)
- **Files:** 11 (8 created, 3 modified)

## Accomplishments
- **`ExploreScreen`** — a `Column` in `verticalScroll` (`explore.screen`), serif "Explore" title, reused not-medical-advice `DisclaimerBanner`, a `Lanes | Scatter` mode toggle (default Lanes; Scatter is an honest Plan-06 placeholder). Reads snapshots + suppressed via injected SUSPEND seams so it is testable off a fake and consent-by-construction holds. Selection/mode/range live in EPHEMERAL state ONLY (never persisted). Fails closed (suppressed defaults true until load).
- **`SeriesPicker` + pure cap** — a `FlowRow` of toggle chips over `ExploreSelection.offered(suppressed)` + a range control. `quantitativeSelectionAtCap`/`chipEnabled` are PURE: at 4 quantitative selections unselected quantitative chips disable + `explore.picker.capHint` shows; event chips are uncapped. Selected vs unselected differ by fill + icon + weight (never hue alone).
- **`ExploreSelection.offered`** — the pure defense-in-depth filter removing BOTH bleeding series (`bleeding.intensity` + `bleeding.event`) under suppression, over the `:insights` accessor gate.
- **`ExploreLaneChart`** — one quantitative overlay lane (Canvas, ~120dp) on its OWN fixed `SeriesNormalization.domain` via the single `quantLaneDomain` source; hidden numeric y-axis, scale in the title ("Sleep — h"); semantic mark hue per `scaleKind`; gaps = line breaks (reuse `ChartSeriesGrouping`/`ChartPrimitives`), one dot per logged day; per-point TalkBack overlay via `trendSemanticNodes` with the lane's real-unit readout.
- **`EventLaneChart`** — a presence lane (~44dp) of date-aligned occurrence ticks from `occurrenceDays` (never a connected 0/1 line), a summary node "{name} — logged on {n} days" INSTEAD of a value axis, per-tick TalkBack nodes ("Logged"), shared scrub rule.
- **Shared scrub + multi-series tooltip** — one scrub surface over the whole stack maps x->day; each lane reads the shared day in the DRAW phase; one tooltip card resolves quantitative lines via `ChartScrubSelection.resolve` (real-unit or "Not logged") and event lines via `series.isPresent`, each line un-masked by its own testTag.
- **`ExploreCopy`** — registered in `CopyAuditRegistry` in the SAME change; ports the UI-SPEC Explore strings VERBATIM with the flagged substring dodges ("add a new one" not "another"; the honest-gap sparse body; "Logged" never "1").
- **Wiring** — the Explore snapshot seam threaded `AppGate` (Consented arm ONLY) -> `ConsentedShell` -> `ExploreScreen`, replacing the Phase-19 placeholder.

## Task Commits
1. **Explore lane + event Canvas charts** — `7cc9d57` (feat)
2. **Explore Lanes screen — picker, per-scale lanes, shared scrub, copy, wiring** — `bf38f9f` (feat)
3. **Explore lanes + lane-honesty tests** — `1ceda04` (test)

## REAL Test Results
- `:app:testDebugUnitTest` — **304/304 pass, 0 failures, 0 skipped** (+10 new: `ExploreLanesTest` 6, `LaneHonestyTest` 4).
- `:insights:test` — **176/176 pass** (source untouched).
- `:app:assembleDebug` — **SUCCESSFUL**. `:app:lintDebug` — **SUCCESSFUL**.
- `NotificationSeparationTest`, `InsightsSeparationTest`, `AppGateConsentTest` — **all pass** (Explore imports no notification/alarm API; the consent gate holds).
- `CopyAuditTest` — green over the enlarged catalog including `ExploreCopy` (substring-clean against ClaimsPolicy).
- Honesty PROVEN at the render layer: sleep lane maps 0..24 and stress lane 0..10 INDEPENDENTLY (distinct real-unit a11y readouts "8 h" vs "8 of 10" — no shared/normalized axis); a gap day is a `.notLogged` break (H1) and a logged severity-0 a real "0 of 10" point (H2); the event lane renders occurrence ticks + a summary node (no value axis, no 0/1 line); one shared scrub reads every visible lane into one tooltip with "Not logged" for a gap; a suppressed user is offered no bleeding chip and the cap disables unselected quantitative chips (events uncapped).

## Deviations from Plan
- **[Sequencing] Commit boundaries reordered from the plan's Task 1/Task 2 split.** The plan groups the lane charts under Task 2, but `ExploreScreen` (Task 1) renders them, so the charts must compile first. Delivered as three atomic commits — charts (`7cc9d57`), then the screen + picker + copy + wiring (`bf38f9f`), then both test files (`1ceda04`). Each commit compiles; the final state is complete and matches both tasks' `<done>` criteria.
- **[Rule 3] MainActivity not modified.** The plan listed `MainActivity.kt` in `files_modified`, but the Explore snapshot seam (`repository::daySnapshots`) binds inside `AppGate`'s Consented arm from the `repository` AppGate already receives — mirroring how `loadSuppressed`/`healthConnect` bind. No `MainActivity` change was needed or made.
- **[Rule 3] Mode/range toggle uses a custom Bloom segmented control** (`ExploreSegmentedToggle`) instead of the experimental M3 `SingleChoiceSegmentedButtonRow`, to avoid experimental-API coupling. Active-plum / inactive-petal visual parity and the `explore.mode.*` / `explore.range.*` testTags are preserved.

## Threat Model
- **T-22-01 (Tampering / lane render honesty) — MITIGATED:** separate per-scale lanes on the fixed `SeriesNormalization.domain` (no dual/shared y-axis), gaps = breaks, events = ticks not 0/1 — pinned by `LaneHonestyTest`.
- **T-22-01h (Info disclosure / bleeding under suppression) — MITIGATED:** `ExploreSelection.offered` removes both bleeding chips AND the `:insights` accessor gate stops values; `ExploreLanesTest` asserts no bleeding chip for a suppressed user.
- **T-22-01i (Info disclosure / ephemeral selection) — MITIGATED:** Explore selection/mode/range live in ephemeral Compose state, never persisted.

## Known Stubs
Scatter mode is an intentional honest placeholder (`explore.scatter.placeholder`, "The scatter view is coming soon.") that **Plan 06** fills with the count-encoded scatter + the scatter deep-link into this screen. This is planned scope, not a data stub — the Lanes mode this plan owns is fully wired against real `daySnapshots()` data.

## User Setup Required
None.

## Next Phase Readiness
- Plan 06 fills the Scatter-mode branch (count-encoded PointMark cloud, no fitted line/r-value) and the "See the days behind this" deep-link that switches to this screen in Scatter mode.
- Phase 24 (PDF report) reuses `ExploreLaneChart`/`EventLaneChart`'s `ChartPrimitives`-based drawing for the vector PDF chart.
- Device scrub feel / haptic / TalkBack sweep + flat-recomposition during scrub remain the documented Manual-Only verification (22-VALIDATION).

## Self-Check: PASSED

All 8 created files + the 3 modified files (`AuditableCopy.kt`, `AppGate.kt`, `ConsentedShell.kt`) and this SUMMARY exist on disk; all three task commits (`7cc9d57`, `bf38f9f`, `1ceda04`) are in the git log.

---
*Phase: 22-insights-explore-charts*
*Completed: 2026-07-09*
