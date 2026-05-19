# Implementation Plan: Remove KMP Expect/Actual Class Suppressions

**Branch**: `046-kmp-expect-class-migration` | **Date**: 2026-05-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/046-kmp-expect-class-migration/spec.md` (includes clarify session 2026-05-18)

## Summary

Two-phase delivery on branch `046-kmp-expect-class-migration`:

1. **KMP structural migration (complete on branch)** — Replace `expect class` / `actual class` for `PlatformBluetoothHid` and `PlatformLock` with commonMain interfaces and `Android*` / `Ios*` implementations; remove all `EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING` suppressions.
2. **Green local CI (remaining)** — Fix all compile, test, detekt, ktlint, and Android lint failures so `tools/local-ci.ps1` exits 0. Production fixes only where structural; tests may be updated or removed (never disabled). Detekt/ktlint: source fixes only, no baseline/config changes.

## Technical Context

**Language/Version**: Kotlin 2.x (Kotlin Multiplatform), AGP KMP library + Android application
**Primary Dependencies**: Koin (DI), Android Bluetooth / PackageManager, `ReentrantLock` / `NSRecursiveLock`, project `tools/local-ci.ps1`
**Storage**: N/A
**Testing**: `testAndroidHostTest`, `test` (Gradle), JUnit 5 + MockK in `:feature:fido2` host tests; `LocalCrashReportingLogWriterTest`
**Target Platform**: Android (primary CI host: Windows); iOS placeholders (native link disabled off-macOS per `feature/fido2/build.gradle.kts`)
**Project Type**: KMP mobile app (`:app` + `:feature:fido2` + core modules)
**Performance Goals**: No runtime change (refactor + CI repair)
**Constraints**: FR-008–FR-010; Constitution §IX local CI gate; no detekt baseline edits
**Scale/Scope**: 6 Kotlin files migrated (done); CI fixes across any failing Gradle modules

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| §III Clean Architecture | Platform boundaries via commonMain interfaces | Pass |
| §IX Local CI | `tools/local-ci.ps1` exit 0 on branch | **Pending** (implementation Phase 2) |
| §XI.1 YAGNI | No new modules; follow `PlatformUserVerification` pattern only | Pass |
| §X.2 KMP | Minimize `expect`/`actual`; interfaces for platform APIs | Pass |
| §IX Quality gates | Detekt/Ktlint via source fixes, not baseline weakening | Pass (design) |

**Post-Phase 1 Re-Check**: Gates pass at design level. §IX completion verified only after Phase 2 CI green.

## Project Structure

### Documentation (this feature)

```text
specs/046-kmp-expect-class-migration/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/platform-abstractions.md
└── checklists/requirements.md
```

### Source Code

```text
feature/fido2/src/
├── commonMain/.../platform/PlatformBluetoothHid.kt       # interface [DONE]
├── commonMain/.../logging/PlatformLock.kt                # interface + withLock [DONE]
├── androidMain/.../AndroidPlatformBluetoothHid.kt          [DONE]
├── androidMain/.../AndroidPlatformLock.kt                  [DONE]
├── androidMain/.../LocalCrashReportingLogWriter.kt         # AndroidPlatformLock() [DONE]
├── iosMain/.../IosPlatformBluetoothHid.kt                  [DONE]
└── iosMain/.../IosPlatformLock.kt                          [DONE]

feature/fido2/src/test/kotlin/                             # androidHostTest — COMPILE FAILURES
app/src/main/kotlin/.../ChimaliApplication.kt               # FIDO2 DI — verify release compile
```

**Structure Decision**: KMP types stay in `:feature:fido2`. CI remediation may touch `:app` and any other module failing `local-ci.ps1`.

## Phase 0: Research Summary

See [research.md](research.md). Key findings:

- KMP migration satisfies FR-001–FR-006 (verified by grep).
- `:feature:fido2:compileAndroidHostTest` fails: tests under `src/test/kotlin` reference `androidMain` types (`HidReportParser`, CTAP constants) — classpath/source-set alignment required.
- `:app:compileReleaseKotlin` currently succeeds when run with `:feature:fido2:compileAndroidMain` (re-verify in full pipeline).
- `tools/local-ci.ps1` runs: ktlintFormat → detekt (autoCorrect) → lintRelease → compile* → `test`.

## Phase 1: Design

- **Data model**: [data-model.md](data-model.md) — `PlatformBluetoothHid`, `PlatformLock`
- **Contracts**: [contracts/platform-abstractions.md](contracts/platform-abstractions.md)
- **Validation**: [quickstart.md](quickstart.md)

## Phase 2: Implementation Plan

### Track A — KMP migration [COMPLETE]

| Step | Action | Status |
|------|--------|--------|
| A1 | `PlatformBluetoothHid` → interface + Android/iOS impls | Done |
| A2 | `PlatformLock` → interface + Android/iOS impls | Done |
| A3 | Update `LocalCrashReportingLogWriter` call site | Done |
| A4 | Grep: zero `EXPECT_ACTUAL` suppressions; zero `expect class` in fido2 | Done |

### Track B — Green local CI [TODO]

| Step | Action | Module(s) | Notes |
|------|--------|-----------|-------|
| B1 | Fix `compileAndroidHostTest` / host-test classpath | `:feature:fido2` | Standardize Gradle setup: remove `kotlin.srcDir("src/test/kotlin")` override from `androidHostTest` in `feature/fido2/build.gradle.kts` to allow automatic `androidMain` classpath resolution — **no `@Ignore`** |
| B2 | Fix remaining `compileDebugUnitTestSources` / `compileAndroidDeviceTest` failures | Per failure | Same test policy |
| B3 | Run `ktlintFormat` / fix ktlintCheck violations | Any failing | Source only (FR-009) |
| B4 | Run `detekt`; fix violations in source | Any failing | No baseline edits (FR-009) |
| B5 | Fix `lintRelease` issues | Any failing | Layout/manifest/code per Android lint |
| B6 | Run `test`; update or remove failing tests | Any failing | Update assertions; delete obsolete only (FR-008) |
| B7 | Full gate: `.\tools\local-ci.ps1` | Repo | Must exit 0 (FR-007, SC-003) |

### Track C — Documentation [PARTIAL]

| Step | Action | Status |
|------|--------|--------|
| C1 | Mark spec 040 exclusion rows superseded | Done |
| C2 | Update changelog when CI green | In Progress (tracked by T030 in `tasks.md`, target `docs/changelogs/`) |

## Complexity Tracking

> No constitution violations requiring justification.
