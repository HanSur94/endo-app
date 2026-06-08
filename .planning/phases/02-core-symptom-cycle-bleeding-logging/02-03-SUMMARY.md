---
phase: 02-core-symptom-cycle-bleeding-logging
plan: 03
subsystem: domain-cycle-engine
tags: [domain, pure-value-type, tdd, cycle-math, foundation-only, swift6, no-phantom-cycle]

# Dependency graph
requires:
  - phase: 02-core-symptom-cycle-bleeding-logging
    provides: "Sendable BleedingDay value snapshot + bleedingDays()/isSuppressed() value reads (Plan 02-02 read seam); RED CycleMathTests placeholders (Plan 02-01)"
provides:
  - "CycleSummary value type (isSuppressed, optional cycleDay/phase, optional nextPeriod ClosedRange<Date>, Confidence, hasEnoughData) + .suppressed/.notEnoughData statics"
  - "Confidence enum (Int-backed, Comparable, Sendable: none < low < medium < high)"
  - "CycleMath.summarize(bleedingDays:isSuppressed:today:calendar:) — pure derived-cycle engine (Foundation only, today injected)"
  - "Pure helpers: periodRuns / consecutiveGaps / medianInt / spread / confidenceLevel / predictRange"
affects: [02-04, phase-06-insights]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure value-type domain engine: Foundation-only, NO SwiftData/SwiftUI, today injected for determinism (the insights-engine seed)"
    - "No-phantom-cycle contract enforced in code: suppressed short-circuit emits NOTHING; <2 cycles -> notEnoughData; next-period ALWAYS a ClosedRange<Date> + confidence, never a hard 28-day default"
    - "Median-of-gaps cycle length (sort + middle) + spread (max-min) regularity -> confidence; range widens with spread, shrinks with confidence"
    - "Confidence Int-backed + Comparable so callers can order/degrade gracefully"

key-files:
  created:
    - Sources/EndoApp/Domain/CycleSummary.swift
    - Sources/EndoApp/Domain/CycleMath.swift
  modified:
    - Tests/EndoAppTests/CycleMathTests.swift

key-decisions:
  - "Confidence is Int-backed + Comparable (none=0..high=3) so the irregular fixture can assert confidence strictly < .high and the UI can degrade gracefully"
  - "summarize takes bleedingDays: [Date] (the plan's exact contract), not [BleedingDay] — Plan 04 maps the BleedingDay snapshot's flow days to [Date] at the Repository->engine seam"
  - "phase held always-nil this phase (field exists for Phase 6 / INST-02 named phases — Open Question 2; no contract change later)"
  - "confidence heuristic (A3, tunable): >=3 cycles & spread<=3 -> high; tight-but-few or moderate-spread -> medium; wide spread/sparse -> low. The LOCKED contract is monotonic (tight long history high; wide spread never high), not the exact thresholds"
  - "predictRange half-width = max(1, spread/2 + confidencePad) guarantees a non-degenerate range and that irregular is strictly wider than regular"

requirements-completed: [CYCL-02, CYCL-03, CYCL-04]

# Metrics
duration: ~6min
completed: 2026-06-08
---

# Phase 2 Plan 03: Pure CycleMath Engine + CycleSummary Summary

**The keystone no-phantom-cycle engine: a pure, Foundation-only `CycleMath` that derives a period as a run of consecutive bleeding days and a cycle length as the median start-to-start gap (never an assumed 28), returning a next-period RANGE + confidence that widens for irregular data — while a suppressed user emits NOTHING and a sub-2-cycle user gets "not enough data yet". `today` is injected, so every summary is deterministic and Equatable.**

## Performance

- **Duration:** ~6 min
- **Started:** 2026-06-08T09:57:23Z
- **Completed:** 2026-06-08T10:00:30Z
- **Tasks:** 2 (TDD RED -> GREEN)
- **Files created/modified:** 2 created, 1 modified

## TDD Gate Sequence

| Gate | Commit | What |
| ---- | ------ | ---- |
| RED   | `115b2b4` | `test(02-03)`: real fixture-driven CycleMathTests referencing CycleMath/CycleSummary/Confidence (not yet implemented) -> suite fails to COMPILE = RED |
| GREEN | `3a1fc69` | `feat(02-03)`: CycleSummary.swift + CycleMath.swift -> all 9 CycleMathTests pass |

No REFACTOR commit — helpers landed small, single-purpose, and independently documented; nothing to clean.

## Accomplishments
- Replaced the four symbol-free 02-01 RED placeholders with **9** real, fixture-driven Swift Testing tests (regular / irregular / suppressed / sparse + 5 edges), each over a fixed UTC calendar and an injected `today` anchor.
- Built `CycleSummary` (the UI-facing value type) + `Confidence` (Int-backed, `Comparable`, `Sendable`) with `.suppressed` / `.notEnoughData` convenience statics.
- Built `CycleMath.summarize` exactly per RESEARCH Pattern 2: (1) suppressed short-circuit -> `.suppressed`; (2) `periodRuns` (consecutive-day grouping, same-day de-dup, start-of-day normalize); (3) starts; (4) `consecutiveGaps`; (5) guard `gaps.count >= 2` else `.notEnoughData`; (6) `medianInt`; (7) `spread`; (8) `confidenceLevel`; (9) `predictRange` (ALWAYS a `ClosedRange<Date>`); (10) `cycleDay`.
- Locked the no-phantom-cycle contract (D-08/D-09/D-10): suppressed emits cycleDay/phase/nextPeriod nil + confidence `.none`; `<2` cycles emits nothing meaningful; the estimate is a range, never a hard date; **nowhere is 28 assumed**.

