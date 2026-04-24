# ProGuard rules for Instrumentation Tests (shrunkDebugAndroidTest)
# We keep all of AndroidX and Kotlin in the test APK to ensure stability 
# during instrumentation runs while the app APK remains optimized.

# Preserve all classes in the test packages to ensure the instrumentation runner can find them.
-keep class com.chimali.**.**Test { *; }
-keep class com.chimali.**.**Test$* { *; }

# Keep the AndroidJUnitRunner
-keep class androidx.test.runner.AndroidJUnitRunner { *; }

-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-keep enum androidx.** { *; }
-keepclassmembers class androidx.** { *; }

-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep enum kotlin.** { *; }
-keepclassmembers class kotlin.** { *; }

-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

-keepattributes Signature,AnnotationDefault,EnclosingMethod,InnerClasses,SourceFile,LineNumberTable,*Annotation*
