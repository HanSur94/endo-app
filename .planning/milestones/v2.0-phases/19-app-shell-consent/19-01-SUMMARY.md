---
phase: 19-app-shell-consent
plan: 01
subsystem: ui
tags: [android, compose, material3, robolectric, designsystem, bloom, version-catalog, jvm-test-harness]

# Dependency graph
requires:
  - phase: 18-room-persistence
    provides: ":app Android Application module (AGP 9 built-in Kotlin), version catalog + VersionCatalogAuditTests, bare zero-permission manifest, BackupRulesTest source-scan precedent"
provides:
  - "Compose BOM 2026.06.00 + Material 3 wired into :app (buildFeatures.compose=true, kotlin.compose plugin)"
  - "Robolectric 4.16.1 + compose-ui-test-junit4 JVM Compose test harness (BloomHarnessSmokeTest) — renders composables on the JVM, no emulator"
  - "com.endoapp.app.designsystem: BloomColors (17 tokens + LocalBloomColors), BloomTypography, BloomShapes, BloomTheme (derived M3 ColorScheme, primary == plum), BloomComponents (bloomCard, BloomPrimaryButton, BloomChip, EyebrowLabel, DisclaimerBanner)"
  - "NoDynamicColorScanTest — a red-capable guard forbidding Material You dynamic color in the designsystem package"
affects: [19-02, 19-03, 19-04, 19-05, 20-tracking, 22-insights-explore]

# Tech tracking
tech-stack:
  added:
    - "androidx.compose:compose-bom 2026.06.00 (resolves ui 1.11.3 / material3 1.4.0)"
    - "androidx.activity:activity-compose 1.13.0"
    - "androidx.lifecycle:lifecycle-runtime-compose 2.10.0 (2.11.0 requires compileSdk 37; 2.10.0 is the latest compatible with the locked compileSdk 36)"
    - "org.robolectric:robolectric 4.16.1"
    - "org.jetbrains.kotlin.plugin.compose (pinned at Kotlin 2.3.21)"
  patterns:
    - "BOM as the single Compose version knob — every androidx.compose artifact declared versionless"
    - "ui-test-manifest via debugImplementation (contributes ComponentActivity to the merged debug manifest that Robolectric reads)"
    - "Brand tokens flow via LocalBloomColors (staticCompositionLocalOf); a derived M3 ColorScheme keeps stock M3 components on-brand"
    - "surfaceTint = Transparent to disable M3 tonal-elevation tinting (Bloom owns its shadows/fills)"
    - "Comment-stripped source-scan test (NoDynamicColorScanTest) — extends the VersionCatalogAuditTests / BackupRulesTest precedent"
    - "Bundled inline ImageVector for the info glyph (asset-light; avoids material-icons-extended)"

key-files:
  created:
    - android/app/src/main/kotlin/com/endoapp/app/designsystem/BloomColors.kt
    - android/app/src/main/kotlin/com/endoapp/app/designsystem/BloomTypography.kt
    - android/app/src/main/kotlin/com/endoapp/app/designsystem/BloomShapes.kt
    - android/app/src/main/kotlin/com/endoapp/app/designsystem/BloomTheme.kt
    - android/app/src/main/kotlin/com/endoapp/app/designsystem/BloomComponents.kt
    - android/app/src/test/kotlin/com/endoapp/app/designsystem/BloomHarnessSmokeTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/designsystem/BloomThemeTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/designsystem/NoDynamicColorScanTest.kt
    - android/app/src/test/kotlin/com/endoapp/app/designsystem/BloomComponentsTest.kt
  modified:
    - android/gradle/libs.versions.toml
    - android/app/build.gradle.kts

key-decisions:
  - "lifecycle-runtime-compose downgraded 2.11.0 -> 2.10.0: 2.11.0 requires compileSdk 37 (checkDebugAarMetadata hard-fail); 2.10.0 needs minCompileSdk 35, compatible with the Phase-18-locked compileSdk 36"
  - "ui-test-manifest moved from testImplementation (plan text) to debugImplementation — Robolectric reads the merged DEBUG manifest; the plan wiring left ComponentActivity unresolved at runtime"
  - "assertExists / assertDoesNotExist are MEMBER methods on SemanticsNodeInteraction in Compose 1.11.x (not top-level extensions) — no import"
  - "Info glyph bundled as an inline ImageVector rather than adding material-icons-core/-extended (material3 1.4.0 no longer depends on the icons artifact)"

requirements-completed: [ASHL-02]

# Metrics
duration: 26min
completed: 2026-07-09
status: complete
---

# Phase 19 Plan 01: Compose Toolchain + Bloom Design System Port Summary

