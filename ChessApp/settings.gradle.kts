pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "CasualChess"

// Pure Kotlin/JVM chess engine, game logic and persistence codec. No Android dependencies.
include(":chess-core")

// The Android app needs the Android SDK and the Android Gradle Plugin. Pass -Pchess.coreOnly=true to build and test only
// the engine on a machine (or CI job) that has neither; that mode also uses build.coreonly.gradle.kts as the root script.
if (providers.gradleProperty("chess.coreOnly").orNull == "true") {
    rootProject.buildFileName = "build.coreonly.gradle.kts"
} else {
    include(":app")
}
