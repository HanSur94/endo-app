// :app — the Android Application module (com.endoapp). This phase (18) ships only the data
// layer + its bridge to :insights; the Compose UI shell arrives in Phase 19.
//
// AGP 9 has BUILT-IN Kotlin: applying `com.android.application` compiles Kotlin already.
// DELIBERATELY do NOT apply `org.jetbrains.kotlin.android` — it is incompatible with AGP 9's
// built-in-Kotlin DSL and breaks the build (the #1 stale-doc trap for this port; STACK.md).
// The `android.builtInKotlin=false` opt-out is removed in AGP 10, so there is no going back —
// the module is built-in-Kotlin from day one.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    // Compose compiler plugin — ships with Kotlin (2.3.21); required for any @Composable to compile.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    // Placeholder identity, finalizable at release (Phase 25).
    namespace = "com.endoapp.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.endoapp.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Compose UI (Phase 19 shell). The compiler is wired by the kotlin.compose plugin above;
    // this flag turns on the Compose build features (composable compilation, previews).
    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric must load the merged manifest + resources to render composables on the
            // JVM lane — without this the Compose test harness cannot inflate an Android context.
            isIncludeAndroidResources = true
        }
    }

    ksp {
        // Room v1 schema export is IRREVERSIBLE and must be born correct — commit the
        // generated schemas under android/app/schemas/ (Plan 02 adds exportSchema entities).
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    sourceSets {
        // Expose the committed schemas to instrumentation tests so MigrationTestHelper (Class-based
        // constructor) can load `<dbClassFqn>/<version>.json` from androidTest assets and prove the
        // v1 baseline is a valid migration starting point (Plan 02 Task 3).
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

kotlin {
    // One toolchain across every module; matches :insights and CI (JDK 21).
    jvmToolchain(21)
}

dependencies {
    // One-way dependency arrow (:app -> :insights), never reversed — :insights stays pure JVM.
    implementation(project(":insights"))
    // Room 2.8.4 — persistence. Compiler runs via KSP (never kapt).
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    // DataStore — consent flag + reminder prefs, outside the health database.
    implementation(libs.datastore.preferences)
    // WorkManager — the daily check-in reminder's INEXACT periodic scheduling (com.endoapp.app.reminders,
    // the app's ONLY notification/alarm surface). Never an exact alarm; survives reboot on its own.
    implementation(libs.work.runtime)
    // kotlinx-serialization — the versioned export/import JSON DTOs (contract lands this phase).
    implementation(libs.kotlinx.serialization.json)
    // Health Connect (androidx.health.connect:connect-client) — the READ-ONLY import broker (Phase 21).
    // The app declares only the four health READ perms + READ_HEALTH_DATA_HISTORY; zero WRITE, no
    // INTERNET, no background read. Availability is gated per session via HealthConnectClient.getSdkStatus.
    implementation(libs.health.connect.client)

    // Jetpack Compose (Phase 19 UI). The BOM is the single version knob — every artifact below
    // is versionless and pinned by the platform() import.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)

    // :app unit tests use JUnit 4 (AGP-native); Jupiter stays in :insights.
    testImplementation(libs.junit4)
    // JVM Compose test lane: Robolectric renders composables on the JVM (no emulator). The BOM
    // platform must be re-imported on the test classpath so the ui-test artifacts resolve versionless.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    // Coroutines virtual-time test lane — drives the InsightsCoordinator debounce (advanceUntilIdle)
    // and off-main recompute deterministically on the JVM (no real clock, no emulator).
    testImplementation(libs.kotlinx.coroutines.test)
    // WorkManager test lane — WorkManagerTestInitHelper/TestDriver proves the reminder enqueues an
    // INEXACT periodic unique work (and never an exact alarm) on the JVM Robolectric lane.
    testImplementation(libs.work.testing)
    // ui-test-manifest contributes the <activity androidx.activity.ComponentActivity> that
    // createComposeRule() launches. It must merge into the DEBUG manifest (debugImplementation),
    // not the test classpath — Robolectric reads the merged debug manifest, so a testImplementation
    // here would leave the activity unresolved ("Unable to resolve activity for Intent … ComponentActivity").
    debugImplementation(libs.compose.ui.test.manifest)

    // androidTest — instrumentation runner base + Room MigrationTestHelper.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
}
