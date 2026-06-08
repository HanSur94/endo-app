# Deferred Items — Phase 06

Out-of-scope discoveries logged during execution (NOT fixed; not caused by the current plan's changes).

## Flaky UI test: `CheckInFlowUITests/testMedicationTaken_persistsAcrossReopen`

- **Found during:** Plan 06-03 full-suite regression run.
- **Symptom:** Intermittent `XCTAssertEqual failed: Optional("0") is not equal to Optional("1")` at `Tests/EndoAppUITests/CheckInFlowUITests.swift:211` — the medication-taken toggle reads OFF immediately after a tap (LIFE-03), a UI read-back timing race.
- **Pre-existing / unrelated:** Plan 06-03 changes are purely additive persistence (new `DismissedInsight` @Model, V3→V4 lightweight migration, consent-gated dismissal CRUD). They touch no UI, no check-in flow, and no medication code. Re-running the test in isolation passes (flaky, not a regression).
- **Disposition:** Deferred — needs a UI-test stabilization (e.g. wait-for-toggle-state assertion) outside this plan's scope. The unit suites (115 + 28 tests) and the other UI suites are green.
