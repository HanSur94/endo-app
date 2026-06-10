---
phase: 12-flare-early-warning
plan: 03
subsystem: insights
tags: [swift, swift-testing, xcuitest, foundation, honesty-layer, flare, insights, composition, source-scan, e2e]

# Dependency graph
requires:
  - phase: 12-flare-early-warning
    provides: "Plan 02's FlareEngine.flareWarning producer + FlareCopy/FlareCopyText mirror + drift guard (composed here); Plan 01's flare fixtures + RED testFlare_rendersFirst E2E + -uitest-seed-flare seed seam + the alarmist forbidden-term policy (turned GREEN here)"
  - phase: 11-personalized-recommendations
    provides: "the one-more-producer composition line (+ RecommendationsEngine.recommendations) + the omit-when-empty dashboard section(...) helper + the recommendation E2E swipe/robustness idioms this plan mirrors"
  - phase: 08-insights-integration-coordinator-cached-insights-dashboard
    provides: "InsightsView sections + the section(title:identifier:kind:) helper + InsightsDashboardUITests harness + InsightCardView (kind-agnostic, the .flare branch already present)"
provides:
  - "The composed InsightsEngine: FlareEngine.flareWarning routed through the SINGLE HonestyGate, public signature byte-identical (D-16), no separate gating path (D-08) — INST-06 reaches the public API"
  - "The calm, omit-when-empty .flare dashboard section placed FIRST in InsightsView (identifier insights.section.flare) — time-sensitivity by POSITION only, no alarm visuals (D-07)"
  - "FlareNoAlarmVisualTests — the #filePath source-scan asserting the flare rendering code has NO alarm affordance (checker WR-4: 'a warning that must not alarm', automated)"
  - "The package zero-forbidden-terms acceptance extended to the flare fixtures + composition acceptances (rising->one flare / suppressed-flat->zero) — GREEN"
  - "testFlare_rendersFirst E2E GREEN (seeded rising history renders one calm flare card above Trends)"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "One-more-producer composition: a new insight kind reaches the user by ONE additive term in the candidate concatenation, behind the SAME single gate — zero signature/gating change (D-08/D-16)"
    - "Position-not-paint time-sensitivity: the most time-sensitive section is placed FIRST but rendered with the IDENTICAL calm section helper + reused card — discoverability by order, never by alarm visuals (D-07)"
    - "No-alarm-visual source-scan: a #filePath scan over the rendering view files (anchored on the section id, non-empty file-set guarded) locks the calm-non-alarmist contract structurally, so a future red/glyph edit fails the build"

key-files:
  created:
    - "Tests/EndoAppTests/FlareNoAlarmVisualTests.swift"
  modified:
    - "EndoInsights/Sources/EndoInsights/InsightsEngine.swift"
    - "EndoInsights/Tests/EndoInsightsTests/InsightsEngineTests.swift"
    - "EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift"
    - "Sources/EndoApp/Features/Insights/InsightsView.swift"
    - "Tests/EndoAppUITests/InsightsDashboardUITests.swift"

key-decisions:
  - "The flare candidate is concatenated LAST in InsightsEngine (additive — candidate order does not affect gating); the dashboard places the .flare section FIRST as a pure UI concern (D-06/D-07)"
  - "The no-alarm-visual guard scans InsightsView + InsightCardView (the two flare-rendering files), anchored on the insights.section.flare string + a non-empty file-set guard so it is provably non-vacuous"
  - "The E2E section-id bleed (a SwiftUI Section .accessibilityIdentifier maps to the header StaticText AND every descendant card cell -> a multi-match .frame throws) is resolved by reading .firstMatch (the topmost/header element) before comparing vertical origins"

requirements-completed: [INST-06]

# Metrics
duration: 28min
completed: 2026-06-10
---

# Phase 12 Plan 03: Flare Early-Warning Integration & Payoff Summary

**Delivered INST-06 end-to-end: composed the FlareEngine producer into the LOCKED InsightsEngine behind the single HonestyGate (byte-identical signature, no separate gating path), added the calm omit-when-empty `.flare` section placed FIRST in the dashboard (time-sensitivity by position only — no alarm color/badge/glyph), locked the "a warning that must not alarm" contract with a no-alarm-visual source-scan, and turned the Plan-01 RED flare E2E GREEN — the final requirement of the milestone now ships.**

## Performance

- **Duration:** ~28 min
- **Started:** 2026-06-10T00:29:18Z
- **Completed:** 2026-06-10T00:57:31Z
- **Tasks:** 2
- **Files modified:** 5 modified, 1 created

