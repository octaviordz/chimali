package com.chimali.fido2.domain.model

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * T034 — Unit tests for [AttestationObject] construction.
 *
 * Verifies:
 * - `authData` contains non-empty AAGUID (16 bytes), Credential ID, and Public Key
 * - AttestationType enum covers BASIC, SELF, ATT_CA, and NONE
 * - createSelfAttested() produces a "none" format object
 * - createAttCa() produces an "attCA" format object (T037)
 */
class AttestationObjectTest {
    private val ownedBuffers = mutableListOf<ByteArray>()
    private val ownedClientData = mutableListOf<ClientData>()
    private val ownedAttestations = mutableListOf<AttestationObject>()

    private fun ownedBytes(
        size: Int,
        value: Byte = 0,
    ): ByteArray = ByteArray(size) { value }.also(ownedBuffers::add)

    private val validRpIdHash = ownedBytes(32, 0x01)
    private val validAaguid = ownedBytes(16, 0x02)
    private val validCredentialId = ownedBytes(32, 0x03)
    private val validPublicKey = ownedBytes(77, 0x04) // Typical COSE ES256 key size

    @AfterTest
    fun eraseSyntheticAttestationFixtures() {
        ownedAttestations.forEach(AttestationObject::eraseSensitiveData)
        ownedClientData.forEach(ClientData::eraseSensitiveData)
        ownedBuffers.forEach { it.fill(0) }
        ownedAttestations.clear()
        ownedClientData.clear()
        ownedBuffers.clear()
    }

    private fun makeAuthData(
        aaguid: ByteArray = validAaguid,
        credentialId: ByteArray = validCredentialId,
        publicKey: ByteArray = validPublicKey,
    ) = AuthenticatorData.create(
        rpIdHash = validRpIdHash,
        flags = ownedBytes(1),
        aaguid = aaguid,
        credentialId = credentialId,
        publicKey = publicKey,
    )

    private fun makeClientData(): ClientData {
        val clientData =
            ClientData.create(
                challenge = ownedBytes(32),
                origin = "example.com",
            )
        ownedClientData.add(clientData)
        return clientData
    }

    // ── AuthData contents ─────────────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun authData_contains_nonEmpty_aaguid() {
        val authData = makeAuthData()
        assertFalse(authData.aaguid.isEmpty(), "AAGUID must not be empty")
        assertEquals(16, authData.aaguid.size, "AAGUID must be exactly 16 bytes")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun authData_contains_nonEmpty_credentialId() {
        val authData = makeAuthData()
        assertFalse(authData.credentialId.isEmpty(), "Credential ID must not be empty")
        assertTrue(authData.credentialId.size >= 16, "Credential ID must be at least 16 bytes")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun authData_contains_nonEmpty_publicKey() {
        val authData = makeAuthData()
        assertFalse(authData.publicKey.isEmpty(), "Public key must not be empty")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun authData_equality_compares_byte_array_contents_and_scalar_fields() {
        val original = makeAuthData()
        val equal = makeAuthData()

        assertTrue(original == original)
        assertTrue(original == equal)
        assertTrue(original.hashCode() == equal.hashCode())
        assertFalse(original.equals("not authenticator data"))
        assertFalse(original == makeAuthData().copy(rpIdHash = ownedBytes(32, 0x05)))
        assertFalse(original == makeAuthData().copy(flags = ownedBytes(1, 0x04)))
        assertFalse(original == makeAuthData().copy(counter = 1L))
        assertFalse(original == makeAuthData().copy(aaguid = ownedBytes(16, 0x06)))
        assertFalse(original == makeAuthData().copy(credentialId = ownedBytes(32, 0x07)))
        assertFalse(original == makeAuthData().copy(publicKey = ownedBytes(77, 0x08)))
    }

    // ── AttestationObject construction ────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun create_packed_format_succeeds() {
        val obj =
            AttestationObject.create(
                fmt = "packed",
                authData = makeAuthData(),
                attStmt = AttestationStatement.create(alg = AttestationStatement.COSE_ALG_ES256, fmt = "packed"),
                clientData = makeClientData(),
            )
        ownedAttestations.add(obj)
        assertNotNull(obj)
        assertEquals("packed", obj.fmt)
        assertTrue(obj.isPacked())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun createSelfAttested_returns_none_format() {
        val obj =
            AttestationObject.createSelfAttested(
                authData = makeAuthData(),
                clientData = makeClientData(),
            )
        ownedAttestations.add(obj)
        assertNotNull(obj)
        assertEquals("none", obj.fmt)
        assertTrue(obj.isSelfAttested())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun createAttCa_returns_attCA_format() {
        val obj =
            AttestationObject.createAttCa(
                authData = makeAuthData(),
                attStmt = AttestationStatement.create(alg = AttestationStatement.COSE_ALG_ES256, fmt = "attCA"),
                clientData = makeClientData(),
            )
        ownedAttestations.add(obj)
        assertNotNull(obj)
        assertEquals("attCA", obj.fmt)
    }

    // ── AttestationType enum ──────────────────────────────────────────────────

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun attestationType_covers_BASIC() {
        val type = AttestationType.BASIC
        assertEquals("Basic Attestation", type.getLabel())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun attestationType_covers_SELF() {
        val type = AttestationType.SELF
        assertEquals("Self Attestation", type.getLabel())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun attestationType_covers_ATT_CA() {
        val type = AttestationType.ATT_CA
        assertEquals("Attestation CA", type.getLabel())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun attestationType_covers_NONE() {
        val type = AttestationType.NONE
        assertEquals("No Attestation", type.getLabel())
    }
}
