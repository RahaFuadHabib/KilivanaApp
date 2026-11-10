
// Top-level build.gradle.kts file
// This file declares plugins available to the modules in the project.

plugins {

    // Declares the Android Application plugin.
    // This plugin allows a module to build an Android application (APK/AAB).
    // apply false means the plugin is NOT applied to the root project.
    //The alias() function references a plugin defined in the Gradle Version Catalog, usually located at:
    //
    //gradle/libs.versions.toml
    alias(libs.plugins.android.application) apply false

    // Declares the Kotlin Android plugin.
    // This plugin enables Kotlin support for Android modules.
    // apply false means it will be applied only where needed.
    alias(libs.plugins.kotlin.android) apply false

    // Declares the Kotlin Compose Compiler plugin.
    // This plugin enables the Compose compiler integration
    // for modules using Jetpack Compose.
    alias(libs.plugins.kotlin.compose) apply false
}
