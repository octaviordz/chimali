# Data Model: Security Assessment and Cleanup Ownership

**Status**: Phase 1 design refreshed to the three owner clarifications. These are audit records and a small internal clipboard contract, not new product database tables. Implementation/runtime results remain Unverified.

## SourceInventory

Fields: assessed commit/tree and clean/dirty designation; build/configuration fingerprints; tool versions; configured module/source roots; per-file path/hash; source set/platform; generated-schema provenance; assessment disposition (`Open`, `Relevant`, `Assessed-no-relevant-usage`); rationale; usage IDs; assessor/date. The module set must match `settings.gradle.kts` (currently 14). New/removed/changed files trigger impact reassessment. Generated code is traced to schema and generated type signatures, not discarded wholesale.

Validation: each file has exactly one current disposition. A no-relevant-usage decision requires recorded inspection rationale, not just an empty search result. A relevant file requires usages or a documented open finding explaining missing enumeration. Other-platform roots remain explicit even when runtime acceptance is Android-only.

## AssessedUsage

Fields: stable audit ID; inventory file and symbol/line; classification ID(s); boundary ID(s), if any; stable BRD/TRD requirement IDs; fields/categories; representation and conversions; application owner; borrowers; ownership-transfer rule; purpose; persistence/logging restrictions; acquisition/terminal events; evidence IDs; finding IDs; assessor/date/revision.

Relationships: a file has zero or more usages; a usage has one or more cleanup obligations and may share canonical classification/boundary entries. Record ephemeral conversion sites separately from long-lived owners. Classifying a value as public metadata never grants permission to log it.

Validation: actual payloads, passwords, recovery words, identifying RP/user data and lookup tokens are never record fields. Source paths, non-sensitive labels and fixture IDs provide traceability. Missing owner, conversion classification or terminal rule prevents verified status.

## CleanupObligation

Fields: obligation ID; usage/owner/borrower IDs; owned versus borrowed; transfer point; termination trigger; applicability and rationale for success/failure/cancellation/session termination; partial-initialization and never-started-work cases; expected erased range/reference-release effect; active-borrower preservation requirement; assertion/procedure; observed outcome; evidence/finding references.

State transitions: `Acquired -> Active -> Terminal -> Erased/Released` for application-owned buffers or permitted reference release. Ownership transfer changes the responsible owner exactly once; borrowers do not become independent owners by convenience. Termination must wait for joined borrowers before shared data is erased. `Transferred`, `Borrowed` and `Provider-retained` are ownership categories, not passing cleanup results. A failed/unknown terminal effect leaves an open finding.

## ClipboardReceipt and PendingCopy

A returned publication request produces a non-secret receipt used to correlate current UI feedback. Receipt fields: process-local generation, scheduled elapsed time and publication status (`MetadataConfirmed` or `Unconfirmed`); the random platform token stays private to the adapter in production. Isolated synthetic tests may inject a controller-generated token through a test-only, non-exported token-source override as specified in the observer contract. Pending fields: process-local generation, random ownership token, `copyElapsedMs`, `scheduledElapsedMs = copyElapsedMs + 30_000`, and job reference. Android times use elapsed realtime, not wall clock. Pending storage is bounded to at most two records: a predecessor and one unconfirmed candidate, each with its own token/due time. A separate two-slot receipt snapshot counts pending and unacknowledged terminal records together; reject copy admission before publication if no slot is available, including when an unconfirmed candidate is pending. Completion or manual clear changes the relevant receipt to a terminal outcome. Admission is allowed when a receipt slot is free and no unconfirmed candidate remains pending; terminal outcomes need acknowledgement or explicit abandonment only when their occupied slots otherwise prevent admission. The fixed 30-second policy is shared; callers cannot override it. No payload, content-derived hash, credential/RP/user identity or identifying label appears in these records, status or timer closures.

The outgoing Android clip description contains the same random token under a namespaced extras key and the sensitive flag set before publication. The token is correlation metadata, not a secrecy/authenticity promise. Only metadata is read for automatic ownership checks; no text reads/coercion or content comparisons.

Transitions:

