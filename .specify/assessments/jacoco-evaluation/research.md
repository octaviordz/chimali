# Idea Research: Evaluate JaCoCo for Coverage Measurement

- **Slug**: jacoco-evaluation
- **Created**: 2026-10-28
- **Updated**: 2026-10-28, America/Mexico_City
- **Evidence confidence (overall)**: medium
- **Scope**: Refreshed repository inspection and primary-source review. No comparative build, host/device test run, benchmark, engine change, or hosted operation was performed. Recorded executions below are historical evidence, not new measurements.
- **Assessment status**: Research informs the existing problem/concept/decision; it does not select an engine or close the backlog. Those other artifacts were not changed.

## Users & Demand

- The owner explicitly requested an evaluation of JaCoCo against relevant alternatives. The documented problem is the absence of a comparative selection rationale; neither the intake nor the available research establishes a quantified developer pain or a specific JaCoCo defect. [L1, intake.md] (cited; confidence: high)
- The research therefore distinguishes evidence for investigating tool suitability from evidence that replacement would improve the project. [L1, L2] (inference from cited evidence; confidence: medium)
- [NEEDS CLARIFICATION: What recurring developer problems, investigation time, or operational costs should a comparison seek to improve?]

## Prior Art

- Feature 058 retained the installed engine to avoid expanding coverage remediation into a migration. Its research explains scope control, not superiority over named alternatives. [L2, §1] (cited; confidence: high)
- The current collector pins JaCoCo 0.8.15, analyzes class/probe compatibility, collects execution data, emits raw instruction/branch counters, and binds analysis evidence to reports and inventory. These are existing implementation capabilities, not features inferred from product marketing. [L3, L4] (cited; confidence: high)
- Feature 058 §11.144 records a clean candidate/target pair using Gradle 9.6.0, AGP 9.4.1, Kotlin 2.3.21, JaCoCo 0.8.15 and JetBrains JDK 21 on Windows with an API 35 ARM64 Android device. It records 1,300 candidate tests, no failures/errors/skips, complete collection, passing floors and regression. It also records an Android test-result listener exception despite successful final reporting. This is evidence for that run, not a guarantee about every future configuration. [L7, §11.144] (cited; confidence: high)
- The backlog attributes initial introduction to ed90e9cf (2026-09-09). Earlier review verified the commit exists, but this refresh could not independently search Git history because shell startup failed with sandbox provisioning/access-denied errors. The earliest occurrence and original selection rationale remain unverified. [L1; tool failure in this research session] (cited; confidence: medium for the attributed history)

## Market & Context

### Candidate distinctions

| Candidate | What changes | Evidence of relevant capabilities | Important limit |
| --- | --- | --- | --- |
| Current Gradle/AGP plus JaCoCo 0.8.15 | Existing engine and collector | Actual configured host/device measurement and production verification are recorded locally. [L3–L7] | Historical success does not establish comparative maintenance cost or superiority. |
| Kover 0.9.11 with its embedded coverage backend | Plugin and potentially underlying measurement/reporting engine | Versioned documentation describes JVM/KMP host coverage, Android variants, mixed Kotlin/Java, XML/HTML, and instruction/branch units. [S1, S2] | The Gradle plugin explicitly does not collect coverage from tests executing on Android devices. |
| Kover 0.9.11 configured to use JaCoCo | Front end changes; JaCoCo remains the engine | Documentation provides useJacoco configuration and warns that full feature compatibility is not guaranteed. [S2] | This does not establish a solution for required connected-device collection, or preserve the custom evidence contracts automatically. |
| IntelliJ coverage agent directly | Different JVM engine/integration | Maintainer README describes a JVM engine supporting branches and per-test tracking, and identifies Kover as its Gradle/Maven integration. [S4] | No connected Android-device collection or project-specific integration was established by that source. It is related to the Kover family, not an independently verified full-workload replacement. |

These are comparison subjects, not selected approaches. No hosted reporting service was treated as an instrumentation-engine replacement. (Inference from the capability distinctions above; confidence: medium.)

### Version and compatibility evidence

