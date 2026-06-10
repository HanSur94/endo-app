---
phase: 08-insights-integration-coordinator-cached-insights-dashboard
plan: 03
subsystem: ui
tags: [swiftui, swift-charts, accessibility, voiceover, copy-audit, insights, ios]

# Dependency graph
requires:
  - phase: 08-01
    provides: "Insight Codable/Hashable value type + CachedInsight @Model + Repository CRUD"
  - phase: 08-02
    provides: "@Observable @MainActor InsightsCoordinator (insights/isUpdating)"
provides:
  - "ChartSeriesGrouping — pure Foundation gap helper grouping contiguous logged-day runs into distinct chart series (absence != 0, proven without rendering)"
  - "InsightTrendChart — Swift Charts line view on an absolute-date X-axis with honest gaps, per-mark a11y, text summary, Reduce-Motion handling"
  - "InsightCardView — kind-agnostic card (headline / plain-language confidence pill / detail / supporting data / basis note / non-destructive dismiss)"
  - "InsightsCopy — auditable chrome-copy surface scanned by CopyAuditTests"
  - "Extended CopyAuditTests — forbidden-verb + gendered-token + numeric-confidence scans over InsightsCopy"
affects: [08-04, dashboard, insights-view, tab-insights]

# Tech tracking
tech-stack:
  added: ["Swift Charts (Apple first-party framework, no SPM dependency)"]
  patterns:
    - "Pure value-in/value-out helper for chart gap logic so honesty is unit-provable without rendering"
    - "Distinct chart `series` per contiguous run = honest line breaks (RESEARCH Pitfall 1)"
    - "Auditable chrome-copy enum (clone of CycleSummaryCopy) scanned by a substring ClaimsPolicy audit"
    - "Color-independent confidence pill: text word + per-level SF Symbol shape (never hue alone)"
    - "Dismiss as a non-destructive preference: swipe + contextMenu + VoiceOver custom action via onDismiss closure"

key-files:
  created:
    - "Sources/EndoApp/Features/Insights/ChartSeriesGrouping.swift"
    - "Sources/EndoApp/Features/Insights/InsightTrendChart.swift"
    - "Sources/EndoApp/Features/Insights/InsightsCopy.swift"
    - "Sources/EndoApp/Features/Insights/InsightCardView.swift"
    - "Tests/EndoAppTests/ChartSeriesGroupingTests.swift"
  modified:
    - "Tests/EndoAppTests/CopyAuditTests.swift"

key-decisions:
  - "ChartSeriesGrouping kept Foundation-only (no SwiftUI/Charts) so absence!=0 is provable without rendering"
  - "Run.id is the run ordinal — the only contract the chart series needs is distinctness per run"
  - "InsightTrendChart takes asOf as an injected parameter (default .now) so the absolute-date window domain is deterministic"
  - "Empty/sparse copy adjusted from the UI-SPEC verbatim wording to pass the ClaimsPolicy SUBSTRING audit (the 'her'/'will' traps) — the audit is the honesty choke-point and wins"
  - "Confidence symbol set LOCKED to circle / circle.lefthalf.filled / circle.fill (shape carries the level for grayscale/Smart-Invert)"

patterns-established:
  - "Pattern: honest-gap chart = pure run-grouping helper + distinct per-run series + PointMark per logged day"
  - "Pattern: numeric-confidence audit — confidence words must contain no digit/%/p=/r= (D-11)"

requirements-completed: [INST-01, INST-02, INST-03, INST-04]

# Metrics
duration: 40min
completed: 2026-06-09
---

# Phase 8 Plan 03: Insights Chart + Card Components Summary

**Swift Charts trend chart with honest line breaks at every data gap (absence != 0, unit-proven without rendering), a kind-agnostic insight card with a plain-language confidence pill (text + SF Symbol, never a number, never color alone), and an auditable InsightsCopy surface scanned for forbidden verbs / gendered tokens / numeric confidence.**

## Performance

- **Duration:** 40 min (≈18 min active work; ≈22 min waiting on full-suite simulator runs)
- **Started:** 2026-06-09T10:21:42Z
- **Completed:** 2026-06-09T11:02:37Z
- **Tasks:** 3
- **Files created/modified:** 6

