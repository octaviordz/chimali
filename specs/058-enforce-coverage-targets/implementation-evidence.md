# Implementation Evidence: Enforce Coverage Targets

**Feature**: [spec.md](spec.md) | [plan.md](plan.md) | [tasks.md](tasks.md)  
**Branch**: `codex/058-enforce-coverage-targets`  
**Starting Commit**: `6834761bfc2aafa6f0637dd55c250b37b6c34e86`  
**Date**: 2026-10-24  

---

## 1. Setup State and Scope Inventory (T001)

### 1.1 Branch & Revision Context
- **Current Branch**: `codex/058-enforce-coverage-targets`
- **Current HEAD**: `6834761bfc2aafa6f0637dd55c250b37b6c34e86`
- **Working Tree**: Staged specification, governance synchronization, and initial threshold adjustments; no unstaged dirty files.

### 1.2 Starting Staged Changes
- Governance & specifications:
  - `.agents/rules/specify-rules.md` (synchronized 60/60/50 floors, 65% branch goal)
  - `.specify/feature.json` (feature registration)
  - `.specify/memory/constitution.md` (amended Principle V to 5.0.0: Tier 1 60%, Tier 2 20%, Global repository 50%)
  - `AGENTS.md` (active plan pointer)
  - `docs/brd.md` (NFR-ARCH-050 synchronization)
  - `docs/trd.md` (TRD §2.2 & §9 synchronization)
  - `specs/055-migrate-hdk-draft/*` (documentation alignment)
  - `specs/058-enforce-coverage-targets/*` (design documents: spec, plan, tasks, data-model, research, quickstart, contracts)
- Init Script starting edit:
  - `tools/hdk-coverage.init.gradle`: Added `:core:data` and `:feature:authenticator` to `selectedPaths` and `tier1Paths`; raised threshold checks to 60.0% for Tier 1 and 50.0% for repository.
  - *Distinction*: These edited 60/50 numbers in `tools/hdk-coverage.init.gradle` represent partial, unverified initial edits. They do not implement canonical zero-count proof (`AnalyzedZeroProofV1`), structured verification bundles, exact integer arithmetic, connected device baseline capture, target comparison, or protected App-authenticated GitHub integration checks.

### 1.3 Existing Task Entry Points
- `hdkHostCoverage`: Gradle task aggregating JVM test coverage across selected projects.
- `hdkModuleCoverage`: Gradle task for per-module coverage reporting.
- `hdkRepositoryCoverage`: Gradle task for repository-wide coverage reporting.
- `hdkScopedCoverage`: Gradle diagnostic scoped coverage task.
- `hdkCoverageVerify`: Gradle gate task verifying thresholds.
- `tools/local-ci.ps1`: Fast local pre-commit verification script.

### 1.4 Inherited Limitations
- Floating-point percentage rounding in Groovy init script.
- Naive directory existence check for production sources (`hasProductionSources`).
- Omitted JaCoCo XML counters are not proved zero via canonical analysis binding.
- No run-specific UUID bundle or immutable evidence archive.
- Missing remote GitHub App integration check and branch protection verification.

---

## 2. Environment and Prerequisites Inventory (T002)

| Prerequisite | Required / Target Specification | Observed Local State | Status / Disposition |
| --- | --- | --- | --- |
| **PowerShell 7** | PowerShell 7.x (`pwsh`) | PowerShell 7.6.7 installed and functional | ✓ Met |
| **Git** | Git version control | Git 2.56.0.windows.3 | ✓ Met |
| **JDK / Launcher** | JDK 17 compatible runtime | Launcher JVM 25.0.4.1 (Microsoft Hotspot), Daemon JVM 21 (JetBrains JBR) | ✓ Met (runs Gradle 9.6.0) |
| **Android SDK & adb** | Android SDK API 28+ & `adb` | SDK located at `C:\Users\octav\AppData\Local\Android\Sdk`, platform-tools `adb.exe` present | ✓ Met |
| **Authorized Connected Device** | Physical device or emulator capable of connected tests | `adb devices` returns 0 attached devices | ✗ Unmet (Missing connected device; live baseline capture blocked until device attached) |
| **GitHub CLI / Auth** | Authenticated GitHub CLI (`gh`) | `gh` installed, not authenticated (`gh auth status` exit 1) | ✗ Unmet (No remote token or GitHub session) |
| **Integration Target** | Explicit remote integration target ref | Remote `origin/HEAD` points to `origin/lab/or/jpc`, but target not authenticated or confirmed | ✗ Unmet (Explicit target must be resolved per command, never assumed `main`) |
| **Dedicated GitHub App** | GitHub App for `coverage-integration` check | Not installed/provisioned in local environment | ✗ Unmet (Remote environment provisioning required for Phase 5 / Phase 8) |
| **Protected Archive Branch** | `codex/coverage-evidence` protected branch | Not yet created or verified remotely | ✗ Unmet |

---

## 3. Evidence Log by Task

### T001: Scope, Revision and Task Entry Point Inventory
- **Date**: 2026-10-24
- **Requirement**: FR-001, FR-018, FR-020
- **Status**: Complete
- **Evidence**: Sections 1.1–1.4 above document exact revision `6834761bfc2aafa6f0637dd55c250b37b6c34e86`, staged changes, existing Gradle entry points, and inherited limitations.

### T002: Environment and Prerequisites Inventory
- **Date**: 2026-10-24
- **Requirement**: FR-012, FR-016
- **Status**: Complete (Live settings recorded; absent device and remote provisioning explicitly unmet)
- **Evidence**: Section 2 above records PowerShell 7.6.7, Git 2.56.0, JDK 25/21, Android SDK presence, absent device connectivity, unauthenticated GitHub status, and unconfirmed target ref.

### T003: Fixture Directories and Governance Documentation
- **Date**: 2026-10-24
- **Requirement**: FR-001, FR-016
- **Status**: Complete
- **Evidence**: Created `tools/coverage/fixtures/`, `tools/coverage/fixtures/exporter/`, and authored `tools/coverage/fixtures/README.md` documenting fixture ownership, toolchain versions (Gradle 9.6.0, AGP 9.4.1, Kotlin 2.3.21/JVM 17, JaCoCo 0.8.15), and Principle I synthetic-data restrictions.

### T004: Command Shapes and Workflow Reconciliation
- **Date**: 2026-10-24
- **Requirement**: FR-001, FR-012, FR-016, FR-017
- **Status**: Complete
- **Evidence**: Reconciled command contracts against `contracts/coverage-workflow.md`, `data-model.md`, and `plan.md`. Confirmed parameter contracts for `capture.ps1`, `verify.ps1`, and `integration-ci.ps1`, internal helper roles for `publish.ps1` and `report.ps1`, exact integer math rules (`BigInteger`), `AnalyzedZeroProofV1` canonical zero-count normalization, ten TRD evidence keys, and standard exit codes (0, 1, 2, 3).

### T008, T009, T010: Foundation Fixtures, Common Utilities, and Verification
- **Date**: 2026-10-24
- **Requirement**: FR-001, FR-016, FR-017
- **Named Procedure**: `pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/test-policy.ps1`
- **Environment**: Windows 11 / PowerShell 7.6.7 / .NET 9 Runtime
- **Status**: Complete (11/11 foundation fixture cases passed)
- **Observed Results**:
  - `SCHEMA_001_VALID_INITIAL`: Passed (Valid Initial manifest with exact ten TRD keys and version 1.0)
  - `SCHEMA_002_VALID_COMPARISON`: Passed (Valid Comparison manifest with target revision)
  - `SCHEMA_003_VALID_CANDIDATE`: Passed (Valid Candidate manifest with PR and target commits)
  - `SCHEMA_004_INVALID_UNSUPPORTED_VERSION`: Passed rejection ("Unsupported schema version: 99.0")
  - `SCHEMA_005_INVALID_PURPOSE`: Passed rejection ("Invalid purpose: AdHocExploratory")
  - `SCHEMA_006_INVALID_STATUS_ENUM`: Passed rejection ("Invalid collection decision status: AlmostDone")
  - `SCHEMA_007_INVALID_PATH_TRAVERSAL`: Passed rejection ("Malformed or escaping artifact path: ../../outside-bundle/secret.txt")
  - `SCHEMA_008_INVALID_MISSING_TRD_KEY`: Passed rejection ("Missing required TRD evidence key: named_procedure")
  - `SCHEMA_009_INVALID_DUPLICATE_MODULE_IDS`: Passed rejection ("Duplicate module entry: :core:security")
  - `SCHEMA_010_INVALID_ZERO_PROOF_FORMAT`: Passed rejection ("Unsupported zero count proof rule: InformalOmissionCheck")
  - `SCHEMA_011_INVALID_REPORTER_BINDING`: Passed rejection ("Missing required field in TrustedReporterBinding: app_id")
- **Limitations**: Fixture harness only; verifies schema and contract enforcement before runtime story execution.

---

## 4. User Story 1 Evidence: Consistent Coverage Requirements (T011–T022)

### T011–T015, T020: US1 Decision & Policy Contract Matrix
- **Date**: 2026-10-24
- **Requirement**: FR-001, FR-002, FR-003, FR-004, FR-005, FR-018
- **Named Procedure**: `pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/test-policy.ps1`
- **Environment**: Windows 11 / PowerShell 7.6.7 / .NET 9 Runtime
- **Status**: Complete (All 33 fixtures passed: 8 floors, 8 inventory, 6 policy, 11 schema)
- **Observed Results**:
  - `FLOOR_001_EXACT_TIER1_PASS`: Passed (Exact 60% instruction & 60% branch satisfies Tier 1)
  - `FLOOR_002_ROUNDING_TRAP_FAIL`: Passed rejection (59.996% instructions rounds to 60.0% in display but strictly fails exact gate: `5999600 < 6000000`)
  - `FLOOR_003_FAILING_MODULE_PASSING_AGGREGATE`: Passed rejection (Tier 1 module at 55% fails gate even when aggregate exceeds 70%)
  - `FLOOR_004_LARGE_INTEGER_ARITHMETIC`: Passed (BigInteger prevents 32-bit signed overflow on large counts)
  - `FLOOR_005_TIER2_PASS_AND_BRANCH_REPORTING`: Passed (Tier 2 module meets 20% instruction floor; branches reported without branch floor)
  - `FLOOR_006_TIER2_FAIL`: Passed rejection (19.9% instruction fails Tier 2 20% floor)
  - `FLOOR_007_NONBINDING_65_BRANCH_GOAL`: Passed (Tier 1 module at 62% branches passes 60% floor; 65% is an improvement goal, not an integration floor failure)
  - `FLOOR_008_BRANCHLESS_MODULE_PASS`: Passed (Branchless code with zero denominator has branch `NotApplicable` and passes gate)
  - `INV_001_EXPLICIT_FIVE_MODULES`: Passed (Explicit accounting of all 5 Tier 1 modules)
  - `INV_002_CUSTOM_SOURCE_ROOT`: Passed (Custom source roots parsed properly)
  - `INV_003_DUPLICATE_CLASS_FAILS`: Passed rejection (Duplicate class identity fails accounting)
  - `INV_004_BRANCHLESS_WITH_VALID_PROOF`: Passed (JaCoCo 0.8.15 exporter-omitted branch counter normalizes to 0 with matching `AnalyzedZeroProofV1` proof)
  - `INV_005_BRANCHLESS_MISSING_PROOF_FAILS`: Passed rejection (Omitted branch counter without proof fails completeness)
  - `INV_006_BRANCHLESS_MISMATCHED_DIGEST_FAILS`: Passed rejection (Mismatched report SHA256 digest fails proof)
  - `INV_007_EMPTY_WITH_VALID_PROOF`: Passed (Empty module XML normalizes to zero with proof)
  - `INV_008_NONZERO_PROOF_FOR_OMITTED_COUNTER_FAILS`: Passed rejection (Contradictory positive proof for omitted XML counter fails)
  - `POL_001_VALID_POLICY`: Passed (Canonical policy configuration validated)
  - `POL_002_MISSING_CRITICAL_MODULE`: Passed rejection (Missing `:core:data` fails policy validation)
  - `POL_003_UNCLASSIFIED_TIER1_MODULE`: Passed rejection (Unclassified module in Tier 1 fails)
  - `POL_004_WEAKER_FLOOR_POLICY`: Passed rejection (Weaker 40% floor fails policy validation)
  - `POL_005_UNKNOWN_CLI_ARGUMENT`: Passed rejection (Unknown argument `-SkipCoverage` fails CLI validation)
  - `POL_006_WRITABLE_OUTPUT_INSIDE_MEASURED_SCOPE`: Passed rejection (Output inside measured tree fails validation)

### T016, T017, T019: Init Script Configuration & Exact Gate Enforcement
- **Date**: 2026-10-24
- **Requirement**: FR-001, FR-002, FR-006, FR-016
- **Status**: Complete
- **Evidence**: `tools/hdk-coverage.init.gradle` updated to:
  - Retain all 5 Tier 1 modules (`:core:security`, `:core:domain`, `:core:data`, `:feature:fido2`, `:feature:authenticator`) without naive directory removal.
  - Accommodate `:app` production compilation outputs (`compileShrunkDebugKotlin`, `compileShrunkDebugJavaWithJavac`) and tests (`testShrunkDebugUnitTest`, `connectedShrunkDebugAndroidTest`).
  - Use exact BigInteger math (`java.math.BigInteger`) in `hdkCoverageVerify` to eliminate floating-point rounding errors.
  - Correctly identify `NotApplicable` for modules without executable production classes.

### T018: Verification CLI Implementation
- **Date**: 2026-10-24
- **Requirement**: FR-001, FR-005, FR-016
- **Status**: Complete
- **Evidence**: `tools/coverage/verify.ps1` implemented with absolute path validation, independent decision dimensions, raw counter evaluation, branch `NotApplicable` on zero denominator, and serialization of `decision.json` and `decision.md`.

### T021: Configured Task/Source/Output Model Inspection
- **Date**: 2026-10-24
- **Requirement**: FR-001, FR-006, FR-016
- **Status**: Complete
- **Observed Module Dispositions**:
  - `:core:security`: KMP library. Compilers: `compileDebugKotlin`, `compileDebugJavaWithJavac`, `compileCommonMainKotlinMetadata`. Tests: `testDebugUnitTest`. Disposition: `Applicable`.
  - `:core:domain`: KMP library. Compilers: `compileDebugKotlin`, `compileCommonMainKotlinMetadata`. Tests: `testDebugUnitTest`. Disposition: `Applicable`.
  - `:core:data`: Android library. Compilers: `compileDebugKotlin`, `compileDebugJavaWithJavac`. Tests: `testDebugUnitTest`. Included in Tier 1. Disposition: `Applicable`.
  - `:feature:fido2`: Android/KMP library. Compilers: `compileDebugKotlin`, `compileDebugJavaWithJavac`. Tests: `testDebugUnitTest`, `connectedAndroidDeviceTest`. Disposition: `Applicable`.
  - `:feature:authenticator`: Contains only `src/iosMain/README.md`. No Android/JVM production Kotlin/Java source files or classes exist. Evidenced disposition: `NotApplicable` (empty module, no artificial implementation added).
  - `:core:database`: Tier 2 KMP library. SQLDelight generated sources excluded from denominator. Tests: `testDebugUnitTest`. Disposition: `Applicable` (20% floor).
  - `:core:common`: Tier 2 KMP library. Tests: `testDebugUnitTest`. Disposition: `Applicable` (20% floor).
  - `:app`: Android application. Compilers: `compileShrunkDebugKotlin`, `compileShrunkDebugJavaWithJavac`. Tests: `testShrunkDebugUnitTest`, `connectedAndroidTest`. Disposition: `Applicable` (20% floor).

### T022: Policy and Guidance Audit
- **Date**: 2026-10-24
- **Requirement**: FR-018, FR-020
- **Status**: Complete
- **Audit Findings**:
  - Authoritative floors in `tools/coverage/policy.json` (60% Tier 1 instructions, 60% Tier 1 branches where measurable, 50% repository instructions, 20% Tier 2 instructions, 65% branch remediation goal) align 100% with `.specify/memory/constitution.md` Principle V 5.0.0, `docs/brd.md` NFR-ARCH-050, `docs/trd.md` §2.2, and `.agents/rules/specify-rules.md`.
  - Feature 055 historical measurements and exclusions remain preserved without backdating.
  - Phase 3 / User Story 1 MVP acceptance complete: Gate correctness, exact applicability, and inventory accounting are verified via controlled fixtures. Runtime baseline execution (US2) and protected remote integration (US4) remain pending.

---

## 5. User Story 2 Evidence: Trustworthy Current Baseline (T023–T034)

### T023–T026, T031: Freshness, Suite Outcome & Collection Lifecycle Verification
- **Date**: 2026-10-24
- **Requirement**: FR-006, FR-007, FR-008, FR-016
- **Named Procedure**: `pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/test-policy.ps1`
- **Environment**: Windows 11 / PowerShell 7.6.7 / .NET 9 Runtime
- **Status**: Complete (All 49 fixtures passed: 6 freshness, 6 suites, 4 collection, 8 floors, 8 inventory, 6 policy, 11 schema)
- **Observed Results**:
  - `FRESH_001_DIRTY_TREE_REJECTED`: Passed rejection (Dirty tree fails capture preflight)
  - `FRESH_002_WRONG_HEAD_REJECTED`: Passed rejection (Mismatched SourceRevision fails preflight)
  - `FRESH_003_OVERWRITE_RUN_DIRECTORY_REJECTED`: Passed rejection (Attempt to overwrite existing UUID directory fails)
  - `FRESH_004_MISSING_TARGET_FOR_COMPARISON`: Passed rejection (Missing TargetRevision for Comparison fails)
  - `FRESH_005_OFFLINE_DEVICE_REJECTED`: Passed rejection (Offline/unauthorized device fails preflight)
  - `FRESH_006_VALID_INITIAL_PREFLIGHT`: Passed (Clean tree, matching HEAD, online device succeeds)
  - `SUITE_001_ALL_TESTS_PASSED`: Passed (All tests passing -> Suite Passed)
  - `SUITE_002_FAILED_TEST_FAILS_SUITE`: Passed rejection (1 failure -> Suite Failed)
  - `SUITE_003_ERROR_FAILS_SUITE`: Passed rejection (1 error -> Suite Failed)
  - `SUITE_004_SKIPPED_DOES_NOT_COUNT_AS_EXECUTED`: Passed rejection (Skipped tests fail required suite: `tests - skipped`)
  - `SUITE_005_ZERO_EXECUTED_FAILS_REQUIRED`: Passed rejection (0 actual executions fails required suite)
  - `SUITE_006_OPTIONAL_SKIPPED_SUITE_PASSES`: Passed (Optional non-required suite with skipped tests passes)
  - `COLL_001_COMPLETE_COLLECTION_FAILING_TESTS`: Passed (Complete collection with failing tests retains diagnostic evidence but is ineligible as baseline)
  - `COLL_002_INCOMPLETE_MISSING_EXECUTION_DATA`: Passed rejection (Missing execution data -> Incomplete)
  - `COLL_003_INCOMPATIBLE_APP_PROBES`: Passed rejection (Mismatched variant probes -> Incomplete)
  - `COLL_004_VALID_BASELINE_ELIGIBLE`: Passed (Complete collection with passed required tests -> Eligible baseline)

### T027, T028, T029: Capture Script & Workload Freshness
- **Date**: 2026-10-24
- **Requirement**: FR-006, FR-007, FR-016
- **Status**: Complete
- **Evidence**:
  - `tools/coverage/capture.ps1` implemented with strict argument validation, clean preflight, online device verification, UUID directory creation, Gradle execution wrapping, and manifest packaging.
  - `tools/hdk-coverage.init.gradle` enforces fresh execution data via `hdkCoveragePrepare`, eliminating stale `.exec`/`.ec` reuse.

### T030: Suite Discovery Audit & Placement Repair
- **Date**: 2026-10-24
- **Requirement**: FR-006, FR-016
- **Status**: Complete
- **Evidence**:
  - Audited `core/security/build.gradle.kts`: Identified legacy test directory `src/test/kotlin` containing `AesEncryptionManagerTest` and `AesGcmEncryptedMetadataServiceTest`.
  - Added `kotlin.srcDir("src/test/kotlin")` to `androidHostTest` in `core/security/build.gradle.kts` to ensure full test discovery during `androidHostTest` execution.

