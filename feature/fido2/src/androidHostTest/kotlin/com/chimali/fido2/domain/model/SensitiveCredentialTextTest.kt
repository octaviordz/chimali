package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Req("NFR-SEC-041", "TR-AUTH-031")
class SensitiveCredentialTextTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `erase overwrites owned characters and leaves independent copy intact`() {
        val owner = SensitiveCredentialText.copyOf("synthetic-user")
        val copy = owner.copyOwner()
        owner.erase()

        assertEquals("\u0000".repeat("synthetic-user".length), owner.toBoundaryString())
        assertEquals("synthetic-user", copy.toBoundaryString())
        assertEquals("[sensitive credential text]", owner.toString())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `subsequence borrows owner and reflects erasure without creating plaintext string`() {
        val owner = SensitiveCredentialText.copyOf("synthetic-name")
        val view = owner.subSequence(10, 14)

        assertEquals("name", String(CharArray(view.length) { view[it] }))
        owner.erase()

        assertEquals("\u0000\u0000\u0000\u0000", String(CharArray(view.length) { view[it] }))
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `text comparison supports value equality without exposing its string representation`() {
        val owner = SensitiveCredentialText.copyOf("Synthetic-RP")

        assertTrue(owner.contentEquals("Synthetic-RP"))
        assertFalse(owner.contentEquals("synthetic-rp"))
        assertTrue(owner.contentEquals("synthetic-rp", ignoreCase = true))
        assertTrue("Synthetic-RP" !in owner.toString())
    }
}
