# Implementation Plan: Passkey UI Enhancements

**Branch**: `011-passkey-ui-enhancements` | **Date**: 2026-04-21 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/011-passkey-ui-enhancements/spec.md`

## Summary

Enhance the passkey management interface by implementing swipe-to-delete with a long-duration undo snackbar, aligning it with the existing devices list. Simplify the details view by removing the custom label field and standardizing iconography to a premium "Fingerprint" icon.

## Technical Context

**Language/Version**: Kotlin 1.9.22 / Compose 1.6.1  
**Primary Dependencies**: AndroidX Compose Material3, Koin (DI), SQLDelight  
**Storage**: SQLDelight (`PasskeyCredential` table)  
**Testing**: JUnit 5, MockK, Compose UI Testing (TDD mandatory)  
**Target Platform**: Android (Minimum SDK 28)
**Project Type**: Mobile Application Feature (FIDO2 Management)  
**Performance Goals**: UI interaction latency < 100ms; Deletion process end-to-end < 500ms  
**Constraints**: Zero-trust memory handling; High-legibility typography (Atkinson Hyperlegible)  
**Scale/Scope**: Refactoring `CredentialListScreen` and `CredentialComponents` within the FIDO2 feature module.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] **III. Architecture**: Uses MVI pattern (Intent -> State/Effect). Koin for DI is already established.
- [x] **IV. Performance**: Deletion flow must not block the main thread.
- [x] **V. Modern UX**: Implements Material 3 `SwipeToDismissBox`.
- [x] **VI. Legibility**: Ensures technical IDs and user names use Atkinson Hyperlegible font.
- [x] **VII. TDD**: All new logic (Undo/Delete state management) MUST be TDD-driven.

## Project Structure

### Documentation (this feature)

```text
specs/011-passkey-ui-enhancements/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Decision log for UI/UX patterns
├── data-model.md        # PasskeyCredential model usage
└── tasks.md             # Implementation tasks (Phase 2)
```

### Source Code (repository root)

```text
feature/fido2/src/main/kotlin/com/chimali/fido2/
├── domain/model/
│   └── PasskeyCredential.kt       # Existing model (ignoring 'label' field)
├── presentation/management/
│   ├── CredentialListScreen.kt    # Main list (Add SwipeToDismissBox)
│   ├── CredentialComponents.kt   # Item & Details UI (Add Swipe logic, Remove Label)
│   └── CredentialManagementViewModel.kt # State management (Add Undo logic)
```

**Structure Decision**: Enhancements will be localized to the `presentation.management` package of the `fido2` feature module.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
