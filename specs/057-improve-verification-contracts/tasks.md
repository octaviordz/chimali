# Tasks: Verifiable Security and Quality Contracts

**Input**: Design documents from `specs/057-improve-verification-contracts/`.

**Prerequisites**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [review contract](contracts/documentation-review.md), [amendment proposal](constitution-amendment.md), and [quickstart.md](quickstart.md).

**Scope**: Constitution and related documentation only. Tasks implement policies, registers, and documentary review. They do not implement or execute application tests, benchmarks, device audits, or code/build/CI changes. No commit is requested; existing commit/CI policies are not waived.

**Organization**: One phase per user story, with document-review acceptance. Only actual completed work may be checked. Future runtime policy is never marked as executed verification.

## Format and Path Conventions

- Tasks use `- [ ] Tnnn [P?] [USn?] Description with exact repository-relative paths`.
- `[P]` identifies work on different files that can run alongside other ready tasks after its prerequisites. It does not authorize parallel edits to shared authorities.
- Setup, foundation, and final tasks have no story label; every user-story task has its story label.
- Paths are relative to the repository root. Review and follow-up records are Markdown documents, not application models or new services.
- Stable feature-local record IDs must be unique within their register/matrix; existing governing requirement IDs must not be deleted or reused.

## Phase 1: Setup (Shared Documentary Records)

**Purpose**: Establish reviewable scope and preserve pre-existing work without changing live governance.

- [X] T001 Record the starting branch/revision, existing worktree changes, documentation-only allowlist from plan.md, and no-runtime-execution boundary in `specs/057-improve-verification-contracts/documentation-review.md`; create FR-001–FR-020 and SC-001–SC-007 review rows with pending documentary dispositions rather than implied passes.
- [X] T002 Create `specs/057-improve-verification-contracts/follow-ups.md` with unique local IDs, governing requirement/policy area, excluded work, exclusion reason, verification status, preserved authorization/deferral reference, responsible role, and needed completion evidence; use lifecycle `Identified → Assigned → Separately authorized → Completed with evidence` and do not mark excluded runtime work complete.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Fix the shared documentary contracts before story-specific edits.

- [X] T003 Inventory the exact current sections and stable IDs in `.specify/memory/constitution.md`, `docs/brd.md`, `docs/trd.md`, and `.agents/rules/specify-rules.md` in `specs/057-improve-verification-contracts/documentation-review.md`; map all five backlog areas and preserve authority precedence, existing budgets, `FR-UI-011`, feature-055 T084/T085/partial SC-007, device limitations, vault pending evidence, and exact-source security-review scopes.
- [X] T004 Add the shared future-evidence schema and status definitions under `docs/trd.md` §9 and cross-reference them from `.agents/rules/specify-rules.md` §1: requirement, scope, revision, named procedure, environment, expected/observed result, date, limitations, status, and artifact link; enforce the data-model constraint "No records may copy real secrets or identifying user data" and distinguish documentary `Satisfied` / `Needs correction` / `Pending approval` from runtime `Passed` / `Failed` / `Deferred` / `Unverified`.
- [X] T005 Establish the implementation review and follow-up validation rules in `specs/057-improve-verification-contracts/documentation-review.md` and `specs/057-improve-verification-contracts/follow-ups.md`, quoting the model constraints "no accepted/adopted status without reviewer/date/revision evidence", "no adoption date for a pending proposal", and "preserve ratification date"; identify the final adoption gate and record the pre-existing BRD §9 coverage-policy discrepancy as a separate follow-up without altering coverage requirements.

**Checkpoint**: Scope, authority mapping, status semantics, approval fields, and follow-up structure are ready. No new runtime result or amendment adoption is claimed.

## Phase 3: User Story 1 — Review Clear Constitutional Obligations (Priority: P1) — MVP

**Goal**: Prepare exact proposed amendment wording and a five-area supporting-document change map for proposal-readiness review. Detailed synchronized-package acceptance follows US2–US5 under T031–T036.