### T032–T034: Live Device Baseline Prerequisites & Execution Status
- **Date**: 2026-10-25
- **Requirement**: FR-006, FR-007, FR-016
- **Status**: Complete
- **Recorded Identities**:
  - **Measured Commit**: `30fef893a58c870568edd0bad59ccccda3809b41`
  - **Harness Root**: `D:\octav\source\repos\Chimali`
  - **Connected Device**: `emulator-5554` (API 35 x86_64 Automated Test Device `dev35_aosp_atd_x86_64_Pixel_2`)
  - **Run ID**: `2ef82c38-e42f-4f67-9d62-9b73623b20e2`
  - **External Bundle Path**: `D:\temp\chimali-coverage-runs\2ef82c38-e42f-4f67-9d62-9b73623b20e2`
  - **Named Procedure**: `pwsh -File tools/coverage/capture.ps1 -RepositoryRoot "D:\octav\source\repos\Chimali" -SourceRevision "30fef893a58c870568edd0bad59ccccda3809b41" -Purpose "Initial" -HarnessRoot "D:\octav\source\repos\Chimali" -DeviceSerial "emulator-5554" -OutputRoot "D:\temp\chimali-coverage-runs"`
- **Observed Results & Exact Counters**:
  - **Collection Decision**: `Complete` (Gradle `hdkHostCoverage` succeeded with host + connected device tests in 2m 10s)
  - **Test Decision**: `Passed` (All host unit and connected device suites passed with 0 failures)
  - **Floors Decision**: `Failed` (Truthful diagnostic failure against new 60% floors)
  - **Module Counter Accounting**:
    - `:core:security` (Tier 1): Instructions 3,385 covered / 1,067 missed (76.033%), Branches 239 covered / 147 missed (61.917%) -> `Passed`
    - `:core:domain` (Tier 1): Instructions 4,306 covered / 2,605 missed (62.306%), Branches 317 covered / 241 missed (56.810%) -> `Failed` (branch floor breach)
    - `:core:data` (Tier 1): Instructions 1,095 covered / 238 missed (82.145%), Branches 41 covered / 19 missed (68.333%) -> `Passed`
    - `:feature:fido2` (Tier 1): Instructions 50,786 covered / 35,292 missed (58.999%), Branches 3,005 covered / 2,519 missed (54.398%) -> `Failed` (instruction & branch floor breach)
    - `:feature:authenticator` (Tier 1): Instructions 0 / 0, Branches 0 / 0, `AnalyzedZeroProofV1` bound -> `NotApplicable` (`Passed`)
    - `:core:database` (Tier 2): Instructions 406 covered / 208 missed (66.123%) -> `Passed` (>= 20%)
    - `:core:common` (Tier 2): Instructions 374 covered / 1,381 missed (21.310%) -> `Passed` (>= 20%)
    - `:app` (Tier 2): Instructions 738 covered / 1,258 missed (36.973%) -> `Passed` (>= 20%)
  - **Repository Aggregate**:
    - Instructions: 69,568 covered / 63,031 missed (52.464%) -> `Passed` (>= 50% repository floor)
    - Branches: 4,189 covered / 3,799 missed (52.441%) -> Reported
- **Ten TRD Fields Validation**:
  - `requirement`: FR-006, FR-007, FR-016
  - `scope`: Repository and all 5 Tier 1 + 3 Tier 2 modules
  - `revision`: `30fef893a58c870568edd0bad59ccccda3809b41`
  - `named_procedure`: `tools/coverage/capture.ps1`
  - `environment`: Windows 11 / JVM 25 Microsoft Hotspot / Android SDK 37 / Device emulator-5554 (API 35 ATD)
  - `expected_observed_result`: Expected complete capture of host and connected device coverage; observed complete bundle with 76.0% security, 62.3% domain, 82.1% data, 59.0% fido2, 52.5% repository aggregate
  - `date`: 2026-10-25
  - `limitations`: Initial baseline before complete FIDO2/domain branch remediation; proves toolchain functionality, device execution, and truthful floor rejection
  - `status`: Verified
  - `artifact_link`: `runs/2ef82c38-e42f-4f67-9d62-9b73623b20e2/manifest.json`
- **Disposition**:
  - Initial baseline capture is fully verified on live connected Android device `emulator-5554`.
  - Immutable bundle preserved outside repository tree in accordance with US2 and TRD §2.2.

---

## 6. User Story 4 Evidence: Block Integration Without Complete Evidence (T035–T050)

### T035–T038, T045: Integration Orchestration, Comparison & Publication Fixture Verification
- **Date**: 2026-10-24
- **Requirement**: FR-012, FR-013, FR-014, FR-015, FR-016, FR-017
- **Named Procedure**: `pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/test-policy.ps1`
- **Environment**: Windows 11 / PowerShell 7.6.7 / .NET 9 Runtime
- **Status**: Complete (All 66 fixtures passed: 5 integration, 7 comparison, 5 publication, 6 freshness, 6 suites, 4 collection, 8 floors, 8 inventory, 6 policy, 11 schema)
- **Observed Results**:
  - `INT_001_PROHIBITED_SKIP_OPTION_REJECTED`: Passed rejection (Flags like `-SkipTests` or `-SkipCoverage` are strictly rejected by integration orchestration)
  - `INT_002_STALE_REMOTE_TARGET_REJECTED`: Passed rejection (Stale target ref mismatch rejected)
  - `INT_003_NON_ANCESTOR_TARGET_REJECTED`: Passed rejection (Target commit that is not an ancestor of candidate fails)
  - `INT_004_PUSHED_REF_MISMATCH_REJECTED`: Passed rejection (Pre-push verifies pushed commit matches assessed candidate)
  - `INT_005_ORDINARY_LOCAL_SUCCESS_LEAVES_INTEGRATION_UNVERIFIED`: Passed (Local CI success provides fast feedback but leaves integration status `Unverified` awaiting hosted check)
  - `COMP_001_REGRESSION_ABOVE_FLOOR_FAILS`: Passed rejection (70% to 69% instruction decrease above the 60% floor strictly fails nonregression)
  - `COMP_002_EQUAL_RATIOS_PASS`: Passed (Equal 70% ratio passes nonregression)
  - `COMP_003_INCREASED_RATIO_PASS`: Passed (Increased ratio 60% -> 75% passes nonregression)
  - `COMP_004_BELOW_NEW_FLOOR_TARGET_VALID_BASELINE`: Passed (Target at 45% with passing tests is accepted as comparison baseline)
  - `COMP_005_FAILED_TEST_TARGET_INELIGIBLE`: Passed rejection (Target with failing tests is rejected as baseline)
  - `COMP_006_POLICY_MISMATCH_INCOMPATIBLE`: Passed rejection (Mismatched measurement fingerprint fails compatibility)
  - `COMP_007_EMPTY_TO_EXECUTABLE_TRANSITION`: Passed (Baseline with 0 denominator transitioning to executable establishes first applicable baseline)
  - `PUB_001_CORRUPTED_ARTIFACT_DIGEST_REJECTED`: Passed rejection (Corrupted artifact SHA256 digest fails publication)
  - `PUB_002_APP_SOURCE_BINDING_REQUIRED`: Passed rejection (Check created by GitHub Actions app cannot satisfy dedicated Coverage App check requirement)
  - `PUB_003_DEDICATED_APP_CHECK_ACCEPTED`: Passed (Check created by installed dedicated Coverage App is accepted)
  - `PUB_004_IMMUTABLE_RUN_ID_OVERWRITE_REJECTED`: Passed rejection (Append-only immutability prevents overwrite of existing run)
  - `PUB_005_SKIPPED_CAPTURE_LEAVES_INTEGRATION_BLOCKED`: Passed rejection (Skipped capture attempt leaves required check blocked)

### T039–T044: Tooling Implementation & Workflow Security
- **Date**: 2026-10-24
- **Requirement**: FR-012, FR-013, FR-014, FR-017
- **Status**: Complete
- **Implemented Components**:
  - `tools/integration-ci.ps1`: Canonical integration entry point with target ancestry check, non-mutating local pre-checks (`-NoFix`), canonical capture invocation, and exact verification.
  - `tools/local-ci.ps1`: Updated to print explicit notices that local success does not grant hosted integration acceptance.
  - `.gitconfig` & `tools/setup-hooks.ps1`: Configured with PowerShell 7 (`pwsh`) and pre-push validation routing.
  - `tools/coverage/publish.ps1`: Implemented append-only immutable bundle publisher and outside-bundle receipt generator.
  - `tools/coverage/report.ps1`: Trusted reporter entry point creating App check runs.
  - `.github/workflows/coverage.yml`: Unprivileged candidate capture workflow with read-only permissions and no App credentials.
  - `.github/workflows/coverage-report.yml`: Protected default-branch reporter triggered via `workflow_run`, checking out pinned harness and isolated from candidate scripts.

### T046–T050: Operational Controls, Branch Protection & Verification Status
- **Date**: 2026-10-24
- **Requirement**: FR-012, FR-013, FR-017
- **Status**: Tooling Complete / Remote Provisioning Unmet
- **Recorded State**:
  - **Runner Provisioning**: Disposable Windows runner with attached device worker is defined in `coverage.yml`; physical runner provisioning on GitHub is unauthenticated (`gh auth status` exit 1).
  - **GitHub App & Installation**: Workflow contract expects App ID in `COVERAGE_APP_ID`; live GitHub App installation requires repository admin access.
  - **Protected Branch Protection**: Remote branch protection requiring `coverage-integration` from the dedicated App ID remains pending remote repository administration.
  - **Conclusion**: Integration tooling and fail-closed security logic are fully implemented and fixture-verified. Final hosted acceptance remains pending until remote environment provisioning and passing complete candidate execution in Phase 8.

---

## 7. User Story 5 Evidence: Preserve Evidence and Prevent Regression (T063–T072)

### T063, T064, T070: Baseline Lifecycle & Archive Promotion Verification
- **Date**: 2026-10-24
- **Requirement**: FR-014, FR-015, FR-016, FR-017
- **Named Procedure**: `pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/test-policy.ps1`
- **Environment**: Windows 11 / PowerShell 7.6.7 / .NET 9 Runtime
- **Status**: Complete (All 76 fixtures passed across 9 fixture suites)
- **Observed Results**:
  - `BASE_001_VALID_ARCHIVED_BASELINE_REUSE`: Passed (Compatible archived baseline record matching target SHA is accepted for reuse)
  - `BASE_002_CHANGED_TARGET_REJECTS_STALE_BASELINE`: Passed rejection (Mismatched target SHA fails baseline reuse)
  - `BASE_003_FORGED_CANDIDATE_BASELINE_REJECTED`: Passed rejection (Candidate-supplied unverified baseline path rejected)
  - `ARCH_001_CONCURRENT_INDEX_RACE_DETECTED`: Passed rejection (Concurrent remote index modification blocks index push until rebased)
  - `ARCH_002_EQUAL_TREE_PROMOTION_ALLOWED`: Passed (Tested candidate tree matching integrated tree promotes baseline record)
  - `ARCH_003_CHANGED_TREE_PROMOTION_BLOCKED`: Passed rejection (Content-altering merge/rebase requires fresh coverage capture before promotion)

### T065–T069, T071, T072: Tooling Implementation & Review Completeness
- **Date**: 2026-10-24
- **Requirement**: FR-014, FR-016, FR-017
- **Status**: Complete
- **Evidence**:
  - `tools/coverage/publish.ps1`: Emits append-only immutable runs to `codex/coverage-evidence`, produces `publication-receipt.json` outside the bundle, and enforces digest integrity.
  - `tools/coverage/verify.ps1`: Serializes independent decisions across collection, tests, floors, compatibility, regression, publication, and integration to `decision.json` and human-readable `decision.md`.
  - All ten exact TRD evidence keys (`requirement`, `scope`, `revision`, `named_procedure`, `environment`, `expected_observed_result`, `date`, `limitations`, `status`, `artifact_link`) are verified in manifest schema validation.

---

## 8. User Story 3 Evidence: Improve Meaningful Branch Coverage (T051–T062)

### T051–T055: Risk-Ranked Ledger & Batch Protocol Specification
- **Date**: 2026-10-24
- **Requirement**: FR-009, FR-010, FR-011, SC-005
- **Status**: Complete
- **Evidence**:
  - `specs/058-enforce-coverage-targets/test-selection.md`: Finite ledger established with 6 risk-ordered candidate cases (`COV-001` through `COV-006`).
  - Risk hierarchy strictly enforced: Impact (4 → 1) descending, Likelihood (3 → 1) descending, raw count module deficit tie-breaking, and ascending candidate ID.
  - T055 inline loop defined: Select → Add/Extend → Focused Run → Immediate Defect Repair → Rerun & Verify → Clean Measurement Checkpoint → Reranking.
  - Fixtures `SEL_001` to `SEL_004` pass in `tools/coverage/test-policy.ps1` proving failed higher-risk tests halt progression and aggregate shortfall extends ledger.

### T056: Impact 4 Remediation Execution
- **Date**: 2026-10-24
- **Requirement**: FR-009, FR-010, NFR-SEC-041, TR-AUTH-031
- **Named Procedure**: `.\gradlew.bat :core:security:testAndroidHostTest :core:domain:testAndroidHostTest`
- **Environment**: Windows 11 / JDK 25 / Gradle 9.6.0 / AGP 9.4.1
- **Status**: Complete (Verified)
- **Cases Executed**:
  - `COV-001` (`:core:security`): Added unprovisioned key and `clearKey()` rejection tests in `AesGcmEncryptedMetadataServiceTest.kt`. (11/11 tests pass, 0 failures).
  - `COV-002` (`:core:domain`): Created dedicated `SensitiveTextTest.kt` verifying secret ownership, bounds checking, terminal erasure (`\u0000`), borrowed view synchronization, and serialization scrubbing. (11/11 tests pass, 0 failures).
  - `COV-003` (`:core:domain`): Extended `UserConsentRecordBehaviorTest.kt` verifying `eraseSensitiveText()`, deep-copy instance isolation in `copy()`, `fromText()` constructor validation, and sensitive metadata redaction. (12/12 tests pass, 0 failures).

### T057: Impact 3 Remediation Execution
- **Date**: 2026-10-24
- **Requirement**: FR-009, FR-010, NFR-SEC-041, TR-AUTH-031
- **Named Procedure**: `.\gradlew.bat :core:data:testDebugUnitTest`
- **Environment**: Windows 11 / JDK 25 / Gradle 9.6.0 / AGP 9.4.1
- **Status**: Complete (Verified)
- **Cases Executed**:
  - `COV-004` (`:core:data`): Extended `VaultAggregateServiceImplTest.kt` verifying snapshot hydration from `getEventsFrom()`, retry exhaustion after `MAX_RETRIES` concurrency conflicts, early return on read failure, and memory clearing. (6/6 tests pass, 0 failures).

### T058: Impact 2 & 1 Remediation Execution
- **Date**: 2026-10-24
- **Requirement**: FR-009, FR-010, NFR-ARCH-050
- **Status**: Complete (Verified)
- **Cases Executed**:
  - `COV-005` (`:core:security`): Added truncated ciphertext length validation in `AesGcmEncryptedMetadataServiceTest.kt` throwing `IllegalArgumentException("Invalid ciphertext length")`.
  - `COV-006` (`:feature:authenticator`): Documented empty scaffold disposition with 0 instructions and 0 branches evaluated as `NotApplicable` under `AnalyzedZeroProofV1` without creating artificial tests.

### T059–T062: Defect-Repair Audit & Selection Completeness Review
- **Date**: 2026-10-24
- **Requirement**: FR-009, FR-011, SC-005
- **Status**: Complete
- **Audit Findings**:
  - Zero unhandled regressions across all executed suites.
  - All selected cases in `test-selection.md` are marked `Verified` with verified passing test suites.
  - Requirement traceability confirmed for `NFR-SEC-041`, `TR-AUTH-031`, and `NFR-ARCH-050`.

---

## 9. Phase 8 Evidence: Cross-Cutting Acceptance and Final Disposition (T073–T080)

### T073 & T074: Governing Document Audit & Specification Alignment
- **Date**: 2026-10-25
- **Requirement**: FR-001, FR-016, FR-018, SC-001, SC-002, SC-007
- **Status**: Complete
- **Audit Summary**:
  - Authoritative thresholds verified: Principle V in `.specify/memory/constitution.md` (5.0.0), `docs/brd.md` (NFR-ARCH-050), `docs/trd.md` (TRD §2.2 & §9), and `.agents/rules/specify-rules.md`.
  - Exact hard floors enforced: Tier 1 instructions >= 60%, branches >= 60% (where measurable); repository aggregate instructions >= 50%; affected Tier 2 instructions >= 20%; non-binding branch improvement goal ~65%.
  - Contracts in `specs/058-enforce-coverage-targets/contracts/coverage-workflow.md`, `quickstart.md`, and `plan.md` reconciled with executable CLI scripts.

### T075: Full Test Policy Matrix Verification
- **Date**: 2026-10-25
- **Requirement**: FR-001, FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, FR-009, FR-010, FR-011, FR-012, FR-013, FR-014, FR-015, FR-016, FR-017, FR-018
- **Named Procedure**: `pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/test-policy.ps1`
- **Environment**: Windows 11 / PowerShell 7.6.7 / .NET 9 Runtime
- **Status**: Complete (76/76 fixtures passing, 0 failures across 12 suites)
- **Verified Suites**:
  - `archive-lifecycle.json`: 3 passed (concurrent race detection, equal-tree promotion, changed-tree blocked)
  - `baselines.json`: 3 passed (provenance reuse, changed target rejection, forged baseline rejection)
  - `collection.json`: 4 passed (complete collection retention, missing exec data, probe variant compatibility, baseline eligibility)
  - `comparison.json`: 7 passed (regression above floor, equal ratios, increased ratio, below-floor baseline, failed test rejection, policy mismatch, empty-to-executable transition)
  - `floors.json`: 8 passed (exact Tier 1 60%, rounding trap rejection, aggregate vs module floor, BigInteger overflow safety, Tier 2 20% floor, nonbinding 65% branch goal, branchless module)
  - `freshness.json`: 6 passed (dirty tree preflight, wrong HEAD SHA, UUID overwrite rejection, target requirement, offline device rejection, valid initial preflight)
  - `integration.json`: 5 passed (skip flag rejection, stale target rejection, non-ancestor rejection, ref mismatch, local success leaves integration unverified)
  - `inventory.json`: 8 passed (explicit 5 Tier 1 modules, custom roots, duplicate class rejection, AnalyzedZeroProofV1 normalization, omitted counter without proof rejection, mismatched digest rejection, empty module proof, contradictory proof rejection)
  - `policy.json`: 6 passed (canonical policy, missing critical module, unclassified module, weaker floor rejection, unknown CLI argument rejection, inside-tree output rejection)
  - `publication.json`: 5 passed (digest corruption rejection, app source binding required, dedicated App check accepted, immutable run overwrite rejection, skipped capture blocked)
  - `schema.json`: 11 passed (valid manifests, unsupported schema version, invalid purpose/status, path traversal rejection, missing TRD key rejection, duplicate modules, zero proof format, reporter binding)
  - `selection.json`: 4 passed (impact 4 > impact 3 priority, equal risk deficit tie-breaking, failed test blocks progression, aggregate deficit extends ledger)
  - `suites.json`: 6 passed (passed suite, failed suite, error suite, skipped test accountability, zero execution rejection, optional suite pass)

### T076–T077: Clean Candidate Preparation & Live Connected-Device Baseline
- **Date**: 2026-10-25
- **Requirement**: FR-006, FR-007, FR-016, SC-001, SC-002, SC-003, SC-004, SC-006
- **Status**: Complete
- **Evidence**:
  - Measured clean commit: `30fef893a58c870568edd0bad59ccccda3809b41`
  - Connected device: `emulator-5554` (API 35 x86_64 Automated Test Device `dev35_aosp_atd_x86_64_Pixel_2`)
  - Run ID: `2ef82c38-e42f-4f67-9d62-9b73623b20e2`
  - Full host unit tests and connected device tests (`connectedShrunkDebugAndroidTest`, `connectedAndroidDeviceTest`) executed under `tools/hdk-coverage.init.gradle`.
  - Captured bundle at `D:\temp\chimali-coverage-runs\2ef82c38-e42f-4f67-9d62-9b73623b20e2` with raw counters, manifests, reports, and canonical `AnalyzedZeroProofV1` zero proofs.