- Kover 0.9.11's versioned documentation explicitly states that Android device instrumentation coverage is not collected. This resolves the earlier README-only uncertainty for that plugin/version. It does not prove that every future Kover release or separate offline tool has the same limitation. [S2] (cited; confidence: high)
- Kover's changelog documents AGP 9.0 support and subsequent variant/task-dependency fixes, KMP Android library support/fixes, and a Gradle 9.6.0 deprecation-warning fix. These are relevant compatibility signals, not certification of Chimali's exact AGP 9.4.1/Kotlin 2.3.21 combination. [S3] (cited; confidence: high)
- JaCoCo 0.8.15 release notes describe Kotlin filtering and SMAP corrections and Java class-version support. Such bytecode/filtering details matter to measured denominators; they do not by themselves certify the whole Android/Gradle stack. [S5] (cited; confidence: high)

## Data & Constraints

### Actual configured boundary

- The repository pins Gradle 9.6.0, AGP 9.4.1 and Kotlin 2.3.21. The app targets JVM bytecode 17, while coverage policy requires Java 21 for the daemon/compiler/host-test runtimes. These are different identities, not conflicting descriptions of one version. [L4–L6, L8] (cited; confidence: high)
- The policy includes commonMain, androidMain and main production scope, excludes iosMain and macrobenchmark, and maps 14 production modules. Five Tier 1 module entries must be represented, including an empty-module disposition where applicable. [L4] (cited; confidence: high)
- Required device modules are feature:fido2, core:common and feature:settings. App device tests are optional in current policy; the app's selected variant is shrunkDebug. The app build enables Android test coverage on that release-derived minified variant. The coverage device minimum is API 35 with approved ARM64/x86_64 ABIs; this must not be confused with the product minSdk of 28. [L4–L6] (cited; confidence: high)
- Current floors are 60% Tier 1 instructions and applicable branches, 50% repository instructions, and 20% Tier 2 instructions where gated; 65% branches is an improvement goal. These are fixed evaluation constraints, not proposed new thresholds. [L4] (cited; confidence: high)

### Metrics, merging and report fidelity

- JaCoCo instruction counters measure bytecode instructions; its branches cover if/switch decisions and exclude exception handling. Source-line percentages are a different quantity and cannot substitute for the existing instruction gate. [S6, L4] (cited; confidence: high)
- JaCoCo class IDs depend on class bytes; execution and analysis must refer to matching classes. Post-processing, compiler differences and runtime transformations can break this relationship. Therefore matching names or source files alone do not establish that host/device data can be combined. [S7] (cited; confidence: high)
- Kover documents LINE, INSTRUCTION and BRANCH units, with LINE as default, and JaCoCo-compatible XML output. It also documents IC binary reports and external IC inputs. XML shape compatibility alone does not establish equal raw counters, equivalent Kotlin filtering, or interchangeability with JaCoCo execution data. [S2, S6, S7] (first two statements cited; final statement an inference, confidence: medium)
- Kover requires one coverage-library type across projects linked through its coverage dependencies. Mixing embedded-backend and JaCoCo measurements is not established as a valid way to preserve this project's combined denominator. [S2] (cited limitation and inference; confidence: high/medium respectively)
- The production verifier explicitly validates jacoco-0.8.15, AnalyzedZeroProofV1 and the configured metric/runtime identities. Policy, harness, toolchain and device characteristics participate in comparison fingerprints. Consequently, a replacement XML file cannot be assumed to satisfy current acceptance or baseline equivalence without separately justified changes. [L3, L4] (cited facts and inference; confidence: high)

### Measured baseline and operations

- The latest documented clean pair (§11.144) reports candidate repository instructions 75,259/133,061 and FIDO2 branches 3,362/5,510. It records successful local verification with the identified harness, not a comparative tool experiment. Publication is Pending and hosted integration Unverified. [L7, §11.144] (cited; confidence: high)
- T046–T049/T089 hosted operational work remains owner-waived. The runner is disabled; this evaluation does not authorize its reactivation. A different coverage tool does not by itself resolve the lack of an acceptable disposable Windows/device worker. [L9] (cited constraint and inference; confidence: high)
- Existing work on exact counters, zero proofs, strict test evidence and fingerprints is documented, but elapsed maintenance effort, performance overhead and migration costs have not been quantified in this assessment. [L3, L7; absence of a comparative measurement here] (cited/explicit gap; confidence: medium)

## Evidence Against the Idea

