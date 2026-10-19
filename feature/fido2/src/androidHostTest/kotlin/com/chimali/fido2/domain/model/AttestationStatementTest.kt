package com.chimali.fido2.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class AttestationStatementTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `supported string and numeric algorithms and formats are accepted`() {
        assertEquals("none", AttestationStatement.createNone().alg)
        assertEquals("EdDSA", AttestationStatement.create(alg = "EdDSA", fmt = "packed").alg)
        assertEquals(AttestationStatement.COSE_ALG_ES256, AttestationStatement.create().alg)
        assertEquals(
            AttestationStatement.COSE_ALG_ML_DSA_65.toLong(),
            AttestationStatement.create(alg = AttestationStatement.COSE_ALG_ML_DSA_65.toLong()).alg,
        )
        assertEquals("attCA", AttestationStatement.create(fmt = "attCA").fmt)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `unsupported algorithms formats and empty optional values are rejected`() {
        assertFailsWith<IllegalArgumentException> { AttestationStatement.create(alg = " ") }
        assertFailsWith<IllegalArgumentException> { AttestationStatement.create(alg = "ES999") }
        assertFailsWith<IllegalArgumentException> { AttestationStatement.create(alg = 123) }
        assertFailsWith<IllegalArgumentException> { AttestationStatement.create(alg = "ES256", fmt = "unknown") }
        assertFailsWith<IllegalArgumentException> { AttestationStatement.create(attCert = byteArrayOf()) }
        assertFailsWith<IllegalArgumentException> { AttestationStatement.create(authData = byteArrayOf()) }
        assertFailsWith<IllegalArgumentException> { AttestationStatement.create(x5c = emptyList()) }
        assertFailsWith<IllegalArgumentException> { AttestationStatement.create(x5c = listOf(byteArrayOf())) }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `certificate auth data and chain size boundaries are enforced`() {
        assertFailsWith<IllegalArgumentException> { AttestationStatement.create(attCert = ByteArray(4097)) }
        assertFailsWith<IllegalArgumentException> {
            AttestationStatement.create(authData = ByteArray(AttestationStatement.MAX_AUTH_DATA_BYTES + 1))
        }
        assertFailsWith<IllegalArgumentException> { AttestationStatement.create(x5c = List(11) { byteArrayOf(1) }) }
        assertFailsWith<IllegalArgumentException> { AttestationStatement.create(x5c = listOf(ByteArray(4097))) }

        val withinBounds =
            AttestationStatement.create(
                attCert = ByteArray(4096),
                authData = ByteArray(AttestationStatement.MAX_AUTH_DATA_BYTES),
                x5c = List(10) { byteArrayOf(1) },
            )
        assertEquals(4096, withinBounds.attCert?.size)
        assertEquals(10, withinBounds.x5c?.size)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `equality and hash compare optional byte fields and chain contents`() {
        val original = sampleStatement()
        val same = sampleStatement()

        assertEquals(original, original)
        assertEquals(original, same)
        assertEquals(original.hashCode(), same.hashCode())
        assertNotEquals(original, sampleStatement(alg = AttestationStatement.COSE_ALG_EDDSA))
        assertNotEquals(original, sampleStatement(fmt = "attCA"))
        assertNotEquals(original, sampleStatement(attCert = byteArrayOf(9)))
        assertNotEquals(original, sampleStatement(authData = byteArrayOf(9)))
        assertNotEquals(original, sampleStatement(x5c = listOf(byteArrayOf(3))))
        assertNotEquals(original, sampleStatement(x5c = listOf(byteArrayOf(3), byteArrayOf(9))))
        assertNotEquals(original, sampleStatement(attCert = null))
        assertNotEquals(original, sampleStatement(authData = null))
        assertNotEquals(original, sampleStatement(x5c = null))
    }

    private fun sampleStatement(
        alg: Any = AttestationStatement.COSE_ALG_ES256,
        fmt: String = "packed",
        attCert: ByteArray? = byteArrayOf(1),
        authData: ByteArray? = byteArrayOf(2),
        x5c: List<ByteArray>? = listOf(byteArrayOf(3), byteArrayOf(4)),
    ) = AttestationStatement.create(alg = alg, fmt = fmt, attCert = attCert, authData = authData, x5c = x5c)
}