## Accomplishments
- `ChartSeriesGrouping`: a PURE Foundation helper splitting `[InsightPoint]` into contiguous calendar-day runs — each run becomes a distinct chart `series` so a Swift Charts `LineMark` BREAKS at every gap (RESEARCH Pitfall 1). It never fabricates a `value: 0` for an unlogged day, retains a literal logged severity-0 point, and keeps a single logged day as a run of one. Eight `@Test` cases prove all six honesty behaviors WITHOUT rendering.
- `InsightTrendChart`: the no-analog Swift Charts view — line on an ABSOLUTE calendar-date X-axis (never cycle-relative, SC3/D-06), honest gaps via the series-grouped runs, a `PointMark` per logged day, fixed Y `0...10` (matches `SeverityControl`), per-mark VoiceOver/Audio-Graph `accessibilityLabel`/`accessibilityValue`, a one-line text summary so the chart is never the sole carrier of meaning, and Reduce-Motion-aware animation.
- `InsightCardView`: kind-agnostic card with the UI-SPEC §B top→bottom structure (headline / confidence pill / detail / supporting data / basis note). The confidence pill is text word + a LOCKED per-level SF Symbol (`circle`/`circle.lefthalf.filled`/`circle.fill`) — never a number, never color alone. Dismiss is a non-destructive (gray) swipe + context-menu + VoiceOver custom action wired via an `onDismiss` closure (the view never imports the Repository). Stable a11y ids per `signalKey`.
- `InsightsCopy`: the auditable chrome-copy surface (confidence words, section headers, empty/sparse states, evidence/window labels, direction words, dismiss/error/updating copy) with an `auditableStrings` array.
- Extended `CopyAuditTests` with a forbidden-verb scan, a gendered-token scan, and a NEW numeric-confidence scan (no digit/%/p=/r= in the confidence words) over `InsightsCopy`.

## Task Commits

Each task was committed atomically:

1. **Task 1: ChartSeriesGrouping pure gap helper + tests** - `4343d37` (feat)
2. **Task 2: InsightTrendChart + InsightsCopy + CopyAuditTests** - `9c3a8f8` (feat)
3. **Task 3: InsightCardView kind-agnostic card** - `96a603d` (feat)

**Plan metadata:** see final docs commit.

## Files Created/Modified
- `Sources/EndoApp/Features/Insights/ChartSeriesGrouping.swift` — pure contiguous-run gap helper.
- `Sources/EndoApp/Features/Insights/InsightTrendChart.swift` — Swift Charts trend view (absolute-date axis, honest gaps, a11y, Reduce Motion).
- `Sources/EndoApp/Features/Insights/InsightsCopy.swift` — auditable chrome-copy surface.
- `Sources/EndoApp/Features/Insights/InsightCardView.swift` — kind-agnostic insight card with non-destructive dismiss.
- `Tests/EndoAppTests/ChartSeriesGroupingTests.swift` — 8 tests proving absence!=0 without rendering.
- `Tests/EndoAppTests/CopyAuditTests.swift` — extended with InsightsCopy verb/gendered/numeric-confidence scans.