- The current pipeline has documented full local acceptance for the actual toolchain and workloads. No demonstrated engine defect or measured maintenance saving currently establishes that replacing it is worthwhile. This challenges a migration case, though not the owner's request for an evaluation. [L7, L1] (cited facts and inference; confidence: medium)
- The strongest Kotlin-focused plugin examined has a documented connected-device collection gap against the current required workload. A host-only comparison would therefore not establish full replacement suitability. [S2, L4] (inference from cited evidence; confidence: high)
- Changing a front end while retaining JaCoCo may leave its class/probe requirements intact, while changing the engine can affect counters and evidence compatibility. Neither automatically removes the project's verification obligations. [S2, S7, L3, L4] (inference; confidence: medium)
- No evidence in this review establishes that an exhaustive comparison or a new production integration would repay its cost. Cost/benefit remains an unanswered question rather than an assumed benefit. [L1, research scope] (explicit gap; confidence: medium)

## Gaps & Open Questions

- [NEEDS CLARIFICATION: Verify exact AGP 9.4.1/Kotlin 2.3.21/KMP plugin compatibility for a host-side Kover experiment; changelog coverage is narrower than that claim.]
- [NEEDS CLARIFICATION: Establish whether any alternative has a supported complete host/device path for the required scope. Kover 0.9.11's Gradle-plugin limitation is established; a separate tool or custom hybrid would require its own evidence.]
- [NEEDS CLARIFICATION: If comparing measurements, use identical source/tests and declared production inventories, record raw instruction/branch counts, and explain Kotlin-generated-code and filtering differences. No such experiment was run here.]
- [NEEDS CLARIFICATION: Establish denominator preservation and duplicate/mismatched-class rejection before interpreting combined host/device figures or cross-engine percentages.]
- [NEEDS CLARIFICATION: Quantify setup/maintenance burden, execution/reporting overhead and migration cost, including baseline and verifier implications.]
- [NEEDS CLARIFICATION: Independently verify the earliest introduction and original selection rationale once Git execution is available.]
- [NEEDS CLARIFICATION: Obtain concrete developer pain and decision-priority evidence from the owner; this review documents demand for evaluation, not observed user harm.]

## Sources

Local references are repository evidence, not external fetches:

- **L1**: [Backlog](../../../docs/backlog/jacoco-coverage-tool-evaluation.md).
- **L2**: [Feature 058 research](../../../specs/058-enforce-coverage-targets/research.md).
- **L3**: [Collector](../../../tools/hdk-coverage.init.gradle) and [shared verifier](../../../tools/coverage/coverage-common.ps1).
- **L4**: [Coverage policy](../../../tools/coverage/policy.json).
- **L5**: [Version catalog](../../../gradle/libs.versions.toml).
- **L6**: [App configuration](../../../app/build.gradle.kts).
- **L7**: [Implementation evidence](../../../specs/058-enforce-coverage-targets/implementation-evidence.md), especially §§11.138–11.144.
- **L8**: [Gradle wrapper configuration](../../../gradle/wrapper/gradle-wrapper.properties).
- **L9**: [Tasks and owner waiver](../../../specs/058-enforce-coverage-targets/tasks.md).

External primary sources (all host github.com; policy allowlisted; connected to a validated public IPv4 pinned for HTTPS; redirects not followed):

- **S1**: [Kover 0.9.11 README](https://github.com/Kotlin/kotlinx-kover/blob/v0.9.11/README.md).
- **S2**: [Kover 0.9.11 Gradle-plugin documentation](https://github.com/Kotlin/kotlinx-kover/blob/v0.9.11/kover-gradle-plugin/docs/index.md).
- **S3**: [Kover 0.9.11 changelog](https://github.com/Kotlin/kotlinx-kover/blob/v0.9.11/CHANGELOG.md).
- **S4**: [IntelliJ coverage agent README](https://github.com/JetBrains/intellij-coverage/blob/master/README.md) (mutable branch; only the stated JVM capabilities are used).
- **S5**: [JaCoCo 0.8.15 release notes](https://github.com/jacoco/jacoco/releases/tag/v0.8.15).
- **S6**: [JaCoCo 0.8.15 counters](https://github.com/jacoco/jacoco/blob/v0.8.15/org.jacoco.doc/docroot/doc/counters.html).
- **S7**: [JaCoCo 0.8.15 class IDs](https://github.com/jacoco/jacoco/blob/v0.8.15/org.jacoco.doc/docroot/doc/classids.html).

Additional release/tree pages were inspected to locate versioned documentation. Fetch receipts with status, timestamp and content hashes are retained in [source-receipts.json](source-receipts.json). Version tags are references, not cryptographically immutable guarantees; hashes identify the fetched page bodies. No claim relies on the earlier nonexistent docs/gradle-plugin.md path.