### T078–T080: Protected Integration Controls & Honest Disposition Summary
- **Date**: 2026-10-25
- **Requirement**: FR-012, FR-013, FR-014, FR-015, FR-017, SC-007, SC-008
- **Status**: Tooling Complete / Remote Environmental Prerequisites Explicitly Unmet
- **Reconciliation with Principle I & Task Governance**:
  - Tooling verification proves that candidate Actions runs cannot grant merge authority (`PUB_002_APP_SOURCE_BINDING_REQUIRED`), skip flags fail closed (`INT_001_PROHIBITED_SKIP_OPTION_REJECTED`), and branch regressions are rejected (`COMP_001_REGRESSION_ABOVE_FLOOR_FAILS`).
  - Remote environmental controls (dedicated GitHub App installation in remote organization, GitHub runner provisioning with attached hardware, and default branch protection rules) remain explicitly unmet rather than simulated.
  - The live candidate run `2ef82c38-e42f-4f67-9d62-9b73623b20e2` correctly and honestly reports floor failure for `:core:domain` branch (56.8%) and `:feature:fido2` (58.9% / 54.4%) while passing repository aggregate (52.5%), exactly matching the test remediation baseline requirements of User Story 2 & User Story 3.
  - Full audit trail preserved in immutable archive bundles and verified by `tools/coverage/test-policy.ps1`.

---

## 10. Phase 9 Evidence: Convergence Audit and Superseding Corrections (T085)

### 10.1 Audit of Baseline Numbers & Floor Realities
- **Measured Live Baseline Bundle**: `2ef82c38-e42f-4f67-9d62-9b73623b20e2` at `D:\temp\chimali-coverage-runs\2ef82c38-e42f-4f67-9d62-9b73623b20e2`.
- **Verified Counter Data**:
  - `:core:security`: Instructions 76.03%, Branches 61.92% (Passed both floors >= 60%)
  - `:core:domain`: Instructions 62.31% (Passed), Branches 56.81% (Failed 60% floor; gap = +3.19% or 24 branches)
  - `:core:data`: Instructions 82.15%, Branches 68.33% (Passed both floors >= 60%)
  - `:feature:fido2`: Instructions 58.999% (Failed 60% floor; gap = +1.001%), Branches 54.398% (Failed 60% floor; gap = +5.602%)
  - `:feature:authenticator`: 0 / 0 instructions and branches (`NotApplicable` under `AnalyzedZeroProofV1`, Passed)
  - `:core:database`: Instructions 66.12% (Passed Tier 2 >= 20%)
  - `:core:common`: Instructions 21.31% (Passed Tier 2 >= 20%)
  - `:app`: Instructions 36.97% (Passed Tier 2 >= 20%)
  - **Repository Aggregate**: Instructions 69,568 / 63,031 (52.464% >= 50% floor, Passed)
- **Superseding Correction on All-Floor Closure**:
  - Neither all-floor closure nor 100% Tier 1 compliance is claimed. The baseline faithfully records that `:core:domain` branches and `:feature:fido2` instructions/branches are below the 60% floor. Full closure is the objective of active convergence remediation.

### 10.2 Audit of Publication and Remote Prerequisites
- **Superseding Correction on Publication Claims**:
  - Remote evidence branch `codex/coverage-evidence` and dedicated GitHub App credentials on GitHub are unmet external prerequisites (T046–T049, T089).
  - `publish.ps1` operates locally with atomic conflict detection, payload hashing, and outside-bundle receipt emission.
  - Dry-run executions emit `status: "DryRun"` receipts, and pushes attempted without remote credentials or branch access fail closed with exit code 1 or 2. In accordance with Constitution V and FR-017, no `Published` acceptance receipt is produced under dry runs or absent remote access.

### 10.3 Audit of Behavior Test Remediation and Traceability (T081)
- **Traceability Verification**:
  - Production code in `VaultAggregateServiceImpl.kt` (`getState`, `execute`) is annotated with `NFR-ARCH-050` and `NFR-SEC-041`.
  - Corresponding tests in `VaultAggregateServiceImplTest.kt`, `SensitiveTextTest.kt`, and `AesGcmEncryptedMetadataServiceTest.kt` carry matching `@Test` annotations referencing `FR-006`, `TR-AUTH-031`, `NFR-SEC-041`, and `NFR-ARCH-050`.
  - Focused suites pass with 100% success (11/11 in `AesGcmEncryptedMetadataServiceTest`, 11/11 in `SensitiveTextTest`, 12/12 in `UserConsentRecordBehaviorTest`, 6/6 in `VaultAggregateServiceImplTest`).
- **Superseding Correction on Batch Measurement**:
  - The unit test cases in Batches 1–3 are implemented and passing, but full batch-by-batch JaCoCo remeasurement and deficit reranking was not concluded prior to the initial live baseline. Full batch remeasurement is actively tracked under T104–T107.

### 10.4 Audit of Verifier Completeness & Sealed Bundle Protection (T083)
- `verify.ps1` and `coverage-common.ps1` have been hardened to:
  - Enforce complete schema validation (`run_id` UUID, 10 TRD keys, non-escaping artifact paths).
  - Verify every test suite outcome in `manifest.suites`; rejected runs cannot claim tests `Passed`.
  - Require `collection: "Complete"` for overall pass; an incomplete run is rejected even if counters exceed floors.
  - Verify JaCoCo XML report counters match `counters.json` exactly.
  - Require canonical `AnalyzedZeroProofV1` proof with matching report SHA256 digest for any omitted counter or empty module.
  - Write immutable decision records (`decision.json`, `decision.md`) strictly outside the sealed capture bundle (defaulting to `<bundle_parent>/assessments/<run_id>/`). Attempts to write inside the bundle fail closed.
  - Prohibit silent overwriting of existing assessment receipts unless `-Force` is explicitly passed.

### 10.5 Status of Tasks and Active Convergence
- Tasks T001–T045, T050–T075, T081–T085 are verified and completed.
- Tasks T046–T049 and T089 remain explicitly unmet (external GitHub App / remote runner provisioning).
- Remaining convergence tasks (T086–T108) continue sequentially.

## 11. FIDO2 Coverage Continuation (2026-10-25)

### 11.1 Behavior tests added

- `PasskeyCredentialTest.rejecting invalid stored metadata erases every owned sensitive field` exercises RP, credProtect, sign-count, credential-ID, AAGUID, and timestamp rejection while asserting erasure of all four owned sensitive text buffers.
- `WalletMasterSeedProviderTest.failed mnemonic encryption erases caller phrase and derived validation seed without persisting` forces the encryption failure path and verifies caller-buffer erasure, validation-seed erasure, no persisted ciphertext, and no published root/parent cache.
- `Ctap2GetAssertionHandlerTest.decodes allow-list identifiers and required user verification from CTAP request` verifies allow-list mapping and required-UV selection reach credential lookup. `rejects malformed CTAP user-verification option with invalid-CBOR status` verifies malformed input is rejected before lookup.
- The focused FIDO2 host suites for these tests passed; the full FIDO2 `testAndroidHostTest` suite also passed.

### 11.2 Full workload diagnostic (not an acceptance bundle)

- **Revision**: `aa1408dd` (`codex/058-enforce-coverage-targets`) with uncommitted test changes.
- **Workload**: `hdkHostCoverage` with `tools/hdk-coverage.init.gradle`; full host test suites, configured connected workloads, and report generation completed successfully on the ASUS_AI2302 physical device (Android API 35).
- **Diagnostic FIDO2 instruction coverage**: 53,070 / 86,100 = 61.64%.
- **Diagnostic FIDO2 branch coverage**: 3,213 / 5,526 = 58.14%; 103 additional covered branches are required for the 60% floor.
- **Earlier diagnostic in this continuation**: 3,206 / 5,526 (58.02%) in sealed handoff run `42bb76a8-47c1-49ba-9c8b-9afce9c281df`; subsequent dirty-tree diagnostics observed 3,208, 3,212, then 3,213 covered branches. These are local reports and are not sealed evidence or an accepted baseline.
- The first full diagnostic attempt used the emulator; `manualRecoveryTransfersOneMutablePhraseBuffer` failed with an empty JUnit failure element, then the emulator disconnected before coverage data could be collected. Re-running with the physical device completed successfully. The unexplained emulator failure remains a test-environment/test result to investigate, not a pass.
- The strict capture script was not run successfully: it requires a clean checkout whose source revision equals `HEAD`. The worktree contains these test edits and the untracked handoff document. Do not use the diagnostic XML as a substitute for a fresh clean `capture.ps1` run.
- Tier 1 floor closure, risk-ledger completion, accepted-target regression, sealed evidence publication, GitHub App check, and remote protection remain unverified or unmet. T104, T105, T107, and T108 must remain open until their full criteria are met.

### 11.3 Malformed allow-list coverage continuation (2026-10-25)

- **Selected behavior**: `COV-010` exercises a non-list allow-list, a non-map descriptor, and an unsupported credential-ID value. Each request returns CTAP invalid-CBOR before candidate lookup and releases the ceremony lock.
- **Focused verification**: `:feature:fido2:testAndroidHostTest --tests '*Ctap2GetAssertionHandlerTest*'` passed.
- **Full diagnostic**: `.\gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage` completed successfully in 2m56s with the configured ASUS_AI2302 physical device (Android API 35) and host suites.
- **Latest FIDO2 counters**: instructions 53,261 / 86,100 = 61.86%; branches 3,230 / 5,526 = 58.45%. The 60% branch floor requires 3,316 covered branches, so 86 more branches are required. The prior local diagnostic had 3,229 covered branches; this test added one covered branch in the latest report.
- This was a direct dirty-worktree diagnostic, not `capture.ps1`; it has no sealed run bundle or accepted assessment. It does not satisfy clean-revision baseline, comparison, publication, or integration acceptance. T104, T105, T107, and T108 remain open, as do external T046-T049 and T089.

### 11.4 Vault and consent ownership cleanup (2026-10-25)

- **Vault defect found by assertion**: retry events shared payload arrays with the input command, while the cleanup helpers only erased titles. That aliasing could make per-attempt event cleanup corrupt the payload needed by a later retry, and command/event/state payload buffers were retained after cleanup.
- **Correction**: `VaultDecider` now copies payloads at command-to-event and event-to-state boundaries. `clearSensitiveMemory` erases each owner’s payload as well as title for commands, events, and states.
- **Behavior assertions**: `VaultAggregateServiceImplTest` now verifies the exact three retry attempts, unchanged input payload at each attempt, command/event erasure after successful retry and exhausted failure, cleanup while cancellation propagates, and state-owned title/payload erasure. The retry-exhaustion result remains the last append failure (`Database lock`), consistent with current implementation behavior.
- **Consent rejection assertion**: `UserConsentRecord.copyWithOwnerFactory` exposes an internal narrow ownership seam; the validation rejection test retains and checks that the rejected copy's RP buffer is zeroed while the original record stays intact.
- **Focused verification**: `:core:data:testDebugUnitTest --tests '*VaultAggregateServiceImplTest*'` and `:core:domain:testAndroidHostTest --tests '*UserConsentRecordBehaviorTest*'` passed. The FIDO2 CTAP suite with COV-010 also passed.
- **Latest full diagnostic**: `hdkHostCoverage` succeeded on 2026-10-25 with the configured host/device workload and ASUS_AI2302 (Android API 35). Tier 1 counters: security 3,385/4,452 instructions (76.03%), 239/386 branches (61.92%); domain 5,081/7,142 (71.14%), 403/570 (70.70%); data 1,158/1,333 (86.87%), 44/60 (73.33%); FIDO2 53,261/86,100 (61.86%), 3,230/5,526 (58.45%); authenticator 0/0 under proved-zero applicability. Repository instructions: 72,891/132,861 (54.86%). FIDO2 remains 86 covered branches short of its 60% floor; the other measured floors pass.
- The full run was a direct dirty-tree Gradle diagnostic without a sealed bundle. It does not close T088, T104, T107, or T108. A clean revision-bound capture, full risk-ranked selection and reranking, regression comparison, immutable publication, and protected integration acceptance remain outstanding.

### 11.6 T105 completion and refreshed diagnostic (2026-10-25)

- `SensitiveTextSerializer.serializeWithTemporaryFactory` provides an internal narrow test seam for observing the temporary character array. `SensitiveTextTest` now proves the array is zeroed after both successful encoding and an encoder exception, while the source owner remains unchanged.
- The consent rejection and vault ownership assertions documented in §11.4 remain passing. The vault retry suite also asserts no snapshot save on retry failure/cancellation. The metadata encryption test suite passed in the same focused verification batch.
- **Focused verification**: `:core:domain:testAndroidHostTest --tests '*SensitiveTextTest*' --tests '*UserConsentRecordBehaviorTest*'`, `:core:data:testDebugUnitTest --tests '*VaultAggregateServiceImplTest*'`, and `:core:security:testAndroidHostTest --tests '*AesGcmEncryptedMetadataServiceTest*'` all passed. T105 is complete.
- **Latest full diagnostic**: `hdkHostCoverage` succeeded on ASUS_AI2302 (Android API 35); FIDO2 and app connected-test exit codes were both `0`. Tier 1 security 3,385/4,452 instructions (76.03%), 239/386 branches (61.92%); domain 5,090/7,151 (71.18%), 403/570 (70.70%); data 1,158/1,333 (86.87%), 44/60 (73.33%); FIDO2 53,261/86,100 (61.86%), 3,230/5,526 (58.45%); authenticator remains 0/0 under proved-zero applicability. Repository instructions 72,900/132,870 (54.87%). FIDO2 remains 86 covered branches short of its 60% floor; other measured floors pass.
- An intervening Moto G Stylus 5G (2022), API 33, diagnostic returned a FIDO2 connected-test exit code of `1` despite aggregate Gradle success. Its report is explicitly excluded from passing diagnostics; the successful ASUS run supersedes it. Neither diagnostic is sealed acceptance evidence because the source worktree is dirty.
- T046–T049 are deferred by explicit user instruction in the current implementation request and remain unchecked. T088, T089, T104, T107, and T108 also remain open; T107 still has a measured FIDO2 shortfall and T108 depends on final acceptance evidence.

### 11.7 HID session lifecycle coverage continuation (2026-10-25)

- Added two independent Bluetooth HID callback tests for rejecting a never-bonded host, preserving the active session when a different host disconnects, and clearing owned queued report bytes while advancing session generation after the active host disconnects.
- The first focused attempt exposed a test-fixture issue: replacing the wrapper's `MutableStateFlow` after construction left the public `StateFlow` view attached to the original instance. The fixture now writes the initial state into the existing flow; production behavior required no change.
- **Focused verification**: `:feature:fido2:testAndroidHostTest --tests '*BluetoothHidDeviceWrapperIncomingReportTest*'` passed, followed by the complete `:feature:fido2:testAndroidHostTest` suite.
- **Latest full diagnostic**: `hdkHostCoverage` completed successfully on ASUS_AI2302 (Android API 35); FIDO2 and app connected-test exit codes were both `0`. Security: 3,385/4,452 instructions (76.03%), 239/386 branches (61.92%); domain: 5,090/7,151 (71.18%), 403/570 (70.70%); data: 1,158/1,333 (86.87%), 44/60 (73.33%); FIDO2: 53,288/86,100 instructions (61.89%), 3,234/5,526 branches (58.52%); authenticator 0/0 under proved-zero applicability; repository instructions 72,927/132,870 (54.89%). FIDO2 needs 82 more covered branches to meet its 60% floor; other measured Tier 1 and repository floors pass.
- This remains a direct dirty-tree diagnostic, not a sealed clean-revision capture or integration assessment. It does not close T088, T089, T104, T107, or T108. T046–T049 remain explicitly deferred and unchecked.

### 11.8 Pairing-in-progress admission test (2026-10-26)

- Added COV-015 to verify that a Bluetooth HID connection received during `BOND_BONDING` stays pending, does not become the active authenticator session, and is cleared if the host disconnects before bonding completes. The test asserts `Advertising`, no active host, and pending-host cleanup.
- **Focused verification**: `:feature:fido2:testAndroidHostTest --tests '*BluetoothHidDeviceWrapperIncomingReportTest*'` passed.
- **Full measurement attempt**: `hdkHostCoverage` stopped at `:app:connectedShrunkDebugAndroidTest` with `DeviceException: No connected devices!`. `adb devices -l` confirmed no attached device. The prior successful ASUS API 35 counters remain the last completed diagnostic; COV-015's coverage impact is unmeasured.
- At this checkpoint T104 had not yet been reconciled; §11.9 records its completion. T046–T049 remain deferred and unchecked, while T088, T089, T107, and T108 remain open. No floor, regression, or integration status is changed by this focused test.

### 11.9 T104 risk ledger reconciliation and RP parser correction (2026-10-26)

- Reconciled `test-selection.md` against clean Initial run `42bb76a8-47c1-49ba-9c8b-9afce9c281df` (commit `aa1408ddcadc599d83ee1f971b6af48c7c1816f8`, clean tree `a120937362e89eee4d4c58fe2577b468240d1e6a`). It contains five Tier 1 dispositions, repository counters, module XML/HTML reports and raw counters, and 187 required suites with no failures, errors, or skips. The exact initial floor gaps are 0/0 for security, domain, and data; 0 instructions / 110 branches for FIDO2; authenticator 0/0 inventory-only. Repository instructions are 72,418/132,630 (54.60%).
- The associated assessment records collection complete, tests passed, FIDO2 branch floor failed at 3,206/5,526, and the other executable module floors passed. This is sufficient report evidence for behavior selection, but not a trusted regression baseline: manifest floor state is `Unverified`, class-inventory digests and authenticator zero-proof report bindings are empty, and comparison compatibility is `Missing`. T088 remains open.
- Audited the missed-branch reports for all five Tier 1 modules and reordered the candidate ledger by impact, likelihood, then current exact floor deficit and stable ID. Added exact test-function references and independent outcomes for legacy candidates; the empty authenticator module is explicitly excluded as a behavioral test candidate. T104 is complete as a selection-ledger task; the ledger records that COV-015 and COV-016 have no current measured coverage delta.
- **COV-016 defect**: `RelyingParty.isValidRpId` accepted `https:///`, `https://?query`, `https://#fragment`, and whitespace within an HTTPS authority. Added these invalid forms to `RelyingPartyTest`; the test failed before repair on `https:///`. The parser now finds the authority boundary without materializing a `String`, rejects an empty authority and authority whitespace, and preserves valid path/query/fragment boundary handling.
- **Verification**: focused `:core:domain:testAndroidHostTest --tests '*RelyingPartyTest*' --tests '*RelyingPartyOwnershipTest*'` passed. The complete `:core:domain:testAndroidHostTest` suite passed.
- **T107/T108 status**: Latest device check still finds no attached Android device. Full host/device remeasurement is therefore unavailable; COV-015/016 deltas, all-floor fresh compliance, and nonregression remain unverified. T046–T049 remain explicitly user-deferred; T089 also remains unmet. T107 and T108 remain open and must not be inferred complete from host tests or the old Initial bundle.

### 11.5 GitHub service cost review (2026-10-25)

- The repository `octaviordz/chimali` is public. GitHub's current official documentation states protected branches are available for public repositories on GitHub Free, standard GitHub-hosted Actions runners are free for public repositories, and self-hosted runners carry no GitHub Actions usage charge (the operator bears host/device costs). A custom GitHub App can be registered and installed on the account; marketplace app subscriptions are separate.
- No paid GitHub plan, paid Marketplace app, or rented runner is required by that zero-subscription configuration. T046-T049 remain operationally unmet until the App, runner, evidence branch, required App-bound check, protection and real rejection/passing demonstrations are actually configured and verified. This cost review is not evidence that those controls exist.

### 11.10 Windows host/device validation (2026-10-26)

- **Measured identity**: clean commit `50526113779a808695dc87ba04d2ea38f1ac8d3e`, tree `8d10c950a899b881ffb0e7e820d19933cbf1350c`, on `lab/or/chimali`. A detached local harness copy at the same commit was used; this is not a protected/reviewed hosted harness provenance claim. This documentation was appended after capture and is not part of that measured tree.
- **Environment preparation**: installed Google Android command-line tools 23.0 (Android CLI `1.1.16717390`), platform `android-37.0` revision 2, and build-tools `37.0.0`. The tools ZIP matched Google's repository SHA-1 metadata. Validation commands selected the existing JetBrains JDK `21.0.10` at `D:/packages/gradle/jdks/jetbrains_s_r_o_-21-amd64-windows.2`; Gradle's committed daemon criteria also select JetBrains Java 21. System-wide Java settings were not changed. Gradle `9.6.0` configuration (`help`) passed in 4m43s.
- **Checkout reproducibility finding**: system Git `core.autocrlf=true` converted the byte-hashed `branchless.xml` and `empty.xml` fixtures to CRLF. The first policy run returned 73 passed / 3 failed. Restoring their committed LF bytes reproduced both producer-proof SHA-256 values; all 76 fixtures then passed. Refreshing the index left no staged or unstaged content changes. No `.gitattributes` fix has been made, so a fresh Windows checkout can reproduce this failure.
- **Device**: physical `ASUS_AI2302`, serial `R7AIB7002901AZ3`, Android 15/API 35, ARM64, authorized over USB. The connected FIDO2 suite executed 25 tests and the app `shrunkDebug` suite executed 11 tests, both with zero failures, errors, or skips. These executions supersede the earlier no-attached-device observation for this local validation only; they do not prove guest-VM connectivity or every separate hardware/performance obligation.
- **Full workload**: external-harness `capture.ps1 -Purpose Initial` ran `hdkHostCoverage` from 2026-10-26 14:42:48Z to 15:10:09Z. Gradle succeeded in 27m20s with 477 tasks executed. Run ID: `eb5a5aea-d356-4356-aa08-26ddc8baacc8`; retained bundle: `D:/octav/coverage-local-validation/runs/eb5a5aea-d356-4356-aa08-26ddc8baacc8`. The collector records 1,179 host tests with zero failures/errors/skips. Its 2,905 listed artifact hashes independently matched their retained files; the bundle contains 2,907 files totaling approximately 28.6 MiB.

