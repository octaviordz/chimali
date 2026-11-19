package com.chimali.fido2.domain.exception

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Fido2ExceptionPrivacyTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `identifier-bearing exception constructors retain fixed messages`() {
        val marker = "synthetic-private-identifier-marker"
        val exceptions =
            listOf(
                Fido2Exception.UnsupportedAlgorithm(marker) to "Unsupported algorithm",
                Fido2Exception.UnsupportedCurve(marker) to "Unsupported elliptic curve",
                Fido2Exception.KeyNotFound(marker) to "Key not found",
                Fido2Exception.RpIdMismatch(marker, marker) to "RP-ID mismatch",
                Fido2Exception.CredentialNotFoundException(marker) to "Credential not found",
                Fido2Exception.CredentialNotFound(marker) to "Credential not found",
                Fido2Exception.DuplicateCredentialException(marker) to "Duplicate credential for RP",
                Fido2Exception.RelyingPartyBlocked(marker) to "Relying party is blocked",
            )

        exceptions.forEach { (exception, expectedMessage) ->
            assertEquals(expectedMessage, exception.message)
            assertFalse(exception.message.orEmpty().contains(marker))
        }
    }
}