**Independent Test**: Review proposed wording, rationale, version impact, authority mappings, and approval state against US1's proposal-readiness scenarios. No application run or completion of the detailed US2–US5 edits is necessary. Final synchronized-package review and adoption remain separate gates under T031–T036.

- [X] T006 [US1] Finalize the exact Principle V addition and all five per-area dispositions in `specs/057-improve-verification-contracts/constitution-amendment.md`; retain the three existing Principle V bullets and all other principle wording, explain provisional 4.0.0 → 4.1.0 MINOR impact, and record the lifecycle `Draft → Ready for review → Approved / Rejected / Changes requested` without changing the live constitution.
- [X] T007 [US1] Prepare the proposed version/date and sync-impact-report changes in `specs/057-improve-verification-contracts/constitution-amendment.md`, mapping BRD/TRD/rules/register edits and preserving the original ratification date; record "Material edits after approval require renewed review of the changed package" and leave adoption metadata pending until a concrete package has a disposition.
- [X] T008 [US1] Add concise verification-policy and authority references at the relevant security, accessibility, rendering, and integration sections of `docs/brd.md`, retaining current IDs, full numeric budgets, and explicit implementation deferrals; identify the amendment as proposed wherever adoption is not yet recorded and avoid links to files that do not yet exist.
- [X] T009 [US1] Add the five-area verification-policy map under `docs/trd.md` §9 and align references in `.agents/rules/specify-rules.md` §1, keeping procedures below constitutional principles and distinguishing future obligations from current product compliance; preserve the existing live-constitution authority until adoption.
- [X] T010 [US1] Perform the US1 proposal-readiness acceptance scenarios and record exact section references, reviewer/date, five dispositions, rationale/version checks, planned and unfinished supporting edits, and explicit approval status in `specs/057-improve-verification-contracts/documentation-review.md`; report the US1 milestone as proposal-ready, leaving final synchronized-package acceptance to T031–T036 without asserting final document approval, adoption, security acceptance, or runtime compliance.

**Checkpoint**: Review-ready amendment and supporting-policy map deliver the MVP. Detailed story-specific policies/registers and package adoption can follow without inventing execution evidence.

## Phase 4: User Story 2 — Resolve Validation and Sensitive-Data Policy (Priority: P1)

**Goal**: Reconcile validation guidance and consolidate existing security classifications/platform usages.

**Independent Test**: Compare the final policy across authorities; trace documented vault and local-key/passkey records to dated historical sources and explicit evidence gaps. No new source/device audit.

- [X] T011 [US2] Rewrite both adjacent domain-error and input-validation paragraphs in `.agents/rules/specify-rules.md` §5.2 together: expected external rejection uses typed `Outcome.Failure` or documented protocol mapping; internal `require()` / `check()` assertions remain distinct, and ordinary external failures cannot escape component boundaries; preserve applicable §3.5 boundary encapsulation.
- [X] T012 [US2] Add validation/negative-case guidance in `docs/trd.md` §9 and synchronize `.agents/rules/specify-rules.md` §1/§5.2 for UI, protocol, import, and persisted/replayed input, applicable field/state/authorization/cross-field constraints, invalid classes/limits, exact outcomes, forbidden side effects, and owned cleanup; avoid redundant validation and invented fields for parameterless operations, and require future evidence rather than new runs here.
- [X] T013 [P] [US2] Create and populate `docs/security/data-classification.md` from existing vault and feature-055 documentary reviews with stable local IDs, fields/categories, documentary scope, sensitivity, owner, representations, persistence/logging restrictions, lifetime, cleanup, requirements, provenance, and gaps; apply "Unknown facts must be marked `Unresolved` with a follow-up reference" and "no secret values or identifying examples", and distinguish public derivation metadata from privacy-sensitive associations.
- [X] T014 [US2] After T013, create and populate `docs/security/platform-text-boundaries.md` with classification links, exact documented dependency/API/usage, missing mutable-interface reason, purpose/lifetime/release controls, runtime limits, original evidence revision/date, reviewer/disposition or explicit gap, and follow-up IDs; preserve "Cryptographic key material, seeds, and serialization buffers never qualify for the platform UI text exception" and "Missing reviewer evidence remains missing; the consolidating author is not automatically the prior security reviewer"; record prohibited/internal usages as findings rather than permissions.
- [X] T015 [US2] Link both populated registers from `docs/brd.md` security obligations, `docs/trd.md` §5.1, and `.agents/rules/specify-rules.md` §5.5; reconcile designated vault changelog/handoff/linked audit and feature-055 security-review checkpoints with original dates/scopes, supersession, and gaps without rewriting historical records or certifying current source.
- [X] T016 [US2] Record input-test/code gaps, missing security evidence, and any separately needed current-source audit in `specs/057-improve-verification-contracts/follow-ups.md`; preserve the classification constraint "runtime compliance remains a separate assessment with its own dated evidence" and do not expand reviewer fingerprints or provider-erasure assurance.
- [X] T017 [US2] Execute the US2 document-review scenarios in `specs/057-improve-verification-contracts/documentation-review.md`: check both validation paragraphs for contradictions, inspect required fields in every consolidated register row, trace one vault and one local-key/passkey example, and verify explicit gaps/noncompliance instead of new exemptions; map FR-003–FR-008 and SC-002/SC-003.

