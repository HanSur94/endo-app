---
phase: 01-foundations-schema-privacy-posture-claims-policy
verified: 2026-06-08T09:45:00Z
status: passed
score: 4/4 in-scope success criteria verified (SC5 gated/deferred per D-11)
mode: mvp
overrides_applied: 0
requirements_verified: [ONBD-01, ONBD-02, ONBD-03, ONBD-04, DATA-01]
deferred:
  - truth: "A throwaway TestFlight build + pre-submission review note validates the private-CloudKit-health-storage 5.1.3(ii) interpretation"
    addressed_in: "Gated on Apple Developer Program enrollment (D-11); follow-up after enrollment, not Phase 1 executable scope"
    evidence: "01-CONTEXT D-11 + ROADMAP Phase 1 note: 'GATED on Apple Developer enrollment ... the architecture that makes that de-risk winnable IS delivered here'"
  - truth: "HealthKit just-in-time consent UX"
    addressed_in: "Phase 9 — HealthKit Import (DATA-05)"
    evidence: "01-CONTEXT D-03 + REQUIREMENTS traceability (DATA-05 → Phase 9)"
---

# Phase 1: Foundations — Schema, Privacy Posture & Claims Policy — Verification Report

**Phase Goal:** Lock every irreversible foundational decision — a CloudKit-safe append-only SwiftData schema (bleeding/treatment first-class, cycle-day derived/optional, no `@Attribute(.unique)`), an explicit withdrawable GDPR consent flow, an in-app privacy policy, a claims/inclusive-language policy, and an early App Store 5.1.3(ii) de-risk (de-risk itself gated on Apple Developer enrollment per D-11).
**Verified:** 2026-06-08T09:45:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Live Build & Test Result

```
xcodegen generate            → Created project at EndoApp.xcodeproj
xcodebuild test ... iPhone 16 → ** TEST SUCCEEDED **
                               Test run with 13 tests in 5 suites passed (0.035s)
```

- Toolchain: Xcode 26.5 (full IDE), xcodegen at /opt/homebrew/bin/xcodegen — build/test commands run.
- Real iOS `.app` bundle produced (NOT an SPM CLI binary): `Build/Products/Debug-iphonesimulator/EndoApp.app`.
- Expected 13 tests / 5 suites — exactly matched. All green.

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth (Success Criterion) | Status | Evidence |
|---|---------------------------|--------|----------|
| 1 | Explicit plain-language consent screen; no health data processed until opt-in; withdrawable later | ✓ VERIFIED | `AppGate.swift` routes solely on `consent.state`; `WalkingSkeletonView` (only write path) constructed exclusively in `.consented` branch. `ConsentView` "I consent" calls `consent.grant(at:)`. `SettingsView` withdraw calls `consent.withdraw()` → `@Observable` store re-gates to onboarding. `Repository.assertConsented()` defence-in-depth. Tests: `ConsentGateTests` (fresh=notConsented, grant→consented, withdraw→notConsented, persists across reinstantiation) — 4 pass. |
| 2 | In-app privacy policy accurately describes on-device + private iCloud + no servers/third parties | ✓ VERIFIED | `PrivacyPolicyText.body` states on-device, private iCloud, no servers, no third-party sharing/analytics, names independent developer as data controller + contact email. Rendered offline in `PrivacyPolicyView` (no network), reachable from onboarding (sheet) AND Settings (NavigationLink). Tests: `PrivacyPolicyTests` (mentions clauses; names controller+contact) — 2 pass. |
| 3 | "Not medical advice / not a medical device" framing in onboarding; inclusive non-gendered language; user can customize identity/terminology | ✓ VERIFIED | `Disclaimer.text` (single source of truth) contains both phrases; `DisclaimerBanner` rendered in `OnboardingView` before the consent action. Default terminology neutral ("bleeding"/"cycle") via `Terminology`; `SettingsView` lets user edit bleeding term/pronouns/display name (optional, not a forced wall). `ClaimsPolicy.forbiddenGenderedTokens` enforces no gendered default copy. Tests: `DisclaimerTests` (1) + `TerminologyTests` (neutral defaults, customization changes copy, no gendered tokens) (3) — 4 pass. |
| 4 | SwiftData schema is `VersionedSchema`, all-optional/defaulted, no `@Attribute(.unique)`, bleeding/treatment first-class with cycle-day optional/derived, private-only CloudKit container initialized | ✓ VERIFIED | `SchemaV1: VersionedSchema` with `versionIdentifier`. All `@Model` attrs optional or defaulted; ZERO real `@Attribute(.unique)` (grep: only in rule comments). `BleedingEvent` + `TreatmentState` first-class, independent of cycle; NO `cycleDay`/`cyclePhase`/`= 28` anywhere (grep clean). `makeModelContainer` uses `ModelConfiguration(cloudKitDatabase:)` with `.none` (local-only, Phase 1 default) and `.private(_:)` branch reserved behind `SyncMode` flag. Tests: `SchemaRoundTripTests` (DayEntry round-trip, BleedingEvent round-trip, suppressed-8-months fixture with no phantom cycle) — 3 pass. |
| 5 | Throwaway TestFlight build + pre-submission review note validates 5.1.3(ii) interpretation, local-only fallback designed in | ⏸ DEFERRED (gated) | TestFlight step GATED on Apple Developer enrollment (D-11) — out of Phase 1 executable scope by design. The enabling architecture IS delivered: local-only fallback (`SyncMode.localOnly` → `.none`), private-only CloudKit reserved (`.private`, never shared/public, D-21), and HealthKit samples not copied into synced storage. Not counted as a gap. |

