package com.chimali.core.domain.model

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.valueobject.CredentialId
import com.chimali.core.domain.valueobject.RpId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** @see NFR-SEC-041 and TR-AUTH-031 */
class CredentialProtectionPolicyTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `only absent extension selects the documented default`() {
        assertNull(CredentialProtectionPolicy.fromExtensions(null))
        assertNull(CredentialProtectionPolicy.fromExtensions(emptyMap<String, Any>()))
        assertEquals(
            CredentialProtectionPolicy.DEFAULT,
            CredentialProtectionPolicy.fromExtensions(emptyMap<String, Any>()) ?: CredentialProtectionPolicy.DEFAULT,
        )
        assertFailsWith<IllegalArgumentException> {
            CredentialProtectionPolicy.fromExtensions(mapOf("credProtect" to null))
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `supported policy values remain exact across extension and persistence boundaries`() {
        (1..3).forEach { policy ->
            assertEquals(policy, CredentialProtectionPolicy.fromExtensions(mapOf("credProtect" to policy)))
            assertEquals(policy, CredentialProtectionPolicy.fromExtensions(mapOf("credProtect" to policy.toLong())))
            assertEquals(policy, CredentialProtectionPolicy.fromPersisted(policy.toLong()))
            val summary =
                CredentialSummary(
                    id = "credential-$policy",
                    rpId = RpId("example.com"),
                    credentialId = CredentialId.fromByteArray(ByteArray(16) { policy.toByte() }),
                    lastUsedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
                    coseAlgorithm = -7,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                    storedPublicKeyEncoding = byteArrayOf(1),
                    credProtectPolicy = policy,
                )
            assertEquals(policy, summary.credProtectPolicy)
        }
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Req("NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `unsupported negative out-of-range and wrapping policies are rejected before narrowing`() {
        listOf(Long.MIN_VALUE, -1L, 0L, 4L, 4_294_967_297L, Long.MAX_VALUE).forEach { raw ->
            assertFailsWith<IllegalArgumentException> { CredentialProtectionPolicy.fromPersisted(raw) }
            assertFailsWith<IllegalArgumentException> {
                CredentialProtectionPolicy.fromExtensions(mapOf("credProtect" to raw))
            }
        }
        listOf(Int.MIN_VALUE, -1, 0, 4, Int.MAX_VALUE).forEach { raw ->
            assertFailsWith<IllegalArgumentException> { CredentialProtectionPolicy.requireSupported(raw) }
            assertFailsWith<IllegalArgumentException> {
                CredentialProtectionPolicy.fromExtensions(mapOf("credProtect" to raw))
            }
            assertFailsWith<IllegalArgumentException> {
                CredentialSummary(
                    id = "invalid-policy",
                    rpId = RpId("example.com"),
                    credentialId = CredentialId.fromByteArray(ByteArray(16)),
                    lastUsedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
                    coseAlgorithm = -7,
                    derivationProfile = CredentialKeyDescriptor.P256_PROFILE,
                    storedPublicKeyEncoding = byteArrayOf(1),
                    credProtectPolicy = raw,
                )
            }
        }
        listOf("3", true, 3.0, null).forEach { raw ->
            assertFailsWith<IllegalArgumentException> {
                CredentialProtectionPolicy.fromExtensions(mapOf("credProtect" to raw))
            }
        }
    }
}
