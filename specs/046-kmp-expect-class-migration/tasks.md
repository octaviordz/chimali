# Tasks: Remove KMP Expect/Actual Class Suppressions

**Input**: Design documents from `specs/046-kmp-expect-class-migration/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/platform-abstractions.md](contracts/platform-abstractions.md)

**Tests**: Not generating new test files — existing tests must compile and pass; update or remove obsolete tests per clarify session (no `@Ignore` / `@Disabled`).

**WIP status (branch `046-kmp-expect-class-migration`)**: **Track A (US1) complete** on branch. **Track B (US3) not started** — `compileAndroidHostTest` and full `tools/local-ci.ps1` still fail.

**Organization**: Tasks grouped by user story (US1 → US3 → US2 → Polish).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks in same phase)
- **[Story]**: US1, US2, US3 per [spec.md](spec.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm branch context and capture CI failure baseline for US3

- [x] T001 Verify branch `046-kmp-expect-class-migration` and [`.specify/feature.json`](../../.specify/feature.json) → `specs/046-kmp-expect-class-migration`
- [ ] T002 Run `.\gradlew :feature:fido2:compileAndroidHostTest --no-daemon`; then `.\gradlew ktlintCheck detekt --continue` and record failing modules/files (baseline for US3; covers FR-008 “any failing module” discovery)

---

## Phase 2: User Story 1 — Maintainable platform boundaries (Priority: P1) 🎯 MVP

**Goal**: Zero `EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING` suppressions; `PlatformBluetoothHid` and `PlatformLock` use interface + `Android*` / `Ios*` pattern.

**Independent Test**: `rg "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING" --glob "*.kt"` → no matches; `rg "expect class|actual class" feature/fido2/src --glob "*.kt"` → no matches.

**Status**: ✅ **Complete on branch** (WIP implementation landed)

### Implementation for User Story 1

- [x] T003 [P] [US1] Convert `PlatformBluetoothHid` to `interface` in [feature/fido2/src/commonMain/kotlin/com/chimali/fido2/platform/PlatformBluetoothHid.kt](../../feature/fido2/src/commonMain/kotlin/com/chimali/fido2/platform/PlatformBluetoothHid.kt)
- [x] T004 [P] [US1] Add `AndroidPlatformBluetoothHid` in [feature/fido2/src/androidMain/kotlin/com/chimali/fido2/platform/AndroidPlatformBluetoothHid.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/platform/AndroidPlatformBluetoothHid.kt)
- [x] T005 [P] [US1] Add `IosPlatformBluetoothHid` in [feature/fido2/src/iosMain/kotlin/com/chimali/fido2/platform/IosPlatformBluetoothHid.kt](../../feature/fido2/src/iosMain/kotlin/com/chimali/fido2/platform/IosPlatformBluetoothHid.kt)
- [x] T006 [P] [US1] Convert `PlatformLock` to `interface` + `withLock` in [feature/fido2/src/commonMain/kotlin/com/chimali/fido2/util/logging/PlatformLock.kt](../../feature/fido2/src/commonMain/kotlin/com/chimali/fido2/util/logging/PlatformLock.kt)
- [x] T007 [P] [US1] Add `AndroidPlatformLock` in [feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/logging/AndroidPlatformLock.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/logging/AndroidPlatformLock.kt)
- [x] T008 [P] [US1] Add `IosPlatformLock` in [feature/fido2/src/iosMain/kotlin/com/chimali/fido2/util/logging/IosPlatformLock.kt](../../feature/fido2/src/iosMain/kotlin/com/chimali/fido2/util/logging/IosPlatformLock.kt)
- [x] T009 [US1] Use `AndroidPlatformLock()` in [feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/logging/LocalCrashReportingLogWriter.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/logging/LocalCrashReportingLogWriter.kt)
- [x] T010 [US1] Confirm FR-001/SC-001: zero `EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING` in `*.kt`
- [x] T011 [US1] Confirm FR-002: zero `expect class` / `actual class` in `feature/fido2/src` production Kotlin

**Checkpoint**: US1 done — KMP migration satisfies FR-001–FR-006.

---

## Phase 3: User Story 3 — Green local CI pipeline (Priority: P1)

**Goal**: `tools/local-ci.ps1` exits 0 on this branch (ktlint, detekt, lintRelease, compile*, `test`).

**Independent Test**: `.\tools\local-ci.ps1` → exit code 0.

**Status**: 🔲 **Remaining work**

### Compile — `:feature:fido2` host tests

- [ ] T012 [US3] Fix `:feature:fido2:compileAndroidHostTest` — remove overriding `kotlin.srcDir("src/test/kotlin")` from `androidHostTest` in [feature/fido2/build.gradle.kts](../../feature/fido2/build.gradle.kts) to enable automatic KMP compilation against `androidMain`.
- [ ] T013 [P] [US3] Fix compile errors in [feature/fido2/src/test/kotlin/com/chimali/fido2/bluetooth/HidReportParserTest.kt](../../feature/fido2/src/test/kotlin/com/chimali/fido2/bluetooth/HidReportParserTest.kt) — **only if still failing after T012** (references [HidReportParser.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth/HidReportParser.kt))
- [ ] T014 [P] [US3] Fix compile errors in [feature/fido2/src/test/kotlin/com/chimali/fido2/ctap2/Ctap2ProtocolTest.kt](../../feature/fido2/src/test/kotlin/com/chimali/fido2/ctap2/Ctap2ProtocolTest.kt) — **only if still failing after T012**
- [ ] T015 [P] [US3] Fix compile errors in [feature/fido2/src/test/kotlin/com/chimali/fido2/data/crypto/CryptoUtilsTest.kt](../../feature/fido2/src/test/kotlin/com/chimali/fido2/data/crypto/CryptoUtilsTest.kt) — **only if still failing after T012**
- [ ] T016 [P] [US3] Fix compile errors in [feature/fido2/src/test/kotlin/com/chimali/fido2/integration/RegistrationAuthenticationDataIntegrationTest.kt](../../feature/fido2/src/test/kotlin/com/chimali/fido2/integration/RegistrationAuthenticationDataIntegrationTest.kt) — **only if still failing after T012**
- [ ] T017 [P] [US3] Fix compile errors in [feature/fido2/src/test/kotlin/com/chimali/fido2/presentation/error/Fido2ErrorHandlerTest.kt](../../feature/fido2/src/test/kotlin/com/chimali/fido2/presentation/error/Fido2ErrorHandlerTest.kt) — **only if still failing after T012**
- [ ] T018 [P] [US3] Fix compile errors in [feature/fido2/src/test/kotlin/com/chimali/fido2/presentation/management/CredentialManagementViewModelTest.kt](../../feature/fido2/src/test/kotlin/com/chimali/fido2/presentation/management/CredentialManagementViewModelTest.kt) — **only if still failing after T012**

### Compile — `:app` and full compile phase

- [ ] T019 [US3] Verify FIDO2 wiring in [app/src/main/kotlin/com/chimali/ChimaliApplication.kt](../../app/src/main/kotlin/com/chimali/ChimaliApplication.kt) (`Fido2Initializer`, `Fido2Module`) compiles
- [ ] T020 [US3] Verify [app/src/main/kotlin/com/chimali/MainActivity.kt](../../app/src/main/kotlin/com/chimali/MainActivity.kt) resolves [Fido2RegistrationNavGraph.kt](../../feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/navigation/Fido2RegistrationNavGraph.kt)
- [ ] T021 [US3] Run local-ci compile phase: `.\gradlew compileDebugSources compileAndroidMain compileDebugUnitTestSources compileAndroidHostTest compileDebugAndroidTestSources compileAndroidDeviceTest --continue` — all pass

### Static analysis (source fixes only — FR-009)

- [ ] T022 [P] [US3] Fix all ktlintCheck failures in Kotlin source (any module); no editorconfig changes
- [ ] T023 [P] [US3] Fix all detekt failures in Kotlin source (any module); no edits to `config/detekt/detekt-baseline.xml` or `feature/fido2/detekt-baseline.xml`; preserve existing `@Suppress("UNCHECKED_CAST")` in tests unless removal is part of a substantive code fix (FR-010)
- [ ] T024 [US3] Fix `lintRelease` issues in any failing module

### Tests and full gate

- [ ] T025 [US3] Run `.\gradlew test` — update assertions or remove obsolete tests only; no `@Ignore` / `@Disabled` (FR-008, SC-002)
- [ ] T026 [US3] Run `.\tools\local-ci.ps1` — must exit 0 (FR-007, SC-003, SC-005)

**Checkpoint**: US3 done — branch merge-ready per Constitution §IX.

---

## Phase 4: User Story 2 — Unchanged runtime behavior (Priority: P2)

**Goal**: No regression in Bluetooth HID capability checks or crash-log locking semantics.

**Independent Test**: Host tests for log writer pass; capability behavior matches pre-migration contract.

- [ ] T027 [US2] Run `.\gradlew :feature:fido2:testAndroidHostTest` — [LocalCrashReportingLogWriterTest.kt](../../feature/fido2/src/test/kotlin/com/chimali/fido2/util/logging/LocalCrashReportingLogWriterTest.kt) passes
- [ ] T028 [US2] Review `AndroidPlatformBluetoothHid` vs deleted `actual class` — same `isSupported` / `isAdapterEnabled` logic; confirm `IosPlatformBluetoothHid` / `IosPlatformLock` placeholders unchanged (SC-004)

**Checkpoint**: US2 validated after US3 compile/test green.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [x] T029 [P] Mark spec 040 KMP exclusion rows superseded in [specs/040-lint-remediation/tasks.md](../040-lint-remediation/tasks.md) and [quickstart.md](../040-lint-remediation/quickstart.md)
- [ ] T030 Add changelog entry under `docs/changelogs/` when T026 passes

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Phase 1 (Setup)
    ↓
Phase 2 (US1) ✅ DONE on branch
    ↓
Phase 3 (US3) ← CURRENT — blocks merge
    ↓
Phase 4 (US2) — after US3 tests compile/run
    ↓
Phase 5 (Polish)
```

