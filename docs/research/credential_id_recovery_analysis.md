# Analysis: Credential ID and Recovery Consistency
**Date**: 2026-03-16


This report analyzes how the `Credential ID` factors into the BIP39/HDK recovery flow and identifies gaps in the current implementation when a device is wiped and restored from a mnemonic.

## Current Mechanism

### 1. Generation
In `RegisterCredentialUseCase.kt`, the `credentialId` is generated using a timestamp and a random part:
```kotlin
private fun generateCredentialId(): String {
    return "cred_${System.currentTimeMillis()}_${SecureRandom().nextInt(10000)}"
}
```

### 2. Key Derivation
In `Fido2CryptoService.kt`, the FIDO2 private key is derived using the `credentialId` as the unique entropy source for the HDK path:
```kotlin
// Simplified path derivation
val hash = sha256(credentialId)
val index = hash.toPositiveInt()
val path = [FIDO2_APP_INDEX, index]
val key = hdkManager.deriveHdk(seed, devicePubKey, path)
```
> [!IMPORTANT]
> The public/private key pair is a deterministic function of `(MasterSeed, credentialId)`.

---

## Recovery Analysis

### Directed Assertion (Login with Username)
When a website already knows who the user is, it sends the `credentialId` to the authenticator in the `allowCredentials` list.
*   **Does it work after recovery?** Yes. Even if the local Chimali database is gone, the RP provides the `credentialId`. `Fido2CryptoService` takes that ID, hashes it, and re-derives the *exact same* key pair.
*   **Current implementation check**: `GetAssertionUseCase.findCandidateCredentials` currently filters by what is in the local `credentialRepository`. If the DB is wiped, it returns an empty list, even if the RP sent a valid ID.

### Discoverable Credentials (Resident Keys)
When a user clicks "Login with a passkey" without entering a username, the client must "discover" which credentials it has for that `rpId`.
*   **Does it work after recovery?** No. Since `credentialId` is random and local storage is wiped, the device no longer knows which IDs it used for that site. It cannot "guess" the `credentialId` to derive the key and user entity.

---

## Findings & Recommendations

| ID | Finding | Recommendation |
|---|---|---|
| **R1** | **Repository Dependency** | `GetAssertionUseCase` fails if the credential isn't in the local DB, even if the user has the mnemonic. | Modify `GetAssertionUseCase` to allow "Stateless Retrieval": if an `allowList` is provided, derive the keys even if the metadata isn't in the DB. |
| **R2** | **Randomized Identifiers** | Random `credentialId` prevents recovery of Discoverable Credentials (resident keys). | **Switch to Deterministic IDs**: Base the `credentialId` on `Hash(rpId, user.id, counter)`. This allows "scanning" for credentials during a discovery flow. |
| **R3** | **Sign Count Sync** | FIDO2 expects a monotonically increasing sign count. | After recovery, the sign count resets to 0. Some RPs might reject this. Consider using a timestamp-based sign count or warn users. |

## Conclusion
The current implementation allows for **Key Re-Derivability** (the math works), but lacks **Metadata Persistence**. Without a local database, Chimali currently "forgets" it owns the keys. To bridge this, Chimali should move towards deterministic `credentialId` generation as outlined in `FR-AUTH-030` requirements.
