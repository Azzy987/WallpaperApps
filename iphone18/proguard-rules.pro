# Shared-code keep rules come from :core/consumer-rules.pro and are applied
# automatically. Only genuinely app-specific rules belong in this file.

# Enhanced ProGuard Rules for iPhone 17 Wallpapers - App Size Optimization

# ===== ULTRA-AGGRESSIVE OPTIMIZATION SETTINGS =====
-optimizationpasses 7
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses ''
-overloadaggressively
-dontpreverify
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses

# ===== REMOVE ALL DEBUGGING INFO =====
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}

# Remove ALL logging from shared + app code. The shared classes now live under
# com.droidates.wallpapers.core.**, so this must cover that package too.
-assumenosideeffects class com.droidates.wallpapers.core.** {
    public static *** Log*(...);
    public *** log*(...);
    void Log*(...);
    *** log(...);
}
-assumenosideeffects class com.droidates.iphone18wallpapers.** {
    public static *** Log*(...);
    public *** log*(...);
    void Log*(...);
    *** log(...);
}

# Remove BuildConfig.DEBUG checks in release
-assumenosideeffects class com.droidates.iphone18wallpapers.BuildConfig {
    public static final boolean DEBUG return false;
}

# Remove Firebase Analytics completely
-dontwarn com.google.firebase.analytics.**
-keep !class com.google.firebase.analytics.** { *; }

# Remove System.out and System.err
-assumenosideeffects class java.lang.System {
    public static *** out;
    public static *** err;
}

# Remove printStackTrace calls
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

# ===== REMOVE UNUSED CODE =====
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn kotlin.jvm.internal.**
-dontwarn kotlin.coroutines.jvm.internal.**

# ===== KEEP ESSENTIAL CLASSES =====

# Firebase/Firestore
-keep class com.google.firebase.** { *; }
-keep class com.google.firebase.ktx.** { *; }
-dontwarn com.google.firebase.ktx.Firebase

# Firestore toObject() / field mapping (release builds)
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keepclassmembers class * {
    @dagger.hilt.android.AndroidEntryPoint *;
}

# Compose
-keep class androidx.compose.** { *; }
-keep class kotlin.coroutines.** { *; }

# Room Database
-keep class androidx.room.** { *; }

# ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# AdMob
-keep class com.google.android.gms.ads.** { *; }

# Coil
-keep class coil.** { *; }

# Play Billing
-keep class com.android.billingclient.** { *; }

# ===== REMOVE UNNECESSARY ATTRIBUTES =====
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod

# ===== ADDITIONAL OPTIMIZATIONS FOR IPHONE 17 WALLPAPERS =====

# Remove unused resources and strings
-assumenosideeffects class java.lang.String {
    java.lang.String intern();
}

# Optimize Kotlin coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-assumenosideeffects class kotlinx.coroutines.** {
    public static *** debug(...);
}

# Optimize Jetpack Compose
-dontwarn androidx.compose.**
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    boolean isTraceInProgress();
    void traceEventStart(...);
    void traceEventEnd();
}

# Remove reflection calls for better performance
-assumenosideeffects class java.lang.Class {
    java.lang.reflect.Method[] getDeclaredMethods();
    java.lang.reflect.Field[] getDeclaredFields();
    java.lang.reflect.Constructor[] getDeclaredConstructors();
}

# Optimize image loading (Coil specific)
-assumenosideeffects class coil.util.Logger {
    void log(...);
    boolean isLoggable(...);
}

# Remove Firebase debug logging
-assumenosideeffects class com.google.firebase.** {
    *** debug(...);
    *** log(...);
}

# ===== OPTIMIZATION FLAGS =====
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-keepattributes Signature

# ===== APP BUNDLE OPTIMIZATION =====
# Enable resource shrinking for maximum size reduction
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Remove unused native libraries
-assumenosideeffects class java.lang.System {
    java.lang.String getProperty(java.lang.String);
    java.lang.String getProperty(java.lang.String, java.lang.String);
}