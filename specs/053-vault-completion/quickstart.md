> **Approved scope update (2026-09-09):** Constitutions 1.0.0 and 1.1.0, plus the approved T039 scope and coverage proposals, govern this document. Earlier universal framework-erasure gate statements are historical. App-owned sensitive storage must remain mutable and explicitly cleaned; app references must be released. Only necessary, audited platform text adapters may use immutable copies, with configured controls and documented residual risk. Critical coverage and actual runtime verification remain mandatory; Constitution XII.3's narrowly documented direct-throw evidence rule applies only where its conditions are met.

# Quickstart: Vault Feature Validation Guide

**Feature**: `053-vault-completion`  
**Date**: 2026-09-07  
**Status**: Ready for Verification

## 1. Prerequisites

1. App builds and compiles successfully:
   ```bash
   ./gradlew :feature:vault:assembleDebug
   ```
2. Unit tests pass:
   ```bash
   ./gradlew :feature:vault:testDebugUnitTest
   ```

---

## 2. End-to-End Validation Scenarios

### Scenario 1: Password Creation and Decryption Flow
1. Launch Chimali app and ensure "Vault" is enabled in Onboarding or Settings.
2. Navigate to the **Vault** tab via bottom bar.
3. Click the Floating Action Button (`+`).
4. Select **Password** from the item type selection.
5. Fill in:
   - **Title**: `Work Email`
   - **Username**: `alice@corp.com`
   - **Password**: `P@ssw0rd!123`
   - **Website**: `https://mail.corp.com`
6. Click **Save**.
7. **Verification**:
   - The user returns to `VaultListScreen`.
   - `Work Email` is listed under "All" items.
8. Tap on `Work Email`.
9. **Verification**:
   - Opens `PasswordDetailScreen`.
   - Website `https://mail.corp.com` and Username `alice@corp.com` are displayed.
   - Password is masked by default with bullets (`••••••••`).
   - Tapping the visibility toggle renders `P@ssw0rd!123` in Atkinson Hyperlegible font.
10. Click **Copy** next to password.
    - Status message shows "Copied to clipboard".
    - After 60 seconds, clipboard is cleared.

### Scenario 2: Credit Card and Note Storage Flow
1. From `VaultListScreen`, tap `+` -> **Credit Card**.
2. Enter Cardholder Name, Number, Expiration (`12/28`), CVV (`456`), and Title `Personal Visa`.
3. Save and confirm entry in list.
4. Tap entry to verify details view.
5. Repeat for **Secure Note** (`Server Recovery Key`, body `xyz-123-recovery`).

### Scenario 3: Item Deletion Flow
1. Open `Work Email` detail screen.
2. Tap the **Delete** icon.
3. Confirm deletion dialog.
4. User returns to `VaultListScreen`.
5. Entry `Work Email` is no longer present.

---

## 3. Automated Verification Commands

```powershell
# Run Vault unit and crypto tests
./gradlew :feature:vault:testDebugUnitTest

# Run Detekt and Ktlint quality enforcement
./gradlew detekt ktlintCheck
```

## 4. Regression Verification Record

The following checks were executed on 2026-09-08 from the repository root:

| Check | Result |
|---|---|
| `./gradlew :feature:vault:testDebugUnitTest` | PASS |
| `./gradlew :core:data:testDebugUnitTest` | PASS |
| `./gradlew :app:compileDebugKotlin` | PASS |
| `./gradlew :feature:vault:compileDebugKotlin` | PASS |
| `./gradlew :feature:vault:ktlintCheck` | PASS |
| `./gradlew :feature:vault:detekt` | PASS |
| `./gradlew :feature:vault:assembleDebug` | PASS |
| `./gradlew :app:compileDebugKotlin :feature:fido2:compileAndroidMain` | PASS |
| `./gradlew :app:connectedShrunkDebugAndroidTest` | PASS (moto g stylus 5G (2022), Android 13; 2026-09-08) |
| `./gradlew :core:data:testDebugUnitTest --tests com.chimali.core.data.eventsourcing.EventStoreRepositoryImplTest` | PASS (round-trip, all entry types, malformed event, rollback, key/buffer cleanup) |
| `./gradlew :core:data:testDebugUnitTest --tests com.chimali.core.data.eventsourcing.VaultAggregateServiceImplIntegrationTest` | PASS (real event/snapshot stores; create/update/read/delete for PASSWORD, CREDIT_CARD, NOTE) |
| `./gradlew :feature:vault:testDebugUnitTest --tests com.chimali.feature.vault.internal.VaultRepositoryImplIntegrationTest` | PASS (real repository projection CRUD for all three entry types) |
| `./gradlew :app:connectedShrunkDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.di.VaultPasskeyWiringTest"` | PASS (moto g stylus 5G (2022), Android 13) |
| `./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.di.VaultPasskeyWiringTest"` | PASS (moto g stylus 5G (2022), Android 13; 2026-09-09; validates both module orders, qualified production services, all Vault types, and passkey authentication) |
| `./gradlew :feature:vault:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.feature.vault.ui.VaultListScreenTest#passwordDetailCopiesToSystemClipboardAndClearsAfterSixtySeconds"` | PASS (moto g stylus 5G (2022), Android 13; 2026-09-09; real detail control copied the synthetic password, displayed success feedback, and cleared the system clipboard after 60 seconds) |

The project does not expose the legacy `:feature:vault:testDebugUnitTest` variant under every Gradle configuration; the command above is the currently discovered and passing task. The documented quickstart was executed on a connected Android device on 2026-09-08 by the user; the supplied Logcat shows normal startup, master-seed initialization, migration completion, and normal application termination without a vault crash. Fresh-install, restart, clipboard-timeout, and 500-item performance results were not separately captured in the supplied run and remain outstanding acceptance evidence.

Save-failure behavior is covered by the ViewModel regression tests: failed encryption retains the error state, overlapping saves are suppressed, cancellation is propagated, and clipboard success/failure feedback follows the service result. The Vault event serializer uses a distinct `eventType` discriminator to preserve the entry `type` field, event-store key/decrypted-buffer cleanup is guarded by `finally`, and passkey/Vault aggregate registrations are qualified independently. The shrunk connected startup suite also passes after retaining SQLite JDBC JNI members and removing runtime PBKDF2 factory lookups.


## 5. T039 ownership and compatibility verification — 2026-09-08

This record covers the current Phase 12 changes; it does not reuse earlier connected-device results as evidence for the new lifecycle implementation.

Executed from the repository root:

```powershell
./gradlew --no-configuration-cache -I tools/vault-memory-coverage.init.gradle :feature:vault:vaultMemoryCoverage :feature:vault:detekt :feature:vault:ktlintCheck :feature:vault:compileDebugAndroidTestKotlin :app:compileDebugKotlin
```

Result: **PASS**, 47 host tests, zero failures/errors/skips; 152 Gradle tasks. Log: `.gradle/t039-validation-final.log`. The optional Gradle init script uses JaCoCo for a reproducible report without changing production dependencies. HTML: `feature/vault/build/reports/jacoco/vaultMemoryCoverage/html/index.html`; XML: `feature/vault/build/reports/jacoco/vaultMemoryCoverage/vaultMemoryCoverage.xml`.

| Critical source | Covered lines | Covered branches |
|---|---:|---:|
| `VaultCryptoServiceImpl.kt` | 78 / 84 | 14 / 17 |
| `VaultPayloadCodec.kt` | 362 / 368 | 183 / 216 |
| `VaultViewModel.kt` (includes existing unrelated methods) | 165 / 229 | 71 / 109 |

These measured counters do **not** satisfy Constitution XII.3's 100% critical-path coverage requirement. T062 remains open; passing tests alone are not completion evidence.

The host suites verify nonempty retained array references after key-provider failure, crypto failure, malformed JSON, injected allocation failure, cancellation before dispatcher entry and after decoding but before return delivery. Submission tests exercise duplicate rejection, jobs cleared before starting, independent custom fields, callback handoff failure, stable retry identity and abandoned late completion. Frozen `src/test/resources/vault-legacy-v1/` fixtures were generated with the old codec and actual AES before production replacement; all three types pass old reads, JSON byte comparison with independent parsing, and edit/reopen. Do not regenerate the fixtures to accommodate codec changes.

Resolved input dependency check:

```powershell
./gradlew :feature:vault:dependencyInsight --dependency androidx.compose.foundation:foundation --configuration debugRuntimeClasspath
```

