# Implementation Plan: KMP Resource Migration (BIP39 Wordlist)

**Branch**: `044-kmp-resource-migration` | **Date**: 2026-05-18 | **Spec**: [spec.md](file:///d:/octav/source/repos/Chimali/specs/044-kmp-resource-migration/spec.md)
**Input**: Feature specification from `specs/044-kmp-resource-migration/spec.md`

## Summary

Migrate the `bip39_english.txt` wordlist from `core/security/src/androidMain/resources/` to `core/security/src/commonMain/resources/` and introduce an `expect`/`actual` resource-loading contract so that the `Bip39MasterSeedGenerator` can read the wordlist from shared code. The existing tests must be refactored to eliminate `android.content.Context` mocking, validating that the resource is available via standard JVM classloader resolution in `androidHostTest`.

## Technical Context

**Language/Version**: Kotlin 2.x (KMP), AGP 8.x  
**Primary Dependencies**: Koin (Annotations + KSP), BouncyCastle (androidMain only), Signum (commonMain)  
**Storage**: N/A (static read-only resource file)  
**Testing**: `kotlin.test`, JUnit 5, MockK (androidHostTest source set)  
**Target Platform**: Android (SDK 28+), iOS (placeholder targets — iosArm64, iosSimulatorArm64)  
**Project Type**: KMP library module (`core:security`)  
**Performance Goals**: Wordlist loading must be lazy and occur at most once per process lifetime  
**Constraints**: No new third-party dependencies; standard JVM `ClassLoader.getResourceAsStream()` is sufficient for Android/JVM targets  
**Scale/Scope**: Single file relocation (~15 KB), 1 source file refactored, 1 test file refactored, 1 `expect`/`actual` pair added

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| §III Architecture | KMP: Maximize domain logic in `commonMain`. Keep `expect`/`actual` minimal. | ✅ PASS — resource moves to `commonMain`; only the resource-loading function uses `expect`/`actual` |
| §X.2 KMP Guidelines | All `expect` declarations MUST have a corresponding `actual` in every supported platform source set. | ✅ PASS — `actual` implementations will be provided for `androidMain` and `iosMain` |
| §X.5 Cryptographic Code | Zeroing and constant-time comparisons for key material | ✅ N/A — wordlist is public data, not key material |
| §XI.1 YAGNI | No speculative abstractions | ✅ PASS — `expect`/`actual` is the established KMP pattern; no new abstractions invented |
| §XI.2 Simplest Solution | Simplest sufficient implementation preferred | ✅ PASS — direct `ClassLoader.getResourceAsStream()` on Android/JVM, stub on iOS |
| §IX Local CI | All changes must pass `tools/local-ci.ps1` | ✅ Will validate |

## Project Structure

### Documentation (this feature)

```text
specs/044-kmp-resource-migration/
├── spec.md
├── spec-draft.md
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (via /speckit-tasks)
```

### Source Code (repository root)

```text
core/security/src/
├── commonMain/
│   ├── kotlin/com/chimali/core/security/
│   │   ├── api/
│   │   │   └── MasterSeedGenerator.kt          # Existing interface (unchanged)
│   │   └── platform/
│   │       └── ResourceLoader.kt               # NEW: expect fun loadResourceLines(name: String): List<String>
│   └── resources/
│       └── bip39_english.txt                    # MOVED from androidMain/resources/
│
├── androidMain/
│   ├── kotlin/com/chimali/core/security/
│   │   ├── impl/
│   │   │   └── Bip39MasterSeedGenerator.kt      # MODIFIED: use ResourceLoader instead of direct classloader
│   │   └── platform/
│   │       └── ResourceLoader.android.kt        # NEW: actual fun via ClassLoader.getResourceAsStream()
│   └── resources/
│       └── (bip39_english.txt REMOVED)
│
├── iosMain/
│   └── kotlin/com/chimali/core/security/
│       └── platform/
│           └── ResourceLoader.ios.kt            # NEW: actual fun stub (TODO for future iOS target)
│
├── androidHostTest/                              # Existing test source set (unchanged)
└── test/
    └── kotlin/com/chimali/core/security/impl/
        └── Bip39MasterSeedGeneratorTest.kt      # MODIFIED: remove Context/AssetManager mocking
```

**Structure Decision**: Follows the existing `core:security` KMP module layout. The `platform/` package mirrors the pattern used in `core/domain` for `expect`/`actual` declarations (e.g., `generateSecureRandomBytes`). No new Gradle modules required.

## Complexity Tracking

No constitution violations to justify. The implementation uses the simplest sufficient approach per §XI.2.
