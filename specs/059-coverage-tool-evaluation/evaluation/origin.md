# JaCoCo Origin and Selection Rationale

Git history distinguishes the earliest mention from the first coverage-tool setup:

- Commit `ed90e9cff2105b27fb92df666386c2559e2c60c2` (2026-09-09) first mentions JaCoCo in the inspected repository history, in a Vault remediation record describing a terminal exception branch that JaCoCo could not probe. Its broad commit message and changed files concern Vault remediation; it does not add the coverage collector or establish that JaCoCo was selected then.
- Commit `2e8a1bd923e19bf7ca99dcd916cea135ad151437` (2026-10-24) introduces the feature 058 coverage enforcement tooling. Its diff configures the Gradle JaCoCo plugin and adds the `tools/hdk-coverage.init.gradle` collector using JaCoCo reports and `.exec`/`.ec` execution data.
- The contemporaneous feature 058 research explains retaining the installed toolchain to avoid expanding coverage remediation into migration. No source reviewed documents the original comparative selection rationale or alternatives considered.

**Disposition**: First JaCoCo mention and first located project coverage-tool setup are separately evidenced. Why JaCoCo was originally chosen remains unknown. Sources: Git commits above, `specs/058-enforce-coverage-targets/research.md`, `tools/hdk-coverage.init.gradle`.
