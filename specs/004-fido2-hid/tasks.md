# Implementation Tasks: FIDO2 Virtual Authenticator via BluetoothHidDevice

**Branch**: `004-fido2-hid` | **Date**: 2026-03-01 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## Phase 1: Setup Tasks

**Goal**: Initialize project structure and dependencies for FIDO2 Virtual Authenticator feature

**Independent Test Criteria**: Project compiles successfully with all dependencies configured

- [X] T001 Create feature/fido2 module structure per implementation plan
- [X] T002 Add FIDO2 dependencies to feature module build.gradle.kts
- [X] T003 Add required permissions to AndroidManifest.xml
- [X] T004 Create Hilt module for FIDO2 dependency injection
- [X] T005 Configure SQLDelight database setup for credential storage
- [X] T006 Set up ProGuard rules for FIDO2 and crypto libraries
- [X] T007 Create base package structure for domain, data, presentation layers
- [X] T008 [P] Set up unit test structure with JUnit5 and MockK
- [X] T009 [P] Set up integration test structure with Compose UI Testing
- [X] T010 Verify project compilation and dependency resolution

## Phase 2: Foundational Tasks

**Goal**: Implement core infrastructure and shared components required by all user stories

**Independent Test Criteria**: Core infrastructure components compile and can be instantiated

- [X] T011 Create SQLDelight database schema for PasskeyCredential entity
- [X] T012 Create SQLDelight database schema for RelyingParty entity
- [X] T013 Create SQLDelight database schema for UserConsentRecord entity
- [X] T014 Create SQLDelight database schema for BluetoothHidSession entity
- [X] T015 Implement SQLCipher encryption wrapper for secure database access
- [X] T016 Create Android KeyStore wrapper for private key storage
- [X] T017 Implement hierarchical deterministic key derivation (HDK-ECDH-P256)
- [X] T017a [US1] Research and integrate ML-KEM/Kyber PQC library for quantum-resistant cryptography
- [X] T017b [US1] Implement PQC key generation and management alongside ECDSA
- [X] T017c [US1] Add PQC fallback logic for devices without quantum support
- [X] T018 Create CBOR encoding/decoding utilities for FIDO2 messages
- [X] T019 Implement memory zeroing utilities for sensitive data
- [X] T020 Create base Fido2Exception hierarchy for error handling
- [X] T021 [P] Implement unit tests for database schemas and migrations
- [X] T022 [P] Implement unit tests for KeyStore wrapper
- [X] T023 [P] Implement unit tests for crypto utilities
- [X] T023a [P] Implement unit tests for PQC operations
- [X] T024 Verify foundational components compile and pass unit tests

## Phase 3: User Story 1 - FIDO2 Registration (Priority: P1)

**Goal**: Enable users to register new passkey credentials using the device as a FIDO2 authenticator

**Independent Test Criteria**: Can complete FIDO2 registration flow end-to-end with test service

### Domain Layer Tasks
- [X] T025 [US1] Create PasskeyCredential domain model with validation
- [X] T026 [US1] Create RelyingParty domain model with validation
- [X] T027 [US1] Create UserConsentRecord domain model
- [X] T028 [US1] Create MakeCredentialOptions domain model
- [X] T029 [US1] Create AttestationObject domain model
- [X] T030 [US1] Create PublicKeyCredentialRpEntity domain model
- [X] T031 [US1] Create PublicKeyCredentialUserEntity domain model
- [X] T032 [US1] Create PublicKeyCredentialParameters domain model
- [X] T033 [US1] Create PublicKeyCredentialDescriptor domain model
- [X] T034 [US1] Define CredentialRepository interface for registration operations
- [X] T035 [US1] Define UserVerification interface for biometric/PIN consent
- [X] T036 [US1] Define Fido2Authenticator interface for registration
- [X] T037 [US1] Create RegisterCredential use case class
- [X] T038 [US1] Create GetUserConsent use case class
- [X] T039 [P] [US1] Implement unit tests for domain models
- [X] T040 [P] [US1] Implement unit tests for use cases

