package com.chimali.fido2.data.mapper

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.common.result.map
import com.chimali.core.domain.model.ConsentOperationType
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import com.chimali.fido2.data.crypto.PublicKeyDecoder
import com.chimali.fido2.data.database.Passkey_credential as PasskeyCredentialEntity
import com.chimali.fido2.data.database.Relying_party as RelyingPartyEntity
import com.chimali.fido2.data.database.User_consent_record as UserConsentRecordEntity
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.PasskeyCredential
import java.util.Base64
import kotlinx.datetime.Instant

fun PasskeyCredentialEntity.toDomainModel(
    decoder: PublicKeyDecoder,
    metadataProtectionService: CredentialMetadataProtectionService,
): Outcome<PasskeyCredential, DomainError.CryptoError> {
    val decryptedRpId =
        if (this.encrypted_metadata != null) {
            try {
                val json = metadataProtectionService.decryptMetadata(this.encrypted_metadata, this.rp_id)
                json.substringAfter("\"rpId\":\"").substringBefore("\"")
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                this.rp_id
            }
        } else {
            this.rp_id
        }

    val decryptedUserId =
        if (this.encrypted_metadata != null) {
            try {
                val json = metadataProtectionService.decryptMetadata(this.encrypted_metadata, this.rp_id)
                json.substringAfter("\"userId\":\"").substringBefore("\"")
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                this.user_id
            }
        } else {
            this.user_id
        }

    return decoder.decodePublicKey(this.public_key, this.cose_algorithm.toInt()).map { decodedKey ->
        PasskeyCredential(
            id = CredentialId.fromEncoded(this.id),
            rpId = RpId(decryptedRpId),
            userId = UserId(decryptedUserId),
            userName = this.user_name,
            userDisplayName = this.user_display_name,
            publicKey = decodedKey,
            privateKeyAlias = this.private_key_alias,
            signCount = this.sign_count,
            createdAt = Instant.fromEpochMilliseconds(this.created_at),
            lastUsedAt =
                this.last_used_at?.let { Instant.fromEpochMilliseconds(it) }
                    ?: Instant.fromEpochMilliseconds(this.created_at),
            aaguid = Base64.getDecoder().decode(this.aaguid),
            credentialId = CredentialId.fromEncoded(this.credential_id).toByteArray(),
            coseAlgorithm = this.cose_algorithm.toInt(),
            credProtectPolicy = this.cred_protect_policy.toInt(),
            label = this.label,
        )
    }
}

fun RelyingPartyEntity.toDomainModel(): RelyingParty =
    RelyingParty(
        id = RpId(this.id),
        name = this.name,
        iconUrl = this.icon_url,
        credentialCount = this.credential_count.toInt(),
        createdAt = Instant.fromEpochMilliseconds(this.created_at),
        lastUsedAt = this.last_used_at?.let { Instant.fromEpochMilliseconds(it) },
        isBlocked = this.is_blocked > 0L,
    )

fun UserConsentRecordEntity.toDomainModel(): UserConsentRecord =
    UserConsentRecord(
        id = this.id,
        rpId = RpId(this.rp_id),
        operationType = ConsentOperationType.valueOf(this.operation_type),
        credentialId = this.credential_id?.let { CredentialId.fromEncoded(it) },
        timestamp = Instant.fromEpochMilliseconds(this.timestamp),
        isBiometricUsed = this.biometric_used > 0L,
        isPinUsed = this.pin_used > 0L,
        ipAddress = null,
        userAgent = null,
        deviceId = null,
    )
