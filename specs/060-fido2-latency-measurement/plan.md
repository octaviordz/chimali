# Implementation Plan: Simulated FIDO2 Ceremony Latency

**Branch**: `060-fido2-latency-measurement` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)

**Status**: Implemented host-simulated assessment runner. The declared run measures all four inventoried operation types and passes the strict per-operation target; physical measurement was not performed.

## Summary

Measure a synthetic registration and authentication journey from a fake relying party issuing a request through its independent verification of the response. Exercise the real Chimali processing path to the extent possible on a host, substituting transport/platform boundaries and user verification as needed. Disclose exactly what is real and simulated. Report the raw simulated round trip and a separately instrumented internal processing interval; this is the project latency acceptance assessment; physical-device timing is diagnostic only. Use a real monotonic clock for every reported elapsed duration.

## Technical Context

**Language/Version**: Kotlin 2.3.21/JVM 17, existing Android/KMP test infrastructure and Gradle; use existing local harness or a minimal in-process synthetic relying party if feasible.

**Dependencies**: Existing FIDO2 handlers, codecs, cryptographic and persistence components where host-compatible, existing coroutine/JUnit/MockK infrastructure where needed. Substitute user-verification and platform/transport dependencies explicitly; no production dependency upgrade or unneeded module.

**Storage**: Local structured synthetic results and linked verification records; no credential secrets, real identity, biometric data or real account state.

**Testing**: Behavior checks for verified registration/authentication and denied/cancelled/invalid paths; real monotonic measurements for reported elapsed durations. Follow applicable existing local CI, traceability and coverage rules. The workload must document any components it cannot exercise on the host rather than replacing them invisibly.

**Target Platform**: Supported developer/CI host; no physical phone, browser or Bluetooth hardware required. Identify runtime and host when reporting. Physical-device latency is diagnostic only under the amended requirement.

**Performance Goals**: Define the simulated round-trip baseline and explicit measurement conditions; assess every implemented internal FIDO operation against the retained strict <200 ms target. Report the full simulated round-trip as diagnostic context without an additional threshold.

**Constraints**: Synthetic data only; real monotonic timing for all elapsed durations; no optimization or behavior change beyond minimal necessary measurement seams; mark physical T084 superseded, keep T085 profiling/optimization deferred, and preserve historical results.

**Scale/Scope**: Registration and authentication, success and representative unsuccessful verification/response paths. Choose warmup and repetition counts and declare the summary statistic before collection; report all attempts and environment. Each internal operation is assessed against the existing strict <200 ms target. Any additional comparison threshold must be declared separately and does not change that target.

## Constitution Check

| Gate | Design disposition |
| --- | --- |
| I / II: security | Synthetic fixtures, no sensitive or identifying evidence; no altered key ownership or cryptographic behavior. |
| III / IV: architecture and history | Reuse existing boundaries and test infrastructure; no product persistence changes or general-purpose framework. |
| V: traceability and evidence | Stable requirement references, meaningful deterministic assertions, existing coverage gates, and TRD ten-field evidence. No unexecuted result is a pass. |
| VI: latency budgets | Owner-approved 2026-10-29 amendment retires the physical <1,000 ms acceptance target; the simulated assessment gates each internal operation at <200 ms. T085 optimization remains deferred. |
| VII / VIII | Synchronize feature docs, preserve real authenticator behavior, and make no additional platform or accessibility claim. |

The completed host assessment reports 40/40 measured occurrences across MakeCredential, GetAssertion, GetInfo and ResetAuthenticator below 200 ms. GetInfo and ResetAuthenticator use operation probes through their production provider/encoder and handler/use-case paths respectively; the probes disclose their synthetic request/verification boundary. The owner-approved constitutional amendment is recorded in Constitution 6.0.0 and synchronized authorities.

## Project Structure

```text
specs/060-fido2-latency-measurement/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/measurement-contract.md
├── quickstart.md
└── checklists/
feature/fido2/src/androidHostTest/         # existing relevant host tests and prospective simulated workflow
feature/fido2/src/androidMain/             # existing handlers and minimal instrumentation seams if needed
tools/ctap2-rp-harness/                    # existing synthetic RP; reuse only where compatible
```

**Structure Decision**: Determine the smallest host-capable integration boundary by inspecting existing registration, assertion, transport and verification seams. Use a fake RP that independently verifies the returned synthetic credential response, and a fake user-verification provider. Reuse actual code and real cryptographic verification wherever supported. Explicitly identify any substituted platform/transport and RP work included in the round trip.

## Phase 0 — Research Decisions

See [research.md](research.md) for metric boundaries, real monotonic timing, scope and benchmark considerations. Existing feature-055 device results remain historical context, not the target of this implementation.

## Phase 1 — Design and Implementation Sequence

1. Inventory host-compatible real registration and assertion paths and existing test seams; define the exact entry and response points, independent relying-party verification and unavoidable substitutions in the [contract](contracts/measurement-contract.md).
2. Define simulated user-verification outcomes without deliberately introduced delay; capture intrinsic duration for each invocation and record `not invoked` without a duration when an attempt ends before invocation. Specify separate spans for the full request-to-verified response, every internal operation, and test-double execution; define how any overlapping test-double interval is excluded from per-operation decisions.
3. Add only the minimum seam needed for host execution and timing, preserving production security, cancellation, and behavior. Use a real monotonic clock for ceremony and test-double execution durations.
4. Build verified registration and authentication paths with synthetic credentials and independent response verification. Cover negative, cancellation and invalid-response outcomes and exclude them from verified sample counts while retaining their results.
5. Predeclare the host runtime, warmup, repetitions, full sample policy, summary and any comparison threshold. Use real-clock measurements throughout; record run-to-run variability and avoid performance claims beyond the measured environment.
6. Record structured local evidence and ten TRD §9.1 fields; report unadjusted simulated round trip and separately bounded internal work. Retain failures and scope limitations. Synchronize documentation and reconcile T084 as superseded; keep SC-007 partial while any inventoried operation type lacks evidence; preserve T085 optimization deferral.

## Requirements and Acceptance Mapping

| Scope | Design and evidence |
| --- | --- |
| FR-001–005, SC-001–002 | Defined simulated RP and verification double, verified ceremonies, explicit timing and application boundaries. |
| FR-006–010, SC-003–004 | Real monotonic measurement, declared workload, complete synthetic evidence and privacy. |
| FR-011–013, SC-005 | Simulated internal-operation assessment, superseded T084 and preserved historical observations. |

## Complexity Tracking

No constitutional exceptions identified. A narrowly scoped test seam is justified only where existing boundaries cannot provide the required simulated workflow.









