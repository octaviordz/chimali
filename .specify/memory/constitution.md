<!--
SYNC IMPACT REPORT
- Version change: 0.14.0 → 0.14.1
- List of modified principles:
  - I. Security First (Zero-Trust Local-First) — generalized encrypted preferences, removed deprecated EncryptedSharedPreferences
  - II. Master Seed Architecture — generalized encrypted preferences
  - X.7 Anti-Patterns & Prohibited Practices — generalized encrypted preferences
- Added sections: None
- Removed sections: None
- Templates requiring updates:
  - ✅ `.specify/templates/plan-template.md` — No updates needed
    (Constitution Check section references principles generically)
  - ✅ `.specify/templates/spec-template.md` — No updates needed
    (scope/requirements sections are technology-agnostic templates)
  - ✅ `.specify/templates/tasks-template.md` — No updates needed
    (task categorisation remains compatible)
- Follow-up TODOs: None
-->

# Chimali Constitution

## Core Principles

### I. Security First (Zero-Trust Local-First)
All sensitive data must be encrypted. The application adheres to a **Multi-Mode Symmetric Encryption Strategy** based on modern Android best practices:
1. **AES-256-GCM** MUST be used for general payload encryption (files, credential blobs, value storage). This enables Hardware Keystore offloading and safe streaming without memory exhaustion.
2. **AES-256-SIV** (Synthetic IV) MUST be used for **Searchable Encrypted Metadata** (e.g., database lookup tags, category names) where deterministic ciphertext is required, and for **Key Wrapping** where nonce-misuse resistance is paramount (e.g., within secure key-value stores or master key boundaries).
3. **SQLCipher (AES-256-CBC)** is explicitly permitted for **SQLite database file-level encryption**. This is a pragmatic exemption: SQLCipher's AES-256-CBC file encryption provides strong data-at-rest protection for Android's encrypted storage layer, which operates at a different abstraction boundary than individual in-flight payload encryption. SQLCipher is configured with a key derived via `PBKDF2-SHA512` from the device's master key. Individual credential blobs stored within the database MUST still be encrypted with AES-256-GCM before database insertion.

4. **Memory Security (Non-Negotiable)**: Plain-text storage of credentials in persistent or long-lived memory is strictly prohibited. Sensitive data MUST only exist in decrypted form within volatile memory using mutable structures (e.g., `ByteArray`, `CharArray`) and MUST be explicitly zeroed out immediately after use (refer to Principle X.5 for mandatory `try/finally` zeroing patterns).

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
- The seed is generated with 32 bytes of entropy (`SecureRandom`) and stored encrypted via an encrypted preference store (AES-256-GCM).

**PQ branch isolation**: The ML-DSA/Post-Quantum key branch uses **HDK DeriveSalt** (§2.4) with the context `"PQ_ML-DSA_Branch"`, followed by HMAC-SHA512 expansion, to produce a child seed that is cryptographically isolated from the ECDSA HDK branch. This ensures full compliance with `draft-dijkhuis-cfrg-hdkeys-06` without any legacy BIP-32/85 dependencies. The ECDSA branch uses `HMAC-SHA512("chimali_device_key_v1", masterSeed)` to derive the device key pair deterministically.

**Spec alignment**:
- `DeriveSalt` conforms to §2.4 of `draft-dijkhuis-cfrg-hdkeys-06`. The normative definition `H(salt || ctx)` is strictly implemented. The `ID` domain separator is embedded in `ctx` via §2.3 (`ctx = ID || I2OSP(index, 4)`) and is not prepended again to the hash input.
- `DST = "ECDH Key Blind"` for `HashToScalar` conforms to §4.1 of `draft-dijkhuis-cfrg-hdkeys-06`.

### III. Uncompromising Architecture & Quality
The application strictly follows Clean Architecture with Unidirectional Data Flow (UDF) using the **MVI (Model-View-Intent)** pattern. Core business logic is encapsulated in **Aggregates** and **Services**, while persistence is driven by an **Event Sourcing** model to ensure absolute auditability. Dependency injection is standardized using **Koin** (with Koin Compiler Plugin) to support Kotlin Multiplatform. The codebase must be highly modularized (Feature-by-module). Static analysis via **Detekt** and **Ktlint** is mandatory to enforce coding standards.

