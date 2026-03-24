# CHANGELOG

All notable changes to the Chimali project will be documented in this file.
Detailed change summaries for major features are stored in the `docs/changelogs/` directory.

## [Unreleased] - 2026-03-24

### Changed
- **Centralized Credential ID Generation**: Unified FIDO2 credential ID logic into `PasskeyCredential.Companion.generateRandomId()`. This ensures all credentials follow the same standard across `RegisterCredentialUseCase` and domain models.
- **Privacy & Security Enhancement**: Transitioned from predictable, timestamp-based credential IDs (`cred_...`) to 32-byte (256-bit) high-entropy `SecureRandom` IDs. This prevents authenticator fingerprinting and registration timing leakage while maintaining compatibility with HDK derivation paths.

### Fixed
- **Test Stability**: Resolved pre-existing compilation errors in `RegisterCredentialUseCaseTest.kt` and `Ctap2WindowsCompatibilityTest.kt` caused by obsolete constructor signatures.
- **Full details**: [2026-03-24-fido2-credential-id-centralization.md](docs/changelogs/2026-03-24-fido2-credential-id-centralization.md)

## [Unreleased] - 2026-03-20

### Changed
- **Logging Infrastructure Refactor**: Standardized on Timber's automatic class-based tagging across the `:feature:fido2` module. Removed redundant `TAG` constants and explicit `.tag(TAG)` calls to reduce boilerplate and ensure log consistency.
- **Code Maintenance**: Performed a general cleanup of `BluetoothHidDeviceWrapper.kt`, including spelling fixes and formatting improvements to align with the project's styling guidelines.
- **Spec Compliance & Cleanup**: Commented out unused constants in `Ctap2GetAssertionHandler.kt` to reduce compiler warnings while maintaining alignment with the FIDO spec for future implementation.
- Full details: [2026-03-20-fido2-logging-refactor-and-maintenance.md](docs/changelogs/2026-03-20-fido2-logging-refactor-and-maintenance.md)

## [Unreleased] - 2026-03-19

### Added
- **FIDO2 Background Notifications (Under Development)**: Implemented an initial High-Priority notification system in `Fido2TransportService`. **Note**: Initial manual testing indicates notifications are currently not firing; requires further investigation into Android's foreground/background lifecycle and notification permissions.

### Fixed
- **GetInfo Spec Compliance**: Removed unsupported `clientPin` and `pinUvAuthProtocols` from the `GetInfo` response to prevent Windows from attempting ClientPIN (0x06) negotiation, resolving `0x8007000d` (Unknown Device State).
- **CTAPHID_INIT Capabilities**: Fixed a bug where `CAPABILITY_NMSG` was incorrectly advertised, potentially confusing Windows drivers about CTAPHID command support.
- **CTAP Error Codes**: Standardized on `0x01` (`CTAP1_ERR_INVALID_COMMAND`) for unsupported commands to align with the FIDO specification.
- **Latency Profiling (NFR-PERF-030)**: Resolved a regression where user interaction time was incorrectly included in system latency measurements during `MakeCredential`.
- Full details: [2026-03-19-fido2-background-notifications-and-spec-compliance.md](docs/changelogs/2026-03-19-fido2-background-notifications-and-spec-compliance.md)

## [Unreleased] - 2026-03-18

### Added
- **Full Integration Verification (T159)**: Achieved a 100% pass rate across the entire `:feature:fido2` unit test suite (59 tests), validating the end-to-end Registration and Authentication flows.
- **Localized Error Handling (T149–T153)**: Implemented a privacy-safe local crash reporting system with Timber and a regex-based `PrivacyLogScrubber` that redacts mnemonics and keys. Technical FIDO2 errors are now mapped to user-friendly UI messages.
- **Atkinson Hyperlegible Font (T142)**: Integrated the Braille Institute's accessibility-focused font across high-density FIDO2 data views to improve character distinguishability for low-vision users.
- **Accessibility Verification (T143)**: Introduced a comprehensive suite of UI tests to verify heading roles, merged semantics, and live region announcements across the Authenticator.

