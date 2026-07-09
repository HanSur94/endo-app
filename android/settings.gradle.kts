pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // The version catalog at gradle/libs.versions.toml is auto-loaded (default location).
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "endo-android"

// Only the pure-JVM engine module exists in Phase 17.
// `:app` (the Android application module) arrives in Phase 19 — do NOT add it here.
include(":insights")
