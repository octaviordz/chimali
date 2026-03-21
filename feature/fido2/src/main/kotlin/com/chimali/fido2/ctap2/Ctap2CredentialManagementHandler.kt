package com.chimali.fido2.ctap2

import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.repository.CredentialRepository
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import kotlinx.coroutines.flow.toList
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

// CTAP2 status codes
private const val CTAP2_OK: Byte = 0x00
private const val CTAP1_ERR_MISSING_PARAMETER: Byte = 0x0E
private const val CTAP2_ERR_UNSUPPORTED_OPTION: Byte = 0x11
private const val CTAP2_ERR_PROCESSING: Byte = 0x17
private const val CTAP2_ERR_NO_CREDENTIALS: Byte = 0x22
private const val CTAP2_ERR_NOT_ALLOWED: Byte = 0x30

/**
 * T117, T118 — CTAP2 authenticatorCredentialManagement (0x0A) handler.
 *
 * Supports all standard subCommands:
 *   1 — getCredsMetadata
 *   2 — enumerateRPsBegin
 *   3 — enumerateRPsGetNextRP
 *   4 — enumerateCredentialsBegin
 *   5 — enumerateCredentialsGetNextCredential
 *   6 — deleteCredential
 */
@Singleton
class Ctap2CredentialManagementHandler @Inject constructor(
    private val cborCodec: CborCodec,
    private val getAllCredentialsUseCase: GetAllCredentialsUseCase,
    private val deleteCredentialUseCase: DeleteCredentialUseCase,
    private val credentialRepository: CredentialRepository
) {
    // ── Stateful enumeration sessions ────────────────────────────────────────

    /** RP enumeration: list of (rpId, rpName, credentialCount, rpIdHash). */
    private var rpEnumerationSession: MutableList<RpEntry> = mutableListOf()

    /** Credential enumeration: list of credential descriptors for a single RP. */
    private var credentialEnumerationSession: MutableList<CredentialEntry> = mutableListOf()

    // ── Public entry point ───────────────────────────────────────────────────

    suspend fun handle(requestBytes: ByteArray): ByteArray {
        return try {
            val params = cborCodec.decodeFromFido2Format(requestBytes)
            val subCommand = (params["1"] as? Number)?.toInt() 
                ?: return byteArrayOf(CTAP1_ERR_MISSING_PARAMETER)

            Timber.d("Credential Management subCommand: %d", subCommand)

            when (subCommand) {
                1 -> handleGetCredsMetadata()
                2 -> handleEnumerateRPsBegin()
                3 -> handleEnumerateRPsGetNextRP()
                4 -> handleEnumerateCredentialsBegin(params)
                5 -> handleEnumerateCredentialsGetNextCredential()
                6 -> handleDeleteCredential(params)
                else -> byteArrayOf(CTAP2_ERR_UNSUPPORTED_OPTION)
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception handling credential management")
            byteArrayOf(CTAP2_ERR_PROCESSING)
        }
    }

    // ── SubCommand 1: getCredsMetadata ───────────────────────────────────────

    private suspend fun handleGetCredsMetadata(): ByteArray {
        val credentials = getAllCredentialsUseCase().toList()
        val numCredentials = credentials.size
        
        val response = mapOf<String, Any>(
            "1" to numCredentials,
            "2" to numCredentials // existingResidentCredentialsCount
        )
        val responseBytes = cborCodec.encodeToFido2Format(response)
        return byteArrayOf(CTAP2_OK) + responseBytes
    }

    // ── SubCommand 2: enumerateRPsBegin ──────────────────────────────────────

    private suspend fun handleEnumerateRPsBegin(): ByteArray {
        val allCredentials = getAllCredentialsUseCase().toList()
        if (allCredentials.isEmpty()) {
            return byteArrayOf(CTAP2_ERR_NO_CREDENTIALS)
        }

        // Group credentials by rpId and build enumeration entries
        rpEnumerationSession = allCredentials
            .groupBy { it.rpId }
            .map { (rpId, creds) ->
                val rpName = credentialRepository.getRelyingParty(rpId)?.name ?: rpId
                RpEntry(
                    rpId = rpId,
                    rpName = rpName,
                    credentialCount = creds.size,
                    rpIdHash = sha256(rpId.toByteArray())
                )
            }
            .toMutableList()

        val totalRPs = rpEnumerationSession.size
        val first = rpEnumerationSession.removeAt(0)
        return buildRpResponse(first, totalRPs)
    }

    // ── SubCommand 3: enumerateRPsGetNextRP ──────────────────────────────────

    private fun handleEnumerateRPsGetNextRP(): ByteArray {
        if (rpEnumerationSession.isEmpty()) {
            return byteArrayOf(CTAP2_ERR_NOT_ALLOWED)
        }
        val next = rpEnumerationSession.removeAt(0)
        return buildRpResponse(next, totalRPs = null)
    }

    // ── SubCommand 4: enumerateCredentialsBegin ──────────────────────────────

    private suspend fun handleEnumerateCredentialsBegin(params: Map<String, Any>): ByteArray {
        val subCommandParams = params["2"] as? Map<*, *>
            ?: return byteArrayOf(CTAP1_ERR_MISSING_PARAMETER)

        // The rpIdHash is passed as bytes in key "1" of subCommandParams
        val rpIdHash = subCommandParams["1"] as? ByteArray

        // Determine which RP to list credentials for.
        // Try rpIdHash first; if not available, fall back to rpId string.
        val rpId = if (rpIdHash != null) {
            findRpIdByHash(rpIdHash)
        } else {
            (subCommandParams["rpId"] as? String)
        } ?: return byteArrayOf(CTAP1_ERR_MISSING_PARAMETER)

        val credentials = credentialRepository.getCredentialsByRpId(rpId).toList()
        if (credentials.isEmpty()) {
            return byteArrayOf(CTAP2_ERR_NO_CREDENTIALS)
        }

        credentialEnumerationSession = credentials.map { cred ->
            CredentialEntry(
                credentialId = cred.credentialId,
                userId = cred.userId,
                userName = cred.userName,
                userDisplayName = cred.userDisplayName,
                publicKeyBytes = cred.publicKey.encoded
            )
        }.toMutableList()

        val totalCredentials = credentialEnumerationSession.size
        val first = credentialEnumerationSession.removeAt(0)
        return buildCredentialResponse(first, totalCredentials)
    }

    // ── SubCommand 5: enumerateCredentialsGetNextCredential ──────────────────

    private fun handleEnumerateCredentialsGetNextCredential(): ByteArray {
        if (credentialEnumerationSession.isEmpty()) {
            return byteArrayOf(CTAP2_ERR_NOT_ALLOWED)
        }
        val next = credentialEnumerationSession.removeAt(0)
        return buildCredentialResponse(next, totalCredentials = null)
    }

    // ── SubCommand 6: deleteCredential ───────────────────────────────────────

    private suspend fun handleDeleteCredential(params: Map<String, Any>): ByteArray {
        val subCommandParams = params["2"] as? Map<*, *> ?: return byteArrayOf(CTAP1_ERR_MISSING_PARAMETER)
        
        // In real CTAP2, CredentialId comes in subCommandParams map
        val credDescriptor = subCommandParams["1"] as? Map<*, *> ?: return byteArrayOf(CTAP1_ERR_MISSING_PARAMETER)
        val credIdBytes = credDescriptor["id"] as? ByteArray ?: return byteArrayOf(CTAP1_ERR_MISSING_PARAMETER)
        
        val credIdBase64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(credIdBytes)
        
        val result = deleteCredentialUseCase(credIdBase64)
        return if (result.isSuccess) {
            byteArrayOf(CTAP2_OK)
        } else {
            byteArrayOf(CTAP2_ERR_NO_CREDENTIALS)
        }
    }

    // ── Response builders ────────────────────────────────────────────────────

    /**
     * Builds an RP enumeration CBOR response.
     * CTAP2 keys: 3 = rp entity, 4 = rpIdHash, 5 = totalRPs (only on Begin)
     */
    private fun buildRpResponse(entry: RpEntry, totalRPs: Int?): ByteArray {
        val response = mutableMapOf(
            "3" to mapOf("id" to entry.rpId, "name" to entry.rpName),
            "4" to entry.rpIdHash.toList()
        )
        if (totalRPs != null) {
            response["5"] = totalRPs
        }
        val responseBytes = cborCodec.encodeToFido2Format(response)
        return byteArrayOf(CTAP2_OK) + responseBytes
    }

    /**
     * Builds a credential enumeration CBOR response.
     * CTAP2 keys: 6 = user entity, 7 = credentialId descriptor,
     *             8 = publicKey, 9 = totalCredentials (only on Begin)
     */
    private fun buildCredentialResponse(entry: CredentialEntry, totalCredentials: Int?): ByteArray {
        val response = mutableMapOf(
            "6" to mapOf(
                "id" to entry.userId,
                "name" to entry.userName,
                "displayName" to entry.userDisplayName
            ),
            "7" to mapOf(
                "id" to entry.credentialId.toList(),
                "type" to "public-key"
            ),
            "8" to entry.publicKeyBytes.toList()
        )
        if (totalCredentials != null) {
            response["9"] = totalCredentials
        }
        val responseBytes = cborCodec.encodeToFido2Format(response)
        return byteArrayOf(CTAP2_OK) + responseBytes
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    /**
     * Reverse-lookup an rpId from a stored hash.  Uses the credentials
     * currently in the RP enumeration session (which were already loaded).
     * Falls back to a full scan through getAllCredentials if needed.
     */
    private suspend fun findRpIdByHash(hash: ByteArray): String? {
        // First check the RP session (might still have entries from a recent Begin)
        rpEnumerationSession.forEach { entry ->
            if (entry.rpIdHash.contentEquals(hash)) return entry.rpId
        }
        // Otherwise, scan all credentials for a matching rpId
        val allCredentials = getAllCredentialsUseCase().toList()
        return allCredentials
            .map { it.rpId }
            .distinct()
            .firstOrNull { sha256(it.toByteArray()).contentEquals(hash) }
    }

    // ── Internal data classes ────────────────────────────────────────────────

    private data class RpEntry(
        val rpId: String,
        val rpName: String,
        val credentialCount: Int,
        val rpIdHash: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RpEntry) return false
            return rpId == other.rpId
        }

        override fun hashCode(): Int = rpId.hashCode()
    }

    private data class CredentialEntry(
        val credentialId: ByteArray,
        val userId: String,
        val userName: String,
        val userDisplayName: String,
        val publicKeyBytes: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CredentialEntry) return false
            return credentialId.contentEquals(other.credentialId)
        }

        override fun hashCode(): Int = credentialId.contentHashCode()
    }
}
