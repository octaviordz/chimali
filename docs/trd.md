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

### 4.3 Passkey Support
#### [FR-PASS-010] Android Credential Manager
- **Technical Detail**: Use `androidx.credentials:credentials` library. Handle `CreatePublicKeyCredentialRequest` and `GetPublicKeyCredentialRequest`.

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
- **Post-Quantum KEM**: No KEM-based remote provisioning capability is defined by the current local credential profile. Any future issuer or provisioning design remains subject to the separate [OpenID4VCI HDK issuance investigation](backlog/openid4vci-hdk-issuance-investigation.md); this document does not claim ML-KEM support or readiness.

### 5.2 Signature Schemes (NFR-SEC-040)
- **ECC**: P-256 (COSE -7) for wide compatibility.
- **PQC**: ML-DSA-65 (COSE -49) for quantum resistance.

## 6. User Interface & Experience (UI/UX Implementation)
- **M3 Integration**: Use `MaterialTheme` color schemes derived from `dynamicLightColorScheme` or `dynamicDarkColorScheme`.
- **Accessibility**: Implement `contentDescription` for all interactive elements and ensure touch targets meet the 48dp minimum. Credentials must be displayed with high-legibility fonts (e.g., Atkinson Hyperlegible).
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

---

## 10. Document Metadata
- **Owner**: Chimali Engineering Team
- **Version**: 0.2.0
- **Reference**: [BRD](brd.md)
