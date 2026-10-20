<!--
SYNC IMPACT REPORT
Current amendment: Version 3.1.0 -> 4.0.0 (MAJOR: Refactor constitution to first principles, eliminate overspecification, and relocate implementation details and coding standards).
Date: 2026-10-19; ratified date retained: 2026-02-19.

Modified Principles:
  - System Purpose: Retained core identity.
  - Principle I (Security First): Condensed multi-mode encryption and memory zeroing to core governance laws; relocated cryptographic implementation minutiae to TRD §5.1 and .agents/rules/specify-rules.md §5.5.
  - Principle II (Master Seed Architecture): Condensed to root of trust, deterministic local profile laws, and ephemeral key invariants; referenced BRD NFR-SEC-041 and TRD TR-AUTH-031.
  - Principle III (Performance & Reliability): Relocated exhaustive numeric tables to BRD NFR-PERF-010..070 and lifecycle details to TRD §2.4; retained core Android Vitals & latency governance laws as Principle VI.
  - Principle IV (Cross-Platform Authenticator): Consolidated into Principle VIII.
  - Principle V (Inclusion & Accessibility): Consolidated into Principle VIII.
  - Principle VI (Documentation Standards): Consolidated under Principle VII (Pragmatism & Specification Discipline).
  - Architecture A-I: Retained as Principle III (Layered Clean Architecture & UDF).
  - Architecture A-II (Feature Module Architecture): Relocated full dependency matrix to TRD §2.1; retained dependency flow law in Principle III.
  - Architecture A-III (Module Criticality): Formulated as Principle V (Module Criticality & DO-178B Rigor).
  - Architecture A-IV (Event Sourcing): Retained as Principle IV (Event Sourcing & Perpetual Auditability); technical snapshot/replay details housed in TRD §2.3.
  - Technical Constraints: Consolidated into TRD §7 and BRD §7.
  - ES-I through ES-V (Engineering Standards & Coding Conventions): Relocated to .agents/rules/specify-rules.md §1 through §5.

Added Sections:
  - Engineering Standards & Conventions reference section linking to .agents/rules/specify-rules.md.
  - Explicit precedence hierarchy in Governance.

Removed Sections:
  - Historical scratch amendment reports (3.1.0, 3.0.0, 2.0.0) removed from constitution body per spec-kit standard.
  - Redundant technical constraint lists and styling conventions relocated to dedicated documents.

Follow-up TODOs: None.
-->

# Chimali Constitution

## System Purpose

Chimali is a security-first Android application providing a local-first, encrypted credential vault and FIDO2 passkey management with Bluetooth HID virtual authenticator capabilities. All design, architecture, and engineering decisions MUST serve this mission without compromising the security, reliability, or privacy of user credentials.

---

## Core Principles

### I. Cryptographic & Memory Security

- **Multi-Mode Encryption**: Sensitive data MUST be encrypted at rest. General payloads (files, credential blobs) MUST use AES-256-GCM. SQLite database files MUST use SQLite3MultipleCiphers with an authenticated-encryption cipher. Exact-match searchable metadata MUST use deterministic keyed lookup tokens (HMAC blind indexes) with explicit domain separation; plaintext search tokens or AES-GCM nonce reuse are strictly prohibited.
- **Volatile Plaintext Ownership & Zeroing**: Application-owned sensitive plaintext (keys, seeds, private keys, working buffers) MUST reside in volatile, explicitly erasable mutable storage (`ByteArray`, `CharArray`). Every owner MUST erase sensitive mutable buffers immediately upon terminal paths (`finally` blocks) covering success, failure, cancellation, and session termination. Immutable `String` retention of sensitive credentials, keys, or seeds is strictly forbidden.
- **Strict Boundary for Platform UI Text**: Immutable strings are permitted only at audited platform input/rendering boundaries where mutable interfaces do not exist. Such references MUST NOT become application state, caches, logs, or diagnostics, and must be released immediately when the platform interaction ends. Cryptographic key material, seeds, and serialization buffers NEVER qualify for this exception. Detailed cryptographic conventions are codified in `.agents/rules/specify-rules.md` §5.5 and `docs/trd.md` §5.1.

