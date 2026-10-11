// All Gradle plugins are declared here, once, with `apply false`, and the modules apply them by alias.
//
// Why this matters: Gradle gives every plugin it loads from the root build a SHARED class loader. The Kotlin Gradle
// plugin must be able to see Android Gradle Plugin (AGP) classes (AGP 9 has built-in Kotlin support and the two call
// into each other). If Kotlin were put on the root classpath while AGP was only loaded inside `:app`, Kotlin could not
// see AGP's classes and the build failed with:
//     NoClassDefFoundError: com/android/build/gradle/api/BaseVariant
//
// The Kotlin version below is deliberately AGP 9's own default (2.2.10, per the AGP 9 release notes). Jetpack Compose
// libraries "target Kotlin 2.2 and require kotlinc 2.2" (Compose UI release notes), so no newer Kotlin is needed.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
