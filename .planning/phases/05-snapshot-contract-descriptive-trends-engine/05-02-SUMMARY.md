---
phase: 05-snapshot-contract-descriptive-trends-engine
plan: 02
subsystem: insights-engine
tags: [swift, foundation, swift-testing, tdd, pure-engine, descriptive-trends, honesty, determinism]

# Dependency graph
requires:
  - phase: 05-01
    provides: EndoInsights package + DaySnapshot/Insight contract + deterministic Insight.derived(...) id path
  - phase: 02-symptoms-pain-bleeding
    provides: CycleMath pure injected-today engine template + the suppressed short-circuit posture
provides:
  - TrendsEngine.trends(from:asOf:calendar:) — the pure descriptive-trends engine (INST-01/SC2)
  - per-symptom severity-mean + frequency, stress/mood means, poor-sleep + bleeding-day frequency trends over 7/30/90/all-time windows on absolute calendar dates
  - the SC3 honesty gate (no bleeding/cycle trend under suppression) + the D-08 min-data threshold ("not enough data")
  - SnapshotFixtures DSL ([DaySnapshot] builders incl. suppressedNoBleedingFixture) for all downstream insights phases
  - the dedicated D-12 BoundaryLintTests (strict Foundation-only import lint, Form A)
affects: [06-cycle-phase, 07-correlations, 08-dashboard, 11-recommendations, 12-flare]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure descriptive-trends engine mirroring CycleMath: enum namespace, injected today+calendar, Equatable outputs, no Date.now"
    - "Honesty invariants as named-constant gates: minLoggedDays threshold + !suppressed && bleedingDayCount>=1 + nil-excluded-from-means"
    - "Direction as a coarse recent-vs-prior-equal-window comparison with a steady band (no regression/smoothing — avoids false precision)"
    - "Mean encoded as the trailing InsightPoint of a severity/scale trend so fixtures assert it exactly"

key-files:
  created:
    - EndoInsights/Sources/EndoInsights/TrendsEngine.swift
    - EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift
    - EndoInsights/Tests/EndoInsightsTests/TrendsEngineTests.swift
    - EndoInsights/Tests/EndoInsightsTests/SuppressedFixtureTests.swift
    - EndoInsights/Tests/EndoInsightsTests/BoundaryLintTests.swift
  modified:
    - EndoInsights/Tests/EndoInsightsTests/ContractTests.swift

key-decisions:
  - "minLoggedDays = 3 (a named, unit-asserted constant) — mirrors CycleMath's '>= 2 cycles' honesty spirit"
  - "Frequency trends gate on days-with-any-log >= minLoggedDays (enough tracked days), NOT days-present — so a legitimate '2 of 4' frequency emits while a sparse window surfaces 'not enough data'"
  - "directionBand = 0.5 (in the signal's units); allTime omits direction entirely (no prior window)"
  - "Bleeding trend gated on !anySuppressed && bleedingDayCount >= 1 — the SC3 short-circuit mirroring CycleMath's if isSuppressed"
  - "BoundaryLint Form A (#filePath) used; the duplicate lint was removed from ContractTests so BoundaryLintTests is the single source of truth (and asserts strict Foundation-ONLY, stronger than the prior forbidden-set check)"

requirements-completed: [INST-01]

# Metrics
duration: ~18min
completed: 2026-06-08
---

# Phase 5 Plan 02: Descriptive TrendsEngine Summary

**A pure, deterministic, Foundation-only `TrendsEngine.trends(from:asOf:)` that turns `[DaySnapshot]` into descriptive `[Insight]` — per-symptom severity-mean + frequency, stress/mood means, poor-sleep + bleeding-day frequency over 7/30/90/all-time absolute-date windows — with the endo honesty invariants (absence ≠ 0, no phantom bleeding/cycle under suppression, no false precision) locked by Swift Testing fixtures and the D-12 boundary lint.**

## Performance

- **Duration:** ~18 min
- **Tasks:** 2 (TDD: RED → GREEN)
- **Files created/modified:** 6 (5 created, 1 modified)

## Accomplishments

