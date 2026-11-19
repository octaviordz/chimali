package com.chimali.fido2.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

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
    fun `request RP entity exposes no immutable identifier compatibility getter`() {
        assertFalse(PublicKeyCredentialRpEntity::class.java.declaredMethods.any { it.name == "getId" })
    }

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
    fun `malformed base64 challenge does not retain decoder failure`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                MakeCredentialOptions.fromBase64Challenge(
                    rp = rp,
                    user = user,
                    challengeBase64 = "synthetic-challenge-marker%%",
                    selectedAlgId = -7,
                )
            }

        assertEquals("Invalid base64 challenge", failure.message)
        assertNull(failure.cause)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `decoded base64 challenge is erased when construction does not transfer ownership`() {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
        var failedChallenge: ByteArray? = null

        assertFailsWith<IllegalArgumentException> {
            MakeCredentialOptions.withDecodedBase64Challenge(encoded) { decoded ->
                failedChallenge = decoded
                throw IllegalArgumentException("synthetic option validation failure")
            }
        }

        assertContentEquals(ByteArray(challenge.size), failedChallenge)

        val transferred = MakeCredentialOptions.withDecodedBase64Challenge(encoded) { it }
        assertContentEquals(challenge, transferred)
        transferred.fill(0)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `independent owner copy erases without changing the source options`() {
        val source = options()
        val ownedCopy = source.copyWithIndependentSensitiveOwners()

        ownedCopy.eraseSensitiveText()

        assertContentEquals(challenge, source.challenge)
        assertTrue(source.challenge.any { it != 0.toByte() })
        assertEquals("testuser", source.user.name)
        assertEquals("Example", source.rp.name)
        assertTrue(ownedCopy.challenge.all { it == 0.toByte() })
        assertTrue(ownedCopy.user.name.all { it == '\u0000' })
        assertTrue(ownedCopy.rp.name.all { it == '\u0000' })
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `eraseAfter waits for canceled request children to finish`() =
        runTest {
            val options = options()
            val request = Job()
            val childStarted = CompletableDeferred<Unit>()
            val finishCleanup = CompletableDeferred<Unit>()
            val child =
                CoroutineScope(coroutineContext + request).launch {
                    try {
                        childStarted.complete(Unit)
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) { finishCleanup.await() }
                    }
                }
            childStarted.await()

            request.cancel()
            options.eraseAfter(request)

            assertEquals("testuser", options.user.name, "admitted child must retain its input while unwinding")
            assertTrue(options.challenge.any { it != 0.toByte() })

            finishCleanup.complete(Unit)
            child.join()
            request.join()

            assertTrue(options.challenge.all { it == 0.toByte() })
            assertTrue(options.user.name.all { it == '\u0000' })
            assertTrue(options.rp.name.all { it == '\u0000' })
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
