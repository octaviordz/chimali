package com.chimali.core.domain.model

import com.chimali.core.domain.valueobject.CredentialId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CredentialKeyDescriptorTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `accepts only the current profile for the selected algorithm`() {
        val id = CredentialId.fromByteArray(ByteArray(16) { it.toByte() })

        assertEquals(
            CredentialKeyDescriptor.P256_PROFILE,
            CredentialKeyDescriptor(
                id,
                CredentialKeyDescriptor.COSE_ES256,
                CredentialKeyDescriptor.P256_PROFILE,
            ).derivationProfile,
        )
        assertEquals(
            CredentialKeyDescriptor.ED25519_PROFILE,
            CredentialKeyDescriptor(
                id,
                CredentialKeyDescriptor.COSE_EDDSA,
                CredentialKeyDescriptor.ED25519_PROFILE,
            ).derivationProfile,
        )
        assertEquals(
            CredentialKeyDescriptor.MLDSA65_PROFILE,
            CredentialKeyDescriptor(
                id,
                CredentialKeyDescriptor.COSE_ML_DSA_65,
                CredentialKeyDescriptor.MLDSA65_PROFILE,
            ).derivationProfile,
        )
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `rejects unknown algorithms and mismatched or unknown profiles`() {
        val id = CredentialId.fromByteArray(ByteArray(16) { it.toByte() })
        assertFailsWith<IllegalArgumentException> {
            CredentialKeyDescriptor(id, -257, CredentialKeyDescriptor.P256_PROFILE)
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialKeyDescriptor(id, CredentialKeyDescriptor.COSE_ES256, CredentialKeyDescriptor.ED25519_PROFILE)
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialKeyDescriptor(id, CredentialKeyDescriptor.COSE_ES256, "unknown-profile")
        }
    }
}
