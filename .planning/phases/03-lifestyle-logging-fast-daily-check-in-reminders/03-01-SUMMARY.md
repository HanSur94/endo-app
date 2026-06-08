---
phase: 03-lifestyle-logging-fast-daily-check-in-reminders
plan: 01
subsystem: database
tags: [swiftdata, cloudkit-safe, swift6, usernotifications, migration, sendable, tdd]

# Dependency graph
requires:
  - phase: 02-core-symptom-cycle-bleeding-logging
    provides: "SymptomReading @Model template, Repository seam + assertConsented/maxNoteLength/clampSeverity, BleedingDay Sendable snapshot, SchemaV2 + lightweight MigrationPlan, Domain enum pattern (SymptomType/FlowIntensity), ClaimsPolicy copy choke-point, XCUITest harness"
provides:
  - "Domain factor enums: FactorKind, FoodTag, ActivityType (default->.other), ActivityIntensity, SleepQuality (failable->nil)"
  - "Sendable FactorDay per-day snapshot (the Phases 5-8 insights seam) with nil!=0 honesty"
  - "CloudKit-safe FactorReading + Medication @Models nested under SchemaV1 (soft-archive)"
  - "Additive SchemaV3 + lightweight v2toV3 MigrationStage; container bumped to V3"
  - "Repository factor + medication CRUD: setFood/setActivity/setSleep/setMedicationTaken/setPRN/setStress/setMood/checkInMarker + addMedication/archiveMedication/savedMedications + factorReadings(on:)/factorDays()"
  - "NotificationScheduler protocol + SystemNotificationScheduler + MockNotificationScheduler"
  - "Pure fatigue logic: isInQuietHours (same-day + midnight-crossing) + shouldFireToday auto-suppress"
  - "QuietHours value type (replaces unrepresentable ClosedRange<DateComponents>)"
  - "ReminderCopy constants (gentle/non-coercive)"
  - "a11y identifiers reserved for Plan 03-04: checkin.sheet/sameAsYesterday/section.*/confirmButton, home.checkInButton"
affects: [03-02, 03-03, 03-04, "Phase 4 CloudKit", "Phases 5-8 insights engine"]

# Tech tracking
tech-stack:
  added: [UserNotifications]
  patterns:
    - "Unified discriminated @Model (FactorReading kindRaw + generic payload columns) instead of one model per factor kind"
    - "Store-free Sendable per-day projection (FactorDay) as the insights boundary"
    - "Protocol-seam + pure-free-function split for testable fatigue logic"

key-files:
  created:
    - Sources/EndoApp/Domain/FactorKind.swift
    - Sources/EndoApp/Domain/FoodTag.swift
    - Sources/EndoApp/Domain/ActivityType.swift
    - Sources/EndoApp/Domain/ActivityIntensity.swift
    - Sources/EndoApp/Domain/SleepQuality.swift
    - Sources/EndoApp/Domain/FactorDay.swift
    - Sources/EndoApp/Persistence/Models/FactorReading.swift
    - Sources/EndoApp/Persistence/Models/Medication.swift
    - Sources/EndoApp/Persistence/SchemaV3.swift
    - Sources/EndoApp/Reminders/NotificationScheduler.swift
    - Tests/EndoAppTests/FactorLoggingTests.swift
    - Tests/EndoAppTests/MedicationTests.swift
    - Tests/EndoAppTests/FactorSnapshotTests.swift
    - Tests/EndoAppTests/NotificationSchedulerTests.swift
    - Tests/EndoAppUITests/CheckInFlowUITests.swift
  modified:
    - Sources/EndoApp/Persistence/MigrationPlan.swift
    - Sources/EndoApp/Persistence/ModelContainer+Config.swift
    - Sources/EndoApp/Persistence/Repository.swift
    - Tests/EndoAppTests/SchemaMigrationTests.swift
    - Tests/EndoAppTests/CopyAuditTests.swift

key-decisions:
  - "Quiet-hours window is a Sendable QuietHours{start,end} value type, NOT ClosedRange<DateComponents> (the planned type traps on a midnight-crossing window where start>end)"
  - "FactorReading column shape: kindRaw + valueRaw + amount + level + note; mapping locked in a model doc-comment"
  - "Food stores one .food row per tag + one note-bearing .food row for free-text; activity-intensity stored as level 0/1/2"
  - "Reminder body reworded to avoid the 'her' substring (e.g. 'here's') so it passes the forbiddenGenderedTokens audit"