Resolved Android foundation: **1.11.1**. `ComposeSecretRetentionTest` is a passing *characterization of an unmet requirement*: a previously obtained immutable String snapshot still contains synthetic text after its source array and `TextFieldState` are cleared. Source archive provenance and audited locations are recorded in `research.md`. This does not establish heap, keyboard, undo, saved-state, accessibility, autofill or rendering erasure. Existing entry/detail String adapters and the String title projection remain unresolved T055/T059 boundaries.

Device inventory for this pass (`C:/Users/octav/AppData/Local/Android/Sdk/platform-tools/adb.exe devices`) returned no devices. `VaultOwnershipNavigationTest` compiles with the actual Vault graph/screens/ViewModel and real AES; its all-type discard and failed-save/retry/reopen cases have **not** executed on a device. When one is available, run:

```powershell
./gradlew :feature:vault:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.feature.vault.ui.navigation.VaultOwnershipNavigationTest"
```

Before T060/T062/T039 completion, extend and run pending-save disposal, system-back and recreation scenarios; finish the critical-path coverage matrix; establish a compliant input/rendering implementation under T055/T059 and execute its synthetic-secret memory probes. Re-run real-storage/passkey isolation and clipboard timeout checks through T044/T046. No constitutional exception or UI erasure claim is implied by the completed mutable-buffer work.


### Continued cleanup verification — 2026-09-08

Two new ViewModel regressions failed before the fix (`.gradle/t039-resume-red.log`): cancellation left the editor permanently pending, and a non-cooperative list refresh could publish stale items after abandonment. Completion now restores IDLE only for the matching cancelled submission, and list refresh checks cancellation before publishing. Retry identity remains intact until explicit abandonment/reset.

Additional crypto tests cover cancellation/provider failure and rejected encryption with nonempty retained buffers for every payload type. The all-type navigation suite now also includes system-back/discard followed by fresh original details/custom fields. This device test compiles but remains unexecuted; device inventory is still empty.

Re-executed the section 5 combined coverage/static/app/navigation compilation command: **PASS**, **51 tests**, zero failures/errors/skips; log `.gradle/t039-resume-validation2.log`. Updated measured counters:

| Critical source | Covered lines | Covered branches |
|---|---:|---:|
| `VaultCryptoServiceImpl.kt` | 84 / 84 | 16 / 17 |
| `VaultPayloadCodec.kt` | 362 / 368 | 183 / 216 |
| `VaultViewModel.kt` | 174 / 238 | 75 / 113 |

The crypto service now has full measured line coverage; this does not establish the required full critical-path branch coverage or satisfy the unresolved UI/device gates. T039/T062 remain open.


### Detail ownership and pending graph disposal - 2026-09-08

The section 5 combined command passes **58 host tests**, zero failures/errors/skips, plus static checks, app compilation and navigation-test compilation. Log: `.gradle/t039-lifecycle-final.log`.

`VaultDetailOwnershipTest` runs for all three types, observing the actual decoded field arrays: dismissal before delivery erases the eventual payload; clearing delivered details and ViewModel disposal erase all observed buffers. `VaultCryptoOwnershipTest` additionally verifies cancellation while decryption is queued never acquires a key or publishes a payload.

`VaultOwnershipNavigationTest.disposingGraphWhileSavingSuppressesLateCompletion` now disposes the actual graph during a delayed, non-cooperative save, then releases completion and checks idle mutation state and cleared selection. This all-type device test compiles but is not executed: current `adb devices` inventory remains empty. Existing system-back coverage is also compiled only. Recreation testing remains outstanding.

Measured crypto-service coverage remains 84/84 lines and 16/17 branches; ViewModel coverage is now 176/238 lines and 81/113 branches. Codec coverage is unchanged. These results do not close T055/T059, the remaining coverage gaps, or device acceptance.


### Mutable editor ownership implementation - 2026-09-08

All three entry screens now retain main input fields in typed `PasswordDraft`, `CreditCardDraft`, and `SecureNoteDraft` owners. `MutableDraftField` owns an independent array, wipes retired storage on replacement, copies submission data, rejects mutation after closure, and redacts diagnostics. Discard clears draft/custom-field/baseline arrays before invoking navigation. Confirmed success explicitly clears them through a composition side effect; disposal remains an idempotent fallback. Comparison and required-field checks read characters without additional secret copies.

