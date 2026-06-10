---
phase: 12-flare-early-warning
plan: 02
subsystem: insights
tags: [swift, swift-testing, foundation, honesty-layer, flare, heuristic, copy-audit, drift-guard, tdd, insights]

# Dependency graph
requires:
  - phase: 12-flare-early-warning
    provides: "the Plan-01 alarmist scan + no-push guard + RED FlareEngineTests/fixtures + the placeholder shim this plan deletes + the locked alarmist policy in both mirrors"
  - phase: 11-personalized-recommendations
    provides: "the one-more-producer + app↔package copy/forbidden-list drift-guard pattern (RecommendationsEngine/RecommendationsCopy/RecommendationsDriftGuard) this plan mirrors verbatim"
  - phase: 07-correlations
    provides: "CorrelationEngine.scoredCandidates typed survivors (active aggravating triggers) + Factor.key/state binarization the active-trigger signal reuses"
  - phase: 06-cycle-phase-mapping
    provides: "CyclePhaseMapper.phaseWindows/phase + HonestyGate.minSample(.flare)==21 the proximity + evidence-floor signals reuse"
  - phase: 05-trends
    provides: "TrendsEngine.bucket/priorBucket/meanSeverity/direction the REQUIRED rising-severity spine composes"
provides:
  - "FlareEngine.flareWarning(from:asOf:calendar:) — the Foundation-only conservative flare heuristic composing 3 gated signals into a bounded ≤.medium, withhold-by-default warning"
  - "FlareCopy (app, audited) + FlareCopyText (package mirror) + FlareDriftGuard — the byte-identical calm uncertainty-first flare copy + cross-boundary drift guard"
  - "The FlareCopy full-union scan (alarmist∪causal∪imperative∪gendered∪numeric) wired into CopyAuditTests"
  - "FlareCopyDriftTests — app↔package byte-identity + alarmist-list set-equality"