### Data Layer Tasks
- [X] T041 [US1] Implement CredentialRepository with SQLDelight
- [X] T042 [US1] Create PasskeyCredential DAO with SQLDelight queries
- [X] T043 [US1] Create RelyingParty DAO with SQLDelight queries
- [X] T044 [US1] Implement UserConsentRecord DAO with SQLDelight queries
- [X] T045 [US1] Create credential storage service with KeyStore integration
- [X] T046 [US1] Implement secure credential encryption/decryption
- [X] T047 [P] [US1] Implement unit tests for repository layer
- [X] T048 [P] [US1] Implement unit tests for DAOs

### Core Bluetooth Tasks
- [X] T049 [US1] Implement BluetoothHidDevice wrapper for HID profile
- [X] T050 [US1] Create HID report parser for CTAP2 messages
- [X] T051 [US1] Implement CTAP2 MakeCredential command handler
- [X] T052 [US1] Create CTAP2 response builder for attestation
- [X] T053 [US1] Implement Bluetooth HID transport layer
- [X] T054 [US1] Add connection state management for HID sessions
- [X] T055 [P] [US1] Implement unit tests for Bluetooth HID layer
- [X] T056 [P] [US1] Implement unit tests for CTAP2 protocol

### Core Crypto Tasks
- [X] T057 [US1] Implement ECDSA P-256 key pair generation
- [X] T058 [US1] Create attestation statement generator
- [X] T059 [US1] Implement client data hash generation
- [X] T060 [US1] Create signature generation for attestation
- [X] T061 [P] [US1] Implement unit tests for crypto operations

### Presentation Layer Tasks
- [X] T062 [US1] Create RegistrationPromptViewModel with MVI pattern
- [X] T063 [US1] Implement RegistrationPrompt Compose screen
- [X] T064 [US1] Create BiometricPrompt Compose component
- [X] T065 [US1] Implement PIN entry dialog Compose component
- [X] T066 [US1] Create registration progress indicator
- [X] T067 [US1] Add navigation for registration flow
- [X] T068 [P] [US1] Implement Compose UI tests for registration screen
- [X] T069 [P] [US1] Implement integration tests for registration flow

### Integration Tasks
- [X] T070 [US1] Wire up RegisterCredential use case with repository
- [X] T071 [US1] Connect registration UI with ViewModel and use cases
- [X] T072 [US1] Integrate biometric verification with registration flow
- [X] T073 [US1] Connect CTAP2 handler with Bluetooth HID transport
- [X] T074 [US1] Implement error handling for registration failures
- [X] T075 [US1] Add user consent logging for registration
- [X] T076 [P] [US1] Implement end-to-end integration tests for registration
- [X] T077 Verify registration story compiles and passes all tests

## Phase 4: User Story 2 - FIDO2 Authentication (Priority: P1)

**Goal**: Enable users to authenticate to services using stored passkey credentials

**Independent Test Criteria**: Can complete FIDO2 authentication flow end-to-end with test service

### Domain Layer Tasks
- [X] T078 [US2] Create GetAssertionOptions domain model
- [X] T079 [US2] Create AssertionObject domain model
- [X] T080 [US2] Create Authenticate use case class
- [X] T081 [US2] Create SelectCredential use case class
- [X] T082 [P] [US2] Implement unit tests for authentication use cases

### Data Layer Tasks
- [X] T083 [US2] Add credential lookup methods to repository
- [X] T084 [US2] Implement sign count update functionality
- [X] T085 [US2] Create credential selection query methods
- [X] T086 [P] [US2] Implement unit tests for authentication repository methods

### Core Bluetooth Tasks
- [X] T087 [US2] Implement CTAP2 GetAssertion command handler
- [X] T088 [US2] Create CTAP2 response builder for assertion
- [X] T089 [US2] Add credential selection support to CTAP2 layer
- [X] T090 [P] [US2] Implement unit tests for GetAssertion handler

### Core Crypto Tasks
- [X] T091 [US2] Implement assertion signature generation
- [X] T092 [US2] Create authenticator data builder for assertions
- [X] T093 [US2] Add user verification to assertion process
- [X] T094 [P] [US2] Implement unit tests for assertion crypto

