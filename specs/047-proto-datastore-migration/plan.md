# Implementation Plan: Proto DataStore Migration

**Branch**: `047-proto-datastore-migration` | **Date**: 2026-05-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/047-proto-datastore-migration/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Migrate from deprecated EncryptedSharedPreferences to Proto DataStore for storing user preferences and settings in a Kotlin Multiplatform project. The migration will remove all EncryptedSharedPreferences and SharedPreferences references, implement Proto DataStore with Protocol Buffers schema, ensure encryption by default for sensitive data, and provide a data migration path that preserves existing user data (BIP39 mnemonics and FIDO2 settings).

## Technical Context

**Language/Version**: Kotlin (KMP) with Minimum SDK 28

**Primary Dependencies**:
- androidx.datastore:datastore (Proto DataStore)
- androidx.datastore:datastore-preferences (for reference)
- Protocol Buffers (protobuf-kotlin)
- Android KeyStore (for encryption key management)
- Koin (dependency injection)

**Storage**:
- Current: EncryptedSharedPreferences (AES-256-GCM encryption via Android KeyStore)
- Target: Proto DataStore with encryption wrapper
- Files: `chimali_wallet_seed.xml`, `fido2_settings.xml` (current) → `.pb` files (target)

**Testing**: kotlin.test (KMP common), JUnit 5, MockK, Compose UI Testing

**Target Platform**: Android Native Application (Minimum SDK 28), Kotlin Multiplatform structure

**Project Type**: Mobile application with FIDO2 virtual authenticator features

**Performance Goals**:
- Data access operations: Maintain or improve current performance
- Migration: Complete on first app launch after upgrade
- Startup: Must not degrade cold/warm/hot start targets (Constitution §IV)

**Constraints**:
- Encryption: Must maintain AES-256-GCM encryption per Constitution §I
- Data preservation: Zero data loss during migration
- KMP: Must work in common KMP module structure
- Memory: Sensitive data must be zeroed after use (Constitution X.5)

**Scale/Scope**:
- 6 files using EncryptedSharedPreferences identified
- 2 primary data types to migrate: BIP39 mnemonic (24 words), FIDO2 settings (max credential count)
- Shared User Preferences schema for future extensibility

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Security First (Constitution §I)
- **Encryption**: Proto DataStore must maintain AES-256-GCM encryption for sensitive data (BIP39 mnemonic) - **PASS** (will use Android KeyStore for encryption wrapper per research decision)
- **Memory Security**: Sensitive data must be zeroed after use - **PASS** (existing pattern in WalletMasterSeedProvider will be maintained)
- **AES-256-SIV**: Not applicable for this migration (no searchable metadata) - **N/A**

### Master Seed Architecture (Constitution §II)
- **Seed Storage**: Master seed must be stored encrypted - **PASS** (migration maintains encryption via Android KeyStore)
- **HDK Compliance**: No changes to HDK derivation - **PASS** (storage layer only, no crypto logic changes)
- **PQ Branch Isolation**: No changes to PQ branch - **PASS** (storage layer only)

### Architecture & Quality (Constitution §III)
- **Clean Architecture**: Migration must maintain MVI pattern - **PASS** (storage layer only, no architectural changes)
- **Koin DI**: Must use Koin for dependency injection - **PASS** (existing pattern maintained)
- **Modularization**: Feature-by-module structure maintained - **PASS** (shared schema in core/common, consumed by feature modules)

### Performance (Constitution §IV)
- **Startup**: Must not degrade startup targets - **PASS** (migration on first launch, subsequent launches use cached data)
- **Latency**: Data access must remain performant - **PASS** (Proto DataStore is designed for performance, lazy loading)
- **Memory**: Zero memory leaks - **PASS** (proper coroutine scoping maintained)

### KMP Structure (Constitution §II.3)
- **Common Module**: User Preferences must be in common KMP location - **PASS** (schema in core/common/proto, implementation in commonMain)
- **Platform-specific code**: Minimal expect/actual - **PASS** (only encryption wrapper in androidMain)

### Memory Safety (Constitution X.5)
- **Zeroing**: Sensitive data must be zeroed - **PASS** (existing pattern maintained in WalletMasterSeedProvider)

### Anti-Patterns (Constitution X.7)
- **Hardcoded Secrets**: No encryption keys in source - **PASS** (using Android KeyStore)
- **God Classes**: No class exceeds 500 lines - **PASS** (DataStore implementation is modular)
- **Over-Engineering**: Simplest sufficient solution - **PASS** (using standard Proto DataStore, not custom solution)

