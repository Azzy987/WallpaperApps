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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WallpaperApps"

// Shared codebase — every app module depends on this.
include(":core")

// One module per Play Store app. Adding an app = one line here + a <module>/ folder.
include(":pixel11")
include(":iphone18")
include(":oneplus7")
include(":s25ultra")
include(":s26ultra")
include(":xiaomi17")
include(":ios27")
include(":iphone17")
include(":samsungfold8")

// Legacy flavor-based module (OnePlus 7 / 7T). Left untouched.
include(":app")
