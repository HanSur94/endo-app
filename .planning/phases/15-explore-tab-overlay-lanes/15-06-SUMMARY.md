---
phase: 15-explore-tab-overlay-lanes
plan: 06
subsystem: testing
tags: [xcuitest, e2e, explore, deterministic-seam, a11y-ids, scrub-tooltip, regression-gate, swift6]

# Dependency graph
requires:
  - phase: 15-explore-tab-overlay-lanes
    plan: 05
    provides: "ExploreView + the consent-gated third tab (tab.explore) + the suppression-filtered chip picker + range presets + empty/sparse states"
  - phase: 15-explore-tab-overlay-lanes
    plan: 04
    provides: "ExploreLanesView lane stack + the multi-series scrub tooltip a11y ids (explore.scrub.tooltip.<id>/.notLogged) + the \\.uiTestScrubDay seam"
  - phase: 13-chart-scrub
    provides: "The -uitest-reset-paired -uitest-select-day / \\.uiTestScrubDay deterministic scrub seam pattern in AppDependencies"
provides:
  - "The DEBUG -uitest-seed-explore (+ -uitest-seed-explore-suppressed) fixture seam in AppDependencies, -uitest-reset-paired (in-memory only), seeding a deterministic multi-series Explore dataset"
  - "ExploreUITests (8 cases) proving EXPL-01..07 end-to-end via a11y ids + the selection seam — no scrub-gesture coordinates"
  - "Re-verified regression surface: ReportFlowUITests + InsightsDashboardUITests + CopyAuditTests + the EndoInsights package all green; the Report/PDF render path byte-untouched across all of Phase 15"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Clone-the-scrub-seed: a new -uitest-seed-explore fixture mirrors -uitest-seed-scrub's posture (DEBUG, && uiTestReset, best-effort try?) and REUSES the shipped -uitest-select-day machinery unchanged"
    - "All-anomalies-in-last-7 seeding rule (Phase-13 carryover): every gap/zero/event lands inside the last-7 window so one -uitest-select-day resolves the SAME state in every range preset"
    - "Container-id-bleed shield: .accessibilityElement(children: .contain) on a SwiftUI container whose id would otherwise mask its descendants' a11y ids (the Phase-13 discovery, now applied to the picker + lane stack)"

key-files:
  created:
    - Tests/EndoAppUITests/ExploreUITests.swift
  modified:
    - Sources/EndoApp/App/AppDependencies.swift
    - Sources/EndoApp/Features/Explore/SeriesPickerView.swift
    - Sources/EndoApp/Features/Explore/ExploreLanesView.swift

key-decisions:
  - "Seeded scrub day = offset 1 (today-1), engineered so the multi-series tooltip exercises ALL three line kinds at once: pelvicPain LOGGED (a real readout), sleep a deliberate GAP (a per-series 'Not logged' line), dairy PRESENT (an event line)"
  - "Two scales by construction: pelvicPain severity (0..10) + sleep hours, so the lane render test proves PER-scale lanes rather than one shared axis"
  - "Suppression proven via a dedicated -uitest-seed-explore-suppressed variant (same dataset + a 'suppressed' TreatmentState) so the default seed keeps the bleeding chips visible (EXPL-02 chip/cap path) while the variant proves the hide-by-absence (T-15-11)"
  - "Sparse state driven by selecting an UNLOGGED series (activity.minutes — the seed logs no activity), an honest too-sparse signal with zero fabricated values"

# Metrics
duration: 27min
completed: 2026-06-15
---

# Phase 15 Plan 06: Explore E2E Proof & Regression Gates Summary

**The phase's Nyquist E2E gate: a DEBUG `-uitest-seed-explore` fixture seam (cloned from `-uitest-seed-scrub`, `-uitest-reset`-paired so it only ever touches the in-memory store) seeds a deterministic two-scale-plus-event Explore dataset, and `ExploreUITests` proves every EXPL-01..07 requirement behaviorally through a11y ids + the shipped `-uitest-select-day` selection seam with ZERO scrub-gesture coordinates — the consent-gated tab, chip toggle + 4-cap + suppression-hides-bleeding, per-scale quantitative lanes + an event lane, the multi-series tooltip reading a real-unit value AND a per-series "Not logged" gap line on the same day, the range-preset switch, and the honest empty/sparse states — while the PDF report path and the Insights dashboard are re-verified untouched.**

## Performance

- **Duration:** ~27 min
- **Started:** 2026-06-15T21:17Z
- **Tasks:** 3
- **Files:** 1 created, 3 modified

## Accomplishments