### Presentation Layer Tasks
- [X] T095 [US2] Create AuthenticationPromptViewModel with MVI pattern
- [X] T096 [US2] Implement AuthenticationPrompt Compose screen
- [X] T097 [US2] Create CredentialSelectionDialog Compose component
- [X] T098 [US2] Add authentication progress indicator
- [X] T099 [P] [US2] Implement Compose UI tests for authentication screen
- [X] T100 [P] [US2] Implement integration tests for authentication flow

### Integration Tasks
- [X] T101 [US2] Wire up Authenticate use case with repository
- [X] T102 [US2] Connect authentication UI with ViewModel and use cases
- [X] T103 [US2] Integrate credential selection with authentication flow
- [X] T104 [US2] Implement error handling for authentication failures
- [X] T105 [US2] Add user consent logging for authentication
- [X] T106 [P] [US2] Implement end-to-end integration tests for authentication
- [X] T107 Verify authentication story compiles and passes all tests

## Phase 5: User Story 3 - Credential Management (Priority: P2)

**Goal**: Enable users to view and manage stored passkey credentials

**Independent Test Criteria**: Can list, view, and delete credentials through management interface

### Domain Layer Tasks
- [X] T108 [US3] Create GetAllCredentials use case class
- [X] T109 [US3] Create DeleteCredential use case class
- [X] T110 [US3] Create DeleteAllCredentials use case class
- [X] T111 [US3] Create ResetAuthenticator use case class
- [X] T112 [P] [US3] Implement unit tests for management use cases

### Data Layer Tasks
- [X] T113 [US3] Add credential enumeration to repository
- [X] T114 [US3] Implement secure credential deletion
- [X] T115 [US3] Add credential count tracking
- [X] T116 [P] [US3] Implement unit tests for management repository methods

### Core Bluetooth Tasks
- [X] T117 [US3] Implement CTAP2 CredentialManagement commands
- [X] T118 [US3] Add credential listing support to CTAP2 layer
- [x] T119 [P] [US3] Implement unit tests for credential management CTAP2

### Presentation Layer Tasks
- [X] T120 [US3] Create CredentialManagementViewModel with MVI pattern
- [X] T120a [US3] Implement unit test for CredentialManagementViewModel
- [X] T121 [US3] Implement CredentialListScreen Compose screen
- [X] T122 [US3] Create CredentialItem Compose component
- [X] T123 [US3] Implement DeleteConfirmationDialog Compose component
- [X] T124 [US3] Add credential details view screen
- [x] T125 [P] [US3] Implement Compose UI tests for credential management
- [x] T126 [P] [US3] Implement integration tests for management flow

### Integration Tasks
- [X] T127 [US3] Wire up management use cases with repository
- [X] T128 [US3] Connect management UI with ViewModel and use cases
- [X] T129 [US3] Implement error handling for management failures
- [X] T130 [US3] Add user consent logging for management operations
- [X] T131 [P] [US3] Implement end-to-end integration tests for management
- [X] T132 Verify management story compiles and passes all tests

## Phase 6: Polish & Cross-Cutting Concerns

**Goal**: Complete implementation with performance optimization, accessibility, and production readiness

**Independent Test Criteria**: All features work smoothly with performance targets met

### Performance Optimization Tasks
- [x] ~~T133 Implement performance monitoring for HID operations~~ (Delayed for future work)
- [x] T133a Create `ClipboardManagerService` to handle explicitly copying sensitive data and scheduling a 60-second coroutine delay to clear the clipboard
- [x] T133b Integrate `ClipboardManagerService` into `DevToolsViewModel` and any other UI elements that copy sensitive data
- [x] T133c [P] Implement clipboard security tests
- [x] ~~T134 Add memory leak detection and prevention~~ (Delayed for future work)
- [x] ~~T135 Optimize database queries for credential operations~~ (Delayed for future work)
- [x] T136 Implement background thread processing for crypto operations
- [x] ~~T137 Add caching for frequently accessed credentials~~ (Delayed for future work)
- [x] ~~T138 [P] Implement performance tests for all operations~~ (Delayed for future work)

### Accessibility Tasks
- [x] T139 Add TalkBack support to all Compose screens
- [x] ~~T140 Implement high-contrast theme support~~ (Delayed for future work)
- [x] ~~T141 Add dynamic text scaling support~~ (Delayed for future work)
- [x] T142 Use Atkinson Hyperlegible font for security text
- [x] T143 [P] Implement accessibility tests for all screens

