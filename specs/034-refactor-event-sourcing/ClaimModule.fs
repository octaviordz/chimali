open System

// --- Simulation ---

let myPermanode = Permanode("user123", Guid.NewGuid())
let pnRef = "sha256-abc123" // Mock hash of the permanode blob

let history = [
    Claim(pnRef, "title", "Initial Title", DateTime(2024, 1, 1), Set)
    Claim(pnRef, "tags", "personal", DateTime(2024, 1, 2), Set)
    Claim(pnRef, "title", "Final Version", DateTime(2024, 1, 5), Set) // Mutation via new claim
]

let currentState = Indexer.calculateState pnRef history

// Result: Map [("tags", "personal"); ("title", "Final Version")]
printfn "Current State: %A" currentState


//======================================================================
open System

type BlobRef = string

// Added 'Add' for multi-value support
type ClaimType = Set | Add | Delete

type Blob =

    | Permanode of owner: string * nonce: Guid
    | Claim of target: BlobRef * attr: string * value: string * date: DateTime * op: ClaimType

module Indexer =
    // The state now maps a key to a SET of values
    let calculateState (target: BlobRef) (allBlobs: Blob list) =
        allBlobs

        |> List.choose (function
            | Claim (t, a, v, d, op) when t = target -> Some (a, v, d, op)
            | _ -> None)

        |> List.sortBy (fun (_, _, date, _) -> date)
        |> List.fold (fun (state: Map<string, Set<string>>) (attr, value, _, op) ->
            match op with

            | Set ->
                // Overwrite everything for this attribute with a single new value
                state.Add(attr, Set.singleton value)
            | Add ->
                // Append a value to the existing set (or create a new set)
                let currentSet = state.TryFind attr |> Option.defaultValue Set.empty
                state.Add(attr, currentSet.Add value)

            | Delete ->
                // Perkeep can delete a specific value or the whole attribute
                // Here we model deleting a specific value from the set
                match state.TryFind attr with
                | Some set -> state.Add(attr, set.Remove value)
                | None -> state
        ) Map.empty

// --- Simulation ---

let pnRef = "sha256-abc123"

let history = [
    Claim(pnRef, "title", "My Project", DateTime(2024, 1, 1), Set)
    Claim(pnRef, "tag", "fsharp", DateTime(2024, 1, 2), Add)   // Add first tag
    Claim(pnRef, "tag", "functional", DateTime(2024, 1, 3), Add) // Add second tag
    Claim(pnRef, "tag", "fsharp", DateTime(2024, 1, 4), Delete) // Remove one tag
]

let currentState = Indexer.calculateState pnRef history

// Result:
// title -> {"My Project"}
// tag   -> {"functional"}
printfn "Current State: %A" currentState



//======================================================================
//==  Event Sourcing basics models
//======================================================================

open System

// 0. Immutable Content-Addressable Storage
// Usually a SHA-256 hash
type BlobRef = string

type Blob =
    | Data of content: string
    | Permanode of owner: string * nonce: Guid
    | Claim of target: BlobRef * attr: string * value: string * date: DateTime * op: ClaimType

// 1. Define the Claim
type ClaimKind = Set | Add | Remove

type Claim =
{
    Target: BlobRef
    Attr: string
    Value: string
    Timestamp: DateTime
    Kind: ClaimKind
    // Metadata for the trace
    Reason: string
}

type Blob =
    | Data of content: string
    | Permanode of owner: string * nonce: Guid
    | Claim of claim: Claim

// 2. Define the EventSource
type EventSourceKind = Claim

type EventSource =
{
    Timestamp: DateTime
// The unique ID of the object (e.g., User-123).
    ModelElementId: string
// The unique ID of the object (e.g., User-123).
    AggregateId: string
// An incrementing integer to keep events in the correct order.
    SequenceNumber: int
// The kind of event source (e.g., Claim, Transaction).
    EventSourceKind: EventSourceKind
// Usually a JSON or binary blob containing the actual event details.
    Payload: Json
}

// 3. Define the Trace Entry (The Audit Log)
type TraceEntry =
{
    Timestamp: DateTime
    Description: string
}

type Model =
{
    Attributes: Map<string, Set<string>>
    Trace: TraceEntry list
}