patterns-established:
  - "FactorDay snapshot: fetch @Model rows, Calendar-bucket by start-of-day, defensive-decode into a store-free Sendable value (mirrors BleedingDay)"
  - "nil!=0 honesty enforced end-to-end: missing .stress/.mood row -> nil; a logged level-0 row -> 0"
  - "Reminder decisions as pure free functions (isInQuietHours/shouldFireToday); UNUserNotificationCenter is the thin untested shell"

requirements-completed: [LIFE-01, LIFE-02, LIFE-03, CHCK-02]

# Metrics
duration: 13min
completed: 2026-06-08
---

# Phase 3 Plan 01: Lifestyle-Logging Data Foundation + Reminder Seam Summary

**Typed factor data spine — unified FactorReading @Model, saved Medication (soft-archive), additive SchemaV3 + lightweight V2→V3 migration, the Sendable FactorDay insights snapshot, additive Repository factor/med CRUD, and a protocol-seam NotificationScheduler with pure quiet-hours/auto-suppress logic — all landed with passing Wave-0 tests.**

## Performance

- **Duration:** ~13 min
- **Started:** 2026-06-08T14:43:06Z
- **Completed:** 2026-06-08T14:56:12Z
- **Tasks:** 3
- **Files modified:** 20 (15 created, 5 modified)

## Accomplishments
- Six Foundation-only Domain types (5 enums + FactorDay) with defensive decode and copy-audit-safe labels.
- CloudKit-safe FactorReading + Medication @Models nested under SchemaV1; additive SchemaV3 with a lightweight v2toV3 migration stage; container bumped to V3.
- Additive Repository factor + saved-medication CRUD with consent gating, free-text bounding, and the store-free factorDays() projection.
- NotificationScheduler protocol + system impl + mock, plus pure, unit-tested quiet-hours (incl. midnight-crossing) and auto-suppress decisions.
- All Wave-0 unit scaffolds GREEN (75 unit tests pass); the CheckIn E2E compiles and is intentionally RED until Plan 03-04.

## Task Commits

Each task was committed atomically:

1. **Task 1: Domain enums + FactorReading/Medication @Models + SchemaV3 + V2→V3 migration** - `5fdb299` (feat)
2. **Task 2: Repository factor + medication CRUD + FactorDay reads** - `c9b6da9` (feat)
3. **Task 3: NotificationScheduler seam + pure fatigue logic + Wave-0 test scaffolds** - `b08395f` (feat)

## Files Created/Modified
- `Sources/EndoApp/Domain/FactorKind.swift` - food/activity/sleep/medication/stress/mood discriminator (default->.other).
- `Sources/EndoApp/Domain/FoodTag.swift` - curated endo food/drink categories (default->.other).
- `Sources/EndoApp/Domain/ActivityType.swift` - activity type set (default->.other).
- `Sources/EndoApp/Domain/ActivityIntensity.swift` - low/medium/high (failable->nil), text-labelled.
- `Sources/EndoApp/Domain/SleepQuality.swift` - poor/okay/good (failable->nil).
- `Sources/EndoApp/Domain/FactorDay.swift` - Sendable per-day snapshot; nil!=0 honesty contract.
- `Sources/EndoApp/Persistence/Models/FactorReading.swift` - unified @Model + kind->column mapping doc-comment.
- `Sources/EndoApp/Persistence/Models/Medication.swift` - saved-med definition, soft-archive via isArchived.
- `Sources/EndoApp/Persistence/SchemaV3.swift` - additive VersionedSchema (V2 set + 2 new models).
- `Sources/EndoApp/Persistence/MigrationPlan.swift` - added SchemaV3 + lightweight v2toV3 stage.
- `Sources/EndoApp/Persistence/ModelContainer+Config.swift` - container schema bumped to SchemaV3.
- `Sources/EndoApp/Persistence/Repository.swift` - factor + medication CRUD + factorReadings/factorDays reads.
- `Sources/EndoApp/Reminders/NotificationScheduler.swift` - protocol + system + mock + QuietHours + pure logic + ReminderCopy.
- `Tests/EndoAppTests/FactorLoggingTests.swift` - LIFE-01/02/03 round-trips via factorDays().
- `Tests/EndoAppTests/MedicationTests.swift` - saved-med CRUD + soft-archive history preservation.
- `Tests/EndoAppTests/FactorSnapshotTests.swift` - nil!=0 honesty + defensive decode.
- `Tests/EndoAppTests/NotificationSchedulerTests.swift` - quiet-hours (both windows) + auto-suppress + mock recording.
- `Tests/EndoAppTests/SchemaMigrationTests.swift` - added V2→V3 data-survival test.
- `Tests/EndoAppTests/CopyAuditTests.swift` - added Phase-3 factor/reminder copy audit.
- `Tests/EndoAppUITests/CheckInFlowUITests.swift` - E2E scaffold (RED until 03-04).