**Checkpoint**: Validation policy is consistent and both populated registers are traceable to existing evidence or explicit findings. Documentary reconciliation is distinct from runtime compliance.

## Phase 5: User Story 3 — Specify Integration Verification Obligations (Priority: P2)

**Goal**: Define separate credential entry-path lifecycles and safe-failure coverage for future verification.

**Independent Test**: Inspect two lifecycle matrices and all 12 failure-category applicability decisions, with expected outcomes and future evidence requirements.

- [X] T018 [US3] Add independent Bluetooth relying-party and Android Credential Manager lifecycle matrices to `docs/trd.md` §9 and link from §4.2/§4.3; specify registration, encrypted persistence, restart/reconstruction, authentication/retrieval, applicable cryptographic response verification, setup/entry/exit points, real/substituted components, restart method, expected outcomes, and required future environment/evidence.
- [X] T019 [US3] Add all six failure categories per path in `docs/trd.md` §9—malformed input, denied verification, cancellation, timeout, disconnect, persistence failure—with included coverage or explicit applicability rationale; specify forbidden success/output, cleanup, and event/projection results while preserving committed append-only history, and apply "`Specified` is a documentary status, not a passing runtime result".
- [X] T020 [US3] Replace vague integration scope in `docs/brd.md` §9 and `.agents/rules/specify-rules.md` §1 with the explicit minimum lifecycle/failure coverage and TRD procedure links, retaining stable HID/passkey IDs and evidence distinctions between host/component substitutes and real device/transport execution.
- [X] T021 [US3] Review both matrices against `specs/057-improve-verification-contracts/contracts/documentation-review.md`, record FR-009–FR-011/SC-004 results in `specs/057-improve-verification-contracts/documentation-review.md`, and add excluded integration/device execution work to `specs/057-improve-verification-contracts/follow-ups.md`; do not require a run to accept the documentation.

**Checkpoint**: Two future journey matrices and 12 applicability decisions are complete; no integration test or hardware scenario is claimed executed.

## Phase 6: User Story 4 — Specify Accessible Task Verification (Priority: P2)

**Goal**: Correct description policy and define usable accessibility assessment procedures without lifting deferrals.

**Independent Test**: Review names/roles/actions/states/grouping, four task categories, scaling/contrast criteria, and screen-reader procedure against existing obligations/deferral.