The existing text widgets still receive immutable values through the explicit `displayText()` boundary. This is application-ownership progress permitted by plan 6.1, not a claim that the T055/T059 framework gate passes. Titles in projections and clipboard/rendering APIs remain unresolved.

The combined section 5 command passes 60 host tests and static/app/navigation compilation checks; log `.gradle/t039-draft-final.log`. Draft owners are now included in the scoped coverage report. `MutableDraftFieldTest` retains old storage across replacement/closure and verifies erasure plus submission independence for all typed draft fields. Navigation/device success/discard behavior remains compiled-only evidence until a device is available.


### Restoration, resource bounds and ownership transfer - 2026-09-08

Restored editor routes now return to the list when their in-memory session is gone. Only a nonsecret session marker is saved; no draft, baseline, secret or edit payload enters saved state. This prevents a restored edit route with a missing edit identity from creating an unintended new record. The all-type `StateRestorationTester` case checks list return, absence of restored edited text, unchanged stored title and fresh original details. This is compiled instrumentation evidence, not a device pass.

Decryption now tracks only the staged owner: clear that reference when ownership transfers after dispatcher delivery; otherwise `finally` erases it. Existing cancellation/retained-reference tests pass. Added draft-input failure/slice cleanup tests, multi-custom-field compatibility/truncation tests, and a resource-bound test that verifies excessive expansion is rejected before scratch allocation without allocating an enormous input.

The section 5 combined command passes **63 host tests**, zero failures/errors/skips, static checks, app and navigation-test compilation; log `.gradle/t039-restore-final.log`.

| Critical source | Covered lines | Covered branches |
|---|---:|---:|
| `VaultCryptoServiceImpl.kt` | 83 / 83 | 15 / 15 |
| `VaultPayloadCodec.kt` | 365 / 368 | 190 / 216 |
| `MutableDraftField.kt` | 26 / 26 | 13 / 14 |

The crypto service now meets full measured statement/branch coverage; remaining sources and UI/device gates do not. No connected Android device or local AVD was available. A user decision on retaining the strict platform erasure gate versus preparing a separate governed scope proposal was requested; no requirement or Constitution change has been applied.


### Codec failure-path audit - 2026-09-08

Parser call sites now throw explicit safe exceptions, making exception-only execution visible to coverage instrumentation. Escape/Unicode decoding is separated into small functions. Validated final parsed fields transfer directly to the payload owner rather than selecting ownership through an unconstrained runtime result-type switch. Mutable draft replacement likewise uses a pending owner cleared on failure. Existing format and lifetime contracts are unchanged.

Added missing-required-field and truncated-token cases that check every captured partial allocation is erased. The section 5 combined command passes **64 host tests**, zero failures/errors/skips, all static checks and app/navigation compilation; log `.gradle/t039-audit-final.log`.

Codec coverage: **353/353 lines, 206/208 branches**. Mutable draft field: **25/25 lines, 13/14 branches**. Crypto-service full coverage is retained. Remaining codec gaps are the guarded nonzero-digit EOF subcondition (caller checks EOF first) and defensive charset error handling with replacement-mode UTF-8. They have not been excluded or asserted covered; full critical-path coverage acceptance remains open along with UI/device gates.


### Late editor callbacks - 2026-09-08

A new retained-input test failed before the fix: a callback arriving after draft closure threw instead of being ignored. Closed fields now ignore callbacks before even reading incoming text length, so they cannot allocate another owned copy. All entry screens also guard late custom-field/save/discard callbacks and disable input after success. Dirty text comparison lives in the typed draft owners; absent and empty custom-field lists compare equivalently when no edit occurred.

The section 5 combined command passes **65 host tests**, zero failures/errors/skips, all static checks and app/navigation compilation; log `.gradle/t039-late-input-final2.log`. The pre-fix failure is in `.gradle/t039-late-input-red.log`.

Current Android device inventory is still empty. The erasure-scope decision remains pending; no policy amendment is inferred from continued implementation authorization.

### Approved platform adapters - 2026-09-09