## Accomplishments
- Composed `FlareEngine.flareWarning(...)` into `InsightsEngine.insights(from:asOf:dismissed:)` by appending ONE additive candidate term before the single `HonestyGate.apply` — the public signature stays byte-identical (D-16) and there is NO separate gating path (D-08). The gate's `minSample(.flare)==21` + the confidence floor + the typed `isInsufficiencyNotice` carve-out + the fail-closed forbidden-term scan (now incl. alarmist) handle the flare candidate with zero new withholding logic.
- Extended the package acceptance: `composition_surfacesFlare_overRisingFixture` (rising-with-≥21 → exactly one bounded ≤.medium `.flare`), `gate_withholdsFlare_forSuppressedAndFlat` (suppressed/flat → zero), and added the three flare fixtures to `allEmittedInsights_haveZeroForbiddenTerms` so the EMITTED flare copy is scanned against the full causal ∪ imperative ∪ alarmist union — now reachable through the PUBLIC engine.
- Added the calm `.flare` section as the FIRST section in `InsightsView` (above Trends) via the EXISTING omit-when-empty `section(title:identifier:kind:)` helper — `identifier: "insights.section.flare"`, `kind: .flare`, `title: FlareCopy.sectionFlare`. No alarm color, no badge, no exclamation; `InsightCardView` reused unchanged (its `.flare` branch was already present); the dismiss path is the existing one. Time-sensitivity is conveyed by POSITION only (D-07).
- Hardened the calm-non-alarmist contract (checker WR-4) with `FlareNoAlarmVisualTests`: a `#filePath` source-scan over `InsightsView.swift` + `InsightCardView.swift` asserting NO alarm affordance (`.red`/`.orange`/`Color.red`/`exclamationmark`/`bell`/`flame`/`.badge`) appears in the flare rendering code — non-vacuous via a non-empty file-set guard + an `insights.section.flare` anchor assertion. A future edit that paints the flare path red or hangs an alarm glyph on it now fails the build.
- Turned the Plan-01 RED `testFlare_rendersFirst` E2E GREEN: the seeded ≥21-day rising history renders one calm flare card, and the flare section renders ABOVE the Trends section (`frame.minY` ordering). Saved the dashboard screenshot to `/tmp/e2e/insights-12-flare.png` for the deferred end-of-phase calm-tone human-verify.

## Task Commits

Each task was committed atomically:

1. **Task 1: Compose FlareEngine into the LOCKED InsightsEngine + package zero-forbidden-terms + composition acceptance** - `7ba1231` (feat)
2. **Task 2: The calm .flare section placed FIRST in InsightsView + no-alarm-visual guard + flare E2E GREEN** - `bd1a64e` (feat)

**Plan metadata:** _(final docs commit below)_

## Files Created/Modified
- `EndoInsights/Sources/EndoInsights/InsightsEngine.swift` - Appended `+ FlareEngine.flareWarning(from:asOf:calendar:)` LAST in the candidate concatenation; documented as the composed flare warning routed through the SAME single gate (no separate gating path, LOCKED signature).
- `EndoInsights/Tests/EndoInsightsTests/InsightsEngineTests.swift` - Added `composition_surfacesFlare_overRisingFixture` + `gate_withholdsFlare_forSuppressedAndFlat`.
- `EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift` - Added the three flare fixtures (rising / under-evidence cusp / flat) to `allEmittedInsights_haveZeroForbiddenTerms`.
- `Sources/EndoApp/Features/Insights/InsightsView.swift` - Added the calm omit-when-empty `.flare` section as the FIRST insight section (above Trends); updated the header SECTIONS doc to the new order.
- `Tests/EndoAppTests/FlareNoAlarmVisualTests.swift` - NEW: the no-alarm-visual `#filePath` source-scan (checker WR-4) with two anti-vacuity guards.
- `Tests/EndoAppUITests/InsightsDashboardUITests.swift` - Resolved the section-id bleed via `.firstMatch` before reading `.frame` so the flare-above-trends ordering assertion is robust.

