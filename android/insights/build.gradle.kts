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

tasks.test {
    useJUnitPlatform()
}
