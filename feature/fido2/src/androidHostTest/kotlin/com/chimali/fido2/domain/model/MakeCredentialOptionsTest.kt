package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MakeCredentialOptionsTest {
    private val rp = PublicKeyCredentialRpEntity.create(RpId("example.com"), "Example")
    private val user =
        PublicKeyCredentialUserEntity.create(
            UserId.fromByteArray("user123".encodeToByteArray()),
            "testuser",
            "Test User",
        )
    private val challenge = ByteArray(32) { it.toByte() }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `getSafeTimeout clamps values correctly`() {
        // T014: Input 20_000 -> returns 30_000 (min)
        val lowTimeout =
            MakeCredentialOptions.create(
                rp = rp,
                user = user,
                challenge = challenge,
                selectedAlgId = -7,
                timeout = 20000L,
            )
        // Currently getSafeTimeout() just returns timeout ?: DEFAULT
        // This test is EXPECTED TO FAIL for clamping until T015/T030
        // But wait, the task says T015 is implement the logic.
        // So I'll write the test and it should fail.
        assertEquals(30000L, lowTimeout.getSafeTimeout(), "Should clamp 20s to 30s min")

        // T014: Input 700_000 -> returns 600_000 (max)
        val highTimeout =
            MakeCredentialOptions.create(
                rp = rp,
                user = user,
                challenge = challenge,
                selectedAlgId = -7,
                timeout = 700000L,
            )
        assertEquals(600000L, highTimeout.getSafeTimeout(), "Should clamp 700s to 600s max")

        // T014: Input null -> returns 120_000 (default)
        val defaultTimeout =
            MakeCredentialOptions.create(
                rp = rp,
                user = user,
                challenge = challenge,
                selectedAlgId = -7,
                timeout = null,
            )
        assertEquals(120000L, defaultTimeout.getSafeTimeout(), "Should use 120s default")
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `selection helpers and challenge encoding reflect the supplied options`() {
        val requiredSelection =
            AuthenticatorSelectionCriteria.create(
                requireResidentKey = ResidentKeyRequirement.REQUIRED,
                userVerification = UserVerificationRequirement.REQUIRED,
            )
        val requiredOptions = options(authenticatorSelection = requiredSelection)

        assertTrue(requiredOptions.allowsResidentKeys())
        assertTrue(requiredOptions.requiresUserVerification())
        assertTrue(requiredOptions.allowsCredentialType(PublicKeyCredentialType.PUBLIC_KEY))
        assertEquals("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8", requiredOptions.getChallengeBase64Url())

        val preferredSelection =
            AuthenticatorSelectionCriteria.create(
                requireResidentKey = ResidentKeyRequirement.PREFERRED,
                userVerification = UserVerificationRequirement.PREFERRED,
            )
        val preferredOptions = options(authenticatorSelection = preferredSelection)
        assertTrue(preferredOptions.allowsResidentKeys())
        assertTrue(preferredOptions.requiresUserVerification())

        val discouragedOptions =
            options(
                authenticatorSelection =
                    AuthenticatorSelectionCriteria.create(
                        requireResidentKey = ResidentKeyRequirement.DISCOURAGED,
                        userVerification = UserVerificationRequirement.DISCOURAGED,
                    ),
            )
        assertEquals(false, discouragedOptions.allowsResidentKeys())
        assertEquals(false, discouragedOptions.requiresUserVerification())
        assertEquals(false, discouragedOptions.allowsCredentialType(PublicKeyCredentialType.UNKNOWN))
        assertEquals(false, options().allowsResidentKeys())
        assertEquals(false, options().requiresUserVerification())
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `present invalid credProtect values fail while absence retains the default`() {
        val absent = options()
        assertEquals(null, absent.extensions?.get("credProtect"))

        listOf(0, -1, 4, 4_294_967_297L, Long.MIN_VALUE, Long.MAX_VALUE, "3", true).forEach { raw ->
            assertFailsWith<IllegalArgumentException> {
                options(extensions = mapOf("credProtect" to raw))
            }
        }
        listOf(1, 2, 3, 1L, 2L, 3L).forEach { policy ->
            assertEquals(policy, options(extensions = mapOf("credProtect" to policy)).extensions?.get("credProtect"))
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `base64 challenge factory decodes url safe data and rejects invalid input`() {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
        val decoded =
            MakeCredentialOptions.fromBase64Challenge(
                rp = rp,
                user = user,
                challengeBase64 = encoded,
                selectedAlgId = -7,
            )

        assertEquals(challenge.toList(), decoded.challenge.toList())
        assertFailsWith<IllegalArgumentException> {
            MakeCredentialOptions.fromBase64Challenge(
                rp = rp,
                user = user,
                challengeBase64 = "%%invalid%%",
                selectedAlgId = -7,
            )
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `validation rejects empty or oversized challenges negative timeout and invalid extension names`() {
        assertFailsWith<IllegalArgumentException> { options(challenge = byteArrayOf()) }
        assertFailsWith<IllegalArgumentException> { options(challenge = ByteArray(1025)) }
        assertFailsWith<IllegalArgumentException> { options(timeout = -1L) }
        assertFailsWith<IllegalArgumentException> { options(extensions = mapOf("" to true)) }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `equality compares every option field and challenge contents`() {
        val original = options()

        assertEquals(original, original)
        assertEquals(original, options(challenge = challenge.copyOf()))
        assertEquals(original.hashCode(), options(challenge = challenge.copyOf()).hashCode())
        assertTrue(!original.equals("not options"))
        assertNotEquals(original, original.copy(rp = rp.copy(name = "Other")))
        assertNotEquals(original, original.copy(user = user.copy(name = "otheruser")))
        assertNotEquals(original, original.copy(challenge = challenge.copyOf().also { it[0] = 99 }))
        assertNotEquals(
            original,
            original.copy(
                pubKeyCredParams =
                    PublicKeyCredentialParameters.create(
                        algorithm = "RS256",
                        curve = null,
                        keyType = "RSA",
                    ),
            ),
        )
        assertNotEquals(original, original.copy(timeout = 1L))
        assertNotEquals(original, original.copy(allowCredentials = emptyList()))
        assertNotEquals(original, original.copy(excludeCredentials = emptyList()))
        assertNotEquals(
            original,
            original.copy(
                authenticatorSelection =
                    AuthenticatorSelectionCriteria.create(
                        requireResidentKey = ResidentKeyRequirement.REQUIRED,
                        userVerification = UserVerificationRequirement.REQUIRED,
                    ),
            ),
        )
        assertNotEquals(original, original.copy(attestation = AttestationConveyancePreference.DIRECT))
        assertNotEquals(original, original.copy(extensions = mapOf("credProtect" to 3)))
        assertNotEquals(original, original.copy(selectedAlgId = -8))
    }

    private fun options(
        challenge: ByteArray = this.challenge,
        timeout: Long? = null,
        authenticatorSelection: AuthenticatorSelectionCriteria? = null,
        extensions: Map<String, Any>? = null,
    ): MakeCredentialOptions =
        MakeCredentialOptions.create(
            rp = rp,
            user = user,
            challenge = challenge,
            selectedAlgId = -7,
            timeout = timeout,
            authenticatorSelection = authenticatorSelection,
            extensions = extensions,
        )
}
