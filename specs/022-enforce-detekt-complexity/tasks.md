# Tasks: Enforce Detekt Complexity Rules

## Phase 1: Foundational / Setup Tasks

- [X] T001 Enforce `CognitiveComplexMethod` threshold of 40 in `config/detekt/detekt.yml`

## Phase 2: User Story 1 - Maintainable Codebase

**Goal:** Ensure code complexity remains manageable so that the codebase is easier to read, maintain, and review without introducing logic changes.
**Independent Test:** Can be fully tested by running Detekt to verify no methods exceed the newly defined threshold.

- [X] T002 [P] [US1] Refactor `buildLegibilityAnnotatedString` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/components/LegibleSecretText.kt`
- [X] T003 [P] [US1] Refactor `PasswordDetailScreen` in `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordDetailScreen.kt`
- [X] T004 [P] [US1] Refactor `registerApp` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth/BluetoothHidDeviceWrapper.kt`
- [X] T005 [P] [US1] Refactor `CredentialListScreen` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialListScreen.kt`
- [X] T006 [P] [US1] Refactor `DevelopmentToolsContent` in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt`

## Final Phase: Polish & Cross-Cutting Concerns

- [X] T007 Run Local CI pipeline via `tools/local-ci.ps1`

## Dependencies

- T001 is a foundational task.
- T002 through T006 are fully parallelizable and independent of each other.
- T007 must run after all refactoring is complete to ensure correctness.

## Parallel Execution Examples

- Developer A can work on T002 and T003 (Vault feature refactoring)
- Developer B can simultaneously work on T004, T005, T006 (FIDO2 feature refactoring)

## Implementation Strategy

We will update the Detekt configuration first so that CI naturally fails for the 5 target methods. Then, we will iteratively refactor each method using "Extract Method" and "Extract Composable", running local CI after each file update to ensure tests pass and logic is retained.
