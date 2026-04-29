# Tasks: Detekt Rules Upgrade and Enforcement

**Feature**: `detekt-rules-upgrade` | **Plan**: [plan.md](plan.md)

## Implementation Strategy
We will follow an incremental approach: first establishing the new ruleset, then exposing the hidden technical debt by removing suppressions, and finally remediating the violations module-by-module. This ensures that the codebase remains in a valid state throughout the process.

## Phase 1: Setup & Baseline
Goal: Ensure the environment is ready and establish a quality baseline.

- [x] T001 Verify current Detekt baseline and CI pipeline state by running `./tools/local-ci.ps1`
- [x] T002 Create a backup of `config/detekt/detekt.yml` before modification

## Phase 2: Foundational Configuration
Goal: Apply the expert-level rules and thresholds.

- [x] T003 Update `complexity` ruleset in `config/detekt/detekt.yml` (CyclomaticComplexMethod, LongMethod, LongParameterList, TooManyFunctions, LargeClass)
- [x] T004 Update `coroutines` ruleset in `config/detekt/detekt.yml` (GlobalCoroutineUsage)
- [x] T005 Update `exceptions` ruleset in `config/detekt/detekt.yml` (SwallowedException)
- [x] T006 Update `style` ruleset in `config/detekt/detekt.yml` (UnnecessaryAbstractClass, MagicNumber, WildcardImport)
- [x] T007 Update `performance` ruleset in `config/detekt/detekt.yml` (SpreadOperator)

## Phase 3: [US1] Expert Rules Verification
Goal: Confirm the new rules are correctly identifying violations.
*Story Goal: Upgrade the project's static analysis configuration to use expert-level thresholds and rules.*
*Independent Test: Run Detekt and verify it reports violations based on the new thresholds (e.g., methods > 40 lines).*

- [x] T008 [US1] Run `./gradlew detekt` and audit the report to ensure the new thresholds are active
- [x] T009 [US1] Verify that `MagicNumber` rule correctly identifies literals outside the ignore list (-1, 0, 1, 2)

## Phase 4: [US2] Suppression Removal
Goal: Expose all hidden technical debt for the target rules.
*Story Goal: Remove all existing @Suppress annotations for the upgraded rules.*
*Independent Test: Search the codebase for @Suppress for target rules; verify zero matches remain.*

- [x] T010 [P] [US2] Remove `@Suppress("MagicNumber")` annotations project-wide in all `.kt` files
- [x] T011 [P] [US2] Remove `@Suppress("LongMethod")` and `@Suppress("CyclomaticComplexMethod")` annotations project-wide
- [x] T012 [P] [US2] Remove `@Suppress("LongParameterList")`, `@Suppress("TooManyFunctions")`, and `@Suppress("LargeClass")` annotations project-wide
- [x] T013 [P] [US2] Remove suppressions for `GlobalCoroutineUsage`, `SwallowedException`, and `SpreadOperator` project-wide
- [x] T014 [US2] Handle nested suppressions (e.g., `@Suppress("MagicNumber", "Other")`) by removing only the target rule

## Phase 5: [US3] Quality Remediation
Goal: Resolve all newly exposed violations without altering business logic.
*Story Goal: Exposed quality violations are fixed automatically or semi-automatically.*
*Independent Test: Run local-ci.ps1 and ensure it passes after all fixes are applied.*

- [x] T015 [US3] Remediate `MagicNumber` violations by extracting literals to constants in companion objects or top-level scopes
- [x] T016 [US3] Remediate `LongMethod` violations by decomposing into private helper functions (Refactoring Priority)
- [x] T017 [US3] Remediate `CyclomaticComplexMethod` violations by simplifying conditional logic
- [x] T018 [US3] Remediate `SwallowedException` violations by adding Kermit logging (Principle III) or rethrowing with context; avoid empty catch blocks.
- [x] T019 [US3] Remediate remaining violations (TooManyFunctions, LargeClass, WildcardImport, SpreadOperator)

## Phase 6: Polish & Cross-Cutting Concerns
Goal: Ensure project-wide consistency and CI compliance.

- [x] T020 Run full `./tools/local-ci.ps1` and ensure 100% compliance across all modules
- [x] T021 Update `CHANGELOG.md` or project documentation to reflect the upgraded quality standards

## Dependencies
US1 (Rules) → US2 (Suppression Removal) → US3 (Remediation)

## Parallel Execution Examples
- T010, T011, T012, T013 can be executed in parallel (different rule suppressions).
- Remediation (T015-T019) can be performed module-by-module if needed.
