---
phase: 01-foundations-schema-privacy-posture-claims-policy
plan: 02
subsystem: persistence-and-consent
tags: [ios, swiftdata, swiftui, versionedschema, cloudkit-safe, consent-gate, gdpr, composition-root, tdd-green]

# Dependency graph
requires:
  - "01-01 — buildable iOS 18 XcodeGen scaffold + EndoAppTests target + RED DATA-01/ONBD-01 tests"
provides:
  - "CloudKit-safe VersionedSchema SchemaV1 (BleedingEvent/TreatmentState/DayEntry, all optional/defaulted, no @Attribute(.unique), app-generated UUID)"
  - "makeModelContainer(inMemory:) behind the SyncMode flag — .none local-only default, .private reserved for Phase 4"
  - "Repository protocol + SwiftDataRepository (the D-22 testable persistence seam) with a debug consent precondition"
  - "ConsentState + ConsentStore protocol + @Observable UserDefaultsConsentStore (grant/withdraw, injectable suite, flag outside the synced store)"
  - "AppDependencies composition root + \\.consentStore EnvironmentKey injection (D-22)"
  - "AppGate consent routing (WalkingSkeletonView only in the .consented branch) + walking-skeleton write/read screen"
  - "DATA-01 (SchemaRoundTripTests) and ONBD-01 (ConsentGateTests) suites GREEN"
affects: [01-03, "Phase 2 (logging UI builds on Repository + schema)", "Phase 4 (flips SyncMode → .cloudKit)"]

# Tech tracking
tech-stack:
  added: []   # all-first-party Apple frameworks; no application package installs (privacy posture)
  patterns:
    - "@Model types nested under SchemaV1 (referenced as SchemaV1.DayEntry etc.) — versioned from v1"
    - "VersionedSchema.versionIdentifier as `static let` (Swift 6 concurrency-safe immutable shared state)"
    - "Consent store is @MainActor-isolated so it is safe as a SwiftUI Environment value under Swift 6"
    - "Control flags (consent) live in UserDefaults, deliberately outside the SwiftData/CloudKit store"
    - "Composition root (AppDependencies) builds container + services; views read injected dependencies"

key-files:
  created:
    - Sources/EndoApp/Persistence/SchemaV1.swift
    - Sources/EndoApp/Persistence/Models/BleedingEvent.swift
    - Sources/EndoApp/Persistence/Models/TreatmentState.swift
    - Sources/EndoApp/Persistence/Models/DayEntry.swift
    - Sources/EndoApp/Persistence/ModelContainer+Config.swift
    - Sources/EndoApp/Persistence/Repository.swift
    - Sources/EndoApp/Consent/ConsentState.swift
    - Sources/EndoApp/Consent/ConsentStore.swift
    - Sources/EndoApp/App/AppDependencies.swift
    - Sources/EndoApp/App/AppGate.swift
    - Sources/EndoApp/Features/Skeleton/WalkingSkeletonView.swift
  modified:
    - Sources/EndoApp/App/EndoAppApp.swift
    - Tests/EndoAppTests/SchemaRoundTripTests.swift
    - Tests/EndoAppTests/ConsentGateTests.swift

key-decisions:
  - "Nested @Model types under SchemaV1 via `extension SchemaV1` so tests' `SchemaV1.DayEntry` references resolve and the schema is versioned from v1 (D-16)"
  - "UserDefaultsConsentStore backs `state` with a stored @Observable property mirrored to UserDefaults (durable source of truth) so SwiftUI re-routes on grant/withdraw"
  - "ConsentStore made @MainActor (Rule 3) — required for Swift 6 Environment-value Sendability; the gate is inherently a main-actor UI concern anyway"
  - "VersionedSchema.versionIdentifier declared `static let` (Rule 3) to satisfy Swift 6 strict-concurrency"

requirements-completed: [DATA-01, ONBD-01]

# Metrics
duration: ~20min
completed: 2026-06-08
---

# Phase 1 Plan 02: CloudKit-safe Schema + Consent Gate + Walking Skeleton Summary