| Scope | Instructions covered / total | Branches covered / total | Diagnostic floor observation |
| --- | --- | --- | --- |
| `:core:security` | 3,385 / 4,452 | 239 / 386 | 60/60 floors met |
| `:core:domain` | 5,124 / 7,184 | 412 / 578 | 60/60 floors met |
| `:core:data` | 1,158 / 1,333 | 44 / 60 | 60/60 floors met |
| `:feature:fido2` | 53,331 / 86,100 | 3,238 / 5,526 | Branches 58.596%; 78 more covered branches needed at this denominator for 60% |
| `:feature:authenticator` | 0 / 0 reported | 0 / 0 reported | Collector's empty disposition remains unsupported by bound retained-scope proof |
| `:core:database` | 409 / 623 | 19 / 36 | Tier 2 instruction floor met |
| `:core:common` | 374 / 1,755 | 8 / 58 | Tier 2 instruction floor met |
| `:app` | 738 / 1,996 | 28 / 95 | Tier 2 instruction floor met |
| Repository | 73,004 / 132,903 | 4,514 / 8,010 | Instructions 54.930%; 50% floor met |

- **Assessment**: `verify.ps1` exited 1 and wrote `D:/octav/coverage-local-validation/assessment-eb5a5aea/decision.json` and `decision.md`; its named floor failure is FIDO2 branches below 60%. Security branches (61.917%) and FIDO2 branches (58.596%) remain below the nonbinding 65% goal; domain/data exceed that goal. This was an Initial diagnostic, with no current-target regression comparison or archive publication.
- **Independent evidence limitations**: the collector parses only `/testsuite`, omitting the Android reports rooted at `/testsuites` and their 36 test results from its manifest and test-report copies. Unmodified copies and hashes are retained separately at `D:/octav/coverage-local-validation/supplement-eb5a5aea/`. Module `class_inventory_digest` values are empty, authenticator zero-proof report/inventory bindings are empty, and Gradle did not emit the canonical root `build/reports/hdk-coverage/counters.json`. The collector's evidence environment is hardcoded to JVM 25 although this capture selected Java 21. Its `Complete`/`Passed` labels and verifier's empty-module approval do not resolve these deficiencies; T088 is not closed by this run.
- **Local check failure**: `tools/local-ci.ps1 -NoFix -NoConfigurationCache` reported `:core:domain:ktlintCommonTestSourceSetCheck FAILED`: `RelyingPartyTest.kt:45:13 Unexpected newline before '.'`. After that definite failure, only this invocation's Gradle wrapper was stopped while parallel Kotlin/Native downloads were pending; the local-CI script returned exit 1, and the daemon subsequently shut down. No formatter or product-source repair was applied. The script also does not actually append `--no-configuration-cache` when its `-NoConfigurationCache` switch is supplied, so that switch is not evidence that caching was disabled. Later local-CI phases were not executed and cannot be claimed passed from this attempt.
- **Task status**: no task completion marks changed. T046-T049, T088, T089, T107, and T108 remain open. Local host/device execution is now demonstrated; disposable-worker provisioning, valid accepted baseline, all-floor closure, trusted current-target comparison, GitHub App/environment/archive/protection controls, and actual protected rejection/passing demonstrations remain unmet or unverified. No VM was created and no GitHub settings were changed.

### 11.11 Post-run local reproducibility fixes (2026-10-26)

- After the diagnostic capture in 11.10, the collector was updated to recognize both top-level `<testsuite>` and nested `<testsuites><testsuite>` JUnit reports, preserve report source-relative paths to avoid same-name collisions, and fail capture when a report cannot be parsed or has no suites. The evidence environment label now reads the Java runtime and Android SDK platform directories from the capture host instead of claiming JVM 25.
- Added `.gitattributes` entries to preserve the exact bytes of the two SHA-256-bound exporter fixtures on Windows checkouts. Corrected `-NoConfigurationCache` to pass Gradle's `--no-configuration-cache` option and fixed the observed Ktlint line break in `RelyingPartyTest.kt`.
- These are source changes after the run recorded in 11.10. They have not been rerun or independently verified; the previous run remains diagnostic evidence only, and no task completion marks changed.

### 11.12 Follow-up verification and collector retry (2026-10-26)

- On committed revision `4fede6149b6a884ea0c47fbb2f750e2361ca7aa8`, `pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/test-policy.ps1` passed all 76 fixtures, and `gradlew.bat --no-configuration-cache :core:domain:ktlintCommonTestSourceSetCheck` completed successfully in 53 seconds.
- A fresh Initial capture against that clean revision found the authorized `ASUS_AI2302` (Android 15/API 35) and completed Gradle task `hdkHostCoverage` successfully in 6m31s (477 tasks; 25 executed, 452 up-to-date). Capture run `2f0e1f84-ee0a-48e7-8535-ca1a655ce631` did not complete: collection stopped while copying the first nested JUnit report because the new destination-path expression called `Join-Path` without its child path. The partial bundle is retained under `D:/octav/coverage-local-validation/runs/2f0e1f84-ee0a-48e7-8535-ca1a655ce631`; no verifier decision or completed manifest was produced.
- Corrected the destination expression. A focused read of the generated reports then parsed 189 JUnit XML files, 199 suites, and 1,215 tests and resolved the relative destination paths. At the time this entry was first recorded, the one-line correction was uncommitted; it was subsequently committed as `0b7e333a15a05e0a3de07afd032abc1b666787d5` and verified by the capture in 11.13.

### 11.13 Clean capture after JUnit path correction (2026-10-26)

- **Measured identity**: commit `0b7e333a15a05e0a3de07afd032abc1b666787d5`, tree recorded in the run manifest; clean preflight and clean postflight both passed. The separate local harness checkout remained at `50526113779a808695dc87ba04d2ea38f1ac8d3e`; this is a local diagnostic, not a protected/reviewed hosted harness claim.
- **Capture**: run `7df73cc7-6ef8-4340-907c-db16f37297ea`, purpose Initial, from 2026-10-26 16:18:04Z to 16:24:43Z. `hdkHostCoverage` succeeded in 6m31s (477 tasks: 25 executed, 452 up-to-date). The connected device was ASUS_AI2302, Android 15/API 35, arm64-v8a. The collector emitted a Complete collection and Passed test decision, retained 189 JUnit XML files with 199 suites / 1,215 tests and zero failures, errors, or skips, and produced the canonical `counters.json`.
- **Verifier**: `verify.ps1` exited 1 and wrote `D:/octav/coverage-local-validation/assessment-7df73cc7-6ef8-4340-907c-db16f37297ea/decision.json` and `decision.md`. All reported module decisions passed except `:feature:fido2`, whose branch coverage was 3,238 / 5,526 = 58.596%, below the 60% floor. FIDO2 instructions were 61.941%; repository instructions were 73,004 / 132,903. This Initial diagnostic has no target comparison, accepted baseline, publication, or integration approval. The retained run bundle is under `D:/octav/coverage-local-validation/runs/7df73cc7-6ef8-4340-907c-db16f37297ea`.
- **Reproducibility result**: this clean capture completed the corrected nested-JUnit parsing and source-relative report copying; the verifier accepted the bundle and its artifact digests. This confirms the collector and line-ending fixes for this local checkout. It does not provision the disposable protected runner or complete T046–T049. No task status was changed.

### 11.14 Repeatable local Windows host setup (2026-10-26)

- Added `tools/coverage/host-profile.json` to record the required PowerShell major version, JetBrains Java 21 Gradle daemon, Android command-line tools 23.0, Android platform 37.0 revision 2, build-tools 37.0.0, platform-tools 37.0.0, and minimum Android device API 28. Existing Gradle wrapper/JVM pins remain the source of truth for Gradle 9.6 and the daemon toolchain.
- Added `tools/coverage/setup-host.ps1` to resolve those pins, optionally install the fixed Android SDK packages, configure only the current PowerShell process and its child processes, validate Gradle's actual daemon selection, and require an online authorized device unless `-SkipDeviceCheck` is specified. SDK root resolution is explicit and can be overridden; no machine-wide settings or persistent environment values are written.
- **Verified on this host**: `pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/setup-host.ps1 -DeviceSerial R7AIB7002901AZ3` exited 0. It selected the cached JetBrains JDK `21.0.10`, Gradle `9.6.0`, SDK platform `android-37.0` revision 2, build-tools `37.0.0`, platform-tools `37.0.0`, and the authorized ASUS_AI2302 (API 35). Before setup, this PowerShell environment exposed Microsoft Java 11 through `java` and did not expose `adb` on `PATH`; setup normalized those for its process scope.
- **Limit**: a fresh host still needs PowerShell 7, Git, a Java 17+ Gradle launcher runtime, and Android SDK Command-line Tools 23.0 as bootstrap prerequisites. Android licenses must be accepted before package installation. The setup script does not provision a physical device, GitHub App, hosted runner, evidence archive, branch protection, or trusted capture-harness provenance. T046–T049 remain open, and task checkboxes were not changed.

### 11.15 Clean capture through the repeatable host setup (2026-10-26)

- **Measured identity**: commit `6d8e0432d7485b5c964b0f58b7a26d04eaf8dd6a`, tree `20fba1e542f7a96e70b50a642e3efa9a454eaf4e`, on `lab/or/chimali`. The capture manifest records clean preflight and postflight. The new `setup-host.ps1` was dot-sourced first, and its Java 21 / Android SDK 37 process environment was inherited by capture and Gradle.
- **Harness provenance**: capture used the separate local detached checkout at commit `50526113779a808695dc87ba04d2ea38f1ac8d3e`. Its capture and Gradle init scripts differ from the measured checkout, and it is not demonstrated as a protected or reviewed trusted harness. Results below are local diagnostics only; no GitHub integration acceptance is claimed.
- **Capture**: run `117f8aa8-85d4-4a3a-9184-8ee080e3f4b3`, purpose Initial, from 2026-10-26 22:03:56Z to 22:14:55Z. `hdkHostCoverage` succeeded in 10m57s (477 tasks: 24 executed, 453 up-to-date). Device ASUS_AI2302, Android 15/API 35, arm64-v8a. The collector recorded Complete / Passed, with 187 suites and 1,179 tests, zero failures, errors, or skips. Bundle: `D:/octav/coverage-local-validation/runs/117f8aa8-85d4-4a3a-9184-8ee080e3f4b3`.
- **Verifier**: `verify.ps1` exited 1 and wrote the decision under `D:/octav/coverage-local-validation/assessment-117f8aa8-85d4-4a3a-9184-8ee080e3f4b3`. `:feature:fido2` remains below its branch floor at 3,238/5,526 (58.596%); it needs 78 additional covered branches at this denominator. FIDO2 instruction coverage is 53,331/86,100 (61.941%). Security, domain, data, authenticator, all configured Tier 2 modules, and the repository instruction floor passed; repository instructions are 73,004/132,903 (54.930%). Overall floors failed. There is no target comparison, regression decision, publication, or integration pass.
- **Evidence limits**: the manifest and counters retain empty class-inventory digests; the authenticator zero proof also has empty report and inventory bindings. Comparison compatibility is Missing. These deficiencies and the unreviewed harness prevent treating this as T088/T046–T049 acceptance, even though the host setup, workload, collector, and local verifier ran end to end. No task checkbox was changed.

### 11.16 Canonical production inventory and report binding repair (2026-10-26)

- **Changes under test**: the Gradle analyzer now emits retained production class identities and SHA-256 hashes, per-module class-inventory digests, report digests, and a whole-inventory digest. The collector copies that inventory and records local harness revision/tree/file hashes; the verifier checks the inventory, reports, counters, and zero-count proofs against those bindings. The authenticator empty report is emitted by JaCoCo from an empty execution stream. These source changes are uncommitted and were exercised on the current Windows checkout, not a clean pinned harness.
- **Workload and analyzer**: `gradlew.bat --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageAnalyze` completed successfully in 7m30s (480 actionable tasks; 29 executed, 451 up-to-date) on ASUS_AI2302, Android 15/API 35. All eight measured module report files matched their canonical report digests. The generated inventory has 14 production module entries; every retained-class digest recomputed successfully after matching Gradle's ordinal class-ID ordering. The exact inventory file SHA-256 matched `counters.json`; the authenticator XML contains a JaCoCo report element and its report, class-inventory, and whole-inventory hashes match the `AnalyzedZeroProofV1` proof.
- **Policy checks**: `tools/coverage/test-policy.ps1` passed 79/79 cases; the focused inventory fixture run passed 11/11. PowerShell parsing for capture, verifier, common policy, and fixture scripts passed, and `git diff --check` passed. A subsequent `hdkCoverageVerify` reran the workload and failed only at the expected FIDO2 branch floor: 3,238/5,526 = 58.596%, below 60%.
- **Limits**: capture/verifier integration against a sealed bundle was not run because the checkout is dirty and capture requires clean source identity. GitHub trusted-harness availability and protection, a valid accepted baseline/current-target comparison, all-floor closure, and protected integration rejection/passing behavior remain unverified. T046–T049 remain unchecked and no task status was changed.

### 11.17 Clean capture and sealed-bundle binding verification (2026-10-26)

- **Measured identity**: commit `2be39f64663c05e7d11987926a25843fb754346b`, tree `fd9f538a08e57934dd6084d3e93abdfe61dc2d95`, with clean preflight and postflight. The same clean local commit was used as `HarnessRoot`; its manifest records the commit/tree and hashes of the capture, Gradle init, policy, and verifier files. This is reproducible local provenance, not a protected or independently reviewed harness.
- **Environment and capture**: `tools/coverage/setup-host.ps1` selected JetBrains Java 21.0.10, Gradle 9.6.0, Android SDK platform 37.0/build-tools 37.0.0/platform-tools 37.0.0, and verified device `R7AIB7002901AZ3` as ASUS_AI2302, API 35, arm64-v8a. Capture run `6788e4cd-753a-45bf-875b-46e9151541c9` completed in 7m23s; Gradle reported `hdkHostCoverage` and `hdkCoverageAnalyze` successful in 7m21s. The bundle records Complete collection and Passed tests: 199 suites / 1,215 tests, with zero failures, errors, or skips. Bundle: `D:/octav/coverage-local-validation/6788e4cd-753a-45bf-875b-46e9151541c9`.
- **Sealed-bundle verifier**: `tools/coverage/verify.ps1` consumed that exact bundle and exited 1 with one unmet reason: FIDO2 branch coverage 3,238/5,526 = 58.596%, below 60%. The decision records collection Complete, tests Passed, repository instructions 54.93% (pass), and all other measured module floors pass. The inventory file SHA-256 matched both the manifest and counters (`49f0a6425151b2dfc321beadc0058961b44c406d7371935c4ed08cbb78ee1b27`); all module report digests and the authenticator zero proof were accepted. Decision: `D:/octav/coverage-local-validation/assessment-6788e4cd-753a-45bf-875b-46e9151541c9/decision.json` and `.md`.
- **Limits**: comparison compatibility is Missing, regression is NotApplicable, publication is Pending, and integration is Failed because the floor decision failed. No accepted baseline, protected runner/harness, GitHub App, archive, branch-protection, or target-comparison behavior was exercised. T046–T049 remain unchecked and no task status was changed.

### 11.18 COV-017 HID callback regression tests and bounded repair (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | `FR-HID-010`, `FR-HID-020`; TRD §9.4 connection-loss handling. |
| `scope` | Android host component tests for bond admission, delayed reconnects, discovery filtering, callback continuations/forwarding, report admission, and real wrapper queue erasure. Added 52 test cases: 34 coordinator cases and 18 additional wrapper cases. |
| `revision` | Base commit `cd9b7dbc6a3d8b64473d6efb451e9b5a3f470651` plus uncommitted test additions and four pending-host cleanup assignments in the coordinator and wrapper. File identities are recorded in `D:/octav/coverage-local-validation/cov017-source-sha256.json`; these results are not clean-commit acceptance evidence. |
| `named_procedure` | `BluetoothHidCallbackCoordinatorTest`; `BluetoothHidDeviceWrapperIncomingReportTest`; `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage`. |
| `environment` | Windows host, JetBrains Java 21.0.10, Gradle 9.6.0, Kotlin 2.3.21; MockK Android platform doubles and coroutine virtual time for the focused tests. Final coverage used authorized ASUS_AI2302 device `R7AIB7002901AZ3`, Android 15/API 35, with the process environment selected by `setup-host.ps1`. |
| `expected_observed_result` | Initial focused run: 46 cases, exactly two failures. `late bond completion after callback session loss cannot admit a host` observed `Connected` instead of `Idle` for `profile-lost` and `registration-lost`. Clearing `pendingBondDevice` inside their existing synchronized cleanup blocks made all 46 cases pass. The expanded 61-case run exposed the same defect after `unregister`, `close`, and `error`. Clearing the pending host in `unregisterApp()` (also used by `close()`) and `reportError()` made all 61 focused cases pass. The final complete FIDO2 host suite passed 957 tests with zero failures, errors, or skips; all local CI phases and the fresh host/device coverage gate passed. |
| `date` | 2026-10-26. |
| `limitations` | Host doubles establish component callback behavior, not physical pairing interoperability. Full CI initially required Native distribution setup and exposed an existing CTAP test import-order issue and redundant credential-test helper parameter; both test-only style fixes preserve assertions. Clean post-change coverage, accepted-target comparison, publication, and integration remain unverified. |
| `status` | Focused assertions Passed; full local CI including diagnostic coverage gate Passed; clean coverage, comparison, and integration Unverified. |
| `artifact_link` | `D:/octav/coverage-local-validation/cov017-before/` and `cov017-extra-before/` (failed JUnit); `cov017-final-focused-passed/` (61 passing focused cases); `cov017-final-ci-passed/` (957 passing FIDO2 host cases); `cov017-extra-focused-before.txt`, `cov017-extra-focused-after.txt`, and `cov017-final-ci-coverage.txt` in the same evidence directory. |

The first iteration's fresh host/device diagnostic passed workload and analysis, then failed only the FIDO2 branch floor at 3,303/5,526 = 59.772% (65 additional covered branches, 13 short). Its counters, inventory, report, and source hashes are retained under `D:/octav/coverage-local-validation/cov017-diagnostic-1`. It used the dirty checkout and is not a sealed or accepted baseline. The same COV-017 risk batch was extended before any lower-risk progression.

The final full pipeline completed successfully in 1,707.31 seconds (28m27s). Its coverage Gradle invocation completed in 16m19s, and `hdkCoverageVerify` passed. Canonical counters generated at `2026-10-27T00:52:40Z` (2026-10-26 local) report FIDO2 instructions 54,452/86,114 (63.232%) and branches 3,333/5,526 (60.315%). This adds 95 covered branches over the clean pre-batch run and exceeds the exact 60% minimum of 3,316 by 17. Repository instructions are 74,125/132,917 (55.768%); all configured module/repository hard floors passed. The approximately-65% branch improvement goal remains unmet and is not an acceptance floor.

All 14 retained production class-inventory digests recomputed successfully; the inventory file hash matched the canonical counter binding, and all six recorded source/test file hashes still matched the final measured files. Final counters, inventory, FIDO2 XML, and source identities are retained under `D:/octav/coverage-local-validation/cov017-final-diagnostic`; the execution log is `D:/octav/coverage-local-validation/cov017-final-ci-coverage.txt`. This was an uncommitted direct Gradle diagnostic, not a `capture.ps1` sealed bundle. A clean post-commit capture and its separate verifier decision remain required; no accepted target comparison, publication, or integration decision was made.

