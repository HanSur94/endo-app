---
phase: 19-app-shell-consent
verified: 2026-07-09T00:00:00Z
status: passed
score: 4/4 must-haves verified (19/19 plan truths)
behavior_unverified: 0
overrides_applied: 0
manual_verifications: # Pre-declared device-gated checks (19-VALIDATION.md Manual-Only) — NOT gaps
  - test: "Compare a shell screenshot to the iOS Bloom shell on device/emulator"
    expected: "Rose/plum palette, typography, floating-capsule tab bar match iOS Bloom feel"
    why_human: "Pixel/aesthetic fidelity is not JVM-automatable (ASHL-02)"
  - test: "Run on an Android 14+ emulator; perform a predictive-back gesture; inspect system bars"
    expected: "Back-preview animation shows; no content draws under status/nav bars (edge-to-edge)"
    why_human: "Gesture animation + inset rendering are OS-mediated, device-only (ASHL-03)"
  - test: "Enable TalkBack on-device; sweep the gate, onboarding/consent, and the 3 tabs"
    expected: "Screen-reader focus order and labels are coherent"
    why_human: "Screen-reader UX is experiential (ASHL-01)"
---

# Phase 19: App Shell & Consent Verification Report

**Phase Goal:** A user completes onboarding/consent and lands in a Bloom-styled 3-tab Compose shell — health UI cannot exist before consent, by construction; the app declares no INTERNET permission.
**Verified:** 2026-07-09
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria — the contract)

| # | Truth | Status | Evidence |
| --- | ----- | ------ | -------- |
| 1 | UI tests prove health surfaces unreachable pre-consent; withdraw re-gates; deep links land on the gate | ✓ VERIFIED | `AppGate.kt` is a plain `when (state)` switch; `ConsentedShell` referenced ONLY in the `Consented ->` arm. `AppGateConsentTest` (Robolectric+Compose, JVM) asserts all invariants and is documented red-capable: NotConsented→no `tab.*` nodes + grant button present; Unknown→splash, no tabs, no CTA; grant→3 tabs, Log selected; withdraw→tabs leave composition + CTA returns; deep-link flag while NotConsented→no health UI. Tests green on rerun. |
| 2 | The ported Bloom design system (tokens, typography, components, tab chrome) renders on all shell screens | ✓ VERIFIED | `BloomColors` = 17 tokens via `LocalBloomColors` (staticCompositionLocalOf); `BloomTheme` derives an M3 `ColorScheme` with `primary = plum 0xFF6E2153`; `BloomTypography` (display/title/H2/H3/eyebrow + M3 mapping); `BloomNavigationBar` floating-capsule chrome (plum selected / inkMuted unselected / petal indicator). `NoDynamicColorScanTest` forbids `dynamicLightColorScheme`/`dynamicDarkColorScheme` (comment-stripped, red-capable). `BloomThemeTest`/`BloomComponentsTest` green. |
| 3 | The 3-tab shell (Log/Insights/Explore) handles edge-to-edge and predictive back at targetSdk 36 | ✓ VERIFIED | `ConsentedShell` = M3 `Scaffold` + `BloomNavigationBar` bottomBar; body pads from `innerPadding`/`WindowInsets` (edge-to-edge); `PredictiveBackHandler` enabled only off Log; pure `tabBackTarget` seam. `ConsentedShellTest` drives a REAL committed back via `activity.onBackPressedDispatcher.onBackPressed()` → returns to Log; tab-switch + default-Log tests green. |
| 4 | A manifest assertion test proves the app declares no INTERNET permission | ✓ VERIFIED | `AndroidManifest.xml` declares zero permissions. `ManifestPermissionsTest` DOM-parses `<uses-permission android:name>` (comment-safe — the manifest comment legitimately names INTERNET), asserts NO `android.permission.INTERNET` AND no `android.permission.health.WRITE_*`; documented red-capable; also transitively checks the merged manifest when built. Green. |

**Score:** 4/4 roadmap success criteria verified · 19/19 plan must-have truths verified · 0 behavior-unverified

### Plan Must-Have Truths (supporting detail)

| Plan | Truths | Status |
| ---- | ------ | ------ |
| 19-01 (design system) | BloomTheme wraps MaterialTheme + LocalBloomColors(17) + Typography + Shapes; ColorScheme.primary == plum; Robolectric+Compose JVM harness runs; no dynamic-color call | ✓ 4/4 |
| 19-02 (gate) | Pre-consent (NotConsented + Unknown) no tab node exists, grant control present; grant→3 tabs Log default; withdraw re-gates; deep-link flag lands on gate | ✓ 4/4 |
| 19-03 (shell) | Bloom M3 NavigationBar 3 destinations; tab tap swaps body via rememberSaveable; edge-to-edge insets; back Insights/Explore→Log, Log→exit via PredictiveBackHandler | ✓ 4/4 |
| 19-04 (onboarding/consent) | Onboarding shows hero + DisclaimerBanner + privacy link BEFORE consent; explicit affirmative + explicit decline (fail-closed); Disclaimer must-contain + PrivacyPolicy on-device/no-servers/no-accounts/no-sharing + dev + email, no iCloud; copy passes ClaimsPolicy | ✓ 4/4 |
| 19-05 (manifest/exit-gate) | No INTERNET (DOM-parsed, red-capable); no health WRITE; all phase copy registered + passes CopyAudit, legal constants pass must-contain | ✓ 3/3 |

### Required Artifacts