- [X] T022 [US4] Correct the universal `contentDescription` statement in `docs/trd.md` §6 to require meaningful accessible names, roles, actions, relevant states, and grouping, allowing visible-text labels and intentionally undescribed decorative graphics; preserve the unrelated 48dp touch-target and high-legibility requirements.
- [X] T023 [US4] Document the four-category vault/credential-management/registration/authentication assessment matrix under `docs/trd.md` §9, covering supported themes, text scaling through 200%, essential readability/reachability and clipping/overlap, and applicable contrast categories/ratios consistent with `FR-UI-011`; cite authoritative standards for any numeric standards detail added rather than imposing one blanket ratio on all elements.
- [X] T024 [US4] Add the future screen-reader walkthrough procedure to `docs/trd.md` §9 and synchronize references in `.agents/rules/specify-rules.md` §1/§5.4 and `docs/brd.md` UI requirements: focus, labels, state/errors, dialogs, essential action completion, authorized secret disclosure, and future device/OS/assistive-technology metadata; preserve the explicit `FR-UI-011` implementation deferral without conducting walkthroughs.
- [X] T025 [US4] Record FR-012–FR-014/SC-005 documentary review results in `specs/057-improve-verification-contracts/documentation-review.md` and excluded rollout/TalkBack/scaling/contrast execution work in `specs/057-improve-verification-contracts/follow-ups.md`; verify all four categories have criteria and no new accessibility compliance claim is made.

**Checkpoint**: Accessibility policy is correct and reviewable; deferred implementation and missing execution remain separate product gaps.

## Phase 7: User Story 5 — Specify Repeatable Rendering Verification (Priority: P2)

**Goal**: Define future measurement requirements and trustworthy result handling while preserving existing budgets.

**Independent Test**: Review two minimum journeys, all required contract fields, review responsibility, retained numeric limits, and missing/unsupported-workload rules without running a benchmark.

- [X] T026 [US5] Update `docs/brd.md` `NFR-PERF-020` references to include vault list scrolling and password detail inspection as minimum verification journeys, preserving the complete existing obligation verbatim: consistent 60 FPS, zero frozen frames (>700 ms), and <1% slow frames (>16 ms), without narrowing general UI coverage or changing other budgets.
- [X] T027 [US5] Add rendering measurement-contract requirements in `docs/trd.md` §9 for device/OS/refresh rate, production-matching variant/compilation mode, seeded dataset/size/initial state, navigation/content/interaction, warm-up/repetitions/window, metric/interpretation/statistic/numeric threshold/aggregation, budget relationship, limitations/older-device method, and future metadata; name the performance verification owner and require engineering review before future compliance measurement, without choosing hardware or replacement thresholds here.
- [X] T028 [US5] Synchronize rendering/evidence links in `.agents/rules/specify-rules.md` §1 and `docs/trd.md` relevant sections; require proof of future discovery/workload execution, fail attempted empty workloads, leave unavailable hardware/metrics unverified unless an applicable reviewed method exists, distinguish processing duration from missed display deadlines, and prohibit collection/averages or inferred cross-device equivalence from establishing compliance alone.
- [X] T029 [US5] Record FR-015–FR-017/SC-006 documentary acceptance results in `specs/057-improve-verification-contracts/documentation-review.md` and separate benchmark discovery/targeting/workload fixes, parameter review, and actual measurements in `specs/057-improve-verification-contracts/follow-ups.md`; preserve feature-055 performance deferrals and report no new benchmark results.

**Checkpoint**: Both minimum journeys and future measurement contracts are specified; concrete parameter selection, fixes, and measurements remain follow-ups.

## Phase 8: Polish, Package Review, and Conditional Adoption

**Purpose**: Finish the complete concrete synchronized package before obtaining the required amendment disposition; keep documentary completion distinct from adoption and product verification.

