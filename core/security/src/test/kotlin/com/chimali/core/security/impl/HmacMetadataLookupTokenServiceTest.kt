package com.chimali.core.security.impl

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.test.assertFailsWith

class HmacMetadataLookupTokenServiceTest {

    private lateinit var service: HmacMetadataLookupTokenService
    private lateinit var testKey: ByteArray

    @BeforeEach
    fun setup() {
        service = HmacMetadataLookupTokenService()
        testKey = ByteArray(32)
        SecureRandom().nextBytes(testKey)
        service.provisionKey(testKey)
    }

    @Test
    fun `generateToken is deterministic for same input and domain`() {
        val domain = "test_domain"
        val value = "test_value"

        val token1 = service.generateToken(domain, value)
        val token2 = service.generateToken(domain, value)

        assertArrayEquals(token1, token2)
    }

    @Test
    fun `generateToken provides domain separation`() {
        val value = "test_value"

        val token1 = service.generateToken("domain_A", value)
        val token2 = service.generateToken("domain_B", value)

        assertFalse(token1.contentEquals(token2))
    }

    @Test
    fun `generateToken fails if key not provisioned`() {
        val unprovisionedService = HmacMetadataLookupTokenService()

        assertFailsWith<IllegalStateException> {
            unprovisionedService.generateToken("domain", "value")
        }
    }

    @Test
    fun `clearKey zeros key material and prevents further generation`() {
        service.clearKey()

        assertFailsWith<IllegalStateException> {
            service.generateToken("domain", "value")
        }
        
        // Also verify that testKey is NOT cleared (since we provision a copy)
        // This is an implementation detail check: provisionKey should copy
        // the input so clearing the service key doesn't destroy the original
        // buffer prematurely if the caller still needs it.
        assertFalse(testKey.all { it == 0.toByte() })
    }
}
