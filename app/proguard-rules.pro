# ProGuard rules for Chimali

# SLF4J
-dontwarn org.slf4j.impl.StaticMDCBinder

# WorkManager
-keep class androidx.work.impl.WorkDatabase { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

# Room
-keep class androidx.room.MultiInstanceInvalidationService { *; }

# General Jetpack Startup
-keep class androidx.startup.InitializationProvider { *; }

# Koin (Keep internal components used by reflection)
-keep class org.koin.core.module.** { *; }
-keep class org.koin.android.** { *; }

# Tracing (required by AndroidJUnitRunner)
-keep class androidx.tracing.Trace { *; }

# Kotlin Standard Library
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep enum kotlin.** { *; }
-keepclassmembers class kotlin.** { *; }

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# AndroidX (Targeted keeps for app startup stability)
-keep class androidx.startup.InitializationProvider { *; }
-keep class androidx.work.impl.WorkDatabase { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.room.MultiInstanceInvalidationService { *; }
-keep class androidx.tracing.Trace { *; }

# Collections (Used by Compose and reflection)
-keep class androidx.collection.** { *; }
-keep interface androidx.collection.** { *; }
-keepclassmembers class androidx.collection.** { *; }

# Compose (Required for testability and runtime stability)
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
-keep enum androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Lifecycle and SavedState (Required for ViewTreeOwner resolution in tests)
-keep class androidx.lifecycle.** { *; }
-keep class androidx.savedstate.** { *; }

# General Hardening Attributes
-keepattributes Signature,AnnotationDefault,EnclosingMethod,InnerClasses,SourceFile,LineNumberTable,*Annotation*
