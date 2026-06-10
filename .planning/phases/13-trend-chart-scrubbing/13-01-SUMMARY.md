---
phase: 13-trend-chart-scrubbing
plan: 01
subsystem: insights-charts
tags: [swift, swift-testing, endoinsights, swift-charts, copy-audit, chart-scrubbing]

# Dependency graph
requires:
  - phase: 08-insights-integration-coordinator-cached-insights-dashboard
    provides: InsightPoint (EndoInsights), InsightsCopy + auditableStrings + CopyAuditTests scans, ChartSeriesGrouping playbook
provides:
  - ChartScrubSelection pure resolver (EndoInsights) — raw scrub Date → exact logged-day-or-nil; the CHART-02 honesty choke-point Phases 15–16 reuse unchanged
  - ChartScrubSelectionTests — 7 unit-pinned CHART-01/02 invariants (nil input, exact match, gap→nil, severity-0→0, 23:59 boundary, Europe/Berlin DST, unsorted input)
  - InsightsCopy.scrubNotLogged + scrubSeverity(_:) — audited LOCKED tooltip strings, registered in auditableStrings + pinned verbatim in CopyAuditTests
affects: [13-02, 13-03, 15-explore, 16-scatter]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Exact-day-match resolver: calendar.startOfDay + isDate(_:inSameDayAs:) only — never epoch math, never min(by:)/nearest-neighbor (grep-gated)"
    - "Copy registration in the SAME edit that introduces the string (RESEARCH Pitfall 5), with a representative call for parameterized strings (scrubSeverity(6))"

key-files:
  created:
    - EndoInsights/Sources/EndoInsights/ChartScrubSelection.swift
    - EndoInsights/Tests/EndoInsightsTests/ChartScrubSelectionTests.swift
  modified:
    - Sources/EndoApp/Features/Insights/InsightsCopy.swift
    - Tests/EndoAppTests/CopyAuditTests.swift

key-decisions:
  - "Resolver placed in the EndoInsights package (not the app target): InsightPoint is a package type, Phases 15–16 consume it, no xcodegen churn, BoundaryLintTests enforces Foundation-only automatically (RESEARCH Pitfall 7 correction of CONTEXT's playbook note)"
  - "Resolution gained explicit public init(day:point:) so the app target can construct it if ever needed — public struct members alone are not enough across module boundaries"
  - "Pin test also locks scrubSeverity(0) == \"Severity 0 of 10\" — the severity-0 ≠ gap distinction is pinned at the copy layer, not just the resolver layer"

patterns-established:
  - "ChartScrubSelection.resolve is the ONLY legal path from a raw gesture Date to a displayed value — Plans 02/03 and Phases 15–16 must route through it"

requirements-completed: [CHART-01, CHART-02]

# Metrics
duration: 5min
completed: 2026-06-10
---

# Phase 13 Plan 01: Scrub Foundations Summary

**Pure exact-day ChartScrubSelection resolver in EndoInsights (gap→nil, severity-0→real 0, DST-safe) plus the two audited LOCKED tooltip strings ("Not logged" / "Severity {n} of 10") registered and verbatim-pinned before any view renders them**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-06-10T17:49:44Z
- **Completed:** 2026-06-10T17:54:29Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments

- **CHART-02's honesty core is unit-proven before any pixel exists:** `ChartScrubSelection.resolve(_:in:calendar:)` resolves a raw scrub `Date` via `calendar.startOfDay` + `isDate(_:inSameDayAs:)` exact match only — a gap day yields `Resolution(day:, point: nil)` (never a neighbor, never a fabricated value), a logged severity-0 day yields a real point with value 0 (absence ≠ 0). Nested `Resolution` is `Sendable, Equatable, public`.
- **7 invariant tests pin the contract** (Swift Testing, fixed-UTC fixture cloning `ChartSeriesGroupingTests`): nil input, exact match, gap→nil-never-nearest, severity-0→0-not-nil, 23:59 same-day boundary, Europe/Berlin 2026-03-29 spring-forward, unsorted input. Tests were written FIRST and observed RED (resolver absent) before the implementation landed.
- **The grep gate holds:** zero `min(by`/`sorted(by…distance`/`timeIntervalSince` constructs in the resolver source (RESEARCH Pitfall 1 warning signs).
- **The two LOCKED tooltip strings are audited before any view exists:** `scrubNotLogged = "Not logged"` and `scrubSeverity(_:) = "Severity {n} of 10"` registered in `auditableStrings` in the same edit (representative `scrubSeverity(6)` call, the `supportingEvidence(loggedDays: 12)` precedent) — all four `insightsCopy_*` scans pass, plus a new verbatim pin test (including `scrubSeverity(0)`, the severity-0 ≠ gap copy distinction).

