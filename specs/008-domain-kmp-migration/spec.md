# Feature Specification: KMP Domain Module Migration

**Feature Branch**: `008-domain-kmp-migration`  
**Created**: 2026-04-21  
**Status**: Draft  
**Input**: User description: "Migrate core:domain module to a KMP compatible module."

## Clarifications

### Session 2026-04-21
- Q: Does the migration include moving any existing UseCases from feature modules? → A: Yes, current Android code should be refactored and migrated to the common module if feasible for reuse across platforms.
- Q: Should we include common domain-level libraries like kotlinx-serialization or kotlinx-datetime in the initial configuration? → A: Core only (Coroutines + Koin Annotations).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Multiplatform Domain Logic Support (Priority: P1)

As a Chimali developer, I want to define domain models and business logic once in the domain module so that I can reuse them across Android and iOS targets without duplication.

**Why this priority**: Core architectural alignment with KMP strategy. Without this, business logic remains fragmented or duplicated.

**Independent Test**: Can be tested by creating a dummy Kotlin class in `core:domain/src/commonMain` and verifying it can be accessed from both `androidMain` and `iosMain` (or by running a multi-platform compilation task).

**Acceptance Scenarios**:

1. **Given** the `core:domain` module, **When** I define a data class in `commonMain`, **Then** it must be visible to both Android and iOS source sets.
2. **Given** the `core:domain` module, **When** I run the build for iOS and Android, **Then** the module must compile successfully for both platforms.

---

### User Story 2 - Clean Architecture Preservation (Priority: P2)

As an architect, I want the domain module to be free of platform-specific dependencies so that it remains pure business logic and independent of hardware or UI frameworks.

**Why this priority**: Ensures the "Security First" and "Clean Architecture" principles (Constitution §III) are maintained.

**Independent Test**: Can be verified by checking that `commonMain` in `core:domain` does not contain any `android.*` or `java.*` imports.

**Acceptance Scenarios**:

1. **Given** a UseCase in `core:domain`, **When** I attempt to import an Android-specific class, **Then** the compiler must reject it.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The `core:domain` module MUST be transformed into a Kotlin Multiplatform (KMP) module.
- **FR-002**: The module MUST support the following targets: Android (via `androidTarget()`), iOS Arm64, and iOS Simulator Arm64.
- **FR-003**: The module MUST provide a `commonMain` source set for shared domain models, use case interfaces, and repository definitions.
- **FR-004**: The `core:domain` module MUST integrate with Koin Annotations using target-specific KSP processors to ensure multiplatform DI compatibility.
- **FR-005**: All existing Android-specific dependencies in `core:domain` MUST be moved to `androidMain` or replaced with multiplatform equivalents.
- **FR-006**: Existing Android business logic MUST be refactored and migrated to `commonMain` where feasible, ensuring it is KMP compatible and reusable across other platforms.

### Key Entities *(include if feature involves data)*

- **UseCase**: Represents a unit of business logic.
- **DomainModel**: Platform-agnostic data representation.
- **Credential**: Core domain entity representing a user's stored credential.
- **Passkey**: Core domain entity representing a FIDO2 passkey.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `core:domain` successfully compiles for Android (`./gradlew :core:domain:assembleDebug`).
- **SC-002**: `core:domain` successfully compiles for iOS targets (`./gradlew :core:domain:iosArm64MainKlibrary`).
- **SC-003**: Zero `android.*` or `java.*` imports in the `commonMain` source set of `core:domain`.

## Assumptions

- The current `core:domain` module is largely empty or contains logic that can be easily translated to pure Kotlin.
- Platform-specific implementations (e.g., using Android KeyStore or iOS Keychain) will reside in other modules (like `core:security`) or in platform-specific source sets, keeping the domain interfaces pure.
- `kotlinx-coroutines` will be the primary mechanism for asynchronous logic in UseCases.
