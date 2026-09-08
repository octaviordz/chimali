package com.chimali.feature.vault.internal.crypto

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import java.util.UUID

/**
 * Service contract for encrypting and decrypting vault payloads with AES-256-GCM
 * and zeroing intermediate plaintext memory immediately upon completion.
 */
interface VaultCryptoService {
    suspend fun encryptPassword(
        id: UUID?,
        payload: PasswordPayload,
        identityId: UUID,
    ): Outcome<VaultItem, DomainError>

    suspend fun encryptCreditCard(
        id: UUID?,
        payload: CreditCardPayload,
        identityId: UUID,
    ): Outcome<VaultItem, DomainError>

    suspend fun encryptSecureNote(
        id: UUID?,
        payload: SecureNotePayload,
        identityId: UUID,
    ): Outcome<VaultItem, DomainError>

    suspend fun decryptPassword(item: VaultItem): Outcome<PasswordPayload, DomainError>

    suspend fun decryptCreditCard(item: VaultItem): Outcome<CreditCardPayload, DomainError>

    suspend fun decryptSecureNote(item: VaultItem): Outcome<SecureNotePayload, DomainError>
}