| Event | Transition/effect |
| --- | --- |
| New copy requested | Candidate generation/token exists only during transfer preparation; caller retains its mutable input until return/cancellation. |
| Preparation/publication throws | Sanitized failure; previous accepted pending copy and timer remain. No false receipt for the failed candidate. |
| Publication returns | Read back description metadata only: matching candidate token gives MetadataConfirmed and transitions prior work to a retained Superseded outcome; otherwise give Unconfirmed, schedule the candidate and retain the predecessor unchanged. At most two pending records; no false copied/replaced claim. |
| Timer becomes runnable | Acquire the owner, compare generation again and record actual execution time separately from scheduled time. Stale callbacks cannot mutate the new copy; a deliberately retained predecessor can act only on its own token when due. |
| Matching readable token | Attempt platform clear, recording returned or rejected status; never infer removal solely from return. |
| Different or missing token in readable metadata | `SkippedReplaced` (includes not-owned); no automatic clear. |
| Null, denied, malformed or otherwise unreadable metadata | `SkippedOwnershipUnavailable`; no automatic clear, no inference of empty clipboard. |
| Automatic attempt/skip completes | Release only this record's pending job/token/metadata, preserving any other retained record; retain its bounded non-secret terminal outcome until its responsible caller acknowledges or explicitly abandons it. No unbounded polling or retry on future focus; release its receipt slot only on acknowledgement/abandonment. |
| Explicit manual clear returns | No automatic ownership gate; cancel both pending timers and transition their receipts to retained manual-clear terminal outcomes. Current entry was targeted but removal remains unconfirmed; receipt slots remain occupied until acknowledged/abandoned. |
| Explicit manual clear throws | Report sanitized failure; preserve any still-pending automatic attempt. |
| Process terminates/restarts | Local pending work is lost. New instance starts with no receipt and cannot claim completion or restore a payload. External evidence may record interruption. |

A delayed timer evaluates once when execution becomes available; a completed skip is terminal. Same-app operations are serialized. The accepted external replacement race between metadata read and clear is documented, not an atomic guarantee.

## ClipboardCleanupStatus and ClipboardObservation

The service exposes a bounded StateFlow snapshot keyed by receipt generation, with at most two slots including pending and unacknowledged terminal outcomes. Values distinguish `Pending`, `ClearRequestSubmitted`, `ClearRejected`, `SkippedReplaced`, `SkippedOwnershipUnavailable`, `Interrupted`, `Superseded`, manual-clear disposition and `Abandoned`, with manual/automatic origin. Cancellation of a scheduled attempt while the app remains able to record state terminates that receipt as `Interrupted`; intentional supersede/manual cancellation retains its own outcome. Neither interruption nor a returned clear request claims clipboard removal. Atomic updates preserve the other slot, so conflation cannot discard an admitted receipt outcome. The caller acknowledges a terminal outcome after handling it, or explicitly abandons it after recording a sanitized disposition; either releases only that slot. On permanent caller disposal, responsibility for its outstanding receipts transfers atomically to the application-scoped clipboard owner, which preserves pending cleanup and records sanitized abandonment when each receipt becomes terminal, releasing that slot. Handoff, terminal updates and acknowledgement are serialized to prevent double disposition; a paused caller remains responsible. No UI feedback is sent to the disposed caller. This process-local handoff does not survive process death. When full, copy admission is rejected before publication and returns a state-neutral `CopyAdmissionRejected` error without creating a receipt or status entry; this refusal is not inserted into the full bounded snapshot because doing so would evict or exceed the admitted receipt records. Explicit manual clear remains available. Process death loses the snapshot and no completion is invented. Cancellation propagates; it is not converted into a successful result. Do not retain raw exceptions or messages in status. Final names may follow project conventions, but these distinctions must remain.

Independent evidence records actual attempt/skip time, observer focus/lock/process conditions, and `OwnMarkerPresent`, `Absent`, `Replaced`, `Inaccessible` or `Unknown`. The test-only controller injects a fresh synthetic token into the target and hands the same token to the observer via a test-package private per-run file, as specified in the contract; it is not an exported production interface or retained report data. `RemovalObserved` is an evidence conclusion requiring an eligible observer and positive capability control, not a synonym for a returned platform call. Force-stop/lost process outcomes are external observations; a dead process cannot emit a final status.

