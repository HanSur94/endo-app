---
phase: 04-private-cloudkit-sync
verified: 2026-06-08T18:45:00Z
status: human_needed
score: 5/5 automatable must-haves verified (4 device-only DATA criteria → human_needed)
overrides_applied: 0
human_verification:
  - test: "DATA-02 — sync across the user's own devices via private CloudKit, no account/login"
    expected: "Log a symptom on Device A; within minutes it appears on Device B; no login was performed"
    why_human: "Requires >=2 real iOS devices signed into the SAME iCloud account + a set DEVELOPMENT_TEAM + a deployed CloudKit schema. CloudKit does not run in CI/Simulator (no iCloud account, no code-signing). Documented in 04-HUMAN-UAT.md Part B Scenario 1 + 04-VALIDATION.md Manual-Only."
  - test: "DATA-03a — offline edits reconcile with last-writer-wins, no lost records"
    expected: "Airplane-mode Device A and edit; edit a different field/day on Device B online; reconnect A; both survive with no record lost"
    why_human: "Requires real network toggling across 2 real devices on the same iCloud account; rides NSPersistentCloudKitContainer framework defaults that cannot be exercised in CI. 04-HUMAN-UAT.md Part B Scenario 2."
  - test: "DATA-03b — fresh install restores full history from private iCloud"
    expected: "Delete + reinstall the app on a signed-in device; full history restores automatically with no restore UI/code"
    why_human: "Requires a real device with a real iCloud account + deployed schema; automatic mirror-back is framework behavior not reachable in CI. 04-HUMAN-UAT.md Part B Scenario 3."
  - test: "DATA-04 — delete purges the CloudKit private DB (reinstall shows nothing)"
    expected: "Delete a day on Device A; it disappears on Device B; reinstall on a clean device; the deleted day does NOT reappear (cloud copy truly removed)"
    why_human: "The LOCAL delete-completeness half is automated and GREEN (EditDeleteTests); the actual cloud-purge proof needs a real device + iCloud + deployed schema. 04-HUMAN-UAT.md Part B Scenario 4."
  - test: "P-1 sign-out grace — app stays fully usable local-only on iCloud sign-out"
    expected: "Sign out of iCloud; the app still logs/reads locally with no crash/error wall; Settings shows 'iCloud sync: Off'. Accept the v1 trade-off that unsynced edits may be wiped on sign-out (iOS 18 by design)."
    why_human: "Requires a real iCloud sign-out on a device; the data-loss window is an accepted v1 trade-off requiring explicit user acknowledgement. 04-HUMAN-UAT.md Part C."
---

# Phase 4: Private CloudKit Sync — Verification Report

**Phase Goal:** Turn the local store into robust private iCloud sync across the user's own devices, surviving offline edits + reinstall without data loss, and truly purging the CloudKit private DB on delete — no account/login.
**Verified:** 2026-06-08T18:45:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

The phase goal splits cleanly into two halves:

1. **Automatable scope** — the private-only CloudKit configuration, account-aware sync gating, the account seam, the Settings sync line, and the DATA-04 local delete-completeness. **All 5 must-haves VERIFIED in the actual source + GREEN deterministic suite.**
2. **Device-only scope** — the 4 ROADMAP success criteria (multi-device sync, offline reconcile, reinstall restore, cloud delete-purge) are *inherently* multi-device + real-iCloud + real-Developer-team and CANNOT run in CI/XCUITest. **Correctly routed to human_needed**, documented in 04-HUMAN-UAT.md and 04-VALIDATION.md.

Per the verification status decision tree, because Step 8 produced human-verification items, status MUST be **human_needed** even though every automatable must-have is VERIFIED.

