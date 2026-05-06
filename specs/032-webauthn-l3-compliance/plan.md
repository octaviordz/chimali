# Implementation Plan: WebAuthn L3 Compliance (Fast-Path Auth)

**Branch**: `032-webauthn-l3-compliance` | **Date**: 2026-05-06 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/032-webauthn-l3-compliance/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Resolving duplicate `GetAssertion` prompts caused by host retry mechanisms and valid multi-request patterns. The solution introduces a headless fast-path for UI-less authentication (Fix E) when UV is not strictly required, and a fallback auto-confirm mechanism for ViewModels (Fix F) when UV capability is NONE.

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Kotlin 2.1  
**Primary Dependencies**: Jetpack Compose, Koin, kotlinx.coroutines, CTAP2 HID protocol  
**Storage**: N/A (Existing encrypted SQLDelight repository)  
**Testing**: JUnit 5, MockK, Compose UI Tests  
**Target Platform**: Android (Min SDK 28)
**Project Type**: Mobile App (KMP Module)  
**Performance Goals**: CTAP2 GetAssertion response < 200ms end-to-end  
**Constraints**: Must accurately adhere to FIDO2 WebAuthn L3 protocol specifications  
**Scale/Scope**: Impacts all FIDO2 authentication ceremonies over Bluetooth HID

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] Unidirectional Data Flow (MVI) enforced
- [x] No magic numbers introduced
- [x] Koin dependency injection utilized
- [x] Performance targets (CTAP2 < 200ms) achievable with headless bypass

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
src/
├── fido2/
│   ├── usecases/GetAssertionUseCase.kt
│   ├── bluetooth/Ctap2GetAssertionHandler.kt
│   └── ui/AuthenticationPromptViewModel.kt
```

**Structure Decision**: Using existing KMP module structure (`fido2`). Modifications will be limited to the CTAP2 HID handler and Authentication UI layer.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
