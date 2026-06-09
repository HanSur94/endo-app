# Phase 8 — Deferred Items

Out-of-scope discoveries logged during execution (NOT fixed — SCOPE BOUNDARY rule).

## Plan 08-01

### Pre-existing UI-test failure: `CheckInFlowUITests/testMedicationTaken_persistsAcrossReopen`

- **Discovered during:** 08-01 phase-gate full-suite run.
- **Symptom:** `XCTAssertEqual failed: ("Optional("0")") is not equal to ("Optional("1")")` at
  `Tests/EndoAppUITests/CheckInFlowUITests.swift:211` — the SwiftUI medication "taken" `Toggle`
  in the check-in Form does not read ON after the simulated trailing-edge taps (the test already
  retries up to 4 times; its own comment notes "a SwiftUI Toggle in a Form can swallow a centre tap").
- **Why out of scope:** This is a Phase 3 (LIFE-03) medication-toggle UI-interaction test. Plan 08-01
  touched ONLY `EndoInsights/Insight.swift` (additive Codable), the persistence schema/migration/repo
  (`SchemaV5`, `CachedInsight`, `MigrationPlan`, `ModelContainer+Config`, `Repository`), and tests —
  no UI, check-in, or medication code (verified via `git diff --name-only`). The failure reproduces
  in isolation and is unrelated to the schema bump or Codable change.
- **Recommendation:** Investigate the toggle tap-coordinate / wait timing in the check-in Form UI test
  harness (likely a simulator/Form hit-testing quirk) under a separate maintenance task. EndoAppTests
  (116/116) and EndoInsights (96/96) are fully green; this is the only red and it is a UI-automation
  interaction issue, not a logic/data-layer regression.
