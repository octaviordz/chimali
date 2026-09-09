package com.chimali.feature.vault.internal.crypto

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import com.chimali.feature.vault.internal.payload.SensitivePayload
import java.security.GeneralSecurityException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VaultCryptoServiceImpl internal constructor(
    private val encryptionManager: EncryptionManager,
    private val eventStoreKeyProvider: EventStoreKeyProvider,
    private val dispatcher: CoroutineDispatcher,
    private val codec: VaultPayloadCodec,
) : VaultCryptoService {
    constructor(
        encryptionManager: EncryptionManager,
        eventStoreKeyProvider: EventStoreKeyProvider,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : this(encryptionManager, eventStoreKeyProvider, dispatcher, VaultPayloadCodec())

    override suspend fun encryptPassword(
        id: UUID?,
        payload: PasswordPayload,
        identityId: UUID,
    ): Outcome<VaultItem, DomainError> =
        encrypt(id, payload, payload.title, identityId, VaultType.PASSWORD) { codec.encode(payload) }

    override suspend fun encryptCreditCard(
        id: UUID?,
        payload: CreditCardPayload,
        identityId: UUID,
    ): Outcome<VaultItem, DomainError> =
        encrypt(id, payload, payload.title, identityId, VaultType.CREDIT_CARD) { codec.encode(payload) }

    override suspend fun encryptSecureNote(
        id: UUID?,
        payload: SecureNotePayload,
        identityId: UUID,
    ): Outcome<VaultItem, DomainError> =
        encrypt(id, payload, payload.title, identityId, VaultType.NOTE) { codec.encode(payload) }

    /** FR-VAULT-026: consume input even when the dispatcher never enters its block. */
    private suspend fun encrypt(
        id: UUID?,
        payload: SensitivePayload,
        title: CharArray,
        identityId: UUID,
        type: VaultType,
        encode: () -> ByteArray,
    ): Outcome<VaultItem, DomainError> =
        try {
            withContext(dispatcher) {
                var key: ByteArray? = null
                var plaintext: ByteArray? = null
                try {
                    key = eventStoreKeyProvider.getEventStoreKey(AGGREGATE_LABEL)
                    plaintext = encode()
                    val encrypted = encryptionManager.encrypt(plaintext, key)
                    val now = Instant.now().toString()
                    Outcome.Success(
                        VaultItem(
                            id ?: UUID.randomUUID(),
                            type,
                            title.copyOf(),
                            encrypted,
                            ByteArray(0),
                            now,
                            now,
                            null,
                            identityId,
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: GeneralSecurityException) {
                    protectError(type)
                } catch (_: IllegalArgumentException) {
                    protectError(type)
                } catch (_: IllegalStateException) {
                    keyError()
                } finally {
                    plaintext?.fill(0)
                    key?.fill(0)
                }
            }
        } finally {
            payload.clearMemory()
        }

    override suspend fun decryptPassword(item: VaultItem): Outcome<PasswordPayload, DomainError> =
        decrypt(item) { codec.decodePassword(it) }

    override suspend fun decryptCreditCard(item: VaultItem): Outcome<CreditCardPayload, DomainError> =
        decrypt(item) { codec.decodeCreditCard(it) }

    override suspend fun decryptSecureNote(item: VaultItem): Outcome<SecureNotePayload, DomainError> =
        decrypt(item) { codec.decodeSecureNote(it) }

    /** FR-VAULT-026: ownership transfers only after successful dispatcher return delivery. */
    private suspend fun <T : SensitivePayload> decrypt(
        item: VaultItem,
        decode: (ByteArray) -> T,
    ): Outcome<T, DomainError> {
        var staged: T? = null
        try {
            val result =
                withContext(dispatcher) {
                    var key: ByteArray? = null
                    var plaintext: ByteArray? = null
                    try {
                        key = eventStoreKeyProvider.getEventStoreKey(AGGREGATE_LABEL)
                        plaintext = encryptionManager.decrypt(item.payload, key)
                        val payload = decode(plaintext)
                        staged = payload
                        Outcome.Success(payload)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: GeneralSecurityException) {
                        openError(item.type)
                    } catch (_: IllegalArgumentException) {
                        openError(item.type)
                    } catch (_: IllegalStateException) {
                        keyError()
                    } finally {
                        plaintext?.fill(0)
                        key?.fill(0)
                    }
                }
            staged = null // Ownership has crossed the dispatcher boundary and transfers to the caller.
            return result
        } finally {
            staged?.clearMemory()
        }
    }

    // Never retain parser/provider exceptions: their messages may contain plaintext.
    private fun protectError(type: VaultType) =
        Outcome.Error(DomainError.CryptoError("Unable to protect ${label(type)} payload"))

    private fun openError(type: VaultType) =
        Outcome.Error(DomainError.CryptoError("Unable to open ${label(type)} payload"))

    private fun keyError() = Outcome.Error(DomainError.CryptoError("Vault encryption is not ready"))

    private fun label(type: VaultType): String =
        when (type) {
            VaultType.PASSWORD -> "password"
            VaultType.CREDIT_CARD -> "card"
            VaultType.NOTE -> "note"
        }

    companion object {
        /** FR-VAULT-035: retained v1 key derivation domain; changing it breaks stored entries. */
        private const val AGGREGATE_LABEL = "chimali_vault_payload_v1"
    }
}
