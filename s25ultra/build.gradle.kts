import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.ksp)
    id("dagger.hilt.android.plugin")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

// Signing details live in <module>/keystore.properties (git-ignored) so passwords
// never enter source control. See NEW_APP_GUIDE.md.
val keystorePropsFile = rootProject.file("s25ultra/keystore.properties")
val keystoreProps = Properties()
keystorePropsFile.takeIf { it.exists() }?.let { f ->
    f.inputStream().use { keystoreProps.load(it) }
}

// All wallpaper apps live in ONE Firebase project (wallpaper-apps-cad2c), and a
// single google-services.json lists every registered package. Keep one shared copy
// at the project root instead of a per-module duplicate: re-download it once after
// registering a new app and every module picks up the change.
//
// NOTE: each package must still be added in the Firebase console — Firebase mints a
// distinct mobilesdk_app_id per package, and the plugin fails with
// "No matching client found" if yours is missing from the file.
val sharedGoogleServices = rootProject.file("google-services.json")
if (sharedGoogleServices.exists()) {
    copy {
        from(sharedGoogleServices)
        into(projectDir)
    }
}

android {
    namespace = "com.droidates.s25ultrawallpapers"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.droidates.s25ultrawallpapers"
        minSdk = 28
        targetSdk = 36
        versionCode = 1131
        versionName = "3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Play Console warns when an AAB ships native code without a symbol file.
            // The only .so files here come from AndroidX (graphics-path, datastore) and
            // are already stripped by their vendors, so crashes in them would otherwise
            // show as unreadable addresses. FULL packages the symbol table into the AAB
            // so Play can symbolicate native crashes and ANRs.
            ndk {
                debugSymbolLevel = "FULL"
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ""
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        // False positive: WallpaperApplication (in :core) does implement
        // Configuration.Provider and getWorkManagerConfiguration(), but lint cannot
        // see across the module boundary from this app's Application subclass.
        // The manifest correctly removes WorkManagerInitializer via tools:node="remove".
        disable += "RemoveWorkManagerInitializer"
    }
}

dependencies {
    // Every screen, viewmodel and utility comes from here.
    implementation(project(":core"))

    // Hilt's Gradle plugin requires the runtime artifact declared in this module,
    // even though :core already exposes it transitively.
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Room's annotation processor must run in the module that declares @Entity.
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
}
