# Feature Specification: Remove KMP Expect/Actual Class Suppressions

**Feature Branch**: `046-kmp-expect-class-migration`

**Created**: 2026-05-18

**Status**: Draft

**Input**: Remove all `@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")` project-wide and migrate remaining `expect class` / `actual class` boundaries in `:feature:fido2` to the established interface + platform-implementation pattern. Preserve runtime/product behavior (structural KMP refactor only); tests may be updated or removed per Clarifications. Restore a fully green local CI pipeline on this branch.

## Clarifications

### Session 2026-05-18

- Q: For this feature, what is the required end state for `tools/local-ci.ps1`? → A: **Full green CI** — fix all compile, test, and lint failures needed so `tools/local-ci.ps1` exits 0 on this branch.
- Q: How strictly should “no behavioral changes” apply when fixing build/CI failures? → A: **Tests may change** — update broken tests to match the codebase; remove tests only when they no longer apply; do **not** disable or skip tests (`@Ignore`, `xtest`, commented-out bodies).
- Q: When detekt or ktlint failures unrelated to the KMP refactor block local CI, how may this feature resolve them? → A: **Code fixes only** — no detekt baseline or `detekt.yml` / ktlint config changes; fix violations in source.
- Q: Which modules may receive production (non-test) code changes for CI fixes? → A: **Any failing module** — structural/build production fixes allowed in any module that blocks `tools/local-ci.ps1`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Maintainable platform boundaries (Priority: P1)

As a maintainer, I need platform-specific capability checks and locking primitives to follow the same multiplatform pattern as other core abstractions, so the codebase does not rely on beta compiler suppressions.

**Why this priority**: Suppressions hide structural debt and block lint remediation goals; aligning patterns reduces review friction and future DI work.

**Independent Test**: Repository search returns zero `EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING` suppressions and zero `expect class` / `actual class` for the migrated types in `:feature:fido2`.

**Acceptance Scenarios**:

1. **Given** the codebase before migration, **When** a maintainer searches for `EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING`, **Then** no matches are found.
2. **Given** Android and iOS builds, **When** platform capability and lock types are used, **Then** behavior matches the pre-migration contract (same return values and lock semantics).

---

### User Story 2 - Unchanged runtime behavior (Priority: P2)

As a user of FIDO2 and crash logging on Android, I should see no change in Bluetooth HID capability reporting or log file locking behavior after the structural migration.

**Why this priority**: The migration is refactor-only; regressions would undermine trust in unrelated changes.

**Independent Test**: Unit tests executed by local CI pass with outcomes consistent with current intended behavior (assertions may be updated per Clarifications); Android Bluetooth HID capability checks and crash-log locking semantics are unchanged from the pre-migration implementation.

**Acceptance Scenarios**:

1. **Given** an Android device with Bluetooth hardware, **When** capability queries run through the new interface implementation, **Then** `isSupported` and `isAdapterEnabled` outcomes match the previous implementation.
2. **Given** concurrent log writes in crash reporting, **When** `withLock` is used, **Then** file operations remain serialized as before.

---

### User Story 3 - Green local CI pipeline (Priority: P1)

As a contributor, I need `tools/local-ci.ps1` to complete without errors on this branch so merges are not blocked by compile, test, or static-analysis failures.

**Why this priority**: Constitution §IX requires the local CI gate; a refactor-only feature must not leave the branch in a broken build state.

**Independent Test**: Run `tools/local-ci.ps1` from the repository root; the script exits with code 0 and reports pipeline success.

**Acceptance Scenarios**:

1. **Given** a clean checkout on branch `046-kmp-expect-class-migration`, **When** `tools/local-ci.ps1` runs with default flags, **Then** all configured ktlint, detekt, lint, compile, and unit-test phases pass.
2. **Given** the KMP migration and any required build fixes are applied, **When** a maintainer runs the same pipeline on Windows (project primary dev host), **Then** no compilation or test task fails.

---

### Edge Cases

