package com.chimali.fido2.data.crypto

import com.chimali.core.domain.valueobject.CredentialId
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CryptoOperationsTest {
    companion object {
        private const val PUB_KEY_SIZE_65 = 65
        private const val COSE_ES256_VAL = -7
    }

    @Test
    fun `sha256 companion helper matches MessageDigest`() {
        val data = "hello fido2".toByteArray()
        val expected = MessageDigest.getInstance("SHA-256").digest(data)
        assertContentEquals(expected, ClientDataHashService.sha256(data))
    }

    // -- Fido2CryptoService constants ------------------------------------------

    @Test
    fun `credentialAlias follows expected prefix`() {
        // CredentialId must be at least 16 bytes
        val credId = CredentialId.fromByteArray("abcdefghijklmnop".encodeToByteArray())
        val alias = Fido2CryptoService.credentialAlias(credId)
        assertTrue(alias.startsWith("device-key/"))
    }

    @Test
    fun `COSE_ES256 constant is minus 7`() {
        assertEquals(COSE_ES256_VAL, Fido2CryptoService.COSE_ES256)
    }

    // -- Fido2KeyPair ----------------------------------------------------------

    @Test
    fun `Fido2KeyPair equality based on alias and bytes`() {
        val bytes = ByteArray(PUB_KEY_SIZE_65) { it.toByte() }
        val kp1 = Fido2KeyPair("alias_a", bytes)
        val kp2 = Fido2KeyPair("alias_a", bytes.copyOf())
        val kp3 = Fido2KeyPair("alias_b", bytes)
        assertEquals(kp1, kp2)
        assertNotEquals(kp1, kp3)
    }

    @Test
    fun `Fido2KeyPair toString contains alias and length`() {
        val kp = Fido2KeyPair("my_alias", ByteArray(PUB_KEY_SIZE_65))
        val str = kp.toString()
        assertTrue(str.contains("my_alias"))
        assertTrue(str.contains(PUB_KEY_SIZE_65.toString()))
    }
}
