package com.chimali.core.domain.eventsourcing

/**
 * Generic interface for the Decider pattern.
 * Responsible for business logic (decide) and state evolution (evolve).
 *
 * @param Command The type of commands that can be executed.
 * @param State The type of the aggregate root state.
 * @param Event The type of domain events emitted.
 */
interface Decider<Command, State, Event> {
    /**
     * The initial state of the aggregate root before any events are applied.
     */
    val initialState: State

    /**
     * Evaluates a command against the current state and returns a list of events to be persisted.
     * This is where business rules and validation logic reside.
     */
    fun decide(
        state: State,
        command: Command,
    ): List<Event>

    /**
     * Evolves the current state by applying a single domain event.
     * This must be a pure function with no side effects.
     */
    fun evolve(
        state: State,
        event: Event,
    ): State
}