**Wired Jetpack Compose (BOM 2026.06.00) + Material 3 + a Robolectric JVM Compose test harness into `:app`, then ported the shipped iOS "Bloom" design system into `com.endoapp.app.designsystem` — 17 brand tokens on `LocalBloomColors` with a derived M3 `ColorScheme` (primary == plum), the Bloom typography/shapes, and the five Bloom component contracts — with NO Material You, all proven green on the JVM lane (`:insights` 176/176, `:app` 70/70) and a real 12.4 MB debug APK.**

## Performance
- **Duration:** 26 min
- **Started:** 2026-07-09T11:24Z
- **Completed:** 2026-07-09T11:50Z
- **Tasks:** 3
- **Files:** 11 (9 created, 2 modified)

## Accomplishments
- **Compose + Robolectric toolchain:** extended the version catalog with `composeBom 2026.06.00`, `activity-compose 1.13.0`, `lifecycle-runtime-compose 2.10.0`, `robolectric 4.16.1`, and the `kotlin-compose` plugin — all first-party androidx/JetBrains/Robolectric coordinates; `VersionCatalogAuditTests` stays green (no analytics/auth/backend/networking substring). `:app` gained `buildFeatures.compose=true`, `testOptions.unitTests.isIncludeAndroidResources=true`, the Compose implementation deps, and the JVM test lane.
- **JVM Compose harness proven:** `BloomHarnessSmokeTest` renders a composable under `RobolectricTestRunner` + `createComposeRule()` and queries the semantics tree on the JVM — the load-bearing prerequisite for Plan 02's consent-by-construction gate test.
- **Bloom tokens:** all 17 color tokens (`canvas`…`indigo`) transcribed byte-for-byte from the iOS palette onto a `BloomColors` data holder, exposed via `LocalBloomColors` (`staticCompositionLocalOf`), plus the light `bloomLightColors` instance.
- **Derived M3 theme:** `BloomTheme { }` provides `LocalBloomColors` AND wraps content in a `MaterialTheme` carrying a `ColorScheme` derived from the Bloom tokens (primary == plum `0xFF6E2153`, error == roseDeep, outline == strokeSubtle, scrim == plumDeep, etc.), with `surfaceTint = Transparent` disabling tonal-elevation tinting so flat Bloom surfaces stay on-brand. Light theme only.
- **Typography & shapes:** the 3-weight Bloom scale (Serif SemiBold headings, Roboto body, Bold tracked eyebrow) mapped onto the M3 `Typography` template slots + extra `bloomH2`/`bloomChipLabel` styles; M3 `Shapes` 8/16/22dp + pill/banner/card named shapes.
- **Component contracts:** `Modifier.bloomCard`, `BloomPrimaryButton`, `BloomChip`, `EyebrowLabel`, `DisclaimerBanner` — each reads tokens from `LocalBloomColors`, with the UI-SPEC insets verbatim (card 18dp, button 52dp, chip h10/v6, radii 22/16/pill). The disclaimer banner merges its semantics into one TalkBack node and takes its copy as a parameter (Plan 04 owns the constant).
- **Anti-feature guard:** `NoDynamicColorScanTest` — a comment-stripped, red-capable source scan forbidding `dynamic*ColorScheme` anywhere in the designsystem package (threat T-19-02a).
- **No regression:** `:insights` full suite still 176/176 green; `:app` 70/70 (7 new design-system tests); `:app:assembleDebug` builds a real 12.4 MB APK on this host.

## Task Commits
1. **Task 1: Compose BOM + Robolectric JVM Compose harness** — `5bf933b` (feat)
2. **Task 2: Bloom color tokens, typography, shapes, theme + tests** — `d0694de` (feat)
3. **Task 3: Bloom component contracts + tests** — `a6dec80` (feat)

_No STATE.md / ROADMAP.md commit — the orchestrator owns state updates for this run (per objective)._

## Test Results (REAL, run on this host)
- `:insights:test` — **176/176 passed, 0 failures** (no regression).
- `:app:testDebugUnitTest` — **70/70 passed, 0 failures**, including the 7 new tests: `BloomHarnessSmokeTest` (1), `BloomThemeTest` (2), `NoDynamicColorScanTest` (1), `BloomComponentsTest` (3).
- `:app:assembleDebug` — **BUILD SUCCESSFUL**, `app-debug.apk` = 12.4 MB (was 3.4 MB pre-Compose).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] lifecycle-runtime-compose 2.11.0 requires compileSdk 37**
- **Found during:** Task 1 (`checkDebugAarMetadata` hard-failed: "Dependency androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0 requires … compileSdk 37 or later; :app is currently compiled against android-36").
- **Fix:** downgraded the catalog `lifecycle` pin `2.11.0 -> 2.10.0`. Verified 2.10.0's AAR metadata declares `minCompileSdk=35` (compatible with the Phase-18-locked compileSdk 36); activity-compose 1.13.0 (minCompileSdk 36), compose ui 1.11.3 and material3 1.4.0 (minCompileSdk 35) all clear 36. compileSdk stays 36 (a locked Phase-18 decision — not bumped).
- **Files:** `android/gradle/libs.versions.toml`. **Commit:** `5bf933b`.

