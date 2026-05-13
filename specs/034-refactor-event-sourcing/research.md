# Research: Event Sourcing Architecture Refactor

**Feature**: 034-refactor-event-sourcing
**Date**: 2026-05-12 (updated with clarification results)

## Research Tasks

### R1: Event Sourcing Pattern Selection for Mobile/KMP

**Decision**: Adopt the **Decider pattern** (Command → Decide → Events → Evolve → State) as modeled in the F# reference (`EventSourcingModule.fs`).

**Rationale**: The Decider pattern separates business rule validation (`decide`) from state mutation (`apply/evolve`). Both functions are pure, side-effect free, and deterministic—ideal for testing and KMP `commonMain` placement. This is the pattern used by mature ES frameworks (Equinox, Marten, Sharpino) and aligns with the MVI unidirectional data flow already used in the Chimali UI layer.

**Alternatives Considered**:
- **CQRS with external message bus**: Overengineered for a single-device mobile app. No need for distributed event propagation.
- **Perkemp/Camlistore-style Content-Addressable Claims**: Modeled in `ClaimModule.fs`. The `Claim` + `Permanode` + `Indexer` approach provides fine-grained attribute-level mutations with content-addressable storage. This is useful for the future CRDT sync layer but is too granular for the initial refactor. The `EventSourcingModule.fs` aggregate-level events are the right starting granularity.
- **Full CQRS with separate read/write databases**: Unnecessary complexity. SQLDelight already provides type-safe query generation. A single database with separate event tables and materialized read-model views is sufficient.

### R2: Event Store Schema Design for SQLDelight + SQLCipher

**Decision**: Create a **separate `EventStore` table per database** (ChimaliDatabase for VaultEntry, Fido2Database for PasskeyCredential) with a polymorphic `event_kind` discriminator and a JSON `payload` column. Events are serialized using `kotlinx-serialization` JSON before encryption with AES-256-GCM.

**Rationale**: SQLDelight operates on separate encrypted databases. ChimaliDatabase uses SQLCipher; Fido2Database is a KMP SQLDelight database. Keeping EventStore tables separate preserves existing module boundaries. A shared `core:domain` provides common interfaces (`EventStoreRepository`, `SnapshotRepository`, `Decider`) that both databases implement independently. The `aggregate_id` + `sequence_number` composite provides strict per-aggregate ordering. A `UNIQUE` constraint on `(aggregate_id, sequence_number)` enforces append-only semantics at the database level.

**Alternatives Considered**:
- **Single unified EventStore table**: Would require coupling Fido2Database to ChimaliDatabase, breaking KMP module isolation and creating a single point of failure.
- **Per-aggregate-type tables** (e.g., `VaultEventStore`, `PasskeyEventStore`): Simpler queries but increases migration complexity and schema sprawl. Rejected.
- **Binary CBOR payload** instead of JSON: Better performance but harder to debug. JSON is preferred for debuggability (Constitution VIII: Debuggability), and the payloads are encrypted anyway.

### R3: Snapshot Strategy

**Decision**: Implement **periodic snapshotting** triggered every N events (configurable, default 20) per aggregate. Snapshots are stored in a separate `Snapshot` table per database.

**Rationale**: Matches the F# `rehydrate` function (line 163-170) and `Snapshot` type (line 157-160). Loading `Snapshot` + replaying only events after `snapshot.Version` keeps hydration time proportional to the number of events since the last snapshot, not total history. For entities with 100,000+ events, this ensures sub-100ms hydration.

**Alternatives Considered**:
- **Time-based snapshots**: Less predictable. A vault entry modified once a year would never get a snapshot. Event-count-based is more deterministic.
- **No snapshots (always full replay)**: Unacceptable for the 10,000+ vault items target at scale.

### R4: Read Model Projection Strategy

**Decision**: Maintain the existing `VaultEntry` and `PasskeyCredential` SQL tables as **synchronous read models** (materialized projections). On every successful command execution, the read model is updated in the same database transaction as the event append.

