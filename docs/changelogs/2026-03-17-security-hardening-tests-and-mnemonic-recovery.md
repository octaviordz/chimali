# Changelog: Security Hardening, Tests, and Mnemonic Recovery

**Date**: 2026-03-17
**Task References**: T133, T146g, T148

## Overview

This update focuses on reinforcing project security through automated sensitive data management, deterministic cryptographic verification, and robust handling of biometric security events. It also completes the BIP39 mnemonic recovery flow and addresses critical build/test infrastructure issues.

## Detailed Changes

### Security & Cryptography

#### Clipboard Security (T133a-c)
- **New Service**: Implemented `ClipboardManagerService` to manage sensitive clipboard operations.
- **Timed Clearing**: Sensitive data copied to the clipboard is automatically cleared after 60 seconds using a non-blocking coroutine-based timer, satisfying **Constitution §IV**.
- **Dev Tools Integration**: The "Copy Master Seed" feature now utilizes this secure service, providing users with a confirmation snackbar showing the clear-out countdown.

#### Mnemonic Recovery (T146g)
- **Import Flow**: Implemented `importMnemonic(CharArray)` in `WalletMasterSeedProvider`.
- **Validation**: Strict 24-word count verification before persistence.
- **Memory Safety**: Mnemonic buffers are explicitly zeroed (`mnemonic.fill('\u0000')`) immediately after processing in the `finally` block to prevent plain-text remnants in memory (**Constitution §I**).
- **Warning System**: Users are now warned via UI snackbar if an existing seed is being overwritten, as this orphans previous credentials.

#### Security Test Series (T148a-d)
- **Memory Zeroing (T148a)**: Verified that `importMnemonic` successfully clears char buffers.
- **Crypto KATs (T148b)**: Implemented "Known Answer Tests" to verify that identical seeds always derive identical HDK public keys (**SC-006**).
- **Biometric Lockout (T148c)**: Hardened the `DevToolsViewModel` to handle `BIOMETRIC_ERROR_LOCKOUT` and `LOCKOUT_PERMANENT` from the OS, ensuring sensitive state is wiped upon auth failure and preventing retry loops.
- **Storage Integrity (T148d)**: Added tests verifying that the database file header adheres to SQLCipher encryption standards and deviates from plain SQLite magic signatures.

### UI & UX Improvements

- **Responsive Dev Tools**: Refactored the "Copy", "QR", and "Clear" buttons in the Development Tools screen to use `FlowRow`. Labels now wrap vertically below icons on narrow screens, improving legibility on different device form factors.

### Bug Fixes & Technical Debt

- **Build Fix (Vault)**: Resolved a "Unresolved reference: test" error in `feature:vault` by adding missing AndroidX test and Espresso dependencies to the module's `build.gradle.kts`.
- **Test Infrastructure**: Fixed regressions in `Fido2CryptoServiceTest` by properly mocking `android.util.Log` and its static methods in the JVM unit test environment.
- **Task Cleanup**: Soft-removed **T147 (Audit Logging)** from `tasks.md` after a `speckit.analyze` pass identified it as underspecified and a potential privacy risk for sensitive local-first data.

## Constitutional Compliance

- ✅ **Security First (§I)**: Implemented explicit memory zeroing for mnemonics and verified it with T148a.
- ✅ **Master Seed Architecture (§II)**: Verified deterministic key derivation with T148b KAT tests.
- ✅ **Performance & Reliability (§IV)**: Enforced 60s clipboard clearing policy.
- ✅ **Uncompromising Quality (§III)**: Resolved latent build errors and unit test failures.
