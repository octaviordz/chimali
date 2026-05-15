# CHANGELOG

All notable changes to the Chimali project will be documented in this file.
Detailed change summaries for major features are stored in the `docs/changelogs/` directory.

## [Unreleased] - 2026-05-15

### Fixed
- **Encoding & UI Character Integrity**: Successfully remediated a project-wide encoding corruption issue where non-ASCII characters were replaced with the replacement character. Fixed corrupted comments in the FIDO2 UI and restored 10 module READMEs.
- **Bluetooth HID API Modernization**: Modernized the Bluetooth HID transport layer with SDK-gated compatibility helpers and explicit API 28+ hardware feature checks.

### Changed
- **Linting Baseline Remediation**: Finalized the comprehensive code quality audit, achieving a ~90% reduction in inline suppressions.
    - Refactored all generic exception handlers in the FIDO2 and Vault modules to use specific, constitutional error types.
    - Purged redundant Compose suppressions and modernized the technical debt tracking format to `DEFERRED(040)`.
- **Detailed changes**: [2026-05-15-linting-remediation-and-encoding-fixes.md](docs/changelogs/2026-05-15-linting-remediation-and-encoding-fixes.md)

## [Unreleased] - 2026-05-14

### Changed
- **Cryptographic Architecture (HDK Migration)**: Completed the migration of the Post-Quantum (ML-DSA) key branch from legacy BIP-32/85 derivation to the IETF `draft-dijkhuis-cfrg-hdkeys-06` HDK standard.
    - Introduced `HdkManager.deriveSalt` to enforce domain-separated salt derivation for the PQ branch.
    - Eliminated all legacy BIP-32/85 CKD paths and constants from the codebase, strictly adopting HDK primitives for deterministic generation.
    - Restored cryptographic isolation between the classical ECDSA tree and the post-quantum keys.
- **Detailed changes**: [2026-05-14-hdk-migration-pq-branch.md](docs/changelogs/2026-05-14-hdk-migration-pq-branch.md)

## [Unreleased] - 2026-05-13

### Fixed
- **BIP39 Asset Loading & FIDO2 Initialization**: Resolved the `FileNotFoundException: bip39_english.txt` crash on app launch by correcting the AGP asset mapping in `core:security`.
- **Event Sourcing Security Remediation**: Successfully implemented the production-grade key management plan, replacing all legacy "dummy" encryption keys with real AES-256 keys derived from the BIP39 master seed.
    - Introduced `EventStoreKeyProvider` for deterministic HMAC-SHA512 key derivation with aggregate-specific labels.
    - Refactored Vault and Passkey event/snapshot repositories to use secure keys, resolving task T032.
    - Executed a one-time truncation of `EventStore` and `SnapshotStore` tables via SQLDelight migrations (`3.sqm`, `10.sqm`) to purge insecure legacy data.
- **Detailed changes**: [2026-05-13-event-sourcing-remediation-and-bip39-fix.md](docs/changelogs/2026-05-13-event-sourcing-remediation-and-bip39-fix.md)

### Changed
- **Event Sourcing Security Audit & Gap Analysis**: Conducted a comprehensive audit of the event sourcing persistence layer, identifying a critical security gap where placeholder "dummy" keys were used for payload encryption.
    - Documented 10 call sites across core and feature repositories using zero-filled keys.
    - Authored a technical remediation plan [analysis-encryption-key-management.md](specs/034-refactor-event-sourcing/analysis-encryption-key-management.md) proposing HMAC-SHA512 key derivation from the BIP39 master seed.
    - Restored security `TODO`s and applied linting suppressions to all affected repository classes.
    - Authored a [handover document](specs/034-refactor-event-sourcing/handover-encryption-key-management.md) to guide the implementation of production-grade key management.
- **Detailed changes**: [2026-05-13-event-sourcing-security-audit.md](docs/changelogs/2026-05-13-event-sourcing-security-audit.md)

## [Unreleased] - 2026-05-07

### Added
- **FIDO2 Quality Hardening & MagicNumber Cleanup**: Systematically eliminated all remaining `MagicNumber` violations in the `feature:fido2` module.
    - Refactored 38 numeric literals into descriptive constants (e.g., `MNEMONIC_WORD_COUNT`, `DEFAULT_VERIFICATION_TIMEOUT_MS`).
    - Standardized Bluetooth device class masks and protocol-level offsets.
    - Synchronized `detekt-baseline.xml` to maintain a zero-violation state for the `MagicNumber` rule.
- **Architectural Refinement (Outcome Migration)**:
    - Purged legacy `DataResult.kt` from `core:common`, finalizing the transition to the `Outcome<T, DomainError>` architecture.
    - Refactored core domain models (`RelyingParty`, `UserConsentRecord`) and use cases to use strongly-typed functional results.
- **Vault UX & Legibility Enhancements**: Refined the Vault UI with improved legibility controls and secret display components.
    - Introduced `LegibleSecretText` with configurable settings for better accessibility.
- **Build Infrastructure Modernization**:
    - Refactored build scripts to the modern `ProjectLayout` API for Gradle 10 compatibility.
    - Upgraded `ktlint` to 14.2.0 and hardened KSP configurations to silence deprecation warnings.
- **CI Pipeline & Quality Hardening**:
    - Resolved build-blocking `ktlint` violations related to backing property naming and orphan KDoc comments in the FIDO2 module.
    - Migrated UI components to modern `AutoMirrored` icons and `lifecycle-runtime-compose` APIs to eliminate deprecation warnings.
- **FIDO2 Database Migration & Baseline Cleanup**:
    - Implemented SQLDelight migration `8.sqm` to safely rename `biometricUsed` and `pinUsed` columns to `isBiometricUsed` and `isPinUsed` across the `UserConsentRecord` table, avoiding schema crashes for existing users while satisfying strict Detekt Boolean naming rules.
    - Fully deleted `detekt-baseline-main.xml` as `feature:fido2` is now 100% compliant with all configured static analysis quality gates.
- **Detailed changes**: [2026-05-07-fido2-quality-hardening-and-outcome-finalization.md](docs/changelogs/2026-05-07-fido2-quality-hardening-and-outcome-finalization.md)
## [Unreleased] - 2026-05-06

### Added
- **FIDO2 Stabilization & WebAuthn L3 Compliance**: Finalized the compliance remediation and UX stabilization for the FIDO2 module.
    - **Ceremony Serialization**: Implemented `CeremonyLock` to prevent concurrent request storms and redundant UI prompts from multi-channel CTAP2 hosts.
    - **Headless Fast-Path**: Optimized the authentication flow to automatically return assertions when exactly one credential matches and UV is not required.
    - **Auto-Confirm UI**: Added auto-confirmation logic for UV=NONE scenarios, eliminating redundant "Sign in" clicks for un-enrolled devices.
    - **WebAuthn L3 Hardening**: Enforced 16-byte minimum `CredentialId` length, standardized EdDSA algorithm negotiation, and implemented adaptive ceremony timeouts.
    - **Security & Quality**: Implemented memory zeroing for PRF outputs and achieved a zero-failure state in the CI pipeline (Ktlint/Detekt).
