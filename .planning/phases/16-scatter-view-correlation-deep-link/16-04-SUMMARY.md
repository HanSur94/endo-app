# Plan 16-04 Summary — Scatter E2E Proof & Regression Gates

**Status:** Complete (Wave 4 of 4 — the final plan of Phase 16 and of the v1.1 milestone)
**Completed:** 2026-06-17

## What shipped

1. **`-uitest-seed-scatter-link` seed** (`AppDependencies.swift`, committed `ac2fadd`) — a deterministic `stress.high → pelvicPain` QUANTITATIVE correlation (22 days, stress 8/pain 7 vs stress 2/pain 2 by parity). The engine gates ONE `.correlation` whose signalKey (`correlation.stress.high.symptom.pelvicPain`) the `ScatterDeepLink` resolver maps to two quantitative axes → the "See the days behind this" affordance APPEARS (the POSITIVE deep-link case). The repeated pairs also give the scatter DENSE cells (count-encoding). DEBUG + `-uitest-reset`-paired (in-memory only, inert in production). The shipped dairy `-uitest-seed-recommendations` stays the NEGATIVE (presence-only → affordance hidden) case.

2. **`-uitest-scatter-axes <x>,<y>` seam** (`AppDependencies.swift`, committed `cb9b663`) — a DEBUG launch-arg that PRESETS `exploreScatterRouter.pendingScatter`, reusing `ExploreView.consumePendingScatter()` (the SAME gesture-free prefill path the deep-link uses) so the scatter render/count/caption/sparse E2E never has to drive the native SwiftUI `Menu` axis picker. The project's established seam-over-gesture discipline (mirrors `-uitest-select-day`). DEBUG + `-uitest-reset`-paired.

3. **`ScatterUITests`** (`cb9b663`) — 8 gesture-free a11y-id E2E tests proving SCAT-01..04:
   - `testScatterModeRendersWithTwoAxes` — seam presets two axes → plot renders (SCAT-01).
   - `testScatterEmptyStateBeforeAxes` — scatter mode, no axes → honest "pick two factors" (SCAT-03).
   - `testScatterCountEncodedDenseCell` — dense cell + worded summary states the 22 both-logged days (SCAT-02; count, not jitter).
   - `testScatterCaptionIsNeutral` — "Based on N days when both were logged"; no %, no r-value, no "correlation" (SCAT-03).
   - `testSparseStateWhenTooFewDays` — a zero-overlap pair → honest sparse state, never a near-empty plot (SCAT-03).
   - `testSuppressedUserHasNoBleedingAxis` — a suppressed user is offered NO "Bleeding intensity" axis option (SCAT-01 / T-16-07).
   - `testCorrelationCardDeepLinkPrefillsScatter` — tap the stress→pain card's scatter-link → lands on Explore in Scatter mode, prefilled, plot rendered (SCAT-04 positive).
   - `testPresenceFactorCardHasNoScatterLink` — the dairy (presence) card renders but has NO scatter-link (SCAT-04 negative).

4. **`scatterLink` a11y-id fix** (`InsightCardView.swift`, `cb9b663`) — scoped the `.correlation` card's `.evidence` `accessibilityIdentifier` to the text-only inner block so it no longer masks the borderless scatter-link button's own `.scatterLink` id. A container `accessibilityIdentifier` was propagating to its lone interactive descendant (the button), exposing it AS `.evidence`. The affordance always RENDERED correctly; only its test-queryable id was masked — a test-visibility fix, not a feature bug.

## Verification

- **ScatterUITests: 8/8 green** (confirmed test-by-test against the flaky host; each passed in a clean run).
- **Regression:**
  - EndoInsights package **156/156** green.
  - `CopyAuditTests` + `ScatterDeepLinkTests`: **54/54** green (the new scatter copy is audit-clean; the resolver maps/nils as specified).
  - `InsightsDashboardUITests` **6/6** green — confirms the `.evidence` scoping change did NOT break the dashboard cards.
  - `ExploreUITests`: lanes render, scrub tooltip, suppressed-hides-chips, tab-after-consent all green (the `-uitest-scatter-axes` seam is additive/DEBUG-only — no lanes-mode change).
  - `ReportFlowUITests`: `testInvalidCustomRange` green. **`testGeneratePDF_fromPopulatedRange_showsPreview` could NOT be run to a clean result in this environment** — ~15 sequential attempts (incl. a full `simctl erase`) all hit the post-midnight host runner-crash / consent-launch flake, never a code assertion. The PDF render path is **provably untouched** (`git log v1.0..HEAD -- ReportPDFRenderer.swift` is EMPTY; no Phase-16 commit touches the Report path), so the regression risk is zero — carried as a host-flaky **re-run item** (the documented flaky-host posture), NOT a regression.
  - **PDF path zero-diff** since `v1.0` confirmed.

## Deviations / notes

- The 16-04 executor was interrupted by a server rate-limit after committing the seed (`ac2fadd`) + writing `ScatterUITests.swift` (uncommitted). The orchestrator finished the plan inline: built, ran the suite, root-caused two E2E failures (the SwiftUI `Menu` per-option-id non-propagation → switched to the `-uitest-scatter-axes` seam + label queries; and the `.evidence`-masks-`.scatterLink` container-id propagation → the scoping fix), and committed the completion (`cb9b663`).
- SCAT-01..04 are E2E-proven; the REQUIREMENTS.md checkboxes are left for the verifier.

## Manual-only (carried to the pre-release checklist)

- Scatter readability + count-encoding legibility on device (SCAT-01/02).
- Descriptive-only tone / no implied causation (SCAT-03).
- Colorblind-safe distinction (SCAT-01).
- `testGeneratePDF` re-run on a non-degraded host/CI (PDF code untouched).
