# Implementation Plan: fix-event-sourcing

**Branch**: `[035-fix-event-sourcing]` | **Date**: 2026-05-13 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/035-fix-event-sourcing/spec.md`

## Summary

Fix the `bip39_english.txt` asset loading failure by explicitly mapping `src/androidMain/assets` in `core:security` build configuration, and implement `EventStoreKeyProvider` to derive an AES-256-GCM encryption key from the BIP39 master seed for use in EventStore and Snapshot repositories, replacing the current dummy key usage.

## Technical Context

**Language/Version**: Kotlin 1.9+, Java 17  
**Primary Dependencies**: Androidx Security, Koin, SQLDelight  
**Storage**: SQLite (SQLCipher via SQLDelight)  
**Testing**: JUnit 5, MockK, kotlin.test  
**Target Platform**: Android (Minimum SDK 28)
**Project Type**: Android App / KMP Library  
**Performance Goals**: Negligible latency impact on startup and event append/hydration  
**Constraints**: Zero-filled dummy keys must be purged; all crypto must use `AesEncryptionManager` with AES-256-GCM.
**Scale/Scope**: Impacts `core:data`, `core:security`, `feature:fido2` persistence layers.

## Constitution Check

*GATE: Passed*

- **I. Security First (Zero-Trust Local-First)**: Replacing the dummy key with a deterministically derived AES-256 key directly aligns with the requirement to encrypt all sensitive data via AES-256-GCM.
- **II. Master Seed Architecture**: Deriving the EventSourcing key from the master seed via HMAC-SHA512 aligns perfectly with existing patterns (e.g., `WalletMasterSeedProvider`).
- **III. Uncompromising Architecture & Quality**: Koin will be used for injecting the `EventStoreKeyProvider`. Magic numbers will be avoided.
- **VIII. Event Sourcing Architecture**: Immutability is preserved, but security is retrofitted. Truncating the old dummy-encrypted events is the only way to establish the secure baseline.

## Project Structure

### Documentation (this feature)

```text
specs/035-fix-event-sourcing/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
└── tasks.md             # Phase 2 output (future)
```

### Source Code

```text
# Relevant modules affected
core/
├── data/
│   └── src/main/kotlin/com/chimali/core/data/repository/
│       ├── EventStoreRepositoryImpl.kt
│       └── SnapshotRepositoryImpl.kt
├── database/
│   └── src/main/sqldelight/com/chimali/core/database/
│       ├── EventStore.sq
│       └── SnapshotStore.sq
└── security/
    ├── build.gradle.kts (Fixing KMP assets configuration)
    └── src/
        ├── commonMain/kotlin/com/chimali/core/security/EventStoreKeyProvider.kt
        └── androidMain/kotlin/com/chimali/core/security/impl/EventStoreKeyProviderImpl.kt

feature/fido2/
└── src/
    ├── androidMain/kotlin/com/chimali/fido2/data/repository/
    │   ├── PasskeyEventStoreRepositoryImpl.kt
    │   └── PasskeySnapshotRepositoryImpl.kt
    └── commonMain/sqldelight/com/chimali/fido2/data/database/
        ├── EventStore.sq
        └── SnapshotStore.sq
```

**Structure Decision**: The application uses a multi-module KMP structure. `EventStoreKeyProvider` will be an interface in `core:security` `commonMain`, with the implementation in `core:security` `androidMain`. Repositories in `core:data` and `feature:fido2` will consume this provider via DI.

## Complexity Tracking

N/A