### Security Hardening Tasks
- [x] ~~T144 Add certificate pinning for FIDO2 communications~~ (Dropped: Not Applicable)
- [x] T145 Implement rate limiting for PIN attempts
- [x] T145a Integrate `HdkManager` into `Fido2CryptoService` for credential derivation
- [x] T145b Migrate `RegisterCredentialUseCase` and `AuthenticateAssertionUseCase` to use derived keys
- [x] T145c Implement BIP39 Master Seed ingestion/derivation for FIDO2 root of trust *(refs: FR-HID-015, SC-006)*
- [x] T146 Add programmatic verification that mnemonic persists and derives successfully after restart (SC-006)
- [x] T146a [DEV] Create DevToolsViewModel to handle temporary mnemonic export/recovery flows
- [x] T146b [DEV] Implement temporary DevToolsScreen Compose UI for Developer options
- [x] T146c [DEV] Add "View Master Seed" flow guarded by biometric authentication (clears from memory on exit)
- [x] T146d [DEV] Add "Recover from Seed" flow for testing mnemonic ingestion on device wipe
- [x] T146e [DEV] Add "Show QR Code" button to the View Master Seed flow (biometric-gated; cleared on navigate away)
- [x] T146f [DEV] Add "Scan QR Code" button to the Recover flow using CameraX + ML Kit barcode scanning
- [x] T146g Implement persistence of recovered mnemonic via `MasterSeedProvider`: add `importMnemonic(mnemonic: CharArray)` to the interface; implementation must (a) validate 24-word count before writing, (b) warn caller if a mnemonic already exists and default to overwrite/replace, (c) persist to `EncryptedSharedPreferences`, (d) explicitly zero the `CharArray` after use, (e) invalidate `cachedSeed` / `cachedDeviceKeyPair` so the next call re-derives from the new seed; connect to `DevToolsViewModel.recoverFromSeed` resolving the `T146-future` TODO *(refs: FR-HID-015, SC-006)*
- [x] T146g-p [P] Unit tests for `importMnemonic`: overwrite-existing path, wrong-word-count validation failure, cache invalidation (subsequent `getMasterSeed` returns re-derived seed), no-prior-seed path
- [x] ~~T147 Implement audit logging for security events~~ (Removed: Underspecified and potential privacy risk, deferred to future phase)
- [x] T148 [P] Implement security tests for security-critical components
  - [x] T148a [P] Memory zeroing tests: verify `CharArray.fill('\u0000')` clears mnemonic buffers before GC (Constitution §I)
  - [x] T148b [P] Crypto KAT (Known Answer Tests): verify same 24-word seed always derives the same public key pair (Constitution §II, SC-006)
  - [x] T148c [P] Biometric lockout response tests: verify ViewModel correctly handles `ERROR_LOCKOUT` and `ERROR_LOCKOUT_PERMANENT` callbacks from `BiometricPrompt` (shows correct error UI, clears sensitive state, does not retry) — *Note: rate-limiting itself is enforced by Android OS/TEE, not app code*
  - [x] T148d [P] Storage integrity tests: verify SQLCipher database file is not readable as plain-text after creation (FR-HID-015)

### Error Handling & Logging Tasks
- [x] T149 Add comprehensive FIDO2 protocol error reporting 
- [x] T150 Implement local-only crash reporting mechanism (no cloud sync)
- [x] T151 Add privacy-safe debug logging (excluding sensitive data)
- [x] T152 Create user-friendly error messages for connection issues
- [x] T153 [P] Implement error handling tests

### Documentation & Deployment Tasks
- [x] ~~T154 Update API documentation with examples~~ (Delayed for future work)
- [x] ~~T155 Create user guide for FIDO2 setup~~ (Delayed for future work)
- [x] ~~T156 Add troubleshooting documentation~~ (Delayed for future work)
- [x] ~~T157 Prepare release notes and changelog~~ (Delayed for future work)
- [x] ~~T158 [P] Implement documentation tests~~ (Delayed for future work)

