# Credential-selection state owner cleanup

**Finding:** SEC065-FIDO-011  
**Scope:** `AuthenticationState.SelectingCredential` owns an RP text owner and a list of mutable `PasskeyCredential` values.

Before correction, `AuthenticationPromptViewModel` erased only the selecting state's RP text when transitioning away or clearing the state. It left every credential's RP/user text and mutable credential-ID bytes populated. The state now exposes idempotent `eraseSensitiveText()` that clears its RP owner and every credential owner; ViewModel state replacement calls it. A host regression constructs a synthetic chooser state and asserts all RP/user text and credential-ID buffers are zero after cleanup.

The chooser UI converts credential display name, account name and RP ID into immutable Compose text/semantics values. The current source has no production constructor for `SelectingCredential`, so actual chooser rendering is dormant/unverified. See `BND-UI-006` in `docs/security/platform-text-boundaries.md`; no device renderer or heap retention claim is made.

Verification on 2026-11-16: `:feature:fido2:testAndroidHostTest` passed 1,273 tests across 168 XML reports with zero failures, errors, or skips; `:feature:fido2:ktlintCheck` and `:feature:fido2:detekt` passed. `AuthenticationConsentStateOwnershipTest` passed all 5 cases, including `selectingCredentialStateErasesItsCredentialOwners`; report SHA-256: `F594712302761A155AC766B6A4057F915781A2F7D00A493F29BECEFB0BE64ACF`. Provider/Compose copies, UI lifecycle integration and heap roots remain open under T005/T006/T015/T027.
