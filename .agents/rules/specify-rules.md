# Chimali Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-10-19

## Active Technologies
- Kotlin 2.x (Kotlin Multiplatform), AGP KMP library + Android application + Koin (DI), Android Bluetooth / PackageManager, `ReentrantLock` / `NSRecursiveLock`, project `tools/local-ci.ps1` (046-kmp-expect-class-migration)
- Kotlin 2.x (Kotlin Multiplatform) + Android Bluetooth APIs (HID capability check), `java.util.concurrent.locks.ReentrantLock` (Android lock), `NSRecursiveLock` (iOS lock) (046-kmp-expect-class-migration)

## Project Structure

```text
src/
tests/
```

## Commands

# Run local CI verification (Ktlint, Detekt, unit tests)
`tools/local-ci.ps1`

## Code Style

Kotlin 2.x (Kotlin Multiplatform): Follow standard conventions and engineering standards below.

## Recent Changes
- 046-kmp-expect-class-migration: Added Kotlin 2.x (Kotlin Multiplatform), AGP KMP library + Android application + Koin (DI), Android Bluetooth / PackageManager, `ReentrantLock` / `NSRecursiveLock`, project `tools/local-ci.ps1`
- 046-kmp-expect-class-migration: Added Kotlin 2.x (Kotlin Multiplatform) + Android Bluetooth APIs (HID capability check), `java.util.concurrent.locks.ReentrantLock` (Android lock), `NSRecursiveLock` (iOS lock)

<!-- MANUAL ADDITIONS START -->

## Engineering Standards & Enforcement Rules

### 1. Development Methodology & Testing
- **Test-Driven Development (TDD)**: TDD is enforced as the standard engineering methodology. All commits must pass the Local CI pipeline (`tools/local-ci.ps1`). Exemptions are permitted only when a test-first approach is demonstrably unfeasible.
- **KMP-Compatible Test Placement**: Platform-neutral business logic, validation, cryptography contracts, and presentation/domain behavior MUST be tested in `commonTest` with `kotlin.test` whenever the production API is available to common source sets. Android/JVM host tests, instrumented tests, and UI tests are reserved for platform APIs, SQLite3MultipleCiphers/SQLDelight integration, Android framework behavior, hardware boundaries, or source sets that cannot run in `commonTest`.
- **Test Doubles**: Fake implementations with working test-specific behavior SHOULD be the default test double for repositories, services, data sources, clocks, dispatchers, and platform boundaries. MockK mocks, stubs, and spies MAY be used when interaction verification is the purpose of the test, when a platform/final type cannot be replaced by a Fake, or when building a Fake would add more complexity than the behavior under test.
- **Tiered Test Coverage Targets**:
  - **Tier 1 Modules**: MUST meet an initial baseline target of ≥ 40% statement and branch coverage using `kotlin.test` for KMP common logic, ratcheted upward over time.
  - **Tier 2 Modules**: MUST maintain a baseline target of ≥ 20% test coverage focusing on ViewModels, business adapters, and state flows.
  - **Global Repository Floor**: Cumulative codebase coverage across all modules combined MUST NOT drop below 20% in automated CI pipelines.
- **Integration Tests**: Comprehensive integration tests MUST verify interaction between Bluetooth HID emulation, Credential Manager, and Encryption layers. UI components MUST be verified using **Compose UI Testing**.
- **Memory Leak Detection**: Debug builds MUST include **LeakCanary**. No feature branch will be integrated if a reproducible memory leak trail is identified during automated instrumented test runs. Leak reports MUST be treated as blocking defects equivalent to test failures.

