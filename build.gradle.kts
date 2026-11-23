// Top-level build file for AlpAI.
// Plugin versions live in gradle/libs.versions.toml (the version catalog).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    // Screenshot tests (Paparazzi) — test-only, no effect on the release build.
    alias(libs.plugins.paparazzi) apply false
}
