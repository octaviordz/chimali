package com.chimali.fido2.domain.service

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.domain.model.AuthenticatorTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Req("NFR-SEC-041", "TR-AUTH-031")
class AuthenticatorInfoProviderTest {
    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `FR-006 returns the supported CTAP capability contract`() {
        val info = AuthenticatorInfoProvider().getAuthenticatorInfo()

        assertEquals("U2F_V2", info.version)
        assertTrue(info.supportsAlgorithm("ES256"))
        assertTrue(info.supportsAlgorithm("EdDSA"))
        assertTrue(info.supportsAlgorithm("ML-DSA"))
        assertFalse(info.supportsAlgorithm("RS256"))
        assertTrue(info.supportsTransport(AuthenticatorTransport.BLE))
        assertTrue(info.isResidentKeySupported)
        assertTrue(info.isUserVerificationSupported)
        assertTrue(info.isReady())
    }
}
