---
phase: 01-foundations-schema-privacy-posture-claims-policy
plan: 01
subsystem: infra
tags: [ios, swiftui, swiftdata, xcodegen, swift-testing, privacy-manifest, scaffold, tdd-red]

# Dependency graph
requires: []
provides:
  - "Real iOS 18 .xcodeproj scaffold generated from a committed XcodeGen project.yml (no SPM executable target)"
  - "Buildable @main SwiftUI app (EndoApp) that launches in the Simulator with a placeholder root"
  - "Truthful Privacy Manifest (PrivacyInfo.xcprivacy) shipping from v1: no tracking, no off-device collection, CA92.1 UserDefaults reason"
  - "EndoAppTests Swift Testing target wired into the EndoApp scheme"
  - "Five FAILING (RED) requirement test files (DATA-01, ONBD-01..04) that Plans 02/03 turn GREEN"
affects: [01-02, 01-03, "all later phases (every plan builds on this scaffold + the SyncMode/consent seams)"]

# Tech tracking
tech-stack:
  added: [XcodeGen (build-time dev tool), Swift Testing, SwiftUI, Privacy Manifest]
  patterns:
    - "XcodeGen project.yml is the committed source of truth; *.xcodeproj is gitignored and regenerated"
    - "Nyquist Wave 0: every Phase-1 requirement gets a RED test file up front; later plans turn RED -> GREEN"
    - "Placeholder-fail RED tests (#expect(Bool(false), ...)) compile immediately without production symbols"

key-files:
  created:
    - project.yml
    - .gitignore
    - Sources/EndoApp/Resources/Info.plist
    - Sources/EndoApp/Resources/PrivacyInfo.xcprivacy
    - Sources/EndoApp/App/EndoAppApp.swift
    - Sources/EndoApp/Features/Skeleton/PlaceholderRootView.swift
    - Tests/EndoAppTests/SchemaRoundTripTests.swift
    - Tests/EndoAppTests/ConsentGateTests.swift
    - Tests/EndoAppTests/TerminologyTests.swift
    - Tests/EndoAppTests/DisclaimerTests.swift
    - Tests/EndoAppTests/PrivacyPolicyTests.swift
  modified: []

key-decisions:
  - "Kept SWIFT_VERSION 6.0 as planned — no Wave 0 pin needed; built cleanly with Xcode 26.5 (RESEARCH A3 resolved)"
  - "Used Simulator device 'iPhone 16' (booted, iOS 26.5 runtime); deployment target stays iOS 18 (<= SDK is fine)"
  - "Added GENERATE_INFOPLIST_FILE: YES to the EndoAppTests target so the unit-test bundle code-signs (Rule 3 blocking fix)"

patterns-established:
  - "Pattern: project.yml committed, *.xcodeproj gitignored + regenerated via xcodegen generate"
  - "Pattern: composition-root stub in EndoAppApp (@main) that Plan 02 expands (ModelContainer + services + AppGate)"
  - "Pattern: RED-first requirement tests, one Swift Testing suite per requirement ID, each naming the Plan that turns it GREEN"

requirements-completed: []  # RED only this plan — DATA-01/ONBD-01 go GREEN in Plan 02/03, ONBD-02/03/04 in Plan 02/03. NONE delivered (passing) here by design.

# Metrics
duration: ~12min
completed: 2026-06-08
---

# Phase 1 Plan 01: Walking-Skeleton Foundation Summary

**Real iOS 18 XcodeGen `.xcodeproj` that builds for the iPhone 16 Simulator with a truthful Privacy Manifest, a placeholder SwiftUI app, and five RED Swift Testing files (one per Phase-1 requirement) that compile and fail by design.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-06-08T08:13Z (approx)
- **Completed:** 2026-06-08T08:35Z (approx)
- **Tasks:** 3 (Task 1 checkpoint verified-as-satisfied; Tasks 2 & 3 executed)
- **Files modified:** 11 created + project.yml amended

## Accomplishments

- **Task 1 (checkpoint, satisfied):** Verified the full toolchain — Xcode 26.5 (build 17F42) active at `/Applications/Xcode.app/...`, XcodeGen 2.45.4, and a booted **iPhone 16** Simulator on the iOS 26.5 runtime. The `autonomous:false` environment checkpoint was pre-satisfied, so execution proceeded without pausing.
- **Task 2 (scaffold):** Created `project.yml` (iOS 18 `application` target `EndoApp` + `bundle.unit-test` `EndoAppTests`, no SPM executable target), `.gitignore` (ignores the regenerated `*.xcodeproj`), `Info.plist` (display name "Endo-App", D-09), a truthful `PrivacyInfo.xcprivacy` (NSPrivacyTracking false, empty collected types, CA92.1 UserDefaults reason — T-01-01 mitigation), the `@main` composition-root stub, and the placeholder root view. **`xcodebuild ... build` SUCCEEDED** for `platform=iOS Simulator,name=iPhone 16`.
- **Task 3 (RED tests):** Created the five Swift Testing files (DATA-01, ONBD-01, ONBD-02, ONBD-03, ONBD-04). **All 11 tests across 5 suites COMPILE and FAIL (RED)** — exactly correct for Nyquist Wave 0. No production source files were created (the RED state is the point).

## Task Commits

1. **Task 2: Scaffold iOS 18 XcodeGen project + Privacy Manifest + placeholder app** — `f26a013` (feat)
2. **Task 3: Five FAILING (RED) requirement test files** — `dd5881b` (test) — TDD RED gate; also carries the Rule-3 `project.yml` fix

_Task 1 was a verified-as-satisfied checkpoint (no commit — no files produced)._
_This plan is RED-only by design: there is no GREEN/`feat` commit for the requirement behaviors — Plans 02/03 supply production symbols and turn the tests GREEN._

