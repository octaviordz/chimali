# Event Store Contract

**Feature**: 034-refactor-event-sourcing
**Date**: 2026-05-12 (updated with clarification results)

## Overview

This document defines the contracts (interfaces) exposed by the Event Sourcing infrastructure layer. These are the boundaries between the domain layer and the persistence layer. Shared interfaces live in `core:domain` (`commonMain`). Implementations are split per database module.

---

## 1. EventStoreRepository

The primary interface for appending and reading events. Placed in `core:domain` (`commonMain`).

```kotlin
interface EventStoreRepository {

    /**
     * Appends a list of events for a given aggregate.
     * Events MUST be appended atomically within a single transaction.
     *
     * @param events The ordered list of events to append.
     * @throws OptimisticConcurrencyException if the expected sequence number
     *         does not match the current sequence number in the store.
     */
    suspend fun appendEvents(events: List<DomainEvent>): Result<Unit>

    /**
     * Retrieves all events for a given aggregate, ordered by sequence number.
     *
     * @param aggregateId The unique identifier of the aggregate.
     * @return Ordered list of events.
     */
    suspend fun getEvents(aggregateId: String): Result<List<DomainEvent>>

    /**
     * Retrieves events for a given aggregate starting from a specific
     * sequence number (exclusive). Used for snapshot-accelerated hydration.
     *
     * @param aggregateId The unique identifier of the aggregate.
     * @param afterSequenceNumber Only events with sequence_number > this value are returned.
     * @return Ordered list of events after the given sequence number.
     */
    suspend fun getEventsAfter(
        aggregateId: String,
        afterSequenceNumber: Long,
    ): Result<List<DomainEvent>>

    /**
     * Retrieves events for a given aggregate up to (and including) a specific
     * timestamp. Used for temporal queries (time travel).
     *
     * @param aggregateId The unique identifier of the aggregate.
     * @param upToTimestamp Events up to and including this timestamp.
     * @return Ordered list of events up to the given timestamp.
     */
    suspend fun getEventsUpTo(
        aggregateId: String,
        upToTimestamp: Instant,
    ): Result<List<DomainEvent>>

    /**
     * Exports the full event stream for a given aggregate as a portable
     * serialized format. Used for debugging (Constitution VIII: Debuggability).
     *
     * @param aggregateId The unique identifier of the aggregate.
     * @return The serialized event stream as a byte array (JSON).
     */
    suspend fun exportEventStream(aggregateId: String): Result<ByteArray>
}
```

### Invariants

- Events MUST be appended atomically. Partial writes are forbidden.
- `appendEvents` MUST verify `(aggregate_id, sequence_number)` uniqueness. If a conflicting sequence number exists, the call MUST fail with an `OptimisticConcurrencyException`.
- Events MUST NOT be deleted or modified after append. No `delete` or `update` methods exist on this interface.
- Event payloads MUST be encrypted with AES-256-GCM before storage.

---

## 2. SnapshotRepository

The interface for saving and loading aggregate snapshots. Placed in `core:domain` (`commonMain`).

```kotlin
interface SnapshotRepository {

    /**
     * Saves a snapshot for a given aggregate. Upserts (replaces the
     * previous snapshot for the same aggregate + event kind).
     *
     * @param snapshot The snapshot to save.
     */
    suspend fun <T> saveSnapshot(snapshot: Snapshot<T>): Result<Unit>

    /**
     * Loads the most recent snapshot for a given aggregate.
     *
     * @param aggregateId The unique identifier of the aggregate.
     * @param eventKind The event kind (to discriminate aggregate types).
     * @return The latest snapshot, or null if no snapshot exists.
     */
    suspend fun <T> getLatestSnapshot(
        aggregateId: String,
        eventKind: EventKind,
    ): Result<Snapshot<T>?>
}
```

### Invariants

- Snapshot save is an **upsert**: only the latest snapshot per `(aggregate_id, event_kind)` is retained.
- If a snapshot is corrupted or cannot be deserialized, `getLatestSnapshot` MUST return `null` (graceful degradation to full replay).
- Snapshot state MUST be encrypted with AES-256-GCM before storage.

---

## 3. Decider Contract (Domain Service)

The pure functional contract for the Decider pattern. Not a repository—this is pure domain logic with no I/O.

