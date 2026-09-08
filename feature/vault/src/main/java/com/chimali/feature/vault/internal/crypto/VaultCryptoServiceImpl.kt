package com.chimali.feature.vault.internal.crypto

import co.touchlab.kermit.Logger
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.security.api.EncryptionManager
import com.chimali.core.security.api.EventStoreKeyProvider
import com.chimali.feature.vault.api.VaultItem
import com.chimali.feature.vault.api.VaultType
import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.CustomField
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class CustomFieldDto(
    val name: String,
    val value: String,
    val isConcealed: Boolean,
)

@Serializable
internal data class PasswordPayloadDto(
    val title: String,
    val username: String,
    val password: String,
    val uri: String,
    val notes: String? = null,
    val customFields: List<CustomFieldDto>? = null,
)

@Serializable
internal data class CreditCardPayloadDto(
    val title: String,
    val cardholderName: String,
    val cardNumber: String,
    val expirationDate: String,
    val cvv: String,
    val notes: String? = null,
    val customFields: List<CustomFieldDto>? = null,
)

@Serializable
internal data class SecureNotePayloadDto(
    val title: String,
    val content: String,
    val customFields: List<CustomFieldDto>? = null,
)

@Suppress("TooGenericExceptionCaught")
class VaultCryptoServiceImpl(
    private val encryptionManager: EncryptionManager,
    private val eventStoreKeyProvider: EventStoreKeyProvider,
) : VaultCryptoService {
    companion object {
        private const val AGGREGATE_LABEL = "chimali_vault_payload_v1"
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }

    override suspend fun encryptPassword(
        id: UUID?,
        payload: PasswordPayload,
        identityId: UUID,
    ): Outcome<VaultItem, DomainError> =
        withContext(Dispatchers.Default) {
            try {
                val dto =
                    PasswordPayloadDto(
                        title = payload.title,
                        username = String(payload.username),
                        password = String(payload.password),
                        uri = payload.uri,
                        notes = payload.notes?.let { String(it) },
                        customFields =
                            payload.customFields?.map {
                                CustomFieldDto(it.name, String(it.value), it.isConcealed)
                            },
                    )
                val jsonString = json.encodeToString(dto)
                val plaintextBytes = jsonString.toByteArray(StandardCharsets.UTF_8)
                val key = eventStoreKeyProvider.getEventStoreKey(AGGREGATE_LABEL)
                val encryptedBytes =
                    try {
                        encryptionManager.encrypt(plaintextBytes, key)
                    } finally {
                        plaintextBytes.fill(0)
                        key.fill(0)
                    }

                val now = Instant.now().toString()
                val itemId = id ?: UUID.randomUUID()
                Outcome.Success(
                    VaultItem(
                        id = itemId,
                        type = VaultType.PASSWORD,
                        title = payload.title,
                        payload = encryptedBytes,
                        crdtState = ByteArray(0),
                        dateCreated = now,
                        dateModified = now,
                        lastBackedUpAt = null,
                        identityId = identityId,
                    ),
                )
            } catch (e: Exception) {
                Logger.e(e) { "VaultCryptoServiceImpl: Failed to encrypt PasswordPayload" }
                Outcome.Error(DomainError.CryptoError("Failed to encrypt password payload: ${e.message}", e))
            }
        }

    override suspend fun encryptCreditCard(
        id: UUID?,
        payload: CreditCardPayload,
        identityId: UUID,
    ): Outcome<VaultItem, DomainError> =
        withContext(Dispatchers.Default) {
            try {
                val dto =
                    CreditCardPayloadDto(
                        title = payload.title,
                        cardholderName = String(payload.cardholderName),
                        cardNumber = String(payload.cardNumber),
                        expirationDate = payload.expirationDate,
                        cvv = String(payload.cvv),
                        notes = payload.notes?.let { String(it) },
                        customFields =
                            payload.customFields?.map {
                                CustomFieldDto(it.name, String(it.value), it.isConcealed)
                            },
                    )
                val jsonString = json.encodeToString(dto)
                val plaintextBytes = jsonString.toByteArray(StandardCharsets.UTF_8)
                val key = eventStoreKeyProvider.getEventStoreKey(AGGREGATE_LABEL)
                val encryptedBytes =
                    try {
                        encryptionManager.encrypt(plaintextBytes, key)
                    } finally {
                        plaintextBytes.fill(0)
                        key.fill(0)
                    }

                val now = Instant.now().toString()
                val itemId = id ?: UUID.randomUUID()
                Outcome.Success(
                    VaultItem(
                        id = itemId,
                        type = VaultType.CREDIT_CARD,
                        title = payload.title,
                        payload = encryptedBytes,
                        crdtState = ByteArray(0),
                        dateCreated = now,
                        dateModified = now,
                        lastBackedUpAt = null,
                        identityId = identityId,
                    ),
                )
            } catch (e: Exception) {
                Logger.e(e) { "VaultCryptoServiceImpl: Failed to encrypt CreditCardPayload" }
                Outcome.Error(DomainError.CryptoError("Failed to encrypt credit card payload: ${e.message}", e))
            }
        }

    override suspend fun encryptSecureNote(
        id: UUID?,
        payload: SecureNotePayload,
        identityId: UUID,
    ): Outcome<VaultItem, DomainError> =
        withContext(Dispatchers.Default) {
            try {
                val dto =
                    SecureNotePayloadDto(
                        title = payload.title,
                        content = String(payload.content),
                        customFields =
                            payload.customFields?.map {
                                CustomFieldDto(it.name, String(it.value), it.isConcealed)
                            },
                    )
                val jsonString = json.encodeToString(dto)
                val plaintextBytes = jsonString.toByteArray(StandardCharsets.UTF_8)
                val key = eventStoreKeyProvider.getEventStoreKey(AGGREGATE_LABEL)
                val encryptedBytes =
                    try {
                        encryptionManager.encrypt(plaintextBytes, key)
                    } finally {
                        plaintextBytes.fill(0)
                        key.fill(0)
                    }

                val now = Instant.now().toString()
                val itemId = id ?: UUID.randomUUID()
                Outcome.Success(
                    VaultItem(
                        id = itemId,
                        type = VaultType.NOTE,
                        title = payload.title,
                        payload = encryptedBytes,
                        crdtState = ByteArray(0),
                        dateCreated = now,
                        dateModified = now,
                        lastBackedUpAt = null,
                        identityId = identityId,
                    ),
                )
            } catch (e: Exception) {
                Logger.e(e) { "VaultCryptoServiceImpl: Failed to encrypt SecureNotePayload" }
                Outcome.Error(DomainError.CryptoError("Failed to encrypt secure note payload: ${e.message}", e))
            }
        }

    override suspend fun decryptPassword(item: VaultItem): Outcome<PasswordPayload, DomainError> =
        withContext(Dispatchers.Default) {
            val key = eventStoreKeyProvider.getEventStoreKey(AGGREGATE_LABEL)
            var decryptedBytes: ByteArray? = null
            try {
                decryptedBytes = encryptionManager.decrypt(item.payload, key)
                val jsonStr = String(decryptedBytes, StandardCharsets.UTF_8)
                val dto = json.decodeFromString<PasswordPayloadDto>(jsonStr)

                Outcome.Success(
                    PasswordPayload(
                        title = dto.title,
                        username = dto.username.toCharArray(),
                        password = dto.password.toCharArray(),
                        uri = dto.uri,
                        notes = dto.notes?.toCharArray(),
                        customFields =
                            dto.customFields?.map {
                                CustomField(it.name, it.value.toCharArray(), it.isConcealed)
                            },
                    ),
                )
            } catch (e: Exception) {
                Logger.e(e) { "VaultCryptoServiceImpl: Failed to decrypt PasswordPayload id=${item.id}" }
                Outcome.Error(DomainError.CryptoError("Failed to decrypt password: ${e.message}", e))
            } finally {
                decryptedBytes?.fill(0)
                key.fill(0)
            }
        }

    override suspend fun decryptCreditCard(item: VaultItem): Outcome<CreditCardPayload, DomainError> =
        withContext(Dispatchers.Default) {
            val key = eventStoreKeyProvider.getEventStoreKey(AGGREGATE_LABEL)
            var decryptedBytes: ByteArray? = null
            try {
                decryptedBytes = encryptionManager.decrypt(item.payload, key)
                val jsonStr = String(decryptedBytes, StandardCharsets.UTF_8)
                val dto = json.decodeFromString<CreditCardPayloadDto>(jsonStr)

                Outcome.Success(
                    CreditCardPayload(
                        title = dto.title,
                        cardholderName = dto.cardholderName.toCharArray(),
                        cardNumber = dto.cardNumber.toCharArray(),
                        expirationDate = dto.expirationDate,
                        cvv = dto.cvv.toCharArray(),
                        notes = dto.notes?.toCharArray(),
                        customFields =
                            dto.customFields?.map {
                                CustomField(it.name, it.value.toCharArray(), it.isConcealed)
                            },
                    ),
                )
            } catch (e: Exception) {
                Logger.e(e) { "VaultCryptoServiceImpl: Failed to decrypt CreditCardPayload id=${item.id}" }
                Outcome.Error(DomainError.CryptoError("Failed to decrypt credit card: ${e.message}", e))
            } finally {
                decryptedBytes?.fill(0)
                key.fill(0)
            }
        }

    override suspend fun decryptSecureNote(item: VaultItem): Outcome<SecureNotePayload, DomainError> =
        withContext(Dispatchers.Default) {
            val key = eventStoreKeyProvider.getEventStoreKey(AGGREGATE_LABEL)
            var decryptedBytes: ByteArray? = null
            try {
                decryptedBytes = encryptionManager.decrypt(item.payload, key)
                val jsonStr = String(decryptedBytes, StandardCharsets.UTF_8)
                val dto = json.decodeFromString<SecureNotePayloadDto>(jsonStr)

                Outcome.Success(
                    SecureNotePayload(
                        title = dto.title,
                        content = dto.content.toCharArray(),
                        customFields =
                            dto.customFields?.map {
                                CustomField(it.name, it.value.toCharArray(), it.isConcealed)
                            },
                    ),
                )
            } catch (e: Exception) {
                Logger.e(e) { "VaultCryptoServiceImpl: Failed to decrypt SecureNotePayload id=${item.id}" }
                Outcome.Error(DomainError.CryptoError("Failed to decrypt secure note: ${e.message}", e))
            } finally {
                decryptedBytes?.fill(0)
                key.fill(0)
            }
        }
}
