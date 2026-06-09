---
phase: 12-flare-early-warning
plan: 01
subsystem: testing
tags: [swift, swift-testing, xcuitest, foundation, honesty-layer, copy-audit, source-scan, tdd, flare, insights]

# Dependency graph
requires:
  - phase: 11-personalized-recommendations
    provides: "the one-more-producer + ForbiddenTerms/ClaimsPolicy copy-scan + app↔package drift-guard + RED-suite-with-placeholder-shim pattern this plan mirrors"
  - phase: 10-doctor-ready-pdf-report
    provides: "ReportNoNetworkTests — the #filePath source-scan no-egress pattern the no-push guard clones"
  - phase: 08-insights-integration-coordinator-cached-insights-dashboard
    provides: "InsightsView sections + InsightsDashboardUITests harness + the -uitest-reset/-uitest-seed seed seam in AppDependencies"
provides:
  - "ForbiddenTerms.alarmist (package) + ClaimsPolicy.forbiddenAlarmist (app) — the LOCKED alarmist/diagnostic copy policy, wired as the third clause of ForbiddenTerms.violation(in:)"
  - "CopyAuditTests alarmist scan with a positive control (deliberate alarmist copy flagged) AND a negative control (curated calm flare words pass clean) — GREEN"
  - "FlareNoNotificationTests — the SC2 no-push source-scan guard (locked token set; RED until Plan 02 ships the flare files)"
  - "FlareEngineTests — the RED honesty/absence acceptance suite for FlareEngine.flareWarning, with a file-local placeholder shim so the scheme compiles"
  - "Three new flare fixtures (rising-with-evidence / flat-no-rise / under-evidence rise) in SnapshotFixtures"
  - "RED testFlare_rendersFirst E2E + the -uitest-seed-flare seed seam (seedFlareWarning) in AppDependencies"
