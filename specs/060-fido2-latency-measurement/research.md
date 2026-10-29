# Research: Simulated FIDO2 Ceremony Latency

## Measurement boundary

**Decision**: The principal feature measurement starts when a fake relying party issues a synthetic request and ends after it receives and independently verifies the response. Retain the raw duration and report exactly which real and simulated components it covers. Measure app-internal processing separately where supported.

**Rationale**: The user wants repeatable insight into Chimali's work without waiting for external systems or a person. The simulated round trip also includes any fake relying-party and simulated transport work, so it cannot automatically be described as pure app processing.

**Alternatives considered**: A physical browser-to-RP campaign has different scope and requires hardware and human interaction; treating simulated round trip and internal operation timing as identical conceals overhead.

## User-verification double

**Decision**: Supply explicit success, denial, cancellation, timeout and error outcomes at the user-verification boundary without a real biometric prompt; introduce no deliberate delay, record intrinsic test-double execution duration for each invocation, and distinguish that interval from internal work. Record `not invoked` with no duration when an attempt ends before invocation.

**Rationale**: Repeatable interaction removes real human delay while still exercising application control flow. The double is not evidence about platform biometric behavior.

**Alternatives considered**: Physical verification introduces variable external waiting; bypassing the application's verification decision entirely can leave the desired workflow unexercised and must be disclosed if unavoidable.

## Clock and benchmark scope

**Decision**: Use a real monotonic clock for every elapsed-time measurement, including the ceremony and intrinsic test-double execution. Predeclare the workload, warmup, repetitions and summary. Preserve failures and outliers and identify environmental variability.

**Rationale**: A real monotonic clock measures elapsed time while the declared host workflow runs. Host scheduling, runtime warmup, fake-RP verification, and simulated transport all affect the result. No defensible host threshold follows automatically from the current physical target.

**Alternatives considered**: Timing only mocked operations would omit relevant execution; unexplained broad pass thresholds are unreliable.

## Existing code and amended obligation

**Decision**: Inspect existing FIDO2 host tests, handlers and lifecycle seams to find the smallest path using real production logic; use synthetic independent RP verification and minimal doubles. Record all unavailable components. Preserve historical ASUS evidence; mark T084 superseded and retain the separate T085 profiling/optimization deferral.

**Rationale**: Existing host tests include real handler fixtures and mocked transport/user-interaction collaborators, but their presence does not demonstrate the new end-to-end measurement. The existing `BaselineMeasurements` internal metric and the physical RP-to-verified-response metric have distinct boundaries. The owner-authorized 2026-10-29 amendment retires the physical target and makes the simulated internal-operation assessment authoritative. The strict <200ms internal target remains unchanged; the simulation does not establish physical device timing.

**Alternatives considered**: The physical target was retired through explicit owner authorization and synchronized governance amendment; physical timings remain diagnostic and must not be presented as compliance.





