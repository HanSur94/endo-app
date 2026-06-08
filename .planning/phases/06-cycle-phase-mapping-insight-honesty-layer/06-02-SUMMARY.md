---
phase: 06-cycle-phase-mapping-insight-honesty-layer
plan: 02
subsystem: insights
tags: [honesty-gate, forbidden-terms, insights-engine, swift, foundation, INST-04]

# Dependency graph
requires:
  - phase: 06-01
    provides: "Insight.signalKey (stable dismissal/derivation key) + CyclePhaseMapper.insights (self-gated estimated-phase associations)"
  - phase: 05
    provides: "TrendsEngine.trends + Insight value contract (InsightKind, InsightConfidence Comparable)"
  - phase: 06-03
    provides: "Repository.dismissedSignalKeys() — the live Set<String> Phase 8 will feed into the gate"
provides:
  - "ForbiddenTerms: package-local non-causal language policy (ClaimsPolicy mirror + D-08 causal connectives)"
  - "HonestyGate: the single mandatory insight choke-point (dismissal-exclusion, per-kind min-sample superset filter, confidence floor with .insufficient carve-out, DEBUG forbidden-term assert)"
  - "InsightsEngine: the ONE public insight producer composing trends + cycle-phase through the gate (signature LOCKED, D-16)"
  - "TrendsEngine demoted to package-internal behind the InsightsEngine facade"
affects: [phase-07-correlation, phase-08-insights-ui, phase-11-recommendations, phase-12-flare]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single mandatory honesty choke-point: every insight kind routes through HonestyGate.apply before reaching a user"
    - "Superset filter over self-gating producers: the gate never count-drops a valid producer output (Pitfall 1)"
    - "Curated copy + substring scan + DEBUG assert; the zero-forbidden-terms TEST (not the release runtime) is the durable guarantee"

key-files:
  created:
    - EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift
    - EndoInsights/Sources/EndoInsights/HonestyGate.swift
    - EndoInsights/Sources/EndoInsights/InsightsEngine.swift
    - EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift
    - EndoInsights/Tests/EndoInsightsTests/InsightsEngineTests.swift
  modified:
    - EndoInsights/Sources/EndoInsights/TrendsEngine.swift

key-decisions:
  - "minSample is an API CONTRACT producers self-check BEFORE emitting; the gate performs NO count-based drop in Phase 6 (never uses insight.points.count) — prevents WITHHOLDING a valid 1-2-point frequency trend (Pitfall 1)"
  - "The .insufficient 'not enough data yet' signal is carved out of the confidence floor — it IS the honest sub-threshold signal, kept despite .none confidence"
  - "Forbidden-term enforcement is a DEBUG assert (a forbidden term is a copy bug, not user data — never silently dropped in release); the HonestyGateTests zero-terms scan is the durable guarantee every future kind must extend"
  - "TrendsEngine demoted to internal ONLY AFTER InsightsEngine existed (sequence-flag 2) — no window with no public producer"

patterns-established:
  - "HonestyGate as the single choke-point (D-05/D-16): future kinds extend minSample by adding a case, not redesigning"
  - "InsightsEngine.insights(from:asOf:dismissed:calendar:) is the locked public API (D-13/D-16) — Ph7/11/12 add producers without changing it"

requirements-completed: [INST-04]

# Metrics
duration: ~12min
completed: 2026-06-08
---

# Phase 6 Plan 02: Insight-Honesty Layer Summary

**The single mandatory HonestyGate choke-point (per-kind min-sample superset filter, confidence floor with the `.insufficient` carve-out, dismissal-exclusion, DEBUG non-causal assert) plus the one public InsightsEngine composing trends + cycle-phase, with TrendsEngine demoted to internal — INST-04, the ethical core.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-06-08T23:40Z
- **Completed:** 2026-06-08T23:44Z
- **Tasks:** 3
- **Files modified:** 6 (5 created, 1 modified)

