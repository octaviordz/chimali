package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.RpId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GetAssertionOptionsTest {
    private val rpId = RpId("example.com")
    private val clientDataHash = ByteArray(32) { it.toByte() }

    @Test
    fun `getSafeTimeout clamps values correctly`() {
        // T014: Input 20_000 -> returns 30_000 (min)
        val lowTimeout =
            GetAssertionOptions.create(
                rpId = rpId,
                clientDataHash = clientDataHash,
                timeout = 20000L,
            )
        assertEquals(30000L, lowTimeout.getSafeTimeout(), "Should clamp 20s to 30s min")

        // T014: Input 700_000 -> returns 600_000 (max)
        val highTimeout =
            GetAssertionOptions.create(
                rpId = rpId,
                clientDataHash = clientDataHash,
                timeout = 700000L,
            )
        assertEquals(600000L, highTimeout.getSafeTimeout(), "Should clamp 700s to 600s max")

        // T014: Input null -> returns 120_000 (default)
        val defaultTimeout =
            GetAssertionOptions.create(
                rpId = rpId,
                clientDataHash = clientDataHash,
                timeout = null,
            )
        assertEquals(120000L, defaultTimeout.getSafeTimeout(), "Should use 120s default")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `copy owns an independent erasable RP identifier`() {
        val original = GetAssertionOptions.create(rpId, clientDataHash)
        val copy = original.copy(allowCredentials = emptyList())

        original.eraseSensitiveText()

        assertEquals(rpId, copy.rpId)
        copy.eraseSensitiveText()
        assertTrue(copy.rpId.value.all { it == '\u0000' })
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `invalid construction and copies erase new owners while preserving borrowed source`() {
        val transferred = SensitiveCredentialText.copyOf("example.com")
        assertFailsWith<IllegalArgumentException> {
            GetAssertionOptions.fromSensitiveRpId(transferred, ByteArray(31), timeout = -1)
        }
        assertTrue(transferred.copyCharacters().all { it == '\u0000' })

        val original = GetAssertionOptions.create(rpId, clientDataHash)
        val failedCopyOwners = mutableListOf<SensitiveCredentialText>()
        assertFailsWith<IllegalArgumentException> {
            original.copyWithOwnerFactory(
                timeout = -1,
                sourceOwnerFactory = { it.copyOwner().also(failedCopyOwners::add) },
            )
        }
        assertTrue(failedCopyOwners.single().copyCharacters().all { it == '\u0000' })
        failedCopyOwners.clear()
        assertFailsWith<IllegalArgumentException> {
            original.copyWithOwnerFactory(
                clientDataHash = ByteArray(31),
                sourceOwnerFactory = { it.copyOwner().also(failedCopyOwners::add) },
            )
        }
        assertTrue(failedCopyOwners.single().copyCharacters().all { it == '\u0000' })
        assertEquals(rpId, original.rpId)

        val validCopy = original.copy()
        original.eraseSensitiveText()
        assertEquals(rpId, validCopy.rpId)
        validCopy.eraseSensitiveText()
    }
}
