---
phase: 15-explore-tab-overlay-lanes
plan: 01
subsystem: ui
tags: [swift-charts, swiftui, lane-alignment, chartXScale, chartYAxis-hidden, prototype]

# Dependency graph
requires:
  - phase: 14-explore-pure-layer
    provides: "SeriesNormalization.domain(for:) fixed honest per-ScaleKind domains; InsightPoint; ScaleKind"
provides:
  - "RESOLVED A2 lane-alignment mechanism: .chartYAxis(.hidden) + unit-in-lane-title makes sibling Charts on different Y scales share identical plot leading insets"
  - "Throwaway _LaneAlignmentProbe.swift proving 3-lane shared-X / shared-RuleMark alignment by screenshot"
  - "Screenshot evidence at /tmp/e2e/15-lane-alignment.png"
affects: [15-04, explore-lane-build, ExploreLaneChart, ExploreLanesView]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Shared-scrub sibling lanes: N separate Charts, IDENTICAL chartXScale(domain:), one @State Date? bound to every chartXSelection, per-lane chartYScale from SeriesNormalization.domain(for:)"
    - "A2 inset alignment: .chartYAxis(.hidden) on every quantitative lane + carry unit/honest-range in a .subheadline lane TITLE → no lane reserves a numeric-label left gutter → all plots share full width → shared RuleMark aligns"

key-files:
  created:
    - Sources/EndoApp/Features/Explore/_LaneAlignmentProbe.swift
  modified:
    - Sources/EndoApp/App/EndoAppApp.swift

key-decisions:
  - "A2 RESOLVED: the DEFAULT mechanism (.chartYAxis(.hidden) + unit-in-title) aligns cleanly — NO fallback to a fixed-width Y axis is needed"
  - "Probe written as THREE inline Chart bodies (not one factored lane view) so each chartXScale/chartYScale/chartYAxis(.hidden)/chartXSelection call is visible per lane and mirrors the real 15-04 stack"
  - "Screenshot captured by a DEBUG-only -probe-lane-alignment launch-arg host (the probe is otherwise preview-only and unreachable in-app)"

patterns-established:
  - "Hidden-Y-axis + unit-in-title is the chosen Explore lane inset mechanism for ALL quantitative lanes in 15-04"

requirements-completed: []  # PROTOTYPE — no EXPL requirement is marked complete; 15-04 implements EXPL-03/04/05 for real

# Metrics
duration: 5min
completed: 2026-06-15
---

# Phase 15 Plan 01: Lane-Alignment Probe Summary