**The walking skeleton's real end-to-end happy path: a CloudKit-safe `VersionedSchema` (bleeding/treatment first-class, no `.unique`, all optional/defaulted, no phantom cycle field), a local-only `ModelContainer` behind the `SyncMode` flag, a `UserDefaults`-backed consent gate that blocks all health-data processing until opt-in, and a post-consent screen that writes one `DayEntry` and reads it back — turning the DATA-01 and ONBD-01 suites GREEN.**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-06-08
- **Tasks:** 2 (both `tdd="true"`; RED gates pre-existed from 01-01, this plan delivered GREEN)
- **Files:** 11 created + 3 modified

## Accomplishments

- **Task 1 (DATA-01, GREEN):** Implemented `SchemaV1: VersionedSchema` (D-16) with three CloudKit-safe `@Model` types nested under it — `BleedingEvent` (append-only event, D-18; first-class, D-19), `TreatmentState` (first-class suppression state, independent of cycle, D-19), and `DayEntry` (skeleton round-trip record). Every stored property is optional or defaulted; **zero `@Attribute(.unique)`**; identity is an app-generated `UUID` (D-20); **no `cycleDay`/`cyclePhase`/`cycleLength` and no `= 28` default anywhere** (the #1 foundational pitfall guard). Added `makeModelContainer(inMemory:)` driven by the `SyncMode` flag — `.none` (local-only, Simulator-friendly, no entitlement) by default, `.private("iCloud.com.endoapp.ios")` reserved for Phase 4 (D-10/D-21). Added the `Repository` protocol + `SwiftDataRepository` (the D-22 testable seam) with a debug consent precondition. Turned `SchemaRoundTripTests` GREEN, **including the suppressed/no-bleeding fixture**.
- **Task 2 (ONBD-01, GREEN):** Implemented `ConsentState` (`notConsented` / `consented(Date)`) and the `ConsentStore` protocol + `@Observable UserDefaultsConsentStore` (grant/withdraw, injectable `UserDefaults` suite, flag stored **outside** the synced store — RESEARCH Pattern 3). Wired the `AppDependencies` composition root (builds the `ModelContainer` + `ConsentStore`, exposes `\.consentStore` via a custom `EnvironmentKey`, D-22). `AppGate` routes on `consent.state`; **`WalkingSkeletonView` is constructed only in the `.consented` branch** (threat T-01-03). `EndoAppApp` now renders `AppGate` as the root with the injected environment + `.modelContainer`. `WalkingSkeletonView` writes one `DayEntry` and reads it back via `@Query`. Turned `ConsentGateTests` GREEN (fresh = notConsented, grant = consented, withdraw = notConsented, plus a durability-across-reinstantiation test).

## Task Commits

1. **Task 1: CloudKit-safe VersionedSchema + models + local-only container (DATA-01)** — `e84e8ed` (feat)
2. **Task 2: Consent gate + composition root + skeleton write/read (ONBD-01)** — `ee8b29e` (feat)

## Test Results (GREEN)

```
xcodebuild test ... -only-testing:EndoAppTests/SchemaRoundTripTests -only-testing:EndoAppTests/ConsentGateTests
✔ ONBD-01 consent gate — 4 tests passed
✔ DATA-01 schema round-trip — 3 tests passed
✔ Test run with 7 tests in 2 suites passed
** TEST SUCCEEDED **
```

Build: `xcodebuild -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' build` → **BUILD SUCCEEDED** (real `AppGate` root).

## Reference Surfaces (for downstream plans)

- **Model field lists:**
  - `SchemaV1.BleedingEvent`: `id: UUID = UUID()`, `occurredAt: Date = Date()`, `flowRaw: String?`, `note: String?`
  - `SchemaV1.TreatmentState`: `id: UUID = UUID()`, `startedAt: Date = Date()`, `modeRaw: String?`
  - `SchemaV1.DayEntry`: `id: UUID = UUID()`, `date: Date = Date()`, `note: String?`
- **Repository protocol surface:** `func add(_ entry: SchemaV1.DayEntry) throws` · `func allDayEntries() throws -> [SchemaV1.DayEntry]`
- **Consent injection mechanism:** custom `EnvironmentKey` exposing `EnvironmentValues.consentStore` (`any ConsentStore`); store built in `AppDependencies` and injected via `.environment(\.consentStore, dependencies.consentStore)`. The model container is injected via SwiftUI's built-in `.modelContainer(_:)`.
- **Container flag:** `SyncMode.current == .localOnly` → `cloudKitDatabase: .none` (Phase 1). Flip to `.cloudKit` in Phase 4.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `VersionedSchema.versionIdentifier` non-concurrency-safe under Swift 6**
- **Found during:** Task 1 (first `xcodebuild test` run)
- **Issue:** `static var versionIdentifier = Schema.Version(1,0,0)` (the shape shown in RESEARCH Pattern 1) failed to compile under the Swift 6 language mode: *"static property 'versionIdentifier' is not concurrency-safe because it is nonisolated global shared mutable state."*
- **Fix:** Declared it `static let` (the protocol requirement is get-only, so an immutable `let` satisfies it and is Sendable-safe).
- **Files modified:** `Sources/EndoApp/Persistence/SchemaV1.swift`
- **Commit:** `e84e8ed`

**2. [Rule 3 - Blocking] `ConsentStore` not usable as a SwiftUI Environment value under Swift 6**
- **Found during:** Task 2 (build for `ConsentGateTests`)
- **Issue:** A custom `EnvironmentKey` whose `defaultValue` is `any ConsentStore` failed: *"static property 'defaultValue' is not concurrency-safe because non-'Sendable' type 'any ConsentStore' may have shared mutable state."*
- **Fix:** Made `ConsentStore` and `UserDefaultsConsentStore` `@MainActor`-isolated (consent state is inherently a main-actor UI concern that drives SwiftUI routing) and constructed the key's default via `MainActor.assumeIsolated`. Annotated `ConsentGateTests` `@MainActor` so the suite can call `grant`/`withdraw`/read `state`.
- **Files modified:** `Sources/EndoApp/Consent/ConsentStore.swift`, `Sources/EndoApp/App/AppDependencies.swift`, `Tests/EndoAppTests/ConsentGateTests.swift`
- **Commit:** `ee8b29e`

**Total deviations:** 2 auto-fixed (both Rule 3 blocking, both Swift-6-strict-concurrency adaptations of verified RESEARCH Pattern shapes). No architectural or scope changes; the RESEARCH shapes are otherwise implemented as written.

## Threat Mitigations Delivered

- **T-01-03 (processing health data before consent):** `AppGate` blocks all health-data processing until `.consented`; `WalkingSkeletonView` (the only write path) is reachable only from the `.consented` branch; `withdraw()` re-gates (proven by `ConsentGateTests`).
- **T-01-04 (consent/health state pushed to CloudKit):** consent flag in `UserDefaults`, not a synced `@Model`; container ships `.none` (no sync) in Phase 1.
- **T-01-05 (unsafe schema breaking future CloudKit sync):** schema CloudKit-safe from v1 — no `@Attribute(.unique)`, all optional/defaulted, `VersionedSchema`; grep-gated.
- **T-01-06 (health values leaked via logs):** no `DayEntry`/`BleedingEvent` field values are logged in the write/read path; no analytics/crash SDK present.

## Known Stubs

- `AppGate`'s `.notConsented` branch shows a minimal inline `ConsentPromptView` (an "I consent" button). This is intentional per the plan — Plan 03 replaces it with the warm onboarding flow + full disclaimer (ONBD-02) + privacy link (ONBD-04). Does not block this plan's goal (the consent gate functions and is tested).
- `Sources/EndoApp/Features/Skeleton/PlaceholderRootView.swift` from 01-01 is now unreferenced (root is `AppGate`). Left in place (harmless, still compiles); Plan 03 / cleanup can remove it.

## Self-Check: PASSED

(See below.)
