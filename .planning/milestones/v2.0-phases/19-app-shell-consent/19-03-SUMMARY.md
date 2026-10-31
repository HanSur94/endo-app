---
phase: 19-app-shell-consent
plan: 03
subsystem: ui
tags: [android, compose, material3, navigation-bar, edge-to-edge, predictive-back, robolectric, copy-audit, bloom]

# Dependency graph
requires:
  - phase: 19-app-shell-consent
    plan: 01
    provides: "BloomTheme + LocalBloomColors + Bloom components (bloomCard/EyebrowLabel/bloomH3/bloomBody) + Robolectric+Compose JVM harness"
  - phase: 19-app-shell-consent
    plan: 02
    provides: "ConsentedShell/ConsentedTab (tab.* testTags) + AppGate Consented arm + CheckInRouter + ShellCopy + CopyAuditRegistry"
provides:
  - "BloomNavigationBar — M3 NavigationBar wearing the Bloom floating-capsule chrome (surface container, plum selected, inkMuted unselected, petal indicator, plum-tinted float shadow); generic over the tab type so designsystem stays foundational"
  - "BloomTabIcons — three distinct bundled 24dp ImageVector tab glyphs (edit/pencil Log, rising line-chart Insights, layered-squares Explore); no material-icons-extended"
  - "Enriched ConsentedShell — edge-to-edge Scaffold insets + Bloom placeholder cards + predictive-back-to-Log (PredictiveBackHandler), rememberSaveable tab state, no NavHost"
  - "TabPlaceholder — centered bloomCard (eyebrow + heading + body) with merged TalkBack semantics + per-tab tab.*.body testTag"
  - "PlaceholderCopy — nine curated 'arrives soon' strings registered in CopyAuditRegistry, phase-marked (Log->20, Insights/Explore->22)"
  - "tabBackTarget — pure predictive-back decision seam (secondary tab -> Log, Log -> null)"
  - "ConsentedShellTest — ASHL-03 shell behavior, 4 JVM cases (default Log, tab switch, real back-dispatcher-to-Log, pure back logic)"
affects: [19-04, 19-05, 20-tracking, 22-insights-explore]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "M3 NavigationBar (not a fully-custom capsule) as the shell bottomBar — chosen for correct edge-to-edge inset handling, predictive-back friendliness, and built-in >=48dp targets + Tab semantics"
    - "Design-system nav bar kept generic over the tab type <T> (testTag/label/icon projections) so :app/designsystem never imports :app/ui.shell — foundational layering preserved with zero coupling"
    - "Tab glyphs bundled in code as ImageVector paths (mirrors BloomComponents.bloomInfoGlyph) — asset-light, no material-icons-extended, dependency-count unchanged (threat T-19-SC accept)"
    - "PredictiveBackHandler enabled-gated on a pure decision (tabBackTarget) — back intercepted ONLY off the start tab; the start tab keeps default predictive-back-to-exit"
    - "Predictive-back decision extracted as a pure function so the shell test pins it deterministically on the JVM while the animated gesture stays a device Manual-Only check"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/designsystem/BloomNavigationBar.kt
    - android/app/src/main/kotlin/com/endoapp/app/designsystem/BloomIcons.kt
    - android/app/src/main/kotlin/com/endoapp/app/ui/shell/TabPlaceholders.kt
    - android/app/src/main/kotlin/com/endoapp/app/content/PlaceholderCopy.kt
    - android/app/src/test/kotlin/com/endoapp/app/ui/shell/ConsentedShellTest.kt
  modified:
    - android/app/src/main/kotlin/com/endoapp/app/ui/shell/ConsentedShell.kt
    - android/app/src/main/kotlin/com/endoapp/app/content/AuditableCopy.kt

key-decisions:
  - "BloomNavigationBar made GENERIC over the tab type (testTag/label/icon lambdas) instead of importing ConsentedTab — keeps the foundational designsystem layer free of a feature-layer (ui.shell) dependency; the shell supplies the projections. Faithful to the plan's 'takes the tab list + current tab + onSelect' while avoiding the layering smell."
  - "All three tab glyphs bundled in code (including Log) rather than using Icons.Outlined.Edit from material-icons-core — material-icons-core is only a transitive artifact of material3 (not a declared dep), and the repo's own convention (bloomInfoGlyph) bundles glyphs in code. This matches the plan's own threat model T-19-SC ('icons are bundled ImageVectors') and guarantees compilation without leaning on a transitive dependency."
  - "Predictive-back behavior split into a pure tabBackTarget seam + a thin TabBackHandler(PredictiveBackHandler) wrapper — the pure seam is the deterministic JVM pin; the wrapper is the predictive-back-friendly production wiring."
  - "ConsentedShellTest uses createAndroidComposeRule<ComponentActivity>() (vs. AppGateConsentTest's createComposeRule()) specifically to reach activity.onBackPressedDispatcher and drive a REAL committed back — stronger evidence than a state-only assertion."

requirements-completed: [ASHL-03]

# Metrics
duration: ~25min
completed: 2026-07-09
status: complete
---

