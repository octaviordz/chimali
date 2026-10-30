# Feature Specification: Simulated FIDO2 Ceremony Latency

**Feature Branch**: `060-fido2-latency-measurement`

**Created**: 2026-10-29

**Status**: Draft — expanded to include Android device measurement

**Input**: Measure the time required for Chimali's internal FIDO2 workflow using a fake relying party and a test double for user verification on a host and on an Android device, without human biometric interaction.

## Clarifications

### Session 2026-10-29

- Q: If an Android device operation takes 200 ms or longer, should that result block completion of the Android performance work? → A: No. Record the device assessment as a failure; it does not block completion of the Android measurement work.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Measure a simulated ceremony (Priority: P1)

As a developer, I want a repeatable simulated registration and authentication ceremony so I can measure the time from a fake relying-party request until it receives and verifies the response, with a test double supplying user verification.

**Why this priority**: This provides actionable measurements of the application workflow without waiting for a person; the host workload remains available without a physical device.

**Independent Test**: Run registration and authentication through the specified simulated path, with the verification double providing a successful response, and inspect the recorded request-to-verified-response duration and exercised scope.

**Acceptance Scenarios**:

1. **Given** a synthetic registration or authentication request, **When** the fake relying party sends it and verifies a valid response, **Then** the result includes the unadjusted elapsed duration, ceremony type, outcome, and the real and simulated boundaries exercised.
2. **Given** an unsuccessful, cancelled, invalid, or unverified ceremony, **When** the result is recorded, **Then** it is identified by outcome and cannot be reported as a verified latency sample.
3. **Given** a simulated verification response, **When** the workflow is measured, **Then** no physical biometric interaction is required and the test double's behavior is recorded.

### User Story 2 - Trust the timing (Priority: P2)

As a reviewer, I need the start and stop boundaries for each internal operation and the verification test double to be explicit, so I can interpret the measured runtime.

**Why this priority**: A number is actionable only if its boundaries and included work are clear.

**Independent Test**: Measure each declared internal FIDO operation, the verification test double, and the full simulated ceremony with a real monotonic clock; inspect their raw durations and boundaries.

**Acceptance Scenarios**:

1. **Given** a successful simulated ceremony, **When** it runs, **Then** the real monotonic measurements separately report the full request-to-verified-response duration, each internal FIDO operation, and the verification test double execution duration.
2. **Given** an unsuccessful or incomplete operation, **When** results are reported, **Then** its outcome and timing are retained and it is not counted as a passing operation.

### User Story 3 - Interpret results responsibly (Priority: P3)

As a project owner, I need the simulated internal-operation assessment to provide the project latency acceptance result and its physical-device limitations.

**Why this priority**: The project assessment targets internal processing; external host, transport, and physical biometric time are outside its acceptance boundary.

**Independent Test**: Review a report for both ceremony types and verify that it contains operation-level results, the <200ms decisions, simulated boundaries, and physical limitations.

**Acceptance Scenarios**:

1. **Given** a simulated measurement, **When** it is reported, **Then** it assesses the internal <200ms target per operation and does not claim a physical browser-to-device ceremony result.
2. **Given** missing evidence or an invalid response, **When** the workflow result is reviewed, **Then** the report retains the failure and does not represent it as a passing measurement.

### User Story 4 - Measure on an Android device (Priority: P1)

As a developer, I want to run the same simulated registration and authentication workload on an Android device so I can understand the application's internal operation latency in the device environment.

**Why this priority**: Device execution shows the effect of the Android runtime and device conditions on internal work while avoiding human verification waits.

**Independent Test**: Run the declared simulated workload on a connected Android device and inspect a device-specific report for both ceremonies, every inventoried operation, verification outcomes, timing and environment.

**Acceptance Scenarios**:

1. **Given** an available Android device, **When** the device workload runs, **Then** registration and authentication execute on the device, use a simulated relying party and immediate user-verification response, and independently verify successful responses.
2. **Given** a completed device run, **When** its report is reviewed, **Then** every eligible operation occurrence has its own timing and strict <200 ms decision, with the full simulated ceremony and verification-double durations reported separately.
3. **Given** a disconnected device, interrupted run, invalid timing or incomplete operation coverage, **When** the outcome is recorded, **Then** it is identified as failed or incomplete and cannot be presented as a passing device assessment.
4. **Given** host and device results, **When** they are reviewed, **Then** their environments, workload differences and outcomes are identifiable separately; the existing host acceptance result remains independently interpretable.

### Edge Cases

- Verification is denied, cancelled, times out, or returns an error; test-double execution time is distinguished from application processing time where reported.
- Registration or authentication returns an invalid or unverifiable response.
- Timing events are missing, out of order, overlapping, negative, or at a strict threshold.
- Multiple requests, probes, or concurrent operations occur; attribution must remain explicit.
- Repetitions include failures, warmup effects, or outliers; the report must not silently discard them.
- Test doubles replace more of the workflow than intended, or runtime conditions change between comparisons.
- The Android device disconnects, sleeps, interrupts execution, or cannot exercise a required operation; missing evidence is not a pass.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The feature MUST define a reproducible simulated registration and authentication workload, including the real application components exercised, all test doubles, synthetic data, environment, and measurement boundaries.
- **FR-002**: The simulated relying party MUST issue a synthetic request, receive the response, and independently verify the response before an attempt is classified as verified.
- **FR-003**: For each verified simulated ceremony, the measurement MUST start when the fake relying party issues its request and stop when it receives and verifies the response; the unadjusted elapsed value MUST be retained. The report MUST identify any simulated relying-party and transport work included in that interval.
- **FR-004**: A test double MUST provide user-verification outcomes without physical biometric interaction, with success, denial, cancellation, timeout and error distinguishable; it MUST introduce no deliberate delay. Each attempt MUST record whether the double was invoked and, for each invocation, its behavior and intrinsic execution duration. If the attempt ends before invocation, record `not invoked` with no execution duration; do not report an unmeasured duration as zero.
- **FR-005**: The feature MUST report the full simulated relying-party-to-verified-response interval separately from every internal FIDO operation and the verification test double. Each internal operation interval MUST exclude the test-double execution interval when it overlaps the operation, and retain its unadjusted duration and exclusion calculation.
- **FR-006**: Every performance measurement MUST use a real monotonic clock. The reported timing MUST identify start and stop boundaries and preserve raw elapsed durations for the full ceremony, every internal FIDO operation, and the verification test double.
- **FR-007**: The report MUST retain ceremony type, outcome, raw timing, verification-double behavior and execution duration, workload, run environment, source revision, repetitions, failures, outliers, and the exercised real and simulated boundaries. It MUST describe uncertainty and limitations and preserve enough evidence to reproduce the calculation.
- **FR-008**: The workload MUST declare warmup, repetition count, result summary, and comparison conditions before measurement. All attempts MUST be retained; invalid or unverified attempts MUST NOT count as verified operations. The workload MUST inventory all implemented app-internal FIDO operation types. Each type MUST have eligible measurements for a complete project assessment; types that cannot be exercised MUST be reported as unmeasured and the project assessment MUST remain incomplete.
- **FR-009**: The simulated workflow MUST assess every eligible measured occurrence of each implemented app-internal FIDO operation against the strict <200 ms per-operation target, excluding measured overlap with verification test-double execution and Bluetooth response handoff. A complete passing project assessment requires eligible measurements for every implemented operation type and every eligible measured occurrence to pass. Report failures and incomplete evidence separately. Report the full simulated round trip as diagnostic context without an additional threshold.
- **FR-010**: Evidence MUST use synthetic data and exclude credential secrets, real account identifiers and biometric data. Failed and interrupted runs MUST be visible and MUST NOT be represented as passing.
- **FR-011**: The simulated assessment in this feature supersedes physical-device T084 as the project latency acceptance assessment. Physical ASUS/Windows RP-to-verified-response timing is diagnostic only and is no longer a compliance criterion. T084 is superseded, not completed by physical-device evidence.
- **FR-012**: The strict <200 ms app-internal FIDO operation target is assessed in this feature. The former physical <1,000 ms target is retired. T085 profiling attribution and optimization remain separately deferred; this feature does not claim an optimization.
- **FR-013**: The feature MUST preserve the prior three registration results and user UX assessment as historical evidence without combining them with simulated samples or implying physical compliance.
- **FR-014**: The feature MUST support running the simulated registration and authentication workload on a connected Android device, with a synthetic relying party, synthetic data and an immediate user-verification double; successful responses MUST be independently verified.
- **FR-015**: The Android device workload MUST inventory and assess the same implemented internal FIDO operation types as the host workload, retain raw ceremony, operation and verification-double durations using a real monotonic clock, and apply the same strict <200 ms per-occurrence comparison and measured-exclusion rules. Unmeasured types, invalid intervals, failures and interrupted attempts MUST be disclosed and MUST NOT be reported as passing.
- **FR-016**: Each device run MUST retain a separate report identifying device model, operating-system version, workload, source revision, runtime conditions, real and simulated components, substitutions, timing boundaries, repetitions, outcomes and limitations. Host and device samples MUST remain distinguishable and MUST NOT be combined into a single assessment.
- **FR-017**: The existing host-simulated assessment remains the project latency acceptance method under the constitution. The Android simulated-device assessment is additional device-specific evidence; it MUST NOT be represented as a measurement of physical browser, Bluetooth or biometric interaction.
- **FR-018**: A device operation at or above 200 ms MUST be recorded as a failed device assessment. That performance failure alone MUST NOT block completion of the Android measurement work; the result remains visible as diagnostic evidence.

