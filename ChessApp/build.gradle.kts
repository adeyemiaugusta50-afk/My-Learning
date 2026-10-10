// AGP 9 bundles Kotlin support and defaults to an older Kotlin Gradle plugin. To use a newer one (needed to read
// libraries built with newer Kotlin) the official guidance is to put it on the top-level buildscript classpath.
// Keep this version in sync with `kotlin` in gradle/libs.versions.toml.
buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}
