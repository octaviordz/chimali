# Simulated Ceremony Measurement Contract

**Status**: Implemented host runner; declared assessment executed 2026-10-29 and is **PASS** for the four inventoried internal operation types. The GetInfo and ResetAuthenticator samples are separately labeled operation probes; they do not represent WebAuthn registration/authentication ceremonies or physical transport.

## Initial host inventory and declared workload

The current production CTAP path routes `MakeCredential` and `GetAssertion` through `Ctap2MakeCredentialHandler` and `Ctap2GetAssertionHandler`. Their real app-side work includes the existing use cases and their configured collaborators; current tests in `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/integration/` provide handler/presenter coverage but mock outer dependencies in several paths. `LatencyProfiler` and `BaselineMeasurements` currently report device-side local response handoff and operation timing; they do not provide a host fake-RP ceremony runner or independent RP verification. The existing `tools/ctap2-rp-harness` is browser/manual authenticator oriented and requires a browser and authenticator, so it is not the host-only runner for this feature.

The host workload uses production registration/assertion use cases with an in-process independent RP verifier, synthetic credential state, a simulated transport boundary and immediate user-verification double. A separate operation-probe action exercises the production `CtapHidCommandDispatcher.handleGetInfo` path (including capability provider and response encoding) with HID transport callbacks substituted, plus the production ResetAuthenticator handler/use case against isolated mocked storage. These probes retain their own action identity, monotonic operation span and response check; they are not counted as registration/authentication ceremony samples. The complete inventory is `MakeCredential`, `GetAssertion`, `GetInfo`, and `ResetAuthenticator`. Any production dependency substituted or omitted is listed in the report.

Implementation status: host-only records, monotonic interval assessment, immediate UV test double, operation report model, ceremony trace recorder and independent packed P-256 registration/assertion verifier are implemented. The declared profile ran two warmups and ten measured repetitions for each ceremony and each operation probe. All 40 measured operation occurrences passed <200 ms; the project disposition is PASS for the declared host workload. Registration/authentication CTAP handler dispatch, HID/Bluetooth transport, production persistence and device experience remain outside the workload. GetInfo dispatch is exercised directly with transport callbacks substituted. See [implementation evidence](../implementation-evidence.md) and the retained [machine-readable report](../artifacts/simulated-latency-assessment.json).

For the initial runnable profile, use two warmups and ten measured repetitions per ceremony, declared before capture. Retain every attempt; warmups are labeled and excluded from the acceptance decision, while every measured verified occurrence is evaluated individually. Report per-type min/median/max as descriptive summary only. The fixed acceptance limit is <200 ms; no additional comparison threshold is introduced. Record the actual host OS, JVM/runtime, source revision, `System.nanoTime` clock domain, operation/exclusion endpoints and captured precision with the evidence. An attempt spanning host/process suspension or an observed clock discontinuity is incomplete; the runner must not start measured attempts while the host is suspended. `System.nanoTime` values are elapsed-time readings, not wall-clock timestamps.

## Workload and boundaries

For registration and authentication, a synthetic relying party issues a request to the declared Chimali workflow, receives the response and independently verifies it. The primary raw interval starts at request issuance and ends on completion of response verification. Record all actual production components traversed and every fake, including RP, transport, platform, storage or crypto substitutions. Report fake-RP verification and simulated transport as included overhead; do not call the whole interval pure internal processing.

A user-verification double produces distinguishable success, denial, cancellation, timeout or error outcomes without a physical biometric or visible user prompt. This simulated workflow has no visible prompt or biometric-success frame; neither is implied by the verification result. It introduces no deliberate delay. Record whether it was invoked for each attempt and, for each invocation, record its intrinsic execution duration from invocation to completion using a real monotonic clock. If an attempt ends before invocation, record `not invoked` and leave the execution duration absent, rather than recording zero. For every internal operation, retain the raw duration and endpoints; where test-double execution overlaps that operation, report the test-double interval separately and exclude only its measured overlap from the operation decision. State the interval definitions and calculation. The app-internal operation duration is not interchangeable with the simulated relying-party round trip.

