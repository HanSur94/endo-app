---
phase: 01-foundations-schema-privacy-posture-claims-policy
plan: 03
subsystem: privacy-claims-inclusive-language
tags: [ios, swiftui, onboarding, consent-withdraw, privacy-policy, inclusive-language, claims-policy, gdpr, tdd-green]

# Dependency graph
requires:
  - "01-01 — buildable iOS 18 XcodeGen scaffold + EndoAppTests + RED ONBD-02/03/04 tests"
  - "01-02 — ConsentStore (\\.consentStore EnvironmentKey, grant/withdraw), AppGate routing, AppDependencies composition root, WalkingSkeletonView"
provides:
  - "Disclaimer.text single source of truth ('not medical advice / not a medical device') + reusable DisclaimerBanner (ONBD-02, D-05)"
  - "PrivacyPolicyText.body offline privacy policy (on-device + private iCloud, no servers, no third-party sharing/analytics, names controller + contact email) (ONBD-04, D-13/14)"
  - "ClaimsPolicy: allowedFraming + forbiddenVerbs + forbiddenGenderedTokens — enforceable claims/language checklist (Pitfall 5)"
  - "TerminologyPreference (Codable, neutral .bleeding default, optional pronouns/displayName) + TerminologyStore (UserDefaults, @Observable, injectable suite) (ONBD-03, D-06/07/08)"
  - "Terminology neutral-default lookup (bleedingNoun/cycleNoun) — the copy choke-point all bleeding/cycle copy resolves through (D-08)"
  - "OnboardingView (warm, short, disclaimer + privacy link before consent), ConsentView (explicit grant), PrivacyPolicyView (offline screen), SettingsView (withdraw + terminology + privacy link)"
  - "AppGate .notConsented -> OnboardingView; main shell routes to SettingsView; \\.terminologyStore EnvironmentKey"
  - "ONBD-02/03/04 GREEN + ONBD-01 withdraw-in-Settings (D-04) — all six Phase-1 requirement suites GREEN"
affects: ["Phase 2 (logging UI resolves bleeding/cycle copy through Terminology)", "Phase 5+ (insight surfaces reuse DisclaimerBanner)", "submission (hosted privacy URL D-15 mirrors PrivacyPolicyText)"]

# Tech tracking
tech-stack:
  added: []   # all-first-party Apple frameworks; no application package installs (privacy posture)
  patterns:
    - "Copy choke-point: all bleeding/cycle copy resolves through Terminology (enforceable inclusive-language audit, Pitfall 5)"
    - "Claims policy encoded as DATA (ClaimsPolicy lists) so the posture is testable, not just documented"
    - "TerminologyStore is @MainActor @Observable + injectable UserDefaults suite (same pattern as ConsentStore), exposed via \\.terminologyStore EnvironmentKey"
    - "Free-text identity fields (display name/pronouns) bounded to maxDisplayNameLength; empty -> nil (T-01-05)"
    - "Withdraw consent uses the @Observable ConsentStore state flip to re-route AppGate to onboarding (no explicit navigation)"

key-files:
  created:
    - Sources/EndoApp/Content/Disclaimer.swift
    - Sources/EndoApp/Content/PrivacyPolicyText.swift
    - Sources/EndoApp/Content/ClaimsPolicy.swift
    - Sources/EndoApp/Terminology/TerminologyPreference.swift
    - Sources/EndoApp/Terminology/Terminology.swift
    - Sources/EndoApp/Features/Onboarding/OnboardingView.swift
    - Sources/EndoApp/Features/Onboarding/ConsentView.swift
    - Sources/EndoApp/Features/Privacy/PrivacyPolicyView.swift
    - Sources/EndoApp/Features/Settings/SettingsView.swift
  modified:
    - Sources/EndoApp/App/AppGate.swift
    - Sources/EndoApp/App/AppDependencies.swift
    - Sources/EndoApp/App/EndoAppApp.swift
    - Sources/EndoApp/Features/Skeleton/WalkingSkeletonView.swift
    - Tests/EndoAppTests/TerminologyTests.swift
    - Tests/EndoAppTests/DisclaimerTests.swift
    - Tests/EndoAppTests/PrivacyPolicyTests.swift
  removed:
    - Sources/EndoApp/Features/Skeleton/PlaceholderRootView.swift