| Artifact | Status | Details |
| -------- | ------ | ------- |
| `designsystem/BloomColors.kt` (+ Typography, Shapes, Components, Theme, NavigationBar, Icons) | ✓ VERIFIED | 17 tokens; theme derives M3 ColorScheme; wired into every screen via `BloomTheme` |
| `AppGate.kt` | ✓ VERIFIED | 3-way when-switch; health composables only in Consented arm; no nav library |
| `MainActivity.kt` | ✓ VERIFIED | Single activity, `enableEdgeToEdge()`, `setContent { BloomTheme { AppGate() } }` |
| `ui/shell/ConsentedShell.kt` + `ConsentedTab.kt` + `TabPlaceholders.kt` | ✓ VERIFIED | Scaffold + Bloom nav + insets + predictive back + Bloom placeholder cards (Log→P20, Insights/Explore→P22) |
| `ui/onboarding/OnboardingScreen.kt` + `PrivacyPolicyScreen.kt` | ✓ VERIFIED | Informed-consent order; explicit grant/decline; offline policy |
| `content/Disclaimer.kt` + `PrivacyPolicyText.kt` | ✓ VERIFIED | Single-source constants, exempt-scanned, own must-contain tests |
| `AppGateConsentTest`, `ConsentedShellTest`, `ManifestPermissionsTest`, `NoDynamicColorScanTest`, `BloomThemeTest`, `Disclaimer/PrivacyPolicy/OnboardingScreenTest`, `CopyAuditTest` | ✓ VERIFIED | All present and green |

### Key Link Verification

| From | To | Via | Status |
| ---- | -- | --- | ------ |
| `MainActivity` | `AppGate` | `BloomTheme { AppGate(consentStore, checkInRouter) }` | ✓ WIRED |
| `AppGate` | `ConsentStore.state` | `collectAsStateWithLifecycle`; Unknown→splash (fail-closed) | ✓ WIRED |
| `AppGate` (Consented arm only) | `ConsentedShell` | direct reference — consent-by-construction | ✓ WIRED |
| `CheckInRouter.pendingCheckIn` | observed only inside Consented `ConsentedShell` | LaunchedEffect | ✓ WIRED |
| `BloomTheme` | `LocalBloomColors` (17 tokens) | `CompositionLocalProvider` | ✓ WIRED |
| `ShellCopy` / `PlaceholderCopy` / `OnboardingCopy` | `CopyAuditRegistry.sources` | registered same change | ✓ WIRED |

### Behavioral Spot-Checks (JVM, actually run)

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| :app JVM unit tests (gate/shell/onboarding/manifest/copy/theme) | `./gradlew :app:testDebugUnitTest --rerun-tasks` | 99 tests, 0 failures | ✓ PASS |
| :insights JVM tests (incl. boundary-lint + version-catalog audit) | `./gradlew :insights:test --rerun-tasks` | 176 tests, 0 failures | ✓ PASS |
| Debug APK assembles | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL | ✓ PASS |
| Android lint | `./gradlew :app:lintDebug` | BUILD SUCCESSFUL (no errors) | ✓ PASS |

### Requirements Coverage

| Requirement | Description | Status | Evidence |
| ----------- | ----------- | ------ | -------- |
| ASHL-01 | Consent-by-construction gate; re-gate on withdraw; deep links land on gate | ✓ SATISFIED | `AppGate` when-switch + `AppGateConsentTest` (4 invariants, red-capable) |
| ASHL-02 | Bloom design system ported + rendering on shell screens | ✓ SATISFIED | `BloomColors`/`Typography`/`Components`/`NavigationBar` + `BloomThemeTest`, `NoDynamicColorScanTest` |
| ASHL-03 | 3-tab shell, edge-to-edge + predictive back at targetSdk 36 | ✓ SATISFIED | `ConsentedShell` Scaffold/WindowInsets + `PredictiveBackHandler` + `ConsentedShellTest` |
| ASHL-04 | No INTERNET permission — manifest assertion green | ✓ SATISFIED | `ManifestPermissionsTest` DOM-parsed, comment-safe, red-capable |

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
| ---- | ------- | -------- | ------ |
| — | No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK` debt markers in `android/app/src` | ℹ️ Info | Clean; tab placeholders are intentional, phase-marked scaffolds for P20/P22 |

### iOS Tree Isolation

Git history for the phase window touches only `android/`, `.planning/`, and `.github/workflows/android.yml` (Android CI). The iOS tree (`Sources/`, `EndoInsights/`, `project.yml`) is UNMODIFIED — confirmed via `git log --name-only`.

### Device-Gated Manual Verifications (pre-declared, NOT gaps)

Per `19-VALIDATION.md` Manual-Only contract, three checks are inherently device/experiential and were scoped out of JVM automation from the start. They are documented manual verifications, not verification gaps, and do not block phase completion:

1. Bloom visual fidelity vs iOS (color/typography/tab-capsule feel) — ASHL-02
2. Predictive-back animation + edge-to-edge rendering on a real Android 14+ device — ASHL-03
3. TalkBack sweep of onboarding/consent/tabs — ASHL-01

Their underlying logic (gate invariants, tab-back state transitions, inset wiring, token application) has passing JVM verification; only pixel fidelity and on-device gesture/screen-reader feel remain device-gated.

### Gaps Summary

None. All four ROADMAP success criteria and all 19 plan must-have truths are verified against the codebase with genuinely green JVM tests (99 :app, 176 :insights), a successful `assembleDebug`, and a clean `lintDebug`. The consent-by-construction invariant, Bloom design system, edge-to-edge/predictive-back shell, and no-INTERNET manifest posture are all structurally present, wired, and test-proven. Remaining items are pre-declared device-gated manual verifications, not gaps.

---

_Verified: 2026-07-09_
_Verifier: Claude (gsd-verifier)_
