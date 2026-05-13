# Feature Specification: Event Sourcing Model Integration

**Feature Branch**: `034-refactor-event-sourcing`  
**Created**: 2026-05-12  
**Status**: Draft  
**Input**: User description: "Refactor system code to reflect recent architecture change to use Event Sourcing architecture. [specs/034-refactor-event-sourcing/EventSourcingModule.fs] has F# code modeling an highlevel, incomplete implementation for event sourcing."

## Clarifications

### Session 2026-05-12

- Q: Which aggregates are in scope for this refactor? → A: Core aggregates: VaultEntry (with Label, VaultEntryLabel) and PasskeyCredential. All entities participate in event sourcing through their aggregate root — not every entity requires a one-to-one event sourcing relationship, but all must be event-sourced either directly or via their aggregate. Identity, RelyingParty, BluetoothHidSession, and PairedDevice are out of scope for initial refactor.
- Q: How will event storage be distributed across existing databases? → A: Separate EventStore tables per database (ChimaliDatabase and Fido2Database), with shared common interfaces in core:domain to ensure architectural consistency.
- Q: What is the migration strategy for existing data? → A: No migration required; existing data in VaultEntry and PasskeyCredential tables can be truncated during the transition.
- Q: How are concurrent modification conflicts handled? → A: Automated retry; the system re-hydrates the latest state and re-executes the Decider logic transparently to the user.
- Q: How should event schema evolution be handled? → A: Additive changes only; new fields must have default values to ensure backward compatibility with historical events.

### Session 2026-05-13

- Q: How should event schema evolution be handled? → A: Additive changes only; new fields must have default values to ensure backward compatibility with historical events.
- Q: What is the default threshold for snapshot generation? → A: Every 20 events per aggregate root.
- Q: What is the format for the audit trace log? → A: Structured JSON format, optimized for machine-readability and precise auditing.
- Q: How should data truncation be handled during deployment? → A: Automatically via SQLDelight migration scripts; no explicit user confirmation or backup is required for this pre-production refactor.
- Q: What is the primary interface for temporal queries? → A: Timestamp-based reconstruction (`asOf(timestamp)`), allowing the system to load events up to that specific point in time.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - System Auditability & Traceable Mutations (Priority: P1)

As a security or compliance auditor, I want the system to record all state changes as granular, immutable events (e.g., Claims targeting specific attributes), so that I can independently verify the complete history of any entity without risk of data tampering.

**Why this priority**: Complete auditability is the primary goal of the architectural refactor, ensuring trust and security for credential storage. Granular traceability ensures we know the explicit history of every single mutation.

**Independent Test**: Can be tested by appending multiple contradictory claims (e.g., Set, Add, Remove) to a target and verifying that the final reconstructed state resolves correctly while maintaining a complete trace log.

**Acceptance Scenarios**:

1. **Given** a target entity, **When** multiple claims are applied to the same attribute, **Then** the system resolves the final value based on strict chronological ordering of the events.
2. **Given** a sequence of claims, **When** state is reconstructed, **Then** an accompanying audit trace log is generated mapping each timestamp to its logical mutation description.
3. **Given** an event log, **When** an administrator attempts to delete or modify a past event, **Then** the operation is rejected at the system level.

---

### User Story 2 - Temporal Queries and Time Travel (Priority: P1)

As a system administrator or support engineer, I want to query the exact state of an entity at any specific point in the past, so that I can understand what the user or system saw at that exact moment.

**Why this priority**: Temporal queries are essential for historical analysis and resolving disputes or complex support tickets.

**Independent Test**: Can be fully tested by replaying the event stream up to a specific timestamp and comparing the reconstructed state against expected values.

**Acceptance Scenarios**:

1. **Given** an entity with a rich history of events, **When** a temporal query is executed for a timestamp halfway through its history, **Then** the system reconstructs and returns the entity's state precisely as it existed at that moment.

---

### User Story 3 - Snapshot-Accelerated Hydration (Priority: P2)

As a system administrator, I want the system to periodically save snapshots of reconstructed states, so that subsequent data loads are extremely fast even for entities with thousands of historical events.

**Why this priority**: While immutable history provides security, reading large event streams can degrade performance. Snapshots are essential for the secondary goal of high performance and scalability.

**Independent Test**: Can be tested by loading an entity from a snapshot and providing only the subset of events that occurred after the snapshot's sequence number, verifying the final state matches a full from-scratch hydration.

**Acceptance Scenarios**:

1. **Given** an entity with a large history and a recent snapshot, **When** the entity state is requested, **Then** the system hydrates the state starting from the snapshot and only applies claims that occurred after the snapshot's sequence number.

---

