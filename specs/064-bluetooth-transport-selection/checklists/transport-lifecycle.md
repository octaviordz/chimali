# Transport Lifecycle Requirements Checklist: Bluetooth Transport Selection

**Purpose**: Review the clarity, completeness, consistency and measurability of transport lifecycle and failure-recovery requirements.
**Created**: 2026-11-02
**Feature**: [Bluetooth Transport Selection](../spec.md)

**Review Ownership**: This is a reviewer-owned requirements-quality artifact. Mark an item `[x]` only when a reviewer determines that the requirements-quality criterion is satisfied. A checked item does not mean implementation work is complete.

## Requirement Completeness

- [x] CHK001 Are the choices, default and treatment of installations without a saved preference consistently specified? [Completeness, Spec §FR-001–003]
- [x] CHK002 Are the durable preference, process choice and transport availability defined as distinct concepts with clear ownership? [Completeness, Data Model §Process Transport]
- [x] CHK003 Are requirements for the boundary between selection and the separate FIDO BLE implementation documented, including required lifecycle and security responsibilities? [Dependency, Contract §Repository and Runtime Boundaries]
- [x] CHK004 Are the conditions for accepting or withholding a saved selection fully specified, including persistence failure and concurrent selections? [Completeness, Data Model §Transport Preference]

## Requirement Clarity

- [x] CHK005 Is “app restart” defined precisely enough to distinguish full process termination from activity recreation, service restart and reconnect? [Clarity, Spec §FR-005, Contract §User Interface]
- [x] CHK006 Are “active,” “selected,” “pending” and “unavailable” defined unambiguously for both the user and the process lifecycle? [Clarity, Spec §FR-006–007, Data Model §Derived Settings State]
- [x] CHK007 Are startup readiness, the beginning and end of the stated deadline, and the conditions for initialization failure specified precisely? [Clarity, Plan §Phase 1, Gap]
- [x] CHK008 Are the meanings and permissible values of the selection timestamp and its absence clearly defined? [Clarity, Data Model §Transport Preference]

## Requirement Consistency

- [x] CHK009 Do the specification, data model and interface contract agree about default selection when no saved choice exists and when a selected transport is unavailable? [Consistency, Spec §FR-003, FR-011, Data Model §Transitions]
- [x] CHK010 Do requirements for process lifetime, reconnect and service recreation consistently preserve a pending preference until the next process? [Consistency, Spec §FR-010–012, Contract §Repository and Runtime Boundaries]
- [x] CHK011 Are the Android capability assumption and the plan's device-dependent capability requirements reconciled? [Conflict, Spec §Assumptions, Plan §Technical Context]
- [x] CHK012 Are Windows-only feature acceptance and the existing Classic cross-platform obligations distinguished consistently? [Consistency, Spec §Assumptions, Plan §Constitution Check]

## Acceptance Criteria Quality

- [x] CHK013 Are the scope, expected results and evidence requirements sufficiently precise for registration and authentication in each transport mode? [Measurability, Spec §SC-001, SC-003]
- [x] CHK014 Is the “zero cases” criterion for uninterrupted ceremonies bounded by a defined set of conditions and observable outcomes? [Ambiguity, Spec §SC-005]
- [x] CHK015 Are “no data loss,” “session corruption” and “without ambiguity” defined with observable acceptance criteria? [Ambiguity, Spec §SC-002, SC-006]
- [x] CHK016 Are the criteria for an unavailable transport and successful recovery distinguishable from criteria for an active transport? [Measurability, Spec §FR-011, Data Model §Process Transport]

## Scenario Coverage

- [x] CHK017 Are requirements complete for both switch directions, persistence across lifecycle events and cancellation of a pending choice? [Coverage, Spec §User Stories 2–4, Data Model §Transitions]
- [x] CHK018 Are the responsibilities and ordering for a preference change during an in-flight ceremony explicitly defined? [Coverage, Spec §FR-012, Contract §Repository and Runtime Boundaries]
- [x] CHK019 Are requirements defined for simultaneous startup requests, a settings change during startup and repeated save attempts? [Coverage, Plan §Phase 1, Gap]
- [x] CHK020 Are requirements for the boundary between an initialized transport and an established host connection clear? [Clarity, Data Model §Process Transport, Gap]

## Edge Cases and Recovery

- [x] CHK021 Are distinct requirements specified for unsupported hardware, denied permission, disabled Bluetooth and other startup failures, including actionable recovery? [Coverage, Spec §FR-011, Research §Availability]
- [x] CHK022 Are requirements for missing, unknown, unreadable and corrupted preferences distinguished, including preservation of unrelated stored data? [Coverage, Data Model §TransportMode, §Derived Settings State]
- [x] CHK023 Are timeout, cancellation, resource cleanup and late completion addressed consistently, including the allowed retry behavior? [Coverage, Plan §Phase 1, Data Model §Transitions]
- [x] CHK024 Are recovery requirements explicit when preference storage remains unreadable and the alternative transport cannot be saved? [Gap, Spec §FR-011, Data Model §Derived Settings State]
- [x] CHK025 Are requirements defined for failure of the alternative transport after the user chooses it, without introducing automatic fallback? [Coverage, Spec §FR-011, Gap]

## Non-Functional Requirements

- [x] CHK026 Are transport-selection requirements consistent with authorization, credential confidentiality, cancellation and cleanup obligations for a ceremony? [Consistency, Spec §FR-012, Plan §Constitution Check]
- [x] CHK027 Are startup deadline and responsiveness requirements measurable and consistent with the existing application startup budgets? [Measurability, Plan §Technical Context, §Constitution Check]
- [x] CHK028 Are accessible names, selection status, pending state, error messaging and text scaling requirements specified for every relevant settings state? [Coverage, Contract §User Interface]

## Dependencies, Assumptions and Traceability

- [x] CHK029 Are the separate BLE prerequisite's required capabilities, supported protocol, delivery boundary and acceptance conditions documented without assuming availability? [Dependency, Spec §Assumptions, Research §Protocol and dependency]
- [x] CHK030 Is the claim of Windows compatibility distinguished from evidence for a specific host configuration and ceremony? [Assumption, Spec §Assumptions, Research §Protocol and dependency]
- [x] CHK031 Are stable canonical requirement identifiers and mappings defined for the selector's critical behavior and acceptance criteria? [Traceability, Plan §Constitution Check, Gap]
- [x] CHK032 Are the required evidence types and the distinction between simulated and physical transport outcomes explicit? [Completeness, Contract §Acceptance Observations, Plan §Constitution Check]

## Notes

- Reviewer assessment owns checkbox changes; leave unresolved items unchecked and record findings inline.
- `/speckit-implement` reads checklist state as a gate and must not modify markers.
- `checklists/requirements.md` has a separate built-in lifecycle maintained by `/speckit-specify` and `/speckit-clarify`.
- References identify the requirements to review; a reference does not assert that the item passes.