### Final Integration & Verification Tasks
- [x] T159 Run full integration test suite to verify end-to-end functionality (Registration, Authentication, Credential Management). Ensure 100% pass rate across all feature:fido2 tests.
- [X] T159a [P] Implement automated stress test / simulated loop to execute 100 consecutive FIDO2 operations to verify 95% success rate without state corruption (SC-004)
- [x] ~~T160 Verify performance targets are met~~ (Delayed for future work)
- [x] T160a [P] Implement minimal HID latency profiler (NFR-PERF-030): `LatencyProfiler` utility + Logcat instrumentation in transport and presentation layers to track end-to-end performance excluding user interaction time. Log outputs must strictly sanitize sensitive cryptographic state/credential IDs and avoid cloud telemetry uploads.
  - [x] T160a-1 Implement `LatencyProfiler` utility.
  - [x] T160a-2 Add Logcat instrumentation in `BluetoothHidTransportImpl` and `Fido2CryptoService`.
- [ ] T161 Test on multiple Android devices
- [ ] T162 Validate FIDO2 compliance with test tools
- [x] T162a [P] Ensure algorithm consistency (Ed25519 & ML-DSA-65) across CryptoService and CTAP2 handlers
- [x] T163 Verify BRD requirements compliance (refs: [FR-HID-010-brd-compliance.md](checklists/FR-HID-010-brd-compliance.md))
- [x] T164 Final compilation check and code review
- [x] T165 Prepare feature for merge to main branch

## Phase 7: Hierarchical Key Derivation Refactor

**Goal**: Audit and align the CFRG HDK-ECDH-P256 implementation in `core/security` against
`draft-dijkhuis-cfrg-hdkeys-06` and remove all remaining BIP-32-style artifacts from the codebase.
The FIDO2 main derivation path (used by `Fido2CryptoService`) is already correct; this phase
targets correctness gaps, documentation debt, and test coverage.

**Independent Test Criteria**: `HdkEcdhP256Test` and `Fido2CryptoServiceTest` pass with KAT
vectors; no BIP-32 symbols or comments remain in the HDK layer; `derivePqChildSeed` is
documented as intentionally isolated from the HDK spec.

---

### Correctness & Spec Alignment

- [x] T166 **[BUG][PRIORITY] Fix `DeriveSalt` spec deviation in `HdkEcdhP256`.** ✅ CLOSED
  `deriveSalt` now correctly implements `H(salt || ctx)` per §2.4. The `ID` domain
  separator is already embedded in `ctx` (§2.3: `ctx = ID || I2OSP(index, 4)`) and
  is not prepended again. KDoc updated to reflect the conformant behaviour.
  *(fixed 2026-03-29; refs: HdkEcdhP256.kt:47–61)*

- [x] T167 **Verify `DST` value against the HDK-ECDH-P256 concrete instantiation (§4.1).** ✅ CLOSED
  `MultiplicativeBlinding.DST = "ECDH Key Blind"` has been verified conformant with §4.1 of
  `draft-dijkhuis-cfrg-hdkeys-06`. No change required. *(verified 2026-03-28; refs: MultiplicativeBlinding.kt:17)*

- [x] T168 **Verify `ID` constant alignment with §4.1 for `CreateContext`.** ✅ CLOSED
  **Verified (2026-03-29):** §4.1 of `draft-dijkhuis-cfrg-hdkeys-06` only defines `DST = "ECDH Key Blind"` for
  the HDK-ECDH-P256 instantiation — it does **not** prescribe an `ID` value. The `ID` constant is an
  application-level label used in `CreateContext` (§2.3). The value `"HDK-ECDH-P256-v1"` is a stable,
  self-describing choice and is conformant. KDoc updated to accurately reflect this: the old comment
  `"Instance ID as defined in the spec"` has been replaced with a full verification note.
  *(refs: HdkEcdhP256.kt:25–44)*

- [x] T169 **Audit `FIDO2_APP_INDEX` comment — remove BIP-32/BIP-32 namespace framing.** ✅ CLOSED
  Replaced the inaccurate `"BIP32-style namespace index"` KDoc with an HDK-spec aligned description:
  explains the two-level derivation path role per §2.3, the `"FID2"` ASCII encoding rationale,
  the 31-bit constraint, and an explicit note that this index has no BIP-32 semantics.
  *(fixed 2026-03-29; refs: Fido2CryptoService.kt:478–491)*

