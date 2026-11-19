# Vault editor background session cleanup

**Finding:** SEC065-VAULT-008  
**Requirements:** T005/T006/T015/T033; CLS-VAULT-001/002/003; BND-UI-001/005; FR-003/004/010; NFR-SEC-041  
**Date:** 2026-11-16  
**Revision:** current dirty exploratory worktree; not a frozen release candidate

## Finding and correction

Vault editor screens erased their mutable drafts when their navigation route was disposed. If the app moved to the background, the navigation graph remained composed, so editor field arrays and the independent edit-session payload could remain live. A pending submission could also remain active until its normal completion or graph disposal.

The Vault graph's lifecycle `ON_STOP` handler now clears the list and selected detail owners, cancels/abandons a pending mutation, zeroes any independent edit-session copy and label selection, closes the add-type sheet, and returns navigation to the list. Popping the editor route invokes its existing draft-field cleanup. On the next `ON_START`, item and label data are loaded again; the abandoned editor draft is not restored. Latest Compose state is read by the lifecycle observer through updated callbacks, avoiding a stale closure over the initial empty edit-state values.

## Verification

Ran `:feature:vault:testDebugUnitTest :feature:vault:ktlintCheck :feature:vault:detekt`; 96 tests across 22 reports pass with zero failures/errors/skips, and KtLint/Detekt pass. On the API 35 AOSP emulator, the isolated `backgroundingDiscardsEditorDraftAndReturnsToReloadedList[PASSWORD]` instrumentation case passed one test. It edits a synthetic item, moves the activity to `CREATED`, verifies list/detail owners are cleared and no save remains pending, resumes the activity, then verifies the original list item is shown and the edited draft is absent.

This scoped test does not establish third-party keyboard, Compose renderer/accessibility, heap-root, other-device, or frozen-candidate behavior. The full parameterized navigation class had two previously observed system-back timeouts in separate editor-opening cases; those remain separately recorded under `vault-list-background-owner.md`.

## Artifacts

- `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`
- `feature/vault/src/androidTest/java/com/chimali/feature/vault/ui/navigation/VaultOwnershipNavigationTest.kt`
- `build/security/vault-editor-background-api35-20261116.json`
- `feature/vault/build/test-results/testDebugUnitTest/TEST-com.chimali.feature.vault.internal.VaultViewModelTest.xml`