### IV. Performance & Reliability Excellence
The application must adhere to strict Android Vitals targets:
- Startup: Cold Start < 2s, Warm Start < 1s, Hot Start < 500ms.
- Smoothness: Maintain 60 FPS during interactions.
- Latency: Bluetooth HID Virtual Authenticator actions must be < 200ms end-to-end. Outgoing HID reports MUST utilize a thread-safe FIFO queuing mechanism to prevent packet loss during rapid or fragmented transactions.
- Resources: Zero memory leaks and minimal battery impact (< 0.1% excessive wake locks).
- Clipboard: Sensitive data must be explicitly cleared from the system clipboard within 60 seconds of copy action.
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

- **Development Methodology**: Test-Driven Development (TDD) **MUST** be enforced as the standard engineering methodology. All commits must pass the Local CI pipeline (`tools/local-ci.ps1`). Exemptions are permitted only when a test-first approach is demonstrably unfeasible. 100% unit test coverage for core business logic (encryption, validation) is non-negotiable using **kotlin.test** (for KMP common logic), **JUnit 5**, and **MockK**.
- **Integration**: Comprehensive integration tests must verify the interaction between Bluetooth HID emulation, Credential Manager, and Encryption layers. UI components must be verified using **Compose UI Testing**.

## Governance

- **Constitution Supremacy**: The principles defined in this Constitution supersede all other development practices.
- **Quality Gates**: All Pull Requests must verify compliance with security guidelines (especially memory zeroing) and pass all static analysis checks (Detekt/Ktlint).
- **Performance Budget**: Any feature that degrades startup time or rendering smoothness beyond the defined limits will be rejected.

### VIII. Event Sourcing Architecture
The application MUST implement an Event Sourcing architecture as its primary persistence and audit mechanism for core aggregates (VaultEntry, PasskeyCredential).
- **Immutable History**: Events MUST be appended, never updated or deleted. All state mutations are captured as a sequence of immutable events. Pruning or deletion of historical events is strictly prohibited (**Perpetual History**).
- **Deterministic Reconstruction**: The system MUST utilize a `Decider` mechanism to deterministically reconstruct an entity's state (the `Model`) by folding an ordered stream of events.
- **Temporal Queries (Time Travel)**: The system MUST provide a timestamp-based interface (`asOf(timestamp)`) to reconstruct the exact state of any entity at any specific point in history.
- **Audit Traceability**: Every state reconstruction MUST generate a structured JSON trace log detailing each chronological mutation for precise machine-auditing.
- **Snapshot-Accelerated Hydration**: For performance, the system MUST support snapshots generated every 20 events. The system MUST fall back to full from-scratch hydration if a snapshot is missing or corrupted.
- **Schema Evolution**: Event schema changes MUST be strictly additive. New fields MUST include default values to ensure backward compatibility with the perpetual event log.
- **Concurrency**: The system MUST implement optimistic concurrency via unique sequence numbers and provide automated retry logic (re-hydrate and re-apply) for command conflicts.

### IX. Local CI/CD & Enforcement
1. **Local CI Pipeline**: All developers MUST run the Local CI pipeline (`tools/local-ci.ps1`) before committing. This pipeline includes static analysis (`Ktlint`, `Detekt`) and all unit tests.
2. **Git Hook Enforcement**: A `pre-commit` git hook is mandatory to prevent accidental commits of broken or untested code. The hook is installed via `tools/setup-hooks.ps1`.
3. **Verification**: Any change that bypasses the Local CI gate (e.g., via `--no-verify`) MUST be documented with a valid justification in the commit message.

### X. Coding Conventions & Best Practices

To ensure a highly maintainable, readable, and performant codebase, the following conventions and best practices MUST be strictly adhered to across all technologies.

#### 1. General Style & Formatting
- **Max Line Length**: 120 characters to balance readability with modern display sizes.
- **Indentation**: 4 spaces for Kotlin/Java/SQL, 2 spaces for XML/JSON/YAML.
- **File Encoding**: UTF-8 without BOM.
- **Trailing Commas**: MUST be used in Kotlin multi-line parameter lists, collection literals, and `when` entries to minimize diff noise.
- **Static Analysis**: All code must pass Detekt and Ktlint without warnings. **The use of 'magic numbers' is strictly prohibited; all numeric literals with domain significance must be extracted into meaningful named constants or enums to ensure maintainability and readability.**
- **Import Ordering**: Wildcard imports (`*`) are prohibited. Imports MUST be sorted lexicographically with no blank-line separation between groups (enforced by Ktlint).
- **File Naming**: Kotlin source files MUST be named after the primary public class/interface they contain (e.g., `VaultRepository.kt`). Files containing only top-level extension functions MUST use a descriptive plural noun (e.g., `ByteArrayExtensions.kt`).

