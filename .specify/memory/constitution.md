<!--
SYNC IMPACT REPORT
- Version change: 0.9.1 → 0.9.2
- List of modified principles: II (Master Seed Architecture — T166 DeriveSalt fix decision encoded; T167 formally closed)
- Added sections: None
- Removed sections: None
- Templates requiring updates: None
- Follow-up TODOs: Implement T166 fix in HdkEcdhP256.kt (PRIORITY); update KATs in T172; check T168 ID constant
-->

# Chimali Constitution

## Core Principles

### I. Security First (Zero-Trust Local-First)
All sensitive data must be encrypted. The application adheres to a **Multi-Mode Symmetric Encryption Strategy** based on modern Android best practices:
1. **AES-256-GCM** MUST be used for general payload encryption (files, credential blobs, value storage). This enables Hardware Keystore offloading and safe streaming without memory exhaustion.
2. **AES-256-SIV** (Synthetic IV) MUST be used for **Searchable Encrypted Metadata** (e.g., database lookup tags, category names) where deterministic ciphertext is required, and for **Key Wrapping** where nonce-misuse resistance is paramount (e.g., within `EncryptedSharedPreferences` or master key boundaries).

If the device supports Quantum-Resistant algorithms (PQC, e.g., ML-KEM/Kyber), the application must utilize these as the primary encryption method. Mandatory prohibition of plain-text storage of credentials in memory. Sensitive data must only exist in decrypted form within volatile memory using mutable structures (e.g., byte/char arrays) that are explicitly zeroed out immediately after use.

### II. Master Seed Architecture
The root of trust is established via a **Master Seed (Master Key)** architecture. Credential keys are derived using the **Hierarchical Deterministic Key (HDK) function** following **IETF draft-dijkhuis-cfrg-hdkeys-06**. This standardises privacy-preserving elliptic curve key management by eliminating legacy BIP-32 style components (such as separate chain codes) in favour of standard Key Derivation Functions mapping directly to the curve group. The architecture ensures deterministic derivation of classical (ECDSA/Ed25519) and Post-Quantum signature schemes from the single root seed, aligning with modern cryptography guidelines without reliance on mixed BIP-44/BIP-32 patterns.

**Concrete instantiation**: The implemented HDK instantiation is **HDK-ECDH-P256** (§4.1 of the draft), using:
- **Group**: NIST P-256 (secp256r1)
- **Hash**: SHA-256 (via the `P256_XMD:SHA-256_SSWU_RO_` hash-to-curve suite)
- **Blinding**: Multiplicative blinding (§3.2.2) — `BlindPublicKey(pk, bk, ctx) = ScalarMult(pk, DeriveBlindingFactor(bk, ctx))`
- **KEM**: DHKEM(P-256, HKDF-SHA256) for remote key derivation (§3.3.1)

**Key derivation rules** (per §2.5 of the draft):
- A unit **MUST NOT** persist a blinded private key. Blinded private key bytes must be zeroed immediately after the signing operation completes.
- Salt values (including the seed) **MUST NOT** be reused outside of HDK derivation calls.
- The seed is generated with 32 bytes of entropy (`SecureRandom`) and stored encrypted via `EncryptedSharedPreferences` (AES-256-GCM).

**PQ branch isolation**: The ML-DSA/Post-Quantum key branch uses a **BIP-85-style** hardened CKD derivation (`m/83696968'/83286642'/2'`) to produce a child seed that is cryptographically isolated from the ECDSA HDK branch. This BIP-32 CKD usage is intentional, limited to the PQ branch only, and does **not** conflict with the HDK spec because that child seed never enters the `HdkEcdhP256` derivation tree. The ECDSA branch uses `HMAC-SHA512("chimali_device_key_v1", masterSeed)` to derive the device key pair deterministically.

**Spec alignment status** (Phase 7 `specs/004-fido2-hid/tasks.md`):
- `DeriveSalt` **MUST** conform to §2.4 of `draft-dijkhuis-cfrg-hdkeys-06`. The normative definition is:
  ```
  def DeriveSalt(salt, ctx):
      salt' = H(salt || ctx)
      return salt'
  ```
  The `ID` domain separator is already embedded in `ctx` via §2.3 (`ctx = ID || I2OSP(index, 4)`); it **MUST NOT** be prepended again to the hash input. The current implementation `H(ID || salt || ctx)` is a confirmed deviation. **Decision (2026-03-28): conform strictly to spec.** Fix is tracked as **T166 (PRIORITY)** in `specs/004-fido2-hid/tasks.md`; KAT vectors must be regenerated afterward (T172). **Until T166 is merged, existing derived keys are non-interoperable with conforming HDK implementations.**