- **Detailed changes**: [2026-05-06-fido2-stabilization-and-l3-compliance.md](docs/changelogs/2026-05-06-fido2-stabilization-and-l3-compliance.md)

## [Unreleased] - 2026-05-04 (patch 6)

### Fixed
- **GetAssertion "No credentials found" after registration — Base64 padding mismatch**: `findCandidateSummaries` was comparing `allowCredentials` IDs from the CTAP2 wire (`CredentialId.fromByteArray` → no-padding `=`) against DB-stored IDs (`CredentialId.fromEncoded` → may carry trailing `=` from legacy storage paths) using `CredentialId.equals()` (exact `String` equality). Any padding difference produced a false-negative, causing 3 repeated "Credential not found" failures after registration until the host retried with a fresh CTAP2 channel. Fixed by normalizing both sides via `CredentialId.normalized` (strips trailing `=`) before comparison, using a `HashSet` for O(1) lookups.
- **Spurious auth screen after registration completes — stale bus cache**: When webauthn.io sends a GetAssertion on a second CTAP2 channel *while* a MakeCredential is still being processed on the first, the bus stores both a `currentRegistrationRequest` and a `currentAuthenticationRequest`. After the registration ceremony completes and HomeScreen re-enters composition, its startup cache-check found the stale `currentAuthenticationRequest` and navigated to the auth screen unnecessarily. Fixed by calling `uiEventBus.clearAll()` (new method that atomically wipes both caches) on ceremony completion in both `RegistrationPromptViewModel` and `AuthenticationPromptViewModel` (success and cancel paths).

## [Unreleased] - 2026-05-04 (patch 5)

### Fixed
- **FIDO2 Registration Freeze — `pendingDeferred` Was Null**: Reverted the erroneous consume-and-clear pattern introduced in patch 4 for `Fido2HomeViewModel.getPendingRegistration()` / `getPendingAuthentication()`. Those helpers now only read the cache; clearing is the responsibility of the respective `PromptViewModel` instances (already fixed in patch 4). The premature clear in HomeViewModel caused `RegistrationPromptViewModel` to find no event in the cache, leaving `pendingDeferred = null`, so confirming registration was a no-op and the CTAP2 keepalive loop ran until the foreground service was killed.
- **FIDO2 Spurious "Sign In" Prompt During Registration — Dual `LaunchedEffect(Unit)` Race**: Two separate `LaunchedEffect(Unit)` blocks in `Fido2HomeScreen` ran simultaneously, each with an infinite `collect` loop. The second block (for `AuthenticationRequested`) was a permanent blocking collector independent of the first. On back-navigation recomposition, if `currentAuthenticationRequest` was not yet cleared, the second block re-triggered auth navigation concurrently with the first block checking for a pending registration. Fixed by merging both blocks into a single `LaunchedEffect(Unit)` that performs the one-time cache check first, then uses `coroutineScope { launch { } launch { } }` to run both live collectors in parallel under the same cancellation scope.

## [Unreleased] - 2026-05-04 (patch 4)

### Fixed
- **FIDO2 Repeated UI Prompts — Stale Event Cache Not Cleared**: Fixed a bug where returning to `Fido2HomeScreen` after an authentication or registration ceremony caused a second (and third…) prompt to appear. The `Fido2UiEventBus.currentAuthenticationRequest` / `currentRegistrationRequest` fields were only cleared in the *cache path* of the ViewModel `init` block, but not in the *live SharedFlow path*. When the ViewModel received the event via the flow before the `?.let` cache check executed, `clearAuthenticationRequest()` was never called. On back-navigation, Compose recomposed `HomeScreen`, `LaunchedEffect(Unit)` re-ran, and `getPendingAuthentication()` returned the stale pointer — navigating to the auth screen again.
    - Added `uiEventBus.clearAuthenticationRequest()` at the start of the `AuthenticationRequested` `onEach` handler in `AuthenticationPromptViewModel`.
    - Added `uiEventBus.clearRegistrationRequest()` at the start of the `RegistrationRequested` `onEach` handler in `RegistrationPromptViewModel`.
    - Changed `Fido2HomeViewModel.getPendingRegistration()` and `getPendingAuthentication()` to consume-and-clear atomically (via `.also { clear() }`), so even if the ViewModel hasn't been created yet, checking for a pending event from `HomeScreen` consumes it and prevents it from being re-triggered.

## [Unreleased] - 2026-05-04 (patch 3)

### Fixed
- **FIDO2 Authentication UI Hang — Missing Navigation Route**: Fixed the root cause of the authentication ceremony stalling with no UI prompt. `AuthenticationPromptScreen` was fully implemented but was never wired into the navigation graph, so no route existed to navigate to it. The `Fido2UiEventBus` was correctly dispatching `AuthenticationRequested` events, but `Fido2HomeScreen` had no listener for them, leaving the HID transport waiting indefinitely for a `CompletableDeferred` that was never resolved, eventually causing process termination.
    - Added `AUTHENTICATION_ROUTE = "fido2/authenticate"` to `Fido2Destinations`.
    - Added a `composable(AUTHENTICATION_ROUTE)` entry in `Fido2RegistrationNavGraph`, symmetrically matching the existing `REGISTRATION_ROUTE` entry.
    - Added `onAuthenticateRequest` callback param to `Fido2HomeScreen` and wired a `LaunchedEffect` that checks `getPendingAuthentication()` on start and collects live `AuthenticationRequested` events from the bus, navigating to `AUTHENTICATION_ROUTE` on receipt.
    - Added `getPendingAuthentication()` helper to `Fido2HomeViewModel` to expose `Fido2UiEventBus.currentAuthenticationRequest` for the cached-event check.

## [Unreleased] - 2026-05-04 (patch 2)

### Fixed
- **FIDO2 Authentication Regression — Allow-List Credential ID Mismatch**: Corrected a critical bug in `Ctap2GetAssertionHandler` where incoming allow-list credential IDs sent as Base64url strings were decoded via `String.toByteArray()` (UTF-8 encoding) instead of `CredentialId.fromEncoded()`. This produced a completely different byte sequence than the stored credential, causing the allow-list filter in `GetAssertionUseCase` to always return zero candidates, returning `CTAP2_ERR_NO_CREDENTIALS` to the RP on every authentication attempt after registration.
- **FIDO2 Warmup Crash — `CredentialId` Minimum Length Violation**: Fixed `Fido2CryptoService.warmUpMasterSeed()` where the probe `CredentialId` was constructed from the 6-byte literal `"warmup"`, violating the 16-byte minimum enforced by `CredentialId.fromByteArray()`. The probe now uses a 17-byte string (`"warmup-chimali-v1"`) to satisfy the guard without triggering `IllegalArgumentException` during startup.

