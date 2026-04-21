# Implementation Plan: Manage Saved Passkeys

**Branch**: `010-manage-passkeys` | **Date**: 2026-04-20 | **Spec**: [spec.md](file:///D:/octav/source/repos/Chimali/specs/010-manage-passkeys/spec.md)
**Input**: Feature specification from `/specs/010-manage-passkeys/spec.md`

## Summary
Implement a secure, searchable, and manageable list of FIDO2 passkeys stored in the local vault. The technical approach involves refactoring existing boilerplate code in the `fido2` module, integrating `BiometricPromptComponent` for authorized deletion, and implementing a real-time search filter and "Undo" mechanism in the ViewModel.

## Technical Context

**Language/Version**: Kotlin 2.0 / Java 17  
**Primary Dependencies**: Jetpack Compose, Koin, SQLDelight, Kermit, androidx.biometric  
**Storage**: SQLCipher (AES-256-CBC) with SQLDelight (PasskeyCredential table)  
**Testing**: JUnit 5, MockK, Compose UI Testing  
**Target Platform**: Android Native (Min SDK 28)
**Project Type**: Mobile App Feature Module  
**Performance Goals**: 60 FPS scrolling, < 300ms list load, < 3s search latency  
**Constraints**: < 200ms HID latency (Constitution IV), local-only storage  
**Scale/Scope**: Handles 10,000+ items (Constitution IV)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Security)**: [PASSED] Deletion is guarded by `BiometricPrompt`.
- **Principle III (Architecture)**: [PASSED] Follows MVI pattern with Koin DI.
- **Principle IV (Performance)**: [PASSED] Target metrics aligned with Android Vitals.
- **Principle V (UX)**: [PASSED] Material Design 3 with dynamic coloring.

## Project Structure

### Documentation (this feature)

```text
specs/010-manage-passkeys/
├── plan.md              # This file
├── research.md          # Research findings & decisions
├── data-model.md        # Entities & MVI state
├── quickstart.md        # Manual verification steps
├── contracts/           
│   └── ui-contract.md   # UI interaction contract
└── tasks.md             # TODO list (to be generated)
```

### Source Code

```text
feature/fido2/src/main/kotlin/com/chimali/fido2/
├── domain/
│   ├── model/           # PasskeyCredential.kt
│   ├── repository/      # PasskeyCredentialRepository.kt
│   └── usecase/         # SearchCredentialsUseCase.kt, DeleteCredentialUseCase.kt
├── data/
│   └── repository/      # PasskeyCredentialRepositoryImpl.kt
└── presentation/
    └── management/      # CredentialListScreen.kt, CredentialManagementViewModel.kt
```

**Structure Decision**: Utilizes the existing feature-by-module structure in `feature/fido2`, adhering to Clean Architecture layers.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| MVI Complexity | Constitution compliance | MVP/MVVM lacks strict UDF required for security-critical UI. |
| UseCase Layer | Separation of concerns | Direct Repository access in VM makes testing domain logic harder. |