**Score:** 4/4 in-scope success criteria VERIFIED. SC5 is a documented, gated deferral (D-11) — not a gap.

### Deferred Items (not Phase-1 gaps)

| # | Item | Disposition | Evidence |
|---|------|-------------|----------|
| 1 | App Store 5.1.3(ii) TestFlight de-risk (SC5) | Gated on Apple Developer Program enrollment (D-11); enabling architecture delivered now | ROADMAP Phase 1 note; 01-CONTEXT D-11; 01-03-PLAN frontmatter note |
| 2 | HealthKit just-in-time consent UX | Phase 9 (DATA-05) per D-03 | REQUIREMENTS traceability; 01-CONTEXT D-03 |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `project.yml` | XcodeGen iOS 18 app + unit-test targets, `type: application` | ✓ VERIFIED | iOS 18.0 deployment, `type: application`, EndoAppTests bundle.unit-test, no executable/SPM target |
| `Sources/.../Resources/PrivacyInfo.xcprivacy` | Truthful manifest: no tracking, no collection, CA92.1 | ✓ VERIFIED | `NSPrivacyTracking false`, empty domains+collected-types, single CA92.1 UserDefaults reason |
| `Persistence/SchemaV1.swift` | VersionedSchema listing CloudKit-safe models | ✓ VERIFIED | `enum SchemaV1: VersionedSchema`, models = [BleedingEvent, TreatmentState, DayEntry] |
| `Persistence/Models/BleedingEvent.swift` | First-class append-only bleeding event | ✓ VERIFIED | `@Model`, all attrs optional/defaulted, UUID id, no cycle field |
| `Persistence/Models/TreatmentState.swift` | First-class suppression state independent of cycle | ✓ VERIFIED | `@Model`, `modeRaw` ("suppressed"), no cycle column |
| `Persistence/Models/DayEntry.swift` | Round-trip skeleton record | ✓ VERIFIED | `@Model`, optional/defaulted attrs |
| `Persistence/ModelContainer+Config.swift` | SyncMode flag → cloudKitDatabase (.none default, .private reserved) | ✓ VERIFIED | `.none` when localOnly, `.private(id)` when cloudKit (Phase 4 off) |
| `Persistence/Repository.swift` | Repository protocol + SwiftData impl | ✓ VERIFIED | `protocol Repository` + `SwiftDataRepository`, debug consent assert |
| `Consent/ConsentState.swift` | ConsentState value type | ✓ VERIFIED | `enum ConsentState { notConsented, consented(Date) }` |
| `Consent/ConsentStore.swift` | Protocol + UserDefaults impl, grant/withdraw | ✓ VERIFIED | `@Observable @MainActor`, outside synced store, NOT a `@Model` |
| `App/AppGate.swift` | Top-level consent route | ✓ VERIFIED | switches on `consent.state`; write path only in `.consented` |
| `App/AppDependencies.swift` | Composition root | ✓ VERIFIED | `makeModelContainer`, consent + terminology stores injected into Environment |
| `App/EndoAppApp.swift` | `@main` App rendering AppGate | ✓ VERIFIED | injects container + stores, AppGate root |
| `Features/Skeleton/WalkingSkeletonView.swift` | Post-consent write/read proof | ✓ VERIFIED | inserts DayEntry via modelContext + `@Query` reads back |
| `Content/Disclaimer.swift` | "not medical advice/device" single source | ✓ VERIFIED | both phrases + `DisclaimerBanner` |
| `Content/PrivacyPolicyText.swift` | Offline policy source of truth | ✓ VERIFIED | private iCloud, no servers, no third-party, controller + email |
| `Content/ClaimsPolicy.swift` | Allowed framing + forbidden verbs + gendered tokens | ✓ VERIFIED | three enforceable lists |
| `Terminology/TerminologyPreference.swift` | Codable pref, neutral defaults | ✓ VERIFIED | BleedingTerm enum, optional pronouns/displayName, UserDefaults store |
| `Terminology/Terminology.swift` | Neutral-default copy choke-point | ✓ VERIFIED | bleedingNoun/cycleNoun, auditable strings |
| `Features/Onboarding/OnboardingView.swift` | Warm onboarding + disclaimer + privacy link | ✓ VERIFIED | DisclaimerBanner + privacy sheet before ConsentView |
| `Features/Onboarding/ConsentView.swift` | Explicit consent calling grant | ✓ VERIFIED | "I consent" → `consent.grant(at: .now)` |
| `Features/Privacy/PrivacyPolicyView.swift` | Offline privacy screen | ✓ VERIFIED | renders PrivacyPolicyText, no network |
| `Features/Settings/SettingsView.swift` | Withdraw + terminology customization | ✓ VERIFIED | withdraw confirmation → `consent.withdraw()`, term/pronoun/name editors, privacy link |

