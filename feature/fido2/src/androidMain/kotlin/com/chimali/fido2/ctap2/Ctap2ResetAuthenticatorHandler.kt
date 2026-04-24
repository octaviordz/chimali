package com.chimali.fido2.ctap2

import co.touchlab.kermit.Logger
import com.chimali.fido2.domain.usecase.ResetAuthenticatorUseCase
import org.koin.core.annotation.Single

/**
 * T117 — CTAP2 authenticatorReset (0x07) handler.
 * Performs a master reset of the authenticator, clearing all credentials and settings.
 */
@Single
class Ctap2ResetAuthenticatorHandler(
    private val resetAuthenticatorUseCase: ResetAuthenticatorUseCase,
) {
    companion object {
        private const val CTAP2_OK: Byte = 0x00
        private const val CTAP2_ERR_PROCESSING: Byte = 0x17
    }

    suspend fun handle(requestBytes: ByteArray): ByteArray {
        Logger.d("Handling authenticatorReset")

        return try {
            val result = resetAuthenticatorUseCase()
            if (result.isSuccess) {
                byteArrayOf(CTAP2_OK)
            } else {
                byteArrayOf(CTAP2_ERR_PROCESSING)
            }
        } catch (e: Exception) {
            Logger.e(e) { "Exception handling authenticator reset" }
            byteArrayOf(CTAP2_ERR_PROCESSING)
        }
    }
}