// 4. The Decider (State Reconstruction)
module Decider =
    let emptyModel = { Attributes = Map.empty; Trace = [] }

    // The Evolve function: State -> Claim -> State
    let evolve (model: Model) (claim: Claim) =
        let currentValues = model.Attributes |> Map.tryFind claim.Attr |> Option.defaultValue Set.empty

        let newAttributes, actionDesc =
            match claim.Op with

            | Set ->
                model.Attributes.Add(claim.Attr, Set.singleton claim.Value),
                sprintf "Reset '%s' to '%s'" claim.Attr claim.Value
            | Add ->
                model.Attributes.Add(claim.Attr, currentValues.Add claim.Value),
                sprintf "Added '%s' to '%s'" claim.Value claim.Attr

            | Remove ->
                model.Attributes.Add(claim.Attr, currentValues.Remove claim.Value),
                sprintf "Removed '%s' from '%s'" claim.Value claim.Attr

        {
            Attributes = newAttributes
            Trace = model.Trace @ [{ Timestamp = claim.Timestamp; Description = sprintf "%s (%s)" actionDesc claim.Reason }]
        }

    // Reconstruct state and trace from a stream of claims
    let reconstruct (target: BlobRef) (allClaims: Claim list) =
        allClaims
        |> List.filter (fun c -> c.Target = target)
        |> List.sortBy (fun c -> c.Timestamp)

        |> List.fold evolve emptyModel

// --- Simulation ---

let targetPn = "permanode-123"
let eventHistory = [
    { Target = targetPn; Attr = "Title"; Value = "Project Alpha"; Timestamp = DateTime(2024,1,1); Op = Set; Reason = "Initial creation" }
    { Target = targetPn; Attr = "Tag"; Value = "Experimental"; Timestamp = DateTime(2024,1,2); Op = Add; Reason = "Categorization" }
    { Target = targetPn; Attr = "Title"; Value = "Project Beta"; Timestamp = DateTime(2024,1,3); Op = Set; Reason = "Rebranding" }
]

let result = Decider.reconstruct targetPn eventHistory

// Print the final state
printfn "Current Attributes: %A" result.Attributes

// Print the Trace Log
printfn "\n--- Change Trace ---"
result.Trace |> List.iter (fun t -> printfn "[%s] %s" (t.Time.ToString("yyyy-MM-dd")) t.Description)

//======================================================================
//==  Snapshot support models
//======================================================================

// 1. Define the Snapshot structure
type Snapshot = {
    SequenceNumber: int
    State: Model
}

module SnapshotManager =
    // Hydrate starting from a snapshot instead of from zero
    let hydrateFromSnapshot (snapshot: Snapshot option) (allClaims: Claim list) =
        let initialModel =
            match snapshot with

            | Some s -> s.State
            | None -> Decider.emptyModel

        let lastSeq = snapshot |> Option.map (fun s -> s.SequenceNumber) |> Option.defaultValue 0

        // Filter for claims the snapshot hasn't seen yet
        let newClaims =
            allClaims

            |> List.sortBy (fun c -> c.Timestamp)
            |> List.skip lastSeq

        let finalModel = List.fold Decider.evolve initialModel newClaims

        {
            SequenceNumber = lastSeq + newClaims.Length
            State = finalModel
        }

// --- Simulation ---

// Imagine we previously saved this after the first 2 events
let oldSnapshot = {
    SequenceNumber = 2
    State = {
        Attributes = Map.ofList [("Title", Set.singleton "Project Alpha"); ("Tag", Set.singleton "Experimental")]
        Trace = [{ Timestamp = DateTime(2024,1,1); Description = "Initial creation" }; { Timestamp = DateTime(2024,1,2); Description = "Categorization" }]
    }
}

// New claims arrive
let newHistory = history // The same history list from the previous step

// Hydration is now faster because it skips the first 2 events
let updatedSnapshot = SnapshotManager.hydrateFromSnapshot (Some oldSnapshot) newHistory

printfn "Hydrated up to Sequence: %i" updatedSnapshot.SequenceNumber
printfn "Final Title: %A" (updatedSnapshot.State.Attributes |> Map.find "Title")


//======================================================================