### II. Master Seed & Deterministic Key Derivation

- **Single Root of Trust**: The root of trust is a 24-word BIP39 recovery mnemonic generated from 256 bits of cryptographically secure entropy, protected at rest by authenticated encryption. The 64-byte PBKDF2 seed is transient and MUST be erased after root derivation.
- **Deterministic Derivation Profiles**: Wallet key derivation MUST follow the approved, named local profile using RFC 5869 HKDF-SHA256 (`specs/055-migrate-hdk-draft/contracts/local-key-derivation.md`). P-256 credential keys MUST bind the complete canonical decoded credential ID. Ed25519 and ML-DSA MUST use independently specified algorithm-separated seed paths. Unsupported algorithm/profile pairs MUST fail fast.
- **Ephemeral Keys & No False Security Claims**: Derived private child keys and per-credential seeds MUST NEVER be persisted to storage. Parent signing keys are software-derived and MUST NOT be represented as hardware-backed or non-exportable. Recovery requires both the mnemonic and credential metadata. Feature 055 establishes the current profile for a clean new installation; it does not require retention or conversion of prior formats and does not introduce general exemptions for future published formats. See `docs/brd.md` (NFR-SEC-041) and `docs/trd.md` (TR-AUTH-031).

### III. Layered Clean Architecture & Unidirectional Data Flow

- **Unidirectional Data Flow (UDF)**: The application strictly enforces Clean Architecture and MVI. Data and state MUST flow UP (`Data → Domain → UI`); events, intents, and actions MUST flow DOWN (`UI → Domain → Data`). Neither UI nor Domain may independently mutate or duplicate Data-layer state.
- **Strict Module Dependencies**: Feature and core modules MUST enforce unidirectional dependency constraints: `UI → Domain → Data → Models`, `Database → Data → Models`, and `Common → Models`. Only the Composition Root (`:app`) binds concrete implementations via Koin. Direct dependencies between presentation and concrete storage (`Database`) or circular dependencies are prohibited. See `docs/trd.md` §2.1 for the complete module matrix.

### IV. Event Sourcing & Perpetual Auditability

- **Perpetual History**: Core aggregates (`VaultEntry`, `PasskeyCredential`) MUST use Event Sourcing. Events are strictly append-only; historical pruning or destructive mutation is forbidden.
- **Deterministic State Reconstruction**: Entity state (`Model`) MUST be reconstructed deterministically via a `Decider` fold over the event stream. The system MUST support temporal queries (`asOf(timestamp)`) and generate structured JSON trace logs for state mutations.
- **Snapshot Acceleration & Additive Schemas**: Snapshots captured every 20 events accelerate hydration, falling back to full replay if missing. Schema evolutions MUST be strictly additive with default values. See `docs/trd.md` §2.3.

### V. Module Criticality & DO-178B Rigor

- **Criticality Classification**: Code is partitioned into Tier 1 (Critical: `:core:security`, `:core:domain`, `:core:data`, `:feature:fido2`, `:feature:authenticator`) and Tier 2 (Feature & Infrastructure).
- **DO-178B Traceability**: Tier 1 code MUST maintain 100% bi-directional requirement traceability. Every public and internal function MUST carry a `@Req` annotation mapping to stable IDs in `docs/brd.md` (`FR-*`) or `docs/trd.md` (`NFR-*`, `TR-*`). Unreferenced dead code is strictly prohibited.
- **Execution Determinism & Testing Gates**: Hot execution paths MUST avoid dynamic memory allocations. Loops and retries MUST be bounded. Automated CI pipelines MUST enforce test coverage gates: Tier 1 ≥ 40%, Tier 2 ≥ 20%, Global repository floor ≥ 20%. Detailed rules are enforced via `.agents/rules/specify-rules.md` §3.

