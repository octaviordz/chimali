# Contract: Current Credential Lifecycle and External Behavior

**Related documents**: [local derivation](local-key-derivation.md), [data model](../data-model.md), [specification](../spec.md).

## Initialization and scope

A fresh installation initializes encrypted recovery storage and the current wallet root without previous credentials or database state. This feature defines no upgrade detector, invalidation state, old-key fallback, migration screen or re-registration workflow.

No new network endpoint or issuance flow is introduced. Existing CTAP2/Credential Manager creation and authentication remain the external application flows. Existing algorithm selection and wire identifiers remain governed by the application's current protocol implementation.

## Shared descriptor

`CredentialKeyDescriptor` contains:

- validated full `CredentialId`;
- supported `coseAlgorithm`;
- exact supported `derivationProfile` for that algorithm.

Keep it a small immutable public-metadata value; never include the PRK, seed, factor or private scalar. Validate once at each externally supplied boundary. A `when` over supported algorithms supplies the single current profile for new registration; it is not a plugin registry or legacy selector.

## Registration

1. Generate the existing random credential identifier and resolve the explicit current descriptor.
2. Derive its public key under the byte contract. Return public data, algorithm and profile; no private-key alias or Keystore-existence check.
3. Convert the returned public key directly to the domain/JCA representation without repeating derivation.
4. Build the registration public key and self-attestation using the same descriptor. A required signing failure is reported as the existing documented ceremony error; it must not conceal key mismatch by generating another key.
5. Persist algorithm, profile, raw credential identity, registered public key and ordinary metadata in two explicit SQL transaction phases on the existing FIDO2 database: first the event append, then projection replacement. The projection transaction encloses every related deletion and insertion for that registration; commit all of them together or roll all of them back. Report successful registration only after both phases commit. No secret working buffer is persisted.

If projection persistence fails or is cancelled after the event commits, roll back the entire projection transaction so prior projection records remain intact. Retain the append-only event, return a typed persistence error (or propagate cancellation after cleanup), and do not report completed registration, regenerate a key or overwrite a recorded public key. Current replay/snapshot reconstruction retains that event's descriptor and public key. Verify event-phase failure, projection failure after each deletion/before insertion, cancellation, rollback and successful commit ordering through the existing repository/DAO boundaries. This feature does not add a general transaction coordinator or an asynchronous key-repair path.

## Session ownership and termination

The provider holds at most one cache generation shared by currently authorized operation owners. Foreground visibility and an idle Bluetooth connection do not grant signing authority or keep secrets alive. Every owner is scoped to an existing approved operation; ending one owner does not end unrelated valid owners.

| Owner / event | Required behavior |
| --- | --- |
| Bootstrap, encrypted-store provisioning or explicit warm-up | A bounded operation owner ends when that operation completes/fails/is cancelled; it confers no credential-signing authorization. |
| Foreground wallet/reveal/import operation | Starts through the existing authorized action. Hide, disposal, backgrounding, completion or cancellation ends that foreground owner and erases its mnemonic/operation copies. |
| Active authenticator ceremony | Owns work from request admission through success, failure, timeout or cancellation. The HID handler's `withTimeout` uses the corresponding MakeCredential/GetAssertion `getSafeTimeout()` value: 30,000–600,000 ms, default 120,000 ms. The owner deadline must not outlive that CTAP2 request deadline. Existing user-presence/verification policy must succeed before signing; a transport connection alone is insufficient. |
| Foreground disappears during HID work | End foreground owners. An already authorized active HID ceremony can finish within its existing deadline while its own owner remains valid; cancel a foreground-dependent pending verification/reveal action. Do not authorize another ceremony from the old owner's authorization. |
| HID disconnect | Cancel and end owners belonging to that transport's active ceremony; unrelated authorized foreground work can remain. An idle reconnect does not reinitialize secrets. |
| Last owner ends | Erase provider PRK/parent caches and release handles under the same synchronization used for copying; operation copies erase in their own `finally`. |
| Wallet import/replacement | Fence the old cache generation, cancel all old-generation owners, erase caches and operation copies, reject new signing entry for that generation and discard stale results. Provision the replacement only through its existing authorized import operation. |
| `TRIM_MEMORY_RUNNING_CRITICAL`, `TRIM_MEMORY_BACKGROUND`, `TRIM_MEMORY_MODERATE` | Fence the generation, cancel all its operations, clear 100% of provider caches, release handles and invoke the constitution's GC hint. These callbacks override an otherwise valid active owner; a retained copy cannot authorize a new signing operation or emit a stale response. |
| Next authorized request after clearing | Start a new generation and reconstruct the same wallet from encrypted recovery storage. Do not mint a new wallet or initialize eagerly on background/reconnect events. |

Use bounded operation scopes and synchronized generation/owner state, not an unbounded session registry or a new login/lock screen. Check cancellation/generation validity when admitting a signing operation and again after the provider returns, before returning a signature/response or committing success metadata. A synchronous BC/JCA operation already admitted may finish internally after cancellation; discard its result and erase owned material rather than claiming it can be forcibly interrupted or physically undone. Tests must cover rejected new signing entry and discarded in-flight results, overlapping foreground/HID owners, pending verification, last-owner cleanup, disconnect, replacement, every listed memory level and lazy same-key reinitialization.

Do not introduce a changed attestation-policy feature here; preserve current documented protocol policy while ensuring every produced signature corresponds to its embedded public key.

## Authentication

Candidate listing is a pure metadata read. Summaries carry descriptor metadata, stored public-key encoding, counter policy information and existing selection fields. Selection performs no public-key derivation.

For the selected summary only, derive its current key once; normalize the stored public-key representation and compare against the public key already produced by that derivation. Reject malformed metadata or mismatch through `Outcome.Error`, erase owned secret material, and leave the record intact. Do not schedule key repair. Produce the assertion signature with the matching child key and existing authenticated message format. Persist successful counter/state changes through the existing ceremony/event flow.

Unknown algorithms, malformed profile pairing, missing required inputs, or unavailable wallet material are errors, never ES256 fallback. Cancellation is propagated after cleanup; it must not be translated into success or swallowed by a broad catch.

## Recovery and reconstruction

Given the encrypted recovery material or approved mnemonic plus current credential identity/algorithm/profile/public key metadata, reproduce the same current key after restart and supported reconstruction. Mutable display labels and counters do not affect key derivation. A mnemonic without credential metadata does not establish account discovery.

Current registration command/event/state carries algorithm and profile. Snapshot and full replay agree on them. Serialize fields explicitly; additive field defaults are invalid sentinels, not guessed usable identity. No conversion of old logs or snapshots is required.

## Mnemonic UI boundary

Preserve the existing reveal/import interaction and authorization. UI models and drafts own erasable character buffers, replacing and wiping them on hide, cancellation, replacement and owner disposal. A platform rendering/clipboard/QR adapter may create only its minimum required immutable representation for that existing user action. Record API, lifetime and residual retention limits; do not retain the representation in application state or crypto processing. Clipboard disclosure follows the existing user action and timeout policy.

## Error and diagnostic contract

Use typed existing domain errors for invalid input, unavailable root, unsupported algorithm/profile, provider failure and registered-key mismatch. Public/internal Tier 1 functions and verification reference stable BRD/TRD IDs. Logs may contain an operation/profile/error classification but no private key, mnemonic, PRK, factor, seed, derived secret or raw sensitive working plaintext.

The feature adds no issuer support metadata or claims of remote interoperability. OpenID4VCI investigation remains [a separate backlog item](../../../docs/backlog/openid4vci-hdk-issuance-investigation.md).
