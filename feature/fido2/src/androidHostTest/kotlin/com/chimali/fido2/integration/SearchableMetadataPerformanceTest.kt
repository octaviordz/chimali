package com.chimali.fido2.integration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.fido2.data.database.Fido2Database
import com.chimali.fido2.data.database.Passkey_credential
import com.chimali.fido2.data.database.User_consent_record
import kotlin.system.measureTimeMillis
import org.junit.jupiter.api.AfterEach
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
    private var driver: JdbcSqliteDriver? = null

    private companion object {
        private const val RECORD_COUNT = 10_000
        private const val LOOKUP_TARGET_INDEX = 5_000
        private const val MAX_LOOKUP_MS = 200L
        private const val MAX_BATCH_INSERT_MS = 60_000L
    }

    @BeforeEach
    fun setUp() {
        val inMemoryDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver = inMemoryDriver
        Fido2Database.Schema.create(inMemoryDriver)
        database = Fido2Database(inMemoryDriver)
    }

    @AfterEach
    fun tearDown() {
        driver?.close()
        driver = null
    }

    /** @see NFR-PERF-030 */
    @Test
    fun `exact-match lookup by rp_id_index completes within 200ms over 10000 records`() {
        // Seed relying party for FK constraint
        insertRelyingParty("rp-perf", "Performance RP", RECORD_COUNT.toLong())

        // Target token for the lookup
        val targetRpIdIndex = deriveTestToken("rp-perf", LOOKUP_TARGET_INDEX)

        // Batch insert
        val insertMs =
            measureTimeMillis {
                database.transaction {
                    for (i in 0 until RECORD_COUNT) {
                        insertPasskeyCredential(i, "rp-perf", "Performance RP", 32)
                    }
                }
            }
        assertTrue(
            insertMs < MAX_BATCH_INSERT_MS,
            "Batch insert of $RECORD_COUNT records took ${insertMs}ms, exceeding ${MAX_BATCH_INSERT_MS}ms budget",
        )

        var matchedRows: List<Passkey_credential> = emptyList()
        try {
            clearPasskeyRows(database.passkeyCredentialQueries.select_by_rp_id(targetRpIdIndex).executeAsList())
            val lookupMs =
                measureTimeMillis {
                    matchedRows = database.passkeyCredentialQueries.select_by_rp_id(targetRpIdIndex).executeAsList()
                    assertEquals(1, matchedRows.size, "Should find exactly 1 credential for the target token")
                    assertEquals("cred-$LOOKUP_TARGET_INDEX", matchedRows[0].id)
                }
            assertTrue(
                lookupMs < MAX_LOOKUP_MS,
                "Exact-match lookup took ${lookupMs}ms, exceeding ${MAX_LOOKUP_MS}ms budget",
            )
        } finally {
            clearPasskeyRows(matchedRows)
            targetRpIdIndex.fill(0)
        }
    }

    /** @see NFR-PERF-030 */
    @Test
    fun `exact-match lookup by combined rp_id_index and user_id_index performs within budget`() {
        database.transaction {
            for (i in 0 until RECORD_COUNT) {
                insertPasskeyCredential(i, "rp-combined", "Combined RP", 16)
            }
        }

        val targetRpIdx = deriveTestToken("rp-combined", LOOKUP_TARGET_INDEX)
        val targetUserIdx = deriveTestToken("user", LOOKUP_TARGET_INDEX)

        var matchedRows: List<Passkey_credential> = emptyList()
        try {
            clearPasskeyRows(
                database.passkeyCredentialQueries
                    .select_by_rp_id_and_user_id(
                        targetRpIdx,
                        targetUserIdx,
                    ).executeAsList(),
            )
            val lookupMs =
                measureTimeMillis {
                    matchedRows =
                        database.passkeyCredentialQueries
                            .select_by_rp_id_and_user_id(targetRpIdx, targetUserIdx)
                            .executeAsList()
                    assertEquals(1, matchedRows.size)
                }
            assertTrue(
                lookupMs < MAX_LOOKUP_MS,
                "Combined exact-match lookup took ${lookupMs}ms, exceeding ${MAX_LOOKUP_MS}ms budget",
            )
        } finally {
            clearPasskeyRows(matchedRows)
            targetRpIdx.fill(0)
            targetUserIdx.fill(0)
        }
    }

    /** @see NFR-PERF-030 */
    @Test
    fun `consent record rp_id_index lookup performs within budget over 10000 records`() {
        insertRelyingParty("rp-consent", "Consent RP", 0L)

        database.transaction {
            for (i in 0 until RECORD_COUNT) {
                insertConsentRecord(i)
            }
        }

        val targetToken = deriveTestToken("rp-consent", LOOKUP_TARGET_INDEX)

        var matchedRows: List<User_consent_record> = emptyList()
        try {
            clearConsentRows(database.userConsentRecordQueries.select_by_rp_id(targetToken, 10L).executeAsList())
            val lookupMs =
                measureTimeMillis {
                    matchedRows = database.userConsentRecordQueries.select_by_rp_id(targetToken, 10L).executeAsList()
                    assertEquals(1, matchedRows.size)
                }
            assertTrue(
                lookupMs < MAX_LOOKUP_MS,
                "Consent rp_id_index lookup took ${lookupMs}ms, exceeding ${MAX_LOOKUP_MS}ms budget",
            )
        } finally {
            clearConsentRows(matchedRows)
            targetToken.fill(0)
        }
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
        val domainBytes = domain.encodeToByteArray()
        val indexBytes = index.toString().encodeToByteArray()
        try {
            digest.update(domainBytes)
            digest.update(indexBytes)
            return digest.digest()
        } finally {
            domainBytes.fill(0)
            indexBytes.fill(0)
        }
    }

    private fun insertRelyingParty(
        idText: String,
        nameText: String,
        credentialCount: Long,
    ) {
        val id = idText.encodeToByteArray()
        val name = nameText.encodeToByteArray()
        try {
            database.relyingPartyQueries.insert(
                id = id,
                created_at = 1000L,
                updated_at = 1000L,
                last_used_at = null,
                credential_count = credentialCount,
                icon_url = null,
                is_blocked = 0L,
                name = name,
                encrypted_metadata = null,
            )
        } finally {
            id.fill(0)
            name.fill(0)
        }
    }

    private fun insertPasskeyCredential(
        index: Int,
        rpIdText: String,
        rpNameText: String,
        metadataSize: Int,
    ) {
        val rpId = rpIdText.encodeToByteArray()
        val rpName = rpNameText.encodeToByteArray()
        val displayName = "User $index".encodeToByteArray()
        val userId = "user-$index".encodeToByteArray()
        val userName = "user$index".encodeToByteArray()
        val rpIdIndex = deriveTestToken(rpIdText, index)
        val userIdIndex = deriveTestToken("user", index)
        val metadata = ByteArray(metadataSize) { (index % 256).toByte() }
        try {
            database.passkeyCredentialQueries.insert(
                id = "cred-$index",
                created_at = 1000L + index,
                updated_at = 1000L + index,
                last_used_at = 2000L + index,
                aaguid = "aaguid",
                cose_algorithm = -7L,
                credential_id = "cred-id-$index",
                cred_protect_policy = 1L,
                label = null,
                derivation_profile = "chimali-local-hdk-p256-v1",
                public_key = "pubkey-$index",
                rp_id = rpId,
                rp_name = rpName,
                sign_count = 0L,
                user_display_name = displayName,
                user_id = userId,
                user_name = userName,
                rp_id_index = rpIdIndex,
                user_id_index = userIdIndex,
                encrypted_metadata = metadata,
            )
        } finally {
            rpId.fill(0)
            rpName.fill(0)
            displayName.fill(0)
            userId.fill(0)
            userName.fill(0)
            rpIdIndex.fill(0)
            userIdIndex.fill(0)
            metadata.fill(0)
        }
    }

    private fun insertConsentRecord(index: Int) {
        val rpId = "rp-consent".encodeToByteArray()
        val rpIdIndex = deriveTestToken("rp-consent", index)
        try {
            database.userConsentRecordQueries.insert(
                id = "consent-$index",
                created_at = 1000L + index,
                timestamp = 1000L + index,
                updated_at = 1000L + index,
                biometric_used = 0L,
                credential_id = null,
                device_id = null,
                ip_address = null,
                operation_type = "AUTHENTICATION",
                pin_used = 0L,
                rp_id = rpId,
                user_agent = null,
                rp_id_index = rpIdIndex,
            )
        } finally {
            rpId.fill(0)
            rpIdIndex.fill(0)
        }
    }

    private fun clearPasskeyRows(rows: List<Passkey_credential>) {
        rows.forEach { row ->
            row.encrypted_metadata?.fill(0)
            row.rp_id.fill(0)
            row.rp_id_index?.fill(0)
            row.rp_name.fill(0)
            row.user_display_name.fill(0)
            row.user_id.fill(0)
            row.user_id_index?.fill(0)
            row.user_name.fill(0)
        }
    }

    private fun clearConsentRows(rows: List<User_consent_record>) {
        rows.forEach { row ->
            row.rp_id.fill(0)
            row.rp_id_index?.fill(0)
        }
    }
}
