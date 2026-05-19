package com.chimali.fido2.domain.model

import com.chimali.core.domain.valueobject.RpId
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