key-decisions:
  - "Test contract wins on naming: exposed Terminology.bleedingNoun/cycleNoun (the RED test scaffold names from 01-01), with bleedingWord/cycleWord aliases for the plan-body call sites"
  - "Cycle noun is always 'cycle' (D-08) regardless of bleeding-term choice — bleeding-term customization only changes the bleeding word, never the neutral cycle word"
  - "SettingsView is pushed onto the shell's NavigationStack (no own NavigationStack) to avoid nested-stack title duplication"
  - "Removed the now-unreferenced PlaceholderRootView.swift (01-01 leftover; 01-02 already moved the root to AppGate)"
  - "Withdraw re-gating relies on the @Observable ConsentStore (state flip re-renders AppGate) — no imperative navigation needed"

requirements-completed: [ONBD-02, ONBD-03, ONBD-04, ONBD-01]

# Metrics
duration: ~16min
completed: 2026-06-08
---

# Phase 1 Plan 03: Privacy / Claims / Inclusive-Language Slice Summary

**The privacy/claims/inclusive-language slice on top of the walking skeleton: a warm, short onboarding surfacing the "not medical advice / not a medical device" disclaimer and an offline privacy-policy link before the explicit consent action; a neutral-default `Terminology` copy choke-point with optional customization in Settings; a claims/language policy encoded as enforceable data; and a Settings screen that withdraws consent (re-gating the app) and customizes terminology/pronoun/display name — turning the last three RED suites (ONBD-02/03/04) GREEN and completing the D-04 withdraw-in-Settings half of ONBD-01. All six Phase-1 requirement suites are GREEN and the iPhone 16 Simulator build is green.**

## Performance

- **Duration:** ~16 min
- **Completed:** 2026-06-08
- **Tasks:** 3 (Tasks 1–2 executed; Task 3 human-verify satisfied via automated proof per the hands-off run directive)
- **Files:** 9 created + 7 modified + 1 removed

## Accomplishments

- **Task 1 (ONBD-02/03/04, GREEN):** Created the content + lookups as single sources of truth — `Disclaimer.text` (contains both "not medical advice" and "not a medical device") + reusable `DisclaimerBanner`; `PrivacyPolicyText.body` (on-device, private iCloud, no servers, no third-party sharing/analytics, names the independent developer as data controller with contact `sannahrush@googlemail.com`); `ClaimsPolicy` (allowedFraming + forbiddenVerbs + forbiddenGenderedTokens, the enforceable Pitfall-5 checklist); `TerminologyPreference` (Codable, `.bleeding` neutral default, optional pronouns/displayName) + `TerminologyStore` (UserDefaults-backed, `@Observable`, injectable suite); and `Terminology` (neutral-default `bleedingNoun`/`cycleNoun` lookup all copy resolves through). Turned the three RED test files into real assertions, with the terminology copy-audit reading the gendered-token set from `ClaimsPolicy.forbiddenGenderedTokens`. **The three suites went GREEN (6 tests).**
- **Task 2 (ONBD-01 withdraw / D-04 + ONBD-02/04 surfacing, GREEN):** Built the warm `OnboardingView` (validating non-clinical welcome, `DisclaimerBanner` before consent, privacy-policy sheet, copy routed through `Terminology`), `ConsentView` (explicit "I consent" → `consentStore.grant(at:)`), `PrivacyPolicyView` (offline scrollable screen), and `SettingsView` (withdraw consent with a plain explanation → re-gates the app; bleeding-term picker + pronouns + display-name fields; privacy link). Wired `AppGate.notConsented → OnboardingView`, added a gear toolbar route from the shell to Settings, injected `TerminologyStore` via a new `\.terminologyStore` EnvironmentKey, and removed the unreferenced `PlaceholderRootView`. **`xcodebuild ... build` SUCCEEDED** and the **FULL suite went GREEN.**
- **Task 3 (human-verify, satisfied via automated proof):** Per the hands-off run directive, the end-to-end human walkthrough was delegated to automated evidence rather than a manual Simulator pass — see *Human-Verify Satisfied via Automated Proof*.

## Task Commits

1. **Task 1: claims/terminology/privacy content + neutral lookups (ONBD-02/03/04 GREEN)** — `bb967c7` (feat)
2. **Task 2: warm onboarding + privacy screen + Settings (withdraw + terminology) wired into AppGate** — `5d23a87` (feat)