A retained presentation owner distinguishes MetadataConfirmed from Unconfirmed publication before showing copied versus copy-unconfirmed feedback and may show best-effort30 feedback and a later unconfirmed-cleanup result for its live receipt. After process loss, no durable receipt or synthetic completion is invented. The initial wording discloses that interruption can leave the entry available.

## ClipboardVerificationCase

Fields: case/requirement IDs; candidate/build/device cohort; intended schedule; actual execution/attempt/skip; eligible observations and uncertainty; expected scoped outcome; fixture/control IDs; evidence status and limitations.

Keep two dimensions distinct: clipboard outcome versus verification result. Demonstrated correct skip, refusal handling, interruption semantics or an applicable unsupported preview can satisfy that case's scoped criterion even when removal is unknown. Ineligible observation alone, missing execution or inability to establish the tested conditions stays Unverified. A wrong schedule, premature/stale app clear, missing marking, unrelated text read, ignored eligible work or false success is a Failed behavior check. Accepted external atomicity limits are not relabelled as application guarantees.

## DiagnosticCase

Fields: case ID; emission site; reachable build/binding; synthetic fixture IDs and transformation families; message/tag/throwable/fallback path; destination (including rotation); expected allowed operation metadata; forbidden data categories; capture report/hash; evidence/finding IDs. A successful test checks the final destination, not solely the input to a scrubber. Each configured destination and startup/fallback variant has an applicability decision.

## SecurityFinding

Fields: finding ID; kind (`Defect`, `Unassessed`, `Provider-limit`, `Platform-limit`, `Design-gate`); affected usages/requirements; revision and reachability evidence; impact; lifecycle/retention window; remediation proposal; owner; review disposition; verification evidence; status/history.

Workflow: `Open -> Assessed -> Remediation-proposed -> Corrected-unverified -> Verified-closed` for a defect, with reopening on changed scope/evidence. Limits receive an explicit independent review disposition; that disposition alone cannot waive a violated constitutional rule. Design gates close only through supported evidence or an authorized synchronized contract change. Unassessed or unknown origins remain open. Findings are not runtime status values.

## AssessmentEvidence

Every entry includes all ten required TRD keys (additional metadata is allowed):

| Key | Required value |
| --- | --- |
| `requirement` | Stable BRD/TRD IDs, plus feature-local linkage where useful. |
| `scope` | Exact owners/files/outcomes or physical/heap journey, bounded claim. |
| `revision` | Exact assessed commit/tree and build identity; dirty exploration clearly labelled. |
| `named_procedure` | Fully qualified test/procedure ID and version. |
| `environment` | Host/tool versions, device/API, variant/mode, observer and privileges. |
| `expected_observed_result` | Expected criterion, actual observation and timing uncertainty. |
| `date` | Actual local execution date in ISO form. |
| `limitations` | Fakes, unavailable cases, GC/capture effects, accessibility/observer blind spots and build differences. |
| `status` | Runtime: `Passed`, `Failed`, `Deferred` only with approved reference, or `Unverified`; documentary review uses its separate TRD taxonomy. |
| `artifact_link` | Repository-relative sanitized report, with hash and controlled raw-artifact reference where needed. |

Raw artifact metadata: access owner, private storage locator, digest, tool/capture identity, synthetic-state attestation, retention dependency, reviewer access method and disposal record. Keep raw captures accessible while cited evidence relies on them; disposal requires removing or superseding those references first. Do not commit raw dumps or indiscriminate device logs.

Validation: no status may be upgraded by a document review. An executed criterion failure is Failed; unavailable/non-executed cases are Unverified. Empty workloads do not pass. Changed assessed behavior invalidates relevant evidence; unchanged reused evidence needs explicit compatibility rationale.

## AcceptanceRecord

Fields: candidate/build identity; source coverage and open usages; implementation disposition; required runtime matrix; unresolved findings/design gates; provider/platform limitations; evidence index; independent reviewer identity/date/scope/disposition; release disposition.

Implementation completion requires the delivered scoped corrections and mandatory applicable checks, with remaining release obligations listed. Release security acceptance requires all mandatory candidate evidence and independent review, zero unassessed usages in scope and no unresolved required noncompliance under the current scoped contract. Correct handling of the owner-accepted clipboard limits is not itself noncompliance; missing evidence still prevents closure. Neither a partial implementation record nor an acknowledged provider limitation implies blanket erasure. Historical fingerprints remain separate records.
