# FIDO2 GetAssertion Performance Telemetry Fix

This changelog documents the resolution of the performance budget false-positive during FIDO2 authentication ceremonies.

## [Unreleased] - 2026-05-15

### Fixed
- **Performance Telemetry (NFR-PERF-030)**: Resolved a false-positive performance budget violation during `GetAssertion`.
    - Correctly wrapped the user interaction suspension point (`deferred.await()`) in `Ctap2GetAssertionHandler.kt` with `LatencyProfiler` start/end markers.
    - Implemented a `try/finally` block to ensure user interaction time is always segregated from raw system latency, even in the event of timeouts.
    - Verified that pure system processing time is accurately tracked and correctly reports compliance with the <200ms performance budget.

### Added
- **RCA Documentation**: Documented the root cause analysis and remediation strategy in `specs/042-fido2-perf-rca/RCA-NFR-PERF-030.md`.
- **Implementation Artifacts**: Created the technical specification, implementation plan, and walkthrough for the performance telemetry fix.

### Governance & Alignment
- **Constitution Compliance**: The fix adheres to Principle X.2 (Structured Concurrency) and Principle IV (Performance Monitoring) of the project's technical constitution.
- **CI/CD Validation**: Verified the fix via the `local-ci.ps1` pipeline, ensuring no regressions in the FIDO2 module.
