# Tasks: Lint Remediation

**Input**: Design documents from `/specs/040-lint-remediation/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: Not requested — existing test suites must continue to pass; no new test tasks generated.

**Organization**: Tasks are grouped by user story (P1→P4) to enable independent implementation and validation.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify baseline compliance before any modifications

- [x] T001 Run `tools/local-ci.ps1` and capture baseline output to confirm current green state before modifications
- [x] T002 Run `Select-String -Path "feature/**/*.kt","core/**/*.kt" -Pattern '@Suppress' -Recurse` to capture the pre-remediation suppression count for SC-007 measurement (Baseline: 110)

---

## Phase 2: User Story 1 — Remove Redundant Compose Suppressions (Priority: P1) 🎯 MVP

**Goal**: Remove all `@Suppress("FunctionNaming")` annotations from `@Composable` functions across `feature/vault/` and `feature/fido2/` UI files. The Detekt config (`config/detekt/detekt.yml` line 340, `ignoreAnnotated: ['Composable']`) already exempts these.

**Independent Test**: `detektDebug` passes with zero `FunctionNaming` violations. `Select-String -Pattern '@Suppress\("FunctionNaming"\)' -Path "feature/**/*.kt" -Recurse` returns zero results.

### Implementation for User Story 1 — Vault UI (8 files)

- [x] T003 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/vault/src/main/java/com/chimali/feature/vault/ui/VaultListScreen.kt`
- [x] T004 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/vault/src/main/java/com/chimali/feature/vault/ui/SecureNoteEntryScreen.kt`
- [x] T005 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordEntryScreen.kt`
- [x] T006 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/vault/src/main/java/com/chimali/feature/vault/ui/SecureNoteDetailScreen.kt`
- [x] T007 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/vault/src/main/java/com/chimali/feature/vault/ui/PasswordDetailScreen.kt`
- [x] T008 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/vault/src/main/java/com/chimali/feature/vault/ui/LabelManagerScreen.kt`
- [x] T009 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/vault/src/main/java/com/chimali/feature/vault/ui/CreditCardEntryScreen.kt`
- [x] T010 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/vault/src/main/java/com/chimali/feature/vault/ui/CreditCardDetailScreen.kt`

### Implementation for User Story 1 — Fido2 UI (9 files)

- [x] T011 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/Fido2HomeScreen.kt`
- [x] T012 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt`
- [x] T013 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/RegistrationPromptScreen.kt`
- [x] T014 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/RegistrationProgressIndicator.kt`
- [x] T015 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/PairedDevicesSection.kt`
- [x] T016 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/EditPairedDeviceScreen.kt`
- [x] T017 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/BiometricPromptComponent.kt`
- [x] T018 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/AuthenticationPromptScreen.kt`
- [x] T019 [P] [US1] Remove all `@Suppress("FunctionNaming")` annotations in `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialComponents.kt`

### Validation for User Story 1

- [x] T020 [US1] Run `tools/local-ci.ps1` and verify zero `FunctionNaming` violations. Run `Select-String -Pattern '@Suppress\("FunctionNaming"\)' -Path "feature/**/*.kt" -Recurse` and confirm zero results (SC-001)

**Checkpoint**: US1 complete — all 35 redundant `FunctionNaming` suppressions removed. CI green.

---

## Phase 3: User Story 2 — Resolve ForbiddenComment Suppressions (Priority: P2)

**Goal**: Remove all `@Suppress("ForbiddenComment")` annotations. Category A (stale, no TODO content) — remove outright. Category B (genuine TODOs) — replace `TODO:` with `DEFERRED(040):` format per research.md decision.

**Independent Test**: `Select-String -Pattern '@Suppress\("ForbiddenComment"\)' -Path "feature/**/*.kt","core/**/*.kt" -Recurse` returns zero results. `detektDebug` reports zero `ForbiddenComment` violations (SC-002).

### Category A — Stale Suppressions (no underlying TODOs)

- [x] T021 [P] [US2] Remove `@Suppress("ForbiddenComment")` from class-level annotation (line 23) in `core/data/src/main/kotlin/com/chimali/core/data/eventsourcing/EventStoreRepositoryImpl.kt`
- [x] T022 [P] [US2] Remove `@Suppress("ForbiddenComment")` from class-level annotation (line 19) in `core/data/src/main/kotlin/com/chimali/core/data/eventsourcing/SnapshotRepositoryImpl.kt`
- [x] T023 [P] [US2] Remove `@Suppress("ForbiddenComment")` from class-level annotation (line 21) in `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/data/eventsourcing/PasskeyEventStoreRepositoryImpl.kt`
- [x] T024 [P] [US2] Remove `@Suppress("ForbiddenComment")` from class-level annotation (line 17) in `feature/fido2/src/commonMain/kotlin/com/chimali/fido2/data/eventsourcing/PasskeySnapshotRepositoryImpl.kt`
- [x] T024a [P] [US2] In `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialComponents.kt`: remove `"ForbiddenComment"` from suppression list (line 123) (Stale suppression)

### Category B — Genuine TODO Conversions

- [x] T025 [P] [US2] In `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultViewModel.kt`: remove both `@Suppress("ForbiddenComment")` (lines 15, 90) and replace `// TODO: Trigger actual payload decryption and UI state update here` (line 96) with `// DEFERRED(040): Payload decryption — pending VaultCryptoService integration`
- [x] T026 [P] [US2] In `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/Fido2RepositoryImpl.kt`: remove `@Suppress("ForbiddenComment")` (line 14) and replace `// TODO: Implement FIDO2 registration logic` (line 23) with `// DEFERRED(040): FIDO2 registration — pending CTAP2 ceremony implementation` and replace `// TODO: Implement FIDO2 authentication logic` (line 28) with `// DEFERRED(040): FIDO2 authentication — pending CTAP2 ceremony implementation`
- [x] T027 [P] [US2] In `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/UserConsentRepositoryImpl.kt`: remove `@Suppress("ForbiddenComment")` (line 11) and replace all 4 `// TODO:` comments (lines 14, 19, 24, 29) with `// DEFERRED(040): Consent persistence — pending schema design`
- [x] T028 [P] [US2] In `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/RelyingPartyRepositoryImpl.kt`: remove `@Suppress("ForbiddenComment")` (line 11) and replace all 5 `// TODO:` comments (lines 14, 19, 24, 29, 37) with `// DEFERRED(040): RP persistence — pending schema design`
- [x] T029 [P] [US2] In `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/service/impl/UserVerificationServiceImpl.kt`: remove `@Suppress("ForbiddenComment")` (line 22) and replace `// TODO: Persist consent record` (line 163) with `// DEFERRED(040): Consent persistence — pending UserConsentRepository completion` and replace `// TODO: Return persisted records` (line 171) with `// DEFERRED(040): Consent query — pending UserConsentRepository completion`
- [x] T029a [P] [US2] In `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/management/CredentialListScreen.kt`: remove `@Suppress("ForbiddenComment")` (line 65) and replace stale `// TODO:` with `// DEFERRED(040): Credential sorting — pending preference integration`
- [x] T029b [P] [US2] In `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/DevelopmentToolsScreen.kt`: remove `"ForbiddenComment"` from suppression list (line 719) and replace `// TODO:` (line 725) with `// DEFERRED(040): Debug logs — pending Timber integration`
- [x] T029c [P] [US2] In `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/AuthenticationPromptScreen.kt`: remove `"ForbiddenComment"` from suppression list (line 78) and replace `// TODO:` (line 85) with `// DEFERRED(040): Biometric error handling`
- [x] T029d [P] [US2] In `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/ui/RegistrationPromptScreen.kt`: remove `"ForbiddenComment"` from suppression list (line 302) and replace `// TODO:` (line 310) with `// DEFERRED(040): Attestation verification`
- [x] T029e [P] [US2] In `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/presentation/navigation/Fido2RegistrationNavGraph.kt`: remove `"ForbiddenComment"` from suppression list (line 63) and replace `// TODO:` (line 70) with `// DEFERRED(040): Deep link handling`