- [x] T170 **Document `getPqChildSeed` BIP-32 derivation as an intentional, isolated branch.** ✅ CLOSED
  Added full KDoc to `derivePqChildSeed` and `ckdHard` in `WalletMasterSeedProvider.kt`:
  - Explicitly states BIP-32 CKD is **intentional isolation** — PQ branch output never feeds into `HdkManager.deriveHdk`
  - Documents that ECDSA and ML-DSA branches are cryptographically isolated
  - Clarifies `ckdHard` is a **BIP-85 compatibility tool**, not part of the HDK spec
  - Expands `ckdHard` KDoc with a cross-reference to `derivePqChildSeed` for rationale
  *(fixed 2026-03-29; refs: WalletMasterSeedProvider.kt:211–265)*

- [x] T171 **Audit `Ed25519` branch in `Fido2CryptoService` for HDK alignment.** ✅ CLOSED
  **Audit result**: The Ed25519 branch is **conformant** — intentionally isolated from `HdkManager.deriveHdk`
  because Ed25519 (Curve25519) is incompatible with P-256 multiplicative blinding (§3.2.2 of
  `draft-dijkhuis-cfrg-hdkeys-06`). Decision: direct `SHA-512(masterSeed || "Ed25519" || credentialId)[0..31]`
  is acceptable. Derivation is deterministic (FR-AUTH-030) and private key is zeroed at both call sites.
  **Documentation added**: algorithm routing table in `generateCredentialKeyPair` KDoc; isolation comments
  at key-generation and signing sites. *(fixed 2026-03-29; refs: Fido2CryptoService.kt:84–130, 338–362)*

---

### Test Coverage (KATs & Regression)

- [x] T172 **[P] Add `DeriveSalt` Known Answer Tests to `HdkEcdhP256Test`.** ✅ CLOSED
  Four KATs added (index=0 match, index=1 match, output length=32, different indices → different outputs).
  Comment updated to reflect T166 is applied and tests are GREEN. *(fixed 2026-03-29; refs: HdkEcdhP256Test.kt:173–249)*

- [ ] T173 **[P] Add end-to-end HDK KATs for the two-level FIDO2 path.**
  Using a fixed seed and `credentialId`, assert that `Fido2CryptoService.generateCredentialKeyPair`
  returns a deterministic, known public key. This guards against derivation regressions across
  refactors. *(refs: Fido2CryptoServiceTest.kt)*

- [ ] T174 **[P] Add `BlindPublicKey` / `BlindPrivateKey` consistency KATs.**
  For fixed `(sk, pk, bf)`, assert that `ScalarBaseMult(BlindPrivateKey(sk, bf))` equals
  `BlindPublicKey(pk, bk, ctx)` with deterministic `bk`/`ctx` inputs derived from the KAT seed.
  *(refs: MultiplicativeBlindingTest.kt)*

- [ ] T175 **[P] Add `createContext` boundary tests (index=0, index=2^32-1).**
  Verify that the I2OSP(index, 4) encoding is correct at boundary values. *(refs: HdkEcdhP256Test.kt)*

---

### Documentation & Cleanup

- [ ] T176 **Update `HdkManager` KDoc to reference §2.5–2.6 of `draft-dijkhuis-cfrg-hdkeys-06`.**
  Add section references alongside each function (`generateSeed` → §2.6, `deriveHdk` → §2.5,
  `blindPrivateKey` → §3.2.2, `createBlindedSharedSecret` → §3.3.1). *(refs: HdkManager.kt)*

- [ ] T177 **Add `HdkKeyAlias` utility or documentation for §2.8 key alias format.**
  The spec defines a canonical `hdk-key-alias` format (`origin-alias "/" path`). Evaluate
  whether credential aliases in `Fido2CryptoService.credentialAlias` should conform to this
  format and document the decision. *(refs: Fido2CryptoService.kt:482–483)*

- [ ] T178 **Ensure `blinded private key` is never persisted — add code assertion.**
  Per §2.5: *"A unit MUST NOT persist a blinded private key."* Add an explicit runtime assertion
  or developer warning if `blindPrivateKey` output is inadvertently stored (currently Logcat
  logging and zero-fill are in place but no guard exists). *(refs: Fido2CryptoService.kt:376–388)*

