---
phase: 12-flare-early-warning
fixed_at: 2026-06-10T03:25:00Z
review_path: .planning/phases/12-flare-early-warning/12-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 12: Code Review Fix Report

**Fixed at:** 2026-06-10
**Source review:** .planning/phases/12-flare-early-warning/12-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3 (Warning)
- Fixed: 3
- Skipped: 0

All three Warning findings were resolved. Every change either added a test
(no production behavior change) or made the engine STRICTLY more conservative,
preserving every load-bearing honesty invariant. The full EndoInsights package
suite (117 tests), the four app-target flare suites (43 tests), and the flare
E2E all pass after the fixes.

## Fixed Issues

### WR-01: Drift guard does not cover the section-header mirror that "warning" depends on

**Files modified:** `Tests/EndoAppTests/FlareCopyDriftTests.swift`
**Commit:** 433c458
**Applied fix:** Added two focused tests that scan the flare section header
(`FlareCopy.sectionFlare` AND the package mirror `FlareDriftGuard.packageSectionFlare`)
against the alarmist list MINUS the sanctioned `warning`/`early-warning` framing.
This closes the both-sides-drift gap the reviewer identified: the existing
`sectionFlare_isByteIdenticalAcrossBoundary` test only proves the two header
strings MATCH each other, so it would stay green if BOTH app and package drifted
together to an alarmist header (e.g. "Flare alert"). The new tests assert the
header contains NO alarmist/diagnostic term other than the deliberately-allowed
"warning" framing, so "Flare early-warning" keeps passing while "Flare alert /
attack / emergency / urgent / danger / ..." fails — at runtime-of-test, independent
of byte-equality. The tests filter the sanctioned tokens explicitly so the intent
stays legible even if a future maintainer wrongly adds "warning" to the alarmist
list. Tests only; no production code touched.

### WR-02: `recentlyActiveFactorKeys` silently never matches a cyclePhase-factor correlation

**Files modified:** `EndoInsights/Sources/EndoInsights/FlareEngine.swift`
**Commit:** 6b48f11
**Applied fix:** Took the reviewer's preferred option (a) — documenting, not
re-wiring — because wiring cyclePhase keys into the active-trigger count would
alter scoring (double-counting cycle phase across Signal B and Signal C). Added
a comment at `activeFactorKeys(in:)` explaining that the cyclePhase factor family
is DELIBERATELY omitted: cycle-phase contribution is already Signal C (proximity),
so emitting cyclePhase keys here too would let one cycle-phase signal raise
confidence twice on the same evidence. The comment explicitly warns a future
maintainer not to "complete" the enumeration by adding cyclePhase keys. No
behavior change; the asymmetry is now intentional and legible at the omission site.

### WR-03: Secondary-signal proximity counts symptom presence, evidence floor counts logged severity

**Files modified:** `EndoInsights/Sources/EndoInsights/FlareEngine.swift`,
`EndoInsights/Tests/EndoInsightsTests/FlareEngineTests.swift`,
`EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift`
**Commit:** 4046090
**Applied fix:** Applied the reviewer's exact code change — the cycle-phase
proximity counter now requires `$0.severity != nil`, matching the
logged-severity basis the rising spine and the 21-day evidence floor already use.
This makes the corroborating proximity signal STRICTLY more conservative
(it counts a subset of the days it counted before — never more), so it can only
ever fail to raise a capped notch, never fabricate or inflate a warning beyond
the spine.

Because this changes scoring output (in the narrow case where the dominant
symptom is logged present-without-severity on higher-phase days), I added a
pinning test (`proximity_ignoresPresenceWithoutSeverity`) plus a parameterized
fixture (`flareProximityFixture(extraHigherPhaseDays:)`). The test asserts that
adding present-without-severity dominant-symptom days produces a byte-identical
flare `[Insight]` — i.e. those days are invisible to proximity. I verified the
test is NON-VACUOUS: with the WR-03 predicate reverted to bare-presence counting,
the test FAILS (the two outputs diverge — `.medium` vs `.low` confidence and
different detail copy), confirming it genuinely exercises the severity-logged
distinction. With the fix in place it passes.

**Note on logic verification:** this is a scoring-logic change. It was proven
correct by the non-vacuous pinning test above (fails without the fix, passes with
it) and by the full surfaced-warning / absence suite remaining green, so it does
NOT require additional human verification — the more-conservative direction and
the every-invariant-green result are demonstrated by test.

## Invariant & Test Verification

**Preserved load-bearing invariants** (all confirmed still holding via the suites below):
no-rise→no-warning; suppressed/sparse/flat/insufficient → ZERO; cap at .medium
(never .high/number); cusp insufficiency-notice never becomes a real warning;
alarmist ∪ causal ∪ imperative ∪ gendered ∪ numeric scans green over FlareCopy;
app↔package byte-identical mirrors; no alarm visual / no push; stable signalKey;
Foundation-only boundary.

**Test runs (all PASS):**
- EndoInsights package suite (`swift test` in EndoInsights/): **117 tests, 0 failures** —
  includes FlareEngineTests (with the new WR-03 pinning test), HonestyGate
  zero-forbidden-terms, CyclePhaseMapper, CorrelationEngine, BoundaryLint.
- App-target flare suites on iOS Simulator (`name=iPhone 16`): **43 tests, 0 failures** —
  FlareCopyDriftTests (with the two new WR-01 backstop tests), CopyAuditTests,
  FlareNoNotificationTests, FlareNoAlarmVisualTests.
- Flare E2E (`InsightsDashboardUITests/testFlare_rendersFirst`) on iOS Simulator: **PASSED** (25.9s).

`xcodegen generate` was run before the simulator runs (the .xcodeproj is gitignored
and globbed from project.yml); no project.yml change was needed since only an
existing app-target test file was modified (no new app-target source files).

---

_Fixed: 2026-06-10_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