### Edge Cases

- What happens if a snapshot is corrupted or deleted? (System MUST gracefully fall back to full from-scratch hydration).
- How are concurrent commands handled to ensure sequence numbers don't conflict? (Optimistic concurrency via UNIQUE constraint; system MUST implement automated retry by re-hydrating and re-applying the command).
- How does the system handle schema evolution for events? (Strictly additive changes; new fields MUST include default values for backward compatibility).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST represent state mutations as immutable, append-only events (e.g., `Claim` entities targeting specific attributes with Set/Add/Remove operations).
- **FR-002**: System MUST prohibit the deletion or modification of any persisted event.
- **FR-003**: System MUST utilize a `Decider` mechanism to deterministically reconstruct an entity's current state (the `Model`) by folding an ordered stream of events.
- **FR-004**: System MUST generate a structured JSON `Trace` audit log during state reconstruction, detailing each chronological mutation for precise machine-auditing.
- **FR-005**: System MUST provide a timestamp-based interface (`asOf(timestamp)`) to perform temporal queries, returning the state of an entity as it existed at any specified point in time.
- **FR-006**: System MUST support `Snapshot` generation, storing the computed state alongside the sequence number of the last processed event. Snapshots MUST be generated automatically every 20 events per aggregate.
- **FR-007**: System MUST be capable of state hydration starting from a given `Snapshot`, applying only the events with sequence numbers greater than the snapshot's.
- **FR-008**: System MUST utilize unique identifiers (e.g., `Permanode`, `BlobRef`) to securely identity aggregate roots.
- **FR-009**: The VaultEntry aggregate root MUST event-source all child entities (VaultEntry, Label, VaultEntryLabel) through VaultEntry-level events.
- **FR-010**: The PasskeyCredential aggregate root MUST event-source PasskeyCredential mutations through Passkey-level events.

### Key Entities *(include if feature involves data)*

- **Event / Claim**: An immutable assertion targeting a specific entity attribute, carrying an operation type, value, timestamp, and sequence number.
- **Decider**: The business logic component responsible for taking a base state and evolving it by applying an event.
- **Model**: The reconstructed view of an entity's state, alongside its complete audit `Trace`.
- **Snapshot**: A point-in-time materialization of a `Model` and its corresponding event `SequenceNumber` to optimize future hydration.
- **Event Store**: The underlying storage mechanism maintaining the strict, append-only sequence of events.
- **VaultEntry Aggregate**: Aggregate root owning VaultEntry, Label, and VaultEntryLabel entities. Persisted in `core:database` (ChimaliDatabase / SQLCipher).
- **PasskeyCredential Aggregate**: Aggregate root for FIDO2 credential data. Persisted in `feature:fido2` (Fido2Database / SQLDelight KMP).

### Aggregate Root Map (In-Scope)

| Aggregate Root | Owned Entities | Database |
|----------------|----------------|----------|
| VaultEntry | VaultEntry, Label, VaultEntryLabel | core:database (ChimaliDatabase) |
| PasskeyCredential | PasskeyCredential | feature:fido2 (Fido2Database) |

### Out-of-Scope Aggregates (Future Consideration)

| Aggregate Root | Owned Entities | Database | Rationale |
|----------------|----------------|----------|-----------|
| Identity | Identity, IdentityBackup | core:database | Low mutation frequency; extend later |
| RelyingParty | RelyingParty, UserConsentRecord | feature:fido2 | UserConsentRecord is inherently append-only; extend later |
| BluetoothHidSession | BluetoothHidSession | feature:fido2 | Transient session data |
| PairedDevice | PairedDevice | feature:fido2 | Device pairing cache |

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of state mutations for VaultEntry and PasskeyCredential aggregates are recorded as immutable events; in-place data updates are completely eliminated for these entities.
- **SC-002**: 100% of state hydration operations yield deterministic results, identical whether hydrated from scratch or from a snapshot.
- **SC-003**: Snapshots reduce state reconstruction time by at least 90% for entities with >1,000 events compared to full historical replays.
- **SC-004**: Every reconstructed state includes a complete trace log that accurately maps to the underlying event stream.

## Assumptions

- The event store maintains strict global or per-aggregate ordering of sequence numbers.
- Snapshots are safe to periodically discard, as the source of truth remains the immutable event stream.
- Event structure changes (schema evolution) are handled gracefully by the Decider through additive changes and default value support for new fields.
- The two databases (ChimaliDatabase and Fido2Database) each maintain their own EventStore table. Events are not cross-referenced between databases.
- Existing data in the VaultEntry and PasskeyCredential tables will be truncated (cleared) automatically via SQLDelight migration scripts during the initial deployment.