### Changed
- **TalkBack Optimization (T139)**: Refined the FIDO2 UI hierarchy with explicit heading roles and merged card semantics, significantly reducing screen reader navigation fatigue.
- **Background Crypto (T136)**: Offloaded `sign()` and `generateCredentialKeyPair()` in `Fido2CryptoService` to the background `@DefaultDispatcher`, ensuring a smooth 60fps UI during cryptographic operations.
- **PQC Algorithm Probing**: Enhanced `PostQuantumCrypto` to dynamically resolve between NIST standard (`ML-KEM-512`) and Bouncy Castle legacy (`Kyber`) names for improved cross-environment stability.
- Full details: [2026-03-18-accessibility-and-localized-logging.md](docs/changelogs/2026-03-18-accessibility-and-localized-logging.md), [2026-03-18-fido2-verification-and-pqc-robustness.md](docs/changelogs/2026-03-18-fido2-verification-and-pqc-robustness.md), & [2026-03-18-code-cleanup.md](docs/changelogs/2026-03-18-code-cleanup.md)

### Fixed
- **Windows FIDO2 Timeout Bug**: Reduced CTAPHID `KEEPALIVE` initial delay from `200ms` strictly to `75ms` to prevent Windows from silently aborting the FIDO transaction (`ERROR_INVALID_DATA (0x8007000d)`) during heavy cryptographic warmup.
- **CTAP2 Attestation Compliance**: Synchronized `clientDataHash` handling with `rauth-android` reference implementation, directly signing the raw bytes alongside `authenticatorData` (with `AT` 0x40 flag injected) rather than hashing an artificial JSON envelope. Also fixed `attStmt` encoding to correctly emit `alg` and `sig` for packed attestations.
- **GetAssertion Pre-flight Logging**: Improved error filtering in `GetAssertionUseCase.kt` to identify `CREDENTIAL_NOT_FOUND` via `errorCode` property rather than strict exception casting, neutralizing noisy error logs caused by anticipated Windows WebAuthn OS probes.
- **CBOR Counter Integrity**: Resolved a spec-compliance regression in `CryptoUtilsTest` by enforcing 64-bit `Long` encoding for authenticator counters.
- **Test Logic Robustness**: Fixed logically invalid assertions in `PostQuantumCryptoTest` that were incorrectly failing in certain hardware/JRE environments.
- Full details: [2026-03-18-fido2-makecredential-timing-and-cbor-fixes.md](docs/changelogs/2026-03-18-fido2-makecredential-timing-and-cbor-fixes.md)

### Removed
- **Unused Crypto Helpers**: Deleted the `getRecommendedAlgorithm` probing logic and associated tests to streamline the cryptographic provider interface.

## [Unreleased] - 2026-03-17

### Added
- **Security Test Series (T148)**: Implemented comprehensive security verification for memory zeroing, deterministic key derivation (KATs), biometric lockout response, and SQLCipher storage integrity.
- **Clipboard Security (T133)**: Introduced a centralized `ClipboardManagerService` that automatically clears sensitive data after 60 seconds to prevent leakages via the system clipboard.
- **Mnemonic Import (T146g)**: Completed the seed recovery flow with secure word-count validation and high-assurance buffer zeroing.

### Changed
- **Responsive Dev Tools**: Refactored mnemonic management buttons to use `FlowRow`, ensuring UI adaptivity and icon/text alignment on small screens.

### Fixed
- **Vault Build Error**: Resolved missing test dependencies in the `feature:vault` module preventing instrumentation test compilation.
- **Test Stability**: Fixed Android `Log` stub issues in `Fido2CryptoServiceTest` that were causing unit test regressions.
- Full details: [2026-03-17-security-hardening-tests-and-mnemonic-recovery.md](docs/changelogs/2026-03-17-security-hardening-tests-and-mnemonic-recovery.md)

## [Unreleased] - 2026-03-14
+
+### Added
+- **Dev Tools Seed Management**: Implemented a comprehensive suite of development tools for BIP39 master seed management (T146 series). Includes biometric-gated mnemonic viewing, QR code export, QR code scan import (CameraX + ML Kit), and manual 24-word recovery.
+- **New Dependencies**: Integrated `qrose` for QR generation and ML Kit Barcode Scanning with CameraX for secure QR-based seed transfer in debug builds.
+
+### Changed
+- **SDK Target Migration**: Upgraded `compileSdk` and `targetSdk` to **API 35 (Android 15)** across all 13 modules to comply with Jetpack Compose 1.10.0 requirements and optimize for modern platform features.
+
+### Fixed
+- **QR Scanner Permissions**: Resolved a critical issue where the QR scanner failed to launch due to a missing `CAMERA` permission declaration in the Android Manifest.
+- **Scanner UX**: Hardened the "Scan QR Code" button logic to handle pre-granted permissions gracefully and prevent silent launcher failures.
+- Full details: [2026-03-14-fido2-dev-tools-seed-management-and-sdk-35.md](docs/changelogs/2026-03-14-fido2-dev-tools-seed-management-and-sdk-35.md)
+
+## [Unreleased] - 2026-03-13