### Task 1 — `-uitest-seed-explore` fixture seam (AppDependencies)
- Cloned the `-uitest-seed-scrub` pattern: a `let seedExplore = …contains("-uitest-seed-explore")` flag plus a `-uitest-seed-explore-suppressed` variant, both inserted ONLY when `(seedExplore || seedExploreSuppressed) && uiTestReset` — the `&& uiTestReset` pairing is the security invariant (T-15-14): the seed can only ever touch the in-memory store, unreachable in production by construction, gated DEBUG-inert exactly as the existing seeds are.
- `seedExploreFixture(into:suppressed:)` seeds, ALL inside the last-7 window (the Phase-13 "all anomalies in last-7" rule so one `-uitest-select-day` resolves the SAME state in every range preset):
  - `symptom.pelvicPain.severity` (a 0…10 quantitative lane): offset 3 = GAP (never logged), offset 2 = logged ZERO (severity 0, a real point), the rest = the dense severity-6 spine; **offset 1 (today-1) = the seeded scrub/assertion day, severity 6**.
  - `sleep.hours` (a SECOND quantitative lane on a DIFFERENT scale): logged on the other in-window days but a deliberate GAP on offset 1 — so the scrub day shows sleep's per-series "Not logged" line ALONGSIDE pelvicPain's real readout (the multi-series gap-honest proof).
  - `food.dairy` (a presence/EVENT lane): present on offset 1 (and a couple of others) so the scrub day also carries an event "Logged" line and the event lane renders ticks.
- REUSES the existing `-uitest-select-day` / `\.uiTestScrubDay` machinery UNCHANGED (no new EnvironmentKey; `ExploreLanesView` already reads it from 15-04). The strict `yyyy-MM-dd` bounds-checked parse (V5 / T-15-15) is untouched.
- The suppressed variant additionally calls `repo.setSuppressed(true)` so the latest snapshot's `suppressed` flag drives the picker to hide both bleeding chips (T-15-11).
- **Gate `SEAM_OK`:** the `uitest-seed-explore` + `seedExploreFixture` greps pass, the `(seedExplore … && uiTestReset)` pairing grep passes, and `xcodebuild build` is **BUILD SUCCEEDED**, Swift 6 strict-concurrency clean.

### Task 2 — `ExploreUITests` (EXPL-01..07, gesture-free)
Eight XCUITest cases, each ONE seeded launch (`["-uitest-reset", "-uitest-seed-explore", "-uitest-select-day", dayArg(daysAgo:)]`), cloning the `ChartScrubUITests` harness (consent → resolve `tab.explore` with the descendants fallback → assert via a11y ids + `element.value`):

| Test | Proves |
|------|--------|
| `testExplore_tabAppearsAfterConsent_showsScreen` | **EXPL-01** — the consent-gated third tab appears after consent and shows `explore.screen` + `explore.picker` + `explore.disclaimer` |
| `testExplore_emptyState_beforeAnySelection` | **EXPL-07** — `explore.empty` ("pick a series") before any selection; no lanes |
| `testExplore_chipsToggle_capDisablesFurtherQuantitative` | **EXPL-02** — a chip toggles a lane in/out; at the 4-quantitative cap `explore.picker.capHint` shows and a 5th unselected quantitative chip (`activity.minutes`) is `.isEnabled == false` |
| `testExplore_suppressed_hidesBleedingChips` | **EXPL-02 / T-15-11** — the suppressed-seed variant offers a non-bleeding chip but NO `explore.chip.bleeding.intensity` / `.bleeding.event` (hide-by-absence) |
| `testExplore_lanesRender_perScaleQuantitative_andEvent` | **EXPL-03/04** — `explore.lane.symptom.pelvicPain.severity` + `explore.lane.sleep.hours` (two scales) + `explore.lane.food.dairy.event` (an event lane, not a line) |
| `testExplore_scrubTooltip_readsRealValue_andGapNotLogged` | **EXPL-05** — the seam-set day yields `explore.scrub.tooltip`; the pelvicPain line `.value` reads "6 of 10"; the sleep line carries `…sleep.hours.notLogged` reading "Not logged" (a real readout AND a per-series gap line on the SAME day); the logged series carries NO `.notLogged` marker |
| `testExplore_rangePreset_switchesWindow` | **EXPL-06** — tapping `explore.range.last7` re-renders the lanes |
| `testExplore_sparseState_forTooSparseSelection` | **EXPL-07** — selecting an unlogged series (`activity.minutes`) shows `explore.sparse`, no lanes |

- **Gate `E2E_OK`:** `xcodegen generate` run; the `tab.explore` / `uitest-seed-explore` / `explore.scrub.tooltip` / `explore.empty|sparse` greps pass; `-only-testing:EndoAppUITests/ExploreUITests` → **TEST SUCCEEDED**, 8/8 passed, 0 failures.

### Task 3 — Regression re-verify (PDF path + dashboard untouched)
- **Report/PDF path byte-untouched:** `git status --porcelain Sources/EndoApp/Features/Report` is clean; NO 15-06 commit touched it; the last commit to `ReportPDFRenderer.swift` is a Phase-10 `fix(10)` (2026-06-09) — untouched across ALL of Phase 15 (T-15-16).
- **Regression suites all green** (`REGRESSION_OK`): `ReportFlowUITests` 2/2, `InsightsDashboardUITests` 6/6, `CopyAuditTests` 8/8 (the explore-copy suite still green now that the views render the strings) — **TEST SUCCEEDED**. The `EndoInsights` package suite: **156 tests passed** in 18 suites.
- No code changes in this task — no regression surfaced.

