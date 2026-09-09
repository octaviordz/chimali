> **Approved scope update (2026-09-09):** Constitutions 1.0.0 and 1.1.0, plus the approved T039 scope and coverage proposals, govern this document. App-owned sensitive storage must remain mutable and explicitly cleaned; app references must be released. Only necessary, audited platform text adapters may use immutable copies, with configured controls and documented residual risk. Cryptographic/serialization cleanup, compatibility, critical coverage and actual runtime verification remain mandatory.

# Contract: Vault Crypto & Payload Serialization

**Component**: `feature:vault` & `core:security`  
**Date**: 2026-09-07  
**Status**: Approved

## 1. Vault Crypto Service Interface

```kotlin
package com.chimali.feature.vault.internal.crypto

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import java.util.UUID

interface VaultCryptoService {
    /**
     * Encrypts and serializes a [PasswordPayload] into a persistent [VaultItem].
     */
    suspend fun encryptPassword(
        id: UUID?,
        payload: PasswordPayload,
        identityId: UUID,
    ): Outcome<VaultItem, DomainError>

    /**
     * Encrypts and serializes a [CreditCardPayload] into a persistent [VaultItem].
     */
    suspend fun encryptCreditCard(
        id: UUID?,
        payload: CreditCardPayload,
        identityId: UUID,
    ): Outcome<VaultItem, DomainError>

    /**
     * Encrypts and serializes a [SecureNotePayload] into a persistent [VaultItem].
     */
    suspend fun encryptSecureNote(
        id: UUID?,
        payload: SecureNotePayload,
        identityId: UUID,
    ): Outcome<VaultItem, DomainError>

    /**
     * Decrypts a [VaultItem] payload into a [PasswordPayload].
     */
    suspend fun decryptPassword(
        item: VaultItem,
    ): Outcome<PasswordPayload, DomainError>

    /**
     * Decrypts a [VaultItem] payload into a [CreditCardPayload].
     */
    suspend fun decryptCreditCard(
        item: VaultItem,
    ): Outcome<CreditCardPayload, DomainError>

    /**
     * Decrypts a [VaultItem] payload into a [SecureNotePayload].
     */
    suspend fun decryptSecureNote(
        item: VaultItem,
    ): Outcome<SecureNotePayload, DomainError>
}
```

## 2. Memory Sanitization Contract

All implementors of `VaultCryptoService` MUST:
1. Store decrypted strings only in `CharArray` or `ByteArray`.
2. Wrap any intermediate `ByteArray` or `CharArray` in a `try/finally` block.
3. Call `.fill('\u0000')` or `.fill(0)` on all sensitive byte and char arrays in the `finally` block.
4. Wipe intermediate key material derived from `EventStoreKeyProvider`.

## 3. Ownership and compatibility amendment (2026-09-08)

- Every `encrypt*` invocation consumes an independent payload copy and zeros all its text arrays, including metadata/custom-field names, whether it succeeds, fails, or is cancelled before dispatcher entry. The caller must never pass its active draft or detail arrays.
- Key acquisition occurs before serialization. Keys and plaintext are protected by `finally`; codec scratch is separately owned/cleared. Failure does not retain the raw technical exception or a parser excerpt containing input.
- Successful `decrypt*` delivery transfers one independently owned mutable payload to the caller. Failure/cancellation, including cancellation while returning from the dispatcher, erases partial/undelivered payloads. The receiver must erase on replacement/departure/disposal.
- JSON keys/types/default/null/unknown-field behavior, UTF-8, AES-GCM envelope, and `chimali_vault_payload_v1` are preserved. See `data-model.md` and the frozen v1 fixtures. There is no schema migration or passkey format change.
- Zeroing uses `CharArray.fill('\u0000')` / `ByteArray.fill(0)`, not the printable digit `'0'`.
- `VaultItem.title` is an application-owned `CharArray`. The database projection uses UTF-8 BLOB storage, and the event/snapshot serializer creates a transient framework-boundary JSON String only for the preserved v1 schema.