# Phase 19 Plan 03: App Shell — Bloom 3-Tab Shell, Edge-to-Edge & Predictive Back Summary

**Enriched the minimal Plan-02 `ConsentedShell` into the real Bloom 3-tab shell: a Material 3 `NavigationBar` (`BloomNavigationBar`) wearing the Bloom floating-capsule chrome (surface container, plum selected, inkMuted unselected, petal indicator pill, plum-tinted float shadow) over a `rememberSaveable` `ConsentedTab` — NO navigation library. Content is edge-to-edge inset-correct (Scaffold `innerPadding` + the nav bar consuming `navigationBars`), each tab renders an honest Bloom placeholder card marked for its filling phase (Log→20, Insights/Explore→22), and system back from Insights/Explore returns to Log via `PredictiveBackHandler` while back from Log exits. Three distinct tab glyphs are bundled in code (no `material-icons-extended`), the nine placeholder strings are registered and audit-clean, and `ConsentedShellTest` pins the shell behavior on the JVM — driving a REAL committed back through the activity's `OnBackPressedDispatcher`.**

## Performance
- **Duration:** ~25 min
- **Completed:** 2026-07-09
- **Tasks:** 3
- **Files:** 7 (5 created, 2 modified)

## Accomplishments
- **`BloomNavigationBar` (Bloom-chrome M3 `NavigationBar`):** `containerColor = surface`, a ~12% plum top float-shadow, `NavigationBarItem` colors selected `plum` / unselected `inkMuted` / indicator `petal`, each item a 24dp glyph (`contentDescription = null` — the label names it) + the 14sp SemiBold `bloomChipLabel`. Kept a stock `NavigationBar` (not a fully-custom capsule) for correct inset handling, predictive-back friendliness, and free ≥48dp Tab-role targets. Made **generic over the tab type** so the design-system layer never imports the shell.
- **`BloomTabIcons` (bundled glyphs):** three distinct in-code `ImageVector`s — an edit/pencil (Log), a rising line-chart zig-zag (Insights), and layered/stacked squares (Explore, visually distinct from the line chart). No `material-icons-extended` added — dependency count unchanged (threat T-19-SC accept).
- **Edge-to-edge `ConsentedShell`:** `Scaffold` consumes the system-bar `WindowInsets` and hands the body `innerPadding`; the `bottomBar` `NavigationBar` consumes the `navigationBars` inset; the placeholder body pads from `innerPadding`, so nothing draws under the status/nav bars. Tab state stays in `rememberSaveable` (no `NavHost`). Router deep-link flag still observed ONLY inside this consented subtree; the temporary `shell.withdraw` affordance preserved for the withdraw-re-gate invariant.
- **Bloom placeholder cards (`TabPlaceholder`):** a centered `bloomCard` carrying `EyebrowLabel` + `bloomH3` heading + `bloomBody` copy, with **merged semantics** so TalkBack reads the card as one node; each keeps its Plan-02 `tab.*.body` testTag.
- **`PlaceholderCopy` (registered from birth):** the nine curated strings (`Log`/`Daily log`/`Your daily tracking arrives in a coming update.`; `Insights`/`Your patterns`/`Insights from your own data appear once you have logged a few days.`; `Explore`/`Explore your data`/`Charts and overlays for your logged data arrive in a coming update.`), each code-commented with its filling phase, appended to `CopyAuditRegistry.sources` in the SAME change — `CopyAuditTest` now scans them (7/7 green).
- **Predictive-back-to-Log:** `TabBackHandler` wraps `PredictiveBackHandler`, enabled ONLY when a non-Log tab is selected — a back gesture animates the return to Log; on Log the handler does not register, so back falls through to the system default (exit). The decision is the pure `tabBackTarget` (secondary → Log, Log → `null`). Back is never intercepted generally.

## Task Commits
1. **Task 1: BloomNavigationBar chrome + bundled tab glyphs** — `b870131` (feat)
2. **Task 2: edge-to-edge ConsentedShell + Bloom placeholder cards** — `db8ec5c` (feat)
3. **Task 3: predictive-back-to-Log + ConsentedShellTest** — `ca306d7` (feat)

_No STATE.md / ROADMAP.md commit — the orchestrator owns state updates for this run (per objective)._

## Test Results (REAL, run on this host)
- `:app:testDebugUnitTest` — **79/79 passed, 0 failures, 0 errors** (was 75/75 in Plan 02; +4 = the new `ConsentedShellTest` cases).
  - `ConsentedShellTest` **4/4**: `defaultTab_isLog_showsThreeTabs_andLogPlaceholder`, `tappingInsights_rendersInsightsPlaceholder_andHidesLog`, `backFromInsights_returnsToLog` (driven through the real `activity.onBackPressedDispatcher.onBackPressed()`), `tabBackTarget_returnsLogFromSecondaryTabs_andNullFromLog`.
  - `CopyAuditTest` **7/7** (now scanning the 6 `ShellCopy` + 9 `PlaceholderCopy` strings — all clean against the ClaimsPolicy substring gate).
  - `AppGateConsentTest` **5/5** unaffected — the `tab.*` testTags are preserved, so the consent-by-construction invariant still holds.
