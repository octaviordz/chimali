# Technical Requirements Document (TRD) - Chimali

**Project Name**: Chimali  
**Version**: 0.2.0  
**Status**: Approved  
**Reference Document**: [BRD](brd.md)

---

## 1. Executive Summary (Technical Context)
This Technical Requirements Document (TRD) translates the business goals outlined in the [BRD](brd.md) into actionable technical specifications. While the BRD focuses on the "what" and "why," the TRD focuses on the "how" and the specific technical constraints for implementation.

## 2. Project Overview (Architectural)
Chimali follows a **Clean Architecture** pattern with **Unidirectional Data Flow (UDF)** and **MVI (Model-View-Intent)** across three primary layers:

- **UI / Presentation Layer** (Jetpack Compose): Renders state and emits user intents. MUST contain no business logic. State is consumed as `StateFlow`; intents are dispatched to ViewModels.
- **Domain Layer** (Aggregates, Services, Use Cases): Pure Kotlin business rules, invariants, and workflows. MUST remain independent of presentation frameworks and persistence mechanisms.
- **Data Layer** (Repositories, Event Store): The authoritative Single Source of Truth (SSOT). Exposes reactive streams (`Flow` / `StateFlow`) upward.

Data and state flow UP (`Data → Domain → UI`); events, intents, and actions flow DOWN (`UI → Domain → Data`). Neither UI nor Domain independently mutates or duplicates Data-held state.

### 2.1 Feature Module Architecture & Dependency Rules
All Gradle project modules within a feature or core library adhere to strict ownership boundaries and unidirectional dependency flow:

- **Models** (`:core:model`, `:feature:{name}:model`):
  Immutable Kotlin data classes, value classes, events, and domain primitives.
  - *Responsibilities*: Define public data structures, contracts, and schema primitives.
  - *Permitted dependencies*: Kotlin Standard Library only.
  - *Prohibited dependencies*: Data, Database, Domain, App/DI, UI, or Android framework SDKs.

- **Common** (`:core:common`, `:feature:{name}:common`):
  Platform-neutral technical utilities, algorithms, and helpers (hashing, canonicalization, mathematical primitives).
  - *Responsibilities*: Reusable, stateless algorithmic logic and low-level cross-cutting tools.
  - *Permitted dependencies*: Models.
  - *Prohibited dependencies*: Data, Database, Domain, App/DI, UI.

- **Data** (`:core:data`, `:feature:{name}:data`):
  Data source and repository abstractions, persistence interfaces, and Event Sourcing contracts.
  - *Responsibilities*: Define abstractions for reading and writing feature data without binding to concrete storage.
  - *Permitted dependencies*: Models, Common.
  - *Prohibited dependencies*: Concrete storage (`Database`), Domain, App/DI, UI. Data contracts MUST remain persistence-technology-agnostic.

- **Database** (`:core:database`, `:feature:{name}:database`):
  Concrete storage implementations using SQLDelight and SQLite3MultipleCiphers.
  - *Responsibilities*: Implement persistence mechanisms, execute Event Sourcing appends, manage transactions, and map storage entities to Models.
  - *Permitted dependencies*: Data, Models, Common.
  - *Prohibited dependencies*: Domain, App/DI, UI.

- **Domain** (`:core:domain`, `:feature:{name}:domain`):
  Core business logic, use cases, domain services, Event Sourcing `Decider` mechanisms, and validation rules.
  - *Responsibilities*: Encapsulate domain algorithms, business invariants, and application workflows.
  - *Permitted dependencies*: Models, Common, Data abstractions (interfaces only).
  - *Prohibited dependencies*: Concrete storage (`Database`), App/DI, UI. Domain logic MUST remain independent of persistence mechanisms and presentation frameworks.

- **UI / Presentation** (`:feature:{name}:ui`):
  Jetpack Compose screens, MVI ViewModels, and interaction handling.
  - *Responsibilities*: Render state, handle user intents, and delegate business workflows to Domain.
  - *Permitted dependencies*: Public contracts from Domain and Models. May depend on Data abstractions only if strictly necessary.
  - *Prohibited dependencies*: Concrete `Database` storage, feature-internal business rules, persistence logic, or hardware workflows.

- **App & DI (Composition Root)** (`:app`):
  The integration boundary, composition root, and Koin service-registration layer.
  - *Responsibilities*: Compose concrete implementations with abstractions, register dependency injection, and adapt host platform lifecycles.
  - *Permitted dependencies*: All layers (Domain, Data, Database, Common, Models, UI).
  - *Prohibited dependencies*: Must NOT be referenced by any other module.

Dependency flow points toward stable contracts: `UI → Domain → Data → Models`, `Database → Data → Models`, and `Common → Models`. Only `:app` binds implementations via Koin. Circular dependencies and direct UI-to-storage dependencies are strictly forbidden.

### 2.2 Module Criticality Classification (Tier 1 & Tier 2)
Modules across the project are categorized into two architectural criticality tiers:

- **Tier 1 (Critical Modules)**: Modules where a defect can directly compromise security, cryptographic operations, data integrity, or authentication correctness:
  - `:core:security` — cryptographic operations, key derivation, memory zeroing
  - `:core:domain` — aggregates, domain services, Event Sourcing `Decider` mechanisms
  - `:core:data` — repository contracts and data integrity rules
  - `:feature:fido2` — CTAP2 protocol implementation and passkey flows
  - `:feature:authenticator` — Bluetooth HID authentication orchestration
  *Requirements*: Full DO-178B-inspired traceability (`@Req`), forbidden dead code, bounded execution, and high test coverage gates (>= 40%).
- **Tier 2 (Feature & Infrastructure Modules)**: All other modules (`:core:bluetooth`, `:core:crdt`, `:core:database`, `:core:common`, `:core:ui`, `:feature:vault`, `:feature:editor`, `:feature:settings`, `:feature:onboarding`, `:app`).
  *Requirements*: Root `README.md` requirement mapping (`FR-*`, `NFR-*`) and baseline test coverage (>= 20%).

### 2.3 Event Sourcing Technical Architecture
Event Sourcing serves as the primary persistence and audit mechanism for core aggregates (`VaultEntry`, `PasskeyCredential`):
- **Immutable History**: Events are appended only; historical pruning is strictly prohibited (**Perpetual History**).
- **Deterministic Reconstruction**: A `Decider` mechanism deterministically reconstructs entity state (`Model`) by folding an ordered event stream.
- **Temporal Queries (Time Travel)**: Timestamp-based interface (`asOf(timestamp)`) reconstructs entity state at any point in history. Every reconstruction generates structured JSON trace logs for audits.
- **Snapshot Acceleration**: Snapshots captured every 20 events for hydration performance, falling back to full fold if missing or corrupt.
- **Schema Evolution**: Schema changes are strictly additive with default values. Optimistic concurrency with unique sequence numbers and automated retry on conflict.

