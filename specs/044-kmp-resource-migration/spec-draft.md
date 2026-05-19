# Feature Specification: KMP Resource Migration (BIP39 Wordlist)

**Feature Branch**: `[044-kmp-resource-migration]`  
**Created**: 2026-05-16  
**Status**: Draft  
**Input**: User description: "Please write a document for the migration of bip39_english to the recommended KMP archetecture commonMain/resources"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Standardize KMP Asset Packaging (Priority: P1)

As a Kotlin Multiplatform developer, I want all shared domain/security resources to live in `commonMain/resources` rather than platform-specific source sets (`androidMain`), so that future platform targets (iOS, Desktop) can reuse the identical cryptographic resources without duplication or relying on platform-specific APIs.

**Why this priority**: Correcting the architectural location of the `bip39_english.txt` file is critical to unblocking iOS target development and adhering to KMP best practices. It natively drops the Android `AssetManager` requirement which drastically improves unit testability.

**Independent Test**: Can be fully tested by verifying that `Bip39MasterSeedGenerator` correctly resolves the resource at runtime on Android and that local JVM tests pass without mocking `android.content.Context`.

**Acceptance Scenarios**:

1. **Given** the application requires mnemonic generation, **When** the `Bip39MasterSeedGenerator` initializes its wordlist, **Then** it successfully reads `bip39_english.txt` from the `commonMain/resources` JVM classpath.
2. **Given** a multiplatform build configuration, **When** the `core:security` module is compiled, **Then** the `bip39_english.txt` file is packaged appropriately for all compiled targets.

---

### Edge Cases

- What happens when the JVM classloader cannot resolve the resource stream (e.g., corrupted packaging)? The system should fail fast with a descriptive `IllegalStateException` or `error("Missing bip39_english.txt resource")` to prevent silent cryptographic failures.
- How does the system handle different encoding environments? The resource reader must strictly enforce `UTF-8` encoding and filter out any blank lines or carriage returns (`\r\n` to `\n`) to ensure the wordlist strictly contains 2048 words.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST store the `bip39_english.txt` asset inside the `core/security/src/commonMain/resources` directory.
- **FR-002**: The `Bip39MasterSeedGenerator` MUST read the wordlist utilizing an `expect`/`actual` function mechanism to support reading JVM resources in `androidMain` and Native resources in `iosMain`.
- **FR-003**: The Android implementation (`androidMain`) MUST read the file utilizing standard `ClassLoader.getResourceAsStream()`.
- **FR-004**: The system MUST validate that the loaded wordlist contains exactly 2048 words, ignoring any whitespace/blank lines.

### Key Entities

- **Bip39MasterSeedGenerator**: The core cryptographic entity responsible for parsing the resource stream and producing compliant BIP39 mnemonics.
- **bip39_english.txt**: The 2048-word IETF/BIP39 standard dictionary.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `core:security` Android instrumentation tests and local unit tests pass at a 100% success rate without requiring Robolectric or `android.content.Context` mocking.
- **SC-002**: The resulting Android App Bundle (AAB) / APK successfully includes `bip39_english.txt` at its root classpath.
- **SC-003**: The project conforms to the `.specify/memory/constitution.md` definition of multiplatform purity (i.e. minimal Android-only dependencies for domain logic).

## Assumptions

- The project uses standard KMP resource bundling plugins or native `ClassLoader` mechanisms for Android. iOS implementation of the resource reader will be handled via NSBundle or a shared resource library like moko-resources in the future, meaning the `expect/actual` pattern is sufficient for this migration.
- `bip39_english.txt` does not need runtime updates and remains statically packaged.
