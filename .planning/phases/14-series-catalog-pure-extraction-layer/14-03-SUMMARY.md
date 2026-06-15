---
phase: 14-series-catalog-pure-extraction-layer
plan: 03
subsystem: infra
tags: [swift, swift-testing, foundation, value-types, scatter, honesty-invariants, count-encoding, endoinsights]

# Dependency graph
requires:
  - phase: 14-series-catalog-pure-extraction-layer (Plan 01)
    provides: "ExploreSeries catalog + ScaleKind.isQuantitative + quantitativeValue(from:) suppression-gated accessor + SeriesTestSupport harness"
provides:
  - "ScatterPairing enum namespace (pure, Foundation-only, no instances)"
  - "ScatterPoint value type (x: Double, y: Double, count: Int — a factual tally, NO fitted line/r-value/coefficient)"
  - "pairs(_:x:y:calendar:) -> [ScatterPoint]: both-logged-day intersection + identical-pair count-aggregation, quantitative-axis-only, sorted by (x,y)"
  - "Scatter honesty invariants unit-pinned: both-logged-only / no-imputation / no-jitter / logged-0-real / suppression-carry / determinism"
affects: [16-scatter-view]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Both-logged-day intersection via guard-let-else-continue (either factor nil excludes the day — never imputed/0-substituted)"
    - "Identical (x,y) count-aggregation via private PairKey dictionary (no jitter, no fractional fabrication)"
    - "Quantitative-axis-only precondition at the API boundary (presence factors structurally cannot be a scatter axis)"
    - "ScatterPoint carries ONLY x/y/count — statistics are unrepresentable on the type by construction (wellness-vs-medical-device line)"

key-files:
  created:
    - "EndoInsights/Sources/EndoInsights/ScatterPairing.swift"
    - "EndoInsights/Tests/EndoInsightsTests/ScatterPairingTests.swift"
  modified: []

key-decisions:
  - "ScatterPoint carries NO fitted line / r-value / coefficient / slope — only x/y/count, so a statistic is unrepresentable by the type's shape (T-14-07, enforced by construction not runtime check)"
  - "Both axes gate through quantitativeValue(from:), so the Plan-01 bleeding suppression carries through for free — a suppressed bleeding axis returns nil → no pair (T-14-02), no new suppression code"
  - "PairKey is a bit-pattern-exact Hashable on (Double, Double) — integer-valued ordinals (0…10, 1…5, 1…4) aggregate exactly; NO jitter / rounding / Double.random near the pairing logic (T-14-06)"
  - "Output sorted by (x, y) for deterministic fixtures (mirrors the Plan-02 extraction determinism contract)"

patterns-established:
  - "Both-logged-day scatter intersection: the single legal path from [DaySnapshot] to scatter cells — either-factor-nil excludes the day, identical pairs count-aggregate"
  - "Count-encoding instead of jitter: overlapping points stack via an integer tally (a fact), never spread via fabricated fractional offsets (a lie)"

requirements-completed: []  # Phase 14 carries NO direct requirement IDs. SCAT-01/SCAT-02 are DOWNSTREAM (Phase 16) requirements this plan ENABLES — left Pending; Phase 16 delivers them.

# Metrics
duration: ~4min
completed: 2026-06-15
---

# Phase 14 Plan 03: Scatter Catalog & Pure Pairing Layer Summary

**`ScatterPairing` — the both-logged-day intersection + identical-pair count-aggregation (`ScatterPoint(x:y:count:)`, a factual tally with NO fitted line/r-value/coefficient) over the Plan-01 quantitative accessors: either-factor-nil excludes a day (never imputed), identical (x,y) days stack via an integer count (never jittered), and bleeding suppression carries through for free.**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-06-15T10:05:32Z
- **Completed:** 2026-06-15T10:09:30Z
- **Tasks:** 2 (TDD: RED → GREEN)
- **Files modified:** 2 (both created)

## Accomplishments
- `ScatterPairing.pairs(_:x:y:calendar:)` returns `[ScatterPoint]` where a scatter point is ONLY a day on which BOTH factors were logged — `guard let xv, let yv else { continue }` excludes any day missing either factor (never `?? 0`/lastKnown — T-14-05 / Pitfall 1/5).
- Identical `(x, y)` day-pairs aggregate into ONE `ScatterPoint` whose `count` is a FACTUAL tally — via a private bit-pattern-exact `PairKey` dictionary, with NO `Double.random`/jitter anywhere near the pairing logic (T-14-06 / SCAT-02).
- `ScatterPoint` carries ONLY `x` / `y` / `count` — NO fitted line, NO r-value, NO coefficient, NO slope (the wellness-vs-medical-device line, T-14-07) — so a statistic is unrepresentable on the type by construction.
- Scatter axes are QUANTITATIVE-ONLY at the API boundary (`precondition(x.scaleKind.isQuantitative && y.scaleKind.isQuantitative)`) — a presence factor has no numeric value and cannot be a scatter axis (RESEARCH OQ3).
- Bleeding suppression carries through for free: both axes resolve through the Plan-01 `quantitativeValue(from:)` accessor, which already gates on `!snap.suppressed`, so a suppressed bleeding axis returns nil → no pair (T-14-02) — no new suppression code.
- A logged literal 0 on both axes is a REAL pair at (0, 0) (the accessor returns `0.0`, never dropped — Pitfall 2 carried into scatter). Output sorted by `(x, y)` for deterministic fixtures.