#### 2. Kotlin & KMP (Kotlin Multiplatform) Guidelines
- **Naming Conventions**:
  - Classes, Interfaces, Objects: `PascalCase` (e.g., `CredentialRepository`).
  - Functions, Properties, Variables: `camelCase` (e.g., `deriveMasterKey`).
  - Constants (`const val`) and Enum entries: `UPPER_SNAKE_CASE` (e.g., `MAX_RETRY_COUNT`).
  - Top-level `val` singletons (non-`const`): `PascalCase` when they represent a singleton object or type-like value, `camelCase` otherwise.
  - Packages: `lowercase` without underscores (e.g., `com.chimali.feature.vault`).
  - Backing Properties: Prefix with an underscore `_` (e.g., `_state` for mutable state, `state` for public immutable state). The public property MUST have the same name minus the underscore prefix.
  - Type Aliases: `PascalCase`, descriptive (e.g., `typealias CredentialId = String`).
  - Test Functions: Use backtick-enclosed descriptive names (e.g., `` `deriveMasterKey returns deterministic output` ``).
- **Idioms & Best Practices**:
  - Prefer immutable data structures (`val` over `var`, `List` over `MutableList`).
  - Utilize sealed classes/interfaces for exhaustive `when` statements and strict state representation. Every `when` on a sealed type MUST be exhaustive (no `else` branch).
  - Leverage coroutines and `Flow` for asynchronous operations. Never block the main/UI thread.
  - Use `Outcome<D, E : DomainError>` as the mandatory standard for domain error handling instead of `kotlin.Result` or throwing exceptions. `Outcome` ensures domain isolation by preventing the leakage of platform-specific `Throwable` types into the presentation layer. Exceptions MUST be reserved exclusively for unrecoverable technical failures.
  - Apply `require()` and `check()` for aggressive input validation and state verification. Fail fast.
  - Write declarative, functional-style code using collection operations (`map`, `filter`, `fold`) where appropriate.
  - **Structured Concurrency**: All coroutine launches MUST be scoped to a lifecycle-aware scope (`viewModelScope`, `lifecycleScope`, or a custom `SupervisorScope` for services). Unscoped `GlobalScope.launch` is prohibited.
  - **Null Safety**: Prefer `?.let {}` and `?:` (Elvis) over `if (x != null)` blocks. Non-null assertions (`!!`) are prohibited except in test code with explicit justification.
  - **Extension Functions**: Use for cross-cutting operations on domain types (e.g., `ByteArray.toHex()`). Keep extensions in dedicated `*Extensions.kt` files organized by receiver type.
  - **Memory Safety**: For sensitive cryptographic material (keys, passwords), use mutable structures (e.g., `ByteArray`, `CharArray`) and explicitly zero them out immediately after use. Use `try/finally` blocks to guarantee zeroing even on exceptions.
  - **KMP**: Keep platform-specific code (`expect`/`actual`) minimal. Maximize domain and presentation logic in the `commonMain` source set. All `expect` declarations MUST have a corresponding `actual` in every supported platform source set.
- **Performance Guidelines**:
  - Avoid unnecessary object allocations in tight loops (e.g., `onDraw` in Compose, high-frequency cryptography). Use `value class` (inline classes) to wrap primitives without allocation overhead.
  - Use `Sequence` for multi-step operations on large collections (>100 elements) to prevent intermediate list creation.
  - Scope coroutines properly (e.g., `viewModelScope`) to prevent memory leaks and ensure cancellation when the lifecycle owner is destroyed.
  - Prefer `buildList`, `buildMap`, `buildString` over mutable-collection-plus-loop patterns for constructing collections.
  - Use `@JvmStatic` on companion-object functions that are called from Java or through reflection to avoid synthetic accessor methods.