## [Unreleased] - 2026-05-04

### Added
- **WebAuthn Level 3 Compliance**: Finalized Phase 9 Polish, achieving full FIDO2 implementation compliance with WebAuthn L3 requirements.
    - **Algorithm Negotiation Hardening**: Implemented strict negotiation favoring ES256 and EdDSA, while explicitly rejecting deprecated COSE identifiers (-9, -19, -51, -52) per spec §5.4.
    - **PRF Extension (hmac-secret)**: Implemented full CTAP2.1 PRF support, enabling hardware-backed salt-based key derivation for advanced authentication scenarios.
    - **credProtect Support**: Added FIDO2.1 `credProtect` extension parsing and logging, enabling granular user verification policy enforcement.
    - **Attestation Integrity**: Implemented "AttCA" attestation support and refactored CBOR serialization to use pre-signed `authData` blocks, ensuring 100% signature verification success.
    - **Static Analysis Remediation**: Achieved a zero-violation state across the CI pipeline by resolving complex linting, formatting, and detekt findings across the FIDO2 and Domain modules.
- **Detailed changes**: [2026-05-04-webauthn-l3-compliance.md](docs/changelogs/2026-05-04-webauthn-l3-compliance.md)

## [Unreleased] - 2026-05-01

### Changed
- **Shared Domain Library Evolution**: Completed the migration of the business logic layer to Kotlin Multiplatform (KMP). Standardized on strongly-typed value classes (`RpId`, `UserId`, `PasskeyId`, `CredentialId`) to eliminate primitive obsession and ensure cross-platform type safety.
- **FIDO2 Authentication Fix**: Resolved a critical regression where Windows devices failed to recognize security keys. Enforced raw binary serialization for FIDO2 identifiers in the CTAP2 protocol handler, ensuring strict compliance with the CTAP2 specification.
- **CredentialId Normalization**: Hardened `CredentialId` handling with automatic Base64 padding normalization and robust decoding, resolving inconsistencies between platform-specific Base64 implementations.
- **Domain Model Modernization**: Refactored core entities to use platform-agnostic types (`kotlinx.datetime`, `kotlinx.serialization`), enabling seamless code sharing across Android and iOS.
- **Test Suite Stabilization**: Resolved integration test failures by remediating mock mismatches and eliminating test pollution, achieving 100% pass rate in the FIDO2 data integration suite.
- **Detailed changes**: [2026-05-01-shared-domain-library-and-fido2-fix.md](docs/changelogs/2026-05-01-shared-domain-library-and-fido2-fix.md)

## [Unreleased] - 2026-04-30

### Changed
- **FIDO2 Outcome Migration**: Finalized the transition of `feature/fido2` to a refined `Outcome<T, DomainError>` architecture. Successfully migrated UseCases, Services, and Handlers while preserving functional parity with existing test suites by wrapping legacy exceptions as error causes.
- **Outcome API Enhancements**: Expanded the functional result library in `core:common` with idiomatic property-based state checks (`isSuccess`, `isFailure`) and structured processing helpers (`getOrThrow`, `getOrElse`, `mapError`).
- **Functional Exception Handling Migration**: Completed the transition of `feature/vault` to structured functional architecture, eliminating generic `try-catch` blocks and standardizing error propagation.
- **ViewModel Architecture Refinement**: Refactored ViewModels project-wide to propagate domain-specific errors via functional results, ensuring clean decoupling of the presentation layer from technical failures.
- **Data Layer Hardening**: Replaced generic exception catching in `BluetoothHidTransportImpl` with explicit bounds checking and standardized boundary-level logging via Kermit.
- **Detailed changes**: [2026-04-30-fido2-outcome-migration.md](docs/changelogs/2026-04-30-fido2-outcome-migration.md) & [2026-04-30-functional-exception-handling-migration.md](docs/changelogs/2026-04-30-functional-exception-handling-migration.md)

## [Unreleased] - 2026-04-29

### Changed
- **Detekt Quality Gate Hardening**: Upgraded the project's static analysis configuration to align with Android Kotlin Expert rules (e.g., `CyclomaticComplexity` at 40, `LongMethod` at 400).
- **Technical Debt Elimination**: Systematically removed all target `@Suppress` annotations project-wide and successfully remediated all exposed quality violations (e.g., extracting magic numbers to `const val`, handling swallowed exceptions) without altering business logic.
- **Strict Zero Literal Policy**: Enforced the `MagicNumber` rule with a restrictive ignore list to prevent the use of magic numbers in Compose configurations.
- **Detailed changes**: [2026-04-29-detekt-expert-rules-upgrade.md](docs/changelogs/2026-04-29-detekt-expert-rules-upgrade.md)

### Changed
- **Compose Preview Visibility Enforcement**: Systematically refactored all UI previews in `feature:vault` to `private` scope to clean up the public API surface.
- **Detekt Quality Gate Hardening**: Updated `detekt.yml` to ignore `@Preview` functions for `UnusedPrivateMember` while strictly enforcing the `PreviewPublic` rule project-wide.
- **Technical Debt Elimination**: Purged all `@Suppress("PreviewPublic")` and `@Suppress("ForbiddenComment")` annotations along with associated visibility TODOs.
- **Detailed changes**: [2026-04-29-compose-preview-visibility-enforcement.md](docs/changelogs/2026-04-29-compose-preview-visibility-enforcement.md)

## [Unreleased] - 2026-04-27

### Changed
- **ViewModel Forwarding Cleanup**: Eliminated `@Suppress("ViewModelForwarding")` annotations and refactored `CredentialSwipeToDismissBox` to use proper callback-based communication instead of direct ViewModel parameter passing.
- **Compose Architecture Compliance**: Updated component signature from `(credential, viewModel)` to `(credential, onPendingDelete, onSelect, modifier)` following Jetpack Compose state hoisting best practices.
- **Static Analysis Enforcement**: Achieved zero Detekt `ViewModelForwarding` violations while maintaining 100% functional compatibility and all existing test results.
- **Detailed changes**: [2026-04-27-viewmodel-forwarding-cleanup.md](docs/changelogs/2026-04-27-viewmodel-forwarding-cleanup.md)

### Changed
- **Compose Modifier Compliance**: Systematically refactored 30+ UI composables across `:feature:vault` and `:feature:fido2` to strictly enforce `ModifierMissing` and `ComposableParamOrder` lint rules.
- **Parameter Ordering Standardization**: Established a project-wide signature pattern `(requiredData, requiredEventLambdas, modifier: Modifier = Modifier, optionalParams)` to resolve linting conflicts between reordering requirements and trailing lambda rules.
- **Lint Cleanup**: Eliminated all project-wide technical debt related to `ModifierMissing` by removing `@Suppress` annotations and verifying compliance via `tools/local-ci.ps1`.
- **Detailed changes**: [2026-04-27-compose-modifier-compliance-and-lint-cleanup.md](docs/changelogs/2026-04-27-compose-modifier-compliance-and-lint-cleanup.md)