**2. [Rule 3 - Blocking] ui-test-manifest must be debugImplementation, not testImplementation**
- **Found during:** Task 1 (`BloomHarnessSmokeTest` compiled but threw at runtime: "Unable to resolve activity for Intent … androidx.activity.ComponentActivity"). The bare Phase-18 manifest declares no activity; `createComposeRule()` launches `ComponentActivity`.
- **Fix:** wired `compose-ui-test-manifest` as `debugImplementation` (the documented pattern) so its `<activity ComponentActivity>` merges into the DEBUG manifest that Robolectric reads — rather than the plan's literal `testImplementation`, which leaves it off the merged manifest.
- **Files:** `android/app/build.gradle.kts`. **Commit:** `5bf933b`.

**3. [Rule 1 - Bug] `assertExists` import unresolved (Compose 1.11.x API shape)**
- **Found during:** Task 1 (compile error `Unresolved reference 'assertExists'`). In Compose 1.11.3, `assertExists`/`assertDoesNotExist` are MEMBER methods on `SemanticsNodeInteraction`, not top-level extensions in `androidx.compose.ui.test`.
- **Fix:** removed the erroneous `import androidx.compose.ui.test.assertExists`; call it as a member. (`assertDoesNotExist` — needed by Plan 02's gate test — is likewise a member.)
- **Files:** `BloomHarnessSmokeTest.kt`. **Commit:** `5bf933b`.

**4. [Rule 1 - Bug] NoDynamicColorScanTest false-flagged on its own documentation**
- **Found during:** Task 2 (test red: BloomTheme's KDoc legitimately NAMES `dynamicLightColorScheme` in prose). A raw substring scan can't tell a call from documentation.
- **Fix:** the scan now strips Kotlin block + line comments before matching (the `VersionCatalogAuditTests` precedent) — so the theme may document the anti-feature by name while only a real call trips the guard. (Also fixed a KDoc that contained a literal comment-terminator, which prematurely closed the doc block.)
- **Files:** `NoDynamicColorScanTest.kt`. **Commit:** `d0694de`.

**5. [Rule 3 - Blocking] no material-icons dependency for the DisclaimerBanner info icon**
- **Found during:** Task 3 (material3 1.4.0 no longer transitively brings `material-icons-core`, so `Icons.Outlined.Info` is unavailable).
- **Fix:** bundled a minimal inline `info` `ImageVector` (Material info_outline path data) inside `BloomComponents.kt` — asset-light, single fill color tinted by the `Icon` composable — per the UI-SPEC "bundle 2-3 ImageVectors rather than add material-icons-extended" ethos. No new dependency (keeps the catalog audit surface minimal). Also added the required `androidx.compose.ui.graphics.vector.path` import and dropped a member-not-import `and` matcher import in the test.
- **Files:** `BloomComponents.kt`, `BloomComponentsTest.kt`. **Commit:** `a6dec80`.

## Threat Model Compliance
- **T-19-SC (supply chain):** every added coordinate is first-party androidx / JetBrains / Robolectric via the audited catalog — no npm/pip/cargo/third-party install. `VersionCatalogAuditTests` re-run green after the additions (no analytics/auth/backend/networking substring entered the catalog).
- **T-19-02a (Material You info-disclosure / anti-feature):** `NoDynamicColorScanTest` forbids `dynamic*ColorScheme` in the designsystem package and is red-capable (comment-stripped scan of real source). The theme uses a fixed derived scheme; no wallpaper color is ever sampled.

## Known Stubs
None. `DisclaimerBanner` intentionally takes its copy as a parameter (it does not own the disclaimer constant — that arrives in Plan 04 per the UI-SPEC copywriting contract); this is a documented contract, not a stub.

## Self-Check: PASSED
- All 9 created files + 2 modified files exist on disk.
- All 3 task commits present in git history: `5bf933b`, `d0694de`, `a6dec80`.
- Real build/test evidence captured above (`:insights` 176/176, `:app` 70/70, assembleDebug 12.4 MB APK).

---
*Phase: 19-app-shell-consent*
*Completed: 2026-07-09*