### Observable Truths (Automatable must-haves)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | CloudKit entitlement targets the private database ONLY — no public/shared scope | ✓ VERIFIED | `EndoApp.entitlements`: `icloud-services=[CloudKit]`, container `iCloud.com.endoapp.ios`, `aps-environment`, ubiquity-kvstore only. Grep for `public-database`/`shared-database`/`<string>public/shared` → no match. No `.public`/`.shared`/`.automatic` CloudKit scope anywhere in Sources/Tests (only an `.automatic` mention is a comment explaining what NOT to do). |
| 2 | Account-aware SyncMode resolved BEFORE container construction; `.none` forced on every local-only path (P-2 guard) | ✓ VERIFIED | `ModelContainer+Config.swift`: `SyncMode.resolve(_:)` reads the seam; `makeModelContainer(syncMode:)` computes `.private(...)` only when `.cloudKit`, else `.none`; the `url:` overload hard-forces `cloudKitDatabase: .none`. `AppDependencies.init()` resolves availability → `syncMode` BEFORE the first `makeModelContainer` and threads it through all 3 recovery tiers. SyncModeTests `localOnly_buildsContainer` GREEN (builds a usable `.none` store with the entitlement shipping). |
| 3 | AccountAvailability seam exists; token never logged | ✓ VERIFIED | `AccountAvailability.swift`: `protocol AccountAvailability: Sendable` + `SystemAccountAvailability` reading `FileManager.default.ubiquityIdentityToken != nil` (presence check only) + `StubAccountAvailability`. No `print`/log of the token. NOT `@MainActor` (read off-main at build time). Wired into Environment via `AccountAvailabilityKey` + injected in `EndoAppApp.swift`. |
| 4 | Settings shows a non-blocking "iCloud sync: On/Off" line reading the injected seam | ✓ VERIFIED | `SettingsView.swift:88-101`: `@Environment(\.accountAvailability)`, a Section with `Text(...isICloudAvailable ? "On" : "Off")` + `.accessibilityIdentifier("settings.sync.status")`, private-iCloud footer copy. No `ProgressView`/`Toggle`/conflict UI (D-12 non-blocking confirmed). |
| 5 | DATA-04 local fix: deleteDay removes all FactorReading rows, preserves Medication/TreatmentState definitions | ✓ VERIFIED | `Repository.deleteDay` (line 229-242) enumerates `factorReadings(on: day)` and `context.delete` each, alongside symptom/pain/bleeding/dayEntry; `factorReadings(on:)` applies NO `kindRaw` filter (all FactorKinds). Medication/TreatmentState untouched. EditDeleteTests asserts FactorReadings empty after delete AND that a saved Medication definition survives — both GREEN (TDD RED→GREEN per 04-02 SUMMARY). |

**Score:** 5/5 automatable truths verified. The 4 device-only DATA criteria are human_needed (see below).

### Device-Only Success Criteria → human_needed

The four ROADMAP success criteria are the phase's user-story outcomes. Each is inherently device-only and is captured (not faked) in the manual protocol:

| # | ROADMAP Success Criterion | Status | Routed To |
|---|---------------------------|--------|-----------|
| 1 | Data syncs across the user's own Apple devices via private CloudKit, no account/login | ⏸ HUMAN | 04-HUMAN-UAT Part B Scenario 1 |
| 2 | Offline edits reconcile (LWW), no lost records across two devices | ⏸ HUMAN | 04-HUMAN-UAT Part B Scenario 2 |
| 3 | Fresh install restores full history from private iCloud | ⏸ HUMAN | 04-HUMAN-UAT Part B Scenario 3 |
| 4 | Deleting data purges the CloudKit private DB (clean reinstall shows nothing) | ⏸ HUMAN | 04-HUMAN-UAT Part B Scenario 4 |