### 2. Local CI/CD & Enforcement
1. **Local CI Pipeline**: Run `tools/local-ci.ps1` before committing. This pipeline runs static analysis (`Ktlint`, `Detekt`) and all unit tests.
2. **Git Hook Enforcement**: A `pre-commit` git hook is mandatory to prevent accidental commits of broken or untested code. Install via `tools/setup-hooks.ps1`.
3. **Bypass Justification**: Any change that bypasses the Local CI gate (e.g., via `--no-verify`) MUST be documented with a valid justification in the commit message.
4. **R8 / Build Optimization**: `isMinifyEnabled` and `isShrinkResources` MUST be evaluated on every feature branch using a production-matching `release` or `staging` build variant. Branches that disable minification or resource shrinking without documented justification MUST be rejected.
5. **App Size Watchdog**: Release CI pipelines measure AAB base module size against the current baseline. Any branch exceeding the **+500 KB** delta threshold defined in BRD NFR-PERF-060 MUST be blocked until architecture approval is granted and recorded in the commit message.

### 3. DO-178B Principles for Critical Code
To ensure reliability, determinism, and verifiability of safety/operationally-critical components (Biometrics, Master Seed derivation, Cryptographic Operations), all new code MUST adhere to:

#### 3.1 Rigorous Traceability
- **Tier 1 (Critical Traceability)**: Every `public` and `internal` function in Tier 1 modules (`:core:security`, `:core:domain`, `:core:data`, `:feature:fido2`, `:feature:authenticator`) MUST carry a `@Req` annotation referencing one or more stable requirement identifiers from `docs/brd.md` (`FR-*`) or `docs/trd.md` (`NFR-*`, `TR-*`) (e.g., `@Req("FR-AUTH-010", "NFR-SEC-020")`).
- **Forbidden Dead Code**: Dead code, deactivated code, unreferenced helper methods, and hidden features are strictly FORBIDDEN. Any code path that cannot be traced to an approved requirement MUST be excised.
- **Bi-Directional Verification Traceability**: Tests verifying Tier 1 logic MUST explicitly reference the same requirement ID — either in the KDoc `@see` tag or in the test function name (e.g., `` `FR-AUTH-010 deriveDeviceKeyPair returns deterministic output` ``).
- **Tier 2 (Feature Traceability)**: Each Tier 2 module MUST maintain a `README.md` at its root identifying which functional requirements from `docs/brd.md` and technical requirements from `docs/trd.md` it satisfies.

#### 3.2 Determinism & Predictable Execution
- **Zero Allocation in Hot Paths**: Dynamic memory allocation in execution-critical hot paths (biometric processing loops, cryptographic stream operations, low-level HID interrupt dispatching) MUST be avoided. Buffers and mutable structures MUST be pre-allocated and reused.
- **Deterministic Loop Termination**: All loops, polling loops, and retry mechanisms MUST have deterministic, statically bounded exit conditions or explicit timeout thresholds (`withTimeoutOrNull` / deadline constraints). Unbounded loops and open-ended recursion are strictly prohibited.
- **Predictable State Transitions**: Critical state machines MUST have fully defined, deterministic state progressions with exhaustive handling of all valid and invalid inputs.

#### 3.3 High-Coverage Testing & Independence
- **Coverage Enforcement & MC/DC Mindset**: Tier 1 modules MUST enforce coverage gates. Complex decision logic MUST be evaluated with a Modified Condition/Decision Coverage (MC/DC) mindset. A terminal exception branch that an instrumenter cannot credit because it exits directly through `throw` MAY be accepted only when: (a) every nonterminal executable line is covered, (b) an independent test asserts the exact branch input and exception type, and (c) that test verifies every cleanup invariant affected by the exit. The exception MUST be recorded with its source location, test name, and the reason a normal successor probe would alter or obscure production behavior. It MUST NOT apply to recoverable, nonterminal, asynchronous, cancellation, or externally delegated paths.
- **Independent Test Execution**: Verification tests MUST be executed independently of implementation units, operating without shared mutable state to eliminate verification bias and order dependency.
- **"Test What You Fly, Fly What You Test"**: The test environment, test doubles, and execution harness MUST precisely mirror production execution conditions, hardware constraints, and threading characteristics. Simulated harnesses MUST NOT mask production concurrency, latency, or lifecycle failure modes.