### Validation for User Story 2

- [x] T030 [US2] Run `tools/local-ci.ps1` and verify zero `ForbiddenComment` violations. Run `Select-String -Pattern '@Suppress\("ForbiddenComment"\)' -Path "feature/**/*.kt","core/**/*.kt" -Recurse` and confirm zero results (SC-002)

**Checkpoint**: US2 complete — all 10 `ForbiddenComment` suppressions removed, underlying TODOs either eliminated (Category A) or converted to `DEFERRED(040):` format (Category B). CI green.

---

## Phase 4: User Story 3 — Migrate Generic Exception Handling to Outcome (Priority: P3)

**Goal**: Remove all `@Suppress("TooGenericExceptionCaught")` from production code (excluding `FunctionalCatching.kt`). Replace `catch(e: Exception)` with specific exception types mapped to `DomainError` subtypes. Use `runCatchingOutcome` from `FunctionalCatching.kt` where a true safety-net catch is needed.

**Independent Test**: `Select-String -Pattern '@Suppress\("TooGenericExceptionCaught"\)' -Path "feature/**/*.kt","core/**/*.kt" -Recurse` returns only hits in `FunctionalCatching.kt`. All existing unit tests pass (SC-003, SC-006).

### Implementation for User Story 3

- [x] T031 [US3] In `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultRepositoryImpl.kt`: remove class-level `@Suppress("TooGenericExceptionCaught")` (line 19). In `saveItem()` (line 103), replace `catch (e: Exception)` with `catch (e: android.database.SQLException)` mapped to `DomainError.DatabaseError` and `catch (e: IllegalStateException)` mapped to `DomainError.OperationDenied`. In `deleteItem()` (line 121), apply the same pattern. The `getItems()` function already uses specific catches and needs no change.
- [x] T032 [P] [US3] In `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain/usecase/RegisterCredentialUseCase.kt`: remove `@Suppress("TooGenericExceptionCaught")` (line 84). The function already catches `IllegalArgumentException` and `IllegalStateException` specifically (lines 160-163). Replace the terminal `catch (e: Exception)` (line 164) with `catch (e: java.security.GeneralSecurityException)` mapped to `DomainError.CryptoError` for crypto failures, plus use `runCatchingOutcome` wrapper from `FunctionalCatching.kt` for the outermost boundary if a true safety-net is still needed.
- [x] T033 [P] [US3] In `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/repository/PairedDeviceRepositoryImpl.kt`: remove all 3 `@Suppress("TooGenericExceptionCaught")` annotations (lines 43, 76, 89). Replace each `catch (e: Exception)` (lines 71, 84, 94) with `catch (e: android.database.SQLException)` mapped to `DomainError.DatabaseError` and `catch (e: IllegalArgumentException)` mapped to `DomainError.ValidationError`.
- [x] T034 [P] [US3] In `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt`: remove `@Suppress("TooGenericExceptionCaught")` from `generateCredentialKeyPair()` (line 110), `warmUpMasterSeed()` (line 297), and `sign()` (line 384). For `generateCredentialKeyPair()` and `sign()`, replace `catch (e: Exception)` with `catch (e: java.security.GeneralSecurityException)` and `catch (e: IllegalStateException)`. For `warmUpMasterSeed()`, which is non-fatal fire-and-forget, use `runCatchingOutcome` or narrow to `catch (e: java.security.GeneralSecurityException)` + `catch (e: IllegalStateException)`, logging and discarding the error.

