# Tasks: KMP Domain Module Migration

**Input**: Design documents from `/specs/008-domain-kmp-migration/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and KMP build configuration

- [X] T001 Configure core:domain/build.gradle.kts for KMP and KSP with target-specific processors

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [X] T002 Create KMP source directory structure (commonMain, androidMain, iosMain) in core/domain/src/

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Multiplatform Domain Logic Support (Priority: P1) 🎯 MVP

**Goal**: Enable shared business logic definitions in commonMain

**Independent Test**: Verify that classes in commonMain are visible to platform source sets

### Implementation for User Story 1

- [X] T003 [P] [US1] Create base UseCase interface in core/domain/src/commonMain/kotlin/com/chimali/core/domain/usecase/UseCase.kt
- [X] T004 [P] [US1] Create base DomainModel placeholder in core/domain/src/commonMain/kotlin/com/chimali/core/domain/model/DomainModel.kt

**Checkpoint**: User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Clean Architecture Preservation (Priority: P2)

**Goal**: Ensure domain purity and platform independence

**Independent Test**: Verify zero platform-specific imports in commonMain

### Implementation for User Story 2

- [X] T005 [P] [US2] Clean up unused Android-only dependencies from core/domain/build.gradle.kts
- [X] T006 [US2] Move any residual Android-specific code to androidMain (if found during migration)

**Checkpoint**: User Stories 1 AND 2 should both work independently

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T007 [P] Verify Android compilation with ./gradlew :core:domain:assembleDebug
- [X] T008 [P] Verify iOS compilation with ./gradlew :core:domain:iosArm64MainKlibrary
- [X] T009 [P] Update module documentation and Run quickstart.md validation

---

# Part 2: Domain Layer Implementation

**Input**: `domain-kmp-migration-plan.md`

## Phase 6: Foundation Enhancement (High Priority)

- [X] T101 [P] Create `BaseUseCase` abstract classes (`BaseUseCase`, `BaseUseCaseNoParams`, `BaseUseCaseIn`) in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/usecase/BaseUseCase.kt`
- [X] T102 [P] Create Value Objects (`CredentialId`, `PasskeyId`, `EncryptedString`, `CredentialCategory`, `CredentialTag`) in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/valueobject/ValueObjects.kt`
- [X] T103 Create `Credential` domain model in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/model/Credential.kt` (depends on T102)
- [X] T104 Create `Passkey` domain model in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/model/Passkey.kt` (depends on T102)

## Phase 7: Repository Layer (High Priority)

- [X] T201 [P] Create `CredentialRepository` interface in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/repository/CredentialRepository.kt` (depends on T103)
- [X] T202 [P] Create `PasskeyRepository` interface in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/repository/PasskeyRepository.kt` (depends on T104)

## Phase 8: Business Logic Use Cases & Validation (High Priority)

- [X] T301 [P] Create `DomainException` hierarchy in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/exception/DomainException.kt`
- [X] T302 [P] Create `Validator` interface and `CredentialValidator` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/validation/Validator.kt`
- [X] T303 Create Credential UseCases (`GetCredentialsUseCase`, `SearchCredentialsUseCase`, `SaveCredentialUseCase`, `CopyCredentialToClipboardUseCase`) with `@Factory` annotations in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/usecase/credential/` (depends on T201, T302)
- [X] T304 Create Passkey UseCases (`GetPasskeysUseCase`, `CreatePasskeyUseCase`) with `@Factory` annotations in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/usecase/passkey/` (depends on T202)

## Phase 9: Cross-Cutting Concerns

- [X] T401 [P] Implement `TimeProvider` using `kotlinx-datetime` and `@Single` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/time/TimeProvider.kt`
- [X] T402 [P] Create `DomainModule` with `@Module` and `@ComponentScan` in `core/domain/src/commonMain/kotlin/com/chimali/core/domain/di/DomainModule.kt`

## Phase 10: Testing Infrastructure (Medium Priority)

- [X] T501 [P] Create `FakeCredentialRepository` and `TestDataFactory` in `core/domain/src/commonTest/kotlin/com/chimali/core/domain/`

## Phase 11: Polish

- [X] T601 Compile and run unit tests for `core:domain` using `kotlin-test` to verify dependency wiring and syntax (`./gradlew :core:domain:allTests`).
