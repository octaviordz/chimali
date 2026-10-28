# Backlog: Evaluate JaCoCo for Coverage Measurement

**Status**: Open - evaluation requested by the user
**Created**: 2026-10-28
**Origin**: Feature 058 coverage-tool selection rationale
**Related plan**: [Coverage enforcement plan](../../specs/058-enforce-coverage-targets/plan.md)
**Related evidence**: JaCoCo first appears in repository history in commit `ed90e9cf` (2026-09-09), which added scoped Vault coverage measurement. The available records explain the need for critical-code coverage, but do not document why JaCoCo was selected over alternatives.

## Evaluation scope

Assess whether JaCoCo is a good fit for Chimali's Kotlin Multiplatform Android/JVM coverage needs and compare it with relevant alternatives, including Kotlin/KMP-focused options. Evaluate the actual configured source sets and host/device workloads, Android variants and shrinking, instruction and branch metric meanings, `.exec`/`.ec` merging and class/probe compatibility, Gradle/AGP/Kotlin support, inventory and report fidelity, CI operations, maintenance, and migration cost.

Use authoritative compatibility evidence and a small reproducible representative measurement where needed. Distinguish alternative report or instrumentation front ends from alternatives that use a different coverage engine. Record material gaps, assumptions, and limitations.

## Decision and completion criteria

Document an evidence-based recommendation to retain JaCoCo, replace it, or defer the choice, with the reasons and any proposed migration follow-up. Do not change the active engine, coverage thresholds, or enforcement policy as part of this evaluation. Complete when the comparison and recommendation are recorded in `specs/058-enforce-coverage-targets/research.md` and any separately approved migration work is captured as its own backlog item.
