package com.chimali.fido2.integration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.fido2.data.database.Fido2Database
import kotlin.system.measureTimeMillis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * T050 [US4] — 10,000-record exact-match lookup performance coverage.
 *
 * Validates that token-based exact-match queries on indexed BLOB columns
 * remain performant at scale (< 200ms for a single lookup out of 10,000 records).
 */
class SearchableMetadataPerformanceTest {
    private lateinit var database: Fido2Database

    private companion object {
        private const val RECORD_COUNT = 10_000
        private const val LOOKUP_TARGET_INDEX = 5_000
        private const val MAX_LOOKUP_MS = 200L
        private const val MAX_BATCH_INSERT_MS = 60_000L
    }

    @BeforeEach
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Fido2Database.Schema.create(driver)
        database = Fido2Database(driver)
    }

    @Test
    fun `exact-match lookup by rp_id_index completes within 200ms over 10000 records`() {
        // Seed relying party for FK constraint
        database.relyingPartyQueries.insert(
            id = "rp-perf",
            created_at = 1000L,
            last_used_at = null,
            credential_count = RECORD_COUNT.toLong(),
            icon_url = null,
            is_blocked = 0L,
            name = "Performance RP",
            encrypted_metadata = null,
        )

        // Target token for the lookup
        val targetRpIdIndex = deriveTestToken("rp-perf", LOOKUP_TARGET_INDEX)

        // Batch insert
        val insertMs =
            measureTimeMillis {
                database.transaction {
                    for (i in 0 until RECORD_COUNT) {
                        val rpIdIndex = deriveTestToken("rp-perf", i)
                        val userIdIndex = deriveTestToken("user", i)
                        database.passkeyCredentialQueries.insert(
                            id = "cred-$i",
                            created_at = 1000L + i,
                            last_used_at = 2000L + i,
                            aaguid = "aaguid",
                            cose_algorithm = -7L,
                            credential_id = "cred-id-$i",
                            cred_protect_policy = 1L,
                            label = null,
                            private_key_alias = "alias-$i",
                            public_key = "pubkey-$i",
                            rp_id = "rp-perf",
                            rp_name = "Performance RP",
                            sign_count = 0L,
                            user_display_name = "User $i",
                            user_id = "user-$i",
                            user_name = "user$i",
                            rp_id_index = rpIdIndex,
                            user_id_index = userIdIndex,
                            encrypted_metadata = ByteArray(32) { (i % 256).toByte() },
                        )
                    }
                }
            }
        assertTrue(
            insertMs < MAX_BATCH_INSERT_MS,
            "Batch insert of $RECORD_COUNT records took ${insertMs}ms, exceeding ${MAX_BATCH_INSERT_MS}ms budget",
        )

        // Warm up the query engine
        database.passkeyCredentialQueries.select_by_rp_id(targetRpIdIndex).executeAsList()

        // Measure exact-match lookup
        val lookupMs =
            measureTimeMillis {
                val results =
                    database.passkeyCredentialQueries
                        .select_by_rp_id(targetRpIdIndex)
                        .executeAsList()
                assertEquals(1, results.size, "Should find exactly 1 credential for the target token")
                assertEquals("cred-$LOOKUP_TARGET_INDEX", results[0].id)
            }

        assertTrue(
            lookupMs < MAX_LOOKUP_MS,
            "Exact-match lookup took ${lookupMs}ms, exceeding ${MAX_LOOKUP_MS}ms budget",
        )
    }

    @Test
    fun `exact-match lookup by combined rp_id_index and user_id_index performs within budget`() {
        database.transaction {
            for (i in 0 until RECORD_COUNT) {
                database.passkeyCredentialQueries.insert(
                    id = "cred-$i",
                    created_at = 1000L + i,
                    last_used_at = 2000L + i,
                    aaguid = "aaguid",
                    cose_algorithm = -7L,
                    credential_id = "cred-id-$i",
                    cred_protect_policy = 1L,
                    label = null,
                    private_key_alias = "alias-$i",
                    public_key = "pubkey-$i",
                    rp_id = "rp-combined",
                    rp_name = "Combined RP",
                    sign_count = 0L,
                    user_display_name = "User $i",
                    user_id = "user-$i",
                    user_name = "user$i",
                    rp_id_index = deriveTestToken("rp-combined", i),
                    user_id_index = deriveTestToken("user", i),
                    encrypted_metadata = ByteArray(16),
                )
            }
        }

        val targetRpIdx = deriveTestToken("rp-combined", LOOKUP_TARGET_INDEX)
        val targetUserIdx = deriveTestToken("user", LOOKUP_TARGET_INDEX)

        // Warm up
        database.passkeyCredentialQueries.select_by_rp_id_and_user_id(targetRpIdx, targetUserIdx).executeAsList()

        val lookupMs =
            measureTimeMillis {
                val results =
                    database.passkeyCredentialQueries
                        .select_by_rp_id_and_user_id(targetRpIdx, targetUserIdx)
                        .executeAsList()
                assertEquals(1, results.size)
            }

        assertTrue(
            lookupMs < MAX_LOOKUP_MS,
            "Combined exact-match lookup took ${lookupMs}ms, exceeding ${MAX_LOOKUP_MS}ms budget",
        )
    }

    @Test
    fun `consent record rp_id_index lookup performs within budget over 10000 records`() {
        database.relyingPartyQueries.insert(
            id = "rp-consent",
            created_at = 1000L,
            last_used_at = null,
            credential_count = 0L,
            icon_url = null,
            is_blocked = 0L,
            name = "Consent RP",
            encrypted_metadata = null,
        )

        database.transaction {
            for (i in 0 until RECORD_COUNT) {
                database.userConsentRecordQueries.insert(
                    id = "consent-$i",
                    timestamp = 1000L + i,
                    biometric_used = 0L,
                    credential_id = null,
                    device_id = null,
                    ip_address = null,
                    operation_type = "AUTHENTICATION",
                    pin_used = 0L,
                    rp_id = "rp-consent",
                    user_agent = null,
                    rp_id_index = deriveTestToken("rp-consent", i),
                )
            }
        }

        val targetToken = deriveTestToken("rp-consent", LOOKUP_TARGET_INDEX)

        // Warm up
        database.userConsentRecordQueries.select_by_rp_id(targetToken, 10L).executeAsList()

        val lookupMs =
            measureTimeMillis {
                val results =
                    database.userConsentRecordQueries
                        .select_by_rp_id(targetToken, 10L)
                        .executeAsList()
                assertEquals(1, results.size)
            }

        assertTrue(
            lookupMs < MAX_LOOKUP_MS,
            "Consent rp_id_index lookup took ${lookupMs}ms, exceeding ${MAX_LOOKUP_MS}ms budget",
        )
    }

    /**
     * Derives a unique deterministic test token for a given domain and index.
     * Uses a simple hash to simulate distinct HMAC tokens without requiring
     * the real crypto service.
     */
    private fun deriveTestToken(
        domain: String,
        index: Int,
    ): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(domain.toByteArray())
        digest.update(index.toString().toByteArray())
        return digest.digest()
    }
}
