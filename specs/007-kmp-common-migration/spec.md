# Feature Specification: KMP Common Module Migration

**Feature Branch**: `007-kmp-common-migration`  
**Created**: 2026-04-20  
**Status**: Draft  
**Input**: User description: "Migrate 'core:common' module to KMP compatible common module."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Shared Utilities Access (Priority: P1)

As a developer, I want to access common utility interfaces (like `ClipboardManagerService`) and infrastructure (like `Fido2EventBus`) from the `commonMain` source set of any feature module, so that I can write platform-agnostic business logic.

**Why this priority**: This is the fundamental requirement for multiplatform code sharing. Without it, the core infrastructure remains siloed in Android-specific modules.

**Independent Test**: Can be fully tested by creating a dummy class in a different module's `commonMain` that imports and utilizes `ClipboardManagerService` or `Fido2EventBus` from `core:common`.

**Acceptance Scenarios**:

1. **Given** `core:common` is migrated to KMP, **When** I import `ClipboardManagerService` into a `commonMain` source set of another module, **Then** the code compiles successfully for all targets.
2. **Given** `core:common` is migrated to KMP, **When** I run the application on Android, **Then** the `AndroidClipboardManagerService` continues to function as expected.

---

### User Story 2 - Cross-Platform DI Configuration (Priority: P1)

As a developer, I want to define and use dependency injection qualifiers (like `DispatcherQualifiers`) in a platform-agnostic way, so that I can provide platform-specific implementations (like IO or Main dispatchers) without leaking platform details into common code.

**Why this priority**: Essential for maintaining a clean architecture where common logic depends on abstractions, and platform-specific implementations are provided at the edge.

**Independent Test**: Verify that `DispatcherQualifiers` can be used in `commonMain` to inject `CoroutineDispatcher` instances that are resolved to platform-appropriate threads at runtime.

**Acceptance Scenarios**:

1. **Given** the common DI module is available, **When** I use `@Named(DispatcherQualifiers.IO)` in a common service, **Then** the Android implementation receives `Dispatchers.IO` and the iOS implementation receives a background dispatcher.

---

### Edge Cases

- What happens when a platform-specific API (like Android's `ClipboardManager`) is accessed from a non-Android target? The system MUST provide a placeholder or throw a clear "not implemented" error if no actual implementation exists yet for that platform.
- How does the system handle platform-specific dependencies (like `androidx.core:core-ktx`) in a KMP module? These MUST be moved to the `androidMain` dependency block to avoid polluting common code.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `core:common` MUST be refactored from an Android Library to a Kotlin Multiplatform Library.
- **FR-002**: The `ClipboardManagerService` interface and `Fido2EventBus` MUST be moved to `commonMain`.
- **FR-003**: `AndroidClipboardManagerService` MUST remain in a platform-specific source set (`androidMain`) and implement the common interface.
- **FR-004**: `DispatcherQualifiers` and the base `DispatchersModule` configuration MUST be moved to `commonMain`.
- **FR-005**: All unit tests that do not depend on Android runtime MUST be moved to `commonTest`.
- **FR-006**: The module MUST define `androidTarget()` and `ios()` (or specific iOS targets like `iosArm64()`, `iosSimulatorArm64()`).

### Key Entities *(include if feature involves data)*

- **ClipboardManagerService**: Interface defining platform-agnostic clipboard operations.
- **Fido2EventBus**: A shared reactive stream for broadcasting events across feature boundaries.
- **DispatcherQualifiers**: A set of markers used to distinguish between different types of coroutine dispatchers (IO, Main, Default).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `core:common` compiles successfully for Android (`compileDebugKotlinAndroid`) and iOS (`compileKotlinIosArm64`).
- **SC-002**: Existing unit tests in `core:common` pass successfully when run via `./gradlew :core:common:test`.
- **SC-003**: Zero references to `android.*` or `androidx.*` exist within the `commonMain` source set of `core:common`.
- **SC-004**: The module's `build.gradle.kts` uses the `kotlin("multiplatform")` plugin and defines appropriate source sets.

## Assumptions

- The project's existing Kotlin version and Gradle configuration support the `kotlin("multiplatform")` plugin version 2.0+.
- iOS-specific implementations for `ClipboardManagerService` are NOT required in this migration phase but can be added as placeholders.
- `kotlinx-coroutines` multiplatform dependency is available and will replace `kotlinx-coroutines-android` in common code.
