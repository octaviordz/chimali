# Feature Specification: Compose Detekt & Android Lint Integration

**Feature Branch**: `023-compose-detekt-lint`
**Created**: 2026-04-26
**Status**: Draft

## Clarifications

### Session 2026-04-26

- Q: Which Compose rule sub-set and severity should be activated? → A: All Compose rules enabled at **error** severity immediately (zero-tolerance from day 1).
- Q: Which lint variant runs in CI? → A: `lintRelease` — validates against production build configuration for maximum accuracy.
- Q: Where should the lint configuration block live? → A: **Shared convention plugin** in `build-logic/` — single change point, zero per-module drift.
- Q: Which modules should the Compose rule plugin target? → A: Only modules that **already apply the Compose compiler plugin** (UI/feature modules) — not pure-Kotlin or non-Compose Android modules.
- Q: How should pre-existing Compose violations be handled at integration time? → A: Each pre-existing violation is suppressed individually with `@Suppress` and a `// TODO: resolve after integration` comment — zero-tolerance policy preserved from day 1, backlog remains visible and grep-able.

## User Scenarios & Testing *(mandatory)*

### User Story 1 – Compose rule violations are surfaced in Detekt reports (Priority: P1)

A developer working on Jetpack Compose UI code runs a local code-quality check and receives specific, actionable feedback about Compose best-practice violations — such as incorrect composable naming, missing default parameter values, or improper modifier usage — before opening a pull request.

**Why this priority**: Catching Compose pitfalls early prevents common bugs and anti-patterns from reaching code review or production, directly improving codebase maintainability.

**Independent Test**: Introduce a deliberately malformed Composable (e.g., a composable function not following naming conventions) into any UI module, run the quality check command, and verify that at least one Compose-specific violation appears in the generated report.

**Acceptance Scenarios**:

1. **Given** the codebase contains a Composable with a naming violation, **When** the developer runs the quality check, **Then** the report includes a Compose-specific violation message referencing the offending file and line number.
2. **Given** all Composables conform to the rule set, **When** the developer runs the quality check, **Then** no Compose-specific violations are reported.
3. **Given** the developer fixes a reported violation and reruns the check, **Then** the violation no longer appears in the report.

---

### User Story 2 – Android Lint catches platform-specific issues in CI (Priority: P2)

The CI pipeline automatically runs Android Lint on every build and fails the build when it detects any error-level platform issue, ensuring that only lint-clean code is merged into the main branch.

**Why this priority**: Platform-specific issues (e.g., hard-coded string directions, missing accessibility attributes) cannot be caught by Detekt alone; Lint provides the Android-platform layer of quality enforcement.

**Independent Test**: Introduce a known lint error (e.g., a hard-coded LTR direction string) into an Android module, trigger a CI run, and confirm that the pipeline aborts with a non-zero exit code and a lint error report.

**Acceptance Scenarios**:

1. **Given** an Android module contains a lint error, **When** CI runs, **Then** the build fails and the lint report identifies the violating file and issue ID.
2. **Given** all Android modules are lint-clean, **When** CI runs, **Then** the lint step completes successfully and the overall build passes.
3. **Given** a lint check is intentionally suppressed for a documented reason, **When** CI runs, **Then** the suppressed check does not cause a build failure.

---

### User Story 3 – Developers can run both checks locally and understand how to suppress warnings (Priority: P3)

A developer can run both the Compose Detekt check and Android Lint locally with a single command, view HTML reports, and consult project documentation to learn how to suppress a false positive.

**Why this priority**: Developer experience matters; if local results differ from CI or there is no guidance on suppressions, developers lose trust in the tooling.

**Independent Test**: Follow the documentation instructions step-by-step to suppress one Lint warning using the documented method; confirm the suppressed warning no longer appears in the local report.

**Acceptance Scenarios**:

1. **Given** the documentation instructions, **When** a developer follows them to run the checks locally, **Then** they produce HTML and XML reports identical in structure to CI reports.
2. **Given** a false positive Lint warning, **When** the developer applies the suppression method documented in `docs/quality.md`, **Then** the warning is suppressed in subsequent runs without affecting other checks.

