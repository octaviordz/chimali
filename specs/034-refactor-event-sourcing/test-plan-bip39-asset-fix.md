# Test Plan: BIP39 Asset Loading Fix

**Feature**: 034-refactor-event-sourcing  
**Date**: 2026-05-13  
**Implementation Plan**: [plan-bip39-asset-fix.md](plan-bip39-asset-fix.md)  
**RCA Reference**: [rca-bip39-asset-loading.md](rca-bip39-asset-loading.md)

---

## 1. Objective

Verify that `bip39_english.txt` is correctly bundled into the APK after adding the explicit
`assets.srcDirs` configuration, and that the FIDO2 crypto initialization no longer crashes.

---

## 2. Pre-Existing Test Coverage

### 2.1. Unit Tests (Already Passing — Mock-Based)

The following tests validate the algorithmic correctness of `Bip39MasterSeedGenerator` but
**do not** verify asset bundling (they mock the `AssetManager`):

| Test Class | File | Tests | Status |
|-----------|------|-------|--------|
| `Bip39MasterSeedGeneratorTest` | `core/security/src/test/.../Bip39MasterSeedGeneratorTest.kt` | 7 tests | ✅ Pass |

These tests remain unchanged and should continue to pass after the fix.

---

## 3. Verification Steps

### 3.1. Build Verification (Automated)

| # | Step | Command | Expected Result |
|---|------|---------|-----------------|
| 1 | Gradle sync | Open project in Android Studio, sync Gradle | Sync succeeds with no errors |
| 2 | Debug build | `./gradlew :app:assembleDebug` | Build succeeds |
| 3 | Release build | `./gradlew :app:assembleRelease` | Build succeeds |
| 4 | Unit tests | `./gradlew :core:security:androidHostTest` | All 7 Bip39 tests pass |
| 5 | Full CI | `tools/local-ci.ps1` | All checks pass |

### 3.2. APK Asset Verification (Manual)

| # | Step | Expected Result |
|---|------|-----------------|
| 1 | Open Android Studio → Build → Analyze APK → select debug APK | APK analysis view opens |
| 2 | Navigate to `assets/` directory in the APK | Directory is present |
| 3 | Verify `bip39_english.txt` is listed | File is present, ~15 KB |
| 4 | Repeat for release APK | Same result |

> **IMPORTANT**: This step is critical because it validates what the unit tests cannot: that the
> Gradle build system actually packages the asset file into the final binary.

### 3.3. Runtime Verification (Manual — On Device/Emulator)

| # | Step | Expected Result |
|---|------|-----------------|
| 1 | Install debug APK on device/emulator | Installation succeeds |
| 2 | Launch app (fresh install, no prior data) | App launches without crash |
| 3 | Check logcat for seed initialization | Log: `"Master seed initialized from BIP39 mnemonic (word count: 24)"` |
| 4 | Verify no `FileNotFoundException` in logcat | No exception logged |
| 5 | Navigate to FIDO2 registration flow | Registration UI loads (no crash) |
| 6 | Force-stop and relaunch app | Log: `"Loaded existing BIP39 mnemonic from secure storage"` |

### 3.4. Negative Verification (Manual)

| # | Step | Expected Result |
|---|------|-----------------|
| 1 | Temporarily remove `bip39_english.txt` from `src/androidMain/assets/` | File absent |
| 2 | Build and install APK | Build succeeds (no compile-time error) |
| 3 | Launch app | App crashes with `FileNotFoundException: bip39_english.txt` |
| 4 | **Restore** the file | Confirm the fix is the sole corrective factor |

> This negative test confirms the failure mode and validates that the fix (not some other change)
> is what resolves the issue.

---

## 4. Regression Test Recommendations

### 4.1. Recommended: Instrumented Integration Test

Add an on-device (instrumented) test to `core:security` that validates the asset is loadable
at runtime. This prevents future regressions if the build configuration is modified.

**Proposed test** (for `androidDeviceTest` source set):

```kotlin
@RunWith(AndroidJUnit4::class)
class Bip39AssetIntegrationTest {
    @Test
    fun bip39_english_txt_is_loadable_from_assets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val lines = context.assets
            .open("bip39_english.txt")
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }

        assertEquals(2048, lines.size, "BIP39 wordlist must contain exactly 2048 words")
        assertEquals("abandon", lines.first(), "First word must be 'abandon'")
        assertEquals("zoo", lines.last(), "Last word must be 'zoo'")
    }
}
```

**Note**: This requires adding `withDeviceTest {}` to the `core:security` android block and
adding `androidDeviceTest` dependencies (`androidx.test.ext:junit`, `androidx.test.runner`).
This is a follow-up enhancement, not a prerequisite for the asset fix.

### 4.2. Recommended: Gradle Build Verification Task

As a lightweight alternative to an instrumented test, a Gradle task can verify asset presence
in the AAR output:

```kotlin
// core/security/build.gradle.kts — optional build verification
tasks.register("verifyBip39Asset") {
    dependsOn("assembleRelease")
    doLast {
        val aar = layout.buildDirectory.file("outputs/aar/security-release.aar").get().asFile
        val zip = java.util.zip.ZipFile(aar)
        val entry = zip.getEntry("assets/bip39_english.txt")
        requireNotNull(entry) { "bip39_english.txt not found in AAR!" }
        zip.close()
    }
}
```

---

## 5. Test Matrix Summary

| Category | Type | Validates | Required for Fix? |
|----------|------|-----------|-------------------|
| Unit tests | Automated | BIP39 algorithm correctness | Yes (existing, must pass) |
| Gradle build | Automated | Compilation success | Yes |
| APK analysis | Manual | Asset bundled into APK | Yes |
| Runtime smoke | Manual | No crash on launch, seed initialized | Yes |
| Negative test | Manual | Fix is the corrective factor | Recommended |
| `local-ci.ps1` | Automated | No regressions across project | Yes |
| Instrumented test | Automated | Asset loadable at runtime (future CI) | Recommended follow-up |
| AAR verification task | Automated | Asset in AAR output (future CI) | Recommended follow-up |

---

## 6. Pass Criteria

The fix is considered verified when **all** of the following are true:

1. ✅ `./gradlew :core:security:androidHostTest` passes (7/7 Bip39 tests)
2. ✅ `./gradlew :app:assembleDebug` succeeds
3. ✅ `bip39_english.txt` is visible in the APK via "Analyze APK"
4. ✅ App launches without `FileNotFoundException` on a fresh install
5. ✅ Logcat shows `"Master seed initialized from BIP39 mnemonic"`
6. ✅ `tools/local-ci.ps1` passes with no regressions