### Key Link Verification

| From | To | Via | Status |
|------|----|----|--------|
| EndoAppApp | AppDependencies | composition root builds container + injects | ✓ WIRED |
| AppGate | ConsentStore | reads `consent.state` to route | ✓ WIRED |
| WalkingSkeletonView | DayEntry/modelContext | write + `@Query` read-back | ✓ WIRED |
| ModelContainer+Config | SchemaV1 | `Schema(versionedSchema: SchemaV1.self)` | ✓ WIRED |
| OnboardingView | Disclaimer/DisclaimerBanner | banner before consent | ✓ WIRED |
| OnboardingView | PrivacyPolicyView | privacy sheet before consent | ✓ WIRED |
| SettingsView | ConsentStore | `withdraw()` re-gates app | ✓ WIRED |
| SettingsView | TerminologyPreference | edits bleeding term/pronoun/name | ✓ WIRED |
| copy | Terminology | bleeding/cycle copy resolves through neutral lookup | ✓ WIRED |
| WalkingSkeletonView | SettingsView | gear toolbar NavigationLink | ✓ WIRED |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full test suite builds + runs | `xcodebuild test ... iPhone 16` | 13/13 pass, 5 suites | ✓ PASS |
| Real iOS app bundle (not SPM CLI) | find DerivedData for `EndoApp.app` | `EndoApp.app` dir found | ✓ PASS |
| No real `@Attribute(.unique)` | grep Sources/ | only in rule comments | ✓ PASS |
| No `cycleDay`/`cyclePhase`/`= 28` default | grep Sources/ | only in guard comments | ✓ PASS |
| No SPM executable target | grep project.yml | none | ✓ PASS |
| Consent state not a synced `@Model` | grep Consent/ for @Model | none (UserDefaults-backed) | ✓ PASS |

