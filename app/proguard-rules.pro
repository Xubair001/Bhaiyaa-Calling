# BHAIYAAA R8 / ProGuard rules.
#
# Only rules that are actually needed are listed. Everything else relies on
# AGP's bundled androidx and Compose rules, and on Room/KSP generating real
# code rather than using reflection.

# ---------------------------------------------------------------- WorkManager
# Workers are instantiated reflectively from a class name persisted in
# WorkManager's own database, so R8 cannot see the construction site. Without
# this, a queued model download or call sync fails after an app update with
# ClassNotFoundException.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ------------------------------------------------------------------- JNA/Vosk
# Vosk talks to its native library through JNA, which maps Java types to C
# structs by reflection over field and class names. Renaming any of it breaks
# offline speech at runtime with no compile-time warning.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class org.vosk.** { *; }
# JNA references desktop AWT classes that do not exist on Android; they are
# never reached, so silence the warnings rather than keeping them.
-dontwarn java.awt.**
-dontwarn javax.swing.**

# ------------------------------------------------------------------ Room/FTS
# Room generates its own implementations, so entities need no reflection rules.
# The one exception is the FTS content-table plumbing, which resolves table
# names as strings.
-keep class com.codeaza.bhaiyaaa.data.db.entity.** { *; }

# ------------------------------------------------------------------ Coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --------------------------------------------------------------------- Privacy
# Strip Log calls from release builds. BHAIYAAA never logs phone numbers, notes
# or memories, but removing the call sites entirely means a future careless
# log statement cannot leak private data from a shipped build either.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Keep line numbers so release crash reports stay readable, but hide the
# original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
