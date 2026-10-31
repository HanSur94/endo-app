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

// `:app` — the Android Application module (data layer added in Phase 18; Compose UI shell
// in Phase 19). `:insights` — the pure-JVM analytics engine. The dependency arrow is
// one-way (:app -> :insights) and never reversed.
include(":app", ":insights")