### Changed
- **Constitution (v0.7.0)**: Formally adopted a **Multi-Mode Symmetric Encryption Strategy**. Established AES-256-GCM as the mandate for payload/streaming encryption to preserve Hardware Keystore offloading, and established AES-256-SIV as the mandate for searchable metadata and key wrapping to provide nonce-misuse resistance. Updated BRD `NFR-SEC-010` accordingly.
- Full details: [2026-03-13-constitution-v0.7.0.md](docs/changelogs/2026-03-13-constitution-v0.7.0.md)

### Fixed
- **FIDO2 Registration UX**: Resolved issues where "Registration failed" and "Passkey created" screens were only visible for a fraction of a second due to immediate navigation/retry loops.
- **Crypto Provider Exception**: Fixed a critical `NoSuchAlgorithmException` where Android's security framework shadowed the BouncyCastle provider name.
- **Retry Logic**: Fixed a bug where the "Try again" button failed to re-initiate registration after a failure due to state being cleared prematurely.
- Full details: [2026-03-13-fido2-registration-ux-and-crypto-fixes.md](docs/changelogs/2026-03-13-fido2-registration-ux-and-crypto-fixes.md)

## [Unreleased] - 2026-03-12

### Added
- **FIDO2 HDK Integration**: Completed a major architectural transition for FIDO2 credentials to support **Master Seed backup (FR-AUTH-030)**. Keys are now derived deterministically using HDK-ECDH-P256 instead of being tied to non-exportable hardware KeyStore blocks.
- **Master Seed Plumbing**: Introduced `MasterSeedProvider` interface and `EphemeralMasterSeedProvider` to facilitate centralized seed management across modules.

### Changed
- **Cryptographic Service Refactor**: `Fido2CryptoService` now performs in-memory software key derivation and uses BouncyCastle for signing, strictly ensuring private key material never touches persistent storage.
- **UseCase Migration**: `GetAssertionUseCase` migrated away from direct Android KeyStore dependencies to use the abstracted `Fido2CryptoService` API.

### Fixed
- **Latent DAO Bug**: Resolved a pre-existing parameter naming mismatch in `PasskeyCredentialDao` uncovered during full module recompilation.
- **Test Integrity**: Updated full FIDO2 unit test suite to align with the new derivation architecture.
- Full details: [2026-03-12-fido2-hdk-integration.md](docs/changelogs/2026-03-12-fido2-hdk-integration.md)

## [Unreleased] - 2026-03-11

### Added
- **Device Class Identification**: Implemented Bluetooth "Major Device Class" extraction. The Authenticator now identifies if the connecting host is a Computer, Smartphone, or Wearable and displays the corresponding Material icon.
- **Enhanced Paired Devices UI**: Added a "Bottom Navigation Bar", refined "Swipe-to-Delete" with 10s undo, and immediate UI hiding for swiped items.
- **Status Hierarchy Update**: Redesigned the connection status indicator to emphasize the host name (larger bold font) over the status label.

### Fixed
- **SQL Migration Alignment**: Resolved a `NullPointerException` by aligning SQLDelight schema column order with physical SQLite `ALTER TABLE` behavior.
- **Icon Persistence Logic**: Hardened the repository to prevent "Uncategorized" class reports from overwriting known computer/phone icons.
- Full details: [2026-03-11-fido2-device-class-and-ui-polish.md](docs/changelogs/2026-03-11-fido2-device-class-and-ui-polish.md)

## [Unreleased] - 2026-03-09

### Added
- **Automated Bluetooth Pairing**: Replaced manual "Pair new device" steps with a single-click discoverability flow in the `Fido2HomeScreen`. This uses `ACTION_REQUEST_DISCOVERABLE` to both enable Bluetooth and make the device visible to Windows for pairing in one system prompt.

### Fixed
- **Asus Zenfone 10 Bluetooth Compatibility**: Hardened `BluetoothHidDeviceWrapper` to prevent silent initialization hangs. Added a 5-second timeout and 3-retry loop to handle cases where the Android Bluetooth stack silently drops the `onServiceConnected` callback.
- **Strict Vendor Stack Rejection**: Optimized `BluetoothHidDeviceAppSdpSettings` to `SUBCLASS1_COMBO` and transitioned to system-default Quality of Service (QoS) parameters (passing `null` to `registerApp`), resolving silent HID advertisement rejections on certain Qualcomm/Asus/Samsung Bluetooth stacks.
- **Enhanced Diagnostics**: Integrated detailed Logcat tracing for Bluetooth adapter states and profile registration progress to simplify future troubleshooting of OEM-specific Bluetooth stacks.
- Full details: [2026-03-09-fido2-zenfone-bluetooth-compatibility.md](docs/changelogs/2026-03-09-fido2-zenfone-bluetooth-compatibility.md)

