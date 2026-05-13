# Quickstart: Event Sourcing Architecture

**Feature**: 034-refactor-event-sourcing
**Date**: 2026-05-12

## Architecture Overview

The Event Sourcing architecture replaces direct CRUD operations with an append-only event log. State is reconstructed by replaying events through a pure `Decider` function. There are two separate `EventStore` tables: one in `ChimaliDatabase` (core:data) for the VaultEntry aggregate, and one in `Fido2Database` (feature:fido2) for the PasskeyCredential aggregate.

```
┌──────────────┐     ┌──────────┐     ┌──────────────┐
│   Command    │────▶│ Decider  │────▶│   Events     │
│ (User Intent)│     │ (decide) │     │ (Immutable)  │
└──────────────┘     └────┬─────┘     └──────┬───────┘
                          │                   │
                     ┌────▼─────┐        ┌────▼───────┐
                     │  State   │◀───────│  EventStore│
                     │ (evolve) │        │ (append)   │
                     └────┬─────┘        └────────────┘
                          │
                     ┌────▼─────┐
                     │ Read     │
                     │ Model    │
                     │(project) │
                     └──────────┘
```

## Command Flow (Write Path) with Automated Retry

```kotlin
// 1. Create a command
val command = VaultCommand.CreateEntry(
    id = UUID.randomUUID().toString(),
    identityId = currentIdentityId,
    docId = "doc-001",
    title = "My Credential",
    encryptedPayload = encryptedBytes,
    crdtState = crdtBytes,
)

// 2. Handle through the aggregate service
// The handle function automatically retries upon OptimisticConcurrencyException
val result = vaultAggregateService.handle(command)

// 3. Result contains emitted events (or error)
result.fold(
    onSuccess = { events -> /* events appended, read model updated */ },
    onFailure = { error -> /* validation failed or unresolvable conflict */ },
)
```

## State Reconstruction (Read Path)

```kotlin
// Current state
val currentState = vaultAggregateService.getState(aggregateId)

// Time travel: state at a specific moment
val historicalState = vaultAggregateService.getStateAt(
    aggregateId = aggregateId,
    asOf = Instant.parse("2026-03-15T10:30:00Z"),
)

// Audit Trace
val trace = vaultAggregateService.getTrace(aggregateId)
```

## The Decider Pattern

The Decider is the core of the architecture. It has two pure functions:

### `decide(command, state) → Result<List<Event>>`

Validates the command against the current state and returns events to emit:

```kotlin
// From VaultDecider
override fun decide(command: VaultCommand, state: VaultState): Result<List<VaultEvent>> {
    return when (command) {
        is VaultCommand.CreateEntry -> {
            if (command.title.isBlank()) Result.failure(/* title required */)
            else Result.success(listOf(VaultEvent.EntryCreated(/* ... */)))
        }
        is VaultCommand.UpdateContent -> {
            if (state.isDeleted) Result.failure(/* cannot update deleted entry */)
            if (state.identityId != command.identityId) Result.failure(/* unauthorized */)
            else Result.success(listOf(VaultEvent.EntryUpdated(/* ... */)))
        }
        // ... exhaustive handling
    }
}
```

### `evolve(state, event) → State`

Applies an event to the current state (pure, side-effect free):

```kotlin
override fun evolve(state: VaultState, event: VaultEvent): VaultState {
    return when (event) {
        is VaultEvent.EntryCreated -> state.copy(
            id = event.aggregateId, title = event.title, /* ... */
        )
        is VaultEvent.LabelAttached -> state.copy(
            labels = state.labels + event.labelId,
        )
        is VaultEvent.EntryDeleted -> state.copy(isDeleted = true)
        // ... exhaustive
    }
}
```

## Snapshot Hydration

```kotlin
// The AggregateService internally does:
// 1. Load snapshot (if available)
val snapshot = snapshotRepo.getLatestSnapshot(aggregateId, EventKind.VAULT_EVENT)

// 2. Load only events AFTER the snapshot
val newEvents = eventStoreRepo.getEventsAfter(
    aggregateId,
    afterSequenceNumber = snapshot?.sequenceNumber ?: 0,
)

// 3. Fold from snapshot state
val state = newEvents.fold(snapshot?.state ?: VaultState.EMPTY) { s, e ->
    decider.evolve(s, e as VaultEvent)
}
```

## Key Principles

1. **Events are facts**: Once appended, they are never modified or deleted.
2. **State is derived**: The current state is always the result of folding all events.
3. **Commands are intent**: They describe what the user wants to do, not what happened.
4. **Deciders are pure**: No I/O, no side effects. Fully testable.
5. **Read models are projections**: The existing `VaultEntry` and `PasskeyCredential` tables become materialized views, updated synchronously when events are appended.
6. **Separate databases**: The Vault event store lives in `core:data` while the Passkey event store lives in `feature:fido2`. Do not cross-reference events.
7. **Clean slate**: Existing data in the tables will be truncated upon migration to event sourcing.

## Testing Strategy

```kotlin
// Decider tests are trivial — no mocks needed
@Test
fun `decide rejects update on deleted entry`() {
    val state = VaultState(isDeleted = true, identityId = "user-1")
    val command = VaultCommand.UpdateContent(
        aggregateId = "entry-1",
        identityId = "user-1",
        title = "New Title",
        /* ... */
    )
    val result = decider.decide(command, state)
    assertTrue(result.isFailure)
}

@Test
fun `evolve applies EntryCreated correctly`() {
    val event = VaultEvent.EntryCreated(
        aggregateId = "entry-1",
        sequenceNumber = 1,
        timestamp = Clock.System.now(),
        title = "My Credential",
        /* ... */
    )
    val state = decider.evolve(VaultState.EMPTY, event)
    assertEquals("My Credential", state.title)
}
```