## [Unreleased] - 2026-04-26

### Added
- **Static Analysis Documentation**: Authored `docs/quality.md` outlining the local execution, tooling overview, and suppression guidelines for Ktlint, Detekt, and Android Lint.

### Changed
- **Compose Detekt Quality Gates**: Integrated `detekt-compose-rules` across all Compose-enabled Android modules (`:app`, `:core:ui`, `:feature:*`). Enforced Jetpack Compose best practices, suppressed legitimate false-positives via narrowest-scope `@Suppress("RuleId")` annotations, and preserved unmodified `detekt.yml` baselines.
- **Android Lint Configuration**: Standardized `lintOptions` (now configured via modern AGP 9.0+ `ApplicationExtension`/`LibraryExtension`) across all Android modules in the root `build.gradle.kts`. Fails the build on error-level issues and generates isolated XML/HTML reports.
- **CI Pipeline Enhancements**: Appended a `Lint (Release)` task invocation inside `tools/local-ci.ps1`, ensuring robust CI tracking for platform-specific accessibility, structural, and layout problems.
- **Detailed changes**: [2026-04-26-compose-detekt-and-android-lint.md](docs/changelogs/2026-04-26-compose-detekt-and-android-lint.md)

### Fixed
- **Bluetooth Adapter Deprecation**: Replaced `BluetoothAdapter.getDefaultAdapter()` with `Context.getSystemService(BluetoothManager::class.java)` in `BluetoothHidAuthenticatorImpl` to address API deprecations and ensure future-proof compatibility. Also updated out-of-date KDoc references.
- **Detailed changes**: [2026-04-26-bluetooth-adapter-deprecation-fix.md](docs/changelogs/2026-04-26-bluetooth-adapter-deprecation-fix.md)

## [Unreleased] - 2026-04-25

### Fixed
- **Detekt Quality Hardening (Extended)**:
    - Hardened project gates by enabling `ForbiddenImport` (banning `android.util.Log` in favor of Kermit), `ForbiddenMethodCall` (banning `println`), `DontDowncastCollectionTypes`, and `StringShouldBeRawString`.
    - Expanded all internal wildcard imports across the FIDO2 module and established a project-wide ban on new wildcards.
    - Standardized string threshold for raw strings to **5** to balance readability for JSON fragments.
- **CI Pipeline Hardening**:
    - Updated `local-ci.ps1` to correctly trigger and report KMP Android compilation errors.
    - Enabled Gradle Configuration Cache (`--configuration-cache`) by default in `local-ci.ps1` to drastically reduce CI execution time. Added a `-NoConfigurationCache` opt-out flag.
- **FIDO2 Test Stability**: Resolved "Unresolved reference 'any'" errors in FIDO2 tests caused by MockK/Kotlin resolution ambiguities in the current project environment.
- **Code Polish & Refactoring**:
    - Refactored `RegisterCredentialUseCase` from nested `if/else` to idiomatic guard clauses.
    - Migrated `Fido2Initializer` to structured concurrency (feature-scoped coroutines).
    - Renamed `Services.kt` to `CryptoService.kt` for domain clarity.
    - Purged unused dependencies, mocks, and scratch files from the `fido2` module.
- **Build & KSP Stability**:
    - Eliminated Koin KSP deprecation warnings (`'defaultModule' generation is deprecated`) by configuring `KOIN_DEFAULT_MODULE` properly within `android.defaultConfig` for standard Android modules (`vault`, `editor`) and top-level for KMP modules (`domain`).
- **Detailed changes**: [2026-04-25-detekt-quality-hardening-and-code-polish.md](docs/changelogs/2026-04-25-detekt-quality-hardening-and-code-polish.md), [2026-04-25-ci-pipeline-hardening-and-fido2-test-fixes.md](docs/changelogs/2026-04-25-ci-pipeline-hardening-and-fido2-test-fixes.md), & [2026-04-25-build-and-ksp-fixes.md](docs/changelogs/2026-04-25-build-and-ksp-fixes.md)

## [Unreleased] - 2026-04-24

### Added
- **R8 Optimization & Hardening**: Implemented a production-ready R8 configuration. Enabled Full Mode, resource shrinking, and established a `shrunkDebug` build type for local verification. Purged redundant ProGuard rules for Bouncy Castle, SQLCipher, and AndroidX, resulting in significant APK size reduction.
- **KMP Debug Flag**: Implemented a centralized, platform-agnostic `isDebug` flag using `expect/actual` pattern. Enabled secure build variant detection in `:core:common` and adopted it in `:feature:fido2` to gate sensitive development tools, ensuring they are stripped from production binaries via R8.
- **Detailed changes**: [2026-04-24-r8-optimization-and-hardening.md](docs/changelogs/2026-04-24-r8-optimization-and-hardening.md), [2026-04-24-kmp-debug-flag-implementation.md](docs/changelogs/2026-04-24-kmp-debug-flag-implementation.md), & [2026-04-24-detekt-maxlinelength-enforcement.md](docs/changelogs/2026-04-24-detekt-maxlinelength-enforcement.md)
- **Detekt Quality Enforcement**: Successfully implemented a project-wide 120-character line length limit. Refactored production and test code across `fido2`, `vault`, `bluetooth`, and `ui` modules to comply with the new quality gates while preserving documentation integrity. Standardized on string concatenation for long logical single lines.

## [Unreleased] - 2026-04-23

### Changed
- **KMP Build Stabilization**: Successfully stabilized the multiplatform build infrastructure following the migration to AGP 9.2.0 and the new Kotlin Multiplatform Android plugin.
- **Platform-Specific Isolation**: Relocated JVM-specific cryptographic and database tests from `commonTest` to `androidHostTest` to ensure clean compilation of Kotlin/Native (iOS) targets.
- **SqlDelight 2.0 Compatibility**: Resolved critical type mismatch regressions and `ClassCastException` in DAO unit tests caused by SqlDelight 2.0's transition to `QueryResult` wrappers.
- **CI Hardening**: Updated `local-ci.ps1` with robust failure detection and comprehensive compilation coverage across all test and production source sets.
- **KtLint Project-wide Polish**: Standardized the entire repository using the Official Kotlin Style Guide and established style enforcement gates.

### Fixed
- **iOS Compilation**: Resolved compilation failures in iOS source sets by adding missing `ExperimentalForeignApi` opt-ins for `NSFileManager` interactions.
- **Compose Runtime Mismatch**: Fixed "IncompatibleComposeRuntimeVersionException" by adding explicit `compose.runtime` dependencies to KMP `commonMain` in feature modules.
- **Hidden Compilation Errors**: Resolved hidden test failures in `RegisterCredentialUseCaseTest.kt` by exposing them through expanded CI compilation checks.

### Added
- **Detailed changes**: [2026-04-23-agp-920-kmp-stabilization.md](docs/changelogs/2026-04-23-agp-920-kmp-stabilization.md) & [2026-04-23-ci-hardening-and-ktlint-polish.md](docs/changelogs/2026-04-23-ci-hardening-and-ktlint-polish.md)

