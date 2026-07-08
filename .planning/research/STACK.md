# Stack Research — v2.0 Android Port (Version Pins & Integration Specifics)

**Domain:** Native Android port of a privacy-first health/self-tracking app (Kotlin + Compose, local-only, near-zero dependencies)
**Researched:** 2026-07-08
**Confidence:** MEDIUM-HIGH (every load-bearing pin verified against developer.android.com / kotlinlang.org / Maven within the last day; web-search tier per classify-confidence seam = MEDIUM for cross-checked findings)

> **Scope note:** All stack *choices* are locked in [ANDROID-PORT-PLAN.md](ANDROID-PORT-PLAN.md) §1. This document does not re-open them — it pins exact current versions, Maven coordinates, and the integration details that make the locked decisions executable. The two consciously revisitable calls (Canvas-vs-Vico, Drive appDataFolder) stay revisitable; everything else here is a pin, not a proposal.

## Recommended Stack

### Core Technologies

| Technology | Version (pin) | Purpose | Why this exact pin |
|------------|---------------|---------|--------------------|
| **Kotlin** | **2.3.21** | Language for `:app` and `:insights` | Kotlin 2.4.0 went stable in June 2026 but is only weeks old; **AGP 9.2's own docs pair AGP 9.2.0 with Kotlin 2.3.21**, kotlinx-serialization 1.11.0 is built against 2.3.20, and KSP 2.3.x is proven on 2.3.x. Pin 2.3.21 for a friction-free port; bump to 2.4.x is a documented, low-risk follow-up (see Stack Patterns). **Confidence: HIGH on 2.3.21 working; MEDIUM on 2.4.0 being frictionless with AGP 9.2 today.** |
| **Android Gradle Plugin** | **9.2.0** | Build (`com.android.application`) | Latest stable (April 2026). **AGP 9 has built-in Kotlin enabled by default** — do NOT apply `org.jetbrains.kotlin.android` in `:app` (it is incompatible with the new DSL, and the `android.builtInKotlin=false` opt-out is removed in AGP 10). Supports up to API 37, so compileSdk 36 is fully covered. |
| **Gradle** | **9.4.1+** (wrapper; use latest 9.x) | Build system | 9.4.1 is the documented minimum for AGP 9.2. Kotlin DSL + version catalog per locked decision. |
| **JDK** | **21** (toolchain) | Build/test JVM | AGP 9 minimum is JDK 17; pin the toolchain to 21 because JUnit 6 requires 17+ and Robolectric-with-SDK-36 (if ever used) requires 21. One toolchain, no per-module drift. |
| **Jetpack Compose** | **BOM `androidx.compose:compose-bom:2026.06.00`** | UI (Compose + Material 3) | Latest stable BOM (June 2026). Import the BOM `platform(...)` and declare `ui`, `material3`, `ui-tooling-preview`, `ui-test-junit4` **without versions** — the BOM is the single version knob and keeps the catalog short (the audit artifact). Compose compiler ships with Kotlin since 2.0 via `org.jetbrains.kotlin.plugin.compose` at the Kotlin version (2.3.21). |
| **Room** | **2.8.4** (`androidx.room`) | Persistence, 10 mirrored entities | Latest stable of the `androidx.room` line. **Deliberately NOT `androidx.room3` 3.0.0** (stable 2026-07-01): Room 3 is a new-coordinates, KMP-focused major rewrite and Google's own docs still recommend Room 2.x for Android-only apps. Room 2.x is in maintenance mode (bug fixes continue) — that is *stability*, not risk, for a v1 port. Coroutines/Flow support lives in `room-runtime` (the old `room-ktx` was merged in as of 2.7 — do not add it). Compiler via **KSP only** (no kapt). |
| **KSP** | **2.3.9** (`com.google.devtools.ksp`, or latest 2.3.x) | Room compiler processing | Since KSP 2.3.0 the plugin is versioned standalone (no more `-1.0.x` Kotlin-tied scheme) and works with Kotlin 2.2+ including 2.4.0. AGP 9 auto-upgrades KSP below 2.2.10-2.0.2, so any 2.3.x pin is safe. |
| **DataStore** | **1.2.0** (`androidx.datastore:datastore-preferences`) | Consent flag + reminder prefs (outside the health DB) | Latest stable (March 2026). Preferences DataStore only — no proto needed for a boolean + a couple of prefs. (1.3.0-alpha adds built-in DataStore encryption; alpha — not for v1, see What NOT to Use.) |
| **Health Connect** | **1.1.0** (`androidx.health.connect:connect-client`) | Read-only import of the 4 record types | First and current **stable** (Oct 2025). 1.2.0-alpha04 exists but adds nothing this app needs (matchmaking APIs). All four mapped record types (`MenstruationFlowRecord`/`MenstruationPeriodRecord`, `SleepSessionRecord`, `ExerciseSessionRecord`, `NutritionRecord`) are in 1.1.0. API-level notes below in Version Compatibility. |
| **kotlinx-serialization** | runtime **`org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0`**, plugin **`org.jetbrains.kotlin.plugin.serialization:2.3.21`** | Versioned export/import JSON | 1.11.0 is the latest stable (built against Kotlin 2.3.20; forward-compatible with 2.3.21). The compiler-plugin version must equal the Kotlin version — that is why the Kotlin pin drives this pin. Its new public JSON-exception API is genuinely useful for the import path (clean "wrong file / corrupt file" errors without leaking file contents into messages). |
| **Crypto: `javax.crypto` (platform, zero-dependency)** | platform (API 26+) | AES-GCM passphrase-encrypted export/import | **The no-dependency answer exists and is sufficient at minSdk 26:** `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` is available since API 26, and `Cipher.getInstance("AES/GCM/NoPadding")` is backed by Conscrypt on every supported device (the well-known Tink AES-GCM caveat applies only to API ≤ 19 — irrelevant here). No Tink, no Bouncy Castle, no native Argon2 lib. Exact parameters below in the crypto spec. **Confidence: HIGH.** |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.room:room-compiler` | 2.8.4 (via `ksp(...)`) | Room codegen | `:app` only. |
| `androidx.room:room-testing` | 2.8.4 | `MigrationTestHelper` | androidTest — the migration scaffold Phase 2 requires. |
| `androidx.activity:activity-compose` | current stable at catalog creation | `ComponentActivity.setContent` | Required Compose host; not in the BOM (androidx.activity has its own release train — take the current stable from the AndroidX releases page when writing the catalog; not load-bearing). |
| `androidx.lifecycle:lifecycle-runtime-compose` | current stable at catalog creation | `collectAsStateWithLifecycle` for Flow→UI | Same note as activity-compose. |
| `androidx.core:core-ktx` | current stable at catalog creation | Platform KTX niceties + `FileProvider` lives in androidx.core | PDF share via `FileProvider` + `ACTION_SEND` (locked decision) needs androidx.core anyway. |
| **JUnit Jupiter** | `org.junit.jupiter:junit-jupiter:6.1.1` | `:insights` JVM test framework | Pure-JVM module → `useJUnitPlatform()`, no Android test plugin needed. Jupiter 6.1.1 (June 2026) requires Java 17 — covered by the JDK 21 toolchain. Parameterized tests map cleanly onto the Swift Testing parameterized suites being ported. Add `org.jetbrains.kotlin:kotlin-test-junit5` (version = Kotlin) only if the `kotlin.test` assertion facade is wanted; Jupiter's own assertions are sufficient. |
| **JUnit 4** | `junit:junit:4.13.2` | `:app` unit tests (copy audit, structural scans) + androidTest runner base | AGP's unit-test and instrumentation stack is JUnit4-native; using JUnit 5 inside an AGP module needs a third-party plugin (mannodermaus) — not worth the dependency. Split: Jupiter in `:insights`, JUnit4 in `:app`. |
| `androidx.test.ext:junit` | 1.3.0 | `AndroidJUnit4` runner for androidTest | Room migration tests + Compose UI tests. |
| `androidx.test.espresso:espresso-core` | 3.7.0 | Intent/interop assertions in androidTest | Only where Compose test APIs don't reach (e.g., share-intent assertions). |
| `androidx.compose.ui:ui-test-junit4` + `ui-test-manifest` | via BOM (no version) | Compose UI tests (consent gate) | The Phase 3 "health surface unreachable pre-consent" test. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **Android Studio** (current stable) | IDE for `android/` | The iOS side keeps Xcode; Gradle and Xcode never see each other's files (locked monorepo layout). Studio's bundled JDK is fine, but pin `org.gradle.java.installations` / toolchain 21 so CLI and CI agree. |
| **Gradle version catalog** (`android/gradle/libs.versions.toml`) | The audited dependency list | Locked decision; the full catalog is written out in Installation below. A JVM guard test can parse this file and assert no forbidden coordinates (analytics/auth/backend SDKs) ever appear — the Android mirror of the iOS dependency-audit stance. |
| **Gradle wrapper 9.4.1+** | Reproducible builds | Commit the wrapper; AGP 9.2 refuses older Gradle. |
| **Play Console pre-launch report** | Phase 8 gate | Free; runs on real devices — the Android counterpart of "budget for real-device testing." Health Connect flows still need a real device or an API 34+ emulator with the HC module. |

## Installation

`android/gradle/libs.versions.toml` — the complete catalog for Phases 1–8 (this short list IS the zero-dependency audit surface):

```toml
[versions]
kotlin = "2.3.21"
agp = "9.2.0"
ksp = "2.3.9"                    # standalone versioning since KSP 2.3.0; works with Kotlin 2.2+
composeBom = "2026.06.00"
room = "2.8.4"
datastore = "1.2.0"
healthConnect = "1.1.0"
kotlinxSerialization = "1.11.0"
activityCompose = "<current stable at catalog creation>"
lifecycle = "<current stable at catalog creation>"
coreKtx = "<current stable at catalog creation>"
junitJupiter = "6.1.1"
junit4 = "4.13.2"
androidxTestJunit = "1.3.0"
espresso = "3.7.0"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }                      # version from BOM
compose-material3 = { group = "androidx.compose.material3", name = "material3" } # version from BOM
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }   # coroutines/Flow included; no room-ktx
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
health-connect-client = { group = "androidx.health.connect", name = "connect-client", version.ref = "healthConnect" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
junit-jupiter = { group = "org.junit.jupiter", name = "junit-jupiter", version.ref = "junitJupiter" }
junit4 = { group = "junit", name = "junit", version.ref = "junit4" }
androidx-test-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestJunit" }
espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }          # :insights only
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