- **The core Phase-5 deliverable (INST-01/SC2):** `TrendsEngine` is a pure enum-namespace engine mirroring `CycleMath` — injected `today` + `calendar`, no `Date.now`/randomness, `Equatable` `[Insight]` outputs. Windows are anchored on **absolute calendar dates** (`[today-(N-1) … today]` inclusive, DST-safe `calendar.date(byAdding:.day,...)` math — never epoch arithmetic), NOT cycle phase.
- **Descriptive trends (D-05/D-07):** per-symptom severity mean (nil severities excluded) + frequency (`days-present / days-with-any-log`), stress/mood means, poor-sleep-day frequency, and bleeding-day frequency — all descriptive, non-causal copy.
- **The honesty invariants locked by tests:**
  - **SC3 keystone:** the `suppressedNoBleedingFixture` (4 months of `suppressed == true`, `flow == nil`) yields **NO** bleeding/cycle/period Insight and **NO** `.cyclePhase` kind — gated on `!anySuppressed && bleedingDayCount >= 1`, mirroring `CycleMath.summarize`'s `if isSuppressed`.
  - **absence ≠ 0:** an unlogged stress day is excluded from the stress-mean denominator (mean 4.0, not 2.0), distinct from three logged-0 days (mean 0.0). A nil severity is excluded from a severity mean (mean 6.0, not 4.5).
  - **no false precision (D-08):** below `minLoggedDays = 3` tracked days, the engine emits an explicit "not enough data yet" Insight (`.none` confidence) instead of a misleading trend.
- **Direction (D-07):** coarse `.up`/`.down`/`.steady` comparing the recent window against the immediately-prior equal-length window (`[today-(2N-1) … today-N]`), with a `directionBand` of 0.5 for steady; `allTime` omits direction (no prior window).
- **D-12 boundary lint:** a dedicated `BoundaryLintTests` (Form A, `#filePath` source scan) asserts every `EndoInsights/Sources` import is **Foundation only**; the Form-B grep guard is also clean.
- **No regression:** full app+package suite GREEN — 87 app unit tests (18 suites) + 22 package tests (5 suites). (One pre-existing flaky UI test recurred under full-suite load and passed in isolation — see Issues.)

## Task Commits

1. **Task 1: Fixture DSL + RED engine/honesty/SC3 specs** — `721189d` (test) — fixtures compile, specs fail RED on `TrendsEngine` absence.
2. **Task 2: Implement TrendsEngine (GREEN) + the D-12 boundary lint** — `ce1a2dd` (feat) — all 22 EndoInsightsTests pass.

## Trend-math specifics (for Plan 03 / Phases 6–12)

- **Min-data threshold:** `TrendsEngine.minLoggedDays = 3` (named, tunable, unit-asserted). Frequency trends gate on **days-with-any-log ≥ 3**; severity/scale means gate on **non-nil readings ≥ 3**. Sub-threshold → an explicit "not enough data yet" Insight (`signalKey` suffix `.insufficient`, confidence `.none`).
- **Direction band + allTime:** `directionBand = 0.5` in the signal's units; `.up` when `recent − prior > band`, `.down` when `< −band`, else `.steady`. `allTime` has no prior window → direction omitted from copy.
- **Copy style:** descriptive, non-causal, present/past tense — e.g. "Pelvic pain severity averaged 6.0 in the last 7 days", "You logged pelvic pain on 2 of the 4 days you tracked in the last 7 days", "…higher than the prior period". No "trigger/causes/cause/will" (asserted across all emitted insights).
- **Confidence:** descriptive from logged-day count (`< 3 → none`, `3–6 → low`, `7–13 → medium`, `≥ 14 → high`) — the *formal* gating/withholding layer is Phase 6 (D-16).
- **Mean encoding:** a severity/scale trend's mean is the **trailing `InsightPoint.value`** so fixtures assert it exactly while the leading points carry the per-day series for Phase-8 charts.
- **BoundaryLint:** **Form A** (`#filePath`) — `#filePath` resolved cleanly at test time (no flakiness), so Form B was not needed in-suite (kept as a CI grep guard).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Frequency-trend threshold gated on the wrong count**
- **Found during:** Task 2 (GREEN) — the `symptomFrequency_isDaysPresentOverDaysWithAnyLog` test (a legitimate "2 of 4" frequency) failed because the frequency trend was gated on `daysPresent >= minLoggedDays`, which makes any "2 of N" frequency impossible to emit.
- **Issue:** the D-08 threshold is "≥ N **logged** days in the window" (enough tracked data overall), not "≥ N days the signal was **present**". Gating on days-present both suppressed valid low-frequency trends AND would have let a window with plenty of present days but little other data emit.
- **Fix:** gate frequency trends on `daysWithAnyLog >= minLoggedDays`. The sub-threshold fixture (2 total logged days) still correctly surfaces "not enough data"; the "2 of 4" fixture now emits.
- **Files modified:** `EndoInsights/Sources/EndoInsights/TrendsEngine.swift`
- **Commit:** `ce1a2dd`

