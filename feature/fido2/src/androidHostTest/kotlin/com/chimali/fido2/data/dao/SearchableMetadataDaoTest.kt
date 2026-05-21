package com.chimali.fido2.data.dao

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.chimali.fido2.data.database.Fido2Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * T024 [US2] — DAO exact-match token query tests.
 *
 * Verifies that SQLDelight queries on lookup-token columns (rp_id_index, user_id_index)
 * return the correct records and that token-based lookups are exact-match only.
 */
class SearchableMetadataDaoTest {
    private lateinit var database: Fido2Database

    @BeforeEach
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Fido2Database.Schema.create(driver)
        database = Fido2Database(driver)
    }

    // -- PasskeyCredential token queries --

    @Test
    fun `select_by_rp_id returns credentials matching rp_id_index token`() {
        val rpIdIndex = byteArrayOf(1, 2, 3)
        seedCredential("cred-1", rpIdIndex = rpIdIndex, userIdIndex = byteArrayOf(10))
        seedCredential("cred-2", rpIdIndex = byteArrayOf(99, 98), userIdIndex = byteArrayOf(20))

        val results = database.passkeyCredentialQueries.select_by_rp_id(rpIdIndex).executeAsList()
        assertEquals(1, results.size)
        assertEquals("cred-1", results[0].id)
    }

    @Test
    fun `select_by_user_id returns credentials matching user_id_index token`() {
        val userIdIndex = byteArrayOf(4, 5, 6)
        seedCredential("cred-1", rpIdIndex = byteArrayOf(10), userIdIndex = userIdIndex)
        seedCredential("cred-2", rpIdIndex = byteArrayOf(20), userIdIndex = byteArrayOf(99))

        val results = database.passkeyCredentialQueries.select_by_user_id(userIdIndex).executeAsList()
        assertEquals(1, results.size)
        assertEquals("cred-1", results[0].id)
    }

    @Test
    fun `select_by_rp_id_and_user_id returns credentials matching both tokens`() {
        val rpIdIndex = byteArrayOf(1, 2)
        val userIdIndex = byteArrayOf(3, 4)
        seedCredential("match", rpIdIndex = rpIdIndex, userIdIndex = userIdIndex)
        seedCredential("rp-only", rpIdIndex = rpIdIndex, userIdIndex = byteArrayOf(99))
        seedCredential("user-only", rpIdIndex = byteArrayOf(99), userIdIndex = userIdIndex)

        val results =
            database.passkeyCredentialQueries
                .select_by_rp_id_and_user_id(rpIdIndex, userIdIndex)
                .executeAsList()
        assertEquals(1, results.size)
        assertEquals("match", results[0].id)
    }

    @Test
    fun `count_by_rp_id returns correct count for token`() {
        val rpIdIndex = byteArrayOf(1, 2, 3)
        seedCredential("cred-1", rpIdIndex = rpIdIndex, userIdIndex = byteArrayOf(10))
        seedCredential("cred-2", rpIdIndex = rpIdIndex, userIdIndex = byteArrayOf(20))
        seedCredential("cred-3", rpIdIndex = byteArrayOf(99), userIdIndex = byteArrayOf(30))

        val count = database.passkeyCredentialQueries.count_by_rp_id(rpIdIndex).executeAsOne()
        assertEquals(2L, count)
    }

    @Test
    fun `delete_by_rp_id removes only matching token credentials`() {
        val rpIdIndex = byteArrayOf(1, 2, 3)
        seedCredential("cred-1", rpIdIndex = rpIdIndex, userIdIndex = byteArrayOf(10))
        seedCredential("cred-2", rpIdIndex = byteArrayOf(99), userIdIndex = byteArrayOf(20))

        database.passkeyCredentialQueries.delete_by_rp_id(rpIdIndex)
        val remaining = database.passkeyCredentialQueries.select_all().executeAsList()
        assertEquals(1, remaining.size)
        assertEquals("cred-2", remaining[0].id)
    }

    // -- UserConsentRecord token queries --

    @Test
    fun `select_by_rp_id returns consent records matching rp_id_index token`() {
        val rpIdIndex = byteArrayOf(7, 8, 9)
        seedRelayingParty("rp-1")
        seedConsent("consent-1", rpId = "rp-1", rpIdIndex = rpIdIndex)
        seedConsent("consent-2", rpId = "rp-1", rpIdIndex = byteArrayOf(99))

        val results =
            database.userConsentRecordQueries
                .select_by_rp_id(rpIdIndex, 100L)
                .executeAsList()
        assertEquals(1, results.size)
        assertEquals("consent-1", results[0].id)
    }

    @Test
    fun `count_by_rp_id returns correct consent count for token`() {
        val rpIdIndex = byteArrayOf(7, 8)
        seedRelayingParty("rp-1")
        seedConsent("c-1", rpId = "rp-1", rpIdIndex = rpIdIndex)
        seedConsent("c-2", rpId = "rp-1", rpIdIndex = rpIdIndex)
        seedConsent("c-3", rpId = "rp-1", rpIdIndex = byteArrayOf(99))

        val count = database.userConsentRecordQueries.count_by_rp_id(rpIdIndex).executeAsOne()
        assertEquals(2L, count)
    }

    @Test
    fun `search_by_rp_id uses token for exact RP match and LIKE for display search`() {
        val rpIdIndex = byteArrayOf(1, 2, 3)
        seedCredential("cred-1", rpIdIndex = rpIdIndex, userIdIndex = byteArrayOf(10), userName = "alice")
        seedCredential("cred-2", rpIdIndex = rpIdIndex, userIdIndex = byteArrayOf(20), userName = "bob")

        val results =
            database.passkeyCredentialQueries
                .search_by_rp_id(rp_id_index = rpIdIndex, query = "%ali%")
                .executeAsList()
        assertEquals(1, results.size)
        assertEquals("cred-1", results[0].id)
    }

    @Test
    fun `null tokens return no results for token-based queries`() {
        seedCredential("cred-1", rpIdIndex = null, userIdIndex = null)

        val rpResults = database.passkeyCredentialQueries.select_by_rp_id(byteArrayOf(1, 2)).executeAsList()
        assertTrue(rpResults.isEmpty())

        val userResults = database.passkeyCredentialQueries.select_by_user_id(byteArrayOf(3, 4)).executeAsList()
        assertTrue(userResults.isEmpty())
    }

    // -- Helpers --

    private fun seedRelayingParty(id: String) {
        database.relyingPartyQueries.insert(
            id = id,
            created_at = 1000L,
            last_used_at = null,
            credential_count = 0L,
            icon_url = null,
            is_blocked = 0L,
            name = "RP $id",
            encrypted_metadata = null,
        )
    }

    private fun seedCredential(
        id: String,
        rpIdIndex: ByteArray? = null,
        userIdIndex: ByteArray? = null,
        userName: String = "user",
    ) {
        database.passkeyCredentialQueries.insert(
            id = id,
            created_at = 1000L,
            last_used_at = 2000L,
            aaguid = "aaguid",
            cose_algorithm = -7L,
            credential_id = "cred-id-$id",
            cred_protect_policy = 1L,
            label = null,
            private_key_alias = "alias-$id",
            public_key = "pubkey-$id",
            rp_id = "rp-1",
            rp_name = "RP Name",
            sign_count = 0L,
            user_display_name = "Display",
            user_id = "user-$id",
            user_name = userName,
            rp_id_index = rpIdIndex,
            user_id_index = userIdIndex,
            encrypted_metadata = null,
        )
    }

    private fun seedConsent(
        id: String,
        rpId: String,
        rpIdIndex: ByteArray? = null,
    ) {
        database.userConsentRecordQueries.insert(
            id = id,
            timestamp = 1000L,
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
    }
}
