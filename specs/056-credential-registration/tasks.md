# Tasks: Credential registration failure

**Input**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md)

## Phase 1: Setup

- [x] T001 Create `bug/056-credential-registration` through the Spec Kit git hook and record
  baseline/stash identity in `specs/056-credential-registration/research.md`.
- [x] T002 Complete `specs/056-credential-registration/checklists/requirements.md` and design artifacts.

## Phase 2: Root-cause reproduction

- [x] T003 [US1] Add `app/src/androidTest/kotlin/com/chimali/CredentialCryptoProviderTest.kt` and
  demonstrate missing crypto services against the optimized target APK before changing rules.

## Phase 3: User Story 1 - Register and use optimized credentials (P1)

**Independent test**: target APK key conversion plus valid/modified-message signature checks for
ES256, Ed25519, and ML-DSA-65 on ASUS.

- [x] T004 [US1] Add narrowly scoped reflection keeps in `feature/fido2/proguard-rules.pro`.
- [x] T005 [US1] Repeat `CredentialCryptoProviderTest` and verify all three algorithms pass on ASUS.

## Phase 4: Validation

- [x] T006 Run existing host tests and quality checks; record results in
  `specs/056-credential-registration/verification.md`.
- [x] T007 Build release AAB, compare matched binary sizes, and inspect retained provider classes;
  record exact evidence and remaining browser limitations in `specs/056-credential-registration/verification.md`.

## Dependencies and execution

T001 -> T002 -> T003 -> T004 -> T005 -> T006 -> T007. The single story is the MVP. No agent
delegation or parallel source edits are needed. After T005, documentation review and read-only
artifact inspection may run independently of host validation. Tests precede the packaging fix.

## Hooks

Mandatory `speckit.git.feature` executed. Optional commit hooks are disabled by git-config.yml;
they are skipped for specify, plan, tasks, and implement. No auto-commit is made.

## Completion

All seven tasks completed. The dedicated bug workflow became available during this work and its
assessment, fix, and verification reports are in `.specify/bugs/credential-registration/`.
Three controlled device tests and 458 existing host tests pass. Browser ceremony confirmation is
recorded as a separate follow-up and is not claimed by these results.
