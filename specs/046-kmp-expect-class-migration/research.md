# Research: KMP Expect/Actual Class Migration + Green CI

**Date**: 2026-05-18
**Branch**: `046-kmp-expect-class-migration`

## Decision 1: Interface + platform implementations (KMP)

**Rationale**: `expect class` / `actual class` requires `EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING`. `:core:security` already uses `PlatformUserVerification` as a commonMain interface with `Android*` / `Ios*` classes.

**Alternatives considered**: Keep suppressions (rejected); `expect fun` factory (rejected — unnecessary).

**Status**: Implemented on branch.

---

## Decision 2: Full green `tools/local-ci.ps1` (clarify 2026-05-18)

**Rationale**: Constitution §IX; user chose full green CI, not “no new failures only.”

**Alternatives considered**: Module-scoped CI (rejected); defer CI (rejected).

**Status**: In progress.

---

## Decision 3: Test remediation policy

**Rationale**: Clarify session — update or delete obsolete tests; never disable/skip.

**Alternatives considered**: Disable failing tests (rejected); no test changes (rejected).

---

## Decision 4: Static analysis remediation

**Rationale**: Clarify session — fix detekt/ktlint in source only; no baseline or config changes.

**Alternatives considered**: Update `detekt-baseline.xml` (rejected).

---

## Decision 5: Production CI fix scope

**Rationale**: Clarify session — any Gradle module that fails local CI may receive structural production fixes.

**Alternatives considered**: `:feature:fido2` only (rejected for full CI).

---

## CI failure inventory (branch snapshot)

| Area | Symptom | Likely cause | Remediation direction |
|------|---------|--------------|----------------------|
| `:feature:fido2:compileAndroidHostTest` | `Unresolved reference 'HidReportParser'` in `src/test/kotlin` | Host-test source set not resolving `androidMain` types (or tests should live under `androidHostTest` path) | Align source sets / move tests; do not delete coverage without justification |
| Same | `Unresolved reference` to `internal const` CTAP offsets | Constants are `internal` in `androidMain` — same classpath issue | Fix classpath first; if needed use `@VisibleForTesting` or test helper in `androidMain` |
| `:app` (historical) | `Unresolved reference 'Fido2Initializer'` | KMP publication / compile order | Re-verify; `:app:compileReleaseKotlin` succeeded in isolated run 2026-05-18 |
| `tools/local-ci.ps1` | Full pipeline | Aggregates ktlint, detekt, lintRelease, compile*, test | Fix failures in order B1→B7 in plan.md |

---

## `local-ci.ps1` phases (reference)

1. `ktlintFormat` (all projects)
2. `detekt` with autoCorrect
3. `lintRelease`
4. `compileDebugSources compileAndroidMain compileDebugUnitTestSources compileAndroidHostTest compileDebugAndroidTestSources compileAndroidDeviceTest --continue`
5. `test`

iOS native compile/link disabled on non-Mac in `:feature:fido2` — not required for Windows CI pass.

---

## Supersedes

Spec 040 FR-006 KMP suppression exclusions — superseded for `PlatformBluetoothHid` / `PlatformLock`.
