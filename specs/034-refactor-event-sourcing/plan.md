# Implementation Plan: Event Sourcing Model Integration

**Branch**: `lab/or/chimali` | **Date**: 2026-05-12 | **Spec**: [spec.md](file:///D:/octav/source/repos/Chimali/specs/034-refactor-event-sourcing/spec.md)
**Input**: Feature specification from `specs/034-refactor-event-sourcing/spec.md`

## Summary

Refactor the Chimali persistence layer from direct CRUD to an Event Sourcing architecture using the **Decider pattern** (Command → Decide → Events → Evolve → State). The F# prototype (`EventSourcingModule.fs`) provides the reference model. Two in-scope aggregates — **VaultEntry** (core:database) and **PasskeyCredential** (feature:fido2) — each get their own EventStore and Snapshot tables in their respective SQLDelight databases. Existing data is truncated (no migration). Concurrency is handled via optimistic locking with automated retry. The existing read-model tables (`VaultEntry`, `PasskeyCredential`) are preserved as synchronous projections.

## Technical Context

**Language/Version**: Kotlin 2.1.x (KMP commonMain for domain, JVM for Android platform)
**Primary Dependencies**: kotlinx-serialization (JSON), kotlinx-datetime, SQLDelight, SQLCipher, Koin
**Storage**: SQLCipher (ChimaliDatabase for Vault), SQLDelight KMP (Fido2Database for Passkey) — separate EventStore tables per database
**Testing**: kotlin.test (commonMain), JUnit 5, MockK — TDD mandatory per Constitution
**Target Platform**: Android (SDK 28+), KMP-ready module structure
**Project Type**: Mobile app (Android) with Kotlin Multiplatform domain layer
**Performance Goals**: Sub-100ms state hydration for entities with >1,000 events via snapshot-accelerated replay
**Constraints**: All event payloads encrypted with AES-256-GCM before storage. Existing data truncated during transition.
**Scale/Scope**: 10,000+ vault items, 2 aggregate roots (VaultEntry, PasskeyCredential)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Security First | ✅ PASS | Event payloads encrypted AES-256-GCM before storage. SQLCipher file-level encryption maintained. |
| II. Master Seed Architecture | ✅ PASS (N/A) | No changes to key derivation. Events carry encrypted payloads, not raw secrets. |
| III. Architecture & Quality | ✅ PASS | Decider pattern is pure functional, maps to MVI/UDF. Sealed interfaces enforce exhaustive matching. No magic numbers. |
| IV. Performance & Reliability | ✅ PASS | Snapshot-accelerated hydration targets <100ms. Automated retry handles concurrency transparently. |
| V. Cross-Platform Utility | ✅ PASS (N/A) | ES infrastructure in KMP commonMain. FIDO2 HID layer unchanged. |
| VI. Accessibility | ✅ PASS (N/A) | Infrastructure refactor; no UI changes. |
| VII. Documentation | ✅ PASS | Comprehensive spec, research, data-model, and contracts already generated. |
| VIII. Event Sourcing | ✅ PASS | This feature directly implements the principle. Immutable history, temporal queries, debuggability all addressed. |
| IX. Local CI/CD | ✅ PASS | All changes must pass `tools/local-ci.ps1`. TDD enforced. |

## Project Structure

### Documentation (this feature)

```text
specs/034-refactor-event-sourcing/
├── plan.md                        # This file
├── spec.md                        # Feature specification with clarifications
├── research.md                    # Phase 0: Technology decisions
├── data-model.md                  # Phase 1: Domain entities and SQL schema
├── quickstart.md                  # Phase 1: Developer onboarding guide
├── contracts/
│   └── event-store-contract.md    # Phase 1: Interface contracts
├── EventSourcingModule.fs         # F# reference model (input)
├── ClaimModule.fs                 # F# reference model (input)
└── tasks.md                       # Phase 2: Task breakdown (from /speckit-tasks)
```

### Source Code (repository root)

```text
core/domain/src/commonMain/kotlin/com/chimali/core/domain/
├── eventsourcing/                      # NEW: ES infrastructure contracts
│   ├── DomainEvent.kt                  # Base sealed interface
│   ├── EventKind.kt                    # Enum discriminator
│   ├── Decider.kt                      # Generic Decider<State, Command, Event> interface
│   ├── AggregateService.kt             # Generic orchestrator interface
│   ├── Snapshot.kt                     # Generic Snapshot<T> data class
│   └── TraceEntry.kt                   # Audit log entry
├── eventsourcing/vault/                # NEW: Vault aggregate domain types
│   ├── VaultEvent.kt                   # Sealed interface: EntryCreated, EntryUpdated, etc.
│   ├── VaultCommand.kt                 # Sealed interface: CreateEntry, UpdateContent, etc.
│   ├── VaultState.kt                   # Write-model state (aggregate root state)
│   └── VaultDecider.kt                 # Pure decide/evolve implementation
├── eventsourcing/passkey/              # NEW: Passkey aggregate domain types
│   ├── PasskeyEvent.kt                 # Sealed interface: PasskeyCreated, PasskeyUpdated, etc.
│   ├── PasskeyCommand.kt              # Sealed interface for passkey commands
│   ├── PasskeyState.kt                # Write-model state
│   └── PasskeyDecider.kt             # Pure decide/evolve implementation
├── repository/
│   ├── EventStoreRepository.kt         # NEW: Append/read events interface
│   └── SnapshotRepository.kt           # NEW: Save/load snapshots interface

core/database/src/main/sqldelight/com/chimali/core/database/
├── Vault.sq                            # MODIFIED: Add EventStore + Snapshot tables
│                                       # (existing VaultEntry table preserved as read model)

core/data/src/main/kotlin/com/chimali/core/data/
├── eventsourcing/                      # NEW: ES persistence implementations
│   ├── EventStoreRepositoryImpl.kt     # SQLDelight implementation for ChimaliDatabase
│   ├── SnapshotRepositoryImpl.kt       # SQLDelight implementation for ChimaliDatabase
│   └── VaultAggregateServiceImpl.kt    # Orchestrator: hydrate → decide → append → project → snapshot

feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/
├── Fido2Database.sq                    # MODIFIED: Add EventStore + Snapshot tables
│                                       # (existing PasskeyCredential table preserved as read model)

feature/fido2/src/.../data/
├── eventsourcing/                      # NEW: Passkey ES persistence
│   ├── PasskeyEventStoreRepositoryImpl.kt
│   ├── PasskeySnapshotRepositoryImpl.kt
│   └── PasskeyAggregateServiceImpl.kt

feature/vault/src/main/java/com/chimali/feature/vault/internal/
├── VaultRepositoryImpl.kt              # MODIFIED: Dispatch commands through VaultAggregateService
                                        # instead of direct SQL inserts

core/domain/src/commonTest/kotlin/com/chimali/core/domain/eventsourcing/
├── VaultDeciderTest.kt                 # NEW: Pure decide/evolve unit tests
├── PasskeyDeciderTest.kt               # NEW: Pure decide/evolve unit tests

core/data/src/test/kotlin/com/chimali/core/data/eventsourcing/
├── EventStoreRepositoryImplTest.kt     # NEW: Append, read, concurrency tests
├── SnapshotRepositoryImplTest.kt       # NEW: Save, load, corruption fallback tests
├── VaultAggregateServiceImplTest.kt    # NEW: End-to-end hydrate → decide → project tests
```

**Structure Decision**: Event Sourcing infrastructure interfaces are placed in `core:domain` (KMP commonMain) for cross-platform reuse. Concrete implementations are split between `core:data` (for ChimaliDatabase/Vault) and `feature:fido2` (for Fido2Database/Passkey), keeping the existing module boundaries intact. Each database maintains its own `EventStore` and `Snapshot` tables.

## F# Reference Model Mapping

The following table maps the F# prototype (`EventSourcingModule.fs`) to the Kotlin implementation targets:

| F# Construct | Line(s) | Kotlin Target | Module |
|---|---|---|---|
| `type VaultEntry` (state) | 6-17 | `VaultState` data class | core:domain |
| `type VaultEvent` (DU) | 20-26 | `VaultEvent` sealed interface | core:domain |
| `type PasskeyEvent` (DU) | 28-31 | `PasskeyEvent` sealed interface | core:domain |
| `type VaultCommand` (DU) | 34-38 | `VaultCommand` sealed interface | core:domain |
| `type EventKind` | 41 | `EventKind` enum class | core:domain |
| `type EventStore` (record) | 43-54 | `EventStore` SQL table + `DomainEvent` sealed interface | core:domain + core:database |
| `let apply` (evolve) | 60-74 | `VaultDecider.evolve()` | core:domain |
| `let decide` (decide) | 79-95 | `VaultDecider.decide()` | core:domain |
| `type VaultEntryReadModel` | 103-108 | Existing `VaultEntry` SQL table (synchronous projection) | core:database |
| `let project` | 110-122 | `VaultAggregateServiceImpl.projectToReadModel()` | core:data |
| `VaultCommand` with `identityId` | 134-146 | `VaultCommand` sealed interface with `identityId` property | core:domain |
| `type Snapshot` | 157-160 | `Snapshot<T>` generic data class | core:domain |
| `let rehydrate` | 163-170 | `AggregateService.getState()` internal logic | core:data |

### Key Differences from F# Model

1. **Identity ownership**: The F# model shows `identityId` added to commands at line 134-146. Kotlin implementation enforces this as a required `identityId` property on the `VaultCommand` sealed interface, validated in `VaultDecider.decide()`.
2. **Dual-database**: F# model assumes a single store. Kotlin implementation creates separate `EventStore` tables in `ChimaliDatabase` (Vault aggregate) and `Fido2Database` (Passkey aggregate) per clarification.
3. **Automated retry**: F# model doesn't address concurrency. Kotlin `AggregateServiceImpl` wraps the hydrate→decide→append cycle in a retry loop that catches `OptimisticConcurrencyException` and re-hydrates.
4. **Encryption**: F# model uses plain-text payloads. Kotlin implementation encrypts all event payloads with AES-256-GCM before SQL storage, per Constitution I.
5. **TraceEntry**: The `project` function (F# line 110) is split in Kotlin: the read-model projection updates the SQL `VaultEntry` table, and a separate `TraceEntry` list is accumulated during `evolve` for FR-004 audit logging.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| Dual EventStore tables | Preserves existing module boundary (core vs feature:fido2) | Single shared table would couple Fido2Database to ChimaliDatabase, breaking KMP module isolation |
| Repository pattern for EventStore | Abstracts encryption + SQL + serialization | Direct SQL access would leak encryption concerns into domain logic |
