# R8 Configuration Analysis - Chimali

This report evaluates the current R8 configuration and ProGuard keep rules for the Chimali project, identifying redundancies and optimization opportunities.

## Configuration Analysis

- **AGP Version**: 9.2.0.
- **Minification Status**: `isMinifyEnabled = false` is set for the `release` build type in [app/build.gradle.kts](file:///D:/octav/source/repos/Chimali/app/build.gradle.kts). R8 shrinking and obfuscation are currently disabled for production builds.
- **R8 Full Mode**: `android.r8.strictFullModeForKeepRules=false` is set in [gradle.properties](file:///D:/octav/source/repos/Chimali/gradle.properties).

### Recommendations
1.  **Enable Minification**: Set `isMinifyEnabled = true` in the `release` build type to enable app size optimization and code obfuscation.
2.  **Enable R8 Full Mode**: Set `android.r8.strictFullModeForKeepRules=true` to enable more aggressive optimizations.

---

## Keep Rule Analysis

The following keep rules in [feature/fido2/proguard-rules.pro](file:///D:/octav/source/repos/Chimali/feature/fido2/proguard-rules.pro) have been identified for removal or refinement.

### 1. Redundant Library Rules
These rules target libraries that already bundle their own consumer ProGuard rules. Manual rules often prevent R8 from stripping unused code within these libraries.

| Rule | Action | Reasoning |
|------|--------|-----------|
| `-keep class org.bouncycastle.** { *; }` | **Remove** | Bouncy Castle provides its own consumer rules. Global preservation prevents optimization of unused crypto providers. |
| `-keep class net.sqlcipher.** { *; }` | **Remove** | SQLCipher bundles required rules in its AAR. |
| `-keep class kotlinx.serialization.** { *; }` | **Remove** | The serialization plugin and library handle their own keep rules. |
| `-keep class dagger.hilt.** { *; }` | **Remove** | Redundant. Furthermore, the project has migrated to Koin; Hilt is no longer used. |
| `-keep class javax.inject.** { *; }` | **Remove** | Handled automatically by DI libraries. |
| `-keep class androidx.biometric.** { *; }` | **Remove** | AndroidX libraries bundle their own rules. |
| `-keep class androidx.compose.** { *; }` | **Remove** | Compose and its compiler automatically handle required keep rules. |
| `-keep class kotlinx.coroutines.** { *; }` | **Remove** | Kotlin Coroutines (v1.7.0+) include their own R8 optimizations. |
| `-keep class kotlin.Metadata { *; }` | **Remove** | Kotlin compiler and R8 handle metadata preservation where necessary. |

### 2. Redundant Platform Rules
Rules targeting standard Android and Java packages are unnecessary as these are never obfuscated or shrunk by R8 when they are part of the platform SDK.

| Rule | Action | Reasoning |
|------|--------|-----------|
| `-keep class android.security.keystore.** { *; }` | **Remove** | System SDK classes are not shrunk by R8. |
| `-keep class javax.crypto.** { *; }` | **Remove** | Standard Java crypto packages are part of the platform/runtime. |
| `-keep class android.bluetooth.** { *; }` | **Remove** | System SDK classes are not shrunk by R8. |
| `-keep class android.hardware.usb.** { *; }` | **Remove** | System SDK classes are not shrunk by R8. |

### 3. Subsuming and Overly Broad Rules
These rules use broad wildcards that prevent optimization for entire packages.

| Rule | Action | Reasoning |
|------|--------|-----------|
| `-keep class com.chimali.fido2.** { *; }` | **Remove** | Extremely broad. It prevents all optimization for the entire FIDO2 module. |
| `-keep class com.chimali.fido2.data.database.** { *; }` | **Remove** | SQLDelight handles its own generated code rules. |
| `-keep class com.chimali.fido2.domain.model.** { *; }` | **Refine** | Instead of keeping the whole package, use `@Serializable` (which you are already using) and rely on the serialization plugin's rules. |
| `-keep class com.chimali.fido2.data.model.** { *; }` | **Refine** | Same as above; target specific classes only if reflection is used outside of serialization. |

---

## Verification Plan

After applying these changes and enabling `isMinifyEnabled = true`:

1. **Automated Testing**: Run the full instrumentation test suite to ensure no `ClassNotFoundException` or `NoSuchMethodError` occurs at runtime due to over-shrinking.
2. **Manual Verification**: Specifically test the FIDO2 registration and authentication flows, as these involve complex serialization and cryptographic operations.
3. **UI Automator**: Use [UI Automator](https://developer.android.com/training/testing/other-components/ui-automator) to verify that UI components (especially Compose-based ones) remain functional after obfuscation.
