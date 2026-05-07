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
import com.chimali.fido2.data.database.PasskeyCredential as PasskeyCredentialEntity
import com.chimali.fido2.data.database.RelyingParty as RelyingPartyEntity
import com.chimali.fido2.data.database.UserConsentRecord as UserConsentRecordEntity
import com.chimali.fido2.domain.model.PasskeyCredential
import java.util.Base64
import kotlinx.datetime.Instant

fun PasskeyCredentialEntity.toDomainModel(
    decoder: PublicKeyDecoder,
): Outcome<PasskeyCredential, DomainError.CryptoError> =
    decoder.decodePublicKey(this.publicKey, this.coseAlgorithm.toInt()).map { decodedKey ->
        PasskeyCredential(
            id = CredentialId.fromEncoded(this.id),
            rpId = RpId(this.rpId),
            userId = UserId(this.userId),
            userName = this.userName,
            userDisplayName = this.userDisplayName,
            publicKey = decodedKey,
            privateKeyAlias = this.privateKeyAlias,
            signCount = this.signCount,
            createdAt = Instant.fromEpochMilliseconds(this.createdAt),
            lastUsedAt =
                this.lastUsedAt?.let { Instant.fromEpochMilliseconds(it) }
                    ?: Instant.fromEpochMilliseconds(this.createdAt),
            aaguid = Base64.getDecoder().decode(this.aaguid),
            credentialId = CredentialId.fromEncoded(this.credentialId).toByteArray(),
            coseAlgorithm = this.coseAlgorithm.toInt(),
            credProtectPolicy = this.credProtectPolicy.toInt(),
            label = this.label,
        )
    }

fun RelyingPartyEntity.toDomainModel(): RelyingParty =
    RelyingParty(
        id = RpId(this.id),
        name = this.name,
        iconUrl = this.iconUrl,
        credentialCount = this.credentialCount.toInt(),
        createdAt = Instant.fromEpochMilliseconds(this.createdAt),
        lastUsedAt = this.lastUsedAt?.let { Instant.fromEpochMilliseconds(it) },
        isBlocked = this.isBlocked > 0L,
    )

fun UserConsentRecordEntity.toDomainModel(): UserConsentRecord =
    UserConsentRecord(
        id = this.id,
        rpId = RpId(this.rpId),
        operationType = ConsentOperationType.valueOf(this.operationType),
        credentialId = this.credentialId?.let { CredentialId.fromEncoded(it) },
        timestamp = Instant.fromEpochMilliseconds(this.timestamp),
        biometricUsed = this.biometricUsed > 0L,
        pinUsed = this.pinUsed > 0L,
        ipAddress = null,
        userAgent = null,
        deviceId = null,
    )