- [X] T030 Update each of the five items in `docs/backlog/constitution-testability-improvements.md` with the policy/documentary delivery references and separate implementation/execution follow-up IDs, using pending-review wording until final disposition and never marking the complete runtime backlog closed solely because documents changed.
- [X] T031 Review every FR-001–FR-020 and SC-001–SC-007 against exact implemented sections/records in `specs/057-improve-verification-contracts/documentation-review.md`; reconcile authority precedence, stable IDs, all five constitutional dispositions, record-field completeness, 12 integration applicability decisions, four accessibility categories, two rendering journeys, and explicit deferrals/limitations; record corrections rather than blanket passes.
- [X] T032 Apply the documentary checks in `specs/057-improve-verification-contracts/quickstart.md` and record scope/diff inspection, local-link results, unresolved-marker/whitespace checks including untracked files, and no-runtime-run confirmation in `specs/057-improve-verification-contracts/documentation-review.md`; verify zero application/build/CI/test-code changes and correct any document issues without running CI or tests.
- [X] T033 Assemble the exact final synchronized review package in `specs/057-improve-verification-contracts/constitution-amendment.md`, identifying base revision and current-document hashes or an equivalently unambiguous package manifest for BRD/TRD/rules/registers and proposed constitution text; reconcile `specs/057-improve-verification-contracts/documentation-review.md` and `specs/057-improve-verification-contracts/follow-ups.md` so approval is requested only for concrete completed work.
- [X] T034 Record the actual engineering disposition for the exact package in `specs/057-improve-verification-contracts/constitution-amendment.md` and `specs/057-improve-verification-contracts/documentation-review.md`, including reviewer identity/date/revision when supplied; if unavailable or rejected, record `Pending approval` or rejection explicitly, leave T035 unchecked, and report the amendment as proposed without inventing approval or blocking already-authorized documentary preparation.
- [X] T035 Only after affirmative package approval in T034, adopt the approved Principle V bullet in `.specify/memory/constitution.md`, update the actual amendment date and approved version (provisionally 4.1.0), preserve ratification date/unchanged principles, and replace the sync-impact report; update `specs/057-improve-verification-contracts/constitution-amendment.md` with adoption evidence, applying "Material edits after approval require renewed review of the changed package" rather than changing approved semantics silently.
- [X] T036 After disposition, recheck synchronized links/metadata, documentary scope, historical assurance boundaries, and per-item statuses in `specs/057-improve-verification-contracts/documentation-review.md` and `docs/backlog/constitution-testability-improvements.md`; report either the actually adopted version after T035 or a documentation-ready/pending-adoption package with T035 still open, and keep all excluded runtime work in `specs/057-improve-verification-contracts/follow-ups.md`.

**Checkpoint**: The package has an explicit real disposition. Pending/rejected adoption is never reported as an enacted amendment; code/test/device work remains outside scope. All tasks are complete only if their work occurred, including affirmative adoption when applicable.

## Dependencies & Execution Order

### Phase Dependencies

1. Setup: T001 → T002.
2. Foundation: T003 → T004 → T005; depends on setup and blocks all story tasks.
3. US1: T006 → T007 → T008 → T009 → T010. This creates a review-ready proposed package and common references, not premature final approval.
4. US2: T011 → T012; T013 can run alongside T011 after US1 because it uses a different file and foundation schemas. T014 depends on T013. T015 waits for T012/T014, then T016 → T017.
5. US3: T018 → T019 → T020 → T021.
6. US4: T022 → T023 → T024 → T025.
7. US5: T026 → T027 → T028 → T029.
8. Final: T030 → T031 → T032 → T033 → T034. T035 requires affirmative approval, not simply completion of T034. T036 follows T035 when adopted, or follows T034 to truthfully report pending/rejected adoption while T035 remains open.

### User Story Completion Graph

```text
Setup → Foundation → US1 (review-ready amendment MVP)
                         ├→ US2 (validation/registers)
                         ├→ US3 (integration policy)
                         ├→ US4 (accessibility policy)
                         └→ US5 (rendering policy)
US2 + US3 + US4 + US5 → Final package review → Disposition
                                             ├→ Approval → Adoption → Final report
                                             └→ Pending/rejected → Explicit open adoption report
```

US2–US5 are conceptually independent after the common US1 map: each has its own document-review acceptance criteria and requires no other story's runtime implementation. The default execution order is US1 → US2 → US3 → US4 → US5 to avoid conflicts in `docs/brd.md`, `docs/trd.md`, `.agents/rules/specify-rules.md`, and shared review/follow-up records. The final consolidated approval waits for all supporting edits; it does not create a dependency cycle with US1's proposed-package checkpoint.

### Parallel Execution Examples Per Story