## Operation association and timing validity

Associate every internal FIDO operation with the initiating simulated ceremony using an explicit run/session and action identity carried by the test workflow. Give each operation a unique identity within its process session and retain its type, start, completion, and terminal outcome. Include every operation generated by the action, including discovery/probe work and failed or cancelled operations. The fake relying party's verified response must refer to the same action. Duplicate, missing, unmatched, or ambiguous operation records make that ceremony's per-operation assessment incomplete; do not infer association from close timestamps or matching operation names alone. If a host workflow cannot exercise an operation type, report it as unmeasured rather than passing it.

Use a real monotonic elapsed-time source for all timing, including the complete ceremony and each verification test-double invocation. Record the test-double start and completion timestamps and derive its intrinsic execution duration; do not inject deliberate delay. Measurement validity requires an actual successful relying-party verification, ordered and complete boundaries in a compatible clock domain, a declared workload and no missing decision-relevant events. Reject invalid or negative intervals; do not clamp to success. Clearly identify failed, denied, cancelled, timed-out and unverified attempts and retain their records.

Use real monotonic measurements for every result. Predeclare warmup, number of repetitions, summary, outlier policy, host/runtime and any comparison threshold before collection. Preserve all attempts and report any uncertainty and host-specific limitations; do not infer population-wide latency.

## Assessment calculation and disposition

Inventory all implemented app-internal FIDO operation types, including types that the host workflow cannot exercise. A complete project assessment requires eligible measurements for every implemented type. Report an unexercised or unmeasured type explicitly and mark the project assessment incomplete.

For each eligible measured occurrence, calculate assessed duration as its raw elapsed duration minus the measured overlap of that operation with verification-test-double execution and Bluetooth response handoff. Define and capture the endpoints and clock domain for each exclusion; subtract the union of overlapping exclusions so no time is counted twice. Retain raw endpoints, raw duration, exclusion intervals and the calculation. Missing, invalid, out-of-order, incompatible or ambiguous decision-relevant timing evidence makes the operation assessment incomplete; do not infer or clamp an exclusion.

Compare the unrounded assessed duration against 200 ms in a consistent unit at the clock's recorded precision: a duration strictly below 200 ms passes; a duration equal to or above 200 ms fails. Display rounding must not change the decision. Assess every eligible measured occurrence; a summary or average cannot conceal a failing occurrence. Retain and separately label warmups under the workload policy declared before collection; the policy must identify which occurrences are eligible for the assessment.

Report observed operation failures and incomplete evidence separately, including when both occur. A complete passing project assessment requires eligible measurements for every implemented operation type and every eligible measured occurrence to pass. A failing occurrence is reported as a failure; missing evidence or unmeasured types make coverage incomplete and cannot be represented as a complete pass. An unsuccessful or unverified ceremony cannot be used as a verified latency sample.

Record an interrupted interval or a detected monotonic-clock discontinuity as incomplete; do not derive an unsupported duration. Declare how the selected clock handles host suspend/resume and mark an interval spanning suspend incomplete if its validity cannot be established.

## Evidence and target scope

Retain synthetic results with source revision, environment, named procedure, workload, verification-double behavior and intrinsic execution duration, raw timings, real/simulated components, outcome and limitations; link a TRD §9.1 ten-field verification record. Do not store real account identifiers, biometric information, credentials or secret key material.

The host-simulated internal-operation assessment is the project latency acceptance method and applies the existing strict <200ms target per operation. The former physical ASUS/Windows RP-to-verified-response <1,000ms target and T084 device campaign are superseded by the owner-authorized 2026-10-29 governance amendment. T085 profiling attribution and optimization remain deferred. Historical feature-055 evidence is preserved as diagnostic context only.






