# Root Cause Analysis: BIP39 Asset Loading Failure

**Feature**: 034-refactor-event-sourcing  
**Date**: 2026-05-13  
**Severity**: Critical — Blocks FIDO2 startup sequence  
**Status**: Root cause identified, fix pending

---

## 1. Symptom

The application crashes during the FIDO2 crypto service initialization with:

```
java.io.FileNotFoundException: bip39_english.txt
```

The crash occurs in `Bip39MasterSeedGenerator.kt` at L29–30 when the `wordList` lazy property
is first accessed via `context.assets.open("bip39_english.txt")`. This blocks `WalletMasterSeedProvider.ensureInitialized()`, preventing master seed derivation and all downstream FIDO2 credential operations (registration, authentication).

---

## 2. Investigation

### 2.1. Asset File Verification

The file **does exist** on disk at the correct path:

```
core/security/src/androidMain/assets/bip39_english.txt
```

- Size: 15,164 bytes
- Content: 2,048 BIP39 English words (one per line), valid wordlist
- Created: 2026-04-24

### 2.2. Code Path Verification

The loading code in `Bip39MasterSeedGenerator.kt` (L28–35) is straightforward:

```kotlin
private val wordList: List<String> by lazy {
    context.assets
        .open("bip39_english.txt")
        .bufferedReader()
        .readLines()
        .filter { it.isNotBlank() }
        .also { require(it.size == 2048) { ... } }
}
```

This is standard Android `AssetManager` usage. The file path `"bip39_english.txt"` is a relative
path within the assets root — no subdirectory prefix is needed.

### 2.3. Build Configuration Analysis — **Root Cause Identified**

The `core:security` module uses the `com.android.kotlin.multiplatform.library` plugin (AGP 9.2.1):

```kotlin
// core/security/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)   // ← KMP library plugin
    alias(libs.plugins.ksp)
}
```

The Android target is configured inside the `kotlin { android { ... } }` block (L7–18), which
uses the KMP-specific DSL. The `sourceSets` block (L24–52) only declares **dependencies** for
`commonMain`, `androidMain`, `commonTest`, and `androidHostTest`. It does **not** configure the
Android source set's `assets.srcDirs`.

**The key problem**: The `com.android.kotlin.multiplatform.library` plugin expects Android source
files in `src/androidMain/kotlin/` (which it correctly picks up), but it does **not**
automatically include `src/androidMain/assets/` in the Android `main` source set's asset
directories. This differs from the traditional `com.android.library` plugin, which auto-discovers
`src/main/assets/`.

**Supporting evidence**:
- No module in the project has an explicit `assets.srcDirs` configuration (confirmed via
  project-wide search for `assets.srcDirs` — zero results)
- The `core:security` module is the **only** module that ships asset files in `src/androidMain/assets/`
- No other KMP library modules need assets, so the gap was never surfaced elsewhere

### 2.4. Unit Test Mismatch

The existing unit test (`Bip39MasterSeedGeneratorTest.kt`) uses MockK to mock the `AssetManager`:

```kotlin
every { assetManager.open("bip39_english.txt") } answers {
    wordListText.byteInputStream()
}
```

This bypasses the actual Android `AssetManager` entirely, so the test suite passes even though
the asset is never bundled into the APK. The gap between mock-based and integration-level testing
allowed this defect to persist undetected.

---

## 3. Root Cause

**The `com.android.kotlin.multiplatform.library` Gradle plugin (AGP 9.2.1) does not
automatically map `src/androidMain/assets/` to the Android `main` source set's asset
directories.** The `bip39_english.txt` file exists on disk but is never included in the
compiled AAR/APK, causing a `FileNotFoundException` at runtime when `AssetManager.open()` is called.

### Contributing Factors

| Factor | Description |
|--------|-------------|
| **Plugin behavior gap** | `com.android.kotlin.multiplatform.library` handles `kotlin/` source dirs automatically but requires explicit configuration for `assets/` |
| **No integration test** | The `Bip39MasterSeedGenerator` unit tests mock the `AssetManager`, never validating actual asset bundling |
| **Single affected module** | `core:security` is the only KMP library module with assets, so the issue was not discovered via other modules |
| **No APK analysis** | The asset was never verified to exist in the final APK (e.g., via Android Studio's "Analyze APK" tool) |

---

## 4. Impact

### Direct Impact

| Component | Effect |
|-----------|--------|
| `Bip39MasterSeedGenerator` | Cannot load BIP39 wordlist → `FileNotFoundException` |
| `WalletMasterSeedProvider` | Cannot generate or derive master seed |
| `HdkManager` | Cannot derive FIDO2 credential key pairs |
| FIDO2 Registration | Completely blocked — app crashes before credential generation |
| FIDO2 Authentication | Completely blocked — app crashes before key lookup |

### Downstream Impact

The `EventStoreKeyProvider` (per the encryption key management analysis) will also depend on
`WalletMasterSeedProvider` for key material derivation. Until this asset loading issue is
resolved, the entire encryption infrastructure cannot be activated.

---

## 5. Dependency Chain

```
bip39_english.txt (asset)
  └─ Bip39MasterSeedGenerator.wordList (lazy init)
       └─ MasterSeedGenerator.generateMnemonic()
            └─ WalletMasterSeedProvider.getOrCreateMnemonic()
                 └─ WalletMasterSeedProvider.ensureInitialized()
                      ├─ getMasterSeed()
                      │    └─ EventStoreKeyProvider (planned, T032)
                      │         └─ EventStoreRepositoryImpl.append/getEvents
                      │         └─ SnapshotRepositoryImpl.save/getLatest
                      └─ getDeviceKeyPair()
                           └─ HdkManager.deriveHdk()
                                └─ FIDO2 credential operations
```

---

## 6. Classification

| Attribute | Value |
|-----------|-------|
| **Type** | Build configuration defect |
| **Introduced by** | KMP migration to `com.android.kotlin.multiplatform.library` plugin |
| **Detected at** | Runtime (device launch) |
| **Could have been caught by** | Integration test, APK analysis, or on-device smoke test |
| **Regression risk** | Low — fix is additive (one line in `build.gradle.kts`) |
