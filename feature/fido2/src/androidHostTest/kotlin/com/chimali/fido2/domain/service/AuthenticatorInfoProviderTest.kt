package com.chimali.fido2.domain.service

import com.chimali.fido2.domain.model.AuthenticatorTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthenticatorInfoProviderTest {
    @Test
    fun `FR-006 returns the supported CTAP capability contract`() {
        val info = AuthenticatorInfoProvider().getAuthenticatorInfo()

        assertEquals("U2F_V2", info.version)
        assertTrue(info.supportsAlgorithm("ES256"))
        assertTrue(info.supportsTransport(AuthenticatorTransport.BLE))
        assertTrue(info.isResidentKeySupported)
        assertTrue(info.isUserVerificationSupported)
        assertTrue(info.isReady())
    }
}
