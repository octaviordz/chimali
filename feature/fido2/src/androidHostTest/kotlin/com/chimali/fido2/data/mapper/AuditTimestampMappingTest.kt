package com.chimali.fido2.data.mapper

import com.chimali.core.common.annotation.Req
import com.chimali.core.domain.model.ConsentOperationType
import com.chimali.fido2.data.database.Relying_party
import com.chimali.fido2.data.database.User_consent_record
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Verifies audit timestamps at SQLDelight reconstruction boundaries. */
class AuditTimestampMappingTest {
    /** @see FR-003, FR-016 and TR-AUTH-031 */
    @Req("FR-003", "FR-016", "NFR-SEC-041", "TR-AUTH-031")
    @Test
    fun `RP and consent mapping accept equal and advanced audit timestamps`() {
        listOf(1000L to null, 1000L to 1000L, 3000L to 2000L).forEach { (updatedAt, activityAt) ->
            val rp = relyingParty(createdAt = 1000L, lastUsedAt = activityAt, updatedAt = updatedAt)
            val mappedRp = rp.toDomainModel()
            mappedRp.eraseSensitiveText()
            assertTrue(rp.id.all { it == 0.toByte() })
            assertTrue(rp.name.all { it == 0.toByte() })

            val consent = consent(createdAt = 1000L, timestamp = activityAt ?: 1000L, updatedAt = updatedAt)
            val mappedConsent = consent.toDomainModel()
            mappedConsent.eraseSensitiveText()
            assertTrue(consent.rp_id.all { it == 0.toByte() })
        }
    }

    /** @see FR-003, FR-016 and TR-AUTH-031 */
    @Req("FR-003", "FR-016", "NFR-SEC-041", "TR-AUTH-031")
    @ParameterizedTest
    @ValueSource(strings = ["before-created", "before-activity"])
    fun `RP mapper rejects reversed audit timestamps and erases row bytes`(case: String) {
        val row =
            when (case) {
                "before-created" -> relyingParty(createdAt = 3000L, lastUsedAt = null, updatedAt = 2000L)
                "before-activity" -> relyingParty(createdAt = 1000L, lastUsedAt = 3000L, updatedAt = 2000L)
                else -> error("Unknown fixture")
            }

        val error = assertFailsWith<IllegalArgumentException> { row.toDomainModel() }

        assertEquals("Malformed relying-party record", error.message)
        assertEquals(null, error.cause)
        assertTrue(row.id.all { it == 0.toByte() })
        assertTrue(row.name.all { it == 0.toByte() })
    }

    /** @see FR-003, FR-016 and TR-AUTH-031 */
    @Req("FR-003", "FR-016", "NFR-SEC-041", "TR-AUTH-031")
    @ParameterizedTest
    @ValueSource(strings = ["before-created", "before-consent"])
    fun `consent mapper rejects reversed audit timestamps and erases row bytes`(case: String) {
        val row =
            when (case) {
                "before-created" -> consent(createdAt = 3000L, timestamp = 1000L, updatedAt = 2000L)
                "before-consent" -> consent(createdAt = 1000L, timestamp = 3000L, updatedAt = 2000L)
                else -> error("Unknown fixture")
            }

        val error = assertFailsWith<IllegalArgumentException> { row.toDomainModel() }

        assertEquals("Malformed consent record", error.message)
        assertEquals(null, error.cause)
        assertTrue(row.rp_id.all { it == 0.toByte() })
    }

    private fun relyingParty(
        createdAt: Long,
        lastUsedAt: Long?,
        updatedAt: Long,
    ) = Relying_party(
        id = "https://synthetic.example".encodeToByteArray(),
        created_at = createdAt,
        last_used_at = lastUsedAt,
        updated_at = updatedAt,
        credential_count = 0,
        encrypted_metadata = null,
        icon_url = null,
        is_blocked = 0,
        name = "Synthetic RP".encodeToByteArray(),
    )

    private fun consent(
        createdAt: Long,
        timestamp: Long,
        updatedAt: Long,
    ) = User_consent_record(
        id = "synthetic-consent",
        created_at = createdAt,
        timestamp = timestamp,
        updated_at = updatedAt,
        biometric_used = 0,
        credential_id = null,
        device_id = null,
        ip_address = null,
        operation_type = ConsentOperationType.AUTHENTICATION.name,
        pin_used = 0,
        rp_id = "https://synthetic.example".encodeToByteArray(),
        rp_id_index = null,
        user_agent = null,
    )
}
