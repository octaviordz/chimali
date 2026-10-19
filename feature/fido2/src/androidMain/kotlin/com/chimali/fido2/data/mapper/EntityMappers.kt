package com.chimali.fido2.data.mapper

import com.chimali.core.common.annotation.Req
import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.core.domain.model.ConsentOperationType
import com.chimali.core.domain.model.CredentialKeyDescriptor
import com.chimali.core.domain.model.CredentialProtectionPolicy
import com.chimali.core.domain.model.RelyingParty
import com.chimali.core.domain.model.SensitiveText
import com.chimali.core.domain.model.UserConsentRecord
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.fido2.data.crypto.PublicKeyDecoder
import com.chimali.fido2.data.database.Passkey_credential as PasskeyCredentialEntity
import com.chimali.fido2.data.database.Relying_party as RelyingPartyEntity
import com.chimali.fido2.data.database.User_consent_record as UserConsentRecordEntity
import com.chimali.fido2.data.service.CredentialMetadataProtectionService
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.domain.model.SensitiveCredentialText
import com.chimali.fido2.domain.model.decodeSensitiveText
import com.chimali.fido2.domain.util.matchesCredentialId
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant

/** Reconstructs current records, containing malformed storage as a redacted typed error. */
@Req("NFR-SEC-041", "TR-AUTH-031")
fun PasskeyCredentialEntity.toDomainModel(
    decoder: PublicKeyDecoder,
    metadataProtectionService: CredentialMetadataProtectionService,
): Outcome<PasskeyCredential, DomainError.CryptoError> =
    toDomainModelWithTextDecoder(decoder, metadataProtectionService, SensitiveCredentialText::fromUtf8Bytes)

/** Runs row hydration with the production text decoder, exposed internally for retained-owner verification. */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal fun PasskeyCredentialEntity.toDomainModelWithTextDecoder(
    decoder: PublicKeyDecoder,
    metadataProtectionService: CredentialMetadataProtectionService,
    textDecoder: (ByteArray) -> SensitiveCredentialText,
): Outcome<PasskeyCredential, DomainError.CryptoError> =
    try {
        mapCredentialRecord(decoder, metadataProtectionService, textDecoder)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: java.nio.charset.CharacterCodingException) {
        malformedCredentialRecord()
    } catch (_: IllegalArgumentException) {
        malformedCredentialRecord()
    } catch (_: IllegalStateException) {
        malformedCredentialRecord()
    } finally {
        eraseSensitiveMetadata()
    }

/** Erases the mutable text byte buffers owned by this SQLDelight projection row. */
@Req("NFR-SEC-041", "TR-AUTH-031")
fun PasskeyCredentialEntity.eraseSensitiveMetadata() {
    rp_id.fill(0)
    rp_name.fill(0)
    user_display_name.fill(0)
    user_id.fill(0)
    user_name.fill(0)
}

private fun malformedCredentialRecord(): Outcome.Error<DomainError.CryptoError> =
    Outcome.Error(DomainError.CryptoError("Malformed credential record"))

private fun requireAuditTimestampOrder(
    createdAt: Long,
    updatedAt: Long,
    priorActivityAt: Long?,
    recordLabel: String,
) {
    if (updatedAt < createdAt || (priorActivityAt != null && updatedAt < priorActivityAt)) {
        throw IllegalArgumentException(recordLabel)
    }
}