These cannot be asserted in CI: they need >=2 real devices on the same iCloud account, a set `DEVELOPMENT_TEAM`, and a deployed CloudKit schema. The codebase delivers all the inert-but-correct config + the local precondition; the live-cloud proof is the human-gated remainder.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `Sources/EndoApp/Resources/EndoApp.entitlements` | Private-DB-only iCloud + CloudKit + aps-environment | ✓ VERIFIED | Exact keys present; no public/shared scope |
| `Sources/EndoApp/Persistence/AccountAvailability.swift` | Sendable seam (protocol + System + Stub) | ✓ VERIFIED | Token presence-check only, never logged, NOT @MainActor |
| `Sources/EndoApp/Persistence/ModelContainer+Config.swift` | `resolve` + `makeModelContainer(syncMode:)` with explicit `.none` | ✓ VERIFIED | Caller-supplied syncMode; `.none` on local + url overload |
| `Sources/EndoApp/Persistence/Repository.swift` | deleteDay purges FactorReading-for-day | ✓ VERIFIED | factorReadings(on:) deletion wired in deleteDay; no over-delete |
| `Sources/EndoApp/Features/Settings/SettingsView.swift` | Non-blocking sync status line | ✓ VERIFIED | `settings.sync.status`, no spinner/toggle, reads seam |
| `Sources/EndoApp/App/AppDependencies.swift` | Resolve-before-construct + Environment key | ✓ VERIFIED | syncMode resolved before container; WR-02 recovery now safe under live CloudKit |
| `Sources/EndoApp/App/EndoAppApp.swift` | Inject `\.accountAvailability` | ✓ VERIFIED | Line 39 injects the seam |
| `project.yml` | CODE_SIGN_ENTITLEMENTS wired; DEVELOPMENT_TEAM commented | ✓ VERIFIED | Entitlements wired; only commented team placeholder |
| `Tests/EndoAppTests/SyncModeTests.swift` | resolve + P-2 guard + CI-localOnly | ✓ VERIFIED | 4 @Test cases, all GREEN |
| `Tests/EndoAppTests/EditDeleteTests.swift` | FactorReading-orphan + med-survives | ✓ VERIFIED | Both assertions present and GREEN |
| `.planning/phases/04-private-cloudkit-sync/04-HUMAN-UAT.md` | Device-only protocol for DATA-02/03/04 | ✓ VERIFIED | 5 scenarios + Part A setup + P-1 caveat + 5.1.3(ii) note; no fabricated @Test/XCUIApplication |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| AppDependencies | SyncMode.resolve | resolve account availability BEFORE makeModelContainer | ✓ WIRED | `SyncMode.resolve(availability)` then `makeModelContainer(syncMode: syncMode, ...)` |
| ModelContainer+Config | AccountAvailability | resolve consults the injected seam | ✓ WIRED | `static func resolve(_ availability: AccountAvailability)` |
| SettingsView | EnvironmentValues.accountAvailability | `@Environment(\.accountAvailability)` for the On/Off line | ✓ WIRED | Read at line 23, rendered at line 92 |
| Repository.deleteDay | FactorReading | enumerates factorReadings(on:) and deletes each | ✓ WIRED | Line 239 |
| EndoAppApp | accountAvailability | `.environment(\.accountAvailability, ...)` | ✓ WIRED | Line 39 |
| project.yml | EndoApp.entitlements | CODE_SIGN_ENTITLEMENTS | ✓ WIRED | Line 31 |
| 04-HUMAN-UAT.md | project.yml DEVELOPMENT_TEAM | team-setup prerequisite | ✓ WIRED | Part A1 references the commented placeholder |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Deterministic unit suite GREEN local-only (entitlement present, no DEVELOPMENT_TEAM) | `xcodegen generate && xcodebuild test -only-testing:EndoAppTests` | `** TEST SUCCEEDED **` — 87 tests in 18 suites, 0 failures | ✓ PASS |
| DATA-02 account-aware SyncMode suite | (part of above) | 4/4 GREEN: resolve cloudKit/localOnly, P-2 localOnly_buildsContainer, systemAvailability_inCI_resolvesLocalOnly | ✓ PASS |
| WR-02 store-recovery safety | (part of above) | "WR-02 store-recovery safety" suite passed (incl. discard_inMemory_skipsWipe) | ✓ PASS |
| Entitlement private-DB-only | `grep -Eni 'public-database|shared-database|<string>(public|shared)'` | no match | ✓ PASS |
| CI resolves local-only | `SystemAccountAvailability().isICloudAvailable` on Simulator | false → `.localOnly` (test green) | ✓ PASS |

Multi-device sync / offline / reinstall / cloud-purge are SKIP (route to human — cannot run without real devices + team + deployed schema).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| DATA-02 | 04-01, 04-02 | Data syncs across the user's own Apple devices via private iCloud, no account/login | ◐ Code-complete + HUMAN-UAT pending | Private-DB-only entitlement + account-aware SyncMode + Settings line VERIFIED; multi-device proof = human_needed (HUMAN-UAT Scenario 1). REQUIREMENTS.md traceability matches. |
| DATA-03 | 04-01, 04-03 | Sync survives multi-device, offline, reinstall without data loss | ◐ Code-complete + HUMAN-UAT pending | Runtime gate + account seam shipped; offline/reinstall/LWW ride NSPersistentCloudKitContainer defaults; proof = human_needed (HUMAN-UAT Scenarios 2-3). |
| DATA-04 | 04-02, 04-03 | Deleting data removes it from the private CloudKit store too | ◐ Code-complete + HUMAN-UAT pending | LOCAL completeness VERIFIED + locked by EditDeleteTests; cloud-purge proof = human_needed (HUMAN-UAT Scenario 4). |

