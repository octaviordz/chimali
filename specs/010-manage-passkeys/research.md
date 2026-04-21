# Research: Manage Saved Passkeys

## Technical Context
- **Storage**: `Fido2Database.sq` defines the `PasskeyCredential` table. `PasskeyCredentialRepository.kt` provides access to it.
- **Authentication**: `BiometricPromptComponent.kt` is an existing reusable component for biometric authentication in the `fido2` module.
- **UI**: `CredentialListScreen.kt` and `CredentialManagementViewModel.kt` exist as partial implementations/boilerplates.
- **Dependency**: `GetAllCredentialsUseCase`, `DeleteCredentialUseCase`.

## Decisions

### 1. Data Access & Refactoring
- **Decision**: Refactor the existing "boilerplate" management code to align with the SRS (Spec 010).
- **Rationale**: The existing code includes "Delete All" and "Update Label" which are explicitly out of scope/rejected in the spec.
- **Action**:
    - Remove `DeleteAllCredentialsUseCase` and `UpdateCredentialLabelUseCase`.
    - Update `CredentialManagementViewModel` to remove corresponding intents.

### 2. Search Implementation
- **Decision**: Implement search filtering in the `ViewModel` using a `StateFlow` and `combine` operator.
- **Rationale**: Real-time filtering in the ViewModel provides a smoother UI experience for up to 50 items (per SC-001) without repeated database queries.
- **Alternatives**: Database-side `LIKE` queries (overkill for small lists, but available in `PasskeyCredential.sq` if list grows large).

### 3. Deletion & Undo Flow
- **Decision**: Implement the "Undo" logic in the `CredentialManagementViewModel` using a temporary "last deleted" state variable.
- **Rationale**: Allows for immediate UI feedback and a simple restoration mechanism within the 5-second window (per FR-007).
- **Security**: The "Undo" action will *not* require a second biometric check, as the initial deletion was already authorized.

### 4. Biometric Authorization
- **Decision**: Trigger `BiometricPromptComponent` *before* calling `DeleteCredentialUseCase`.
- **Rationale**: Ensures compliance with FR-004.

## Open Questions / Unknowns
- [Resolved] Should "Undo" persist across app restarts? **No**, it's a temporary UI safety net (snackbar).
- [Resolved] Is there a "Last Used" date? **Yes**, `PasskeyCredential.lastUsedAt` exists in the DB.

## Constitution Compliance
- **Security**: All destructive actions (delete) are guarded by biometric/PIN (III.1).
- **Architecture**: MVI/UDF pattern is maintained in `CredentialManagementViewModel` (III).
- **UX**: Material 3 standards with dynamic coloring and smooth animations (V).
- **Performance**: Search must be < 3s, load < 300ms (IV).