private fun PasskeyCredentialEntity.mapCredentialRecord(
    decoder: PublicKeyDecoder,
    metadataProtectionService: CredentialMetadataProtectionService,
    textDecoder: (ByteArray) -> SensitiveCredentialText,
): Outcome<PasskeyCredential, DomainError.CryptoError> {
    requireAuditTimestampOrder(created_at, updated_at, last_used_at, "Malformed credential record")
    require(cose_algorithm == cose_algorithm.toInt().toLong()) { "Invalid persisted algorithm value" }
    val id = CredentialId.fromEncoded(this.id)
    val credentialId = CredentialId.fromEncoded(credential_id)
    require(id.matchesCredentialId(credentialId)) { "Inconsistent stored credential identity" }
    val descriptor = CredentialKeyDescriptor(credentialId, cose_algorithm.toInt(), derivation_profile)
    var rpId: SensitiveCredentialText? = null
    var userId: SensitiveCredentialText? = null
    var userName: SensitiveCredentialText? = null
    var userDisplayName: SensitiveCredentialText? = null
    try {
        rpId = textDecoder(rp_id)
        userId = textDecoder(user_id)
        userName = textDecoder(user_name)
        userDisplayName = textDecoder(user_display_name)
        val metadata =
            this.encrypted_metadata?.let { encrypted ->
                try {
                    metadataProtectionService.decryptCredentialMetadata(encrypted, requireNotNull(rpId))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: SecurityException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                } catch (_: IllegalStateException) {
                    null
                } catch (_: java.nio.charset.CharacterCodingException) {
                    null
                }
            }
        metadata?.let {
            try {
                require(it.matches(requireNotNull(rpId), requireNotNull(userId))) {
                    "Inconsistent protected credential metadata"
                }
            } finally {
                it.erase()
            }
        }

        val decodedKey =
            when (val result = decoder.decodePublicKey(public_key, descriptor.coseAlgorithm)) {
                is Outcome.Success -> result.data
                is Outcome.Error -> return malformedCredentialRecord()
            }
        val credential =
            PasskeyCredential(
                id = id,
                rpId = requireNotNull(rpId),
                userId = requireNotNull(userId),
                userName = requireNotNull(userName),
                userDisplayName = requireNotNull(userDisplayName),
                publicKey = decodedKey,
                signCount = this.sign_count,
                createdAt = Instant.fromEpochMilliseconds(this.created_at),
                lastUsedAt =
                    this.last_used_at?.let { Instant.fromEpochMilliseconds(it) }
                        ?: Instant.fromEpochMilliseconds(this.created_at),
                aaguid = Base64.getDecoder().decode(this.aaguid),
                credentialId = credentialId.toByteArray(),
                coseAlgorithm = descriptor.coseAlgorithm,
                derivationProfile = descriptor.derivationProfile,
                credProtectPolicy = CredentialProtectionPolicy.fromPersisted(this.cred_protect_policy),
                label = this.label,
            )
        rpId = null
        userId = null
        userName = null
        userDisplayName = null
        return Outcome.Success(credential)
    } finally {
        rpId?.erase()
        userId?.erase()
        userName?.erase()
        userDisplayName?.erase()
    }
}

/** Consumes the RP projection row's mutable bytes and transfers a validated text owner to its caller. */
@Req("NFR-SEC-041", "TR-AUTH-031")
fun RelyingPartyEntity.toDomainModel(): RelyingParty = toDomainModelWithTextDecoder(::decodeSensitiveText)

/** Consumes RP row bytes, exposing only the decoder seam needed to verify partial-construction cleanup. */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal fun RelyingPartyEntity.toDomainModelWithTextDecoder(textDecoder: (ByteArray) -> SensitiveText): RelyingParty {
    var rpId: SensitiveText? = null
    var rpName: SensitiveText? = null
    try {
        requireAuditTimestampOrder(created_at, updated_at, last_used_at, "Malformed relying-party record")
        rpId = textDecoder(id)
        rpName = textDecoder(name)
        return RelyingParty
            .fromOwnedText(
                id = requireNotNull(rpId),
                name = requireNotNull(rpName),
                iconUrl = icon_url,
                credentialCount = credential_count.toInt(),
                createdAt = Instant.fromEpochMilliseconds(created_at),
                lastUsedAt = last_used_at?.let { Instant.fromEpochMilliseconds(it) },
                isBlocked = is_blocked > 0L,
            ).also {
                rpId = null
                rpName = null
            }
    } catch (_: java.nio.charset.CharacterCodingException) {
        throw IllegalArgumentException("Malformed relying-party record")
    } finally {
        rpId?.erase()
        rpName?.erase()
        eraseSensitiveMetadata()
    }
}

/** Erases RP row buffers after hydration, filtering or abandoned batch ownership. */
@Req("NFR-SEC-041", "TR-AUTH-031")
fun RelyingPartyEntity.eraseSensitiveMetadata() {
    id.fill(0)
    name.fill(0)
}

/** Consumes consent row bytes and copies the RP into an independently erasable model. */
@Req("NFR-SEC-041", "TR-AUTH-031")
fun UserConsentRecordEntity.toDomainModel(): UserConsentRecord {
    var rpId: SensitiveText? = null
    try {
        requireAuditTimestampOrder(created_at, updated_at, timestamp, "Malformed consent record")
        val owner = decodeSensitiveText(rp_id)
        rpId = owner
        return UserConsentRecord.fromText(
            id = id,
            rpId = owner,
            operationType = ConsentOperationType.valueOf(operation_type),
            credentialId = credential_id?.let { CredentialId.fromEncoded(it) },
            timestamp = Instant.fromEpochMilliseconds(timestamp),
            isBiometricUsed = biometric_used > 0L,
            isPinUsed = pin_used > 0L,
        )
    } catch (_: java.nio.charset.CharacterCodingException) {
        throw IllegalArgumentException("Malformed consent record")
    } finally {
        rpId?.erase()
        eraseSensitiveMetadata()
    }
}

/** Erases a consent row's mutable RP association after its last use. */
@Req("NFR-SEC-041", "TR-AUTH-031")
fun UserConsentRecordEntity.eraseSensitiveMetadata() {
    rp_id.fill(0)
}
