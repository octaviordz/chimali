# FIDO2 Virtual Authenticator ProGuard Rules
# Hardened and optimized configuration compliant with AGP 9.2.0 Full Mode

# -------------------------------------------------------------------------------------------------
# Domain & Data Models (Serialization)
# -------------------------------------------------------------------------------------------------

# Keep @Serializable classes and their companion objects to prevent runtime crashes
# during JSON/CBOR decoding when using named companions.
-keepclassmembers class com.chimali.fido2.** {
    public static ** Companion;
}

# Keep the generated $serializer for @Serializable classes
-keep class com.chimali.fido2.**.**$serializer { *; }

# -------------------------------------------------------------------------------------------------
# Reflection & JNI Entry Points
# -------------------------------------------------------------------------------------------------

# Keep native methods (Standard optimization rule)
-keepclasseswithmembernames class * {
    native <methods>;
}

# -------------------------------------------------------------------------------------------------
# Security & Crypto (Hardening)
# -------------------------------------------------------------------------------------------------

# Note: Bouncy Castle and SQLite3MultipleCiphers rules are now handled by library consumer rules.
# We only add project-specific security keeps here if necessary.

# -------------------------------------------------------------------------------------------------
# Logging & Debugging
# -------------------------------------------------------------------------------------------------

# Remove logging in release builds to prevent leaking sensitive information
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# -------------------------------------------------------------------------------------------------
# Obfuscation Hardening
# -------------------------------------------------------------------------------------------------

# Ensure we don't accidentally keep broad package wildcards.
# Redundant library rules (AndroidX, Hilt, Kotlinx) have been removed.