No orphaned requirements: DATA-02/03/04 are the only IDs REQUIREMENTS.md maps to Phase 4, and all three are claimed across the plans.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| project.yml | 33 | Commented `# DEVELOPMENT_TEAM: ABCDE12345` | ℹ️ Info | Intentional D-02 user-setup placeholder (commented, never active). Keeps Simulator build unsigned-green. NOT a stub. |

No `TBD`/`FIXME`/`XXX` debt markers in any phase-modified source file. No `TODO`/`HACK`/`PLACEHOLDER`, no empty-implementation stubs, no hardcoded-empty render data. The Settings line and deleteDay read/write the live seam/store.

### Code-Review Findings Status (04-REVIEW.md)

0 Critical, 4 Warning at review time; all 4 Warnings + the Info items confirmed fixed in the actual code and git history:

- **WR-01** (PrivacyInfo.xcprivacy stale "Revisit in Phase 4"): replaced with a dated resolved note (CloudKit private-DB = user's own iCloud, empty collected-types remains truthful). VERIFIED in file.
- **WR-02** (store-recovery hardcoded filename unsafe under live CloudKit): `discardUnreadableStore` now discovers the URL from `ModelConfiguration` and is gated to `.localOnly` only; `.cloudKit` failures fall back to in-memory without wiping. VERIFIED in AppDependencies.swift + "WR-02 store-recovery safety" test suite GREEN. (commit e7cd187)
- **WR-03** (stale NotificationScheduler entitlement comment): corrected to note Phase 4 added aps-environment/Background-Modes for CloudKit push. VERIFIED. (commit 005e777)
- **WR-04** (setStress shared clampSeverity coupling): decoupled. VERIFIED. (commit 3b597a3)
- **IN-02** (Info.plist wrong decision id D-04): corrected to D-01/D-21. VERIFIED. (commit 08d9f0e)

### Human Verification Required

The four DATA success criteria plus the P-1 sign-out trade-off require real-hardware testing (see `human_verification` frontmatter for full step/expected detail):

1. **DATA-02 multi-device sync** — log on Device A, confirm on Device B, no login.
2. **DATA-03a offline reconcile** — airplane-mode A + edit, edit on B, reconnect, no lost records.
3. **DATA-03b reinstall restore** — delete+reinstall, full history restores from iCloud.
4. **DATA-04 cloud purge** — delete a day, confirm gone on B and on a clean reinstall.
5. **P-1 sign-out grace** — app usable local-only on iCloud sign-out; accept the unsynced-edit-loss v1 trade-off.

Prerequisites (one-time, per 04-HUMAN-UAT Part A): set `DEVELOPMENT_TEAM` in project.yml, register/auto-create the container, deploy the CloudKit schema Development→Production.

### Gaps Summary

**No gaps.** Every automatable must-have is VERIFIED against the actual source and a GREEN deterministic suite (87/87). The private-only CloudKit posture, the resolve-before-construct P-2 guard with explicit `.none` on every local path, the non-logging account seam, the Settings sync line, and the DATA-04 local delete-completeness all exist and function as designed. CI stays deterministically local-only (SystemAccountAvailability resolves `.localOnly`; suite green with the entitlement present and no team). All 4 code-review Warnings are fixed.

The four ROADMAP success criteria are not asserted in code because they are physically impossible to assert without >=2 real devices, a real Apple team, and a deployed CloudKit schema. They are honestly captured as a human-run protocol (no fabricated automated sync tests — D-16 honesty gate passes). Status is therefore **human_needed**, not gaps_found: the engineering is complete and correct; the remaining proof is device-gated and surfaced to the developer.

---

_Verified: 2026-06-08T18:45:00Z_
_Verifier: Claude (gsd-verifier)_
