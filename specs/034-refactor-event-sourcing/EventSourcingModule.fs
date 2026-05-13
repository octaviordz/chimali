// 1. State, Events, and Commands We define the VaultEntry state and
// the discrete facts (Events) that can happen to it.
open System

// 1. Core State
type VaultEntry = {
    Id: Guid
    DocId: string
    Title: string
    EncryptedPayload: byte[]
    CrdtState: byte[]
    Labels: Set<Guid>
    IsDeleted: bool
} with
    static member Empty =
        { Id = Guid.Empty; DocId = ""; Title = ""; EncryptedPayload = [||]
          CrdtState = [||]; Labels = Set.empty; IsDeleted = false }

// 2. Events (Facts)
type VaultEvent =
    | EntryCreated of id: Guid * docId: string * title: string * payload: byte[] * crdt: byte[]
    | EntryUpdated of title: string * payload: byte[] * crdt: byte[]
    | LabelAttached of labelId: Guid

    | LabelDetached of labelId: Guid
    | EntryDeleted

type PasskeyEvent =
    | PasskeyCreated of credentialId: CredentialId * aaguid: string * coseAlgorithm: int * rpId: string * rpName: string * signCount: int * userDisplayName: string * userId: string * userName: string
    | PasskeyUpdated of credentialId: CredentialId * signCount: int
    | PasskeyDeleted of credentialId: CredentialId

// 3. Commands (Intent)
type VaultCommand =
    | CreateEntry of id: Guid * docId: string * title: string * payload: byte[] * crdt: byte[]
    | UpdateContent of title: string * payload: byte[] * crdt: byte[]
    | AddLabel of labelId: Guid
    | RemoveLabel of labelId: Guid

// 4. EventStore
type EventKind = VaultEvent | PasskeyEvent | Claim

type EventStore =
{
    Timestamp: DateTime
// The unique ID of the object (e.g., User-123).
    AggregateId: string
// An incrementing integer to keep events in the correct order.
    SequenceNumber: int
// The kind of event source (e.g., VaultEvent, Claim, Transaction).
    EventKind: EventKind
// JSON containing the actual event details.
    Payload: Json
}


// 2. Evolution Function (The "Apply" Logic)
// This function rebuilds the VaultEntry by folding over past events.
// It is pure and side-effect free.
let apply state event =
    match event with

    | EntryCreated (id, docId, title, payload, crdt) ->
        { state with Id = id; DocId = docId; Title = title;
                     EncryptedPayload = payload; CrdtState = crdt }
    | EntryUpdated (title, payload, crdt) ->
        { state with Title = title; EncryptedPayload = payload; CrdtState = crdt }

    | LabelAttached labelId ->
        { state with Labels = Set.add labelId state.Labels }
    | LabelDetached labelId ->
        { state with Labels = Set.remove labelId state.Labels }
    | EntryDeleted ->
        { state with IsDeleted = true }


// 3. Decision Function (The Business Rules) This is where you validate
// constraints (e.g., "cannot update an archived entry").
let decide command state =
    match command with

    | CreateEntry (id, docId, title, payload, crdt) ->
        if String.IsNullOrWhiteSpace(title) then Error "Title required"
        else Ok [EntryCreated(id, docId, title, payload, crdt)]

    | UpdateContent (title, payload, crdt) ->
        if state.IsDeleted then Error "Cannot update archived entry"
        else Ok [EntryUpdated(title, payload, crdt)]


    | AddLabel labelId ->
        if state.Labels.Contains(labelId) then Ok [] // Idempotent
        else Ok [LabelAttached labelId]

    | _ -> Ok [] // Handle others...



// 4. Projection (Read Model)
// Since your schema includes date_modified and last_backed_up_at, we project
// the events into a record that matches your SQL VaultEntry table for
// easy querying.
type VaultEntryReadModel = {
    Id: Guid
    Title: string
    DateModified: DateTime
    LabelCount: int
}

let project (model: VaultEntryReadModel) event timestamp =
    match event with

    | EntryCreated (id, _, title, _, _) ->
        { model with Id = id; Title = title; DateModified = timestamp; LabelCount = 0 }
    | EntryUpdated (title, _, _) ->
        { model with Title = title; DateModified = timestamp }

    | LabelAttached _ ->
        { model with LabelCount = model.LabelCount + 1; DateModified = timestamp }
    | LabelDetached _ ->
        { model with LabelCount = model.LabelCount - 1; DateModified = timestamp }
    | _ -> model



// To handle a Vault system effectively as it scales, you must manage
// how Identity (who owns the secret) is verified during command processing
// and how Snapshotting prevents performance degradation for entries with
// thousands of updates.
// 1. Handling Identity Context In Event Sourcing, the "Write Model" should be
// agnostic to global state but must enforce ownership. We handle this by
// passing the IdentityId into the decide function as
// part of the command context
type VaultCommand =
    | CreateEntry of id: Guid * identityId: string * title: string // ...
    | UpdateContent of identityId: string * title: string // ...

let decide command state =
    match command with

    | CreateEntry (id, identityId, title) ->
        Ok [EntryCreated(id, identityId, title)]
    | UpdateContent (identityId, title) ->
        // Enforce that only the original owner can update
        if state.IdentityId <> identityId then Error "Unauthorized"
        else Ok [EntryUpdated(title)]


// 2. Managing Long Event Histories (Snapshotting)
// Snapshotting is an optimization where you periodically save
// the current state of an aggregate.
// Instead of replaying 5,000 events, you load the latest snapshot and
// only replay the few events that occurred after it.
// The Snapshot Strategy
// You can implement a strategy that triggers every \(N\) events
// (e.g., every 20 events) or based on time.
type Snapshot = {
    Data: VaultEntry
    Version: int // The event number this snapshot represents
}

// Optimization: Rehydrating state from a Snapshot + remaining Events
let rehydrate (snapshot: Snapshot option) (events: VaultEvent list) =
    let initialState =
        match snapshot with

        | Some s -> s.Data
        | None -> VaultEntry.Empty

    events |> List.fold apply initialState

// 3. Implementation Workflow
// When a request comes in for a VaultEntry:
// Load Snapshot: Fetch the most recent snapshot for the EntryId from your Snapshot Store.
// Load Events: Fetch only the events with a version number higher than the snapshot.Version.
// Rehydrate: Apply those new events to the snapshot state.
// Execute: Pass the command to the decide function.
// Save & Snapshot: If the total event count passes a threshold (e.g., 20), save a new Snapshot record.

// For general reference,
// see https://github.com/tonyx/Sharpino (A little F# event-sourcing library.)