The approved Constitution 1.0.0 supersedes the historical pending-scope notes. The current adapter audit and remaining title-model boundary are in `research.md` section 5.

Validation command:

```powershell
.\gradlew :core:common:testAndroidHostTest --tests '*AndroidClipboardManagerServiceTest' :feature:vault:testDebugUnitTest :feature:vault:detekt :feature:vault:ktlintCheck :feature:vault:compileDebugAndroidTestKotlin :app:compileDebugKotlin
```

Result: **BUILD SUCCESSFUL**, 72 Vault host tests and 4 clipboard host tests, no failures or errors (`.gradle/t039-platform-validation.log`). The clipboard extra regression failed before correction (`.gradle/t039-clipboard-red.log`). New cases cover owned clipboard-copy cleanup, keyboard configuration and masked/revealed rendering. This command did not regenerate coverage. The subsequent `VaultInputPolicyInstrumentationTest` still needs compilation/execution.

Windows WHPX acceleration is available. The optional `tools/vault-managed-device.init.gradle` provisions a headless API 35 AOSP ATD Pixel 2 and uses existing accepted SDK licenses. Initial image installation succeeded; navigation runtime results are pending in `.gradle/t039-managed-device.log`:

```powershell
.\gradlew --no-configuration-cache -I tools/vault-managed-device.init.gradle :feature:vault:vaultApi35DebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.chimali.feature.vault.ui.navigation.VaultOwnershipNavigationTest' '-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect'
```

T039 remains open. App-owned title/event/projection Strings, remaining adapter controls, actual lifecycle evidence and critical-path coverage are not waived by the platform exception.

#### First actual Android execution

The managed API 35 device booted and executed all 15 actual-navigation cases. All failed, predominantly at one-second waits for detail selection (`.gradle/t039-managed-device.log`). These are runtime failures, not passes or unavailable checks. Bounded waits were increased to ten seconds for diagnosis. The retry stalled at `performTextReplacement` waiting for Compose/Espresso idleness; a thread dump showed repeated Material3 text-field label animation (`.gradle/t039-device-thread-dump.txt`). The synthetic test app was stopped to end that hung run (`.gradle/t039-device-retry.log`).

Both fixtures now include `MaterialTheme`, matching `MainActivity`'s outer composition, instead of exercising Material3 fields without the app's theme provider. A focused keyboard-boundary run is pending in `.gradle/t039-device-input.log`. The theme correction is not yet claimed to fix navigation or the stall. The new input test compiled before this correction (`.gradle/t039-input-device-compile.log`). No device case is marked passed until the corrected run reports it.

Focused result: **3/3 actual Android API 35 keyboard-boundary cases PASS**, zero failures/errors/skips (`.gradle/t039-device-input.log`, BUILD SUCCESSFUL in 1m 12s). Each actual entry screen is populated with synthetic title text and its real Android input connection is checked for restricted IME options/input flags and empty initial surrounding text. This verifies the configured boundary, not keyboard compliance or erasure of platform copies. Navigation is rerunning separately in `.gradle/t039-device-navigation-themed.log`; its acceptance is still pending.

Corrected navigation result: **15/15 actual Android API 35 navigation cases PASS**, zero failures/errors/skips (`.gradle/t039-device-navigation-themed.log`, BUILD SUCCESSFUL in 4m 35s). Password, card and note each pass discard, system-back discard, failed-save/retry/reopen, pending-save graph disposal with late completion, and state restoration without restoring the draft. These validate real routes/screens/ViewModel with real AES and isolated synthetic storage. They do not establish production database/title ownership or heap/reference cleanup for every editor buffer; those remaining T039 gates are separate.

#### Metadata storage buffers and stronger runtime ownership checks

The title-path audit found missing mutable-buffer cleanup in `SnapshotRepositoryImpl` and event append. Five new host regressions cover snapshot save/read, valid/malformed contents, encryption/decryption failure, cancellation, key-provider cancellation before serialization, and event append. Four failed against the previous implementation (`.gradle/t039-metadata-buffers-red.log`). Snapshot key/plaintext buffers now clear in outer `finally` blocks; event append clears each serialized byte buffer in `finally`. This does **not** remove immutable title models or event/snapshot JSON Strings; that compatibility-sensitive work remains open.