#### 3.4 Separation of Concerns (Partitioning)
- **Hardware & Driver Isolation**: Hardware-interfacing code (CameraX, biometric sensors, Bluetooth HID L2CAP sockets) MUST be strictly isolated behind dedicated abstraction boundaries.
- **Fault Containment**: Failures or exceptions in lower-criticality components MUST NOT compromise or crash higher-criticality components.
- **Resource Partitioning**: Long-running background jobs and I/O tasks MUST NOT starve critical execution paths of CPU cycles, memory, or thread pool dispatchers.

#### 3.5 Fail-Safe Error Handling & Graceful Degradation
- **Component Boundary Encapsulation**: Exceptions MUST be caught and handled at component boundaries. Uncaught exceptions MUST NOT escape hardware-interfacing or service boundaries into higher layers.
- **Fail-Secure State**: Upon any hardware failure, sensor disconnection, timeout, or unrecoverable error, the system MUST degrade gracefully into a safe, secure state (abort authentication, zero out sensitive volatile keys, and lock secure storage).
- **Peripheral Resilience**: The application MUST NOT crash due to external device failures, disconnected peripherals, Bluetooth stack resets, USB unplug events, or camera driver stalls.

### 4. Risk Management & Pragmatism Guidelines
- **YAGNI First**: Do not implement features, abstractions, or infrastructure that do not serve an immediate, documented requirement. Speculative future-proofing is debt, not investment.
- **Three-Use Rule**: An abstraction (interface, wrapper, utility) MUST be justified by at least three concrete usage sites or an explicit architectural mandate in the constitution. Single-use abstractions MUST be documented with justification in the introducing spec.
- **Specification Depth**: Specifications MUST document *what* and *why*, not *how*. Implementation details belong in code and code comments, not in spec documents.
- **Simplest Sufficient Solution**: For any design decision, prefer the simplest implementation that satisfies all hard requirements and known constraints.
- **Module Count**: New Gradle modules MUST be justified by a concrete isolation or build-performance benefit.
- **Pattern Justification**: Design patterns MUST be applied to solve a specific problem, not for theoretical adherence.
- **Scope Budgets & Incremental Delivery**: Feature specs MUST include a complexity estimate (S/M/L/XL). XL features MUST be decomposed into independently deliverable increments. Ship the smallest valuable slice, then iterate.

### 5. Coding Conventions & Best Practices

#### 5.1 General Style & Formatting
- **Max Line Length**: 120 characters.
- **Indentation**: 4 spaces for Kotlin/Java/SQL, 2 spaces for XML/JSON/YAML.
- **File Encoding**: UTF-8 without BOM.
- **Trailing Commas**: MUST be used in Kotlin multi-line parameter lists, collection literals, and `when` entries to minimize diff noise.
- **Code Documentation**: All `public` and `internal` declarations (classes, interfaces, functions, properties) MUST have KDoc comments explaining purpose, parameters (`@param`), return values (`@return`), and potential error states (`@throws`). Enforced via Detekt rules (`UndocumentedPublicClass`, `UndocumentedPublicFunction`).
- **Static Analysis**: All code must pass Detekt and Ktlint without warnings. Magic numbers are strictly prohibited; numeric literals with domain significance MUST be extracted into named constants or enums.
- **Import Ordering**: Wildcard imports (`*`) are prohibited. Imports MUST be sorted lexicographically with no blank-line separation between groups (enforced by Ktlint).
- **File Naming**: Kotlin source files MUST be named after the primary public class/interface (e.g., `VaultRepository.kt`). Files containing only top-level extension functions MUST use a descriptive plural noun (e.g., `ByteArrayExtensions.kt`).

