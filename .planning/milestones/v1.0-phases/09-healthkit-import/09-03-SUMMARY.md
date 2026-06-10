---
phase: 09-healthkit-import
plan: 03
subsystem: health-import
tags: [healthkit, entitlement, nshealthshareusagedescription, hkanchoredobjectquery, swift6-globalactor, rationale-before-sheet, provenance-badge, claimspolicy, xcuitest-fixture-seam]
requires:
  - phase: 09-01
    provides: "HealthDataReading seam, HealthDomain read-type sets, ImportedSampleValue/ImportedPayload/ValueSource Sendable value contracts, HealthSampleMapping pure HK->app tables, ImportedSample @Model under SchemaV6"
  - phase: 09-02
    provides: "HealthImportService isolated actor (auth+anchored pull+persist+recompute), HealthAnchorStore, FixtureHealthDataReading two-pull fixture, Repository.replaceOrInsertImported + daySnapshotSources(on:) + manual-wins merge"
  - phase: 08
    provides: "InsightsCoordinator.dataChanged() recompute seam (D-12)"
provides:
  - "Read-only HealthKit capability shipping via the entitlements file + NSHealthShareUsageDescription Info.plist string (NO write/update key — PLAT-01 deferred)"
  - "SystemHealthDataReading — the real HKHealthStore-backed HealthDataReading behind a @globalActor isolation domain; async toShare:[] auth + anchored 365-day backfill / incremental pull mapping HKSample -> Sendable values inside the continuation"
  - "HealthRationaleView — in-app rationale (read-only/private/optional) shown BEFORE Apple's system sheet (D-06); Continue is the only proceed path"
  - "HealthConnectSection — passive contextual connect affordance; previously-decided users deep-link to Settings (D-08), no in-app re-request, no nag, no read-status probe"
  - "HealthSourceBadge — 'from Apple Health' provenance pill (symbol+text, never color-alone, D-03)"
  - "HealthConnectCopy — ClaimsPolicy-audited copy enum + the source-of-truth NSHealthShareUsageDescription literal"
  - "AppDependencies \\.healthImportService injection + -uitest-health-seed/-denied/-empty fixture selection (paired with -uitest-reset)"
  - "HealthConnectUITests — E2E proof of the whole flow on the Simulator via the fixture seam (no real device)"
affects:
  - "Phase 9 verification gate (the real-device read + system auth sheet is the deferred human-verify)"
  - "Any future logging-surface that wants the contextual HealthConnectSection affordance or the HealthSourceBadge"
tech-stack:
  added: []
  patterns: [globalactor-isolated-hkhealthstore, rationale-before-system-sheet, passive-no-probe-connect-affordance, separate-provenance-read-to-badge, claimspolicy-audited-copy-enum, launch-arg-fixture-seam-paired-with-inmemory-store]
key-files:
  created:
    - Sources/EndoApp/Health/SystemHealthDataReading.swift
    - Sources/EndoApp/Health/HealthConnectCopy.swift
    - Sources/EndoApp/Features/Health/HealthRationaleView.swift
    - Sources/EndoApp/Features/Health/HealthConnectSection.swift
    - Sources/EndoApp/Features/Health/HealthSourceBadge.swift
    - Tests/EndoAppTests/HealthConnectCopyAuditTests.swift
    - Tests/EndoAppUITests/HealthConnectUITests.swift
  modified:
    - Sources/EndoApp/Resources/EndoApp.entitlements
    - Sources/EndoApp/Resources/Info.plist
    - Sources/EndoApp/App/AppDependencies.swift
    - Sources/EndoApp/App/EndoAppApp.swift
    - Sources/EndoApp/Features/Settings/SettingsView.swift
    - Sources/EndoApp/Features/Home/HomeView.swift
key-decisions:
  - "HKHealthStore isolated behind a dedicated @globalActor (HealthStoreActor) rather than inside SystemHealthDataReading-as-actor, so the store is a single serialized app-wide resource while the seam stays a plain Sendable type"
  - "Read-only proven THREE ways: empty .access array + no write entitlement, toShare:[] at runtime, and NSHealthUpdateUsageDescription absent (grep-gated count 0)"
  - "'Previously decided' is a device-local UserDefaults flag (health.connect.decided), NOT a HealthKit read-status probe (Apple hides read status — Pitfall 1 / T-09-12); the flag only chooses rationale-vs-deep-link, never what data to show"
  - "The provenance badge renders on the HomeView day row when daySnapshotSources reports any .appleHealth field — a SEPARATE app-side read, never a read-status branch"
  - "persist/recompute closures run on the HealthImportService actor's executor; the dedicated ModelContext is confined to that single-threaded actor (no MainActor hop for persist), recompute hops via Task { @MainActor }"
