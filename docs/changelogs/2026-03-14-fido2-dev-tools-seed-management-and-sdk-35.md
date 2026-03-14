# Changelog: FIDO2 Dev Tools Seed Management and SDK 35 (2026-03-14)

## Summary
This update introduces powerful development utilities for BIP39 master seed management, enabling secure export and recovery via QR codes and manual entry. It also includes a significant platform upgrade to Android 15 (API 35) to support the latest Jetpack Compose features.

## Changes

### feature:fido2 Layer

#### [NEW] [DevToolsViewModel.kt](file:///d:/octav/source/repos/Chimali/feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/viewmodel/DevToolsViewModel.kt)
- **MVI Architecture**: Implemented a dedicated ViewModel to manage mnemonic loading, biometric gating, and recovery flows.
- **Security Gates**: Implemented `ClearMnemonic` intent to zero out sensitive state from memory as soon as the user navigates away from the Dev Tools screen.

#### [NEW] [MnemonicQrScanner.kt](file:///d:/octav/source/repos/Chimali/feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/ui/MnemonicQrScanner.kt)
- **CameraX + ML Kit**: Created a high-performance QR scanner component optimized for reading 24-word BIP39 mnemonics.
- **Validation**: Integrated real-time word count validation (24 words) before accepting scanned results.

#### [MODIFY] [DevelopmentToolsScreen.kt](file:///d:/octav/source/repos/Chimali/feature/fido2/src/main/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt)
- **Biometric Protection**: Added biometric authentication gate for viewing the master seed.
- **Visual Word Grid**: Implemented a numbered grid display for easy manual verification of mnemonic words.
- **QR Export**: Integrated `qrose` for generating on-screen QR codes for seed transfer.
- **Manual Recovery Form**: Added a 24-field input grid for manual seed ingestion.
- **Permission Handling**: Fixed a silent failure by properly checking and requesting `CAMERA` permissions.

### core:security & data Layers

#### [MODIFY] [MasterSeedProvider.kt](file:///d:/octav/source/repos/Chimali/feature/fido2/src/main/kotlin/com/chimali/fido2/data/crypto/MasterSeedProvider.kt) & [WalletMasterSeedProvider.kt](file:///d:/octav/source/repos/Chimali/feature/fido2/src/main/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProvider.kt)
- **Mnemonic Retrieval**: Extended the interface to support `getMnemonic()` for debug-only inspection.
- **Encrypted Storage**: Implemented retrieval from `EncryptedSharedPreferences`.

### Build & Platform

#### [MODIFY] [libs.versions.toml](file:///d:/octav/source/repos/Chimali/gradle/libs.versions.toml) & [build.gradle.kts](file:///d:/octav/source/repos/Chimali/feature/fido2/build.gradle.kts)
- **SDK 35 Migration**: Bumped `compileSdk` and `targetSdk` to 35 across the entire project.
- **Debug-Only Dependencies**: Added CameraX, ML Kit, and QRose libraries specifically for `debugImplementation` to maintain a slim and secure production APK.

## Impact
- **Developer Productivity**: Dramatically simplifies the process of testing seed persistence and device recovery scenarios.
- **Future Proofing**: Project is now fully aligned with Android 15 requirements for 16KB page alignment and modern platform APIs.

## Verification
- **Unit Tests**: Added `DevToolsViewModelTest` (6 cases) and updated `WalletMasterSeedProviderTest`.
- **Manual Flow**: Confirmed biometric gating, QR generation, QR scanning, and manual entry all function as expected in debug builds.
- **Build Integrity**: Verified that `release` builds do not contain camera/QR libraries or the dev-only UI.
