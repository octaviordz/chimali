package com.chimali.core.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.PasskeyId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlinx.datetime.Instant

/**
 * Unit tests for Passkey domain model equals, hashCode, and property boundaries.
 * Traceable to NFR-SEC-041 and TR-AUTH-031.
 */
@Req("NFR-SEC-041", "TR-AUTH-031")
class PasskeyTest {
    private val defaultInstant = Instant.parse("2026-10-25T00:00:00Z")
    private val passkey =
        Passkey(
            id = PasskeyId("passkey-id-1"),
            relyingParty = "example.com",
            username = "alice",
            credentialId = byteArrayOf(1, 2, 3),
            publicKey = byteArrayOf(4, 5, 6),
            signatureCounter = 42L,
            createdAt = defaultInstant,
            lastUsed = defaultInstant,
        )

    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `equals reflexivity and null or different class handling`() {
        assertEquals(passkey, passkey)
        val nullObj: Any? = null
        assertFalse(passkey == nullObj)
        assertFalse(passkey.equals("not a passkey"))
    }

    @Test
    @Req("NFR-SEC-041", "TR-AUTH-031")
    fun `equals and hashCode compare all value properties`() {
        val same =
            Passkey(
                id = PasskeyId("passkey-id-1"),
                relyingParty = "example.com",
                username = "alice",
                credentialId = byteArrayOf(1, 2, 3),
                publicKey = byteArrayOf(4, 5, 6),
                signatureCounter = 42L,
                createdAt = defaultInstant,
                lastUsed = defaultInstant,
            )
        assertEquals(passkey, same)
        assertEquals(passkey.hashCode(), same.hashCode())

        // Different ID
        assertNotEquals(passkey, passkey.copy(id = PasskeyId("passkey-id-2")))

        // Different Relying Party
        assertNotEquals(passkey, passkey.copy(relyingParty = "other.com"))

        // Different Username
        assertNotEquals(passkey, passkey.copy(username = "bob"))

        // Different Credential ID bytes
        assertNotEquals(passkey, passkey.copy(credentialId = byteArrayOf(1, 2, 9)))

        // Different Public Key bytes
        assertNotEquals(passkey, passkey.copy(publicKey = byteArrayOf(4, 5, 9)))

        // Different Signature Counter
        assertNotEquals(passkey, passkey.copy(signatureCounter = 43L))

        // Different CreatedAt
        assertNotEquals(passkey, passkey.copy(createdAt = Instant.parse("2026-10-24T00:00:00Z")))

        // Different LastUsed: null vs non-null
        val passkeyNullLastUsed = passkey.copy(lastUsed = null)
        assertNotEquals(passkey, passkeyNullLastUsed)
        assertNotEquals(passkeyNullLastUsed, passkey)

        // Different LastUsed: different instant
        assertNotEquals(passkey, passkey.copy(lastUsed = Instant.parse("2026-10-25T01:00:00Z")))

        // HashCode with null lastUsed
        val otherNullLastUsed = passkey.copy(lastUsed = null)
        assertEquals(passkeyNullLastUsed.hashCode(), otherNullLastUsed.hashCode())
        assertNotEquals(passkey.hashCode(), passkeyNullLastUsed.hashCode())
    }
}