## Test Results (FULL SUITE GREEN)

```
xcodebuild test -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16'
✔ ONBD-01 consent gate — 4 tests passed
✔ ONBD-02 disclaimer presence — 1 test passed
✔ ONBD-03 neutral terminology — 3 tests passed
✔ ONBD-04 privacy policy text — 2 tests passed
✔ DATA-01 schema round-trip — 3 tests passed
✔ Test run with 13 tests in 5 suites passed
** TEST SUCCEEDED **
```

Build: `xcodebuild -project EndoApp.xcodeproj -scheme EndoApp -destination 'platform=iOS Simulator,name=iPhone 16' build` → **BUILD SUCCEEDED** (real `OnboardingView` in the `.notConsented` branch).

## Human-Verify Satisfied via Automated Proof

This `autonomous:false` plan ends with a `checkpoint:human-verify`. For this hands-off run the end-to-end verification was delegated to automated evidence; an optional manual Simulator walkthrough remains available to the user. Each manual check maps to automated proof:

| Manual check (Task 3) | Automated proof |
|---|---|
| App launches into a gated warm onboarding; tone warm/plain (D-01) | `BUILD SUCCEEDED`; `AppGate.notConsented` renders `OnboardingView` (grep-verified); copy is validating/non-clinical and routes through `Terminology` |
| "Not medical advice / not a medical device" disclaimer visible (ONBD-02) | `DisclaimerTests.disclaimer_containsRequiredFraming` GREEN; `OnboardingView` renders `DisclaimerBanner` before consent (grep ≥ 1) |
| Privacy link opens an offline screen: on-device + private iCloud, no servers, no third-party sharing, names developer + email (ONBD-04) | `PrivacyPolicyTests` (2 tests) GREEN — asserts on-device, private iCloud, no servers, no third-party/analytics, "data controller", and the exact contact email; `PrivacyPolicyView` reachable from onboarding **and** Settings (grep) |
| No bleeding/cycle copy uses she/women/lady (ONBD-03) | `TerminologyTests.defaults_containNoGenderedTokens` GREEN — audits every default resolved string against `ClaimsPolicy.forbiddenGenderedTokens` |
| Cannot reach a logging/skeleton screen before "I consent" (ONBD-01 gate) | `ConsentGateTests` GREEN; by construction `WalkingSkeletonView` is built only in `AppGate.consented` (T-01-03 invariant preserved) |
| "I consent" → skeleton appears; "Log a day" writes + reads back (DATA-01) | `ConsentGateTests` (grant → consented) + `SchemaRoundTripTests` (write/read round-trip) GREEN; `ConsentView` calls `consentStore.grant(at:)` |
| Settings: change bleeding term to "period" updates copy; name/pronouns persist (D-07) | `TerminologyTests.customizingBleedingTerm_changesResolvedCopy` GREEN; `SettingsView` edits `TerminologyPreference` via the `@Observable` `TerminologyStore` (UserDefaults-persisted) |
| Settings: "Withdraw consent" shows explanation → returns to gated onboarding (D-04) | `ConsentGateTests.afterWithdraw_returnsToNotConsented` GREEN; `SettingsView` calls `consentStore.withdraw()` (grep ≥ 1); the `@Observable` state flip re-routes `AppGate` to onboarding |

**Optional manual walkthrough (not required for completion):** `xcodebuild ... build` then launch on the booted iPhone 16 Simulator and follow Task 3 steps 1–6.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Test-scaffold symbol names differed from the plan body**
- **Found during:** Task 1
- **Issue:** The RED test scaffolds (locked in 01-01) reference `Terminology.bleedingNoun` / `cycleNoun`, while the plan `<action>` text described `bleedingWord` / `cycleWord`. Implementing only the plan-body names would have left the suites RED (failed compile/assert).
- **Fix:** Exposed `bleedingNoun` / `cycleNoun` as the primary API (the binding test contract) and added `bleedingWord` / `cycleWord` aliases for the plan-body call sites. No behavior change.
- **Files modified:** `Sources/EndoApp/Terminology/Terminology.swift`
- **Commit:** `bb967c7`

