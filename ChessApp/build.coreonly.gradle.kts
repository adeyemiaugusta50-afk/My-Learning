// Root build script used ONLY with -Pchess.coreOnly=true (see settings.gradle.kts): builds and tests the pure-JVM
// `chess-core` module on a machine that has no Android SDK. It is the same as build.gradle.kts minus the Android plugin.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