- `:app:assembleDebug` — **BUILD SUCCESSFUL** (real APK).
- `:insights:test` — **176/176 passed, 0 failures** (no regression; source untouched).
- **No forbidden deps:** `grep material-icons-extended|androidx.navigation` over `build.gradle.kts` / `libs.versions.toml` / `src/main` → only doc-comment mentions, zero actual dependency; catalog-minimalism posture intact.

## Deviations from Plan

### Design adaptations

**1. [Rule 3 / layering] `BloomNavigationBar` made generic instead of importing `ConsentedTab`**
- **Found during:** Task 1.
- **Issue:** The plan describes `BloomNavigationBar` as taking "the current `ConsentedTab`" — but `ConsentedTab` lives in `ui.shell` (a feature layer) while `BloomNavigationBar` lives in `designsystem` (the foundational layer). Importing the shell enum into the design system inverts the dependency direction (foundational → feature).
- **Fix:** made the composable generic over the tab type `T` with `testTag`/`label`/`icon` projection lambdas; the shell passes `ConsentedTab.entries` + the projections. Fully satisfies "takes the tab list + current tab + onSelect" with zero cross-layer coupling and no behavior change. (No boundary test enforces this today; the choice keeps the design system reusable.)
- **Files:** `BloomNavigationBar.kt`, `ConsentedShell.kt`. **Commit:** `b870131` / `db8ec5c`.

**2. [Rule 3 / dependency safety] Log glyph bundled in code rather than `Icons.Outlined.Edit`**
- **Found during:** Task 1.
- **Issue:** The plan suggested `Icons.Outlined.Edit` from `material-icons-core` for the Log tab. `material-icons-core` is NOT a declared dependency — it is only a transitive artifact of `material3`. Relying on a transitive artifact for a source symbol is fragile, and the plan's own threat model T-19-SC states "icons are bundled `ImageVector`s".
- **Fix:** bundled all three tab glyphs in code as `ImageVector` paths (matching the repo's established `BloomComponents.bloomInfoGlyph` convention). Guarantees compilation with no reliance on a transitive dependency and keeps the dependency count unchanged.
- **Files:** `BloomIcons.kt`. **Commit:** `b870131`.

## Threat Model Compliance
- **T-19-01 (EoP — consent bypass, high):** mitigated by construction — `ConsentedShell` is still referenced ONLY inside `AppGate`'s `is Consented ->` arm; the enrichment added no pre-consent reachable surface. The `tab.log`/`tab.insights`/`tab.explore` testTags are preserved, so `AppGateConsentTest` (Plan 02, 5/5) continues to prove unreachable-pre-consent.
- **T-19-03 (Information disclosure — placeholder/tab copy, medium):** mitigated — every new placeholder string is registered in `CopyAuditRegistry.sources` in the same change and scanned by `CopyAuditTest` (7/7) against the ClaimsPolicy forbidden-substring set. Copy curated to dodge substring traps (`update`/`coming`/`arrives` clean; no `start`/`use`/`her` host words).
- **T-19-SC (Tampering — package installs, low/accept):** honored — no new installs; the tab glyphs are bundled `ImageVector`s, no `material-icons-extended`, no `androidx.navigation`.

## Known Stubs
- **Tab bodies are intentional, phase-marked placeholders:** each tab renders an honest "arrives soon" `TabPlaceholder` card this phase — the Log/Insights/Explore CONTENT is deferred to Phases 20/22 by the CONTEXT/UI-SPEC. Each `PlaceholderCopy` string is code-commented with its filling phase (Log→20, Insights/Explore→22). This is the deliverable for ASHL-03 (the inset-correct, predictive-back-aware chrome + honest placeholders), not a stub that blocks the plan goal.
- **`shell.withdraw` remains a temporary affordance** (unchanged from Plan 02) — the real Settings entry with a confirmation dialog is Phase 20; it exists here only so the withdraw-re-gate invariant is exercisable. Documented in-code + via the registered `ShellCopy.WITHDRAW`.

## Manual-Only device checks (per 19-VALIDATION)
- The predictive-back **animation** (the live back-preview scaling as the gesture drags) and the true edge-to-edge inset **rendering** (pixels not drawing under the status/nav bars on a real device) are device-gated visual checks. This JVM suite pins the state transitions (tab switch, back-to-Log commit) and the inset-honoring structure (Scaffold `innerPadding` consumed), not the pixels.

## Self-Check: PASSED
- All 5 created files + 2 modified files exist on disk.
- All 3 task commits present in git history: `b870131`, `db8ec5c`, `ca306d7`.
- Real build/test evidence captured above (`:app` 79/79, `ConsentedShellTest` 4/4 incl. real back-dispatcher drive, `CopyAuditTest` 7/7 over 15 registered strings, `AppGateConsentTest` 5/5 unaffected, `assembleDebug` SUCCESSFUL, `:insights` 176/176, no material-icons-extended / nav library).

---
*Phase: 19-app-shell-consent*
*Completed: 2026-07-09*