## [Unreleased] - 2026-04-22

### Added
- **Corrupted Key Repair Implementation**: Completed the `CorruptedKeyRepairWorkerImpl` to perform background HDK re-derivation of public keys, ensuring that corrupted or missing database entries are automatically healed during pagination triggers.
- **Detailed changes**: [2026-04-22-fido2-pagination-stabilization.md](docs/changelogs/2026-04-22-fido2-pagination-stabilization.md) & [2026-04-22-corrupted-key-repair-implementation.md](docs/changelogs/2026-04-22-corrupted-key-repair-implementation.md)

### Changed
- **FIDO2 Pagination Stabilization**: Refactored the FIDO2 testing architecture to explicitly align MockK stubs and UI assertions with the newly paginated `CredentialRepository` interface, ensuring robust type resolution.
- **Flaky Test Resolution**: Hardened `CredentialManagementViewModelTest` by sorting simulated pagination data correctly by `lastUsedAt`, eliminating race conditions and nanosecond timing collisions during list evaluation.
- **Database Schema & DAO**: Extended `PasskeyCredential.sq` and `PasskeyCredentialDao` with a targeted `updatePublicKey` operation to support the background repair workflow.

### Fixed
- **Runtime Dependency Resolution**: Identified and eliminated a circular Koin dependency loop (StackOverflowError) between `CredentialRepositoryImpl` and `CorruptedKeyRepairWorkerImpl`, restoring stable dependency injection on Android execution paths by injecting `PasskeyCredentialDao` and `Fido2CryptoService` directly into the worker.
- **Type Mismatch Compilations**: Removed redundant MockK overrides that were returning invalid type parameters during unit testing, bringing the FIDO2 feature suite back to 100% build stability.

## [Unreleased] - 2026-04-21

### Changed
- **KMP Migration of `core:domain`**: Migrated the business logic layer to Kotlin Multiplatform (KMP). Standardized on `@Serializable` domain entities and implemented platform-agnostic repository interfaces and use cases, verified with cross-platform tests.
- **Clipboard Service Stabilization**: Refactored `ClipboardManagerService` to be asynchronous (`suspend`), enabling thread-safe coroutine-based synchronization via `Mutex`. Resolved compilation errors and updated all callers in `DevToolsViewModel` and `CredentialUseCases`.
- **FIDO2 Database Stability**: Implemented atomic `UPSERT` for `RelyingParty` using `sqlite-3-38-dialect` to fix primary key collisions during re-registration. Aligned JVM targets to `JVM_17` to resolve build inconsistencies.

### Fixed
- **Clipboard Concurrency & Syntax**: Resolved a compilation failure in `AndroidClipboardManagerService` by correctly using `Mutex.withLock` within a coroutine context. Fixed property hiding and syntax errors in `ClipboardError.kt`.
- **Passkey Algorithm Validation**: Added support for `COSE_RS256` (-257) in the `PasskeyCredential` model, resolving crashes when loading credentials registered with RSA keys.
- **Management UI Navigation**: Fixed unresponsive "Close" button in Passkey Details by ensuring correct state dismissal in `CredentialManagementViewModel`.

### Added
- **Detailed changes**: [2026-04-21-core-domain-kmp-migration-and-clipboard-service-fixes.md](docs/changelogs/2026-04-21-core-domain-kmp-migration-and-clipboard-service-fixes.md) & [2026-04-21-fido2-database-stability-and-rs256-support.md](docs/changelogs/2026-04-21-fido2-database-stability-and-rs256-support.md)

## [Unreleased] - 2026-04-20

### Changed
- **KMP Migration of `core:common`**: Converted the `:core:common` module to Kotlin Multiplatform (KMP), enabling shared code across Android and iOS. Standardized on Koin Annotations (Constitution §III) using a robust `expect`/`actual` bridge pattern for dispatchers and clipboard services.
- **Kermit Logging Migration**: Completed the refactor of 50+ legacy `Timber` calls to `co.touchlab.kermit.Logger` lambda syntax across the `:feature:fido2` module. Standardized all structured diagnostic logging to use native Kotlin string templates instead of `String.format()`, ensuring KMP compatibility and eliminating unnecessary object allocations.
- **KMP Logging Infrastructure**: Refactored `LocalCrashReportingLogWriter` and `PrivacyLogScrubber` to be fully Kotlin Multiplatform (KMP) compliant. Migrated file I/O to Okio and timestamping to `kotlinx-datetime` (via `kotlin.time`), enabling reliable cross-platform crash logging and log rotation in `commonMain` for Android and iOS targets.

### Fixed
- **FIDO2 Performance Optimization (NFR-PERF-030)**:
    - Resolved app startup hangs by offloading BouncyCastle/AndroidKeyStore warm-ups to background threads.
    - Eliminated "OVER BUDGET" ceremony latency by replacing `@Synchronized` with a `Mutex` in `WalletMasterSeedProvider` and implementing proactive master seed pre-warming in the FIDO2 dashboard.

### Added
- **Detailed changes**: [2026-04-20-core-common-kmp-migration-and-di-refactor.md](docs/changelogs/2026-04-20-core-common-kmp-migration-and-di-refactor.md) & [2026-04-20-fido2-logging-migration-and-performance-optimizations.md](docs/changelogs/2026-04-20-fido2-logging-migration-and-performance-optimizations.md)

## [Unreleased] - 2026-04-19

### Fixed
- **FIDO2 Registration & Android 14 Compliance**: Resolved "Register" ceremony failures by explicitly declaring `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` for the `Fido2TransportService` and ensuring `POST_NOTIFICATIONS` is granted on Android 13+.
- **Foreground Service Permissions**: Fixed a `SecurityException` on Android 14+ by adding mandatory `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_CONNECTED_DEVICE` declarations to the manifests.
- **Startup Permission Gate**: Expanded the `MainActivity` startup gate to include the notification permission request for Android 13+, ensuring stable background-to-foreground transitions for the Bluetooth HID transport.

### Added
- **Detailed changes**: [2026-04-19-fido2-android-14-foreground-service-and-notification-fixes.md](docs/changelogs/2026-04-19-fido2-android-14-foreground-service-and-notification-fixes.md)

## [Unreleased] - 2026-04-18

### Fixed
- **Koin DI Stabilization**: Resolved cascading `NoDefinitionFoundException` runtime crashes by adding missing `@Single` annotations to `BluetoothHidDeviceWrapper` and `HidReportParser`, allowing KSP to correctly generate the DI graph for the `fido2` module.
- **Android 12+ Bluetooth Permissions**: Fixed a `SecurityException` triggered when users interacted with the "Start Authenticator" button. Updated `Fido2HomeScreen` to explicitly check and dynamically request `BLUETOOTH_CONNECT` and `BLUETOOTH_ADVERTISE` via `ActivityResultContracts.RequestMultiplePermissions` on Android API 31+.