Parallelism is optional; no agents or extra chats are required. After prerequisites, independent review can occur alongside drafting only with separate file ownership. Merge observations into shared review records sequentially.

| Story | Safe parallel example | Required join / restriction |
| --- | --- | --- |
| US1 | T008 BRD reference edits alongside read-only review of T006/T007's amendment rationale | Both finish before T009/T010; do not edit the same amendment/review file concurrently. |
| US2 | T011 validation-policy edit and T013 `[P]` classification-register population | T014 waits for classification IDs; T015 joins final validation and both registers. |
| US3 | After T019, T020 BRD/rules edits alongside read-only lifecycle/failure-matrix review | Findings join T021; no concurrent TRD writes. |
| US4 | After T023, T024 BRD/rules reference drafting alongside read-only review of TRD accessible-label/contrast criteria | Serialize T024's TRD additions and T025 shared-record writes. |
| US5 | After T027, T028 rules-reference edits alongside read-only check of preserved BRD numeric limits | Finish T028 and review before T029; no concurrent TRD/review-record writes. |

Only T013 carries `[P]` because it has a concrete different-file peer among implementation tasks. Read-only review opportunities do not imply blanket parallel execution of shared-authority edits.

## Requirements Coverage

| Requirements / outcomes | Task ownership |
| --- | --- |
| FR-001/FR-002/FR-019; SC-001 | T001–T010, T031–T036 |
| FR-003–FR-005; SC-002 | T011–T012, T017 |
| FR-006–FR-008; SC-003 | T013–T017 |
| FR-009–FR-011; SC-004 | T018–T021 |
| FR-012–FR-014; SC-005 | T022–T025 |
| FR-015–FR-017; SC-006 | T026–T029 |
| FR-018 | T004, T009, T017, T021, T025, T029, T031–T032 |
| FR-020; SC-007 | T002–T005, T016, T021, T025, T029–T036 |

## Implementation Strategy

### MVP First

Complete T001–T010 and review US1 independently. This produces a concise proposed amendment, exact rationale/versioning, and supporting-policy map. Do not adopt the constitution or claim runtime compliance at that checkpoint. The remaining stories supply the final detailed package before the governance disposition.

### Incremental Delivery

Deliver and review US2's validation/registers, then US3's integration, US4's accessibility, and US5's rendering documentation. At every checkpoint, retain dated evidence and explicit gaps. Complete final synchronization/scope checks before requesting a disposition for the concrete package. Adopt only an affirmatively approved amendment.

### Execution Boundaries

Document checks and read-only inspection are authorized. All runtime/code/test/device work is captured as follow-up, never added as an implementation task here. Preserve existing local-CI/commit policy without introducing a bypass; task generation does not request a commit. External-source lookups are needed only when adding precise standards-derived detail, using authoritative sources and retaining attribution.

## Phase 9: Convergence

**Assessment date**: 2026-10-23. Scope: current documentation only; no runtime tests, application changes, Git comparison, or new audit. Existing checked tasks remain unchanged; the findings below identify remaining documentary work despite their completion markers.

**Findings**: F1–F3 are CRITICAL constitutional contradictions; F4–F6 are HIGH; F7 is MEDIUM. Complete the security-policy corrections before revalidating the final package. Review resulting behavior and evidence, not the earlier completion claims. Preserve actual dated history and obtain any required disposition for material post-approval edits.

