# Quickstart: KMP Resource Migration (BIP39 Wordlist)

**Feature**: 044-kmp-resource-migration  
**Date**: 2026-05-18

## Overview

This feature migrates `bip39_english.txt` from `androidMain/resources/` to `commonMain/resources/` and introduces a platform-abstracted `loadResourceLines()` function using the `expect`/`actual` pattern. The goal is to eliminate platform-specific resource loading in the security module.

## Prerequisites

- Branch `044-kmp-resource-migration` checked out
- Android SDK 35 / min SDK 28
- JDK 17+

## Key Files

| File | Action | Purpose |
|------|--------|---------|
| `core/security/src/commonMain/resources/bip39_english.txt` | **CREATE** (move) | Shared resource location |
| `core/security/src/androidMain/resources/bip39_english.txt` | **DELETE** | Remove platform-specific copy |
| `core/security/src/commonMain/kotlin/.../platform/ResourceLoader.kt` | **CREATE** | `expect fun loadResourceLines(name: String): List<String>` |
| `core/security/src/androidMain/kotlin/.../platform/ResourceLoader.android.kt` | **CREATE** | `actual fun` using `ClassLoader.getResourceAsStream()` |
| `core/security/src/iosMain/kotlin/.../platform/ResourceLoader.ios.kt` | **CREATE** | `actual fun` stub with `TODO()` |
| `core/security/src/androidMain/kotlin/.../impl/Bip39MasterSeedGenerator.kt` | **MODIFY** | Use `loadResourceLines()` instead of direct classloader |
| `core/security/src/test/.../impl/Bip39MasterSeedGeneratorTest.kt` | **MOVE+MODIFY** | Move to `androidHostTest/`, remove Context mocking |

## Build & Verify

```powershell
# Run the full local CI pipeline (required before any commit)
.\tools\local-ci.ps1

# Run only the security module tests (faster iteration)
.\gradlew :core:security:androidHostTest
```

## Success Verification

1. ✅ `.\gradlew :core:security:androidHostTest` — all tests pass without Context/AssetManager mocking
2. ✅ `.\gradlew assembleDebug` — APK builds successfully with wordlist included
3. ✅ `bip39_english.txt` exists in `commonMain/resources/` and NOT in `androidMain/resources/`
4. ✅ `.\tools\local-ci.ps1` — full pipeline passes