### Added
- **Detailed changes**: [2026-04-18-fido2-koin-di-and-bluetooth-permissions-fixes.md](docs/changelogs/2026-04-18-fido2-koin-di-and-bluetooth-permissions-fixes.md)

## [Unreleased] - 2026-04-17

### Changed
- **KMP & CMP Migration**: Finalized the transition to Kotlin Multiplatform and Compose Multiplatform for the `:feature:fido2` and `:feature:vault` modules. This includes migrating from Hilt to Koin for dependency injection (Constitution §III) and integrating CMP plugins for multi-environment UI sharing.
- **KMP-Native Cryptography**: Introduced the Signum library to the project dependency graph to support multiplatform-native cryptographic operations.

### Fixed
- **KMP Test Suite Stabilization**: Migrated 300+ assertions to `kotlin.test`, resolved cryptographic TypeMismatch bindings in HDK tests, and eliminated platform-specific context instantiation conflicts in common source sets.
- **Git Hygiene**: Updated the root `.gitignore` to exclude `.kotlin/` metadata and `*.klib` artifacts, preventing local build caches from being tracked.

### Added
- **Detailed changes**: [2026-04-17-kmp-cmp-migration-and-stabilization.md](docs/changelogs/2026-04-17-kmp-cmp-migration-and-stabilization.md)

## [Unreleased] - 2026-04-14

### Fixed
- **ML-DSA WebAuthn Compatibility**: Resolved "Invalid data" and "Invalid key type" errors in WebAuthn registration. Corrected COSE algorithm ID to `-49` (ML-DSA-65), optimized key encoding to raw 1952-byte format, and implemented a custom `DeterministicSecureRandom` to ensure cryptographic consistency across Android application lifecycles.
- **Attestation Statement Integrity**: Refactored the attestation response flow to use pre-signed `authData` bytes directly, eliminating signature verification failures caused by CBOR re-serialization divergences.
- **Buffer Size Alignment**: Increased the attestation buffer limit to 4096 bytes to support large Post-Quantum (ML-DSA) signature payloads.

### Added
- **Detailed changes**: [2026-04-14-fido2-ml-dsa-webauthn-compat-fixes.md](docs/changelogs/2026-04-14-fido2-ml-dsa-webauthn-compat-fixes.md)

## [Unreleased] - 2026-04-08 (Update 2)

### Changed
- **Bluetooth Configuration Centralization**: Refactored scattered Bluetooth connection lifecycle constants (timeouts, retries, and pacing delays) into a centralized `BluetoothHidConfigProvider`. It supplies a configurable `BluetoothHidConfig` data class that cleanly manages OEM-specific Bluetooth connection behaviors.

### Fixed
- **Windows 11 L2CAP MTU Compliance**: Resolved severe connection rejection (`ERROR_NOT_SUPPORTED 0x32`) by the Windows 11 `bthid.sys` driver. Reverted the FIDO HID report size from `64` bytes back to `62` bytes. Including the 2-byte HID header overhead, packets now fit exactly within the strict `64`-byte Classic Bluetooth L2CAP MTU enforced by Windows and Android.

### Added
- **Detailed changes**: [2026-04-08-fido2-bluetooth-mtu-and-config-refactor.md](docs/changelogs/2026-04-08-fido2-bluetooth-mtu-and-config-refactor.md)

## [Unreleased] - 2026-04-08

### Fixed
- **FIDO2 HID Framing Accuracy**: Corrected a critical regression in `HidReportParser` where reports were incorrectly assumed to be 62 bytes. Aligned `HID_PACKET_SIZE` to exactly 64 bytes to match the HID Descriptor and Android's `onInterruptData` delivery, eliminating multi-packet CBOR corruption in `GetInfo` responses.
- **Windows U2F Polling Loop**: Restored the `CAPABILITY_NMSG` (0x08) bit in `CTAPHID_INIT` responses to explicitly signal lack of U2F support. This prevents Windows from entering an infinite "Touch your security key" polling loop caused by legacy U2F registration probes.
- **Asus Connectivity Stability**: Resolved an issue where proactive `connect()` calls triggered bond destruction on Asus Zenfone devices. Reverted to standard, passive OS-level connection management for better stability across OEM Bluetooth stacks.
- **U2F-to-CTAP2 Escalation**: Refactored `BluetoothHidTransportImpl` to return `SW_CONDITIONS_NOT_SATISFIED` (0x6985) for `U2F_REGISTER` probes, correctly signaling to Windows to escalate to the CTAP2 MakeCredential flow.

### Added
- **Protocol Safeguards**: Added extensive documentation and in-code warnings regarding 64-byte report size invariants and `CAPABILITY_NMSG` requirements to prevent future "cleanup" regressions.
- **Detailed changes**: [2026-04-08-fido2-bluetooth-hid-connectivity-fixes.md](docs/changelogs/2026-04-08-fido2-bluetooth-hid-connectivity-fixes.md)

## [Unreleased] - 2026-04-06

### Changed
- **Bluetooth HID Transport Hardening**: Refactored the HID transport layer to eliminate redundant internal queues and race conditions. Implemented a serialized, single-worker coroutine for packet dispatch with a mandatory 20ms inter-packet pacing delay (REPORT_PACE_DELAY_MS) to prevent HCI buffer overflows on constrained OEM Bluetooth stacks.
- **Robust Bonding Lifecycle**: Implemented deferred connection acceptance for devices in the `BOND_BONDING` state. The authenticator now parks these connections and only promotes them to `Connected` once `ACTION_BOND_STATE_CHANGED` confirms a secure `BOND_BONDED` link, preventing Windows `0x8007000d` (ERROR_INVALID_DATA) errors caused by unencrypted L2CAP traffic.
- **Protocol Precision**: Refactored `BluetoothHidTransportImpl` to replace magic numbers with named constants for CTAPHID/APDU offsets and DER encoding tags, improving specification parity and code maintainability.

### Fixed
- **Motorola HID Reconnection (Zombie Lockout)**: Resolved a critical edge case where Motorola's Bluetooth daemon would lock out incoming Windows connections for 66 seconds after app restart. Implemented a "Phantom Flush" exploit that commands native connecting/disconnecting to the zombie session MAC, forcing the baseband cache to clear and eliminating the 66-second reconnection lockout.
- **Windows Error Mitigation**: Resolved the "Unknown Device State" (0x8007000d) error encountered during first registration by removing the malformed all-zeros HID "keepalive" report, which was incorrectly interpreted by the Windows CTAPHID parser.
- **Reconnection Reliability**: Restored the mandatory disconnect of any existing matching device during app registration. This clears stale L2CAP sockets from the Bluetooth daemon, enabling reliable "one-click" reconnection without requiring the user to unpair/repair.
- **Bluetooth Hardware Resilience**: Added an `ACTION_STATE_CHANGED` receiver to the HID wrapper to immediately clear proxy references and reset state when the user disables Bluetooth hardware.