```kotlin
interface Decider<State, Command, Event> {

    /** The empty initial state of the aggregate. */
    val emptyState: State

    /**
     * Evolve (apply) a single event to the current state.
     * MUST be pure and side-effect free.
     *
     * Maps to F# `let apply state event` (EventSourcingModule.fs, line 60).
     *
     * @param state The current state.
     * @param event The event to apply.
     * @return The new state after applying the event.
     */
    fun evolve(state: State, event: Event): State

    /**
     * Decide whether a command is valid given the current state.
     * Returns a list of events to emit, or an error.
     * MUST be pure and side-effect free.
     *
     * Maps to F# `let decide command state` (EventSourcingModule.fs, line 79).
     *
     * @param command The command to validate.
     * @param state The current state.
     * @return Result containing the list of events to emit, or a DomainError.
     */
    fun decide(command: Command, state: State): Result<List<Event>>
}
```

### Invariants

- `evolve` and `decide` MUST be pure functions (no I/O, no side effects).
- `decide` MUST validate business rules (e.g., "cannot update a deleted entry", "only the owning identity can modify").
- `evolve` MUST handle all event variants exhaustively (sealed interface guarantees this at compile time).

---

## 4. AggregateService Contract (Orchestrator)

The service that coordinates loading, deciding, appending, and snapshotting.
Implements **automated retry** on concurrency conflicts per clarification.

```kotlin
interface AggregateService<State, Command, Event> {

    /**
     * Handles a command by:
     * 1. Loading snapshot (if available)
     * 2. Loading events after snapshot
     * 3. Rehydrating state (maps to F# `let rehydrate`, line 163)
     * 4. Deciding (validating command)
     * 5. Appending new events
     * 6. Updating read model projection
     * 7. Creating snapshot if threshold reached
     *
     * On OptimisticConcurrencyException, the system MUST automatically
     * retry by re-hydrating the latest state and re-executing decide.
     * Maximum retry count is configurable (default: 3).
     *
     * @param command The command to handle.
     * @return Result with the new events emitted, or an error.
     */
    suspend fun handle(command: Command): Result<List<Event>>

    /**
     * Retrieves the current state of an aggregate by rehydrating from
     * snapshot + events.
     *
     * @param aggregateId The unique identifier of the aggregate.
     * @return The current state.
     */
    suspend fun getState(aggregateId: String): Result<State>

    /**
     * Retrieves the state of an aggregate at a specific point in time.
     * Used for temporal queries (time travel, FR-005).
     *
     * @param aggregateId The unique identifier of the aggregate.
     * @param asOf The timestamp to reconstruct state up to.
     * @return The state as it existed at the given timestamp.
     */
    suspend fun getStateAt(aggregateId: String, asOf: Instant): Result<State>

    /**
     * Retrieves the complete audit trace for an aggregate.
     *
     * @param aggregateId The unique identifier of the aggregate.
     * @return Ordered list of trace entries (FR-004).
     */
    suspend fun getTrace(aggregateId: String): Result<List<TraceEntry>>
}
```

---

## 5. OptimisticConcurrencyException

```kotlin
/**
 * Thrown when an event append fails due to a sequence number conflict.
 * The caller (AggregateService) should catch this and retry.
 */
class OptimisticConcurrencyException(
    val aggregateId: String,
    val expectedSequenceNumber: Long,
    val actualSequenceNumber: Long,
) : Exception(
    "Concurrency conflict on aggregate '$aggregateId': " +
        "expected sequence $expectedSequenceNumber, found $actualSequenceNumber"
)
```

---

## Implementation Notes

### Per-Database Implementations

| Contract | ChimaliDatabase (core:data) | Fido2Database (feature:fido2) |
|----------|----------------------------|-------------------------------|
| `EventStoreRepository` | `EventStoreRepositoryImpl` | `PasskeyEventStoreRepositoryImpl` |
| `SnapshotRepository` | `SnapshotRepositoryImpl` | `PasskeySnapshotRepositoryImpl` |
| `Decider` | `VaultDecider` (core:domain) | `PasskeyDecider` (core:domain) |
| `AggregateService` | `VaultAggregateServiceImpl` | `PasskeyAggregateServiceImpl` |
