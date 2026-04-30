# Specification: Shared Domain Library Evolution

## 1. Objective
To unify the business logic and domain entities of the Chimali project into a single, platform-agnostic (KMP) source of truth. This prevents duplication (e.g., `CredentialId` existing in both `:core:domain` and `:feature:fido2`) and enforces type safety across architectural layers.

## 2. Current State Assessment
*   **Module**: `:core:domain` exists but is underutilized by feature modules.
*   **Duplication**: `CredentialId` is currently defined as a primitive-wrapped value class in both `:core:domain` (simple) and `:feature:fido2` (with generation logic).
*   **Primitive Obsession**: Repository interfaces (like `CredentialRepository`) and Use Cases currently rely on `String` for critical identifiers (`credentialId`, `rpId`, `userId`), leading to potential bugs and lack of clarity.

## 3. Architectural Principles
1.  **Platform Agnostic**: All shared domain models must use Kotlin Multiplatform-safe types (e.g., `kotlinx.datetime.Instant` instead of `java.time.Instant`).
2.  **Immutability**: All domain models must be immutable `data class` or `value class` types.
3.  **Self-Validation**: Models should validate their state upon initialization (`init { validate() }`).
4.  **Serialization-Ready**: Use `@Serializable` to allow domain objects to be passed across module boundaries or stored easily.

## 4. Key Components to Implement/Migrate

### 4.1. Unified Value Objects (`core:domain:valueobject`)
Migrate and expand the existing value objects to replace raw strings:
*   **`CredentialId`**:
    *   Unified implementation in `:core:domain`.
    *   Include a KMP-safe `generate()` method (using `org.kotlincrypto.random.CryptoRand` provided by the existing `signum` dependency).
*   **`RpId`**: Value class for Relying Party identifiers (e.g., "example.com").
*   **`UserId`**: Value class for User identifiers (Internal UUIDs or FIDO2 user handles).
*   **`PasskeyId`**: Unique identifier for the passkey entry in the vault.

### 4.2. Shared Domain Models (`core:domain:model`)
*   **`RelyingParty`**: Move from `fido2` to `core:domain`. Refactor to remove `java.net.URI` and `java.time.Instant` dependencies.
*   **`CredentialSummary`**: A lightweight projection for list displays, containing only the metadata required for selection and display (Id, Title/User, LastUsed).
*   **`UserConsentRecord`**: If you plan to have a global audit log or consent management UI outside of the FIDO2 flow.

## 5. Migration Roadmap

### Phase 1: Core Domain Strengthening
1.  Update `:core:domain` dependencies to include `kotlinx-datetime` and `kotlinx-serialization`.
2.  Enhance `CredentialId` with the generation logic currently trapped in `feature:fido2`.
3.  Define `RpId` and `UserId` value classes.

### Phase 2: Feature Integration (`fido2`)
1.  Add `:core:domain` as a dependency to `feature:fido2`.
2.  Refactor `CredentialRepository` interface:
    *   `getCredentialById(credentialId: String)` → `getCredentialById(id: CredentialId)`
    *   `getCredentialsByRpId(rpId: String)` → `getCredentialsByRpId(rpId: RpId)`
3.  Delete the duplicate `CredentialId` in `feature:fido2`.

### Phase 3: Feature Integration (`vault`)
1.  Refactor `VaultService` and its models to use the shared `CredentialId` and `UserId`.
2.  Unify the "Password" credential and "Passkey" credential under a common sealed hierarchy if applicable.

## 6. Validation & Quality Standards
*   **Static Analysis**: Zero `TooGenericExceptionCaught` or `MagicNumber` violations in the shared library.
*   **Unit Tests**: 100% coverage for Value Object validation logic.
*   **Binary Compatibility**: Ensure `@JvmInline` is used correctly to maintain performance parity with primitive types.