**Rationale**: The current `VaultRepositoryImpl` and all UI queries read from the `VaultEntry` table. Keeping it as a synchronous projection means zero changes to the query side—all existing `getVaultEntries`, `getVaultEntriesByLabel`, etc. continue to work unchanged. The `project` function from the F# model (line 110-122, `VaultEntryReadModel`) maps directly to `UPDATE` statements on the existing table. Same applies for `PasskeyCredential` queries in the FIDO2 module.

**Alternatives Considered**:
- **Async projections with eventual consistency**: Adds complexity (polling, versioning) inappropriate for a single-device app. Rejected.
- **Remove the read model and always reconstruct from events**: Would break all existing queries and degrade performance. Rejected.

### R5: Event Serialization and Schema Evolution

**Decision**: Use `kotlinx-serialization` JSON with `@Serializable` sealed interfaces for event types. Schema evolution handled via `@SerialName` stability and optional fields with defaults.

**Rationale**: `kotlinx-serialization` is already a project dependency. Sealed interfaces provide exhaustive pattern matching in the `evolve` function. Adding new fields as optional with defaults ensures backward compatibility. Adding new event variants is additive and non-breaking.

**Alternatives Considered**:
- **Protobuf**: Better wire efficiency but adds a build-time dependency (protoc) and is less debuggable. JSON payloads are encrypted anyway.
- **Manual serialization**: Error-prone and unmaintainable. Rejected.

### R6: Concurrency and Optimistic Locking

**Decision**: Use **optimistic concurrency control** via the `sequence_number` with **automated retry**. When appending events, the expected next sequence number is checked. If a concurrent modification has incremented it, the system automatically re-hydrates the latest state and re-executes the `decide` function transparently to the user.

**Rationale**: SQLite (via SQLCipher) is inherently single-writer. Optimistic locking via sequence number is the standard ES pattern. The automated retry approach provides the smoothest UX for a mobile app—many conflicts (like updating different attributes concurrently) are "blindly" resolvable without bothering the user. A configurable max-retry count prevents infinite loops.

**Alternatives Considered**:
- **Fail & Notify**: Blocks the user with an error dialog on every conflict. Poor UX for a mobile app.
- **CRDT merge logic**: Uses Loro.dev integration to merge conflicting payloads. Overkill for the initial ES refactor; deferred to future CRDT sync layer work.

### R7: Identity Context and Ownership Enforcement

**Decision**: The `decide` function receives the `identityId` as part of the command context (as modeled in the F# `VaultCommand` with `identityId`, lines 134-146). Ownership is validated before event emission.

**Rationale**: The existing `VaultEntry` table already has an `identity_id` foreign key. The Decider enforces that only the owning identity can mutate an aggregate, rejecting unauthorized commands with a domain error.

### R8: Data Migration Strategy

**Decision**: **No migration**. Existing data in VaultEntry and PasskeyCredential tables will be truncated during the transition to event sourcing.

**Rationale**: The system is in active development (pre-production). Migrating existing CRUD records to "synthetic" `Created` events adds complexity with no user-facing value. A clean start with event sourcing ensures 100% of the event log is genuine.

**Alternatives Considered**:
- **Bulk migration**: Generate `Created` events from existing records. Adds implementation cost and muddies the event log with synthetic events.
- **Lazy migration**: Only migrate records on first modification. Creates a long-lived hybrid system with complex branching logic.

### R9: Aggregate Root Scope

**Decision**: Two aggregate roots in-scope: **VaultEntry** (owning Label, VaultEntryLabel) and **PasskeyCredential**. All child entities are event-sourced through their aggregate root's event stream.

**Rationale**: These are the core domain aggregates with the highest auditability value. Identity, RelyingParty, BluetoothHidSession, and PairedDevice are out of scope—they have low mutation frequency or are transient/operational data. The architecture is designed to be extensible: adding new aggregate types requires implementing the `Decider` interface and adding a new `EventKind` variant.