#### 5.2 Kotlin & KMP Guidelines
- **Naming Conventions**:
  - Classes, Interfaces, Objects: `PascalCase` (e.g., `CredentialRepository`).
  - Functions, Properties, Variables: `camelCase` (e.g., `deriveMasterKey`).
  - Constants (`const val`) and Enum entries: `UPPER_SNAKE_CASE` (e.g., `MAX_RETRY_COUNT`).
  - Top-level `val` singletons: `PascalCase` for type-like values, `camelCase` otherwise.
  - Packages: `lowercase` without underscores (e.g., `com.chimali.feature.vault`).
  - Backing Properties: Prefix with `_` (e.g., `_state` for mutable state, `state` for public immutable state).
  - Type Aliases: `PascalCase`, descriptive (e.g., `typealias CredentialId = String`).
  - Test Functions: Use backtick-enclosed descriptive names (e.g., `` `deriveMasterKey returns deterministic output` ``).
- **Idioms & Best Practices**:
  - Prefer immutable data structures (`val` over `var`, `List` over `MutableList`).
  - Use sealed classes/interfaces for exhaustive `when` statements (no `else` branch).
  - Leverage coroutines and `Flow` for asynchronous operations. Never block the main/UI thread.
  - Use `Outcome<D, E : DomainError>` as the mandatory standard for domain error handling. Exceptions MUST be reserved exclusively for unrecoverable technical failures.
  - **Input Validation**: All user inputs MUST be strictly validated before processing or persisting. Apply `require()` and `check()` for aggressive input validation and state verification. Fail fast.
  - **Structured Concurrency**: All coroutine launches MUST be scoped to a lifecycle-aware scope. Unscoped `GlobalScope.launch` is prohibited.
  - **Null Safety**: Prefer `?.let {}` and `?:` over `if (x != null)` blocks. Non-null assertions (`!!`) are prohibited except in test code with explicit justification.
  - **Memory Safety**: Application-owned sensitive data MUST use mutable structures and explicit terminal cleanup under Constitution Principle I. Operation-owned secrets MUST be zeroed in `try/finally` even on exceptions.
- **Performance Guidelines**:
  - Avoid unnecessary object allocations in tight loops. Use `value class` to wrap primitives without allocation overhead.
  - Use `Sequence` for multi-step operations on large collections (>100 elements).
  - Prefer `buildList`, `buildMap`, `buildString` over mutable-collection-plus-loop patterns.

#### 5.3 SQL & Database Guidelines (SQLDelight & SQLite3MultipleCiphers)
- **Naming Conventions**:
  - Tables: `snake_case`, singular (e.g., `vault_item`, `credential`).
  - Columns: `snake_case` (e.g., `created_at`, `master_seed`).
  - Indices: `idx_<table>_<column(s)>` (e.g., `idx_credential_rp_id`).
  - Foreign Keys: referenced table with `_id` suffix (e.g., `vault_item_id`).
  - SQLDelight Queries: `camelCase` (e.g., `getVaultItemById`).
  - Migration Files: `<version_number>.sqm` (e.g., `2.sqm`, `3.sqm`).
- **Column Ordering**: Primary key column(s) → Audit/temporal columns (`created_at`, `last_used_at`, `updated_at`) → Remaining columns (alphabetized).
- **Idioms & Best Practices**:
  - Use prepared statements and bind variables (native to SQLDelight) to prevent SQL injection.
  - Keep business logic out of the database; no stored procedures or complex computed columns.
  - Use explicit `transaction { }` blocks for multiple related writes.
  - Schema changes MUST be backwards-compatible or accompanied by migration scripts. Column drops MUST be staged across two releases (deprecate → drop).
  - All tables MUST include `created_at` and `updated_at` `INTEGER` columns (Unix epoch ms).
- **Performance Guidelines**:
  - Index frequently queried columns and foreign keys. Exact-match indexing on encrypted fields requires HMAC blind indexes.
  - Avoid `SELECT *`. Explicitly select required columns only.
  - Run database operations on `Dispatchers.IO`. Wrap bulk operations in a single transaction.

