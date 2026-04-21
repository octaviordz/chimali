# Implementation Plan: Fix Dev Tools QR scan button

**Branch**: `009-fix-devtools-qr-scan` | **Date**: 2026-04-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/009-fix-devtools-qr-scan/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

The primary goal is to resolve a regression where the "Scan QR Code" button in the Development Tools screen fails to launch the camera scanner for BIP39 mnemonic recovery. The technical approach involves verifying the `ActivityResultLauncher` integration in the `DevelopmentToolsScreen` Composable, ensuring runtime permission requests are handled correctly, and validating the state update in `DevToolsViewModel`.

## Technical Context

**Language/Version**: Kotlin 1.9+, Android SDK 28+
**Primary Dependencies**: Jetpack Compose, CameraX, ML Kit (Barcode Scanning), Koin (DI)
**Storage**: EncryptedSharedPreferences (AES-256-GCM) for master seed persistence
**Testing**: JUnit 5, MockK, Compose UI Testing
**Target Platform**: Android (Mobile App)
**Project Type**: Feature Module (`feature/fido2`)
**Performance Goals**: Scanner launch < 500ms, QR Recognition < 1s
**Constraints**: Zero-trust local-first, mandatory memory zeroing for mnemonics
**Scale/Scope**: Single screen interaction (Dev Tools)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Security First**: **PASS**. Scan results (mnemonics) will be handled as sensitive data.
- **III. Uncompromising Architecture**: **PASS**. Implementation follows MVI and UDF patterns.
- **IV. Performance & Reliability**: **PASS**. Target launch and recognition times are within budget.
- **V. Modern UX**: **PASS**. Material Design 3 components (M3) are used.
- **VII. Privacy Focus**: **PASS**. On-device ML Kit used for QR scanning.

## Project Structure

### Documentation (this feature)

```text
specs/009-fix-devtools-qr-scan/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (via /speckit.tasks)
```

### Source Code (repository root)

```text
feature/fido2/src/main/kotlin/com/chimali/fido2/
├── presentation/
│   ├── ui/
│   │   └── DevelopmentToolsScreen.kt  # UI implementation & Camera Launcher
│   ├── viewmodel/
│   │   └── DevToolsViewModel.kt       # State management & Import logic
│   └── navigation/
│       └── Fido2RegistrationNavGraph.kt # Navigation entry
├── data/
│   └── crypto/
│       └── WalletMasterSeedProvider.kt # Seed management
└── AndroidManifest.xml                 # Camera permissions
```

**Structure Decision**: The feature is contained within the `feature/fido2` module, following the established MVI and feature-by-module architecture.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
