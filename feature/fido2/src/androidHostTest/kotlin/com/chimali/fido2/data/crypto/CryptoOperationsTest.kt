package com.chimali.fido2.data.crypto

import com.chimali.core.common.annotation.Req
import com.chimali.fido2.domain.model.SensitiveCredentialText
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class CryptoOperationsTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `sha256 helper matches MessageDigest`() {
        val data = "hello fido2".toByteArray()
        val expected = MessageDigest.getInstance("SHA-256").digest(data)
        assertContentEquals(expected, ClientDataHashService().sha256(data))
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `RP hash encodes borrowed Unicode owner and leaves caller ownership intact`() {
        val rpOwner = SensitiveCredentialText.copyOf("münchen.example")
        val expected = MessageDigest.getInstance("SHA-256").digest("münchen.example".toByteArray(Charsets.UTF_8))

        assertContentEquals(expected, ClientDataHashService().rpIdHash(rpOwner))
        assertEquals("münchen.example", rpOwner.toBoundaryString())
        rpOwner.erase()
        assertEquals("\u0000".repeat("münchen.example".length), rpOwner.toBoundaryString())
    }
}
