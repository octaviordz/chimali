# Analysis: Removing AES-256-SIV

Date: 2026-05-20

## Executive recommendation

Removing the custom AES-256-SIV implementation is a reasonable trade-off only if Chimali changes the searchable-metadata design from "deterministic decryptable ciphertext" to "keyed blind indexes plus AES-GCM-encrypted values." AES-256-GCM is good enough for payload encryption, DataStore encryption, and random-IV key wrapping, but it is not a safe drop-in replacement for deterministic searchable metadata.

The stronger conclusion is that removing AES-SIV will not, by itself, remove Bouncy Castle from the app. The current source uses Bouncy Castle for HDK P-256 point arithmetic, FIDO2 ES256 key reconstruction/signing, Ed25519, ML-DSA-65, ASN.1 public-key handling, provider registration, and warm-up paths. AES-SIV is only one Bouncy Castle consumer.

Recommended direction:

1. Replace the constitutional AES-SIV requirement with a narrower requirement: use AES-256-GCM for encrypted values and use HMAC-SHA-256 or HMAC-SHA-512 blind indexes for exact-match searchable metadata.
2. Do not use AES-GCM with fixed, reused, or plaintext-derived IVs to make it deterministic.
3. Treat Bouncy Castle removal as a separate migration, because it requires replacing HDK/FIDO2/PQC primitives, not only AES-SIV.

## Constitution impact

The current constitution requires a multi-mode symmetric encryption strategy:

- AES-256-GCM for general payload encryption.
- AES-256-SIV for searchable encrypted metadata and key wrapping.
- SQLCipher for SQLite file-level encryption, with individual credential blobs still encrypted before insertion.

That means outright removal of AES-SIV is currently constitution-breaking unless the constitution is amended. A defensible amendment would preserve the security goal rather than the exact algorithm:

> Searchable metadata MUST NOT be stored as plaintext. Exact-match lookup columns MUST use deterministic keyed blind indexes with explicit domain separation. The underlying metadata value MUST be encrypted with AES-256-GCM or protected by SQLCipher according to the data classification. AES-SIV MAY be used only when decryptable deterministic ciphertext is explicitly required and has dedicated test vectors.

This amendment is more pragmatic than requiring AES-SIV everywhere deterministic lookup is mentioned, because most lookup paths only need equality testing, not decryption of the index token.

## Current source-code findings

### AES-SIV exists, but it is not wired into storage paths

The source contains:

- `core/security/src/commonMain/kotlin/com/chimali/core/security/api/SivEncryptionManager.kt`
- `core/security/src/androidMain/kotlin/com/chimali/core/security/impl/AesSivEncryptionManager.kt`
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/service/EncryptedMetadataIndexService.kt`

`AesSivEncryptionManager` is a hand-written RFC 5297-style construction using Bouncy Castle `AESEngine`, `CMac`, and `SICBlockCipher`. It accepts a 64-byte key split into two 32-byte AES keys, so the implementation is effectively the RFC 5297 `AEAD_AES_SIV_CMAC_512` profile even though the code and constitution call it AES-256-SIV.

The important integration finding is that `EncryptedMetadataIndexService` appears unused by the FIDO2 DAO and repository paths. Grep found only the service definition and its own methods, not callers provisioning the key or writing SIV tags to the database.

The live SQLDelight schema still stores and indexes readable metadata columns:

- `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/Fido2Database.sq` stores `rp_id`, `user_id`, `user_name`, `user_display_name`, and `label`.
- `feature/fido2/src/commonMain/sqldelight/com/chimali/fido2/data/database/PasskeyCredential.sq` queries `WHERE rp_id = ?`, `WHERE rp_id = ? AND user_id = ?`, and `LIKE` over user-visible names.
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/dao/PasskeyCredentialDao.kt` writes `credential.rpId.value`, `credential.userId.value`, `credential.userName`, and `credential.userDisplayName` directly.

So the present practical choice is not "SIV versus GCM in production metadata indexing." It is closer to "unused SIV implementation versus plaintext metadata columns inside a SQLCipher-protected database."

### AES-GCM is already the live encryption path for payload-style storage

The code already uses AES-GCM in the main value-encryption paths:

- `core/security/src/androidMain/kotlin/com/chimali/core/security/impl/AesEncryptionManager.kt` uses `AES/GCM/NoPadding` with a generated 12-byte IV.
- `core/common/src/androidMain/kotlin/com/chimali/core/common/datastore/EncryptionWrapper.kt` uses Android Keystore AES-GCM for Proto DataStore encryption.
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/CredentialEncryptionService.kt` uses `AES/GCM/NoPadding` for credential metadata/value encryption.

This aligns with NIST SP 800-38D, which specifies GCM as authenticated encryption with associated data, and with Android Keystore support for AES-128/AES-256 with GCM and 96-bit GCM nonces.

### Removing SIV does not remove Bouncy Castle

Bouncy Castle remains required elsewhere:

- `core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/P256Group.kt` uses Bouncy Castle curve parameters and `ECPoint`.
- `core/security/src/androidMain/kotlin/com/chimali/core/security/hdkeys/HdkEcdhP256.kt`, `MultiplicativeBlinding.kt`, and `DhKem.kt` use Bouncy Castle `ECPoint` types.
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/Fido2CryptoService.kt` uses Bouncy Castle provider-backed key factories and raw Ed25519 primitives.
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/PostQuantumCrypto.kt` uses Bouncy Castle for ML-DSA-65 key generation, signing, and verification.
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/PublicKeyDecoder.kt` uses Bouncy Castle for ML-DSA-65 and Ed25519 `KeyFactory` instances.
- `feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto/CborCodec.kt` uses Bouncy Castle ASN.1 `SubjectPublicKeyInfo`.
- `app/src/main/kotlin/com/chimali/ChimaliApplication.kt` registers `BouncyCastleProvider` globally.

