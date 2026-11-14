# Backlog: Input Validation and Negative-Test Coverage

**Status**: Open — partial implementation; full contract unverified

**Created**: 2026-11-13 (split from feature 057 follow-ups)

**Tracking ID**: `FUP-SEC-002`

**Origin**: [Feature 057 follow-up index](../../specs/057-improve-verification-contracts/follow-ups.md) and [original combined backlog](../../specs/057-improve-verification-contracts/testability-backlog-origin.md). This file owns active scope, status and completion evidence for this work area.

## Context carried forward

- **Governing Area**: Input Validation & Error Handling (Constitution Principle III & V, Rules §1 / §5.2, TRD §9.3)
- **Mapped Feature Requirements**: FR-003, FR-004, FR-005
- **Remaining Scope**: Writing and executing unit/integration negative test suites covering distinct invalid input classes, boundary values, parameterless authorization checks, and forbidden side effects.
- **Reason Excluded**: Feature 057 defines documentation contracts and rules; test implementation and execution are excluded. "Runtime compliance remains a separate assessment with its own dated evidence."
- **Current Verification Status**: `Unverified` for the complete contract. Feature 058 implemented and executed selected negative and cleanup cases, including COV-021 and COV-022; see [test-selection.md](../../specs/058-enforce-coverage-targets/test-selection.md) and [implementation-evidence.md](../../specs/058-enforce-coverage-targets/implementation-evidence.md). A complete constraint-to-test mapping and execution evidence for all applicable boundaries remain outstanding.
- **Lifecycle Status**: `Assigned`
- **Preserved Authorization / Deferral Reference**: Constitution Principle V testing gates; rules §5.2.
- **Responsible Role**: Core Domain / Security Engineer
- **Needed Completion Evidence**: Executed test suite reports in `commonTest` asserting typed `Outcome.Failure` or protocol mapping on invalid inputs, zeroing of working buffers, and no uncaught exceptions escaping component boundaries.

## Next steps

Map external entry points, prerequisites and constraints to current tests. Reuse feature 058 COV-021/COV-022 evidence, then fill the remaining security and data-integrity gaps with exact failure, forbidden-effect and cleanup assertions. Prefer common tests where APIs permit and platform tests where required.

## Evidence and completion

Maintain a constraint-to-test mapping and named execution results in the implementing feature's evidence record, linked from the rules/TRD. Include expected failures, side-effect assertions, and applicable cleanup assertions.

Reconciled error policy; documented entry boundaries and constraints; executed invalid-class and boundary tests; no ordinary invalid-input exception escaping the assessed component boundary.

Completion accounts for the declared boundary/constraint inventory, applicable invalid classes, prerequisite state/authorization, typed failures, forbidden effects and cleanup assertions. Record exclusions explicitly.