**Verified by screenshot that `.chartYAxis(.hidden)` + unit-in-lane-title makes 3 sibling Swift Charts on radically different Y scales (0...10 / 0...24 / 0...240) share identical plot leading insets, so one shared RuleMark crosses all lanes at the same date — A2 (the milestone's central design risk) is RESOLVED with the default mechanism.**

---

## ⭐ CHOSEN ALIGNMENT MECHANISM (load-bearing — 15-04 MUST consume this)

### (a) Which mechanism aligned

The **RESOLVED DEFAULT** — `.chartYAxis(.hidden)` on every quantitative lane + carrying the unit and honest min/max in a per-lane `.subheadline` TITLE above each chart. It aligned **cleanly on the first try**. The fallback (fixed-width Y axis / `.chartPlotStyle` constant left gutter from RESEARCH Pitfall 1 option 1) was **NOT needed** and was not exercised.

### (b) Exact modifiers used per lane

Each of the three sibling lanes applies, in this order:

```swift
Chart {
    ForEach(points, id: \.date) { p in
        LineMark(x: .value("Date", p.date), y: .value("<SeriesName>", p.value))
        PointMark(x: .value("Date", p.date), y: .value("<SeriesName>", p.value))
    }
    RuleMark(x: .value("Selected", probeDate))          // shared fixed-date scrub rule
        .foregroundStyle(.secondary.opacity(0.5)).zIndex(-1)
}
.chartXScale(domain: xDomain)                           // IDENTICAL ClosedRange<Date> on every lane
.chartYScale(domain: SeriesNormalization.domain(for: kind) ?? 0...1)   // per-lane fixed honest domain
.chartYAxis(.hidden)                                    // ← THE inset mechanism: no numeric Y gutter
.chartXSelection(value: $selectedDate)                  // ONE shared @State Date? across all lanes
.frame(height: 120)                                     // lane-quant-height (UI-SPEC)
```

The unit/range lives in a sibling `Text("Severity — of 10").font(.subheadline).foregroundStyle(.secondary)` directly above each `Chart` (NOT inside the chart) — this is what makes hiding the numeric Y axis lossless for legibility.

### (c) Screenshot path

`/tmp/e2e/15-lane-alignment.png` — booted iPhone 16 simulator (iOS 26.5), launched via the DEBUG `-probe-lane-alignment` host.

### (d) Caveats 15-04 MUST honor

1. **`.chartYAxis(.hidden)` is mandatory on EVERY quantitative lane** — including the narrow 0...10 ones. The alignment property holds only if NO lane reserves a numeric-label left gutter; a single lane with a visible numeric Y axis would shift only that lane's plot and re-break the shared scrub line.
2. **The unit/scale reference MUST move to the lane title** (`displayName` + unit, e.g. "Sleep — h"). With the numeric Y axis hidden, the title is the only remaining scale signal — do not drop it.
3. **`chartXScale(domain:)` must be byte-identical across lanes** (same `ClosedRange<Date>` value). Identical-but-recomputed domains are fine as long as the values are equal; alignment depends on equal X domains, not shared identity.
4. **The shared RuleMark uses the SAME date value per lane** — in the probe a fixed `probeDate`; in 15-04 it is the resolved scrub day. The X-axis labels (`16. Jun` / `23. Jun`) also lined up across lanes, confirming the X domain renders identically.
5. **Event lanes (EXPL-04, height 44)** already plan `.chartYAxis(.hidden)` + `chartYScale(domain: 0...1)`, so they inherit the same identical-inset property for free — no extra work, but keep them in the SAME stack so their plot width matches the quantitative lanes.
6. The probe validated **only** the inset/alignment mechanism. It does NOT validate the multi-series tooltip, the gap-run `series:` keying, or the haptic guard — those reuse shipped Phase-13 contracts unchanged and are out of this prototype's scope.

---

## Performance

- **Duration:** 5 min
- **Started:** 2026-06-15T20:36:05Z
- **Completed:** 2026-06-15T20:41:22Z
- **Tasks:** 2
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments
- Built a throwaway 3-lane probe with deliberately maximal Y-label-width divergence (0...10 severity, 0...24 sleep, 0...240 activity) sharing ONE X domain + ONE scrub binding + ONE fixed RuleMark.
- `xcodegen generate` picked up the new file via the `Sources/EndoApp` glob with no `project.yml` edit; the app target builds **BUILD SUCCEEDED** under Swift 6 strict concurrency.
- Captured a real simulator screenshot and eyeballed it: the gray RuleMark is vertically continuous across all three lanes; the activity 0...240 lane's plot does NOT start further right than the severity lane. Gap days break the lines honestly.
- Recorded the resolved mechanism so 15-04 builds against a verified approach instead of discovering it under the full assembly.

## Task Commits

Each task was committed atomically:

1. **Task 1: Build the 3-lane alignment probe and force identical plot insets** - `20237de` (feat)
2. **Task 2: Regenerate project, build, screenshot, record mechanism** - `f6bf38b` (chore — DEBUG-only host)

**Plan metadata:** (docs commit below)

## Files Created/Modified
- `Sources/EndoApp/Features/Explore/_LaneAlignmentProbe.swift` - Throwaway 3-lane alignment probe + `#Preview`; three inline sibling Charts with the A2 default inset mechanism.
- `Sources/EndoApp/App/EndoAppApp.swift` - Added a DEBUG-only `-probe-lane-alignment` launch-arg host that renders the probe as root for screenshotting (removed with the probe in 15-04).

## Decisions Made
- **A2 resolved with the default mechanism** — `.chartYAxis(.hidden)` + unit-in-title aligned cleanly; fixed-width-Y fallback not needed (see the starred section above for full detail).
- Wrote the probe as three INLINE Chart bodies rather than a factored reusable lane view, so the per-lane modifier set is explicit and the plan's structural verification (`chartXScale(domain:)` ≥3 occurrences) holds.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Inlined the three lanes instead of one reusable lane view**
- **Found during:** Task 1
- **Issue:** The first draft factored the lane into one reusable `_ProbeLane` view used 3×. That is functionally correct but collapses `chartXScale(domain:)` to a single source occurrence, failing the plan's `<verify>` gate which counts ≥3 literal occurrences (the plan's `<action>` explicitly specifies "THREE sibling `Chart` views").
- **Fix:** Rewrote the probe with three inline `Chart` bodies, one per lane — each with its own `chartXScale`/`chartYScale`/`chartYAxis(.hidden)`/`chartXSelection`. This also better mirrors the real 15-04 stack.
- **Files modified:** Sources/EndoApp/Features/Explore/_LaneAlignmentProbe.swift
- **Verification:** `PROBE_OK` (Task 1 automated gate passes).
- **Committed in:** `20237de`

