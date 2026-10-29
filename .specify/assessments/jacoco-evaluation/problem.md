# Problem Definition: Confidence in Coverage Tool Selection

- **Slug**: jacoco-evaluation
- **Created**: 2026-10-28
- **Inputs used**: [intake.md](intake.md) and [research.md](research.md)

## Problem Statement

The project owner lacks a documented, evidence-based comparison of the current coverage tool and relevant alternatives for Chimali's configured Android/JVM workloads. This makes it difficult to judge whether the existing selection remains appropriate and what trade-offs a future change would entail; a specific tool-caused defect or quantified cost has not been established.

## Affected Users & Stakeholders

- **User and decision maker**: The project owner requested a basis for evaluating the coverage tool and alternatives. ([intake](intake.md), [research](research.md))
- **Other users and stakeholders**: [NEEDS CLARIFICATION: Which developers or maintainers experience concrete problems with the current coverage process, and who else participates in the decision?]

## Goals

- Establish a credible basis for judging coverage-tool suitability against the project's actual workloads, metric requirements and operating constraints.
- Make material compatibility limitations, evidence gaps, maintenance and potential transition costs visible to the decision maker.
- Support a documented decision to retain, replace or defer, without assuming that a change is necessary.

## Non-Goals

- Change the active coverage engine, thresholds or enforcement policy as part of this evaluation.
- Treat hosted enforcement as verified or reactivate the disabled runner under the existing operational waiver.
- Claim native iOS coverage as part of the current Android/JVM measurement scope.

## Success Metrics

- **Decision evidence completeness**: A documented comparison addresses the configured host/device workloads, variants, metrics, compatibility, evidence continuity and costs, and identifies material unknowns. (Baseline: feature 058 records a decision to retain the existing tool for scope reasons, but not a named-alternative comparison; [research](research.md).)
- **Decision traceability**: A recommendation and its supporting reasons are recorded in feature 058 research, including any separately approved migration follow-up. (Baseline: no completed comparative recommendation recorded in this assessment; [intake](intake.md), [research](research.md).)
- **Evidence quality**: Compatibility claims have authoritative support or are explicitly marked unverified; representative measurements are reproducible where needed. (Baseline: selected primary documentation exists, but no project-specific comparative measurement or complete compatibility matrix; [research](research.md).)

## Cost of Inaction

The existing JaCoCo pipeline and its recorded local acceptance remain the available basis for coverage, while the relative suitability and potential costs of alternatives remain unknown. The available evidence does not quantify a resulting loss or establish that a change would improve the outcome. ([research](research.md))

## Open Questions

- [NEEDS CLARIFICATION: What concrete problems, if any, do developers experience with the current pipeline, and how often?]
- [NEEDS CLARIFICATION: Which viable alternatives and underlying engines support the project's exact toolchain and host/device workloads, including shrinking?]
- [NEEDS CLARIFICATION: What representative measurements are needed to compare inventories, raw counters and metric differences?]
- [NEEDS CLARIFICATION: Can existing baselines, regression checks, evidence schemas and policy metrics remain comparable under each alternative?]
- [NEEDS CLARIFICATION: What are the maintenance, operational and transition costs, including the impact of the hosted-worker waiver?]
- [NEEDS CLARIFICATION: What contemporaneous evidence explains the original selection and establishes when JaCoCo first appeared?]