### Risk Management (Constitution XI)
- **YAGNI First**: No speculative features - **PASS** (only migrating existing data)
- **Three-Use Rule**: Shared schema justified by multiple consumers - **PASS** (wallet seed + FIDO2 settings + future preferences)
- **Incremental Delivery**: Migrating incrementally - **PASS** (P1, P2, P3 priorities)

**Pre-Design GATE Status**: **PASS**
**Post-Design GATE Status**: **PASS** - No constitutional violations introduced by design decisions

## Project Structure

### Documentation (this feature)

```text
specs/047-proto-datastore-migration/
├── plan.md              # This file (/speckit-plan command output)
├── spec.md              # Feature specification
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
├── checklists/          # Quality checklists
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
core/common/                    # Common KMP module for shared preferences
├── proto/                      # Protocol Buffer definitions
│   └── user_preferences.proto  # User Preferences schema
├── src/commonMain/
│   └── kotlin/com/chimali/core/common/datastore/
│       ├── UserPreferencesSerializer.kt  # Proto serializer
│       └── UserPreferencesDataStore.kt    # DataStore instance
└── src/androidMain/
    └── kotlin/com/chimali/core/common/datastore/
        └── EncryptionWrapper.kt  # Android KeyStore encryption wrapper

feature/fido2/                  # FIDO2 feature module (migration target)
└── src/androidMain/kotlin/com/chimali/fido2/
    ├── data/crypto/
    │   └── WalletMasterSeedProvider.kt  # Migrate to use DataStore
    └── data/repository/
        └── Fido2SettingsRepositoryImpl.kt  # Migrate to use DataStore

core/security/                  # Security module (reference updates)
└── src/androidMain/kotlin/com/chimali/core/security/
    └── impl/AesSivEncryptionManager.kt  # Update comments/references
```

**Structure Decision**: This is a Kotlin Multiplatform project with a modular structure. The shared User Preferences schema will be placed in `core/common` to enable KMP sharing. Platform-specific encryption (Android KeyStore) will be in `androidMain` source sets. The FIDO2 feature module will consume the common DataStore implementation.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No constitutional violations identified - this section not applicable.

## Phase 0: Research & Technical Decisions

### Research Summary

No NEEDS CLARIFICATION markers were identified in the Technical Context. The following research documents the technical decisions and rationale for the migration approach.

### Technical Decisions

#### Decision 1: Use Proto DataStore over Preferences DataStore
**Rationale**: Proto DataStore provides type safety, schema evolution, and better performance compared to Preferences DataStore. The Protocol Buffer schema ensures compile-time type checking and enables structured data storage.

**Alternatives Considered**:
- Preferences DataStore: Rejected because it lacks type safety and schema evolution capabilities
- Room Database: Rejected because it's overkill for simple key-value storage and adds unnecessary complexity
- Custom encrypted file solution: Rejected because it would require implementing encryption, serialization, and migration logic from scratch

#### Decision 2: Android KeyStore for Encryption Wrapper
**Rationale**: Android KeyStore provides hardware-backed encryption key storage, which is the standard approach for Android secure storage. This maintains compatibility with the existing EncryptedSharedPreferences encryption approach (AES-256-GCM).

**Alternatives Considered**:
- Custom encryption key management: Rejected due to security risks and complexity
- Jetpack Security (EncryptedFile): Rejected because it's designed for file encryption, not key-value storage
- No encryption: Rejected because it violates Constitution §I (Security First)

#### Decision 3: Shared User Preferences in core/common Module
**Rationale**: Placing the User Preferences schema in the common KMP module enables future platform expansion (iOS, desktop) and ensures consistency across the project. This aligns with Constitution §II.3 (KMP Structure).

**Alternatives Considered**:
- Platform-specific preferences in each feature module: Rejected because it would duplicate code and prevent sharing
- Separate preferences module: Rejected because it would add unnecessary module complexity without clear benefit (Constitution XI.2 - Over-Engineering Guard)

#### Decision 4: Incremental Migration Strategy
**Rationale**: Migrating incrementally (P1: wallet seed, P2: FIDO2 settings, P3: shared schema) minimizes risk and allows for rollback if issues occur. This aligns with Constitution XI.3 (Realistic Goal Setting - Incremental Delivery).

**Alternatives Considered**:
- Big bang migration: Rejected due to high risk and potential for data loss
- Parallel storage during migration: Accepted as part of the incremental approach to enable rollback

#### Decision 5: Protocol Buffer Schema Design
**Rationale**: The schema will include fields for wallet seed (BIP39 mnemonic) and FIDO2 settings (max credential count) with optional fields to support schema evolution. This allows adding new preferences in the future without breaking existing data.

**Alternatives Considered**:
- Separate proto files for each preference type: Rejected because it would complicate the DataStore implementation
- JSON serialization: Rejected because it lacks type safety and performance benefits of Protocol Buffers
