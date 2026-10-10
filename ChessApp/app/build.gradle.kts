plugins {
    alias(libs.plugins.android.application)
    // AGP 9 has built-in Kotlin support, so there is no `org.jetbrains.kotlin.android` plugin here.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.casualchess.app"
    compileSdk = 36

    defaultConfig {
        // IMPORTANT: change this to an ID under a domain/namespace you control before publishing to Google Play.
        applicationId = "com.casualchess.app"
        minSdk = 26 // Android 8.0: lets the launcher icon be a pure vector adaptive icon.
        // Google Play: new apps and updates must target API 36 (Android 16) or higher from 2026-08-31.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":chess-core"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