## How It Works

- **Deterministic, gesture-free (Pitfall 4):** the E2E never scrubs by coordinate. Selecting the seeded chips renders `ExploreLanesView`, whose shipped `.task(id: uiTestScrubDay)` sets `selectedDate` to the seam-injected day (guarded by `xDomain.contains` + `selectedDate == nil`). The tooltip then renders and its per-series proxy elements expose the readout via `accessibilityValue` — asserted through `element.value`.
- **In-memory-only by construction (T-15-14):** the seed runs only under `(seedExplore || seedExploreSuppressed) && uiTestReset`; `-uitest-reset` forces the in-memory store, so the fixture can never reach a real local/CloudKit store, and the whole branch is DEBUG-inert in release.
- **Two scales + an event + a gap + a logged-0 + a known scrub day:** the single fixture exercises per-scale lanes (EXPL-03), an event lane (EXPL-04), the multi-series tooltip's readout AND gap line together (EXPL-05), and (with pelvicPain's logged-0 day) the absence-≠-zero honesty — all from one deterministic window.

## Verification

- `SEAM_OK` (Task 1): grep gates + `BUILD SUCCEEDED`, Swift 6 clean.
- `E2E_OK` (Task 2): `xcodegen generate` + grep gates + `-only-testing:EndoAppUITests/ExploreUITests` **TEST SUCCEEDED** (8/8).
- `REGRESSION_OK` (Task 3): Report path git-clean (whole-phase); `ReportFlowUITests` + `InsightsDashboardUITests` + `CopyAuditTests` **TEST SUCCEEDED**; `EndoInsights` package 156 tests passed.

## Requirements

EXPL-01, EXPL-02, EXPL-03, EXPL-04, EXPL-05, EXPL-06, EXPL-07 are now **E2E-proven** by `ExploreUITests` (the table above maps each test → requirement). Per the execution context, the REQUIREMENTS.md checkboxes are LEFT for the verifier to flip — this plan records the behavioral proof only.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Container-id bleed masked the documented per-chip and per-lane a11y ids**
- **Found during:** Task 2 (first E2E run — all chip-dependent tests failed; a live element-tree dump showed every chip Button resolving to `identifier: 'explore.picker'`, and every lane Chart masked by `explore.lanes`).
- **Issue:** A SwiftUI container's `.accessibilityIdentifier` bleeds onto and MASKS its descendants' ids. `SeriesPickerView`'s `explore.picker` FlowLayout masked every `explore.chip.<series.id>`; `ExploreLanesView`'s `explore.lanes` VStack masked every `explore.lane.<series.id>`(`.event`). The shipped 15-04/15-05 a11y-id contract was therefore untargetable by the documented ids — a real defect in the shipped accessibility tree, not just a test problem.
- **Fix:** Added `.accessibilityElement(children: .contain)` to the picker FlowLayout and the lanes VStack — the SAME id-bleed shield `ExploreLanesView` already applies to its scrub-tooltip proxy (the Phase-13 discovery). The per-chip and per-lane ids now survive the container boundary.
- **Files modified:** `Sources/EndoApp/Features/Explore/SeriesPickerView.swift`, `Sources/EndoApp/Features/Explore/ExploreLanesView.swift`
- **Verification:** all 8 `ExploreUITests` pass; the 15-05 privacy grep gate (no `@AppStorage`/`@SceneStorage`) and the 15-04 honesty grep gates still pass; build Swift 6 clean.
- **Committed in:** `6963ed4`

**Total deviations:** 1 (Rule 1 — bug fix to the shipped a11y-id contract, within the planned E2E task). No scope creep, no new dependencies, no new health-data surface, no architectural change.

## Known Stubs

None. The seed fixture writes real logged values through the live Repository CRUD; the test asserts against the rendered a11y tree. The empty/sparse states are HONEST EXPL-07 signals (the plan's explicit design), not stubs.

## Threat Flags

None. No new network endpoint, auth path, or trust-boundary surface. The new seam is a DEBUG, `-uitest-reset`-paired, in-memory-only test fixture (T-15-14), reuses the existing strict date parse unchanged (T-15-15), and the Report/PDF path is byte-untouched (T-15-16). E2E runs simulator + seeded in-memory only — no real-install screenshots (T-15-17).

## Task Commits

1. **Task 1: `-uitest-seed-explore` seam** — `02c3a1d` (feat)
2. **Task 2: ExploreUITests + the id-bleed fixes** — `6963ed4` (test)
3. **Task 3: regression re-verify** — no code commit (verification only; no regression surfaced)

## Self-Check: PASSED

(populated by the self-check step below)