The comment in `core/security/build.gradle.kts` saying Bouncy Castle is retained "ONLY for AES-SIV" is stale relative to the code. Removing `AesSivEncryptionManager` would reduce one custom crypto implementation, but it would not remove the provider dependency from the application.

## Cryptographic evaluation

### AES-GCM is strong when the IV rule is obeyed

AES-GCM is an appropriate authenticated encryption mode for local encrypted payloads when:

- Each encryption under the same key uses a unique IV.
- The implementation uses a 96-bit IV for efficiency and platform compatibility.
- The authentication tag is 128 bits unless there is a reviewed reason to shorten it.
- Keys are separated by purpose.

NIST SP 800-38D is very direct about this: the IV is essentially a nonce, and uniqueness is crucial. It also warns that repeating an IV with the same key can enable forgery attacks. Android Keystore's GCM support also standardizes around 96-bit nonces.

The existing `EncryptionWrapper` path is good directionally because it lets the platform cipher generate the IV and stores `iv || ciphertext`. The code should still keep validating input lengths, zero sensitive plaintext buffers, and avoid manual nonce construction.

### AES-GCM is not a deterministic index primitive

AES-GCM is not good enough for deterministic searchable metadata by itself.

Random-IV GCM intentionally produces different ciphertexts for the same plaintext, so it cannot support `WHERE encrypted_rp_id = ?` lookup. Making GCM deterministic by using a fixed IV, a counter that can reset, or an IV derived from the plaintext is the wrong trade-off. It moves GCM outside its intended security model and risks catastrophic nonce-reuse failure.

If exact-match lookup is the requirement, a keyed blind index is simpler and safer:

```text
rp_id_index = HMAC-SHA-256(indexKey, "chimali.rp_id.v1" || canonicalRpId)
user_id_index = HMAC-SHA-256(indexKey, "chimali.user_id.v1" || canonicalUserId)
label_index = HMAC-SHA-256(indexKey, "chimali.label.v1" || canonicalLabel)
```

Store those fixed-size index bytes in indexed columns. Store the real metadata either:

- inside an AES-GCM-encrypted metadata blob, or
- in SQLCipher-protected tables if the project explicitly accepts file-level database encryption for that field class.

This gives deterministic equality search without AES-SIV and without unsafe GCM nonce reuse. It leaks equality and frequency, but AES-SIV deterministic ciphertext leaks the same equality/frequency pattern.

### AES-SIV remains useful, but the current use case may not need it

AES-SIV is useful when the system needs deterministic authenticated encryption where the deterministic token must also be decryptable. RFC 5297 explicitly covers deterministic authenticated encryption with SIV. It is also robust against nonce misuse compared with ordinary nonce-based AEADs.

For Chimali's current source, the index service does not appear integrated, and most lookup requirements need equality testing, not decryption of the index token. HMAC blind indexes satisfy that narrower requirement with standard JCA primitives and less implementation risk.

If the project keeps AES-SIV, it should add dedicated RFC 5297 known-answer tests and integration tests proving that database lookup uses SIV tags instead of plaintext metadata. Keeping an unused, hand-written cryptographic mode creates maintenance risk without buying production security.

### Key wrapping does not require SIV in this codebase

The constitution currently names AES-SIV for key wrapping where nonce-misuse resistance is paramount. The source I reviewed does not show `SivEncryptionManager` being used for actual key wrapping. The Proto DataStore migration path uses Android Keystore-backed AES-GCM via `EncryptionWrapper`.

For local storage of high-entropy keys or seed material, AES-GCM is sufficient if the key-encryption key is protected by Android Keystore, IVs are generated by the cipher, and wrapped outputs store the IV with the ciphertext. Deterministic wrapping is not necessary for the current DataStore design and can reveal duplicate wrapped material.

If future architecture requires a dedicated key-wrap primitive, prefer a standard platform-supported wrapping construction or Keystore-backed AES-GCM with strict key separation before adding an external AES-SIV dependency.

## Option comparison

