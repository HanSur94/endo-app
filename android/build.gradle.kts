// Root build script — no logic. It only declares the plugins the version catalog
// pins so every subproject applies them at a single, audited version. `apply false`
// means the plugins are resolved here but applied per-module (see insights/build.gradle.kts).
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