## Files Created/Modified
- `Sources/EndoApp/Domain/CycleSummary.swift` — `Confidence` enum + `CycleSummary` value type + `.suppressed`/`.notEnoughData`. Foundation only.
- `Sources/EndoApp/Domain/CycleMath.swift` — pure `enum CycleMath` engine + 6 independently-testable helpers. Foundation only; `today` injected.
- `Tests/EndoAppTests/CycleMathTests.swift` — RED placeholders -> 9 real GREEN fixture tests.

## Decisions Made
- **`Confidence: Int, Comparable`** so the irregular fixture asserts `confidence < .high` and the UI degrades gracefully (none<low<medium<high).
- **`summarize(bleedingDays: [Date], ...)`** — the plan's exact contract. Plan 04 maps `BleedingDay.hadFlow` days to `[Date]` at the Repository->engine seam (BleedingDay carries `{day, hadFlow}`).
- **`phase` held nil this phase** — the field exists so Phase 6 (INST-02) can populate named phases with no contract change (Open Question 2).
- **Tunable confidence heuristic (A3)** — the LOCKED behavior is monotonic (a tight, longer history is high; a wide spread is never high), not the exact thresholds. Tunable in later phases without breaking the contract the fixtures assert.

## Deviations from Plan

None — plan executed exactly as written. RED->GREEN gates present; no auto-fixes, no architectural changes, no scope creep. No package installs (pure Foundation, as the threat register's T-02-SC anticipated).

## Threat-Model Compliance
- **T-02-10 (28-day/phantom-cycle re-entry):** grep gate CLEAN — no `import SwiftData`/`import SwiftUI`, no `Date.now`/`Date()`, no `= 28`/`cycleLength`/`28` literal in either file's CODE (the only matches are comments documenting the prohibition). Cycle length is the **median of derived gaps**. Suppressed + sparse fixtures assert nothing is emitted.
- **T-02-11 (degenerate input):** empty / single-day / all-consecutive (one long run) fixtures all return `.notEnoughData` with no force-unwrap on empty collections (`compactMap(\.first)` + `guard ... let lastStart`). No crash.
- **T-02-12 (health-value logging):** CycleMath performs NO logging — pure value-in / value-out (V7). grep: no `print`/`os_log`/`Logger`/`NSLog`.

## Test Results
- `EndoAppTests/CycleMathTests` — **GREEN, 9/9** (`✔ Test run with 9 tests in 1 suite passed`):
  - `regularCycler_medianLength_sensibleRange` — hasEnoughData, cycleDay==7, range brackets lastStart+median (day 112), confidence .high, phase nil.
  - `irregularCycler_widerRange_lowerConfidence` — range strictly WIDER than the regular fixture, confidence `.low` (< .high).
  - `suppressed_emitsNothing` — cycleDay/phase/nextPeriod nil, confidence .none, hasEnoughData false, == `.suppressed`.
  - `sparse_underTwoCycles_notEnoughData` — one gap -> `.notEnoughData`.
  - Edges: `emptyInput`, `singleBleedingDay`, `consecutiveDays_collapseIntoOneRun`, `neverCycledThenSuppressed_emitsNothing`, `determinism_sameInputs_equalSummary` — all GREEN.
- **Full `EndoAppTests` suite:** 40/42 pass. The **only** 2 failures are the known downstream 02-04 RED placeholders (`BodyMapBindingTests.tappingRegion_...` SYMP-02 and `EditDeleteTests.edit_updates_delete_...` SYMP-04 — both `#expect(Bool(false))` placeholders, documented "still RED by design" in 02-02-SUMMARY). No regression introduced by this plan; every previously-GREEN suite stays GREEN.
- **Purity grep gate:** CLEAN (code imports only `Foundation`; no SwiftData/SwiftUI, no `Date.now`/`Date()`, no `28`/`cycleLength`).

## Known Stubs
- `CycleSummary.phase` is intentionally always `nil` this phase — Phase 6 (INST-02) populates named phases. Field reserved by design (Open Question 2); not goal-blocking — the cycle summary surfaces cycle-day + next-period range + confidence without it.

## Next Phase Readiness
- The pure engine is ready for Plan 02-04 to consume: map `Repository.bleedingDays()` flow days -> `[Date]`, read `isSuppressed()`, inject `today`, and render the resulting `CycleSummary` in the reserved `home.cycleSummary` slot (suppressed -> empty/no-cycle state; `!hasEnoughData` -> "not enough data yet"; otherwise cycle-day + range + confidence).
- CYCL-02/03/04 are GREEN at the logic level here; Plan 04 surfaces them in the UI + the suppressed-mode E2E.
- No blockers.

## Self-Check: PASSED

Both created files exist on disk (`Sources/EndoApp/Domain/CycleSummary.swift`, `Sources/EndoApp/Domain/CycleMath.swift`); both task commits (`115b2b4` RED, `3a1fc69` GREEN) are present in git history; CycleMathTests GREEN 9/9 verified by `xcodebuild test`.

---
*Phase: 02-core-symptom-cycle-bleeding-logging*
*Completed: 2026-06-08*
