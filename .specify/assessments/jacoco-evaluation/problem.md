# Problem Definition: Confidence in Coverage Tool Selection

- **Slug**: jacoco-evaluation
- **Created**: 2026-10-28
- **Updated**: 2026-10-28, America/Mexico_City
- **Inputs used**: [intake.md](intake.md) and refreshed [research.md](research.md)
- **Scope of this revision**: Problem definition only; the concept and decision have not been reassessed in this step.

## Problem Statement

The project owner lacks a complete, evidence-based basis for judging whether the current coverage tool remains appropriate for Chimali's configured Android/JVM host and device workloads, and what the trade-offs of alternatives would be. Existing local acceptance demonstrates operation for identified JaCoCo runs, while the partial alternatives research does not establish an equivalent replacement or quantify a benefit from changing tools. ([research](research.md), Users & Demand, Prior Art and Data & Constraints)

## Affected Users & Stakeholders

- **User and decision maker**: The project owner requested the evaluation and needs a defensible retain, replace or defer recommendation. ([intake](intake.md))
- **Other affected roles**: Developer and maintainer involvement, observed pain and decision participation remain unverified; see open question 1. ([research](research.md), Users & Demand)

## Goals

- Establish comparative suitability against the actual required host/device scope, production variants and evidence obligations.
- Distinguish a front-end change from an engine change so that the owner can judge the actual benefit and compatibility implications.
- Make material limits, unknowns, costs and baseline-comparison consequences visible without assuming replacement is necessary.
- Support a traceable retain, replace or defer recommendation, including the limits of the evidence.

These goals follow the [intake](intake.md) and the compatibility and metric findings in [research](research.md).

## Evidence and Constraints

- **Established operating baseline**: Feature 058 §11.144 records complete host/device collection and successful local production verification for the identified JaCoCo 0.8.15 run under Gradle 9.6.0, AGP 9.4.1, Kotlin 2.3.21 and Java 21. It also records an Android test-result listener exception. This is historical evidence, not a new or universal compatibility claim. ([research](research.md), Prior Art)
- **Established candidate limitation**: Kover 0.9.11's Gradle-plugin documentation explicitly excludes Android device-test coverage. The previous uncertainty about that plugin/version is resolved; complete-workload suitability of other tools remains open. ([research](research.md), Version and compatibility evidence)
- **Measurement continuity**: Existing acceptance requires instruction and applicable branch counters, class/probe compatibility, inventory and zero-counter evidence, and compatible fingerprints. XML format compatibility alone does not establish these properties. ([research](research.md), Metrics, merging and report fidelity)
- **Actual workload boundary**: Current policy requires device workloads for FIDO2, core common and settings; app device tests are optional, and its production variant is shrunkDebug. Native iOS is outside the measured scope. ([research](research.md), Actual configured boundary)
- **Operational boundary**: Hosted rollout remains owner-waived and unverified, and the registered runner is disabled. A coverage-tool evaluation does not resolve or authorize changing that operational constraint. ([research](research.md), Measured baseline and operations)

## Non-Goals

- Change the active engine, coverage thresholds, required workloads, generated-code policy or enforcement policy during this evaluation.
- Reactivate the runner, change hosted controls or claim waived hosted verification.
- Establish native iOS coverage or redesign the production coverage infrastructure.
- Assume that current-tool success proves superiority, or that a candidate's host-only coverage proves complete replacement suitability.

The first three boundaries derive from the [intake](intake.md) and [research](research.md); the last bounds interpretation of the comparative evidence.

## Success Metrics

| Signal | Completion condition | Current baseline |
| --- | --- | --- |
| Comparison completeness | Each compared option has a supported, unsupported or unverified disposition with evidence for configured source sets, host/device workloads, variants/shrinking, metric semantics, data compatibility, inventory/report fidelity and toolchain support. | Partial candidate comparison; Kover 0.9.11's device collection limitation is established, while exact host-side toolchain compatibility remains unverified. |
| Measurement credibility | Comparative numerical claims identify scope and raw counters, explain material differences, and use reproducible representative evidence where needed; missing evidence is explicit. | No comparative project measurement has been run. |
| Decision traceability | Feature 058 research records a reasoned retain, replace or defer recommendation, limitations and any separately approved migration follow-up. | Existing retention rationale addresses feature scope, not a completed comparative evaluation. |
| Cost visibility | The recommendation distinguishes supported cost evidence from unknown maintenance, operational and transition costs. | No quantified comparative costs or developer pain recorded. |

Baselines are grounded in [research](research.md); completion conditions operationalize the goals and the [intake](intake.md). These measure evaluation quality, not new product coverage thresholds or a required performance improvement.

## Cost of Inaction

The current JaCoCo pipeline remains the available coverage mechanism with its recorded local acceptance; the owner's comparative decision question stays incompletely answered. Research has not established a quantified loss from continuing it, a specific engine defect, or savings from replacement. The documented Kover device-test limitation narrows one candidate's applicability but does not establish that no viable alternative exists. ([research](research.md), Evidence Against the Idea)

## Open Questions

1. [NEEDS CLARIFICATION: What concrete problems or recurring costs do developers experience, which roles are affected, and who participates in the owner's decision?]
2. [NEEDS CLARIFICATION: Which alternatives have a supported complete host/device path for the current scope, given Kover 0.9.11's established Gradle-plugin limitation?]
3. [NEEDS CLARIFICATION: What authoritative or measured evidence establishes exact AGP 9.4.1/Kotlin 2.3.21/KMP compatibility for any candidate host-side coverage?]
4. [NEEDS CLARIFICATION: What representative evidence is necessary to judge raw counters, Kotlin filtering, denominator preservation and duplicate/mismatched-class handling fairly?]
5. [NEEDS CLARIFICATION: Can existing baselines, regression checks and evidence contracts remain comparable under each alternative, and what justified transition would otherwise be required?]
6. [NEEDS CLARIFICATION: What are the actual maintenance, runtime/reporting and migration costs within the existing hosted-worker constraint?]
7. [NEEDS CLARIFICATION: What contemporaneous records establish the earliest JaCoCo introduction and original selection rationale?]

These carry forward unresolved research questions. The Kover 0.9.11 device limitation is an established fact rather than an unanswered question; its broader implications still require evaluation.
