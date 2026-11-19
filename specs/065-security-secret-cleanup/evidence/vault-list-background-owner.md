# Vault list background owner cleanup

**Finding:** SEC065-VAULT-007  
**Requirements:** T005/T006/T015/T033; CLS-VAULT-001/003; BND-UI-005; FR-003/010; NFR-SEC-041  
**Date:** 2026-11-16  
**Revision:** current dirty exploratory worktree; not a frozen release candidate

## Finding and correction

`VaultViewModel.state.items` owns mutable `VaultItem` title, encrypted payload, and CRDT arrays used by the vault list. Before this change, those arrays were cleared when a list response replaced them or when the ViewModel was destroyed. The activity could remain alive while backgrounded, leaving the list owner populated. In addition, an in-flight item query could complete after background cleanup and restore its result into state.

The vault graph now clears list items and label references on lifecycle `ON_STOP`, then reloads the retained filter and labels on `ON_START`. `VaultViewModel.ClearListData` removes state references and zeroes item arrays. Independent item and label load generations reject responses that complete after cleanup or a newer request; stale successful item results are zeroed instead of published. Label names are immutable Strings, so state cleanup releases the application's references but cannot erase Compose or runtime copies. Initial composition retains its existing loads and does not issue an extra load for the observer's initial lifecycle synchronization.

The list title still crosses into immutable Compose text while visible. Compose renderer, accessibility, screenshot and compositor snapshots remain outside app-owned array cleanup and are not covered by this change.

## Verification

Ran `:feature:vault:testDebugUnitTest :feature:vault:ktlintCheck :feature:vault:detekt`. The full Vault unit suite passed 96 tests across 22 XML reports with zero failures, errors or skips; KtLint and Detekt passed. `VaultViewModelTest` includes a foreground-clear case and a deferred-query regression proving a response completing after cleanup is erased and not restored into state.

This is host-side ownership evidence. It does not establish lifecycle callback behavior on Android, renderer/IME retention, heap roots, or frozen-candidate behavior. The full Feature 065 source inventory and release gates remain open.

The same source was installed as the isolated Vault instrumentation APK on the API 35 AOSP emulator only (`emulator-5554`; the Mi A2 was not selected). The final method-filtered instrumentation run passed the background/resume case once for the password parameter (`OK (1 test)`); it confirmed item arrays were zeroed, list and label state references were cleared, then new items and labels loaded after resume. The full parameterized navigation class ran 18 cases against the earlier item-only cleanup build: the lifecycle case passed for all three Vault item types, while two existing `systemBackDiscardReloadsOriginalDetails` cases timed out during editor opening for password and card. The full class run therefore remains Failed overall; these failures did not occur in the new background/resume case. Existing list-visible UI cases also passed in that run. Sanitized run details and separate APK hashes are in `build/security/vault-list-lifecycle-api35-20261116.json`. Do not claim the full navigation class is green from this run.

## Artifacts

- `feature/vault/src/main/java/com/chimali/feature/vault/api/VaultMvi.kt`
- `feature/vault/src/main/java/com/chimali/feature/vault/internal/VaultViewModel.kt`
- `feature/vault/src/main/java/com/chimali/feature/vault/ui/navigation/VaultNavGraph.kt`
- `feature/vault/src/test/java/com/chimali/feature/vault/internal/VaultViewModelTest.kt`
- `feature/vault/src/androidTest/java/com/chimali/feature/vault/ui/navigation/VaultOwnershipNavigationTest.kt`
- `feature/vault/build/test-results/testDebugUnitTest/TEST-com.chimali.feature.vault.internal.VaultViewModelTest.xml` (SHA-256 `9269793743b5c661e9ecf7aa6185aee06af4d3f89db21f88700b4cafbe6829f2`)
