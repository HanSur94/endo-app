---
phase: 19-app-shell-consent
plan: 02
subsystem: ui
tags: [android, compose, material3, consent-gate, robolectric, single-activity, datastore, copy-audit]

# Dependency graph
requires:
  - phase: 19-app-shell-consent
    plan: 01
    provides: "BloomTheme + LocalBloomColors + BloomPrimaryButton; Robolectric+Compose JVM harness (createComposeRule)"
  - phase: 18-room-persistence
    plan: 03
    provides: "ConsentStore interface + DataStoreConsentStore (consent.preferences_pb) + 3-state ConsentState (Unknown/NotConsented/Consented)"
provides:
  - "AppGate — the consent-by-construction when-switch on ConsentState (no navigation library); health/tab composables referenced ONLY in the Consented arm"
  - "Single-activity app entry: App(Application) + AppDependencies (manual composition root over DataStoreConsentStore, no DI/ViewModel) + MainActivity (enableEdgeToEdge + BloomTheme { AppGate() })"
  - "CheckInRouter — non-sensitive deep-link/notification flag observed only inside the Consented branch"
  - "ConsentedTab enum (tab.log/tab.insights/tab.explore testTags) + minimal-but-real ConsentedShell (M3 NavigationBar) + OnboardingScreen (consent.grantButton/consent.declineButton)"
  - "ShellCopy (registered in CopyAuditRegistry.sources) — first :app AuditableCopy source"
  - "AppGateConsentTest — the ASHL-01 invariant, 5 JVM cases, red-capable"
  - "Theme.EndoApp launch theme + @color/canvas window background (no white flash)"
affects: [19-03, 19-04, 19-05, 20-tracking, 22-insights-explore]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Consent-by-construction: the shell subtree is only REFERENCED inside AppGate's `is Consented ->` arm — a structural guarantee, not a runtime guard condition"
    - "Manual composition root (AppDependencies) built once in Application.onCreate, read via (application as App).dependencies — no DI framework"
    - "Preferences DataStore built via PreferenceDataStoreFactory.create(scope, produceFile = context.preferencesDataStoreFile(FILE_NAME)) on a process-lifetime SupervisorJob scope"
    - "Deep-link seam is a plain mutableStateOf boolean consumed by a LaunchedEffect inside the consented shell — never observed in the pre-consent arms"
    - "rememberSaveable over an enum (ConsentedTab) — enums are Serializable so the autoSaver handles them (import from androidx.compose.runtime.saveable)"
    - "Platform NoActionBar launch theme (@android:style/Theme.Material.Light.NoActionBar) — no AppCompat/Material XML dependency; Compose owns all real styling"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/App.kt
    - android/app/src/main/kotlin/com/endoapp/app/AppDependencies.kt
    - android/app/src/main/kotlin/com/endoapp/app/MainActivity.kt
    - android/app/src/main/kotlin/com/endoapp/app/AppGate.kt
    - android/app/src/main/kotlin/com/endoapp/app/CheckInRouter.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/shell/ConsentedTab.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/shell/ConsentedShell.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/onboarding/OnboardingScreen.kt
    - android/app/src/main/kotlin/com/endoapp/app/content/ShellCopy.kt
    - android/app/src/main/res/values/colors.xml
    - android/app/src/main/res/values/themes.xml
    - android/app/src/test/kotlin/com/endoapp/app/AppGateConsentTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt
    - android/app/src/main/AndroidManifest.xml

key-decisions:
  - "Commit order inverted from plan task order (Task 2 code committed before Task 1 entry): the plan's Task 1 (MainActivity->AppGate, AppDependencies->CheckInRouter) references Task 2 symbols, so committing the self-contained gate/shell/copy first keeps EVERY commit compilable"
  - "Withdraw affordance carries a real, registered label (ShellCopy.WITHDRAW = \"Withdraw consent\") rather than being label-less — satisfying the W1 fold-in (register user-facing copy in the same change) with an honest control instead of a hidden one"
  - "rememberSaveable import is androidx.compose.runtime.saveable.rememberSaveable (not the runtime package) — the initial build error"
  - "Kept createComposeRule() (deprecation-warned in this toolchain) to match the Plan-01 harness precedent; not migrating to the v2 rule this phase"

requirements-completed: [ASHL-01]

# Metrics
duration: ~35min
completed: 2026-07-09
status: complete
---

# Phase 19 Plan 02: App Shell & Consent-by-Construction AppGate Summary

**Stood up the single-activity app entry and the consent-by-construction `AppGate` — a plain three-way `when`-switch on the Phase-18 3-state `ConsentState` (Unknown → fail-closed splash, NotConsented → onboarding, Consented → the 3-tab shell) with NO navigation library, where the health/tab composables are UNCONSTRUCTIBLE pre-consent because they are referenced only inside the `Consented` arm. `AppGateConsentTest` proves all four ASHL-01 invariants on the JVM Robolectric+Compose lane (5/5 green, red-capable), the shell/consent copy is registered and audit-clean, and the app builds a real APK with zero network permission.**

