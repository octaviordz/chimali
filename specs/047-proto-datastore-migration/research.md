# Research: Proto DataStore Migration

**Feature**: Proto DataStore Migration
**Date**: 2026-05-19

## Research Summary

No NEEDS CLARIFICATION markers were identified in the Technical Context. This document consolidates the technical decisions and rationale for the migration approach from EncryptedSharedPreferences to Proto DataStore.

## Technical Decisions

### Decision 1: Use Proto DataStore over Preferences DataStore

**Rationale**: Proto DataStore provides type safety, schema evolution, and better performance compared to Preferences DataStore. The Protocol Buffer schema ensures compile-time type checking and enables structured data storage.

**Alternatives Considered**:
- **Preferences DataStore**: Rejected because it lacks type safety and schema evolution capabilities
- **Room Database**: Rejected because it's overkill for simple key-value storage and adds unnecessary complexity
- **Custom encrypted file solution**: Rejected because it would require implementing encryption, serialization, and migration logic from scratch

### Decision 2: Android KeyStore for Encryption Wrapper

**Rationale**: Android KeyStore provides hardware-backed encryption key storage, which is the standard approach for Android secure storage. This maintains compatibility with the existing EncryptedSharedPreferences encryption approach (AES-256-GCM).

**Alternatives Considered**:
- **Custom encryption key management**: Rejected due to security risks and complexity
- **Jetpack Security (EncryptedFile)**: Rejected because it's designed for file encryption, not key-value storage
- **No encryption**: Rejected because it violates Constitution §I (Security First)

### Decision 3: Shared User Preferences in core/common Module

**Rationale**: Placing the User Preferences schema in the common KMP module enables future platform expansion (iOS, desktop) and ensures consistency across the project. This aligns with Constitution §II.3 (KMP Structure).

**Alternatives Considered**:
- **Platform-specific preferences in each feature module**: Rejected because it would duplicate code and prevent sharing
- **Separate preferences module**: Rejected because it would add unnecessary module complexity without clear benefit (Constitution XI.2 - Over-Engineering Guard)

### Decision 4: Incremental Migration Strategy

**Rationale**: Migrating incrementally (P1: wallet seed, P2: FIDO2 settings, P3: shared schema) minimizes risk and allows for rollback if issues occur. This aligns with Constitution XI.3 (Realistic Goal Setting - Incremental Delivery).

**Alternatives Considered**:
- **Big bang migration**: Rejected due to high risk and potential for data loss
- **Parallel storage during migration**: Accepted as part of the incremental approach to enable rollback

### Decision 5: Protocol Buffer Schema Design

**Rationale**: The schema will include fields for wallet seed (BIP39 mnemonic) and FIDO2 settings (max credential count) with optional fields to support schema evolution. This allows adding new preferences in the future without breaking existing data.

**Alternatives Considered**:
- **Separate proto files for each preference type**: Rejected because it would complicate the DataStore implementation
- **JSON serialization**: Rejected because it lacks type safety and performance benefits of Protocol Buffers

## KMP DataStore Setup Reference

Based on the Android developer guide for Kotlin Multiplatform DataStore:
- Proto DataStore is supported in KMP via `androidx.datastore:datastore-core`
- Protocol Buffer definitions go in `commonMain/proto/`
- Platform-specific implementations use `expect/actual` for encryption wrappers
- Serialization uses `protobuf-kotlin` library

## Migration Strategy

### Data Migration Approach

1. **Read from EncryptedSharedPreferences**: On first launch, check if legacy data exists
2. **Write to Proto DataStore**: Migrate data to new format with encryption wrapper
3. **Verify Migration**: Confirm data integrity before removing legacy storage
4. **Cleanup**: Remove EncryptedSharedPreferences files after successful migration

### Rollback Strategy

- Keep both storage mechanisms active during migration window
- Add feature flag to enable/disable migration
- Log migration failures for debugging
- Allow manual re-trigger of migration if needed

## Performance Considerations

- Proto DataStore uses lazy loading and caching
- Encryption operations use Android KeyStore (hardware-backed when available)
- Migration occurs once on first launch, subsequent launches use cached data
- No impact on startup time after migration completes
