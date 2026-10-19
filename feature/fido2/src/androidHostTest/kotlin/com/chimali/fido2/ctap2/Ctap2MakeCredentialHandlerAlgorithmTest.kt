package com.chimali.fido2.ctap2

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.CredentialKeyDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Verifies MakeCredential negotiation against the implemented local derivation profiles. */
@Req("NFR-SEC-041", "TR-AUTH-031")
class Ctap2MakeCredentialHandlerAlgorithmTest {
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `mixed unsupported and supported offers select the first supported RP offer`() {
        val selected = MakeCredentialAlgorithmNegotiator.select(listOf(-257, -49, -7))

        assertEquals(CredentialKeyDescriptor.COSE_ML_DSA_65, selected?.first)
        assertEquals("ML-DSA", selected?.second?.algorithm)
    }

    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `unsupported offer before ES256 is skipped`() {
        val selected = MakeCredentialAlgorithmNegotiator.select(listOf(-257, -7))

        assertEquals(CredentialKeyDescriptor.COSE_ES256, selected?.first)
        assertEquals("ES256", selected?.second?.algorithm)
    }

    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `EdDSA is selected when it is the first supported offer`() {
        val selected = MakeCredentialAlgorithmNegotiator.select(listOf(-257, -8, -7))

        assertEquals(CredentialKeyDescriptor.COSE_EDDSA, selected?.first)
        assertEquals("EdDSA", selected?.second?.algorithm)
    }

    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `ES256 is selected when it is the only supported offer`() {
        val selected = MakeCredentialAlgorithmNegotiator.select(listOf(-7))

        assertEquals(CredentialKeyDescriptor.COSE_ES256, selected?.first)
        assertEquals("ES256", selected?.second?.algorithm)
    }

    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `ML-DSA is selected when it is the only supported offer`() {
        val selected = MakeCredentialAlgorithmNegotiator.select(listOf(-49))

        assertEquals(CredentialKeyDescriptor.COSE_ML_DSA_65, selected?.first)
        assertEquals("ML-DSA", selected?.second?.algorithm)
    }

    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `unsupported-only offers have no negotiated algorithm`() {
        assertNull(MakeCredentialAlgorithmNegotiator.select(listOf(-257, -9, -19, -51, -52)))
    }
}
