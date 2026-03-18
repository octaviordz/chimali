package com.chimali.fido2.ctap2

import android.util.Log
import timber.log.Timber
import com.chimali.fido2.domain.usecase.ResetAuthenticatorUseCase
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Ctap2ResetAuth"

/**
 * T117 — CTAP2 authenticatorReset (0x07) handler.
 * Performs a master reset of the authenticator, clearing all credentials and settings.
 */
@Singleton
class Ctap2ResetAuthenticatorHandler @Inject constructor(
    private val resetAuthenticatorUseCase: ResetAuthenticatorUseCase
) {

    suspend fun handle(requestBytes: ByteArray): ByteArray {
        Log.d(TAG, "Handling authenticatorReset")
        
        return try {
            val result = resetAuthenticatorUseCase()
            if (result.isSuccess) {
                byteArrayOf(0x00) // CTAP2_OK
            } else {
                byteArrayOf(0x17) // CTAP2_ERR_PROCESSING
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception handling authenticator reset")
            byteArrayOf(0x17) // CTAP2_ERR_PROCESSING
        }
    }
}
