# Partial secret cleanup and boundary assessment — 2026-11-13

**Status:** Open; bounded engineering assessment, not an independent security review or release certification. **Base revision:** `78812e4d0a2d4ff422e03cd71611d6eb4ab4c57d`, branch `lab/or/chimali`. The source correction described below is an uncommitted working-tree change on that base; the tests ran against that working tree. This revision is not asserted to be the release branch.

## Scope and findings

| Register | Inspected source and result | Disposition |
| --- | --- | --- |
| `CLS-VAULT-001`, `BND-UI-002` | `feature/vault/.../internal/VaultViewModel.kt` creates a `String` for the clipboard service and clears its mutable password operation copy in `finally` and completion handling. `core/common/.../clipboard/ClipboardManagerService.kt` accepts `String`; `AndroidClipboardManagerService.kt` sets `IS_SENSITIVE`, then schedules a coroutine delay and calls `clearClipboard`. | Host ownership and virtual-time tests passed as below. The delay exists only in the app process; process death loses it. The scheduled call ignores a failed clear result, so neither this code nor the host tests establish an unconditional 60-second deadline. The Android API accepts `CharSequence`; the service's `String` requirement is an application interface choice, not proof the platform requires that specific type. Review a durable and platform-compatible cleanup strategy and its failure reporting. A platform clipboard copy and other OS or IME copies are not erasable by zeroing the operation array. |
| `CLS-FIDO-001`, `CLS-FIDO-002`, `BND-ERR-001` | `feature/fido2/.../data/crypto/CredentialEncryptionService.kt` included an RP ID in a metadata-encryption failure message and a credential ID in three key-operation failure messages. `PrivacyLogScrubber.kt` uses patterns for key material and certain keywords, not a general identifier filter; `PrivacyScrubbingLogWriter.kt` applies it to the message. | Four messages were changed to fixed operation labels in the working tree. A repository search found the service class and its host tests but no production instantiation or call sites; reachability in deployed configurations was not established. Its `CredentialMetadata` data class and JSON serialization still use identifying `String` values and remain unassessed for lifetime and exposure. A passing functional suite is not proof that all logs are safe. |
| `CLS-FIDO-002` | `Ctap2MakeCredentialHandler.kt` declares request RP and user fields as `SensitiveCredentialText`; `SearchableMetadataMigrationState.kt` contains explicit erasure for decoded RP owners and index buffers. | These observations are not an audit of all constructors, error/cancellation paths, database projections, CTAP requests, UI state or logs. No absence-of-retention claim is made. |
| All other classifications and boundaries | Not inspected in this bounded assessment. | Open, including mnemonic and provider state, vault editor and rendering, remaining CTAP models, projections and logging. Historical review dates, scope, and fingerprints remain unchanged. |

Paths above abbreviate source-set/package components; the exact files are discoverable by their named class under the given module. The canonical registers retain their historical rows and link here for current findings.

## Verification

Synthetic host fixtures, 2026-11-13 local time:

- `:core:common:testAndroidHostTest --tests '*AndroidClipboardManagerServiceTest'`: 4 passed, 0 failed or skipped. Tests use a mocked clipboard and virtual scheduler; they check the flag, a scheduled clear, explicit clear, and reset on a subsequent copy.
- `:feature:vault:testDebugUnitTest --tests '*VaultClipboardOwnershipTest'`: 3 passed, 0 failed or skipped. Tests check the operation copy on success, failure, cancellation, and a never-started request; they do not inspect the JVM heap.
- `:feature:fido2:testAndroidHostTest --tests '*CredentialEncryptionServiceTest'`: 12 passed, 0 failed or skipped after the fixed-label change. These are functional tests, not a comprehensive logging assertion.

A physical ASUS Android API 35 device was visible to ADB. No physical clipboard test was run in this assessment; the existing `VaultListScreenTest.passwordDetailCopiesToSystemClipboardAndClearsAfterSixtySeconds` overwrites the device's system clipboard and tests a foreground, live-process scenario only. No runtime heap dump was captured. No failure, process-death, background-clear or other OEM/device scenario was verified. Test output is local build output, not a retained immutable release evidence bundle.

## Remaining work

Inventory the remaining production usages and register rows at a declared release revision; trace plaintext ownership, persistence and diagnostic paths, including identifying RP/user strings, database projections and CTAP models. Repair and verify findings, including the clipboard lifecycle gap, then perform synthetic runtime heap and physical timeout checks with recorded procedures and results. Document any remaining non-erasable JCA `BigInteger`, Bouncy Castle private-key, ICU native, IME, Compose and OS clipboard copies without asserting their erasure. Obtain the required independent security assessment before closure.

## Implementation addendum — 2026-11-13

The assessment above records the source and test state at its original inspection point. Subsequent work on the same exploratory branch changed the clipboard path: the shared API now borrows a mutable `CharArray`; Android sets the sensitive-content flag before publication, schedules a fixed 30-second best-effort attempt, checks only ownership metadata before automatic clearing, and records sanitized per-receipt outcomes. The vault caller reports copy confirmation separately from cleanup confirmation. Error messages discard platform-provided text and causes. A focused host run passed 11 Android clipboard tests, including unreadable metadata, failed manual clear and caller-disposal handoff cases; a vault ownership run passed 6 cases, including active detail-owner preservation. The full test matrix, test-only independent observer, device cohorts, runtime heap evidence, whole-source inventory and independent review remain open. This addendum does not supersede historical evidence or certify a release candidate; see `specs/065-security-secret-cleanup/evidence/index.md`.