No task checkbox was changed. T046–T049 remain unchecked. The current batch must receive a clean measurement checkpoint before lower-risk progression.

### 11.19 Clean post-commit capture and passing sealed verification (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Spec 058 local collection, inventory, hard-floor, and per-batch measurement requirements; COV-017 component behavior under `FR-HID-010` / `FR-HID-020`. |
| `scope` | Complete Initial host/device capture and separate sealed-bundle verification on this Windows host. No accepted-target comparison or hosted integration was performed. |
| `revision` | Commit `997cc3b3ccab94f1f57f5b9c770e98c5b969118f`, tree `bc6b6f323d80fd2d8ce6785096b7bd20fe9529c1`. Both clean preflight and postflight passed. The same clean local commit/tree supplied the harness; the manifest retains hashes of its capture, Gradle init, policy, and verifier files. |
| `named_procedure` | `setup-host.ps1 -DeviceSerial R7AIB7002901AZ3`; `capture.ps1` with exact source revision and `Purpose Initial`; `verify.ps1` against exact run `5fad15f8-0604-46ea-b822-6b0997de4e26`, writing the assessment outside the sealed bundle. |
| `environment` | Windows, JetBrains Java 21.0.10, Gradle 9.6.0, Android platform 37.0/build-tools 37.0.0/platform-tools 37.0.0; authorized ASUS_AI2302 (`R7AIB7002901AZ3`), Android 15/API 35, arm64-v8a. Setup applied process-scoped environment values. |
| `expected_observed_result` | Capture exited 0 with collection Complete and tests Passed. It retained 200 suites / 1,267 test results, zero failures, errors, or skips. The two HID suites contain 34 and 27 passing cases. The separate verifier exited 0 with `overall_passed=true`, collection Complete, tests Passed, floors Passed, and zero unmet local reasons. All eight configured module decisions passed, including the authenticator's proved-empty disposition. |
| `date` | 2026-10-26 local. Manifest workload timestamps: `2026-10-27T00:58:58Z` to `2026-10-27T01:10:09Z`; Gradle reported 11m9s. Report packaging finished before the separate verification. |
| `limitations` | Local harness identity is recorded; protected/reviewed harness provenance is not established. Compatibility is Missing, regression NotApplicable, publication Pending, and integration Unverified. Physical Bluetooth pairing interoperability and the stronger separate business-logic objective were not certified. The nonbinding approximately-65% branch goal remains unmet. |
| `status` | Passed for local collection, tests, floors, and the COV-017 measurement checkpoint. Accepted-target comparison, publication, and hosted integration remain Unverified. |
| `artifact_link` | Bundle: `D:/octav/coverage-local-validation/5fad15f8-0604-46ea-b822-6b0997de4e26`; assessment: `D:/octav/coverage-local-validation/assessment-5fad15f8-0604-46ea-b822-6b0997de4e26/decision.json` and `decision.md`; execution logs: `capture-997cc3b.txt` and `verify-997cc3b.txt` under the same evidence root. |

The sealed verifier accepted artifact integrity, production inventory/counter/report bindings, and the authenticator zero proof. Its manifest digest is `708382d35e6c32129e3c303b8e27043e3244d24b57d0217f9e5e4e3670c62583`. Raw FIDO2 instructions are 54,452/86,114 (63.232%); branches are 3,333/5,526 (60.315%), 95 more covered branches than the clean pre-batch measurement and 17 above the exact 60% minimum. Repository instructions are 74,125/132,917 (55.768%). All configured hard floors passed without threshold or exclusion changes.

The selection ledger now records the verified local clean checkpoint. No task checkbox changed; T046–T049 remain unchecked, and no protected integration acceptance is claimed.

### 11.20 T046–T049 live GitHub provisioning assessment (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | T046–T049: actual disposable device runner, protected reporter/App setup, evidence archive, source-bound required check, and controlled blocking/invalidation demonstrations. |
| `scope` | Read-only inspection of the public `octaviordz/chimali` repository and its GitHub settings. No App, runner, environment, protection rule, branch, secret, or workflow run was created or changed. |
| `revision` | GitHub repository metadata reports ID `1085334134`, default branch `lab/or/chimali`, current remote default-branch head `997cc3b3ccab94f1f57f5b9c770e98c5b969118f`, and public visibility. The repository API reports the connected account has admin permission. Local checkout `HEAD` is `37e032608f5060c85fbfeaba696e8f663bf4b469`; it is not the remote default-branch head. |
| `named_procedure` | GitHub repository metadata, rulesets and branches API reads; signed-in GitHub settings pages for Developer Settings > GitHub Apps, repository Settings > Branches, Actions > Runners, and Environments; Actions workflow-runs API; local `gh auth status`. |
| `expected_observed_result` | The repository rulesets API returned an empty list. The default branch entry reports `protected=false`; the Branches settings page shows no branch-protection rules. The branch listing contains 62 branches and no `codex/coverage-evidence` branch. The Runners settings page reports no configured runners. Environments reports none for the repository. Developer Settings > GitHub Apps reports “No GitHub Apps” for the account. Actions workflow-runs API returned `total_count=0`. These are confirmed absent controls; they do not prove that no third-party App is installed elsewhere or that no repository-level secrets exist. |
| `workflow_review` | The two workflow files are present on the current default branch. `coverage.yml` requires labels `[self-hosted, windows, android-device]`, but no runner is registered; its `push.branches` list omits the actual default branch `lab/or/chimali`. Both workflows use `vars.TRUSTED_HARNESS_SHA || '9af4b4ab63d42bc2bc6be703d15dc9d5924fcbc7'`, so the protected pin is not mandatory. `coverage-report.yml` names environment `coverage-reporting`, which is absent, and its default-branch promotion list (`main`, `lab/or/jpc`) omits `lab/or/chimali`. No hosted workflow has run on the remote default branch. |
| `access` | The GitHub connector's repository metadata reports admin permission, but `gh auth status` reports the local `octaviordz` keyring token is invalid and `gh repo view` returns HTTP 401. The connector's branch-protection endpoint returned HTTP 403; the signed-in GitHub settings page independently confirmed no branch protection rules. |
| `date` | 2026-10-26 local. |
| `limitations` | The connected Android device and reproducible local host are verified in §11.19, but this Windows host is not a disposable isolated Actions runner and is not registered. The repository is public; candidate pull-request code would execute on any runner selected by the current capture workflow. Dedicated coverage App/installation IDs and key, exact-ref environment restriction, evidence-branch protection/permissions, required check bound to the App ID, and actual negative/positive/invalidation runs remain unavailable or unverified. No protected default-branch bootstrap or acceptance is claimed. |
| `status` | T046–T049 resumed; all four remain open. No completion checkbox changed. |
| `artifact_link` | Repository: `https://github.com/octaviordz/chimali`; default-branch settings observed while signed in; workflow definitions: `.github/workflows/coverage.yml` and `.github/workflows/coverage-report.yml`. |


### 11.21 Dedicated coverage App registration and repository installation (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | T046 App identity/installation and least-privilege configuration; T047 reporter check and archive permission provisioning. |
| `scope` | Registered the dedicated GitHub App and installed it only on `octaviordz/chimali`, after the user confirmed App/key creation. No repository workflow, environment, branch protection, archive branch, or secret was changed. |
| `named_procedure` | GitHub Developer Settings > Create GitHub App; Credentials > Key pairs; Install App, selecting only `octaviordz/chimali`. |
| `expected_observed_result` | App `chimali-coverage-integration`, App ID `5488780`; installation ID `175659848`. The App is installable only on the `@octaviordz` account; its installation selects exactly one repository, `octaviordz/chimali`. Installed permissions are repository metadata read, Checks read/write, and Contents read/write; other repository, organization, account, and enterprise permissions were left at none. The App webhook was disabled and no user authorization flow was configured. |
| `credential_handling` | GitHub generated one App private key. The browser downloaded `chimali-coverage-integration.2026-10-26.private-key.pem` to the local Downloads folder. The key value was not read into the conversation, printed, or added to the repository. It has not been entered into GitHub Actions secrets or transmitted elsewhere. |
| `date` | 2026-10-26 local. |
| `limitations` | The App is installed, but the repository has no `coverage-reporting` environment and the default branch is unprotected. The generated key therefore is not yet bound to an exact-ref environment. Repository-wide Contents write is currently part of this installation grant; the trusted publisher environment and protected archive branch still need to constrain its use. No check has been created and no workflow has run. |
| `status` | App registration, key generation, and single-repository installation verified. T046–T049 remain open; no task checkbox changed. |

Before transmitting the generated private key, review and repair the reporter workflow and mandatory full-SHA harness pin, then create `coverage-reporting` with its deployment branch rule restricted to the exact default branch `lab/or/chimali`. Only then add `COVERAGE_APP_ID=5488780`, `COVERAGE_APP_INSTALLATION_ID=175659848`, and `COVERAGE_APP_PRIVATE_KEY` as environment secrets. The Computer Use skill requires action-time confirmation before editing cloud access controls and before transmitting a sensitive value. Do not fall back to `GITHUB_TOKEN`, a personal access token, or a local workstation runner. The App-bound default-branch ruleset is now active (§11.22), but protected reporter review, the disposable device runner, archive protection/trial retrieval, environment restriction/secrets, and controlled integration checks remain separate unresolved requirements.

### 11.22 App-bound default-branch ruleset activation (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | T048 source-bound required check, strict current-target handling, no accepted bypass; T046 actual integration-target protection. |
| `scope` | Activated a repository branch ruleset on the confirmed default branch after the user explicitly approved activation. This does not establish a passing coverage report or complete T046–T049. |
| `revision` | Repository `octaviordz/chimali` (ID `1085334134`); exact target `refs/heads/lab/or/chimali`; current target SHA `997cc3b3ccab94f1f57f5b9c770e98c5b969118f`. Ruleset ID `25926101`, name `coverage-integration-default-branch`. |
| `named_procedure` | Saved the configured GitHub Settings > Rulesets form; independently read `GET /repos/octaviordz/chimali/rulesets` and `GET /repos/octaviordz/chimali/rulesets/25926101`; read the target commit's check-runs API. |
| `expected_observed_result` | GitHub API reports `enforcement=active`, exact inclusion `refs/heads/lab/or/chimali`, empty `bypass_actors`, and `current_user_can_bypass=never`. Rules include deletion protection, non-fast-forward (force-push) protection, pull-request requirement, and required `coverage-integration` check with `integration_id=5488780`; `strict_required_status_checks_policy=true` and `do_not_enforce_on_create=false`. The pull-request approval count is `0`; the ruleset requires a PR but does not require a human approval. |
| `check_probe` | App-authenticated bootstrap probe check ID `121960503455`, name `coverage-integration`, App ID `5488780`, head SHA `997cc3b3ccab94f1f57f5b9c770e98c5b969118f`, conclusion `failure`. Its output explicitly states provisioning is incomplete and grants no coverage acceptance. This intentional failure keeps the required check unsatisfied until the trusted reporter can validate complete current evidence. |
| `date` | 2026-10-26 local (GitHub API timestamps are 2026-10-27 UTC). |
| `limitations` | The active ruleset establishes the requested App source binding and branch protections, but the check is intentionally failing. The disposable Android runner, protected reporter review/full-SHA pin, `coverage-reporting` environment and secrets, protected evidence archive, trial publication/retrieval, and controlled negative/forged-success/target-invalidation runs remain incomplete. No run was made passing and no task is complete. |
| `status` | T048 ruleset/source-binding configuration verified; T046–T049 remain unchecked. PR approval requirement is not enforced (configured count 0). |
| `artifact_link` | Ruleset: `https://github.com/octaviordz/chimali/rules/25926101`; check probe: `https://github.com/octaviordz/chimali/runs/121960503455`. |

### 11.23 Connected-device and provisioning checkpoint (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | T046 connected Android capability and reproducible host configuration; current T046–T049 operational readiness. |
| `scope` | Ran the repository's process-scoped host setup and device preflight. This validates the local physical device path; it does not provision or validate an isolated Actions runner. |
| `revision` | Local working tree based on `37e032608f5060c85fbfeaba696e8f663bf4b469`; setup contract from `tools/coverage/host-profile.json` and `tools/coverage/setup-host.ps1`. |
| `named_procedure` | `pwsh -NoProfile -File .\tools\coverage\setup-host.ps1 -DeviceSerial R7AIB7002901AZ3`. Bare `adb` is not on the shell's initial PATH; the setup script resolved the pinned SDK's platform-tools and performed the device check. |
| `environment` | Windows host; JetBrains JDK 21.0.10, Gradle 9.6.0, Android platform 37.0, build-tools 37.0.0, platform-tools 37.0.0. Setup scoped environment changes to its PowerShell process and children. |
| `expected_observed_result` | Setup exited successfully and reported Android device `R7AIB7002901AZ3` online and authorized at API 35, meeting the configured minimum API 28. |
| `date` | 2026-10-26 local. |
| `limitations` | The physical device is attached to this developer host; it is not evidence of a disposable isolated Windows/device Actions worker. No self-hosted runner, environment, environment secret, archive branch, reporter run, or controlled integration test was provisioned. The default branch remains subject to the active App-bound ruleset and its intentional failing probe (§11.22). |
| `status` | Local device/host capability verified; T046–T049 remain unchecked. |
| `artifact_link` | `tools/coverage/setup-host.ps1`; `tools/coverage/host-profile.json`; command output in this session. |

### 11.24 Persistent Windows/device Actions runner registration (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | T046 Windows/device runner availability and reproducible host configuration. The owner explicitly waived the disposable-runner aspect; that waiver does not waive device, reporter, environment, archive, credential-isolation, protection, or controlled-integration requirements. |
| `scope` | Registered the current Windows host as a repository-scoped GitHub Actions runner for `octaviordz/chimali`. Registration is persistent and non-ephemeral. It is started interactively; no Windows runner service or system-wide environment setting was installed. |
| `runner` | Name `chimali-coverage-device`; GitHub status `Idle` (available for jobs); labels `self-hosted`, `Windows`, `X64`, and `android-device`. Local runner version `2.338.0`; console reported `Connected to GitHub` and `Listening for Jobs`. GitHub Settings > Actions > Runners displayed the registered runner. |
| `host` | Runner package was downloaded from the official repository setup page and its SHA-256 matched the published value `f48e0750a21812bca5f82de5f7f5aeae71abee647fab5a582f1742d07eba455f`. Package and registration files are outside the repository under `%LOCALAPPDATA%\GitHubActionsRunner\chimali-coverage-v2.338.0`. Registration used the repository-generated short-lived token through the browser clipboard and a local script; the token was not committed or printed. |
| `environment` | `tools/coverage/setup-host.ps1 -DeviceSerial R7AIB7002901AZ3` succeeded in the runner's PowerShell process and reported JetBrains JDK 21.0.10, Gradle 9.6.0, Android SDK platform 37.0, build-tools 37.0.0, platform-tools 37.0.0, and authorized ASUS_AI2302 / Android API 35. The setup script scopes environment changes to that PowerShell process and its child processes. |
| `reproduction` | Added `tools/coverage/start-runner.ps1`, which validates local registration metadata, runs the existing process-scoped host setup for the requested serial, and starts `run.cmd`. Command: `pwsh -NoProfile -File .\tools\coverage\start-runner.ps1 -DeviceSerial R7AIB7002901AZ3`. |
| `credential_boundary` | The App key is stored as the `COVERAGE_APP_PRIVATE_KEY` secret in the `coverage-reporting` environment; App ID `5488780` and installation ID `175659848` are environment variables. The environment permits deployments only from exact branch pattern `lab/or/chimali`, and its setting to allow administrator bypass is off. The local PEM was permanently deleted from Downloads through File Explorer after user confirmation, and its Recycle Bin entry was permanently deleted; subsequent path and Recycle Bin metadata checks found no copy at that location. The runner was restarted after verifying its registration and device. |
| `workflow_configuration` | Local uncommitted changes now pin the capture and reporter harness checkouts to protected default-branch SHA `997cc3b3ccab94f1f57f5b9c770e98c5b969118f`, which was verified as the current `lab/or/chimali` head. Actions are pinned to the official repository commit SHAs: checkout `11d5960a326750d5838078e36cf38b85af677262`, setup-java `cf277c60eb25467037889841efdb72551f06f6c3`, upload-artifact `ea165f8d65b6e75b540449e92b4886f43607fa02`, and download-artifact `d3f86a106a0bac45b974a628896c90dbdf5c8093`. The report job remains on `windows-latest`; it reads App identifiers from environment variables and the private key from the environment secret. These local workflow changes are not yet on the protected default branch and have not run in GitHub Actions. |
| `other_controls` | `coverage.yml` uses the `self-hosted`, `windows`, `android-device` labels and a shared `coverage-runner-device` concurrency group. Ruleset `25930272` (`coverage-evidence-archive`) is Active for `codex/coverage-evidence`, has an empty bypass list, and enables Restrict deletions, Require linear history, and Block force pushes; Restrict creations and Restrict updates are off so initial branch creation and publisher fast-forward updates are allowed. GitHub currently reports 0 matching targets, so the archive branch does not yet exist. Current protected reporter provenance and artifact validation are not accepted until the workflow changes are reviewed on the default branch and operationally exercised. Trial publication and remote read-back remain unverified. |
| `date` | 2026-10-26 local (runner log timestamps are 2026-10-27 UTC). |
| `limitations` | The registered runner satisfies the waived runner-availability prerequisite, but it is a persistent host attached to the physical device and is not disposable or isolated. The environment and secret are configured, and the local key copy was removed; however, the workflow changes remain local/uncommitted. The protected archive ruleset exists but the branch does not, and trial publication/retrieval plus T049 negative/forged-success/target-invalidation demonstrations remain incomplete. The active `coverage-integration` check remains the intentionally failing bootstrap probe in §11.22. No T046–T049 task is complete. |
| `status` | Runner registration, current online/idle availability, device setup, and local PEM removal are verified. Archive and controlled-integration acceptance remain open; T046–T049 remain unchecked. |
| `artifact_link` | Runner setup: `https://github.com/octaviordz/chimali/settings/actions/runners`; `tools/coverage/start-runner.ps1`; `tools/coverage/setup-host.ps1`. |

### 11.25 Protected evidence-branch ruleset and current host check (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | T047 protected append-only evidence archive setup and T046 device-host readiness. |
| `archive_ruleset` | GitHub repository ruleset ID `25930272`, name `coverage-evidence-archive`, displayed Active at `https://github.com/octaviordz/chimali/settings/rules/25930272`. Target include is exact branch `codex/coverage-evidence`; bypass list is empty. Restrict deletions, Require linear history, and Block force pushes are enabled. Restrict creations and Restrict updates are disabled to permit initial publisher branch creation and normal fast-forward publication. The settings page reports 0 matching targets because the branch has not yet been created. |
| `host_check` | Re-ran `pwsh -NoProfile -File .\tools\coverage\setup-host.ps1 -DeviceSerial R7AIB7002901AZ3`; exit 0, JDK 21.0.10, Gradle 9.6.0, Android SDK/platform 37, build-tools/platform-tools 37.0.0, and the authorized ASUS_AI2302 / API 35 were reported. `Runner.Listener.exe` process query returned no running listener. |
| `credential_state` | The user confirmed deletion. File Explorer permanently deleted the matching PEM from Recycle Bin after it had been removed from Downloads. Read-only filesystem and Recycle Bin metadata checks then reported `LocalKeyExists=False` and `RecycleBinMetadataMatches=0`. |
| `validation` | PowerShell parser reported zero errors for `coverage-common.ps1`, `publish.ps1`, `report.ps1`, and `start-runner.ps1`; `git diff --check` passed. Full `tools/coverage/test-policy.ps1` run: 82 passed, 0 failed. The three new publication fixtures prove matching remote-ref read-back is accepted while a commit mismatch and a different branch ref are rejected. The publisher now verifies the remote branch SHA after push instead of checking only its local worktree SHA. No trial publication was possible, so this code path is not operationally verified. |
| `status` | Archive ruleset settings, local key removal, and runner restart are verified. Archive branch existence, trial bundle publication and retrieval, and all T049 controlled integration cases remain unverified. T046–T049 remain unchecked. |

### 11.26 App-key removal and runner resumed (2026-10-26)

Historical startup verification; the runner was subsequently disabled at the owner's request in §11.27.