## Task Commits

Each task was committed atomically:

1. **Task 1: Pure ChartScrubSelection resolver + invariant test suite** - `2b3ea34` (feat, TDD: RED observed via compile failure of the tests-first file, then GREEN — committed together as one atomic pair per the plan's co-created-pair instruction)
2. **Task 2: Audited tooltip copy — scrubNotLogged + scrubSeverity registered** - `9ecf934` (feat)

## Files Created/Modified

- `EndoInsights/Sources/EndoInsights/ChartScrubSelection.swift` - The pure raw-date → logged-day-or-nil resolver (Foundation-only; the honesty choke-point)
- `EndoInsights/Tests/EndoInsightsTests/ChartScrubSelectionTests.swift` - 7 unit-pinned CHART-01/02 invariants
- `Sources/EndoApp/Features/Insights/InsightsCopy.swift` - `scrubNotLogged` + `scrubSeverity(_:)` + same-edit `auditableStrings` registration
- `Tests/EndoAppTests/CopyAuditTests.swift` - `insightsCopy_scrubTooltipStrings_areLockedVerbatim` pin test

## Decisions Made

- Resolver lives in the EndoInsights package (RESEARCH Pitfall 7 correction): `InsightPoint` is a package type, Phases 15–16 consume the resolver, package files need no `xcodegen`, and `BoundaryLintTests` enforces Foundation-only automatically.
- Added an explicit `public init(day:point:)` to `Resolution` — `public struct` stored properties alone don't synthesize a cross-module initializer; the app target (Plan 02) and tests construct expected values.
- The pin test additionally locks `scrubSeverity(0) == "Severity 0 of 10"` so the absence ≠ 0 distinction is pinned at the copy layer too.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. (`gsd-tools` CLI was unavailable in this environment; STATE.md updates were applied by direct edit, matching prior-phase practice.)

## Verification Results

- `swift test --package-path EndoInsights --filter ChartScrubSelectionTests` — 7/7 green
- `swift test --package-path EndoInsights` — full package green: **124 tests / 14 suites** (was 117; BoundaryLintTests confirms Foundation-only holds)
- Nearest-neighbor/epoch-math grep gate on the resolver source — **0 matches (PASS)**
- `xcodebuild test … -only-testing:EndoAppTests/CopyAuditTests` (iPhone 16 simulator) — **32/32 green, TEST SUCCEEDED** (incl. the new pin test; both strings pass forbidden-verb, gendered-token, and numeric-confidence scans via registration)
- No tooltip string literal exists outside `InsightsCopy` (grep-verified; the one match is a pre-existing doc comment in `InsightTrendChart.swift`)
- Full `-only-testing:EndoAppTests` deliberately NOT run (known >15-min stall in this environment — STATE.md 08-03/08-04; targeted suites are the established pattern)

## Next Phase Readiness

- Plan 13-02 can now build the `InsightTrendChart` selection layer as pure composition: `ChartScrubSelection.resolve` is the only value-lookup path, and both tooltip strings are audited and pinned.
- Zero xcodegen churn this plan (no app-target files added) — Plan 02 modifies existing app files only; Plan 03's new UITest file is the first xcodegen-requiring change.

## Self-Check: PASSED

- All 5 created/modified files exist on disk (incl. this SUMMARY)
- Both task commits (`2b3ea34`, `9ecf934`) present in git log
- `scrubNotLogged` registered in `auditableStrings` (3 occurrences in InsightsCopy.swift: declaration + registration + doc reference)

---
*Phase: 13-trend-chart-scrubbing*
*Completed: 2026-06-10*
