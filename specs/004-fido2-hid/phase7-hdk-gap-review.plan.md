---
name: phase7-hdk-gap-review
overview: Review Phase 7 HDK migration completeness against draft-dijkhuis-cfrg-hdkeys-06 and extend the task list with missing conformance, security, and regression tasks before implementation continues.
todos:
  - id: review-phase7-open-items
    content: Confirm current implementation status of T173-T178 against code/tests and identify concrete missing coverage.
    status: pending
  - id: add-uint32-conformance-task
    content: Add a Phase 7 task covering uint32 index domain handling decision and associated tests/docs.
    status: pending
  - id: add-fixed-kat-tasks
    content: Add tasks for fixed external vectors (non-self-referential) at HDK primitive and FIDO2 E2E levels.
    status: pending
  - id: add-negative-security-tests
    content: Add tasks for malformed key handle/public key and remote-derivation failure-path coverage.
    status: pending
  - id: add-conformance-register-task
    content: Add a draft section conformance checklist task with code references and explicit N/A rationale.
    status: pending
isProject: false
---

# Phase 7 HDK Migration Review and Task Augmentation

## Findings From Current Code and Task List

- Phase 7 is directionally sound and already closed the critical `DeriveSalt` bug (`H(salt || ctx)`), with corresponding updates in `HdkEcdhP256` and task tracking in [D:/octav/source/repos/Chimali/specs/004-fido2-hid/tasks.md](D:/octav/source/repos/Chimali/specs/004-fido2-hid/tasks.md).
- The largest remaining gaps are still-open tasks `T173`-`T178` (E2E KAT coverage, blinding consistency KAT, boundary coverage, KDoc/spec mapping, alias decision, non-persistence guard).
- There is a concrete spec-domain mismatch risk: public APIs still use signed `Int` indices (`path: List<Int>`, `index: Int`) in [D:/octav/source/repos/Chimali/core/security/src/main/kotlin/com/chimali/core/security/api/HdkManager.kt](D:/octav/source/repos/Chimali/core/security/src/main/kotlin/com/chimali/core/security/api/HdkManager.kt) and [D:/octav/source/repos/Chimali/core/security/src/main/kotlin/com/chimali/core/security/hdkeys/HdkEcdhP256.kt](D:/octav/source/repos/Chimali/core/security/src/main/kotlin/com/chimali/core/security/hdkeys/HdkEcdhP256.kt), while draft index domain is full 32-bit unsigned.
- Existing KATs are mostly implementation-coupled (computed by local reference logic) rather than fixed interoperability vectors.

## Proposed Additions to `tasks.md` (Phase 7)

- Add a task to resolve uint32 index conformance explicitly:
  - either migrate to `UInt`/`Long` API + strict `I2OSP(4)` bounds checks,
  - or document a deliberate profile restriction to 31-bit indices and update acceptance criteria/tests accordingly.
- Add fixed-vector KAT tasks (hex-anchored expected outputs) for:
  - `CreateContext`, `DeriveSalt`, `DeriveBlindKey`, `DeriveBlindingFactor`, and one full 2-level HDK path in `Fido2CryptoService`.
- Add negative/robustness tests for remote and decode paths:
  - malformed `keyHandle`, invalid public key encoding, wrong expected public key, wrong index/path length assumptions.
- Add storage-safety task for “blinded private key never persisted” with testable enforcement point (repository/storage boundary check) beyond comments/zero-fill.
- Add a conformance delta checklist task mapping draft MUST/SHOULD items used by this implementation to `covered/partial/not-applicable` status, with code refs.

## Review Feedback Scope

- Validate whether open `T173`-`T178` are sufficient for release confidence.
- Validate whether the prior plan at [C:/Users/octav/.gemini/antigravity/brain/41fadb85-6202-402c-b3bc-100df9f69846/implementation_plan.md.resolved](C:/Users/octav/.gemini/antigravity/brain/41fadb85-6202-402c-b3bc-100df9f69846/implementation_plan.md.resolved) needs updates to reflect already-closed tasks and the remaining conformance risks.

## Execution Sequence Once Approved

- Update [D:/octav/source/repos/Chimali/specs/004-fido2-hid/tasks.md](D:/octav/source/repos/Chimali/specs/004-fido2-hid/tasks.md) by appending new Phase 7 tasks (new IDs after `T178`).
- Keep existing task IDs/statuses unchanged.
- Add concise acceptance criteria text for each new task to avoid ambiguous completion.

