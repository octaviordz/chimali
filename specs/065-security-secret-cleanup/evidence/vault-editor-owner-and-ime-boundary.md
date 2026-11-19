# Vault editor ownership and IME boundary

Date: 2026-11-15  
Scope: password, credit-card and secure-note Compose entry screens.

## Application-owned buffers

`MutableDraftField` owns a mutable character array for each field. Replacing a value copies the new `CharSequence` into a new array, then zeros the retired array; if copying fails, the new partial array is zeroed and the current retry value remains intact. Typed `PasswordDraft`, `CreditCardDraft`, and `SecureNoteDraft` aggregate these field owners and erase them on clear. Their entry screens clear drafts on save success, cancel, initial-payload replacement, or Compose disposal. Custom fields use separate deep-copied owners and have corresponding disposal cleanup.

Save constructs an independent mutable payload from each field's `copyChars()`. `submitOwned` transfers that payload only if the callback returns successfully; a rejected/throwing callback clears the submission while preserving the active draft for retry. Existing host tests cover the success/rejection transfer, replacement, disposal, stale-slice invalidation and independent custom-field behavior.

## Platform text boundary

Material `OutlinedTextField` takes and returns immutable `String` values, so `displayText()` and the Compose editing pipeline create managed text copies that cannot be explicitly erased. `VaultInputPolicy` requests no personalized learning, no extracted/fullscreen UI, no suggestions or initial surrounding text, and excludes descendants from Android autofill; it restores the previous autofill value at disposal. The host policy tests verify the flags and input-type preservation, not actual keyboard/provider compliance or device memory behavior.

The existing `MutableDraftFieldTest` (4 cases), `DraftOwnershipTest` (4 cases), and `VaultInputPolicyTest` (2 cases) passed within the full Vault host suite (91 tests across 20 reports); KtLint passed. Compose renderer lifetime, OS/third-party IME behavior and heap-root retention remain unverified under T006/T015/T027.