## Decisions Made
- Kept `ChartSeriesGrouping` Foundation-only so the "absence != 0" guarantee is a pure unit test (no SwiftUI snapshot needed).
- `InsightTrendChart.asOf` is an injected parameter (default `.now`) so the absolute-date window domain is deterministic and testable.
- Confidence SF-Symbol set LOCKED to `circle` / `circle.lefthalf.filled` / `circle.fill` (shape, not hue, carries the level — T-08-11).
- The `directionWord` for the chart summary is derived from the trend's first→last logged value using the neutral words "trending up/down/steady" (never "higher"/"lower").

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Empty/sparse chrome copy tripped the ClaimsPolicy substring audit**
- **Found during:** Task 2 (InsightsCopy + CopyAuditTests)
- **Issue:** The UI-SPEC-verbatim empty/sparse strings contained substrings the enforceable `ClaimsPolicy` audit flags: "there's"/"here" contain the gendered token "her", and "this will fill in" contains the forbidden verb "will". The audit is a SUBSTRING scan and cannot distinguish "here" from "her", so the verbatim copy genuinely failed the honesty choke-point.
- **Fix:** Reworded the three strings to preserve meaning while passing the audit: emptyHeading "…once you have enough data.", emptyBody "…shows them on this screen. Nothing to see yet — keep going.", sparseSubtitle "…and this fills in over time." (documented inline in `InsightsCopy.swift`).
- **Files modified:** `Sources/EndoApp/Features/Insights/InsightsCopy.swift`
- **Verification:** `CopyAuditTests` (13 tests) GREEN — InsightsCopy passes forbidden-verb, gendered-token, and numeric-confidence scans.
- **Committed in:** `9c3a8f8` (Task 2 commit)

**2. [Rule 3 - Blocking] Type-check timeout in CopyAuditTests after adding the EndoInsights import**
- **Found during:** Task 2 (extending CopyAuditTests)
- **Issue:** Adding `import EndoInsights` pushed the batch compile of the pre-existing `allPhase3Copy` chained-`+` array over the Swift type-checker's "unable to type-check in reasonable time" limit; a keypath `contains(where:)` inside an `#expect` macro also produced a spurious "call can throw" error.
- **Fix:** Rewrote `allPhase3Copy` as explicit `+=` appends and extracted the `contains` calls into local `let` bindings before `#expect`.
- **Files modified:** `Tests/EndoAppTests/CopyAuditTests.swift`
- **Verification:** Target compiles; `CopyAuditTests` GREEN.
- **Committed in:** `9c3a8f8` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 honesty bug, 1 blocking compile). 
**Impact on plan:** Both auto-fixes necessary; no scope creep. The copy reword is the honesty choke-point working as designed (the audit catches even verbatim-spec copy).

## Issues Encountered
- The full `-only-testing:EndoAppTests` simulator run stalled in this environment (>15 min with no progress while the targeted suites each completed in ~17s). It was terminated; instead a combined run of the new + adjacent Insights suites (`ChartSeriesGroupingTests` + `CopyAuditTests` + `InsightFingerprintTests`) ran GREEN in ONE process — **27/27 tests, 3 suites, TEST SUCCEEDED** — confirming no cross-suite interaction. Each task's required `<verify>` also passed in isolation, and `xcodebuild build` reports BUILD SUCCEEDED. The known Phase-3 medication-toggle UI flake lives in the separate `EndoAppUITests` target and is out of scope (this plan touches no medication code).

## Verification Summary
- `xcodebuild test … -only-testing:EndoAppTests/ChartSeriesGroupingTests` → 8/8 GREEN (absence!=0 proven without rendering).
- `xcodebuild test … -only-testing:EndoAppTests/CopyAuditTests` → 13/13 GREEN (InsightsCopy passes verb + gendered + numeric-confidence scans).
- Combined `ChartSeriesGrouping + CopyAudit + InsightFingerprint` suites in ONE process → 27/27 GREEN, TEST SUCCEEDED (no cross-suite interaction).
- `xcodebuild build -scheme EndoApp` → BUILD SUCCEEDED (chart + card views compile).
- Deferred (D-16, phase gate): Swift Charts VISUAL quality (gap rendering, Dynamic Type, dark mode) via simulator screenshots + `/gsd-ui-review` in 08-04.

## Next Phase Readiness
- 08-04 can compose `InsightCardView` into the Trends / Cycle-phase / Possible-associations `Section`s, wire `onDismiss` to `Repository.dismissInsight(signalKey:)`, read `coordinator.insights`/`isUpdating`, and add the `tab.insights` entry. The chart + card + copy pieces are unit-tested and build-clean.

## Self-Check: PASSED

All 6 created/modified files present on disk; all 3 task commits (`4343d37`, `9c3a8f8`, `96a603d`) present in git history.

---
*Phase: 08-insights-integration-coordinator-cached-insights-dashboard*
*Completed: 2026-06-09*
