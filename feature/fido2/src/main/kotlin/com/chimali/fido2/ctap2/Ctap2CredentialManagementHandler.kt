package com.chimali.fido2.ctap2

import android.util.Log
import com.chimali.fido2.data.crypto.CborCodec
import com.chimali.fido2.domain.usecase.DeleteCredentialUseCase
import com.chimali.fido2.domain.usecase.GetAllCredentialsUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.toList

private const val TAG = "Ctap2CredentialMgmt"

/**
 * T117, T118 — CTAP2 authenticatorCredentialManagement (0x0A) handler.
 * Supports subCommands for enumerating and deleting credentials.
 */
@Singleton
class Ctap2CredentialManagementHandler @Inject constructor(
    private val cborCodec: CborCodec,
    private val getAllCredentialsUseCase: GetAllCredentialsUseCase,
    private val deleteCredentialUseCase: DeleteCredentialUseCase
) {

    suspend fun handle(requestBytes: ByteArray): ByteArray {
        return try {
            val params = cborCodec.decodeFromFido2Format(requestBytes)
            val subCommand = (params["1"] as? Number)?.toInt() 
                ?: return byteArrayOf(0x0E) // CTAP1_ERR_MISSING_PARAMETER

            Log.d(TAG, "Credential Management subCommand: $subCommand")

            when (subCommand) {
                1 -> handleGetCredsMetadata()
                // 2 -> enumerateRPsBegin
                // 3 -> enumerateRPsGetNextRP
                // 4 -> enumerateCredentialsBegin 
                // 5 -> enumerateCredentialsGetNextCredential
                6 -> handleDeleteCredential(params)
                else -> byteArrayOf(0x11) // CTAP2_ERR_UNSUPPORTED_OPTION
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception handling credential management", e)
            byteArrayOf(0x17) // CTAP2_ERR_PROCESSING
        }
    }

    private suspend fun handleGetCredsMetadata(): ByteArray {
        val credentials = getAllCredentialsUseCase().toList()
        val numCredentials = credentials.size
        
        val response = mapOf<String, Any>(
            "1" to numCredentials,
            "2" to numCredentials // existingResidentCredentialsCount
        )
        val responseBytes = cborCodec.encodeToFido2Format(response)
        return byteArrayOf(0x00) + responseBytes
    }

    private suspend fun handleDeleteCredential(params: Map<String, Any>): ByteArray {
        val subCommandParams = params["2"] as? Map<*, *> ?: return byteArrayOf(0x0E)
        
        // In real CTAP2, CredentialId comes in subCommandParams map
        val credDescriptor = subCommandParams["1"] as? Map<*, *> ?: return byteArrayOf(0x0E)
        val credIdBytes = credDescriptor["id"] as? ByteArray ?: return byteArrayOf(0x0E)
        
        val credIdBase64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(credIdBytes)
        
        val result = deleteCredentialUseCase(credIdBase64)
        return if (result.isSuccess) {
            byteArrayOf(0x00) // CTAP2_OK
        } else {
            byteArrayOf(0x22) // CTAP2_ERR_NO_CREDENTIALS
        }
    }
}
