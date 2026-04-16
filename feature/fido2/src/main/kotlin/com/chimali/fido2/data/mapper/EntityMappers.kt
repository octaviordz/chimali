package com.chimali.fido2.data.mapper

import com.chimali.fido2.data.database.PasskeyCredential as PasskeyCredentialEntity
import com.chimali.fido2.data.database.RelyingParty as RelyingPartyEntity
import com.chimali.fido2.data.database.UserConsentRecord as UserConsentRecordEntity
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.RelyingParty
import com.chimali.fido2.domain.model.UserConsentRecord
import com.chimali.fido2.domain.model.ConsentOperationType
import com.chimali.fido2.domain.model.VerificationMethod
import com.chimali.fido2.domain.usecase.ConsentVerificationResult
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

fun PasskeyCredentialEntity.toDomainModel(publicKey: java.security.PublicKey): PasskeyCredential {
    return PasskeyCredential(
        id = this.id,
        rpId = this.rpId,
        userId = this.userId,
        userName = this.userName,
        userDisplayName = this.userDisplayName,
        publicKey = publicKey,
        privateKeyAlias = this.privateKeyAlias,
        signCount = this.signCount,
        createdAt = Instant.ofEpochMilli(this.createdAt),
        lastUsedAt = this.lastUsedAt?.let { Instant.ofEpochMilli(it) } ?: Instant.ofEpochMilli(this.createdAt),
        aaguid = Base64.getDecoder().decode(this.aaguid),
        credentialId = Base64.getDecoder().decode(this.credentialId),
        coseAlgorithm = this.coseAlgorithm.toInt(),
        credProtectPolicy = this.credProtectPolicy.toInt(),
        label = this.label
    )
}

fun RelyingPartyEntity.toDomainModel(): RelyingParty {
    return RelyingParty(
        id = this.id,
        name = this.name,
        iconUrl = this.iconUrl,
        credentialCount = this.credentialCount.toInt(),
        createdAt = Instant.ofEpochMilli(this.createdAt),
        lastUsedAt = this.lastUsedAt?.let { Instant.ofEpochMilli(it) },
        isBlocked = this.isBlocked > 0L
    )
}

fun UserConsentRecordEntity.toDomainModel(): UserConsentRecord {
    return UserConsentRecord(
        id = this.id,
        rpId = this.rpId,
        operationType = ConsentOperationType.valueOf(this.operationType),
        credentialId = this.credentialId,
        timestamp = Instant.ofEpochMilli(this.timestamp),
        biometricUsed = this.biometricUsed > 0L,
        pinUsed = this.pinUsed > 0L,
        ipAddress = null,
        userAgent = null,
        deviceId = null
    )
}
