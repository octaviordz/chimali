# Idea Intake: Evaluate JaCoCo for Coverage Measurement

- **Slug**: jacoco-evaluation
- **Created**: 2026-10-28
- **Source**: Completed backlog request, now documented in [the feature 059 recommendation](../../../specs/059-coverage-tool-evaluation/evaluation/recommendation.md) and [feature 058 research](../../../specs/058-enforce-coverage-targets/research.md). The original backlog file was removed after completion.
- **Type**: exploration

## Idea (as captured)

> # Backlog: Evaluate JaCoCo for Coverage Measurement
> 
> **Status**: Open - evaluation requested by the user
> **Created**: 2026-10-28
> **Origin**: Feature 058 coverage-tool selection rationale
> **Related plan**: [Coverage enforcement plan](../../specs/058-enforce-coverage-targets/plan.md)
> **Related evidence**: The repository first mentions JaCoCo in commit `ed90e9c` (2026-09-09); the first located project coverage-tool setup is commit `2e8a1bd` (2026-10-24). The original comparative selection rationale remains undocumented.
> 
> ## Evaluation scope
> 
> Assess whether JaCoCo is a good fit for Chimali's Kotlin Multiplatform Android/JVM coverage needs and compare it with relevant alternatives, including Kotlin/KMP-focused options. Evaluate the actual configured source sets and host/device workloads, Android variants and shrinking, instruction and branch metric meanings, `.exec`/`.ec` merging and class/probe compatibility, Gradle/AGP/Kotlin support, inventory and report fidelity, CI operations, maintenance, and migration cost.
> 
> Use authoritative compatibility evidence and a small reproducible representative measurement where needed. Distinguish alternative report or instrumentation front ends from alternatives that use a different coverage engine. Record material gaps, assumptions, and limitations.
> 
> ## Decision and completion criteria
> 
> Document an evidence-based recommendation to retain JaCoCo, replace it, or defer the choice, with the reasons and any proposed migration follow-up. Do not change the active engine, coverage thresholds, or enforcement policy as part of this evaluation. Complete when the comparison and recommendation are recorded in `specs/058-enforce-coverage-targets/research.md` and any separately approved migration work is captured as its own backlog item.

## Restated

Evaluate JaCoCo and relevant alternatives for Chimali's Android/JVM coverage needs, then document a recommendation and any proposed migration follow-up. The evaluation itself does not change the active engine, thresholds, or enforcement policy.

## Origin & Context

- **Raised by**: User.
- **Trigger**: The backlog item identifies an absence of documented rationale for the original selection of JaCoCo over alternatives and references feature 058's coverage-tool selection rationale.

## First-Glance Unknowns

- [NEEDS CLARIFICATION: Which alternatives and coverage engines are suitable candidates for comparison?]
- [NEEDS CLARIFICATION: How well does each candidate support the configured source sets, variants, host and device workloads, and existing metric and evidence requirements?]
- [NEEDS CLARIFICATION: What representative measurement and compatibility evidence are needed to support a recommendation?]
- [NEEDS CLARIFICATION: What migration and maintenance costs or limitations would each option entail?]
