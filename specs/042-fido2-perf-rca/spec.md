# Feature Specification: FIDO2 Performance RCA Fix

**Feature Branch**: `042-fido2-perf-rca`
**Created**: 2026-05-15
**Status**: Draft
**Input**: User description: "fix 'During a FIDO2 authentication ceremony (GetAssertion), the system incorrectly reports a performance budget violation for the [NFR-PERF-030] requirement' Use information from @[specs/042-fido2-perf-rca/RCA-NFR-PERF-030.md]. IMPORTANT Use '042-fido2-perf-rca' as spec's directory and as branch name."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Accurate Performance Telemetry for FIDO2 Authentication (Priority: P1)

As a system administrator or developer monitoring the FIDO2 implementation, I want the system to accurately track raw system processing time during authentication, excluding user interaction time, so that performance budget violations are only reported for true system latencies.

**Why this priority**: Accurate telemetry is critical to monitor NFR-PERF-030 compliance and ensure false-positive performance alerts do not mask real issues.

**Independent Test**: Can be fully tested by triggering a GetAssertion operation that includes user interaction and verifying that the `Total` and `User` times are correctly segregated in the performance logs, and no false-positive violation is raised when pure system latency is under 200ms.

**Acceptance Scenarios**:

1. **Given** a FIDO2 GetAssertion request requiring user interaction, **When** the user takes several seconds to interact with the UI, **Then** the performance logs should correctly reflect the accumulated user interaction time and NOT trigger an `NFR-PERF-030` budget violation if the system processing time is under 200ms.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST pause the performance profiler during user interactions in the `GetAssertion` process.
- **FR-002**: The system MUST subtract user interaction time from the total elapsed time when calculating the latency against the performance budget.
- **FR-003**: The system MUST accurately log both the Total elapsed time and the User interaction time for telemetry analysis.

### Key Entities

- **LatencyProfiler**: Responsible for tracking operations and segregating raw processing time from user interaction time.
- **GetAssertion**: The FIDO2 authentication ceremony operation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% elimination of false-positive `NFR-PERF-030` performance budget violations caused by user interaction delays during GetAssertion.
- **SC-002**: System telemetry accurately logs user interaction duration separate from system processing duration.

## Assumptions

- Assumes that the underlying `LatencyProfiler` implementation correctly handles nested or sequential start/end interactions as per existing patterns.
- Assumes the time spent in the UI accurately reflects user interaction.