```powershell
.\gradlew :core:data:testDebugUnitTest :core:data:ktlintCheck :core:data:detekt
```

Result: **21 core-data host tests PASS**, zero failures/errors/skips; static checks pass (`.gradle/t039-metadata-buffers-verified.log`). The repeated `vault` DI qualifier is now a shared value to satisfy the module's duplicate-literal check, preserving the qualifier and registrations.

The navigation fixture now records actual codec/key allocations and checks erasure on detail-to-edit handoff and graph/ViewModel disposal. First stronger run: **14/15 PASS**; password system-back timed out waiting for returned details, while its final erasure check passed (`.gradle/t039-device-buffer-ownership.log`). An explicit idle wait after editing was added before Back. The integration rerun (Vault host tests/static checks, app compilation, stronger navigation suite) is pending in `.gradle/t039-ownership-integration-final.log`. No passing memory assertion is used to conceal the functional timeout.

Integration result: **BUILD SUCCESSFUL** in 7m 6s (`.gradle/t039-ownership-integration-final.log`). It includes the 72 Vault host tests, Vault static checks, app compilation and **15/15** API 35 navigation ownership cases with the retained-buffer assertions. The former system-back timeout does not reproduce after the explicit idle synchronization.

Frozen old-format metadata fixtures now cover created/updated/null events and nested snapshots with title Unicode, escapes and signed byte arrays. The pre-remediation serializers pass these fixtures (`.gradle/t039-metadata-legacy-fixtures.log`). They are a compatibility oracle for the remaining immutable title-model/serialization change; do not regenerate them to accommodate it.

`VaultItem`, aggregate commands/events/state and database projection now use owned mutable title values. Vault-entry title storage changed from SQLite `TEXT` to UTF-8 `BLOB` with verified migration 5; the event/snapshot title serializer keeps the frozen v1 JSON string schema without storing a title `String` in an application owner. Core domain, data, database and Vault tests pass after this change.

The actual API 35 managed-device navigation suite also passes all 15 parameterized ownership cases after the title migration (`.gradle/t039-vaultitem-title-device.log`). Its synthetic persistence adapter now takes and returns independent copies at the storage boundary, matching production database reads and ensuring detail cleanup cannot mutate stored or list owners.

#### Current scoped coverage

```powershell
.\gradlew --no-configuration-cache -I tools/vault-memory-coverage.init.gradle :feature:vault:vaultMemoryCoverage
```

Result: **BUILD SUCCESSFUL** (`.gradle/t039-memory-coverage-final3.log`). The latest scoped report keeps complete measured line/branch coverage for `VaultCryptoServiceImpl` (**83/83**, **15/15**) and full line coverage for `VaultPayloadCodec` (**353/353**, with one defensive branch still uncovered). `MutableDraftField` has one generated-property instruction and one defensive cleanup branch not measured. Constitution XII.3's full critical-path coverage acceptance remains open.

#### Final ownership and adapter verification - 2026-09-09

The approved app-owned cleanup scope is implemented and verified for all password, credit-card and secure-note editor lifecycles. The API 35 navigation suite passes all 15 ownership cases, and the focused API 35 input suite passes all three editor cases, including the root-level autofill exclusion control. The platform audit documents the remaining framework-owned text, rendering, undo/history and accessibility exposure without claiming it can be erased by the app.

The following final host/build command passes with zero test, formatting, static-analysis or compilation failures:

```powershell
.\gradlew --no-configuration-cache :feature:vault:testDebugUnitTest :feature:vault:ktlintCheck :feature:vault:detekt :app:compileDebugKotlin :feature:vault:compileDebugAndroidTestKotlin
```

The scoped coverage command also passes. `VaultCryptoServiceImpl` remains **83/83 lines** and **15/15 branches**; `MutableDraftField` is **26/26 lines** and **10/10 branches**. `VaultPayloadCodec` is **357/357 lines** and **207/208 branches**. The only uncredited branch is the direct malformed-number EOF throw in `Reader.requireNonzeroDigit()`. `missingRequiredFieldsAndTruncatedTokensEraseAllPartialData` asserts its exact `IllegalArgumentException` and verifies all captured partial arrays are erased. The approved Constitution 1.1.0 XII.3 exception accepts this single terminal branch; no other unmeasured path is accepted.
