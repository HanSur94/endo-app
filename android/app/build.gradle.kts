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

    ksp {
        // Room v1 schema export is IRREVERSIBLE and must be born correct — commit the
        // generated schemas under android/app/schemas/ (Plan 02 adds exportSchema entities).
        arg("room.schemaLocation", "$projectDir/schemas")
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
    // kotlinx-serialization — the versioned export/import JSON DTOs (contract lands this phase).
    implementation(libs.kotlinx.serialization.json)

    // :app unit tests use JUnit 4 (AGP-native); Jupiter stays in :insights.
    testImplementation(libs.junit4)

    // androidTest — instrumentation runner base + Room MigrationTestHelper.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
}