## Task Commits

Each task was committed atomically (TDD RED → GREEN):

1. **Task 2 fixtures (RED): failing scatter-pairing honesty fixtures** - `5c9aa21` (test)
2. **Task 1 (GREEN): ScatterPairing both-logged-only + count-aggregation** - `34a5491` (feat)

_The comprehensive Task-2 fixture file was authored first to drive the RED gate; the Task-1 source made it GREEN. No REFACTOR commit — the GREEN implementation is already minimal and clean._

## Files Created/Modified
- `EndoInsights/Sources/EndoInsights/ScatterPairing.swift` - `ScatterPairing` enum namespace + `ScatterPoint` value type (x/y/count, no statistic) + `pairs(_:x:y:calendar:)` (both-logged intersection, count-aggregation, quantitative-axis precondition, suppression-carry, sorted output) + private `PairKey`. Foundation-only.
- `EndoInsights/Tests/EndoInsightsTests/ScatterPairingTests.swift` - Six fixtures: identical-pairs→count:10, one-factor-missing→no-pair, mixed-days→only-both-logged-counted, distinct-pairs→sorted-distinct-points, logged-0-both→real-pair, suppressed-bleeding-axis→no-pair. Uses the Plan-01 `SeriesTestSupport` harness UNCHANGED.

## Decisions Made
- **ScatterPoint has no statistic field by construction** — the only legal payload is x/y/count, so "emit an r-value" is impossible to even write (T-14-07). This is the structural honesty move, mirroring how Plan-02's `occurrenceDays -> [Date]` makes 0/1 coercion unrepresentable.
- **Suppression is inherited, not re-implemented** — both axes flow through `quantitativeValue(from:)`, so the Plan-01 bleeding suppression gate carries through. A dedicated `suppressedBleedingAxis_contributesNoPair` fixture pins this carry-through rather than duplicating the gate.
- **count-aggregation, not jitter** — overlapping logged days stack via an integer tally (a fact about how many days landed on that cell), never spread via `Double.random` (which would fabricate fractional values the user never logged).
- **Authored the comprehensive test file first for the RED gate** — the plan splits source (Task 1, tdd) and fixtures (Task 2); writing all six fixtures up front gave a strong RED (cannot-find-ScatterPairing) and let one GREEN implementation satisfy both tasks' deliverables. This is the same pattern Plan 02 used.

## Deviations from Plan

None - plan executed exactly as written. The `calendar` parameter is accepted for signature-consistency with the sibling helpers (as the plan permits) and is not used for any day-normalization here — there is no epoch math and no `86400`.

## Issues Encountered
- The verification grep `grep -v '^[[:space:]]*//'` does NOT strip comment lines once `grep -n` has prefixed each line with `N:` (the `//` is no longer at the line start). Re-ran with `grep -vE '^[0-9]+:[[:space:]]*//'` to confirm all honesty-gate matches (`?? 0`, `random`/`jitter`, `coefficient`/`fitted`) live ONLY in doc-comments — the code is CLEAN. No code issue; a grep-pattern nuance.

## User Setup Required
None - no external service configuration required. Zero dependencies added (SC1 boundary intact).

## Verification Results
- `swift test --package-path EndoInsights` — **146/146 tests in 17 suites GREEN** (was 137; +6 scatter fixtures, +1 new suite). The "Scatter pairing (both-logged-only; count-encoded, never jittered or imputed)" suite passed.
- `BoundaryLintTests.packageSourcesImportOnlyFoundation` — **GREEN** (`ScatterPairing.swift` imports only Foundation; no regression).
- Honesty grep gates on `ScatterPairing.swift` (code-only, excluding doc-comments) — all CLEAN: no `?? 0`/`lastKnown` (imputation), no `random`/`jitter`, no `rValue|pValue|coefficient|slope|fitted|regression` (statistics), no `86400` (epoch arithmetic).
- TDD gate sequence in git log: `test(14-03)` 5c9aa21 (RED) precedes `feat(14-03)` 34a5491 (GREEN). ✔

## Next Phase Readiness
- **The scatter pairing layer is landed compile-clean and honesty-pinned.** Phase 16 (scatter view) composes on `ScatterPairing.pairs(...)` directly — it renders the returned `[ScatterPoint]` and reads `count` for point sizing/stacking, with the both-logged-only and no-jitter invariants already enforced in this layer (no scatter pixel can fabricate a pair).
- SCAT-01 (scatter point = a both-logged day) and SCAT-02 (identical day-pairs stack via count, no jitter) are now ENABLED but remain Pending in REQUIREMENTS.md — Phase 16 delivers the UI that satisfies them.
- No blockers. Phase 14's pure-extraction layer (Plans 01 catalog, 02 extraction, 03 scatter) is feature-complete; only Plan 04 (normalization) remains in this phase if planned.

## Self-Check: PASSED

- FOUND: EndoInsights/Sources/EndoInsights/ScatterPairing.swift
- FOUND: EndoInsights/Tests/EndoInsightsTests/ScatterPairingTests.swift
- FOUND: .planning/phases/14-series-catalog-pure-extraction-layer/14-03-SUMMARY.md
- Commits FOUND: 5c9aa21 (test/RED), 34a5491 (feat/GREEN)

---
*Phase: 14-series-catalog-pure-extraction-layer*
*Completed: 2026-06-15*
