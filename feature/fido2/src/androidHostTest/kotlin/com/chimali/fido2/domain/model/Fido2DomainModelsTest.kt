package com.chimali.fido2.domain.model

import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Fido2DomainModelsTest {
    private val nullRef: Any? = null
    private val otherTypeObj: Any = "unrelated string"

    // ── AuthenticatorSelectionCriteria ──────────────────────────────────────

    @Test
    fun `authenticatorSelectionCriteria property contracts and helpers`() {
        val credDescriptor =
            PublicKeyCredentialDescriptor.create(
                id = CredentialId.fromByteArray(ByteArray(16) { it.toByte() }),
            )
        val criteria =
            AuthenticatorSelectionCriteria.create(
                authenticatorAttachment = AuthenticatorAttachment.CROSS_PLATFORM,
                requireResidentKey = ResidentKeyRequirement.REQUIRED,
                userVerification = UserVerificationRequirement.REQUIRED,
                timeoutSeconds = 60L,
                allowCredentials = listOf(credDescriptor),
            )

        assertTrue(criteria.allowsCrossPlatform())
        assertTrue(criteria.requiresResidentKeys())
        assertTrue(criteria.requiresUserVerification())

        val description = criteria.getDescription()
        assertTrue(description.contains("cross_platform"))
        assertTrue(description.contains("required"))
        assertTrue(description.contains("60s"))
        assertTrue(description.contains("1 items"))

        val emptyCriteria = AuthenticatorSelectionCriteria.create()
        assertFalse(emptyCriteria.allowsCrossPlatform())
        assertFalse(emptyCriteria.requiresResidentKeys())
        assertFalse(emptyCriteria.requiresUserVerification())
        assertEquals("No specific criteria", emptyCriteria.getDescription())

        val criteria2 = criteria.copy()
        assertEquals(criteria, criteria2)
        assertEquals(criteria.hashCode(), criteria2.hashCode())
        assertFalse(criteria == nullRef)
        assertFalse(criteria == otherTypeObj)
    }

    @Test
    fun `authenticatorSelectionCriteria validation branches`() {
        assertFailsWith<IllegalArgumentException> {
            AuthenticatorSelectionCriteria.create(timeoutSeconds = 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            AuthenticatorSelectionCriteria.create(timeoutSeconds = -5L)
        }
        assertFailsWith<IllegalArgumentException> {
            AuthenticatorSelectionCriteria.create(timeoutSeconds = 301L)
        }

        val oversizedList =
            List(33) {
                PublicKeyCredentialDescriptor.create(
                    id = CredentialId.fromByteArray(ByteArray(16) { byteIdx -> (byteIdx + it).toByte() }),
                )
            }
        assertFailsWith<IllegalArgumentException> {
            AuthenticatorSelectionCriteria.create(allowCredentials = oversizedList)
        }
    }

    // ── PublicKeyCredentialDescriptor ───────────────────────────────────────

    @Test
    fun `publicKeyCredentialDescriptor property contracts and transports`() {
        val credId = CredentialId.fromByteArray(ByteArray(16) { it.toByte() })
        val descriptor =
            PublicKeyCredentialDescriptor.create(
                type = PublicKeyCredentialType.PUBLIC_KEY,
                id = credId,
                transports = listOf(AuthenticatorTransport.INTERNAL, AuthenticatorTransport.HYBRID),
            )

        assertEquals(PublicKeyCredentialType.PUBLIC_KEY, descriptor.type)
        assertEquals(credId, descriptor.id)
        assertEquals(credId.encoded, descriptor.getIdBase64Url())
        assertTrue(descriptor.getIdHex().isNotEmpty())
        assertTrue(descriptor.supportsTransport(AuthenticatorTransport.INTERNAL))
        assertFalse(descriptor.supportsTransport(AuthenticatorTransport.USB))
        assertEquals(2, descriptor.getSupportedTransports().size)

        val noTransports = PublicKeyCredentialDescriptor.create(id = credId)
        assertFalse(noTransports.supportsTransport(AuthenticatorTransport.INTERNAL))
        assertTrue(noTransports.getSupportedTransports().isEmpty())

        val descriptorSame = descriptor.copy()
        assertEquals(descriptor, descriptorSame)
        assertEquals(descriptor.hashCode(), descriptorSame.hashCode())
        assertFalse(descriptor == nullRef)
        assertFalse(descriptor == otherTypeObj)

        val diffType = descriptor.copy(type = PublicKeyCredentialType.PUBLIC_KEY)
        assertEquals(descriptor, diffType)
        val diffId = descriptor.copy(id = CredentialId.fromByteArray(ByteArray(16) { (it + 9).toByte() }))
        assertFalse(descriptor == diffId)
        val diffTransports = descriptor.copy(transports = listOf(AuthenticatorTransport.USB))
        assertFalse(descriptor == diffTransports)
    }

    @Test
    fun `publicKeyCredentialDescriptor validation branches`() {
        val credId = CredentialId.fromByteArray(ByteArray(16) { it.toByte() })
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialDescriptor(
                type = PublicKeyCredentialType.UNKNOWN,
                id = credId,
                transports = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialDescriptor.create(
                id = credId,
                transports = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            val list = List(6) { AuthenticatorTransport.INTERNAL }
            PublicKeyCredentialDescriptor.create(id = credId, transports = list)
        }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialDescriptor.create(
                id = credId,
                transports = listOf(AuthenticatorTransport.UNKNOWN),
            )
        }
    }

    // ── PublicKeyCredentialUserEntity ───────────────────────────────────────

    @Test
    fun `publicKeyCredentialUserEntity lifecycle and boundary methods`() {
        val userId = UserId.fromByteArray(byteArrayOf(0x0A, 0x0B, 0x0C))
        val user =
            PublicKeyCredentialUserEntity(
                id = userId,
                name = "bob@example.com",
                displayName = "Bob Dylan",
                icon = "https://example.com/bob.png",
            )

        assertEquals(userId, user.id)
        assertEquals("bob@example.com", user.name)
        assertEquals("Bob Dylan", user.displayName)
        assertEquals("https://example.com/bob.png", user.icon)
        assertEquals(userId.value, user.getIdBase64Url())
        assertEquals("0a0b0c", user.getIdHex())
        assertTrue(user.hasIcon())
        assertEquals("Bob Dylan", user.getSafeDisplayName())
        assertEquals("PublicKeyCredentialUserEntity([sensitive account fields redacted])", user.toString())

        val userNoIcon =
            PublicKeyCredentialUserEntity(
                id = userId,
                name = "carol",
                displayName = "Carol Danvers",
                icon = null,
            )
        assertFalse(userNoIcon.hasIcon())
        assertEquals("Carol Danvers", userNoIcon.getSafeDisplayName())

        val nameOwner = user.copyNameOwner()
        val displayNameOwner = user.copyDisplayNameOwner()
        val encodedIdOwner = user.copyEncodedIdOwner()
        assertNotNull(nameOwner)
        assertNotNull(displayNameOwner)
        assertNotNull(encodedIdOwner)

        val copiedUser = user.copy(displayName = "Robert Zimmerman")
        assertEquals("Robert Zimmerman", copiedUser.displayName)
        assertEquals("bob@example.com", copiedUser.name)

        user.eraseSensitiveText()
        assertTrue(user.name.all { it == '\u0000' })
        assertTrue(user.displayName.all { it == '\u0000' })
        assertTrue(user.getIdHex().all { it == '0' })
    }

    @Test
    fun `publicKeyCredentialUserEntity validation branches`() {
        val validId = UserId.fromByteArray(byteArrayOf(1))
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialUserEntity(UserId.fromByteArray(byteArrayOf()), "name", "display", null)
        }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialUserEntity(validId, "", "display", null)
        }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialUserEntity(validId, "name", "", null)
        }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialUserEntity(validId, "A".repeat(65), "display", null)
        }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialUserEntity(validId, "name", "B".repeat(65), null)
        }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialUserEntity(validId, "name", "display", "   ")
        }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialUserEntity(validId, "name", "display", "ftp://example.com/icon.png")
        }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialUserEntity(validId, "name", "display", "https://example.com/" + "A".repeat(2050))
        }
    }

    @Test
    fun `publicKeyCredentialUserEntity equals and hashCode branches`() {
        val u1 = PublicKeyCredentialUserEntity(UserId.fromByteArray(byteArrayOf(1, 2)), "name", "disp", "https://a.com")
        val u2 = PublicKeyCredentialUserEntity(UserId.fromByteArray(byteArrayOf(1, 2)), "name", "disp", "https://a.com")
        val uDiffId =
            PublicKeyCredentialUserEntity(UserId.fromByteArray(byteArrayOf(9)), "name", "disp", "https://a.com")
        val uDiffName =
            PublicKeyCredentialUserEntity(UserId.fromByteArray(byteArrayOf(1, 2)), "other", "disp", "https://a.com")
        val uDiffDisp =
            PublicKeyCredentialUserEntity(UserId.fromByteArray(byteArrayOf(1, 2)), "name", "other", "https://a.com")
        val uDiffIcon =
            PublicKeyCredentialUserEntity(UserId.fromByteArray(byteArrayOf(1, 2)), "name", "disp", "https://b.com")

        assertEquals(u1, u2)
        assertEquals(u1.hashCode(), u2.hashCode())
        assertFalse(u1 == uDiffId)
        assertFalse(u1 == uDiffName)
        assertFalse(u1 == uDiffDisp)
        assertFalse(u1 == uDiffIcon)
        assertFalse(u1 == nullRef)
        assertFalse(u1 == otherTypeObj)
    }

    // ── PublicKeyCredentialRpEntity ─────────────────────────────────────────

    @Test
    fun `publicKeyCredentialRpEntity lifecycle and validation`() {
        val rpId = RpId("example.com")
        val rp =
            PublicKeyCredentialRpEntity(
                id = rpId,
                name = "Example Corp",
                icon = "https://example.com/logo.png",
            )

        assertEquals(rpId, rp.id)
        assertEquals("Example Corp", rp.name)
        assertEquals("example.com", rp.getDomain())
        assertEquals("Example Corp", rp.getSafeName())
        assertEquals("PublicKeyCredentialRpEntity([sensitive RP fields redacted])", rp.toString())

        val idOwner = rp.copyIdOwner()
        val nameOwner = rp.copyNameOwner()
        assertNotNull(idOwner)
        assertNotNull(nameOwner)

        val copied = rp.copy(name = "New Corp")
        assertEquals("New Corp", copied.name)

        val rpSame = PublicKeyCredentialRpEntity(rpId, "Example Corp", "https://example.com/logo.png")
        assertEquals(rp, rpSame)
        assertEquals(rp.hashCode(), rpSame.hashCode())
        assertFalse(rp == nullRef)
        assertFalse(rp == otherTypeObj)

        rp.eraseSensitiveText()
        assertTrue(rp.name.all { it == '\u0000' })

        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialRpEntity(rpId, "", null)
        }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialRpEntity(RpId(""), "name", null)
        }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialRpEntity(rpId, "A".repeat(65), null)
        }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialRpEntity(rpId, "name", "   ")
        }
        assertFailsWith<IllegalArgumentException> {
            PublicKeyCredentialRpEntity(rpId, "name", "https://example.com/" + "X".repeat(2050))
        }
    }

    // ── AssertionObject ─────────────────────────────────────────────────────

    @Test
    fun `assertionObject property contracts, sign-count, and user presence flags`() {
        val credId = CredentialId.fromByteArray(ByteArray(16) { it.toByte() })
        val rpId = RpId("service.test")
        val assertion = AssertionObject.createTest(credentialId = credId, rpId = rpId)

        assertEquals(credId.encoded, assertion.credentialId)
        assertEquals(1L, assertion.extractSignCount())
        assertTrue(assertion.isUserPresent())
        assertTrue(assertion.isUserVerified())

        // Construct assertion with custom authData (flags = 0x00, counter = 0x12345678)
        val rpHash = ByteArray(32) { 0xAA.toByte() }
        val customFlags = byteArrayOf(0x00)
        val customCounter = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val customAuthData = rpHash + customFlags + customCounter
        val customSignature = ByteArray(64) { 0xBB.toByte() }

        val customAssertion =
            AssertionObject(
                credential = null,
                authData = customAuthData,
                signature = customSignature,
                user = null,
            )

        assertFalse(customAssertion.isUserPresent())
        assertFalse(customAssertion.isUserVerified())
        assertEquals(0x12345678L, customAssertion.extractSignCount())

        val assertionSame = AssertionObject.createTest(credentialId = credId, rpId = rpId)
        assertEquals(assertion, assertionSame)
        assertEquals(customAssertion, customAssertion)
        assertFalse(customAssertion == assertion)
        assertFalse(customAssertion == nullRef)
        assertFalse(customAssertion == otherTypeObj)

        assertFailsWith<IllegalArgumentException> {
            AssertionObject(
                credential = null,
                authData = ByteArray(36), // < 37 bytes
                signature = customSignature,
                user = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AssertionObject(
                credential = null,
                authData = customAuthData,
                signature = ByteArray(0), // empty signature
                user = null,
            )
        }
    }
}