| Option | Security fit | Dependency impact | Functional impact | Recommendation |
| --- | --- | --- | --- | --- |
| Keep AES-SIV as mandated | Good for decryptable deterministic metadata | Keeps Bouncy Castle and custom low-level crypto | Requires wiring it into schema/DAO to be meaningful | Acceptable only if deterministic decryptable ciphertext is truly required |
| Replace SIV with AES-GCM only | Good for payloads | Removes one BC use, not all BC | Breaks deterministic lookup unless unsafe nonce reuse is introduced | Do not use for searchable metadata |
| Use AES-GCM plus HMAC blind indexes | Good for payloads and exact-match search | Removes need for AES-SIV-specific code | Requires schema/index migration and constitution amendment | Recommended |
| Use AES-GCM-SIV | Misuse-resistant AEAD, standardized in RFC 8452 | Still requires a non-platform library on Android | Not needed for exact-match indexes; not Android Keystore-native | Not the right simplification target |

## Proposed design if AES-SIV is removed

### Storage model

For passkey metadata:

```text
passkey_credential
  id TEXT PRIMARY KEY
  rp_id_index BLOB NOT NULL
  user_id_index BLOB NOT NULL
  label_index BLOB NULL
  encrypted_metadata BLOB NOT NULL
  public_key TEXT NOT NULL
  credential_id TEXT NOT NULL
  ...
```

Where:

- `encrypted_metadata` is AES-256-GCM with associated data containing stable non-secret identifiers such as schema version and credential id.
- `rp_id_index`, `user_id_index`, and `label_index` are HMAC outputs using a dedicated index key.
- The index key is derived separately from the master seed using HKDF or HMAC with clear domain separation. Do not reuse the AES-GCM key as an HMAC index key.

### Query model

For exact lookup:

```text
lookupRpIdIndex = HMAC-SHA-256(indexKey, "chimali.rp_id.v1" || canonicalRpId)
SELECT ... FROM passkey_credential WHERE rp_id_index = ?
```

For substring search over user names or display names, neither AES-SIV nor blind indexes solve the problem cleanly. Choose one:

- decrypt/filter client-side after bounding the candidate set,
- keep a non-sensitive display field in SQLCipher only, documented by threat model,
- or implement a separate searchable-encryption design later, which is substantially more complex.

### Key management

Use separate keys for:

- AES-GCM payload encryption.
- HMAC blind indexes.
- SQLCipher database key derivation.
- FIDO2/HDK signing keys.

Each derived key should include an explicit domain string and version, for example:

```text
chimali.security.payload-gcm.v1
chimali.security.metadata-index-hmac.v1
chimali.security.sqlcipher.v1
```

## Migration implications

If the project chooses AES-GCM plus blind indexes:

1. Amend the constitution first, because AES-SIV is currently a MUST.
2. Add `HmacMetadataIndexService` or equivalent in `core:security` or `feature:fido2`, depending on ownership.
3. Add new indexed BLOB columns for `rp_id_index`, `user_id_index`, and any exact-match metadata fields.
4. Backfill indexes from the current plaintext SQLCipher-protected fields or from decrypted metadata blobs.
5. Stop writing plaintext searchable metadata where the threat model says it is sensitive.
6. Remove `SivEncryptionManager`, `AesSivEncryptionManager`, and `EncryptedMetadataIndexService` only after callers and migrations are complete.
7. Re-run `tools/local-ci.ps1`.

If the real goal is to remove Bouncy Castle completely, add separate tasks:

1. Port HDK P-256 point arithmetic away from Bouncy Castle.
2. Replace ES256 raw scalar signing and public-key reconstruction.
3. Replace Ed25519 support for minSdk 28 devices.
4. Replace or drop ML-DSA-65 support, because Android platform support is not currently the source-code path.
5. Remove global provider registration and warm-up once no provider-backed algorithms remain.

## Final answer

AES-256-GCM is good enough for Chimali's general encrypted payload and Proto DataStore use cases. It is not good enough as a deterministic searchable-metadata primitive.

The best trade-off is not "SIV to GCM"; it is "SIV deterministic ciphertext to HMAC blind indexes plus GCM-encrypted values." That preserves exact-match lookup, avoids unsafe GCM nonce reuse, removes the AES-SIV-specific external-library pressure, and better matches the codebase's current actual usage.

However, this does not achieve "no Bouncy Castle." Bouncy Castle remains part of the current FIDO2, HDK, Ed25519, and ML-DSA implementation. Removing it is a larger cryptography-provider migration.

## References

- NIST SP 800-38D, "Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM) and GMAC": https://csrc.nist.gov/pubs/sp/800/38/d/final
- NIST SP 800-38D PDF, IV uniqueness and 96-bit IV guidance: https://nvlpubs.nist.gov/nistpubs/legacy/sp/nistspecialpublication800-38d.pdf
- RFC 5297, "Synthetic Initialization Vector (SIV) Authenticated Encryption Using the Advanced Encryption Standard (AES)": https://www.rfc-editor.org/rfc/rfc5297
- RFC 8452, "AES-GCM-SIV: Nonce Misuse-Resistant Authenticated Encryption": https://www.rfc-editor.org/rfc/rfc8452.html
- Android Keystore cryptographic feature support: https://source.android.com/docs/security/features/keystore/features
- Android `EncryptedSharedPreferences` API reference: https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences
