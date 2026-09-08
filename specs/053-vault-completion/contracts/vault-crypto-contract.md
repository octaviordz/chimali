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
3. Call `.fill('0')` or `.fill(0)` on all sensitive byte and char arrays in the `finally` block.
4. Wipe intermediate key material derived from `EventStoreKeyProvider`.