### Added
- **OEM Quirk Management**: Introduced `BluetoothQuirks.kt` to centralize hardware-specific workarounds, including phantom-device disconnect logic for Motorola stacks.
- **Detailed changes**: [2026-04-06-fido2-bluetooth-hid-reliability-and-pacing.md](docs/changelogs/2026-04-06-fido2-bluetooth-hid-reliability-and-pacing.md)

## [Unreleased] - 2026-04-04

### Changed
- **FIDO2 Protocol Refinement**: Refactored domain models (`AttestationObject`, `AssertionObject`, `ClientData`) to replace hardcoded strings and magic numbers with standardized constants (e.g., `TYPE_CREATE`, `FLAG_USER_PRESENT`).
- **Code Quality & Maintenance**: Performed module-wide clean-up of `RegisterCredentialUseCase` and `GetAssertionUseCase` to eliminate code smells and improve alignment with modern Kotlin idioms.
- **Static Analysis Compliance**: Resolved residual Detekt and Ktlint warnings, ensuring 100% compliance with project quality gates (NFR-ARCH-040).
- **Test Stability**: Enhanced `Fido2StressTest` with refined state management for more reliable automated verification.
- **Detailed changes**: [2026-04-04-fido2-code-quality-and-refining.md](docs/changelogs/2026-04-04-fido2-code-quality-and-refining.md)

## [Unreleased] - 2026-04-03

### Added
- **Cryptography & Security**: Implemented `AesSivEncryptionManager` for deterministic, authenticated AES-256-SIV encryption, enabling exact-match database lookups on encrypted metadata (`EncryptedMetadataIndexService`). This ensures all RP ID tags and credential aliases are stored cryptographically secure per Constitution §I.2 constraints.
- **FIDO2 Protocol Extensibility**: Added `HmacSecretProcessor` to construct, encrypt, and fulfill CTAP `hmac-secret` extensions natively over the authenticator interfaces.

### Changed
- **HID Transport Stability**: Introduced a thread-safe Kotlin `Channel<ByteArray>` based FIFO queuing system inside `BluetoothHidTransportImpl`. It explicitly enforces zero packet collisions between continuous internal probing and out-of-band CBOR telemetry mapping.

### Fixed
- **Specification Parity**: Aligned documented failure paths (in `spec.md` and `plan.md`) directly with CTAP standard OS errors (0x27 Memory Full, 0x29 Consent Denied).
- **Static Check Gate**: Resolved residual detekt exceptions resulting in clean analysis reports for all feature layers.
- **Detailed changes**: [2026-04-03-fido2-cryptographic-and-transport-hardening.md](docs/changelogs/2026-04-03-fido2-cryptographic-and-transport-hardening.md)

## [Unreleased] - 2026-04-02

### Added
- **FIDO2.1 credProtect Extension (Phase 6)**: Implemented full support for the `credProtect` extension in `RegisterCredentialUseCase`. The authenticator now defaults to `userVerificationOptional (0x01)` for all credentials, enhancing compatibility with modern browser requirements.
- **HDK Known Answer Tests (KATs) (Phase 7)**: Implemented a robust suite of fixed-vector tests for `CreateContext`, `DeriveSalt`, and `DeriveBlindingFactor` to ensure 100% compliance with IETF `draft-dijkhuis-cfrg-hdkeys-06`.

### Changed
- **HDK Spec Alignment (Phase 7)**: Corrected the `DeriveSalt` formula to strictly match `H(salt || ctx)` as per spec §2.3. Achieved full alignment with the P-256 ciphersuite.
- **Credential Storage Restoration**: Standardized the maximum credential limit to **1000 accounts**, aligning the repository implementation with the Business Requirements Document (FR-HID-022).
- **Security Hardening**: Implemented explicit memory zeroization for high-entropy blinding factors during the derivation process using `try/finally` blocks to prevent RAM-based key leakage.
- **UI Button Standardization**: Standardized button styling across FIDO2 prompts, the Authenticator Home screen, and Vault detail screens. Replaced hardcoded heights with Material 3 idiomatic `contentPadding` and `shape.large`, improving accessibility and visual consistency.

### Fixed
- **Test Suite Compilation**: Resolved compilation errors in `PasskeyCredentialDaoTest` and `CredentialRepositoryImplTest` caused by the addition of the `credProtectPolicy` field to the credential schema.
- **Detailed changes**: [2026-04-02-fido2-hid-finalization-and-hdk-alignment.md](docs/changelogs/2026-04-02-fido2-hid-finalization-and-hdk-alignment.md)

## [Unreleased] - 2026-03-31

### Changed
- **HDK Index Migration to Unsigned Integers (UInt)**: Migrated the entire HDK derivation path logic from 31-bit signed integers to full 32-bit unsigned integers to ensure compliance with IETF `draft-dijkhuis-cfrg-hdkeys-06`. Updated `HdkManager`, `HdkEcdhP256`, and `HashToScalar` to handle `UInt` values, and refactored `Fido2CryptoService` to use the full 32-bit entropy domain for credential indices and spec-compliant alias formatting.
- **Detailed changes**: [2026-03-31-hdk-uint-index-migration.md](docs/changelogs/2026-03-31-hdk-uint-index-migration.md)

## [Unreleased] - 2026-03-30

### Added
- **Configurable FIDO2 Credential Limit (FR-HID-022, T115a)**: Migrated the FIDO2 credential storage limit from a hardcoded constant to a runtime-configurable system setting. Introduced `Fido2SettingsRepository` (interface) and `Fido2SettingsRepositoryImpl` (backed by `EncryptedSharedPreferences`), defaulting to **1000** credentials. `RegisterCredentialUseCase` now enforces the limit dynamically and `Fido2Exception.TooManyCredentials` exposes the `limit` as a public property for accurate error reporting.
- **Detailed changes**: [2026-03-30-fido2-configurable-credential-limit.md](docs/changelogs/2026-03-30-fido2-configurable-credential-limit.md)

### Fixed
- **HDK Seed Size Mismatch (Critical)**: Resolved a runtime crash that blocked all FIDO2/ES256 credential registrations. `WalletMasterSeedProvider` was passing the raw 64-byte BIP39 PBKDF2-SHA512 seed directly to `HdkEcdhP256.deriveHdk()`, which enforces exactly **32 bytes** per HDK spec §2.2 (`Ns = 32`). Fixed by truncating the BIP39 seed to its first 32 bytes for the HDK layer while retaining the full 64 bytes for device key pair derivation via HMAC-SHA512. Also resolved a `keyset not found` warning from `EncryptedSharedPreferences` on first launch (expected `AndroidKeysetManager` behaviour when auto-generating a new keyset).
- **Detailed changes**: [2026-03-30-hdk-seed-size-fix.md](docs/changelogs/2026-03-30-hdk-seed-size-fix.md)

---

