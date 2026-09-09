# Vault Completion Handoff

**Updated**: 2026-09-09  
**Current feature**: `053-vault-completion`, confirmed by `.specify/feature.json`  
**Status**: Ownership remediation complete in the task record; feature acceptance still pending.

## Read first

- [Specification](spec.md), [plan](plan.md), and [tasks](tasks.md) define current scope and status.
- [Constitution](../../.specify/memory/constitution.md) is at approved version 1.1.0.
- [Quickstart and verification history](quickstart.md) records commands, failures, fixes, and results.
  Its final dated results supersede earlier pending-scope, coverage, and device-availability notes.
- [Research](research.md) records platform text boundaries and retention/disclosure controls.
- [Scope proposal](t039-scope-proposal.md) and [coverage proposal](t039-coverage-proposal.md)
  document approved constraints; do not interpret old universal-erasure wording as the current scope.

The root AGENTS.md still points to feature 051 for general project context. Use this feature's
artifacts for Vault intent; do not accidentally continue feature 051.

## Completed work recorded in the workspace

- Vault/passkey aggregate and event-store qualifiers, startup module wiring, and the Vault event
  discriminator correction address the reported save-path failures.
- Save completion/error state, duplicate-submission prevention, retained retry drafts, label flows,
  editing/discard handling, and mutable payload ownership are implemented in the current task record.
- T039 and T055–T062 are marked complete: mutable drafts and titles, bounded payload codec,
  crypto/key cleanup, cancellation/disposal ownership, and controlled platform text adapters.
- Title projection migration 5 changes TEXT to UTF-8 BLOB. Frozen payload and event/snapshot
  fixtures preserve compatibility; AES-256-GCM payload protection remains required.
- The specification-quality checklist has all 16 items checked after moving technical detail into
  the plan. This is specification quality, not proof that all runtime acceptance has passed.

## Recorded verification, not rerun for this handoff

| Area | Latest recorded evidence | Limit |
| --- | --- | --- |
| Actual editor input connections | 3/3 API 35 cases pass | Configured controls; no claim of erasing OS/keyboard copies |
| Vault ownership navigation | 15/15 API 35 cases pass after title migration; `.gradle/t039-vaultitem-title-device.log` | Real screens/ViewModel/crypto with synthetic storage; not full production storage/passkey isolation |
| Host/build checks | Domain/data/Vault tests, migration verification, listed static checks, app and Vault Android-test compilation recorded passing | Does not establish a new full local-CI run |
| Clipboard | Focused timeout/ownership host tests recorded passing | End-to-end device timeout remains pending |
| Scoped coverage | Crypto 83/83 lines, 15/15 branches; mutable field 26/26, 10/10; codec 357/357, 207/208 | Sole terminal EOF throw accepted under approved XII.3 rule with independent exception/cleanup assertions |

The `.gradle` logs and build reports are local generated evidence and may not survive cleanup or a
fresh checkout. Preserve needed evidence before cleaning. Historical failures remain in quickstart
for diagnosis, including MaterialTheme/idleness and system-back synchronization corrections.

## Remaining work

Only **T044** and **T046** are unchecked in the current tasks file.

1. **T044 — production coexistence and storage acceptance.** Execute the production-composition
   `app/src/androidTest/kotlin/com/chimali/di/VaultPasskeyWiringTest.kt` for both module orders.
   Complete/verify all-type create, reopen, update, delete, event/projection checks, unchanged
   pre-existing passkeys, and passkey save/authentication regression. The old placeholder Vault
   service tests are deleted in the workspace; inspect replacement integration coverage against
   each acceptance requirement rather than treating deletion or compilation as completion.
2. **T046 — end-to-end clipboard acceptance.** Exercise actual detail-screen copy, verify the
   copied value and success feedback, and verify sensitive clipboard clearing after 60 seconds
   on Android. Host service tests alone do not close this task.
3. **Feature-wide evidence reconciliation.** Quickstart still notes uncaptured fresh-install,
   restart, clipboard-timeout, and 500-item performance evidence. Complete the missing matrix and
   record results even where historical tasks are checked; do not infer acceptance from markers.

## Resume commands

Run from the repository root. These are continuation commands, not newly executed results.
Check connected devices before selecting connected tests. The managed-device init script provides
the Vault device suite; do not assume it also provisions the app production-graph suite.

```powershell
adb devices -l
.\gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.di.VaultPasskeyWiringTest'
.\gradlew --no-configuration-cache -I tools/vault-managed-device.init.gradle :feature:vault:vaultApi35DebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.feature.vault.ui.navigation.VaultOwnershipNavigationTest' '-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect'
.\gradlew --no-configuration-cache -I tools/vault-memory-coverage.init.gradle :feature:vault:vaultMemoryCoverage
```

Add/run the missing end-to-end clipboard assertion and any uncovered production-storage scenarios,
then record exact results in quickstart and update task markers only after their full criteria pass.
Run `tools/local-ci.ps1` before committing under the constitution. A follow-up convergence review
should check implementation against the current spec/plan/tasks before declaring feature completion.

## Working tree and preservation notes

At handoff creation, substantial implementation and documentation changes were already staged,
including changes outside this feature. This pass adds/updates documentation without committing,
restaging, or altering those implementation changes. Review both staged and unstaged diffs before
preparing a commit; do not discard unrelated work.

Keep synthetic secrets in test runs. Preserve frozen compatibility fixtures and existing user data;
do not reset databases to make migration or coexistence tests pass. App-owned arrays must be erased;
the approved platform-adapter scope does not claim erasure of framework or operating-system copies.

## Change records

- [Root changelog](../../CHANGELOG.md)
- [Ownership remediation](../../docs/changelogs/2026-09-09-vault-secret-ownership-remediation.md)
- [Documentation and handoff update](../../docs/changelogs/2026-09-09-vault-completion-handoff.md)
