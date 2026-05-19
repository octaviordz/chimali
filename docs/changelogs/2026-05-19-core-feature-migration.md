# Core Feature Migration

This changelog summarizes the successful completion of the Core Feature Migration, transitioning legacy stubs and platform-dependent abstractions into robust, multiplatform, and clean architecture core systems.

## [Unreleased] - 2026-05-19

### Changed
- **Centralized SQLCipher Database Storage (US1)**: Migrated `VaultDatabase` creation to a unified, centralized `EncryptedDriverFactory` in `:core:database`.
  - Utilizes modern SQLCipher `SupportOpenHelperFactory` for secure database instantiations.
  - Implements cryptographically secure PBKDF2-HMAC-SHA512 key derivation with 2048 iterations from high-entropy master seeds (Constitution §I.3).
  - Enforces physical database integrity verification executing `"PRAGMA integrity_check"` on driver instances.
  - Guarantees strict memory safety by immediately zeroing derived keys and clearing PBE key specifications inside `try/finally` blocks (Constitution §X.5).
  - Fully removed the legacy `SqlCipherWrapper.kt` stub from `:feature:fido2`.
  - Removed all `@Suppress("MagicNumber")` and `@Suppress("TooGenericExceptionCaught")` annotations by introducing well-named companion constants and catching specific `SQLiteException` / `IllegalStateException` types.
- **Secure Clipboard with Auto-Clear (US2)**: Refactored sensitive copy-paste actions to use the multiplatform-native `ClipboardManagerService` from `:core:common` to enforce configured clipboard auto-clear timeouts (FR-SEC-010).
  - Fully purged the deprecated `ClipboardManagerWrapper.kt` stub from `:feature:vault`.
- **Centralized Crypto Provider Initialization (US3)**: Centralized global security provider initialization inside the `onCreate` block of `ChimaliApplication.kt` under `:app`, ensuring BouncyCastle registers exactly once on start.
  - Purged duplicate helper initializations inside the FIDO2 module.
- **Shared Biometric Capability Check (US4)**: Moved the biometric platform capability check (`PlatformUserVerification`) from `:feature:fido2` to a centralized KMP module `:core:security`.
  - Refactored `PlatformUserVerification` to a clean Kotlin Multiplatform **interface**, completely removing `expect class` / `actual class` boilerplate and eliminating `@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")` annotations from the codebase.
  - Implemented concrete, platform-specific classes `AndroidPlatformUserVerification` and `IosPlatformUserVerification`.
  - Added Android dependency `androidx.biometric` directly to `:core:security` `androidMain`.
  - Developed comprehensive JUnit host tests in `androidHostTest` to verify capability mappings without physical devices on the concrete Android implementation.
  - Fully purged legacy stubs in `:feature:fido2`.

### Governance & Alignment
- **Task Tracking**: Marked all migrated tasks (T001 to T019) as completed in the master plan.
- **CI/CD Validation**: Verified the entire migration pass via the full `.\tools\local-ci.ps1` pipeline, achieving a 100% green build, lint, detekt, and unit test execution across all targets.