## [Unreleased] - 2026-03-07

### Fixed
- **FIDO2 Bluetooth Reliability**: Hardened `BluetoothHidDeviceWrapper` with an exponential backoff retry loop and `5000ms` timeouts to prevent coroutine hangs when buggy Android Bluetooth stacks silently drop `registerApp` callbacks. 
- **Phantom Connection Sockets**: Added explicit `disconnect()` teardown logic in `onAppStatusChanged` to clear falsely reported `pluggedDevice` sockets on registration, preventing silently dropped incoming connections from Windows PCs.
- **Windows Dual Device Profile Split**: Changed SDP service registration to `BluetoothHidDevice.SUBCLASS1_NONE` (was `COMBO`) to correctly identify the app as a raw security key. This prevents strict Windows 11 drivers from splitting the FIDO profile into conflicting devices (Phone/Screen widgets) and endless connection loops.
- Full details: [2026-03-07-fido2-bluetooth-reliability.md](docs/changelogs/2026-03-07-fido2-bluetooth-reliability.md)

## [Unreleased] - 2026-03-05

### Fixed
- **FIDO2 Domain & Unit Tests**: Fixed 13 previously failing test suites related to FIDO2 domain validations, RP ID extraction, and strict vs implicit consent constraints.
- **BiometricPrompt Context Fix**: Resolved `IllegalStateException` by migrating `MainActivity` to `FragmentActivity` and implementing a robust `findFragmentActivity()` context-lookup helper to unwrap `ContextThemeWrapper` during FIDO2 flows.
- **FIDO2 Signature Counter Fix**: Fixed authentication failure on `webauthn.io` by correcting an ID mismatch in SQLDelight queries, ensuring `signCount` correctly increments and persists in the database.
- Full details: [2026-03-05-fido2-domain-tests-fix.md](docs/changelogs/2026-03-05-fido2-domain-tests-fix.md), [2026-03-05-fido2-biometric-prompt-fix.md](docs/changelogs/2026-03-05-fido2-biometric-prompt-fix.md), & [2026-03-05-fido2-sign-count-fix.md](docs/changelogs/2026-03-05-fido2-sign-count-fix.md)

## [Unreleased] - 2026-03-04
- **FIDO2 Bluetooth Reliability**: Resolved critical protocol negotiation and stability issues for Windows compatibility.
- **U2F-to-CTAP2 Fallback (Windows Probing Fix)**:
    - Implemented a structural dummy `U2F_REGISTER` response to satisfy mandatory host probing during registration.
    - Added `SW_WRONG_DATA` (0x6A80) response to `U2F_AUTHENTICATE` to correctly signal the host to fall back to `CTAP2 GetAssertion`.
- **GetInfo Response Fixes**:
    - Added `pinUvAuthProtocols` (Key `0x06`) to enable CTAP2 negotiation on Windows.
    - Changed `uv` to `true` with `plat: false` to align with biometric cross-platform key expectations.
- **CBOR Integer Encoding**: Fixed a critical bug in `CborCodec` where negative integers (e.g., COSE ES256 algorithm ID `-7`) were incorrectly encoded as unsigned/float16 garbage, causing Windows parser rejects.
- **AuthenticatorData Attestation Fix**: Fixed missing `AT (0x40)` bit in `AuthenticatorData` flags. This prevents the Windows browser from crashing when processing `MakeCredential` attestation responses containing large public key payloads.
- **GetAssertion Base64 Encoding Bug**: Fixed a critical `E_INVALIDARG (0x80070057)` error on Windows by encoding `authData`, `signature`, and `credentialId` as binary CBOR byte strings instead of Base64 ASCII text during authentication.
- **KeyNotFound Alias Mismatch**: Refactored `RegisterCredentialUseCase` and `CredentialRepositoryImpl` to centralize cryptographic generation in `Fido2CryptoService`. This resolved a dual-generation race condition that immediately discarded private keys and caused `KeyNotFound` errors on every `GetAssertion` attempt.
- **AAGUID Metadata Integrity**: Replaced the random `SecureRandom` AAGUID generation with a static, deterministic `CHIMALI_AAGUID` to prevent Windows from treating the authenticator as an unknown device model.

