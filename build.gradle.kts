// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    // Resolves the version-less id("dagger.hilt.android.plugin") applied in :app
    id("com.google.dagger.hilt.android") version "2.55" apply false
}

// Mediation adapters (Meta, Unity) still declare a dependency on the LEGACY Google
// Mobile Ads SDK. We ship GMA Next-Gen, which already contains every class those
// adapters need, so pulling the legacy artifact in as well fails the build on duplicate
// symbols. Excluding it is the configuration Google documents for Next-Gen + mediation.
//
// Applied to every subproject rather than just :core because the exclusion has to hold
// on the final APK's resolved classpath, and each of the twelve app modules resolves its
// own. A per-module exclusion would silently miss any app added later.
subprojects {
    configurations.configureEach {
        exclude(group = "com.google.android.gms", module = "play-services-ads")
        exclude(group = "com.google.android.gms", module = "play-services-ads-lite")

        // Both mediation adapters request kotlin-stdlib 2.3.0 directly. Gradle's conflict
        // resolution then raises stdlib for the WHOLE project, and the 2.0.21 compiler
        // refuses to read 2.3.0 metadata ("expected version is 2.0.0") — the build fails
        // in :core:kspDebugKotlin before it ever reaches an adapter class.
        //
        // Pin stdlib to the compiler's own version instead of upgrading Kotlin: the
        // adapters are plain Java/Android libraries that need no 2.3 language features,
        // and a Kotlin upgrade across twelve shipping apps is a far larger change than
        // adding two ad partners should require. Revisit this line if Kotlin is upgraded.
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
        }
    }
}