- `DST = "ECDH Key Blind"` for `HashToScalar` — **✅ CLOSED (T167)**: verified conformant with §4.1 of `draft-dijkhuis-cfrg-hdkeys-06`. No change required.

### III. Uncompromising Architecture & Quality
The application strictly follows Clean Architecture with Unidirectional Data Flow (UDF) using the **MVI (Model-View-Intent)** pattern. Dependency injection is standardized using **Hilt**. The codebase must be highly modularized (Feature-by-module). Static analysis via **Detekt** and **Ktlint** is mandatory to enforce coding standards. **The use of 'magic numbers' is strictly prohibited; all numeric literals with domain significance must be extracted into meaningful named constants or enums to ensure maintainability and readability.**

### IV. Performance & Reliability Excellence
The application must adhere to strict Android Vitals targets:
- Startup: Cold Start < 2s, Warm Start < 1s, Hot Start < 500ms.
- Smoothness: Maintain 60 FPS during interactions.
- Latency: Bluetooth HID Virtual Authenticator actions must be < 200ms end-to-end. Outgoing HID reports MUST utilize a thread-safe FIFO queuing mechanism to prevent packet loss during rapid or fragmented transactions.
- Resources: Zero memory leaks and minimal battery impact (< 0.1% excessive wake locks).
- Clipboard: Sensitive data mustache be explicitly cleared from the system clipboard within 60 seconds of copy action.
- Scalability: The system must be designed to handle 10,000+ vault items with negligible performance degradation.

### V. Cross-Platform Utility & Modern UX
The app must seamlessly emulate a FIDO2 Virtual Authenticator via `BluetoothHidDevice` to support cross-platform authentication (Windows, macOS, Linux). Protocol implementation must strictly adhere to CTAP2 CBOR encoding standards (e.g., proper integer keys, correct negative integer major types, and AT flags) and provide defensive legacy U2F fallback probing to ensure strict OS compatibility (e.g., Windows 11). The UI must follow Material Design 3 (M3) with dynamic coloring, ensuring a premium user experience.

### VI. Inclusion & Universal Accessibility
Accessibility is a core functional and security requirement. The application MUST support screen readers (TalkBack), high-contrast modes, and dynamic text scaling. Legibility is treated as a security feature to prevent user error during credential management: credentials MUST be displayed using high-legibility fonts (e.g., [Atkinson Hyperlegible](https://brailleinstitute.org/atkinson-hyperlegible-font)) with clear character differentiation.

### VII. Documentation Standards
All project documentation must be kept up to date and aligned with the codebase at all times. Requirements documentation follows **IEEE 830 (SRS)** principles for clarity, traceability, and unambiguity, combined with **modern Agile documentation** practices (lightweight, living documents, close to the code). Requirement identifiers use a **stable mnemonic path format** (`[TYPE]-[CATEGORY]-[NNN]`, e.g., `FR-VAULT-010`, `NFR-SEC-020`) to prevent re-indexing cascades when requirements are added, moved, or removed. Once assigned, a requirement ID is never reused; deprecated requirements are marked as `[DEPRECATED]` rather than deleted.

## Technical Constraints

- **Platform**: Android Native Application (Minimum SDK 28).
- **Language**: Primary language is Kotlin (100% for UI/Android layers). Languages that produce native code (e.g., Rust) are allowed under special cases (e.g., core cryptography, shared low-level logic, Loro.dev CRDT integration via UniFFI).
- **Architecture**: Kotlin Multiplatform (KMP) ready module structure must be maintained to facilitate future expansion.
- **Storage**: Standardized encrypted persistence using **SQLCipher** and **SQLDelight**.
- **UI Framework**: Jetpack Compose (Material Design 3).
- **Hardware Integration**: Mandatory support for Bluetooth HID Device Profile for virtual authenticator features.
- **Privacy Focus**: On-device AI only (e.g., ML Kit, Gemini Nano) for credential categorization; no cloud processing of plain-text data.

## Development Workflow & Testing

- **Testing Methodology**: Test-Driven Development (TDD) where feasible. 100% unit test coverage for core business logic (encryption, validation) is non-negotiable using **JUnit 5** and **MockK**.
- **Integration**: Comprehensive integration tests must verify the interaction between Bluetooth HID emulation, Credential Manager, and Encryption layers. UI components must be verified using **Compose UI Testing**.

## Governance

- **Constitution Supremacy**: The principles defined in this Constitution supersede all other development practices.
- **Quality Gates**: All Pull Requests must verify compliance with security guidelines (especially memory zeroing) and pass all static analysis checks (Detekt/Ktlint).
- **Performance Budget**: Any feature that degrades startup time or rendering smoothness beyond the defined limits will be rejected.

**Version**: 0.9.2 | **Ratified**: 2026-02-19 | **Last Amended**: 2026-03-28
