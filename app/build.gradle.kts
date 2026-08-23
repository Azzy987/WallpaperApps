plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.ksp)
    id("dagger.hilt.android.plugin")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.droidates.wallpapers"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        androidResources {
            localeFilters += setOf("en", "es")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Signing Configs
    // Passwords are read from environment variables — never hardcode them here.
    // Set env vars before building release:
    //   export ONEPLUS7_STORE_PASS=yourpassword
    //   export ONEPLUS7_KEY_PASS=yourpassword
    // ─────────────────────────────────────────────────────────────────────────
    signingConfigs {

        create("oneplus7") {
            storeFile = file("keystores/oneplus7.jks")
            storePassword = System.getenv("ONEPLUS7_STORE_PASS") ?: ""
            keyAlias = "upload"
            keyPassword = System.getenv("ONEPLUS7_KEY_PASS") ?: ""
        }

        create("oneplus7t") {
            storeFile = file("keystores/oneplus7t.jks")
            storePassword = System.getenv("ONEPLUS7T_STORE_PASS") ?: ""
            keyAlias = "upload"
            keyPassword = System.getenv("ONEPLUS7T_KEY_PASS") ?: ""
        }

        // ── Add a new signingConfig here when adding a new app ────────────
        // create("samsungs25") {
        //     storeFile = file("keystores/samsungs25.jks")
        //     storePassword = System.getenv("SAMSUNGS25_STORE_PASS") ?: ""
        //     keyAlias = "upload"
        //     keyPassword = System.getenv("SAMSUNGS25_KEY_PASS") ?: ""
        // }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Product Flavors — one block per published app
    // applicationId MUST match exactly what is registered on Play Console
    // ─────────────────────────────────────────────────────────────────────────
    flavorDimensions += "app"

    productFlavors {

        // ── OnePlus 7 Wallpapers ──────────────────────────────────────────
        create("oneplus7") {
            dimension = "app"
            applicationId = "com.droidates.oneplus7wallpapers"
            versionCode = 7
            versionName = "1.7"
            signingConfig = signingConfigs.getByName("oneplus7")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-6427410984085546~7840971629"
        }

        // ── OnePlus 7T Wallpapers ─────────────────────────────────────────
        create("oneplus7t") {
            dimension = "app"
            applicationId = "com.droidates.oneplus7twallpapers"
            versionCode = 1
            versionName = "1.0"
            signingConfig = signingConfigs.getByName("oneplus7t")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-6427410984085546~7395873201"
        }

        // ── Template: copy this block to add a new app ────────────────────
        // create("samsungs25") {
        //     dimension = "app"
        //     applicationId = "com.droidates.samsungs25wallpapers"  // from Play Console
        //     versionCode = 1
        //     versionName = "1.0"
        //     signingConfig = signingConfigs.getByName("samsungs25")
        //     manifestPlaceholders["admobAppId"] = "ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX"
        // }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isDebuggable = false
            isJniDebuggable = false
            isPseudoLocalesEnabled = false
            isEmbedMicroApp = false
            ndk { debugSymbolLevel = "FULL" }
            enableUnitTestCoverage = false
            enableAndroidTestCoverage = false
            packaging {
                resources {
                    excludes += listOf(
                        "/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES",
                        "/META-INF/LICENSE*", "/META-INF/license*",
                        "/META-INF/NOTICE*", "/META-INF/notice*",
                        "/META-INF/ASL2.0", "/META-INF/*.kotlin_module",
                        "/META-INF/MANIFEST.MF", "/META-INF/maven/**",
                        "/META-INF/proguard/**", "/META-INF/services/**",
                        "**/*.version", "**/kotlin/**", "**/*.pro",
                        "**/*.md", "**/*.txt", "**/*.properties",
                        "**/module-info.class", "DebugProbesKt.bin",
                        "org/intellij/**", "org/jetbrains/**", "kotlin/**"
                    )
                }
            }
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    lint {
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin/**"
            excludes += "org/intellij/**"
            excludes += "org/jetbrains/**"
        }
    }
    bundle {
        storeArchive { enable = false }
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(platform(libs.androidx.compose.bom))

    implementation("com.google.firebase:firebase-analytics-ktx") {
        exclude(group = "org.jetbrains.kotlin"); exclude(group = "org.jetbrains.kotlinx")
    }
    implementation("com.google.firebase:firebase-firestore") {
        exclude(group = "org.jetbrains.kotlin"); exclude(group = "org.jetbrains.kotlinx")
    }
    implementation("com.google.firebase:firebase-storage") {
        exclude(group = "org.jetbrains.kotlin"); exclude(group = "org.jetbrains.kotlinx")
    }
    implementation("com.google.firebase:firebase-messaging-ktx") {
        exclude(group = "org.jetbrains.kotlin"); exclude(group = "org.jetbrains.kotlinx")
    }
    implementation("com.google.firebase:firebase-auth-ktx") {
        exclude(group = "org.jetbrains.kotlin"); exclude(group = "org.jetbrains.kotlinx")
    }

    implementation(libs.play.services.auth)
    implementation(libs.billing.client)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx.v262)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material)
    implementation(libs.firebase.firestore)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.play.services.ads)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.material3)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.app.update)
    implementation(libs.app.update.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.gson)
    implementation(libs.androidx.core.splashscreen)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}