### Validation for User Story 3

- [x] T035 [US3] Run `tools/local-ci.ps1` and verify all tests pass. Run `Select-String -Pattern '@Suppress\("TooGenericExceptionCaught"\)' -Path "feature/**/*.kt","core/**/*.kt" -Recurse` and confirm only `FunctionalCatching.kt` appears (SC-003, SC-006)

**Checkpoint**: US3 complete — all 8 remediable `TooGenericExceptionCaught` suppressions removed. Exception handling follows constitutional mandate. CI green.

---

## Phase 5: User Story 4 — Address Deprecated API Usage (Priority: P4)

**Goal**: Replace the broad `@Suppress("DEPRECATION")` annotations in `BluetoothHidDeviceWrapper.kt` with a single SDK-version-gated compat extension function. The suppression is narrowed to the minimal scope.

**Independent Test**: `detektDebug` passes. The `@Suppress("DEPRECATION")` exists only inside the new `getParcelableExtraCompat` helper (1 line, narrowest scope). Compilation succeeds without deprecation warnings at call sites.

### Implementation for User Story 4

- [x] T036 [US4] In `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/bluetooth/BluetoothHidDeviceWrapper.kt`: add a private inline extension function `Intent.getParcelableExtraCompat<T : Parcelable>(key: String): T?` that uses `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU` to call the new API and falls back to the deprecated API with `@Suppress("DEPRECATION")` narrowed to the single expression. Replace the two call sites (lines ~252, ~322) to use `intent.getParcelableExtraCompat<BluetoothDevice>(...)` and remove both existing `@Suppress("DEPRECATION")` annotations.

### Validation for User Story 4

- [x] T037 [US4] Run `tools/local-ci.ps1` and verify zero deprecation warnings in the affected module. Confirm `@Suppress("DEPRECATION")` exists only inside the compat helper function (SC-004)

**Checkpoint**: US4 complete — deprecated API modernized with SDK-gated compat pattern. Suppression narrowed to 1 expression. CI green.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final verification, measurement, and documentation

