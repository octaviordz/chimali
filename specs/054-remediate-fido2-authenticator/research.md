# Research: Remediate FIDO2 Authenticator

## Decision 1: Remove the stale authenticator facade instead of implementing it

**Decision**: Delete `Fido2Authenticator` and `Fido2AuthenticatorImpl`; move its sole live consumer's capability
lookup to a focused authoritative path.

**Rationale**: `BluetoothHidTransportImpl` invokes the CTAP make-credential and get-assertion handlers directly.
Its only use of the facade is `getAuthenticatorInfo()`. Adding delegation from the facade to the existing use cases
would duplicate the ceremony entry points and leave the new facade unreferenced. Removing it satisfies Constitution
XII.1's ban on dead and nonfunctional code.

**Alternatives considered**:

- Delegate every facade method to existing use cases: rejected because the transport does not use them and the facade
  would remain duplicate/dead code.
- Keep only the facade's working methods: rejected because it preserves an oversized, misleading abstraction and
  reintroduces unimplemented state-management responsibilities.

## Decision 2: Preserve the established CTAP ceremony owners

**Decision**: Do not move registration or assertion logic. Continue using `Ctap2MakeCredentialHandler`,
`Ctap2GetAssertionHandler`, `RegisterCredentialUseCase`, and `GetAssertionUseCase`.

**Rationale**: These paths already enforce validation, verification, credential persistence, and cryptographic
operations. Keeping them avoids behavior drift and avoids additional key-derivation work on the HID path.

**Alternatives considered**:

- Route CTAP handlers through a new facade: rejected because it adds latency and a second orchestration layer without
  user value.

## Decision 3: Derive verification-required results from persisted credProtect policy

**Decision**: Use the stored `cred_protect_policy` value to return and count credentials that require verification.
Policy value `3` is the currently enforced user-verification-required policy.

**Rationale**: The persistence mapper and DAO already store and restore this attribute, and the assertion use case
already interprets policy `3` as requiring user verification. This makes repository reports consistent with ceremony
enforcement without a schema migration.

**Alternatives considered**:

- Add a new boolean column: rejected because the existing policy is authoritative and an additional representation
  risks divergence.
- Continue returning an empty flow/count: rejected because it conceals applicable credentials and contradicts the
  repository contract.