patterns-established:
  - "globalactor-isolated-hkhealthstore: the non-Sendable store + samples never escape; only Sendable values + the opaque anchor cross the seam"
  - "rationale-before-system-sheet: a bespoke in-app rationale screen is the ONLY path to requestAuthorization (D-06)"
  - "passive-no-probe-connect-affordance: graceful denial via a device-local decided-flag + a Settings deep-link, never a read-status probe, never a nag"
  - "launch-arg-fixture-seam-paired-with-inmemory-store: -uitest-health-* selects FixtureHealthDataReading ONLY alongside -uitest-reset, so the fixture can never touch a real store (T-09-06)"
requirements-completed: [DATA-05]
duration: ~95min
completed: 2026-06-09
---

# Phase 9 Plan 03: HealthKit Import — Permission UX + Provenance + Entitlement Summary

**The payoff slice: read-only HealthKit ships (entitlement + share usage string, no write key), a real `HKHealthStore` reader isolated behind a `@globalActor`, an in-app rationale BEFORE Apple's system sheet, graceful denial that keeps the app fully usable, a "from Apple Health" provenance badge, a "Refresh from Health" action — all E2E-provable on the Simulator via the `-uitest-health-*` fixture seam.**

## Performance

- **Duration:** ~95 min
- **Started:** 2026-06-09T17:30Z (approx)
- **Completed:** 2026-06-09T19:05Z (approx)
- **Tasks:** 3 automatable executed + committed; 1 deferred human-verify recorded (auto-approved)
- **Files modified:** 13 (7 created, 6 modified)

## Accomplishments