### Added
- **Documentation**: Added comprehensive analysis of CTAP2 Bluetooth constraints and fixes tailored for Windows 11 / webauthn.io. Full details: [FIDO2_Windows_Bluetooth_Analysis.md](docs/FIDO2_Windows_Bluetooth_Analysis.md)

## [Unreleased] - 2026-03-03

### Fixed
- **Passkey Storage Error**: Fixed `SQLiteConstraintException` by ensuring Relying Party persistence before consent/credential creation.
- **Credential Upsert**: Implemented insert-or-replace logic in `saveCredential` to support passkey re-registration without UNIQUE constraint crashes.
- **Navigation Loop**: Resolved a "stuck" UI feedback loop by changing `Fido2UiEventBus` to `replay=0` and implementing event consumption.
- **CTAP2 Spec Compliance**: Updated CBOR response builders to use required integer keys for CTAP2 compatibility with Windows.
- **Bluetooth Stability**: Fixed a double-resume race condition in HID app registration; stabilized Windows "Security Key" handshake.
- **Validation Refinement**: Relaxed HTTPS origin requirement and added support for `"none"` algorithm in attestation statements.
- **UI Responsiveness**: Fixed infinite loading screen and biometric prompt triggers in FIDO2 registration/authentication flows.
- **Predictive Back**: Enabled `android:enableOnBackInvokedCallback` to support modern Android back gestures.
- **Bluetooth Init**: Made `initialize()` suspending so "Start Authenticator" requires only one click.
- **HID MTU Fix**: Reverted HID report size to 62 bytes. Android's Classic HID over L2CAP has a strict 64-byte MTU limit; the HIDP protocol consumes 2 bytes, leaving exactly 62 bytes for the FIDO payload. Using 64 bytes caused packet truncation and `0x32 (Not Supported)` errors on Windows.
- **CTAP2 Metadata**: Fixed Windows "Cannot use this security key" error by adding the `algorithms` field (Key `0x0A`) to the GetInfo response and explicitly setting the `transports` field to `usb` to align with Windows CTAP enumeration requirements for HID devices.

### Changed
- **Validation**: Relaxed RP ID validation to support optional protocol prefixes.
- **Test Data**: Randomized mock user IDs in test UI to prevent database collisions.
- **Error Handling**: Enhanced UI error messages with underlying diagnostic details.

### Added
- **Repository**: Added `saveRelyingParty` to handle both insert and update operations for RPs.
- Full details: [2026-03-03-fido2-registration-fix.md](docs/changelogs/2026-03-03-fido2-registration-fix.md) & [2026-03-03-fido2-spec-and-nav-fix.md](docs/changelogs/2026-03-03-fido2-spec-and-nav-fix.md)

## [Unreleased] - 2026-03-02

### Added
- **CTAP2 Credential Management**: Implemented stateful enumeration subcommands (2-5) for listing RPs and credentials.
- **Authentication Testing**: Completed full test suite for US2 (unit, UI, and integration) covering `GetAssertionUseCase`, `SelectCredentialUseCase`, and `AuthenticationPromptScreen`.
- **Hilt Dependency Injection**: Added missing `CredentialRepository` binding in `Fido2Module.kt`.

### Fixed
- **Test Build Issues**: Updated `UserVerificationAvailability` usage in tests to align with updated domain models.
- **SQLDelight schema mismatches**: All named query parameters in `RelyingParty.sq` and `UserConsentRecord.sq` aligned with generated Kotlin API
- **`UserVerificationServiceImpl`**: Rewrote to implement all 14 abstract members of `UserVerificationService`
- **`ConsentVerificationResult.verificationMethod`**: Made nullable to allow unauthenticated consent paths
- **`RegisterCredentialUseCase`**: Removed duplicate `DISCOURAGED` when-branch
- **`CredentialEncryptionService`**: Fixed `RpIdMismatch` constructor argument count
- **`Ctap2CredentialManagementHandler`**: Fixed Flow collection to enable `.size` access
- **`RelyingPartyDao`**: Fixed transaction blocks and `Long`→`Int`/`Boolean` return type casts
- **`BluetoothHidDeviceWrapper`**: Wrapped all permission-gated Android 12+ Bluetooth calls (`registerApp`, `sendReport`, `getProfileProxy`, etc.) in `try/catch SecurityException` blocks to resolve Android Studio lint warnings and handle runtime revocation gracefully.
- Added `BluetoothPermissionDenied` exception subclass to `Fido2Exception`.
- Full details: [2026-03-02-fido2-ctap2-enumeration-and-test-completion.md](docs/changelogs/2026-03-02-fido2-ctap2-enumeration-and-test-completion.md)