affects: [12-03 (composes FlareEngine into InsightsEngine + adds the calm .flare dashboard section, turning the E2E GREEN)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Compose-never-re-derive heuristic: a new insight kind built ENTIRELY from already-gated engine helpers (TrendsEngine/CorrelationEngine/CyclePhaseMapper), running zero raw statistics of its own (D-01/D-02)"
    - "Required-spine + additive-notches confidence: a REQUIRED primary signal (rising severity) that secondary signals can only RAISE one notch each (never start), capped at .medium, floored at .low — bias to withhold"
    - "Insufficiency-on-the-cusp: a real recent signal with sub-floor evidence emits ONE typed isInsufficiencyNotice card sharing the same signalKey; silent otherwise"

key-files:
  created:
    - "Sources/EndoApp/Features/Insights/FlareCopy.swift"
    - "EndoInsights/Sources/EndoInsights/FlareEngine.swift"
    - "Tests/EndoAppTests/FlareCopyDriftTests.swift"
  modified:
    - "Tests/EndoAppTests/CopyAuditTests.swift"
    - "EndoInsights/Tests/EndoInsightsTests/FlareEngineTests.swift"
    - "project.yml (regenerated EndoApp.xcodeproj via xcodegen for the new app files)"

key-decisions:
  - "The rising-severity spine is detected via TrendsEngine.direction over the .last7 recent-vs-prior buckets (the same coarse banded direction the trends engine uses), while the 21-day evidence floor is measured against the dominant symptom's logged-severity day count over the .last30 recent bucket — so a 30-day rising fixture clears 21 and a 14-day one does not"
  - "An active aggravating trigger = a CorrelationEngine.scoredCandidates .aggravating survivor whose factor key is still PRESENT on at least one .last30 day (recomputed locally from the snapshots' factor binarization), so a stale trigger the user no longer logs cannot inflate the warning"
  - "cycle-phase proximity = today falls in an estimated phase window AND that phase is the historically-highest dominant-symptom phase (deterministic argmax, tie → lowest rawValue); inherits []-on-suppressed/irregular/<2-cycle from CyclePhaseMapper, so it is false for every flare fixture (none log bleeding)"
  - "The contributing note is derived from the TYPED signals (activeTriggerCount>0 || nearHigherPhase), NEVER parsed from copy, and is the curated FlareCopyText.contributing appended to the detail only when a secondary signal fired"

requirements-completed: []

# Metrics
duration: 33min
completed: 2026-06-10
---

# Phase 12 Plan 02: FlareEngine Producer + FlareCopy Mirror + Drift Guard Summary

**Built the phase's one genuinely-new logic: a deterministic, ML-free flare heuristic that composes a REQUIRED rising-severity spine plus currently-active triggers plus cycle-phase proximity into a bounded (≤.medium), uncertainty-first, withhold-by-default warning — plus the byte-identical calm app/package flare copy + drift guard — turning the Plan-01 RED FlareEngineTests, the no-push guard, and the FlareCopy scan GREEN.**

## Performance

- **Duration:** ~33 min
- **Started:** 2026-06-09T23:51:58Z
- **Completed:** 2026-06-10T00:24:56Z
- **Tasks:** 2 (both TDD)
- **Files modified:** 2 modified + 1 regenerated, 3 created

## Accomplishments
- Shipped `FlareCopy` (app, audited) + `FlareCopyText` (package mirror) + `public FlareDriftGuard` — six LOCKED calm, uncertainty-first strings that dodge BOTH load-bearing substring traps ("her" ⊂ "higher" → "more difficult days"; "predict" ⊂ "prediction" → the detail OMITS "not a prediction"), proven byte-identical across the SC1 boundary.
- Wired the FlareCopy FULL scan union (alarmist ∪ causal ∪ imperative ∪ gendered ∪ numeric) into `CopyAuditTests` and added `FlareCopyDriftTests` (byte-identity of every rendered string + alarmist-list set-equality).
- Implemented `FlareEngine.flareWarning` — the conservative heuristic EXACTLY per the locked order of operations: suppressed→[], REQUIRED rising-severity spine (no rise → []), +1 confidence notch per currently-active aggravating trigger and per near-higher-symptom cycle phase, CAP .medium / floor .low, `sampleSize == rising.loggedDays` self-gated on the SAME `minSample(.flare)==21` the gate enforces, ≤1 card, the A3 insufficiency notice only on the cusp, stable window-independent signalKey "flare.warning".
- Deleted the Plan-01 RED placeholder shim from `FlareEngineTests` so the real producer is exercised — the full honesty/absence suite goes GREEN.
- Held the package Foundation-only (BoundaryLintTests green) by composing the existing engine helpers and re-deriving NO raw stats (D-01/D-02).

## Task Commits

Each task was committed atomically:

1. **Task 1: FlareCopy + FlareCopyText + FlareDriftGuard + the FlareCopy alarmist scan + drift tests** - `87853b8` (feat)
2. **Task 2: The FlareEngine producer (compose 3 signals → bounded ≤.medium) + delete the placeholder shim** - `06ac267` (feat)

**Plan metadata:** _(final docs commit below)_

## Files Created/Modified
- `Sources/EndoApp/Features/Insights/FlareCopy.swift` - NEW: the app-side audited flare copy surface (six LOCKED strings + `auditableStrings`), mirroring `RecommendationsCopy`'s posture + header documentation.
- `EndoInsights/Sources/EndoInsights/FlareEngine.swift` - NEW: the Foundation-only `FlareEngine` producer + the package-internal `FlareCopyText` mirror + the public test-support `FlareDriftGuard`.
- `Tests/EndoAppTests/FlareCopyDriftTests.swift` - NEW: the app↔package byte-identity (headline/detail/contributing/section/insufficient) + alarmist-list set-equality drift guard.
- `Tests/EndoAppTests/CopyAuditTests.swift` - Added the `FlareCopy.auditableStrings` full-union scan block (forbidden-verb / gendered / imperative / alarmist / numeric + non-empty).
- `EndoInsights/Tests/EndoInsightsTests/FlareEngineTests.swift` - Deleted the Plan-01 RED placeholder shim; the suite now exercises the real producer.
- `project.yml` / `EndoApp.xcodeproj` - Regenerated via `xcodegen generate` so the new app-target files compile into the scheme (the established pattern; project file is gitignored).

## Decisions Made
- Rising spine detected via `TrendsEngine.direction` over `.last7` recent-vs-prior buckets; the 21-day evidence floor measured against the dominant symptom's logged-severity-day count over the `.last30` recent bucket — so the 30-day rising fixture clears 21 (one warning) and the 14-day under-evidence fixture does not (insufficiency notice).
- "Currently active" trigger = an `.aggravating` `scoredCandidates` survivor whose factor key is still PRESENT on ≥1 `.last30` day (binarized locally from the snapshots), so a stale factor cannot inflate confidence.
- Cycle-phase proximity reuses `CyclePhaseMapper.phaseWindows`; it is `false` for every flare fixture (none log bleeding → no windows), so the surfaced warning rests on the rising spine alone at `.low` — exactly the conservative ≤.medium, withhold-by-default posture the contract demands.
- The contributing note is derived from the TYPED signals, never parsed from copy, and appended to the detail only when a secondary signal fired.

## Deviations from Plan

None - plan executed exactly as written. Both tasks followed the locked order of operations and the LOCKED curated copy strings; no auto-fixes were required.

## Issues Encountered
- The full `EndoAppTests` run reported one failure: `InsightsCoordinatorTests.dataChanged_coalescesRapidCallsIntoOneRecompute()`. This is a PRE-EXISTING Phase-8 async-debounce timing flake — it PASSES in isolation and when its own suite runs alone (verified both ways), and is unrelated to the flare files (which never touch `InsightsCoordinator`). Logged to `deferred-items.md` per the executor scope boundary; NOT fixed here.

## Verification Snapshot
- `FlareEngineTests` — 5/5 GREEN via both `swift test` and the iOS-Simulator `xcodebuild` run (suppressed/flat → []; under-21 rise → no non-notice warning; rising-with-≥21 → exactly one bounded ≤.medium "flare.warning"; deterministic).
- `BoundaryLintTests` ("D-12 package import boundary") — GREEN (EndoInsights stays Foundation-only).
- `FlareCopyDriftTests` — 8/8 GREEN (byte-identity + alarmist-list set-equality).
- `CopyAuditTests` flare block — GREEN (alarmist ∪ causal ∪ imperative ∪ gendered ∪ numeric over `FlareCopy.auditableStrings`).
- `FlareNoNotificationTests` (the SC2 no-push guard) — now GREEN (the two flare files exist, satisfying the non-empty file-set guard, and reference no notification API).
- Full `EndoInsightsTests` — 114/114 GREEN (no Phase-5/6/7/11 regression). Full `EndoAppTests` — 62 Swift-Testing tests pass; the ONLY failure is the pre-existing out-of-scope coordinator coalescing flake noted above.

## Known Stubs
None — the producer is fully wired to live engine signals; no hardcoded/placeholder data. (Composition into `InsightsEngine` + the dashboard section is Plan 03's scope, per the phase wave plan — not a stub in this plan.)

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- **For Plan 03:** compose `FlareEngine.flareWarning(...)` into the LOCKED `InsightsEngine.insights(from:asOf:dismissed:)` behind the single `HonestyGate`, and add the calm, omit-when-empty `.flare` section FIRST in `InsightsView` (D-07) — that turns `testFlare_rendersFirst` GREEN and completes INST-06 end-to-end.
- The signalKey "flare.warning" (window `.allTime`) + the `isInsufficiencyNotice` carve-out are already gate-compatible (the gate's min-sample backstop is `minSample(.flare)==21`, exactly what the producer self-gates on), so the producer drops straight in behind the gate with no gating changes.

## TDD Gate Compliance
Both tasks ran RED→GREEN against the Plan-01 RED suites (FlareEngineTests + the CopyAudit/drift specs): the suites failed before the producer/copy shipped and pass after. Per-task commits are `feat(...)` because the failing tests were authored in Plan 01 (the RED gate); this plan delivers the GREEN implementation. No separate `test(...)` commit was needed this plan (the RED commits live in Plan 01: `327e52b`).

## Self-Check: PASSED

- FOUND: `Sources/EndoApp/Features/Insights/FlareCopy.swift`
- FOUND: `EndoInsights/Sources/EndoInsights/FlareEngine.swift`
- FOUND: `Tests/EndoAppTests/FlareCopyDriftTests.swift`
- FOUND commits: `87853b8`, `06ac267`

---
*Phase: 12-flare-early-warning*
*Completed: 2026-06-10*
