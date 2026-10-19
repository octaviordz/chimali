package com.chimali.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelyingPartyOwnershipTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `failed RP construction erases every transferred owner`() {
        val rp = "https://sensitive.example".toCharArray()
        val name = " ".toCharArray()
        assertFailsWith<IllegalArgumentException> {
            RelyingParty.fromOwnedText(SensitiveText.takeOwnership(rp), SensitiveText.takeOwnership(name))
        }
        assertTrue(rp.all { it == '\u0000' })
        assertTrue(name.all { it == '\u0000' })
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `RP copy remains live after source disposal and diagnostics redact both owners`() {
        val rp =
            RelyingParty.fromOwnedText(
                SensitiveText.copyOf("https://sensitive.example"),
                SensitiveText.copyOf("München secret"),
            )
        val borrowedId = rp.idText
        val borrowedName = rp.nameText
        val copy = rp.withCredentialCount(2)
        rp.eraseSensitiveText()
        try {
            assertTrue(borrowedId.all { it == '\u0000' })
            assertTrue(borrowedName.all { it == '\u0000' })
            assertTrue(copy.idText.contentEquals("https://sensitive.example"))
            assertTrue(copy.nameText.contentEquals("München secret"))
            assertEquals(2, copy.credentialCount)
            assertFalse(copy.toString().contains("sensitive.example"))
            assertFalse(copy.toString().contains("München"))
        } finally {
            copy.eraseSensitiveText()
        }
    }
}
