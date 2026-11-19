package com.chimali.core.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse

class CredentialSummaryApiPrivacyTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `candidate summary does not expose an immutable RP compatibility getter`() {
        assertFalse(exposesImmutableRpGetter(CredentialSummary::class.java))
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `consent record does not expose an immutable RP compatibility getter`() {
        assertFalse(exposesImmutableRpGetter(UserConsentRecord::class.java))
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `consent record does not expose the unsafe credential diagnostic helper`() {
        val accessorNames = UserConsentRecord::class.java.methods.map { it.name }
        assertFalse(accessorNames.any { it == "getSafeCredentialId" })
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `relying party does not expose immutable id or name compatibility getters`() {
        val accessorNames = RelyingParty::class.java.methods.map { it.name }
        assertFalse(accessorNames.any { it == "getId" || it.startsWith("getId-") })
        assertFalse(accessorNames.any { it == "getName" || it.startsWith("getName-") })
    }

    private fun exposesImmutableRpGetter(type: Class<*>): Boolean =
        type.methods.any { method -> method.name == "getRpId" || method.name.startsWith("getRpId-") }
}
