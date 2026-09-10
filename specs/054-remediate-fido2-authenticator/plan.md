# Implementation Plan: Remediate FIDO2 Authenticator

**Branch**: `054-remediate-fido2-authenticator` | **Date**: 2026-09-10 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/054-remediate-fido2-authenticator/spec.md`

## Summary

Remove the obsolete `Fido2Authenticator` facade whose implementation advertises multiple nonfunctional operations.
The live Bluetooth HID path already uses the authoritative CTAP registration and assertion handlers; preserve those
paths and source authenticator information from a focused authoritative provider. Correct credential repository
queries and statistics so user-verification policy is based on persisted `credProtect` metadata instead of a
hard-coded empty result.

## Technical Context

**Language/Version**: Kotlin, Android/Kotlin Multiplatform module structure

**Primary Dependencies**: Koin annotations, Kotlin coroutines/Flow, SQLDelight, Bluetooth HID APIs

**Storage**: SQLite3MultipleCiphers-backed SQLDelight credential database; persisted `cred_protect_policy`

**Testing**: kotlin.test, JUnit 5, MockK, Android host and instrumentation tests

**Target Platform**: Android API 28+

**Project Type**: Mobile application

**Performance Goals**: Preserve current CTAP ceremony latency; do not add key derivation or database hydration to
the HID get-info path.

**Constraints**: Preserve existing CTAP2 make-credential/get-assertion behavior; use typed `Outcome` errors;
maintain key/secret zeroing; do not add a duplicate ceremony implementation or a new transport.

**Scale/Scope**: Small-to-medium: retire one stale facade, introduce a focused capability provider if needed,
repair two repository projections/statistics paths, and add targeted tests.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Security First | PASS | Existing ceremony and encrypted-storage paths are retained; policy evaluation reads persisted metadata only. |
| III. Architecture & Quality | PASS | Removes a duplicate facade and preserves handler/use-case ownership. |
| X. Kotlin & KMP | PASS | New/remediated domain paths use `Outcome`; no unscoped coroutine is introduced. |
| XII.1 Traceability / no dead code | PASS | Removes the reachable placeholder implementation and adds FR-linked tests. |
| XII.3 Verification | PASS | Tests cover facade retirement, capability information, and `credProtect` filtering/counting. |
| XII.4 Hardware isolation | PASS | Bluetooth HID remains isolated in the transport layer. |

**Post-design re-check**: All gates remain satisfied. The design removes nonfunctional public behavior rather than
creating a second ceremony path, and confines credential-policy evaluation to the repository layer.

## Project Structure

### Documentation (this feature)

```text
specs/054-remediate-fido2-authenticator/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/
│   └── authenticator-remediation.md
├── quickstart.md
└── tasks.md
```

### Source Code (repository root)

```text
feature/fido2/src/
├── androidMain/kotlin/com/chimali/fido2/
│   ├── data/repository/CredentialRepositoryImpl.kt       # modify policy query/statistics
│   ├── data/transport/BluetoothHidTransportImpl.kt       # replace stale get-info dependency
│   ├── domain/service/Fido2Authenticator.kt              # delete stale facade
│   ├── domain/service/impl/Fido2AuthenticatorImpl.kt     # delete stale facade
│   └── ctap2/                                            # source or add focused capability information provider
└── androidHostTest/kotlin/com/chimali/fido2/
    ├── data/repository/                                  # add credProtect query/statistics tests
    └── data/transport/ or ctap2/                         # add capability and no-stub-path tests
```

**Structure Decision**: Keep CTAP ceremonies in their existing handlers/use cases. Keep Bluetooth-specific response
framing in `BluetoothHidTransportImpl`. Limit repository changes to translating persisted credential policy into
accurate domain projections and statistics.

## Complexity Tracking

No constitutional violations require an exception. The design removes existing violations.