## Files Created/Modified

- `project.yml` — XcodeGen spec: iOS 18 EndoApp app + EndoAppTests unit-test target; placeholder bundle id `com.endoapp.ios`; no iCloud/CloudKit/Background-Modes capability (Phase 4)
- `.gitignore` — ignores `*.xcodeproj`, DerivedData, build artifacts
- `Sources/EndoApp/Resources/Info.plist` — minimal iOS plist, CFBundleDisplayName "Endo-App", no HealthKit/usage strings
- `Sources/EndoApp/Resources/PrivacyInfo.xcprivacy` — truthful manifest (no tracking, empty collected types, CA92.1)
- `Sources/EndoApp/App/EndoAppApp.swift` — `@main` SwiftUI App, composition-root stub rendering PlaceholderRootView
- `Sources/EndoApp/Features/Skeleton/PlaceholderRootView.swift` — trivial launch-proof view
- `Tests/EndoAppTests/SchemaRoundTripTests.swift` — DATA-01 (DayEntry/BleedingEvent round-trip + suppressed-no-bleeding fixture)
- `Tests/EndoAppTests/ConsentGateTests.swift` — ONBD-01 (notConsented/grant/withdraw)
- `Tests/EndoAppTests/TerminologyTests.swift` — ONBD-03 (neutral defaults, customization, no gendered tokens)
- `Tests/EndoAppTests/DisclaimerTests.swift` — ONBD-02 ("not medical advice"/"not a medical device")
- `Tests/EndoAppTests/PrivacyPolicyTests.swift` — ONBD-04 (on-device + private iCloud + no third-party sharing)

## Decisions Made

- **SWIFT_VERSION stays 6.0** — RESEARCH Assumption A3 flagged a possible Wave-0 pin; none was needed. The project built cleanly under Xcode 26.5 with Swift 6.0 language mode. No adjustment made.
- **Simulator device = "iPhone 16"** (booted, iOS 26.5 runtime). Recorded for Plans 02/03 `-destination` strings. Deployment target remains iOS 18 (≤ the installed iOS 26.5 SDK, which is correct).
- **Bundle id = `com.endoapp.ios`** (D-09 placeholder, flagged in `project.yml` to finalize at branding/enrollment).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] EndoAppTests unit-test bundle could not code-sign (missing Info.plist)**
- **Found during:** Task 3 (running the RED suite)
- **Issue:** `xcodebuild test` failed: *"Cannot code sign because the target does not have an Info.plist file and one is not being generated automatically"* for `EndoAppTests`. The unit-test bundle had no hand-written Info.plist and Info.plist generation was not enabled on it.
- **Fix:** Added `GENERATE_INFOPLIST_FILE: YES` to the `EndoAppTests` target settings in `project.yml`, regenerated the project, and re-ran the suite.
- **Files modified:** `project.yml`
- **Verification:** `xcodebuild test ... -only-testing:EndoAppTests` then ran the suite — 11 tests compiled and failed (RED), no build error.
- **Committed in:** `dd5881b` (Task 3 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary to make the test target buildable/runnable so the RED state is observable. No scope creep — the app target was unaffected and still has its explicit hand-written Info.plist.

## Issues Encountered

- XcodeGen emits the empty-test-target source warning before Task 3 adds files; harmless — generation still succeeds and the build/run is gated on the app target. Resolved naturally once the test files landed.

## TDD Gate Compliance

This plan's `type: execute` with a single `tdd="true"` Wave-0 task that is **RED-only by design** (the plan explicitly forbids creating production stubs). The RED gate is satisfied: a `test(...)` commit (`dd5881b`) exists and the suite **fails**. There is intentionally **no GREEN `feat(...)` commit** for these behaviors in this plan — Plans 02 and 03 add the production symbols (`SchemaV1`, `ConsentStore`, `Terminology`, `Disclaimer`, `PrivacyPolicyText`) and turn each suite GREEN. This is the documented Nyquist Wave 0 pattern, not a missing-gate defect.

## Known Stubs

- `Sources/EndoApp/Features/Skeleton/PlaceholderRootView.swift` and the `EndoAppApp` body are intentional placeholders (the walking-skeleton launch proof). Plan 02 replaces the root with the real `AppGate` + composition root. Documented and intentional — does not block this plan's goal (a buildable, launchable, testable shell).

## User Setup Required

None blocking — the Task 1 environment prerequisites (full Xcode, XcodeGen, an iOS 18+ iPhone Simulator) were already installed and verified. The placeholder bundle id and display name (D-09) and the hosted privacy-policy URL (D-15) remain deferred to branding/enrollment/submission, not now.

## Next Phase Readiness

- **Ready for Plan 02 (Wave 2):** a buildable iOS 18 app + the `EndoAppTests` target are in place. Plan 02 adds the CloudKit-safe `VersionedSchema` (`SchemaV1`), the local-only `ModelContainer` behind the `SyncMode` flag, the `ConsentStore`/`ConsentState` gate, and the skeleton write/read — turning `SchemaRoundTripTests` and `ConsentGateTests` GREEN.
- **Destination for later plans:** `-destination 'platform=iOS Simulator,name=iPhone 16'`.
- **No blockers.** Apple Developer enrollment (CloudKit sync, TestFlight, 5.1.3(ii) de-risk) remains correctly gated out of Phase 1.

## Self-Check: PASSED

All 11 created files exist on disk; both task commits (`f26a013`, `dd5881b`) are present in git history.

---
*Phase: 01-foundations-schema-privacy-posture-claims-policy*
*Completed: 2026-06-08*
