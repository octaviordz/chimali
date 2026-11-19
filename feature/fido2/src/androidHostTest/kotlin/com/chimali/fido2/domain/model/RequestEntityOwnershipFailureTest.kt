package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RequestEntityOwnershipFailureTest {
    private val ownedText = mutableListOf<SensitiveCredentialText>()
    private val ownedBytes = mutableListOf<ByteArray>()
    private val ownedRelyingParties = mutableListOf<PublicKeyCredentialRpEntity>()
    private val ownedUsers = mutableListOf<PublicKeyCredentialUserEntity>()

    @AfterTest
    fun tearDown() {
        ownedRelyingParties.forEach(PublicKeyCredentialRpEntity::eraseSensitiveText)
        ownedUsers.forEach(PublicKeyCredentialUserEntity::eraseSensitiveText)
        ownedText.forEach(SensitiveCredentialText::erase)
        ownedBytes.forEach { it.fill(0) }
        ownedRelyingParties.clear()
        ownedUsers.clear()
        ownedText.clear()
        ownedBytes.clear()
    }

    private fun own(owner: SensitiveCredentialText): SensitiveCredentialText = owner.also(ownedText::add)

    private fun own(bytes: ByteArray): ByteArray = bytes.also(ownedBytes::add)

    private fun own(entity: PublicKeyCredentialRpEntity): PublicKeyCredentialRpEntity =
        entity.also(ownedRelyingParties::add)

    private fun own(entity: PublicKeyCredentialUserEntity): PublicKeyCredentialUserEntity = entity.also(ownedUsers::add)

    private fun assertErased(owner: SensitiveCredentialText) {
        val copy = owner.copyCharacters()
        try {
            assertTrue(copy.all { it == '\u0000' })
        } finally {
            copy.fill('\u0000')
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `failed RP owner transfer erases both owners and failed copies preserve original`() {
        val idOwner = own(SensitiveCredentialText.copyOf("invalid rp"))
        val nameOwner = own(SensitiveCredentialText.copyOf("Account RP"))
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialRpEntity.fromSensitiveOwners(idOwner, nameOwner)
        }
        assertErased(idOwner)
        assertErased(nameOwner)

        val original = own(PublicKeyCredentialRpEntity.create(RpId("example.com"), "Account RP"))
        val failedCopyOwners = mutableListOf<SensitiveCredentialText>()
        assertFailsWith<IllegalArgumentException> {
            original.copyWithOwnerFactory(name = " ", ownerFactory = {
                own(SensitiveCredentialText.copyOf(it)).also(failedCopyOwners::add)
            })
        }
        assertEquals(2, failedCopyOwners.size)
        failedCopyOwners.forEach(::assertErased)
        assertTrue(original.idText.contentEquals("example.com"))
        assertEquals("Account RP", original.name)

        val copy = own(original.copy())
        original.eraseSensitiveText()
        assertTrue(copy.idText.contentEquals("example.com"))
        assertEquals("Account RP", copy.name)
        copy.eraseSensitiveText()
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `failed user owner transfer erases id and text and failed copies preserve original`() {
        val idBytes = own(byteArrayOf(1, 2, 3))
        val nameOwner = own(SensitiveCredentialText.copyOf(" "))
        val displayOwner = own(SensitiveCredentialText.copyOf("Account"))
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialUserEntity.fromSensitiveOwners(idBytes, nameOwner, displayOwner)
        }
        assertContentEquals(ByteArray(3), idBytes)
        assertErased(nameOwner)
        assertErased(displayOwner)

        val original = own(PublicKeyCredentialUserEntity.create(UserId("YWxpY2U"), "alice", "Alice"))
        val failedCopyTextOwners = mutableListOf<SensitiveCredentialText>()
        var failedCopyId: ByteArray? = null
        assertFailsWith<IllegalArgumentException> {
            original.copyWithOwnerFactories(
                name = "",
                byteFactory = { it.copyOf().also { copy -> failedCopyId = own(copy) } },
                ownerFactory = { own(SensitiveCredentialText.copyOf(it)).also(failedCopyTextOwners::add) },
            )
        }
        assertContentEquals(ByteArray(5), requireNotNull(failedCopyId))
        assertEquals(2, failedCopyTextOwners.size)
        failedCopyTextOwners.forEach(::assertErased)
        assertEquals("alice", original.name)
        assertEquals("Alice", original.displayName)
        val encodedIdOwner = own(original.copyEncodedIdOwner())
        assertEquals("YWxpY2U", encodedIdOwner.toBoundaryString())
        assertEquals(
            "José",
            run {
                val unicode = own(PublicKeyCredentialUserEntity.create(UserId("dXNlcg"), "José", "José User"))
                try {
                    unicode.name
                } finally {
                    unicode.eraseSensitiveText()
                }
            },
        )
        encodedIdOwner.erase()

        val copy = own(original.copy())
        original.eraseSensitiveText()
        assertEquals("alice", copy.name)
        assertEquals("Alice", copy.displayName)
        copy.eraseSensitiveText()
    }
}