- **Read-only entitlement + usage string** ship through the entitlements file + Info.plist (grep-gated: `NSHealthShareUsageDescription` present = 1, `NSHealthUpdateUsageDescription` absent = 0, `com.apple.developer.healthkit` present). No write/share key anywhere (PLAT-01 deferred, T-09-14).
- **`SystemHealthDataReading`** — the real reader: `HKHealthStore` lives behind `@HealthStoreActor`; `requestAuthorization(toShare:[], read:)` (read-only); `HKAnchoredObjectQuery` for the D-13 365-day backfill / D-09 incremental pull, mapping each `HKSample` → `Sendable` `ImportedSampleValue` **inside** the continuation so no `HKSample`/`HKHealthStore` escapes (Pitfall 2).
- **Rationale-first permission (SC2):** `HealthRationaleView` states read-only + private + optional and is the ONLY path to `requestAuthorization`; `HealthConnectSection` presents it as a sheet, never the system sheet cold.
- **Graceful denial (SC3/D-08):** the connect affordance is passive; a previously-decided user deep-links to the system Settings app (HK reads can't be re-requested in-app); the app stays fully usable via manual logging; an empty/denied read leaves insights at the warm `insights.empty` state, never "no symptoms".
- **Provenance (D-03):** `HealthSourceBadge` ("from Apple Health", symbol+text) renders on a HomeView day row whose merged source is `.appleHealth` (via the 09-02 `daySnapshotSources`).
- **Refresh (D-10):** a "Refresh from Health" Settings row pulls across domains and relies on the service's `dataChanged()` recompute (D-12).
- **Provability:** `HealthConnectUITests` exercises rationale-before-sheet, denied-stays-usable, provenance badge, and refresh-pulls-recomputes entirely on the Simulator via the fixture seam — the real OS sheet + real-device data are the only deferred items.

## Task Commits

1. **Task 1: entitlement + usage string + SystemHealthDataReading + AppDependencies wiring** — `c3f1edb` (feat)
2. **Task 2: contextual connect UX (rationale, denial deep-link, badge, Refresh)** — `9b82354` (feat)
3. **Task 3 (TDD): ClaimsPolicy audit + E2E**
   - `4ccc287` (test) — RED/test gate: HealthConnectCopy audit
   - `b2b711e` (feat) — GREEN/feature gate: HealthConnectUITests + the actor-isolation + flag-leak fixes
4. **Task 4:** deferred real-device human-verify (no code; recorded below, auto-approved under AUTO_MODE)

## Files Created/Modified

- `Sources/EndoApp/Health/SystemHealthDataReading.swift` (created) — real `HealthDataReading`; `@globalActor`-isolated `HKHealthStore`; anchored pull mapping samples to Sendable values inside the continuation.
- `Sources/EndoApp/Health/HealthConnectCopy.swift` (created) — ClaimsPolicy-audited copy enum + the NSHealthShareUsageDescription source-of-truth literal.
- `Sources/EndoApp/Features/Health/HealthRationaleView.swift` (created) — the rationale screen (D-06); Continue → requestAuthorization → importNew.
- `Sources/EndoApp/Features/Health/HealthConnectSection.swift` (created) — passive connect affordance; decided-flag → Settings deep-link (D-08).
- `Sources/EndoApp/Features/Health/HealthSourceBadge.swift` (created) — the "from Apple Health" pill (D-03).
- `Tests/EndoAppTests/HealthConnectCopyAuditTests.swift` (created) — the copy choke-point (T-09-15).
- `Tests/EndoAppUITests/HealthConnectUITests.swift` (created) — the four E2E behaviors via the fixture seam.
- `Sources/EndoApp/Resources/EndoApp.entitlements` (modified) — `com.apple.developer.healthkit` + empty `.access`; NO write key.
- `Sources/EndoApp/Resources/Info.plist` (modified) — `NSHealthShareUsageDescription` only.
- `Sources/EndoApp/App/AppDependencies.swift` (modified) — construct `HealthImportService` over the selected reader; `-uitest-health-*` fixture selection; clear `health.connect.decided` under `-uitest-reset`; inject `\.healthImportService`.
- `Sources/EndoApp/App/EndoAppApp.swift` (modified) — inject `\.healthImportService` at the App root.
- `Sources/EndoApp/Features/Settings/SettingsView.swift` (modified) — "Apple Health" section (connect affordance + Refresh row).
- `Sources/EndoApp/Features/Home/HomeView.swift` (modified) — render the provenance badge on `.appleHealth`-sourced day rows.

## Decisions Made

- **Single `@globalActor` for the store** (not store-inside-actor) keeps `SystemHealthDataReading` a plain `Sendable` seam while the non-`Sendable` `HKHealthStore` stays serialized app-wide.
- **Read-only is triple-proven** (empty `.access` + `toShare:[]` + update-key absent grep gate).
- **"Previously decided" is a device-local flag**, never a read-status probe — honoring Apple's hidden read status (T-09-12 / Pitfall 1).
- **The badge reads provenance via the separate `daySnapshotSources` read**, leaving the `EndoInsights.DaySnapshot` contract untouched (minimal blast radius, carried from 09-02).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `MainActor.assumeIsolated` in the persist/recompute closures crashed the app**
- **Found during:** Task 3 (first E2E run)
- **Issue:** The `HealthImportService` `persist`/`recompute` closures run SYNCHRONOUSLY on the actor's executor, NOT the main actor. `MainActor.assumeIsolated` therefore tripped its precondition and terminated the app ("cannot request screenshot data because it does not exist"), failing 3 of 4 E2E tests.
- **Fix:** The dedicated `ModelContext` is confined to the single-threaded import-service actor, so `persist` calls the repo directly (no main-actor hop); `recompute` hops to the `@MainActor` coordinator via a fire-and-forget `Task { @MainActor in coordinator.dataChanged() }` (the recompute is async/debounced by contract).
- **Files modified:** Sources/EndoApp/App/AppDependencies.swift
- **Verification:** All 4 `HealthConnectUITests` GREEN.
- **Committed in:** `b2b711e` (Task 3 feature gate)

**2. [Rule 1 - Bug] `health.connect.decided` flag leaked across same-Simulator UI runs**
- **Found during:** Task 3 (denied-test run)
- **Issue:** `@AppStorage("health.connect.decided")` persists in UserDefaults. `-uitest-reset` cleared only `consent.grantedAt`, so a prior test that tapped connect left the flag `true`, flipping the affordance to the `health.connect.openSettings` id and failing the next run's `health.connect.button` assertion.
- **Fix:** `-uitest-reset` now also clears `health.connect.decided`, so every UI run starts at the fresh passive connect affordance.
- **Files modified:** Sources/EndoApp/App/AppDependencies.swift
- **Verification:** Full `HealthConnectUITests` suite GREEN across repeated runs.
- **Committed in:** `b2b711e` (Task 3 feature gate)

**3. [Rule 3 - Blocking] `HealthConnectCopy` landed in Task 2 (build dependency), so the Task-3 RED audit was GREEN on first run**
- **Found during:** Task 3 (TDD RED step)
- **Issue:** The Task-2 connect UI cannot build without the copy enum it renders, so `HealthConnectCopy` was created in Task 2 as a legitimate build dependency (the plan explicitly allows Task 1/2 to land the audited literal and "let Task 3's audit test cover it"). The TDD "RED" audit therefore passed immediately rather than failing.
- **Fix:** Recorded under TDD Gate Compliance below — the audit was curated clean (verified by an independent pre-write forbidden-verb/gendered-token scan), so it is a genuine GREEN, not a skipped RED. The `test(...)` commit still precedes the `feat(...)` commit to preserve the gate ordering.
- **Files modified:** (none beyond the planned files)
- **Committed in:** `9b82354` (copy enum, Task 2) / `4ccc287` (audit test, Task 3)

---

**Total deviations:** 3 auto-fixed (2 bugs, 1 blocking/sequencing).
**Impact on plan:** Both bugs were necessary for a correct, green E2E. The copy-enum sequencing is an explicit plan allowance, not scope creep. No architectural changes.

## TDD Gate Compliance

Task 3 was `tdd="true"`. Gate ordering preserved: `test(09-03)` commit `4ccc287` (the `HealthConnectCopyAuditTests`) precedes `feat(09-03)` commit `b2b711e` (the E2E + fixes). The audit passed GREEN on first run because its subject (`HealthConnectCopy`) was a legitimate Task-2 build dependency — per the TDD fail-fast rule this was investigated: the copy was independently scanned for forbidden verbs + gendered tokens BEFORE writing the test, so the GREEN is genuine (the feature/literal already existed and is correct), not a mis-targeted or skipped test. No REFACTOR commit needed.

## Human-Verify (Deferred, Auto-Approved under AUTO_MODE)

**Task 4 — Real-device HealthKit read + system authorization sheet.** Same deferred posture as Phase 4's HUMAN-UAT and Phase 8's chart-visual human-verify. The Simulator E2E (`HealthConnectUITests`) proves the structural flow against the fixture seam; the REAL OS authorization sheet and a REAL device's Health sample data cannot be exercised in CI (Simulator HealthKit has no realistic menstrual/sleep/workout/nutrition data, and the entitlement is inert until a `DEVELOPMENT_TEAM` is set).

To verify on a real iPhone with Health data (after setting `DEVELOPMENT_TEAM` in `project.yml` and regenerating): connect each type from Settings → "Connect Apple Health", confirm the in-app rationale appears BEFORE Apple's sheet (read-only/private/optional, Continue-only); grant and confirm imported values appear "from Apple Health" and flow into insights; tap "Refresh from Health"; on a fresh run deny and confirm the app stays fully usable and insights never read "no symptoms".

Recorded in `09-VALIDATION.md` (row 09-03-T4: "manual (deferred, auto-approved)") and surfaced to the phase verification gate. Does NOT block CI. **Auto-approved under AUTO_MODE.**

## Verification

| Check | Command | Result |
|-------|---------|--------|
| Read-only grep gates | `grep -c NSHealthShareUsageDescription/NSHealthUpdateUsageDescription Info.plist` + entitlement grep | share=1, update=0, healthkit present |
| App build | `xcodebuild build … -scheme EndoApp` | BUILD SUCCEEDED |
| Copy audit | `-only-testing:EndoAppTests/HealthConnectCopyAuditTests` | GREEN (5 tests) |
| E2E (fixture seam) | `-only-testing:EndoAppUITests/HealthConnectUITests` | GREEN (4 tests) |
| Full unit regression | `-only-testing:EndoAppTests` | GREEN (183 tests, 0 failures) |

## Known Stubs

None that block the plan goal. The real-device read + the OS authorization sheet are DEFERRED human-verify items (documented above), not stubs — the entire flow is implemented and proven via the fixture seam. The badge surfaces on a day that already has a `DayEntry` (manual-wins): an imported-only day with no manual `DayEntry` does not appear in the HomeView day-history list (which is `@Query DayEntry`-driven); this matches the manual-wins model and is the same surface the E2E exercises (log a symptom → refresh → badge on that day).

## Threat Surface Scan

No new security surface beyond the plan's `<threat_model>`. Mitigations landed as specified: T-09-12 (no read-status probe — the decided-flag only picks rationale-vs-deep-link), T-09-13 (all connect/rationale/badge UI is reachable only inside the `.consented` shell — Settings/Home are built solely there), T-09-14 (read-only: empty `.access` + `toShare:[]` + update-key absent grep gate), T-09-15 (HealthConnectCopy routed through HealthConnectCopyAuditTests), T-09-06 (the `-uitest-health-*` fixture is selected ONLY alongside `-uitest-reset`, the in-memory store). No package installs (T-09-SC — zero new third-party packages).

## Next Phase Readiness

- DATA-05 is fully implemented and Simulator-provable; the phase's only outstanding item is the deferred real-device human-verify (Task 4), surfaced to the phase gate.
- `/gsd-verify-work` can run: the full app suite + the package suite are GREEN.

## Self-Check: PASSED

All 7 created source/test files + the SUMMARY present on disk; all 4 task commits (c3f1edb, 9b82354, 4ccc287, b2b711e) present in git history.

---
*Phase: 09-healthkit-import*
*Completed: 2026-06-09*