### VI. Performance Vitals & Resource Budgets

- **Android Vitals Enforcement**: Features MUST adhere to strict vitals: Cold start < 2s, Warm < 1s, Hot < 500ms; consistent 60 FPS; zero memory leaks; wake locks < 0.1%.
- **Authenticator Latency Budget**: FIDO2 Bluetooth authenticator RP-to-verified-response round trip MUST be < 1,000ms (excluding measured user-verification wait). Internal FIDO operation processing MUST be < 200ms (excluding user-verification wait and BLE handoff). Outgoing HID reports MUST utilize a thread-safe FIFO queuing mechanism to prevent packet loss during rapid or fragmented transactions.
- **Binary & Memory Limits**: Base release AAB MUST NOT exceed 50 MB (delta ceiling +500 KB without architecture review; R8 shrinking mandatory). Volatile caches MUST implement `ComponentCallbacks2.onTrimMemory()` to release 100% of non-essential memory upon critical/moderate signals. Detailed budgets reside in `docs/brd.md` (NFR-PERF-010 through 070).

### VII. Pragmatism, YAGNI & Specification Discipline

- **YAGNI & Three-Use Rule**: Speculative infrastructure or future-proofing without immediate documented requirements is prohibited. Abstractions MUST be justified by at least three concrete usage sites or an explicit constitutional mandate.
- **Specification Depth**: Specifications MUST document *what* and *why*, not *how*. Implementation details belong in code and technical specifications.
- **Documentation Staleness is a Blocking Defect**: Documentation (specs, BRD, TRD) MUST be synchronized within the same feature branch modifying behavior. Stale documentation is equivalent to a failing test. Stable mnemonic requirement IDs (`FR-*`, `NFR-*`, `TR-*`) MUST NOT be deleted or reused; deprecated requirements are marked `[DEPRECATED]`.

### VIII. Cross-Platform Authenticator & Universal Accessibility

- **Standards-Compliant FIDO2 Emulation**: The application MUST emulate a FIDO2 Virtual Authenticator via `BluetoothHidDevice` across Windows, macOS, and Linux, strictly complying with canonical CTAP2 CBOR encoding (RFC 8949) and defensive U2F probing.
- **Accessibility as a Security Feature**: Credential display MUST use high-legibility fonts (e.g., Atkinson Hyperlegible) to prevent visual confusion. The application MUST support TalkBack, high-contrast themes, and dynamic text scaling.

---

## Engineering Standards & Conventions

Mandatory development workflows (TDD), CI/CD pipelines (`tools/local-ci.ps1`), pre-commit git hooks, coding conventions (Kotlin, SQL, Compose, CBOR), and anti-pattern restrictions are governed by `.agents/rules/specify-rules.md`. Compliance is mandatory for all automated agents and human contributors.

---

## Governance

This constitution supersedes all informal conventions and project documentation.

1. **Amendment Procedure**: Proposed amendments MUST state the exact principle changing, provide architectural rationale, and receive engineering approval prior to implementation.
2. **Semantic Versioning**:
   - **MAJOR**: Backward-incompatible governance or principle redefinitions/removals.
   - **MINOR**: Addition of new principles or materially expanded guidance.
   - **PATCH**: Clarifications, wording refinements, and typo fixes.
3. **Synchronization**: Every amendment MUST synchronize related requirements in `docs/brd.md`, `docs/trd.md`, and `.agents/rules/specify-rules.md`.
4. **Precedence**: In case of conflict, the hierarchy of authority is: Constitution (`.specify/memory/constitution.md`) → BRD (`docs/brd.md`) → TRD (`docs/trd.md`) → Rules (`.agents/rules/specify-rules.md`) → Feature Plans/Tasks.

Agents working on this project MUST read this constitution before beginning any speckit workflow step (specify, plan, tasks, implement).

**Version**: 4.0.0 | **Ratified**: 2026-02-19 | **Last Amended**: 2026-10-19
