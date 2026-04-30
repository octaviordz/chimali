# Feature Specification: Shared Domain Library

**Feature Branch**: `[031-shared-domain-library]`  
**Created**: 2026-04-30  
**Status**: Draft  
**Input**: User description: "Feed @[specs/shared-domain-library.md]to @[/speckit-specify]"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Centralized Domain Identity Types (Priority: P1)

Developers must be able to use strongly-typed identifiers (CredentialId, RpId, UserId, PasskeyId) across all feature modules to ensure type safety and eliminate bugs caused by primitive obsession.

**Why this priority**: Replacing primitive Strings with strongly-typed value classes prevents cross-module integration bugs and ensures type correctness throughout the codebase.

**Independent Test**: Can be tested independently by replacing `String` with the new types in domain models and ensuring the core domain logic compiles and validates constraints.

**Acceptance Scenarios**:

1. **Given** a new credential creation request, **When** generating an identifier, **Then** a cryptographically secure random `CredentialId` is generated using `org.kotlincrypto.random.CryptoRand`.
2. **Given** a data operation, **When** an ID is passed between modules, **Then** the type signature enforces the correct type (`CredentialId`, `RpId`, `UserId`) instead of allowing a raw `String`.

---

### User Story 2 - Platform-Agnostic Domain Models (Priority: P2)

Developers must be able to use shared domain models (`RelyingParty`, `CredentialSummary`, `UserConsentRecord`) across different Kotlin Multiplatform targets (Android, iOS) without relying on JVM-specific APIs.

**Why this priority**: Multiplatform compliance is essential for the `core:domain` module. Removing dependencies like `java.time` and `java.net.URI` enables the codebase to run natively on all KMP targets.

**Independent Test**: Can be tested independently by compiling the `core:domain` module for non-JVM targets (like iOS/Native) and ensuring no JVM-specific unresolved references exist.

**Acceptance Scenarios**:

1. **Given** the `RelyingParty` model, **When** compiled for iOS, **Then** it compiles successfully using `kotlinx.datetime` and KMP-safe structures.
2. **Given** domain objects crossing module boundaries, **When** serialized, **Then** they serialize successfully using `kotlinx.serialization`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide strongly-typed value classes for domain entities (`CredentialId`, `RpId`, `UserId`, `PasskeyId`) within the `core:domain` module.
- **FR-002**: System MUST generate cryptographically secure identifiers using `org.kotlincrypto.random.CryptoRand` provided by the `signum` dependency.
- **FR-003**: System MUST eliminate all dependencies on JVM-specific APIs (`java.time`, `java.net.URI`) in domain models.
- **FR-004**: System MUST use `kotlinx-datetime` and `kotlinx-serialization` for time handling and serialization in the domain layer.
- **FR-005**: System MUST validate its state upon initialization using self-validating data/value classes.
- **FR-006**: System MUST enforce immutability across all domain models.

### Key Entities *(include if feature involves data)*

- **`CredentialId`**: A strongly-typed wrapper for credential identifiers with cryptographically secure generation capabilities.
- **`RpId`**: A strongly-typed wrapper for Relying Party identifiers.
- **`UserId`**: A strongly-typed wrapper for User identifiers.
- **`PasskeyId`**: Unique identifier for the passkey entry in the vault.
- **`RelyingParty`**: Represents a relying party entity without JVM-specific data types.
- **`CredentialSummary`**: A lightweight projection for list displays, containing ID, Title, and LastUsed metadata.
- **`UserConsentRecord`**: Represents a global audit log or consent management record.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero usages of raw `String` for IDs across all domain and repository interfaces.
- **SC-002**: Zero dependencies on `java.*` packages within the `core:domain` module.
- **SC-003**: All domain models are successfully serializable and deserializable.
- **SC-004**: `core:domain` successfully compiles for all configured KMP targets without platform-specific errors.

## Assumptions

- The `signum` library dependency is available to the `core:domain` module and provides `org.kotlincrypto.random.CryptoRand`.
- The existing FIDO2 logic can be safely migrated to use the new KMP-safe types without introducing regressions.