## Accomplishments
- `ForbiddenTerms`: package-local mirror of `ClaimsPolicy.forbiddenVerbs` extended with the D-08 causal connectives, case-insensitive substring scan (cannot import the app target — SC1).
- `HonestyGate.apply`: the ONE filter every insight passes — dismissal-exclusion on `signalKey`, per-kind min-sample as a SUPERSET filter (no `points.count` drop — Pitfall 1), confidence floor with the `.insufficient` carve-out, and a DEBUG forbidden-term assert (never a silent release drop).
- `InsightsEngine.insights(from:asOf:dismissed:calendar:)`: the single public producer (locked signature, D-13/D-16) composing `TrendsEngine` + `CyclePhaseMapper` through the gate.
- `TrendsEngine` demoted `public enum` → `enum` (internal) after `InsightsEngine` existed; its `@testable` tests stay green.
- Full `EndoInsightsTests` suite green: 39 tests / 8 suites, including the Phase-5 no-regression superset test, the mandatory zero-forbidden-terms scan over every fixture, dismissal idempotence (D-12), and BoundaryLint (Foundation-only).

## Task Commits

Each task was committed atomically:

1. **Task 1: ForbiddenTerms + HonestyGate** - `028d70e` (feat)
2. **Task 2: InsightsEngine public root + demote TrendsEngine** - `94fb566` (feat)
3. **Task 3: HonestyGateTests + InsightsEngineTests** - `72c74a7` (test)

_Note: source files were written to spec, so the TDD GREEN was reached at first test run; no separate refactor commit was needed._

## Files Created/Modified
- `EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift` - non-causal language policy (causal verbs + connectives, substring scan)
- `EndoInsights/Sources/EndoInsights/HonestyGate.swift` - the single insight choke-point
- `EndoInsights/Sources/EndoInsights/InsightsEngine.swift` - the one public composition root
- `EndoInsights/Sources/EndoInsights/TrendsEngine.swift` - demoted to internal (visibility-only change)
- `EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift` - withhold/carve-out/dismissal/zero-forbidden-terms
- `EndoInsights/Tests/EndoInsightsTests/InsightsEngineTests.swift` - composition/no-regression/idempotence

## Decisions Made
- **min-sample as contract, not gate (Pitfall 1):** the gate does NOT count-drop `.trend`/`.cyclePhase` — those producers already self-gate (`minLoggedDays>=3`; `gaps.count>=2`/spread/suppressed). Using `insight.points.count` would WITHHOLD a valid frequency trend that legitimately carries only 1-2 present-days. `minSample(for:)` is the contract future producers (Ph7/11/12) check against THEIR OWN sample before emitting.
- **`.insufficient` carve-out:** the `.none`-confidence "not enough data yet" trend signal (signalKey suffix `.insufficient`) is the deliberate honest sub-threshold signal and is KEPT past the floor.
- **DEBUG assert, not silent drop:** a forbidden causal term is a programmer/copy error, not user data; it asserts in debug (mirrors `Repository.assertConsented()`) and is never silently dropped in release. The zero-forbidden-terms TEST is the durable guarantee.
- **Demotion sequencing:** `TrendsEngine` went internal only after `InsightsEngine` existed, so there was never a window with no public producer.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. `EndoApp.xcodeproj` is gitignored (XcodeGen-generated), so only the Swift sources were staged per task.

## Known Stubs
None. The three reserved `minSample` placeholders (`.correlation:14`, `.recommendation:14`, `.flare:21`) are intentional API-contract reservations for Phases 7/11/12 (per the plan, A3), not stubs — they are documented in `HonestyGate` and exercised by no Phase-6 producer.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- The honesty layer is LOCKED and ready: Phase 7 (correlation), Phase 11 (recommendations), Phase 12 (flare) add candidate producers to `InsightsEngine` and self-check against `HonestyGate.minSample(for:)` before emitting — each MUST add a `minSample` case and add its emitted copy to the `HonestyGateTests` zero-forbidden-terms scan.
- Phase 8 (insights UI) renders `InsightsEngine.insights(...)` output and supplies `dismissed` from `Repository.dismissedSignalKeys()` (06-03) — the live wiring.

## Self-Check: PASSED

- FOUND: EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift
- FOUND: EndoInsights/Sources/EndoInsights/HonestyGate.swift
- FOUND: EndoInsights/Sources/EndoInsights/InsightsEngine.swift
- FOUND: EndoInsights/Tests/EndoInsightsTests/HonestyGateTests.swift
- FOUND: EndoInsights/Tests/EndoInsightsTests/InsightsEngineTests.swift
- FOUND commit: 028d70e (Task 1)
- FOUND commit: 94fb566 (Task 2)
- FOUND commit: 72c74a7 (Task 3)

---
*Phase: 06-cycle-phase-mapping-insight-honesty-layer*
*Completed: 2026-06-08*