#### 5.4 Compose & UI Guidelines
- **Stability**: Composable functions MUST receive stable or immutable parameters. Use `@Immutable` or `@Stable` for custom data classes where appropriate.
- **Recomposition Minimization**: Hoist state to the lowest-common-ancestor composable.
- **Side Effects**: Use `LaunchedEffect` for suspend functions, `DisposableEffect` for cleanup, `SideEffect` for non-suspend post-recomposition actions.
- **Lists**: Use `LazyColumn`/`LazyRow` with stable `key` parameters for all dynamic lists. Never use `Column`/`Row` with `forEach` for lists exceeding 10 items.

#### 5.5 Cryptographic Code Guidelines
- **Zeroing**: All byte arrays containing key material, seeds, blinding factors, or derived secrets MUST be zeroed in a `finally` block immediately after use. Non-negotiable.
- **Plaintext Ownership**: Cryptographic payloads and operation copies MUST have explicit mutable owners and failure/cancellation cleanup. UI text exceptions MUST NOT apply within cryptographic processing.
- **Constant-Time Comparisons**: HMAC tags, signatures, and credential IDs MUST be compared using `MessageDigest.isEqual()` or equivalent constant-time comparison to prevent timing attacks.
- **SecureRandom**: Security-sensitive randomness (fresh nonces, IVs) MUST use `java.security.SecureRandom`. A fixed public salt or context required or permitted by a published deterministic KDF (e.g., a named HKDF profile) MAY be used only as a public domain-separation input through an approved, named, versioned derivation profile with documented byte encoding and domain separation. This exception does not authorize treating the salt/context as secret input, nonce reuse, or replacement of randomness where the governing construction requires fresh random values. Using `kotlin.random.Random` or `java.util.Random` for any security-sensitive value is prohibited.
- **Domain Separation**: Context strings and DST values MUST be defined as named constants referencing specification sections.
- **No Cryptographic Invention**: All cryptographic constructions MUST conform to published standards (IETF RFC/Draft, NIST SP, W3C). Custom cryptographic protocols are prohibited without formal security review.

#### 5.6 CBOR, Binary & Wire Protocol Guidelines
- **Encoding**: CTAP2 messages MUST use canonical CBOR encoding (RFC 8949 §4.2.1). Map keys MUST be integers sorted in ascending order.
- **Negative Integers**: CBOR negative integer encoding MUST use major type 1 (e.g., -1 is encoded as `0x20`).
- **Base64**: Wire-level credential IDs and public keys MUST use Base64URL without padding (RFC 4648 §5).
- **Byte Ordering**: Multi-byte integers in HID reports and CTAP2 frames MUST be big-endian unless protocol explicitly specifies little-endian.

#### 5.7 Anti-Patterns & Prohibited Practices
The following patterns are strictly prohibited in production code:
- **God Classes**: No class may exceed 500 lines of code (excluding comments and blank lines).
- **Deep Inheritance**: Inheritance depth MUST NOT exceed 3 levels. Prefer composition.
- **Wildcard Catch**: `catch (e: Exception)` or `catch (e: Throwable)` is prohibited in production code. Broad catches are permitted only at coroutine boundary supervisors with mandatory logging.
- **Mutable Shared State**: Shared mutable state between coroutines MUST use `Mutex`, `StateFlow`, or `Channel`. Direct mutable variable access from multiple coroutines is prohibited.
- **String Concatenation in Loops**: Use `StringBuilder` or `buildString`.
- **Hardcoded Secrets**: API keys, encryption keys, or credentials MUST NOT appear as string literals in source code.
- **Uncompressed Raster Assets**: All static images MUST use Vector Drawables (`.xml`) or compressed WebP format. Raw `.png` or `.jpeg` drawable files are prohibited without documented justification.
- **Large Heap Flag**: `android:largeHeap="true"` in the Android manifest is strictly prohibited. Memory optimization through bounded caches and lifecycle-aware resource management is mandatory.
- **Unbound In-Memory Singletons**: Singletons holding dynamic collections MUST implement bounded cache eviction (e.g., `LruCache`). Global unbounded collections are prohibited.

<!-- MANUAL ADDITIONS END -->
