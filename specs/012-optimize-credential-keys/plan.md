# Implementation Plan: Optimize Credential Keys

**Branch**: `012-optimize-credential-keys` | **Date**: 2026-04-22 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/012-optimize-credential-keys/spec.md`

## Summary

Optimize the performance and memory footprint of the passkeys screen by introducing database-level pagination (lazy loading) and avoiding the expensive re-derivation of cryptographic keys. The system will decode the existing `publicKey` stored in the database. If a key is corrupted, the system will temporarily hide it and schedule a background batch repair job using `kmpworkmanager` to perform the heavy HDK re-derivation without blocking the user interface.

## Technical Context

**Language/Version**: Kotlin (KMP)
**Primary Dependencies**: Jetpack Compose, SQLDelight, Koin, Kotlin Coroutines, kmpworkmanager
**Storage**: SQLCipher via SQLDelight
**Testing**: JUnit 5, MockK, kotlin.test
**Target Platform**: Android Native (Min SDK 28)
**Project Type**: Mobile App / Library
**Performance Goals**: Passkeys list loads < 50ms, 60 FPS scrolling (no UI freeze > 16ms)
**Constraints**: Zero-Trust Local-First, no plaintext secrets, strict MVI architecture
**Scale/Scope**: Support 10,000+ vault items smoothly with lazy loading

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Security First**: No sensitive private key data is exposed or stored. Public keys are safe to decode directly from the database.
- **III. Uncompromising Architecture**: Pagination fits perfectly into the UDF/MVI state flow. Koin will be used for injecting any new stateless decoders. Magic numbers (e.g., page size) will be extracted to constants.
- **IV. Performance Excellence**: Implementing pagination directly addresses the requirement to maintain 60 FPS and avoid memory exhaustion for 10k+ items.
- **VIII. Local CI/CD**: All new pagination logic and mapper functions must be covered by TDD.

## Project Structure

### Documentation (this feature)

```text
specs/012-optimize-credential-keys/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── CredentialRepository.md
└── tasks.md
```

### Source Code (repository root)

```text
feature/fido2/src/
├── commonMain/sqldelight/
│   └── Fido2Database.sq (new paged queries)
├── main/kotlin/com/chimali/fido2/
│   ├── data/
│   │   ├── crypto/PublicKeyDecoder.kt (new stateless decoder)
│   │   ├── mapper/EntityMappers.kt (updated mapper)
│   │   └── repository/CredentialRepositoryImpl.kt (paged implementation)
│   ├── domain/
│   │   └── repository/CredentialRepository.kt (paged interface)
│   └── presentation/management/
│       ├── CredentialManagementViewModel.kt (pagination state)
│       └── CredentialListScreen.kt (infinite scroll triggers)
```

**Structure Decision**: Integrated into the existing `feature/fido2` module, extending the repository and presentation layers.

## Complexity Tracking

No constitution violations.
