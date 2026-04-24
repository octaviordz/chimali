# Research: R8 Optimization & Hardening

## Overview
This research evaluates the technical requirements and best practices for enabling R8 Full Mode, Resource Shrinking, and optimized build types for the Chimali project.

## Findings

### 1. R8 Full Mode & optimizedResourceShrinking
- **R8 Full Mode**: Aggressively optimizes code by assuming no reflection is used unless explicitly kept. This provides significant size reduction but requires precise rules for serialization and JNI.
- **Optimized Resource Shrinking**: Introduced in AGP 8.6+, the flag `android.r8.optimizedResourceShrinking=true` enables a more precise analysis that combines code and resource usage, leading to even smaller APKs.
- **Decision**: Enable both Full Mode and Optimized Resource Shrinking.
- **Rationale**: Chimali is a performance-critical application (Startup < 2s). Maximizing shrinking is essential.

### 2. kotlinx.serialization in Full Mode
- **Rules**: Standard `@Serializable` classes are mostly handled by the library's consumer rules. However, classes with **named companion objects** require explicit rules to prevent stripping of the serializer.
- **Decision**: Add defensive rules for named companion objects and verify polymorphic serialization.
- **Rationale**: Prevents common `SerializationException` at runtime in optimized builds.

### 3. `shrunkDebug` Build Type
- **Implementation**: Define a build type that `initWith(getByName("debug"))` but overrides `isMinifyEnabled = true` and `proguardFiles` to match `release`.
- **Decision**: Use `initWith(getByName("release"))` but set `signingConfig = debug` and `isDebuggable = true`.
- **Rationale**: This more accurately mirrors the production environment (including obfuscation and optimization) while allowing local debugging and testing without release keys.

### 4. Obsolete/Redundant Rules
- **Hilt**: Project has migrated to Koin. All `dagger.hilt.**` and `javax.inject.**` (unless used by Koin) can be removed.
- **Library Rules**: Bouncy Castle, SQLCipher, and AndroidX libraries bundle their own rules. Manual preservation of these packages (`-keep class org.bouncycastle.** { *; }`) is redundant and detrimental to size.

## Action Plan (Summary)
- **gradle.properties**: Enable `android.r8.strictFullModeForKeepRules=true` and `android.r8.optimizedResourceShrinking=true`.
- **app/build.gradle.kts**: Configure `release` and `shrunkDebug` build types.
- **proguard-rules.pro**: Purge redundant rules and add narrow rules for serialization and JNI.