Module plugin application (the AGP-9 shape — note the deliberate absences):

```kotlin
// android/insights/build.gradle.kts — pure JVM, NO Android plugin (locked)
plugins {
    alias(libs.plugins.kotlin.jvm)
}
kotlin { jvmToolchain(21) }
dependencies { testImplementation(libs.junit.jupiter) }
tasks.test { useJUnitPlatform() }

// android/app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)   // built-in Kotlin: do NOT apply org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}
android {
    compileSdk = 36
    defaultConfig { minSdk = 26; targetSdk = 36 }
}
dependencies {
    implementation(project(":insights"))      // one-way arrow, never reversed (locked)
    implementation(platform(libs.compose.bom))
    // ... catalog entries above; androidTestImplementation(platform(libs.compose.bom)) too
    ksp(libs.room.compiler)
}
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Room 2.8.4 (`androidx.room`) | **Room 3.0.0** (`androidx.room3:room3-runtime` / `room3-compiler`, stable 2026-07-01) | Only if this codebase ever goes KMP (explicitly rejected for v1 in the port plan). Room 3 is Kotlin-codegen-only, KSP-only, coroutines-first, and lives at new coordinates precisely so 2.x apps aren't forced over. Entities port mechanically (`androidx.room.*` → `androidx.room3.*`) if ever needed — choosing 2.8.4 now burns nothing. |
| `javax.crypto` PBKDF2 + AES-GCM | **Tink** `com.google.crypto.tink:tink-android:1.22.0` | If the crypto surface grows beyond "one passphrase → one AEAD-encrypted file" (key rotation, envelope encryption, keysets). For a single self-contained export file, Tink adds a dependency without adding security. |
| PBKDF2WithHmacSHA256 (platform) | **Argon2id** (via `argon2kt` / lazysodium — native code) | Argon2 is the stronger KDF on paper (memory-hard), but it is **not in the Android platform** (the OpenJDK `javax.crypto.KDF` Argon2 JEP is a preview and not on Android). Adopting it means a native-library dependency — against the locked posture. Revisit only if the threat model changes to "attacker with GPU farm targets exported files"; the format header (below) makes a KDF swap non-breaking. |
| JUnit Jupiter 6.1.1 in `:insights` | **Kotest** | Rejected: another dependency and another assertion dialect for no capability the port needs; Jupiter parameterized tests already mirror the Swift Testing suites. |
| Real instrumentation for migrations/Compose gate | **Robolectric 4.16.1** | Locked decision keeps Robolectric out of the default stack. If a JVM-speed Android-ish test is ever truly needed, 4.16.1 supports SDK 36 (requires JDK 21 — already the toolchain). Not in the catalog for v1. |
| Compose Canvas charts (owned) | **Vico** (Compose-native charting) | The locked, recorded escape hatch — resolve its then-current version only if invoked in Phase 6. Not a starting dependency; deliberately unpinned here so nobody adds it "because it was in the catalog." |
| Kotlin 2.3.21 | **Kotlin 2.4.0** | Bump when AGP (9.3+/10) documents KGP 2.4 compatibility. Mechanics: bump `kotlin` + verify KSP ≥ 2.3.5 (2.3.9 already qualifies) and kotlinx-serialization runtime (1.11.0 is forward-compatible). Nothing in the app blocks it; it is purely a toolchain-maturity wait. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `androidx.security:security-crypto` (Jetpack Security — `EncryptedSharedPreferences` / `EncryptedFile`) | **Deprecated by Google in April 2025** at 1.1.0-alpha07; known keyset-corruption and main-thread issues; unmaintained. Any tutorial recommending it is stale. | `javax.crypto` for the export file; plain DataStore for prefs (the consent flag is not a secret — it's a gate). |
| `org.jetbrains.kotlin.android` plugin in `:app` | Incompatible with AGP 9's built-in-Kotlin DSL; builds break. (Old tutorials all show it — this is the #1 stale-doc trap for this port.) | AGP 9 built-in Kotlin; KGP applied only in the pure-JVM `:insights` via `kotlin("jvm")`. |
| **kapt** | Legacy annotation processing; slower; Room 2.8 fully supports KSP and Room 3 dropped kapt entirely. | `ksp(libs.room.compiler)`. |
| `androidx.room3` for this app | New-coordinate KMP-focused major version; Google still recommends 2.x for Android-only; mixing `androidx.room.*` docs/snippets with `androidx.room3.*` imports is a fresh source of confusion in 2026 posts. | Room 2.8.4. |
| DataStore 1.3.0-alpha (built-in encryption) | Alpha, and it encrypts *prefs* — this app's secret-bearing artifact is the export file, not the consent flag. Don't ship alpha crypto in a health app. | DataStore 1.2.0 stable. |
| Gson / Moshi / Jackson | kotlinx-serialization is the first-party-adjacent pick, compile-time-safe, and already required for the versioned export schema; a second JSON stack is pure surface area. | `kotlinx-serialization-json:1.11.0`. |
| Google Fit APIs, per-vendor health SDKs | Deprecated / rejected in the locked plan. | Health Connect 1.1.0. |
| Firebase / analytics / auth / backend SDKs of any kind | Locked invariant; the version catalog above is the greppable proof. Add a JVM test that fails if any such coordinate appears in `libs.versions.toml`. | Nothing. Play vitals only. |
| MPAndroidChart / View-based chart libs | View-interop in a Compose app; rejected in the locked plan. | Owned Canvas layer (Vico = recorded escape hatch only). |

## Stack Patterns by Variant

**SDK levels (resolving the locked "current Play-required level at build time"):**
- **compileSdk 36, targetSdk 36, minSdk 26.** Google Play requires new apps and updates to target **API 36 (Android 16) by 2026-08-31** — the Android app ships at or after that date, so target 36 from the first commit. AGP 9.2 supports up to API 37, so compileSdk 36 has headroom. Note the API-36 behavior changes that touch this app: edge-to-edge is fully enforced (Compose + Material 3 handles this if insets are done via `WindowInsets` from day one) and predictive back should be enabled from the start rather than retrofitted.

**AGP 9 built-in Kotlin + compiler plugins (the one genuinely new integration wrinkle):**
- `:app`: apply `com.android.application` only; Kotlin compilation is built in. Apply `org.jetbrains.kotlin.plugin.compose` and `org.jetbrains.kotlin.plugin.serialization` at the Kotlin version (2.3.21). To pin a KGP newer than AGP's embedded runtime KGP, add `org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21` to the root `buildscript` classpath (the AGP-9-documented mechanism); AGP auto-aligns KSP if it's older than the embedded floor.
- `:insights`: unaffected by all of this — plain `kotlin("jvm")` 2.3.21, stdlib only (boundary lint test enforces it, per the locked plan).
- **Fallback if serialization-plugin friction appears under built-in Kotlin:** move the `@Serializable` export DTOs into a tiny pure-JVM module (where KGP applies normally) and keep `:app` plugin-free. Cheap, and it also makes the export schema unit-testable at JVM speed. Do NOT reach for `android.builtInKotlin=false` — it dies in AGP 10.

**Encrypted export/import — exact crypto spec (all `javax.crypto` / `java.security`, zero deps):**
- KDF: `PBKDF2WithHmacSHA256`, **≥ 600,000 iterations** (OWASP current guidance for PBKDF2-HMAC-SHA256), 16-byte salt from `SecureRandom`, 256-bit derived key.
- Cipher: `AES/GCM/NoPadding`, 12-byte random IV per encryption, 128-bit auth tag; never reuse an IV under the same key (fresh salt+IV per export makes this structural).
- File layout: cleartext header `{ magic, formatVersion, kdf: {alg, iterations, salt}, iv }` + ciphertext of the schema-versioned JSON. Header carries the KDF params so iterations can rise (or the KDF can change) without breaking old exports — the import path reads params from the file, never from constants.
- Threat-model honesty for the docs: PBKDF2 is CPU-hard, not memory-hard; the UX should encourage a real passphrase (length guidance, no composition rules). This is the correct standard-library tradeoff for this app; the header makes it upgradeable.
- Write through SAF (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`) — no storage permissions, user-chosen destination (locked decision).

**Health Connect wiring (connect-client 1.1.0):**
- **Availability is three-state, gate at runtime via `HealthConnectClient.getSdkStatus(context)`:** Android 14+ (API 34) → framework module, always present; Android 9–13 (API 28–33) → separate Play Store app, may need install/update (`SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`); **Android 8.0/8.1 (API 26–27) → Health Connect cannot exist** (the provider app itself requires API 28+), so `SDK_UNAVAILABLE` is permanent there. Manual logging is the primary path everywhere; the import feature simply doesn't render on 26/27.
- Permissions (manifest `<uses-permission>`; read-only per locked invariant — a test asserts zero `android.permission.health.WRITE_*`): `READ_MENSTRUATION` (covers both `MenstruationFlowRecord` and `MenstruationPeriodRecord`), `READ_SLEEP`, `READ_EXERCISE`, `READ_NUTRITION`.
- Rationale surface is **mandatory** (Play + HC requirement): an activity handling `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` for Android 13-, plus an `activity-alias` for `android.intent.action.VIEW_PERMISSION_USAGE` with category `android.intent.category.HEALTH_PERMISSIONS` for Android 14+. This is also the natural home for the ported rationale-before-permission UX.
- Phase 8 corollary: Play Console **Health apps declaration** + Data safety form must match the manifest; the pre-launch report checks the rationale intent handlers exist.