### 2.4 Predictable Lifecycles & Memory Management
- **Configuration Changes & Process Death**: ViewModels survive configuration changes. UI state surviving process death is preserved via `SavedStateHandle`. Sensitive cryptographic keys/seeds MUST NOT be stored in `SavedStateHandle` or `onSaveInstanceState`; they MUST be re-derived or re-prompted upon restoration.
- **Memory Pressure Response**: All custom caches, image loaders, and in-memory managers implement `ComponentCallbacks2.onTrimMemory()`. Upon receiving `TRIM_MEMORY_RUNNING_CRITICAL`, `TRIM_MEMORY_BACKGROUND`, or `TRIM_MEMORY_MODERATE`, components clear 100% of non-essential volatile caches and issue `System.gc()` release hints. Unit tests must verify complete cache flushing on trim events.

## 3. Business Goals & Objectives (Technical Translation)
- **Security Leadership**: Integration of Bouncy Castle 1.80+ for the current local credential profile and ML-DSA-65 signing. ML-KEM or remote key provisioning is not part of the current credential profile; any issuer workflow remains a separate research item.
- **Cross-Platform Utility**: Custom HID descriptors to ensure compatibility across OS types via `BluetoothHidDevice`.
- **Modern UX**: Use of `androidx.compose.animation` for micro-interactions and M3 dynamic color tokens.

## 4. Functional Requirements (Technical Specifications)
### 4.1 Core Credential Management
#### [FR-VAULT-010] Secure Storage
- **Technical Detail**: Use **SQLDelight** for structured data. Sensitive fields (passwords, notes) must be encrypted before insertion using the encryption strategy defined in NFR-SEC-010.
- **Key Derivation**: Individual entry keys derived from the Master Seed using HKDF-SHA256.

#### [FR-VAULT-020] Smart Categorization
- **Technical Detail**: Integration with **ML Kit** for on-device text classification. Fallback to a predefined regex-based mapping (e.g., `*google.com` -> "Social") when ML is unavailable.

#### [FR-VAULT-030] Smart Contextualization
- **Technical Detail**: Use `com.google.android.gms.location.GeofencingClient`. Geofences are managed locally; no location data is sent off-device.