### Probe Execution

No `scripts/*/tests/probe-*.sh` declared or present for this phase. Probe verification is satisfied by the live `xcodebuild test` run above (the phase's TDD acceptance harness). N/A.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| ONBD-01 | 01-02, 01-03 | Explicit, withdrawable consent before health-data processing | ✓ SATISFIED | AppGate + ConsentStore + ConsentView + Settings withdraw; `ConsentGateTests` (4) |
| ONBD-02 | 01-03 | "Not medical advice / not a medical device" framing | ✓ SATISFIED | `Disclaimer.text` + DisclaimerBanner in onboarding; `DisclaimerTests` (1) |
| ONBD-03 | 01-03 | Inclusive non-assumptive language + terminology customization | ✓ SATISFIED | neutral `Terminology` defaults + Settings customization + ClaimsPolicy gendered-token guard; `TerminologyTests` (3) |
| ONBD-04 | 01-03 | In-app privacy policy: on-device + private iCloud + no third-party | ✓ SATISFIED | `PrivacyPolicyText` + offline `PrivacyPolicyView`; `PrivacyPolicyTests` (2) |
| DATA-01 | 01-02 | CloudKit-safe append-only SwiftData schema | ✓ SATISFIED | VersionedSchema, optional/defaulted, no .unique, first-class bleeding/treatment, .none container; `SchemaRoundTripTests` (3) |

All 5 Phase-1 requirements (per REQUIREMENTS traceability) are satisfied. No orphaned requirements.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| Info.plist | 4 | "placeholder" (D-09 display name) | ℹ️ Info | Intentional, documented branding placeholder — finalize at enrollment. Not a stub. |
| PrivacyPolicyText.swift | 14 | "placeholder" (D-13 contact email) | ℹ️ Info | Intentional placeholder email (user's own), documented. Test asserts the value. Not a stub. |

No `TBD`/`FIXME`/`XXX` debt markers. No `TODO`/`HACK`. No empty/stub implementations. No hardcoded-empty render data. The two "placeholder" hits are documented D-09/D-13 branding/identity placeholders, asserted by tests, not incomplete code.

### Human Verification Required

None required for a passing verdict. The schema, consent gate, privacy policy, disclaimer, and terminology behaviors are all proven by the 13 automated tests plus source/wiring inspection, and the app builds and launches as a real iOS bundle. (UI polish / end-to-end tap-through was already covered by the Plan 03 human-verify step; no blocking item remains.)

### Gaps Summary

No gaps. All four in-scope ROADMAP success criteria are verified against the live codebase and a passing 13-test suite. The fifth criterion (5.1.3(ii) TestFlight de-risk) is an explicitly gated deferral per decision D-11 — it is blocked only on Apple Developer Program enrollment (a user action), and the architecture that makes the de-risk winnable (local-only fallback, private-only CloudKit reserved behind a flag, no HealthKit data copied into synced storage) is fully delivered. HealthKit consent UX is correctly deferred to Phase 9 per D-03. Every irreversible foundational decision (D-16..D-22 schema rules, ONBD-01..04 privacy/consent posture) is locked in code.

**Verdict: PASS.**

---

_Verified: 2026-06-08T09:45:00Z_
_Verifier: Claude (gsd-verifier)_
