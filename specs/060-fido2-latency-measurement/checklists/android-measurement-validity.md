# Android Measurement Validity Checklist: Simulated FIDO2 Ceremony Latency

**Purpose**: Review the clarity and completeness of requirements for the simulated Android device assessment and its decisions.
**Created**: 2026-10-29
**Feature**: [Feature 060 specification](../spec.md)

**Review ownership**: This is a reviewer-owned requirements-quality checklist. Mark an item `[x]` only when the reviewer determines its criterion is satisfied. A checked item does not mean implementation is complete.

## Workload and measurement boundaries

- [x] CHK001 Are the device workload's registration, authentication and operation types specified consistently with the host inventory? [Completeness, Spec §FR-014, Spec §FR-015, Contract §Android device extension (planned; not yet executed)]
- [x] CHK002 Are the production components and substitutions required for the device workload identified, with differences from the host disclosed? [Completeness, Spec §FR-016, Plan §Android device extension — design and execution]
- [x] CHK003 Are the start and end of the simulated relying-party interval and the scope of work included in it unambiguous for the device run? [Clarity, Spec §FR-003, Spec §FR-014]
- [x] CHK004 Are the boundaries of each internal operation, the verification double and any excluded overlap specified well enough to distinguish their durations? [Clarity, Spec §FR-005, Spec §FR-015]
- [x] CHK005 Are the verification double's outcomes, no-delay requirement, intrinsic duration and not-invoked case clearly applicable to the device workload? [Consistency, Spec §FR-004, Spec §FR-014]
- [x] CHK006 Are independent response verification and the eligibility of successful and unsuccessful attempts specified for both device ceremonies? [Coverage, Spec §FR-002, Spec §FR-008, Spec §FR-014]
- [x] CHK007 Are the device warmup, repetition, outlier and summary policies declared before collection and consistent across the plan and contract? [Consistency, Spec §FR-008, Plan §Android device extension — design and execution, Contract §Android device extension (planned; not yet executed)]

## Timing validity and attribution

- [x] CHK008 Are the primary elapsed-time source, boundaries and retained raw values defined for every reported device interval? [Completeness, Spec §FR-006, Spec §FR-015]
- [x] CHK009 Are the secondary clock's purpose, difference direction, tolerance and consequences of unavailable or divergent readings specified for Android? [Clarity, Contract §Android device extension (planned; not yet executed)]
- [x] CHK010 Are the limits of suspend detection and the treatment of device scheduling and measurement overhead clearly disclosed? [Clarity, Plan §Android device extension — design and execution, Contract §Android device extension (planned; not yet executed)]
- [x] CHK011 Are missing, reversed, incompatible or ambiguous timing and exclusion intervals explicitly classified as incomplete? [Coverage, Spec §FR-006, Spec §FR-015, Contract §Assessment calculation and disposition]
- [x] CHK012 Are the rules for excluding overlapping verification and handoff intervals defined without double subtraction? [Clarity, Spec §FR-005, Contract §Assessment calculation and disposition]
- [x] CHK013 Are run, attempt, action and operation identities and their association requirements specified for device results? [Completeness, Contract §Operation association and timing validity, Data model §Android device run and collection (planned)]
- [x] CHK014 Are mismatched operation records, duplicate identities and unassociated assessments explicitly prevented from supporting a passing decision? [Coverage, Spec §FR-007, Contract §Operation association and timing validity]

## Decisions and acceptance

- [x] CHK015 Is the strict <200 ms comparison specified at unrounded precision for every eligible device occurrence, including equality at the threshold? [Measurability, Spec §FR-015, Spec §SC-007, Contract §Assessment calculation and disposition]
- [x] CHK016 Are the conditions for device PASS, FAIL and INCOMPLETE defined, including simultaneous failed operations and missing evidence? [Completeness, Spec §FR-015, Spec §FR-018, Data model §Android device run and collection (planned)]
- [x] CHK017 Is the difference between a diagnostic device performance FAIL and a runner or evidence defect clear enough to avoid blocking completion solely for an over-threshold result? [Clarity, Spec §FR-018, Contract §Android device extension (planned; not yet executed)]
- [x] CHK018 Are device assessment outcomes and the governing host acceptance decision kept independent across the specification, plan and contract? [Consistency, Spec §FR-017, Plan §Android device extension — design and execution, Contract §Android device extension (planned; not yet executed)]
- [x] CHK019 Are device sample counts and complete-coverage conditions measurable and consistent with the declared workload? [Acceptance Criteria, Spec §SC-006, Contract §Android device extension (planned; not yet executed)]

## Evidence, exceptions and dependencies

- [x] CHK020 Are the device environment, workload, source and package identity, substitutions, limitations and results required in a separate report? [Completeness, Spec §FR-016, Data model §Android device run and collection (planned)]
- [x] CHK021 Are source-to-package association and the consequences of missing or mismatched provenance specified clearly enough to establish which build was measured? [Clarity, Plan §Android device extension — design and execution, Contract §Android device extension (planned; not yet executed)]
- [x] CHK022 Are device selection, unavailable or multiple devices, and concurrent measurement requirements sufficiently defined for reproducible collection? [Coverage, Spec §FR-016, Plan §Android device extension — design and execution, Gap]
- [x] CHK023 Are interruption, device disconnect, process failure and report extraction failure addressed, including partial evidence and an incomplete status? [Exception Flow, Spec §FR-010, Spec §FR-015, Contract §Android device extension (planned; not yet executed)]
- [x] CHK024 Are the requirements clear about whether and how a subsequent run relates to an interrupted run and its retained evidence? [Recovery, Gap, Data model §Android device run and collection (planned)]
- [x] CHK025 Are evidence privacy requirements explicit for synthetic credentials, secrets, biometric information, account identifiers and device identifiers? [Security, Spec §FR-010, Data model §Android device run and collection (planned)]
- [x] CHK026 Are the requirements clear that device samples do not establish browser, Bluetooth, biometric or full physical ceremony latency? [Consistency, Spec §FR-017, Spec §FR-013]
- [x] CHK027 Are any device-specific runtime conditions or substitutions that limit comparison with the host required to be reported before conclusions are drawn? [Assumption, Spec §FR-016, Plan §Android device extension — design and execution]

## Notes

- These items review the written requirements and design; they are not implementation tests.
- Leave items unchecked until the reviewer accepts each requirements-quality criterion; add findings inline as needed.
- `/speckit-implement` reads checklist state as a gate and does not modify reviewer markers.
- The built-in `requirements.md` checklist has a separate lifecycle.