- [X] T037 CRITICAL — Correct `docs/security/platform-text-boundaries.md` entries BND-UI-003, BND-SYS-002, BND-SYS-003, and BND-ERR-001 so cryptographic material, seeds, normalization/serialization internals, and diagnostic APIs cannot acquire platform UI text permission through a `Reviewed permitted` label; distinguish genuine input/rendering boundary scope from provider/runtime limitations and prohibited or unresolved usages, cite the exact historical source/reviewer/disposition where available, and reconcile affected claims in `specs/057-improve-verification-contracts/documentation-review.md` without inventing exemptions or fresh approval; finding F1 per Constitution I, FR-007, FR-008, US2/AC4 (contradicts).
- [X] T038 CRITICAL — Reconcile `docs/security/data-classification.md` rows CLS-FIDO-001/002 and related logging, representation, and ownership rules against Constitution I and the dated feature-055 security review: remove unsupported blanket permissions for privacy-sensitive RP/account text retention, garbage-collection-only cleanup, and audit logging; distinguish public parameters from identifying associations, record unsupported claims as unresolved findings, and review blind-index logging and storage/provenance statements instead of treating classification as authorization; update affected register links, `specs/057-improve-verification-contracts/follow-ups.md`, and documentary review claims; finding F2 per Constitution I, FR-006, FR-008, SC-003 (contradicts).
- [X] T039 CRITICAL — Synchronize current adoption status, authority versions, and verification-section references across `docs/brd.md`, `docs/trd.md`, `.agents/rules/specify-rules.md`, `.specify/memory/constitution.md` sync-impact report, `docs/backlog/constitution-testability-improvements.md`, and `specs/057-improve-verification-contracts/documentation-review.md`: current TRD validation/integration/accessibility/rendering procedures are sections 9.3/9.4/9.5/9.6, not the earlier draft numbering; remove present-tense claims that the live constitution is 4.0.0 or merely proposed when reporting adopted 4.1.0, retain explicitly historical baseline checkpoints, fix obsolete register-absence claims/current review status, and reconcile follow-up status values with the declared documentary/runtime taxonomies; finding F3 per Constitution VII, FR-019, FR-020, T036 (contradicts).
- [X] T040 Reconcile the exact-package manifest and engineering disposition in `specs/057-improve-verification-contracts/constitution-amendment.md` and `specs/057-improve-verification-contracts/documentation-review.md`: current backlog and review-record SHA-256 values do not match two recorded manifest entries; establish which bytes the actual approval covered, cite a traceable reviewer/date/approval source for that package, distinguish approved snapshots from subsequent documentary edits, and obtain any required renewed disposition for material corrections before claiming approval of the corrected package; do not silently overwrite historical hashes, invent approval, or repeat unqualified `SHA-256 verified`/all-satisfied claims; finding F4 per FR-019, SC-001, T033–T036 and governance traceability (partial).
- [X] T041 Complete `docs/trd.md` section 9.4 lifecycle matrices with explicit relying-party/caller cryptographic verification of registration/assertion responses, including expected verification inputs and pass/fail outcomes rather than response generation or JSON-shape checks alone; clarify that persisted/reconstructed values are permitted encrypted recovery/credential metadata with required identity/algorithm/profile/public-key/counter data, never persisted derived private keys or per-credential seeds, and reconcile ambiguous credential-secret-blob language without changing application behavior; update FR-009/010 and SC-004 documentary findings in `specs/057-improve-verification-contracts/documentation-review.md`; finding F5 per FR-009, FR-010, US3/AC1, Constitution II constraints (partial).
- [X] T042 Reconcile `docs/trd.md` section 9.3 and `.agents/rules/specify-rules.md` validation guidance: replace the unconditional `NO session state mutation` prohibition with operation-specific forbidden business/security side effects while explicitly allowing required safe-failure state transitions and owned-buffer cleanup; ensure parameterless/stateful operation rules preserve applicable state/authorization validation; replace generic URI-syntax assumptions for RP identifiers with the applicable field-specific documented contract and authoritative reference where needed; re-review all rejection assertions and SC-002 claims in `specs/057-improve-verification-contracts/documentation-review.md` without adding or executing tests; finding F6 per FR-003–FR-005, SC-002, US2/AC1–AC2 (contradicts).
- [X] T043 Review `docs/trd.md` section 9.6's newly fixed minimum three warm-ups, minimum ten measurement repetitions, and compilation-mode restrictions against the accepted documentation-only scope; restore concrete measurement selections to the future reviewed contract rather than prescribing unrequested values, or document separately authorized justification while preserving existing BRD rendering limits; reconcile `specs/057-improve-verification-contracts/follow-ups.md` and documentary acceptance claims, then repeat document-only scope/link/traceability review after T037–T042 and report remaining approvals or gaps explicitly; finding F7 per FR-016, SC-006, plan: future measurement parameter selection (unrequested).