#### 3. SQL & Database Guidelines (SQLDelight & SQLCipher)
- **Naming Conventions**:
  - Tables: `snake_case`, **singular** (e.g., `vault_item`, `credential`). This deviates from the pluralized convention common in some frameworks; the singular form is chosen for consistency with domain entity naming and SQLDelight generated code clarity.
  - Columns: `snake_case` (e.g., `created_at`, `master_seed`).
  - Indices: `idx_<table>_<column(s)>` (e.g., `idx_credential_rp_id`).
  - Foreign Keys: Name the column after the referenced table with `_id` suffix (e.g., `vault_item_id`).
  - SQLDelight Queries: `camelCase` for named queries (e.g., `getVaultItemById`).
  - Migration Files: `<version_number>.sqm` (e.g., `2.sqm`, `3.sqm`).
- **Column Ordering**:
  - All `CREATE TABLE` statements and migration `ALTER TABLE … ADD COLUMN` groupings MUST follow this canonical column order:
    1. **Primary key column(s)** first (e.g., `id`).
    2. **Audit / temporal columns** next, alphabetized by column name, (e.g. `created_at`, `last_used_at`, `updated_at` include only those applicable to the table).
    3. **All remaining columns** alphabetized by column name.
  - This ordering applies to both `.sq` schema definitions and `.sqm` migration files. When adding columns via migration, the column MUST be placed so that a full schema dump would still satisfy the ordering rule.
- **Idioms & Best Practices**:
  - Use prepared statements and bind variables (native to SQLDelight) to prevent SQL injection.
  - Keep business logic out of the database. Use SQL for storage, retrieval, and basic constraints. No stored procedures or complex computed columns.
  - Use explicit transaction blocks (`transaction { }`) when performing multiple related writes to ensure atomicity.
  - Schema changes MUST be backwards-compatible or accompanied by a data migration script. Column drops MUST be staged across two releases (deprecate → drop).
  - All tables MUST include `created_at` and `updated_at` `INTEGER` timestamp columns (Unix epoch milliseconds).
- **Performance Guidelines**:
  - Index frequently queried columns and foreign keys. *Note*: Due to SQLCipher encryption, indexing searchable data requires deterministic ciphertext (e.g., AES-256-SIV as defined in Principle I).
  - Avoid `SELECT *`. Explicitly select only the required columns to minimize I/O overhead.
  - Run database operations on a dedicated background dispatcher (`Dispatchers.IO`).
  - For bulk inserts or Event Sourcing log appends, ensure they are wrapped in a single transaction to drastically reduce disk I/O and SQLCipher encryption overhead.
  - Prefer `INSERT OR REPLACE` over `SELECT-then-INSERT/UPDATE` patterns to reduce round-trips.

#### 4. Compose & UI Performance Guidelines
- **Stability**: Compose functions MUST receive stable or immutable parameters. Avoid passing mutable collections or non-stable classes directly. Use `@Immutable` or `@Stable` annotations where appropriate for custom data classes.
- **Recomposition Minimization**: Hoist state to the lowest-common-ancestor composable. Avoid reading `State<T>` in a parent when only a child needs it.
- **Side Effects**: Use the correct side-effect handler for the job: `LaunchedEffect` for suspend functions, `DisposableEffect` for cleanup, `SideEffect` for non-suspend code that must run after every recomposition.
- **Lists**: Use `LazyColumn`/`LazyRow` with stable `key` parameters for all dynamic lists. Never use `Column`/`Row` with `forEach` for lists exceeding 10 items.
- **Image Loading**: Use Coil's `AsyncImage` with a shared `ImageLoader` configured for disk and memory caching. Never load raw bitmaps in composable functions.

#### 5. Cryptographic Code Guidelines
- **Zeroing**: All byte arrays containing key material, seeds, blinding factors, or derived secrets MUST be zeroed in a `finally` block immediately after use. This rule is non-negotiable.
- **Constant-Time Comparisons**: HMAC tags, signatures, and credential IDs MUST be compared using `MessageDigest.isEqual()` or equivalent constant-time comparison to prevent timing attacks.
- **SecureRandom**: All nonces, salts, and IVs MUST be generated using `java.security.SecureRandom`. Using `kotlin.random.Random` or `java.util.Random` for any security-sensitive value is prohibited.
- **Domain Separation**: Context strings and DST values MUST be defined as named constants with documentation referencing the specification section (e.g., `/** §4.1 of draft-dijkhuis-cfrg-hdkeys-06 */ const val HDK_DST = "ECDH Key Blind"`).
- **No Cryptographic Invention**: All cryptographic constructions MUST conform to a published standard (IETF RFC/Draft, NIST SP, W3C). Custom cryptographic protocols are prohibited without a formal security review.

