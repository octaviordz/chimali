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
    fun `model construction failure erases both staged owners`() {
        val id = "https://sensitive.example".toCharArray()
        val name = "Sensitive Service".toCharArray()

        assertFailsWith<IllegalStateException> {
            RelyingParty.fromOwnedTextWithFactory(
                SensitiveText.takeOwnership(id),
                SensitiveText.takeOwnership(name),
            ) { _, _ -> error("synthetic wrapper construction failure") }
        }

        assertTrue(id.all { it == '\u0000' })
        assertTrue(name.all { it == '\u0000' })
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `RP copy erases its first staged owner if creating the second fails`() {
        val rp =
            RelyingParty.fromOwnedText(
                SensitiveText.copyOf("https://sensitive.example"),
                SensitiveText.copyOf("Sensitive Service"),
            )
        val stagedOwners = mutableListOf<SensitiveText>()
        var ownerCount = 0

        try {
            assertFailsWith<IllegalStateException> {
                rp.copyWithOwnerFactory(
                    ownerFactory = { source ->
                        ownerCount++
                        if (ownerCount == 2) error("synthetic second-owner copy failure")
                        SensitiveText.copyOf(source).also(stagedOwners::add)
                    },
                )
            }

            assertTrue(stagedOwners.single().all { it == '\u0000' })
            assertTrue(rp.idText.contentEquals("https://sensitive.example"))
            assertTrue(rp.nameText.contentEquals("Sensitive Service"))
        } finally {
            rp.eraseSensitiveText()
            stagedOwners.forEach(SensitiveText::erase)
        }
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