**Testing split (pins for the locked decision):**
- `:insights` (pure JVM): JUnit Jupiter **6.1.1**, `useJUnitPlatform()`, JDK 21 toolchain. Parameterized tests carry the Swift suite's case-count parity requirement.
- `:app` unit tests (`test/`): JUnit **4.13.2** (AGP-native; copy audit, boundary/structural scans, catalog-audit test).
- `:app` androidTest: `androidx.test.ext:junit:1.3.0` + `espresso-core:3.7.0` + Compose `ui-test-junit4`/`ui-test-manifest` (BOM) + `room-testing:2.8.4` for `MigrationTestHelper`.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| AGP 9.2.0 | Gradle ≥ 9.4.1, JDK ≥ 17, max API 37 | Official AGP compatibility table. AGP 9.1 needed Gradle 9.3.1; don't mix an older wrapper in. |
| AGP 9.x built-in Kotlin | KGP ≥ 2.2.10 (embedded); override via buildscript classpath | `org.jetbrains.kotlin.android` must NOT be applied; opt-out flag removed in AGP 10. |
| Kotlin 2.3.21 | serialization plugin 2.3.21 ↔ runtime 1.11.0 | Compiler-plugin version must equal Kotlin version; runtime 1.11.0 is built on 2.3.20 and forward-compatible. |
| KSP 2.3.x (2.3.9) | Kotlin 2.2+ (incl. 2.3.21 and 2.4.0) | Standalone versioning since KSP 2.3.0; AGP 9 auto-bumps KSP below its floor. |
| Room 2.8.4 | KSP (kapt still works on 2.x but don't) | `room-runtime` includes coroutines/Flow; no `room-ktx`. `androidx.room3` is a different, incompatible namespace. |
| connect-client 1.1.0 | minSdk 26 (SDK floor) / provider app needs API 28+ / framework module API 34+ | Three-state availability — see wiring notes. |
| Compose BOM 2026.06.00 | Kotlin 2.x + `org.jetbrains.kotlin.plugin.compose` at the Kotlin version | Compose compiler has shipped with Kotlin since 2.0 — never pin a separate compiler version. |
| JUnit Jupiter 6.1.1 | Java 17+ | Fine under the JDK 21 toolchain; keep Jupiter out of `:app` (JUnit4 there). |
| targetSdk 36 | Play policy deadline 2026-08-31 | New apps/updates must target API 36 by then; ship at 36 from day one. |
| Robolectric 4.16.1 (only if ever adopted) | SDK 36 requires JDK 21 | Already satisfied by the toolchain pin; not in the v1 catalog. |

## Sources

- developer.android.com/build/releases/{about-agp, agp-9-2-0-release-notes, agp-9-0-0-release-notes} — AGP 9.2.0 stable, Gradle 9.4.1 min, JDK 17 min, built-in Kotlin mechanics, KSP auto-bump — MEDIUM (official docs via fetch, cross-checked)
- developer.android.com/jetpack/androidx/releases/{room, room3, datastore, health-connect, test} — Room 2.8.4 / Room3 3.0.0 (2026-07-01) split + "2.x for Android-only" guidance, DataStore 1.2.0, connect-client 1.1.0, androidx.test 1.3.0/espresso 3.7.0 — MEDIUM
- developer.android.com/develop/ui/compose/bom + bom-mapping — Compose BOM 2026.06.00 — MEDIUM
- blog.jetbrains.com (Kotlin 2.4.0 June 2026; 2.3.20 March 2026; AGP 9 migration post) + kotlinlang.org/docs/{releases, ksp-quickstart, gradle-configure-project} — Kotlin/KSP pins — MEDIUM
- github.com/Kotlin/kotlinx.serialization/releases — 1.11.0 latest, Kotlin 2.3.20 baseline (release *dates* from the fetch were garbled; version/baseline cross-checked against Maven search) — MEDIUM
- developer.android.com/health-and-fitness/health-connect/get-started — SDK floor API 26, HC app requires API 28+, framework module API 34+ — MEDIUM
- support.google.com/googleplay + developer.android.com/google/play/requirements/target-sdk — target API 36 by 2026-08-31 — MEDIUM
- github.com/tink-crypto/tink-java + developers.google.com/tink — tink-java 1.22.0 (alternative-only); AES-GCM API ≤ 19 caveat (irrelevant at minSdk 26) — MEDIUM
- developer.android.com/jetpack/androidx/releases/security + ProAndroidDev deprecation write-ups — security-crypto deprecated April 2025 at 1.1.0-alpha07 — MEDIUM
- developer.android.com/reference/javax/crypto/SecretKeyFactory + OWASP password-storage guidance — PBKDF2WithHmacSHA256 since API 26; ≥600k iterations — MEDIUM
- robolectric.org + github releases — 4.16.1, SDK 36 → JDK 21 — MEDIUM
- docs.junit.org — Jupiter 6.1.1 (2026-06-28), Java 17 requirement — MEDIUM

---
*Stack research for: Endo-App v2.0 Android port (version pins for locked decisions)*
*Researched: 2026-07-08*
