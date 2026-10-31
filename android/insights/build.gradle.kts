// :insights — the pure-Kotlin-JVM analytics engine (the Android mirror of the Swift
// EndoInsights package). It is a plain `kotlin("jvm")` module: NO Android Gradle plugin,
// NO android./androidx. imports (BoundaryLintTests enforces this), and NO project(":app")
// dependency — the dependency arrow is one-way (:app -> :insights) and never reversed.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // One toolchain across the module; JUnit 6 requires JDK 17+, we pin 21.
    jvmToolchain(21)
}

dependencies {
    // kotlinx-serialization replaces Swift Codable for Insight + the golden-JSON drift test.
    implementation(libs.kotlinx.serialization.json)
    // JUnit Jupiter aggregator (api + params + engine).
    testImplementation(libs.junit.jupiter)
    // JUnit Platform launcher — required on the test runtime classpath under JUnit 6
    // (not a transitive dep of the Jupiter aggregator; Gradle no longer auto-provides it).
    testRuntimeOnly(libs.junit.platform.launcher)
}

// SAFE-01: the shared repo-root `l10n/` folder (sibling of `android/`) holds the per-language
// DE/FR/ES claims policies. It is the single source both platforms read; nothing is copied by hand.
val l10nDir = rootDir.parentFile.resolve("l10n")

// SAFE-01 (Pitfall 8 on Android): copy ONLY the three policy files onto the main classpath at
// `/l10n/claims-policy.<tag>.json`. The glossary and README are test-only/doc data and never ship.
val syncL10nPolicy = tasks.register<Sync>("syncL10nPolicy") {
    from(l10nDir) {
        include("claims-policy.*.json")
        into("l10n")
    }
    into(layout.buildDirectory.dir("generated/l10n-resources"))
}

// SAFE-01: registering the Sync task as a resources srcDir makes processResources depend on it.
sourceSets.main {
    resources.srcDir(syncL10nPolicy)
}

tasks.test {
    useJUnitPlatform()
    // SAFE-01 (Pitfall 4): tests read `l10n/` files by path (glossary, repo-vs-classpath parity), so the
    // folder is a declared input; a JSON edit invalidates the cached test result instead of staying green.
    inputs.dir(l10nDir)
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("l10nData")
    // SAFE-01 (Pitfall 6): tests resolve the repo root from this property, never by walking up
    // `user.dir` (this worktree is nested inside the main checkout, which may have its own `l10n/`).
    systemProperty("endo.repoRoot", rootDir.parentFile.absolutePath)
}
