# Implementation Plan: BIP39 Asset Loading Fix

**Feature**: 034-refactor-event-sourcing  
**Date**: 2026-05-13  
**Prerequisite for**: T032 (Verify AES-256-GCM encryption), EventStoreKeyProvider  
**RCA Reference**: [rca-bip39-asset-loading.md](rca-bip39-asset-loading.md)

---

## 1. Summary

Add explicit Android asset source set configuration to `core/security/build.gradle.kts` so that
`bip39_english.txt` is correctly bundled into the APK by the
`com.android.kotlin.multiplatform.library` Gradle plugin.

---

## 2. Scope

### In Scope
- Configure `assets.srcDirs` for the `core:security` module
- Validate the fix via build and runtime verification
- Establish a preventive integration test

### Out of Scope
- Implementing `EventStoreKeyProvider` (separate task, depends on this fix)
- Replacing dummy encryption keys (separate task, T032)
- Changes to `Bip39MasterSeedGenerator.kt` logic (code is correct; only the build config is broken)

---

## 3. Changes

### 3.1. [MODIFY] `core/security/build.gradle.kts`

Add an `android.sourceSets` configuration inside the `kotlin { android { ... } }` block to
explicitly register the KMP `androidMain/assets/` directory.

**Current** (L7–18):
```kotlin
kotlin {
    // Android target
    android {
        namespace = "com.chimali.core.security"
        compileSdk = 35
        minSdk = 28

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        withHostTest {}
    }
    // ...
}
```

**Proposed** — add `sourceSets` block inside `android {}`:
```kotlin
kotlin {
    // Android target
    android {
        namespace = "com.chimali.core.security"
        compileSdk = 35
        minSdk = 28

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        withHostTest {}

        // Explicitly register androidMain/assets/ for the KMP library plugin.
        // The com.android.kotlin.multiplatform.library plugin does not auto-discover
        // assets under src/androidMain/assets/ (unlike com.android.library which
        // auto-discovers src/main/assets/). See rca-bip39-asset-loading.md.
        sourceSets.getByName("main") {
            assets.srcDirs("src/androidMain/assets")
        }
    }
    // ...
}
```

> **NOTE**: The `sourceSets.getByName("main")` call targets the **Android** `main` source set
> (managed by AGP), not the **Kotlin** `sourceSets` block that follows. These are two distinct
> DSL scopes.

### 3.2. No Other File Changes Required

- `bip39_english.txt` is already at the correct path (`src/androidMain/assets/`)
- `Bip39MasterSeedGenerator.kt` logic is correct (`context.assets.open("bip39_english.txt")`)
- No dependency or import changes needed

---

## 4. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Wrong DSL scope causes build error | Low | Build fails (immediately visible) | Gradle sync will fail before compilation; easy to catch |
| Fix works for debug but not release | Very Low | Release APK missing asset | Verify via `Analyze APK` on both debug and release variants |
| Other KMP library modules affected | None | N/A | `core:security` is the only module with androidMain assets |

---

## 5. Execution Order

1. **Modify** `core/security/build.gradle.kts` (single change as described in §3.1)
2. **Sync** Gradle project
3. **Build** debug APK: `./gradlew :app:assembleDebug`
4. **Verify** asset presence in APK (see Test Plan)
5. **Run** on device and confirm "Master seed initialized" log appears
6. **Run** `local-ci.ps1` to ensure no regressions

---

## 6. Constitution Compliance

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Security First | ✅ | Enables BIP39 master seed generation, which is the foundation for all key derivation |
| III. Architecture & Quality | ✅ | Single-line additive fix, well-documented rationale |
| IX. Local CI/CD | ✅ | Must pass `local-ci.ps1` after change |
