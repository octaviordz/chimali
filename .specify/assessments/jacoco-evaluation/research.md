# Idea Research: Evaluate JaCoCo for Coverage Measurement

- **Slug**: jacoco-evaluation
- **Created**: 2026-10-28
- **Evidence confidence (overall)**: medium — repository evidence and primary documentation establish selected capabilities and limitations; no comparative project measurement or complete compatibility matrix was obtained.

## Users & Demand

- The user requested an evaluation of JaCoCo and alternatives; the backlog describes the lack of documented rationale for its original selection. This is a stated request, not evidence of a measured productivity or coverage problem. — [source: ../../../docs/backlog/jacoco-coverage-tool-evaluation.md; intake.md] (cited; confidence: high)
- [NEEDS CLARIFICATION: What concrete problems, if any, do developers experience with the current pipeline, and how often?]

## Prior Art

- Feature 058 chose to retain the existing JaCoCo toolchain to address evidence gaps without expanding the feature into an engine migration; it did not provide a named-alternative comparison. — [source: ../../../specs/058-enforce-coverage-targets/research.md §1] (cited; confidence: high)
- The backlog identifies commit ed90e9cf, dated 2026-09-09, as the first repository appearance of JaCoCo; the prior review verified the commit exists, but this research did not independently establish that it was the earliest occurrence. — [source: ../../../docs/backlog/jacoco-coverage-tool-evaluation.md] (cited; confidence: medium)
- The collector configures JaCoCo 0.8.15, gathers host .exec and Android .ec data, and uses the pinned analyzer to check class/probe compatibility and generate supporting counters. — [source: ../../../tools/hdk-coverage.init.gradle] (cited; confidence: high)
- Feature 058 records real class/probe compatibility fixtures and a full connected-device analysis, as well as a clean local candidate/target integration assessment. These are recorded results for identified runs, not independent replications in this research. — [source: ../../../specs/058-enforce-coverage-targets/tasks.md T124, T108; ../../../specs/058-enforce-coverage-targets/implementation-evidence.md §§11.44, 11.105–11.106] (cited; confidence: high)

## Market & Context

- The project currently relies on JaCoCo and its custom collection and verification tools. Continuing that arrangement avoids an immediate migration, but this observation does not establish comparative suitability or future maintenance costs. — [source: ../../../tools/hdk-coverage.init.gradle; ../../../specs/058-enforce-coverage-targets/research.md §1] (cited; confidence: high)
- Kover documents coverage collection for JVM and Android host tests, Kotlin Multiplatform Gradle projects, Android build variants, mixed Kotlin/Java, XML/HTML reports and verification rules. It also supports using JaCoCo as an alternative measurement/reporting library; selecting Kover therefore does not necessarily mean replacing JaCoCo. Its README does not establish connected Android device coverage or compatibility with this project’s exact versions and shrunk variant. — [source: https://github.com/Kotlin/kotlinx-kover] (cited; confidence: high)
- [NEEDS CLARIFICATION: Which current, viable alternatives support this project's exact workloads, and which are front ends over another engine?]

## Data & Constraints

- The policy specifies the app's shrunkDebug compilation, host unit-test and connected Android-test tasks, and identifies jacoco-0.8.15 as the engine. The configured version catalog lists Kotlin 2.3.21 and AGP 9.4.1. — [source: ../../../tools/coverage/policy.json; ../../../gradle/libs.versions.toml] (cited; confidence: high)
- JaCoCo defines branch coverage for if and switch decisions and excludes exception handling from that counter. Feature 058 specifies instruction and applicable branch counters, exact ratio comparisons, configured inventories, proof for omitted zero counters, and compatible execution data. Its coverage scope excludes iOS native. — [source: https://github.com/jacoco/jacoco/blob/master/org.jacoco.doc/docroot/doc/counters.html; ../../../specs/058-enforce-coverage-targets/research.md §§2–4] (cited; confidence: high)
- The owner waived T046–T049 and related hosted operational demonstrations because an acceptable disposable Windows/device worker was unavailable; the registered runner is disabled and reactivation requires explicit authorization. Hosted acceptance is unverified; the waiver does not change local coverage requirements. — [source: ../../../specs/058-enforce-coverage-targets/tasks.md, status and owner waiver] (cited; confidence: high)
- The backlog excludes changing the active engine, thresholds, or enforcement policy during this evaluation. — [source: ../../../docs/backlog/jacoco-coverage-tool-evaluation.md] (cited; confidence: high)

## Evidence Against the Idea

- Feature 058 already invested in a pinned analyzer, class/probe compatibility checks, counter proofs, and verification; evaluating and potentially migrating a tool could require additional work even when the current pipeline meets its recorded local acceptance. The actual cost is not quantified. — [source: ../../../specs/058-enforce-coverage-targets/tasks.md T095, T108, T124; ../../../tools/hdk-coverage.init.gradle] (cited; confidence: medium)
- The current evidence does not demonstrate a specific JaCoCo-caused defect or quantified cost that an alternative would resolve. Absence of such evidence in this limited review does not establish that no problem exists. — [source: ../../../docs/backlog/jacoco-coverage-tool-evaluation.md; ../../../specs/058-enforce-coverage-targets/research.md §1] (cited; confidence: medium)

## Gaps & Open Questions

- [NEEDS CLARIFICATION: Obtain authoritative, current compatibility and metric documentation for JaCoCo and named alternatives, including Kotlin, Gradle, AGP, Android device instrumentation, shrinking, and KMP source sets.]
- [NEEDS CLARIFICATION: Compare representative identical host/device workloads and inventories, with raw counters and documented differences in generated code, branches, and instrumentation.]
- [NEEDS CLARIFICATION: Determine whether existing baselines, exact regression checks, evidence schemas, and policy metrics can be retained or require an explicitly governed transition.]
- [NEEDS CLARIFICATION: Quantify maintenance, operational and migration costs, including the impact of the hosted-worker waiver.]
- [NEEDS CLARIFICATION: Independently verify the earliest JaCoCo introduction and search contemporaneous records for the original selection rationale.]

## Sources

- Repository files linked inline above; historical execution results are attributed to their recorded runs, not newly executed here.
- https://github.com/Kotlin/kotlinx-kover (host: github.com; policy: allowlisted; fetched with connection pinned to a resolved public IPv4 address, no redirects).
- https://github.com/jacoco/jacoco/blob/master/org.jacoco.doc/docroot/doc/counters.html (host: github.com; policy: allowlisted; same connection safeguards).
- https://github.com/jacoco/jacoco (host: github.com; policy: allowlisted; same connection safeguards).
- https://github.com/Kotlin/kotlinx-kover/blob/main/docs/gradle-plugin.md (host: github.com; policy: allowlisted; returned 404; no claim relies on it).
- Documentation was inspected without running a representative comparison. GitHub main/master content is mutable; exact current-version support remains unverified.
