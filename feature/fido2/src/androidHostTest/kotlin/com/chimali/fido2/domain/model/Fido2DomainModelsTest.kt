package com.chimali.fido2.domain.model

import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import kotlin.test.Test
import kotlin.test.assertContentEquals
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

        assertEquals("bob@example.com", user.name)
        assertEquals("Bob Dylan", user.displayName)
        assertEquals("https://example.com/bob.png", user.icon)
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
        val encodedId = encodedIdOwner.copyCharacters()
        val expectedEncodedId = userId.value.toCharArray()
        try {
            assertContentEquals(expectedEncodedId, encodedId)
        } finally {
            encodedId.fill('\u0000')
            expectedEncodedId.fill('\u0000')
        }

        val copiedUser = user.copy(displayName = "Robert Zimmerman")
        assertEquals("Robert Zimmerman", copiedUser.displayName)
        assertEquals("bob@example.com", copiedUser.name)

        user.eraseSensitiveText()
        assertTrue(user.name.all { it == '\u0000' })
        assertTrue(user.displayName.all { it == '\u0000' })
        val idBytesField =
            PublicKeyCredentialUserEntity::class.java
                .getDeclaredField("idBytes")
                .apply { isAccessible = true }
        assertTrue((idBytesField.get(user) as ByteArray).all { it == 0.toByte() })
        nameOwner.erase()
        displayNameOwner.erase()
        encodedIdOwner.erase()
        userNoIcon.eraseSensitiveText()
        copiedUser.eraseSensitiveText()
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
    fun `publicKeyCredentialUserEntity factory failure erases every transferred owner`() {
        val idBytes = ByteArray(16) { 0x5A.toByte() }
        val name = SensitiveCredentialText.copyOf("synthetic-user")
        val displayName = SensitiveCredentialText.copyOf("Synthetic User")

        assertFailsWith<IllegalStateException> {
            PublicKeyCredentialUserEntity.fromSensitiveOwnersWithFactory(idBytes, name, displayName) { _, _, _ ->
                error("synthetic entity construction failure")
            }
        }

        assertTrue(idBytes.all { it == 0.toByte() })
        assertTrue(name.all { it == '\u0000' })
        assertTrue(displayName.all { it == '\u0000' })
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

        assertTrue(rp.idText.contentEquals(rpId.value))
        assertEquals("Example Corp", rp.name)
        assertFalse(PublicKeyCredentialRpEntity::class.java.declaredMethods.any { it.name == "getDomain" })
        assertFalse(PublicKeyCredentialRpEntity::class.java.declaredMethods.any { it.name == "getSafeName" })
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

    @Test
    fun `publicKeyCredentialRpEntity factory failure erases both transferred owners`() {
        val id = SensitiveCredentialText.copyOf("example.com")
        val name = SensitiveCredentialText.copyOf("Example RP")

        assertFailsWith<IllegalStateException> {
            PublicKeyCredentialRpEntity.fromSensitiveOwnersWithFactory(id, name) { _, _ ->
                error("synthetic entity construction failure")
            }
        }

        assertTrue(id.all { it == '\u0000' })
        assertTrue(name.all { it == '\u0000' })
    }

    @Test
    fun `publicKeyCredentialRpEntity create erases first staged copy if second copy fails`() {
        val stagedOwners = mutableListOf<SensitiveCredentialText>()
        var ownerCount = 0

        assertFailsWith<IllegalStateException> {
            PublicKeyCredentialRpEntity.createWithOwnerFactory(
                RpId("example.com"),
                "Example RP",
                ownerFactory = { source ->
                    ownerCount++
                    if (ownerCount == 2) error("synthetic RP name copy failure")
                    SensitiveCredentialText.copyOf(source).also(stagedOwners::add)
                },
            )
        }

        assertTrue(stagedOwners.single().all { it == '\u0000' })
    }

    // ── AssertionObject ─────────────────────────────────────────────────────

    @Test
    fun `assertionObject property contracts, sign-count, and user presence flags`() {
        val credId = CredentialId.fromByteArray(ByteArray(16) { it.toByte() })
        val rpId = RpId("service.test")
        val assertion = AssertionObject.createTest(credentialId = credId, rpId = rpId)

        assertEquals(credId.encoded, assertion.credentialId)
        assertFalse(AssertionObject::class.java.declaredFields.any { it.name == "credentialId" })
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
