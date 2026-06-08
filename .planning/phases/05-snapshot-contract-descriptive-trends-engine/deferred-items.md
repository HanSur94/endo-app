# Phase 05 — Deferred / Out-of-Scope Items

Items discovered during execution that are NOT caused by this phase's changes
(SCOPE BOUNDARY — logged, not fixed).

## Flaky UI test (pre-existing, unrelated to Plan 05-01)

- **Test:** `EndoAppUITests/CheckInFlowUITests/testMedicationTaken_persistsAcrossReopen`
- **Discovered during:** Plan 05-01 Task 1 (package-test-in-scheme smoke, full-suite run)
- **Symptom:** Under full-suite load the medication-taken `Switch` read `Optional("0")`
  (OFF) immediately after the tap that should have flipped it ON — an XCUITest
  tap/read timing race (`CheckInFlowUITests.swift:211`).
- **Proof it is a flake, not a regression:** the same test PASSED in isolation on retry
  (`xcodebuild test -only-testing:EndoAppUITests/CheckInFlowUITests/testMedicationTaken_persistsAcrossReopen`
  → "TEST SUCCEEDED", `/tmp/e2e/phase5-checkin-retry.log`).
- **Why out of scope:** Plan 05-01 adds only a Foundation-only SPM package + XcodeGen
  scheme test-target wiring — it touches no app logic, no medication/check-in code. The
  failure cannot be caused by these changes.
- **Disposition:** Deferred (UI-test stability hardening, candidate for a tap-then-poll
  assertion). Not blocking Phase 5.