## Performance
- **Duration:** ~35 min
- **Completed:** 2026-07-09
- **Tasks:** 3
- **Files:** 14 (12 created, 2 modified)

## Accomplishments
- **Consent-by-construction `AppGate`:** a plain `when (ConsentState)` — `Unknown -> SplashScreen()` (full-bleed canvas Box, `Loading` a11y, fail-closed — never flashes gated UI), `NotConsented -> OnboardingScreen(...)` (the router is deliberately NOT observed here), `is Consented -> ConsentedShell(...)`. The shell and its tabs are referenced ONLY in the `Consented` arm — no `NavHost`, no guard condition. This is the iOS `AppGate` invariant, ported.
- **Single-activity entry:** `App : Application` builds one `AppDependencies` (manual composition root — no DI, no ViewModel) that constructs the consent `DataStore<Preferences>` at `preferencesDataStoreFile("consent")` on a process-lifetime `SupervisorJob` scope, OUTSIDE the Room health DB, and wraps it in the Phase-18 `DataStoreConsentStore`. `MainActivity` calls `enableEdgeToEdge()` then `setContent { BloomTheme { AppGate(consentStore, router) } }`.
- **Deep-link seam (`CheckInRouter`):** a non-sensitive `mutableStateOf` boolean. `MainActivity.onCreate`/`onNewIntent` flip it only when the intent carries `EXTRA_REMINDER_CHECK_IN`; it is consumed by a `LaunchedEffect` INSIDE `ConsentedShell` — so a deep link/notification fired while unconsented lands on the gate and cannot bypass it (threat T-19-01b).
- **Minimal-but-real arms (stable testTag contracts for Plans 03/04):** `ConsentedShell` is an M3 `Scaffold` + `NavigationBar` over a `rememberSaveable` `ConsentedTab` selection (Log default), Bloom nav colors, empty per-tab placeholder bodies, and a temporary `shell.withdraw` affordance. `OnboardingScreen` is the `I consent` (`consent.grantButton`) + `Not now` (`consent.declineButton`) affordances on canvas with `safeDrawingPadding()`.
- **Copy registered from birth:** `ShellCopy` (6 strings: 3 tab labels + `I consent` + `Not now` + `Withdraw consent`) added to `CopyAuditRegistry.sources` in the SAME change — `CopyAuditTest` now scans real copy (7/7 green).
- **No white flash:** `Theme.EndoApp` (platform NoActionBar parent) sets `@color/canvas` as the launch-window background + transparent system bars.

## Task Commits
1. **Task 2: consent-by-construction AppGate + shell/onboarding + ShellCopy** — `3dbb3b5` (feat)
2. **Task 1: single-activity app entry — App/AppDependencies/MainActivity** — `fa08b8b` (feat)
3. **Task 3: AppGateConsentTest — consent-by-construction invariant** — `c308cac` (test)

_Commit order is inverted from the plan's task numbering (see Deviations) so every commit compiles. No STATE.md / ROADMAP.md commit — the orchestrator owns state updates for this run (per objective)._

## Test Results (REAL, run on this host)
- `:app:testDebugUnitTest` — **75/75 passed, 0 failures** (was 70/70 in Plan 01; +5 = the new `AppGateConsentTest` cases).
  - `AppGateConsentTest` **5/5**: `notConsented_hasNoTabNodes_andShowsConsentButton`, `unknown_failsClosed_showsSplash_noTabNodes_noConsentButton`, `grantingConsent_revealsShell_withLogSelectedByDefault`, `withdrawingConsent_reGatesToOnboarding`, `deepLinkFlag_whileNotConsented_landsOnGate_noHealthUi`.
  - `CopyAuditTest` **7/7** (now scanning the 6 registered `ShellCopy` strings — all clean against the ClaimsPolicy substring gate).
