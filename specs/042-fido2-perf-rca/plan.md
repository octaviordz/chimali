# Implementation Plan: FIDO2 Performance RCA Fix

**Branch**: `042-fido2-perf-rca` | **Date**: 2026-05-15 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/042-fido2-perf-rca/spec.md`

## Summary

Fix the false-positive performance budget violation [NFR-PERF-030] by properly recording user interaction time during the FIDO2 `GetAssertion` process in `Ctap2GetAssertionHandler.kt`.

## Technical Context

**Language/Version**: Kotlin  
**Primary Dependencies**: `kotlinx.coroutines`  
**Storage**: N/A  
**Testing**: JUnit 5, MockK  
**Target Platform**: Android  
**Project Type**: Android Feature Module (`feature:fido2`)  
**Performance Goals**: Accurate measurement of NFR-PERF-030 (<200ms latency)  
**Constraints**: Ensure user interaction pauses profiler  
**Scale/Scope**: 1 file modification (`Ctap2GetAssertionHandler.kt`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **X.2 Kotlin & KMP Guidelines**: `try/finally` blocks MUST be used for cleanup. The implementation relies on a `try/finally` block to guarantee `LatencyProfiler.endUserInteraction` is invoked.
- **X.2 Structured Concurrency**: Handled correctly through `withTimeout` and `deferred.await()`.
- **IV. Performance & Reliability Excellence**: Accurately distinguishing between user interaction and raw system latency is critical for telemetry and verifying the <200ms threshold.

All gates passed.

## Project Structure

### Documentation (this feature)

```text
specs/042-fido2-perf-rca/
├── plan.md              
├── research.md          
├── data-model.md        
├── quickstart.md        
└── tasks.md             
```

### Source Code (repository root)

```text
feature/fido2/
└── src/androidMain/kotlin/com/chimali/fido2/presentation/
    └── Ctap2GetAssertionHandler.kt
```

**Structure Decision**: Single file modification in existing FIDO2 module.

## Complexity Tracking

None.
