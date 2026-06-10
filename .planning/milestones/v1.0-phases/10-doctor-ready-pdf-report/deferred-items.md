# Phase 10 — Deferred Items

Out-of-scope discoveries logged during execution (NOT fixed — SCOPE BOUNDARY rule).

## Pre-existing flaky XCUITest (unrelated to Plan 10-01)

- **Test:** `EndoAppUITests.CheckInFlowUITests.testMedicationTaken_persistsAcrossReopen`
- **File:** `Tests/EndoAppUITests/CheckInFlowUITests.swift:211`
- **Symptom:** `XCTAssertEqual failed: Optional("0") != Optional("1")` — the medication-taken toggle
  reads OFF immediately after a tap (LIFE-03). A UI-interaction timing/flakiness issue in the
  Phase-3 check-in flow E2E.
- **Why deferred:** Plan 10-01 (Wave 1) adds the PURE `ReportModel` + `ReportCopy` +
  `ReportRangeMapping` and tests only — it adds NO UI and touches NO medication flow. The failure
  is in a pre-existing, unrelated UI test target. All Swift Testing unit suites (200 + 96 tests,
  including the new Phase-10 ReportModelTests / ReportRenderSpikeTests / CopyAuditTests) pass.
- **Discovered during:** Plan 10-01 full-suite regression check.