| Evidence field | Verified record |
| --- | --- |
| `action` | After the user's explicit confirmation, File Explorer permanently deleted the App PEM from Recycle Bin. The UI showed the exact private-key PEM, 1.63 KB, original location `C:\Users\octav\Downloads`; Recycle Bin then displayed 0 items for the active query. A filesystem path check confirmed the prior Downloads path is absent, and a read-only scan of Recycle Bin metadata found no matching entry. |
| `runner` | Started using `pwsh -NoProfile -File .\tools\coverage\start-runner.ps1 -DeviceSerial R7AIB7002901AZ3`. Console reported host setup success, `Connected to GitHub`, runner version `2.338.0`, and `Listening for Jobs`. GitHub Settings > Actions > Runners displayed `chimali-coverage-device` as `Idle` with `self-hosted`, `Windows`, `X64`, and `android-device` labels. |
| `credential_boundary` | App credentials remain in the `coverage-reporting` environment, restricted to the exact `lab/or/chimali` branch pattern. No local PEM copy or Recycle Bin metadata entry remains at the checked locations. |
| `limitations` | The runner is persistent and shares the Windows account/device environment. Workflow changes remain local/uncommitted and have not bootstrapped on the protected default branch. The archive branch and real trial publication/read-back remain absent; T049 controlled missing/failing coverage, forged-success, and target-invalidation runs remain unperformed. |
| `status` | Key removal and runner online/idle availability are verified. T046–T049 remain unchecked. |

### 11.27 Persistent runner disabled; registration retained (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `owner_decision` | The owner reassessed the exposure of the Windows host and attached Android device and explicitly requested disabling the runner, then clarified that its registration must be retained. Do not re-enable it without explicit owner authorization. |
| `host_action` | Stopped the interactive runner session. A subsequent process query found 0 `Runner.Listener.exe` and 0 `Runner.Worker.exe` processes. No Windows service named `actions.runner*` or scheduled task with a matching runner/start-script action was found. |
| `startup_disabled` | In `%LOCALAPPDATA%\GitHubActionsRunner\chimali-coverage-v2.338.0`, preserved `run.cmd` as `run.cmd.disabled-by-owner` and added `.coverage-runner-disabled.json`. The `.runner` registration metadata remains present. `tools/coverage/start-runner.ps1` now rejects startup when that marker exists, before host setup or device access. |
| `github_state` | Refreshed GitHub Settings > Actions > Runners; `chimali-coverage-device` remained listed at runner ID `2` with status `Offline`. Its registration was not removed. |
| `validation` | Invoking the start script with the configured device serial was rejected with the owner-disabled message and exit code 1. PowerShell parsing reported 0 errors. A final process query still found 0 runner listener/worker processes. |
| `status` | This host is disabled for Actions execution and its registration is retained. Device runner availability is unmet. T046–T049 remain unchecked; the required integration gate has no available device runner from this host. |

### 11.28 Owner waiver of T046–T049 (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | T046 runner/reporter bootstrap; T047 trusted publisher/archive provisioning and trial retrieval; T048 App-bound integration protection; T049 controlled negative/forged-success/current-target demonstrations. |
| `scope` | The owner explicitly waived all four operational tasks. T089 repeats those requirements, so its operational scope is covered by the same exception. The hosted-control/archive/integration demonstrations in T108 are excluded; local tests, coverage floors, complete inventories, regression comparison, evidence retention, and final documentation remain active. |
| `revision` | Working documentation on `lab/or/chimali`, based on commit `89cd2d9`; no new commit or hosted run was made for the waiver. |
| `named_procedure` | Record task status as WAIVED, retain the original requirements and partial results, update spec/plan/validation/contract scope, and continue local evidence verification. Do not mark waived tasks as successfully implemented or tested. |
| `environment` | One available persistent Windows host and attached Android device. The owner reports no spare physical hardware or acceptable disposable Windows worker and disposable Android physical/virtual device; earlier Sandbox/Hyper-V investigations did not yield a usable replacement. |
| `expected_observed_result` | The owner rejects the attack surface of contributor-controlled code executing against the Windows account/files and connected Android device. The runner stays disabled with registration retained as verified in §11.27. T046–T049 are excluded from current execution under that explicit decision. |
| `date` | 2026-10-26, America/Mexico_City. |
| `limitations` | A waiver records accepted non-delivery, not proven hosted enforcement or publication. It supersedes the earlier disposability-only waiver, does not certify isolation, and does not permit a synthetic passing check or automatic runner restart. Local numerical and behavioral requirements remain unchanged. |
| `status` | T046–T049 WAIVED by owner. Their checkboxes remain unchecked to distinguish waiver from verification. T089 duplicate operational work follows the waiver; T088, T107, and local T108 acceptance remain open. |
| `artifact_link` | `tasks.md` owner-waiver section; `spec.md`; `plan.md`; `quickstart.md`; `contracts/coverage-workflow.md`; owner messages in this session. |

### 11.29 Coverage requirement removed and owner bypass enabled (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Apply the owner's separate instructions to remove only the coverage requirement and allow the owner's user account to bypass default-branch rules. These are operational policy changes following the T046–T049 waiver, not successful T048 verification. |
| `scope` | Edited existing ruleset `25926101`, `coverage-integration-default-branch`, applying to exact `refs/heads/lab/or/chimali`. No archive-ruleset change, runner restart, or App credential change was performed. |
| `revision` | Repository settings were read before and after the edit through GitHub's public ruleset API; saved bypass state was read through authenticated Settings UI after a full reload. |
| `named_procedure` | Removed the sole required-status-check rule, which required `coverage-integration` from App `5488780`. Added exact user `octaviordz` to the bypass list in `Always allow` mode; saved and reloaded the ruleset. |
| `environment` | GitHub repository `octaviordz/chimali`; authenticated owner's Edge session. The public read endpoint omits bypass actors, so it was used for rule verification and the authenticated reloaded UI was used for actor/mode verification. |
| `expected_observed_result` | API read-back shows enforcement `active`, the same exact target ref, and only `deletion`, `non_fast_forward`, and `pull_request` rule types. Pull-request parameters, including zero required approving reviews and allowed merge/squash/rebase methods, match the prior settings. Reloaded UI shows only `octaviordz` / Users / `Always allow` in the bypass list. |
| `date` | 2026-10-26, America/Mexico_City. |
| `limitations` | The coverage check is no longer a merge requirement. The owner can bypass all rules in this default-branch ruleset; other contributors remain subject to its PR/deletion/force-push restrictions. This grants no coverage acceptance and does not re-enable the device runner. The archive ruleset remains as previously provisioned. |
| `status` | Requested settings changes verified by API/UI read-back. T046–T049 remain WAIVED, not passed. |
| `artifact_link` | `https://github.com/octaviordz/chimali/settings/rules/25926101`; `https://api.github.com/repos/octaviordz/chimali/rulesets/25926101`; retained public API snapshot `D:/octav/coverage-local-validation/assessment-waiver-audit-b8c48f33ec0d4fd6a12d8b630e371c8e/ruleset-25926101-after-waiver.json`. |

### 11.30 Retained local capture revalidation after waiver (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Continue the local T088/T107/T108 evidence work after the operational waiver; preserve the exact measured revision and distinguish retained measurement from current-candidate integration. |
| `scope` | Revalidated existing sealed Initial bundle `5fad15f8-0604-46ea-b822-6b0997de4e26`; no candidate/device code or Actions job was executed. |
| `revision` | Measured source and local harness commit `997cc3b3ccab94f1f57f5b9c770e98c5b969118f`, tree `bc6b6f323d80fd2d8ce6785096b7bd20fe9529c1`; clean preflight/postflight recorded. Manifest SHA-256 remains `708382d35e6c32129e3c303b8e27043e3244d24b57d0217f9e5e4e3670c62583`. |
| `named_procedure` | Ran current `tools/coverage/verify.ps1` against the retained bundle and canonical policy, writing a new assessment outside the sealed bundle. |
| `environment` | Trusted local PowerShell execution on this Windows host; registered Actions runner remains disabled. The retained bundle has 2,932 declared artifacts and 200 recorded suites. |
| `expected_observed_result` | Verifier exit 0; collection Complete, tests Passed, floors Passed, `overall_passed=true`. All eight module decisions passed, including authenticator's inventory/zero-proof disposition. Raw FIDO2 instructions remain 54,452/86,114 and branches 3,333/5,526; repository instructions remain 74,125/132,917. |
| `date` | 2026-10-26, America/Mexico_City. This is a new assessment of the prior run, not new workload execution. |
| `limitations` | Comparison compatibility is Missing, regression NotApplicable, publication Pending, and integration Unverified. The local harness has recorded source identity, but no newly established independent/protected review or trusted accepted-target baseline. This audit does not by itself close T088/T107 or establish measurements for the current changed commit/tree. Security and FIDO2 remain below the nonbinding 65% branch goal. |
| `status` | Retained local evidence integrity and floors revalidated. T088/T107/local T108 remain open pending their full unwaived criteria; hosted criteria remain waived. |
| `artifact_link` | Bundle: `D:/octav/coverage-local-validation/5fad15f8-0604-46ea-b822-6b0997de4e26`; new assessment: `D:/octav/coverage-local-validation/assessment-waiver-audit-b8c48f33ec0d4fd6a12d8b630e371c8e/decision.json` and `decision.md`. |

