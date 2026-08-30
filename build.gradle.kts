// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Requested here (unapplied) so its version/classpath resolves. app/build.gradle.kts
    // applies it imperatively, only when google-services.json is present.
    alias(libs.plugins.google.services) apply false
}