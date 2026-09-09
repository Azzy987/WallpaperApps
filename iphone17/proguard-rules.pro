# App-specific R8 rules for iPhone 18 Wallpapers.
#
# Shared-code keep rules come from :core/consumer-rules.pro and are applied
# automatically — do not duplicate them here.
#
# IMPORTANT: resist adding `-keep class <framework>.** { *; }` rules. Keeping a whole
# framework (Compose, Firebase, coroutines, Coil, Room, Hilt) disables shrinking and
# optimization for the bulk of the app — that is what produced the 15% optimization /
# obfuscation / shrinking rates Play Console reported for version code 2. Every library
# used here ships its own consumer rules that already keep exactly what reflection needs.

# ===== OPTIMIZATION =====
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# ===== STRIP LOGGING IN RELEASE =====
# android.util.Log calls become no-ops. R8 then removes the string concatenation that
# only fed them.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}

# Compose composition tracing is debug-only instrumentation.
-assumenosideeffects class androidx.compose.runtime.ComposerKt {
    boolean isTraceInProgress();
    void traceEventStart(...);
    void traceEventEnd();
}

# ===== ATTRIBUTES =====
# Signature + annotations: required for Firestore's generic type resolution and for the
# reflective @PropertyName mapping in :core's models.
# SourceFile/LineNumberTable: kept so Play Console crash reports stay readable — they are
# remapped by the uploaded mapping.txt, and renaming SourceFile saves almost nothing.
-keepattributes Signature,*Annotation*,RuntimeVisibleAnnotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable
-keepattributes Exceptions,InnerClasses,EnclosingMethod

# ===== SUPPRESS WARNINGS FOR OPTIONAL DEPS =====
-dontwarn javax.annotation.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# NOTE deliberately NOT present:
#  * -keep class androidx.compose.** / com.google.firebase.** / kotlinx.coroutines.** /
#    coil.** / androidx.room.** / dagger.hilt.** — these blanket keeps were the cause of
#    the low optimization rate. Their AAR consumer rules cover what must survive.
#  * -assumenosideeffects on java.lang.Class getDeclaredMethods/Fields/Constructors —
#    that removes the reflection Firestore and Hilt depend on, and fails only at runtime.
#  * -keep !class com.google.firebase.analytics.** — the `!class` form does not do what it
#    appears to; it silently widened what was kept.
#  * -overloadaggressively / -mergeinterfacesaggressively — known to break reflective
#    frameworks and worth far less than the shrinking above.