## Phase 10: Convergence

**Assessment date**: 2026-10-23. Scope: current documentary implementation, including all T001–T043 and the resulting Phase 9 corrections. No Git comparison, runtime execution, or source audit. All eight current Phase 9 manifest checksums match their files; the remaining findings concern disposition traceability and registration-verification completeness, not the already-corrected hash values or superseded policies.

- [X] T044 Complete the remaining F4 governance disposition in `specs/057-improve-verification-contracts/constitution-amendment.md` and `specs/057-improve-verification-contracts/documentation-review.md`: assess whether each post-adoption policy correction changes the approved package materially, record the assessment rationale, and attach the actual reviewer/date/disposition tied to the exact corrected manifest when renewed review is required; otherwise record the explicit basis for retaining the original approval without claiming its byte coverage includes later changes. If required approval is unavailable, report that corrected-package disposition as pending and do not claim T040 or all governance criteria fully satisfied. Preserve the original approval record and live amendment provenance; do not invent approval or silently overwrite snapshots. Finalize any further T045 policy edits before obtaining the final corrected-package disposition and refreshing its manifest; finding F4 per FR-019, SC-001, T040, plan: material edits after approval require renewed review (partial).
- [X] T045 Complete the remaining F5 registration-verification contract for JRN-PASS-REG in `docs/trd.md` section 9.4: challenge echo and public-key-format checks alone must not be labeled cryptographic attestation verification. Specify expected registration response and attestation format/policy, the applicable signature verification inputs/key and explicit success/tamper-failure outcomes, or an explicit format-specific rationale when cryptographic attestation is not applicable; preserve the independent assertion-verification requirements and metadata-only persistence rules. Reconcile `specs/057-improve-verification-contracts/documentation-review.md` T041/FR-009/FR-010/SC-004 claims, then apply document-only link/consistency checks and finalize the exact corrected-package manifest/disposition under T044 after these edits; no new implementation, test, or device work belongs in this task; finding F5 per FR-009, FR-010, US3/AC1, T041 (partial).

**Dependency note**: T044 may begin its materiality assessment immediately, but its final package disposition must follow T045's completed policy edits and final-byte validation. Neither task authorizes runtime work or fabricated review evidence. Existing task rows and prior convergence phases remain unchanged.

## Phase 11: Convergence

**Assessment date**: 2026-10-23. Scope: current documentation, including the results of T001–T045. Registration attestation policy and all eight current manifest checksums satisfy the prior corrections. One HIGH partial finding remains: the corrected-package approval claim lacks the identity and affirmative-decision provenance required by T044. No new policy, runtime implementation, or test execution is requested.

- [X] T046 Complete the remaining F4 evidence in `specs/057-improve-verification-contracts/constitution-amendment.md` section 6.3 and `specs/057-improve-verification-contracts/documentation-review.md`: the corrected package is explicitly classified as material and requiring re-review, but its `Approved (Convergence Package)` disposition has no identified engineering approver or traceable affirmative decision for the exact section 5.2 package. If actual renewed approval exists, record the approving actor/engineering role, decision date, and a traceable approval-source reference or exact authorized decision tied to that package; the historical baseline `Proceed with T035` approval does not establish approval of subsequent material edits, and an implementation author's documentary checks alone must not be represented as engineering sign-off. If that evidence is unavailable, set only the corrected-package disposition to `Pending approval`, remove or qualify unsupported all-governance-satisfied and renewed-approval claims in both records, retain the valid baseline adoption provenance and live constitution, and list the pending decision explicitly. A truthful pending disposition is an acceptable completion of this corrective task; obtaining approval is not a prerequisite to preparing or reporting the documentary package. Recalculate the final current manifest after review-record edits, preserve historical snapshots, and perform document-only consistency/link checks without changing policy, code, or tests; finding F4 per FR-019, SC-001, T044, plan: exact engineering reviewer/date/package disposition (partial).
