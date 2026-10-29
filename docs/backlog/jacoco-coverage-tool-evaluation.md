# Backlog: Evaluate JaCoCo for Coverage Measurement

**Status**: Complete - comparison and recommendation recorded in [feature 058 research](../../specs/058-enforce-coverage-targets/research.md) and [feature 059 recommendation](../../specs/059-coverage-tool-evaluation/evaluation/recommendation.md)
**Created**: 2026-10-28
**Origin**: Feature 058 coverage-tool selection rationale
**Related plan**: [Coverage enforcement plan](../../specs/058-enforce-coverage-targets/plan.md)
**Related evidence**: The first JaCoCo mention located is commit `ed90e9cff2105b27fb92df666386c2559e2c60c2` (2026-09-09), discussing an unprobeable terminal exception. The first located project coverage-tool setup is commit `2e8a1bd923e19bf7ca99dcd916cea135ad151437` (2026-10-24), which configures the JaCoCo collector. The original comparative selection rationale remains undocumented. See [origin investigation](../../specs/059-coverage-tool-evaluation/evaluation/origin.md).

## Evaluation scope

Assess whether JaCoCo is a good fit for Chimali's Kotlin Multiplatform Android/JVM coverage needs and compare it with relevant alternatives, including Kotlin/KMP-focused options. Evaluate the actual configured source sets and host/device workloads, Android variants and shrinking, instruction and branch metric meanings, `.exec`/`.ec` merging and class/probe compatibility, Gradle/AGP/Kotlin support, inventory and report fidelity, CI operations, maintenance, and migration cost.

Use authoritative compatibility evidence and a small reproducible representative measurement where needed. Distinguish alternative report or instrumentation front ends from alternatives that use a different coverage engine. Record material gaps, assumptions, and limitations.

## Decision and completion criteria

Document an evidence-based recommendation to retain JaCoCo, replace it, or defer the choice, with the reasons and any proposed migration follow-up. Do not change the active engine, coverage thresholds, or enforcement policy as part of this evaluation. Complete when the comparison and recommendation are recorded in `specs/058-enforce-coverage-targets/research.md` and any separately approved migration work is captured as its own backlog item.
