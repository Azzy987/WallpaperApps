import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.ksp)
    id("dagger.hilt.android.plugin")
    alias(libs.plugins.kotlin.compose)
}

/**
 * Shared code for every Droidates wallpaper app.
 *
 * Everything that is identical across apps lives here — screens, viewmodels,
 * repositories, DI, utils and the shared resources. Per-app values are supplied
 * at runtime through `AppConfig.install(AppSpec)`, so this module never contains
 * a brand name, ad unit, application id or keystore.
 *
 * Fix a bug here once and every app picks it up on its next build.
 */
android {
    namespace = "com.droidates.wallpapers.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// `api` (not `implementation`) so the app modules inherit these transitively —
// an app module should only need to declare what is genuinely app-specific.
dependencies {
    api(platform(libs.androidx.compose.bom))

    api(libs.play.services.auth)
    api(libs.billing.client)
    api(libs.androidx.palette.ktx)
    api(libs.androidx.lifecycle.process)
    api(libs.androidx.tracing)

    api(libs.androidx.core.ktx)
    api(libs.androidx.lifecycle.runtime.ktx.v262)
    api(libs.androidx.activity.compose)

    api(libs.androidx.ui)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.ui.tooling.preview)
    api(libs.androidx.material)

    api(platform(libs.firebase.bom))
    api(libs.firebase.firestore)
    api(libs.firebase.analytics)
    api("com.google.firebase:firebase-auth")
    api("com.google.firebase:firebase-messaging")

    api(libs.androidx.navigation.compose)
    api(libs.coil.compose)
    api(libs.ads.mobile.sdk)

    api(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    api(libs.androidx.hilt.navigation.compose)

    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.coroutines.play.services)

    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.profileinstaller)

    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    api(libs.androidx.datastore.preferences)
    api(libs.androidx.datastore.preferences.core)

    api(libs.androidx.material.icons.core)
    api(libs.androidx.material.icons.extended)
    api(libs.material3)
    api(libs.androidx.ui.text.google.fonts)

    api(libs.app.update)
    api(libs.app.update.ktx)

    api(libs.androidx.work.runtime.ktx)
    api(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    api(libs.gson)
    api(libs.androidx.core.splashscreen)

    debugApi(libs.androidx.ui.tooling)
}