**2. [Rule 3 - Blocking] Nested NavigationStack in pushed SettingsView**
- **Found during:** Task 2
- **Issue:** `SettingsView` initially wrapped its `Form` in its own `NavigationStack`, but it is pushed onto the shell's stack from `WalkingSkeletonView` — nesting stacks duplicates the nav bar / title.
- **Fix:** Removed `SettingsView`'s own `NavigationStack`; it relies on the shell's stack (and `PrivacyPolicyView` pushes within it).
- **Files modified:** `Sources/EndoApp/Features/Settings/SettingsView.swift`
- **Commit:** `5d23a87`

**3. [Rule 2 - Missing critical functionality] Bounded free-text identity fields (T-01-05)**
- **Found during:** Task 2
- **Issue:** Display-name/pronoun free-text had no length bound (threat T-01-05 in the register is dispositioned `mitigate`).
- **Fix:** Bounded both fields to `TerminologyStore.maxDisplayNameLength` (60) and stored empty as `nil`. Plain local strings — no SQL/HTML/network injection surface.
- **Files modified:** `Sources/EndoApp/Terminology/TerminologyPreference.swift`, `Sources/EndoApp/Features/Settings/SettingsView.swift`
- **Commit:** `5d23a87`

### Intentional file removal

- Removed `Sources/EndoApp/Features/Skeleton/PlaceholderRootView.swift` — an unreferenced 01-01 leftover (the root moved to `AppGate` in 01-02). The 01-02 summary explicitly flagged it for cleanup. Verified no remaining references before removal; committed in `5d23a87`.

**Total deviations:** 3 auto-fixed (2 Rule-3 blocking, 1 Rule-2 mitigation) + 1 intentional removal. No architectural or scope changes; RESEARCH Pattern 4 / Claims & Language Policy implemented as specified.

## Threat Mitigations Delivered

- **T-01-07 (informed consent, GDPR Art. 9):** onboarding surfaces the disclaimer + a plain data-use summary + the offline privacy policy BEFORE `grant(at:)`; consent is withdrawable in Settings with an explanation (D-04).
- **T-01-08 (inaccurate privacy claims):** `PrivacyPolicyText` truthfully mirrors the verified architecture (on-device + private iCloud, no servers, zero third-party SDKs); no third-party deps added.
- **T-01-09 (medical-purpose / gendered copy):** `ClaimsPolicy` encodes forbidden verbs + gendered tokens; `TerminologyTests` enforces no gendered default copy; all bleeding/cycle copy routes through `Terminology`.
- **T-01-05 (free-text input validation):** display name / pronouns bounded to 60 chars, treated as plain local strings.

## Known Stubs

None that block the plan goal. The bleeding-term/cycle copy choke-point is wired and used by `OnboardingView`; Phase 2's logging UI will broaden where `Terminology` is consumed. The hosted privacy URL (D-15), final app name/bundle id (D-09), Apple Developer enrollment + 5.1.3(ii) TestFlight de-risk (D-11), and turning on CloudKit sync (D-10/Phase 4) remain intentionally deferred (out of Phase 1 scope).

## Threat Flags

None — no new network endpoints, auth paths, file-access patterns, or schema changes at trust boundaries were introduced (the privacy policy is offline; terminology prefs live in UserDefaults, outside the synced store).

## Deferred Items (carried forward)

- **Hosted privacy-policy URL (D-15)** → App Store submission; will mirror `PrivacyPolicyText.body` verbatim.
- **Final app name + bundle id (D-09)** → branding / Apple Developer enrollment.
- **Apple Developer enrollment + 5.1.3(ii) TestFlight de-risk (D-11)** → before release; local-first fallback keeps Phase 1 unblocked.
- **Turn on CloudKit sync (D-10)** → Phase 4, behind the `SyncMode` flag established in 01-02.

## Next Phase Readiness

Phase 1 is complete: all six requirement suites (DATA-01, ONBD-01/02/03/04) are GREEN, the iPhone 16 Simulator build is green, and the irreversible foundations (CloudKit-safe schema, GDPR consent gate + withdraw, claims/inclusive-language choke-point, offline privacy policy) are locked. Phase 2 (logging UI) builds on `Repository` + `SchemaV1` and resolves bleeding/cycle copy through `Terminology`. Destination for later plans: `-destination 'platform=iOS Simulator,name=iPhone 16'`. No blockers.

## Self-Check: PASSED

All 9 created source files + the SUMMARY exist on disk; both task commits (`bb967c7`, `5d23a87`) are present in git history; `PlaceholderRootView.swift` removal confirmed.
