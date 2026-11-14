# Backlog: Rendering Smoothness Measurement and Benchmarks

**Status**: Open — benchmark execution deferred/unverified

**Created**: 2026-11-13 (split from feature 057 follow-ups)

**Tracking ID**: `FUP-PERF-001`

**Origin**: [Feature 057 follow-up index](../../specs/057-improve-verification-contracts/follow-ups.md) and [original combined backlog](../../specs/057-improve-verification-contracts/testability-backlog-origin.md). This file owns active scope, status and completion evidence for this work area.

## Context carried forward

- **Governing Area**: Performance Vitals & Rendering Smoothness (Constitution Principle VI, BRD `NFR-PERF-020`, TRD §9.6)
- **Mapped Feature Requirements**: FR-015, FR-016, FR-017
- **Remaining Scope**: Implementing new Macrobenchmark target screens (e.g. `PasswordDetailScreen`), fixing existing benchmark harness issues, selecting reference hardware models/compilation modes, and executing performance runs.
- **Reason Excluded**: Feature 057 preserves existing budgets and specifies measurement contract fields; selecting hardware and executing benchmarks is excluded.
- **Current Verification Status**: `Deferred` / `Unverified` for rendering (budgets retained; rendering benchmark run pending; later FIDO operation results have separate scope)
- **Lifecycle Status**: `Assigned`
- **Preserved Authorization / Deferral Reference**: BRD `NFR-PERF-020` and TRD §9.6. Feature-055 T084 was superseded by the owner-approved feature-060 simulated operation assessment; T085 profiling/optimization continues in feature 062 and is outside this rendering item.
- **Responsible Role**: Performance Verification Owner / Android Performance Engineer
- **Needed Completion Evidence**: Reviewed and approved measurement contract document, followed by benchmark execution reports on reference devices confirming consistent 60 FPS, 0 frozen frames (>700ms), and <1% slow frames (>16ms) on vault list scrolling and detail inspection.

## Next steps

Complete the TRD §9.6 measurement contract. Reassess the existing benchmark target, source-set discovery and workload execution; add password-detail coverage. Execute declared workloads and assess rendering budgets with supported metrics, distinguishing CPU duration from display deadlines and recording device/API limits.

## Evidence and completion

Prove test discovery, correct application targeting, seeded navigation, and actual scrolling. Retain benchmark output and environment metadata in the implementing feature's evidence record, linked from the TRD procedure.

Approved measurement contract; executable benchmark with assertions preventing empty workloads; recorded results assessed against approved thresholds; unresolved device/metric limitations explicitly tracked.

The [feature-060 simulated operation results](../../specs/060-fido2-latency-measurement/implementation-evidence.md) do not establish rendering compliance. FIDO attribution/optimization continues in [feature 062](../../specs/062-fido2-performance-investigation/implementation-evidence.md) and its existing backlog items.