### 11.31 Current Android SDK host-profile refresh (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Keep the local capture host reproducible against Android SDK package revisions actually available on the current machine, without weakening coverage policy or treating environment setup as coverage evidence. |
| `scope` | Updated `tools/coverage/host-profile.json` and `quickstart.md` to pin Command-line Tools 22.0 and platform-tools 37.0.1. The installed Android platform remains `android-37.0` revision 2 and build-tools 37.0.0. |
| `revision` | Working tree based on `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`; profile and guide edits are not committed. |
| `named_procedure` | Downloaded the official Windows Command-line Tools archive `commandlinetools-win-15859902_latest.zip`, checked its SHA-256 against Google's published `90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a`, installed it side-by-side as SDK package 22.0, then dot-sourced `tools/coverage/setup-host.ps1 -DeviceSerial R7AIB7002901AZ3`. Ran `tools/coverage/test-policy.ps1` and `git diff --check`. |
| `environment` | PowerShell 7.6.5; JetBrains JDK 21.0.10; Gradle 9.6.0; Android platform 37.0 revision 2; build-tools 37.0.0; platform-tools 37.0.1; authorized ASUS_AI2302, serial `R7AIB7002901AZ3`, Android API 35. |
| `expected_observed_result` | Host setup exited 0 and confirmed the pinned process-scoped SDK/JDK/Gradle/device environment. Policy fixtures passed 82/82 after restoring the two byte-hashed JaCoCo XML fixtures to their committed LF bytes; `git diff --check` passed. |
| `date` | 2026-10-26, America/Mexico_City. |
| `limitations` | No fresh capture was run: the tracked profile/documentation changes make the measured tree dirty, and `capture.ps1` requires a clean committed source revision. This setup record does not replace T088's fresh Initial baseline, T107's measured remediation, comparison/nonregression, or local T108 acceptance. The downloaded 37.0.0 archive was not installed; the existing SDK already has 37.0.1. |
| `status` | Host setup verified; T088, T107, and local T108 remain open. |
| `artifact_link` | `tools/coverage/host-profile.json`; `tools/coverage/setup-host.ps1`; `tools/coverage/test-policy.ps1`; [Android SDK downloads](https://developer.android.com/studio#command-line-tools-only); [Platform Tools release notes](https://developer.android.com/tools/releases/platform-tools). |

### 11.32 Clean Initial baseline on current coverage implementation (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | T088 / FR-006/007/016 / SC-004: capture fresh, clean, revision-bound host/device coverage with complete Tier 1 and repository inventories, test outcomes, zero proofs, and retained raw evidence. |
| `scope` | Complete `Initial` measurement using `tools/coverage/capture.ps1` and a separate clean local harness checkout at the same revision. All five Tier 1 modules, repository production scope, configured Tier 2 scopes, configured host/device test workload, canonical counters, inventory, raw reports, test reports, and execution data were captured. |
| `revision` | Source commit `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`; source tree `5c0ed87b07424a3e572bc7328d1136caa2786280`. Clean preflight and postflight both passed. Harness commit/tree match the source; captured harness-file SHA-256 values are retained in `manifest.json`. Manifest SHA-256: `5d6fe97c2cacf0fb3d1fd4b7e1eecb321682e2aa2d765cb97422c74d904c7ed4`. |
| `named_procedure` | Dot-sourced the validated process-scoped Java/Android environment, then ran `tools/coverage/capture.ps1 -RepositoryRoot <repo> -SourceRevision fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2 -Purpose Initial -HarnessRoot D:/temp/chimali-coverage-harness-fa93 -DeviceSerial R7AIB7002901AZ3 -OutputRoot D:/octav/coverage-local-validation/runs`. Ran `tools/coverage/verify.ps1` against the resulting bundle; verifier exit code 0. |
| `environment` | Windows 10.0.26300.0; PowerShell 7.6.5; JetBrains Java 21.0.10; Gradle 9.6.0; JaCoCo 0.8.15; Android SDK platform 37.0 revision 2; build-tools 37.0.0; Command-line Tools 22.0; platform-tools 37.0.1. Authorized physical ASUS_AI2302, serial `R7AIB7002901AZ3`, Android API 35, `arm64-v8a`. The capture manifest's environment string lists JVM, SDK platform directories, and device; this record adds exact SDK package revisions. |
| `expected_observed_result` | Run `83ccf4f6-e85c-4db4-8476-1ee0b349c284`, 2026-10-27 04:20:51–04:26:17 UTC. Collection Complete; required tests Passed: 202 suites / 1,271 tests, 0 failures, 0 errors, 0 skipped. Verifier: overall pass, collection/tests/floors Passed. Tier 1 raw counts: security 3,385/4,452 instructions (76.033%) and 239/386 branches (61.917%); domain 5,124/7,184 (71.325%) and 412/578 (71.280%); data 1,158/1,333 (86.872%) and 44/60 (73.333%); FIDO2 54,452/86,114 (63.232%) and 3,333/5,526 (60.315%); authenticator 0/0 with verifier-confirmed `NotApplicable` and bound zero proof. Repository: 74,125/132,917 instructions (55.768%) and 4,609/8,010 branches (reported; no repository branch floor). All Tier 2 instruction floors passed. The nonbinding 65% branch goal remains short by security 3.083 percentage points and FIDO2 4.685 points. |
| `date` | 2026-10-26, America/Mexico_City (run timestamps are 2026-10-27 UTC). |
| `limitations` | Generated SQLDelight classes follow the reviewed exact-pattern exclusions (20 database patterns and 40 FIDO2 patterns); native iOS remains outside the measured scope. This is an Initial run: comparison compatibility is Missing, regression is NotApplicable, publication is Pending, and integration is Unverified. It does not establish changed-module nonregression or satisfy T107/T108. Source revision `fa93a40` predates the pending tracked host-profile documentation correction; the actual SDK package versions used are recorded above and the corrected profile/quickstart are included in the current working changes. |
| `status` | T088 complete: fresh clean measurement and floor verification passed. T107 remains open for final risk-batch/nonregression accounting; T108 remains open for current-target comparison, local regression matrix, and final evidence reconciliation. T046–T049 remain waived, not passed. |
| `artifact_link` | Bundle: `D:/octav/coverage-local-validation/runs/83ccf4f6-e85c-4db4-8476-1ee0b349c284`; verifier assessment: `D:/octav/coverage-local-validation/assessments/fa93a40-initial/decision.json` and `decision.md`; separate harness: `D:/temp/chimali-coverage-harness-fa93`. |

### 11.33 Full local CI after baseline and host-profile refresh (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Continue local T107/T108 acceptance by running the complete repository local CI, including host and connected-device coverage collection and floor verification, without claiming hosted integration. |
| `scope` | Ran `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` on the current working tree, with the authorized ASUS_AI2302 device connected. The pipeline included lint, release lint, all compilation, unit and Android host tests, connected Android device tests, repository/module coverage analysis and `hdkCoverageVerify`. |
| `revision` | HEAD `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`; working tree additionally contained only the documented evidence, quickstart, task-status and host-profile edits. This is a local diagnostic on that dirty worktree, not a sealed candidate capture. |
| `named_procedure` | Dot-sourced `tools/coverage/setup-host.ps1 -DeviceSerial R7AIB7002901AZ3`; ran the command above to completion. Captured process exit code and `tools/local-ci.ps1` stage summary. |
| `environment` | Windows 10.0.26300.0; PowerShell 7.6.5; JetBrains Java 21.0.10; Gradle 9.6.0; Android platform 37.0 revision 2; build-tools 37.0.0; Command-line Tools 22.0; platform-tools 37.0.1; authorized ASUS_AI2302, Android API 35, `arm64-v8a`. |
| `expected_observed_result` | Exit code 0. Local CI reported success in 1,106.9 seconds; the coverage gate passed in 315.57 seconds. `hdkCoverageAnalyze` wrote canonical counters and production inventory, and `hdkCoverageVerify` passed all configured floors. Compilation, unit tests, Android host tests and connected-device tests passed. |
| `date` | 2026-10-26, America/Mexico_City. |
| `limitations` | The local CI output does not provide a sealed bundle, exact complete-suite test count, immutable reports or a current-target regression comparison. Its worktree was dirty, so this run cannot replace the clean captured Initial baseline or be promoted as final candidate evidence. Hosted integration, protected controls and publication remain waived/unverified. |
| `status` | Full local CI and coverage floors verified. T107 still needs risk-batch receipts and changed-Tier-1 nonregression against a compatible trusted target; T108 still needs a frozen clean candidate, compatible target comparison, and final evidence reconciliation. T046–T049 remain waived, not passed. |
| `artifact_link` | Local reports: `build/reports/hdk-coverage/`; clean Initial bundle and assessment: §11.32. |

### 11.34 Security coverage boundary batch diagnostic (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Continue T107's risk-ranked test selection and verify the effect of additional Tier 1 security cases against fresh host/device coverage counters. |
| `scope` | Added four HKDF tests for zero and maximum RFC output length, malformed PRK/negative length, empty/non-ASCII labels, and incomplete BIP39 seeds; added two HdkP256 tests for public-only derivation/scalar composition and malformed local-secret lengths. No production behavior was changed. |
| `revision` | Based on HEAD `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`; the working tree was dirty with the four new test functions and existing documentation/profile edits. This run is diagnostic only and has no sealed source-tree identity. |
| `named_procedure` | Focused command `:core:security:testAndroidHostTest --tests 'com.chimali.core.security.hdkeys.HkdfSha256Test' --tests 'com.chimali.core.security.hdkeys.HdkP256Test'` passed. Then ran `tools/coverage/setup-host.ps1 -DeviceSerial R7AIB7002901AZ3` and `gradlew --no-configuration-cache -I tools/hdk-coverage.init.gradle hdkHostCoverage hdkCoverageAnalyze hdkCoverageVerify`; process exit code 0. |
| `environment` | Windows 10.0.26300.0; PowerShell 7.6.5; JetBrains Java 21.0.10; Gradle 9.6.0; Android platform 37.0 revision 2; build-tools 37.0.0; Command-line Tools 22.0; platform-tools 37.0.1; authorized ASUS_AI2302, Android API 35, `arm64-v8a`. |
| `expected_observed_result` | Focused reports: HdkP256 6 tests, HKDF 9 tests, zero failures/errors in both. Full configured host/device coverage workload and `hdkCoverageVerify` completed successfully. Tier 1 raw counters: security 3,624/4,452 instructions (81.400%) and 258/386 branches (66.839%); domain 5,124/7,184 (71.325%) and 412/578 (71.280%); data 1,158/1,333 (86.872%) and 44/60 (73.333%); FIDO2 54,452/86,114 (63.232%) and 3,333/5,526 (60.315%); authenticator 0/0 with verifier-confirmed `AnalyzedZeroProofV1`. Repository instructions 74,166/132,917 (55.799%); repository branches 4,621/8,010 (reported, no floor). All configured Tier 2 and Tier 1 hard floors passed. Security now exceeds the approximate 65% branch goal; FIDO2 is 259 covered branches short of 65%. After correcting one ktlint formatting issue, `tools/local-ci.ps1 -NoFix -NoConfigurationCache` passed lint, Detekt, release lint, full compilation, unit tests, and configured Android host suites in 381.96 seconds. |
| `date` | 2026-10-26, America/Mexico_City. |
| `limitations` | The security count improved from 249 to 258 covered branches between the intermediate and final dirty-tree diagnostics; the individual case contributions were not isolated. The current counters are from an unsealed dirty-tree diagnostic, not a clean candidate capture or trusted target comparison. The clean Initial run in §11.32 predates these test-only edits. |
| `status` | Both focused suites and the full coverage gate passed; current hard floors remain satisfied, and security reached the approximate branch goal. T107 remains open for FIDO2 goal shortfall reporting in the final risk ledger and valid changed-Tier-1 target comparison. T108 remains open for clean candidate/current-target comparison and final evidence reconciliation. |
| `artifact_link` | Focused JUnit XML: `core/security/build/test-results/testAndroidHostTest/TEST-com.chimali.core.security.hdkeys.HkdfSha256Test.xml` and `...HdkP256Test.xml`; current diagnostic reports/counters: `core/security/build/reports/hdk-coverage/`, `build/reports/hdk-coverage/counters.json`. |

### 11.35 Legacy sensitive CTAP request value diagnostic (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Continue T107's risk-ranked FIDO2 protocol assertions and fresh connected-device coverage measurement after adding a regression case for the legacy sensitive-text CTAP representation. |
| `scope` | Added COV-020 in `Ctap2GetAssertionHandlerTest.kt`: decode a base64 `clientDataHash` owner, verify the use case receives the exact 32-byte digest and normalized RP ID, then assert source RP/hash owners are erased after request completion. No production code changed. |
| `revision` | Based on HEAD `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`; working tree was dirty with the COV-020 test and previous test/document/profile changes. This diagnostic has no sealed source-tree identity. |
| `named_procedure` | Focused `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.ctap2.Ctap2GetAssertionHandlerTest'` passed (14 tests, zero failures/errors); `:feature:fido2:ktlintAndroidHostTestSourceSetCheck` passed. Ran the full `hdkHostCoverage hdkCoverageAnalyze hdkCoverageVerify` pipeline with the configured ASUS device; exit code 0. |
| `environment` | Windows 10.0.26300.0; PowerShell 7.6.5; JetBrains Java 21.0.10; Gradle 9.6.0; Android platform 37.0 revision 2; build-tools 37.0.0; Command-line Tools 22.0; platform-tools 37.0.1; authorized ASUS_AI2302, Android API 35, `arm64-v8a`. |
| `expected_observed_result` | All configured host/device test and coverage tasks completed successfully; `hdkCoverageVerify` passed. Tier 1 raw counters: security 3,624/4,452 instructions (81.400%) and 258/386 branches (66.839%); domain 5,124/7,184 (71.325%) and 412/578 (71.280%); data 1,158/1,333 (86.872%) and 44/60 (73.333%); FIDO2 54,465/86,114 (63.247%) and 3,335/5,526 (60.351%); authenticator 0/0 with verifier-confirmed `AnalyzedZeroProofV1`. Repository instructions 74,179/132,917 (55.809%); repository branches 4,621/8,010 (reported, no floor). All configured hard floors passed. FIDO2 is 257 covered branches short of the nonbinding 65% goal. |
| `date` | 2026-10-26, America/Mexico_City. |
| `limitations` | The diagnostic is unsealed and dirty-tree; it does not prove a clean final candidate or nonregression against a trusted accepted-target bundle. The +2 FIDO2 branch / +13 instruction difference from §11.34 is a cumulative single-test diagnostic delta, not proof of isolated statement-level causality. |
| `status` | COV-020 focused assertion, lint, and full host/device coverage gate passed. Hard floors remain satisfied; the FIDO2 65% goal is still short. T107 remains open for complete clean-candidate reranking and trusted changed-module comparison; T108 remains open for final clean current-target measurement/comparison and evidence reconciliation. |
| `artifact_link` | Focused JUnit XML: `feature/fido2/build/test-results/testAndroidHostTest/TEST-com.chimali.fido2.ctap2.Ctap2GetAssertionHandlerTest.xml`; current module report: `feature/fido2/build/reports/hdk-coverage/hdkModuleCoverage.xml`; canonical counters: `build/reports/hdk-coverage/counters.json`. |

### 11.36 Fresh clean current-target comparison capture (2026-10-26)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Establish a clean, full connected-device measurement of the configured integration target and verify compatibility/regression controls before assessing candidate changes (T107/T108; FR-006/007/016; SC-004/006). |
| `scope` | Captured exact configured target `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2` from a detached clean worktree. The capture includes all required host and connected-device suites, all five Tier 1 inventories, Tier 2 scope, repository aggregate, raw report/test artifacts and bound zero proof. Compared it with clean Initial run `83ccf4f6-e85c-4db4-8476-1ee0b349c284` from the same source revision. |
| `revision` | Source and target commit `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`; tree `5c0ed87b07424a3e572bc7328d1136caa2786280`. Clean preflight/postflight passed. Harness commit/tree match source. Manifest SHA-256 `869c86f5bc549bb7306aa01d6d28465dc52a1e9d6755aa9d0ed47cc0d2dec649`. |
| `named_procedure` | Ran `tools/coverage/capture.ps1 -RepositoryRoot D:/temp/chimali-target-fa93 -SourceRevision fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2 -Purpose Comparison -TargetRevision fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2 -HarnessRoot D:/temp/chimali-coverage-harness-fa93 -DeviceSerial R7AIB7002901AZ3 -OutputRoot D:/octav/coverage-local-validation/runs`. Then ran `tools/coverage/verify.ps1` with that bundle, the §11.32 Initial bundle, policy `tools/coverage/policy.json`, and output directory `D:/octav/coverage-local-validation/assessments/fa93-current-target`; both commands exited 0. |
| `environment` | Windows 10.0.26300.0; PowerShell 7.6.5; JetBrains Java 21.0.10; Gradle 9.6.0; JaCoCo 0.8.15; Android platform 37.0 revision 2; build-tools 37.0.0; Command-line Tools 22.0; platform-tools 37.0.1; connected ASUS_AI2302, serial `R7AIB7002901AZ3`, API 35, `arm64-v8a`. |
| `expected_observed_result` | Comparison run `10b510ad-4638-4107-9fe6-91efdbbf9827`, 2026-10-27 05:23:35–05:44:08 UTC. Collection Complete; 200 suites / 1,267 tests, 0 failures, 0 errors, 0 skipped. Verifier decision: overall PASS; collection/tests/floors Passed, compatibility Compatible, regression Passed. Tier 1 raw counters: security 3,385/4,452 instructions and 239/386 branches; domain 5,124/7,184 and 412/578; data 1,158/1,333 and 44/60; FIDO2 54,452/86,114 and 3,333/5,526; authenticator 0/0 with bound `AnalyzedZeroProofV1`. Repository 74,125/132,917 instructions and 4,609/8,010 branches. These counters match the clean same-revision Initial run. |
| `date` | 2026-10-26, America/Mexico_City (UTC capture timestamps cross midnight to 2026-10-27). |
| `limitations` | This is a clean baseline of the configured target, not a capture of the dirty candidate. It establishes a comparison reference but does not prove candidate nonregression, publication/read-back, protected integration, or final T108 acceptance. The verifier reports publication Pending and integration Unverified. |
| `status` | Current-target clean capture and same-revision compatibility/regression assessment passed. T107 remains open for clean-candidate reranking and changed-Tier-1 comparison. Local T108 remains open for frozen clean candidate measurement, final evidence reconciliation and operational acceptance. Hosted T046–T049 remain waived by the owner (§11.28). |
| `artifact_link` | `D:/octav/coverage-local-validation/runs/10b510ad-4638-4107-9fe6-91efdbbf9827/manifest.json`; `D:/octav/coverage-local-validation/assessments/fa93-current-target/decision.md`; prior same-revision Initial bundle in §11.32. |

### 11.37 Partially initialized passkey snapshot validation diagnostic (2026-10-27)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Validate malformed persisted aggregate snapshots fail closed, erase sensitive owners, and recover authoritative history (T107; FR-009/010/020; SC-005). |
| `scope` | Added COV-021 in the separate `PasskeyAggregateSnapshotValidationTest.kt` so the existing service test class remains within Detekt's class-size limit. The test creates partial initial-state snapshots by populating each of the 13 persisted state fields independently, confirms replay from the event store returns the authoritative registered state, verifies sensitive character fields are zeroed, and confirms snapshot suffix replay is not used. |
| `revision` | Commit `94bbdc5de2651e728229b7cf0b115de1da05b204` plus subsequent COV-022 uncommitted test edit. Source changes for COV-021 are committed; no sealed bundle was created for this diagnostic. |
| `named_procedure` | Focused `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.data.eventsourcing.PasskeyAggregateSnapshotValidationTest'`, `:feature:fido2:ktlintAndroidHostTestSourceSetCheck`, and `:feature:fido2:detekt` passed after the split. Ran `setup-host.ps1 -DeviceSerial R7AIB7002901AZ3` then `hdkHostCoverage hdkCoverageAnalyze hdkCoverageVerify`; exit code 0. The same full run included COV-022 after it was added. |
| `environment` | Windows 10.0.26300.0; PowerShell 7.6.5; JetBrains Java 21.0.10; Gradle 9.6.0; JaCoCo 0.8.15; Android platform 37.0 revision 2; build-tools 37.0.0; Command-line Tools 22.0; platform-tools 37.0.1; connected ASUS_AI2302, serial `R7AIB7002901AZ3`, API 35, `arm64-v8a`. |
| `expected_observed_result` | Focused service suite passed 15 tests, zero failures/errors; lint passed. Full connected-device coverage pipeline and `hdkCoverageVerify` passed. Compared with the immediately preceding COV-020 diagnostic, FIDO2 increased by 20 covered branches and 8 covered instructions, to the COV-021 checkpoint of 3,355/5,526 branches. |
| `date` | 2026-10-27, America/Mexico_City. |
| `limitations` | The full pipeline ran while COV-021's commit was clean, but it was invoked as a direct Gradle diagnostic and produced no immutable run bundle or target comparison. The current combined diagnostic also contains COV-022; COV-021's interim counters were read before adding that test. |
| `status` | COV-021 focused behavioral checks, lint, Detekt, and hard coverage gates passed. The case measurably advances FIDO2 protocol-state coverage; clean final candidate capture and changed-module regression remain open. |
| `artifact_link` | `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/eventsourcing/PasskeyAggregateSnapshotValidationTest.kt`; JaCoCo report `feature/fido2/build/reports/hdk-coverage/hdkModuleCoverage.xml`; canonical counters `build/reports/hdk-coverage/counters.json`. |

### 11.38 Mnemonic recovery decoding boundary diagnostic (2026-10-27)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Ensure encrypted wallet mnemonic recovery rejects malformed encodings and erases decrypted byte owners on failure (T107; NFR-SEC-041; TR-AUTH-031). |
| `scope` | Added COV-022 in `WalletMasterSeedProviderTest.kt`: invalid Base64 ciphertext is rejected; empty decrypted plaintext is rejected and erased; malformed UTF-8 is rejected and its returned plaintext buffer is erased. |
| `revision` | Commit `94bbdc5de2651e728229b7cf0b115de1da05b204` plus the uncommitted COV-022 test. The source identity is not sealed in a bundle. |
| `named_procedure` | Focused `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.data.crypto.WalletMasterSeedProviderTest'` and `:feature:fido2:ktlintAndroidHostTestSourceSetCheck` passed. Re-ran the full configured host/device pipeline using `hdkHostCoverage hdkCoverageAnalyze hdkCoverageVerify`; exit code 0. |
| `environment` | Windows 10.0.26300.0; PowerShell 7.6.5; JetBrains Java 21.0.10; Gradle 9.6.0; JaCoCo 0.8.15; Android platform 37.0 revision 2; build-tools 37.0.0; Command-line Tools 22.0; platform-tools 37.0.1; connected ASUS_AI2302, serial `R7AIB7002901AZ3`, API 35, `arm64-v8a`. |
| `expected_observed_result` | Focused provider suite passed 16 tests, zero failures/errors; lint passed. Full connected-device coverage pipeline and `hdkCoverageVerify` passed. Latest Tier 1 counters: security 3,624/4,452 instructions and 258/386 branches; domain 5,124/7,184 and 412/578; data 1,158/1,333 and 44/60; FIDO2 54,496/86,114 (63.284%) and 3,360/5,526 (60.803%); authenticator 0/0 with verifier-confirmed `AnalyzedZeroProofV1`. Repository 74,210/132,917 instructions (55.825%) and 4,648/8,010 branches. All hard module/repository floors passed. FIDO2 is 232 covered branches below the 65% goal (3,592 required). |
| `date` | 2026-10-27, America/Mexico_City. |
| `limitations` | This is an unsealed dirty-tree diagnostic; COV-022 has not been committed or compared against the clean current-target reference. The 65% goal remains unmet. No causal attribution is claimed beyond the successive diagnostic delta because this is a shared JaCoCo execution-data pipeline. |
| `status` | COV-022 focused behavioral checks, lint, all hard floors and full host/device coverage gate passed. T107 remains open for complete candidate reranking and changed-Tier 1 regression; local T108 acceptance remains open. |
| `artifact_link` | `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/data/crypto/WalletMasterSeedProviderTest.kt`; JaCoCo report `feature/fido2/build/reports/hdk-coverage/hdkModuleCoverage.xml`; canonical counters `build/reports/hdk-coverage/counters.json`. |

### 11.39 Typed hmac-secret unavailable-seed response diagnostic (2026-10-27)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | The CTAP assertion handler must omit the optional `hmac-secret` response extension when no credential seed can produce its output, while retaining the ordinary assertion response (T107; FR-009/010; NFR-SEC-041). |
| `scope` | Added COV-023 to `Ctap2GetAssertionHandlerTest.kt`. A typed PRF request whose derivation service returns no output produces only authData and signature fields; extension encoding is not invoked. Test-owned buffers are erased after assertions. |
| `revision` | Based on committed test/document baseline `94bbdc5de2651e728229b7cf0b115de1da05b204` plus uncommitted COV-022 and COV-023 edits and evidence/ledger updates. No sealed bundle exists for this diagnostic. |
| `named_procedure` | Focused `:feature:fido2:testAndroidHostTest --tests 'com.chimali.fido2.ctap2.Ctap2GetAssertionHandlerTest'` and `:feature:fido2:ktlintAndroidHostTestSourceSetCheck` passed (15 test cases, zero failures/errors). Then ran the full configured host/device pipeline `hdkHostCoverage hdkCoverageAnalyze hdkCoverageVerify`; exit code 0. Also ran `tools/local-ci.ps1 -NoFix -NoConfigurationCache` after splitting COV-021 into its own test class; all local CI stages passed in 187.14 seconds. |
| `environment` | Windows 10.0.26300.0; PowerShell 7.6.5; JetBrains Java 21.0.10; Gradle 9.6.0; JaCoCo 0.8.15; Android platform 37.0 revision 2; build-tools 37.0.0; Command-line Tools 22.0; platform-tools 37.0.1; connected ASUS_AI2302, serial `R7AIB7002901AZ3`, API 35, `arm64-v8a`. |
| `expected_observed_result` | Focused test and lint passed. Full host/device pipeline and `hdkCoverageVerify` passed. Latest Tier 1 counters: security 3,624/4,452 instructions and 258/386 branches; domain 5,124/7,184 and 412/578; data 1,158/1,333 and 44/60; FIDO2 54,508/86,114 (63.298%) and 3,362/5,526 (60.839%); authenticator 0/0 with verifier-confirmed `AnalyzedZeroProofV1`. Repository 74,222/132,917 instructions (55.834%) and 4,650/8,010 branches. All hard floors passed. FIDO2 is 230 covered branches short of 65% (3,592 required). |
| `date` | 2026-10-27, America/Mexico_City. |
| `limitations` | This is a dirty-tree diagnostic without a sealed candidate or current-target regression assessment. The nonbinding 65% branch goal remains unmet. |
| `status` | COV-023 focused assertion, lint and full coverage gates passed; all fixed floors remain satisfied. T107 is still open for clean-candidate reranking/comparison. T108 is open for local acceptance and final evidence reconciliation. |
| `artifact_link` | `feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/ctap2/Ctap2GetAssertionHandlerTest.kt`; JaCoCo report `feature/fido2/build/reports/hdk-coverage/hdkModuleCoverage.xml`; canonical counters `build/reports/hdk-coverage/counters.json`. |

### 11.40 Clean candidate coverage and current-target regression assessment (2026-10-27)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Close T107's clean candidate measurement, complete Tier 1/repository floor accounting, and changed-Tier 1 regression assessment; retain the risk-goal shortfall explicitly (FR-006/007/009/010/016/020; SC-001/002/004/005/006). |
| `scope` | Full `Comparison` candidate capture of all five Tier 1 inventories, configured Tier 2 and repository production scope, host/device tests, JaCoCo reports/execution data, retained artifact hashes, ten-field evidence records, and authenticator zero proof. Candidate compared against the separately captured clean current target; target itself was previously verified compatible with the same-revision Initial run. |
| `revision` | Candidate/source commit `6a892bc93a9ad62253b3dc2f8da60fe308e86fd1`; source tree `83057f1b146c5d177b2c2847fd8ec9db81e60e1d`; target commit `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`. Clean preflight and postflight both passed. Harness revision/tree matched candidate and was clean. Manifest SHA-256 `faf0b92c0de385d5735b14040d9e6e8f14d8c9b23320142bcb61c826d15db7a9`. |
| `named_procedure` | Ran `setup-host.ps1 -DeviceSerial R7AIB7002901AZ3`, then `capture.ps1` with `-Purpose Comparison`, exact source SHA, target SHA, clean harness checkout, and external run output root. Ran `verify.ps1` against current-target run `10b510ad-4638-4107-9fe6-91efdbbf9827` and policy `tools/coverage/policy.json`; both commands exited 0. The same source revision had passed `tools/local-ci.ps1 -NoFix -NoConfigurationCache -RunCoverage` before capture (158.47 seconds). |
| `environment` | Windows 10.0.26300.0; PowerShell 7.6.5; JetBrains Java 21.0.10; Gradle 9.6.0; JaCoCo 0.8.15; Android platform 37.0 revision 2; build-tools 37.0.0; Command-line Tools 22.0; platform-tools 37.0.1; authorized ASUS_AI2302, serial `R7AIB7002901AZ3`, API 35, `arm64-v8a`. |
| `expected_observed_result` | Run `5d12eb03-7e25-44c1-a10f-bc7c69c461d7`, 2026-10-27 06:12:42–06:14:48 UTC. 203 suites / 1,281 tests; 0 failures, errors, or skips. Verifier overall PASS: collection/tests/floors Passed, compatibility Compatible, regression Passed. Tier 1 raw counters: security 3,624/4,452 instructions and 258/386 branches; domain 5,124/7,184 and 412/578; data 1,158/1,333 and 44/60; FIDO2 54,508/86,114 (63.298%) and 3,362/5,526 (60.839%); authenticator 0/0 with `AnalyzedZeroProofV1`. Repository 74,222/132,917 instructions (55.834%) and 4,650/8,010 branches. All fixed hard floors passed. Versus target, security gained 239 covered instructions and 19 branches; FIDO2 gained 56 instructions and 29 branches; other Tier 1 counters were unchanged. FIDO2 is 46 branches above its fixed 60% floor and 230 below the approximate 65% goal. The external manifest contains nine evidence records with all ten required fields, 3,378 retained artifacts, and one bound zero proof. |
| `date` | 2026-10-27, America/Mexico_City. |
| `limitations` | The verifier correctly reports publication Pending and integration Unverified. Hosted T046–T049 are waived by the owner (§11.28); this record claims local collection, floors, compatibility, and regression only. The local integration wrapper attempt on this revision exposed a result-parser bug after capture; that wrapper was corrected separately and still requires a clean-revision rerun. The local pre-push negative checks are recorded in §11.41. |
| `status` | T107's clean-candidate accounting and changed-Tier 1 regression check pass. All required hard floors pass; the approximate 65% FIDO2 goal remains 230 branches short and is documented without changing thresholds or exclusions. |
| `artifact_link` | `D:/octav/coverage-local-validation/runs/5d12eb03-7e25-44c1-a10f-bc7c69c461d7/manifest.json`; `D:/octav/coverage-local-validation/assessments/6a892-current-target/decision.md`; target manifest/assessment in §11.36. |

### 11.41 Local pre-push prerequisite negative checks (2026-10-27)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Demonstrate the installed local pre-push path fails closed when no target is configured or a configured target cannot be resolved, without invoking local CI for invalid targets (T108; FR-012/013; SC-003). |
| `scope` | Executed the real `tools/coverage/pre-push-hook.ps1` from an isolated clone at candidate SHA `6a892bc93a9ad62253b3dc2f8da60fe308e86fd1`. Case 1 removed target-related environment configuration and used an empty global Git config; case 2 configured nonexistent `refs/heads/does-not-exist`, the authorized device serial, and a disposable external evidence path. No remote push was made. |
| `revision` | Probe clone source commit `6a892bc93a9ad62253b3dc2f8da60fe308e86fd1`; working tree remained the clean committed revision. Clone path `D:/temp/chimali-prepush-probe`. |
| `named_procedure` | Piped a valid pre-push ref update to `pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/pre-push-hook.ps1` in the isolated clone. Missing-target case exited 1 with `No integration target configured`. Unresolvable-target case exited 1 through `integration-ci.ps1` before `[Step 1/3]` local checks, with target/ref-resolution failure. |
| `environment` | Windows 10.0.26300.0; PowerShell 7.6.5; Git; isolated local clone; device serial `R7AIB7002901AZ3` configured only for the stale-target case. The disposable evidence path was outside both source checkouts. |
| `expected_observed_result` | Both negative cases returned exit code 1 before any integration test run. Missing target failed with actionable configuration guidance; stale/unresolvable target failed before candidate local CI. The clone and its config isolated all probe settings from the user's main Git configuration. |
| `date` | 2026-10-27, America/Mexico_City. |
| `limitations` | These checks demonstrate fail-closed missing/unresolvable target behavior only; they do not establish hosted protection or publication, and no actual network push was attempted. The wrapper's successful capture-result parsing remains pending a fresh clean commit after its parser fix. |
| `status` | Two real local pre-push negative cases passed. Hosted publication/protection remains unverified under the owner waiver. |
| `artifact_link` | `tools/coverage/pre-push-hook.ps1`; `tools/integration-ci.ps1`; `D:/temp/chimali-prepush-probe`; parser regression fixtures in `tools/coverage/fixtures/capture-result.json`. |

### 11.42 Device candidate capture and wrapper blank-line parser diagnostic (2026-10-27)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Complete T108 local integration verification with a clean exact-revision candidate, full connected-device coverage capture, all fixed coverage floors, and changed-module comparison; wrapper must parse capture output containing ordinary blank log lines (FR-016/017/020; SC-001-SC-008). |
| `scope` | Ran the real `tools/integration-ci.ps1` against current target `origin/lab/or/chimali`, with local CI, full host/device coverage, retained reports, all Tier 1 modules, configured Tier 2 modules and repository aggregate. The wrapper failed after capture when PowerShell rejected a blank capture-output line for the parser's `[string[]]` parameter. Independently ran the produced bundle through `verify.ps1` with the clean current-target baseline. Added `[AllowEmptyString()]` to the parser parameter and included a blank log line in its parser fixture; focused and full policy fixtures pass. |
| `revision` | Capture candidate commit `281e4549307bc41c63b90b680dbe8831e29574cb`, tree `2feb6cd0556f1d43bdd65fdf36d07c5551d938c5`; target `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`. Candidate and harness worktrees were clean at the same revision/tree before capture and passed clean preflight/postflight. Parser blank-line fix is pending in the next manual commit. |
| `named_procedure` | `setup-host.ps1 -DeviceSerial R7AIB7002901AZ3`; actual `integration-ci.ps1` invocation; then direct `verify.ps1 -RunBundle ... -ComparisonBundle ...` because the wrapper stopped before invoking its verifier. The wrapper's Step 1 Local CI passed (32.78 sec). Candidate run `c498330c-a113-4a4e-8859-9816d76f5172` completed in 2m11s with 203 suites / 1,281 tests, zero failures/errors/skips, 3,378 retained artifacts and `collection=Complete`, `tests=Passed`. Direct independent verification returned Overall PASS: collection, tests, floors, compatibility and regression Passed. Focused parser fixtures passed 4/4; complete policy suite passed 86/86; PowerShell parse checks and `git diff --check` passed after the parser correction. |
| `environment` | Windows 10.0.26300.0; PowerShell 7.6.5; JetBrains Java 21.0.10; Gradle 9.6.0; JaCoCo 0.8.15; Android SDK platform 37.0 revision 2; build-tools 37.0.0; platform-tools 37.0.1; connected ASUS_AI2302 serial `R7AIB7002901AZ3`, API 35, `arm64-v8a`. |
| `expected_observed_result` | Candidate counters: security 3,624/4,452 instructions and 258/386 branches; domain 5,124/7,184 and 412/578; data 1,158/1,333 and 44/60; FIDO2 54,508/86,114 and 3,362/5,526; authenticator 0/0 with `AnalyzedZeroProofV1`. Repository 74,222/132,917 instructions (55.841%) and 4,650/8,010 branches. Direct verifier decision at `D:/octav/coverage-local-validation/assessments/281e454-capture/decision.json` is Overall PASS, with regression Passed. Wrapper invocation itself exited 2 after capture due to the blank-line parameter-binding error. |
| `date` | 2026-10-27, America/Mexico_City. |
| `limitations` | The direct bundle verification validates the produced clean candidate's floors and regression but does not substitute for a successful `integration-ci.ps1` run. The corrected blank-line parser remains uncommitted; the wrapper must be rerun against a fresh clean committed revision. Publication is Pending and hosted integration is Unverified under the T046–T049 owner waiver. |
| `status` | Full candidate capture and independent verification pass. Wrapper correctly captured and printed `[CAPTURE_RESULT]`, but invocation failed binding an emitted empty line. Parser now allows empty log lines; focused and full policy tests pass. T108 remains open until the corrected successful wrapper rerun and final evidence reconciliation. |
| `artifact_link` | `D:/octav/coverage-local-validation/integration-281e454/c498330c-a113-4a4e-8859-9816d76f5172/manifest.json`; `D:/octav/coverage-local-validation/assessments/281e454-capture/decision.md`; `tools/coverage/fixtures/capture-result.json`. |

### 11.43 Exact nested candidate/target identity validation diagnostic (2026-10-27)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Ensure the local integration wrapper binds a captured Candidate bundle to the exact proposed candidate and configured target revisions before assessment (FR-016/017/020; SC-003/006). |
| `scope` | Ran the real integration wrapper on a clean candidate after the blank-line parser fix. Local CI passed, connected-device capture completed with all required suites and artifacts, and emitted one valid `[CAPTURE_RESULT]`. The wrapper then rejected the run because it looked for top-level `source_commit` / `target_commit` while schema v1 stores these values under `git`. Independently assessed the sealed run against the current-target baseline; added a helper that requires exact nested `git.source_commit` and `git.target_commit`, plus fixture cases for a valid nested identity, mismatched source, mismatched target, and a contradictory top-level decoy. |
| `revision` | Capture candidate commit `1fac4cbff918f86f646ce7b4bf030c4f52d9e827`, tree `4b286a54a1aba6e5c44679590dfd3098cf7c272c`; target commit `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`. Candidate and pinned harness worktrees were clean and matched revision/tree before capture; clean preflight and postflight passed. Nested identity helper and its fixture file are pending the next manual commit. |
| `named_procedure` | `setup-host.ps1 -DeviceSerial R7AIB7002901AZ3`; actual `integration-ci.ps1`; direct `verify.ps1` with baseline `10b510ad-4638-4107-9fe6-91efdbbf9827`; focused and full `test-policy.ps1`; PowerShell parser checks; `git diff --check`. Local CI passed (33.99 sec). Candidate run `bce36f77-8c6a-4061-bade-355814dc30a2` completed in 2m23s with 203 suites / 1,281 tests, zero failures/errors/skips, 3,378 retained artifacts, and complete collection. Direct independent verifier result is Overall PASS, with collection, tests, floors, compatibility and regression Passed. The integration wrapper itself exited 2 at the manifest identity assertion before invoking its verifier. New nested-identity tests pass 4/4; full policy suite passes 90/90. |
| `environment` | Windows 10.0.26300.0; PowerShell 7.6.5; JetBrains Java 21.0.10; Gradle 9.6.0; JaCoCo 0.8.15; Android platform 37.0 revision 2; build-tools 37.0.0; platform-tools 37.0.1; connected ASUS_AI2302, serial `R7AIB7002901AZ3`, API 35, `arm64-v8a`. |
| `expected_observed_result` | Run manifest SHA-256 `7ec5e06b258a0acf3fe3410e594daebf43d143b534759a58c018b1300bc5a9fa`. Captured Tier 1 counters: security 3,624/4,452 instructions and 258/386 branches; domain 5,124/7,184 and 412/578; data 1,158/1,333 and 44/60; FIDO2 54,508/86,114 and 3,362/5,526; authenticator 0/0 with a bound `AnalyzedZeroProofV1`. Repository aggregate 74,222/132,917 instructions and 4,650/8,010 branches. Wrapper's identity check correctly stopped before accepting the mismatched/empty top-level fields; nested identity helper now compares the schema's actual fields. |
| `date` | 2026-10-27, America/Mexico_City. |
| `limitations` | The direct verifier proves floors and current-target regression for this captured commit, but the wrapper invocation did not complete successfully. The corrected nested identity path is still uncommitted and requires a fresh clean-revision wrapper run. Publication remains Pending; hosted integration remains Unverified under the T046–T049 owner waiver. |
| `status` | Capture, tests, hard floors and current-target regression passed. Wrapper failed due manifest identity schema mismatch; corrected nested-field validation and four regression fixtures pass. T108 remains open until a complete clean committed wrapper run and final evidence reconciliation. |
| `artifact_link` | `D:/octav/coverage-local-validation/integration-1fac4cb/bce36f77-8c6a-4061-bade-355814dc30a2/manifest.json`; `D:/octav/coverage-local-validation/assessments/1fac4cb-capture/decision.md`; `tools/coverage/fixtures/manifest-identity.json`. |

### 11.44 Successful local integration wrapper and exact candidate acceptance (2026-10-27)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Complete T108 local acceptance on a clean exact-revision candidate: required local CI and connected host/device capture; parse capture result; verify complete evidence, all fixed floors, current-target compatibility and changed-Tier 1 regression; retain run and assessment outside measured source (FR-016/017/020; SC-001-SC-008). |
| `scope` | Actual `tools/integration-ci.ps1` ran all three steps: non-mutating local checks, candidate capture using the full-SHA-pinned same-revision harness, and independent policy verification against the clean current-target baseline. The wrapper parsed `[CAPTURE_RESULT]`, validated nested `git.source_commit` and `git.target_commit`, checked both refs remained unchanged, and exited 0. No hosted publication or promotion is claimed. |
| `revision` | Candidate commit `8c733f106e8cd8b3bcf0fc2b6a3a7577181ef38e`; source tree `d6ed8ca3b553557c3ff1cef761ec6c4fd3189e7e`; target `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`. Candidate and harness had identical clean revision/tree; preflight and postflight were clean. Harness file digests matched the reviewed `capture.ps1`, `hdk-coverage.init.gradle`, `policy.json`, and `verify.ps1` recorded in the external manifest. |
| `named_procedure` | `setup-host.ps1 -DeviceSerial R7AIB7002901AZ3`, then `integration-ci.ps1` with `-CandidateRef 8c733f106e8cd8b3bcf0fc2b6a3a7577181ef38e`, `-TargetRef origin/lab/or/chimali`, exact candidate harness, external evidence root, and current-target baseline `10b510ad-4638-4107-9fe6-91efdbbf9827`. Wrapper exit code 0. Step 1 Local CI passed in 65.15 sec. Step 2 coverage capture completed in 2m17s and reported Complete / Passed. Step 3 `verify.ps1` wrote the passing decision; wrapper completed its post-run candidate/target identity check. |
| `environment` | Windows 10.0.26300.0; PowerShell 7.6.5; JetBrains Java 21.0.10; Gradle 9.6.0; JaCoCo 0.8.15; Android platform 37.0 revision 2; build-tools 37.0.0; platform-tools 37.0.1; ASUS_AI2302 serial `R7AIB7002901AZ3`, API 35, `arm64-v8a`, online and unlocked during execution. |
| `expected_observed_result` | Run `68529721-f8cd-4f62-bc9e-f56d19072c37`, 2026-10-27 07:38:42–07:40:59 UTC; 203 required suites / 1,281 tests, zero failures/errors/skips, 3,378 retained artifacts; manifest SHA-256 `d1aa929196b911a60687223580dd9177ef20876dda00486d264a95321d5b0f02`. Verifier Overall PASS: collection/tests/floors Passed, compatibility Compatible, regression Passed; publication Pending and hosted integration Unverified. Tier 1 counters: security 3,624/4,452 instructions, 258/386 branches; domain 5,124/7,184, 412/578; data 1,158/1,333, 44/60; FIDO2 54,508/86,114 (63.298%), 3,362/5,526 (60.839%); authenticator 0/0 with bound `AnalyzedZeroProofV1`. Tier 2 instruction counters: database 409/623, common 374/1,755, app 738/1,996; all passed 20%. Repository 74,222/132,917 instructions (55.841%), 4,650/8,010 branches; 50% floor passed. FIDO2 is 230 covered branches short of approximate 65% goal; goal is nonbinding. |
| `date` | 2026-10-27, America/Mexico_City. |
| `limitations` | Owner waived hosted runner, dedicated App, protected archive, and required hosted integration demonstrations (T046–T049 and T089; §11.28). Publication remains Pending and hosted integration Unverified. No remote publication, push, protected integration, or promotion was performed or inferred. |
| `status` | T108 local acceptance is complete: actual wrapper, clean candidate/harness binding, device suites, all fixed coverage floors, changed-Tier 1 regression, and external retained evidence passed. Hosted operational acceptance remains waived and unverified; the 65% FIDO2 branch goal shortfall is documented without altering policy. |
| `artifact_link` | `D:/octav/coverage-local-validation/integration-8c733f1/68529721-f8cd-4f62-bc9e-f56d19072c37/manifest.json`; `D:/octav/coverage-local-validation/integration-8c733f1/assessments/68529721-f8cd-4f62-bc9e-f56d19072c37/decision.md`; `D:/octav/coverage-local-validation/runs/10b510ad-4638-4107-9fe6-91efdbbf9827/manifest.json`. |










### 11.45 Convergence remediation tooling changes (2026-10-27)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Enforce trusted accepted-target comparison and exact nested manifest identities, pass changed production paths into Tier 1 regression scoping, and fail closed on stale targets (FR-012/014/015/017; SC-003/006/007). |
| `scope` | Updated `integration-ci.ps1` to require a live remote target matching the frozen local target, validate the accepted-baseline index provenance/target/path/run ID/digest and the baseline's complete/passed suite evidence, bind the baseline source commit to the candidate target, and supply target-to-candidate changed paths to `verify.ps1`. Updated `verify.ps1` to reject missing/incomplete/failed/mismatched baselines and analyzer identities, and to expand shared build/tool/policy/workflow changes to all Tier 1 modules. Updated `report.ps1` to validate nested source/target commits and accept only supported comparison/changed-path verifier arguments. Updated `publish.ps1` to use nested tested commit/tree values and require exact tree equality before promotion. Added four comparison-manifest fixtures. |
| `revision` | Working tree changes are uncommitted. No commit or hosted workflow execution is claimed. |
| `named_procedure` | Parsed six changed PowerShell scripts with the PowerShell AST parser; ran `pwsh -NoProfile -ExecutionPolicy Bypass -File tools/coverage/test-policy.ps1`; ran `git diff --check`. |
| `environment` | Windows PowerShell 7 runtime in the Chimali repository workspace, 2026-10-27. |
| `expected_observed_result` | All six changed PowerShell scripts parsed without syntax errors. Coverage policy fixture suite: 94 passed, 0 failed. `git diff --check` passed; Git reported only the existing tasks document line-ending normalization warning. |
| `date` | 2026-10-27, America/Mexico_City. |
| `limitations` | No full `integration-ci.ps1` run, fresh device capture, archive retrieval, App publication, promotion, or protected hosted check was executed. The production entry-point scenarios, current accepted-baseline setup, and live remote target behavior still need integration verification; T109–T111 remain unchecked. T046–T049 and T089 remain owner-waived. |
| `status` | Code changes and deterministic fixture validation are complete for this pass; end-to-end acceptance of T109–T111 remains open pending production-entry-point verification. No hosted acceptance is claimed. |
| `artifact_link` | `tools/coverage/fixtures/trusted-comparison.json`; `tools/coverage/test-policy.ps1` output (94/94); changed source files under `tools/coverage/` and `tools/integration-ci.ps1`. |

### 11.46 T109–T111 expanded deterministic verification (2026-10-27)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Verify accepted baseline provenance and exact target/source identity, changed Tier 1 scoping, and nested candidate/promotion identities (FR-012/014/015/017; SC-003/006/007). |
| `scope` | Added production helper checks and fixtures for accepted-index provenance, target, path traversal and digest; valid/mismatched/incomplete/failed baseline manifests; single-module, multi-module, shared-change, empty and fallback changed-path scopes; and nested promotion-tree identity. Replayed the retained T108 Candidate run against the retained current-target Comparison bundle through the changed production `verify.ps1`. |
| `revision` | Local changes remain uncommitted. Candidate bundle source `8c733f106e8cd8b3bcf0fc2b6a3a7577181ef38e`; target/baseline source `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`. |
| `named_procedure` | PowerShell AST parse of six scripts; `tools/coverage/test-policy.ps1`; direct `verify.ps1 -RunBundle ... -ComparisonBundle ... -ChangedPathsFile ...`; independent recomputation of the accepted baseline bundle digest; `git diff --check`. |
| `environment` | Windows PowerShell 7 runtime in the Chimali repository workspace; retained external coverage bundles at `D:/octav/coverage-local-validation`; 2026-10-27. |
| `expected_observed_result` | Fixture suite: 108 passed, 0 failed. Direct verification passed collection, tests, floors, baseline compatibility and regression (`overall_passed = true`). Coverage bundle digest helper matched an independent recomputation (`1e66af4f40ba33cccb6cfe35368615dab38b0f6235832346771342ab2cf4bee5`). PowerShell syntax and whitespace checks passed. |
| `date` | 2026-10-27, America/Mexico_City. |
| `limitations` | Full `integration-ci.ps1` was not run: the evidence root has no accepted `baselines/current.json` index, and the current source tree is modified. Live remote target freshness and the changed wrapper/report/promotion flows have not been exercised end to end. No new device capture, archive write/read-back, GitHub App call, promotion, or hosted check was performed. T109–T111 remain open pending those production-entry-point checks; T046–T049 and T089 remain owner-waived. |
| `status` | Deterministic baseline, changed-path, promotion-identity and real retained-bundle verification passed. Local orchestration and hosted operations remain unverified; no acceptance claim is made for T109–T111. |
| `artifact_link` | `tools/coverage/fixtures/accepted-baseline-index.json`; `tools/coverage/fixtures/trusted-comparison.json`; `tools/coverage/fixtures/changed-paths.json`; `tools/coverage/fixtures/promotion-identity.json`; `D:/octav/coverage-local-validation/assessments/convergence-final-5df3d5d5ea2e4d0da19cea52c5478fc5/decision.json`. |

### 11.47 Local accepted-target index bootstrap (2026-10-27)

| Evidence field | Verified record |
| --- | --- |
| `requirement` | Make the existing complete measured target run discoverable through the local accepted-baseline index required by T109. |
| `scope` | Created `D:/octav/coverage-local-validation/baselines/current.json` from the retained target Comparison run after checking its nested source commit, Complete collection, Passed tests, and bundle digest. The index identifies provenance as `MeasuredAcceptedTarget`, uses relative bundle path `runs/10b510ad-4638-4107-9fe6-91efdbbf9827`, and binds target commit `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`. |
| `revision` | Target commit `fa93a40cf34f6e130f7e4c79a4b55886d9afe9f2`; baseline run `10b510ad-4638-4107-9fe6-91efdbbf9827`. Index is external local evidence, not a repository source change. |
| `named_procedure` | Validated baseline `manifest.json`, computed `Get-CoverageBundleDigest`, wrote the local index once without overwrite, reloaded it, and validated it with `Test-AcceptedBaselineIndex`. |
| `environment` | Windows PowerShell 7; `D:/octav/coverage-local-validation`; 2026-10-27. No GitHub API or network request. |
| `expected_observed_result` | Index created and revalidated successfully; bundle provenance, target commit, run ID, and SHA-256 digest are bound. |
| `date` | 2026-10-27, America/Mexico_City. |
| `limitations` | The full integration wrapper remains unrun because source changes are uncommitted and live remote target freshness has not been checked. The index is a local measurement record; it is not a hosted archive receipt. |
| `status` | Local accepted-target index bootstrap complete. T109–T111 remain open pending clean-revision production entry-point verification; hosted operations remain unverified/waived. |
| `artifact_link` | `D:/octav/coverage-local-validation/baselines/current.json`; target bundle manifest `D:/octav/coverage-local-validation/runs/10b510ad-4638-4107-9fe6-91efdbbf9827/manifest.json`. |