- iOS placeholder implementations continue to return `false` for Bluetooth HID until CoreBluetooth integration exists.
- `PlatformBluetoothHid` has no production callers today; migration must not break compilation of platform source sets.
- Spec 040 previously excluded these suppressions as valid; this feature supersedes that exclusion for the migrated types only.
- Pre-existing compile failures (e.g. `:app` FIDO2 wiring, `:feature:fido2` host-test source-set visibility) are **in scope** for resolution until local CI is green.
- iOS native link tasks may remain disabled on non-macOS hosts per project Gradle configuration; local CI on Windows must still pass all tasks the script actually runs.
- Broken tests are fixed by **updating assertions/setup** or **deleting** obsolete tests (defined as tests where the underlying API or feature has been entirely removed, not merely failing under structural changes)—not by `@Ignore`, `@Disabled`, `xtest`, or empty/commented test bodies.
- Detekt/ktlint: fix violations in Kotlin source; do not widen baselines or relax rules to pass CI.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST contain zero `@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")` annotations.
- **FR-002**: `PlatformBluetoothHid` and `PlatformLock` MUST NOT use `expect class` or `actual class` in `:feature:fido2` production sources.
- **FR-003**: Both types MUST be declared as commonMain interfaces with platform-specific implementation classes on Android and iOS.
- **FR-004**: The public contract MUST be preserved: `isSupported()`, `isAdapterEnabled()` for Bluetooth HID; `lock()`, `unlock()`, and `withLock` for locking.
- **FR-005**: Android Bluetooth HID implementation MUST remain `Context`-injectable; iOS MUST remain a safe placeholder returning `false`.
- **FR-006**: Android lock implementation MUST continue using a reentrant JVM lock; iOS MUST continue using a recursive native lock.
- **FR-007**: `tools/local-ci.ps1` MUST exit 0 on this branch, including ktlint, detekt, release lint, full compile phase, and unit tests as defined in the script.
- **FR-008**: All compile and test failures blocking local CI on this branch MUST be resolved. Production/runtime behavior MUST NOT change except for structural/build fixes (wiring, visibility, source sets). Production code changes for CI are allowed in **any module** that fails the local CI pipeline, not only `:feature:fido2`. **Tests MAY be updated** to compile and assert correctly against current code; obsolete tests MAY be **removed** when they no longer apply. Tests MUST NOT be disabled or skipped to pass CI.
- **FR-009**: Detekt and ktlint violations blocking local CI MUST be resolved by **source code fixes only** — no updates to `config/detekt/detekt-baseline.xml`, `feature/fido2/detekt-baseline.xml`, `config/detekt/detekt.yml`, or ktlint editorconfig/rules.
- **FR-010**: Existing `@Suppress("UNCHECKED_CAST")` in test scope SHOULD remain unless removing the suppression is part of a proper code fix (not baseline-driven).

### Key Entities

- **PlatformBluetoothHid**: Abstraction for whether the device can act as a Bluetooth HID peripheral and whether Bluetooth is powered on.
- **PlatformLock**: Abstraction for mutual exclusion around file I/O in platform logging code.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Zero instances of `EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING` exist in the repository.
- **SC-002**: All unit tests executed by `tools/local-ci.ps1` pass after changes; updated tests reflect current intended behavior; removed tests are justified as obsolete (not merely failing).
- **SC-003**: `tools/local-ci.ps1` completes successfully (exit code 0) on this branch after all feature work is merged.
- **SC-004**: Platform capability and locking behavior on Android and iOS remain identical to pre-migration behavior for the same inputs and environment.
- **SC-005**: Zero Gradle tasks in the local CI compile and test phases fail on the primary Windows development configuration.

## Assumptions

- The `PlatformUserVerification` migration in `:core:security` is the reference pattern for interface + `Android*` / `Ios*` implementations.
- `PlatformBluetoothHid` has no production callers yet; migration risk is limited to compilation and future DI wiring.
- `PlatformLock` is constructed only in `LocalCrashReportingLogWriter` on Android today; iOS implementation exists for parity.
- Spec 040 FR-006 exclusion for these suppressions is superseded by this feature for documentation purposes only where noted.
- Test remediation follows clarify session policy: update or delete; never disable-to-green.
- Production CI fixes may touch any failing Gradle module (e.g. `:app` FIDO2 wiring); drive-by refactors in passing modules are out of scope.