- [ ] T179 **Decide and enforce uint32 index domain for HDK paths (API/docs/tests).**
  Resolve the current mismatch where public APIs accept signed `Int` indices but the draft index
  domain is full 32-bit unsigned. Choose one:
  - **Conformant**: migrate HDK path/index inputs to `UInt` (or `Long` with strict bounds) and
    enforce \(0 \le index \le 2^{32}-1\) with `I2OSP(index, 4)` encoding.
  - **Profiled restriction**: explicitly restrict to 31-bit non-negative indices and document
    the conformance delta (and why it is safe for this application).
  **Acceptance criteria**:
  - Update KDoc/spec mapping in `HdkManager` and `HdkEcdhP256` to state the chosen domain and
    encoding behaviour (including error behaviour for out-of-domain inputs).
  - Add tests covering boundary values (0, max allowed) and rejection of out-of-domain values
    (negative, and either \(2^{31}\) / \(2^{32}\) depending on the decision).
  - Ensure callers in `Fido2CryptoService` use the chosen domain consistently (no silent
    truncation/sign-extension). *(refs: HdkManager.kt, HdkEcdhP256.kt, Fido2CryptoService.kt)*

## Dependencies

### Story Completion Order
1. **Phase 1** (Setup) → **Phase 2** (Foundational) → **Phase 3** (US1: Registration) → **Phase 4** (US2: Authentication) → **Phase 5** (US3: Management) → **Phase 6** (Polish)

### Critical Path Dependencies
- T001-T010 must complete before any other tasks
- T011-T024 must complete before any user story tasks
- T077 must complete before Phase 4 tasks
- T107 must complete before Phase 5 tasks
- T132 must complete before Phase 6 tasks

## Parallel Execution Opportunities

### Within Phase 1 (Setup)
- T002, T003, T004 can be done in parallel
- T008, T009 can be done in parallel with setup tasks

### Within Phase 2 (Foundational)
- T011-T014 can be done in parallel (different entities)
- T021-T024 can be done in parallel with implementation
- T016, T017, T018 can be done in parallel

### Within User Story Phases
- Domain model tasks (T025-T040) can be done in parallel with data layer setup
- UI tasks (T062-T069) can be done in parallel with backend implementation
- Unit tests (T039, T040, T047, T048) can be done in parallel with implementation

### Cross-Story Parallelism
- Phase 4 (Authentication) core tasks can start once Phase 3 registration infrastructure is stable
- Phase 5 (Management) can be partially implemented in parallel with Phase 4 authentication UI work

## Implementation Strategy

### MVP Scope (Phase 1-3)
Focus on User Story 1 (FIDO2 Registration) to deliver minimum viable product:
- Complete T001-T077 for basic registration functionality
- Ensure registration flow works with at least one test service
- Target: 2-3 week development cycle

### Incremental Delivery
1. **Sprint 1**: Setup + Foundational + Registration (T001-T077)
2. **Sprint 2**: Authentication (T078-T107) 
3. **Sprint 3**: Management (T108-T132)
4. **Sprint 4**: Polish & Production Readiness (T133-T165)

### Risk Mitigation
- Start with Bluetooth HID implementation (highest technical risk)
- Implement comprehensive unit tests before integration
- Use reference implementations as validation baseline
- Test on multiple Android versions early

## Compilation Verification

Every task includes specific file paths and clear completion criteria to ensure the project compiles at each checkpoint:
- **Phase 1**: Basic project structure and dependencies
- **Phase 2**: Core infrastructure components
- **Phase 3-5**: Each user story independently compilable
- **Phase 6**: Full feature compilation and optimization

## Total Task Count

**Summary**: 168 total tasks
- **Setup**: 10 tasks (T001-T010)
- **Foundational**: 14 tasks (T011-T024)
- **User Story 1**: 53 tasks (T025-T077)
- **User Story 2**: 30 tasks (T078-T107)
- **User Story 3**: 25 tasks (T108-T132)
- **Polish**: 36 tasks (T133-T165, includes T145a-c)

**Parallel Tasks**: 42 tasks marked with [P] for parallel execution
**Independent Test Criteria**: Each phase has clear verification requirements
**MVP Focus**: First 77 tasks deliver core registration functionality
