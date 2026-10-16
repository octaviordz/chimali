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

# BC 1.80 discovers these mappings via constructed class names and loads the registered
# JCA services by name. Its bundled dontwarn rules do not preserve those entry points.
# Keep only credential algorithms and their reflective constructors; dependencies remain optimizable.
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.EC$Mappings {
    public <init>();
}
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.EdEC$Mappings {
    public <init>();
}
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.MLDSA$Mappings {
    public <init>();
}

# ES256 conversion/signing plus the EC generator used by crypto warmup.
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.ec.KeyFactorySpi$EC {
    public <init>();
}
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.ec.KeyPairGeneratorSpi$EC {
    public <init>();
}
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.ec.SignatureSpi$ecDSA {
    public <init>();
}
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.ec.SignatureSpi$ecDSA256 {
    public <init>();
}

# Ed25519 services, including the generic EdDSA factory used for encoded-key aliases.
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi$Ed25519 {
    public <init>();
}
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.edec.KeyFactorySpi$EdDSA {
    public <init>();
}
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.edec.KeyPairGeneratorSpi$Ed25519 {
    public <init>();
}
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.edec.SignatureSpi$Ed25519 {
    public <init>();
}

# ML-DSA-65 and the generic pure-ML-DSA services referenced by its aliases.
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.mldsa.MLDSAKeyFactorySpi$Pure {
    public <init>();
}
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.mldsa.MLDSAKeyFactorySpi$MLDSA65 {
    public <init>();
}
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.mldsa.MLDSAKeyPairGeneratorSpi$Pure {
    public <init>();
}
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.mldsa.MLDSAKeyPairGeneratorSpi$MLDSA65 {
    public <init>();
}
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.mldsa.SignatureSpi$MLDSA {
    public <init>();
}
-keep,allowoptimization class org.bouncycastle.jcajce.provider.asymmetric.mldsa.SignatureSpi$MLDSA65 {
    public <init>();
}

# SQLite3MultipleCiphers publishes its own consumer rules.

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