#### 6. CBOR, Binary & Wire Protocol Guidelines
- **Encoding**: CTAP2 messages MUST use canonical CBOR encoding ([RFC 8949](https://www.rfc-editor.org/rfc/rfc8949) §4.2.1). Map keys MUST be integers, sorted in ascending order.
- **Negative Integers**: CBOR negative integer encoding MUST use major type 1 (the value -1 is encoded as `0x20`, not as a tagged negative integer).
- **Base64**: Wire-level credential IDs and public keys MUST use **Base64URL** (no padding) per [RFC 4648 §5](https://www.rfc-editor.org/rfc/rfc4648#section-5). Storage-level encoding MAY use standard Base64 but MUST be consistent within a table.
- **Byte Ordering**: Multi-byte integers in HID reports and CTAP2 frames MUST be big-endian unless a protocol specification explicitly requires little-endian.

#### 7. Anti-Patterns & Prohibited Practices
The following patterns are explicitly prohibited across the codebase:
- **God Classes**: No class may exceed 500 lines of code (excluding comments and blank lines). If a class exceeds this limit, it MUST be decomposed.
- **Deep Inheritance**: Inheritance depth MUST NOT exceed 3 levels. Prefer composition via interfaces and delegation.
- **Wildcard Catch**: `catch (e: Exception)` or `catch (e: Throwable)` is prohibited in production code. Catch specific exception types. Broad catches are permitted only at coroutine boundary supervisors with mandatory logging.
- **Mutable Shared State**: Shared mutable state between coroutines MUST use `Mutex`, `StateFlow`, or `Channel`. Direct mutable variable access from multiple coroutines is prohibited.
- **String Concatenation in Loops**: Use `StringBuilder` or `buildString` for string construction in loops.
- **Hardcoded Secrets**: API keys, encryption keys, or credentials MUST NOT appear as string literals in source code. Use `BuildConfig`, an encrypted preference store, or the Android Keystore.

### XI. Risk Management & Pragmatism

To guard against overspecification, over-engineering, and unrealistic goals, the following risk-mitigation principles MUST be applied during planning and implementation.

#### 1. Overspecification Guard
- **YAGNI First**: Do not implement features, abstractions, or infrastructure components that do not serve an immediate, documented requirement. Speculative future-proofing is debt, not investment.
- **Three-Use Rule**: An abstraction (interface, wrapper, utility) MUST be justified by at least three concrete usage sites or an explicit architectural mandate in this constitution. Single-use abstractions require documented justification.
- **Specification Depth**: Specifications MUST document *what* and *why*, not *how*. Implementation details belong in code and code comments, not in spec documents. Over-constraining the solution space in specs reduces engineering autonomy.

#### 2. Over-Engineering Guard
- **Simplest Sufficient Solution**: For any design decision, the simplest implementation that satisfies all hard requirements and known constraints MUST be preferred. Complexity is justified only when a simpler approach demonstrably fails a constitutional principle (Security, Performance, Scalability).
- **Module Count**: New Gradle modules MUST be justified by a concrete isolation or build-performance benefit. Moving code into a separate module solely for "clean architecture purity" without measurable gain is prohibited.
- **Pattern Justification**: Design patterns (Repository, Mediator, Decorator, etc.) MUST be applied to solve a specific problem, not for theoretical adherence. Each pattern usage MUST be traceable to a requirement or constitutional principle.

#### 3. Realistic Goal Setting
- **Scope Budgets**: Feature specifications MUST include a complexity estimate (S/M/L/XL). Features estimated as XL MUST be decomposed into independently deliverable increments before implementation begins.
- **Performance Targets**: Performance targets defined in Principle IV represent upper bounds. Achieving targets on reference hardware (Pixel 6a or equivalent mid-range) is sufficient; optimizing for all edge-case devices is explicitly out of scope for initial delivery.
- **Incremental Delivery**: Prefer a working, tested, minimal implementation over a comprehensive but unfinished one. Ship the smallest valuable slice, then iterate.

**Version**: 0.14.1 | **Ratified**: 2026-02-19 | **Last Amended**: 2026-05-19