affects: [12-02 (FlareEngine producer + FlareCopy turn these RED suites GREEN), 12-03 (the calm .flare dashboard section turns the E2E GREEN)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Absence-by-test: the flare honesty contract (suppressed/flat/insufficient → ZERO) is RED-specified BEFORE the producer exists"
    - "RED-suite-with-file-local-placeholder-shim: an enum FlareEngine { flareWarning -> [] } shim keeps the shared scheme compiling while assertions stay RED at runtime (Plan 02 deletes it)"
    - "Locked-token source-scan whose file set is asserted non-empty → RED-now/GREEN-when-files-ship (the no-push guard)"

key-files:
  created:
    - "Tests/EndoAppTests/FlareNoNotificationTests.swift"
    - "EndoInsights/Tests/EndoInsightsTests/FlareEngineTests.swift"
  modified:
    - "EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift"
    - "Sources/EndoApp/Content/ClaimsPolicy.swift"
    - "Tests/EndoAppTests/CopyAuditTests.swift"
    - "EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift"
    - "Tests/EndoAppUITests/InsightsDashboardUITests.swift"
    - "Sources/EndoApp/App/AppDependencies.swift"

key-decisions:
  - "'warning' is DELIBERATELY EXCLUDED from the alarmist list (the section header is literally 'Flare early-warning', D-07); the scan covers in-card copy only"
  - "The flare signalKey is the stable, value/window-independent 'flare.warning' with window .allTime (mirrors recommendations); confidence bounded .low…≤.medium (uncertainty-first, D-03)"
  - "The negative-control calm words use the FINAL clean forms ('more difficult' not 'higher'; 'rising pattern' not 'prediction') to dodge the 'her'/'predict' substring traps"

patterns-established:
  - "Pattern: a flare warning is never alarmist/diagnostic/certain — proven by a positive+negative copy-scan control before any flare copy ships"
  - "Pattern: the no-push invariant (SC2) is a structural source-scan that fails the build on any UNUserNotification/NotificationScheduler/scheduleReminder reference in the flare module"

requirements-completed: [INST-06]

# Metrics
duration: 7min
completed: 2026-06-09
---

# Phase 12 Plan 01: Flare Early-Warning Wave-0 Scaffold Summary

**Made the two highest-stakes flare invariants executable FIRST: the LOCKED alarmist/diagnostic copy policy (with positive+negative scan controls, GREEN), the SC2 no-push source guard, and the RED FlareEngine honesty/absence acceptance suite + flare fixtures + RED dashboard E2E + seed seam — with NO producer or UI written.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-06-09T23:39:50Z
- **Completed:** 2026-06-09T23:46:22Z
- **Tasks:** 3
- **Files modified:** 6 modified, 2 created

## Accomplishments
- Locked the alarmist/diagnostic copy policy in both mirrors (`ForbiddenTerms.alarmist` package + `ClaimsPolicy.forbiddenAlarmist` app), wired as the third clause of `ForbiddenTerms.violation(in:)`, with the substring-trap rationale documented and `"warning"` deliberately excluded for the header.
- Proved the alarmist scan works independent of Plan 02: a POSITIVE control flags every deliberate alarmist sentence and a NEGATIVE control passes every curated calm flare word clean (CopyAuditTests 25/25 GREEN).
- Locked the SC2 no-push invariant: `FlareNoNotificationTests` scans the two (not-yet-existing) flare files for the full notification/push token set, strips comments, and asserts a non-empty file set (RED until Plan 02 ships the files).
- RED-specified the FlareEngine honesty contract before the producer exists: suppressed/flat/insufficient → ZERO warning; rising-with-≥21-evidence → one bounded ≤.medium `flare.warning`. The placeholder shim keeps the shared scheme compiling; verified RED-at-runtime AND non-vacuous.
- Added three flare fixtures + the RED `testFlare_rendersFirst` E2E (flare section above trends, D-07) + the `-uitest-seed-flare` seed seam.

## Task Commits

Each task was committed atomically:

1. **Task 1: Lock the alarmist policy + scan (package + app) with positive/negative controls** - `f80fff1` (feat)
2. **Task 2: The no-push source-scan guard (FlareNoNotificationTests)** - `a00f6df` (test)
3. **Task 3: RED FlareEngineTests honesty/absence suite + flare fixtures + RED E2E + seed seam** - `327e52b` (test)

**Plan metadata:** _(final docs commit below)_

## Files Created/Modified
- `EndoInsights/Sources/EndoInsights/ForbiddenTerms.swift` - Added `static let alarmist` (the LOCKED list) + the third `violation(in:)` clause; documented the substring-trap rationale.
- `Sources/EndoApp/Content/ClaimsPolicy.swift` - Added the byte-set-identical `forbiddenAlarmist` app mirror.
- `Tests/EndoAppTests/CopyAuditTests.swift` - Added `firstAlarmist(in:)` + the positive/negative alarmist control tests.
- `Tests/EndoAppTests/FlareNoNotificationTests.swift` - NEW: the SC2 no-push source-scan guard (locked token set, two anti-vacuity guards).
- `EndoInsights/Tests/EndoInsightsTests/FlareEngineTests.swift` - NEW: the RED honesty/absence suite + the file-local RED placeholder shim.
- `EndoInsights/Tests/EndoInsightsTests/SnapshotFixtures.swift` - Added `risingFlareFixture`, `flatNoRiseFixture`, `risingButUnderEvidenceFixture`.
- `Tests/EndoAppUITests/InsightsDashboardUITests.swift` - Added the RED `testFlare_rendersFirst` E2E (flare-above-trends ordering via `frame.minY`).
- `Sources/EndoApp/App/AppDependencies.swift` - Added the `-uitest-seed-flare` arg + `seedFlareWarning(into:)` (30-day rising spine, gated on `seedFlare && uiTestReset`).

## Decisions Made
- `"warning"` is excluded from the alarmist list (the section header is "Flare early-warning", D-07) — the scan covers in-card copy only; exclamation is caught structurally by curating no `"!"` into FlareCopy.
- The flare signalKey is `"flare.warning"` (stable, value/window-independent) with window `.allTime`; confidence bounded `.low…≤.medium` (uncertainty-first, D-03).
- The negative-control words use the final clean forms ("more difficult", not "higher"; "rising pattern", not "prediction") to dodge the "her"/"predict" substring traps, matching what Plan 02 ships.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Swift Testing `#expect` Comment cannot accept a `String`-concatenated (`+`) message**
- **Found during:** Task 3 (the build-for-testing verification, which exercises the Task-2 file as it compiles into the shared scheme)
- **Issue:** `FlareNoNotificationTests` built its `#expect(...)` messages with `"..." + "\(...)" + "..."`. Unlike XCTest's `@autoclosure () -> String`, Swift Testing's `Comment?` is `ExpressibleByStringLiteral` only — a `String` `+` expression is not a literal, so the file failed to compile (`cannot convert value of type 'String' to expected argument type 'Comment?'`), which broke the whole shared scheme build-for-testing.
- **Fix:** Collapsed both `#expect` messages to single string literals (interpolation still allowed inside one literal).
- **Files modified:** `Tests/EndoAppTests/FlareNoNotificationTests.swift`
- **Verification:** `xcodebuild build-for-testing` → `** TEST BUILD SUCCEEDED **`; the no-push guard still carries the full token set.
- **Committed in:** `327e52b` (folded into the Task 3 commit since it was discovered during Task 3's build gate)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** The fix was required for the shared scheme to compile for testing; no scope creep, no behavior change to the guard.

## Issues Encountered
- The `-only-testing:EndoAppTests/CopyAuditTests/<func>` filter executed 0 Swift-Testing tests (the function-level filter does not select `@Test`s the way XCTest method filters do). Resolved by running the whole `CopyAuditTests` suite, which confirmed both new alarmist controls pass (25/25 GREEN) with no regression.

## Verification Snapshot (the intended RED/GREEN posture)
- `CopyAuditTests` — 25/25 GREEN (the two new alarmist controls pass; no regression to the 23 prior scans).
- `FlareNoNotificationTests` — exists with the full locked token set; RED until Plan 02 ships `FlareEngine.swift` + `FlareCopy.swift` (file-set non-empty guard), as designed.
- `FlareEngineTests` — compiles via the placeholder shim; RED-at-runtime AND non-vacuous: the absence (suppressed/flat/under-evidence) + determinism assertions PASS against the `[]` shim, and the `risingWithEvidence_surfacesOneBoundedWarning` GREEN expectation correctly FAILS (shim returns 0, the contract demands 1). Plan 02 turns it GREEN.
- Full `EndoInsightsTests` — 114 tests, the ONLY failure being the intended RED rising-with-evidence assertion; zero Phase-5/6/7/11 regression.
- `xcodebuild build-for-testing` (the whole shared EndoApp scheme) — `** TEST BUILD SUCCEEDED **`.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- The honesty invariants are now executable and RED-pinned for Plan 02 (the FlareEngine producer + FlareCopy) and Plan 03 (the calm `.flare` dashboard section + composition into `InsightsEngine`).
- **For Plan 02:** ship `EndoInsights/Sources/EndoInsights/FlareEngine.swift` (`flareWarning(from:asOf:calendar:)` returning one bounded ≤.medium `flare.warning` for the rising-with-evidence case, `[]` otherwise) + `Sources/EndoApp/Features/Insights/FlareCopy.swift` (calm curated copy + a `FlareDriftGuard` asserting set-equality of the two alarmist mirrors), then DELETE the placeholder shim in `FlareEngineTests.swift` — that turns the FlareEngine suite + the no-push guard GREEN.
- **For Plan 03:** add the calm, omit-when-empty `.flare` section to `InsightsView` placed FIRST (D-07) and compose `FlareEngine` into the LOCKED `InsightsEngine.insights(...)` — that turns `testFlare_rendersFirst` GREEN.
- EndoInsights stays Foundation-only (no boundary regression — only test files + a doc-commented data list were added to the package).

## Self-Check: PASSED

- FOUND: `Tests/EndoAppTests/FlareNoNotificationTests.swift`
- FOUND: `EndoInsights/Tests/EndoInsightsTests/FlareEngineTests.swift`
- FOUND: `.planning/phases/12-flare-early-warning/12-01-SUMMARY.md`
- FOUND commits: `f80fff1`, `a00f6df`, `327e52b`

---
*Phase: 12-flare-early-warning*
*Completed: 2026-06-09*