### 4.2 Virtual Authenticator & Bluetooth HID
#### [FR-HID-010] FIDO2 Authenticator over HID
- **Technical Detail**: Implement `BluetoothHidDevice.Callback`. 
- **HID Report Descriptor**: Use a custom FIDO2 report descriptor (see [FIDO2 HID Specification](https://fidoalliance.org/specs/fido-v2.3-ps-20260226/fido-client-to-authenticator-protocol-v2.3-ps-20260226.html#usb-hid-transport)).
- **CTAP2 Layer**: Implement a state machine to handle `CTAP2_GET_INFO`, `CTAP2_MAKE_CREDENTIAL`, and `CTAP2_GET_ASSERTION` commands.
- **Supported Public Key Algorithms**: ES256, Ed25519, ML-DSA-65
- **COSE Algorithm Mapping**: Algorithm identifiers (`alg`) must follow the [IANA COSE Registry](https://www.iana.org/assignments/cose/cose.xhtml#algorithms) (e.g., -7 for ES256, -257 for RS256, -49 for ML-DSA-65).

#### [FR-HID-020-030] Connection Management & Confirmations
- **Technical Detail**: Support for multiple bonded devices via `BluetoothAdapter.getBondedDevices()`. Authentication "confirmations" triggered via Local Notifications or Heads-Up display.
- **Integration Verification**: End-to-end Bluetooth relying-party lifecycle and failure handling are specified in [§9.4 Dual Credential Integration Lifecycle & Failure Matrices](#94-dual-credential-integration-lifecycle--failure-matrices).

### 4.3 Passkey Support
#### [FR-PASS-010] Android Credential Manager
- **Technical Detail**: Use `androidx.credentials:credentials` library. Handle `CreatePublicKeyCredentialRequest` and `GetPublicKeyCredentialRequest`.
- **Integration Verification**: Android Credential Manager lifecycle and failure handling are specified in [§9.4 Dual Credential Integration Lifecycle & Failure Matrices](#94-dual-credential-integration-lifecycle--failure-matrices).

### 4.4 Authentication & Security
#### [FR-AUTH-030] Master Seed & Recovery

#### [TR-AUTH-031] Current Local Credential Derivation Profile
- Keep the exact byte and algorithm contract in `specs/055-migrate-hdk-draft/contracts/local-key-derivation.md` synchronized with code and independently checked fixtures.
- Bind complete decoded credential IDs; carry algorithm/profile metadata through registration, event replay, snapshots, SQL projections and assertion reconstruction.
- Preserve independent Ed25519 and ML-DSA seed paths. Reject unsupported combinations without fallback.
- Scope compatibility to clean installations as authorized for feature 055; do not imply old-key conversion or issuer interoperability.
- **Technical Detail**: 
  - **Mnemonic**: BIP39 (12/24 words).
  - **Entropy**: Derived from `SecureRandom`.
  - **Mnemonic-to-Seed Stretching**: BIP-39 PBKDF2(HMAC-SHA512, 2048 iterations).
  - **Current derivation (TR-AUTH-031)**: Use the 64-byte BIP39 seed as HKDF-SHA256 input to the named local profile and retain its 32-byte PRK as the derivation root. The P-256 branch binds the complete decoded credential ID, derives the deterministic P-256 parent with RFC 9180 §7.1.3, and adapts the scalar mapping and child-key relation in Section 7 of `draft-dijkhuis-hdk-00` at local index zero, substituting a local RFC 5869 credential secret for the draft's per-issuance X25519 shared secret. This is custom local composition, not the draft's issuance flow or an interoperability claim. Ed25519 and ML-DSA use separate 32-byte HKDF seed labels. Persist the explicit supported profile with the credential's algorithm; reject unknown pairs. Store no derived private key or seed. Parent signing is software-based and requires the existing authorization policy; encrypted recovery storage does not make the parent non-exportable. Recovery requires the mnemonic and credential metadata.

## 5. Non-Functional Requirements (Technical Standards)
### 5.1 Encryption Standards (NFR-SEC-010)
- **Classical**: AES-256-GCM (Hardware accelerated) for payloads and encrypted metadata values.
- **Searchable Metadata**: HMAC-SHA-256 blind indexes for deterministic exact-match lookup tokens.
- **Database File Encryption**: SQLite3MultipleCiphers configured with authenticated encryption, using a 256-bit key derived via `PBKDF2-HMAC-SHA512` from the master key.
- **Key Wrapping**: Platform-backed AES-GCM/AEAD with unique nonces and associated data.
- **Canonical Security Registers**: Sensitive data fields, volatile memory ownership, and audited platform UI text boundaries are governed by [Data Classification Register](security/data-classification.md) and [Platform Text Boundary Register](security/platform-text-boundaries.md). Historical security reviews from 2026-09-09 (Vault ownership remediation) and 2026-10-14..16 (feature-055 local key derivation) retain their original dated scopes and commit fingerprints without asserting current-source certification.
- **Post-Quantum KEM**: No KEM-based remote provisioning capability is defined by the current local credential profile. Any future issuer or provisioning design remains subject to the separate [OpenID4VCI HDK issuance investigation](backlog/openid4vci-hdk-issuance-investigation.md); this document does not claim ML-KEM support or readiness.

### 5.2 Signature Schemes (NFR-SEC-040)
- **ECC**: P-256 (COSE -7) for wide compatibility.
- **PQC**: ML-DSA-65 (COSE -49) for quantum resistance.

## 6. User Interface & Experience (UI/UX Implementation)
- **M3 Integration**: Use `MaterialTheme` color schemes derived from `dynamicLightColorScheme` or `dynamicDarkColorScheme`.
- **Accessibility (Semantics & Interaction Targets)**: Interactive elements MUST provide meaningful accessible names, appropriate accessibility roles, available actions, relevant states, and semantic grouping in the Compose semantics tree. Controls with visible text labels or built-in accessible semantics do NOT require separate `contentDescription` attributes; intentionally decorative graphics MUST NOT have content descriptions. Touch targets MUST meet the 48dp minimum, and credential displays MUST use high-legibility fonts (e.g., Atkinson Hyperlegible). Full high-contrast and dynamic text scaling verification criteria and screen-reader walkthrough procedures are specified in [§9.5](#95-universal-accessibility-verification-procedures), preserving the governing `FR-UI-011` implementation deferral.
- **TR-UI-040: FIDO Passkey Icon Integration**: Implement the official FIDO Passkey Icon (glyph) as a high-fidelity vector resource. All generic security/lock icons in the FIDO2 presentation module must be transitioned to this standardized glyph.
- **TR-UI-050: Relying Party Icon Loading**: Prompts must support display of the RP icon/favicon. Implement using an asynchronous image loading library (e.g., **Coil**) with an automatic fallback to `https://{rpId}/favicon.ico` if the request doesn't provide a specific icon URL.
- **TR-UI-060: Multi-Transport UI Context**: Prompt ViewModels must determine the transport (Local/Credential Manager vs. Remote/HID) to display the correct contextual header (e.g. "Create a passkey on another device" vs. "Create a passkey for this device").
- **TR-UI-070: Standardized Button Styling Implementation**: All primary and secondary action buttons in the FIDO2 and Vault modules must be implemented using Material 3 `shape.large` (16dp rounded corners) and a minimum vertical `contentPadding` of 16dp. This ensures global visual consistency and high-density touch targets compliant with NFR-PERF-030 accessibility guidelines.
- **TR-PERF-010: Performance Monitoring Reporting**: Provide a local automated report for the RP-to-verified-response round trip (<1,000ms after subtracting measured user-verification wait) and all internal FIDO operation processing (<200ms each after subtracting measured user-verification wait and excluding Bluetooth response handoff), with accepted-report status and no credential secrets or identifying data.
- **TR-UI-080 (superseded)**: The previous HDK remote-flow/KEM requirement is retired for the current local profile. No remote provisioning or issuer behavior is specified here.
- **TR-DOC-010: Help System Architecture**: User guidance must be maintained as internal static Markdown files, rendered via a specialized `MarkdownText` Composable to provide a localized, on-device help experience.
- **TR-DOC-020: Technical Documentation**: Full API documentation and cryptographic architectural notes (specifically for HDK derivation) must be maintained for developer reference.

## 7. Technical Constraints & Data Schema
### 7.1 Platform Constraints
- **Minimum SDK**: API 28 (Android 9.0) required for the `BluetoothHidDevice` profile.
- **Language**: Primary language Kotlin (100% for UI/Android layers). Languages producing native code (e.g., Rust) permitted for core cryptography, shared low-level logic, and Loro.dev CRDT integration via UniFFI.
- **Multiplatform Readiness**: KMP-ready module structure maintained to facilitate future expansion. Platform-specific code (`expect`/`actual`) minimized; domain and presentation logic maximized in `commonMain`.
- **Encrypted Persistence**: Standardized persistence using **SQLite3MultipleCiphers** and **SQLDelight**.
- **Hardware Profile**: Mandatory support for Bluetooth HID Device Profile for virtual authenticator operations.
- **Privacy Focus**: On-device AI only (ML Kit, Gemini Nano) for credential categorization; zero cloud processing of plaintext.

### 7.2 High-Level Schema
| Table | Key Technical Field | Description |
|-------|-------------------|-------------|
| `PasskeyCredential` | `coseAlgorithm` | Integer ID mapping to COSE standard (e.g., -49 for ML-DSA-65). |
| `VaultEntry` | `encryptedBlob` | AES-GCM encrypted JSON payload. |
| `RelyingParty` | `rpIdHash` | SHA-256 hash of the RP ID (used in CTAP2). |

## 8. Technical References (Specification Links)
- **CTAP 2.3**: [Official Specification](https://fidoalliance.org/specs/fido-v2.3-ps-20260226/fido-client-to-authenticator-protocol-v2.3-ps-20260226.html)
- **WebAuthn L3**: [W3C Recommendation](https://www.w3.org/TR/webauthn-3/)
- **COSE Algorithms**: [IANA Registry](https://www.iana.org/assignments/cose/cose.xhtml#algorithms)

## 9. Development & Verification Methodology
- **TDD Flow**: Prefer `commonTest` with `kotlin.test` for platform-neutral Kotlin behavior and Fake test doubles for repositories, services, and platform boundaries. Use `JUnit 5`, `MockK`, and `AndroidX Test` only when platform APIs, interaction verification, or instrumentation are the behavior under test.
- **Static Analysis**: `Detekt` for architectural rules and `Ktlint` for formatting.

### 9.1 Verification Evidence Schema & Status Taxonomy

All verification activities across automated testing, manual walkthroughs, performance benchmarks, and security assessments MUST conform to the shared future-evidence schema. Verification records MUST reside in version-controlled Markdown artifacts or structured test outputs linked from feature records.

#### Mandatory Evidence Record Fields
Every verification evidence entry MUST record all ten fields:
1. **`requirement`**: The exact stable requirement identifier(s) (`FR-*`, `NFR-*`, `TR-*`) satisfied or evaluated.
2. **`scope`**: The evaluated component, module boundary, screen, or end-to-end integration path.
3. **`revision`**: The exact Git commit SHA or tree revision under test.
4. **`named_procedure`**: The fully qualified test function, suite, benchmark class, or documented manual walkthrough procedure.
5. **`environment`**: Hardware device model, OS/API level, host OS, build variant (`release`/`debug`), compilation mode, and relevant hardware capabilities.
6. **`expected_observed_result`**: Explicit measurable criteria and the actual observed metric or output.
7. **`date`**: Execution date in ISO 8601 format (`YYYY-MM-DD`).
8. **`limitations`**: Documented test doubles, environment constraints, stubbed platform components, or known coverage gaps.
9. **`status`**: Current status adhering strictly to the taxonomy below.
10. **`artifact_link`**: Relative repository path to the verifiable test report, trace log, or benchmark results file.

> [!CAUTION]
> **Data Privacy & Secret Prohibition in Verification Records**: Verification records and test logs MUST NEVER record real secrets (private keys, master seeds, decrypted vault payloads, symmetric keys) or identifying user data (real usernames, biometric templates, real email addresses). Synthetic test fixtures and non-sensitive identifiers MUST be used exclusively.

#### Status Taxonomy & Semantic Separation
To prevent conflation between document readiness and live software verification, status values are strictly segregated into runtime and documentary domains:

- **Runtime Execution Statuses** (Apply ONLY to executed software tests, benchmarks, and device audits):
  - **`Passed`**: Verification procedure executed and strictly satisfied all defined criteria on the designated environment.
  - **`Failed`**: Verification procedure executed and failed to satisfy criteria, threw unexpected exceptions, or attempted an empty workload.
  - **`Deferred`**: Verification is explicitly postponed under a documented, approved project deferral (e.g., BRD `FR-UI-011` UI phase).
  - **`Unverified`**: Verification procedure is specified or planned, but has not yet executed or the required execution environment was unavailable.
- **Documentary Review Statuses** (Apply ONLY to governance packages, specifications, and review registers):
  - **`Satisfied`**: The document, policy, register, or matrix accurately fulfills all structural, consistency, and procedural requirements.
  - **`Needs correction`**: The documentation contains defects, omissions, contradictions, or missing required fields.
  - **`Pending approval`**: Complete documentary package submitted for formal engineering disposition and awaiting decision.

*Rule of Precedence*: A documentary status of `Satisfied` NEVER implies or substitutes for a runtime status of `Passed`. Unexecuted or failing technical obligations MUST remain `Unverified`, `Failed`, or `Deferred` in all runtime registers.

### 9.2 Verification Policy Framework & Five-Area Authority Map

In alignment with the Constitution Principle V verifiable acceptance policy (Constitution 4.1.0), verification procedures are systematically structured across five critical governance areas. In accordance with the constitutional precedence hierarchy (Constitution → BRD → TRD → Rules → Feature artifacts), the procedures defined in this section and engineering rules are operational mechanisms subordinate to constitutional principles.

| Governance Area | Constitutional Principle & Authority Baseline | Governing Product Requirement | TRD Verification Section | Engineering Rules Anchor | Register / Evidence Location |
| --- | --- | --- | --- | --- | --- |
| **1. Rendering Smoothness** | Principle VI (Android Vitals); Principle V (Verifiable Acceptance & Evidence) | BRD `NFR-PERF-020` (60 FPS, 0 frozen >700ms, <1% slow >16ms) | §9.6 Rendering Smoothness Contract | Rules §1 (Rendering test execution rules) | Feature evidence records; `follow-ups.md` (FUP-PERF-001) |
| **2. Validation & Negative Testing** | Principle I (Memory), III (Architecture), V (Rigor) | BRD §5.1, §9; core business logic integrity | §9.3 Validation Verification | Rules §1, §5.2 (`Outcome.Failure` vs internal assertions) | Feature test suites; `follow-ups.md` (FUP-SEC-002) |
| **3. Sensitive Data & Platform Boundaries** | Principle I (Zeroing & strict platform text boundary) | BRD `NFR-SEC-010`, `NFR-SEC-030` | §5.1 Cryptographic standards | Rules §5.5 (Cryptographic conventions) | `docs/security/data-classification.md`, `docs/security/platform-text-boundaries.md` |
| **4. Credential Integration** | Principle III, IV, V, VIII (FIDO2 & Passkeys) | BRD §4.2 (`FR-HID-010`), §4.3 (`FR-PASS-010`), §9 | §9.4 Dual Credential Integration Matrices | Rules §1 (Integration boundaries) | Feature evidence records; `follow-ups.md` (FUP-INT-001, FUP-INT-002) |
| **5. Universal Accessibility** | Principle VIII (Accessibility as a Security Feature) | BRD `FR-UI-010`, `FR-UI-011` (Deferred) | §6 (Corrected description rule), §9.5 Accessibility Matrix | Rules §1, §5.4 (UI Compose testing) | Feature evidence records; `follow-ups.md` (FUP-ACC-001) |

> [!IMPORTANT]
> **Separation of Policy Delivery from Runtime Product Compliance**: The inclusion of verification schemas, lifecycle matrices, and measurement contracts in this technical specification establishes mandatory future obligations. It DOES NOT indicate that current application code complies with these standards or that tests have been executed. Runtime compliance remains an independent empirical determination requiring executed evidence. The live Constitution (4.1.0) remains the supreme baseline.

### 9.3 Validation & Negative-Case Verification Contracts

All external input entry points MUST enforce rigorous validation contracts before data flows into domain entities, cryptographic operations, event-sourced streams, or persistent storage.

#### Input Boundaries and Applicable Constraint Classes
Validation requirements apply across four external boundaries:
1. **User Interface Boundaries**: Form inputs, text fields, search queries, configuration toggles.
2. **Wire Protocol Boundaries**: CTAP2 CBOR payloads, Bluetooth HID control and interrupt reports, WebAuthn client data.
3. **External File & Import Boundaries**: Unencrypted/encrypted backup files, credential import streams, QR code scan data.
4. **Persisted & Replayed State Boundaries**: Serialized event streams, snapshot blobs, cached database records.

For each boundary, operations MUST evaluate applicable constraint classes:
- **Requiredness & Non-Blankness**: Mandatory fields must be present and non-empty; whitespace-only strings must be rejected where substantive input is required.
- **Length & Size Limits**: Strict byte/character bounds (e.g., credential ID length, relying party name bounds, max password length).
- **Format, Encoding & Syntax**: Canonical UTF-8 validity, Base64URL without padding, canonical CBOR map key ordering, valid domain name / registrable domain suffix syntax per WebAuthn L3 §5.4.2 for Relying Party identifiers (`rpId`).
- **Range & Value Set Bounds**: Valid integer algorithm IDs (e.g., COSE -7, -49), valid enum ranges, positive timestamp values.
- **State & Authorization Constraints**: Calling component state, authenticated session validity, biometric authorization tokens.
- **Cross-Field Consistency**: Relationships between paired inputs (e.g., password matching, algorithm/key length compatibility).

> [!NOTE]
> **Parameterless & Stateful Operations**: Operations requiring no external parameters (e.g., manual vault lock, session flush, biometric prompt invocation) MUST NOT invent synthetic input fields; they MUST rigorously validate prerequisite lifecycle state, session validity, and authorization (e.g., verifying an active unlocked session exists before processing operations). Domain models constructed through validated value objects MUST NOT be subjected to redundant revalidation down the execution chain.

#### Negative-Testing Verification Requirements
Every external boundary MUST be accompanied by dedicated negative test coverage in future implementation phases:
1. **Distinct Invalid Classes**: Tests MUST be authored for each materially distinct invalid input category (empty, below min, above max, illegal characters, malformed encoding, truncated payload, invalid lifecycle state).
2. **Boundary-Value Verification**: Boundary tests MUST explicitly evaluate values at, immediately below, and immediately above defined thresholds.
3. **Exact Error Outcomes**: Assertions MUST verify the exact typed `Outcome.Failure` or wire protocol error code; assertions that accept generic exceptions or arbitrary failures are prohibited.
4. **Absence of Forbidden Business/Security Side Effects**: Negative tests MUST explicitly assert that invalid input causes NO unauthorized business or security state mutations (e.g., NO partial database writes, NO unauthenticated append-only event emission, NO grant of authenticated privileges, and NO leaked platform state). Permitted and required side effects are strictly confined to necessary safe-failure state transitions (e.g., advancing the state machine to an error/idle state, resetting transient session buffers, or logging static scrubbed diagnostics) and immediate terminal zeroing of volatile secrets.
5. **Terminal Secret Cleanup**: Where sensitive mutable buffers (`ByteArray`, `CharArray`) are introduced into operations that subsequently reject input, tests MUST assert that terminal cleanup (`finally` block zeroing) executed successfully.

*Execution Note*: These negative-testing criteria define requirements for future test development. No new test runs or code modifications are conducted in this documentation feature; missing negative tests are tracked in `specs/057-improve-verification-contracts/follow-ups.md` (FUP-SEC-002).

### 9.4 Dual Credential Integration Lifecycle & Failure Matrices

Integration verification for Chimali spans two distinct, independent credential entry paths:
1. **Remote Bluetooth HID Virtual Authenticator**: Emulating a physical FIDO2 security key over BLE/L2CAP for external relying-party desktops (Windows, macOS, Linux).
2. **Local Android Credential Manager**: Serving as a credential provider on-device for local Android applications and browsers via `androidx.credentials`.

These paths are architecturally separate entry mechanisms with different protocols, lifecycle triggers, and failure modes; they MUST NOT be modeled as sequential stages of a single flow. Furthermore, host-level tests utilizing Fake test doubles or mocked Bluetooth managers provide valuable logic verification, but CANNOT certify physical transport behavior, L2CAP fragmentation, or platform provider integration. Real device and transport verification remains a distinct tier of evidence.

In accordance with Constitution Principle II (Deterministic Key Derivation & Single Root of Trust), non-volatile storage stores ONLY encrypted recovery and credential metadata (relying party identity, user handle, COSE algorithm ID, derivation profile, credential public key bytes, and assertion sign count). Derived private keys, child private scalars, blinding factors, and per-credential seeds are NEVER persisted to disk in either plaintext or encrypted form. All signing keys are derived deterministically on demand in volatile memory during active ceremonies and erased in `finally` upon completion, cancellation, or error.

#### Lifecycle Integration Matrices

##### Path 1: Remote Bluetooth HID Virtual Authenticator Journey
| Journey ID | Lifecycle Phase | Setup & Entry Point | Triggering Action | Component Boundaries (Real vs. Substituted) | Restart / Hydration Method | Expected Verifiable Outcome | Required Future Environment & Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **JRN-BLE-REG** | Registration | Android device paired and bonded via Bluetooth HID to desktop host; wallet unlocked | Desktop browser initiates WebAuthn `navigator.credentials.create()`; CTAP2 `authenticatorMakeCredential` command received | **Real**: `Fido2CryptoService`, `CredentialRepository`, event store. **Substituted (Host)**: Virtual HID L2CAP transport fake. **Device**: Real `BluetoothHidDevice`. | N/A (Active session) | Canonical CTAP2 CBOR response delivered to host. **RP cryptographic verification**: RP decodes attestation statement, extracts credential public key ($pk$) and credential ID ($C$), verifies attestation signature over `authData \|\| clientDataHash`; verification PASSES with recorded $pk$ and $C$. Corrupted attestation signature or invalid CBOR strictly FAILS RP verification. Passkey aggregate appended to event store. | Connected Android test report with physical host pairing; host tests in `feature:fido2` |
| **JRN-BLE-ENC** | Encrypted Persistence | Passkey registration completed | Repository commits credential entity to database | **Real**: SQLite3MultipleCiphers, SQLDelight, KeyStore master key | Database process restart | Permitted encrypted recovery/credential metadata persisted (AES-256-GCM envelope storing `rpId`, `userHandle`, `signCount`, algorithm ID, profile, and public key); deterministic HMAC blind index written; NO derived private keys or per-credential seeds written to disk (Principle II). | SQLCipher integration test report on Android host / device |
| **JRN-BLE-REC** | Restart & Reconstruction | Credential persisted; application process terminated | Application cold restart; Bluetooth HID service reconnects | **Real**: SQLite event store, `Decider` fold engine | Cold app launch; full event log replay or snapshot hydration | `PasskeyCredential` aggregate state hydrated from event stream and SQL projection with exact historical metadata: credential ID, RP ID, user handle, COSE algorithm, derivation profile, public key, and sign count. Private signing keys are NEVER reconstructed from storage; they are re-derived on demand in volatile memory during ceremonies. | Host JVM test (`commonTest`) and Android instrumentation cold startup test |
| **JRN-BLE-AUTH** | Authentication & Retrieval | Bonded host connected; credential exists on device | Desktop initiates `navigator.credentials.get()`; CTAP2 `authenticatorGetAssertion` command received | **Real**: Crypto service, assertion decider. **Substituted (Host)**: Transport fake. **Device**: Real BLE stack. | N/A (Active session) | User presence / biometric prompt confirmed; canonical CBOR response delivered to host. **RP cryptographic verification**: RP receives `authData`, `clientDataHash`, signature $\sigma$; verifies $\sigma$ over `authData \|\| clientDataHash` using registered credential public key $pk$; verifies `UP=1`, `UV=1`, and signature counter increment ($c > c_{\text{prev}}$); verification PASSES. Corrupted `clientDataHash`, forged signature, or non-incremented counter strictly FAILS RP verification. Sign count incremented in event store. | End-to-end ceremony trace on reference hardware (API 28+) connected to Windows/macOS/Linux host |

##### Path 2: Local Android Credential Manager Journey
| Journey ID | Lifecycle Phase | Setup & Entry Point | Triggering Action | Component Boundaries (Real vs. Substituted) | Restart / Hydration Method | Expected Verifiable Outcome | Required Future Environment & Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **JRN-PASS-REG** | Registration | Android calling app initiates passkey registration via `CredentialManager` | Calling app executes `createCredential()` with `CreatePublicKeyCredentialRequest` | **Real**: Provider service, crypto service, repository. **Substituted**: Calling app fixture. | N/A (In-process IPC) | UI prompt displayed; biometric authentication confirmed; key pair derived on-demand; `CreatePublicKeyCredentialResponse` delivered to calling app containing Base64URL `clientDataJSON` and CBOR `attestationObject`. **Caller/RP registration verification**: For direct/enterprise attestation formats (`fmt="android-key"` or `fmt="packed"`), RP cryptographically verifies `attStmt.sig` over `authData \|\| SHA-256(clientDataJSON)` using the leaf attestation certificate public key and verifies certificate chain; valid signature PASSES, corrupted signature, tampered payload, or invalid certificate chain strictly FAILS verification. For standard consumer passkeys (`attestation="none"`, `fmt="none"` per WebAuthn L3 §8.7), cryptographic attestation signature is not applicable because `attStmt` is an empty map (`{}`); RP verifies empty `attStmt`, verifies challenge echo and calling origin in `clientDataJSON`, verifies `rpIdHash` and `UP=1`/`UV=1` flags in `authData`, and extracts valid COSE credential public key $pk$ and credential ID $C$; valid parameters PASS, challenge mismatch, bad origin, or corrupted CBOR/COSE strictly FAILS verification. Encrypted passkey metadata aggregate appended to event store. | Android Instrumented test using AndroidX Credentials test harness |
| **JRN-PASS-ENC** | Encrypted Persistence | Credential registration accepted | Event store appends passkey creation event | **Real**: SQLite3MultipleCiphers database | Database reopen | AES-256-GCM encrypted credential metadata committed (storing `userHandle`, `userName`, `rpId`, algorithm, and public key); deterministic HMAC blind index created; NO derived private keys or per-credential seeds persisted; all private keys re-derived deterministically on demand (Principle II). | Instrumentation test verifying encrypted SQLDelight tables |
| **JRN-PASS-REC** | Restart & Reconstruction | Persisted passkeys exist; device rebooted or process killed | Calling app requests passkeys after clean launch | **Real**: Repository, `Decider` fold engine | Cold process launch | `Decider` folds event stream deterministically; credential list populated for calling RP with exact public metadata, counter, and algorithm. Private keys are NEVER stored or hydrated from disk; all cryptographic operations re-derive transient private scalars in volatile memory. | Android host test with cold repository re-instantiation |
| **JRN-PASS-AUTH** | Authentication & Retrieval | Registered passkey exists for calling RP | Calling app invokes `getCredential()` with `GetPublicKeyCredentialRequest` | **Real**: Provider service, assertion signer. **Substituted**: Test calling activity. | N/A (Active IPC) | Biometric confirmed; valid WebAuthn assertion JSON response delivered to caller. **Caller/RP cryptographic verification**: Calling app extracts `authenticatorData`, `clientDataJSON`, and signature $\sigma$; verifies $\sigma$ over `authenticatorData \|\| SHA-256(clientDataJSON)` using stored credential public key $pk$; verifies `UP=1`, `UV=1`, and sign count increment; verification PASSES. Tampered challenge or invalid signature strictly FAILS verification. Counter updated in event store. | Android instrumented test asserting signed assertion payload against WebAuthn JSON spec |

#### Twelve Failure-Category Applicability Decisions

In accordance with Constitution Principle III (Layered Architecture), Principle IV (Event Sourcing), and Principle V (DO-178B Rigor), both integration paths MUST enforce deterministic safe-failure behaviors. Under NO circumstances may a failure handling routine delete or prune already committed append-only history to achieve rollback.

| Path | Failure Category | Decision & Inclusion Status | Documented Failure Behavior, Cleanup & Consistency Obligations | Applicability Rationale |
| --- | --- | --- | --- | --- |
| **Bluetooth HID** | **Malformed input** (`FLR-BLE-MAL`) | **Included** | CTAP2 parser rejects payload; returns `CTAP2_ERR_INVALID_CBOR` or `CTAP2_ERR_MISSING_PARAMETER`; no key material derived; no partial event appended; working buffers zeroed in `finally`. | Applicable: untrusted external host input over wireless transport. |
| **Bluetooth HID** | **Denied verification** (`FLR-BLE-DEN`) | **Included** | User denies local notification/prompt; ceremony aborts; returns `CTAP2_ERR_OPERATION_DENIED`; ephemeral keys erased; no signature generated. | Applicable: core user presence and authorization requirement. |
| **Bluetooth HID** | **Cancellation** (`FLR-BLE-CAN`) | **Included** | Host sends CTAP cancel command or user dismisses prompt; returns `CTAP2_ERR_KEEPALIVE_CANCEL`; state machine resets to idle; volatile buffers zeroed; storage untouched. | Applicable: standard WebAuthn / CTAP2 transaction cancellation flow. |
| **Bluetooth HID** | **Timeout** (`FLR-BLE-TIM`) | **Included** | User verification or transaction timer expires; returns `CTAP2_ERR_USER_ACTION_TIMEOUT`; state machine resets; in-flight keys erased; late response delivery strictly prevented. | Applicable: mandatory bounded timeout requirement (Constitution VI / Rules §3.2). |
| **Bluetooth HID** | **Disconnect** (`FLR-BLE-DIS`) | **Included** | BLE link drops, L2CAP socket resets, or host disconnects; in-flight transaction aborts immediately; state machine resets to unbonded/disconnected; no stale response sent upon reconnect. | Applicable: volatile RF wireless transport boundary. |
| **Bluetooth HID** | **Persistence failure** (`FLR-BLE-PST`) | **Included** | SQLCipher write fails during counter update or credential save; committed event log remains intact; projection failure logged via static diagnostic; error returned to host; no unpersisted success delivered. | Applicable: storage I/O and disk write failure boundary. |
| **Credential Manager** | **Malformed input** (`FLR-PASS-MAL`) | **Included** | Request JSON fails parsing; returns `CreateCredentialCustomException` or `GetCredentialCustomException` with typed domain error; no internal state mutated; no uncaught exceptions escape boundary. | Applicable: external caller JSON request input. |
| **Credential Manager** | **Denied verification** (`FLR-PASS-DEN`) | **Included** | Biometric prompt cancelled or biometric rejected; returns `GetCredentialCancellationException`; transient derivation buffers zeroed immediately; no assertion output emitted. | Applicable: Android biometric authentication gate. |
| **Credential Manager** | **Cancellation** (`FLR-PASS-CAN`) | **Included** | Calling app signals cancellation via `CancellationSignal`; provider aborts processing; returns `GetCredentialCancellationException`; in-flight buffers cleared; no delivered success. | Applicable: standard AndroidX `CancellationSignal` API contract. |
| **Credential Manager** | **Timeout** (`FLR-PASS-TIM`) | **Included** | Handled when supported by framework / caller timeout contract; returns `GetCredentialInterruptedException` or failure mapping; provider clears mutable buffers and resets state. | Applicable: framework caller timeout specifications. |
| **Credential Manager** | **Disconnect** (`FLR-PASS-DIS`) | **Included** | Defined as calling activity/process termination or Binder IPC disconnection before response delivery; operation aborts; response discarded; no background zombie writes to storage. | Applicable: Android IPC and lifecycle teardown boundary. |
| **Credential Manager** | **Persistence failure** (`FLR-PASS-PST`) | **Included** | Database write fails during passkey save; returns provider error to caller; already committed append-only history is preserved without deletion; projection inconsistency handled on next replay. | Applicable: local database write failure boundary. |

> [!NOTE]
> **Documentary Status vs. Runtime Execution**: In accordance with the project evidence contract, the designation `Specified` for these matrices certifies documentary completeness. It DOES NOT represent passing runtime execution. Implementation of physical device tests and automated integration test suites is tracked in `specs/057-improve-verification-contracts/follow-ups.md` (`FUP-INT-001`, `FUP-INT-002`).

### 9.5 Universal Accessibility Verification Procedures

In alignment with Constitution Principle VIII and BRD `FR-UI-010` / `FR-UI-011`, accessibility verification ensures that the application's security-critical operations are universally usable.

#### Four-Category Accessibility Assessment Matrix
Verification criteria are organized across four representative task categories:

| Category ID | Task Category & User Journeys | Semantic Tree & Accessible Name Requirements | Dynamic Text Scaling (through 200%) Criteria | Supported Themes & Color Contrast Criteria (WCAG AAA / `FR-UI-011`) |
| --- | --- | --- | --- | --- |
| **ACC-TASK-001** | **Vault List & Detail Inspection**: Browsing items, search, viewing credential detail, copying password. | List items provide merged semantics tree; item titles and categories announced; action buttons have clear names ("Copy Password", "Reveal Password"); search field announces clear/active state. | Layout adapts dynamically up to 200% font scale without horizontal scrolling of body text, clipped password values, or overlapping action buttons. | Tested in Light and Dark dynamic themes. Normal text achieves ≥ 7:1; large text (≥18pt or ≥14pt bold) ≥ 4.5:1; UI control boundaries ≥ 3:1 (per WCAG 2.1 SC 1.4.6 & 1.4.11). |
| **ACC-TASK-002** | **Credential Management & Settings**: Passkey deletion/edit, viewing metadata, managing backup and onboarding options. | Toggles and checkboxes announce state ("Checked" / "Unchecked"); destructive action dialogs announce warnings; heading semantics applied to sections. | All dialogs, labels, and confirmation prompts remain fully visible and reachable within viewport at 200% scaling. | Tested across light/dark themes. Status chips and text comply with 7:1 normal / 4.5:1 large contrast. Disabled actions exempt per WCAG rules. |
| **ACC-TASK-003** | **Registration Ceremonies**: In-app passkey creation, CTAP2 registration dialog, biometric prompt, relying-party verification. | Relying-party name and account identifiers announced; Passkey glyph marked as decorative if adjacent to descriptive label; biometric prompt accessible. | Registration dialog content expands vertically; primary action button ("Register" / "Confirm") remains on-screen and reachable at 200% scale. | Action buttons and boundary outlines maintain ≥ 3:1 contrast; prompt text adheres to 7:1 contrast against modal container background. |
| **ACC-TASK-004** | **Authentication Ceremonies**: FIDO2 assertion prompt, Credential Manager selection, user verification. | Prompt header announces transport context ("Authenticate for remote device" vs "Local authentication"); credential selector items clearly announce account names. | Multi-account lists in authentication sheet scroll cleanly at 200% scaling; cancel and confirm buttons remain reachable. | High-legibility typography adheres to 7:1 normal text contrast; focus indicators provide clear ≥ 3:1 contrast against adjacent colors. |

#### Future Screen-Reader (TalkBack) Walkthrough Procedure
When executing manual or automated screen-reader verification, procedures MUST follow this standardized protocol:
1. **Focus Traversal & Logical Ordering**: Verify that TalkBack swipe gestures traverse composable nodes in natural reading order (top-to-bottom, start-to-end), without unexpected jumps across dialogs or background screens.
2. **Accessible Names, Roles & Grouping**: Verify that all actionable nodes announce their role (e.g., "Button", "Switch") and name derived from visible text or semantics. Unrelated visual sub-elements must be merged using `Modifier.semantics(mergeDescendants = true)`.
3. **State & Error Announcements**: Changes in screen state (e.g., "Vault Locked", "Biometric Authentication Failed", "Copied to Clipboard") MUST be dispatched via accessibility live regions (`LiveRegionMode.Polite` or `Assertive`).
4. **Modal Dialog Containment**: When confirmation or authentication dialogs open, accessibility focus MUST be trapped within the dialog container until dismissed or completed.
5. **Essential Task Completion**: A user relying solely on TalkBack MUST be able to complete all four representative journeys without sighted assistance.
6. **Authorized Secret Disclosure Protection**: Plaintext credentials, passwords, and recovery phrases MUST NOT be automatically read aloud by TalkBack upon navigating to a screen. Plaintext MUST only be spoken when the user explicitly triggers an authorized "Reveal" action. Clipboard copy feedback MUST confirm copy success without speaking the secret text.
7. **Execution Environment Metadata**: All future TalkBack walkthrough reports MUST record: test device model, Android OS version, TalkBack version, active theme (Light/Dark), font scale setting (100%–200%), and list of identified traversal/announcement defects.

> [!IMPORTANT]
> **Preservation of Implementation Deferral**: Full implementation of high-contrast modes, 200% scaling adaptations, and complete TalkBack audits is explicitly deferred under BRD `FR-UI-011` to a dedicated UI accessibility phase. The inclusion of this assessment matrix and walkthrough protocol establishes the future technical contract; it DOES NOT claim current product compliance or close the `FR-UI-011` deferral. Tracked in `specs/057-improve-verification-contracts/follow-ups.md` (`FUP-ACC-001`).

### 9.6 Rendering Smoothness Measurement Contract

In accordance with Constitution Principle VI (Vitals) and BRD `NFR-PERF-020`, UI interactions MUST maintain consistent 60 FPS rendering, zero frozen frames (>700ms), and < 1% slow frames (>16ms). To ensure that performance verification is reproducible and trustworthy, all future rendering measurements MUST be governed by a reviewed measurement contract.

#### Minimum Designated Journeys
Two user journeys are established as the mandatory minimum verification coverage:
1. **Vault List Scrolling (`JRN-PERF-LIST`)**: Continuous vertical fling and programmatic scrolling through a populated credential list in `VaultListScreen`.
2. **Password Detail Inspection (`JRN-PERF-DETAIL`)**: Navigation from the vault list to `PasswordDetailScreen`, measuring initial composition, layout time, field disclosure animation, and back-navigation return.

*Scope Rule*: These journeys represent the minimum automated benchmark floor; designating them DOES NOT restrict or narrow the general 60-FPS rendering obligation, which applies across all screens and user flows.

#### Mandatory Measurement Contract Fields
Any future performance measurement proposal or benchmark suite MUST fully specify and receive engineering approval for all of the following contract fields prior to executing compliance runs (concrete parameter values are selected and justified in the implementing contract authored by the performance verification owner, not hardcoded here):
1. **Reference Hardware**: Exact device manufacturer, model, CPU architecture, and RAM capacity.
2. **Platform & Display Settings**: Android OS version, API level, and native display refresh rate (e.g., 60Hz, 90Hz, 120Hz).
3. **Build Variant & Optimization**: Production-matching build variant (`release` or staging `benchmark` with R8 minification and resource shrinking enabled); `debug` builds MUST NOT be used for performance acceptance.
4. **Compilation Mode**: Specification of the Android runtime compilation mode (e.g., `CompilationMode.Full()`, `CompilationMode.Partial(BaselineProfile)`, or custom profile), selected and justified in the reviewed contract to reflect realistic user performance.
5. **Seeded Dataset & State**: Fixed database fixture, exact item count (e.g., populated standard list; 10,000 items for `NFR-PERF-070` scalability testing), authenticated vault state, and image cache pre-conditions.
6. **Interaction Steps & Timing**: Explicit UIAutomator / Macrobenchmark interaction sequence, scroll speed/distance, gesture duration, and visual confirmation of rendered content.
7. **Repetition & Window Configuration**: Number of warm-up iterations, measurement repetitions, and time-bounded measurement capture window, selected and justified in the reviewed contract to ensure statistical confidence.
8. **Metric & Interpretation**: Selected metric (e.g., Jetpack Macrobenchmark `FrameTimingMetric`). Interpretation MUST strictly distinguish CPU frame production duration from missed VSYNC display deadlines (e.g., `frameOverrunMs`).
9. **Acceptance Statistic & Thresholds**: Acceptance statistic (e.g., P90, P95, P99, and maximum) strictly mapped to governing BRD `NFR-PERF-020` budgets: target frame render time < 16.6ms, slow frames < 1%, and zero frozen frames (>700ms).
10. **Aggregation & Failure Policy**: Criteria for aggregating multiple runs. If any repetition fails to execute setup, navigation, or interaction, the entire run fails.
11. **Older-Device & API Limitations**: For reference devices on API < 31 where `frameOverrunMs` is unsupported by Jetpack Macrobenchmark, the contract MUST specify an approved alternative method (e.g., `dumpsys gfxinfo` frame duration analysis); devices lacking an approved measurement method remain `Unverified`.
12. **Evidence Metadata**: Trace log location, Macrobenchmark JSON report, and system Perfetto capture links.

#### Governance & Trustworthy Result Handling
- **Performance Verification Owner**: The designated Performance Engineer is responsible for authoring the complete measurement contract and securing engineering approval before conducting compliance runs.
- **Proof of Execution Required**: Reports MUST include tool logs proving test discovery and active workload execution.
- **Failed Workload Rule**: Any benchmark run that fails setup, aborts navigation, or produces an empty workload MUST be marked `Failed`; reporting a 0-ms empty run as passing is strictly prohibited.
- **Prohibition on Inferred Compliance**: Collecting raw metrics without threshold comparison, reporting mathematical averages that obscure frame spikes, or inferring device compliance from an emulator or disparate hardware is prohibited from establishing compliance.
- **Preserved Deferrals**: Feature-055 performance deferrals (T084, T085, partial SC-007) and benchmark fixes remain deferred in `specs/057-improve-verification-contracts/follow-ups.md` (`FUP-PERF-001`). This feature defines the measurement contract framework; no new benchmarks are executed.

---

## 10. Document Metadata
- **Owner**: Chimali Engineering Team
- **Version**: 0.2.0
- **Reference**: [BRD](brd.md)
