# Feature Specification: KMP Resource Migration (BIP39 Wordlist)

**Feature Branch**: `[044-kmp-resource-migration]`  
**Created**: 2026-05-16  
**Status**: Draft  
**Input**: User description: "Migrate the bip39_english.txt wordlist from a platform-specific location to the shared multiplatform resource directory so that all targets can access the same canonical dictionary without platform-specific loading mechanisms."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Share Cryptographic Wordlist Across All Platforms (Priority: P1)

As a Kotlin Multiplatform developer, I want the BIP39 English wordlist to reside in a shared, platform-neutral location so that every compilation target (Android, iOS, Desktop) has access to the identical canonical dictionary without duplication or platform-specific asset loading.

**Why this priority**: The wordlist is a foundational cryptographic resource for mnemonic seed generation. Keeping it in a platform-specific location blocks future target expansion and violates the project's multiplatform purity principle (Constitution §III, §X.2 KMP Guidelines). Correcting the location is the prerequisite for any cross-platform seed generation work.

**Independent Test**: Can be fully tested by confirming that the mnemonic generator successfully loads the wordlist and produces valid BIP39 mnemonics on Android (instrumentation and local unit tests) without requiring platform-specific test infrastructure.

**Acceptance Scenarios**:

1. **Given** the application requires mnemonic generation, **When** the seed generator initializes its wordlist, **Then** it successfully reads the BIP39 English dictionary from the shared multiplatform resource location.
2. **Given** a multiplatform build configuration, **When** the security module is compiled for any supported target, **Then** the BIP39 English dictionary is packaged and accessible at runtime for that target.
3. **Given** the wordlist file is missing or corrupted at runtime, **When** the seed generator attempts to load it, **Then** the system fails immediately with a descriptive error message preventing silent cryptographic failures.

---

### User Story 2 - Eliminate Platform-Specific Test Dependencies (Priority: P2)

As a developer writing tests for mnemonic generation, I want to run unit tests for the seed generator on a standard JVM without needing platform-specific mocks or test runners, so that the feedback loop is fast and the test infrastructure remains simple.

**Why this priority**: Today, testing the seed generator requires platform-specific context mocking. Moving the resource to a shared location removes this coupling, enabling plain unit tests and improving developer productivity.

**Independent Test**: Can be verified by running the existing seed generation test suite as local JVM tests and confirming 100% pass rate without any platform-specific mocking framework.

**Acceptance Scenarios**:

1. **Given** the wordlist is in the shared resource location, **When** a developer runs local unit tests for the security module, **Then** all seed generation tests pass without platform-specific context or mocking.
2. **Given** the migration is complete, **When** the full local CI pipeline runs, **Then** no test requires platform-specific mocking infrastructure to access the wordlist.

---

### Edge Cases

- What happens when the resource file cannot be found at runtime (e.g., corrupted packaging or misconfigured build)? The system must fail immediately with a clear, descriptive error to prevent silent fallback to an empty or incorrect wordlist.
- How does the system handle text encoding differences across platforms? The resource reader must enforce consistent encoding and normalize line endings so that the parsed wordlist always contains exactly 2048 words regardless of the host platform.
- What happens if the wordlist file is present but contains an unexpected number of words (e.g., truncated file)? The system must validate the word count and reject any wordlist that does not contain exactly 2048 entries.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The BIP39 English wordlist MUST be stored in the shared multiplatform resource directory of the security module, accessible to all compilation targets without platform-specific asset loading.
- **FR-002**: The seed generator MUST load the wordlist through a platform-abstracted resource loading mechanism, with each supported target providing its own implementation for resource resolution.
- **FR-003**: The system MUST validate that the loaded wordlist contains exactly 2048 words after filtering out blank lines and normalizing line endings.
- **FR-004**: The system MUST fail immediately with a descriptive error if the wordlist resource cannot be located or loaded at runtime, preventing any silent cryptographic failure.
- **FR-005**: The wordlist MUST be read using strict character encoding to guarantee consistent parsing across all platforms and environments.
- **FR-006**: After migration, the original platform-specific copy of the wordlist MUST be removed to prevent resource duplication and ambiguity about the canonical source.

### Key Entities

- **BIP39 English Wordlist**: The 2048-word standard dictionary defined by the BIP39 specification, used as the canonical source for mnemonic seed phrase generation.
- **Seed Generator**: The core cryptographic component responsible for loading the wordlist and producing compliant BIP39 mnemonic phrases from entropy.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All security module tests (instrumentation and local unit) pass at a 100% success rate without requiring any platform-specific mocking or test runner infrastructure for wordlist access.
- **SC-002**: The BIP39 English wordlist is included in the final application package for every compiled target and is resolvable at runtime.
- **SC-003**: The project's security module contains zero platform-specific dependencies for loading the BIP39 wordlist, conforming to the multiplatform purity mandate in the project constitution.
- **SC-004**: The full local CI pipeline completes successfully with no regressions after the migration.
- **SC-005**: No duplicate copies of the BIP39 wordlist exist in platform-specific resource directories after the migration is complete.

## Assumptions

- The BIP39 English wordlist is a static, read-only resource that does not require runtime updates or over-the-air modification.
- The project's build system supports packaging shared resources from the common source set into all compiled targets.
- Only the Android target implementation exists today; iOS and Desktop resource loading will be implemented in future features when those targets are added. The migration establishes the shared resource location and the platform-abstraction contract as a prerequisite.
- The existing seed generation test suite provides adequate coverage to validate that the migration produces no behavioral regression.

**Complexity Estimate**: S (Small) — Single resource relocation with platform-abstraction layer and test validation.