---

### Edge Cases

- What happens when a pure-Kotlin (non-Android) module or a non-Compose Android module is present? The Compose rule plugin MUST NOT be applied to those modules; it is scoped to modules that apply the Compose compiler plugin only.
- How does the CI behave when the lint report file exists but contains zero issues — does it still pass?
- What is the expected behaviour when generated code (from annotation processors) triggers a Compose or Lint rule? Generated code must be excluded from enforcement.
- What happens if a lint check is disabled project-wide but re-enabled in a single module — does the most specific config win?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The project build system MUST declare the Compose-specific static analysis rule plugin as a quality-check dependency applied **exclusively to modules that already apply the Compose compiler plugin** (i.e., UI and feature modules); it MUST NOT be applied to pure-Kotlin or non-Compose Android modules.
- **FR-002**: The central quality-check configuration file MUST be extended to activate **all** Compose rule set rules at **error** severity (zero-tolerance), while preserving all existing complexity rules.
- **FR-003**: The **shared Android convention plugin** in `build-logic/` MUST include a lint block that sets build-failure-on-error mode, enables HTML and XML report generation, disables known noisy Compose-unrelated checks (typography), and enables RTL-hardcoding checks — applied uniformly to all Android modules without per-module duplication.
- **FR-004**: The CI script (`tools/local-ci.ps1`) MUST invoke **`lintRelease`** after the existing quality-check task, and MUST fail with a non-zero exit code when any lint error is reported. The `release` variant is required to validate against production build configuration.
- **FR-005**: The project documentation (`docs/quality.md`) MUST include a section describing the Compose rule set, how to run checks locally, and how to apply inline or config-file suppressions for false positives.
- **FR-006**: Generated source code MUST be excluded from both the Compose rule checks and Android Lint enforcement.
- **FR-007**: Any Compose rule violation that exists in the codebase at the point of integration MUST be suppressed with a targeted `@Suppress` annotation on the affected declaration, accompanied by a `// TODO: resolve Compose rule violation` comment to maintain a visible, trackable backlog. Broad file-level or module-level suppressions are prohibited.

### Key Entities

- **Quality Rule Plugin**: The Compose-specific static analysis rule set applied as a plugin to relevant modules; version-pinned in the version catalog.
- **Quality Configuration File**: The central YAML file governing all static analysis rules; extended without removing existing rules.
- **Lint Configuration Block**: Lint behaviour, report paths, and error thresholds defined **once** in the shared Android convention plugin (`build-logic/`), automatically applied to all Android modules.
- **CI Script**: The PowerShell script orchestrating all quality gates; extended to include the lint step.
- **Quality Documentation**: The markdown file in `docs/` describing all quality tools, their rules, and developer guidance.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: When a Compose violation is intentionally introduced, 100% of affected quality-check runs report that violation **at error severity** and fail the build — zero false-negatives on known violations.
- **SC-002**: When Android Lint detects an error-level issue, 100% of CI builds fail — zero builds merge with unacknowledged lint errors.
- **SC-003**: Developers can produce a local quality report (both Detekt and Lint) in a single command invocation; the command completes in under 5 minutes on a warmed-up build cache.
- **SC-004**: The project documentation update is reviewed and approved by at least one team member as part of the feature pull request.
- **SC-005**: Zero regressions in existing quality checks — all currently-passing Detekt rules continue to pass after the integration.

## Assumptions

- The project already targets Android Gradle Plugin 9, which provides the `lint {}` DSL used in the lint configuration block.
- The CI environment has network access to fetch the Compose rule plugin from its public repository during build.
- Convention plugins or a shared build-logic module are available to centralise the lint block, avoiding duplication across modules.
- Kotlin and the Compose compiler version already in use are compatible with the latest stable Compose rule plugin release.
- Any pre-existing Compose rule violations found at integration time will be suppressed individually (per FR-007) rather than via a global baseline or rule-severity demotion, preserving zero-tolerance from day 1.
