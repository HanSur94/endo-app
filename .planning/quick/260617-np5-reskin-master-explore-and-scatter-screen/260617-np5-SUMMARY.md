---
phase: quick-260617-np5
plan: 01
subsystem: Explore feature (lanes + scatter)
tags: [reskin, bloom, restyle-only, explore, scatter, swiftui, swift-charts]
requires:
  - Bloom design tokens (Sources/EndoApp/DesignSystem/BloomColors.swift, BloomComponents.swift, BloomTypography.swift)
  - EndoInsights ExploreSeries catalog (scaleKind taxonomy)
provides:
  - Bloom-skinned Explore lanes + scatter screens (visual parity with Insights/Home)
affects:
  - Sources/EndoApp/Features/Explore/* (7 view files, colour/layout modifiers only)
tech-stack:
  added: []
  patterns:
    - "Chart mark recolour via .chartForegroundStyleScale(domain:[displayName], range:[bloomHue]) keeping .foregroundStyle(by:) encoding"
    - "Feature-private bloomColor(for:) switching on ExploreSeries.scaleKind (no new BloomColors token)"
key-files:
  created: []
  modified:
    - Sources/EndoApp/Features/Explore/ExploreView.swift
    - Sources/EndoApp/Features/Explore/SeriesPickerView.swift
    - Sources/EndoApp/Features/Explore/ScatterAxisPicker.swift
    - Sources/EndoApp/Features/Explore/ExploreLaneChart.swift
    - Sources/EndoApp/Features/Explore/EventLaneChart.swift
    - Sources/EndoApp/Features/Explore/ExploreLanesView.swift
    - Sources/EndoApp/Features/Explore/ScatterPlotView.swift
decisions:
  - "Recoloured chart marks with .chartForegroundStyleScale rather than replacing .foregroundStyle(by:) — preserves the legend + per-mark a11y value encoding the honesty contract depends on."
  - "Per-series Bloom hue switches on the explicit ScaleKind enum so it can never drift from the catalog taxonomy; helper kept private to each chart (no new public token)."
  - "Did NOT wrap the scatter in .bloomCard() — left it on Color.canvas to avoid any risk to the explore.scatter.plot hit-testing / caption layout (plan's preferred-safe option)."
metrics:
  duration: ~25m
  completed: 2026-06-17
---

# Phase quick-260617-np5 Plan 01: Reskin Explore + Scatter to Bloom Summary

RESTYLE-ONLY reskin of master's Explore feature (lanes overlay + scatter + axis/series pickers) to the Bloom design system — warm canvas, serif title, Bloom pills/cards, plum selection tint, Bloom-palette chart marks — with zero behaviour, data, honesty, accessibility-id, or test changes.

## What was done

**Task 1 — shell + pickers (commit `c8f8aca`):**
- `ExploreView`: `.background(Color.canvas)` + `.scrollContentBackground(.hidden)` on the ScrollView; in-content `Text("Explore").bloomTitle()` header; `.navigationTitle("")` + `.navigationBarTitleDisplayMode(.inline)`; `.tint(Color.plum)` on the mode segmented Picker; honest-state icon/body recoloured to `Color.inkSecondary`.
- `SeriesPickerView`: chips restyled to Bloom — selected fill `Color.petal`, unselected `Color.surface`, stroke `isSelected ? Color.plum : Color.strokeSubtle`, text `isSelected ? Color.plum : Color.ink`. Kept BOTH shape signals (`checkmark.circle.fill`/`plus.circle`), the weight signal, and the `xmark` remove glyph (colour-independent selected cue preserved). capHint → `Color.inkSecondary`; range Picker `.tint(Color.plum)`.
- `ScatterAxisPicker`: field fill `Color.surface` + `Color.strokeSubtle` hairline overlay; axis letter/value/chevron → `inkSecondary`/`inkMuted`/`ink`.

**Task 2 — lane charts (commit `d53066e`):**
- `ExploreLaneChart` + `EventLaneChart`: added `.chartForegroundStyleScale(domain: [series.displayName], range: [bloomColor(for: series)])` to recolour the LineMark/PointMark/RectangleMark marks WITHOUT removing the `.foregroundStyle(by: .value("Series", series.displayName))` form — the legend + per-mark a11y `.value("Series", …)` encoding survives intact.
- Per-series Bloom hue via a `private func bloomColor(for:)` switching on `ScaleKind`: ordinal0to10 (severity/pain/stress) → rose, continuousHours (sleep) → indigo, ordinalBleeding → plum, ordinal1to5 (mood) → gold, continuousMinutes (activity) → teal, presenceEvent → plum. No new BloomColors token.
- Scrub `RuleMark` chrome recoloured `Color.secondary.opacity(0.5)` → `Color.inkMuted.opacity(0.5)` (stays distinct from data marks; `.zIndex(-1)` kept).
- `ExploreLanesView`: lane titles → `Color.inkSecondary`; multi-series tooltip moved off `Color(.secondarySystemBackground)` onto `Color.card` + `Color.strokeSubtle` hairline overlay; tooltip header/lines → `inkSecondary`/`ink`. The whole `scrubAccessibilityProxy`, the `.contain`/`.combine` boundaries, the `.task(id:)` seam, and every `explore.scrub.tooltip…` id untouched.

**Task 3 — scatter plot (commit `b1cabf1`):**
- `ScatterPlotView`: PointMark `.foregroundStyle(Color.accentColor)` → `.foregroundStyle(Color.plum)` (single hue; `.symbolSize(by:)` count-encoding untouched — count is size, never colour). axisTitles/caption/summary captions → `Color.inkSecondary`. The nil-domain honest-absence guard and all four scatter ids kept byte-for-byte. No `.bloomCard()` added (avoids E2E risk).

## Verification

- `xcodegen generate` — clean.
- **CLEAN build** to a fresh derived-data path (`build/explore-reskin-dd`): `** BUILD SUCCEEDED **` (Swift 6 / iOS 18, no new dependencies). Re-confirmed after the full 7-file reskin.
- No Explore file references `Color(.secondarySystemBackground)` or `Color.accentColor`; Bloom tokens present at every converted site.
- Both lane charts carry `.chartForegroundStyleScale` AND still carry `.foregroundStyle(by:`; scatter mark is a single `Color.plum`.
- Every `explore.*`/`scatter` a11y id grep-survives byte-for-byte (no id added/removed/renamed); `.contain`/`.combine`/`.ignore` boundaries intact.
- **ExploreUITests: 8/8 passed.** **ScatterUITests: 7/8 passed** — every `explore.*`/`scatter` a11y assertion green.
- **EndoInsights package: 156 tests, 0 failures** (swift test — untouched).
- **CopyAuditTests + ExploreCopyAuditTests: green** (untouched; no `ExploreCopy` string edits).

## Deviations from Plan

None — plan executed exactly as written. (Optional honest-state `inkSecondary` parity in `ExploreView.honestState` was applied; the scatter `.bloomCard()` was deliberately NOT applied, both per the plan's stated options.)

## Environmental / Pre-existing Test Failure (not a regression)

`ScatterUITests.testCorrelationCardDeepLinkPrefillsScatter()` failed on:
`XCTAssertTrue failed - The stress→pain correlation card must render (insights.card.correlation.stress.high.symptom.pelvicPain.evidence)`.

This is an **Insights-dashboard correlation-card precondition**, not an `explore.*`/`scatter` assertion, and lies entirely outside this plan's change surface (this reskin never touched `InsightsView`, `InsightCardView`, `ScatterDeepLink`, or any seeding). Per the plan (ScatterUITests Pitfall 5 — flaky host runner), the method was re-run alone and reproduced. It was then **proven pre-existing**: checking out the seven Explore files at the pre-reskin baseline (`44d8bdc`), rebuilding, and running the same single test reproduced the IDENTICAL failure with no Bloom changes present. The seeded `stress.high→pelvicPain` correlation card intermittently fails to render in the iOS 26.5 simulator runner (a ~10s Button-existence wait times out before the dashboard finishes seeding/scrolling).

Binding proof for this restyle-only plan, as the plan specifies for environmental simulator failures: the CLEAN `** BUILD SUCCEEDED **`, the byte-for-byte a11y-id survival greps, and all 15 other Explore/Scatter assertions (every one that touches the restyled surface) passing green.

## Known Stubs

None — no hardcoded empty values, placeholders, or unwired data sources introduced. This is a colour/layout-modifier-only reskin.

## Self-Check: PASSED

Files modified (all exist):
- FOUND: Sources/EndoApp/Features/Explore/ExploreView.swift
- FOUND: Sources/EndoApp/Features/Explore/SeriesPickerView.swift
- FOUND: Sources/EndoApp/Features/Explore/ScatterAxisPicker.swift
- FOUND: Sources/EndoApp/Features/Explore/ExploreLaneChart.swift
- FOUND: Sources/EndoApp/Features/Explore/EventLaneChart.swift
- FOUND: Sources/EndoApp/Features/Explore/ExploreLanesView.swift
- FOUND: Sources/EndoApp/Features/Explore/ScatterPlotView.swift

Commits (all on `bloom-on-master`):
- FOUND: c8f8aca (Task 1 — shell + pickers)
- FOUND: d53066e (Task 2 — lane charts)
- FOUND: b1cabf1 (Task 3 — scatter plot)
