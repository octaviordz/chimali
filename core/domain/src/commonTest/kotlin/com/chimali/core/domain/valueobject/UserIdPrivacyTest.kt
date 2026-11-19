package com.chimali.core.domain.valueobject

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** @see NFR-SEC-041 and TR-AUTH-031 */
@Req("NFR-SEC-041", "TR-AUTH-031")
class UserIdPrivacyTest {
    @Test
    fun `generic diagnostic string redacts the user identifier`() {
        val marker = "synthetic-user-id-marker"
        val userId = UserId(marker)

        assertEquals(marker, userId.value)
        assertEquals("UserId([redacted])", userId.toString())
        assertFalse(marker in userId.toString())
    }
}