- [x] T038 Run full `tools/local-ci.ps1` (Detekt, Ktlint, all unit tests) and confirm zero violations across all modules (SC-005, SC-006)
- [x] T038a Verify that `config/detekt/detekt-baseline.xml` and module-level baselines have NOT been modified unless explicitly documented for a deferred resolution (FR-007)
- [x] T039 [P] Capture final suppression count via `Select-String -Pattern '@Suppress' -Path "feature/**/*.kt","core/**/*.kt" -Recurse` and compute SC-007: at least 80% reduction from pre-remediation baseline (T002 output)
- [x] T040 [P] Verify exclusion list: confirm `FunctionalCatching.kt` retains `TooGenericExceptionCaught`, KMP files retain `EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING`, and test files retain `UNCHECKED_CAST` — per FR-006

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — capture baseline first
- **US1 (Phase 2)**: Depends on Setup (Phase 1)
- **US2 (Phase 3)**: Depends on Setup (Phase 1) — can run in parallel with US1
- **US3 (Phase 4)**: Depends on Setup (Phase 1) — can run in parallel with US1 and US2
- **US4 (Phase 5)**: Depends on Setup (Phase 1) — can run in parallel with US1, US2, and US3
- **Polish (Phase 6)**: Depends on ALL user stories completing

### User Story Dependencies

- **US1 (P1)**: No dependencies on other stories — pure annotation removal
- **US2 (P2)**: No dependencies on other stories — annotation removal + TODO conversion
- **US3 (P3)**: No dependencies on other stories — catch block refactoring
- **US4 (P4)**: No dependencies on other stories — single file modification

### Within Each User Story

- All tasks marked [P] within a story can be executed in parallel (different files)
- Validation task is the final task in each story (depends on all preceding tasks)

### Parallel Opportunities

- **All 4 user stories are fully independent** and can be executed in parallel
- Within US1: all 17 file tasks (T003–T019) can run simultaneously
- Within US2: all 9 file tasks (T021–T029) can run simultaneously
- Within US3: T032, T033, T034 can run in parallel; T031 is independent
- Phase 6 tasks T039 and T040 can run in parallel after T038

---

## Parallel Example: User Story 1

```powershell
# All 17 files can be edited simultaneously since each is independent:
# Vault UI (8 files):
Task: "Remove @Suppress('FunctionNaming') in VaultListScreen.kt"
Task: "Remove @Suppress('FunctionNaming') in SecureNoteEntryScreen.kt"
# ... (6 more vault files)
# Fido2 UI (9 files):
Task: "Remove @Suppress('FunctionNaming') in Fido2HomeScreen.kt"
Task: "Remove @Suppress('FunctionNaming') in DevelopmentToolsScreen.kt"
# ... (7 more fido2 files)
```

---

## Parallel Example: User Story 2

```powershell
# All 9 remediation tasks can run simultaneously:
Task: "Remove stale suppress in EventStoreRepositoryImpl.kt"
Task: "Remove stale suppress in SnapshotRepositoryImpl.kt"
Task: "Convert TODOs in VaultViewModel.kt"
Task: "Convert TODOs in Fido2RepositoryImpl.kt"
# ... (5 more files)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (baseline capture)
2. Complete Phase 2: User Story 1 (remove 35 FunctionNaming suppressions)
3. **STOP and VALIDATE**: Run local-ci.ps1 — this alone delivers ~63% of the total reduction
4. Commit if green

### Incremental Delivery

1. Complete Setup → Baseline captured
2. US1 (17 files) → 35 suppressions removed → CI green → Commit
3. US2 (9 files) → 10 suppressions removed → CI green → Commit
4. US3 (4 files) → 8 suppressions removed → CI green → Commit
5. US4 (1 file) → 2 suppressions narrowed → CI green → Commit
6. Polish → Final count verified → SC-007 measured

### Files NOT to Touch

| File | Suppression | Reason |
|------|------------|--------|
| `core/common/.../FunctionalCatching.kt` | `TooGenericExceptionCaught` | Architectural boundary (FR-006) |
| `feature/fido2/.../PlatformBluetoothHid.kt` | `EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING` | Valid KMP suppression (FR-006) |
| `feature/fido2/.../PlatformUserVerification.kt` | `EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING` | Valid KMP suppression (FR-006) |
| `feature/fido2/.../Ctap2AttestationStatementTest.kt` | `UNCHECKED_CAST` | Valid test scope (FR-006) |
| `feature/fido2/.../Ctap2Fido21FlagsTest.kt` | `UNCHECKED_CAST` | Valid test scope (FR-006) |

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story is independently completable and testable
- Commit after each story's validation task
- `FunctionalCatching.kt` is the ONLY permitted site for `catch(e: Throwable)` — do not touch
- The `DEFERRED(040):` format was chosen specifically because Detekt's `ForbiddenComment` rule only triggers on `TODO:`, `FIXME:`, and `STOPSHIP:` — not on `DEFERRED`
