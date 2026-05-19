package com.chimali.fido2.domain.model

import com.chimali.core.domain.valueobject.RpId
import com.chimali.core.domain.valueobject.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

class MakeCredentialOptionsTest {
    private val rp = PublicKeyCredentialRpEntity.create(RpId("example.com"), "Example")
    private val user = PublicKeyCredentialUserEntity.create(UserId("user123"), "testuser", "Test User")
    private val challenge = ByteArray(32) { it.toByte() }

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
}