### User Story Dependencies

| Story | Depends on | Notes |
|-------|------------|-------|
| US1 | Setup | **Complete** (WIP) |
| US3 | US1 (done) | Can start at T012; T002 optional baseline |
| US2 | US3 | Needs `testAndroidHostTest` / full test run |
| Polish T030 | US3 T026 | Changelog after green CI |

### Parallel Opportunities

**US3 after T012** (source-set fix unblocks test file work):

```text
T013 ∥ T014 ∥ T015 ∥ T016 ∥ T017 ∥ T018   (different test files)
T022 ∥ T023                                 (ktlint vs detekt, different modules/files)
```

**US1** (already done in parallel):

```text
T003–T005 ∥ T006–T008   (Bluetooth HID vs PlatformLock files)
```

---

## Implementation Strategy

### Current state (WIP)

1. ✅ US1 — KMP interface migration landed (T003–T011)
2. 🔲 US3 — Start at **T012** (host-test classpath); then parallel test fixes T013–T018
3. Run T021 → T022–T024 → T025 → **T026** (`local-ci.ps1`)
4. US2 validation T027–T028
5. T030 changelog

### MVP for merge

Minimum to unblock: **Complete US3 (T012–T026)**. US1 already satisfies structural FRs; US2 is validation.

### Suggested `/speckit-implement` focus

```text
Next task: T012 → T013–T018 (parallel) → T019–T021 → T022–T024 → T025 → T026
```

---

## Notes

- Do not edit detekt baselines or ktlint config (clarify session / FR-009).
- Do not disable failing tests — update or delete with justification (FR-008).
- Production fixes allowed in **any** module that fails CI (clarify session).
- iOS native link disabled off-macOS — not required for Windows `local-ci.ps1` pass.
