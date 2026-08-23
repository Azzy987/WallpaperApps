# Consumer ProGuard rules for :core
#
# These are applied automatically to EVERY app module that depends on :core, so a
# new app is protected without copying rules around.
#
# Why this file matters: the shared code moved to com.droidates.wallpapers.core.**,
# while each app's own package now holds only its spec + Application. Rules written
# against an app package therefore no longer cover the models, viewmodels or Room
# entities — that only breaks in minified RELEASE builds, not debug.

# Firestore maps documents onto these by reflection: field names and the no-arg
# constructor must survive.
-keep class com.droidates.wallpapers.core.model.** { *; }
-keepclassmembers class com.droidates.wallpapers.core.model.** {
    <fields>;
    <init>(...);
}
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class com.droidates.wallpapers.core.model.** {
    @com.google.firebase.firestore.PropertyName <fields>;
}

# Room entities/DAOs are generated against these names.
-keep class com.droidates.wallpapers.core.data.** { *; }

# ViewModels are constructed reflectively by Hilt/Compose.
-keep class com.droidates.wallpapers.core.viewmodel.** { *; }

# AppSpec implementations live in each app module and are referenced only via the
# interface, so keep both sides.
-keep interface com.droidates.wallpapers.core.config.AppSpec { *; }
-keep class * implements com.droidates.wallpapers.core.config.AppSpec { *; }

# Application base class + startup initializer are named in the manifest.
-keep class com.droidates.wallpapers.core.WallpaperApplication { *; }
-keep class com.droidates.wallpapers.core.utils.FirebaseInitializer { *; }
-keep class com.droidates.wallpapers.core.MainActivity { *; }

# NOTE: no rule is needed for EngagementNotificationWorker — R8's bundled AndroidX
# rules already keep androidx.work.Worker subclasses (verified in mapping.txt: the
# class is not renamed). FCMService was removed when FCM was dropped.
