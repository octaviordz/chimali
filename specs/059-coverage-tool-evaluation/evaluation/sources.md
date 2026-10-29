# Source Register

| ID | Version/date | Source | Claim used | Limits |
|---|---|---|---|---|
| R1 | `eebc391bbe1956a325a95bcc6b2e401607e72ce7` | `tools/coverage/policy.json`, `gradle/libs.versions.toml`, build files | Configured workloads and pinned toolchain | Must be refreshed for later revisions. |
| R2 | Historical, through 2026-10-28 | `specs/058-enforce-coverage-targets/implementation-evidence.md` §11.144 | Local JaCoCo run was accepted for the recorded scope | Not comparative or present device availability. |
| R3 | Historical, 2026-10-28 | `specs/058-enforce-coverage-targets/tasks.md` | Hosted worker work waived; runner recorded disabled | Does not establish live runner state. |
| R4 | Kover 0.9.11 | https://github.com/Kotlin/kotlinx-kover/blob/v0.9.11/kover-gradle-plugin/docs/index.md | Gradle plugin support, metrics, backend options and device-test limitation | Version-specific; not a compatibility certification for this exact stack. |
| R5 | Kover 0.9.11 | https://github.com/Kotlin/kotlinx-kover/blob/v0.9.11/CHANGELOG.md | Version history and compatibility fixes | Changelog does not prove exact AGP/Kotlin combination. |
| R6 | JaCoCo 0.8.15 | https://github.com/jacoco/jacoco/blob/v0.8.15/org.jacoco.doc/docroot/doc/counters.html | Instruction and branch counter definitions | Does not establish equal cross-engine counters. |
| R7 | JaCoCo 0.8.15 | https://github.com/jacoco/jacoco/blob/v0.8.15/org.jacoco.doc/docroot/doc/classids.html | Class identity needed for execution-data analysis | Does not certify other engines' data compatibility. |
| R8 | Current docs inspected 2026-10-28 | https://kotlin.github.io/kotlinx-kover/gradle-plugin/ | Kover host/Android variant coverage and device-test limit | Unversioned site; R4 is used for version-specific conclusions. |
| R9 | Current docs inspected 2026-10-28 | https://github.com/Kotlin/kotlinx-kover/blob/main/CHANGELOG.md | Kover 0.9.11 latest documented release at inspection | Mutable branch; point-in-time only. |
| R10 | Current official Kotlin documentation inspected 2026-10-28 | https://kotlinlang.org/docs/jvm-backend-faq.html | Kover recommended for Kotlin-first work; JaCoCo remains an option for established pipelines | General recommendation, not project-specific suitability proof. |
| R11 | Git commits 2026-09-09 and 2026-10-24 | `ed90e9cff2105b27fb92df666386c2559e2c60c2`; `2e8a1bd923e19bf7ca99dcd916cea135ad151437` | First JaCoCo mention versus first located coverage-tool setup | First mention does not establish tool selection; original rationale remains unknown. See `origin.md`. |`n| R12 | Existing assessment receipts | `.specify/assessments/jacoco-evaluation/source-receipts.json` | Historical source fetch provenance | Receipts belong to prior research and are not new measurements. |

## Candidate search

Search used official Kotlin/Kover documentation and repository, Gradle's official JaCoCo plugin documentation, and targeted searches for JVM/Android device coverage alternatives. Kover is the only documented Kotlin-focused plugin candidate found, already required by the spec. IntelliJ coverage agent is part of the Kover family and no complete Android device workflow was established. No third candidate met the relevance and distinct-engine evidence criterion; see [third-candidate.md](third-candidate.md).
