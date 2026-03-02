# FIDO2 Virtual Authenticator ProGuard Rules

# Keep FIDO2 related classes
-keep class com.chimali.fido2.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class net.sqlcipher.** { *; }

# Keep CBOR serialization classes
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.serialization.cbor.** { *; }
-dontwarn kotlinx.serialization.**

# Keep SQLDelight generated classes
-keep class com.chimali.fido2.data.database.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp

# Keep Android KeyStore related classes
-keep class android.security.keystore.** { *; }
-keep class javax.crypto.** { *; }

# Keep Bluetooth HID classes
-keep class android.bluetooth.** { *; }
-keep class android.hardware.usb.** { *; }

# Keep biometric classes
-keep class androidx.biometric.** { *; }

# Keep Compose related classes
-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }

# Keep coroutine related classes
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable implementations
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# SQLCipher specific rules
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# Bouncy Castle PQC specific rules
-keep class org.bouncycastle.pqc.** { *; }
-keep class org.bouncycastle.crypto.** { *; }
-dontwarn org.bouncycastle.**

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Keep model classes used in serialization
-keep class com.chimali.fido2.domain.model.** { *; }
-keep class com.chimali.fido2.data.model.** { *; }
