# Implementation Plan: Core Feature Migration

**Branch**: `045-core-feature-analysis` | **Date**: 2026-05-18 | **Spec**: [spec.md](file:///d:/octav/source/repos/Chimali/specs/045-core-feature-analysis/spec.md)
**Input**: Feature specification from `specs/045-core-feature-analysis/spec.md`

## Summary

Migrate four cross-cutting concerns from feature modules to their correct core module homes, improving architectural consistency and enabling code reuse across features. The migrations are: (1) build a production `EncryptedDriverFactory` in `:core:database` to replace the FIDO2 stub, (2) delete the obsolete `ClipboardManagerWrapper` in Vault since `:core:common` already has a fully functional `ClipboardManagerService`, (3) move BouncyCastle provider registration to app startup, and (4) relocate `PlatformUserVerification` expect/actual to `:core:security`.

## Technical Context

**Language/Version**: Kotlin 2.x (Kotlin Multiplatform)
**Primary Dependencies**: Koin (DI), SQLDelight (database), SQLCipher (encryption), BouncyCastle (crypto provider), AndroidX Biometric (capability check)
**Storage**: SQLCipher-encrypted SQLite databases via SQLDelight
**Testing**: kotlin.test (commonTest), JUnit 5 + MockK (androidHostTest)
**Target Platform**: Android (min SDK 28), iOS (placeholder stubs)
**Project Type**: KMP mobile app with feature-by-module architecture
**Performance Goals**: Cold start < 2s, BT HID < 200ms end-to-end (NFR-PERF-030)
**Constraints**: Memory zeroing for all crypto material (Constitution §X.5), clipboard auto-clear within 60s (Constitution §IV)
**Scale/Scope**: 4 focused migrations touching 4 core modules and 2 feature modules

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| §I Security First | Encryption keys zeroed after use, SQLCipher with PBKDF2-SHA512 | ✅ Pass — `EncryptedDriverFactory` design mandates `try/finally` zeroing |
| §III Clean Architecture | Core modules provide abstractions, features consume | ✅ Pass — all migrations move to correct core homes |
| §IV Performance | Clipboard cleared within 60s, startup < 2s | ✅ Pass — clipboard already implemented; BC registration adds ~20ms to startup |
| §IX Local CI | All changes pass `tools/local-ci.ps1` | ✅ Pass — CI gate after each migration phase |
| §X.5 Memory Safety | Sensitive byte arrays zeroed in finally blocks | ✅ Pass — `EncryptedDriverFactory` design enforces this |
| §XI.1 YAGNI | No speculative abstractions | ✅ Pass — all migrations serve concrete needs or delete dead code |
| §XI.2 Three-Use Rule | Abstractions justified by 3+ usage sites or constitutional mandate | ✅ Pass — SQLCipher (2 databases + constitution mandate), Clipboard (2 features already), BC (app-wide), PlatformUV (proactive but small) |
| §XI.2 Module Count | New modules justified by isolation/build benefit | ✅ Pass — no new modules created; all migrations target existing modules |

**Post-Phase 1 Re-Check**: All gates pass. The research phase revealed that the clipboard migration is already complete (only cleanup remains), reducing the scope and risk further.

## Project Structure

### Documentation (this feature)

```text
specs/045-core-feature-analysis/
├── analysis.md          # Original architectural analysis
├── critique.md          # Expert review of analysis proposals
├── spec.md              # Feature specification (/speckit-specify)
├── plan.md              # This file (/speckit-plan)
├── research.md          # Phase 0 research findings
├── data-model.md        # Phase 1 entity model
├── quickstart.md        # Phase 1 step-by-step guide
└── checklists/
    └── requirements.md  # Spec quality validation
```

### Source Code (repository root)

```text
core/
├── common/
│   └── src/
│       ├── commonMain/.../clipboard/        # ClipboardManagerService [EXISTS]
│       ├── androidMain/.../clipboard/       # AndroidClipboardManagerService [EXISTS]
│       └── iosMain/.../clipboard/           # IosClipboardManagerService [EXISTS]
├── database/
│   └── src/main/java/.../
│       ├── di/DatabaseModule.kt             # UPDATE — use EncryptedDriverFactory
│       └── EncryptedDriverFactory.kt        # NEW — SQLCipher driver factory
├── security/
│   └── src/
│       ├── commonMain/.../biometrics/       # NEW — PlatformUserVerification expect
│       ├── androidMain/.../biometrics/      # NEW — Android actual (BiometricManager)
│       └── iosMain/.../biometrics/          # NEW — iOS actual (placeholder)

feature/
├── fido2/
│   └── src/
│       ├── androidMain/.../data/storage/SqlCipherWrapper.kt     # DELETE
│       ├── androidMain/.../util/performance/WarmUpHelper.kt     # UPDATE — remove provider reg
│       ├── androidMain/.../data/crypto/Fido2CryptoService.kt    # UPDATE — remove provider reg
│       ├── commonMain/.../platform/PlatformUserVerification.kt  # DELETE (moved)
│       ├── androidMain/.../platform/PlatformUserVerification.kt # DELETE (moved)
│       └── iosMain/.../platform/PlatformUserVerification.kt     # DELETE (moved)
├── vault/
│   └── src/main/java/.../internal/
│       ├── ClipboardManagerWrapper.kt       # DELETE
│       └── VaultViewModel.kt               # UPDATE — use core ClipboardManagerService

app/
└── src/main/kotlin/.../ChimaliApplication.kt  # UPDATE — add BC provider registration
```

**Structure Decision**: No new modules. All migrations target existing core modules (`:core:database`, `:core:common`, `:core:security`) and clean up dead code in feature modules (`:feature:fido2`, `:feature:vault`).

## Complexity Tracking

No violations to justify — all migrations operate within existing module boundaries and follow established patterns.