### Key Entities *(include if feature involves data)*

- **Workload definition**: Ceremony type, simulated and real components, verification-double behavior, environment, timing boundaries, repetitions and any benchmark goal.
- **Ceremony attempt**: Synthetic request and verified or unsuccessful outcome, recorded milestones, raw duration and limitations.
- **Measurement report**: The complete attempt set, declared summary and comparison rules, runtime and revision, internal-operation decisions, and T085 profiling/optimization status.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Both registration and authentication can be measured on the host from fake relying-party request through independently verified response without physical hardware or a human biometric action.
- **SC-002**: Every reported verified sample retains its raw duration, ceremony type, verification-double behavior and execution duration, and stated real and simulated boundaries; failed or unverified attempts are separately identified.
- **SC-003**: Every reported duration is measured with a real monotonic clock using declared boundaries; the test-double duration and full request-to-verified-response duration are reported separately.
- **SC-004**: Every measurement report identifies the workload, runtime environment, revision, repetition policy, failures, outliers, summary statistic and scope limitations so a reviewer can interpret and reproduce it.
- **SC-005**: The simulated assessment is the project latency acceptance method and assesses the internal-operation target; physical latency is diagnostic only, and historical device evidence remains available.
- **SC-006**: A connected Android device can execute both simulated ceremonies and produce a separate report with at least ten measured occurrences for each inventoried operation type, or identify the device assessment as incomplete when that evidence is unavailable.
- **SC-007**: Every eligible device operation occurrence has an unrounded strict <200 ms decision and recorded timing; failed, interrupted and incomplete device runs cannot be presented as passing.

## Assumptions

- A fake relying party and test verification response are sufficient for the intended simulated workflow; exact application entry and transport substitutions must be established during implementation design and disclosed in every report.
- The simulated request-to-verified-response duration includes any work done by the fake relying party and simulated transport; separately instrumented internal processing is needed to isolate Chimali's work.
- A host measurement is specific to the runtime and workload under which its real monotonic duration was collected.
- The owner-approved 2026-10-29 amendment replaces the physical end-to-end target with host-simulated internal-operation assessment. The strict <200ms per-operation target remains in force.
- The Android device run is an additional assessment using the same simulated boundaries and target, not a replacement for the constitution's host acceptance assessment. Device-specific substitutions and any differences from the host workload will be declared before measurement.
- The connected ASUS Android device may be used for the initial device run; device availability and execution outcomes must be recorded rather than assumed.






