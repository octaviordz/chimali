# Phase 0: Research

## FIDO2 Performance Bug [NFR-PERF-030]

**Decision**: Wrap `deferred.await()` with `LatencyProfiler.startUserInteraction` and `endUserInteraction` inside a `try/finally` block in `Ctap2GetAssertionHandler.kt`.
**Rationale**: Based on `RCA-NFR-PERF-030.md`, the `LatencyProfiler` is active during the `GetAssertion` process but is not paused when the system awaits user interaction (unlike the `MakeCredential` handler). This leads to user interaction time being incorrectly attributed to system latency.
**Alternatives considered**: Modifying `BluetoothHidTransportImpl.kt` (rejected because it doesn't know when UI begins/ends; it only knows about the CBOR payload boundary).