- `:app:assembleDebug` — **BUILD SUCCESSFUL** (real APK; single activity + Theme.EndoApp + launcher intent-filter registered).
- `:insights:test` — **green, no regression** (unchanged since Plan 01's 176/176; source untouched).
- **RED-capability proof:** temporarily routing the `NotConsented` arm to `ConsentedShell` turned **4 of 5** `AppGateConsentTest` cases RED (the `Unknown` case stayed green — its splash arm was untouched, exactly as expected); reverted to 5/5 green.
- **Zero-permission posture:** source manifest has `0` `uses-permission`; merged debug manifest contains no `android.permission.INTERNET`.
- **No nav library:** `grep -rn "androidx.navigation" app/src/` → none in source.

## The four ASHL-01 invariants (all proven)
1. **Unreachable pre-consent:** with `NotConsented` AND `Unknown`, no `tab.log`/`tab.insights`/`tab.explore` node exists; the consent grant control is present (NotConsented) / the splash is shown (Unknown, fail-closed).
2. **Grant reveals the shell:** clicking `consent.grantButton` flips the store to `Consented` → the three tab nodes exist, `tab.log` is selected by default.
3. **Withdraw re-gates:** clicking `shell.withdraw` flips back → the tab nodes leave composition, `consent.grantButton` returns.
4. **Deep link lands on the gate:** `CheckInRouter.pendingCheckIn = true` while `NotConsented` surfaces no health/tab UI (the flag is observed only inside the Consented branch).

## Deviations from Plan

### Design adaptations

**1. [Rule 3 - Blocking / plan ordering] Commit order inverted so every commit compiles**
- **Found during:** Task 1.
- **Issue:** The plan authors Task 1 (App/AppDependencies/MainActivity) before Task 2 (AppGate/CheckInRouter), but Task 1's files reference Task 2's symbols (`MainActivity` → `AppGate`, `AppDependencies` → `CheckInRouter`), so a Task-1-first commit would not compile in isolation.
- **Fix:** committed the self-contained Task 2 code (gate/shell/onboarding/copy — depends on nothing in Task 1) FIRST, then the Task 1 entry, then the Task 3 test. File→task authorship is unchanged; only commit sequencing was reordered. Every commit builds green.
- **Files:** commit sequencing only. **Commits:** `3dbb3b5`, `fa08b8b`, `c308cac`.

**2. [W1 fold-in compliance] Withdraw affordance uses a registered, visible label**
- **Context:** the W1 plan-checker fold-in requires that any withdraw affordance either be non-user-facing (testTag-only, no visible label) OR have its user-facing strings registered in the CopyAudit source in the same change.
- **Choice:** gave the temporary `shell.withdraw` control a real label (`ShellCopy.WITHDRAW = "Withdraw consent"`, verbatim from the UI-SPEC copy table) and registered it in `ShellCopy.auditableStrings` — an honest visible control rather than a hidden one. `CopyAuditTest` scans it and it passes the substring gate. (The plan's literal `ShellCopy` list was 5 strings; this makes it 6.)
- **Files:** `ShellCopy.kt`, `ConsentedShell.kt`. **Commit:** `3dbb3b5`.

**3. [Rule 1 - Bug] `rememberSaveable` import package**
- **Found during:** the first `:app:assembleDebug` (Unresolved reference 'rememberSaveable').
- **Fix:** import `androidx.compose.runtime.saveable.rememberSaveable` (not `androidx.compose.runtime.rememberSaveable`). Enum `ConsentedTab` is `Serializable`, so the default autoSaver handles it — no custom `Saver` needed.
- **Files:** `ConsentedShell.kt`. **Commit:** `3dbb3b5`.

## Threat Model Compliance
- **T-19-01 (EoP — consent bypass, high):** mitigated by construction — `AppGate` is a `when (ConsentState)` whose shell/tab composables are referenced only in the `is Consented ->` arm; `Unknown` fails closed to a splash. `AppGateConsentTest` pins unreachable-pre-consent (NotConsented + Unknown), grant-reveals, and withdraw-re-gates, and is proven red-capable.
- **T-19-01b (Spoofing — deep-link/notification, high):** mitigated — `CheckInRouter.pendingCheckIn` is a non-sensitive boolean; `MainActivity` only flips it; it is observed solely inside the Consented branch. The deep-link test proves the flag lands on the gate while `NotConsented` (no health UI).
- **T-19-SC (supply chain, low/accept):** honored — no new package installs; only first-party androidx already in the audited catalog. No `androidx.navigation` added.

## Known Stubs
- **Tab bodies are intentional placeholders:** each `ConsentedTab` renders an empty `Box` (`tab.*.body` testTag) this phase — the Log/Insights/Explore CONTENT is deferred to Phases 20/22 by the CONTEXT/UI-SPEC (this phase renders the gate + shell chrome only). Not a stub that blocks the plan goal (the gate invariant is the deliverable, and it is complete and test-proven).
- **`shell.withdraw` is a temporary affordance:** the real Settings entry (with a confirmation dialog) is Phase 20; it exists here only so the withdraw-re-gate invariant is exercisable. Documented in-code and via the registered copy.
- Onboarding's richer copy (welcome hero, disclaimer banner, privacy link, consent explainer) is Plan 04 per the plan; `OnboardingScreen` intentionally ships only the two load-bearing consent affordances this phase.

## Self-Check: PASSED
- All 12 created files + 2 modified files exist on disk.
- All 3 task commits present in git history: `3dbb3b5`, `fa08b8b`, `c308cac`.
- Real build/test evidence captured above (`:app` 75/75, `AppGateConsentTest` 5/5, `CopyAuditTest` 7/7, `assembleDebug` SUCCESSFUL, red-capability proven, zero INTERNET permission, no nav library).

---
*Phase: 19-app-shell-consent*
*Completed: 2026-07-09*