### Constitutional Compliance
- ✅ Security First — biometric/PIN gating on all credential operations
- ✅ Zero-Knowledge design — relying parties cannot enumerate stored credentials
- ✅ Post-Quantum ready — PQC key paths integrated through Registration flow

---

## [v0.1.0-alpha] - 2026-03-01

### Added
- **FIDO2 Virtual Authenticator**: Complete foundational implementation for passkey management
- **Post-Quantum Cryptography**: ML-KEM/Kyber integration with Bouncy Castle PQC provider
- **Hierarchical Key Derivation**: HDK-ECDH-P256 implementation following master seed architecture
- **Encrypted Storage**: SQLCipher wrapper for secure credential database access
- **Android KeyStore**: Hardware-backed private key storage and management
- **CBOR Codec**: FIDO2 message encoding/decoding utilities
- **Memory Security**: Zeroing utilities for sensitive data handling
- **Exception Hierarchy**: Comprehensive Fido2Exception system for error handling
- **Clean Architecture**: Domain/data/presentation layer separation with repository pattern
- **Testing Framework**: JUnit5, MockK, and Compose UI Testing setup

### Security
- **Quantum-Ready**: Post-Quantum Cryptography support for future-proofing
- **Master Seed Architecture**: Hierarchical deterministic key derivation
- **Hardware Security**: Android KeyStore integration for private key protection
- **Encrypted Database**: SQLCipher for credential metadata protection
- **Memory Safety**: Secure zeroing of sensitive data arrays

### Architecture
- **Dependency Injection**: Comprehensive Hilt module configuration
- **Database Design**: SQLDelight schemas with optimized indexes and views
- **Error Handling**: Structured exception hierarchy for all failure modes
- **Protocol Support**: FIDO2/WebAuthn message formatting

### Constitutional Compliance
- ✅ Security First principle with PQC integration
- ✅ Master Seed Architecture through hierarchical key derivation
- ✅ Zero-Knowledge privacy-preserving design
- ✅ Post-Quantum cryptographic capabilities
- ✅ Memory safety and secure data handling

### Technical
- **Build System**: Gradle configuration with all required dependencies
- **Permissions**: Android manifest permissions for Bluetooth, biometric, network
- **ProGuard**: Security rules for crypto libraries and obfuscation

## [Unreleased] - 2026-02-25

### Added
- **Documentation Restructuring**: Implemented a stable mnemonic requirement system (`FR-VAULT-010`, etc.) and updated the BRD structure to include a dedicated Accessibility section.
    - Full details: [2026-02-25-doc-restructuring.md](docs/changelogs/2026-02-25-doc-restructuring.md)

### Changed
- **Constitution (v0.3.0)**: Formally adopted IEEE 830 and modern Agile documentation standards as project principles.
- **Requirement IDs**: Migrated all functional and non-functional requirements to a category-based mnemonic path format for better long-term maintainability.

---

## [Unreleased] - 2026-02-23

### Added
- **Secure Credentials Vault (FR-VAULT-010)**: Initial implementation of local-first encrypted storage for passwords, cards, and notes.
    - Hybrid **SQLCipher** + **Loro.dev CRDT** storage architecture.
    - Hardware-backed key management (Android Keystore).
    - Support for dynamic **Custom Fields** and **Labels**.
    - Full details: [2026-02-23-vault-implementation.md](docs/changelogs/2026-02-23-vault-implementation.md)
- **HDKeys Implementation**: New hierarchical deterministic key system based on IETF draft-dijkhuis-cfrg-hdkeys-06 (HDK-ECDH-P256).
    - Multiplicative key blinding for enhanced privacy.
    - RFC 9380 (Hash-to-Curve/Field) support.
    - RFC 9180 (DHKEM) for remote derivation.
    - Full details: [2026-02-18-hdkeys-implementation.md](docs/changelogs/2026-02-18-hdkeys-implementation.md)

### Changed
- **Security Provider**: Switched to **Bouncy Castle** for P-256 elliptic curve arithmetic.
- **BRD Update**: Updated master key management requirements to align with the new HDKeys specification.

### Removed
- **BIP-32**: Legacy BIP-32/BIP-44 implementation removed in favor of HDKeys.

---
*Last Updated: 2026-03-24*