## Decisions Made
- **QuietHours value type over ClosedRange<DateComponents>:** the plan-mandated range type cannot hold a midnight-crossing window (start > end traps `ClosedRange`'s invariant, even via `uncheckedBounds`). A tiny `Sendable` struct preserves the literal start/end and lets `isInQuietHours` handle the wrap. No downstream plan had consumed the old type.
- **Reminder body wording:** "here's" contains the "her" substring, tripping `forbiddenGenderedTokens`. The body is now "Whenever you have a moment, your daily check-in is ready." — same gentle, non-coercive intent.
- FactorReading column shape and the kind→column mapping locked in the model doc-comment (Claude's discretion per D-01).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1/3 - Bug + Blocking] Replaced ClosedRange<DateComponents> quiet-hours window with a QuietHours value type**
- **Found during:** Task 3 (NotificationScheduler)
- **Issue:** The plan and the locked artifact list specify `quietHours: ClosedRange<DateComponents>?`. But `DateComponents` is not `Comparable`, and once made comparable, a midnight-crossing window (22:00–07:00) has `start > end`, which traps `ClosedRange`'s `lowerBound <= upperBound` precondition at runtime — `ClosedRange(uncheckedBounds:)` traps too (verified in-toolchain). The locked midnight-crossing requirement (D-19a / RESEARCH Open Question 2) is therefore unrepresentable as a `ClosedRange`.
- **Fix:** Introduced `struct QuietHours: Sendable, Equatable { start, end }` and changed the protocol/impl/mock + `isInQuietHours` signatures to take `QuietHours?`. Same semantics, no trap. This is the foundation plan, so no downstream consumer of the old type existed.
- **Files modified:** Sources/EndoApp/Reminders/NotificationScheduler.swift, Tests/EndoAppTests/NotificationSchedulerTests.swift
- **Verification:** NotificationSchedulerTests (same-day + midnight-crossing windows) pass; full unit suite green.
- **Committed in:** `b08395f` (Task 3 commit)

**2. [Rule 1 - Bug] Reworded reminder body to avoid the "her" substring**
- **Found during:** Task 3 (CopyAuditTests extension)
- **Issue:** The UI-SPEC body "...here's your daily check-in." contains "here's", whose substring "her" is in `ClaimsPolicy.forbiddenGenderedTokens` — failing `phase3Copy_containsNoGenderedToken`.
- **Fix:** Body changed to "Whenever you have a moment, your daily check-in is ready." (still gentle/non-coercive, D-21).
- **Files modified:** Sources/EndoApp/Reminders/NotificationScheduler.swift
- **Verification:** phase3Copy_containsNoGenderedToken passes.
- **Committed in:** `b08395f` (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking type conflict, 1 copy-audit correctness)
**Impact on plan:** Both were required for the code to compile/run and pass the locked copy audit. The QuietHours change is the only API-shape deviation from the plan and is isolated to the reminder seam; no scope creep.

## Issues Encountered
- The Task 2 verify gate runs three test suites whose files are listed under Task 3. To keep the TDD RED→GREEN gates honest, those three suites (FactorLogging/Medication/FactorSnapshot) were written during Task 2 and committed alongside the Task 3 scaffold set. All gates passed at their respective steps.

## User Setup Required
None - no external service configuration required. Local notifications need no entitlement/capability (project.yml unchanged).

## Next Phase Readiness
- The typed factor data spine + FactorDay snapshot are landed and unit-proven — Plans 03-02/03/04 (UI: day-log factor sections, reminders settings, check-in sheet) can build on the Repository surface and the reserved a11y identifiers.
- The CheckInFlowUITests E2E is RED-by-design and turns GREEN in Plan 03-04.
- A1 discipline closed for Phase-3 models (V2→V3 survival proven) ahead of Phase 4 CloudKit.

## Self-Check: PASSED

- All 15 created files verified present on disk (6 Domain, 2 @Models, SchemaV3, NotificationScheduler, 5 test files; SUMMARY).
- All 3 task commits verified in git history: `5fdb299`, `c9b6da9`, `b08395f`.
- `xcodebuild build` succeeds; `xcodebuild test -only-testing:EndoAppTests` = 75/75 pass; UI test target compiles (CheckIn E2E RED-by-design).

---
*Phase: 03-lifestyle-logging-fast-daily-check-in-reminders*
*Completed: 2026-06-08*