### Within-scope cleanup

**2. [Rule 3 - Blocking] Argument-order compile errors in the fixture builder**
- The `snapshot(...)` builder declares `symptoms` before `stress`; three call sites passed `stress:` first. Reordered to match the signature so the RED suite compiled (and failed solely on `TrendsEngine` absence, the intended RED state).
- **Files modified:** `EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift` — folded into `721189d`.

**3. [Rule 3 - Consistency] De-duplicated the D-12 boundary lint**
- Plan 05-01 had shipped a `packageSourcesImportOnlyFoundation` `@Test` inside `ContractTests`. Plan 05-02 adds a dedicated `BoundaryLintTests` (the plan's named file). To avoid two lints drifting, the `ContractTests` copy was removed and replaced with a pointer comment; the dedicated test additionally asserts **strict Foundation-ONLY** imports (stronger than the prior forbidden-set check).
- **Files modified:** `EndoInsights/Tests/EndoInsightsTests/ContractTests.swift` — folded into `ce1a2dd`.

**Total deviations:** 1 auto-fixed bug (Rule 1) + 2 within-scope blocking/consistency fixes (Rule 3). No architectural changes, no scope creep beyond the plan's named files.

## Known Stubs

None. The engine is fully wired to its `[DaySnapshot]` input and emits real computed `[Insight]` values. (The app-side `Repository.daySnapshots()` mapping that feeds it is Plan 05-03, by design — the engine is pure and store-agnostic per SC1.)

## Issues Encountered

- **Pre-existing flaky UI test recurred (NOT a regression).** `EndoAppUITests/CheckInFlowUITests/testMedicationTaken_persistsAcrossReopen` failed once under full-suite load (a medication-toggle XCUITest tap/read timing race at `CheckInFlowUITests.swift:211`, `Optional("0") != Optional("1")`), then **passed in isolation on retry** ("TEST SUCCEEDED", 1 test, 0 failures). This plan adds only a pure Foundation-only package engine + tests — no app/UI/medication code — so it cannot be the cause. Already tracked in `deferred-items.md` (first logged in Plan 05-01); a Plan-05-02 recurrence note was appended. Out of scope.

## Next Phase Readiness

- **Plan 05-03 (Repository.daySnapshots()):** map the SwiftData `@Model` rows → `[DaySnapshot]` app-side (consent-gated, defensive decode, `EndoInsights.PainPoint` fully-qualified) and feed `TrendsEngine.trends(from:asOf:)`. The engine contract + fixtures are stable.
- **Phases 6–12:** extend `TrendsEngine` (cycle-phase, correlations) and render `[Insight]` (dashboard, recommendations, flare). The descriptive-trends + honesty-gate foundation is in place.
- No blockers.

## Self-Check: PASSED

All 5 created files + the modified `ContractTests.swift` + this SUMMARY exist on disk; both task commits (`721189d`, `ce1a2dd`) are in the git log; the full app+package suite is GREEN (87 + 22 unit tests), the SC3 fixture emits no bleeding/cycle trend, and the D-12 boundary lint passes.

---
*Phase: 05-snapshot-contract-descriptive-trends-engine*
*Completed: 2026-06-08*
