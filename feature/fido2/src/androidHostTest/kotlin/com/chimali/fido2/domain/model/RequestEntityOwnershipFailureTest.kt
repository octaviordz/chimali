package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RequestEntityOwnershipFailureTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `failed RP owner transfer erases both owners and failed copies preserve original`() {
        val idOwner = SensitiveCredentialText.copyOf("invalid rp")
        val nameOwner = SensitiveCredentialText.copyOf("Account RP")
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialRpEntity.fromSensitiveOwners(idOwner, nameOwner)
        }
        assertTrue(idOwner.copyCharacters().all { it == '\u0000' })
        assertTrue(nameOwner.copyCharacters().all { it == '\u0000' })

        val original = PublicKeyCredentialRpEntity.create(RpId("example.com"), "Account RP")
        val failedCopyOwners = mutableListOf<SensitiveCredentialText>()
        assertFailsWith<IllegalArgumentException> {
            original.copyWithOwnerFactory(name = " ", ownerFactory = {
                SensitiveCredentialText.copyOf(it).also(failedCopyOwners::add)
            })
        }
        assertEquals(2, failedCopyOwners.size)
        failedCopyOwners.forEach { owner -> assertTrue(owner.copyCharacters().all { it == '\u0000' }) }
        assertEquals(RpId("example.com"), original.id)
        assertEquals("Account RP", original.name)

        val copy = original.copy()
        original.eraseSensitiveText()
        assertEquals(RpId("example.com"), copy.id)
        assertEquals("Account RP", copy.name)
        copy.eraseSensitiveText()
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `failed user owner transfer erases id and text and failed copies preserve original`() {
        val idBytes = byteArrayOf(1, 2, 3)
        val nameOwner = SensitiveCredentialText.copyOf(" ")
        val displayOwner = SensitiveCredentialText.copyOf("Account")
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialUserEntity.fromSensitiveOwners(idBytes, nameOwner, displayOwner)
        }
        assertContentEquals(ByteArray(3), idBytes)
        assertTrue(nameOwner.copyCharacters().all { it == '\u0000' })
        assertTrue(displayOwner.copyCharacters().all { it == '\u0000' })

        val original = PublicKeyCredentialUserEntity.create(UserId("YWxpY2U"), "alice", "Alice")
        val failedCopyTextOwners = mutableListOf<SensitiveCredentialText>()
        var failedCopyId: ByteArray? = null
        assertFailsWith<IllegalArgumentException> {
            original.copyWithOwnerFactories(
                name = "",
                byteFactory = { it.copyOf().also { copy -> failedCopyId = copy } },
                ownerFactory = { SensitiveCredentialText.copyOf(it).also(failedCopyTextOwners::add) },
            )
        }
        assertContentEquals(ByteArray(5), requireNotNull(failedCopyId))
        assertEquals(2, failedCopyTextOwners.size)
        failedCopyTextOwners.forEach { owner -> assertTrue(owner.copyCharacters().all { it == '\u0000' }) }
        assertEquals("alice", original.name)
        assertEquals("Alice", original.displayName)
        assertEquals(UserId("YWxpY2U"), original.id)
        val encodedIdOwner = original.copyEncodedIdOwner()
        assertEquals(original.id.value, encodedIdOwner.toBoundaryString())
        assertEquals(
            "José",
            run {
                val unicode = PublicKeyCredentialUserEntity.create(UserId("dXNlcg"), "José", "José User")
                try {
                    unicode.name
                } finally {
                    unicode.eraseSensitiveText()
                }
            },
        )
        encodedIdOwner.erase()

        val copy = original.copy()
        original.eraseSensitiveText()
        assertEquals("alice", copy.name)
        assertEquals("Alice", copy.displayName)
        copy.eraseSensitiveText()
    }
}