### Fixed
- **HDK DeriveSalt Spec Alignment (T166, T172)**: Corrected a spec deviation in `HdkEcdhP256.kt` where the `ID` domain separator was incorrectly double-prepended during salt derivation. The implementation now strictly conforms to `draft-dijkhuis-cfrg-hdkeys-06` §2.4 (`H(salt || ctx)`). Added comprehensive Known Answer Tests (KATs) as a regression guard.
- **Detailed changes**: [2026-03-29-hdk-derivesalt-fix.md](docs/changelogs/2026-03-29-hdk-derivesalt-fix.md)

---

## [Unreleased] - 2026-03-26

### Added
- **FIDO2 Ed25519 (COSE -19) Support**: Implemented deterministic Ed25519 key derivation and signing using **BouncyCastle 1.80**. Keys are derived from the user's Master Seed, fulfilling the backup requirement (FR-AUTH-030) while bypassing Android KeyStore limitations on older API levels (28-32) and OS-level bugs on Android 14/15.
- **CBOR OKP Encoding**: Added native Octet Key Pair (OKP) encoding for Ed25519 public keys in `CborCodec.kt`.
- **FIDO2 Algorithm Selection (Dev Tools)**: Added a Segmented Button selector to `DevelopmentToolsScreen.kt` allowing developers to explicitly choose between ES256, Ed25519, and ML-DSA-65 algorithms for mock registration tests.
- **SQLDelight Migration (v4)**: Implemented database migration `4.sqm` to add the `coseAlgorithm` column to the `PasskeyCredential` table. This resolved a critical `NullPointerException` encountered on physical devices with existing installations.

### Changed
- **Algorithm Identifier Correction (ML-DSA-65)**: Corrected the COSE identifier for ML-DSA-65 from `-257` to the draft standard `-49` across `Fido2CryptoService`, `AttestationObject`, and `CborCodec` to resolve the collision with RS256.
- **Algorithm Identifier Refactor**: Replaced multiple instances of magic numbers (`-7`, `-257`) with domain-level constants in `PasskeyCredential` and `Fido2CryptoService`.
- **Namespace Cleanup**: Refined test files by removing redundant fully-qualified class names for `PostQuantumCrypto`, improving code cleanliness and maintainability.

### Fixed
- **Registration Failed UI Error**: Resolved the database schema mismatch that caused registration to fail during the credential saving phase on-device.
- **Passkey Validation Crash**: Fixed a critical crash in `RegisterCredentialUseCase` by updating `PasskeyCredential.validate()` to accept the Ed25519 (`-19`) and ML-DSA-65 (`-49`) identifiers.
- **Detailed changes**: [2026-03-26-fido2-algorithm-persistence-and-ux-refinements.md](docs/changelogs/2026-03-26-fido2-algorithm-persistence-and-ux-refinements.md) & [2026-03-26-fido2-ed25519-support.md](docs/changelogs/2026-03-26-fido2-ed25519-support.md)

---

## [Unreleased] - 2026-03-25

### Added
- **Post-Quantum Cryptography (ML-DSA) Support**: Replaced experimental ML-KEM/Kyber implementation with the NIST standardized ML-DSA-65 (FIPS 204) signature scheme using BouncyCastle v1.80.
- **BIP-85 Hierarchical PQ Seed Derivation**: Implemented `HMAC-SHA512` based cryptographically isolated hierarchical deterministic derivation for the post-quantum keys, ensuring compromise isolation from the classical ECDSA keys.
- **CTAP2 Algorithm Negotiation**: The authenticator now advertises support for `COSE -257` (ML-DSA-65) in `getInfo` and parses MakeCredential options to negotiate the highest priority supported algorithm.
- **FIDO2 Automated Stress Testing (T159a)**: Implemented a robust integration test suite (`Fido2StressTest.kt`) that validates the system's stability through 100 consecutive registration and authentication operations. Verified 100% success rate with real P-256 scalar math simulation.

### Changed
- **CredentialId Value Class Refactor**: Migrated `CredentialId` to a Kotlin-idiomatic `@JvmInline value class` with a `String` (Base64URL) backing field, significantly improving type safety and memory efficiency across all CTAP2 logic.
- **Hierarchical Key Derivation Path**: Refined the bitwise conversion logic in `Fido2CryptoService.derivePath()` to ensure consistent 31-bit positive integer indices for credential derivation.

### Fixed
- **GetAssertion Signature Failure After Restart (Critical)**: Fixed a bug where authentication always failed with `"Could not verify authentication signature"` after closing and reopening the app. Root cause: `generateDeviceKeyPair()` used `SecureRandom` on every cold start, producing a different device key each time. Since the signing formula is `sk_device × blindingFactor mod n`, any change in `sk_device` produces an unverifiable signature. Fixed by replacing the random call with a deterministic `HMAC-SHA512("chimali_device_key_v1", masterSeed)` derivation in `WalletMasterSeedProvider`.
- **Authentication Query Accuracy**: Resolved a bug in the integration tests where full origin RP IDs (e.g., `https://...`) stored in the repository were incorrectly queried using Hostnames, resulting in empty credential results.
- **Cross-module Compilation**: Fixed a pre-existing compile break in `RegisterCredentialUseCaseTest` caused by recent signature updates to the key generation API.
- **Full details**:
  - [2026-03-25-fido2-getassertion-restart-fix.md](docs/changelogs/2026-03-25-fido2-getassertion-restart-fix.md)
  - [2026-03-25-fido2-stress-testing-and-credentialid-refactor.md](docs/changelogs/2026-03-25-fido2-stress-testing-and-credentialid-refactor.md)
  - [2026-03-25-fido2-mldsa-pqc-integration.md](docs/changelogs/2026-03-25-fido2-mldsa-pqc-integration.md)

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

### Added
- **Dev Tools Seed Management**: Implemented a comprehensive suite of development tools for BIP39 master seed management (T146 series). Includes biometric-gated mnemonic viewing, QR code export, QR code scan import (CameraX + ML Kit), and manual 24-word recovery.
- **New Dependencies**: Integrated `qrose` for QR generation and ML Kit Barcode Scanning with CameraX for secure QR-based seed transfer in debug builds.

### Changed
- **SDK Target Migration**: Upgraded `compileSdk` and `targetSdk` to **API 35 (Android 15)** across all 13 modules to comply with Jetpack Compose 1.10.0 requirements and optimize for modern platform features.

### Fixed
- **QR Scanner Permissions**: Resolved a critical issue where the QR scanner failed to launch due to a missing `CAMERA` permission declaration in the Android Manifest.
- **Scanner UX**: Hardened the "Scan QR Code" button logic to handle pre-granted permissions gracefully and prevent silent launcher failures.
- Full details: [2026-03-14-fido2-dev-tools-seed-management-and-sdk-35.md](docs/changelogs/2026-03-14-fido2-dev-tools-seed-management-and-sdk-35.md)

## [Unreleased] - 2026-03-13

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
*Last Updated: 2026-05-06*