**2. [Rule 3 - Blocking] Added a DEBUG-only probe host to capture the screenshot**
- **Found during:** Task 2
- **Issue:** The probe is preview-only (not wired into any tab/ConsentedShell), and there is no headless `#Preview` render CLI on this toolchain — so the screenshot could not be captured without rendering the probe on the simulator.
- **Fix:** Added a `#if DEBUG`, launch-arg-gated (`-probe-lane-alignment`) branch in `EndoAppApp.body` that renders `_LaneAlignmentProbe()` as root. The plan's Task 2 explicitly permits "stand up a trivial DEBUG-only host." Unreachable in Release and on any normal launch; removed with the probe in 15-04.
- **Files modified:** Sources/EndoApp/App/EndoAppApp.swift
- **Verification:** `BUILD_AND_SHOT_OK` (Task 2 automated gate passes); screenshot rendered correctly.
- **Committed in:** `f6bf38b`

---

**Total deviations:** 2 auto-fixed (both Rule 3 - blocking, to satisfy the plan's own verification gates).
**Impact on plan:** No scope creep — both deviations serve the plan's stated deliverable (3 sibling lanes + a screenshot). No new dependencies, no persistence, no shipped copy, no health-data surface (T-15-01/02 honored: synthetic hard-coded points only).

## Issues Encountered
- A stale top-level brace after introducing the `appRoot` computed property caused one build failure ("extraneous '}' at top level"); fixed by removing the now-redundant WindowGroup-close brace. Resolved in one edit; final build is BUILD SUCCEEDED.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- **15-04 can build the lane stack against a known-good inset mechanism** (`.chartYAxis(.hidden)` + unit-in-title) instead of discovering it under the full assembly. The central A2 risk is retired.
- The probe file (`_LaneAlignmentProbe.swift`) and the DEBUG `-probe-lane-alignment` host in `EndoAppApp.swift` are THROWAWAY — 15-04 must delete both when it builds the real `ExploreLaneChart`/`ExploreLanesView`.
- No EXPL requirement is marked complete (this is a prototype); 15-04 implements EXPL-03/04/05 for real.

---
*Phase: 15-explore-tab-overlay-lanes*
*Completed: 2026-06-15*

## Self-Check: PASSED

- FOUND: Sources/EndoApp/Features/Explore/_LaneAlignmentProbe.swift
- FOUND: Sources/EndoApp/App/EndoAppApp.swift
- FOUND: .planning/phases/15-explore-tab-overlay-lanes/15-01-SUMMARY.md
- FOUND: /tmp/e2e/15-lane-alignment.png
- FOUND commit: 20237de (Task 1)
- FOUND commit: f6bf38b (Task 2)