## Decisions Made
- The flare candidate is concatenated LAST in `InsightsEngine` (additive — candidate order does not affect gating); the dashboard places the `.flare` section FIRST as a pure UI concern (D-06/D-07).
- The no-alarm-visual guard scans both flare-rendering view files (`InsightsView` + `InsightCardView`), anchored on the `insights.section.flare` string + a non-empty file-set guard, so it cannot pass by scanning nothing or the wrong file.
- The E2E section-id bleed (a SwiftUI `Section` identifier maps onto the header StaticText AND every descendant card cell → a multi-match `.frame` throws) is resolved by reading `.firstMatch` (the topmost header element) before comparing the two sections' vertical origins.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The Plan-01 RED E2E ordering assertion threw on the multi-match section id**
- **Found during:** Task 2 (the `testFlare_rendersFirst` verification run)
- **Issue:** The Plan-01-authored E2E resolved `app.descendants(matching: .any)["insights.section.flare"]` and read `.frame.minY` on it. A SwiftUI `Section`'s `.accessibilityIdentifier` BLEEDS onto its header StaticText AND each descendant card cell (the same masking `firstInsightCard` documents at lines 68-70), so the id resolves to MULTIPLE elements and `.frame` on a multi-match query throws `"Multiple matching elements found"`. The flare section itself rendered correctly and FIRST (confirmed in the a11y tree dump + the screenshot) — the failure was purely the harness's single-match assumption.
- **Fix:** Switched both section lookups to `.matching(identifier:).firstMatch` (the topmost/header element, which is exactly the vertical origin the ordering check needs) before reading `.frame`. No source/UI change — a test-robustness fix only.
- **Files modified:** `Tests/EndoAppUITests/InsightsDashboardUITests.swift`
- **Verification:** `testFlare_rendersFirst` GREEN; the full `InsightsDashboardUITests` suite 6/6 GREEN.
- **Committed in:** `bd1a64e` (folded into the Task 2 commit since it was discovered during Task 2's E2E gate)

---

**Total deviations:** 1 auto-fixed (1 bug — a test-harness robustness fix; no behavior/UI change).
**Impact on plan:** None to scope. The flare section + composition shipped exactly as specified; the fix only made the pre-authored ordering assertion resolve the bleed-masked section id.

## Issues Encountered
- The full `EndoAppTests` run reported one failure: `InsightsCoordinatorTests.dataChanged_coalescesRapidCallsIntoOneRecompute()`. This is the KNOWN pre-existing Phase-8 async-debounce timing flake (logged in `deferred-items.md`, explicitly out of scope per the plan's key reminders). It PASSES in isolation, is unrelated to the flare files, and was NOT chased. Every flare-related test (composition acceptances, `FlareNoAlarmVisualTests`, `FlareNoNotificationTests`, `FlareCopyDriftTests`, the `CopyAudit` flare block, the dashboard E2E) is GREEN.

## Known Stubs
None — the flare warning is fully wired end-to-end (live producer composed behind the gate, calm section rendered from the real coordinator output, dismiss on the existing path). No hardcoded/placeholder data.

## Threat Flags
None — no new network endpoint, auth path, file-access pattern, or schema change was introduced. The composition adds one in-memory producer term behind the existing gate; the dashboard section adds no notification surface (proven by `FlareNoNotificationTests`, GREEN) and no alarm affordance (proven by `FlareNoAlarmVisualTests`, GREEN). All `<threat_model>` `mitigate` dispositions (T-12-02 omit-when-empty, T-12-03 no-push, T-12-07 no-alarm-visuals) are satisfied with executable guards.

## Verification Snapshot
- Full `EndoInsightsTests` — 116/116 GREEN via `swift test`, including the two new flare composition acceptances + the flare fixtures in the zero-forbidden-terms scan; no Phase-5/6/7/11 regression.
- `FlareNoAlarmVisualTests` — GREEN (the flare rendering code carries no alarm affordance; the scan is provably non-vacuous via the anchor + non-empty guards).
- `testFlare_rendersFirst` E2E — GREEN (seeded rising history → one calm flare card rendered ABOVE Trends).
- Full `InsightsDashboardUITests` — 6/6 GREEN (seeded / empty / suppressed / dismiss / recommendation / flare).
- Full app unit suite (`EndoAppTests`) — 62 Swift-Testing tests pass (CopyAudit / FlareCopyDrift / FlareNoNotification / FlareNoAlarmVisual all GREEN); the ONLY failure is the pre-existing out-of-scope coordinator coalescing flake.
- Screenshot `/tmp/e2e/insights-12-flare.png` saved + visually confirmed calm: the "Flare early-warning" section is FIRST (below the pinned disclaimer, above Trends), rendered with the identical grey "Low — possible association" pill as a trend card, uncertainty-first copy, no red/orange/exclamation/badge.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- INST-06 is delivered end-to-end and marked complete: the heuristic, non-diagnostic flare early-warning reaches the user — calm, uncertainty-first, gated like every insight, omit-when-empty, dismissable, withheld when insufficient, never a push. This is the FINAL requirement of the milestone.
- The remaining phase work is the end-of-phase human-verify (the calm-tone judgment on `/tmp/e2e/insights-12-flare.png`, per `human_verify_mode: end-of-phase`) and the phase verifier/validation pass.

## Self-Check: PASSED

- FOUND: `EndoInsights/Sources/EndoInsights/InsightsEngine.swift` (contains `FlareEngine.flareWarning`)
- FOUND: `Sources/EndoApp/Features/Insights/InsightsView.swift` (contains `insights.section.flare`)
- FOUND: `Tests/EndoAppTests/FlareNoAlarmVisualTests.swift`
- FOUND: `.planning/phases/12-flare-early-warning/12-03-SUMMARY.md`
- FOUND: `/tmp/e2e/insights-12-flare.png`
- FOUND commits: `7ba1231`, `bd1a64e`

---
*Phase: 12-flare-early-warning*
*Completed: 2026-06-10*
