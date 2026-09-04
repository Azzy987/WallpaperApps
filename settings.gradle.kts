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
include(":gtavi")
include(":redmi")

// Legacy flavor-based module (OnePlus 7 / 7T), UNREGISTERED.
//
// :app predates :core and carries its own standalone copy of every screen (175
// Kotlin files). It has fallen behind two shared upgrades and no longer configures:
//   - GMA Next-Gen migration removed the `play-services-ads` catalog alias it uses;
//     its 8 ad files still call the old com.google.android.gms.ads API.
//   - Billing 6 -> 8 removed the BillingClient methods its BillingRepository calls.
//
// Because Gradle resolves every included build script, those errors failed EVERY
// task in the project — `clean` included — not just :app's own builds. Commenting
// out the include is what unblocks the other nine apps.
//
// Nothing is deleted: app/ is still on disk and in git history. Its two flavors ship
// com.droidates.oneplus7wallpapers and com.droidates.oneplus7twallpapers, which are
// DIFFERENT Play listings from :oneplus7 (com.droidates.oneplus7Wallpapers, capital
// W) — so :oneplus7 does not replace them. To ship them again, migrate each flavor
// into its own :core-based module per NEW_APP_GUIDE.md rather than reviving :app.
// include(":app")
